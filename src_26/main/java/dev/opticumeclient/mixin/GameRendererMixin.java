package dev.opticumeclient.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.opticumeclient.client.modules.ModuleManager;
import dev.opticumeclient.client.modules.optimization.NoHurtCamModule;
import dev.opticumeclient.client.modules.visuals.NoRenderModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {

    private static final PoseStack REUSABLE_MATRICES = new PoseStack();

    @Inject(method = "renderLevel", at = @At("TAIL"))
    private void opticume$onRenderWorld(CallbackInfo ci) {
        while (!REUSABLE_MATRICES.isEmpty()) {
            REUSABLE_MATRICES.popPose();
        }
        REUSABLE_MATRICES.setIdentity();
        float partialTick = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true);
        ModuleManager.getInstance().onRender3D(REUSABLE_MATRICES, partialTick);
    }

    @Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true)
    private void opticume$onTiltViewWhenHurt(CameraRenderState state, PoseStack matrices, CallbackInfo ci) {
        NoHurtCamModule noHurtCam = NoHurtCamModule.getInstance();
        if (noHurtCam != null && noHurtCam.isEnabled()) {
            ci.cancel();
            return;
        }
        NoRenderModule noRender = NoRenderModule.getInstance();
        if (noRender != null && noRender.isHurtCam()) {
            ci.cancel();
        }
    }
}
