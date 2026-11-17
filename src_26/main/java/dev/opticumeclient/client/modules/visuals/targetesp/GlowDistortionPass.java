package dev.opticumeclient.client.modules.visuals.targetesp;

import com.mojang.blaze3d.buffers.Std140Builder;
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
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.IndexType;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.UniformType;
import com.mojang.renderpearl.api.textures.FilterMode;
import net.minecraft.client.Minecraft;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;
import java.util.Optional;

public final class GlowDistortionPass {
    public static final GlowDistortionPass INSTANCE = new GlowDistortionPass();

    private static final RenderPipeline MASK_QUADS_PIPELINE = createMaskPipeline("opticumeclient/glow_distortion_mask", PrimitiveTopology.QUADS);
    private static final RenderPipeline MASK_TRIANGLES_PIPELINE = createMaskPipeline("opticumeclient/glow_distortion_mask_triangles", PrimitiveTopology.TRIANGLES);
    private static final RenderPipeline COMPOSITE_PIPELINE = PipelineBuilder.create()
            .location("opticumeclient/glow_distortion")
            .vertexShader(EspResources.shader("target_fullscreen"))
            .fragmentShader(EspResources.shader("target_esp_rhomb_distortion"))
            .depthTest(CompareOp.ALWAYS_PASS)
            .writeDepth(false)
            .cull(false)
            .noBlend()
            .vertexFormat(PipelineBuilder.EMPTY_VERTEX_FORMAT, PrimitiveTopology.TRIANGLES)
            .uniform("GlowDistortion", UniformType.UNIFORM_BUFFER)
            .sampler("SceneSampler")
            .sampler("GlowSampler")
            .build();

    private TextureTarget maskTarget;
    private TextureTarget sceneTarget;

    private GlowDistortionPass() {
    }

    private static RenderPipeline createMaskPipeline(String location, PrimitiveTopology topology) {
        return PipelineBuilder.create(PipelineBuilder.PROJECTION_SNIPPET)
                .location(location)
                .vertexShader(EspResources.shader("dead_chams"))
                .fragmentShader(EspResources.shader("target_esp_rhomb_distortion_mask"))
                .depthTest(CompareOp.ALWAYS_PASS)
                .writeDepth(false)
                .cull(false)
                .blend(BlendFunction.ADDITIVE)
                .vertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, topology)
                .build();
    }

    public void renderFromBuilder(BufferBuilder builder, float strength) {
        MeshData mesh = builder.build();
        if (mesh != null) {
            try (mesh) {
                if (!(strength <= 0.0F)) {
                    DrawState state = mesh.drawState();
                    int indexCount = state.indexCount();
                    AutoStorageIndexBuffer seq = RenderSystem.getSequentialBuffer(state.primitiveTopology());
                    GpuBuffer indexBuffer = seq.getBuffer(indexCount);
                    IndexType indexType = seq.type();
                    GpuBufferSlice vertexSlice = GpuStaging.uploadVertices(mesh.vertexBuffer(), state.format().getVertexSize());
                    this.renderSlice(vertexSlice, indexBuffer, indexType, indexCount, strength, state.primitiveTopology());
                }
            }
        }
    }

    public void renderFromTarget(RenderTarget glowTarget, float strength) {
        if (!(strength <= 0.0F)) {
            Minecraft mc = Minecraft.getInstance();
            RenderTarget mainTarget = mc.gameRenderer.mainRenderTarget();
            if (mainTarget != null && mainTarget.getColorTextureView() != null && glowTarget != null && glowTarget.getColorTextureView() != null) {
                this.ensureTargets(mainTarget.width, mainTarget.height, false);
                this.composite(glowTarget, mainTarget, strength, RenderSystem.getDevice().createCommandEncoder());
            }
        }
    }

    private void renderSlice(GpuBufferSlice vertexSlice, GpuBuffer indexBuffer, IndexType indexType, int indexCount, float strength, PrimitiveTopology topology) {
        Minecraft mc = Minecraft.getInstance();
        RenderTarget mainTarget = mc.gameRenderer.mainRenderTarget();
        if (mainTarget != null && mainTarget.getColorTextureView() != null && indexCount > 0 && !(strength <= 0.0F)) {
            this.ensureTargets(mainTarget.width, mainTarget.height, true);
            CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
            try (RenderPass pass = encoder.createRenderPass(() -> "glowDistortionMask", this.maskTarget.getColorTextureView(), Optional.of(new Vector4f(0.0F)))) {
                RenderSystem.bindDefaultUniforms(pass);
                pass.setPipeline(PipelineBuilder.compile(topology == PrimitiveTopology.TRIANGLES ? MASK_TRIANGLES_PIPELINE : MASK_QUADS_PIPELINE));
                pass.setVertexBuffer(0, vertexSlice);
                pass.setIndexBuffer(indexBuffer, indexType);
                pass.drawIndexed(indexCount, 1, 0, 0, 0);
            }
            this.composite(this.maskTarget, mainTarget, strength, encoder);
        }
    }

    public void renderIndexed(GpuBuffer vertexBuffer, GpuBuffer indexBuffer, IndexType indexType, int indexCount, float strength, PrimitiveTopology topology) {
        this.renderSlice(vertexBuffer.slice(), indexBuffer, indexType, indexCount, strength, topology);
    }

    public void renderQuads(GpuBuffer vertexBuffer, GpuBuffer indexBuffer, IndexType indexType, int indexCount, float strength) {
        this.renderIndexed(vertexBuffer, indexBuffer, indexType, indexCount, strength, PrimitiveTopology.QUADS);
    }

    private void composite(RenderTarget glowTarget, RenderTarget mainTarget, float strength, CommandEncoder encoder) {
        encoder.copyTextureToTexture(mainTarget.getColorTexture(), this.sceneTarget.getColorTexture(), 0, 0, 0, 0, 0, mainTarget.width, mainTarget.height);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer uboData = Std140Builder.onStack(stack, 16).putFloat(strength).align(16).get();
            GpuBufferSlice uboSlice = GpuStaging.uploadUniform(uboData);
            try (RenderPass pass = encoder.createRenderPass(() -> "glowDistortionComposite", mainTarget.getColorTextureView(), Optional.empty())) {
                pass.setPipeline(PipelineBuilder.compile(COMPOSITE_PIPELINE));
                pass.setUniform("SceneSampler", this.sceneTarget.getColorTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
                pass.setUniform("GlowSampler", glowTarget.getColorTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
                pass.setUniform("GlowDistortion", uboSlice);
                pass.draw(3, 1, 0, 0);
            }
        }
    }

    private void ensureTargets(int width, int height, boolean needMaskTarget) {
        if (this.sceneTarget == null) {
            this.sceneTarget = new TextureTarget("glow-distortion-scene", width, height, GpuFormat.RGBA8_UNORM, null);
        } else if (this.sceneTarget.width != width || this.sceneTarget.height != height) {
            this.sceneTarget.resize(width, height);
        }

        if (needMaskTarget) {
            if (this.maskTarget == null) {
                this.maskTarget = new TextureTarget("glow-distortion-mask", width, height, GpuFormat.RGBA8_UNORM, null);
            } else if (this.maskTarget.width != width || this.maskTarget.height != height) {
                this.maskTarget.resize(width, height);
            }
        }
    }

    public void close() {
        if (this.sceneTarget != null) {
            this.sceneTarget.destroyBuffers();
            this.sceneTarget = null;
        }
        if (this.maskTarget != null) {
            this.maskTarget.destroyBuffers();
            this.maskTarget = null;
        }
    }
}
