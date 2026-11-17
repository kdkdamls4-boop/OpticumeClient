package dev.opticumeclient.client.modules.visuals.targetesp;

import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.UniformType;
import com.mojang.renderpearl.api.textures.FilterMode;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;
import java.util.Optional;

public final class EspBloomPass implements AutoCloseable {
    private static final int BLUR_UBO_SIZE = 48;
    private static final int GLOW_UBO_SIZE = 80;
    private static final float MAX_RADIUS = 12.0F;
    private static final String FULLSCREEN_VSH = "target_fullscreen";

    private static final RenderPipeline MASK_PIPELINE = PipelineBuilder.create()
            .location("opticumeclient/entity_esp_other_mask")
            .vertexShader(EspResources.shader(FULLSCREEN_VSH))
            .fragmentShader(EspResources.shader("entity_esp_mask"))
            .depthTest(CompareOp.ALWAYS_PASS)
            .writeDepth(false)
            .cull(false)
            .noBlend()
            .vertexFormat(PipelineBuilder.EMPTY_VERTEX_FORMAT, PrimitiveTopology.TRIANGLES)
            .sampler("InSampler")
            .build();

    private static final RenderPipeline BLUR_PIPELINE = PipelineBuilder.create()
            .location("opticumeclient/entity_esp_other_blur")
            .vertexShader(EspResources.shader(FULLSCREEN_VSH))
            .fragmentShader(EspResources.shader("entity_esp_gaussian_blur"))
            .depthTest(CompareOp.ALWAYS_PASS)
            .writeDepth(false)
            .cull(false)
            .noBlend()
            .vertexFormat(PipelineBuilder.EMPTY_VERTEX_FORMAT, PrimitiveTopology.TRIANGLES)
            .uniform("EspBlur", UniformType.UNIFORM_BUFFER)
            .sampler("InSampler")
            .build();

    private static final RenderPipeline GLOW_PIPELINE = PipelineBuilder.create()
            .location("opticumeclient/entity_esp_other_glow")
            .vertexShader(EspResources.shader(FULLSCREEN_VSH))
            .fragmentShader(EspResources.shader("entity_esp_outer_glow"))
            .depthTest(CompareOp.ALWAYS_PASS)
            .writeDepth(false)
            .cull(false)
            .blend(BlendFunction.LIGHTNING)
            .vertexFormat(PipelineBuilder.EMPTY_VERTEX_FORMAT, PrimitiveTopology.TRIANGLES)
            .uniform("EspOuterGlow", UniformType.UNIFORM_BUFFER)
            .sampler("BloomSampler")
            .sampler("MaskSampler")
            .build();

    private static TextureTarget blurHorizontalTarget;
    private static TextureTarget blurVerticalTarget;
    private static GpuBuffer blurHorizontalUbo;
    private static GpuBuffer blurVerticalUbo;
    private static GpuBuffer glowUbo;

