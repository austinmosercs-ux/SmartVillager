package com.smartvillager.village;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.npc.villager.VillagerType;
import net.minecraft.world.item.DyeColor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * All persistent data for a single registered village.
 *
 * Persisted fields (written to disk via VillageRegistry.CODEC):
 *   id               — stable UUID for this village
 *   anchor           — BlockPos of the village Bell (patrol origin for Guards,
 *                      meeting point for all villagers)
 *   villagerTypeId   — Identifier of the VillagerType (desert, plains, etc.);
 *                      determines the Merchant's robe color palette
 *   merchantColor    — DyeColor chosen at registration; fixed for this village
 *   roster           — maps each living villager's entity UUID to the
 *                      Identifier of their assigned profession
 *   lastAbstractUpdate — server game-time tick of the last abstract batch
 *                        update; used to schedule the next one
 *
 * Runtime-only field (NOT persisted):
 *   mode — SimulationMode; always starts as ABSTRACT on load and transitions
 *          to FULL when a player is within range
 */
public final class SmartVillage {

    private static final Codec<Map<UUID, Identifier>> ROSTER_CODEC =
        Codec.unboundedMap(UUIDUtil.STRING_CODEC, Identifier.CODEC);

    public static final Codec<SmartVillage> CODEC = RecordCodecBuilder.create(i -> i.group(
        UUIDUtil.STRING_CODEC
            .fieldOf("id")
            .forGetter(SmartVillage::getId),
        BlockPos.CODEC
            .fieldOf("anchor")
            .forGetter(SmartVillage::getAnchor),
        ResourceKey.<VillagerType>codec(Registries.VILLAGER_TYPE)
            .fieldOf("villager_type")
            .forGetter(SmartVillage::getVillagerTypeKey),
        DyeColor.CODEC
            .fieldOf("merchant_color")
            .forGetter(SmartVillage::getMerchantColor),
        ROSTER_CODEC
            .fieldOf("roster")
            .forGetter(SmartVillage::getRoster),
        Codec.LONG
            .fieldOf("last_abstract_update")
            .forGetter(SmartVillage::getLastAbstractUpdate)
    ).apply(i, SmartVillage::new));

    private final UUID id;
    private final BlockPos anchor;
    private final ResourceKey<VillagerType> villagerTypeKey;
    private final DyeColor merchantColor;
    private final Map<UUID, Identifier> roster;
    private long lastAbstractUpdate;
    private SimulationMode mode = SimulationMode.ABSTRACT;

    public SmartVillage(UUID id, BlockPos anchor, ResourceKey<VillagerType> villagerTypeKey,
                        DyeColor merchantColor, Map<UUID, Identifier> roster, long lastAbstractUpdate) {
        this.id = id;
        this.anchor = anchor;
        this.villagerTypeKey = villagerTypeKey;
        this.merchantColor = merchantColor;
        this.roster = new HashMap<>(roster);
        this.lastAbstractUpdate = lastAbstractUpdate;
    }

    public static SmartVillage create(BlockPos anchor, ResourceKey<VillagerType> typeKey,
                                      RandomSource random, long gameTime) {
        return new SmartVillage(
            UUID.randomUUID(),
            anchor,
            typeKey,
            MerchantColor.randomFor(typeKey, random),
            new HashMap<>(),
            gameTime
        );
    }

    // --- roster management ---

    public boolean hasVillager(UUID villagerUUID) {
        return roster.containsKey(villagerUUID);
    }

    public void assignProfession(UUID villagerUUID, Identifier professionId) {
        roster.put(villagerUUID, professionId);
    }

    public void removeVillager(UUID villagerUUID) {
        roster.remove(villagerUUID);
    }

    public int countProfession(Identifier professionId) {
        int count = 0;
        for (Identifier prof : roster.values()) {
            if (prof.equals(professionId)) count++;
        }
        return count;
    }

    // --- simulation mode ---

    public SimulationMode getMode() {
        return mode;
    }

    public void setMode(SimulationMode mode) {
        this.mode = mode;
    }

    // --- abstract simulation ---

    public long getLastAbstractUpdate() {
        return lastAbstractUpdate;
    }

    public void setLastAbstractUpdate(long tick) {
        this.lastAbstractUpdate = tick;
    }

    // --- getters ---

    public UUID getId()                          { return id; }
    public BlockPos getAnchor()                  { return anchor; }
    public ResourceKey<VillagerType> getVillagerTypeKey() { return villagerTypeKey; }
    public DyeColor getMerchantColor()           { return merchantColor; }
    public Map<UUID, Identifier> getRoster()     { return roster; }
}
