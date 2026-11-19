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
import com.mojang.renderpearl.api.textures.GpuTextureView;
import dev.opticumeclient.client.modules.visuals.AmbienceModule;
import dev.opticumeclient.client.modules.visuals.targetesp.PipelineBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Depth-weighted Kawase distance blur renderer for AmbienceModule.
 */
public final class FogBlurRenderer {
    private static final FogBlurRenderer INSTANCE = new FogBlurRenderer();
    private static final int KAWASE_LEVELS = 3;
    private static final int KAWASE_UBO_SIZE = 16;
    private static final int COMPOSITE_UBO_SIZE = 80; // mat4 (64) + vec4 (16)

    private static final RenderPipeline KAWASE_DOWN = PipelineBuilder.create()
            .location("opticumeclient/ambience_fog_kawase_down")
            .vertexShader(WorldRenderBridge.FULLSCREEN_QUAD_VSH)
            .fragmentShader(Identifier.fromNamespaceAndPath("opticumeclient", "core/ambience/fog_blur_down"))
            .depthTest(CompareOp.ALWAYS_PASS)
            .writeDepth(false)
            .cull(false)
            .noBlend()
            .vertexFormat(PipelineBuilder.EMPTY_VERTEX_FORMAT, PrimitiveTopology.TRIANGLES)
            .uniform("FogKawaseUniforms", UniformType.UNIFORM_BUFFER)
            .sampler("CurrentInput")
            .build();

    private static final RenderPipeline KAWASE_UP = PipelineBuilder.create()
            .location("opticumeclient/ambience_fog_kawase_up")
            .vertexShader(WorldRenderBridge.FULLSCREEN_QUAD_VSH)
            .fragmentShader(Identifier.fromNamespaceAndPath("opticumeclient", "core/ambience/fog_blur_up"))
            .depthTest(CompareOp.ALWAYS_PASS)
            .writeDepth(false)
            .cull(false)
            .noBlend()
            .vertexFormat(PipelineBuilder.EMPTY_VERTEX_FORMAT, PrimitiveTopology.TRIANGLES)
            .uniform("FogKawaseUniforms", UniformType.UNIFORM_BUFFER)
            .sampler("CurrentInput")
            .build();

    private static final RenderPipeline COMPOSITE = PipelineBuilder.create()
            .location("opticumeclient/ambience_fog_blur_composite")
            .vertexShader(WorldRenderBridge.FULLSCREEN_QUAD_VSH)
            .fragmentShader(Identifier.fromNamespaceAndPath("opticumeclient", "core/ambience/fog_blur"))
            .depthTest(CompareOp.ALWAYS_PASS)
            .writeDepth(false)
            .cull(false)
            .noBlend()
            .vertexFormat(PipelineBuilder.EMPTY_VERTEX_FORMAT, PrimitiveTopology.TRIANGLES)
            .uniform("FogBlurUniforms", UniformType.UNIFORM_BUFFER)
            .sampler("SceneSampler")
            .sampler("BlurSampler")
            .sampler("DepthSampler")
            .build();

    private final List<TextureTarget> mips = new ArrayList<>(KAWASE_LEVELS);
    private final List<GpuBuffer> passUbos = new ArrayList<>(KAWASE_LEVELS * 2);
    private TextureTarget sceneCopy;
    private GpuBuffer compositeUbo;

    private FogBlurRenderer() {
    }

    public static FogBlurRenderer getInstance() {
        return INSTANCE;
    }