    private static void updateGlowUniforms(CommandEncoder encoder, RenderTarget maskTarget, int color, float intensity, float colorMode, boolean unusedFlag, float saturation, int outlineColor, float outlineWidth) {
        glowUbo = ensureUbo(glowUbo, GLOW_UBO_SIZE, "entityEspOtherGlowUniforms");
        float r = red(color);
        float g = green(color);
        float b = blue(color);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer buf = Std140Builder.onStack(stack, GLOW_UBO_SIZE)
                    .putVec4(intensity, colorMode, saturation, outlineWidth)
                    .putVec4(r, g, b, 1.0F)
                    .putVec4(r * 0.5F, g * 0.5F, b * 0.5F, outlineWidth)
                    .putVec4(red(outlineColor), green(outlineColor), blue(outlineColor), alpha(outlineColor))
                    .putVec4(maskTarget.width, maskTarget.height, outlineWidth, 0.0F)
                    .get();
            encoder.writeToBuffer(glowUbo.slice(), buf);
        }
    }

    private static GpuBuffer ensureUbo(GpuBuffer existing, int size, String label) {
        return existing != null && !existing.isClosed()
                ? existing
                : RenderSystem.getDevice().createBuffer(() -> label, GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, size);
    }

    private static float red(int argb) {
        return (argb >>> 16 & 0xFF) / 255.0F;
    }

    private static float green(int argb) {
        return (argb >>> 8 & 0xFF) / 255.0F;
    }

    private static float blue(int argb) {
        return (argb & 0xFF) / 255.0F;
    }

    private static float alpha(int argb) {
        return (argb >>> 24 & 0xFF) / 255.0F;
    }

    private static boolean isValidTarget(RenderTarget target) {
        return target != null && target.getColorTextureView() != null;
    }

    private static void ensureTargets(int width, int height) {
        if (blurHorizontalTarget == null || blurVerticalTarget == null) {
            blurHorizontalTarget = new TextureTarget("entity-esp-other-blur-horizontal", width, height, GpuFormat.RGBA8_UNORM, null);
            blurVerticalTarget = new TextureTarget("entity-esp-other-blur-vertical", width, height, GpuFormat.RGBA8_UNORM, null);
        } else if (blurHorizontalTarget.width != width || blurHorizontalTarget.height != height) {
            blurHorizontalTarget.resize(width, height);
            blurVerticalTarget.resize(width, height);
        }
    }

    public static void render(RenderTarget maskTarget, RenderTarget dstTarget, int color, float radius, float intensity, float colorMode, boolean unusedFlag, float saturation, int outlineColor, float outlineWidth) {
        if (isValidTarget(maskTarget) && isValidTarget(dstTarget)) {
            int halfW = Math.max(1, maskTarget.width / 2);
            int halfH = Math.max(1, maskTarget.height / 2);
            float clampedRadius = Math.clamp(radius, 1.0F, MAX_RADIUS);
            ensureTargets(halfW, halfH);
            CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
            updateBlurUniforms(encoder, halfW, halfH, clampedRadius);
            updateGlowUniforms(encoder, maskTarget, color, intensity, colorMode, unusedFlag, saturation, outlineColor, outlineWidth);

            try (RenderPass pass = encoder.createRenderPass(() -> "entityEspOtherMask", blurHorizontalTarget.getColorTextureView(), Optional.of(new Vector4f(0.0F)))) {
                pass.setPipeline(PipelineBuilder.compile(MASK_PIPELINE));
                pass.setUniform("InSampler", maskTarget.getColorTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
                pass.draw(3, 1, 0, 0);
            }

            for (int i = 0; i < 2; i++) {
                runBlurPass(encoder, blurHorizontalTarget, blurVerticalTarget, blurHorizontalUbo);
                runBlurPass(encoder, blurVerticalTarget, blurHorizontalTarget, blurVerticalUbo);
            }

            try (RenderPass pass = encoder.createRenderPass(() -> "entityEspOtherGlow", dstTarget.getColorTextureView(), Optional.empty())) {
                pass.setPipeline(PipelineBuilder.compile(GLOW_PIPELINE));
                pass.setUniform("BloomSampler", blurHorizontalTarget.getColorTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
                pass.setUniform("MaskSampler", maskTarget.getColorTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
                pass.setUniform("EspOuterGlow", glowUbo);
                pass.draw(3, 1, 0, 0);
            }
        }
    }

    private static void runBlurPass(CommandEncoder encoder, RenderTarget src, RenderTarget dst, GpuBuffer ubo) {
        try (RenderPass pass = encoder.createRenderPass(() -> "entityEspOtherBlur", dst.getColorTextureView(), Optional.of(new Vector4f(0.0F)))) {
            pass.setPipeline(PipelineBuilder.compile(BLUR_PIPELINE));
            pass.setUniform("InSampler", src.getColorTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
            pass.setUniform("EspBlur", ubo);
            pass.draw(3, 1, 0, 0);
        }
    }

    public static void renderSimple(RenderTarget maskTarget, RenderTarget dstTarget, int color, float radius, float intensity, float colorMode) {
        render(maskTarget, dstTarget, color, radius, intensity, colorMode, false, 0.0F, 0, 0.0F);
    }

    public static void releaseGpuResources() {
        if (blurHorizontalTarget != null) {
            blurHorizontalTarget.destroyBuffers();
            blurHorizontalTarget = null;
        }
        if (blurVerticalTarget != null) {
            blurVerticalTarget.destroyBuffers();
            blurVerticalTarget = null;
        }
        if (blurHorizontalUbo != null) {
            blurHorizontalUbo.close();
            blurHorizontalUbo = null;
        }
        if (blurVerticalUbo != null) {
            blurVerticalUbo.close();
            blurVerticalUbo = null;
        }
        if (glowUbo != null) {
            glowUbo.close();
            glowUbo = null;
        }
    }

    @Override
    public void close() {
        releaseGpuResources();
    }

    private static void updateBlurUniforms(CommandEncoder encoder, int width, int height, float radius) {
        blurHorizontalUbo = ensureUbo(blurHorizontalUbo, BLUR_UBO_SIZE, "entityEspOtherBlurHorizontalUniforms");
        blurVerticalUbo = ensureUbo(blurVerticalUbo, BLUR_UBO_SIZE, "entityEspOtherBlurVerticalUniforms");
        float[] w = gaussianWeights();
        float step = 0.5F + radius * 0.35F;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer horiz = Std140Builder.onStack(stack, BLUR_UBO_SIZE)
                    .putVec2(1.0F / width, 1.0F / height).putVec2(1.0F, 0.0F)
                    .putVec4(step, w[0], w[1], w[2]).putVec4(w[3], w[4], w[5], 0.0F).get();
            encoder.writeToBuffer(blurHorizontalUbo.slice(), horiz);
            ByteBuffer vert = Std140Builder.onStack(stack, BLUR_UBO_SIZE)
                    .putVec2(1.0F / width, 1.0F / height).putVec2(0.0F, 1.0F)
                    .putVec4(step, w[0], w[1], w[2]).putVec4(w[3], w[4], w[5], 0.0F).get();
            encoder.writeToBuffer(blurVerticalUbo.slice(), vert);
        }
    }

    private static float[] gaussianWeights() {
        float sigma = 2.2F;
        float[] w = new float[6];
        float sum = 0.0F;
        for (int i = 0; i < 6; i++) {
            w[i] = (float) Math.exp(-(i * i) / (2.0F * sigma * sigma));
            sum += i == 0 ? w[i] : w[i] * 2.0F;
        }
        for (int i = 0; i < 6; i++) {
            w[i] /= sum;
        }
        return w;
    }
}
