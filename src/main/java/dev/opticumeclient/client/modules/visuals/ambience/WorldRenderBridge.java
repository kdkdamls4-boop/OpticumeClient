package dev.opticumeclient.client.modules.visuals.ambience;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import com.mojang.blaze3d.buffers.Std140Builder;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;
import java.util.function.Consumer;

/**
 * Shared RenderPearl utilities, UBO/target lifecycle helpers, and current-frame
 * CameraRenderState bridge for Ambience & Atmosphere shaders in MC 26.3.
 */
public final class WorldRenderBridge {
    public static final Identifier FULLSCREEN_QUAD_VSH =
            Identifier.fromNamespaceAndPath("opticumeclient", "core/ambience/ambience_screen_quad");

    private static volatile CameraRenderState currentCamera;

    private WorldRenderBridge() {
    }

    public static void setCurrentCamera(CameraRenderState camera) {
        currentCamera = camera;
    }

    public static CameraRenderState getCurrentCamera() {
        return currentCamera;
    }

    public static boolean isReverseZ() {
        try {
            return RenderSystem.getDevice().getDeviceInfo().isZZeroToOne();
        } catch (Throwable ignored) {
            return true;
        }
    }

    public static float shaderTimeSeconds() {
        return (System.nanoTime() % 1_000_000_000_000L) / 1.0e9f;
    }

    public static GpuSampler linearSampler() {
        return RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
    }

    public static GpuSampler nearestSampler() {
        return RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
    }

    public static Matrix4f computeInvViewProj(CameraRenderState camera) {
        if (camera == null || camera.projectionMatrix == null || camera.viewRotationMatrix == null) {
            return new Matrix4f();
        }
        return new Matrix4f(camera.projectionMatrix)
                .mul(camera.viewRotationMatrix)
                .invert();
    }

    public static float extractFovDegrees(CameraRenderState camera) {
        if (camera == null || camera.projectionMatrix == null) {
            return 70.0f;
        }
        float m11 = Math.abs(camera.projectionMatrix.m11());
        if (m11 < 1.0e-4f) {
            return 70.0f;
        }
        return (float) Math.toDegrees(2.0 * Math.atan(1.0 / m11));
    }

    public static GpuBuffer createUniformBuffer(String label, int byteSize) {
        return RenderSystem.getDevice().createBuffer(
                () -> label,
                GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                byteSize
        );
    }

    public static GpuBuffer ensureUniformBuffer(GpuBuffer current, String label, int byteSize) {
        if (current == null || current.isClosed()) {
            return createUniformBuffer(label, byteSize);
        }
        return current;
    }

    public static void writeUniforms(GpuBuffer buffer, int byteSize, Consumer<Std140Builder> writer) {
        if (buffer == null || buffer.isClosed()) {
            return;
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            Std140Builder builder = Std140Builder.onStack(stack, byteSize);
            writer.accept(builder);
            ByteBuffer data = builder.get();
            RenderSystem.getDevice().createCommandEncoder().writeToBuffer(buffer.slice(), data);
        }
    }

    public static TextureTarget ensureColorTarget(TextureTarget current, String label, int width, int height) {
        int safeW = Math.max(1, width);
        int safeH = Math.max(1, height);
        if (current != null && current.width == safeW && current.height == safeH
                && current.getColorTextureView() != null && !current.getColorTextureView().isClosed()) {
            return current;
        }
        if (current != null) {
            current.destroyBuffers();
        }
        return new TextureTarget(label, safeW, safeH, GpuFormat.RGBA8_UNORM, null);
    }

    public static boolean copyMainColor(RenderTarget main, TextureTarget copyTarget) {
        if (main == null || copyTarget == null) {
            return false;
        }
        GpuTexture src = main.getColorTexture();
        GpuTexture dst = copyTarget.getColorTexture();
        GpuTextureView dstView = copyTarget.getColorTextureView();
        if (src == null || dst == null || dstView == null || dstView.isClosed()) {
            return false;
        }
        int w = Math.min(main.width, copyTarget.width);
        int h = Math.min(main.height, copyTarget.height);
        if (w <= 0 || h <= 0) {
            return false;
        }
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        encoder.copyTextureToTexture(src, dst, 0, 0, 0, 0, 0, w, h);
        return true;
    }

    public static float red(int argb) {
        return ((argb >> 16) & 0xFF) / 255.0f;
    }

    public static float green(int argb) {
        return ((argb >> 8) & 0xFF) / 255.0f;
    }

    public static float blue(int argb) {
        return (argb & 0xFF) / 255.0f;
    }

    public static float alpha(int argb) {
        return ((argb >> 24) & 0xFF) / 255.0f;
    }
}
