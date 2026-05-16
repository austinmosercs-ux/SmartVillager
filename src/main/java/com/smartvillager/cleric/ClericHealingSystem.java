package com.smartvillager.cleric;

import com.mojang.logging.LogUtils;
import com.smartvillager.SmartVillager;
import com.smartvillager.health.VillagerHealth;
import com.smartvillager.needqueue.NeedPriority;
import com.smartvillager.needqueue.NeedQueue;
import com.smartvillager.needqueue.NeedRequest;
import com.smartvillager.needqueue.NeedTypes;
import com.smartvillager.needqueue.VillageNeedQueue;
import com.smartvillager.registration.ModAttachments;
import com.smartvillager.village.SmartVillage;
import com.smartvillager.village.VillageStockpile;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.villager.Villager;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Drives Cleric healing and potion brewing during full and abstract simulation.
 *
 * Full simulation:
 *   Each tick interval the system finds loaded Clerics, assigns them the
 *   highest-priority patient (Guards via NEED_HEALING queue first, then any
 *   other injured roster villager by direct health-attachment scan), paths the
 *   Cleric to the patient, and applies healing when in range. When all villagers
 *   are healthy the Cleric heals any injured player near the village anchor.
 *   When idle and the supply reserve is low the Cleric brews additional doses
 *   from stockpile ingredients.
 *
 * Abstract simulation:
 *   No entities are loaded. The system heals abstract health entries directly
 *   from the stockpile supply pool and brews one batch from stockpile ingredients
 *   if the reserve is below the cap.
 *
 * Supply model:
 *   The healing supply is tracked as smartvillager:healing_supply in the shared
 *   stockpile. The Cleric brews it from minecraft:nether_wart (1) +
 *   minecraft:glass_bottle (3) → 3 doses. Each heal consumes 1 dose.
 *   LibrarianCoordinator flags a shortage when supply falls below 4 doses.
 */
public final class ClericHealingSystem {
    private ClericHealingSystem() {}

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Stockpile item identifier for brewed healing doses. */
    public static final Identifier HEALING_SUPPLY =
        Identifier.fromNamespaceAndPath(SmartVillager.MOD_ID, "healing_supply");

    private static final Identifier NETHER_WART  = Identifier.withDefaultNamespace("nether_wart");
    private static final Identifier GLASS_BOTTLE = Identifier.withDefaultNamespace("glass_bottle");

    private static final Identifier PROF_CLERIC =
        Identifier.withDefaultNamespace("cleric");
    private static final Identifier PROF_GUARD  =
        Identifier.fromNamespaceAndPath(SmartVillager.MOD_ID, "guard");

    /** HP restored to a villager per healing dose. */
    private static final float HEAL_AMOUNT = 6.0f;
    /** HP restored to a player per healing dose. */
    private static final float PLAYER_HEAL_AMOUNT = 4.0f;
    /** Player health below which the Cleric will walk toward them (14 = 70% of 20 HP). */
    private static final float PLAYER_HEAL_THRESHOLD = 14.0f;

    /** Squared distance (blocks²) at which the Cleric applies healing. */
    private static final double HEAL_RANGE_SQ = 9.0;
    /** Squared distance from the village anchor within which a player triggers proximity healing. */
    private static final double PLAYER_VILLAGE_RANGE_SQ = 64.0 * 64.0;
    /** Ticks between successive player heal applications (prevents spamming). */
    private static final long PLAYER_HEAL_COOLDOWN_TICKS = 100L;

    /** Nether wart consumed per brew batch. */
    private static final int BREW_NETHER_WART   = 1;
    /** Glass bottles consumed per brew batch. */
    private static final int BREW_GLASS_BOTTLES = 3;
    /** Healing supply doses produced per brew batch. */
    private static final int BREW_OUTPUT        = 3;
    /** Ticks between Cleric brew attempts during full simulation. */
    private static final long BREW_COOLDOWN_TICKS = 200L;
    /** Cleric will not brew if stockpile supply already meets or exceeds this value. */
    private static final int MAX_SUPPLY = 16;

    /** Cleric movement speed while walking to a patient or player. */
    private static final float MOVE_SPEED = 0.5f;

    /** How often (ticks) this system runs during full simulation. */
    public static final int TICK_INTERVAL = 20;

    // Runtime-only state — acceptable to reset on server restart.
    /** Maps Cleric UUID → UUID of the entity they are currently assigned to heal. */
    private static final Map<UUID, UUID> clericPatient = new HashMap<>();
    /** Maps player UUID → last game tick the Cleric healed that player. */
    private static final Map<UUID, Long> lastPlayerHealTick = new HashMap<>();
    /** Maps Cleric UUID → last game tick they completed a brew batch. */
    private static final Map<UUID, Long> lastBrewTick = new HashMap<>();

    // -------------------------------------------------------------------------
    // Full simulation entry point
    // -------------------------------------------------------------------------

    public static void tick(ServerLevel level, SmartVillage village, long gameTick) {
        if (gameTick % TICK_INTERVAL != 0) return;

        List<Villager> clerics = findClerics(level, village);
        if (clerics.isEmpty()) return;

        for (Villager cleric : clerics) {
            tickCleric(level, village, cleric, gameTick);
        }
    }

