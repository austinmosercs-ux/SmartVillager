package com.smartvillager.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.smartvillager.SmartVillager;
import com.smartvillager.health.VillagerHealth;
import com.smartvillager.hunger.VillagerHunger;
import com.smartvillager.registration.ModAttachments;
import com.smartvillager.village.SmartVillage;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import com.mojang.brigadier.arguments.IntegerArgumentType;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * /sv debug — toggle villager thought broadcast
 * /sv nearby [radius] — list nearby villagers with HP and hunger
 * /sv villager heal|feed — force-heal or force-feed roster villagers nearby
 */
@EventBusSubscriber(modid = SmartVillager.MOD_ID)
final class VillagerDebugCommands {
    private VillagerDebugCommands() {}

    private static final Set<UUID> DEBUG_PLAYERS = new HashSet<>();
    private static final int    DEBUG_INTERVAL   = 100;
    private static final double DEBUG_RADIUS      = 32.0;

    static void register(LiteralArgumentBuilder<CommandSourceStack> sv) {
        sv.then(Commands.literal("debug")
            .executes(ctx -> toggleDebug(ctx.getSource().getPlayerOrException())));

        sv.then(Commands.literal("nearby")
            .executes(ctx -> nearbyVillagers(ctx.getSource().getPlayerOrException(), 16))
            .then(Commands.argument("radius", IntegerArgumentType.integer(1, 128))
                .executes(ctx -> nearbyVillagers(
                    ctx.getSource().getPlayerOrException(),
                    IntegerArgumentType.getInteger(ctx, "radius")))));

        sv.then(Commands.literal("villager")
            .then(Commands.literal("heal")
                .executes(ctx -> villagerHeal(ctx.getSource().getPlayerOrException())))
            .then(Commands.literal("feed")
                .executes(ctx -> villagerFeed(ctx.getSource().getPlayerOrException()))));
    }

    // ── /sv debug ────────────────────────────────────────────────────────────

    private static int toggleDebug(ServerPlayer player) {
        UUID id = player.getUUID();
        if (DEBUG_PLAYERS.remove(id)) {
            player.sendSystemMessage(
                Component.literal("[SmartVillager] Villager thoughts OFF.")
                    .withStyle(ChatFormatting.GRAY));
        } else {
            DEBUG_PLAYERS.add(id);
            player.sendSystemMessage(
                Component.literal("[SmartVillager] Villager thoughts ON — broadcasting every 5s within "
                    + (int) DEBUG_RADIUS + "b.")
                    .withStyle(ChatFormatting.GREEN));
        }
        return 1;
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (DEBUG_PLAYERS.isEmpty()) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (level.getGameTime() % DEBUG_INTERVAL != 0) return;

        for (ServerPlayer player : level.players()) {
            if (!DEBUG_PLAYERS.contains(player.getUUID())) continue;

            double r = DEBUG_RADIUS;
            AABB box = new AABB(
                player.getX() - r, player.getY() - r, player.getZ() - r,
                player.getX() + r, player.getY() + r, player.getZ() + r
            );

            for (Villager v : level.getEntitiesOfClass(Villager.class, box)) {
                String profession = v.getVillagerData().profession().unwrapKey()
                    .map(k -> k.identifier().getPath())
                    .orElse("?");

                VillagerHealth health = v.getData(ModAttachments.VILLAGER_HEALTH);
                VillagerHunger hunger = v.getData(ModAttachments.VILLAGER_HUNGER);
                String uuid = v.getUUID().toString().substring(0, 8);

                String thought = resolveThought(health, hunger);
                ChatFormatting thoughtColor = resolveThoughtColor(health, hunger);

                player.sendSystemMessage(
                    Component.literal("[" + profession + " #" + uuid + "] ")
                        .withStyle(ChatFormatting.DARK_AQUA)
                        .append(Component.literal("\"" + thought + "\"").withStyle(thoughtColor)));
            }
        }
    }

    private static String resolveThought(VillagerHealth health, VillagerHunger hunger) {
        if (health.isSeekingHealing()) return "I need healing!";
        if (hunger.isHungry())         return "I need food!";
        if (health.isLow())            return "I am injured...";
        return "Idle.";
    }

