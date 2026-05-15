package com.smartvillager.registration;

import com.smartvillager.SmartVillager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModBlocks {
    private ModBlocks() {}

    public static final DeferredRegister.Blocks BLOCKS =
        DeferredRegister.createBlocks(SmartVillager.MOD_ID);

    public static final DeferredBlock<Block> BUILDERS_WORKBENCH =
        BLOCKS.registerSimpleBlock("builders_workbench", () -> BlockBehaviour.Properties.of()
            .mapColor(MapColor.WOOD)
            .strength(2.5f)
            .sound(SoundType.WOOD));

    public static final DeferredBlock<Block> MINING_STATION =
        BLOCKS.registerSimpleBlock("mining_station", () -> BlockBehaviour.Properties.of()
            .mapColor(MapColor.STONE)
            .strength(3.0f)
            .sound(SoundType.STONE));

    public static final DeferredBlock<Block> HUNTER_POST =
        BLOCKS.registerSimpleBlock("hunter_post", () -> BlockBehaviour.Properties.of()
            .mapColor(MapColor.WOOD)
            .strength(2.5f)
            .sound(SoundType.WOOD));

    public static final DeferredBlock<Block> GUARD_POST =
        BLOCKS.registerSimpleBlock("guard_post", () -> BlockBehaviour.Properties.of()
            .mapColor(MapColor.STONE)
            .strength(3.5f)
            .sound(SoundType.STONE));

    public static final DeferredBlock<Block> ELDER_PODIUM =
        BLOCKS.registerSimpleBlock("elder_podium", () -> BlockBehaviour.Properties.of()
            .mapColor(MapColor.GOLD)
            .strength(4.0f)
            .sound(SoundType.WOOD));
}
