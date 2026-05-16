package com.smartvillager.village;

import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.npc.villager.VillagerType;
import net.minecraft.world.item.DyeColor;

import java.util.List;
import java.util.Map;

/**
 * Maps VillagerType (biome skin) to the Merchant robe color options defined
 * in the design spec. One color is chosen at random when a village first
 * registers and is fixed for that village permanently.
 *
 * Color table (from CLAUDE.md):
 *   Desert         → Cyan, Green, Lime
 *   Plains         → White, Yellow
 *   Savanna        → Orange, Red, Yellow
 *   Taiga          → Blue, Purple
 *   Snow (tundra)  → Blue, Red, White
 *   Jungle / Swamp → fallback White
 */
public final class MerchantColor {
    private MerchantColor() {}

    private static final Map<ResourceKey<VillagerType>, List<DyeColor>> COLOR_TABLE = Map.of(
        VillagerType.DESERT,  List.of(DyeColor.CYAN, DyeColor.GREEN, DyeColor.LIME),
        VillagerType.PLAINS,  List.of(DyeColor.WHITE, DyeColor.YELLOW),
        VillagerType.SAVANNA, List.of(DyeColor.ORANGE, DyeColor.RED, DyeColor.YELLOW),
        VillagerType.TAIGA,   List.of(DyeColor.BLUE, DyeColor.PURPLE),
        VillagerType.SNOW,    List.of(DyeColor.BLUE, DyeColor.RED, DyeColor.WHITE),
        VillagerType.JUNGLE,  List.of(DyeColor.WHITE),
        VillagerType.SWAMP,   List.of(DyeColor.WHITE)
    );

    public static DyeColor randomFor(ResourceKey<VillagerType> type, RandomSource random) {
        List<DyeColor> options = COLOR_TABLE.getOrDefault(type, List.of(DyeColor.WHITE));
        return options.get(random.nextInt(options.size()));
    }
}
