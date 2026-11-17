package dev.opticumeclient.mixin;

import dev.opticumeclient.client.modules.visuals.AmbienceModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.material.FogType;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(FogRenderer.class)
public abstract class FogRendererMixin {

    @Inject(method = "setupFog", at = @At("RETURN"))
    private void opticume$overrideFog(Camera camera, int renderDistance, DeltaTracker deltaTracker, float tickDelta, ClientLevel level, CallbackInfoReturnable<FogData> cir) {
        if (camera == null || camera.getFluidInCamera() != FogType.NONE) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && (mc.player.hasEffect(MobEffects.BLINDNESS) || mc.player.hasEffect(MobEffects.DARKNESS))) {
            return;
        }
        if (camera.entity() instanceof LivingEntity living && (living.hasEffect(MobEffects.BLINDNESS) || living.hasEffect(MobEffects.DARKNESS))) {
            return;
        }

        AmbienceModule ambience = AmbienceModule.getInstance();
        if (ambience == null || !ambience.isEnabled() || !ambience.isCustomFogEnabled()) return;

        String fogMode = ambience.getFogMode();
        if (fogMode.equals("Vanilla")) return;

        FogData data = cir.getReturnValue();
        if (data == null) return;

        if (fogMode.equals("Sync Sky")) {
            if (!ambience.getSkyMode().equals("Vanilla")) {
                int skyColor = ambience.getSkyBaseColor();
                float bright = ambience.getSkyBrightness();
                float r = (((skyColor >> 16) & 0xFF) / 255.0f) * bright;
                float g = (((skyColor >> 8) & 0xFF) / 255.0f) * bright;
                float b = ((skyColor & 0xFF) / 255.0f) * bright;
                data.color = new Vector4f(r, g, b, 1.0f);
            }
        } else {
            int color = ambience.getFogBaseColor();
            float bright = ambience.getFogBrightness();
            float r = (((color >> 16) & 0xFF) / 255.0f) * bright;
            float g = (((color >> 8) & 0xFF) / 255.0f) * bright;
            float b = ((color & 0xFF) / 255.0f) * bright;
            data.color = new Vector4f(r, g, b, 1.0f);
        }

        float maxDist = ambience.getFogDistance();
        float density = ambience.getFogDensity();
        data.environmentalStart = maxDist * (1.0f - density);
        data.environmentalEnd = maxDist;
    }
}
