package com.smartvillager.food;

import com.mojang.logging.LogUtils;
import com.smartvillager.daynight.DayNightCycle;
import com.smartvillager.needqueue.NeedPriority;
import com.smartvillager.needqueue.NeedQueue;
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
import java.util.Set;
import java.util.UUID;

/**
 * Drives Butcher meat processing and the NEED_FOOD_BOOST signal to Shepherd.
 *
 * Full simulation: Butchers walk to the anchor area (smoker) and convert raw
 * meat from the stockpile to cooked meat on a timer. When total cooked meat
 * supply drops below a critical threshold, posts NEED_FOOD_BOOST to the
 * NeedQueue so Shepherd doubles culling rate.
 *
 * Abstract simulation: Same raw-to-cooked conversion applied as a batch from
 * roster count.
 */
public final class ButcherSystem {
    private ButcherSystem() {}

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final Identifier PROF_BUTCHER = Identifier.withDefaultNamespace("butcher");

    private static final Identifier RAW_BEEF     = Identifier.withDefaultNamespace("beef");
    private static final Identifier RAW_PORKCHOP = Identifier.withDefaultNamespace("porkchop");
    private static final Identifier RAW_CHICKEN  = Identifier.withDefaultNamespace("chicken");
    private static final Identifier RAW_MUTTON   = Identifier.withDefaultNamespace("mutton");

    private static final Identifier COOKED_BEEF     = Identifier.withDefaultNamespace("cooked_beef");
    private static final Identifier COOKED_PORKCHOP = Identifier.withDefaultNamespace("cooked_porkchop");
    private static final Identifier COOKED_CHICKEN  = Identifier.withDefaultNamespace("cooked_chicken");
    private static final Identifier COOKED_MUTTON   = Identifier.withDefaultNamespace("cooked_mutton");

    /** Cooked meat items — used to compute total when checking the NEED_FOOD_BOOST threshold. */
    static final Set<Identifier> COOKED_MEAT_ITEMS = Set.of(
        COOKED_BEEF, COOKED_PORKCHOP, COOKED_CHICKEN, COOKED_MUTTON
    );

    /** Max raw meat withdrawn and converted per cook cycle per butcher. */
    private static final int MAX_PER_COOK = 3;
    /** Cooked meat total below which NEED_FOOD_BOOST is posted. */
    private static final int COOKED_LOW_THRESHOLD = 8;

    /** Ticks between cook cycles. */
    private static final long COOK_INTERVAL = 200L;

    private static final float MOVE_SPEED    = 0.5f;
    private static final double WORK_RANGE_SQ = 16.0;

    /** Per abstract-batch max cook amount per type per butcher (~6 cycles, day-adjusted). */
    private static final int BATCH_MAX_PER_TYPE = 6;

    public static final int TICK_INTERVAL = 20;

    private static final Map<UUID, Long> lastCookTick = new HashMap<>();

    // -------------------------------------------------------------------------
    // Full simulation
    // -------------------------------------------------------------------------

    public static void tick(ServerLevel level, SmartVillage village, long gameTick) {
        if (gameTick % TICK_INTERVAL != 0) return;
        for (Villager butcher : FarmerSystem.findVillagers(level, village, PROF_BUTCHER)) {
            tickButcher(level, village, butcher, gameTick);
        }
    }

    private static void tickButcher(ServerLevel level, SmartVillage village,
                                     Villager butcher, long gameTick) {
        if (!DayNightCycle.isResourceGatheringAllowed(level)) return;

        UUID id = butcher.getUUID();
        VillageStockpile stockpile = village.getStockpile();

        if (!isAtWorkArea(butcher, village)) {
            butcher.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                new WalkTarget(village.getAnchor(), MOVE_SPEED, 2));
            return;
        }

        long last = lastCookTick.getOrDefault(id, 0L);
        if (gameTick - last >= COOK_INTERVAL) {
            cook(stockpile, RAW_BEEF,     COOKED_BEEF,     MAX_PER_COOK);
            cook(stockpile, RAW_PORKCHOP, COOKED_PORKCHOP, MAX_PER_COOK);
            cook(stockpile, RAW_CHICKEN,  COOKED_CHICKEN,  MAX_PER_COOK);
            cook(stockpile, RAW_MUTTON,   COOKED_MUTTON,   MAX_PER_COOK);
            lastCookTick.put(id, gameTick);
            LOGGER.debug("[SmartVillager] Butcher {} cooked meat (village {})", id, village.getAnchor());
        }

        maybePostFoodBoost(village, gameTick);
    }

    // -------------------------------------------------------------------------
    // Abstract simulation
    // -------------------------------------------------------------------------

    public static void abstractTick(SmartVillage village) {
        int count = village.countProfession(PROF_BUTCHER);
        if (count == 0) return;

        VillageStockpile stockpile = village.getStockpile();
        int max = count * BATCH_MAX_PER_TYPE;
        cook(stockpile, RAW_BEEF,     COOKED_BEEF,     max);
        cook(stockpile, RAW_PORKCHOP, COOKED_PORKCHOP, max);
        cook(stockpile, RAW_CHICKEN,  COOKED_CHICKEN,  max);
        cook(stockpile, RAW_MUTTON,   COOKED_MUTTON,   max);

        LOGGER.debug("[SmartVillager] Abstract Butcher: {} butchers cooked meat (village {})",
            count, village.getAnchor());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static void cook(VillageStockpile stockpile, Identifier raw, Identifier cooked, int max) {
        int taken = stockpile.withdraw(raw, max);
        if (taken > 0) stockpile.deposit(cooked, taken);
    }

    private static void maybePostFoodBoost(SmartVillage village, long gameTick) {
        if (village.getStockpile().totalOf(COOKED_MEAT_ITEMS) >= COOKED_LOW_THRESHOLD) return;
        if (village.getNeedQueue().hasOpenRequest(NeedTypes.NEED_FOOD_BOOST, Optional.empty())) return;
        NeedQueue.postRequest(village, NeedTypes.NEED_FOOD_BOOST, NeedPriority.HIGH,
            NeedQueue.LIBRARIAN_POSTER, Optional.empty(), gameTick);
        LOGGER.info("[SmartVillager] Butcher posted NEED_FOOD_BOOST — cooked meat critically low (village {})",
            village.getAnchor());
    }

    private static boolean isAtWorkArea(Villager v, SmartVillage village) {
        return v.distanceToSqr(
            village.getAnchor().getX(),
            village.getAnchor().getY(),
            village.getAnchor().getZ()) <= WORK_RANGE_SQ;
    }
}
