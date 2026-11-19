package dev.opticumeclient.client.modules.visuals.targetesp;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.RenderSystem.AutoStorageIndexBuffer;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.MeshData.DrawState;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;

import java.util.Optional;
import java.util.OptionalDouble;

public final class CubeParticleRenderer {
    public static final Identifier PARTICLES_1 = Identifier.fromNamespaceAndPath("opticumeclient", "targetesp/textures/nightix/particles_1.png");
    public static final Identifier PARTICLES_2 = Identifier.fromNamespaceAndPath("opticumeclient", "targetesp/textures/nightix/particles_2.png");

    private static final RenderPipeline[] SOLID_PIPELINES = new RenderPipeline[]{createPipeline(false, false, false), createPipeline(false, false, true)};
    private static final RenderPipeline[] LINE_PIPELINES = new RenderPipeline[]{createPipeline(true, false, false), createPipeline(true, false, true)};
    private static final RenderPipeline[] GLOW_PIPELINES = new RenderPipeline[]{createPipeline(false, true, false), createPipeline(false, true, true)};

    private static final String SHADER_GLOW = "nightix_cube_glow";
    private static final String SHADER_SOLID = "nightix_cube";
    private static final String SAMPLER_NAME = "Sampler0";
    private static final String PASS_LABEL = "Nightix cubes";
    private static final String BUFFER_LABEL = "Nightix cube vertices";

    private CubeParticleRenderer() {
    }

    public static void emitWireCube(BufferBuilder buffer, Matrix4f pose, float halfSize, int color) {
        for (int ySign = -1; ySign <= 1; ySign += 2) {
            emitLine(buffer, pose, -halfSize, ySign * halfSize, -halfSize, halfSize, ySign * halfSize, -halfSize, color);
            emitLine(buffer, pose, halfSize, ySign * halfSize, -halfSize, halfSize, ySign * halfSize, halfSize, color);
            emitLine(buffer, pose, halfSize, ySign * halfSize, halfSize, -halfSize, ySign * halfSize, halfSize, color);
            emitLine(buffer, pose, -halfSize, ySign * halfSize, halfSize, -halfSize, ySign * halfSize, -halfSize, color);
        }

        for (int xSign = -1; xSign <= 1; xSign += 2) {
            for (int zSign = -1; zSign <= 1; zSign += 2) {
                emitLine(buffer, pose, xSign * halfSize, -halfSize, zSign * halfSize, xSign * halfSize, halfSize, zSign * halfSize, color);
            }
        }
    }

    private static void emitLine(BufferBuilder buffer, Matrix4f pose, float x1, float y1, float z1, float x2, float y2, float z2, int color) {
        buffer.addVertex(pose, x1, y1, z1).setColor(color);
        buffer.addVertex(pose, x2, y2, z2).setColor(color);
    }

    public static BufferBuilder begin(boolean lines, boolean textured) {
        return BufferPool.getInstance().begin(
                lines ? PrimitiveTopology.DEBUG_LINES : PrimitiveTopology.QUADS,
                textured ? DefaultVertexFormat.POSITION_TEX_COLOR : DefaultVertexFormat.POSITION_COLOR
        );
    }

    private static void emitFace(
            BufferBuilder buffer,
            Matrix4f pose,
            float x1, float y1, float z1,
            float x2, float y2, float z2,
            float x3, float y3, float z3,
            float x4, float y4, float z4,
            int color
    ) {
        buffer.addVertex(pose, x1, y1, z1).setColor(color);
        buffer.addVertex(pose, x2, y2, z2).setColor(color);
        buffer.addVertex(pose, x3, y3, z3).setColor(color);
        buffer.addVertex(pose, x4, y4, z4).setColor(color);
    }

    public static void emitGlowQuad(BufferBuilder buffer, Matrix4f pose, float halfSize, int color) {
        buffer.addVertex(pose, -halfSize, -halfSize, 0.0F).setColor(color).setUv(0.0F, 1.0F);
        buffer.addVertex(pose, halfSize, -halfSize, 0.0F).setColor(color).setUv(1.0F, 1.0F);
        buffer.addVertex(pose, halfSize, halfSize, 0.0F).setColor(color).setUv(1.0F, 0.0F);
        buffer.addVertex(pose, -halfSize, halfSize, 0.0F).setColor(color).setUv(0.0F, 0.0F);
    }

