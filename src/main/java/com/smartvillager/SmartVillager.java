package com.smartvillager;

import com.smartvillager.registration.ModBlocks;
import com.smartvillager.registration.ModItems;
import com.smartvillager.registration.ModPoiTypes;
import com.smartvillager.registration.ModProfessions;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@Mod(SmartVillager.MOD_ID)
public final class SmartVillager {
    public static final String MOD_ID = "smartvillager";

    public SmartVillager(IEventBus modEventBus) {
        ModBlocks.BLOCKS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModPoiTypes.POI_TYPES.register(modEventBus);
        ModProfessions.PROFESSIONS.register(modEventBus);
    }
}
