package dev.optimusprime.mixin;

import dev.optimusprime.client.modules.visuals.AmbienceModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(FogRenderer.class)
public abstract class FogRendererMixin {

    @Inject(method = "setupFog", at = @At("RETURN"))
    private void optimus$overrideFog(Camera camera, int renderDistance, DeltaTracker deltaTracker, float tickDelta, ClientLevel level, CallbackInfoReturnable<FogData> cir) {
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
