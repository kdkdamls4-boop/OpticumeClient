package dev.opticumeclient.mixin;

import dev.opticumeclient.client.modules.ModuleManager;
import dev.opticumeclient.client.modules.visuals.FullbrightModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import net.minecraft.client.renderer.state.LightmapRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(LightmapRenderStateExtractor.class)
public abstract class LightmapRenderStateExtractorMixin {

    @Inject(method = "extract", at = @At("TAIL"))
    private void opticume$overrideLightmap(LightmapRenderState lightmapRenderState, float partialTicks, CallbackInfo ci) {
        FullbrightModule fullbright = FullbrightModule.getInstance();
        if (fullbright == null) {
            fullbright = ModuleManager.getInstance().getModule(FullbrightModule.class);
        }
        if (fullbright != null && fullbright.isLightmapMode()) {
            lightmapRenderState.brightness = Math.max(lightmapRenderState.brightness, fullbright.getBrightness());
            lightmapRenderState.needsUpdate = true;
        }
    }
}
