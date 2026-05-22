package com.smartvillager.food;

import com.mojang.logging.LogUtils;
import com.smartvillager.daynight.DayNightCycle;
import com.smartvillager.needqueue.NeedTypes;
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
import java.util.Optional;
import java.util.UUID;

/**
 * Drives Shepherd animal tending, byproduct harvesting, and the Butcher-coordinated
 * food boost subrole.
 *
 * Full simulation: Shepherds walk to the anchor area (animal pen) and produce
 * wool, raw meat, feathers, leather, and eggs on a timer. When NEED_FOOD_BOOST
 * is active in the NeedQueue, raw meat production is doubled.
 *
 * Abstract simulation: Batch production per-shepherd from roster count, with the
 * same boost check against the live NeedQueue.
 */
public final class ShepherdSystem {
    private ShepherdSystem() {}

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final Identifier PROF_SHEPHERD = Identifier.withDefaultNamespace("shepherd");

    private static final Identifier WHITE_WOOL   = Identifier.withDefaultNamespace("white_wool");
    private static final Identifier RAW_BEEF     = Identifier.withDefaultNamespace("beef");
    private static final Identifier RAW_PORKCHOP = Identifier.withDefaultNamespace("porkchop");
    private static final Identifier RAW_CHICKEN  = Identifier.withDefaultNamespace("chicken");
    private static final Identifier RAW_MUTTON   = Identifier.withDefaultNamespace("mutton");
    private static final Identifier FEATHER      = Identifier.withDefaultNamespace("feather");
    private static final Identifier LEATHER      = Identifier.withDefaultNamespace("leather");
    private static final Identifier EGG          = Identifier.withDefaultNamespace("egg");

    /** Ticks between animal tending cycles. */
    private static final long TEND_INTERVAL = 300L;

    private static final float MOVE_SPEED    = 0.5f;
    private static final double WORK_RANGE_SQ = 16.0;

    /** Per abstract-batch production per shepherd (~4 tending cycles, day-adjusted). */
    private static final int BATCH_WOOL         = 6;
    private static final int BATCH_RAW_BEEF     = 6;
    private static final int BATCH_RAW_PORKCHOP = 3;
    private static final int BATCH_RAW_CHICKEN  = 3;
    private static final int BATCH_RAW_MUTTON   = 2;
    private static final int BATCH_FEATHER      = 12;
    private static final int BATCH_LEATHER      = 6;
    private static final int BATCH_EGG          = 12;

    public static final int TICK_INTERVAL = 20;

    private static final Map<UUID, Long> lastTendTick = new HashMap<>();

    // -------------------------------------------------------------------------
    // Full simulation
    // -------------------------------------------------------------------------

    public static void tick(ServerLevel level, SmartVillage village, long gameTick) {
        if (gameTick % TICK_INTERVAL != 0) return;
        boolean boost = isFoodBoostActive(village);
        for (Villager shepherd : FarmerSystem.findVillagers(level, village, PROF_SHEPHERD)) {
            tickShepherd(level, village, shepherd, gameTick, boost);
        }
    }

    private static void tickShepherd(ServerLevel level, SmartVillage village,
                                      Villager shepherd, long gameTick, boolean boost) {
        if (!DayNightCycle.isResourceGatheringAllowed(level)) return;

        UUID id = shepherd.getUUID();
        VillageStockpile stockpile = village.getStockpile();

        if (!isAtWorkArea(shepherd, village)) {
            shepherd.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                new WalkTarget(village.getAnchor(), MOVE_SPEED, 2));
            return;
        }

        long last = lastTendTick.getOrDefault(id, 0L);
        if (gameTick - last < TEND_INTERVAL) return;

        int meatMultiplier = boost ? 2 : 1;
        stockpile.deposit(WHITE_WOOL,   2);
        stockpile.deposit(RAW_BEEF,     2 * meatMultiplier);
        stockpile.deposit(RAW_PORKCHOP, 1 * meatMultiplier);
        stockpile.deposit(RAW_CHICKEN,  1 * meatMultiplier);
        stockpile.deposit(RAW_MUTTON,   1 * meatMultiplier);
        stockpile.deposit(FEATHER,      4);
        stockpile.deposit(LEATHER,      2);
        stockpile.deposit(EGG,          3);
        lastTendTick.put(id, gameTick);

        LOGGER.debug("[SmartVillager] Shepherd {} tended animals{} (village {})",
            id, boost ? " [BOOST]" : "", village.getAnchor());
    }

    // -------------------------------------------------------------------------
    // Abstract simulation
    // -------------------------------------------------------------------------

    public static void abstractTick(SmartVillage village) {
        int count = village.countProfession(PROF_SHEPHERD);
        if (count == 0) return;

        boolean boost = isFoodBoostActive(village);
        int meatMultiplier = boost ? 2 : 1;
        VillageStockpile stockpile = village.getStockpile();

        stockpile.deposit(WHITE_WOOL,   count * BATCH_WOOL);
        stockpile.deposit(RAW_BEEF,     count * BATCH_RAW_BEEF     * meatMultiplier);
        stockpile.deposit(RAW_PORKCHOP, count * BATCH_RAW_PORKCHOP * meatMultiplier);
        stockpile.deposit(RAW_CHICKEN,  count * BATCH_RAW_CHICKEN  * meatMultiplier);
        stockpile.deposit(RAW_MUTTON,   count * BATCH_RAW_MUTTON   * meatMultiplier);
        stockpile.deposit(FEATHER,      count * BATCH_FEATHER);
        stockpile.deposit(LEATHER,      count * BATCH_LEATHER);
        stockpile.deposit(EGG,          count * BATCH_EGG);

        LOGGER.debug("[SmartVillager] Abstract Shepherd: {} shepherds tended animals{} (village {})",
            count, boost ? " [BOOST]" : "", village.getAnchor());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static boolean isFoodBoostActive(SmartVillage village) {
        return village.getNeedQueue().hasOpenRequest(NeedTypes.NEED_FOOD_BOOST, Optional.empty());
    }

    private static boolean isAtWorkArea(Villager v, SmartVillage village) {
        return v.distanceToSqr(
            village.getAnchor().getX(),
            village.getAnchor().getY(),
            village.getAnchor().getZ()) <= WORK_RANGE_SQ;
    }
}
