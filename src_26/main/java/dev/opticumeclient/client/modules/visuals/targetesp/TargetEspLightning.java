package dev.opticumeclient.client.modules.visuals.targetesp;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.RenderSystem.AutoStorageIndexBuffer;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.MeshData.DrawState;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.api.pipeline.BlendFactor;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.UniformType;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.DynamicGpuDataStorage.DynamicGpuData;
import net.minecraft.client.renderer.DynamicGpuDataStorageMapped;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

public final class TargetEspLightning {
    public static final String DEPTH_AUTO = "Auto";
    public static final String DEPTH_ALWAYS = "Always";
    public static final String DEPTH_NEVER = "Never";

    private static final Minecraft MC = Minecraft.getInstance();
    private static final Logger LOGGER = LoggerFactory.getLogger("opticume-target-lightning");
    private static final List<Runnable> PENDING_CLEANUP = new ArrayList<>();

    private static final BlendFunction LINE_BLEND = new BlendFunction(BlendFactor.SRC_ALPHA, BlendFactor.ONE, BlendFactor.ONE, BlendFactor.ZERO);
    private static final BlendFunction COMPOSITE_BLEND = new BlendFunction(
            BlendFactor.ONE, BlendFactor.ONE_MINUS_SRC_ALPHA, BlendFactor.ONE, BlendFactor.ONE_MINUS_SRC_ALPHA
    );

    private static final RenderPipeline LINE_NO_DEPTH_PIPELINE = createLinePipeline("target_lightning", CompareOp.ALWAYS_PASS);
    private static final RenderPipeline LINE_DEPTH_PIPELINE = createLinePipeline("target_lightning_depth", CompareOp.GREATER_THAN_OR_EQUAL);

    private static final RenderPipeline BLUR_PIPELINE = PipelineBuilder.create()
            .vertexShader(EspResources.shader("esp/esp_fullscreen"))
            .fragmentShader(EspResources.shader("target/lightning_blur"))
            .sampler("Sampler0")
            .uniform("LightningBlur", UniformType.UNIFORM_BUFFER)
            .noBlend()
            .depthTest(CompareOp.ALWAYS_PASS)
            .writeDepth(false)
            .cull(false)
            .vertexFormat(DefaultVertexFormat.POSITION, PrimitiveTopology.TRIANGLE_STRIP)
            .location(Identifier.fromNamespaceAndPath("opticumeclient", "pipeline/target_lightning_blur"))
            .build();

    private static final RenderPipeline COMPOSITE_PIPELINE = PipelineBuilder.create()
            .vertexShader(EspResources.shader("esp/esp_fullscreen"))
            .fragmentShader(EspResources.shader("target/lightning_composite"))
            .sampler("Sampler0")
            .uniform("LightningComposite", UniformType.UNIFORM_BUFFER)
            .blend(COMPOSITE_BLEND)
            .depthTest(CompareOp.ALWAYS_PASS)
            .writeDepth(false)
            .cull(false)
            .vertexFormat(DefaultVertexFormat.POSITION, PrimitiveTopology.TRIANGLE_STRIP)
            .location(Identifier.fromNamespaceAndPath("opticumeclient", "pipeline/target_lightning_composite"))
            .build();

    private static DynamicGpuDataStorageMapped<LineUniformData> lineUniforms;
    private static DynamicGpuDataStorageMapped<FloatParamData> blurUniforms;
    private static DynamicGpuDataStorageMapped<FloatParamData> compositeUniforms;

    private static GpuBuffer vertexBuffer;
    private static long vertexBufferCapacity;
    private static GpuBuffer fullscreenQuadBuffer;
    private static GpuSampler linearSampler;
    private static TextureTarget captureTarget;
    private static TextureTarget blurTargetA;
    private static TextureTarget blurTargetB;
    private static long lastFrameNs;
    private static float elapsedSeconds;
    private static boolean renderFailed;

    private TargetEspLightning() {
    }

