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
import dev.opticumeclient.client.modules.visuals.AmbienceModule;
import dev.opticumeclient.client.modules.visuals.targetesp.PipelineBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.Optional;

/**
 * Screen-space wet puddles, ripples, and SSR reflection renderer ("Modern" & "Classic")
 * ported from Desire's wet_solution.fsh and wet_world.fsh.
 */
public final class WetShaderRenderer {
    private static final WetShaderRenderer INSTANCE = new WetShaderRenderer();
    private static final int SOLUTION_UBO_SIZE = 320; // 4 * mat4 (256) + 4 * vec4 (64)
    private static final int WORLD_UBO_SIZE = 288;    // 3 * mat4 (192) + 6 * vec4 (96)

    private static final RenderPipeline WET_SOLUTION = PipelineBuilder.create()
            .location("opticumeclient/ambience_wet_solution")
            .vertexShader(WorldRenderBridge.FULLSCREEN_QUAD_VSH)
            .fragmentShader(Identifier.fromNamespaceAndPath("opticumeclient", "core/ambience/wet_solution"))
            .depthTest(CompareOp.ALWAYS_PASS)
            .writeDepth(false)
            .cull(false)
            .noBlend()
            .vertexFormat(PipelineBuilder.EMPTY_VERTEX_FORMAT, PrimitiveTopology.TRIANGLES)
            .uniform("WetSurfaceUniforms", UniformType.UNIFORM_BUFFER)
            .sampler("SceneSampler")
            .sampler("DepthSampler")
            .build();

    private static final RenderPipeline WET_WORLD = PipelineBuilder.create()
            .location("opticumeclient/ambience_wet_world")
            .vertexShader(WorldRenderBridge.FULLSCREEN_QUAD_VSH)
            .fragmentShader(Identifier.fromNamespaceAndPath("opticumeclient", "core/ambience/wet_world"))
            .depthTest(CompareOp.ALWAYS_PASS)
            .writeDepth(false)
            .cull(false)
            .noBlend()
            .vertexFormat(PipelineBuilder.EMPTY_VERTEX_FORMAT, PrimitiveTopology.TRIANGLES)
            .uniform("WetWorldUniforms", UniformType.UNIFORM_BUFFER)
            .sampler("SceneSampler")
            .sampler("DepthSampler")
            .build();

    private TextureTarget sceneCopy;
    private GpuBuffer solutionUbo;
    private GpuBuffer worldUbo;

    private WetShaderRenderer() {
    }

    public static WetShaderRenderer getInstance() {
        return INSTANCE;
    }

