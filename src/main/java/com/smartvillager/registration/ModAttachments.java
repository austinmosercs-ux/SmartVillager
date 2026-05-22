package com.smartvillager.registration;

import com.smartvillager.SmartVillager;
import com.smartvillager.health.VillagerHealth;
import com.smartvillager.hunger.VillagerHunger;
import com.smartvillager.inventory.VillagerBackpack;
import com.smartvillager.personality.VillagerPersonality;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class ModAttachments {
    private ModAttachments() {}

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
        DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, SmartVillager.MOD_ID);

    // Attached to every Villager entity on first access.
    // Not serialized yet — NeoForge 26.x IAttachmentSerializer uses ValueInput/ValueOutput
    // whose exact API needs confirmation before implementing. Persistence will be added
    // once the correct read/write method signatures are established.
    // Death behavior (clear vs. drop vs. keep) is handled in feature/health-system.
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<VillagerBackpack>>
        VILLAGER_BACKPACK = ATTACHMENT_TYPES.register("villager_backpack", () ->
            AttachmentType.builder(VillagerBackpack::new).build()
        );

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<VillagerHunger>>
        VILLAGER_HUNGER = ATTACHMENT_TYPES.register("villager_hunger", () ->
            AttachmentType.builder(VillagerHunger::new).build()
        );

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<VillagerHealth>>
        VILLAGER_HEALTH = ATTACHMENT_TYPES.register("villager_health", () ->
            AttachmentType.builder(VillagerHealth::new).build()
        );

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<VillagerPersonality>>
        VILLAGER_PERSONALITY = ATTACHMENT_TYPES.register("villager_personality", () ->
            AttachmentType.builder(() -> new VillagerPersonality()).build()
        );
}
