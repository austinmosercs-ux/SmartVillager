package com.smartvillager.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.smartvillager.needqueue.NeedRequest;
import com.smartvillager.village.SmartVillage;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * /sv queue — list all open and in-progress NeedRequests
 * /sv queue clear — cancel all open requests
 */
final class NeedQueueCommands {
    private NeedQueueCommands() {}

    static void register(LiteralArgumentBuilder<CommandSourceStack> sv) {
        sv.then(Commands.literal("queue")
            .executes(ctx -> queueList(ctx.getSource().getPlayerOrException()))
            .then(Commands.literal(DebugHelper.CMD_CLEAR)
                .executes(ctx -> queueClear(ctx.getSource().getPlayerOrException()))));
    }

    private static int queueList(ServerPlayer player) {
        Optional<SmartVillage> found = DebugHelper.findNearestVillage(player);
        if (found.isEmpty()) {
            player.sendSystemMessage(Component.literal(DebugHelper.NO_VILLAGE_MSG).withStyle(ChatFormatting.RED));
            return 0;
        }

        SmartVillage village = found.get();
        List<NeedRequest> open   = village.getNeedQueue().allOpen();
        List<NeedRequest> inProg = village.getNeedQueue().allInProgress();

        player.sendSystemMessage(
            Component.literal("=== NeedQueue @ " + village.getAnchor().toShortString() + " ===")
                .withStyle(ChatFormatting.GOLD));

        if (open.isEmpty() && inProg.isEmpty()) {
            player.sendSystemMessage(Component.literal(DebugHelper.EMPTY_LIST).withStyle(ChatFormatting.GRAY));
            return 0;
        }

        if (!open.isEmpty()) {
            player.sendSystemMessage(
                Component.literal("  Open (" + open.size() + "):").withStyle(ChatFormatting.YELLOW));
            open.forEach(r -> player.sendSystemMessage(formatRequest(r, false)));
        }
        if (!inProg.isEmpty()) {
            player.sendSystemMessage(
                Component.literal("  In-Progress (" + inProg.size() + "):").withStyle(ChatFormatting.GREEN));
            inProg.forEach(r -> player.sendSystemMessage(formatRequest(r, true)));
        }

        return open.size() + inProg.size();
    }

    private static Component formatRequest(NeedRequest r, boolean inProgress) {
        String type   = r.getType().getPath().replace("need/", "");
        String item   = r.getItemData().map(id -> " [" + id.getPath() + "]").orElse("");
        String prio   = r.getPriority().name().toLowerCase();
        String poster = r.getPoster().toString().substring(0, 8);
        ChatFormatting color = inProgress ? ChatFormatting.GREEN : ChatFormatting.YELLOW;
        return Component.literal("    " + type + item + "  " + prio + "  poster:#" + poster)
            .withStyle(color);
    }

    private static int queueClear(ServerPlayer player) {
        Optional<SmartVillage> found = DebugHelper.findNearestVillage(player);
        if (found.isEmpty()) {
            player.sendSystemMessage(Component.literal(DebugHelper.NO_VILLAGE_MSG).withStyle(ChatFormatting.RED));
            return 0;
        }

        SmartVillage village = found.get();
        // Copy before mutating
        List<NeedRequest> open = new ArrayList<>(village.getNeedQueue().allOpen());
        int cleared = 0;
        for (NeedRequest r : open) {
            village.getNeedQueue().cancel(r.getId());
            cleared++;
        }

        player.sendSystemMessage(
            Component.literal("Cleared " + cleared + " open request(s) from the NeedQueue.")
                .withStyle(ChatFormatting.YELLOW));

        return cleared;
    }
}
