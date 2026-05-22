package com.smartvillager.supply;

import com.mojang.logging.LogUtils;
import com.smartvillager.daynight.DayNightCycle;
import com.smartvillager.food.FarmerSystem;
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
import java.util.UUID;

/**
 * Drives Fletcher arrow crafting and material request subrole.
 *
 * Full simulation: Fletchers walk to the anchor area and craft arrows from
 * oak logs (converted internally to sticks), feathers, and flint. When any
 * ingredient supply drops below the low threshold, the subrole posts a
 * NEED_MATERIALS request to the NeedQueue so the responsible producer responds.
 *
 * Abstract simulation: Craft arrows from available materials in the stockpile.
 *
 * Arrow recipe (per craft cycle): 1 oak log + 4 feathers + 4 flints → 16 arrows.
 * The log represents the sticks needed (1 log yields 8 sticks in vanilla;
 * 4 sticks are consumed per 16-arrow batch). Partial batches are not crafted.
 */
public final class FletcherSystem {
    private FletcherSystem() {}

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final Identifier PROF_FLETCHER  = Identifier.withDefaultNamespace("fletcher");

    private static final Identifier OAK_LOG        = Identifier.withDefaultNamespace("oak_log");
    private static final Identifier FEATHER         = Identifier.withDefaultNamespace("feather");
    private static final Identifier FLINT           = Identifier.withDefaultNamespace("flint");
    private static final Identifier ARROW           = Identifier.withDefaultNamespace("arrow");

    /** Ingredients required per craft cycle. */
    private static final int CRAFT_LOG_COST     = 1;
    private static final int CRAFT_FEATHER_COST = 4;
    private static final int CRAFT_FLINT_COST   = 4;
    /** Arrows produced per craft cycle. */
    private static final int CRAFT_ARROW_OUTPUT = 16;

    /** Target stockpile buffer for arrows before halting production. */
    private static final int ARROW_BUFFER = 64;

    /**
     * Ingredient counts below which NEED_MATERIALS is posted.
     * Shepherd responds to feather requests; Fisherman/Mason respond to flint.
     */
    private static final int FEATHER_LOW_THRESHOLD = 8;
    private static final int FLINT_LOW_THRESHOLD   = 8;

    /** Ticks between craft cycles. */
    private static final long CRAFT_INTERVAL = 300L;

    /** Craft cycles attempted per fletcher per abstract batch. */
    private static final int BATCH_CRAFT_ROUNDS = 3;

    public static final int TICK_INTERVAL = 20;

    private static final float  MOVE_SPEED    = 0.5f;
    private static final double WORK_RANGE_SQ = 16.0;

    private static final Map<UUID, Long> lastCraftTick = new HashMap<>();

    // -------------------------------------------------------------------------
    // Full simulation
    // -------------------------------------------------------------------------

    public static void tick(ServerLevel level, SmartVillage village, long gameTick) {
        if (gameTick % TICK_INTERVAL != 0) return;
        for (Villager fletcher : FarmerSystem.findVillagers(level, village, PROF_FLETCHER)) {
            tickFletcher(level, village, fletcher, gameTick);
        }
    }

    private static void tickFletcher(ServerLevel level, SmartVillage village,
                                      Villager fletcher, long gameTick) {
        if (!DayNightCycle.isResourceGatheringAllowed(level)) return;

        UUID id = fletcher.getUUID();
        VillageStockpile stockpile = village.getStockpile();

        if (!isAtWorkArea(fletcher, village)) {
            fletcher.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                new WalkTarget(village.getAnchor(), MOVE_SPEED, 2));
            return;
        }

        // Subrole: post material requests when supply is running low.
        maybePostMaterialRequests(village, stockpile, id, gameTick);

        // Primary role: craft arrows up to buffer.
        long lastCraft = lastCraftTick.getOrDefault(id, 0L);
        if (gameTick - lastCraft >= CRAFT_INTERVAL) {
            craftArrows(stockpile);
            lastCraftTick.put(id, gameTick);
            LOGGER.debug("[SmartVillager] Fletcher {} craft cycle (village {})", id, village.getAnchor());
        }
    }

    // -------------------------------------------------------------------------
    // Abstract simulation
    // -------------------------------------------------------------------------

    public static void abstractTick(SmartVillage village) {
        int count = village.countProfession(PROF_FLETCHER);
        if (count == 0) return;

        VillageStockpile stockpile = village.getStockpile();

        for (int i = 0; i < count; i++) {
            for (int r = 0; r < BATCH_CRAFT_ROUNDS; r++) {
                craftArrows(stockpile);
            }
        }

        LOGGER.debug("[SmartVillager] Abstract Fletcher: {} fletchers ran (village {})",
            count, village.getAnchor());
    }

    // -------------------------------------------------------------------------
    // Crafting
    // -------------------------------------------------------------------------

    private static void craftArrows(VillageStockpile stockpile) {
        if (stockpile.getCount(ARROW) >= ARROW_BUFFER) return;
        if (!stockpile.hasEnough(OAK_LOG, CRAFT_LOG_COST)) return;
        if (!stockpile.hasEnough(FEATHER, CRAFT_FEATHER_COST)) return;
        if (!stockpile.hasEnough(FLINT, CRAFT_FLINT_COST)) return;

        stockpile.withdraw(OAK_LOG, CRAFT_LOG_COST);
        stockpile.withdraw(FEATHER, CRAFT_FEATHER_COST);
        stockpile.withdraw(FLINT,   CRAFT_FLINT_COST);
        stockpile.deposit(ARROW, CRAFT_ARROW_OUTPUT);
    }

    // -------------------------------------------------------------------------
    // Material request subrole
    // -------------------------------------------------------------------------

    private static void maybePostMaterialRequests(SmartVillage village, VillageStockpile stockpile,
                                                   UUID posterId, long gameTick) {
        if (stockpile.getCount(FEATHER) < FEATHER_LOW_THRESHOLD
                && !village.getNeedQueue().hasOpenRequest(NeedTypes.NEED_MATERIALS, Optional.of(FEATHER))) {
            NeedQueue.postRequest(village, NeedTypes.NEED_MATERIALS, NeedPriority.NORMAL,
                posterId, Optional.of(FEATHER), gameTick);
            LOGGER.info("[SmartVillager] Fletcher posted NEED_MATERIALS:feather — supply low (village {})",
                village.getAnchor());
        }
        if (stockpile.getCount(FLINT) < FLINT_LOW_THRESHOLD
                && !village.getNeedQueue().hasOpenRequest(NeedTypes.NEED_MATERIALS, Optional.of(FLINT))) {
            NeedQueue.postRequest(village, NeedTypes.NEED_MATERIALS, NeedPriority.NORMAL,
                posterId, Optional.of(FLINT), gameTick);
            LOGGER.info("[SmartVillager] Fletcher posted NEED_MATERIALS:flint — supply low (village {})",
                village.getAnchor());
        }
    }

    // -------------------------------------------------------------------------
    // Utility
    // -------------------------------------------------------------------------

    private static boolean isAtWorkArea(Villager v, SmartVillage village) {
        return v.distanceToSqr(
            village.getAnchor().getX(),
            village.getAnchor().getY(),
            village.getAnchor().getZ()) <= WORK_RANGE_SQ;
    }
}
