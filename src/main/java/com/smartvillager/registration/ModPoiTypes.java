package com.smartvillager.registration;

import com.google.common.collect.ImmutableSet;
import com.smartvillager.SmartVillager;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModPoiTypes {
    private ModPoiTypes() {}

    public static final DeferredRegister<PoiType> POI_TYPES =
        DeferredRegister.create(Registries.POINT_OF_INTEREST_TYPE, SmartVillager.MOD_ID);

    public static final DeferredHolder<PoiType, PoiType> BUILDER_POI =
        POI_TYPES.register("builder_poi", () -> new PoiType(
            ImmutableSet.copyOf(ModBlocks.BUILDERS_WORKBENCH.get().getStateDefinition().getPossibleStates()),
            1, 1));

    public static final DeferredHolder<PoiType, PoiType> MINER_POI =
        POI_TYPES.register("miner_poi", () -> new PoiType(
            ImmutableSet.copyOf(ModBlocks.MINING_STATION.get().getStateDefinition().getPossibleStates()),
            1, 1));

    public static final DeferredHolder<PoiType, PoiType> HUNTER_POI =
        POI_TYPES.register("hunter_poi", () -> new PoiType(
            ImmutableSet.copyOf(ModBlocks.HUNTER_POST.get().getStateDefinition().getPossibleStates()),
            1, 1));

    public static final DeferredHolder<PoiType, PoiType> GUARD_POI =
        POI_TYPES.register("guard_poi", () -> new PoiType(
            ImmutableSet.copyOf(ModBlocks.GUARD_POST.get().getStateDefinition().getPossibleStates()),
            1, 1));

    public static final DeferredHolder<PoiType, PoiType> ELDER_POI =
        POI_TYPES.register("elder_poi", () -> new PoiType(
            ImmutableSet.copyOf(ModBlocks.ELDER_PODIUM.get().getStateDefinition().getPossibleStates()),
            1, 1));
}
