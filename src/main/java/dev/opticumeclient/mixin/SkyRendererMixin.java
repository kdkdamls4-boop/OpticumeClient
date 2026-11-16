package dev.opticumeclient.mixin;

import dev.opticumeclient.client.modules.visuals.AmbienceModule;
import dev.opticumeclient.client.render.ThemeManager;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(SkyRenderer.class)
public abstract class SkyRendererMixin {

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void opticume$overrideSky(ClientLevel level, float partialTicks, Camera camera, SkyRenderState state, CallbackInfo ci) {
        AmbienceModule ambience = AmbienceModule.getInstance();
        if (ambience == null || !ambience.isEnabled()) return;

        // 1. Time of Day override (visual-only, no server desync)
        switch (ambience.getTimeMode()) {
            case "Day" -> {
                state.sunAngle = (float) Math.toRadians(90.0);
                state.moonAngle = (float) Math.toRadians(270.0);
                state.starBrightness = 0.0f;
            }
            case "Sunset" -> {
                state.sunAngle = (float) Math.toRadians(0.0);
                state.moonAngle = (float) Math.toRadians(180.0);
                state.sunriseAndSunsetColor = new Vector4f(1.0f, 0.42f, 0.22f, 0.9f);
                state.starBrightness = 0.3f;
            }
            case "Night" -> {
                state.sunAngle = (float) Math.toRadians(270.0);
                state.moonAngle = (float) Math.toRadians(90.0);
                state.starBrightness = 1.0f;
            }
            case "Custom" -> {
                float angle = (ambience.getCustomTime() / 24000.0f) * (float) (Math.PI * 2.0);
                state.sunAngle = angle;
                state.moonAngle = angle + (float) Math.PI;
            }
        }

        // 2. Custom sky color tint
        if (!ambience.getSkyMode().equals("Vanilla")) {
            int color = ambience.getSkyBaseColor();
            float bright = ambience.getSkyBrightness();
            float r = (((color >> 16) & 0xFF) / 255.0f) * bright;
            float g = (((color >> 8) & 0xFF) / 255.0f) * bright;
            float b = ((color & 0xFF) / 255.0f) * bright;
            state.skyColor = new Vector3f(r, g, b);
        }
    }
}
