package dev.opticumeclient.client.render;

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
import dev.opticumeclient.client.modules.visuals.targetesp.BufferPool;
import dev.opticumeclient.client.modules.visuals.targetesp.EspResources;
import dev.opticumeclient.client.modules.visuals.targetesp.PipelineBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Shared RenderPearl SPIR-V 3D primitive batch renderer for Stage 4/5 world visuals
 * (BlockOverlay Plasma/Fill, Predictions GlowLine, Particles 3D billboards).
 */
public final class WorldPrimitiveRenderer {
    private static final String SHADER_SOLID = "nightix_cube";
    private static final String SHADER_TEXTURED = "nightix_cube_glow";
    private static final String SAMPLER_NAME = "Sampler0";

    private static final RenderPipeline[] QUADS_TRANSLUCENT = {
            createPipeline("opticume_prim_quad_tr_nodepth", false, false, false, false),
            createPipeline("opticume_prim_quad_tr_depth", false, false, false, true)
    };
    private static final RenderPipeline[] QUADS_ADDITIVE = {
            createPipeline("opticume_prim_quad_add_nodepth", false, false, true, false),
            createPipeline("opticume_prim_quad_add_depth", false, false, true, true)
    };
    private static final RenderPipeline[] LINES_PIPELINE = {
            createPipeline("opticume_prim_line_nodepth", true, false, false, false),
            createPipeline("opticume_prim_line_depth", true, false, false, true)
    };
    private static final RenderPipeline[] TEX_TRANSLUCENT = {
            createPipeline("opticume_prim_tex_tr_nodepth", false, true, false, false),
            createPipeline("opticume_prim_tex_tr_depth", false, true, false, true)
    };
    private static final RenderPipeline[] TEX_ADDITIVE = {
            createPipeline("opticume_prim_tex_add_nodepth", false, true, true, false),
            createPipeline("opticume_prim_tex_add_depth", false, true, true, true)
    };

    private WorldPrimitiveRenderer() {
    }

    private static RenderPipeline createPipeline(String name, boolean lines, boolean textured, boolean additive, boolean depthTest) {
        PipelineBuilder builder = PipelineBuilder.create(PipelineBuilder.PROJECTION_SNIPPET)
                .location("opticumeclient/" + name)
                .vertexShader(EspResources.shader(textured ? SHADER_TEXTURED : SHADER_SOLID))
                .fragmentShader(EspResources.shader(textured ? SHADER_TEXTURED : SHADER_SOLID))
                .vertexFormat(
                        textured ? DefaultVertexFormat.POSITION_TEX_COLOR : DefaultVertexFormat.POSITION_COLOR,
                        lines ? PrimitiveTopology.DEBUG_LINES : PrimitiveTopology.QUADS
                )
                .depthTest(depthTest ? CompareOp.GREATER_THAN_OR_EQUAL : CompareOp.ALWAYS_PASS)
                .writeDepth(false)
                .cull(false)
                .blend(additive ? BlendFunction.LIGHTNING : BlendFunction.TRANSLUCENT);
        if (textured) {
            builder.sampler(SAMPLER_NAME);
        }
        return builder.build();
    }

    public static BufferBuilder beginQuads() {
        return BufferPool.getInstance().begin(PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_COLOR);
    }

