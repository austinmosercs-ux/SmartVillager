package com.smartvillager.food;

import com.mojang.logging.LogUtils;
import com.smartvillager.daynight.DayNightCycle;
import com.smartvillager.village.SmartVillage;
import com.smartvillager.village.VillageStockpile;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.villager.Villager;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Drives Fisherman fish production and water-adjacent resource subrole.
 *
 * Full simulation: Fishermen walk to the anchor area (representing the nearest
 * water source) and produce cooked fish on a timer. As a subrole they also
 * deposit sand, gravel, flint, and clay gathered at the water's edge.
 *
 * Abstract simulation: Batch production per-fisherman from roster count.
 */
public final class FishermanSystem {
    private FishermanSystem() {}

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final Identifier PROF_FISHERMAN = Identifier.withDefaultNamespace("fisherman");

    private static final Identifier COOKED_COD    = Identifier.withDefaultNamespace("cooked_cod");
    private static final Identifier COOKED_SALMON = Identifier.withDefaultNamespace("cooked_salmon");
    private static final Identifier SAND          = Identifier.withDefaultNamespace("sand");
    private static final Identifier GRAVEL        = Identifier.withDefaultNamespace("gravel");
    private static final Identifier FLINT         = Identifier.withDefaultNamespace("flint");
    private static final Identifier CLAY_BALL     = Identifier.withDefaultNamespace("clay_ball");

    /** Ticks between fishing cycles. */
    private static final long FISH_INTERVAL     = 250L;
    /** Ticks between water-edge resource gathering cycles. */
    private static final long RESOURCE_INTERVAL = 400L;

    private static final float MOVE_SPEED    = 0.5f;
    private static final double WORK_RANGE_SQ = 16.0;

    /** Per abstract-batch production per fisherman (~4 fishing cycles, day-adjusted). */
    private static final int BATCH_COOKED_COD    = 3;
    private static final int BATCH_COOKED_SALMON = 2;
    private static final int BATCH_SAND          = 3;
    private static final int BATCH_GRAVEL        = 2;
    private static final int BATCH_FLINT         = 2;
    private static final int BATCH_CLAY          = 2;

    public static final int TICK_INTERVAL = 20;

    private static final Map<UUID, Long> lastFishTick     = new HashMap<>();
    private static final Map<UUID, Long> lastResourceTick = new HashMap<>();

    // -------------------------------------------------------------------------
    // Full simulation
    // -------------------------------------------------------------------------

    public static void tick(ServerLevel level, SmartVillage village, long gameTick) {
        if (gameTick % TICK_INTERVAL != 0) return;
        for (Villager fisherman : FarmerSystem.findVillagers(level, village, PROF_FISHERMAN)) {
            tickFisherman(level, village, fisherman, gameTick);
        }
    }

    private static void tickFisherman(ServerLevel level, SmartVillage village,
                                       Villager fisherman, long gameTick) {
        if (!DayNightCycle.isResourceGatheringAllowed(level)) return;

        UUID id = fisherman.getUUID();
        VillageStockpile stockpile = village.getStockpile();

        if (!isAtWorkArea(fisherman, village)) {
            fisherman.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                new WalkTarget(village.getAnchor(), MOVE_SPEED, 2));
            return;
        }

        long lastFish = lastFishTick.getOrDefault(id, 0L);
        if (gameTick - lastFish >= FISH_INTERVAL) {
            stockpile.deposit(COOKED_COD,    2);
            stockpile.deposit(COOKED_SALMON, 1);
            lastFishTick.put(id, gameTick);
            LOGGER.debug("[SmartVillager] Fisherman {} caught fish (village {})", id, village.getAnchor());
        }

        long lastResource = lastResourceTick.getOrDefault(id, 0L);
        if (gameTick - lastResource >= RESOURCE_INTERVAL) {
            stockpile.deposit(SAND,      2);
            stockpile.deposit(GRAVEL,    1);
            stockpile.deposit(FLINT,     1);
            stockpile.deposit(CLAY_BALL, 1);
            lastResourceTick.put(id, gameTick);
            LOGGER.debug("[SmartVillager] Fisherman {} gathered water-edge resources (village {})", id, village.getAnchor());
        }
    }

    // -------------------------------------------------------------------------
    // Abstract simulation
    // -------------------------------------------------------------------------

    public static void abstractTick(SmartVillage village) {
        int count = village.countProfession(PROF_FISHERMAN);
        if (count == 0) return;

        VillageStockpile stockpile = village.getStockpile();
        stockpile.deposit(COOKED_COD,    count * BATCH_COOKED_COD);
        stockpile.deposit(COOKED_SALMON, count * BATCH_COOKED_SALMON);
        stockpile.deposit(SAND,          count * BATCH_SAND);
        stockpile.deposit(GRAVEL,        count * BATCH_GRAVEL);
        stockpile.deposit(FLINT,         count * BATCH_FLINT);
        stockpile.deposit(CLAY_BALL,     count * BATCH_CLAY);

        LOGGER.debug("[SmartVillager] Abstract Fisherman: {} fishermen produced (village {})",
            count, village.getAnchor());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static boolean isAtWorkArea(Villager v, SmartVillage village) {
        return v.distanceToSqr(
            village.getAnchor().getX(),
            village.getAnchor().getY(),
            village.getAnchor().getZ()) <= WORK_RANGE_SQ;
    }
}
