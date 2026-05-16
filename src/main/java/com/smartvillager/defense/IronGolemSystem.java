package com.smartvillager.defense;

import com.mojang.logging.LogUtils;
import com.smartvillager.SmartVillager;
import com.smartvillager.village.SmartVillage;
import com.smartvillager.village.VillageRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Commissions, spawns, and stations village-owned iron golems at the storehouse.
 *
 * Commissioning (full simulation):
 *   The Librarian check in tick() triggers golem creation when two conditions are
 *   met: village prosperity >= GOLEM_PROSPERITY_THRESHOLD AND the stockpile holds
 *   at least GOLEM_IRON_COST iron ingots. 36 ingots are consumed from the stockpile
 *   and spawnGolem() is called. This is a stand-in for the Armorer behavior which
 *   will be wired up in a later branch.
 *
 * Behavior (full simulation):
 *   - Peaceful: golems are walked back to their station at the storehouse entrance
 *     whenever they drift beyond STATION_RADIUS blocks from it.
 *   - THREAT_ALERT active: golem.setTarget() is called with the nearest Monster in
 *     the village scan area; vanilla IronGolem AI handles the actual combat.
 *   - After combat resolves, golems return to station on the next tick.
 *
 * Death detection:
 *   - LivingDeathEvent fires when a golem dies during full simulation.
 *   - detectDeadGolems() provides a tick-based fallback for golems that vanish
 *     without a death event (e.g. /kill, command removal).
 *   - Both paths call village.removeGolem() which records the replacement cooldown.
 *
 * Abstract simulation:
 *   - abstractTick() applies ABSTRACT_THREAT_DAMAGE HP per call to each tracked
 *     golem while a threat alert is active. At zero HP the golem slot is vacated.
 *   - Golem abstract health defaults to GOLEM_MAX_HEALTH after a server restart
 *     (same pattern as per-villager abstract health).
 *
 * Cap:
 *   SmartVillage.GOLEM_CAP golems per village (currently 1; scales with Bell count
 *   when multi-Bell support is added to VillageRegistry).
 */
@EventBusSubscriber(modid = SmartVillager.MOD_ID)
public final class IronGolemSystem {
    private IronGolemSystem() {}

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Prosperity score required before the village commissions a golem. */
    public static final int GOLEM_PROSPERITY_THRESHOLD = 250;
    /** Iron ingots consumed from the stockpile to commission one golem. */
    public static final int GOLEM_IRON_COST = 36;
    /** Notional HP assigned to a freshly spawned golem for abstract-sim tracking. */
    public static final int GOLEM_MAX_HEALTH = 100;

    /** How often (ticks) this system runs during full simulation. */
    public static final int TICK_INTERVAL = 40;

    private static final Identifier IRON_INGOT = Identifier.withDefaultNamespace("iron_ingot");

    /** Squared distance (blocks²) within which a golem is considered "on station". */
    private static final double STATION_RADIUS_SQ = 64.0; // 8-block radius
    /** Navigation speed used when returning a golem to the storehouse. */
    private static final double STATION_NAV_SPEED = 0.5;
    /** Radius (blocks) around the village anchor to scan for threats to engage. */
    private static final double THREAT_SCAN_RADIUS = 24.0;
    /** Notional damage applied to each golem per abstract-sim batch call during a threat. */
    private static final int ABSTRACT_THREAT_DAMAGE = 15;

    // -------------------------------------------------------------------------
    // Public spawn API — will also be called by Armorer behavior in a later branch
    // -------------------------------------------------------------------------

    /**
     * Spawns a vanilla IronGolem near the storehouse, records its UUID on the
     * village, and initialises its abstract-sim health to GOLEM_MAX_HEALTH.
     */
    public static void spawnGolem(ServerLevel level, SmartVillage village) {
        BlockPos station = getStationPos(village);

        IronGolem golem = EntityType.IRON_GOLEM.create(level, EntitySpawnReason.MOB_SUMMONED);
        if (golem == null) {
            LOGGER.warn("[SmartVillager] Village at {} — failed to create IronGolem entity",
                village.getAnchor());
            return;
        }

        golem.snapTo(station.getX() + 0.5, station.getY(), station.getZ() + 0.5, 0.0f, 0.0f);
        level.addFreshEntity(golem);
        village.addGolem(golem.getUUID());

        LOGGER.info("[SmartVillager] Village at {} — iron golem {} spawned at {} (prosperity={}, cost={} ingots)",
            village.getAnchor(), golem.getUUID(), station,
            village.getProsperityScore(), GOLEM_IRON_COST);
    }

    // -------------------------------------------------------------------------
    // Full simulation tick
    // -------------------------------------------------------------------------

    public static void tick(ServerLevel level, SmartVillage village, long gameTick) {
        if (gameTick % TICK_INTERVAL != 0) return;

        detectDeadGolems(level, village, gameTick);

        if (village.getGolems().size() < SmartVillage.GOLEM_CAP) {
            tryCommission(level, village, gameTick);
        }

        stationOrEngageGolems(level, village);
    }

