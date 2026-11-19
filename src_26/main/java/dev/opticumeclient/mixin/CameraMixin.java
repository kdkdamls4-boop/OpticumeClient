package dev.opticumeclient.mixin;

import dev.opticumeclient.client.modules.visuals.AspectRatioModule;
import dev.opticumeclient.client.modules.visuals.InterpolateF5Module;
import dev.opticumeclient.client.modules.visuals.ZoomModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(Camera.class)
public abstract class CameraMixin {

    @Shadow @Final private Minecraft minecraft;
    @Shadow private boolean detached;
    @Shadow private float yRot;
    @Shadow private float xRot;

    @Shadow protected abstract void move(float zoomOffset, float verticalOffset, float horizontalOffset);
    @Shadow protected abstract void setRotation(float yRot, float xRot);

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

    @ModifyVariable(method = "setupPerspective", at = @At("HEAD"), ordinal = 3, argsOnly = true)
    private float opticume$modifyAspectRatioWidth(float width) {
        AspectRatioModule aspect = AspectRatioModule.getInstance();
        if (aspect != null && aspect.isEnabled() && this.minecraft != null && this.minecraft.getWindow() != null) {
            return aspect.modifyProjectionWidth(width, (float) this.minecraft.getWindow().getHeight());
        }
        return width;
    }

    @Inject(
            method = "alignWithEntity",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/Camera;getMaxZoom(F)F"
            )
    )
    private void opticume$applyInterpolateF5Transform(float partialTick, CallbackInfo ci) {
        InterpolateF5Module f5 = InterpolateF5Module.getInstance();
        if (f5 != null && f5.isEnabled() && this.detached) {
            boolean mirrored = this.minecraft != null && this.minecraft.options.getCameraType().isMirrored();
            // Undo vanilla's instant +180 / -pitch mirror snap so we can apply smooth circular orbit & stabilization
            float baseYaw = mirrored ? this.yRot - 180.0f : this.yRot;
            float basePitch = mirrored ? -this.xRot : this.xRot;

            if (f5.isCameraStabilizationEnabled()) {
                baseYaw = f5.getInterpolatedYaw(partialTick);
                basePitch = f5.getInterpolatedPitch(partialTick);
            }

            if (f5.isFrontOrbitEnabled()) {
                float orbitDeg = f5.getInterpolatedOrbitAngle(partialTick);
                float cosOrbit = (float) Math.cos(Math.toRadians(orbitDeg));
                this.setRotation(baseYaw + orbitDeg, basePitch * cosOrbit);
            } else if (f5.isCameraStabilizationEnabled()) {
                this.setRotation(mirrored ? baseYaw + 180.0f : baseYaw, mirrored ? -basePitch : basePitch);
            }

            float yOffset = f5.getInterpolatedHeightOffset(partialTick);
            if (Math.abs(yOffset) > 0.001f) {
                this.move(0.0f, yOffset, 0.0f);
            }
        }
    }

    @ModifyVariable(method = "getMaxZoom", at = @At("HEAD"), ordinal = 0, argsOnly = true)
    private float opticume$modifyF5TargetDistance(float maxZoom) {
        InterpolateF5Module f5 = InterpolateF5Module.getInstance();
        if (f5 != null && f5.isEnabled() && this.minecraft != null) {
            float partialTick = this.minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(true);
            return f5.getInterpolatedDistance(partialTick);
        }
        return maxZoom;
    }

    @Inject(method = "getMaxZoom", at = @At("HEAD"), cancellable = true)
    private void opticume$onGetMaxZoomNoClip(float maxZoom, CallbackInfoReturnable<Float> cir) {
        InterpolateF5Module f5 = InterpolateF5Module.getInstance();
        if (f5 != null && f5.isNoClip()) {
            cir.setReturnValue(maxZoom);
        }
    }
}
