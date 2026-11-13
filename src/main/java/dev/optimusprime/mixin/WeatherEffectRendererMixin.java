package dev.optimusprime.mixin;

import com.mojang.renderpearl.api.commands.RenderPass;
import dev.optimusprime.client.modules.visuals.NoRenderModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.WeatherEffectRenderer;
import net.minecraft.client.renderer.state.level.WeatherRenderState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(WeatherEffectRenderer.class)
public abstract class WeatherEffectRendererMixin {

    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
    private void optimus$cancelWeatherExtract(ClientLevel level, float partialTick, Vec3 cameraPos, WeatherRenderState state, CallbackInfo ci) {
        NoRenderModule noRender = NoRenderModule.getInstance();
        if (noRender != null && noRender.isWeather()) {
            ci.cancel();
        }
    }

    @Inject(method = "render(Lnet/minecraft/client/renderer/state/level/WeatherRenderState;Lcom/mojang/renderpearl/api/commands/RenderPass;)V", at = @At("HEAD"), cancellable = true)
    private void optimus$cancelWeatherRender(WeatherRenderState state, RenderPass renderPass, CallbackInfo ci) {
        NoRenderModule noRender = NoRenderModule.getInstance();
        if (noRender != null && noRender.isWeather()) {
            ci.cancel();
        }
    }
}
