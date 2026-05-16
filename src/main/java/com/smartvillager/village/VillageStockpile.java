package com.smartvillager.village;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Shared item inventory for a village.
 *
 * Full simulation (chunks loaded, player nearby):
 *   deposit/withdraw/getCount all route to the physical ChestBlockEntity tiles
 *   tracked by the StockpileChestTracker. Virtual items (those with no
 *   corresponding MC Item registration, e.g. smartvillager:healing_supply) are
 *   kept in the snapshot map even during full sim.
 *
 * Abstract simulation (chunks unloaded):
 *   All operations use the snapshot map only. On transition to abstract the
 *   snapshot is populated from the physical chests; on transition back to full
 *   the snapshot is reconciled into the physical chests.
 *
 * Codec:
 *   Serializes the snapshot map to disk. Virtual items always live here; real
 *   items are present when abstract, cleared when full sim begins and replaced
 *   by physical chest contents on the next abstract transition.
 */
public final class VillageStockpile {

    public static final Codec<VillageStockpile> CODEC =
        Codec.unboundedMap(Identifier.CODEC, Codec.INT)
            .xmap(VillageStockpile::fromMap, s -> Collections.unmodifiableMap(s.snapshotItems));

    // Snapshot map — always authoritative for virtual items; authoritative for
    // real items only during abstract simulation.
    private final Map<Identifier, Integer> snapshotItems;

    // Non-null only during full simulation.
    @Nullable private ServerLevel activeLevel;
    @Nullable private StockpileChestTracker tracker;

    public VillageStockpile() {
        this.snapshotItems = new HashMap<>();
    }

    private static VillageStockpile fromMap(Map<Identifier, Integer> map) {
        VillageStockpile s = new VillageStockpile();
        map.forEach((id, count) -> {
            if (count > 0) s.snapshotItems.put(id, count);
        });
        return s;
    }

    // -------------------------------------------------------------------------
    // Simulation mode transitions (called by SmartVillage)
    // -------------------------------------------------------------------------

    /**
     * Called when the village switches to FULL simulation.
     * Reconciles snapshot items into the physical chests, then activates
     * chest-based I/O for all subsequent calls.
     */
    public void activateChests(ServerLevel level, StockpileChestTracker chestTracker) {
        this.activeLevel = level;
        this.tracker = chestTracker;
        if (!chestTracker.isEmpty()) {
            reconcileSnapshotToChests(level, chestTracker);
        }
    }

    /**
     * Called when the village switches to ABSTRACT simulation.
     * Reads physical chest contents into the snapshot map, then disables
     * chest-based I/O.
     */
    public void deactivateChests(ServerLevel level, StockpileChestTracker chestTracker) {
        if (!chestTracker.isEmpty()) {
            snapshotChestsToMap(level, chestTracker);
        }
        this.activeLevel = null;
        this.tracker = null;
    }

    // -------------------------------------------------------------------------
    // Public API — same signatures as before; routing is transparent to callers
    // -------------------------------------------------------------------------

    public void deposit(Identifier itemId, int count) {
        if (count <= 0) return;
        if (shouldUseChests(itemId)) {
            depositToChests(itemId, count);
        } else {
            snapshotItems.merge(itemId, count, Integer::sum);
        }
    }

    /**
     * Withdraws up to {@code count} of the given item.
     *
     * @return the amount actually withdrawn (may be less than requested)
     */
    public int withdraw(Identifier itemId, int count) {
        if (count <= 0) return 0;
        if (shouldUseChests(itemId)) {
            return withdrawFromChests(itemId, count);
        }
        return withdrawFromSnapshot(itemId, count);
    }

    public int getCount(Identifier itemId) {
        if (shouldUseChests(itemId)) {
            return countInChests(itemId);
        }
        return snapshotItems.getOrDefault(itemId, 0);
    }

    public boolean hasEnough(Identifier itemId, int count) {
        return getCount(itemId) >= count;
    }

    /** Sum of counts for all item IDs in the given set. Used for category-level thresholds. */
    public int totalOf(Set<Identifier> itemIds) {
        int total = 0;
        for (Identifier id : itemIds) {
            total += getCount(id);
        }
        return total;
    }

