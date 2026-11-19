package dev.opticumeclient.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import dev.opticumeclient.client.modules.visuals.GlowEspModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.ShaderManager;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;

@Environment(EnvType.CLIENT)
@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {

    @Shadow
    @Final
    private RenderTarget entityOutlineTarget;

    @Shadow
    private boolean currentFrameRendersEntityOutline;

    @Inject(method = "render", at = @At("HEAD"))
    private void opticume$onRenderLevelHead(
            com.mojang.blaze3d.resource.GraphicsResourceAllocator allocator,
            boolean renderBlockOutline,
            net.minecraft.client.renderer.state.level.CameraRenderState cameraRenderState,
            com.mojang.renderpearl.api.buffers.GpuBufferSlice fogBuffer,
            org.joml.Vector4f fogColor,
            boolean renderOutline,
            boolean shouldDrawChunkBorders,
            CallbackInfo ci) {
        dev.opticumeclient.client.modules.visuals.ambience.WorldRenderBridge.setCurrentCamera(cameraRenderState);
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void opticume$onRenderLevelTail(
            com.mojang.blaze3d.resource.GraphicsResourceAllocator allocator,
            boolean renderBlockOutline,
            net.minecraft.client.renderer.state.level.CameraRenderState cameraRenderState,
            com.mojang.renderpearl.api.buffers.GpuBufferSlice fogBuffer,
            org.joml.Vector4f fogColor,
            boolean renderOutline,
            boolean shouldDrawChunkBorders,
            CallbackInfo ci) {
        dev.opticumeclient.client.modules.visuals.AmbienceModule ambience =
                dev.opticumeclient.client.modules.visuals.AmbienceModule.getInstance();
        if (ambience != null && ambience.isEnabled()) {
            dev.opticumeclient.client.modules.visuals.ambience.WetShaderRenderer.getInstance().render(ambience, cameraRenderState);
            dev.opticumeclient.client.modules.visuals.ambience.FogBlurRenderer.getInstance().render(ambience, cameraRenderState);
        }
        dev.opticumeclient.client.modules.visuals.AtmosphereModule atmosphere =
                dev.opticumeclient.client.modules.visuals.AtmosphereModule.getInstance();
        if (atmosphere != null && atmosphere.isEnabled()) {
            dev.opticumeclient.client.modules.visuals.atmosphere.AtmosphereRenderer.getInstance().render(atmosphere, cameraRenderState);
        }
        dev.opticumeclient.client.modules.visuals.JumpCirclesModule jumpCircles =
                dev.opticumeclient.client.modules.visuals.JumpCirclesModule.getInstance();
        if (jumpCircles != null && jumpCircles.isEnabled()) {
            jumpCircles.renderPostFx(cameraRenderState);
        }
        if (ambience != null && ambience.isEnabled()) {
            dev.opticumeclient.client.modules.visuals.ambience.AmbienceSaturationProgram.getInstance().render(ambience);
        }
    }

    @Redirect(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/ShaderManager;getPostChain(Lnet/minecraft/resources/Identifier;Ljava/util/Set;)Lnet/minecraft/client/renderer/PostChain;"
            )
    )
    private PostChain opticume$skipVanillaOutlineChain(ShaderManager manager, Identifier id, Set<Identifier> targets) {
        GlowEspModule glow = GlowEspModule.getInstance();
        if (glow != null && glow.replacesOutline() && "entity_outline".equals(id.getPath())) {
            return null;
        }
        return manager.getPostChain(id, targets);
    }

    @Inject(method = "blitEntityOutline", at = @At("HEAD"), cancellable = true)
    private void opticume$glowEspComposite(CallbackInfo ci) {
        GlowEspModule glow = GlowEspModule.getInstance();
        if (glow == null || !glow.replacesOutline()) return;
        ci.cancel();
        if (this.currentFrameRendersEntityOutline) {
            glow.composite(this.entityOutlineTarget, Minecraft.getInstance().gameRenderer.mainRenderTarget());
        }
    }
}
