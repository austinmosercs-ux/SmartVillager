package com.smartvillager.registration;

import com.smartvillager.SmartVillager;
import com.smartvillager.inventory.VillagerBackpack;
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
}