    private static void ensureUniformStorages() {
        if (lineUniforms == null) {
            lineUniforms = new DynamicGpuDataStorageMapped<>("Target lightning line UBO", 96, GpuBuffer.USAGE_UNIFORM, 256);
        }
        if (blurUniforms == null) {
            blurUniforms = new DynamicGpuDataStorageMapped<>("Target lightning blur UBO", 16, GpuBuffer.USAGE_UNIFORM, 256);
        }
        if (compositeUniforms == null) {
            compositeUniforms = new DynamicGpuDataStorageMapped<>("Target lightning composite UBO", 16, GpuBuffer.USAGE_UNIFORM, 256);
        }
    }

    private static void compositeBloom(RenderTarget mainTarget, float blurScale, float alphaFactor, boolean distortion, float distortionStrength) {
        if (captureTarget != null && blurTargetA != null && blurTargetB != null && fullscreenQuadBuffer != null) {
            ensureUniformStorages();
            CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
            runBlurPass(encoder, captureTarget.getColorTextureView(), blurTargetB, blurScale, "OpticumeClient TargetESP lightning blur horizontal");
            runBlurPass(encoder, blurTargetB.getColorTextureView(), blurTargetA, -blurScale * 0.5F, "OpticumeClient TargetESP lightning blur vertical");
            if (distortion) {
                GlowDistortionPass.INSTANCE.renderFromTarget(blurTargetA, distortionStrength);
            }

            GpuBufferSlice uboSlice = compositeUniforms.writeData(new FloatParamData(alphaFactor));
            try (RenderPass pass = encoder.createRenderPass(() -> "OpticumeClient TargetESP lightning bloom composite", mainTarget.getColorTextureView(), Optional.empty(), null, OptionalDouble.empty())) {
                pass.setPipeline(PipelineBuilder.compile(COMPOSITE_PIPELINE));
                pass.setUniform("LightningComposite", uboSlice);
                pass.setUniform("Sampler0", blurTargetA.getColorTextureView(), getLinearSampler());
                pass.setVertexBuffer(0, fullscreenQuadBuffer.slice());
                pass.draw(4, 1, 0, 0);
            }
        }
    }

    private static void scheduleRelease() {
        scheduleDestroyTarget(captureTarget);
        scheduleDestroyTarget(blurTargetA);
        scheduleDestroyTarget(blurTargetB);
        captureTarget = null;
        blurTargetA = null;
        blurTargetB = null;
        scheduleCloseBuffer(vertexBuffer);
        vertexBuffer = null;
        vertexBufferCapacity = 0L;
        scheduleCloseBuffer(fullscreenQuadBuffer);
        fullscreenQuadBuffer = null;
        linearSampler = null;
    }

    public static void releaseGpuResources() {
        scheduleRelease();
        runPendingCleanup();
        if (lineUniforms != null) {
            lineUniforms.close();
            lineUniforms = null;
        }
        if (blurUniforms != null) {
            blurUniforms.close();
            blurUniforms = null;
        }
        if (compositeUniforms != null) {
            compositeUniforms.close();
            compositeUniforms = null;
        }
        lastFrameNs = 0L;
        elapsedSeconds = 0.0F;
        renderFailed = false;
    }

