package com.smartvillager.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.smartvillager.SmartVillager;
import com.smartvillager.health.VillagerHealth;
import com.smartvillager.hunger.VillagerHunger;
import com.smartvillager.registration.ModAttachments;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.List;

@EventBusSubscriber(modid = SmartVillager.MOD_ID)
public final class DebugCommands {
    private DebugCommands() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
            Commands.literal("sv")
                .then(Commands.literal("nearby")
                    .executes(ctx -> nearbyVillagers(ctx.getSource().getPlayerOrException(), 16))
                    .then(Commands.argument("radius", IntegerArgumentType.integer(1, 128))
                        .executes(ctx -> nearbyVillagers(
                            ctx.getSource().getPlayerOrException(),
                            IntegerArgumentType.getInteger(ctx, "radius"))))));
    }

    private static int nearbyVillagers(ServerPlayer player, int radius) {
        ServerLevel level = player.level();
        double r = radius;
        AABB box = new AABB(
            player.getX() - r, player.getY() - r, player.getZ() - r,
            player.getX() + r, player.getY() + r, player.getZ() + r
        );
        List<Villager> villagers = level.getEntitiesOfClass(Villager.class, box);

        if (villagers.isEmpty()) {
            player.sendSystemMessage(
                Component.literal("No villagers within " + radius + " blocks.")
                    .withStyle(ChatFormatting.GRAY));
            return 0;
        }

        player.sendSystemMessage(
            Component.literal("=== Villagers within " + radius + "b (" + villagers.size() + ") ===")
                .withStyle(ChatFormatting.GOLD));

        for (Villager v : villagers) {
            String profession = v.getVillagerData().profession().unwrapKey()
                .map(k -> k.identifier().toString())
                .orElse("none");

            VillagerHealth health = v.getData(ModAttachments.VILLAGER_HEALTH);
            VillagerHunger hunger = v.getData(ModAttachments.VILLAGER_HUNGER);

            String hpStr   = String.format("%.1f/%.0f", health.get(), VillagerHealth.MAX);
            String hunStr  = String.format("%.1f/%.0f", hunger.get(), VillagerHunger.MAX);
            String uuid    = v.getUUID().toString().substring(0, 8);

            ChatFormatting hpColor  = health.isLow()    ? ChatFormatting.RED : ChatFormatting.GREEN;
            ChatFormatting hunColor = hunger.isHungry() ? ChatFormatting.RED : ChatFormatting.GREEN;

            String hpSuffix  = health.isSeekingHealing() ? " [!]" : "";
            String hunSuffix = hunger.isHungry()         ? " [!]" : "";

            Component line = Component.literal("[" + profession + "] ")
                .withStyle(ChatFormatting.YELLOW)
                .append(Component.literal("HP: ").withStyle(ChatFormatting.WHITE))
                .append(Component.literal(hpStr + hpSuffix).withStyle(hpColor))
                .append(Component.literal("  Hunger: ").withStyle(ChatFormatting.WHITE))
                .append(Component.literal(hunStr + hunSuffix).withStyle(hunColor))
                .append(Component.literal("  #" + uuid).withStyle(ChatFormatting.DARK_GRAY));

            player.sendSystemMessage(line);
        }

        return villagers.size();
    }
}
