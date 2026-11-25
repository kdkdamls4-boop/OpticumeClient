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
import com.mojang.renderpearl.api.textures.GpuSampler;
import dev.opticumeclient.client.modules.visuals.MotionBlurModule;
import dev.opticumeclient.client.modules.visuals.targetesp.PipelineBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;

import java.util.Optional;

/**
 * Reprojection camera MotionBlur post-pass running in LevelRenderer tail (before GuiRenderer).
 */
public final class MotionBlurRenderer {
    private static final MotionBlurRenderer INSTANCE = new MotionBlurRenderer();
    private static final int UBO_SIZE = 144; // 2 * mat4 (128) + 1 * vec4 (16)
    private static final String UNIFORM_NAME = "MotionBlurUniforms";

    private static final RenderPipeline PIPELINE = PipelineBuilder.create()
            .location("opticumeclient/motion_blur")
            .vertexShader(WorldRenderBridge.FULLSCREEN_QUAD_VSH)
            .fragmentShader(Identifier.fromNamespaceAndPath("opticumeclient", "core/ambience/motion_blur"))
            .depthTest(CompareOp.ALWAYS_PASS)
            .writeDepth(false)
            .cull(false)
            .noBlend()
            .vertexFormat(PipelineBuilder.EMPTY_VERTEX_FORMAT, PrimitiveTopology.TRIANGLES)
            .uniform(UNIFORM_NAME, UniformType.UNIFORM_BUFFER)
            .sampler("DepthSampler")
            .sampler("SceneSampler")
            .build();

    private TextureTarget sceneCopy;
    private GpuBuffer uniformBuffer;
    private final Matrix4f prevViewProj = new Matrix4f();
    private double prevCamX, prevCamY, prevCamZ;
    private boolean hasPrev = false;

    private MotionBlurRenderer() {
    }

    public static MotionBlurRenderer getInstance() {
        return INSTANCE;
    }

    public void reset() {
        hasPrev = false;
    }

    public void render(MotionBlurModule module, CameraRenderState camera) {
        if (module == null || !module.isEnabled() || camera == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.gameRenderer == null || mc.level == null) {
            reset();
            return;
        }
        RenderTarget main = mc.gameRenderer.mainRenderTarget();
        if (main == null || main.width <= 1 || main.height <= 1
                || main.getColorTextureView() == null
                || main.getDepthTextureView() == null) {
            return;
        }

        Matrix4f viewProj = new Matrix4f(camera.projectionMatrix).mul(camera.viewRotationMatrix);
        double dx = camera.pos.x - prevCamX;
        double dy = camera.pos.y - prevCamY;
        double dz = camera.pos.z - prevCamZ;

        // Avoid smearing on teleport or initial frame
        if (!hasPrev || (dx * dx + dy * dy + dz * dz) > 25.0) {
            prevViewProj.set(viewProj);
            prevCamX = camera.pos.x;
            prevCamY = camera.pos.y;
            prevCamZ = camera.pos.z;
            hasPrev = true;
            return;
        }

        sceneCopy = WorldRenderBridge.ensureColorTarget(sceneCopy, "opticume-mblur-copy", main.width, main.height);
        if (!WorldRenderBridge.copyMainColor(main, sceneCopy)) {
            return;
        }

        Matrix4f invViewProj = new Matrix4f(viewProj).invert();
        Matrix4f prevRelative = new Matrix4f(prevViewProj);
        float reverseZ = WorldRenderBridge.isReverseZ() ? 1.0f : 0.0f;
        float strength = module.getStrength();
        float maxBlur = module.getMaxBlur();
        float samples = (float) module.getSamples();

        uniformBuffer = WorldRenderBridge.ensureUniformBuffer(uniformBuffer, "OpticumeMotionBlurUbo", UBO_SIZE);
        WorldRenderBridge.writeUniforms(uniformBuffer, UBO_SIZE, builder -> {
            builder.putMat4f(invViewProj);
            builder.putMat4f(prevRelative);
            builder.putVec4(strength, maxBlur, reverseZ, samples);
        });

        GpuSampler linear = WorldRenderBridge.linearSampler();
        GpuSampler nearest = WorldRenderBridge.nearestSampler();

        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "OpticumeMotionBlur",
                main.getColorTextureView(),
                Optional.empty()
        )) {
            pass.setPipeline(PipelineBuilder.compile(PIPELINE));
            pass.setUniform(UNIFORM_NAME, uniformBuffer);
            pass.setUniform("DepthSampler", main.getDepthTextureView(), nearest);
            pass.setUniform("SceneSampler", sceneCopy.getColorTextureView(), linear);
            pass.draw(3, 1, 0, 0);
        } catch (Throwable ignored) {
        } finally {
            prevViewProj.set(viewProj);
            prevCamX = camera.pos.x;
            prevCamY = camera.pos.y;
            prevCamZ = camera.pos.z;
        }
    }

    public void releaseGpuResources() {
        hasPrev = false;
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
