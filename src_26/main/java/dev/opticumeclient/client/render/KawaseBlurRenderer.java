package dev.opticumeclient.client.render;

import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.UniformType;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import dev.opticumeclient.client.modules.visuals.targetesp.PipelineBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.joml.Vector4f;
import org.joml.Vector4fc;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;
import java.util.Optional;

/**
 * Ping-pong GPU Gaussian/Kawase backdrop blur renderer for MC 26.3 (RenderPearl).
 * Captures the main framebuffer into half-resolution TextureTargets so GUI panels
 * can render real frosted Liquid Glass via {@link BlurredRectRenderState}.
 */
public final class KawaseBlurRenderer {
    private static final int UBO_SIZE = 16;
    private static final long MIN_INTERVAL_NS_NORMAL = 1_000_000_000L / 48L;
    private static final long MIN_INTERVAL_NS_LOW_FPS = 1_000_000_000L / 30L;

    private static final RenderPipeline BLUR_PIPELINE = PipelineBuilder.create()
            .location("opticumeclient/ui_blur_gaussian")
            .vertexShader(Identifier.fromNamespaceAndPath("opticumeclient", "core/targetesp/target_fullscreen"))
            .fragmentShader(Identifier.fromNamespaceAndPath("opticumeclient", "core/ui_blur_gaussian"))
            .depthTest(CompareOp.ALWAYS_PASS)
            .writeDepth(false)
            .cull(false)
            .noBlend()
            .vertexFormat(PipelineBuilder.EMPTY_VERTEX_FORMAT, PrimitiveTopology.TRIANGLES)
            .uniform("UiBlurParams", UniformType.UNIFORM_BUFFER)
            .sampler("InSampler")
            .build();

    private static TextureTarget targetA;
    private static TextureTarget targetB;
    private static GpuBuffer uniformBufA;
    private static GpuBuffer uniformBufB;

    private static volatile boolean worldBlurRequested = false;
    private static volatile boolean menuBlurRequested = false;
    private static volatile int requestedPasses = 3;
    private static volatile float requestedOffset = 2.0f;
    private static long lastCaptureNs = 0L;

    private static volatile boolean errorLogged = false;

    private KawaseBlurRenderer() {
    }

    public static void requestWorldBlur(int passes, float offset) {
        worldBlurRequested = true;
        requestedPasses = Math.max(1, Math.min(8, passes));
        requestedOffset = Math.max(0.25f, Math.min(8.0f, offset));
    }

    public static void requestMenuBlur(int passes, float offset) {
        menuBlurRequested = true;
        requestedPasses = Math.max(1, Math.min(8, passes));
        requestedOffset = Math.max(0.25f, Math.min(8.0f, offset));
    }

    public static boolean consumeMenuBlurRequest() {
        boolean requested = menuBlurRequested;
        menuBlurRequested = false;
        return requested;
    }

    public static void onBeforeGuiRender() {
        boolean run = worldBlurRequested;
        worldBlurRequested = false;
        if (!run) return;
        captureFromMainTarget(requestedPasses, requestedOffset, true);
    }

    public static void captureMenuNow() {
        captureFromMainTarget(requestedPasses, requestedOffset, false);
    }

