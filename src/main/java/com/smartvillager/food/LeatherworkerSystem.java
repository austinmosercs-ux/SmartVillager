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
 * Drives Leatherworker leather processing into early-tier armor for Guards.
 *
 * Full simulation: Leatherworkers walk to the anchor area and convert leather
 * from the stockpile into armor pieces on a timer. When leather supply is
 * exhausted they idle in place until Shepherd restocks it.
 *
 * Abstract simulation: Batch leather-to-armor conversion from roster count.
 * Armor pieces are distributed evenly across the four slots so Guards receive
 * complete sets over time.
 */
public final class LeatherworkerSystem {
    private LeatherworkerSystem() {}

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final Identifier PROF_LEATHERWORKER = Identifier.withDefaultNamespace("leatherworker");

    private static final Identifier LEATHER            = Identifier.withDefaultNamespace("leather");
    private static final Identifier LEATHER_HELMET     = Identifier.withDefaultNamespace("leather_helmet");
    private static final Identifier LEATHER_CHESTPLATE = Identifier.withDefaultNamespace("leather_chestplate");
    private static final Identifier LEATHER_LEGGINGS   = Identifier.withDefaultNamespace("leather_leggings");
    private static final Identifier LEATHER_BOOTS      = Identifier.withDefaultNamespace("leather_boots");

    /** Leather consumed per craft cycle — yields one chestplate + one boots. */
    private static final int CRAFT_LEATHER_COST = 4;
    /** Ticks between craft cycles. */
    private static final long CRAFT_INTERVAL    = 300L;

    private static final float MOVE_SPEED    = 0.5f;
    private static final double WORK_RANGE_SQ = 16.0;

    /**
     * Per abstract-batch leather attempted per leatherworker.
     * At 4 leather/piece and ~3 day-time cycles: 12 leather → 3 armor pieces.
     */
    private static final int BATCH_LEATHER_WITHDRAW = 12;

    public static final int TICK_INTERVAL = 20;

    private static final Map<UUID, Long> lastCraftTick = new HashMap<>();

    // -------------------------------------------------------------------------
    // Full simulation
    // -------------------------------------------------------------------------

    public static void tick(ServerLevel level, SmartVillage village, long gameTick) {
        if (gameTick % TICK_INTERVAL != 0) return;
        for (Villager lw : FarmerSystem.findVillagers(level, village, PROF_LEATHERWORKER)) {
            tickLeatherworker(level, village, lw, gameTick);
        }
    }

    private static void tickLeatherworker(ServerLevel level, SmartVillage village,
                                           Villager lw, long gameTick) {
        if (!DayNightCycle.isResourceGatheringAllowed(level)) return;

        UUID id = lw.getUUID();
        VillageStockpile stockpile = village.getStockpile();

        if (!isAtWorkArea(lw, village)) {
            lw.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                new WalkTarget(village.getAnchor(), MOVE_SPEED, 2));
            return;
        }

        long last = lastCraftTick.getOrDefault(id, 0L);
        if (gameTick - last < CRAFT_INTERVAL) return;
        if (!stockpile.hasEnough(LEATHER, CRAFT_LEATHER_COST)) return;

        stockpile.withdraw(LEATHER, CRAFT_LEATHER_COST);
        // Alternate between chestplate+boots and helmet+leggings each cycle.
        long cycleIndex = (gameTick / CRAFT_INTERVAL) % 2;
        if (cycleIndex == 0) {
            stockpile.deposit(LEATHER_CHESTPLATE, 1);
            stockpile.deposit(LEATHER_BOOTS,      1);
        } else {
            stockpile.deposit(LEATHER_HELMET,   1);
            stockpile.deposit(LEATHER_LEGGINGS, 1);
        }
        lastCraftTick.put(id, gameTick);
        LOGGER.debug("[SmartVillager] Leatherworker {} crafted leather armor (village {})", id, village.getAnchor());
    }

    // -------------------------------------------------------------------------
    // Abstract simulation
    // -------------------------------------------------------------------------

    public static void abstractTick(SmartVillage village) {
        int count = village.countProfession(PROF_LEATHERWORKER);
        if (count == 0) return;

        VillageStockpile stockpile = village.getStockpile();
        int attempted = count * BATCH_LEATHER_WITHDRAW;
        int taken = stockpile.withdraw(LEATHER, attempted);
        int pieces = taken / CRAFT_LEATHER_COST;

        // Return any leather not used by a complete craft cycle.
        int remainder = taken - (pieces * CRAFT_LEATHER_COST);
        if (remainder > 0) stockpile.deposit(LEATHER, remainder);
        if (pieces == 0) return;

        // Distribute evenly across all four armor slots.
        stockpile.deposit(LEATHER_HELMET,     (pieces + 3) / 4);
        stockpile.deposit(LEATHER_CHESTPLATE, (pieces + 2) / 4);
        stockpile.deposit(LEATHER_LEGGINGS,   (pieces + 1) / 4);
        stockpile.deposit(LEATHER_BOOTS,      pieces / 4);

        LOGGER.debug("[SmartVillager] Abstract Leatherworker: {} crafted {} armor pieces (village {})",
            count, pieces, village.getAnchor());
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
