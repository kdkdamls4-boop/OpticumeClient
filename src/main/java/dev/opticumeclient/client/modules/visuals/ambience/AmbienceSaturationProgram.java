package dev.opticumeclient.client.modules.visuals.ambience;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.UniformType;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import dev.opticumeclient.client.modules.visuals.AmbienceModule;
import dev.opticumeclient.client.modules.visuals.targetesp.PipelineBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

import java.util.Optional;

/**
 * Post-world color saturation pass for AmbienceModule.
 */
public final class AmbienceSaturationProgram {
    private static final AmbienceSaturationProgram INSTANCE = new AmbienceSaturationProgram();
    private static final int UBO_SIZE = 16;
    private static final String UNIFORM_NAME = "AmbienceSaturationUniforms";

    private static final RenderPipeline PIPELINE = PipelineBuilder.create()
            .location("opticumeclient/ambience_saturation")
            .vertexShader(WorldRenderBridge.FULLSCREEN_QUAD_VSH)
            .fragmentShader(Identifier.fromNamespaceAndPath("opticumeclient", "core/ambience/ambience_saturation"))
            .depthTest(CompareOp.ALWAYS_PASS)
            .writeDepth(false)
            .cull(false)
            .noBlend()
            .vertexFormat(PipelineBuilder.EMPTY_VERTEX_FORMAT, PrimitiveTopology.TRIANGLES)
            .uniform(UNIFORM_NAME, UniformType.UNIFORM_BUFFER)
            .sampler("InSampler")
            .build();

    private TextureTarget sceneCopy;
    private GpuBuffer uniformBuffer;

    private AmbienceSaturationProgram() {
    }

    public static AmbienceSaturationProgram getInstance() {
        return INSTANCE;
    }

    public void render(AmbienceModule module) {
        if (module == null || !module.isSaturationActive()) {
            return;
        }
        float sat = module.getSaturationLevel();
        if (Math.abs(sat - 1.0f) < 0.005f) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.gameRenderer == null) {
            return;
        }
        RenderTarget main = mc.gameRenderer.mainRenderTarget();
        if (main == null || main.width <= 1 || main.height <= 1 || main.getColorTextureView() == null) {
            return;
        }

        sceneCopy = WorldRenderBridge.ensureColorTarget(sceneCopy, "opticume-sat-copy", main.width, main.height);
        if (!WorldRenderBridge.copyMainColor(main, sceneCopy)) {
            return;
        }

        uniformBuffer = WorldRenderBridge.ensureUniformBuffer(uniformBuffer, "OpticumeSatUbo", UBO_SIZE);
        WorldRenderBridge.writeUniforms(uniformBuffer, UBO_SIZE, builder -> builder.putVec4(sat, 0.0f, 0.0f, 0.0f));

        GpuTextureView mainColor = main.getColorTextureView();
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "OpticumeAmbienceSaturation",
                mainColor,
                Optional.empty()
        )) {
            pass.setPipeline(PipelineBuilder.compile(PIPELINE));
            pass.setUniform(UNIFORM_NAME, uniformBuffer);
            pass.setUniform("InSampler", sceneCopy.getColorTextureView(), WorldRenderBridge.linearSampler());
            pass.draw(3, 1, 0, 0);
        } catch (Throwable ignored) {
        }
    }

    public void releaseGpuResources() {
        if (sceneCopy != null) {
            sceneCopy.destroyBuffers();
            sceneCopy = null;
        }
        if (uniformBuffer != null && !uniformBuffer.isClosed()) {
            uniformBuffer.close();
            uniformBuffer = null;
        }
    }
}
