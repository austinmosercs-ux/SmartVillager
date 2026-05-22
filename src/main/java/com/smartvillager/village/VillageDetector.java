package com.smartvillager.village;

import com.mojang.logging.LogUtils;
import com.smartvillager.SmartVillager;
import com.smartvillager.cleric.ClericHealingSystem;
import com.smartvillager.defense.GuardDefenseSystem;
import com.smartvillager.defense.IronGolemSystem;
import com.smartvillager.defense.PatrolSystem;
import com.smartvillager.food.ButcherSystem;
import com.smartvillager.food.FarmerSystem;
import com.smartvillager.food.FishermanSystem;
import com.smartvillager.food.LeatherworkerSystem;
import com.smartvillager.food.ShepherdSystem;
import com.smartvillager.build.MasonSystem;
import com.smartvillager.supply.ArmorerSystem;
import com.smartvillager.supply.FletcherSystem;
import com.smartvillager.supply.WeaponsmithSystem;
import com.smartvillager.health.HealthSystem;
import com.smartvillager.hunger.HungerSystem;
import com.smartvillager.needqueue.NeedQueue;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.npc.villager.VillagerType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import javax.annotation.Nullable;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;

import java.util.Optional;

/**
 * Detects vanilla villages on villager join, registers them with VillageRegistry,
 * assigns professions at birth, and drives simulation mode switching.
 */
@EventBusSubscriber(modid = SmartVillager.MOD_ID)
public final class VillageDetector {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** How often (in ticks) we check player proximity and run abstract batch updates. */
    private static final int PROXIMITY_CHECK_INTERVAL = 40;
    /** Distance (in blocks) within which full simulation runs. */
    private static final int FULL_SIM_RADIUS = 512;
    /** How many ticks between abstract batch updates (~60 seconds). */
    private static final long ABSTRACT_UPDATE_INTERVAL = 1200L;

    // --- Starting inventory seeds ---
    private static final Identifier ITEM_BREAD = Identifier.withDefaultNamespace("bread");

    // --- Prosperity thresholds for role unlocking ---
    // Tier 0 (0+):   Librarian, Farmer, Guard — always available; core survival
    // Tier 1 (50+):  Cleric, Fisherman — healthcare and supplementary food
    // Tier 2 (100+): Shepherd, Butcher — animal husbandry chain
    // Tier 3 (150+): Leatherworker, Toolsmith — early manufacturing
    // Tier 4 (200+): Weaponsmith, Armorer, Fletcher, Mason — full defense and expansion
    private static final int TIER_1_THRESHOLD = 50;
    private static final int TIER_2_THRESHOLD = 100;
    private static final int TIER_3_THRESHOLD = 150;
    private static final int TIER_4_THRESHOLD = 200;

    // --- Profession Identifiers ---
    private static final Identifier PROF_LIBRARIAN     = Identifier.withDefaultNamespace("librarian");
    private static final Identifier PROF_FARMER        = Identifier.withDefaultNamespace("farmer");
    private static final Identifier PROF_GUARD         = Identifier.fromNamespaceAndPath(SmartVillager.MOD_ID, "guard");
    private static final Identifier PROF_CLERIC        = Identifier.withDefaultNamespace("cleric");
    private static final Identifier PROF_FISHERMAN     = Identifier.withDefaultNamespace("fisherman");
    private static final Identifier PROF_SHEPHERD      = Identifier.withDefaultNamespace("shepherd");
    private static final Identifier PROF_BUTCHER       = Identifier.withDefaultNamespace("butcher");
    private static final Identifier PROF_LEATHERWORKER = Identifier.withDefaultNamespace("leatherworker");
    private static final Identifier PROF_TOOLSMITH     = Identifier.withDefaultNamespace("toolsmith");
    private static final Identifier PROF_WEAPONSMITH   = Identifier.withDefaultNamespace("weaponsmith");
    private static final Identifier PROF_ARMORER       = Identifier.withDefaultNamespace("armorer");
    private static final Identifier PROF_FLETCHER      = Identifier.withDefaultNamespace("fletcher");
    private static final Identifier PROF_MASON         = Identifier.withDefaultNamespace("mason");
    private VillageDetector() {}

    // -------------------------------------------------------------------------
    // Permanent death + role replacement
    // -------------------------------------------------------------------------