    public void render(AmbienceModule module, CameraRenderState camera) {
        if (module == null || camera == null || !module.isFogBlurActive()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.gameRenderer == null) {
            return;
        }
        RenderTarget main = mc.gameRenderer.mainRenderTarget();
        if (main == null || main.width <= 2 || main.height <= 2
                || main.getColorTextureView() == null
                || main.getDepthTextureView() == null) {
            return;
        }

        sceneCopy = WorldRenderBridge.ensureColorTarget(sceneCopy, "opticume-fogblur-copy", main.width, main.height);
        if (!WorldRenderBridge.copyMainColor(main, sceneCopy)) {
            return;
        }

        ensureMips(main.width, main.height);
        GpuSampler linear = WorldRenderBridge.linearSampler();
        GpuSampler nearest = WorldRenderBridge.nearestSampler();

        try {
            int uboIdx = 0;
            GpuTextureView input = sceneCopy.getColorTextureView();
            int inputW = main.width;
            int inputH = main.height;
            float offset = 1.6f + module.getFogBlurStrength() * 1.6f;

            for (int i = 0; i < KAWASE_LEVELS; i++) {
                TextureTarget dst = mips.get(i);
                runKawasePass(uboIdx++, KAWASE_DOWN, dst.getColorTextureView(), input, inputW, inputH, offset, linear);
                input = dst.getColorTextureView();
                inputW = dst.width;
                inputH = dst.height;
            }
            for (int i = KAWASE_LEVELS - 1; i > 0; i--) {
                TextureTarget srcTarget = mips.get(i);
                TextureTarget dstTarget = mips.get(i - 1);
                runKawasePass(uboIdx++, KAWASE_UP, dstTarget.getColorTextureView(),
                        srcTarget.getColorTextureView(), srcTarget.width, srcTarget.height, offset, linear);
            }

            GpuTextureView blurredView = mips.get(0).getColorTextureView();
            if (blurredView == null || blurredView.isClosed()) {
                return;
            }

            compositeUbo = WorldRenderBridge.ensureUniformBuffer(compositeUbo, "OpticumeFogBlurUbo", COMPOSITE_UBO_SIZE);
            Matrix4f invProj = new Matrix4f(camera.projectionMatrix).invert();
            float reverseZ = WorldRenderBridge.isReverseZ() ? 1.0f : 0.0f;
            float start = module.getEffectiveFogBlurStart();
            float end = Math.max(start + 1.0f, module.getEffectiveFogBlurEnd());
            float strength = module.getFogBlurStrength();

            WorldRenderBridge.writeUniforms(compositeUbo, COMPOSITE_UBO_SIZE, builder -> {
                builder.putMat4f(invProj);
                builder.putVec4(start, end, strength, reverseZ);
            });

            try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                    () -> "OpticumeFogBlurComposite",
                    main.getColorTextureView(),
                    Optional.empty()
            )) {
                pass.setPipeline(PipelineBuilder.compile(COMPOSITE));
                pass.setUniform("FogBlurUniforms", compositeUbo);
                pass.setUniform("SceneSampler", sceneCopy.getColorTextureView(), linear);
                pass.setUniform("BlurSampler", blurredView, linear);
                pass.setUniform("DepthSampler", main.getDepthTextureView(), nearest);
                pass.draw(3, 1, 0, 0);
            }
        } catch (Throwable ignored) {
        }
    }

    private void runKawasePass(int uboIndex, RenderPipeline pipeline, GpuTextureView dst, GpuTextureView src,
                               int srcW, int srcH, float offset, GpuSampler sampler) {
        GpuBuffer ubo = getPassUbo(uboIndex);
        WorldRenderBridge.writeUniforms(ubo, KAWASE_UBO_SIZE, builder -> {
            builder.putVec2(1.0f / Math.max(1, srcW), 1.0f / Math.max(1, srcH));
            builder.putFloat(offset);
            builder.putFloat(1.0f);
        });
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "OpticumeFogKawase",
                dst,
                Optional.empty()
        )) {
            pass.setPipeline(PipelineBuilder.compile(pipeline));
            pass.setUniform("FogKawaseUniforms", ubo);
            pass.setUniform("CurrentInput", src, sampler);
            pass.draw(3, 1, 0, 0);
        }
    }

    private GpuBuffer getPassUbo(int idx) {
        while (passUbos.size() <= idx) {
            passUbos.add(WorldRenderBridge.createUniformBuffer("OpticumeFogKawaseUbo" + passUbos.size(), KAWASE_UBO_SIZE));
        }
        GpuBuffer buf = passUbos.get(idx);
        if (buf.isClosed()) {
            buf = WorldRenderBridge.createUniformBuffer("OpticumeFogKawaseUbo" + idx, KAWASE_UBO_SIZE);
            passUbos.set(idx, buf);
        }
        return buf;
    }

    private void ensureMips(int width, int height) {
        while (mips.size() < KAWASE_LEVELS) {
            mips.add(null);
        }
        int mipW = width;
        int mipH = height;
        for (int i = 0; i < KAWASE_LEVELS; i++) {
            mipW = Math.max(1, mipW / 2);
            mipH = Math.max(1, mipH / 2);
            mips.set(i, WorldRenderBridge.ensureColorTarget(mips.get(i), "opticume-fogblur-mip-" + i, mipW, mipH));
        }
    }

    public void releaseGpuResources() {
        if (sceneCopy != null) {
            sceneCopy.destroyBuffers();
            sceneCopy = null;
        }
        for (TextureTarget mip : mips) {
            if (mip != null) {
                mip.destroyBuffers();
            }
        }
        mips.clear();
        for (GpuBuffer ubo : passUbos) {
            if (ubo != null && !ubo.isClosed()) {
                ubo.close();
            }
        }
        passUbos.clear();
        if (compositeUbo != null && !compositeUbo.isClosed()) {
            compositeUbo.close();
            compositeUbo = null;
        }
    }
}
