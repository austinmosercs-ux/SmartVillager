package com.smartvillager.mixin;

import com.smartvillager.ai.ProfessionBehaviorRegistry;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.villager.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hooks into Villager.refreshBrain() to allow profession-specific behavior
 * injection via ProfessionBehaviorRegistry.
 *
 * refreshBrain() is called at spawn, on profession change, and on baby-to-adult
 * transition. Injecting at RETURN guarantees the vanilla Brain is fully built
 * before our registry runs, so Brain.addActivity() calls in handlers append
 * on top of the vanilla setup.
 */
@Mixin(Villager.class)
public class MixinVillager {

    @Inject(method = "refreshBrain", at = @At("RETURN"))
    @SuppressWarnings("java:S100") // Mixin convention requires $ separator; regex rule doesn't apply here
    private void smartvillager$onRefreshBrain(ServerLevel level, CallbackInfo ci) {
        ProfessionBehaviorRegistry.onBrainRefresh(level, (Villager)(Object)this);
    }
}
