package dev.opticumeclient.client.modules.visuals.targetesp;

import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.BlendFactor;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.UniformType;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryStack;

import java.util.Optional;

public final class SurfaceLightingPass {
    private static final int MAX_LIGHTS = 4;
    private static final int UBO_SIZE = 4448;
    private static final RenderPipeline PIPELINE = PipelineBuilder.create()
            .location("opticumeclient/target_surface_light")
            .vertexShader(EspResources.shader("target_fullscreen"))
            .fragmentShader(EspResources.shader("target_surface_light"))
            .depthTest(CompareOp.ALWAYS_PASS)
            .writeDepth(false)
            .cull(false)
            .blend(new BlendFunction(BlendFactor.ONE, BlendFactor.ONE, BlendFactor.ZERO, BlendFactor.ONE))
            .vertexFormat(PipelineBuilder.EMPTY_VERTEX_FORMAT, PrimitiveTopology.TRIANGLES)
            .uniform("TargetLighting", UniformType.UNIFORM_BUFFER)
            .sampler("DepthSampler")
            .sampler("SceneSampler")
            .build();

    private static final Vector3f[] LIGHT_POS = new Vector3f[MAX_LIGHTS];
    private static final Vector3f[] LIGHT_COLOR = new Vector3f[MAX_LIGHTS];
    private static final float[] LIGHT_WEIGHT_SUM = new float[MAX_LIGHTS];
    private static final float[] LIGHT_INTENSITY = new float[MAX_LIGHTS];
    private static final float[] LIGHT_RADIUS = new float[MAX_LIGHTS];
    private static final Matrix4f VIEW_MAT = new Matrix4f();
    private static final Matrix4f INV_PROJ = new Matrix4f();
    private static final Matrix4f INV_VIEW = new Matrix4f();
    private static final BlockLightSampler BLOCK_SAMPLER = new BlockLightSampler();
    private static final Vector3f TEMP_VIEW_POS = new Vector3f();
    private static final Vector4f TEMP_CLIP_POS = new Vector4f();

    private static GpuTexture depthCopyTex;
    private static GpuTextureView depthCopyView;
    private static GpuTexture sceneCopyTex;
    private static GpuTextureView sceneCopyView;
    private static boolean sceneCaptured;
    private static boolean collecting;
    private static int lightCount;

    static {
        for (int i = 0; i < MAX_LIGHTS; i++) {
            LIGHT_POS[i] = new Vector3f();
            LIGHT_COLOR[i] = new Vector3f();
        }
    }

    private SurfaceLightingPass() {
    }

    public static void resetFrame() {
        sceneCaptured = false;
        collecting = false;
        lightCount = 0;
    }

    public static void releaseGpuResources() {
        resetFrame();
        if (depthCopyView != null && !depthCopyView.isClosed()) {
            depthCopyView.close();
        }
        if (depthCopyTex != null && !depthCopyTex.isClosed()) {
            depthCopyTex.close();
        }
        if (sceneCopyView != null && !sceneCopyView.isClosed()) {
            sceneCopyView.close();
        }
        if (sceneCopyTex != null && !sceneCopyTex.isClosed()) {
            sceneCopyTex.close();
        }
        depthCopyView = null;
        depthCopyTex = null;
        sceneCopyView = null;
        sceneCopyTex = null;
        BLOCK_SAMPLER.reset();
    }

    public static void begin(boolean enabled, Matrix4f viewMatrix) {
        collecting = enabled && sceneCaptured;
        lightCount = 0;
        VIEW_MAT.set(viewMatrix);
    }

    public static void addLight(float x, float y, float z, float strength, int color) {
        addLight(VIEW_MAT, x, y, z, strength, color);
    }

