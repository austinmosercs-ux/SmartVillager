package com.smartvillager.memory;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;

/**
 * An immutable record of a single hostile threat encounter.
 *
 * Fields:
 *   pos       — block position where the threat was detected
 *   gameTick  — server game tick when the threat was recorded
 *   cleared   — true once a Guard has reported the area safe
 *
 * Persisted in VillageMemory.CODEC as part of SmartVillage.
 */
public record ThreatRecord(BlockPos pos, long gameTick, boolean cleared) {

    public static final Codec<ThreatRecord> CODEC = RecordCodecBuilder.create(i -> i.group(
        BlockPos.CODEC
            .fieldOf("pos")
            .forGetter(ThreatRecord::pos),
        Codec.LONG
            .fieldOf("game_tick")
            .forGetter(ThreatRecord::gameTick),
        Codec.BOOL
            .optionalFieldOf("cleared", false)
            .forGetter(ThreatRecord::cleared)
    ).apply(i, ThreatRecord::new));

    public ThreatRecord withCleared() {
        return new ThreatRecord(pos, gameTick, true);
    }

    /** True if this record is still "active" — uncleared and within the given age limit. */
    public boolean isActive(long currentTick, long maxAgeTicks) {
        return !cleared && (currentTick - gameTick) < maxAgeTicks;
    }
}
