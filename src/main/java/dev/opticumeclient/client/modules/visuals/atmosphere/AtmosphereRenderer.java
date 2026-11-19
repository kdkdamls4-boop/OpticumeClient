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
import dev.opticumeclient.client.modules.visuals.AtmosphereModule;
import dev.opticumeclient.client.modules.visuals.ambience.WorldRenderBridge;
import dev.opticumeclient.client.modules.visuals.targetesp.PipelineBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.Optional;

/**
 * Screen-space volumetric height fog, low haze, precipitation layers,
 * surface ripples, and projected lightning bolt post-processor.
 */
public final class AtmosphereRenderer {
    private static final AtmosphereRenderer INSTANCE = new AtmosphereRenderer();
    private static final int UBO_SIZE = 304; // mat4 (64) + 15 * vec4 (240)
    private static final String UNIFORM_NAME = "AtmosphereUniforms";

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

    public void render(AtmosphereModule module, CameraRenderState camera) {
        if (module == null || camera == null || !module.isEnabled()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.gameRenderer == null || mc.level == null) {
            return;
        }
        RenderTarget main = mc.gameRenderer.mainRenderTarget();
        if (main == null || main.width <= 1 || main.height <= 1
                || main.getColorTextureView() == null
                || main.getDepthTextureView() == null) {
            return;
        }

        stormState.update(module.isLightningEnabled());

        sceneCopy = WorldRenderBridge.ensureColorTarget(sceneCopy, "opticume-atmo-copy", main.width, main.height);
        if (!WorldRenderBridge.copyMainColor(main, sceneCopy)) {
            return;
        }

        Matrix4f viewProj = new Matrix4f(camera.projectionMatrix).mul(camera.viewRotationMatrix);
        Matrix4f invViewProj = new Matrix4f(viewProj).invert();
        float aspect = (float) main.width / Math.max(1.0f, (float) main.height);
        float reverseZ = WorldRenderBridge.isReverseZ() ? 1.0f : 0.0f;
        float t = stormState.getElapsedSeconds();

        int fogRgb = module.getEffectiveFogColor();
        float fogR = WorldRenderBridge.red(fogRgb);
        float fogG = WorldRenderBridge.green(fogRgb);
        float fogB = WorldRenderBridge.blue(fogRgb);
        float fogDensity = module.isFogEnabled() ? module.getEffectiveFogDensity() : 0.0f;
        float invH = 1.0f / Math.max(4.0f, module.getEffectiveFogHeight());
        float scatterMult = 1.05f;

        float hazeDensity = module.isHazeEnabled() ? module.getEffectiveHazeDensity() : 0.0f;
        float hazeInvH = 1.0f / 14.0f;
        float deckOffset = (float) camera.pos.y - 68.0f;

        // Compute sun angle from level clock
        long ticks = mc.level.getOverworldClockTime() % 24000L;
        double sunAngle = (ticks / 24000.0) * Math.PI * 2.0 - Math.PI * 0.5;
        float sunX = (float) Math.cos(sunAngle);
        float sunY = (float) Math.sin(sunAngle);
        float sunVis = Math.max(0.0f, Math.min(1.0f, sunY * 2.0f + 0.15f));

        // Project procedural bolt top & bottom into [0..1] screen UV
        float boltIntensity = stormState.getBoltIntensity();
        float boltTopU = -10.0f;
        float boltTopV = -10.0f;
        float boltBotU = -10.0f;
        float boltBotV = -10.0f;
        float boltDist = 280.0f;

        if (boltIntensity > 0.01f) {
            float az = stormState.getBoltAzimuth();
            float bx = (float) Math.cos(az) * boltDist;
            float bz = (float) Math.sin(az) * boltDist;
            Vector4f topClip = new Vector4f(bx, 180.0f, bz, 1.0f).mul(viewProj);
            Vector4f botClip = new Vector4f(bx, -45.0f, bz, 1.0f).mul(viewProj);
            if (topClip.w > 0.1f && botClip.w > 0.1f) {
                boltTopU = (topClip.x / topClip.w) * 0.5f + 0.5f;
                boltTopV = (topClip.y / topClip.w) * 0.5f + 0.5f;
                boltBotU = (botClip.x / botClip.w) * 0.5f + 0.5f;
                boltBotV = (botClip.y / botClip.w) * 0.5f + 0.5f;
            } else {
                boltIntensity = 0.0f;
            }
        }

        final float finalBoltIntensity = boltIntensity;
        final float finalTopU = boltTopU;
        final float finalTopV = boltTopV;
        final float finalBotU = boltBotU;
        final float finalBotV = boltBotV;
        boolean snow = module.isSnowMode();
        float rainDensity = module.isPrecipitationEnabled() ? module.getEffectivePrecipDensity() : 0.0f;
        float rippleStrength = module.isRipplesEnabled() ? module.getRippleStrength() : 0.0f;
        float windStrength = module.getWindStrength();

        uniformBuffer = WorldRenderBridge.ensureUniformBuffer(uniformBuffer, "OpticumeAtmoUbo", UBO_SIZE);
        WorldRenderBridge.writeUniforms(uniformBuffer, UBO_SIZE, builder -> {
            builder.putMat4f(invViewProj);
            builder.putVec4(fogR, fogG, fogB, fogDensity);
            builder.putVec4(invH, scatterMult, reverseZ, aspect);
            builder.putVec4(module.getTurbulence(), module.getSunScatter(), t, 0.0f);
            builder.putVec4(sunX, sunY, 0.25f, sunVis);
            builder.putVec4((float) camera.pos.x, (float) camera.pos.y, (float) camera.pos.z, 12.0f);
            builder.putVec4(stormState.getWindDirX(), stormState.getWindDirZ(), windStrength, 0.0f);
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
