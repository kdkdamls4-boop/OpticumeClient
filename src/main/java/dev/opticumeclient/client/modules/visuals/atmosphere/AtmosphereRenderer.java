package dev.opticumeclient.client.modules.visuals.atmosphere;

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
import dev.opticumeclient.client.modules.visuals.AmbienceModule;
import dev.opticumeclient.client.modules.visuals.ambience.WorldRenderBridge;
import dev.opticumeclient.client.modules.visuals.targetesp.PipelineBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.LightLayer;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.Optional;

/**
 * Screen-space volumetric height fog, low haze, precipitation layers,
 * surface ripples, and projected lightning bolt post-processor.
 */
public final class AtmosphereRenderer {
    private static final AtmosphereRenderer INSTANCE = new AtmosphereRenderer();
    private static final int UBO_SIZE = 368; // mat4 (64) + 19 * vec4 (304)
    private static final String UNIFORM_NAME = "AtmosphereUniforms";

    private float smoothedOutside = 1.0f;
    private long lastFrameNanos = 0L;

    private static final RenderPipeline PIPELINE = PipelineBuilder.create()
            .location("opticumeclient/atmosphere_fog")
            .vertexShader(WorldRenderBridge.FULLSCREEN_QUAD_VSH)
            .fragmentShader(Identifier.fromNamespaceAndPath("opticumeclient", "core/atmosphere/atmosphere_fog"))
            .depthTest(CompareOp.ALWAYS_PASS)
            .writeDepth(false)
            .cull(false)
            .noBlend()
            .vertexFormat(PipelineBuilder.EMPTY_VERTEX_FORMAT, PrimitiveTopology.TRIANGLES)
            .uniform(UNIFORM_NAME, UniformType.UNIFORM_BUFFER)
            .sampler("DepthSampler")
            .sampler("SceneSampler")
            .build();

    private final StormState stormState = new StormState();
    private final Matrix4f viewProjScratch = new Matrix4f();
    private final Matrix4f invViewProjScratch = new Matrix4f();
    private final Vector4f boltTopClip = new Vector4f();
    private final Vector4f boltBotClip = new Vector4f();
    private final Vector4f sunClip = new Vector4f();
    private TextureTarget sceneCopy;
    private GpuBuffer uniformBuffer;

    private AtmosphereRenderer() {
    }

    public static AtmosphereRenderer getInstance() {
        return INSTANCE;
    }

    public void reset() {
        stormState.reset();
    }

    public void render(AmbienceModule module, CameraRenderState camera) {
        render(module, camera, false);
    }