    /**
     * When a villager dies (any cause — combat, environment, starvation), remove
     * them from the village roster permanently. The role is not filled immediately;
     * chooseProfession() will prioritize restoring the lost profession at the next
     * natural villager birth.
     */
    @SubscribeEvent
    public static void onVillagerDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof Villager villager)) return;
        if (villager.level().isClientSide()) return;

        ServerLevel level = (ServerLevel) villager.level();
        UUID uuid = villager.getUUID();

        VillageRegistry registry = VillageRegistry.get(level);
        registry.all().stream()
            .filter(v -> v.hasVillager(uuid))
            .findFirst()
            .ifPresent(village -> {
                Identifier profession = village.getRoster().get(uuid);
                village.removeVillager(uuid);
                registry.setDirty();
                LOGGER.info("[SmartVillager] Villager {} ({}) died permanently in village at {} — role queued for replacement at next birth",
                    uuid, profession, village.getAnchor());
                if (village.countProfession(profession) == 0) {
                    LOGGER.warn("[SmartVillager] Critical role {} lost — village at {} has no remaining {}",
                        profession, village.getAnchor(), profession);
                }
            });
    }

    // -------------------------------------------------------------------------
    // Village detection + profession assignment
    // -------------------------------------------------------------------------

    @SubscribeEvent
    public static void onVillagerJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof Villager villager)) return;

        ServerLevel level = (ServerLevel) event.getLevel();

        // Find the nearest Bell (MEETING POI) within 64 blocks.
        // Bell == village anchor; no Bell == not a SmartVillager-managed village.
        Optional<BlockPos> bellPos = level.getPoiManager().findClosest(
            h -> h.is(PoiTypes.MEETING),
            villager.blockPosition(),
            64,
            PoiManager.Occupancy.ANY
        );

        if (bellPos.isEmpty()) return;

        VillageRegistry registry = VillageRegistry.get(level);

        SmartVillage village = registry.findByAnchor(bellPos.get())
            .orElseGet(() -> registerNewVillage(level, registry, bellPos.get(), villager));

        if (!village.hasVillager(villager.getUUID())) {
            assignProfession(level, village, villager);
            registry.setDirty();
        }
    }

    private static SmartVillage registerNewVillage(ServerLevel level, VillageRegistry registry,
                                                    BlockPos anchor, Villager firstVillager) {
        ResourceKey<VillagerType> typeKey = firstVillager.getVillagerData().type().unwrapKey()
            .orElse(VillagerType.PLAINS);

        SmartVillage village = SmartVillage.create(anchor, typeKey, level.getRandom(), level.getGameTime());
        registry.register(village);

        initializeStorehouse(level, village);

        LOGGER.info("[SmartVillager] Registered new village at {} (type={}, color={})",
            anchor, typeKey.identifier(), village.getMerchantColor());
        return village;
    }

    /**
     * Places a starting storehouse chest near the Bell anchor and seeds the
     * village with a minimal starting inventory. The chest position is
     * registered with the StockpileChestTracker so all subsequent deposits and
     * withdrawals route through it once full simulation activates.
     *
     * If no valid position is found (unusual terrain), items are kept in the
     * abstract snapshot only and the chest is placed later by Mason expansion.
     */
    private static void initializeStorehouse(ServerLevel level, SmartVillage village) {
        seedStartingInventory(village);

        BlockPos chestPos = findStorehousePos(level, village.getAnchor());
        if (chestPos == null) {
            LOGGER.warn("[SmartVillager] Could not find valid storehouse position near {} — starting with snapshot-only stockpile",
                village.getAnchor());
            return;
        }

        level.setBlock(chestPos, Blocks.CHEST.defaultBlockState(), 3);
        village.getChestTracker().register(chestPos);

        LOGGER.info("[SmartVillager] Placed initial storehouse chest at {} for village at {}",
            chestPos, village.getAnchor());
    }

    /** Seeds the village stockpile with minimal starting resources per design spec. */
    private static void seedStartingInventory(SmartVillage village) {
        village.getStockpile().deposit(ITEM_BREAD, 8);
        village.getStockpile().deposit(ClericHealingSystem.HEALING_SUPPLY, 4);
    }

    /**
     * Searches cardinal directions at increasing distances from the anchor for
     * an air block with a sturdy floor — a valid chest placement site.
     */
    @Nullable
    private static BlockPos findStorehousePos(ServerLevel level, BlockPos anchor) {
        for (int dist = 4; dist <= 10; dist++) {
            int[][] cardinals = {{dist, 0}, {-dist, 0}, {0, dist}, {0, -dist}};
            for (int[] dir : cardinals) {
                for (int dy = -3; dy <= 3; dy++) {
                    BlockPos candidate = anchor.offset(dir[0], dy, dir[1]);
                    if (!level.getBlockState(candidate).isAir()) continue;
                    BlockPos floor = candidate.below();
                    if (level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP)) {
                        return candidate;
                    }
                }
            }
        }
        return null;
    }

    /**
     * Assigns a profession to a villager based on what the village currently needs.
     *
     * Starting roster priority:
     *   1. Librarian — the administrative hub; must exist in every village
     *   2. Farmer    — critical food supply; must exist before Guard is added
     *   3. Guard     — defense; one per Bell is the target
     *   4. Farmer    — default for all additional villagers
     */
    private static void assignProfession(ServerLevel level, SmartVillage village, Villager villager) {
        Identifier professionId = chooseProfession(village);

        ResourceKey<VillagerProfession> profKey = ResourceKey.create(Registries.VILLAGER_PROFESSION, professionId);
        villager.setVillagerData(villager.getVillagerData().withProfession(level.registryAccess(), profKey));
        // XP > 0 prevents ResetProfession from stripping the profession if there is no
        // job site block within reach (workblocks are anchors only in this mod, and the
        // village may not yet have the matching vanilla block placed nearby).
        villager.setVillagerXp(1);
        villager.refreshBrain(level);

        village.assignProfession(villager.getUUID(), professionId);

        LOGGER.debug("[SmartVillager] Assigned {} to villager {} in village at {}",
            professionId, villager.getUUID(), village.getAnchor());
    }

    private static Identifier chooseProfession(SmartVillage village) {
        int prosperity = village.getProsperityScore();

        // Tier 0 — always available; every village needs these to function at all.
        Identifier missing = firstMissing(village, PROF_LIBRARIAN, PROF_FARMER, PROF_GUARD);
        if (missing != null) return missing;

        // Tier 1 — healthcare and supplementary food (prosperity 50+).
        if (prosperity >= TIER_1_THRESHOLD) {
            missing = firstMissing(village, PROF_CLERIC, PROF_FISHERMAN);
            if (missing != null) return missing;
        }

        // Tier 2 — animal husbandry chain (prosperity 100+).
        if (prosperity >= TIER_2_THRESHOLD) {
            missing = firstMissing(village, PROF_SHEPHERD, PROF_BUTCHER);
            if (missing != null) return missing;
        }

        // Tier 3 — early manufacturing (prosperity 150+).
        if (prosperity >= TIER_3_THRESHOLD) {
            missing = firstMissing(village, PROF_LEATHERWORKER, PROF_TOOLSMITH);
            if (missing != null) return missing;
        }

        // Tier 4 — full defense and expansion chain (prosperity 200+).
        if (prosperity >= TIER_4_THRESHOLD) {
            missing = firstMissing(village, PROF_WEAPONSMITH, PROF_ARMORER, PROF_FLETCHER, PROF_MASON);
            if (missing != null) return missing;
        }

        // Default: additional Farmer. Excess villagers build food surplus that
        // drives prosperity growth, which eventually unlocks the next role tier.
        // A role lost while prosperity is below its tier threshold cannot be
        // replaced until prosperity recovers — the loss has lasting consequences.
        return PROF_FARMER;
    }

    /** Returns the first profession in the list that has zero members in the village roster. */
    @Nullable
    private static Identifier firstMissing(SmartVillage village, Identifier... professions) {
        for (Identifier prof : professions) {
            if (village.countProfession(prof) == 0) return prof;
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // Simulation mode switching + abstract batch updates
    // -------------------------------------------------------------------------

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (level.getGameTime() % PROXIMITY_CHECK_INTERVAL != 0) return;

        VillageRegistry registry = VillageRegistry.get(level);
        for (SmartVillage village : registry.all()) {
            tickVillage(level, registry, village);
        }
    }

    /** How often (in ticks) the LibrarianCoordinator scans for shortages during full simulation. */
    private static final int LIBRARIAN_CHECK_INTERVAL = 200;

    private static void tickVillage(ServerLevel level, VillageRegistry registry, SmartVillage village) {
        boolean playerNearby = level.players().stream().anyMatch(
            p -> p.blockPosition().distSqr(village.getAnchor()) <= (long) FULL_SIM_RADIUS * FULL_SIM_RADIUS
        );

        SimulationMode newMode = playerNearby ? SimulationMode.FULL : SimulationMode.ABSTRACT;

        if (village.getMode() != newMode) {
            if (newMode == SimulationMode.FULL) {
                onTransitionToFull(level, village);
            } else {
                onTransitionToAbstract(level, village);
            }
            village.setMode(newMode);
        }

        if (village.getMode() == SimulationMode.ABSTRACT) {
            long elapsed = level.getGameTime() - village.getLastAbstractUpdate();
            if (elapsed >= ABSTRACT_UPDATE_INTERVAL) {
                runAbstractBatchUpdate(village, elapsed, level.getGameTime());
                village.setLastAbstractUpdate(level.getGameTime());
                registry.setDirty();
            }
        } else {
            HungerSystem.tick(level, village, PROXIMITY_CHECK_INTERVAL);
            HealthSystem.tick(level, village);
            FarmerSystem.tick(level, village, level.getGameTime());
            FishermanSystem.tick(level, village, level.getGameTime());
            ShepherdSystem.tick(level, village, level.getGameTime());
            ButcherSystem.tick(level, village, level.getGameTime());
            LeatherworkerSystem.tick(level, village, level.getGameTime());
            WeaponsmithSystem.tick(level, village, level.getGameTime());
            ArmorerSystem.tick(level, village, level.getGameTime());
            FletcherSystem.tick(level, village, level.getGameTime());
            MasonSystem.tick(level, village, level.getGameTime());
            ClericHealingSystem.tick(level, village, level.getGameTime());
            GuardDefenseSystem.tick(level, village, level.getGameTime());
            PatrolSystem.tick(level, village, level.getGameTime());
            IronGolemSystem.tick(level, village, level.getGameTime());
            if (level.getGameTime() % LIBRARIAN_CHECK_INTERVAL == 0) {
                LibrarianCoordinator.tick(village);
                NeedQueue.tick(village, level.getGameTime());
                village.addProsperity(1);
            }
        }
    }

    private static void onTransitionToFull(ServerLevel level, SmartVillage village) {
        LOGGER.debug("[SmartVillager] Village at {} → FULL simulation (player entered range)",
            village.getAnchor());
        village.activateFullSim(level);
    }

    private static void onTransitionToAbstract(ServerLevel level, SmartVillage village) {
        LOGGER.debug("[SmartVillager] Village at {} → ABSTRACT simulation (player left range)",
            village.getAnchor());
        village.activateAbstractSim(level);
    }

    private static void runAbstractBatchUpdate(SmartVillage village, long elapsed, long currentTick) {
        LOGGER.debug("[SmartVillager] Abstract batch update for village at {} (roster size: {}, elapsed: {} ticks)",
            village.getAnchor(), village.getRoster().size(), elapsed);
        LibrarianCoordinator.abstractTick(village);
        NeedQueue.abstractTick(village, currentTick);
        GuardDefenseSystem.abstractTick(village);
        IronGolemSystem.abstractTick(village);
        FarmerSystem.abstractTick(village);
        FishermanSystem.abstractTick(village);
        ShepherdSystem.abstractTick(village);
        ButcherSystem.abstractTick(village);
        LeatherworkerSystem.abstractTick(village);
        WeaponsmithSystem.abstractTick(village);
        ArmorerSystem.abstractTick(village);
        FletcherSystem.abstractTick(village);
        MasonSystem.abstractTick(village);
        village.addProsperity(5);
        Map<UUID, Integer> missedMeals = HungerSystem.abstractTick(village, elapsed);
        Set<UUID> died = HealthSystem.abstractTick(village, missedMeals);
        ClericHealingSystem.abstractTick(village);
        for (UUID uuid : died) {
            Identifier profession = village.getRoster().get(uuid);
            village.removeVillager(uuid);
            LOGGER.info("[SmartVillager] Villager {} ({}) starved to death during abstract simulation in village at {}",
                uuid, profession, village.getAnchor());
        }
    }
}
