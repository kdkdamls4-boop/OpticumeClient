package dev.optimusprime.mixin;

import dev.optimusprime.client.modules.visuals.ChamsModule;
import dev.optimusprime.client.modules.visuals.HitColorModule;
import dev.optimusprime.client.modules.visuals.NameTagsModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.entity.LivingEntityRenderer; 
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState; 
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V", at = @At("TAIL"))
    private void optimus$onChamsExtractRenderState(LivingEntity entity, LivingEntityRenderState state, float partialTicks, CallbackInfo ci) {
        ChamsModule chams = ChamsModule.getInstance();
        if (chams != null && chams.isEnabled() && chams.shouldApply(entity) && chams.isOutlineMode()) {
            state.outlineColor = chams.getColorForEntity(entity);
        }
    }

    @Inject(method = "getModelTint", at = @At("HEAD"), cancellable = true)
    private void optimus$onGetMixColor(LivingEntityRenderState state, CallbackInfoReturnable<Integer> cir) {
        HitColorModule hitColor = HitColorModule.getInstance();
        if (hitColor != null && hitColor.isEnabled() && state != null && state.hasRedOverlay) {
            cir.setReturnValue(hitColor.getColor());
            return;
        }

        ChamsModule chams = ChamsModule.getInstance();
        if (chams != null && chams.isEnabled() && chams.isTintActive() && state != null) {
            cir.setReturnValue(chams.getTintColor(state));
        }
    }

    @Inject(method = "shouldShowName(Lnet/minecraft/world/entity/LivingEntity;D)Z", at = @At("HEAD"), cancellable = true)
    private void optimus$onShouldShowName(LivingEntity entity, double distance, CallbackInfoReturnable<Boolean> cir) {
        NameTagsModule nameTags = NameTagsModule.getInstance();
        if (nameTags != null && nameTags.isEnabled() && entity instanceof Player) {
            cir.setReturnValue(false);
        }
    }
}
