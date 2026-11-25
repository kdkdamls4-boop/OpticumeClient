package dev.opticumeclient.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import dev.opticumeclient.client.modules.ModuleManager;
import dev.opticumeclient.client.modules.visuals.AspectRatioModule;
import dev.opticumeclient.client.modules.visuals.BlockESPModule;
import dev.opticumeclient.client.modules.visuals.BlockOverlayModule;
import dev.opticumeclient.client.modules.visuals.ChinaHatModule;
import dev.opticumeclient.client.modules.visuals.FireworkESPModule;
import dev.opticumeclient.client.modules.visuals.HandsModule;
import dev.opticumeclient.client.modules.visuals.JumpCirclesModule;
import dev.opticumeclient.client.modules.visuals.KillEffectModule;
import dev.opticumeclient.client.modules.visuals.NoRenderModule;
import dev.opticumeclient.client.modules.visuals.ObjectInfoModule;
import dev.opticumeclient.client.modules.visuals.ParticlesModule;
import dev.opticumeclient.client.modules.visuals.PopChamsModule;
import dev.opticumeclient.client.modules.visuals.PotsTimerModule;
import dev.opticumeclient.client.modules.visuals.PredictionsModule;
import dev.opticumeclient.client.modules.visuals.TargetEspModule;
import dev.opticumeclient.client.modules.visuals.TntTimerModule;
import dev.opticumeclient.client.modules.visuals.TrailsModule;
import dev.opticumeclient.client.modules.visuals.hands.HandsEffectRenderer;
import dev.opticumeclient.client.modules.visuals.targetesp.BufferPool;
import dev.opticumeclient.client.modules.visuals.targetesp.SurfaceLightingPass;
import dev.opticumeclient.client.modules.visuals.targetesp.TargetEspFrame;
import dev.opticumeclient.client.modules.visuals.targetesp.TargetEspLightning;
import dev.opticumeclient.client.modules.visuals.targetesp.TargetEspRenderer;
import dev.opticumeclient.client.modules.visuals.targetesp.WorldProjection;
import dev.opticumeclient.client.render.KawaseBlurRenderer;
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
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {

    private static final PoseStack REUSABLE_MATRICES = new PoseStack();

    @Shadow @Final private Minecraft minecraft;
    @Shadow @Final private GameRenderState gameRenderState;
    @Shadow @Final private Camera mainCamera;
    @Shadow @Final private RenderTarget mainRenderTarget;

    @Unique
    private final Matrix4f opticume$worldProjection = new Matrix4f();

    @Inject(method = "shouldRenderBlockOutline", at = @At("HEAD"), cancellable = true)
    private void opticume$hideVanillaBlockOutline(CallbackInfoReturnable<Boolean> cir) {
        if (BlockOverlayModule.hidesVanillaOutline()) {
            cir.setReturnValue(false);
        }
    }

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
    private void opticume$renderTargetEspInWorld(CallbackInfo ci) {
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

        DeltaTracker tracker = this.minecraft.getDeltaTracker();
        float partialTicks = tracker.getGameTimeDeltaPartialTick(true);
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

        PredictionsModule predictions = PredictionsModule.getInstance();
        if (predictions != null && predictions.isEnabled()) {
            predictions.renderWorld(frame);
        }

        BlockOverlayModule blockOverlay = BlockOverlayModule.getInstance();
        if (blockOverlay != null && blockOverlay.isEnabled()) {
            blockOverlay.renderWorld(frame);
        }

        ParticlesModule particles = ParticlesModule.getInstance();
        if (particles != null && particles.isEnabled()) {
            particles.renderWorld(frame);
        }

        ChinaHatModule chinaHat = ChinaHatModule.getInstance();
        if (chinaHat != null && chinaHat.isEnabled()) {
            chinaHat.renderWorld(frame);
        }

        JumpCirclesModule jumpCircles = JumpCirclesModule.getInstance();
        if (jumpCircles != null && jumpCircles.isEnabled()) {
            jumpCircles.renderWorld(frame);
        }

        PopChamsModule popChams = PopChamsModule.getInstance();
        if (popChams != null && popChams.isEnabled()) {
            popChams.renderWorld(frame);
        }

        TrailsModule trails = TrailsModule.getInstance();
        if (trails != null && trails.isEnabled()) {
            trails.renderWorld(frame);
        }

        KillEffectModule killEffect = KillEffectModule.getInstance();
        if (killEffect != null && killEffect.isEnabled()) {
            killEffect.renderWorld(frame);
        }

        ObjectInfoModule objInfo = ObjectInfoModule.getInstance();
        if (objInfo != null && objInfo.isEnabled()) {
            objInfo.renderWorld(frame);
        }

        BlockESPModule blockEsp = BlockESPModule.getInstance();
        if (blockEsp != null && blockEsp.isEnabled()) {
            blockEsp.renderWorld(frame);
        }

        TntTimerModule tntTimer = TntTimerModule.getInstance();
        if (tntTimer != null && tntTimer.isEnabled()) {
            tntTimer.renderWorld(frame);
        }

        PotsTimerModule potsTimer = PotsTimerModule.getInstance();
        if (potsTimer != null && potsTimer.isEnabled()) {
            potsTimer.renderWorld(frame);
        }

        FireworkESPModule fireworkEsp = FireworkESPModule.getInstance();
        if (fireworkEsp != null && fireworkEsp.isEnabled()) {
            fireworkEsp.renderWorld(frame);
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
    private void opticume$renderDeferredTargetEsp(CallbackInfo ci) {
        TargetEspRenderer.renderDeferred();
    }

    @Inject(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/render/GuiRenderer;render()V"
            )
    )
    private void opticume$captureWorldBlurBeforeGui(CallbackInfo ci) {
        if (this.minecraft.level != null && this.minecraft.player != null) {
            KawaseBlurRenderer.onBeforeGuiRender();
        }
    }

    @Inject(method = "processBlurEffect", at = @At("HEAD"), cancellable = true)
    private void opticume$captureMenuBlur(CallbackInfo ci) {
        if (KawaseBlurRenderer.consumeMenuBlurRequest()) {
            KawaseBlurRenderer.captureMenuNow();
            ci.cancel();
        }
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void opticume$endTargetEspFrame(CallbackInfo ci) {
        SurfaceLightingPass.resetFrame();
        TargetEspLightning.endFrame();
        BufferPool.getInstance().clear();
    }

    @Inject(method = "resetData", at = @At("HEAD"))
    private void opticume$onResetData(CallbackInfo ci) {
        TargetEspRenderer.releaseGpuResources();
        KawaseBlurRenderer.releaseGpuResources();
        HandsEffectRenderer.getInstance().releaseGpuResources();
        dev.opticumeclient.client.modules.visuals.ambience.SkyPipelines.getInstance().releaseGpuResources();
        dev.opticumeclient.client.modules.visuals.ambience.AmbienceSaturationProgram.getInstance().releaseGpuResources();
        dev.opticumeclient.client.modules.visuals.ambience.FogBlurRenderer.getInstance().releaseGpuResources();
        dev.opticumeclient.client.modules.visuals.ambience.WetShaderRenderer.getInstance().releaseGpuResources();
        dev.opticumeclient.client.modules.visuals.atmosphere.AtmosphereRenderer.getInstance().releaseGpuResources();
        dev.opticumeclient.client.modules.visuals.ambience.MotionBlurRenderer.getInstance().releaseGpuResources();
        JumpCirclesModule jumpCircles = JumpCirclesModule.getInstance();
        if (jumpCircles != null) {
            jumpCircles.releaseGpuResources();
        }
    }

    @Redirect(
            method = "renderItemInHand",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/pipeline/RenderTarget;getColorTextureView()Lcom/mojang/renderpearl/api/textures/GpuTextureView;"
            )
    )
    private GpuTextureView opticume$redirectHandsColorTarget(RenderTarget instance) {
        if (HandsModule.isCapturingActive()) {
            TextureTarget capture = HandsModule.getActiveCaptureTarget();
            if (capture != null && capture.getColorTextureView() != null) {
                return capture.getColorTextureView();
            }
        }
        return instance.getColorTextureView();
    }

    @ModifyArg(
            method = "renderItemInHand",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/renderpearl/api/commands/CommandEncoder;createRenderPass(Ljava/util/function/Supplier;Lcom/mojang/renderpearl/api/textures/GpuTextureView;Ljava/util/Optional;Lcom/mojang/renderpearl/api/textures/GpuTextureView;Ljava/util/OptionalDouble;)Lcom/mojang/renderpearl/api/commands/RenderPass;"
            ),
            index = 3
    )
    private GpuTextureView opticume$redirectHandsDepthTarget(GpuTextureView vanillaDepth) {
        if (HandsModule.isCapturingActive()) {
            TextureTarget capture = HandsModule.getActiveCaptureTarget();
            if (capture != null && capture.getDepthTextureView() != null) {
                return capture.getDepthTextureView();
            }
        }
        return vanillaDepth;
    }

    @Inject(method = "renderItemInHand", at = @At("RETURN"))
    private void opticume$compositeCapturedHands(CallbackInfo ci) {
        if (HandsModule.isCapturingActive()) {
            HandsModule.onEndCapture(this.mainRenderTarget);
        }
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
        NoRenderModule noRender = NoRenderModule.getInstance();
        if (noRender != null && noRender.isHurtCam()) {
            ci.cancel();
        }
    }

    @ModifyArg(
            method = "render3dHud",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/Projection;setupPerspective(FFFFF)V"
            ),
            index = 3
    )
    private float opticume$modifyHud3dAspectRatioWidth(float width) {
        AspectRatioModule aspect = AspectRatioModule.getInstance();
        if (aspect != null && aspect.isAffectHands() && this.minecraft != null && this.minecraft.getWindow() != null) {
            return aspect.modifyProjectionWidth(width, (float) this.minecraft.getWindow().getHeight());
        }
        return width;
    }
}