    private static RenderPipeline createPipeline(boolean lines, boolean textured, boolean depthTest) {
        PipelineBuilder builder = PipelineBuilder.create(PipelineBuilder.PROJECTION_SNIPPET)
                .location("opticumeclient/nightix_cube_" + lines + "_" + textured + "_" + depthTest)
                .vertexShader(EspResources.shader(textured ? SHADER_GLOW : SHADER_SOLID))
                .fragmentShader(EspResources.shader(textured ? SHADER_GLOW : SHADER_SOLID))
                .vertexFormat(
                        textured ? DefaultVertexFormat.POSITION_TEX_COLOR : DefaultVertexFormat.POSITION_COLOR,
                        lines ? PrimitiveTopology.DEBUG_LINES : PrimitiveTopology.QUADS
                )
                .depthTest(depthTest ? CompareOp.GREATER_THAN_OR_EQUAL : CompareOp.ALWAYS_PASS)
                .writeDepth(false)
                .cull(false)
                .blend(BlendFunction.LIGHTNING);
        if (textured) {
            builder.sampler(SAMPLER_NAME);
        }
        return builder.build();
    }

    public static void emitSolidCube(BufferBuilder buffer, Matrix4f pose, float halfSize, int color) {
        emitFace(buffer, pose, -halfSize, halfSize, -halfSize, halfSize, halfSize, -halfSize, halfSize, halfSize, halfSize, -halfSize, halfSize, halfSize, color);
        emitFace(buffer, pose, -halfSize, -halfSize, halfSize, halfSize, -halfSize, halfSize, halfSize, -halfSize, -halfSize, -halfSize, -halfSize, -halfSize, color);
        emitFace(buffer, pose, halfSize, -halfSize, -halfSize, halfSize, -halfSize, halfSize, halfSize, halfSize, halfSize, halfSize, halfSize, -halfSize, color);
        emitFace(buffer, pose, -halfSize, -halfSize, halfSize, -halfSize, -halfSize, -halfSize, -halfSize, halfSize, -halfSize, -halfSize, halfSize, halfSize, color);
        emitFace(buffer, pose, -halfSize, -halfSize, halfSize, halfSize, -halfSize, halfSize, halfSize, halfSize, halfSize, -halfSize, halfSize, halfSize, color);
        emitFace(buffer, pose, halfSize, -halfSize, -halfSize, -halfSize, -halfSize, -halfSize, -halfSize, halfSize, -halfSize, halfSize, halfSize, -halfSize, color);
    }

    public static void draw(BufferBuilder buffer, boolean lines, Identifier texture, boolean depthTest) {
        MeshData mesh = buffer.build();
        if (mesh != null) {
            Minecraft mc = Minecraft.getInstance();
            try (mesh; GpuBuffer vertexBuffer = RenderSystem.getDevice().createBuffer(() -> BUFFER_LABEL, GpuBuffer.USAGE_VERTEX, mesh.vertexBuffer())) {
                DrawState state = mesh.drawState();
                AutoStorageIndexBuffer indexSeq = RenderSystem.getSequentialBuffer(state.primitiveTopology());
                GpuBuffer indexBuffer = indexSeq.getBuffer(state.indexCount());
                RenderTarget mainTarget = mc.gameRenderer.mainRenderTarget();
                AbstractTexture tex = texture != null ? mc.getTextureManager().getTexture(texture) : null;
                try (RenderPass pass = RenderSystem.getDevice()
                        .createCommandEncoder()
                        .createRenderPass(() -> PASS_LABEL, mainTarget.getColorTextureView(), Optional.empty(), mainTarget.getDepthTextureView(), OptionalDouble.empty())) {
                    RenderSystem.bindDefaultUniforms(pass);
                    RenderPipeline pipeline = (texture != null ? GLOW_PIPELINES : (lines ? LINE_PIPELINES : SOLID_PIPELINES))[depthTest ? 1 : 0];
                    pass.setPipeline(PipelineBuilder.compile(pipeline));
                    if (tex != null) {
                        pass.setUniform(SAMPLER_NAME, tex.getTextureView(), tex.getSampler());
                    }
                    pass.setVertexBuffer(0, vertexBuffer.slice());
                    pass.setIndexBuffer(indexBuffer, indexSeq.type());
                    pass.drawIndexed(state.indexCount(), 1, 0, 0, 0);
                }
            }
        }
    }
}
