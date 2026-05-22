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
 * Drives Weaponsmith weapon crafting and ore smelting subrole.
 *
 * Full simulation: Weaponsmiths walk to the anchor area and smelt raw ore into
 * ingots (subrole), then craft iron swords and axes up to the stockpile buffer.
 * Smelting runs first so ingots are available for immediate crafting.
 *
 * Abstract simulation: Smelt available ore, then craft weapons up to buffer.
 *
 * Smelting coordination: Weaponsmith smelt triggers at lower ingot threshold
 * than Armorer, giving Weaponsmith first access to ore. Armorer only helps
 * smelt when ore supply is abundant (see ArmorerSystem).
 */
public final class WeaponsmithSystem {
    private WeaponsmithSystem() {}

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final Identifier PROF_WEAPONSMITH    = Identifier.withDefaultNamespace("weaponsmith");

    static final Identifier IRON_ORE            = Identifier.withDefaultNamespace("iron_ore");
    static final Identifier DEEPSLATE_IRON_ORE  = Identifier.withDefaultNamespace("deepslate_iron_ore");
    static final Identifier RAW_IRON            = Identifier.withDefaultNamespace("raw_iron");
    static final Identifier COAL                = Identifier.withDefaultNamespace("coal");
    static final Identifier IRON_INGOT          = Identifier.withDefaultNamespace("iron_ingot");

    private static final Identifier IRON_SWORD  = Identifier.withDefaultNamespace("iron_sword");
    private static final Identifier IRON_AXE    = Identifier.withDefaultNamespace("iron_axe");

    /** Items smelted per coal (vanilla furnace efficiency). */
    static final int SMELT_PER_COAL = 8;
    /** Max ore withdrawn per smelt cycle. */
    private static final int SMELT_BATCH = 8;

    /** Ingot cost per iron sword (2 ingots; stick cost abstracted away). */
    private static final int SWORD_INGOT_COST = 2;
    /** Ingot cost per iron axe (3 ingots; stick cost abstracted away). */
    private static final int AXE_INGOT_COST = 3;

    /** Target stockpile buffer for swords before halting production. */
    private static final int SWORD_BUFFER = 4;
    /** Target stockpile buffer for axes before halting production. */
    private static final int AXE_BUFFER   = 4;

    /** Ingot count below which the smelt subrole activates. Armorer uses a higher threshold. */
    static final int INGOT_SMELT_THRESHOLD = 32;

    /** Ticks between smelt cycles. */
    private static final long SMELT_INTERVAL = 200L;
    /** Ticks between weapon craft cycles. */
    private static final long CRAFT_INTERVAL = 300L;

    /** Max ore smelted per weaponsmith per abstract batch. */
    private static final int BATCH_MAX_SMELT = 16;
    /** Weapon craft attempts per weaponsmith per abstract batch. */
    private static final int BATCH_CRAFT_ROUNDS = 2;

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
        for (Villager ws : FarmerSystem.findVillagers(level, village, PROF_WEAPONSMITH)) {
            tickWeaponsmith(level, village, ws, gameTick);
        }
    }

    private static void tickWeaponsmith(ServerLevel level, SmartVillage village,
                                         Villager ws, long gameTick) {
        if (!DayNightCycle.isResourceGatheringAllowed(level)) return;

        UUID id = ws.getUUID();
        VillageStockpile stockpile = village.getStockpile();

        if (!isAtWorkArea(ws, village)) {
            ws.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                new WalkTarget(village.getAnchor(), MOVE_SPEED, 2));
            return;
        }

        // Smelt subrole: build up ingot supply before crafting.
        long lastSmelt = lastSmeltTick.getOrDefault(id, 0L);
        if (gameTick - lastSmelt >= SMELT_INTERVAL
                && stockpile.getCount(IRON_INGOT) < INGOT_SMELT_THRESHOLD) {
            smeltAvailableOre(stockpile, SMELT_BATCH);
            lastSmeltTick.put(id, gameTick);
        }

        // Primary role: craft weapons up to buffer.
        long lastCraft = lastCraftTick.getOrDefault(id, 0L);
        if (gameTick - lastCraft >= CRAFT_INTERVAL) {
            craftWeapons(stockpile);
            lastCraftTick.put(id, gameTick);
            LOGGER.debug("[SmartVillager] Weaponsmith {} craft cycle (village {})", id, village.getAnchor());
        }
    }

    // -------------------------------------------------------------------------
    // Abstract simulation
    // -------------------------------------------------------------------------

    public static void abstractTick(SmartVillage village) {
        int count = village.countProfession(PROF_WEAPONSMITH);
        if (count == 0) return;

        VillageStockpile stockpile = village.getStockpile();

        for (int i = 0; i < count; i++) {
            if (stockpile.getCount(IRON_INGOT) < INGOT_SMELT_THRESHOLD) {
                smeltAvailableOre(stockpile, BATCH_MAX_SMELT);
            }
        }

        for (int i = 0; i < count; i++) {
            for (int r = 0; r < BATCH_CRAFT_ROUNDS; r++) {
                craftWeapons(stockpile);
            }
        }

        LOGGER.debug("[SmartVillager] Abstract Weaponsmith: {} smiths ran (village {})",
            count, village.getAnchor());
    }

    // -------------------------------------------------------------------------
    // Shared smelting helper — also called by ArmorerSystem
    // -------------------------------------------------------------------------

    /**
     * Attempts to smelt up to {@code maxPerType} of each ore type into iron ingots.
     * Consumes coal; does nothing if coal is unavailable.
     */
    static void smeltAvailableOre(VillageStockpile stockpile, int maxPerType) {
        smeltOneType(stockpile, IRON_ORE,           maxPerType);
        smeltOneType(stockpile, DEEPSLATE_IRON_ORE, maxPerType);
        smeltOneType(stockpile, RAW_IRON,           maxPerType);
    }

    private static void smeltOneType(VillageStockpile stockpile, Identifier oreId, int max) {
        if (!stockpile.hasEnough(COAL, 1)) return;
        int ore = stockpile.withdraw(oreId, max);
        if (ore == 0) return;
        int coalCost = Math.max(1, ore / SMELT_PER_COAL);
        int coalTaken = stockpile.withdraw(COAL, coalCost);
        if (coalTaken == 0) {
            stockpile.deposit(oreId, ore);
            return;
        }
        stockpile.deposit(IRON_INGOT, ore);
    }

    // -------------------------------------------------------------------------
    // Crafting
    // -------------------------------------------------------------------------

    private static void craftWeapons(VillageStockpile stockpile) {
        if (stockpile.getCount(IRON_SWORD) < SWORD_BUFFER
                && stockpile.hasEnough(IRON_INGOT, SWORD_INGOT_COST)) {
            stockpile.withdraw(IRON_INGOT, SWORD_INGOT_COST);
            stockpile.deposit(IRON_SWORD, 1);
        }
        if (stockpile.getCount(IRON_AXE) < AXE_BUFFER
                && stockpile.hasEnough(IRON_INGOT, AXE_INGOT_COST)) {
            stockpile.withdraw(IRON_INGOT, AXE_INGOT_COST);
            stockpile.deposit(IRON_AXE, 1);
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