    public void render(AmbienceModule module, CameraRenderState camera) {
        if (module == null || camera == null || !module.isWetWorldActive()) {
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

        sceneCopy = WorldRenderBridge.ensureColorTarget(sceneCopy, "opticume-wet-copy", main.width, main.height);
        if (!WorldRenderBridge.copyMainColor(main, sceneCopy)) {
            return;
        }

        float time = WorldRenderBridge.shaderTimeSeconds();
        float reverseZ = WorldRenderBridge.isReverseZ() ? 1.0f : 0.0f;
        Matrix4f proj = new Matrix4f(camera.projectionMatrix);
        Matrix4f invProj = new Matrix4f(camera.projectionMatrix).invert();
        Matrix4f viewRot = new Matrix4f(camera.viewRotationMatrix);
        Matrix4f invViewRot = new Matrix4f(camera.viewRotationMatrix).invert();
        GpuSampler linear = WorldRenderBridge.linearSampler();
        GpuSampler nearest = WorldRenderBridge.nearestSampler();

        try {
            if (module.isModernWetMode()) {
                solutionUbo = WorldRenderBridge.ensureUniformBuffer(solutionUbo, "OpticumeWetSolUbo", SOLUTION_UBO_SIZE);
                Matrix4f invViewWorld = new Matrix4f()
                        .translate((float) camera.pos.x, (float) camera.pos.y, (float) camera.pos.z)
                        .mul(invViewRot);
                float rainAmount = mc.level.getRainLevel(1.0f);
                float steps = switch (module.getWetQualityIndex()) {
                    case 0 -> 6.0f;
                    case 1 -> 10.0f;
                    case 3 -> 16.0f;
                    default -> 14.0f;
                };
                float saturation = module.isSaturationActive() ? 1.0f : 1.08f;

                WorldRenderBridge.writeUniforms(solutionUbo, SOLUTION_UBO_SIZE, builder -> {
                    builder.putMat4f(viewRot);
                    builder.putMat4f(proj);
                    builder.putMat4f(invProj);
                    builder.putMat4f(invViewWorld);
                    builder.putVec4(main.width, main.height, time, module.getWetIntensity());
                    builder.putVec4(
                            module.getPuddleCoverage(),
                            module.getWetReflectionStrength(),
                            module.getWetDistance(),
                            module.getWetRippleStrength()
                    );
                    builder.putVec4(
                            Math.max(0.25f, rainAmount),
                            steps,
                            module.isWetRipplesEnabled() ? 1.0f : 0.0f,
                            saturation
                    );
                    builder.putVec4(reverseZ, 0.0f, 0.0f, 0.0f);
                });

                try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                        () -> "OpticumeWetSolution",
                        main.getColorTextureView(),
                        Optional.empty()
                )) {
                    pass.setPipeline(PipelineBuilder.compile(WET_SOLUTION));
                    pass.setUniform("WetSurfaceUniforms", solutionUbo);
                    pass.setUniform("SceneSampler", sceneCopy.getColorTextureView(), linear);
                    pass.setUniform("DepthSampler", main.getDepthTextureView(), nearest);
                    pass.draw(3, 1, 0, 0);
                }
            } else {
                worldUbo = WorldRenderBridge.ensureUniformBuffer(worldUbo, "OpticumeWetWorldUbo", WORLD_UBO_SIZE);
                Vector4f fogColor = module.getLastFogColor();
                WorldRenderBridge.writeUniforms(worldUbo, WORLD_UBO_SIZE, builder -> {
                    builder.putMat4f(invProj);
                    builder.putMat4f(proj);
                    builder.putMat4f(invViewRot);
                    builder.putVec4((float) camera.pos.x, (float) camera.pos.y, (float) camera.pos.z, 1.0f);
                    builder.putVec4(main.width, main.height, reverseZ, 0.0f);
                    builder.putVec4(module.getWetReflectionStrength(), module.getWetIntensity() * 0.45f, time, 0.0f);
                    builder.putVec4(
                            module.getWetQualityIndex(),
                            module.isWetRipplesEnabled() ? 1.0f : 0.0f,
                            0.65f + module.getWetRippleStrength() * 0.7f,
                            0.0f
                    );
                    builder.putVec4(
                            module.getLastFogStart(),
                            Math.max(module.getLastFogStart() + 1.0f, module.getLastFogEnd()),
                            module.isCustomFogActive() ? 1.0f : 0.0f,
                            0.0f
                    );
                    builder.putVec4(fogColor.x, fogColor.y, fogColor.z, fogColor.w);
                });

                try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                        () -> "OpticumeWetWorld",
                        main.getColorTextureView(),
                        Optional.empty()
                )) {
                    pass.setPipeline(PipelineBuilder.compile(WET_WORLD));
                    pass.setUniform("WetWorldUniforms", worldUbo);
                    pass.setUniform("SceneSampler", sceneCopy.getColorTextureView(), linear);
                    pass.setUniform("DepthSampler", main.getDepthTextureView(), nearest);
                    pass.draw(3, 1, 0, 0);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    public void releaseGpuResources() {
        if (sceneCopy != null) {
            sceneCopy.destroyBuffers();
            sceneCopy = null;
        }
        if (solutionUbo != null && !solutionUbo.isClosed()) {
            solutionUbo.close();
            solutionUbo = null;
        }
        if (worldUbo != null && !worldUbo.isClosed()) {
            worldUbo.close();
            worldUbo = null;
        }
    }
}
