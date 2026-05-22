package com.smartvillager.registration;

import com.smartvillager.SmartVillager;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Registers the SmartVillager creative mode tab.
 *
 * Currently empty — no custom items exist yet. Add items to the displayItems
 * callback as mod blocks and items are introduced. The tab is registered now
 * so the infrastructure is in place without needing a separate branch later.
 */
public final class ModCreativeTabs {
    private ModCreativeTabs() {}

    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
        DeferredRegister.create(Registries.CREATIVE_MODE_TAB, SmartVillager.MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> SMARTVILLAGER_TAB =
        CREATIVE_MODE_TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.smartvillager.main"))
            .icon(() -> new ItemStack(Items.EMERALD))
            .displayItems((params, output) -> {
                // Add mod items here as custom blocks and items are introduced.
            })
            .build());
}