    public static void addLight(Matrix4f pose, float x, float y, float z, float strength, int color) {
        if (collecting && !(strength <= 0.02F)) {
            float alpha = (color >>> 24) / 255.0F;
            if (!(alpha <= 0.025F)) {
                pose.transformPosition(x, y, z, TEMP_VIEW_POS);
                if (TEMP_VIEW_POS.isFinite() && !(TEMP_VIEW_POS.lengthSquared() > 6400.0F)) {
                    float weight = Math.min(strength, 1.2F) * alpha;
                    int nearestIdx = -1;
                    float nearestDistSq = Float.POSITIVE_INFINITY;

                    for (int i = 0; i < lightCount; i++) {
                        float d2 = LIGHT_POS[i].distanceSquared(TEMP_VIEW_POS);
                        if (d2 < nearestDistSq) {
                            nearestIdx = i;
                            nearestDistSq = d2;
                        }
                    }

                    float r = (color >> 16 & 0xFF) / 255.0F;
                    float g = (color >> 8 & 0xFF) / 255.0F;
                    float b = (color & 0xFF) / 255.0F;
                    float radius = Math.clamp(1.6F + strength * 1.6F, 1.8F, 3.8F);
                    if (nearestIdx >= 0 && (!(nearestDistSq > 0.64F) || lightCount >= MAX_LIGHTS)) {
                        float totalWeight = LIGHT_WEIGHT_SUM[nearestIdx] + weight;
                        float factor = weight / totalWeight;
                        LIGHT_POS[nearestIdx].lerp(TEMP_VIEW_POS, factor);
                        Vector3f c = LIGHT_COLOR[nearestIdx];
                        c.set(c.x + (r - c.x) * factor, c.y + (g - c.y) * factor, c.z + (b - c.z) * factor);
                        LIGHT_WEIGHT_SUM[nearestIdx] = totalWeight;
                        LIGHT_INTENSITY[nearestIdx] = Math.max(LIGHT_INTENSITY[nearestIdx], weight);
                        LIGHT_RADIUS[nearestIdx] = Math.max(LIGHT_RADIUS[nearestIdx], radius);
                    } else {
                        int idx = lightCount++;
                        LIGHT_POS[idx].set(TEMP_VIEW_POS);
                        LIGHT_COLOR[idx].set(r, g, b);
                        LIGHT_WEIGHT_SUM[idx] = LIGHT_INTENSITY[idx] = weight;
                        LIGHT_RADIUS[idx] = radius;
                    }
                }
            }
        }
    }

    public static void captureScene() {
        Minecraft mc = Minecraft.getInstance();
        if (!sceneCaptured && mc.level != null) {
            RenderTarget mainTarget = mc.gameRenderer.mainRenderTarget();
            if (mainTarget != null) {
                GpuTexture depthTex = mainTarget.getDepthTexture();
                GpuTexture colorTex = mainTarget.getColorTexture();
                if (depthTex != null && !depthTex.isClosed() && colorTex != null && !colorTex.isClosed()) {
                    int w = depthTex.getWidth(0);
                    int h = depthTex.getHeight(0);
                    if (w > 0 && h > 0) {
                        if (depthCopyTex == null
                                || depthCopyTex.isClosed()
                                || depthCopyView == null
                                || depthCopyView.isClosed()
                                || depthCopyTex.getWidth(0) != w
                                || depthCopyTex.getHeight(0) != h
                                || depthCopyTex.getFormat() != depthTex.getFormat()
                                || sceneCopyTex == null
                                || sceneCopyTex.isClosed()
                                || sceneCopyView == null
                                || sceneCopyView.isClosed()
                                || sceneCopyTex.getFormat() != colorTex.getFormat()) {
                            releaseGpuResources();
                            depthCopyTex = RenderSystem.getDevice().createTexture("opticume-target-lighting-depth", 5, depthTex.getFormat(), w, h, 1, 1);
                            depthCopyView = RenderSystem.getDevice().createTextureView(depthCopyTex);
                            sceneCopyTex = RenderSystem.getDevice().createTexture("opticume-target-lighting-color", 5, colorTex.getFormat(), w, h, 1, 1);
                            sceneCopyView = RenderSystem.getDevice().createTextureView(sceneCopyTex);
                        }

                        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
                        encoder.copyTextureToTexture(depthTex, depthCopyTex, 0, 0, 0, 0, 0, w, h);
                        encoder.copyTextureToTexture(colorTex, sceneCopyTex, 0, 0, 0, 0, 0, w, h);
                        sceneCaptured = true;
                    }
                }
            }
        }
    }

