package com.smartvillager.defense;

import com.mojang.logging.LogUtils;
import com.smartvillager.SmartVillager;
import com.smartvillager.needqueue.NeedPriority;
import com.smartvillager.needqueue.NeedQueue;
import com.smartvillager.needqueue.NeedTypes;
import com.smartvillager.village.ProsperitySystem;
import com.smartvillager.village.SmartVillage;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Drives Guard combat and village-wide threat response during full simulation.
 *
 * Each tick interval:
 *   1. Scans for hostile mobs near each Guard and the village anchor.
 *   2. If threats are found: activates THREAT_ALERT on SmartVillage, directs Guards
 *      toward the nearest hostile and deals melee damage when in range, and orders
 *      civilian villagers to shelter near the Bell.
 *   3. If no threats remain for THREAT_CLEAR_COOLDOWN ticks: clears the alert and
 *      posts NEED_HEALING to the NeedQueue for any Guards below the heal threshold.
 *
 * Civilians shelter via two mechanisms:
 *   - HEARD_BELL_TIME: triggers vanilla panic behavior (villager runs to hiding spot).
 *   - WALK_TARGET override: direct path to the Bell anchor as a reliable fallback.
 *
 * Abstract simulation: no entity movement is possible, so the method is a no-op.
 * The threat alert state persists on SmartVillage until full simulation resumes.
 */
public final class GuardDefenseSystem {
    private GuardDefenseSystem() {}

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final Identifier PROF_GUARD =
        Identifier.fromNamespaceAndPath(SmartVillager.MOD_ID, "guard");

    /** How often (ticks) the system runs during full simulation — every real-world second. */
    public static final int TICK_INTERVAL = 20;

    /** Radius (blocks) around the village anchor and each Guard to scan for hostiles. */
    private static final double THREAT_SCAN_RADIUS = 24.0;

    /** Squared distance (blocks²) at which a Guard attempts a melee strike. */
    private static final double MELEE_RANGE_SQ = 4.0;

    /** Damage dealt per Guard melee strike. */
    private static final float GUARD_ATTACK_DAMAGE = 4.0f;

    /** Minimum ticks between melee strikes for a single Guard. */
    private static final int ATTACK_COOLDOWN_TICKS = 20;

    /** Ticks with no hostile detected before THREAT_ALERT clears (~60 seconds). */
    private static final long THREAT_CLEAR_COOLDOWN = 1200L;

    /** Guard HP at or below which a NEED_HEALING is posted after a fight clears. */
    private static final float HEAL_REQUEST_HP_THRESHOLD = 10.0f;

    /** Guard movement speed while engaging a threat. */
    private static final float ENGAGE_SPEED = 0.65f;

    /** Civilian movement speed while sheltering. */
    private static final float SHELTER_SPEED = 0.6f;

    /** Radius (blocks) around the anchor to scan for civilian villagers during shelter. */
    private static final double CIVILIAN_SCAN_RADIUS = 96.0;

    /** Per-Guard last-attack game tick; cleared naturally when Guards are no longer tracked. */
    private static final Map<UUID, Long> attackCooldowns = new HashMap<>();

    // -------------------------------------------------------------------------
    // Full simulation entry point
    // -------------------------------------------------------------------------

    public static void tick(ServerLevel level, SmartVillage village, long gameTick) {
        if (gameTick % TICK_INTERVAL != 0) return;

        List<Villager> guards = findGuards(level, village);
        List<Monster> threats = scanForThreats(level, village, guards);

        if (!threats.isEmpty()) {
            village.activateThreatAlert(gameTick);
            threats.forEach(m -> village.getVillageMemory().recordThreat(m.blockPosition(), gameTick));
            engageGuards(level, guards, threats, gameTick);
            shelterCivilians(level, village, guards);
            LOGGER.debug("[SmartVillager] Village at {} — THREAT_ALERT active ({} guard(s), {} threat(s))",
                village.getAnchor(), guards.size(), threats.size());
        } else if (village.isThreatAlertActive()
                && gameTick - village.getLastThreatSeenTick() >= THREAT_CLEAR_COOLDOWN) {
            clearAlert(village, guards, gameTick);
        }
    }

    // -------------------------------------------------------------------------
    // Abstract simulation entry point
    // -------------------------------------------------------------------------

    public static void abstractTick() {
        // No entities are loaded during abstract simulation; the threat alert state
        // is preserved as-is on SmartVillage. Threat resolution resumes when full
        // simulation restarts and Guards can actually engage.
    }

    // -------------------------------------------------------------------------
    // Guard and threat discovery
    // -------------------------------------------------------------------------

    private static List<Villager> findGuards(ServerLevel level, SmartVillage village) {
        List<Villager> guards = new ArrayList<>();
        for (Map.Entry<UUID, Identifier> entry : village.getRoster().entrySet()) {
            if (!PROF_GUARD.equals(entry.getValue())) continue;
            if (level.getEntity(entry.getKey()) instanceof Villager v) {
                guards.add(v);
            }
        }
        return guards;
    }