    private static void tickCleric(ServerLevel level, SmartVillage village,
                                    Villager cleric, long gameTick) {
        UUID clericId = cleric.getUUID();
        VillageStockpile stockpile = village.getStockpile();
        boolean hasSupply = stockpile.getCount(HEALING_SUPPLY) > 0;

        // Resolve or continue with the current patient assignment.
        UUID patientId = clericPatient.get(clericId);
        if (patientId != null) {
            if (resolveCurrentPatient(level, village, cleric, patientId, stockpile, gameTick)) {
                return;
            }
            clericPatient.remove(clericId);
        }

        if (hasSupply) {
            // 1. Highest-priority NEED_HEALING queue entry (Guards post these via GuardDefenseSystem).
            Optional<NeedRequest> queued = village.getNeedQueue()
                .findHighestPriorityOpen(NeedTypes.NEED_HEALING);
            if (queued.isPresent()) {
                clericPatient.put(clericId, queued.get().getPoster());
                return;
            }

            // 2. Any other injured roster villager detected by direct health scan.
            UUID direct = findInjuredRosterVillager(level, village);
            if (direct != null) {
                clericPatient.put(clericId, direct);
                return;
            }

            // 3. Player proximity healing — no menu, proximity triggered.
            if (healPlayerNearby(level, village, cleric, stockpile, gameTick)) {
                return;
            }
        }

        // Subrole: brew potions when idle and supply reserve is low.
        brewIfPossible(village, cleric, stockpile, gameTick);
    }

    // -------------------------------------------------------------------------
    // Active patient resolution
    // -------------------------------------------------------------------------

    /**
     * Continues the Cleric's current healing assignment.
     *
     * @return true if the Cleric is still engaged with this patient
     */
    private static boolean resolveCurrentPatient(ServerLevel level, SmartVillage village,
                                                   Villager cleric, UUID patientId,
                                                   VillageStockpile stockpile, long gameTick) {
        if (!(level.getEntity(patientId) instanceof Villager patient)) {
            village.getNeedQueue().cancelByPosterAndType(patientId, NeedTypes.NEED_HEALING);
            return false;
        }

        VillagerHealth health = patient.getData(ModAttachments.VILLAGER_HEALTH);
        if (!health.isSeekingHealing()) {
            village.getNeedQueue().cancelByPosterAndType(patientId, NeedTypes.NEED_HEALING);
            return false;
        }

        if (stockpile.getCount(HEALING_SUPPLY) <= 0) {
            return false;
        }

        // Path toward patient until in heal range.
        if (cleric.distanceToSqr(patient) > HEAL_RANGE_SQ) {
            cleric.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                new WalkTarget(patient.blockPosition(), MOVE_SPEED, 1));
            return true;
        }

        // In range — apply healing.
        patient.heal(HEAL_AMOUNT);
        patient.getData(ModAttachments.VILLAGER_HEALTH).heal(HEAL_AMOUNT);
        stockpile.withdraw(HEALING_SUPPLY, 1);
        village.getNeedQueue().cancelByPosterAndType(patientId, NeedTypes.NEED_HEALING);

        LOGGER.info("[SmartVillager] Cleric {} healed villager {} +{}HP (supply left: {})",
            cleric.getUUID(), patientId, HEAL_AMOUNT, stockpile.getCount(HEALING_SUPPLY));

        // Post NEED_MATERIALS if supply is critically low after this dose.
        if (stockpile.getCount(HEALING_SUPPLY) < 4) {
            postSupplyShortage(village, gameTick);
        }

