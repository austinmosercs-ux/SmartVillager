package com.smartvillager.command;

import com.smartvillager.village.SmartVillage;
import com.smartvillager.village.VillageRegistry;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

/** Shared constants and utilities used by all /sv command classes. */
final class DebugHelper {
    private DebugHelper() {}

    static final double VILLAGE_SEARCH_RADIUS = 256.0;
    static final String NO_VILLAGE_MSG = "No village within " + (int) VILLAGE_SEARCH_RADIUS + " blocks.";

    static final String FMT_HP     = "%.1f/%.0f";
    static final String EMPTY_LIST = "  (empty)";

    static final String CMD_AMOUNT = "amount";
    static final String CMD_CLEAR  = "clear";
    static final String CMD_ITEM   = "item";

    static Optional<SmartVillage> findNearestVillage(ServerPlayer player) {
        return VillageRegistry.get(player.level())
            .findNearest(player.blockPosition(), VILLAGE_SEARCH_RADIUS * VILLAGE_SEARCH_RADIUS);
    }

    static Identifier parseItemId(String itemArg) {
        return itemArg.contains(":") ? Identifier.parse(itemArg)
                                     : Identifier.withDefaultNamespace(itemArg);
    }
}
