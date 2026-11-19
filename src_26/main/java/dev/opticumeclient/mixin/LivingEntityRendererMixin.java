package dev.opticumeclient.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.opticumeclient.client.modules.visuals.GlowEspModule;
import dev.opticumeclient.client.modules.visuals.NameTagsModule;
import dev.opticumeclient.client.modules.visuals.SeeInvisiblesModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin<T extends LivingEntity, S extends LivingEntityRenderState> {

    @Shadow
    protected abstract RenderType getRenderType(S state, boolean showBody, boolean translucent, boolean showOutline);

    @Unique
    private static final ThreadLocal<LivingEntityRenderState> OPTICUME_ACTIVE_STATE = new ThreadLocal<>();

    @Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V", at = @At("HEAD"))
    private void opticume$captureSubmitState(S state, PoseStack matrices, SubmitNodeCollector collector, CameraRenderState camera, CallbackInfo ci) {
        OPTICUME_ACTIVE_STATE.set(state);
    }

    @Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V", at = @At("RETURN"))
    private void opticume$releaseSubmitState(S state, PoseStack matrices, SubmitNodeCollector collector, CameraRenderState camera, CallbackInfo ci) {
        OPTICUME_ACTIVE_STATE.remove();
    }

    @Redirect(
            method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/entity/LivingEntityRenderer;getRenderType(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;ZZZ)Lnet/minecraft/client/renderer/rendertype/RenderType;"
            )
    )
    private RenderType opticume$makeInvisiblesTranslucent(LivingEntityRenderer<?, S, ?> instance, S state, boolean showBody, boolean translucent, boolean showOutline) {
        SeeInvisiblesModule seeInvis = SeeInvisiblesModule.getInstance();
        if (!showBody && !translucent && seeInvis != null && seeInvis.shouldShow(state)) {
            translucent = true;
        }
        return this.getRenderType(state, showBody, translucent, showOutline);
    }

    @ModifyArg(
            method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;IIILnet/minecraft/client/renderer/texture/UvMapping;I)V"
            ),
            index = 6
    )
    private int opticume$overrideInvisibleModelTint(int tintColor) {
        LivingEntityRenderState state = OPTICUME_ACTIVE_STATE.get();
        SeeInvisiblesModule seeInvis = SeeInvisiblesModule.getInstance();
        if (state != null && seeInvis != null && seeInvis.shouldShow(state)) {
            return seeInvis.applyAlphaTint(tintColor);
        }
        return tintColor;
    }

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V", at = @At("TAIL"))
    private void opticume$onGlowEspExtractRenderState(LivingEntity entity, LivingEntityRenderState state, float partialTicks, CallbackInfo ci) {
        GlowEspModule glow = GlowEspModule.getInstance();
        if (glow != null && glow.shouldGlow(entity)) {
            state.outlineColor = 0xFF000000 | glow.color(entity);
        }
    }

    @Inject(method = "shouldShowName(Lnet/minecraft/world/entity/LivingEntity;D)Z", at = @At("HEAD"), cancellable = true)
    private void opticume$onShouldShowName(LivingEntity entity, double distance, CallbackInfoReturnable<Boolean> cir) {
        NameTagsModule nameTags = NameTagsModule.getInstance();
        if (nameTags != null && nameTags.shouldHideVanillaTag(entity)) {
            cir.setReturnValue(false);
        }
    }
}
