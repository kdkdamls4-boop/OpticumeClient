package dev.opticumeclient.mixin;

import dev.opticumeclient.client.modules.ModuleManager;
import dev.opticumeclient.client.modules.movement.NoJumpDelayModule;
import dev.opticumeclient.client.modules.visuals.SwingAnimationsModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
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
            SwingAnimationsModule anim = SwingAnimationsModule.getInstance();
            if (anim != null && anim.isEnabled()) {
                int duration = anim.swingDuration(true);
                if (duration > 0) {
                    cir.setReturnValue(duration);
                }
            }
        }
    }

    @Inject(method = "completeUsingItem", at = @At("HEAD"))
    private void opticume$onCompleteUsingItem(CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self == Minecraft.getInstance().player) {
            dev.opticumeclient.client.modules.other.AssistModule.onFinishUsing(self.getUseItem());
        }
    }
}
