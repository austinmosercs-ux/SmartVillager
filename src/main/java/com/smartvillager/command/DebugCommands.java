package com.smartvillager.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.smartvillager.SmartVillager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * Entry point for all /sv debug commands.
 *
 * Builds the "sv" command tree by delegating to focused submodules:
 *   VillagerDebugCommands — /sv debug, /sv nearby, /sv villager heal|feed
 *   VillageInfoCommands   — /sv status, /sv roster
 *   StockpileCommands     — /sv stockpile (list/add/set/clear)
 *   NeedQueueCommands     — /sv queue (list/clear)
 *   DefenseCommands       — /sv threat (trigger/clear), /sv golem (list/spawn)
 *   EconomyCommands       — /sv prosperity (add/set), /sv build (list/clear)
 *
 * To add a new command: add it in the appropriate submodule's register() method
 * and document it in DEV_COMMANDS.md.
 */
@EventBusSubscriber(modid = SmartVillager.MOD_ID)
public final class DebugCommands {
    private DebugCommands() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> sv = Commands.literal("sv");

        VillagerDebugCommands.register(sv);
        VillageInfoCommands.register(sv);
        StockpileCommands.register(sv);
        NeedQueueCommands.register(sv);
        DefenseCommands.register(sv);
        EconomyCommands.register(sv);

        event.getDispatcher().register(sv);
    }
}
