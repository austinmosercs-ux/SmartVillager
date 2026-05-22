package com.smartvillager.client;

import net.minecraft.world.entity.EntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * Client-only rendering hooks for SmartVillager.
 *
 * Replaces the vanilla VillagerRenderer with SmartVillagerRenderer so that the
 * MerchantColorLayer is active for every villager render pass. Called from the
 * @Mod constructor only when dist == CLIENT to prevent server-side class loading
 * of client-only renderer classes.
 *
 * Guard and all vanilla professions are unaffected — their appearance is driven
 * by the standard profession texture lookup:
 *   assets/smartvillager/textures/entity/villager/profession/guard.png
 *   assets/smartvillager/textures/entity/villager/profession/merchant.png
 * (Place PNG files at those paths to replace the missing-texture placeholder.)
 */
public final class ClientModEvents {
    private ClientModEvents() {}

    /** Called from SmartVillager constructor, client dist only. */
    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(ClientModEvents::onRegisterRenderers);
    }

    private static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(EntityType.VILLAGER, SmartVillagerRenderer::new);
    }
}