    public static void flush(Matrix4f projection) {
        collecting = false;
        Minecraft mc = Minecraft.getInstance();
        if (sceneCaptured && lightCount != 0 && mc.level != null && projection != null) {
            sceneCaptured = false;
            RenderTarget mainTarget = mc.gameRenderer.mainRenderTarget();
            if (mainTarget != null
                    && mainTarget.getColorTextureView() != null
                    && depthCopyTex != null
                    && !depthCopyTex.isClosed()
                    && depthCopyView != null
                    && !depthCopyView.isClosed()
                    && sceneCopyTex != null
                    && !sceneCopyTex.isClosed()
                    && sceneCopyView != null
                    && !sceneCopyView.isClosed()
                    && depthCopyTex.getWidth(0) == mainTarget.width
                    && depthCopyTex.getHeight(0) == mainTarget.height) {
                INV_PROJ.set(projection).invert();
                INV_VIEW.set(VIEW_MAT).invert();
                if (INV_PROJ.isFinite() && INV_VIEW.isFinite()) {
                    float minU = 1.0F;
                    float minV = 1.0F;
                    float maxU = 0.0F;
                    float maxV = 0.0F;

                    for (int i = 0; i < lightCount; i++) {
                        Vector3f pos = LIGHT_POS[i];
                        float r = LIGHT_RADIUS[i];
                        if (!(pos.z - r >= 0.0F)) {
                            for (int corner = 0; corner < 8; corner++) {
                                TEMP_CLIP_POS.set(
                                        pos.x + ((corner & 1) == 0 ? -r : r),
                                        pos.y + ((corner & 2) == 0 ? -r : r),
                                        pos.z + ((corner & 4) == 0 ? -r : r),
                                        1.0F
                                ).mul(projection);
                                if (TEMP_CLIP_POS.w <= 0.01F) {
                                    minV = 0.0F;
                                    minU = 0.0F;
                                    maxV = 1.0F;
                                    maxU = 1.0F;
                                    break;
                                }
                                float u = TEMP_CLIP_POS.x / TEMP_CLIP_POS.w * 0.5F + 0.5F;
                                float v = TEMP_CLIP_POS.y / TEMP_CLIP_POS.w * 0.5F + 0.5F;
                                minU = Math.min(minU, u);
                                minV = Math.min(minV, v);
                                maxU = Math.max(maxU, u);
                                maxV = Math.max(maxV, v);
                            }
                        }
                    }

                    int sx0 = Math.max(0, (int) Math.floor(minU * mainTarget.width) - 2);
                    int sy0 = Math.max(0, (int) Math.floor(minV * mainTarget.height) - 2);
                    int sx1 = Math.min(mainTarget.width, (int) Math.ceil(maxU * mainTarget.width) + 2);
                    int sy1 = Math.min(mainTarget.height, (int) Math.ceil(maxV * mainTarget.height) + 2);
                    if (sx1 > sx0 && sy1 > sy0) {
                        Vec3 camPos = mc.gameRenderer.mainCamera().position();
                        BLOCK_SAMPLER.sample(INV_VIEW, LIGHT_POS, LIGHT_RADIUS, lightCount, camPos);
                        try (MemoryStack stack = MemoryStack.stackPush()) {
                            Std140Builder builder = Std140Builder.onStack(stack, UBO_SIZE)
                                    .putMat4f(projection)
                                    .putMat4f(INV_PROJ)
                                    .putVec4(1.0F / mainTarget.width, 1.0F / mainTarget.height, lightCount, 4.5F);

                            for (int i = 0; i < MAX_LIGHTS; i++) {
                                Vector3f p = LIGHT_POS[i];
                                builder.putVec4(p.x, p.y, p.z, LIGHT_RADIUS[i]);
                            }

                            for (int i = 0; i < MAX_LIGHTS; i++) {
                                Vector3f c = LIGHT_COLOR[i];
                                builder.putVec4(c.x, c.y, c.z, LIGHT_INTENSITY[i]);
                            }

                            builder.putMat4f(INV_VIEW).putVec4(
                                    BLOCK_SAMPLER.relativeOriginX(camPos),
                                    BLOCK_SAMPLER.relativeOriginY(camPos),
                                    BLOCK_SAMPLER.relativeOriginZ(camPos),
                                    2.0F
                            );
                            int[] words = BLOCK_SAMPLER.getOccluderWords();
                            for (int i = 0; i < words.length; i += 4) {
                                builder.putIVec4(words[i], words[i + 1], words[i + 2], words[i + 3]);
                            }

                            GpuBufferSlice uboSlice = GpuStaging.uploadUniform(builder.get());
                            try (RenderPass pass = RenderSystem.getDevice()
                                    .createCommandEncoder()
                                    .createRenderPass(() -> "Target surface lighting", mainTarget.getColorTextureView(), Optional.empty())) {
                                pass.setPipeline(PipelineBuilder.compile(PIPELINE));
                                pass.setUniform("TargetLighting", uboSlice);
                                pass.setUniform("DepthSampler", depthCopyView, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
                                pass.setUniform("SceneSampler", sceneCopyView, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
                                pass.enableScissor(sx0, sy0, sx1 - sx0, sy1 - sy0);
                                pass.draw(3, 1, 0, 0);
                            }
                        }
                    }
                }
            }
        }
    }
}