    private static List<Monster> scanForThreats(ServerLevel level, SmartVillage village,
                                                  List<Villager> guards) {
        // LinkedHashSet deduplicates mobs found in overlapping scan areas.
        Set<Monster> found = new LinkedHashSet<>();

        BlockPos anchor = village.getAnchor();
        double r = THREAT_SCAN_RADIUS;
        AABB anchorArea = new AABB(
            anchor.getX() - r, anchor.getY() - 8.0, anchor.getZ() - r,
            anchor.getX() + r, anchor.getY() + 8.0, anchor.getZ() + r
        );
        found.addAll(level.getEntitiesOfClass(Monster.class, anchorArea));

        for (Villager guard : guards) {
            found.addAll(level.getEntitiesOfClass(
                Monster.class, guard.getBoundingBox().inflate(THREAT_SCAN_RADIUS)));
        }
        return new ArrayList<>(found);
    }

    // -------------------------------------------------------------------------
    // Guard engagement
    // -------------------------------------------------------------------------

    private static void engageGuards(ServerLevel level, List<Villager> guards,
                                      List<Monster> threats, long gameTick) {
        for (Villager guard : guards) {
            Monster target = findNearest(guard, threats);
            if (target == null) continue;

            guard.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                new WalkTarget(target.blockPosition(), ENGAGE_SPEED, 1));

            if (guard.distanceToSqr(target) <= MELEE_RANGE_SQ) {
                long lastAttack = attackCooldowns.getOrDefault(guard.getUUID(), 0L);
                if (gameTick - lastAttack >= ATTACK_COOLDOWN_TICKS) {
                    target.hurtServer(level, level.damageSources().mobAttack(guard), GUARD_ATTACK_DAMAGE);
                    attackCooldowns.put(guard.getUUID(), gameTick);
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("[SmartVillager] Guard {} struck {} for {} damage",
                            guard.getUUID(), target.getType().toShortString(), GUARD_ATTACK_DAMAGE);
                    }
                }
            }
        }
    }

    private static Monster findNearest(Villager guard, List<Monster> threats) {
        Monster nearest = null;
        double best = Double.MAX_VALUE;
        for (Monster m : threats) {
            double d = guard.distanceToSqr(m);
            if (d < best) { best = d; nearest = m; }
        }
        return nearest;
    }

    // -------------------------------------------------------------------------
    // Civilian shelter
    // -------------------------------------------------------------------------

    private static void shelterCivilians(ServerLevel level, SmartVillage village,
                                          List<Villager> guards) {
        BlockPos anchor = village.getAnchor();
        double r = CIVILIAN_SCAN_RADIUS;
        AABB area = new AABB(
            anchor.getX() - r, anchor.getY() - 32.0, anchor.getZ() - r,
            anchor.getX() + r, anchor.getY() + 32.0, anchor.getZ() + r
        );

        level.getEntitiesOfClass(Villager.class, area).stream()
            .filter(v -> village.hasVillager(v.getUUID()) && !guards.contains(v))
            .forEach(v -> {
                // HEARD_BELL_TIME triggers vanilla panic: the villager's brain switches to a
                // "hide near Bell" state. WALK_TARGET toward the anchor is a direct override
                // in case the vanilla hiding spot logic has nowhere to send them.
                v.getBrain().setMemory(MemoryModuleType.HEARD_BELL_TIME, level.getGameTime());
                v.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                    new WalkTarget(anchor, SHELTER_SPEED, 4));
            });
    }

    // -------------------------------------------------------------------------
    // Alert clearance and post-fight healing
    // -------------------------------------------------------------------------

    private static void clearAlert(SmartVillage village, List<Villager> guards, long gameTick) {
        village.clearThreatAlert();
        ProsperitySystem.onThreatDefeated(village);
        // Mark threat areas near the anchor as cleared now that Guards have resolved the fight.
        village.getVillageMemory().clearThreatNear(village.getAnchor());
        LOGGER.info("[SmartVillager] Village at {} — THREAT_ALERT cleared ({}s quiet)",
            village.getAnchor(), THREAT_CLEAR_COOLDOWN / 20);
        postHealingRequests(village, guards, gameTick);
    }

    private static void postHealingRequests(SmartVillage village, List<Villager> guards,
                                             long gameTick) {
        for (Villager guard : guards) {
            if (guard.getHealth() <= HEAL_REQUEST_HP_THRESHOLD
                    && !village.getNeedQueue().hasOpenRequest(NeedTypes.NEED_HEALING, Optional.empty())) {
                NeedQueue.postRequest(village, NeedTypes.NEED_HEALING, NeedPriority.HIGH,
                    guard.getUUID(), Optional.empty(), gameTick);
                LOGGER.info("[SmartVillager] Guard {} posted NEED_HEALING (HP {}/{})",
                    guard.getUUID(), guard.getHealth(), guard.getMaxHealth());
            }
        }
    }
}
