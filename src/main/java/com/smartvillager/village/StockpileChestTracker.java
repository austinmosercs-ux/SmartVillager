package com.smartvillager.village;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Tracks the BlockPos of every physical chest registered as part of the village
 * stockpile network. Serialized through SmartVillage.CODEC so positions survive
 * world restarts.
 *
 * Positions are added at village init and when Mason extends the storehouse.
 * Never write to or read from a chest that is not registered here.
 */
public final class StockpileChestTracker {

    public static final Codec<StockpileChestTracker> CODEC =
        BlockPos.CODEC.listOf()
            .xmap(StockpileChestTracker::new, t -> List.copyOf(t.positions));

    private final List<BlockPos> positions;

    public StockpileChestTracker() {
        this.positions = new ArrayList<>();
    }

    private StockpileChestTracker(List<BlockPos> from) {
        this.positions = new ArrayList<>(from);
    }

    /** Registers a chest position. Silently ignores duplicates. */
    public void register(BlockPos pos) {
        if (!positions.contains(pos)) positions.add(pos);
    }

    /** Removes a chest position (e.g. if the block was broken). */
    public void remove(BlockPos pos) {
        positions.remove(pos);
    }

    public List<BlockPos> positions() {
        return Collections.unmodifiableList(positions);
    }

    public boolean isEmpty() {
        return positions.isEmpty();
    }
}
