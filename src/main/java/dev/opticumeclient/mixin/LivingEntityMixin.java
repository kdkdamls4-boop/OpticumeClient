package dev.opticumeclient.mixin;

import dev.opticumeclient.client.modules.ModuleManager;
import dev.opticumeclient.client.modules.movement.NoJumpDelayModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.opticumeclient.client.modules.animations.OldAnimationsModule;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {

    @Shadow
    private int noJumpDelay;

    @Inject(method = "aiStep", at = @At("HEAD"))
    private void opticume$clearJumpDelay(CallbackInfo ci) {
        if ((Object) this == Minecraft.getInstance().player) {
            NoJumpDelayModule noJumpDelayMod = ModuleManager.getInstance().getModule(NoJumpDelayModule.class);
            if (noJumpDelayMod != null && noJumpDelayMod.isEnabled()) {
                this.noJumpDelay = 0;
            }
        }
    }

    @Inject(method = "getModifiedSwingDuration", at = @At("RETURN"), cancellable = true)
    private void opticume$scaleSwingDuration(CallbackInfoReturnable<Integer> cir) {
        if ((Object) this == Minecraft.getInstance().player) {
            OldAnimationsModule anim = OldAnimationsModule.getInstance();
            if (anim == null) {
                anim = ModuleManager.getInstance().getModule(OldAnimationsModule.class);
            }
            if (anim != null && anim.isEnabled()) {
                float speed = anim.getSwingSpeed();
                if (speed > 0.05f) {
                    int original = cir.getReturnValueI();
                    cir.setReturnValue(Math.max(1, Math.round(original / speed)));
                }
            }
        }
    }
}