    private static ChatFormatting resolveThoughtColor(VillagerHealth health, VillagerHunger hunger) {
        if (health.isSeekingHealing()) return ChatFormatting.RED;
        if (hunger.isHungry())         return ChatFormatting.YELLOW;
        if (health.isLow())            return ChatFormatting.GOLD;
        return ChatFormatting.WHITE;
    }

    // ── /sv nearby ───────────────────────────────────────────────────────────

    private static int nearbyVillagers(ServerPlayer player, int radius) {
        double r = radius;
        AABB box = new AABB(
            player.getX() - r, player.getY() - r, player.getZ() - r,
            player.getX() + r, player.getY() + r, player.getZ() + r
        );
        List<Villager> villagers = player.level().getEntitiesOfClass(Villager.class, box);

        if (villagers.isEmpty()) {
            player.sendSystemMessage(
                Component.literal("No villagers within " + radius + "b.").withStyle(ChatFormatting.GRAY));
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

            String hpStr  = String.format(DebugHelper.FMT_HP, health.get(), VillagerHealth.MAX);
            String hunStr = String.format(DebugHelper.FMT_HP, hunger.get(), VillagerHunger.MAX);
            String uuid   = v.getUUID().toString().substring(0, 8);

            player.sendSystemMessage(
                Component.literal("[" + profession + "] ").withStyle(ChatFormatting.YELLOW)
                    .append(Component.literal("HP: ").withStyle(ChatFormatting.WHITE))
                    .append(Component.literal(hpStr + (health.isSeekingHealing() ? " [!]" : ""))
                        .withStyle(health.isLow() ? ChatFormatting.RED : ChatFormatting.GREEN))
                    .append(Component.literal("  Hunger: ").withStyle(ChatFormatting.WHITE))
                    .append(Component.literal(hunStr + (hunger.isHungry() ? " [!]" : ""))
                        .withStyle(hunger.isHungry() ? ChatFormatting.RED : ChatFormatting.GREEN))
                    .append(Component.literal("  #" + uuid).withStyle(ChatFormatting.DARK_GRAY)));
        }

        return villagers.size();
    }

    // ── /sv villager heal|feed ────────────────────────────────────────────────

    private static int villagerHeal(ServerPlayer player) {
        Optional<SmartVillage> found = DebugHelper.findNearestVillage(player);
        if (found.isEmpty()) {
            player.sendSystemMessage(Component.literal(DebugHelper.NO_VILLAGE_MSG).withStyle(ChatFormatting.RED));
            return 0;
        }

        SmartVillage village = found.get();
        int healed = 0;
        for (Villager v : nearbyRosterVillagers(player, village, 64)) {
            VillagerHealth health = v.getData(ModAttachments.VILLAGER_HEALTH);
            health.heal(VillagerHealth.MAX);
            v.setHealth(VillagerHealth.MAX);
            healed++;
        }

        player.sendSystemMessage(
            Component.literal("Healed " + healed + " villager(s) to full HP.").withStyle(ChatFormatting.GREEN));

        return healed;
    }

    private static int villagerFeed(ServerPlayer player) {
        Optional<SmartVillage> found = DebugHelper.findNearestVillage(player);
        if (found.isEmpty()) {
            player.sendSystemMessage(Component.literal(DebugHelper.NO_VILLAGE_MSG).withStyle(ChatFormatting.RED));
            return 0;
        }

        SmartVillage village = found.get();
        int fed = 0;
        for (Villager v : nearbyRosterVillagers(player, village, 64)) {
            v.getData(ModAttachments.VILLAGER_HUNGER).restore(VillagerHunger.MAX);
            fed++;
        }

        player.sendSystemMessage(
            Component.literal("Fed " + fed + " villager(s) to full hunger.").withStyle(ChatFormatting.GREEN));

        return fed;
    }

    static List<Villager> nearbyRosterVillagers(ServerPlayer player, SmartVillage village, double radius) {
        AABB box = new AABB(
            player.getX() - radius, player.getY() - radius, player.getZ() - radius,
            player.getX() + radius, player.getY() + radius, player.getZ() + radius
        );
        return player.level().getEntitiesOfClass(Villager.class, box).stream()
            .filter(v -> village.hasVillager(v.getUUID()))
            .toList();
    }
}
