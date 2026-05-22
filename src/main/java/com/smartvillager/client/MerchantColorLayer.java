package com.smartvillager.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.smartvillager.SmartVillager;
import net.minecraft.client.model.npc.VillagerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.VillagerRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.npc.villager.VillagerType;
import net.minecraft.world.item.DyeColor;

import java.util.Map;

/**
 * Render layer applied to every villager. Only activates when the villager's
 * profession is smartvillager:merchant.
 *
 * Draws the vanilla Wandering Trader texture over the villager model body,
 * tinted with a biome-derived robe color. Uses the first palette entry from
 * MerchantColor for each VillagerType so the appearance is stable without
 * requiring server→client data sync for the exact per-village color.
 */
public final class MerchantColorLayer extends RenderLayer<VillagerRenderState, VillagerModel> {

    private static final ResourceKey<VillagerProfession> MERCHANT_KEY = ResourceKey.create(
        Registries.VILLAGER_PROFESSION,
        Identifier.fromNamespaceAndPath(SmartVillager.MOD_ID, "merchant")
    );

    private static final Identifier WANDERING_TRADER_TEXTURE =
        Identifier.withDefaultNamespace("textures/entity/wandering_trader.png");

    // First color from MerchantColor's palette per VillagerType — stable ARGB int.
    private static final Map<ResourceKey<VillagerType>, Integer> TYPE_TINT = Map.of(
        VillagerType.DESERT,  DyeColor.CYAN.getTextureDiffuseColor(),
        VillagerType.PLAINS,  DyeColor.WHITE.getTextureDiffuseColor(),
        VillagerType.SAVANNA, DyeColor.ORANGE.getTextureDiffuseColor(),
        VillagerType.TAIGA,   DyeColor.BLUE.getTextureDiffuseColor(),
        VillagerType.SNOW,    DyeColor.BLUE.getTextureDiffuseColor(),
        VillagerType.JUNGLE,  DyeColor.WHITE.getTextureDiffuseColor(),
        VillagerType.SWAMP,   DyeColor.WHITE.getTextureDiffuseColor()
    );

    public MerchantColorLayer(RenderLayerParent<VillagerRenderState, VillagerModel> parent) {
        super(parent);
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int packedLight,
                       VillagerRenderState renderState, float yRot, float xRot) {
        if (renderState.villagerData == null) return;
        if (!renderState.villagerData.profession().is(MERCHANT_KEY)) return;

        ResourceKey<VillagerType> typeKey = renderState.villagerData.type()
            .unwrapKey()
            .orElse(VillagerType.PLAINS);
        int tint = TYPE_TINT.getOrDefault(typeKey, DyeColor.WHITE.getTextureDiffuseColor());

        collector.order(1).submitModel(
            getParentModel(),
            renderState,
            poseStack,
            RenderTypes.entityCutout(WANDERING_TRADER_TEXTURE),
            packedLight,
            OverlayTexture.NO_OVERLAY,
            tint,
            null
        );
    }
}
