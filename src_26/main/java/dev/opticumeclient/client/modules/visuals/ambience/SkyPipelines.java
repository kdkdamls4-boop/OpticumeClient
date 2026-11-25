package dev.opticumeclient.client.modules.visuals.ambience;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
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
    private final Map<String, RenderPipeline> offscreenPipelines = new HashMap<>();
    private RenderPipeline blitPipeline;
    private TextureTarget skyHalfTarget;
    private GpuBuffer uniformBuffer;
    private final Matrix4f invViewProjScratch = new Matrix4f();
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
        registerMode("aurora2", "sky_aurora2");
        registerMode("Northern Lights", "sky_northern");
        registerMode("Sakura", "sky_sakura");
        registerMode("Plasma", "sky_plasma");
        registerMode("Plasma 2", "sky_plasma2");
        registerMode("Night", "sky_night");
        registerMode("Summer Sky", "sky_summer");
        registerMode("Caustic", "sky_caustic");
        registerMode("Thunder", "sky_thunder");
        blitPipeline = PipelineBuilder.create()
                .location("opticumeclient/ambience_sky_blit")
                .vertexShader(WorldRenderBridge.FULLSCREEN_QUAD_VSH)
                .fragmentShader(Identifier.fromNamespaceAndPath("opticumeclient", "core/ambience/sky_blit"))
                .depthTest(CompareOp.ALWAYS_PASS)
                .writeDepth(false)
                .cull(false)
                .blend(BlendFunction.TRANSLUCENT)
                .vertexFormat(PipelineBuilder.EMPTY_VERTEX_FORMAT, PrimitiveTopology.TRIANGLES)
                .sampler("SkySampler")
                .build();
    }

    private void registerMode(String modeName, String fragmentName) {
        String safeKey = fragmentName;
        pipelines.put(modeName, buildPipeline(safeKey, fragmentName, true));
        offscreenPipelines.put(modeName, buildPipeline(safeKey + "_offscreen", fragmentName, false));
    }

    private static RenderPipeline buildPipeline(String name, String fragmentName, boolean translucent) {
        PipelineBuilder pb = PipelineBuilder.create()
                .location("opticumeclient/ambience_sky_" + name)
                .vertexShader(WorldRenderBridge.FULLSCREEN_QUAD_VSH)
                .fragmentShader(Identifier.fromNamespaceAndPath("opticumeclient", "core/ambience/" + fragmentName))
                .depthTest(CompareOp.ALWAYS_PASS)
                .writeDepth(false)
                .cull(false);
        if (translucent) {
            pb.blend(BlendFunction.TRANSLUCENT);
        } else {
            pb.noBlend();
        }
        return pb.vertexFormat(PipelineBuilder.EMPTY_VERTEX_FORMAT, PrimitiveTopology.TRIANGLES)
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
        String mode = module.getSkyModeName();
        float resScale = module.getSkyResolutionScale();
        boolean useHalfRes = resScale < 0.99f && target.width > 4 && target.height > 4;
        RenderPipeline pipeline = useHalfRes ? offscreenPipelines.get(mode) : pipelines.get(mode);
        if (pipeline == null) {
            return;
        }

        int renderW = useHalfRes ? Math.max(2, Math.round(target.width * resScale)) : target.width;
        int renderH = useHalfRes ? Math.max(2, Math.round(target.height * resScale)) : target.height;
        GpuTextureView passTargetView = colorView;
        if (useHalfRes) {
            skyHalfTarget = WorldRenderBridge.ensureColorTarget(skyHalfTarget, "opticume-sky-half", renderW, renderH);
            GpuTextureView halfView = skyHalfTarget.getColorTextureView();
            if (halfView == null || halfView.isClosed()) {
                return;
            }
            passTargetView = halfView;
        }

        uniformBuffer = WorldRenderBridge.ensureUniformBuffer(uniformBuffer, "OpticumeSkyUbo", UBO_SIZE);

        Matrix4f invViewProj = WorldRenderBridge.computeInvViewProj(camera, invViewProjScratch);
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
            builder.putVec4(renderW, renderH, 0.0f, 0.0f);
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
                passTargetView,
                Optional.empty()
        )) {
            pass.setPipeline(PipelineBuilder.compile(pipeline));
            pass.setUniform(UNIFORM_NAME, uniformBuffer);
            pass.draw(3, 1, 0, 0);
        } catch (Throwable ignored) {
            return;
        }

        if (useHalfRes) {
            try (RenderPass blit = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                    () -> "OpticumeAmbienceSkyBlit",
                    colorView,
                    Optional.empty()
            )) {
                blit.setPipeline(PipelineBuilder.compile(blitPipeline));
                blit.setUniform("SkySampler", passTargetView, WorldRenderBridge.linearSampler());
                blit.draw(3, 1, 0, 0);
            } catch (Throwable ignored) {
            }
        }
    }

    public void releaseGpuResources() {
        if (skyHalfTarget != null) {
            skyHalfTarget.destroyBuffers();
            skyHalfTarget = null;
        }
        if (uniformBuffer != null && !uniformBuffer.isClosed()) {
            uniformBuffer.close();
        }
        uniformBuffer = null;
    }
}
