package com.smartvillager.registration;

import com.google.common.collect.ImmutableSet;
import com.smartvillager.SmartVillager;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.trading.TradeSet;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModProfessions {
    private ModProfessions() {}

    public static final DeferredRegister<VillagerProfession> PROFESSIONS =
        DeferredRegister.create(Registries.VILLAGER_PROFESSION, SmartVillager.MOD_ID);

    public static final DeferredHolder<VillagerProfession, VillagerProfession> BUILDER =
        PROFESSIONS.register("builder", () -> new VillagerProfession(
            Component.translatable("entity.smartvillager.villager.builder"),
            holder -> holder.is(ModPoiTypes.BUILDER_POI.getKey()),
            holder -> holder.is(ModPoiTypes.BUILDER_POI.getKey()),
            ImmutableSet.of(),
            ImmutableSet.of(),
            SoundEvents.VILLAGER_WORK_TOOLSMITH,
            Int2ObjectMap.<ResourceKey<TradeSet>>ofEntries()));

    public static final DeferredHolder<VillagerProfession, VillagerProfession> MINER =
        PROFESSIONS.register("miner", () -> new VillagerProfession(
            Component.translatable("entity.smartvillager.villager.miner"),
            holder -> holder.is(ModPoiTypes.MINER_POI.getKey()),
            holder -> holder.is(ModPoiTypes.MINER_POI.getKey()),
            ImmutableSet.of(),
            ImmutableSet.of(),
            SoundEvents.VILLAGER_WORK_TOOLSMITH,
            Int2ObjectMap.<ResourceKey<TradeSet>>ofEntries()));

    public static final DeferredHolder<VillagerProfession, VillagerProfession> HUNTER =
        PROFESSIONS.register("hunter", () -> new VillagerProfession(
            Component.translatable("entity.smartvillager.villager.hunter"),
            holder -> holder.is(ModPoiTypes.HUNTER_POI.getKey()),
            holder -> holder.is(ModPoiTypes.HUNTER_POI.getKey()),
            ImmutableSet.of(),
            ImmutableSet.of(),
            SoundEvents.VILLAGER_WORK_FLETCHER,
            Int2ObjectMap.<ResourceKey<TradeSet>>ofEntries()));

    public static final DeferredHolder<VillagerProfession, VillagerProfession> GUARD =
        PROFESSIONS.register("guard", () -> new VillagerProfession(
            Component.translatable("entity.smartvillager.villager.guard"),
            holder -> holder.is(ModPoiTypes.GUARD_POI.getKey()),
            holder -> holder.is(ModPoiTypes.GUARD_POI.getKey()),
            ImmutableSet.of(),
            ImmutableSet.of(),
            SoundEvents.VILLAGER_WORK_WEAPONSMITH,
            Int2ObjectMap.<ResourceKey<TradeSet>>ofEntries()));

    public static final DeferredHolder<VillagerProfession, VillagerProfession> ELDER =
        PROFESSIONS.register("elder", () -> new VillagerProfession(
            Component.translatable("entity.smartvillager.villager.elder"),
            holder -> holder.is(ModPoiTypes.ELDER_POI.getKey()),
            holder -> holder.is(ModPoiTypes.ELDER_POI.getKey()),
            ImmutableSet.of(),
            ImmutableSet.of(),
            SoundEvents.VILLAGER_WORK_LIBRARIAN,
            Int2ObjectMap.<ResourceKey<TradeSet>>ofEntries()));
}
