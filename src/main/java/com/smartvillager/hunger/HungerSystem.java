package com.smartvillager.hunger;

import com.smartvillager.registration.ModAttachments;
import com.smartvillager.village.LibrarianCoordinator;
import com.smartvillager.village.SmartVillage;
import com.smartvillager.village.VillageStockpile;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Central logic for the village hunger system.
 *
 * Full simulation: called every TICK_INTERVAL ticks per village. Depletes
 * hunger for each loaded villager and feeds hungry ones from the stockpile.
 *
 * Abstract simulation: called during batch updates for unloaded villages.
 * Approximates how many meals each villager would have needed and withdraws
 * that food from the stockpile.
 *
 * Starvation consequences (health damage when no food is available) are handled
 * by the health system (branch 6). This system only manages the hunger value
 * and stockpile withdrawals.
 */
public final class HungerSystem {
    private HungerSystem() {}

    /** Scan radius (blocks) around the village anchor to find loaded villagers. */
    private static final double SCAN_RADIUS = 192.0;

    // -------------------------------------------------------------------------
    // Full simulation
    // -------------------------------------------------------------------------

    /**
     * Depletes hunger for every registered villager loaded near the anchor and
     * feeds any that are hungry, drawing food from the village stockpile.
     *
     * @param tickInterval ticks elapsed since the last call (caller's check interval)
     */
    public static void tick(ServerLevel level, SmartVillage village, int tickInterval) {
        BlockPos anchor = village.getAnchor();
        double r = SCAN_RADIUS;
        AABB area = new AABB(
            anchor.getX() - r, (double) anchor.getY() - 64, anchor.getZ() - r,
            anchor.getX() + r, (double) anchor.getY() + 64, anchor.getZ() + r
        );

        List<Villager> nearby = level.getEntitiesOfClass(Villager.class, area);
        for (Villager villager : nearby) {
            if (!village.hasVillager(villager.getUUID())) continue;
            tickVillager(villager, village.getStockpile(), tickInterval);
        }
    }

    private static void tickVillager(Villager villager, VillageStockpile stockpile, int tickInterval) {
        Optional<ResourceKey<VillagerProfession>> keyOpt =
            villager.getVillagerData().profession().unwrapKey();
        if (keyOpt.isEmpty()) return;
        Identifier profId = keyOpt.get().identifier();

        float rate = HungerDepletionRates.rateFor(profId);
        VillagerHunger hunger = villager.getData(ModAttachments.VILLAGER_HUNGER);
        hunger.deplete(tickInterval / rate);

        if (hunger.isHungry()) {
            tryFeed(hunger, stockpile);
            // If still hungry after this tick: branch 6 will apply health damage.
        }
    }

    // -------------------------------------------------------------------------
    // Abstract simulation
    // -------------------------------------------------------------------------

    /**
     * Approximates food consumed over an unloaded (abstract) period.
     *
     * For each villager in the roster, computes how many meals they would have
     * needed and withdraws that food from the stockpile. Assumes full starting
     * hunger — this is a simplification until snapshot/reconcile is wired up
     * in the full→abstract and abstract→full transition hooks.
     *
     * @param elapsedTicks ticks since the last abstract batch update
     */
    public static void abstractTick(SmartVillage village, long elapsedTicks) {
        VillageStockpile stockpile = village.getStockpile();
        for (Map.Entry<UUID, Identifier> entry : village.getRoster().entrySet()) {
            int meals = mealsNeeded(entry.getValue(), elapsedTicks);
            for (int i = 0; i < meals; i++) {
                if (!tryWithdrawFood(stockpile)) {
                    // Stockpile ran out — villager starved for the rest of this period.
                    // Branch 6 will apply health damage here.
                    break;
                }
            }
        }
    }

    /**
     * Estimates how many meals a villager would need over {@code elapsedTicks},
     * assuming they start at full hunger (MAX = 20.0).
     *
     * The first meal is needed after (MAX - HUNGRY) = 14 hunger points deplete.
     * Each subsequent meal is needed every HUNGER_PER_MEAL = 6 hunger points.
     */
    static int mealsNeeded(Identifier professionId, long elapsedTicks) {
        float rate = HungerDepletionRates.rateFor(professionId);
        float totalDepleted = elapsedTicks / rate;
        float buffer = VillagerHunger.MAX - VillagerHunger.HUNGRY; // 14.0 before first meal

        if (totalDepleted <= buffer) return 0;
        return 1 + (int) ((totalDepleted - buffer) / VillagerHunger.HUNGER_PER_MEAL);
    }

    // -------------------------------------------------------------------------
    // Shared helpers
    // -------------------------------------------------------------------------

    private static boolean tryFeed(VillagerHunger hunger, VillageStockpile stockpile) {
        if (tryWithdrawFood(stockpile)) {
            hunger.restore(VillagerHunger.HUNGER_PER_MEAL);
            return true;
        }
        return false;
    }

    static boolean tryWithdrawFood(VillageStockpile stockpile) {
        for (Identifier food : LibrarianCoordinator.FOOD_ITEMS) {
            if (stockpile.withdraw(food, 1) > 0) return true;
        }
        return false;
    }
}
