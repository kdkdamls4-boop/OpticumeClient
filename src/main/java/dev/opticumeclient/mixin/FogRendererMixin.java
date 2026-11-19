package dev.opticumeclient.mixin;

import dev.opticumeclient.client.modules.visuals.AmbienceModule;
import dev.opticumeclient.client.modules.visuals.AtmosphereModule;
import dev.opticumeclient.client.modules.visuals.ambience.WorldRenderBridge;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.material.FogType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(FogRenderer.class)
public abstract class FogRendererMixin {

    @Inject(method = "setupFog", at = @At("RETURN"))
    private void opticume$modifyFog(Camera camera, int renderDistanceChunks, DeltaTracker deltaTracker,
                                    float darkenWorldAmount, ClientLevel level,
                                    CallbackInfoReturnable<FogData> cir) {
        FogData data = cir.getReturnValue();
        if (data == null) {
            return;
        }

        boolean preserveVanillaFog = camera == null
                || camera.getFluidInCamera() != FogType.NONE
                || (camera.entity() instanceof LivingEntity living
                    && (living.hasEffect(MobEffects.BLINDNESS) || living.hasEffect(MobEffects.DARKNESS)));

        AmbienceModule ambience = AmbienceModule.getInstance();
        if (ambience != null && ambience.isEnabled()) {
            if (!preserveVanillaFog && ambience.isCustomFogActive()) {
                int argb = ambience.resolveFogColor();
                data.color.set(
                        WorldRenderBridge.red(argb),
                        WorldRenderBridge.green(argb),
                        WorldRenderBridge.blue(argb),
                        1.0f
                );
                float start = ambience.getCustomFogStart();
                float end = ambience.getCustomFogEnd();
                data.environmentalStart = start;
                data.environmentalEnd = end;
                data.renderDistanceStart = start;
                data.renderDistanceEnd = end;
                data.skyEnd = end;
                data.cloudEnd = end;
            }
            ambience.recordFogDistances(data.renderDistanceStart, data.renderDistanceEnd, data.color);
        }

        AtmosphereModule atmo = AtmosphereModule.getInstance();
        if (!preserveVanillaFog && atmo != null && atmo.isEnabled() && atmo.isFogEnabled()
                && (ambience == null || !ambience.isCustomFogActive())) {
            int argb = atmo.getEffectiveFogColor();
            float blend = 0.55f;
            float r = WorldRenderBridge.red(argb);
            float g = WorldRenderBridge.green(argb);
            float b = WorldRenderBridge.blue(argb);
            data.color.set(
                    data.color.x + (r - data.color.x) * blend,
                    data.color.y + (g - data.color.y) * blend,
                    data.color.z + (b - data.color.z) * blend,
                    data.color.w
            );
        }
    }
}
