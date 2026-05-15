package com.smartvillager.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.smartvillager.SmartVillager;
import com.smartvillager.health.VillagerHealth;
import com.smartvillager.hunger.VillagerHunger;
import com.smartvillager.registration.ModAttachments;
import com.smartvillager.village.SmartVillage;
import com.smartvillager.village.VillageRegistry;
import com.smartvillager.village.VillageStockpile;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@EventBusSubscriber(modid = SmartVillager.MOD_ID)
public final class DebugCommands {
    private DebugCommands() {}

    private static final double VILLAGE_SEARCH_RADIUS = 256.0;
    private static final String NO_VILLAGE_MSG = "No village within " + (int) VILLAGE_SEARCH_RADIUS + " blocks.";

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
            Commands.literal("sv")
                .then(Commands.literal("nearby")
                    .executes(ctx -> nearbyVillagers(ctx.getSource().getPlayerOrException(), 16))
                    .then(Commands.argument("radius", IntegerArgumentType.integer(1, 128))
                        .executes(ctx -> nearbyVillagers(
                            ctx.getSource().getPlayerOrException(),
                            IntegerArgumentType.getInteger(ctx, "radius")))))
                .then(Commands.literal("stockpile")
                    .executes(ctx -> stockpileList(ctx.getSource().getPlayerOrException()))
                    .then(Commands.literal("add")
                        .then(Commands.argument("item", StringArgumentType.word())
                            .then(Commands.argument("amount", IntegerArgumentType.integer(1, 9999))
                                .executes(ctx -> stockpileAdd(
                                    ctx.getSource().getPlayerOrException(),
                                    StringArgumentType.getString(ctx, "item"),
                                    IntegerArgumentType.getInteger(ctx, "amount"))))))));
    }

    // -------------------------------------------------------------------------
    // /sv nearby [radius]
    // -------------------------------------------------------------------------

    private static int nearbyVillagers(ServerPlayer player, int radius) {
        double r = radius;
        AABB box = new AABB(
            player.getX() - r, player.getY() - r, player.getZ() - r,
            player.getX() + r, player.getY() + r, player.getZ() + r
        );
        List<Villager> villagers = player.level().getEntitiesOfClass(Villager.class, box);

        if (villagers.isEmpty()) {
            player.sendSystemMessage(
                Component.literal("No villagers within " + radius + "b.")
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

            String hpStr  = String.format("%.1f/%.0f", health.get(), VillagerHealth.MAX);
            String hunStr = String.format("%.1f/%.0f", hunger.get(), VillagerHunger.MAX);
            String uuid   = v.getUUID().toString().substring(0, 8);

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

    // -------------------------------------------------------------------------
    // /sv stockpile
    // -------------------------------------------------------------------------

    private static int stockpileList(ServerPlayer player) {
        Optional<SmartVillage> found = VillageRegistry.get(player.level())
            .findNearest(player.blockPosition(), VILLAGE_SEARCH_RADIUS * VILLAGE_SEARCH_RADIUS);

        if (found.isEmpty()) {
            player.sendSystemMessage(
                Component.literal(NO_VILLAGE_MSG)
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        SmartVillage village = found.get();
        Map<Identifier, Integer> contents = village.getStockpile().snapshot();

        player.sendSystemMessage(
            Component.literal("=== Stockpile @ " + village.getAnchor().toShortString() + " ===")
                .withStyle(ChatFormatting.GOLD));

        if (contents.isEmpty()) {
            player.sendSystemMessage(
                Component.literal("  (empty)").withStyle(ChatFormatting.GRAY));
            return 0;
        }

        contents.entrySet().stream()
            .sorted(Map.Entry.<Identifier, Integer>comparingByValue().reversed())
            .forEach(e -> player.sendSystemMessage(
                Component.literal("  " + e.getKey() + ": ")
                    .withStyle(ChatFormatting.WHITE)
                    .append(Component.literal(String.valueOf(e.getValue()))
                        .withStyle(ChatFormatting.AQUA))));

        return contents.size();
    }

    // -------------------------------------------------------------------------
    // /sv stockpile add <item> <amount>
    // -------------------------------------------------------------------------

    private static int stockpileAdd(ServerPlayer player, String itemArg, int amount) {
        Optional<SmartVillage> found = VillageRegistry.get(player.level())
            .findNearest(player.blockPosition(), VILLAGE_SEARCH_RADIUS * VILLAGE_SEARCH_RADIUS);

        if (found.isEmpty()) {
            player.sendSystemMessage(
                Component.literal(NO_VILLAGE_MSG)
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        Identifier itemId = itemArg.contains(":") ? Identifier.parse(itemArg)
                                                   : Identifier.withDefaultNamespace(itemArg);

        VillageStockpile stockpile = found.get().getStockpile();
        stockpile.deposit(itemId, amount);

        player.sendSystemMessage(
            Component.literal("Deposited ")
                .withStyle(ChatFormatting.GREEN)
                .append(Component.literal(amount + "x " + itemId).withStyle(ChatFormatting.AQUA))
                .append(Component.literal(" into village stockpile.").withStyle(ChatFormatting.GREEN)));

        return amount;
    }
}