    private static void renderInternal(Camera camera, float partialTicks, LivingEntity target, float alpha, int color, boolean redOnHit, String depthMode, boolean distortion, float distortionStrength) {
        RenderTarget mainTarget = MC.gameRenderer.mainRenderTarget();
        if (mainTarget != null && mainTarget.width > 0 && mainTarget.height > 0) {
            ensureUniformStorages();
            ensureTargets(mainTarget.width, mainTarget.height);
            ensureFullscreenQuad(RenderSystem.getDevice());
            long nowNs = System.nanoTime();
            float dt = lastFrameNs == 0L ? 0.016666668F : Math.min((float) (nowNs - lastFrameNs) / 1.0E9F, 0.05F);
            lastFrameNs = nowNs;
            elapsedSeconds += dt;
            Vec3 camPos = camera.position();
            float rx = (float) (Mth.lerp(partialTicks, target.xOld, target.getX()) - camPos.x);
            float ry = (float) (Mth.lerp(partialTicks, target.yOld, target.getY()) - camPos.y);
            float rz = (float) (Mth.lerp(partialTicks, target.zOld, target.getZ()) - camPos.z);
            float height = target.getBbHeight();
            float radius = target.getBbWidth() * 0.75F + 0.16F;
            int activeColor = color;
            if (redOnHit && target.hurtTime > 0) {
                float ht = Math.max(0.0F, target.hurtTime - partialTicks);
                float hd = Math.max(1.0F, target.hurtDuration);
                float ratio = Mth.clamp(ht / hd, 0.0F, 1.0F);
                float curve = (float) Math.pow(ratio, 0.35);
                activeColor = lerpColor(color, color & 0xFF000000 | 0xFF195D, curve);
            }

            boolean useDepth = shouldUseDepth(depthMode, camPos, target);
            RenderPipeline pipeline = useDepth ? LINE_DEPTH_PIPELINE : LINE_NO_DEPTH_PIPELINE;
            Matrix4f mvp = computeViewProj();
            if (mvp != null) {
                float centerY = ry + height * 0.5F;
                double dist = Math.sqrt(rx * rx + centerY * centerY + rz * rz);
                float blurScale = computeBlurScale(0.085F, dist, mvp, mainTarget.height);
                GpuTextureView depthView = mainTarget.hasDepth() ? mainTarget.getDepthTextureView() : null;
                MeshData captureMesh = buildLightningMesh(rx, ry, rz, height, radius, activeColor, alpha, 10.0F, 1.0F);
                if (captureMesh != null) {
                    drawMesh(captureMesh, pipeline, mvp, mainTarget.width, mainTarget.height, captureTarget.getColorTextureView(), depthView, true);
                    compositeBloom(mainTarget, blurScale, alpha, distortion, distortionStrength);
                }

                MeshData wideMesh = buildLightningMesh(rx, ry, rz, height, radius, activeColor, alpha, 22.0F, 0.4F);
                drawMesh(wideMesh, pipeline, mvp, mainTarget.width, mainTarget.height, mainTarget.getColorTextureView(), depthView, false);
                MeshData coreMesh = buildLightningMesh(rx, ry, rz, height, radius, activeColor, alpha, 9.0F, 1.0F);
                drawMesh(coreMesh, pipeline, mvp, mainTarget.width, mainTarget.height, mainTarget.getColorTextureView(), depthView, false);
            }
        }
    }

    private static int applyAlpha(int color, float factor) {
        int a = Math.round((color >>> 24 & 0xFF) * Mth.clamp(factor, 0.0F, 1.0F));
        return a << 24 | color & 0xFFFFFF;
    }

