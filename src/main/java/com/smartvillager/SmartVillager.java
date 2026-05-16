package com.smartvillager;

import com.smartvillager.registration.ModAttachments;
import com.smartvillager.registration.ModProfessions;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@Mod(SmartVillager.MOD_ID)
@SuppressWarnings("java:S1118") // NeoForge instantiates this class via @Mod reflection; the public constructor is required
public final class SmartVillager {
    public static final String MOD_ID = "smartvillager";

    public SmartVillager(IEventBus modEventBus) {
        ModProfessions.PROFESSIONS.register(modEventBus);
        ModAttachments.ATTACHMENT_TYPES.register(modEventBus);
    }
}
