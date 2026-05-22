package com.smartvillager.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.smartvillager.defense.IronGolemSystem;
import com.smartvillager.health.VillagerHealth;
import com.smartvillager.hunger.VillagerHunger;
import com.smartvillager.registration.ModAttachments;
import com.smartvillager.village.SmartVillage;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.villager.Villager;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * /sv status — full village overview snapshot
 * /sv roster — all roster entries with live or abstract HP/hunger
 */
final class VillageInfoCommands {
    private VillageInfoCommands() {}

    static void register(LiteralArgumentBuilder<CommandSourceStack> sv) {
        sv.then(Commands.literal("status")
            .executes(ctx -> villageStatus(ctx.getSource().getPlayerOrException())));

        sv.then(Commands.literal("roster")
            .executes(ctx -> rosterList(ctx.getSource().getPlayerOrException())));
    }

    // ── /sv status ────────────────────────────────────────────────────────────

    private static int villageStatus(ServerPlayer player) {
        Optional<SmartVillage> found = DebugHelper.findNearestVillage(player);
        if (found.isEmpty()) {
            player.sendSystemMessage(Component.literal(DebugHelper.NO_VILLAGE_MSG).withStyle(ChatFormatting.RED));
            return 0;
        }

        SmartVillage v = found.get();

        player.sendSystemMessage(
            Component.literal("=== Village @ " + v.getAnchor().toShortString() + " ===")
                .withStyle(ChatFormatting.GOLD));

        player.sendSystemMessage(
            Component.literal("  ID: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(v.getId().toString().substring(0, 8) + "…")
                    .withStyle(ChatFormatting.WHITE)));

        boolean isFull = "FULL".equals(v.getMode().name());
        player.sendSystemMessage(
            Component.literal("  Simulation: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(v.getMode().name())
                    .withStyle(isFull ? ChatFormatting.GREEN : ChatFormatting.GRAY)));

        boolean atGolemThreshold = v.getProsperityScore() >= IronGolemSystem.GOLEM_PROSPERITY_THRESHOLD;
        player.sendSystemMessage(
            Component.literal("  Prosperity: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(String.valueOf(v.getProsperityScore()))
                    .withStyle(atGolemThreshold ? ChatFormatting.GREEN : ChatFormatting.YELLOW))
                .append(Component.literal(" (golem at " + IronGolemSystem.GOLEM_PROSPERITY_THRESHOLD + ")")
                    .withStyle(ChatFormatting.DARK_GRAY)));

        player.sendSystemMessage(
            Component.literal("  Threat Alert: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(v.isThreatAlertActive() ? "ACTIVE" : DebugHelper.CMD_CLEAR)
                    .withStyle(v.isThreatAlertActive() ? ChatFormatting.RED : ChatFormatting.GREEN)));

        player.sendSystemMessage(
            Component.literal("  Golems: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(v.getGolems().size() + "/" + SmartVillage.GOLEM_CAP)
                    .withStyle(v.getGolems().isEmpty() ? ChatFormatting.YELLOW : ChatFormatting.GREEN)));

        Map<String, Long> profCounts = new HashMap<>();
        for (Identifier prof : v.getRoster().values()) {
            profCounts.merge(prof.getPath(), 1L, Long::sum);
        }
        player.sendSystemMessage(
            Component.literal("  Roster (" + v.getRoster().size() + " total):").withStyle(ChatFormatting.GRAY));
        profCounts.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(e -> player.sendSystemMessage(
                Component.literal("    " + e.getKey() + ": ").withStyle(ChatFormatting.WHITE)
                    .append(Component.literal(String.valueOf(e.getValue())).withStyle(ChatFormatting.AQUA))));

        int openCount   = v.getNeedQueue().allOpen().size();
        int inProgCount = v.getNeedQueue().allInProgress().size();
        player.sendSystemMessage(
            Component.literal("  NeedQueue: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(openCount + " open, " + inProgCount + " in-progress")
                    .withStyle(openCount > 0 ? ChatFormatting.YELLOW : ChatFormatting.WHITE)));

        player.sendSystemMessage(
            Component.literal("  Build queue: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(v.getBuildQueue().size() + " task(s)").withStyle(ChatFormatting.WHITE)));

        Set<Identifier> shortages = v.getShortages();
        if (shortages.isEmpty()) {
            player.sendSystemMessage(
                Component.literal("  Shortages: ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal("none").withStyle(ChatFormatting.GREEN)));
        } else {
            String list = shortages.stream()
                .map(Identifier::getPath).sorted()
                .reduce((a, b) -> a + ", " + b).orElse("");
            player.sendSystemMessage(
                Component.literal("  Shortages: ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(list).withStyle(ChatFormatting.RED)));
        }

        return 1;
    }

    // ── /sv roster ────────────────────────────────────────────────────────────

    private static int rosterList(ServerPlayer player) {
        Optional<SmartVillage> found = DebugHelper.findNearestVillage(player);
        if (found.isEmpty()) {
            player.sendSystemMessage(Component.literal(DebugHelper.NO_VILLAGE_MSG).withStyle(ChatFormatting.RED));
            return 0;
        }

        SmartVillage village = found.get();
        Map<UUID, Identifier> roster = village.getRoster();

        if (roster.isEmpty()) {
            player.sendSystemMessage(Component.literal("Roster is empty.").withStyle(ChatFormatting.GRAY));
            return 0;
        }

        player.sendSystemMessage(
            Component.literal("=== Roster @ " + village.getAnchor().toShortString()
                + " (" + roster.size() + ") ===")
                .withStyle(ChatFormatting.GOLD));

        ServerLevel level = player.level();
        for (Map.Entry<UUID, Identifier> entry : roster.entrySet()) {
            String prof      = entry.getValue().getPath();
            String uuidShort = entry.getKey().toString().substring(0, 8);

            Component line;
            if (level.getEntity(entry.getKey()) instanceof Villager v) {
                VillagerHealth health = v.getData(ModAttachments.VILLAGER_HEALTH);
                VillagerHunger hunger = v.getData(ModAttachments.VILLAGER_HUNGER);
                String hpStr  = String.format(DebugHelper.FMT_HP, health.get(), VillagerHealth.MAX);
                String hunStr = String.format(DebugHelper.FMT_HP, hunger.get(), VillagerHunger.MAX);
                line = Component.literal("  [" + prof + "] ").withStyle(ChatFormatting.YELLOW)
                    .append(Component.literal("HP: ").withStyle(ChatFormatting.WHITE))
                    .append(Component.literal(hpStr)
                        .withStyle(health.isLow() ? ChatFormatting.RED : ChatFormatting.GREEN))
                    .append(Component.literal("  Hunger: ").withStyle(ChatFormatting.WHITE))
                    .append(Component.literal(hunStr)
                        .withStyle(hunger.isHungry() ? ChatFormatting.RED : ChatFormatting.GREEN))
                    .append(Component.literal("  #" + uuidShort).withStyle(ChatFormatting.DARK_GRAY));
            } else {
                float abstractHp = village.getAbstractHealth(entry.getKey());
                line = Component.literal("  [" + prof + "] ").withStyle(ChatFormatting.YELLOW)
                    .append(Component.literal("(abstract) HP: " + String.format("%.1f", abstractHp))
                        .withStyle(ChatFormatting.GRAY))
                    .append(Component.literal("  #" + uuidShort).withStyle(ChatFormatting.DARK_GRAY));
            }
            player.sendSystemMessage(line);
        }

        return roster.size();
    }
}
