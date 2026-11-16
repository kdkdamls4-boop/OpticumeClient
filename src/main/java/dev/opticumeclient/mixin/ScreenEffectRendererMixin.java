package dev.opticumeclient.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.opticumeclient.client.modules.optimization.LowFireModule;
import dev.opticumeclient.client.modules.visuals.NoRenderModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(ScreenEffectRenderer.class)
public abstract class ScreenEffectRendererMixin {

    @Unique
    private static final ThreadLocal<Boolean> OPTICUME_FIRE_PUSHED = ThreadLocal.withInitial(() -> false);

    @Inject(method = "submitFire", at = @At("HEAD"), cancellable = true)
    private static void opticume$cancelFire(PoseStack matrices, SubmitNodeCollector collector, TextureAtlasSprite sprite, CallbackInfo ci) {
        NoRenderModule noRender = NoRenderModule.getInstance();
        if (noRender != null && noRender.isFire()) {
            ci.cancel();
            return;
        }
        LowFireModule lowFire = LowFireModule.getInstance();
        if (lowFire != null && lowFire.isEnabled()) {
            matrices.pushPose();
            matrices.translate(0.0f, lowFire.getHeightOffset(), 0.0f);
            OPTICUME_FIRE_PUSHED.set(true);
        }
    }

    @Inject(method = "submitFire", at = @At("TAIL"))
    private static void opticume$restoreFire(PoseStack matrices, SubmitNodeCollector collector, TextureAtlasSprite sprite, CallbackInfo ci) {
        if (Boolean.TRUE.equals(OPTICUME_FIRE_PUSHED.get())) {
            matrices.popPose();
            OPTICUME_FIRE_PUSHED.set(false);
        }
    }

    @Inject(method = "renderItemActivationAnimation", at = @At("HEAD"), cancellable = true)
    private void opticume$cancelTotemAnimation(PlayerRenderState playerRenderState, PoseStack matrices, float partialTick, SubmitNodeCollector collector, CallbackInfo ci) {
        NoRenderModule noRender = NoRenderModule.getInstance();
        if (noRender != null && noRender.isTotem()) {
            ci.cancel();
        }
    }
}
