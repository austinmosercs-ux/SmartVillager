package com.smartvillager.client;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.VillagerRenderer;

/**
 * Extends VillagerRenderer to add the MerchantColorLayer on top of the standard
 * profession overlay layers. All vanilla layer behavior (base texture, profession
 * texture, held items) is preserved through super().
 */
public final class SmartVillagerRenderer extends VillagerRenderer {

    public SmartVillagerRenderer(EntityRendererProvider.Context context) {
        super(context);
        addLayer(new MerchantColorLayer(this));
    }
}
