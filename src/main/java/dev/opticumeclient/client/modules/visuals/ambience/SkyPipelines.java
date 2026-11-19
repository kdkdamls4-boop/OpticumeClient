package dev.opticumeclient.client.modules.visuals.ambience;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.UniformType;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import dev.opticumeclient.client.modules.visuals.AmbienceModule;
import dev.opticumeclient.client.modules.visuals.targetesp.PipelineBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Custom procedural sky shader pipelines ported from Desire for MC 26.3 RenderPearl.
 * Excludes the broken legacy sky_aurora preset per user request.
 */
public final class SkyPipelines {
    private static final SkyPipelines INSTANCE = new SkyPipelines();
    private static final int UBO_SIZE = 192; // mat4 (64) + 8 * vec4 (128)
    private static final String UNIFORM_NAME = "AmbienceSkyUniforms";

    private final Map<String, RenderPipeline> pipelines = new HashMap<>();
    private GpuBuffer uniformBuffer;
    private boolean initialized = false;

    private SkyPipelines() {
    }

    public static SkyPipelines getInstance() {
        return INSTANCE;
    }

    private void ensureInitialized() {
        if (initialized) {
            return;
        }
        initialized = true;
        pipelines.put("aurora2", buildPipeline("aurora2", "sky_aurora2"));
        pipelines.put("Northern Lights", buildPipeline("northern", "sky_northern"));
        pipelines.put("Sakura", buildPipeline("sakura", "sky_sakura"));
        pipelines.put("Plasma", buildPipeline("plasma", "sky_plasma"));
        pipelines.put("Plasma 2", buildPipeline("plasma2", "sky_plasma2"));
        pipelines.put("Night", buildPipeline("night", "sky_night"));
        pipelines.put("Summer Sky", buildPipeline("summer", "sky_summer"));
        pipelines.put("Caustic", buildPipeline("caustic", "sky_caustic"));
        pipelines.put("Thunder", buildPipeline("thunder", "sky_thunder"));
    }

    private static RenderPipeline buildPipeline(String name, String fragmentName) {
        return PipelineBuilder.create()
                .location("opticumeclient/ambience_sky_" + name)
                .vertexShader(WorldRenderBridge.FULLSCREEN_QUAD_VSH)
                .fragmentShader(Identifier.fromNamespaceAndPath("opticumeclient", "core/ambience/" + fragmentName))
                .depthTest(CompareOp.ALWAYS_PASS)
                .writeDepth(false)
                .cull(false)
                .blend(BlendFunction.TRANSLUCENT)
                .vertexFormat(PipelineBuilder.EMPTY_VERTEX_FORMAT, PrimitiveTopology.TRIANGLES)
                .uniform(UNIFORM_NAME, UniformType.UNIFORM_BUFFER)
                .build();
    }

    public void render(AmbienceModule module, RenderTarget target, SkyRenderState skyState, CameraRenderState camera) {
        if (module == null || target == null || camera == null) {
            return;
        }
        GpuTextureView colorView = target.getColorTextureView();
        if (colorView == null || colorView.isClosed()) {
            return;
        }
        ensureInitialized();
        RenderPipeline pipeline = pipelines.get(module.getSkyModeName());
        if (pipeline == null) {
            return;
        }

        uniformBuffer = WorldRenderBridge.ensureUniformBuffer(uniformBuffer, "OpticumeSkyUbo", UBO_SIZE);

        Matrix4f invViewProj = WorldRenderBridge.computeInvViewProj(camera);
        float time = WorldRenderBridge.shaderTimeSeconds();
        float yawRad = (float) Math.toRadians(camera.yRot);
        float pitchRad = (float) Math.toRadians(camera.xRot);
        float fov = WorldRenderBridge.extractFovDegrees(camera);

        int c1 = module.resolvePrimaryColor();
        int c2 = module.resolveSecondaryColor();
        float alpha = module.getSkyOpacity();
        float rain = skyState != null ? skyState.rainBrightness : 0.0f;
        Minecraft mc = Minecraft.getInstance();
        float thunder = (mc.level != null) ? mc.level.getThunderLevel(1.0f) : 0.0f;

        WorldRenderBridge.writeUniforms(uniformBuffer, UBO_SIZE, builder -> {
            builder.putMat4f(invViewProj);
            builder.putVec4(target.width, target.height, 0.0f, 0.0f);
            builder.putVec4(time, yawRad, pitchRad, module.getSkyScale());
            builder.putVec4(
                    WorldRenderBridge.red(c1),
                    WorldRenderBridge.green(c1),
                    WorldRenderBridge.blue(c1),
                    alpha
            );
            builder.putVec4(
                    WorldRenderBridge.red(c2),
                    WorldRenderBridge.green(c2),
                    WorldRenderBridge.blue(c2),
                    alpha
            );
            builder.putVec4(fov, module.getSkyIntensity(), module.getSkySpeed(), module.getNightBlend());
            builder.putVec4(rain, thunder, module.getThemeMix(), 0.0f);
            builder.putVec4(
                    module.getThunderInterval(),
                    module.getThunderChance(),
                    module.getThunderGlow(),
                    0.0f
            );
            builder.putVec4(0.0f, 0.0f, 0.0f, 0.0f);
        });

        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "OpticumeAmbienceSky",
                colorView,
                Optional.empty()
        )) {
            pass.setPipeline(PipelineBuilder.compile(pipeline));
            pass.setUniform(UNIFORM_NAME, uniformBuffer);
            pass.draw(3, 1, 0, 0);
        } catch (Throwable ignored) {
        }
    }

    public void releaseGpuResources() {
        if (uniformBuffer != null && !uniformBuffer.isClosed()) {
            uniformBuffer.close();
        }
        uniformBuffer = null;
    }
}
