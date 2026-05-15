package com.smartvillager;

import com.smartvillager.registration.ModProfessions;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@Mod(SmartVillager.MOD_ID)
public final class SmartVillager {
    public static final String MOD_ID = "smartvillager";

    public SmartVillager(IEventBus modEventBus) {
        ModProfessions.PROFESSIONS.register(modEventBus);
    }
}