    private static void emitSegment(BufferBuilder builder, float x1, float y1, float z1, float x2, float y2, float z2, int c1, int c2, float lineWidth) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float dz = z2 - z1;
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (!(len < 1.0E-5F)) {
            float invLen = 1.0F / len;
            float nx = dx * invLen;
            float ny = dy * invLen;
            float nz = dz * invLen;
            builder.addVertex(x1, y1, z1).setColor(c1).setNormal(nx, ny, nz).setLineWidth(lineWidth);
            builder.addVertex(x2, y2, z2).setColor(c2).setNormal(nx, ny, nz).setLineWidth(lineWidth);
        }
    }

    private static void ensureVertexBuffer(int requiredBytes) {
        if (vertexBuffer == null || vertexBuffer.isClosed() || vertexBufferCapacity < requiredBytes) {
            scheduleCloseBuffer(vertexBuffer);
            long cap = 8192L;
            while (cap < requiredBytes) {
                cap <<= 1;
            }
            vertexBuffer = RenderSystem.getDevice().createBuffer(() -> "OpticumeClient TargetESP lightning vertices", GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST, cap);
            vertexBufferCapacity = cap;
        }
    }

    private static void scheduleDestroyTarget(TextureTarget target) {
        if (target != null) {
            PENDING_CLEANUP.add(target::destroyBuffers);
        }
    }

    private static Matrix4f computeViewProj() {
        Matrix4f proj = WorldProjection.getProjectionMatrix();
        Matrix4f view = WorldProjection.getViewMatrix();
        return proj != null && view != null ? proj.mul(view) : null;
    }

    private static void ensureFullscreenQuad(GpuDevice device) {
        if (fullscreenQuadBuffer == null || fullscreenQuadBuffer.isClosed()) {
            fullscreenQuadBuffer = null;
            float[] quad = new float[]{-1.0F, -1.0F, 0.0F, 1.0F, -1.0F, 0.0F, -1.0F, 1.0F, 0.0F, 1.0F, 1.0F, 0.0F};
            ByteBuffer buf = ByteBuffer.allocateDirect(quad.length * 4).order(ByteOrder.nativeOrder());
            for (float v : quad) {
                buf.putFloat(v);
            }
            buf.flip();
            fullscreenQuadBuffer = device.createBuffer(() -> "OpticumeClient TargetESP lightning fullscreen quad", GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST, quad.length * 4L);
            device.createCommandEncoder().writeToBuffer(fullscreenQuadBuffer.slice(0L, quad.length * 4L), buf);
        }
    }

    public static void render(Camera camera, float partialTicks, LivingEntity target, float alpha, int color, boolean redOnHit, String depthMode, boolean distortion, float distortionStrength) {
        if (!renderFailed && camera != null && target != null && !(alpha <= 0.007843138F) && MC.level != null && MC.player != null) {
            try {
                renderInternal(camera, partialTicks, target, alpha, color, redOnHit, depthMode, distortion, distortionStrength);
            } catch (Throwable t) {
                renderFailed = true;
                LOGGER.error("Lightning TargetESP disabled after a render failure", t);
                scheduleRelease();
            }
        }
    }

    public static void render(Camera camera, float partialTicks, LivingEntity target, float alpha, int color, boolean redOnHit, String depthMode) {
        render(camera, partialTicks, target, alpha, color, redOnHit, depthMode, false, 12.0F);
    }

    private static int lerpColor(int c1, int c2, float t) {
        float clamped = Mth.clamp(t, 0.0F, 1.0F);
        int a = Mth.lerpInt(clamped, c1 >>> 24 & 0xFF, c2 >>> 24 & 0xFF);
        int r = Mth.lerpInt(clamped, c1 >>> 16 & 0xFF, c2 >>> 16 & 0xFF);
        int g = Mth.lerpInt(clamped, c1 >>> 8 & 0xFF, c2 >>> 8 & 0xFF);
        int b = Mth.lerpInt(clamped, c1 & 0xFF, c2 & 0xFF);
        return a << 24 | r << 16 | g << 8 | b;
    }

    private static void subdivideBolt(
            BufferBuilder builder,
            float x1, float y1, float z1,
            float x2, float y2, float z2,
            int depth,
            float lineWidth,
            float a1,
            float a2,
            int baseColor,
            int seed,
            int branchGeneration,
            int timeStep,
            float timeFract
    ) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float dz = z2 - z1;
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (depth > 0 && !(len < 1.0E-4F)) {
            float dirX = dx / len;
            float dirY = dy / len;
            float dirZ = dz / len;
            float upX = Math.abs(dirY) < 0.9F ? 0.0F : 1.0F;
            float upY = Math.abs(dirY) < 0.9F ? 1.0F : 0.0F;
            float side1X = -dirZ * upY;
            float side1Y = dirZ * upX;
            float side1Z = dirX * upY - dirY * upX;
            float side1Len = (float) Math.sqrt(side1X * side1X + side1Y * side1Y + side1Z * side1Z);
            if (side1Len < 1.0E-5F) {
                side1Len = 1.0F;
            }
            side1X /= side1Len;
            side1Y /= side1Len;
            side1Z /= side1Len;
            float side2X = dirY * side1Z - dirZ * side1Y;
            float side2Y = dirZ * side1X - dirX * side1Z;
            float side2Z = dirX * side1Y - dirY * side1X;
            float n1 = smoothNoise(seed, 0, 0, timeStep, timeFract);
            float n2 = smoothNoise(seed, 1, 0, timeStep, timeFract);
            float displace = len * 0.3F;
            float midX = (x1 + x2) * 0.5F + (side1X * n1 + side2X * n2) * displace;
            float midY = (y1 + y2) * 0.5F + (side1Y * n1 + side2Y * n2) * displace;
            float midZ = (z1 + z2) * 0.5F + (side1Z * n1 + side2Z * n2) * displace;
            float midA = (a1 + a2) * 0.5F;
            subdivideBolt(builder, x1, y1, z1, midX, midY, midZ, depth - 1, lineWidth, a1, midA, baseColor, seed * 2 + 7, branchGeneration, timeStep, timeFract);
            subdivideBolt(builder, midX, midY, midZ, x2, y2, z2, depth - 1, lineWidth, midA, a2, baseColor, seed * 2 + 9, branchGeneration, timeStep, timeFract);
            if (branchGeneration < 2 && depth >= 2 && smoothNoise(seed, 2, 0, timeStep, timeFract) > 0.45F) {
                float bn1 = smoothNoise(seed, 3, 0, timeStep, timeFract);
                float bn2 = smoothNoise(seed, 4, 0, timeStep, timeFract);
                float bx = dirX * 0.4F + side1X * bn1 + side2X * bn2;
                float by = dirY * 0.4F + side1Y * bn1 + side2Y * bn2;
                float bz = dirZ * 0.4F + side1Z * bn1 + side2Z * bn2;
                float bLen = (float) Math.sqrt(bx * bx + by * by + bz * bz);
                if (bLen < 1.0E-5F) {
                    bLen = 1.0F;
                }
                float branchScale = len * 0.75F / bLen;
                subdivideBolt(
                        builder,
                        midX, midY, midZ,
                        midX + bx * branchScale, midY + by * branchScale, midZ + bz * branchScale,
                        Math.min(depth - 1, 3),
                        lineWidth * 0.55F,
                        midA * 0.7F,
                        0.0F,
                        baseColor,
                        seed * 3 + 13,
                        branchGeneration + 1,
                        timeStep,
                        timeFract
                );
            }
        } else {
            emitSegment(builder, x1, y1, z1, x2, y2, z2, applyAlpha(baseColor, a1), applyAlpha(baseColor, a2), lineWidth);
        }
    }

    private static MeshData buildLightningMesh(float rx, float ry, float rz, float height, float radius, int color, float alpha, float lineWidth, float alphaScale) {
        BufferBuilder builder = BufferPool.getInstance().begin(PrimitiveTopology.LINES, DefaultVertexFormat.POSITION_COLOR_NORMAL_LINE_WIDTH);
        int boltCount = 3;
        int segments = 3;
        float scaledTime = elapsedSeconds * 6.0F;
        int timeStep = (int) scaledTime;
        float timeFract = scaledTime - timeStep;
        float cycleRate = 1.3F;
        float tau = (float) (Math.PI * 2.0);

        for (int b = 0; b < boltCount; b++) {
            float cycle = elapsedSeconds * cycleRate + (float) b / boltCount;
            int cycleIdx = (int) Math.floor(cycle);
            float phase = cycle - cycleIdx;
            float fadeIn = Mth.clamp(phase / 0.08F, 0.0F, 1.0F);
            float fadeOut = 1.0F - Mth.clamp((phase - 0.42F) / 0.3F, 0.0F, 1.0F);
            float flicker = Mth.clamp(0.55F + 0.45F * smoothNoise(b, 900, 0, timeStep, timeFract), 0.0F, 1.0F);
            float boltAlpha = fadeIn * fadeOut * flicker;
            if (!(boltAlpha <= 0.01F)) {
                float finalAlpha = alpha * boltAlpha * alphaScale;
                float yWave = height * 0.16F * (float) Math.sin(elapsedSeconds * 1.7F + b * 2.1F);
                float baseAngle = hashNoise(b, 11, cycleIdx) * (float) Math.PI;
                float turns = 0.6F + 0.5F * Math.abs(hashNoise(b, 12, cycleIdx));
                float dirSign = hashNoise(b, 13, cycleIdx) >= 0.0F ? 1.0F : -1.0F;
                float topY = ry + height * (0.85F + 0.05F * hashNoise(b, 14, cycleIdx));
                float spanY = height * 0.67F;
                float boltRadius = radius * (1.0F + 0.12F * hashNoise(b, 15, cycleIdx));
                float prevX = 0.0F;
                float prevY = 0.0F;
                float prevZ = 0.0F;

                for (int s = 0; s <= segments; s++) {
                    float t = (float) s / segments;
                    float angle = baseAngle + elapsedSeconds * 0.9F + dirSign * turns * tau * t;
                    float px = rx + (float) Math.cos(angle) * boltRadius;
                    float pz = rz + (float) Math.sin(angle) * boltRadius;
                    float py = topY - spanY * t + yWave;
                    if (lineWidth == 10.0F && s > 0 && s < segments) {
                        SurfaceLightingPass.addLight(px, py, pz, 0.5F, applyAlpha(color, finalAlpha));
                    }
                    if (s > 0) {
                        int segSeed = (b * 131 + s) * 17 + cycleIdx * 101 | 1;
                        subdivideBolt(builder, prevX, prevY, prevZ, px, py, pz, 4, lineWidth, finalAlpha, finalAlpha, color, segSeed, 0, timeStep, timeFract);
                    }
                    prevX = px;
                    prevY = py;
                    prevZ = pz;
                }
            }
        }
        return builder.build();
    }

    private static GpuSampler getLinearSampler() {
        if (linearSampler == null) {
            linearSampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        }
        return linearSampler;
    }

    private static float hashNoise(int a, int b, int c) {
        int h = a * 374761393 + b * 668265263 + c * 1274126177;
        h = (h ^ h >>> 13) * 1274126177;
        h ^= h >>> 16;
        return (h & 65535) / 32768.0F - 1.0F;
    }

    public static void endFrame() {
        if (lineUniforms != null) {
            lineUniforms.endFrame();
        }
        if (blurUniforms != null) {
            blurUniforms.endFrame();
        }
        if (compositeUniforms != null) {
            compositeUniforms.endFrame();
        }
        runPendingCleanup();
    }

    private static void runBlurPass(CommandEncoder encoder, GpuTextureView srcView, TextureTarget dstTarget, float offset, String label) {
        GpuBufferSlice uboSlice = blurUniforms.writeData(new FloatParamData(offset));
        try (RenderPass pass = encoder.createRenderPass(() -> label, dstTarget.getColorTextureView(), Optional.of(new Vector4f(0.0F)), null, OptionalDouble.empty())) {
            pass.setPipeline(PipelineBuilder.compile(BLUR_PIPELINE));
            pass.setUniform("LightningBlur", uboSlice);
            pass.setUniform("Sampler0", srcView, getLinearSampler());
            pass.setVertexBuffer(0, fullscreenQuadBuffer.slice());
            pass.draw(4, 1, 0, 0);
        }
    }

    private static void runPendingCleanup() {
        for (int i = 0; i < PENDING_CLEANUP.size(); i++) {
            try {
                PENDING_CLEANUP.get(i).run();
            } catch (Throwable ignored) {
            }
        }
        PENDING_CLEANUP.clear();
    }

    private static void drawMesh(MeshData mesh, RenderPipeline pipeline, Matrix4f mvp, int screenWidth, int screenHeight, GpuTextureView colorView, GpuTextureView depthView, boolean clearColor) {
        if (mesh != null && colorView != null) {
            try (mesh) {
                ByteBuffer vb = mesh.vertexBuffer();
                int bytes = vb.remaining();
                if (bytes <= 0) {
                    return;
                }
                ensureVertexBuffer(bytes);
                CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
                encoder.writeToBuffer(vertexBuffer.slice(0L, bytes), vb);
                GpuBufferSlice uboSlice = lineUniforms.writeData(new LineUniformData(mvp, screenWidth, screenHeight));
                try (RenderPass pass = encoder.createRenderPass(
                        () -> "OpticumeClient TargetESP lightning",
                        colorView,
                        clearColor ? Optional.of(new Vector4f(0.0F)) : Optional.empty(),
                        depthView,
                        OptionalDouble.empty()
                )) {
                    pass.setPipeline(PipelineBuilder.compile(pipeline));
                    pass.setUniform("LightningLine", uboSlice);
                    pass.setVertexBuffer(0, vertexBuffer.slice());
                    DrawState state = mesh.drawState();
                    AutoStorageIndexBuffer seq = RenderSystem.getSequentialBuffer(state.primitiveTopology());
                    pass.setIndexBuffer(seq.getBuffer(state.indexCount()), seq.type());
                    pass.drawIndexed(state.indexCount(), 1, 0, 0, 0);
                }
            }
        }
    }

    private static void ensureTargets(int width, int height) {
        int halfW = Math.max(1, width / 2);
        int halfH = Math.max(1, height / 2);
        if (captureTarget == null || captureTarget.width != width || captureTarget.height != height) {
            scheduleDestroyTarget(captureTarget);
            captureTarget = new TextureTarget("OpticumeClient TargetESP lightning capture", width, height, GpuFormat.RGBA8_UNORM, null);
        }
        if (blurTargetA == null || blurTargetA.width != halfW || blurTargetA.height != halfH) {
            scheduleDestroyTarget(blurTargetA);
            blurTargetA = new TextureTarget("OpticumeClient TargetESP lightning blur A", halfW, halfH, GpuFormat.RGBA8_UNORM, null);
        }
        if (blurTargetB == null || blurTargetB.width != halfW || blurTargetB.height != halfH) {
            scheduleDestroyTarget(blurTargetB);
            blurTargetB = new TextureTarget("OpticumeClient TargetESP lightning blur B", halfW, halfH, GpuFormat.RGBA8_UNORM, null);
        }
    }

    private static float computeBlurScale(float baseScale, double distance, Matrix4f mvp, int screenHeight) {
        float projScale = (float) Math.sqrt(mvp.m01() * mvp.m01() + mvp.m11() * mvp.m11() + mvp.m21() * mvp.m21());
        float factor = projScale * screenHeight / (2.0F * (float) Math.max(distance, 0.5));
        return Mth.clamp(baseScale * factor, 1.0F, 36.0F);
    }

    private static float smoothNoise(int a, int b, int offset, int step, float fract) {
        float v0 = hashNoise(a, b, step * 4 + offset);
        float v1 = hashNoise(a, b, (step + 1) * 4 + offset);
        float s = fract * fract * (3.0F - 2.0F * fract);
        return v0 + (v1 - v0) * s;
    }

    private static void scheduleCloseBuffer(GpuBuffer buffer) {
        if (buffer != null) {
            PENDING_CLEANUP.add(() -> {
                if (!buffer.isClosed()) {
                    buffer.close();
                }
            });
        }
    }

    private static boolean shouldUseDepth(String depthMode, Vec3 camPos, LivingEntity target) {
        if (DEPTH_ALWAYS.equalsIgnoreCase(depthMode)) {
            return true;
        }
        if (!DEPTH_NEVER.equalsIgnoreCase(depthMode) && MC.level != null) {
            AABB box = target.getBoundingBox();
            Vec3 closest = new Vec3(
                    Mth.clamp(camPos.x, box.minX, box.maxX),
                    Mth.clamp(camPos.y, box.minY, box.maxY),
                    Mth.clamp(camPos.z, box.minZ, box.maxZ)
            );
            if (closest.distanceToSqr(camPos) < 1.0E-8) {
                return false;
            }
            BlockHitResult hit = MC.level.clip(new ClipContext(camPos, closest, Block.OUTLINE, Fluid.NONE, MC.player));
            return hit.getType() != Type.MISS;
        }
        return false;
    }

    private static RenderPipeline createLinePipeline(String name, CompareOp depthCompare) {
        return PipelineBuilder.create()
                .vertexShader(EspResources.shader("target/lightning_line"))
                .fragmentShader(EspResources.shader("target/lightning"))
                .uniform("LightningLine", UniformType.UNIFORM_BUFFER)
                .blend(LINE_BLEND)
                .depthTest(depthCompare)
                .writeDepth(false)
                .cull(false)
                .vertexFormat(DefaultVertexFormat.POSITION_COLOR_NORMAL_LINE_WIDTH, PrimitiveTopology.LINES)
                .location(Identifier.fromNamespaceAndPath("opticumeclient", name))
                .build();
    }

    private record FloatParamData(float value) implements DynamicGpuData {
        @Override
        public void write(ByteBuffer buffer) {
            buffer.putFloat(this.value).putFloat(0.0F).putFloat(0.0F).putFloat(0.0F);
        }
    }

    private record LineUniformData(Matrix4f mvp, float screenWidth, float screenHeight) implements DynamicGpuData {
        @Override
        public void write(ByteBuffer buffer) {
            this.mvp.get(buffer);
            buffer.position(buffer.position() + 64);
            buffer.putFloat(1.0F).putFloat(1.0F).putFloat(1.0F).putFloat(1.0F);
            buffer.putFloat(this.screenWidth).putFloat(this.screenHeight).putFloat(0.0F).putFloat(0.0F);
        }
    }
}
