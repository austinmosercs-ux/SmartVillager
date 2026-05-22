package com.smartvillager.supply;

import com.mojang.logging.LogUtils;
import com.smartvillager.daynight.DayNightCycle;
import com.smartvillager.food.FarmerSystem;
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
 * Drives Armorer iron armor crafting and emergency rearm subrole.
 *
 * Full simulation: Armorers walk to the anchor area and craft iron armor pieces
 * (helmet, chestplate, leggings, boots) up to the stockpile buffer. As a subrole,
 * they smelt surplus ore when the stockpile has abundant ore and ingots are low —
 * but only after Weaponsmith has already had a chance to smelt (higher ore threshold).
 *
 * Abstract simulation: Smelt surplus ore as subrole, then craft armor up to buffer.
 *
 * Smelting coordination: The Armorer's smelt subrole requires ore >= ARMORERS_SMELT_ORE_MIN
 * before triggering, giving WeaponsmithSystem priority over scarce ore.
 */
public final class ArmorerSystem {
    private ArmorerSystem() {}

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final Identifier PROF_ARMORER        = Identifier.withDefaultNamespace("armorer");

    private static final Identifier IRON_HELMET         = Identifier.withDefaultNamespace("iron_helmet");
    private static final Identifier IRON_CHESTPLATE     = Identifier.withDefaultNamespace("iron_chestplate");
    private static final Identifier IRON_LEGGINGS       = Identifier.withDefaultNamespace("iron_leggings");
    private static final Identifier IRON_BOOTS          = Identifier.withDefaultNamespace("iron_boots");

    /** Ingot cost per armor piece (vanilla values). */
    private static final int HELMET_COST     = 5;
    private static final int CHESTPLATE_COST = 8;
    private static final int LEGGINGS_COST   = 7;
    private static final int BOOTS_COST      = 4;

    /** Target stockpile buffer for each armor piece before halting production. */
    private static final int ARMOR_BUFFER = 2;

    /**
     * Minimum ore in the stockpile before the Armorer's smelt subrole activates.
     * Higher than Weaponsmith's threshold (which has no minimum ore floor) so
     * Weaponsmith gets first access to scarce ore.
     */
    private static final int ARMORERS_SMELT_ORE_MIN = 24;

    /** Ticks between smelt cycles (offset from Weaponsmith's 200-tick cycle). */
    private static final long SMELT_INTERVAL = 300L;
    /** Ticks between armor craft cycles. */
    private static final long CRAFT_INTERVAL = 400L;

    /** Max ore smelted per armorer per abstract batch. */
    private static final int BATCH_MAX_SMELT = 8;
    /** Armor craft attempts per armorer per abstract batch. */
    private static final int BATCH_CRAFT_ROUNDS = 1;

    public static final int TICK_INTERVAL = 20;

    private static final float  MOVE_SPEED    = 0.5f;
    private static final double WORK_RANGE_SQ = 16.0;

    private static final Map<UUID, Long> lastSmeltTick = new HashMap<>();
    private static final Map<UUID, Long> lastCraftTick = new HashMap<>();

    // -------------------------------------------------------------------------
    // Full simulation
    // -------------------------------------------------------------------------

    public static void tick(ServerLevel level, SmartVillage village, long gameTick) {
        if (gameTick % TICK_INTERVAL != 0) return;
        for (Villager armorer : FarmerSystem.findVillagers(level, village, PROF_ARMORER)) {
            tickArmorer(level, village, armorer, gameTick);
        }
    }

    private static void tickArmorer(ServerLevel level, SmartVillage village,
                                     Villager armorer, long gameTick) {
        if (!DayNightCycle.isResourceGatheringAllowed(level)) return;

        UUID id = armorer.getUUID();
        VillageStockpile stockpile = village.getStockpile();

        if (!isAtWorkArea(armorer, village)) {
            armorer.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                new WalkTarget(village.getAnchor(), MOVE_SPEED, 2));
            return;
        }

