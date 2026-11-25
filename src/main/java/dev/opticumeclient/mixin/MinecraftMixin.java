package dev.opticumeclient.mixin;

import dev.opticumeclient.client.bots.BotContext;
import dev.opticumeclient.client.bots.BotSessionManager;
import dev.opticumeclient.client.modules.visuals.GlowEspModule;
import dev.opticumeclient.client.modules.visuals.ambience.AmbienceSaturationProgram;
import dev.opticumeclient.client.modules.visuals.ambience.FogBlurRenderer;
import dev.opticumeclient.client.modules.visuals.ambience.SkyPipelines;
import dev.opticumeclient.client.modules.visuals.ambience.WetShaderRenderer;
import dev.opticumeclient.client.modules.visuals.atmosphere.AtmosphereRenderer;
import dev.opticumeclient.client.modules.visuals.hands.HandsEffectRenderer;
import dev.opticumeclient.client.modules.visuals.targetesp.BufferPool;
import dev.opticumeclient.client.modules.visuals.targetesp.TargetEspRenderer;
import dev.opticumeclient.client.render.KawaseBlurRenderer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {

    @Inject(method = "tick", at = @At("HEAD"))
    private void opticume$tickBotSessions(CallbackInfo ci) {
        BotSessionManager.getInstance().tick();
    }

    @Inject(
            method = "runTick",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/network/PacketProcessor;processQueuedPackets()V", shift = At.Shift.AFTER)
    )
    private void opticume$drainForegroundBotPackets(boolean advanceGameTime, CallbackInfo ci) {
        BotSessionManager.getInstance().drainForegroundPackets();
    }

    @Inject(method = "setLevel", at = @At("HEAD"), cancellable = true)
    private void opticume$botSetLevel(ClientLevel level, CallbackInfo ci) {
        if (BotContext.isBackgroundContextActive()) {
            ((Minecraft) (Object) this).level = level;
            ci.cancel();
        }
    }

    @Inject(method = "setCameraEntity", at = @At("HEAD"), cancellable = true)
    private void opticume$botSetCamera(Entity cameraEntity, CallbackInfo ci) {
        if (BotContext.isBackgroundContextActive()) {
            ci.cancel();
        }
    }

    @Inject(method = "shouldEntityAppearGlowing", at = @At("RETURN"), cancellable = true)
    private void opticume$glowEspTargets(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ()) {
            GlowEspModule glow = GlowEspModule.getInstance();
            if (glow != null && glow.shouldGlow(entity)) {
                cir.setReturnValue(true);
            }
        }
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void opticume$releaseGpuResourcesOnClose(CallbackInfo ci) {
        try {
            TargetEspRenderer.releaseGpuResources();
            KawaseBlurRenderer.releaseGpuResources();
            HandsEffectRenderer.getInstance().releaseGpuResources();
            SkyPipelines.getInstance().releaseGpuResources();
            AmbienceSaturationProgram.getInstance().releaseGpuResources();
            FogBlurRenderer.getInstance().releaseGpuResources();
            WetShaderRenderer.getInstance().releaseGpuResources();
            AtmosphereRenderer.getInstance().releaseGpuResources();
            dev.opticumeclient.client.modules.visuals.ambience.MotionBlurRenderer.getInstance().releaseGpuResources();
            dev.opticumeclient.client.modules.visuals.JumpCirclesModule jumpCircles =
                    dev.opticumeclient.client.modules.visuals.JumpCirclesModule.getInstance();
            if (jumpCircles != null) {
                jumpCircles.releaseGpuResources();
            }
            BufferPool.getInstance().close();
        } catch (Throwable ignored) {
        }
    }
}
