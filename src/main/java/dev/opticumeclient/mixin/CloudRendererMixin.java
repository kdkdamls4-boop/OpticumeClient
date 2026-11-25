package dev.opticumeclient.mixin;

import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import dev.opticumeclient.client.modules.visuals.AmbienceModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.renderer.CloudRenderer;
import net.minecraft.client.renderer.oit.OitRenderPassProvider;
import net.minecraft.client.renderer.oit.OitStage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Cancels vanilla block-grid cloud geometry when Ambience custom sky hides vanilla sky,
 * preventing dark/blocky vanilla cloud slabs from cutting holes into custom sky shaders.
 */
@Environment(EnvType.CLIENT)
@Mixin(CloudRenderer.class)
public abstract class CloudRendererMixin {

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void opticume$cancelVanillaClouds(CloudStatus status, RenderPass pass, CallbackInfo ci) {
        AmbienceModule ambience = AmbienceModule.getInstance();
        if (ambience != null && ambience.isHideVanillaSky()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderOit", at = @At("HEAD"), cancellable = true)
    private void opticume$cancelVanillaCloudsOit(CloudStatus status, OitStage stage, GpuTextureView target,
                                                 OitRenderPassProvider.Parameters params, CallbackInfo ci) {
        AmbienceModule ambience = AmbienceModule.getInstance();
        if (ambience != null && ambience.isHideVanillaSky()) {
            ci.cancel();
        }
    }
}
