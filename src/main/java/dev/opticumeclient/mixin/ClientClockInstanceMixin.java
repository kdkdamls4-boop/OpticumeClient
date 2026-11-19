package dev.opticumeclient.mixin;

import dev.opticumeclient.client.modules.visuals.AmbienceModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.ClientClockManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(ClientClockManager.ClientClockInstance.class)
public abstract class ClientClockInstanceMixin {

    @Inject(method = "totalTicks", at = @At("RETURN"), cancellable = true)
    private void opticume$overrideTotalTicks(CallbackInfoReturnable<Long> cir) {
        if (AmbienceModule.shouldOverrideTime()) {
            cir.setReturnValue(AmbienceModule.getInstance().getCustomTimeTicks());
        }
    }

    @Inject(method = "partialTick", at = @At("RETURN"), cancellable = true)
    private void opticume$overridePartialTick(CallbackInfoReturnable<Float> cir) {
        if (AmbienceModule.shouldOverrideTime()) {
            cir.setReturnValue(0.0f);
        }
    }
}