        // Stay assigned if patient still needs more healing (HP still below threshold).
        return patient.getData(ModAttachments.VILLAGER_HEALTH).isSeekingHealing();
    }

    // -------------------------------------------------------------------------
    // Patient discovery
    // -------------------------------------------------------------------------

    /**
     * Scans roster for any non-Guard, non-Cleric villager whose health attachment
     * has isSeekingHealing() true. Guards signal via NEED_HEALING queue entries.
     */
    private static UUID findInjuredRosterVillager(ServerLevel level, SmartVillage village) {
        for (Map.Entry<UUID, Identifier> entry : village.getRoster().entrySet()) {
            Identifier prof = entry.getValue();
            if (PROF_CLERIC.equals(prof) || PROF_GUARD.equals(prof)) continue;
            if (!(level.getEntity(entry.getKey()) instanceof Villager villager)) continue;
            if (villager.getData(ModAttachments.VILLAGER_HEALTH).isSeekingHealing()) {
                return entry.getKey();
            }
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // Player proximity healing
    // -------------------------------------------------------------------------

    private static boolean healPlayerNearby(ServerLevel level, SmartVillage village,
                                             Villager cleric, VillageStockpile stockpile,
                                             long gameTick) {
        for (ServerPlayer player : level.players()) {
            if (player.getHealth() >= PLAYER_HEAL_THRESHOLD) continue;
            if (player.blockPosition().distSqr(village.getAnchor()) > PLAYER_VILLAGE_RANGE_SQ) continue;

            long lastHeal = lastPlayerHealTick.getOrDefault(player.getUUID(), 0L);
            if (gameTick - lastHeal < PLAYER_HEAL_COOLDOWN_TICKS) continue;

            if (cleric.distanceToSqr(player) > HEAL_RANGE_SQ) {
                cleric.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                    new WalkTarget(player.blockPosition(), MOVE_SPEED, 1));
                return true;
            }

            player.heal(PLAYER_HEAL_AMOUNT);
            stockpile.withdraw(HEALING_SUPPLY, 1);
            lastPlayerHealTick.put(player.getUUID(), gameTick);

            LOGGER.info("[SmartVillager] Cleric {} healed player {} +{}HP (supply left: {})",
                cleric.getUUID(), player.getUUID(), PLAYER_HEAL_AMOUNT,
                stockpile.getCount(HEALING_SUPPLY));
            return true;
        }
        return false;
    }

    // -------------------------------------------------------------------------
    // Brewing subrole
    // -------------------------------------------------------------------------

    private static void brewIfPossible(SmartVillage village, Villager cleric,
                                        VillageStockpile stockpile, long gameTick) {
        if (stockpile.getCount(HEALING_SUPPLY) >= MAX_SUPPLY) return;

        long lastBrew = lastBrewTick.getOrDefault(cleric.getUUID(), 0L);
        if (gameTick - lastBrew < BREW_COOLDOWN_TICKS) return;

        if (!stockpile.hasEnough(NETHER_WART, BREW_NETHER_WART)) return;
        if (!stockpile.hasEnough(GLASS_BOTTLE, BREW_GLASS_BOTTLES)) return;

        stockpile.withdraw(NETHER_WART, BREW_NETHER_WART);
        stockpile.withdraw(GLASS_BOTTLE, BREW_GLASS_BOTTLES);
        stockpile.deposit(HEALING_SUPPLY, BREW_OUTPUT);
        lastBrewTick.put(cleric.getUUID(), gameTick);

        LOGGER.info("[SmartVillager] Cleric {} brewed {} healing doses (total: {})",
            cleric.getUUID(), BREW_OUTPUT, stockpile.getCount(HEALING_SUPPLY));
    }

    // -------------------------------------------------------------------------
    // Abstract simulation
    // -------------------------------------------------------------------------

    /**
     * Heals injured abstract-health entries and brews one supply batch if possible.
     * Called during each abstract batch update after HungerSystem and HealthSystem.
     */
    public static void abstractTick(SmartVillage village) {
        VillageStockpile stockpile = village.getStockpile();

        // Brew one batch if the reserve is below cap and ingredients are present.
        if (stockpile.getCount(HEALING_SUPPLY) < MAX_SUPPLY
                && stockpile.hasEnough(NETHER_WART, BREW_NETHER_WART)
                && stockpile.hasEnough(GLASS_BOTTLE, BREW_GLASS_BOTTLES)) {
            stockpile.withdraw(NETHER_WART, BREW_NETHER_WART);
            stockpile.withdraw(GLASS_BOTTLE, BREW_GLASS_BOTTLES);
            stockpile.deposit(HEALING_SUPPLY, BREW_OUTPUT);
        }

        // Heal any roster villager whose abstract HP is below the low-health threshold.
        for (UUID uuid : village.getRoster().keySet()) {
            if (stockpile.getCount(HEALING_SUPPLY) <= 0) break;
            float hp = village.getAbstractHealth(uuid);
            if (hp >= VillagerHealth.LOW_HEALTH) continue;

            float healed = Math.min(HEAL_AMOUNT, VillagerHealth.MAX - hp);
            village.setAbstractHealth(uuid, hp + healed);
            stockpile.withdraw(HEALING_SUPPLY, 1);

            LOGGER.debug("[SmartVillager] Abstract Cleric healed villager {} +{}HP",
                uuid, healed);
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static List<Villager> findClerics(ServerLevel level, SmartVillage village) {
        List<Villager> clerics = new ArrayList<>();
        for (Map.Entry<UUID, Identifier> entry : village.getRoster().entrySet()) {
            if (!PROF_CLERIC.equals(entry.getValue())) continue;
            if (level.getEntity(entry.getKey()) instanceof Villager v) {
                clerics.add(v);
            }
        }
        return clerics;
    }

    private static void postSupplyShortage(SmartVillage village, long gameTick) {
        VillageNeedQueue queue = village.getNeedQueue();
        if (!queue.hasOpenRequest(NeedTypes.NEED_MATERIALS, Optional.of(HEALING_SUPPLY))) {
            NeedQueue.postRequest(village, NeedTypes.NEED_MATERIALS, NeedPriority.HIGH,
                NeedQueue.LIBRARIAN_POSTER, Optional.of(HEALING_SUPPLY), gameTick);
            LOGGER.info("[SmartVillager] Village at {} — critically low healing supply, posted NEED_MATERIALS",
                village.getAnchor());
        }
    }
}
