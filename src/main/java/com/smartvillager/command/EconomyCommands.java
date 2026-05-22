package com.smartvillager.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.smartvillager.build.BuildTask;
import com.smartvillager.village.SmartVillage;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Optional;

/**
 * /sv prosperity add|set — adjust the village prosperity score
 * /sv build list|clear — inspect and reset the build queue
 */
final class EconomyCommands {
    private EconomyCommands() {}

    static void register(LiteralArgumentBuilder<CommandSourceStack> sv) {
        sv.then(Commands.literal("prosperity")
            .then(Commands.literal("add")
                .then(Commands.argument(DebugHelper.CMD_AMOUNT, IntegerArgumentType.integer(1, 99999))
                    .executes(ctx -> prosperityAdd(
                        ctx.getSource().getPlayerOrException(),
                        IntegerArgumentType.getInteger(ctx, DebugHelper.CMD_AMOUNT)))))
            .then(Commands.literal("set")
                .then(Commands.argument(DebugHelper.CMD_AMOUNT, IntegerArgumentType.integer(0, 99999))
                    .executes(ctx -> prosperitySet(
                        ctx.getSource().getPlayerOrException(),
                        IntegerArgumentType.getInteger(ctx, DebugHelper.CMD_AMOUNT))))));

        sv.then(Commands.literal("build")
            .then(Commands.literal("list")
                .executes(ctx -> buildList(ctx.getSource().getPlayerOrException())))
            .then(Commands.literal(DebugHelper.CMD_CLEAR)
                .executes(ctx -> buildClear(ctx.getSource().getPlayerOrException()))));
    }

    // ── /sv prosperity ────────────────────────────────────────────────────────

    private static int prosperityAdd(ServerPlayer player, int amount) {
        Optional<SmartVillage> found = DebugHelper.findNearestVillage(player);
        if (found.isEmpty()) {
            player.sendSystemMessage(Component.literal(DebugHelper.NO_VILLAGE_MSG).withStyle(ChatFormatting.RED));
            return 0;
        }

        SmartVillage village = found.get();
        int before = village.getProsperityScore();
        village.addProsperity(amount);

        player.sendSystemMessage(
            Component.literal("Prosperity: ").withStyle(ChatFormatting.GREEN)
                .append(Component.literal(before + " → " + village.getProsperityScore())
                    .withStyle(ChatFormatting.AQUA)));

        return amount;
    }

    private static int prosperitySet(ServerPlayer player, int target) {
        Optional<SmartVillage> found = DebugHelper.findNearestVillage(player);
        if (found.isEmpty()) {
            player.sendSystemMessage(Component.literal(DebugHelper.NO_VILLAGE_MSG).withStyle(ChatFormatting.RED));
            return 0;
        }

        SmartVillage village = found.get();
        int before = village.getProsperityScore();
        village.addProsperity(target - before);

        player.sendSystemMessage(
            Component.literal("Prosperity set: ").withStyle(ChatFormatting.GREEN)
                .append(Component.literal(before + " → " + village.getProsperityScore())
                    .withStyle(ChatFormatting.AQUA)));

        return target;
    }

    // ── /sv build ─────────────────────────────────────────────────────────────

    private static int buildList(ServerPlayer player) {
        Optional<SmartVillage> found = DebugHelper.findNearestVillage(player);
        if (found.isEmpty()) {
            player.sendSystemMessage(Component.literal(DebugHelper.NO_VILLAGE_MSG).withStyle(ChatFormatting.RED));
            return 0;
        }

        SmartVillage village = found.get();
        List<BuildTask> tasks = village.getBuildQueue().tasks();

        player.sendSystemMessage(
            Component.literal("=== Build Queue @ " + village.getAnchor().toShortString()
                + " (" + tasks.size() + ") ===")
                .withStyle(ChatFormatting.GOLD));

        if (tasks.isEmpty()) {
            player.sendSystemMessage(Component.literal(DebugHelper.EMPTY_LIST).withStyle(ChatFormatting.GRAY));
            return 0;
        }

        for (int i = 0; i < tasks.size(); i++) {
            BuildTask t = tasks.get(i);
            player.sendSystemMessage(
                Component.literal("  [" + i + "] " + t.type().name()
                    + " @ " + t.targetPos().toShortString() + "  ")
                    .withStyle(ChatFormatting.WHITE)
                    .append(Component.literal(t.progressDone() + "/" + t.progressRequired() + " ticks")
                        .withStyle(ChatFormatting.AQUA)));
        }

        return tasks.size();
    }

    private static int buildClear(ServerPlayer player) {
        Optional<SmartVillage> found = DebugHelper.findNearestVillage(player);
        if (found.isEmpty()) {
            player.sendSystemMessage(Component.literal(DebugHelper.NO_VILLAGE_MSG).withStyle(ChatFormatting.RED));
            return 0;
        }

        SmartVillage village = found.get();
        int count = village.getBuildQueue().size();
        village.getBuildQueue().clear();

        player.sendSystemMessage(
            Component.literal("Cleared " + count + " build task(s).").withStyle(ChatFormatting.YELLOW));

        return count;
    }
}
