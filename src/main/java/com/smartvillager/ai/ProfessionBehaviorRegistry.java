package com.smartvillager.ai;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Central hook for profession-specific Brain behavior injection.
 *
 * Called from MixinVillager after every refreshBrain() call. Future branches
 * register a handler per profession ResourceKey; the handler receives the live
 * ServerLevel and Villager and can set Brain memories, add activities via
 * Brain.addActivity(), or take any other per-profession setup action.
 *
 * Registration should happen during FMLCommonSetupEvent so all handlers are
 * present before the first villager brain is built.
 */
public final class ProfessionBehaviorRegistry {
    private ProfessionBehaviorRegistry() {}

    private static final Map<ResourceKey<VillagerProfession>, BiConsumer<ServerLevel, Villager>> HANDLERS =
        new HashMap<>();

    public static void register(ResourceKey<VillagerProfession> profession,
                                BiConsumer<ServerLevel, Villager> handler) {
        HANDLERS.put(profession, handler);
    }

    /** Invoked by MixinVillager immediately after Villager.refreshBrain() returns. */
    public static void onBrainRefresh(ServerLevel level, Villager villager) {
        villager.getVillagerData()
            .profession()
            .unwrapKey()
            .ifPresent(key -> {
                BiConsumer<ServerLevel, Villager> handler = HANDLERS.get(key);
                if (handler != null) {
                    handler.accept(level, villager);
                }
            });
    }
}
