package com.smartvillager.memory;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Persistent village memory: threat locations and villager death sites.
 *
 * Stored on SmartVillage and persisted via CODEC.
 *
 * Consumers:
 *   - GuardDefenseSystem  — records new ThreatRecords after each fight
 *   - MasonSystem         — skips quarry positions that are in active threat areas
 *   - CartographerSystem  — marks scouted areas; feeds threat map to Guards
 *   - EscortSystem        — will check whether a route passes flagged areas (future)
 *
 * Threat records expire after THREAT_EXPIRY_TICKS of game time (~4 in-game days).
 * Cleared records are kept briefly so other systems know "this area was dangerous
 * but a Guard confirmed it safe" — they are pruned after CLEARED_EXPIRY_TICKS.
 */
public final class VillageMemory {

    /** Threat records remain active for 96 000 ticks (~4 in-game days). */
    private static final long THREAT_EXPIRY_TICKS  = 96_000L;

    /** Cleared records are pruned after an additional day. */
    private static final long CLEARED_EXPIRY_TICKS = 24_000L;

    /** How close (blocks) to a threat position counts as "in the flagged area". */
    public static final int THREAT_RADIUS = 24;

    public static final Codec<VillageMemory> CODEC = RecordCodecBuilder.create(i -> i.group(
        ThreatRecord.CODEC.listOf()
            .optionalFieldOf("threats", List.of())
            .forGetter(m -> List.copyOf(m.threats)),
        BlockPos.CODEC.listOf()
            .optionalFieldOf("death_sites", List.of())
            .forGetter(m -> List.copyOf(m.deathSites))
    ).apply(i, (t, d) -> new VillageMemory(new ArrayList<>(t), new ArrayList<>(d))));

    private final List<ThreatRecord> threats;
    private final List<BlockPos>     deathSites;

    public VillageMemory() {
        this.threats   = new ArrayList<>();
        this.deathSites = new ArrayList<>();
    }

    private VillageMemory(List<ThreatRecord> threats, List<BlockPos> deathSites) {
        this.threats    = threats;
        this.deathSites = deathSites;
    }

    // -------------------------------------------------------------------------
    // Threat recording
    // -------------------------------------------------------------------------

    /** Record a new threat at the given position. De-duplicates within THREAT_RADIUS. */
    public void recordThreat(BlockPos pos, long gameTick) {
        boolean nearby = threats.stream()
            .anyMatch(r -> !r.cleared() && r.pos().distSqr(pos) < (long) THREAT_RADIUS * THREAT_RADIUS);
        if (!nearby) {
            threats.add(new ThreatRecord(pos, gameTick, false));
        }
    }

    /** Mark all active threat records within THREAT_RADIUS of pos as cleared. */
    public void clearThreatNear(BlockPos pos) {
        threats.replaceAll(r -> {
            if (!r.cleared() && r.pos().distSqr(pos) < (long) THREAT_RADIUS * THREAT_RADIUS) {
                return r.withCleared();
            }
            return r;
        });
    }

    /** True if there is an active (uncleared, not expired) threat within THREAT_RADIUS of pos. */
    public boolean isFlagged(BlockPos pos, long currentTick) {
        return threats.stream().anyMatch(r ->
            r.isActive(currentTick, THREAT_EXPIRY_TICKS)
                && r.pos().distSqr(pos) < (long) THREAT_RADIUS * THREAT_RADIUS);
    }

    // -------------------------------------------------------------------------
    // Death site recording
    // -------------------------------------------------------------------------

    public void recordDeathSite(BlockPos pos) {
        if (!deathSites.contains(pos)) {
            deathSites.add(pos);
        }
    }

    public List<BlockPos> deathSites() {
        return Collections.unmodifiableList(deathSites);
    }

    // -------------------------------------------------------------------------
    // Maintenance — prune expired records
    // -------------------------------------------------------------------------

    public void prune(long currentTick) {
        threats.removeIf(r -> {
            if (r.cleared()) return (currentTick - r.gameTick()) > CLEARED_EXPIRY_TICKS;
            return (currentTick - r.gameTick()) > THREAT_EXPIRY_TICKS;
        });
    }

    public List<ThreatRecord> activeThreats(long currentTick) {
        return threats.stream()
            .filter(r -> r.isActive(currentTick, THREAT_EXPIRY_TICKS))
            .toList();
    }

    /** Returns an unmodifiable view of all threat records (active + cleared + expired). */
    public List<ThreatRecord> allThreats() {
        return Collections.unmodifiableList(threats);
    }
}
