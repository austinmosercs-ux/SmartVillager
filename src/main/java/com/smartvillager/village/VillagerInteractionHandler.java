package com.smartvillager.village;

import com.smartvillager.SmartVillager;
import net.minecraft.world.entity.npc.villager.Villager;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Blocks vanilla villager trading entirely. All player-villager interaction is
 * handled through mod systems (Merchant shop UI, Librarian quests, Cleric healing).
 */
@EventBusSubscriber(modid = SmartVillager.MOD_ID)
public final class VillagerInteractionHandler {
    private VillagerInteractionHandler() {}

    @SubscribeEvent
    public static void onPlayerInteractWithVillager(PlayerInteractEvent.EntityInteract event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getTarget() instanceof Villager)) return;
        event.setCanceled(true);
    }
}