    // -------------------------------------------------------------------------
    // Abstract simulation tick
    // -------------------------------------------------------------------------

    /**
     * Applies threat-based damage to tracked golem(s) during an active threat alert.
     * Called once per abstract batch update (~every 60 seconds of game time).
     */
    public static void abstractTick(SmartVillage village) {
        if (!village.isThreatAlertActive()) return;

        for (UUID golemUUID : new ArrayList<>(village.getGolems())) {
            int hp = village.getAbstractGolemHealth(golemUUID);
            hp -= ABSTRACT_THREAT_DAMAGE;
            if (hp <= 0) {
                long deathTick = village.getLastAbstractUpdate();
                village.removeGolem(golemUUID, deathTick);
                LOGGER.info("[SmartVillager] Village at {} — iron golem {} died during abstract simulation",
                    village.getAnchor(), golemUUID);
            } else {
                village.setAbstractGolemHealth(golemUUID, hp);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Death detection event (full simulation)
    // -------------------------------------------------------------------------

    @SubscribeEvent
    public static void onGolemDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof IronGolem golem)) return;
        if (event.getEntity().level().isClientSide()) return;

        ServerLevel level = (ServerLevel) event.getEntity().level();
        UUID golemUUID = golem.getUUID();

        VillageRegistry registry = VillageRegistry.get(level);
        registry.all().stream()
            .filter(v -> v.getGolems().contains(golemUUID))
            .findFirst()
            .ifPresent(village -> {
                village.removeGolem(golemUUID, level.getGameTime());
                registry.setDirty();
                LOGGER.info("[SmartVillager] Village at {} — iron golem {} died; replacement cooldown until tick {}",
                    village.getAnchor(), golemUUID, village.getGolemReplacementCooldownTick());
            });
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Scans tracked golem UUIDs for any entity that is no longer present in the
     * level (e.g. killed by command, chunk edge case). Vacates those slots.
     */
    private static void detectDeadGolems(ServerLevel level, SmartVillage village, long gameTick) {
        List<UUID> missing = new ArrayList<>();
        for (UUID uuid : village.getGolems()) {
            if (!(level.getEntity(uuid) instanceof IronGolem)) {
                missing.add(uuid);
            }
        }
        for (UUID uuid : missing) {
            village.removeGolem(uuid, gameTick);
            LOGGER.info("[SmartVillager] Village at {} — tracked golem {} no longer present; slot vacated",
                village.getAnchor(), uuid);
        }
    }

    /** Checks all conditions and commissions a golem if they are met. */
    private static void tryCommission(ServerLevel level, SmartVillage village, long gameTick) {
        if (gameTick < village.getGolemReplacementCooldownTick()) return;
        if (village.getProsperityScore() < GOLEM_PROSPERITY_THRESHOLD) return;
        if (!village.getStockpile().hasEnough(IRON_INGOT, GOLEM_IRON_COST)) return;

        village.getStockpile().withdraw(IRON_INGOT, GOLEM_IRON_COST);
        spawnGolem(level, village);
    }

    /** Stations each active golem near the storehouse, or directs them at threats during an alert. */
    private static void stationOrEngageGolems(ServerLevel level, SmartVillage village) {
        BlockPos station = getStationPos(village);

        for (UUID uuid : village.getGolems()) {
            if (!(level.getEntity(uuid) instanceof IronGolem golem)) continue;

            if (village.isThreatAlertActive()) {
                engageNearestThreat(level, golem, village);
            } else {
                returnToStation(golem, station);
            }
        }
    }

    private static void engageNearestThreat(ServerLevel level, IronGolem golem, SmartVillage village) {
        BlockPos anchor = village.getAnchor();
        double r = THREAT_SCAN_RADIUS;
        AABB scanArea = new AABB(
            anchor.getX() - r, anchor.getY() - 8.0, anchor.getZ() - r,
            anchor.getX() + r, anchor.getY() + 8.0, anchor.getZ() + r
        );

        Monster nearest = null;
        double best = Double.MAX_VALUE;
        for (Monster m : level.getEntitiesOfClass(Monster.class, scanArea)) {
            double d = golem.distanceToSqr(m);
            if (d < best) { best = d; nearest = m; }
        }

        if (nearest != null) {
            golem.setTarget(nearest);
        }
    }

    private static void returnToStation(IronGolem golem, BlockPos station) {
        if (golem.blockPosition().distSqr(station) > STATION_RADIUS_SQ) {
            golem.getNavigation().moveTo(
                station.getX() + 0.5, station.getY(), station.getZ() + 0.5,
                STATION_NAV_SPEED);
        }
    }

    /**
     * Returns the position where golems should be stationed.
     * Prefers the first registered stockpile chest; falls back to just north of the Bell anchor.
     */
    static BlockPos getStationPos(SmartVillage village) {
        List<BlockPos> chests = village.getChestTracker().positions();
        if (!chests.isEmpty()) return chests.get(0);
        return village.getAnchor().offset(0, 0, 5);
    }
}
