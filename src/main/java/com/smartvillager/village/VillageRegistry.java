package com.smartvillager.village;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Persisted registry of every SmartVillage registered in a ServerLevel.
 *
 * Backed by SavedData so it survives world restarts. Use VillageRegistry.get(level)
 * to obtain the instance for a given ServerLevel.
 *
 * Villages are indexed by two keys:
 *   - UUID  (stable across restarts, used for cross-system references)
 *   - BlockPos anchor (Bell position, used for fast lookup on villager join)
 */
public final class VillageRegistry extends SavedData {

    private static final Codec<VillageRegistry> CODEC =
        SmartVillage.CODEC.listOf()
            .xmap(
                list -> {
                    VillageRegistry r = new VillageRegistry();
                    list.forEach(r::addInternal);
                    return r;
                },
                r -> r.byId.values().stream().toList()
            );

    public static final SavedDataType<VillageRegistry> TYPE = new SavedDataType<>(
        Identifier.withDefaultNamespace("smartvillager_villages"),
        VillageRegistry::new,
        CODEC
    );

    private final Map<UUID, SmartVillage> byId = new HashMap<>();
    private final Map<BlockPos, UUID> byAnchor = new HashMap<>();

    public VillageRegistry() {
        // Required by SavedDataType's no-arg factory for fresh (empty) registry creation.
    }

    // --- static access ---

    public static VillageRegistry get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    // --- mutation ---

    public void register(SmartVillage village) {
        addInternal(village);
        setDirty();
    }

    private void addInternal(SmartVillage village) {
        byId.put(village.getId(), village);
        byAnchor.put(village.getAnchor(), village.getId());
    }

    public void removeVillager(UUID villageId, UUID villagerUUID) {
        SmartVillage village = byId.get(villageId);
        if (village != null) {
            village.removeVillager(villagerUUID);
            setDirty();
        }
    }

    // --- lookup ---

    public boolean isAnchorRegistered(BlockPos anchor) {
        return byAnchor.containsKey(anchor);
    }

    public Optional<SmartVillage> findByAnchor(BlockPos anchor) {
        return Optional.ofNullable(byAnchor.get(anchor)).map(byId::get);
    }

    public Optional<SmartVillage> findById(UUID id) {
        return Optional.ofNullable(byId.get(id));
    }

    /** Returns the village whose anchor is closest to pos within maxDistSq, if any. */
    public Optional<SmartVillage> findNearest(BlockPos pos, double maxDistSq) {
        SmartVillage best = null;
        double bestDist = Double.MAX_VALUE;
        for (SmartVillage v : byId.values()) {
            double d = v.getAnchor().distSqr(pos);
            if (d < maxDistSq && d < bestDist) {
                bestDist = d;
                best = v;
            }
        }
        return Optional.ofNullable(best);
    }

    public Collection<SmartVillage> all() {
        return Collections.unmodifiableCollection(byId.values());
    }
}
