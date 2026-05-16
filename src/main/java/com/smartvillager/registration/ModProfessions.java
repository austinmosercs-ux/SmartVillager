package com.smartvillager.registration;

import com.google.common.collect.ImmutableSet;
import com.smartvillager.SmartVillager;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.trading.TradeSet;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModProfessions {
    private ModProfessions() {}

    public static final DeferredRegister<VillagerProfession> PROFESSIONS =
        DeferredRegister.create(Registries.VILLAGER_PROFESSION, SmartVillager.MOD_ID);

    // Guard patrols from the vanilla Bell as their origin anchor.
    // acquirableJobSite is always false — profession is assigned at birth by the village
    // registration system, never granted by walking up to a block.
    // heldJobSite matches PoiTypes.MEETING (Bell) so the brain's ValidateNearbyPoi and
    // SetWalkTargetFromBlockMemory behaviors know where the Guard's anchor is.
    public static final DeferredHolder<VillagerProfession, VillagerProfession> GUARD =
        PROFESSIONS.register("guard", () -> new VillagerProfession(
            Component.translatable("entity.smartvillager.villager.guard"),
            holder -> holder.is(PoiTypes.MEETING),
            holder -> false,
            ImmutableSet.of(),
            ImmutableSet.of(),
            SoundEvents.VILLAGER_WORK_WEAPONSMITH,
            Int2ObjectMap.<ResourceKey<TradeSet>>ofEntries()));

    // Merchant has no job site. They wander in the village center during work hours and
    // path toward the player on proximity. Neither predicate ever matches a POI.
    public static final DeferredHolder<VillagerProfession, VillagerProfession> MERCHANT =
        PROFESSIONS.register("merchant", () -> new VillagerProfession(
            Component.translatable("entity.smartvillager.villager.merchant"),
            holder -> false,
            holder -> false,
            ImmutableSet.of(),
            ImmutableSet.of(),
            SoundEvents.VILLAGER_WORK_CARTOGRAPHER,
            Int2ObjectMap.<ResourceKey<TradeSet>>ofEntries()));
}
