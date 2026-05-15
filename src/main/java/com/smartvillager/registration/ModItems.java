package com.smartvillager.registration;

import com.smartvillager.SmartVillager;
import net.minecraft.world.item.BlockItem;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModItems {
    private ModItems() {}

    public static final DeferredRegister.Items ITEMS =
        DeferredRegister.createItems(SmartVillager.MOD_ID);

    public static final DeferredItem<BlockItem> BUILDERS_WORKBENCH =
        ITEMS.registerSimpleBlockItem("builders_workbench", ModBlocks.BUILDERS_WORKBENCH);

    public static final DeferredItem<BlockItem> MINING_STATION =
        ITEMS.registerSimpleBlockItem("mining_station", ModBlocks.MINING_STATION);

    public static final DeferredItem<BlockItem> HUNTER_POST =
        ITEMS.registerSimpleBlockItem("hunter_post", ModBlocks.HUNTER_POST);

    public static final DeferredItem<BlockItem> GUARD_POST =
        ITEMS.registerSimpleBlockItem("guard_post", ModBlocks.GUARD_POST);

    public static final DeferredItem<BlockItem> ELDER_PODIUM =
        ITEMS.registerSimpleBlockItem("elder_podium", ModBlocks.ELDER_PODIUM);
}