    public static BufferBuilder beginLines() {
        return BufferPool.getInstance().begin(PrimitiveTopology.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
    }

    public static BufferBuilder beginTexturedQuads() {
        return BufferPool.getInstance().begin(PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
    }

    public static void drawQuads(BufferBuilder buffer, boolean additive, boolean depthTest) {
        RenderPipeline pipe = (additive ? QUADS_ADDITIVE : QUADS_TRANSLUCENT)[depthTest ? 1 : 0];
        submitMesh(buffer, pipe, null);
    }

    public static void drawLines(BufferBuilder buffer, boolean depthTest) {
        submitMesh(buffer, LINES_PIPELINE[depthTest ? 1 : 0], null);
    }

    public static void drawTexturedQuads(BufferBuilder buffer, Identifier texture, boolean additive, boolean depthTest) {
        RenderPipeline pipe = (additive ? TEX_ADDITIVE : TEX_TRANSLUCENT)[depthTest ? 1 : 0];
        submitMesh(buffer, pipe, texture);
    }

    public static void discardQuietly(BufferBuilder buffer) {
        if (buffer == null) return;
        try {
            MeshData mesh = buffer.build();
            if (mesh != null) {
                mesh.close();
            }
        } catch (Throwable ignored) {
        }
    }

    private static void submitMesh(BufferBuilder buffer, RenderPipeline pipeline, Identifier texture) {
        if (buffer == null) return;
        MeshData mesh;
        try {
            mesh = buffer.build();
        } catch (Throwable ignored) {
            return;
        }
        if (mesh == null) return;

        Minecraft mc = Minecraft.getInstance();
        RenderTarget mainTarget = mc.gameRenderer != null ? mc.gameRenderer.mainRenderTarget() : null;
        if (mainTarget == null || mainTarget.getColorTextureView() == null) {
            mesh.close();
            return;
        }

        AbstractTexture tex = texture != null ? mc.getTextureManager().getTexture(texture) : null;
        try (mesh; GpuBuffer vertexBuffer = RenderSystem.getDevice().createBuffer(() -> "Opticume prim vertices", GpuBuffer.USAGE_VERTEX, mesh.vertexBuffer())) {
            DrawState state = mesh.drawState();
            AutoStorageIndexBuffer indexSeq = RenderSystem.getSequentialBuffer(state.primitiveTopology());
            GpuBuffer indexBuffer = indexSeq.getBuffer(state.indexCount());
            try (RenderPass pass = RenderSystem.getDevice()
                    .createCommandEncoder()
                    .createRenderPass(
                            () -> "Opticume primitives",
                            mainTarget.getColorTextureView(),
                            Optional.empty(),
                            mainTarget.getDepthTextureView(),
                            OptionalDouble.empty())) {
                RenderSystem.bindDefaultUniforms(pass);
                pass.setPipeline(PipelineBuilder.compile(pipeline));
                if (tex != null) {
                    pass.setUniform(SAMPLER_NAME, tex.getTextureView(), tex.getSampler());
                }
                pass.setVertexBuffer(0, vertexBuffer.slice());
                pass.setIndexBuffer(indexBuffer, indexSeq.type());
                pass.drawIndexed(state.indexCount(), 1, 0, 0, 0);
            }
        } catch (Throwable ignored) {
        }
    }

    public static void glowSprite(BufferBuilder buffer, Matrix4f pose, double x, double y, double z,
                                  Quaternionf orientation, float size, int argb) {
        float half = size * 0.5f;
        Vector3f right = orientation.transform(new Vector3f(half, 0.0f, 0.0f));
        Vector3f up = orientation.transform(new Vector3f(0.0f, half, 0.0f));
        float px = (float) x;
        float py = (float) y;
        float pz = (float) z;
        buffer.addVertex(pose, px - right.x - up.x, py - right.y - up.y, pz - right.z - up.z).setUv(0.0f, 1.0f).setColor(argb);
        buffer.addVertex(pose, px + right.x - up.x, py + right.y - up.y, pz + right.z - up.z).setUv(1.0f, 1.0f).setColor(argb);
        buffer.addVertex(pose, px + right.x + up.x, py + right.y + up.y, pz + right.z + up.z).setUv(1.0f, 0.0f).setColor(argb);
        buffer.addVertex(pose, px - right.x + up.x, py - right.y + up.y, pz - right.z + up.z).setUv(0.0f, 0.0f).setColor(argb);
    }

    public static int glowSegment(BufferBuilder buffer, Matrix4f pose, Vec3 from, Vec3 to,
                                  Quaternionf orientation, float size, int argb, double spacing) {
        double length = from.distanceTo(to);
        int steps = Math.max(1, (int) Math.ceil(length / spacing));
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            glowSprite(buffer, pose,
                    from.x + (to.x - from.x) * t,
                    from.y + (to.y - from.y) * t,
                    from.z + (to.z - from.z) * t,
                    orientation, size, argb);
        }
        return steps + 1;
    }
}