    /**
     * Returns a combined view of all items: physical chest contents (when active)
     * merged with virtual items from the snapshot map.
     */
    public Map<Identifier, Integer> snapshot() {
        if (activeLevel != null && tracker != null && !tracker.isEmpty()) {
            Map<Identifier, Integer> combined = new HashMap<>();
            // Virtual items from snapshot.
            for (Map.Entry<Identifier, Integer> e : snapshotItems.entrySet()) {
                if (!isRealItem(e.getKey()) && e.getValue() > 0) {
                    combined.put(e.getKey(), e.getValue());
                }
            }
            // Real items from physical chests.
            for (BlockPos pos : tracker.positions()) {
                ChestBlockEntity chest = getChest(pos);
                if (chest != null) aggregateChestIntoMap(chest, combined);
            }
            return Collections.unmodifiableMap(combined);
        }
        return Collections.unmodifiableMap(snapshotItems);
    }

    // -------------------------------------------------------------------------
    // Routing helpers
    // -------------------------------------------------------------------------

    private boolean shouldUseChests(Identifier itemId) {
        return activeLevel != null && tracker != null
            && !tracker.isEmpty() && isRealItem(itemId);
    }

    private void depositToChests(Identifier itemId, int count) {
        int remaining = count;
        for (BlockPos pos : tracker.positions()) {
            ChestBlockEntity chest = getChest(pos);
            if (chest == null) continue;
            remaining -= insertIntoChest(chest, itemId, remaining);
            if (remaining <= 0) break;
        }
        // Overflow that didn't fit in any chest falls back to the snapshot map.
        if (remaining > 0) {
            snapshotItems.merge(itemId, remaining, Integer::sum);
        }
    }

    private int withdrawFromChests(Identifier itemId, int count) {
        int remaining = count;
        for (BlockPos pos : tracker.positions()) {
            ChestBlockEntity chest = getChest(pos);
            if (chest == null) continue;
            remaining -= removeFromChest(chest, itemId, remaining);
            if (remaining <= 0) break;
        }
        return count - remaining;
    }

    private int countInChests(Identifier itemId) {
        int total = 0;
        for (BlockPos pos : tracker.positions()) {
            ChestBlockEntity chest = getChest(pos);
            if (chest != null) total += countInContainer(chest, itemId);
        }
        return total;
    }

    private int withdrawFromSnapshot(Identifier itemId, int count) {
        int current = snapshotItems.getOrDefault(itemId, 0);
        int taken = Math.min(current, count);
        if (taken <= 0) return 0;
        int remaining = current - taken;
        if (remaining == 0) snapshotItems.remove(itemId);
        else snapshotItems.put(itemId, remaining);
        return taken;
    }

    @Nullable
    private ChestBlockEntity getChest(BlockPos pos) {
        if (activeLevel == null) return null;
        return activeLevel.getBlockEntity(pos) instanceof ChestBlockEntity c ? c : null;
    }

    // -------------------------------------------------------------------------
    // Container-level operations
    // -------------------------------------------------------------------------

