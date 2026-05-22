package com.smartvillager.food;

import com.mojang.logging.LogUtils;
import com.smartvillager.daynight.DayNightCycle;
import com.smartvillager.village.LibrarianCoordinator;
import com.smartvillager.village.SmartVillage;
import com.smartvillager.village.VillageStockpile;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.villager.Villager;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Drives Farmer crop harvesting and wood-gathering subrole in both simulation modes.
 *
 * Full simulation: Farmers walk to the village anchor (farmland area) and produce
 * crops on a timer. When food supply is adequate they switch to the wood subrole,
 * depositing logs and saplings for Fletcher and Mason consumption.
 *
 * Abstract simulation: Batch production is applied per-farmer from the roster count.
 */
public final class FarmerSystem {
    private FarmerSystem() {}

    private static final Logger LOGGER = LogUtils.getLogger();

    static final Identifier PROF_FARMER = Identifier.withDefaultNamespace("farmer");

    private static final Identifier BREAD       = Identifier.withDefaultNamespace("bread");
    private static final Identifier CARROT      = Identifier.withDefaultNamespace("carrot");
    private static final Identifier POTATO      = Identifier.withDefaultNamespace("potato");
    private static final Identifier WHEAT       = Identifier.withDefaultNamespace("wheat");
    private static final Identifier OAK_LOG     = Identifier.withDefaultNamespace("oak_log");
    private static final Identifier OAK_SAPLING = Identifier.withDefaultNamespace("oak_sapling");

    /** Ticks between crop harvest cycles. */
    private static final long CROP_INTERVAL = 200L;
    /** Ticks between wood-gathering cycles (subrole). */
    private static final long WOOD_INTERVAL = 400L;
    /** Food total above which the Farmer activates the wood subrole. */
    private static final int FOOD_ADEQUATE = 64;

    private static final float MOVE_SPEED = 0.5f;
    /** Squared block distance the Farmer must be from the anchor to produce. */
    private static final double WORK_RANGE_SQ = 16.0;

    /** Per abstract-batch production per farmer (~4 crop cycles at 1/200 ticks, day-adjusted). */
    private static final int BATCH_BREAD   = 4;
    private static final int BATCH_CARROT  = 6;
    private static final int BATCH_POTATO  = 6;
    private static final int BATCH_WHEAT   = 4;
    private static final int BATCH_LOG     = 3;
    private static final int BATCH_SAPLING = 1;

    public static final int TICK_INTERVAL = 20;

    private static final Map<UUID, Long> lastCropTick = new HashMap<>();
    private static final Map<UUID, Long> lastWoodTick = new HashMap<>();

    // -------------------------------------------------------------------------
    // Full simulation
    // -------------------------------------------------------------------------

    public static void tick(ServerLevel level, SmartVillage village, long gameTick) {
        if (gameTick % TICK_INTERVAL != 0) return;
        for (Villager farmer : findVillagers(level, village, PROF_FARMER)) {
            tickFarmer(level, village, farmer, gameTick);
        }
    }

    private static void tickFarmer(ServerLevel level, SmartVillage village,
                                    Villager farmer, long gameTick) {
        if (!DayNightCycle.isResourceGatheringAllowed(level)) return;

        UUID id = farmer.getUUID();
        VillageStockpile stockpile = village.getStockpile();

        if (!isAtWorkArea(farmer, village)) {
            farmer.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                new WalkTarget(village.getAnchor(), MOVE_SPEED, 2));
            return;
        }

        long lastCrop = lastCropTick.getOrDefault(id, 0L);
        if (gameTick - lastCrop >= CROP_INTERVAL) {
            stockpile.deposit(BREAD,  1);
            stockpile.deposit(CARROT, 2);
            stockpile.deposit(POTATO, 2);
            stockpile.deposit(WHEAT,  2);
            lastCropTick.put(id, gameTick);
            LOGGER.debug("[SmartVillager] Farmer {} harvested crops (village {})", id, village.getAnchor());
        }

        if (stockpile.totalOf(LibrarianCoordinator.FOOD_ITEMS) >= FOOD_ADEQUATE) {
            long lastWood = lastWoodTick.getOrDefault(id, 0L);
            if (gameTick - lastWood >= WOOD_INTERVAL) {
                stockpile.deposit(OAK_LOG,     1);
                stockpile.deposit(OAK_SAPLING, 1);
                lastWoodTick.put(id, gameTick);
                LOGGER.debug("[SmartVillager] Farmer {} gathered wood (village {})", id, village.getAnchor());
            }
        }
    }

    // -------------------------------------------------------------------------
    // Abstract simulation
    // -------------------------------------------------------------------------

    public static void abstractTick(SmartVillage village) {
        int count = village.countProfession(PROF_FARMER);
        if (count == 0) return;

        VillageStockpile stockpile = village.getStockpile();
        stockpile.deposit(BREAD,  count * BATCH_BREAD);
        stockpile.deposit(CARROT, count * BATCH_CARROT);
        stockpile.deposit(POTATO, count * BATCH_POTATO);
        stockpile.deposit(WHEAT,  count * BATCH_WHEAT);

        if (stockpile.totalOf(LibrarianCoordinator.FOOD_ITEMS) >= FOOD_ADEQUATE) {
            stockpile.deposit(OAK_LOG,     count * BATCH_LOG);
            stockpile.deposit(OAK_SAPLING, count * BATCH_SAPLING);
        }

        LOGGER.debug("[SmartVillager] Abstract Farmer: {} farmers produced crops (village {})",
            count, village.getAnchor());
    }

    // -------------------------------------------------------------------------
    // Shared helpers (package-private — used by other food systems)
    // -------------------------------------------------------------------------

    public static List<Villager> findVillagers(ServerLevel level, SmartVillage village, Identifier prof) {
        List<Villager> result = new ArrayList<>();
        for (Map.Entry<UUID, Identifier> e : village.getRoster().entrySet()) {
            if (!prof.equals(e.getValue())) continue;
            if (level.getEntity(e.getKey()) instanceof Villager v) result.add(v);
        }
        return result;
    }

    private static boolean isAtWorkArea(Villager v, SmartVillage village) {
        return v.distanceToSqr(
            village.getAnchor().getX(),
            village.getAnchor().getY(),
            village.getAnchor().getZ()) <= WORK_RANGE_SQ;
    }
}