    public boolean render(AmbienceModule module, CameraRenderState camera, boolean fuseSaturation) {
        if (module == null || camera == null || !module.isAtmosphereActive()) {
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.gameRenderer == null || mc.level == null) {
            return false;
        }
        RenderTarget main = mc.gameRenderer.mainRenderTarget();
        if (main == null || main.width <= 1 || main.height <= 1
                || main.getColorTextureView() == null
                || main.getDepthTextureView() == null) {
            return false;
        }

        stormState.update(module.isAtmoLightningEnabled());

        float fogDensity = module.isAtmoFogEnabled() ? module.getAtmoEffectiveFogDensity() : 0.0f;
        float hazeDensity = module.isAtmoHazeEnabled() ? module.getAtmoEffectiveHazeDensity() : 0.0f;
        float rainDensity = module.isAtmoPrecipitationEnabled() ? module.getAtmoEffectivePrecipDensity() : 0.0f;
        float rippleStrength = module.isAtmoRipplesEnabled() ? module.getAtmoRippleStrength() : 0.0f;
        float boltIntensity = stormState.getBoltIntensity();
        float rawGodRays = module.getAtmoEffectiveGodRays();
        float postGrade = module.getAtmoPostGrade();
        float vignette = module.getAtmoVignette();
        float frost = module.getAtmoFrostAmount();
        int ddaMode = module.getAtmoDdaModeIndex();
        float ddaAmount = ddaMode > 0 ? module.getAtmoDdaAmount() : 0.0f;
        float fusedSat = fuseSaturation ? module.getSaturationLevel() : 1.0f;
        boolean hasFusedSat = fuseSaturation && Math.abs(fusedSat - 1.0f) > 0.005f;

        if (fogDensity <= 0.0005f && hazeDensity <= 0.0005f && rainDensity <= 0.0005f
                && rippleStrength <= 0.0005f && boltIntensity <= 0.005f && rawGodRays <= 0.0005f
                && postGrade <= 0.0005f && vignette <= 0.0005f && frost <= 0.0005f && ddaAmount <= 0.0005f
                && !hasFusedSat) {
            return false;
        }

        try {
            sceneCopy = WorldRenderBridge.ensureColorTarget(sceneCopy, "opticume-atmo-copy", main.width, main.height);
            if (!WorldRenderBridge.copyMainColor(main, sceneCopy)) {
                return false;
            }

            Matrix4f viewProj = viewProjScratch.set(camera.projectionMatrix).mul(camera.viewRotationMatrix);
            Matrix4f invViewProj = invViewProjScratch.set(viewProj).invert();
            float aspect = (float) main.width / Math.max(1.0f, (float) main.height);
            float reverseZ = WorldRenderBridge.isReverseZ() ? 1.0f : 0.0f;
            float t = stormState.getElapsedSeconds();

            int fogRgb = module.getAtmoEffectiveFogColor();
            float fogR = WorldRenderBridge.red(fogRgb);
            float fogG = WorldRenderBridge.green(fogRgb);
            float fogB = WorldRenderBridge.blue(fogRgb);
            float invH = 1.0f / Math.max(4.0f, module.getAtmoEffectiveFogHeight());
            float scatterMult = 1.05f;

            float hazeInvH = 1.0f / 14.0f;
            float deckOffset = (float) camera.pos.y - 68.0f;

            // Compute sun angle from level clock
            long ticks = mc.level.getOverworldClockTime() % 24000L;
            double sunAngle = (ticks / 24000.0) * Math.PI * 2.0 - Math.PI * 0.5;
            float sunX = (float) Math.cos(sunAngle);
            float sunY = (float) Math.sin(sunAngle);
            float sunVis = Math.max(0.0f, Math.min(1.0f, sunY * 2.0f + 0.15f));

            // Project procedural bolt top & bottom into [0..1] screen UV
            float boltTopU = -10.0f;
            float boltTopV = -10.0f;
            float boltBotU = -10.0f;
            float boltBotV = -10.0f;
            float boltDist = 280.0f;

            if (boltIntensity > 0.01f) {
                float az = stormState.getBoltAzimuth();
                float bx = (float) Math.cos(az) * boltDist;
                float bz = (float) Math.sin(az) * boltDist;
                boltTopClip.set(bx, 180.0f, bz, 1.0f).mul(viewProj);
                boltBotClip.set(bx, -45.0f, bz, 1.0f).mul(viewProj);
                if (boltTopClip.w > 0.1f && boltBotClip.w > 0.1f) {
                    boltTopU = (boltTopClip.x / boltTopClip.w) * 0.5f + 0.5f;
                    boltTopV = (boltTopClip.y / boltTopClip.w) * 0.5f + 0.5f;
                    boltBotU = (boltBotClip.x / boltBotClip.w) * 0.5f + 0.5f;
                    boltBotV = (boltBotClip.y / boltBotClip.w) * 0.5f + 0.5f;
                } else {
                    boltIntensity = 0.0f;
                }
            }

            final float finalBoltIntensity = boltIntensity;
            final float finalTopU = boltTopU;
            final float finalTopV = boltTopV;
            final float finalBotU = boltBotU;
            final float finalBotV = boltBotV;
            boolean snow = module.isAtmoSnowMode();
            float windStrength = module.getAtmoWindStrength();

            long nowNanos = System.nanoTime();
            float dt = lastFrameNanos == 0L ? 0.016f : Math.min(0.05f, Math.max(0.0005f, (nowNanos - lastFrameNanos) / 1.0e9f));
            lastFrameNanos = nowNanos;
            int skyLight = mc.level.getLightEngine()
                    .getLayerListener(LightLayer.SKY)
                    .getLightValue(mc.gameRenderer.mainCamera().blockPosition());
            float targetOutside = Math.max(0.0f, Math.min(1.0f, skyLight / 15.0f));
            float smoothRate = 1.0f - (float) Math.exp(-5.0f * dt);
            smoothedOutside += (targetOutside - smoothedOutside) * smoothRate;

            float sunU = -10.0f;
            float sunV = -10.0f;
            float sunScreenVis = 0.0f;
            sunClip.set(sunX * 500.0f, sunY * 500.0f, 125.0f, 1.0f).mul(viewProj);
            if (sunClip.w > 0.05f && sunVis > 0.01f) {
                sunU = (sunClip.x / sunClip.w) * 0.5f + 0.5f;
                sunV = (sunClip.y / sunClip.w) * 0.5f + 0.5f;
                float distEdge = Math.max(Math.abs(sunU - 0.5f), Math.abs(sunV - 0.5f));
                if (distEdge < 1.35f) {
                    sunScreenVis = sunVis * Math.max(0.0f, 1.0f - Math.max(0.0f, distEdge - 0.5f) / 0.85f);
                }
            }
            float godRays = rawGodRays * sunScreenVis;
            float fogSoftness = module.getAtmoFogSoftness();
            int postTint = module.getAtmoEffectivePostTint();
            float tintR = WorldRenderBridge.red(postTint);
            float tintG = WorldRenderBridge.green(postTint);
            float tintB = WorldRenderBridge.blue(postTint);
            float warmGlow = Math.max(0.0f, sunScreenVis * (1.0f - Math.min(1.0f, sunY * 1.4f)));
            float ddaSpeed = module.getAtmoDdaSpeed();
            final float finalSunU = sunU;
            final float finalSunV = sunV;
            final float finalOutside = smoothedOutside;
            final float quality = (float) module.getShaderQualityIndex();

            uniformBuffer = WorldRenderBridge.ensureUniformBuffer(uniformBuffer, "OpticumeAtmoUbo", UBO_SIZE);
            WorldRenderBridge.writeUniforms(uniformBuffer, UBO_SIZE, builder -> {
                builder.putMat4f(invViewProj);
                builder.putVec4(fogR, fogG, fogB, fogDensity);
                builder.putVec4(invH, scatterMult, reverseZ, aspect);
                builder.putVec4(module.getAtmoTurbulence(), module.getAtmoSunScatter(), t, quality);
                builder.putVec4(sunX, sunY, 0.25f, sunVis);
                builder.putVec4((float) camera.pos.x, (float) camera.pos.y, (float) camera.pos.z, 12.0f);
                builder.putVec4(stormState.getWindDirX(), stormState.getWindDirZ(), windStrength, fusedSat);
                builder.putVec4(0.76f, 0.84f, 1.0f, stormState.getFlashIntensity());
                builder.putVec4(finalTopU, finalTopV, finalBotU, finalBotV);
                builder.putVec4(0.68f, 0.82f, 1.0f, 0.032f);
                builder.putVec4(finalBoltIntensity, 0.0038f, stormState.getBoltSeed(), stormState.getBoltBranches());
                if (snow) {
                    builder.putVec4(0.92f, 0.96f, 1.0f, 0.65f);
                } else {
                    builder.putVec4(0.66f, 0.78f, 0.94f, 0.45f);
                }
                builder.putVec4(
                        rainDensity,
                        t * (snow ? 1.8f : 12.0f),
                        snow ? 1.0f : 0.0f,
                        0.38f
                );
                builder.putVec4(rippleStrength, t * 1.4f, 0.45f, 0.0f);
                builder.putVec4(
                        Math.min(1.0f, fogR * 1.15f + 0.05f),
                        Math.min(1.0f, fogG * 1.15f + 0.06f),
                        Math.min(1.0f, fogB * 1.2f + 0.08f),
                        hazeDensity
                );
                builder.putVec4(hazeInvH, deckOffset, 0.55f, 0.45f);
                builder.putVec4(finalSunU, finalSunV, godRays, finalOutside);
                builder.putVec4(fogSoftness, postGrade, vignette, frost);
                builder.putVec4(tintR, tintG, tintB, warmGlow);
                builder.putVec4((float) ddaMode, ddaAmount, ddaSpeed, 2.4f);
            });

            GpuSampler linear = WorldRenderBridge.linearSampler();
            GpuSampler nearest = WorldRenderBridge.nearestSampler();

            try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                    () -> "OpticumeAtmosphere",
                    main.getColorTextureView(),
                    Optional.empty()
            )) {
                pass.setPipeline(PipelineBuilder.compile(PIPELINE));
                pass.setUniform(UNIFORM_NAME, uniformBuffer);
                pass.setUniform("DepthSampler", main.getDepthTextureView(), nearest);
                pass.setUniform("SceneSampler", sceneCopy.getColorTextureView(), linear);
                pass.draw(3, 1, 0, 0);
                return true;
            }
        } catch (Throwable ignored) {
            return false;
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
