package dev.optimusprime.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.optimusprime.client.modules.visuals.NoRenderModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(ScreenEffectRenderer.class)
public abstract class ScreenEffectRendererMixin {

    @Inject(method = "submitFire", at = @At("HEAD"), cancellable = true)
    private static void optimus$cancelFire(PoseStack matrices, SubmitNodeCollector collector, TextureAtlasSprite sprite, CallbackInfo ci) {
        NoRenderModule noRender = NoRenderModule.getInstance();
        if (noRender != null && noRender.isFire()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderItemActivationAnimation", at = @At("HEAD"), cancellable = true)
    private void optimus$cancelTotemAnimation(PlayerRenderState playerRenderState, PoseStack matrices, float partialTick, SubmitNodeCollector collector, CallbackInfo ci) {
        NoRenderModule noRender = NoRenderModule.getInstance();
        if (noRender != null && noRender.isTotem()) {
            ci.cancel();
        }
    }
}
