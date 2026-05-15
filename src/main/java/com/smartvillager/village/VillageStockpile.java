package com.smartvillager.village;

import com.mojang.serialization.Codec;
import net.minecraft.resources.Identifier;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Shared item inventory for a village. All villagers deposit and withdraw here
 * based on their role. The Librarian (via LibrarianCoordinator) monitors supply
 * levels and flags shortages so the NeedQueue can post job requests.
 *
 * Thread note: all access is server-thread-only; no synchronization needed.
 */
public final class VillageStockpile {

    public static final Codec<VillageStockpile> CODEC =
        Codec.unboundedMap(Identifier.CODEC, Codec.INT)
            .xmap(VillageStockpile::fromMap, s -> Collections.unmodifiableMap(s.items));

    private final Map<Identifier, Integer> items;

    public VillageStockpile() {
        this.items = new HashMap<>();
    }

    private static VillageStockpile fromMap(Map<Identifier, Integer> map) {
        VillageStockpile s = new VillageStockpile();
        map.forEach((id, count) -> {
            if (count > 0) s.items.put(id, count);
        });
        return s;
    }

    public void deposit(Identifier itemId, int count) {
        if (count <= 0) return;
        items.merge(itemId, count, Integer::sum);
    }

    /**
     * Withdraws up to {@code count} of the given item.
     *
     * @return the amount actually withdrawn (may be less than requested)
     */
    public int withdraw(Identifier itemId, int count) {
        if (count <= 0) return 0;
        int current = items.getOrDefault(itemId, 0);
        int taken = Math.min(current, count);
        if (taken <= 0) return 0;
        int remaining = current - taken;
        if (remaining == 0) items.remove(itemId);
        else items.put(itemId, remaining);
        return taken;
    }

    public int getCount(Identifier itemId) {
        return items.getOrDefault(itemId, 0);
    }

    public boolean hasEnough(Identifier itemId, int count) {
        return getCount(itemId) >= count;
    }

    /** Sum of counts for all item IDs in the given set. Used for category-level thresholds. */
    public int totalOf(Set<Identifier> itemIds) {
        int total = 0;
        for (Identifier id : itemIds) {
            total += items.getOrDefault(id, 0);
        }
        return total;
    }

    /** Unmodifiable snapshot for external scanning (LibrarianCoordinator, Merchant UI). */
    public Map<Identifier, Integer> snapshot() {
        return Collections.unmodifiableMap(items);
    }
}