    private static int countInContainer(Container container, Identifier itemId) {
        int total = 0;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (!stack.isEmpty() && matchesId(stack, itemId)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    /** Inserts up to {@code count} items into a chest. Returns the count actually inserted. */
    private static int insertIntoChest(Container chest, Identifier itemId, int count) {
        Item item = resolveItem(itemId);
        if (item == Items.AIR) return 0;

        int inserted = 0;
        int remaining = count;

        // Pass 1: merge into existing partial stacks.
        for (int slot = 0; slot < chest.getContainerSize() && remaining > 0; slot++) {
            ItemStack stack = chest.getItem(slot);
            if (!stack.isEmpty() && stack.is(item)) {
                int room = stack.getMaxStackSize() - stack.getCount();
                int add = Math.min(room, remaining);
                if (add > 0) {
                    chest.setItem(slot, new ItemStack(item, stack.getCount() + add));
                    inserted += add;
                    remaining -= add;
                }
            }
        }

        // Pass 2: fill empty slots.
        int maxStack = new ItemStack(item).getMaxStackSize();
        for (int slot = 0; slot < chest.getContainerSize() && remaining > 0; slot++) {
            if (chest.getItem(slot).isEmpty()) {
                int stackSize = Math.min(remaining, maxStack);
                chest.setItem(slot, new ItemStack(item, stackSize));
                inserted += stackSize;
                remaining -= stackSize;
            }
        }

        return inserted;
    }

    /** Removes up to {@code count} matching items from a chest. Returns removed count. */
    private static int removeFromChest(Container chest, Identifier itemId, int count) {
        int removed = 0;
        int remaining = count;
        for (int slot = 0; slot < chest.getContainerSize() && remaining > 0; slot++) {
            ItemStack stack = chest.getItem(slot);
            if (!stack.isEmpty() && matchesId(stack, itemId)) {
                int take = Math.min(stack.getCount(), remaining);
                chest.removeItem(slot, take);
                removed += take;
                remaining -= take;
            }
        }
        return removed;
    }

    // -------------------------------------------------------------------------
    // Snapshot ↔ chest transitions
    // -------------------------------------------------------------------------

    private void snapshotChestsToMap(ServerLevel level, StockpileChestTracker chestTracker) {
        // Replace the real-item portion of the snapshot with current chest contents.
        snapshotItems.keySet().removeIf(VillageStockpile::isRealItem);
        for (BlockPos pos : chestTracker.positions()) {
            if (level.getBlockEntity(pos) instanceof ChestBlockEntity chest) {
                aggregateChestIntoMap(chest, snapshotItems);
            }
        }
    }

    private void reconcileSnapshotToChests(ServerLevel level, StockpileChestTracker chestTracker) {
        // Clear all chest contents.
        for (BlockPos pos : chestTracker.positions()) {
            if (level.getBlockEntity(pos) instanceof ChestBlockEntity chest) {
                chest.clearContent();
            }
        }

        // Collect real items from snapshot to distribute into chests.
        Map<Identifier, Integer> toDistribute = new HashMap<>();
        for (Map.Entry<Identifier, Integer> e : snapshotItems.entrySet()) {
            if (isRealItem(e.getKey()) && e.getValue() > 0) {
                toDistribute.put(e.getKey(), e.getValue());
            }
        }
        snapshotItems.keySet().removeIf(VillageStockpile::isRealItem);

        for (Map.Entry<Identifier, Integer> e : toDistribute.entrySet()) {
            int overflow = distributeIntoChests(level, chestTracker, e.getKey(), e.getValue());
            // Overflow that didn't fit (chests full) stays in snapshot as a fallback.
            if (overflow > 0) snapshotItems.merge(e.getKey(), overflow, Integer::sum);
        }
    }

    /** Deposits {@code count} of {@code itemId} into chests; returns the unfitted remainder. */
    private static int distributeIntoChests(ServerLevel level, StockpileChestTracker chestTracker,
                                             Identifier itemId, int count) {
        int remaining = count;
        for (BlockPos pos : chestTracker.positions()) {
            if (remaining <= 0) break;
            if (level.getBlockEntity(pos) instanceof ChestBlockEntity chest) {
                remaining -= insertIntoChest(chest, itemId, remaining);
            }
        }
        return remaining;
    }

    private static void aggregateChestIntoMap(Container chest, Map<Identifier, Integer> map) {
        for (int slot = 0; slot < chest.getContainerSize(); slot++) {
            ItemStack stack = chest.getItem(slot);
            if (stack.isEmpty()) continue;
            Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (id != null) map.merge(id, stack.getCount(), Integer::sum);
        }
    }

    // -------------------------------------------------------------------------
    // Registry utilities
    // -------------------------------------------------------------------------

    private static boolean isRealItem(Identifier itemId) {
        return BuiltInRegistries.ITEM.containsKey(itemId);
    }

    private static Item resolveItem(Identifier itemId) {
        return BuiltInRegistries.ITEM.getOptional(itemId)
            .map(net.minecraft.core.Holder::value)
            .orElse(Items.AIR);
    }

    private static boolean matchesId(ItemStack stack, Identifier itemId) {
        return itemId.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }
}
