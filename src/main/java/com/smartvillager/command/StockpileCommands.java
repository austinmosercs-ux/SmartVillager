package com.smartvillager.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.smartvillager.village.SmartVillage;
import com.smartvillager.village.VillageStockpile;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.Optional;

/**
 * /sv stockpile — list all items
 * /sv stockpile add <item> <amount> — deposit items
 * /sv stockpile set <item> <amount> — set exact count
 * /sv stockpile clear — remove all items
 */
final class StockpileCommands {
    private StockpileCommands() {}

    static void register(LiteralArgumentBuilder<CommandSourceStack> sv) {
        sv.then(Commands.literal("stockpile")
            .executes(ctx -> stockpileList(ctx.getSource().getPlayerOrException()))
            .then(Commands.literal("add")
                .then(Commands.argument(DebugHelper.CMD_ITEM, StringArgumentType.word())
                    .then(Commands.argument(DebugHelper.CMD_AMOUNT, IntegerArgumentType.integer(1, 9999))
                        .executes(ctx -> stockpileAdd(
                            ctx.getSource().getPlayerOrException(),
                            StringArgumentType.getString(ctx, DebugHelper.CMD_ITEM),
                            IntegerArgumentType.getInteger(ctx, DebugHelper.CMD_AMOUNT))))))
            .then(Commands.literal("set")
                .then(Commands.argument(DebugHelper.CMD_ITEM, StringArgumentType.word())
                    .then(Commands.argument(DebugHelper.CMD_AMOUNT, IntegerArgumentType.integer(0, 99999))
                        .executes(ctx -> stockpileSet(
                            ctx.getSource().getPlayerOrException(),
                            StringArgumentType.getString(ctx, DebugHelper.CMD_ITEM),
                            IntegerArgumentType.getInteger(ctx, DebugHelper.CMD_AMOUNT))))))
            .then(Commands.literal(DebugHelper.CMD_CLEAR)
                .executes(ctx -> stockpileClear(ctx.getSource().getPlayerOrException()))));
    }

    private static int stockpileList(ServerPlayer player) {
        Optional<SmartVillage> found = DebugHelper.findNearestVillage(player);
        if (found.isEmpty()) {
            player.sendSystemMessage(Component.literal(DebugHelper.NO_VILLAGE_MSG).withStyle(ChatFormatting.RED));
            return 0;
        }

        SmartVillage village = found.get();
        Map<Identifier, Integer> contents = village.getStockpile().snapshot();

        player.sendSystemMessage(
            Component.literal("=== Stockpile @ " + village.getAnchor().toShortString() + " ===")
                .withStyle(ChatFormatting.GOLD));

        if (contents.isEmpty()) {
            player.sendSystemMessage(Component.literal(DebugHelper.EMPTY_LIST).withStyle(ChatFormatting.GRAY));
            return 0;
        }

        contents.entrySet().stream()
            .sorted(Map.Entry.<Identifier, Integer>comparingByValue().reversed())
            .forEach(e -> player.sendSystemMessage(
                Component.literal("  " + e.getKey() + ": ").withStyle(ChatFormatting.WHITE)
                    .append(Component.literal(String.valueOf(e.getValue())).withStyle(ChatFormatting.AQUA))));

        return contents.size();
    }

    private static int stockpileAdd(ServerPlayer player, String itemArg, int amount) {
        Optional<SmartVillage> found = DebugHelper.findNearestVillage(player);
        if (found.isEmpty()) {
            player.sendSystemMessage(Component.literal(DebugHelper.NO_VILLAGE_MSG).withStyle(ChatFormatting.RED));
            return 0;
        }

        Identifier itemId = DebugHelper.parseItemId(itemArg);
        found.get().getStockpile().deposit(itemId, amount);

        player.sendSystemMessage(
            Component.literal("Deposited ").withStyle(ChatFormatting.GREEN)
                .append(Component.literal(amount + "x " + itemId).withStyle(ChatFormatting.AQUA))
                .append(Component.literal(" into village stockpile.").withStyle(ChatFormatting.GREEN)));

        return amount;
    }

    private static int stockpileSet(ServerPlayer player, String itemArg, int amount) {
        Optional<SmartVillage> found = DebugHelper.findNearestVillage(player);
        if (found.isEmpty()) {
            player.sendSystemMessage(Component.literal(DebugHelper.NO_VILLAGE_MSG).withStyle(ChatFormatting.RED));
            return 0;
        }

        Identifier itemId = DebugHelper.parseItemId(itemArg);
        VillageStockpile stockpile = found.get().getStockpile();
        int existing = stockpile.getCount(itemId);
        if (existing > 0) stockpile.withdraw(itemId, existing);
        if (amount > 0)   stockpile.deposit(itemId, amount);

        player.sendSystemMessage(
            Component.literal("Set ").withStyle(ChatFormatting.GREEN)
                .append(Component.literal(itemId.toString()).withStyle(ChatFormatting.AQUA))
                .append(Component.literal(" to ").withStyle(ChatFormatting.GREEN))
                .append(Component.literal(String.valueOf(amount)).withStyle(ChatFormatting.AQUA))
                .append(Component.literal("  (was " + existing + ").").withStyle(ChatFormatting.GRAY)));

        return amount;
    }

    private static int stockpileClear(ServerPlayer player) {
        Optional<SmartVillage> found = DebugHelper.findNearestVillage(player);
        if (found.isEmpty()) {
            player.sendSystemMessage(Component.literal(DebugHelper.NO_VILLAGE_MSG).withStyle(ChatFormatting.RED));
            return 0;
        }

        VillageStockpile stockpile = found.get().getStockpile();
        // snapshot() returns a copy — safe to iterate while withdrawing
        Map<Identifier, Integer> contents = stockpile.snapshot();
        int typeCount = contents.size();
        for (Map.Entry<Identifier, Integer> e : contents.entrySet()) {
            stockpile.withdraw(e.getKey(), e.getValue());
        }

        player.sendSystemMessage(
            Component.literal("Cleared stockpile (" + typeCount + " item type(s) removed).")
                .withStyle(ChatFormatting.YELLOW));

        return typeCount;
    }
}
