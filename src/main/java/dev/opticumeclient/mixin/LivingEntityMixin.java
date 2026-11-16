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
}
