package dev.opticumeclient.mixin;

import com.mojang.blaze3d.platform.FramerateLimitTracker;
import dev.opticumeclient.client.modules.optimization.OptimizationCoordinator;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(FramerateLimitTracker.class)
public abstract class FramerateLimitTrackerMixin {

    @Inject(method = "getFramerateLimit", at = @At("RETURN"), cancellable = true)
    private void opticume$limitUnfocusedFps(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(OptimizationCoordinator.limitBackgroundFramerate(cir.getReturnValue()));
    }
}
