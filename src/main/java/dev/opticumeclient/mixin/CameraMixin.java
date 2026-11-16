package dev.opticumeclient.mixin;

import dev.opticumeclient.client.modules.visuals.ZoomModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(Camera.class)
public abstract class CameraMixin {

    @Inject(method = "calculateFov", at = @At("RETURN"), cancellable = true)
    private void opticume$applyZoomFov(float partialTick, CallbackInfoReturnable<Float> cir) {
        ZoomModule zoom = ZoomModule.getInstance();
        if (zoom != null) {
            float adjusted = zoom.applyZoomFov(cir.getReturnValue());
            if (adjusted != cir.getReturnValue()) {
                cir.setReturnValue(adjusted);
            }
        }
    }
}
