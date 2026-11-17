package dev.opticumeclient.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.opticumeclient.client.modules.ModuleManager;
import dev.opticumeclient.client.modules.optimization.NoHurtCamModule;
import dev.opticumeclient.client.modules.visuals.NoRenderModule;
import dev.opticumeclient.client.modules.visuals.TargetEspModule;
import dev.opticumeclient.client.modules.visuals.targetesp.BufferPool;
import dev.opticumeclient.client.modules.visuals.targetesp.SurfaceLightingPass;
import dev.opticumeclient.client.modules.visuals.targetesp.TargetEspFrame;
import dev.opticumeclient.client.modules.visuals.targetesp.TargetEspLightning;
import dev.opticumeclient.client.modules.visuals.targetesp.TargetEspRenderer;
import dev.opticumeclient.client.modules.visuals.targetesp.WorldProjection;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.GameRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {

    private static final PoseStack REUSABLE_MATRICES = new PoseStack();

    @Shadow @Final private Minecraft minecraft;
    @Shadow @Final private GameRenderState gameRenderState;
    @Shadow @Final private Camera mainCamera;

    @Unique
    private final Matrix4f opticume$worldProjection = new Matrix4f();

    @ModifyArg(
            method = "renderLevel",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/ProjectionMatrixBuffer;getBuffer(Lorg/joml/Matrix4f;)Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice;"
            ),
            index = 0
    )
    private Matrix4f opticume$captureWorldProjection(Matrix4f projection) {
        this.opticume$worldProjection.set(projection);
        return projection;
    }

    @Inject(
            method = "renderLevel",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/GameRenderer;render3dHud(Lnet/minecraft/client/renderer/state/level/CameraRenderState;Lnet/minecraft/client/renderer/state/level/PlayerRenderState;Lnet/minecraft/client/renderer/state/OptionsRenderState;Z)V"
            )
    )
    private void opticume$renderTargetEspInWorld(DeltaTracker tracker, CallbackInfo ci) {
        if (this.minecraft.player == null || this.minecraft.level == null) {
            return;
        }

        CameraRenderState cameraState = this.gameRenderState.levelRenderState.cameraRenderState;
        PoseStack poseStack = new PoseStack();
        if (cameraState != null && cameraState.orientation != null) {
            poseStack.rotate(new Quaternionf(cameraState.orientation).conjugate());
        } else if (this.mainCamera != null) {
            poseStack.rotate(new Quaternionf(this.mainCamera.rotation()).conjugate());
        }

        Matrix4f viewMatrix = new Matrix4f(poseStack.last().pose());
        Matrix4f projMatrix = new Matrix4f(this.opticume$worldProjection);
        WorldProjection.set(viewMatrix, projMatrix);

        float partialTicks = tracker.getGameTimeDeltaPartialTick(false);
        TargetEspFrame frame = new TargetEspFrame(
                tracker,
                poseStack,
                this.mainCamera.position(),
                partialTicks,
                projMatrix,
                viewMatrix
        );

        TargetEspRenderer.beginWorldFrame();
        TargetEspModule targetEsp = TargetEspModule.getInstance();
        if (targetEsp != null && targetEsp.isEnabled()) {
            targetEsp.renderWorld(frame);
        } else {
            TargetEspRenderer.renderFadeOut(frame);
        }
    }

    @Inject(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/LevelRenderer;blitEntityOutline()V",
                    shift = At.Shift.AFTER
            )
    )
    private void opticume$renderDeferredTargetEsp(DeltaTracker tracker, boolean renderLevel, CallbackInfo ci) {
        TargetEspRenderer.renderDeferred();
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void opticume$endTargetEspFrame(DeltaTracker tracker, boolean renderLevel, CallbackInfo ci) {
        SurfaceLightingPass.resetFrame();
        TargetEspLightning.endFrame();
        BufferPool.getInstance().clear();
    }

    @Inject(method = "resetData", at = @At("HEAD"))
    private void opticume$onResetData(CallbackInfo ci) {
        TargetEspRenderer.releaseGpuResources();
    }

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
