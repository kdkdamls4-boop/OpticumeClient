package dev.opticumeclient.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import dev.opticumeclient.client.modules.visuals.AmbienceModule;
import dev.opticumeclient.client.modules.visuals.ambience.SkyPipelines;
import dev.opticumeclient.client.modules.visuals.ambience.WorldRenderBridge;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import net.minecraft.world.level.MoonPhase;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(SkyRenderer.class)
public abstract class SkyRendererMixin {

    @Shadow
    @Final
    private RenderTarget renderTarget;

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void opticume$modifySkyState(ClientLevel level, float partialTick, Camera camera, SkyRenderState state, CallbackInfo ci) {
        AmbienceModule ambience = AmbienceModule.getInstance();
        if (ambience == null || !ambience.isEnabled() || state == null) {
            return;
        }
        if (ambience.isMoonPhaseOverride()) {
            MoonPhase[] phases = MoonPhase.values();
            state.moonPhase = phases[ambience.getMoonPhaseIndex() % phases.length];
        }
        if (ambience.isHideVanillaSky()) {
            state.shouldRenderDarkDisc = false;
        }
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void opticume$renderCustomShaderSky(GpuBufferSlice fogBuffer, SkyRenderState skyState, CallbackInfo ci) {
        AmbienceModule ambience = AmbienceModule.getInstance();
        if (ambience != null && ambience.isShaderSkyActive()) {
            SkyPipelines.getInstance().render(
                    ambience,
                    this.renderTarget,
                    skyState,
                    WorldRenderBridge.getCurrentCamera()
            );
        }
    }

    @Inject(method = "renderSkyDisc", at = @At("HEAD"), cancellable = true)
    private void opticume$cancelSkyDisc(RenderPass pass, org.joml.Vector3fc skyColor, CallbackInfo ci) {
        AmbienceModule ambience = AmbienceModule.getInstance();
        if (ambience != null && ambience.isHideVanillaSky()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderDarkDisc", at = @At("HEAD"), cancellable = true)
    private void opticume$cancelDarkDisc(RenderPass pass, CallbackInfo ci) {
        AmbienceModule ambience = AmbienceModule.getInstance();
        if (ambience != null && ambience.isHideVanillaSky()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderEndSky", at = @At("HEAD"), cancellable = true)
    private void opticume$cancelEndSky(RenderPass pass, CallbackInfo ci) {
        AmbienceModule ambience = AmbienceModule.getInstance();
        if (ambience != null && ambience.isHideVanillaSky()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderEndFlash", at = @At("HEAD"), cancellable = true)
    private void opticume$cancelEndFlash(RenderPass pass, com.mojang.blaze3d.vertex.PoseStack poseStack,
                                         float intensity, float xAngle, float yAngle, CallbackInfo ci) {
        AmbienceModule ambience = AmbienceModule.getInstance();
        if (ambience != null && ambience.isHideVanillaSky()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderSunriseAndSunset", at = @At("HEAD"), cancellable = true)
    private void opticume$cancelSunrise(RenderPass pass, com.mojang.blaze3d.vertex.PoseStack poseStack, float angle, org.joml.Vector4fc color, CallbackInfo ci) {
        AmbienceModule ambience = AmbienceModule.getInstance();
        if (ambience != null && ambience.isHideVanillaSky()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderStars", at = @At("HEAD"), cancellable = true)
    private void opticume$cancelStars(RenderPass pass, float brightness, com.mojang.blaze3d.vertex.PoseStack poseStack, CallbackInfo ci) {
        AmbienceModule ambience = AmbienceModule.getInstance();
        if (ambience != null && ambience.isHideVanillaSky()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderSun", at = @At("HEAD"), cancellable = true)
    private void opticume$cancelSun(RenderPass pass, float brightness, com.mojang.blaze3d.vertex.PoseStack poseStack, CallbackInfo ci) {
        AmbienceModule ambience = AmbienceModule.getInstance();
        if (ambience != null && ambience.isHideSunMoon()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderMoon", at = @At("HEAD"), cancellable = true)
    private void opticume$cancelMoon(RenderPass pass, MoonPhase phase, float brightness, com.mojang.blaze3d.vertex.PoseStack poseStack, CallbackInfo ci) {
        AmbienceModule ambience = AmbienceModule.getInstance();
        if (ambience != null && ambience.isHideSunMoon()) {
            ci.cancel();
        }
    }

    @ModifyArg(
            method = "renderSun",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/SkyRenderer;applyCelestialBodyTransform(Lcom/mojang/blaze3d/vertex/PoseStack;FF)Lorg/joml/Matrix4f;"
            ),
            index = 2
    )
    private float opticume$scaleSun(float size) {
        AmbienceModule ambience = AmbienceModule.getInstance();
        return ambience != null ? size * ambience.getSunScale() : size;
    }

    @ModifyArg(
            method = "renderMoon",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/SkyRenderer;applyCelestialBodyTransform(Lcom/mojang/blaze3d/vertex/PoseStack;FF)Lorg/joml/Matrix4f;"
            ),
            index = 2
    )
    private float opticume$scaleMoon(float size) {
        AmbienceModule ambience = AmbienceModule.getInstance();
        return ambience != null ? size * ambience.getMoonScale() : size;
    }
}
