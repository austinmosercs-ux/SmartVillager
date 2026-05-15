package com.smartvillager.inventory;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Per-villager personal inventory.
 *
 * Layout:
 *   15 general slots (index 0–14) — food, tools, carried materials
 *    4 armor slots   (ArmorSlot ordinal) — HEAD, CHEST, LEGS, FEET
 *
 * Only non-empty slots are written to disk; armor and items are stored in
 * separate string-keyed maps so slot positions survive serialization.
 *
 * All mutating methods store defensive copies so callers cannot corrupt
 * internal state via the returned or passed-in ItemStack.
 */
public final class VillagerBackpack {

    public static final int ITEM_SLOTS  = 15;
    public static final int ARMOR_SLOTS = 4;

    // Codec serializes only non-empty slots as {slotIndex (string) -> ItemStack} maps.
    // String keys are required by Codec.unboundedMap; we parse them back to int on decode.
    private static final Codec<Map<String, ItemStack>> SLOT_MAP_CODEC =
        Codec.unboundedMap(Codec.STRING, ItemStack.CODEC);

    public static final Codec<VillagerBackpack> CODEC = RecordCodecBuilder.create(i -> i.group(
        SLOT_MAP_CODEC.fieldOf("items").forGetter(VillagerBackpack::itemsToStringMap),
        SLOT_MAP_CODEC.fieldOf("armor").forGetter(VillagerBackpack::armorToStringMap)
    ).apply(i, VillagerBackpack::fromStringMaps));

    private final ItemStack[] items = new ItemStack[ITEM_SLOTS];
    private final ItemStack[] armor = new ItemStack[ARMOR_SLOTS];

    public VillagerBackpack() {
        Arrays.fill(items, ItemStack.EMPTY);
        Arrays.fill(armor, ItemStack.EMPTY);
    }

    private static VillagerBackpack fromStringMaps(Map<String, ItemStack> itemMap,
                                                    Map<String, ItemStack> armorMap) {
        VillagerBackpack b = new VillagerBackpack();
        itemMap.forEach((key, stack) -> {
            try {
                int slot = Integer.parseInt(key);
                if (slot >= 0 && slot < ITEM_SLOTS && !stack.isEmpty()) {
                    b.items[slot] = stack.copy();
                }
            } catch (NumberFormatException ignored) {}
        });
        armorMap.forEach((key, stack) -> {
            try {
                int slot = Integer.parseInt(key);
                if (slot >= 0 && slot < ARMOR_SLOTS && !stack.isEmpty()) {
                    b.armor[slot] = stack.copy();
                }
            } catch (NumberFormatException ignored) {}
        });
        return b;
    }

    private Map<String, ItemStack> itemsToStringMap() {
        Map<String, ItemStack> map = new LinkedHashMap<>();
        for (int i = 0; i < ITEM_SLOTS; i++) {
            if (!items[i].isEmpty()) map.put(String.valueOf(i), items[i]);
        }
        return map;
    }

    private Map<String, ItemStack> armorToStringMap() {
        Map<String, ItemStack> map = new LinkedHashMap<>();
        for (int i = 0; i < ARMOR_SLOTS; i++) {
            if (!armor[i].isEmpty()) map.put(String.valueOf(i), armor[i]);
        }
        return map;
    }

    // -------------------------------------------------------------------------
    // General item slots
    // -------------------------------------------------------------------------

    public ItemStack getItem(int slot) {
        if (slot < 0 || slot >= ITEM_SLOTS) return ItemStack.EMPTY;
        return items[slot].copy();
    }

    public void setItem(int slot, ItemStack stack) {
        if (slot < 0 || slot >= ITEM_SLOTS) return;
        items[slot] = stack.isEmpty() ? ItemStack.EMPTY : stack.copy();
    }

    /**
     * Inserts as many items from {@code incoming} as possible into the first
     * matching partial stack, then into empty slots. Returns the remainder.
     */
    public ItemStack addItem(ItemStack incoming) {
        if (incoming.isEmpty()) return ItemStack.EMPTY;
        ItemStack remainder = incoming.copy();

        // Pass 1 — merge into existing matching stacks
        for (int i = 0; i < ITEM_SLOTS && !remainder.isEmpty(); i++) {
            if (!items[i].isEmpty() && ItemStack.isSameItemSameComponents(items[i], remainder)) {
                int room = items[i].getMaxStackSize() - items[i].getCount();
                if (room > 0) {
                    int take = Math.min(room, remainder.getCount());
                    items[i].grow(take);
                    remainder.shrink(take);
                }
            }
        }

        // Pass 2 — place remainder into empty slots
        for (int i = 0; i < ITEM_SLOTS && !remainder.isEmpty(); i++) {
            if (items[i].isEmpty()) {
                items[i] = remainder.copy();
                remainder = ItemStack.EMPTY;
            }
        }

        return remainder;
    }

    /**
     * Removes up to {@code count} items from the given slot.
     *
     * @return the removed stack (may be smaller than requested if the slot had fewer)
     */
    public ItemStack removeItem(int slot, int count) {
        if (slot < 0 || slot >= ITEM_SLOTS || items[slot].isEmpty()) return ItemStack.EMPTY;
        ItemStack removed = items[slot].split(count);
        if (items[slot].isEmpty()) items[slot] = ItemStack.EMPTY;
        return removed;
    }

    // -------------------------------------------------------------------------
    // Armor slots
    // -------------------------------------------------------------------------

    public ItemStack getArmor(ArmorSlot slot) {
        return armor[slot.ordinal()].copy();
    }

    public void setArmor(ArmorSlot slot, ItemStack stack) {
        armor[slot.ordinal()] = stack.isEmpty() ? ItemStack.EMPTY : stack.copy();
    }

    // -------------------------------------------------------------------------
    // Query helpers
    // -------------------------------------------------------------------------

    /** Total count of the given item across all 15 general slots. */
    public int countOf(Item item) {
        int total = 0;
        for (ItemStack stack : items) {
            if (!stack.isEmpty() && stack.is(item)) total += stack.getCount();
        }
        return total;
    }

    /**
     * Returns the index of the first general slot matching {@code predicate},
     * or {@code -1} if no match is found.
     */
    public int findFirst(Predicate<ItemStack> predicate) {
        for (int i = 0; i < ITEM_SLOTS; i++) {
            if (!items[i].isEmpty() && predicate.test(items[i])) return i;
        }
        return -1;
    }

    /** {@code true} if all general and armor slots are empty. */
    public boolean isEmpty() {
        for (ItemStack s : items) if (!s.isEmpty()) return false;
        for (ItemStack s : armor) if (!s.isEmpty()) return false;
        return true;
    }

    /**
     * {@code true} if every general slot is occupied and each stack is at max
     * size — i.e., no more items can be accepted without merging.
     */
    public boolean isFull() {
        for (ItemStack s : items) {
            if (s.isEmpty() || s.getCount() < s.getMaxStackSize()) return false;
        }
        return true;
    }
}