    public static void captureFromMainTarget(int passes, float offset, boolean rateLimit) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.gameRenderer == null) return;
        RenderTarget main = mc.gameRenderer.mainRenderTarget();
        if (main == null || main.width <= 2 || main.height <= 2 || main.getColorTextureView() == null) return;

        long now = System.nanoTime();
        long minInterval = (mc.getFps() > 0 && mc.getFps() < 55) ? MIN_INTERVAL_NS_LOW_FPS : MIN_INTERVAL_NS_NORMAL;
        if (rateLimit && targetA != null && now - lastCaptureNs < minInterval) {
            return;
        }
        lastCaptureNs = now;

        int halfW = Math.max(1, main.width / 2);
        int halfH = Math.max(1, main.height / 2);
        ensureTargets(halfW, halfH);

        try {
            CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
            GpuSampler linear = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
            int iterations = Math.max(1, Math.min(8, passes));

            for (int i = 0; i < iterations; i++) {
                GpuTextureView srcH = (i == 0) ? main.getColorTextureView() : targetA.getColorTextureView();
                float srcInvW = (i == 0) ? (1.0f / main.width) : (1.0f / halfW);
                float srcInvH = (i == 0) ? (1.0f / main.height) : (1.0f / halfH);
                float step = (i == 0) ? Math.max(0.35f, offset) : (offset * (1.0f + i * 0.35f));

                uniformBufA = writeUniform(encoder, uniformBufA, "OpticumeBlurUboA", srcInvW, srcInvH, step, 0.0f);
                runPass(encoder, targetB.getColorTextureView(), srcH, linear, uniformBufA, i == 0);

                uniformBufB = writeUniform(encoder, uniformBufB, "OpticumeBlurUboB", 1.0f / halfW, 1.0f / halfH, 0.0f, step);
                runPass(encoder, targetA.getColorTextureView(), targetB.getColorTextureView(), linear, uniformBufB, false);
            }
        } catch (Throwable t) {
            if (!errorLogged) {
                errorLogged = true;
                System.err.println("[KawaseBlurRenderer] Blur capture failed: " + t);
            }
        }
    }

    public static GpuTextureView getBlurredTextureView() {
        if (targetA == null) return null;
        GpuTextureView view = targetA.getColorTextureView();
        return (view == null || view.isClosed()) ? null : view;
    }

    public static boolean isReady() {
        return getBlurredTextureView() != null;
    }

    private static void runPass(CommandEncoder encoder, GpuTextureView dst, GpuTextureView src, GpuSampler sampler, GpuBuffer ubo, boolean clear) {
        Optional<Vector4fc> clearColor = clear ? Optional.of(new Vector4f(0.0f, 0.0f, 0.0f, 1.0f)) : Optional.empty();
        try (RenderPass pass = encoder.createRenderPass(() -> "OpticumeUiBlur", dst, clearColor)) {
            pass.setPipeline(PipelineBuilder.compile(BLUR_PIPELINE));
            pass.setUniform("InSampler", src, sampler);
            pass.setUniform("UiBlurParams", ubo);
            pass.draw(3, 1, 0, 0);
        }
    }

    private static GpuBuffer writeUniform(CommandEncoder encoder, GpuBuffer existing, String label, float invW, float invH, float dirX, float dirY) {
        GpuBuffer buf = (existing != null && !existing.isClosed())
                ? existing
                : RenderSystem.getDevice().createBuffer(() -> label, GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, UBO_SIZE);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer data = Std140Builder.onStack(stack, UBO_SIZE)
                    .putVec4(invW, invH, dirX, dirY)
                    .get();
            encoder.writeToBuffer(buf.slice(), data);
        }
        return buf;
    }

    private static void ensureTargets(int width, int height) {
        if (targetA == null || targetB == null) {
            targetA = new TextureTarget("opticume-ui-blur-a", width, height, GpuFormat.RGBA8_UNORM, null);
            targetB = new TextureTarget("opticume-ui-blur-b", width, height, GpuFormat.RGBA8_UNORM, null);
        } else if (targetA.width != width || targetA.height != height) {
            targetA.resize(width, height);
            targetB.resize(width, height);
        }
    }

    public static void releaseGpuResources() {
        if (targetA != null) {
            targetA.destroyBuffers();
            targetA = null;
        }
        if (targetB != null) {
            targetB.destroyBuffers();
            targetB = null;
        }
        if (uniformBufA != null) {
            uniformBufA.close();
            uniformBufA = null;
        }
        if (uniformBufB != null) {
            uniformBufB.close();
            uniformBufB = null;
        }
    }
}
