package com.smartvillager.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.smartvillager.defense.IronGolemSystem;
import com.smartvillager.village.SmartVillage;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.animal.golem.IronGolem;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * /sv threat trigger|clear — manual THREAT_ALERT control
 * /sv golem list|spawn — village-owned iron golem management
 */
final class DefenseCommands {
    private DefenseCommands() {}

    static void register(LiteralArgumentBuilder<CommandSourceStack> sv) {
        sv.then(Commands.literal("threat")
            .then(Commands.literal("trigger")
                .executes(ctx -> threatTrigger(ctx.getSource().getPlayerOrException())))
            .then(Commands.literal(DebugHelper.CMD_CLEAR)
                .executes(ctx -> threatClear(ctx.getSource().getPlayerOrException()))));

        sv.then(Commands.literal("golem")
            .then(Commands.literal("list")
                .executes(ctx -> golemList(ctx.getSource().getPlayerOrException())))
            .then(Commands.literal("spawn")
                .executes(ctx -> golemSpawn(ctx.getSource().getPlayerOrException()))));
    }

    // ── /sv threat ────────────────────────────────────────────────────────────

    private static int threatTrigger(ServerPlayer player) {
        Optional<SmartVillage> found = DebugHelper.findNearestVillage(player);
        if (found.isEmpty()) {
            player.sendSystemMessage(Component.literal(DebugHelper.NO_VILLAGE_MSG).withStyle(ChatFormatting.RED));
            return 0;
        }

        SmartVillage village = found.get();
        village.activateThreatAlert(player.level().getGameTime());

        player.sendSystemMessage(
            Component.literal("THREAT_ALERT activated @ " + village.getAnchor().toShortString()
                + ". Civilians will shelter.")
                .withStyle(ChatFormatting.RED));

        return 1;
    }

    private static int threatClear(ServerPlayer player) {
        Optional<SmartVillage> found = DebugHelper.findNearestVillage(player);
        if (found.isEmpty()) {
            player.sendSystemMessage(Component.literal(DebugHelper.NO_VILLAGE_MSG).withStyle(ChatFormatting.RED));
            return 0;
        }

        SmartVillage village = found.get();
        boolean wasActive = village.isThreatAlertActive();
        village.clearThreatAlert();

        player.sendSystemMessage(
            Component.literal("THREAT_ALERT cleared" + (wasActive ? "." : " (was already inactive)."))
                .withStyle(ChatFormatting.GREEN));

        return 1;
    }

    // ── /sv golem ─────────────────────────────────────────────────────────────

    private static int golemList(ServerPlayer player) {
        Optional<SmartVillage> found = DebugHelper.findNearestVillage(player);
        if (found.isEmpty()) {
            player.sendSystemMessage(Component.literal(DebugHelper.NO_VILLAGE_MSG).withStyle(ChatFormatting.RED));
            return 0;
        }

        SmartVillage village = found.get();
        Set<UUID> golems = village.getGolems();

        player.sendSystemMessage(
            Component.literal("=== Golems @ " + village.getAnchor().toShortString() + " ===")
                .withStyle(ChatFormatting.GOLD));

        if (golems.isEmpty()) {
            player.sendSystemMessage(Component.literal(DebugHelper.EMPTY_LIST).withStyle(ChatFormatting.GRAY));
            return 0;
        }

        ServerLevel level = player.level();
        for (UUID uuid : golems) {
            String uuidShort = uuid.toString().substring(0, 8);
            if (level.getEntity(uuid) instanceof IronGolem golem) {
                player.sendSystemMessage(
                    Component.literal("  #" + uuidShort + "  HP: ").withStyle(ChatFormatting.WHITE)
                        .append(Component.literal(
                            String.format(DebugHelper.FMT_HP, golem.getHealth(), golem.getMaxHealth()))
                            .withStyle(ChatFormatting.GREEN)));
            } else {
                int abstractHp = village.getAbstractGolemHealth(uuid);
                player.sendSystemMessage(
                    Component.literal("  #" + uuidShort + "  (abstract) HP: ").withStyle(ChatFormatting.GRAY)
                        .append(Component.literal(abstractHp + "/" + IronGolemSystem.GOLEM_MAX_HEALTH)
                            .withStyle(ChatFormatting.GRAY)));
            }
        }

        return golems.size();
    }

    private static int golemSpawn(ServerPlayer player) {
        Optional<SmartVillage> found = DebugHelper.findNearestVillage(player);
        if (found.isEmpty()) {
            player.sendSystemMessage(Component.literal(DebugHelper.NO_VILLAGE_MSG).withStyle(ChatFormatting.RED));
            return 0;
        }

        SmartVillage village = found.get();
        if (village.getGolems().size() >= SmartVillage.GOLEM_CAP) {
            player.sendSystemMessage(
                Component.literal("Village is at golem cap (" + SmartVillage.GOLEM_CAP
                    + "). Kill the existing golem first.")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        IronGolemSystem.spawnGolem(player.level(), village);

        player.sendSystemMessage(
            Component.literal("Iron golem spawned at storehouse (cost bypassed).").withStyle(ChatFormatting.GREEN));

        return 1;
    }
}
