package dev.opticumeclient.client.modules.visuals.targetesp;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import org.joml.Matrix4f;

public final class GlowQuadPipeline {
    private static final String VERTEX_SHADER = "dead_chams";
    private static final String FRAGMENT_SHADER = "private_glow";
    private static final String SAMPLER_NAME = "Sampler0";

    private GlowQuadPipeline() {
    }

    public static void emitQuad(BufferBuilder buffer, Matrix4f pose, float size, int color) {
        float half = size / 2.0F;
        buffer.addVertex(pose, -half, half, 0.0F).setUv(0.0F, 1.0F).setColor(color);
        buffer.addVertex(pose, half, half, 0.0F).setUv(1.0F, 1.0F).setColor(color);
        buffer.addVertex(pose, half, -half, 0.0F).setUv(1.0F, 0.0F).setColor(color);
        buffer.addVertex(pose, -half, -half, 0.0F).setUv(0.0F, 0.0F).setColor(color);
    }

    public static RenderPipeline createPipeline(String name, CompareOp depthCompare) {
        return PipelineBuilder.create(PipelineBuilder.PROJECTION_SNIPPET)
                .location(name)
                .vertexShader(EspResources.shader(VERTEX_SHADER))
                .fragmentShader(EspResources.shader(FRAGMENT_SHADER))
                .depthTest(depthCompare)
                .writeDepth(false)
                .cull(false)
                .blend(BlendFunction.OVERLAY)
                .vertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, PrimitiveTopology.QUADS)
                .sampler(SAMPLER_NAME)
                .build();
    }
}