        // Smelt subrole: only helps when ore is abundant to avoid competing with Weaponsmith.
        long lastSmelt = lastSmeltTick.getOrDefault(id, 0L);
        if (gameTick - lastSmelt >= SMELT_INTERVAL
                && stockpile.getCount(WeaponsmithSystem.IRON_INGOT) < WeaponsmithSystem.INGOT_SMELT_THRESHOLD
                && totalOre(stockpile) >= ARMORERS_SMELT_ORE_MIN) {
            WeaponsmithSystem.smeltAvailableOre(stockpile, BATCH_MAX_SMELT);
            lastSmeltTick.put(id, gameTick);
        }

        // Primary role: craft armor pieces up to buffer.
        long lastCraft = lastCraftTick.getOrDefault(id, 0L);
        if (gameTick - lastCraft >= CRAFT_INTERVAL) {
            craftArmor(stockpile);
            lastCraftTick.put(id, gameTick);
            LOGGER.debug("[SmartVillager] Armorer {} craft cycle (village {})", id, village.getAnchor());
        }
    }

    // -------------------------------------------------------------------------
    // Abstract simulation
    // -------------------------------------------------------------------------

    public static void abstractTick(SmartVillage village) {
        int count = village.countProfession(PROF_ARMORER);
        if (count == 0) return;

        VillageStockpile stockpile = village.getStockpile();

        // Smelt subrole: only engage when ore is still abundant after Weaponsmith ran.
        for (int i = 0; i < count; i++) {
            if (stockpile.getCount(WeaponsmithSystem.IRON_INGOT) < WeaponsmithSystem.INGOT_SMELT_THRESHOLD
                    && totalOre(stockpile) >= ARMORERS_SMELT_ORE_MIN) {
                WeaponsmithSystem.smeltAvailableOre(stockpile, BATCH_MAX_SMELT);
            }
        }

        // Primary role: craft armor up to buffer.
        for (int i = 0; i < count; i++) {
            for (int r = 0; r < BATCH_CRAFT_ROUNDS; r++) {
                craftArmor(stockpile);
            }
        }

        LOGGER.debug("[SmartVillager] Abstract Armorer: {} armorers ran (village {})",
            count, village.getAnchor());
    }

    // -------------------------------------------------------------------------
    // Crafting
    // -------------------------------------------------------------------------

    private static void craftArmor(VillageStockpile stockpile) {
        tryCraft(stockpile, IRON_HELMET,     HELMET_COST);
        tryCraft(stockpile, IRON_CHESTPLATE, CHESTPLATE_COST);
        tryCraft(stockpile, IRON_LEGGINGS,   LEGGINGS_COST);
        tryCraft(stockpile, IRON_BOOTS,      BOOTS_COST);
    }

    private static void tryCraft(VillageStockpile stockpile, Identifier piece, int ingotCost) {
        if (stockpile.getCount(piece) < ARMOR_BUFFER
                && stockpile.hasEnough(WeaponsmithSystem.IRON_INGOT, ingotCost)) {
            stockpile.withdraw(WeaponsmithSystem.IRON_INGOT, ingotCost);
            stockpile.deposit(piece, 1);
        }
    }

    // -------------------------------------------------------------------------
    // Utility
    // -------------------------------------------------------------------------

    private static int totalOre(VillageStockpile stockpile) {
        return stockpile.getCount(WeaponsmithSystem.IRON_ORE)
             + stockpile.getCount(WeaponsmithSystem.DEEPSLATE_IRON_ORE)
             + stockpile.getCount(WeaponsmithSystem.RAW_IRON);
    }

    private static boolean isAtWorkArea(Villager v, SmartVillage village) {
        return v.distanceToSqr(
            village.getAnchor().getX(),
            village.getAnchor().getY(),
            village.getAnchor().getZ()) <= WORK_RANGE_SQ;
    }
}
