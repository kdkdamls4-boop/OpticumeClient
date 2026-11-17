package dev.opticumeclient.client.modules.visuals.targetesp;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.PolygonMode;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.RenderPipeline.Builder;
import com.mojang.renderpearl.api.pipeline.RenderPipeline.Snippet;
import com.mojang.renderpearl.api.pipeline.UniformType;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.resources.Identifier;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

public final class PipelineBuilder {
    public static final VertexFormat EMPTY_VERTEX_FORMAT = VertexFormat.builder(0).build();
    public static final Snippet PROJECTION_SNIPPET = RenderPipeline.builder(new Snippet[0])
            .withBindGroupLayout(BindGroupLayouts.PROJECTION)
            .buildSnippet();

    private final Builder builder;
    private final BindGroupLayout.Builder bindGroupBuilder = BindGroupLayout.builder();
    private final Set<String> boundNames = new HashSet<>();
    private boolean hasBindGroup;
    private CompareOp depthTest = CompareOp.GREATER_THAN_OR_EQUAL;
    private boolean writeDepth = true;
    private boolean hasDepthStencil;
    private float depthBiasScaleFactor;
    private float depthBiasConstant;
    private Optional<BlendFunction> blendFunction = Optional.empty();
    private int writeMask = ColorTargetState.WRITE_ALL;
    private boolean hasColorTarget;
    private GpuFormat colorFormat = GpuFormat.RGBA8_UNORM;

    private PipelineBuilder(Snippet... snippets) {
        this.builder = RenderPipeline.builder(snippets);

        for (Snippet snippet : snippets) {
            snippet.bindGroupLayouts().ifPresent(layouts -> layouts.forEach(layout ->
                    layout.uniforms().forEach(desc -> this.boundNames.add(desc.name()))
            ));
            snippet.depthStencilState().ifPresent(ds -> {
                this.depthTest = ds.depthTest();
                this.writeDepth = ds.writeDepth();
                this.depthBiasScaleFactor = ds.depthBiasScaleFactor();
                this.depthBiasConstant = ds.depthBiasConstant();
            });
            if (snippet.colorTargetStates() != null && snippet.activeColorTargetStateCount() > 0 && snippet.colorTargetStates()[0] != null) {
                this.colorFormat = snippet.colorTargetStates()[0].format();
                this.blendFunction = snippet.colorTargetStates()[0].blendFunction();
                this.writeMask = snippet.colorTargetStates()[0].writeMask();
            }
        }
    }

    public static PipelineBuilder create(Snippet... snippets) {
        return new PipelineBuilder(snippets);
    }

    public static CompiledRenderPipeline compile(RenderPipeline pipeline) {
        return RenderSystem.getCompiledPipeline(pipeline);
    }

    public PipelineBuilder format(GpuFormat format) {
        this.colorFormat = format;
        this.hasColorTarget = true;
        return this;
    }

    public PipelineBuilder polygonMode(PolygonMode mode) {
        this.builder.withPolygonMode(mode);
        return this;
    }

    public PipelineBuilder depthTest(CompareOp compareOp) {
        this.depthTest = compareOp;
        this.hasDepthStencil = true;
        return this;
    }

    public PipelineBuilder writeMask(boolean writeColor, boolean writeAlpha) {
        this.writeMask = (writeColor ? ColorTargetState.WRITE_COLOR : 0) | (writeAlpha ? ColorTargetState.WRITE_ALPHA : 0);
        this.hasColorTarget = true;
        return this;
    }

    public PipelineBuilder depthBias(float scaleFactor, float constant) {
        this.depthBiasScaleFactor = scaleFactor;
        this.depthBiasConstant = constant;
        this.hasDepthStencil = true;
        return this;
    }

    public RenderPipeline build() {
        this.applyState();
        return this.builder.build();
    }

    public PipelineBuilder define(String name, float value) {
        this.builder.withShaderDefine(name, value);
        return this;
    }

    private void applyState() {
        if (this.hasBindGroup) {
            this.builder.withBindGroupLayout(this.bindGroupBuilder.build());
            this.hasBindGroup = false;
        }

        if (this.hasDepthStencil) {
            this.builder.withDepthStencilState(
                    this.depthTest == CompareOp.ALWAYS_PASS
                            ? Optional.empty()
                            : Optional.of(new DepthStencilState(this.depthTest, this.writeDepth, this.depthBiasScaleFactor, this.depthBiasConstant))
            );
        }

        if (this.hasColorTarget) {
            this.builder.withColorTargetState(new ColorTargetState(this.blendFunction, this.colorFormat, this.writeMask));
        }
    }

    public PipelineBuilder cull(boolean cull) {
        this.builder.withCull(cull);
        return this;
    }

    public PipelineBuilder location(String location) {
        this.builder.withLocation(location);
        return this;
    }

    public PipelineBuilder noBlend() {
        this.blendFunction = Optional.empty();
        this.hasColorTarget = true;
        return this;
    }

    public Snippet buildSnippet() {
        this.applyState();
        return this.builder.buildSnippet();
    }

    public PipelineBuilder uniform(String name, UniformType type) {
        if (this.boundNames.add(name)) {
            this.bindGroupBuilder.withUniform(name, type);
            this.hasBindGroup = true;
        }
        return this;
    }

    public PipelineBuilder fragmentShader(String shader) {
        this.builder.withFragmentShader(shader);
        return this;
    }

    public PipelineBuilder blend(BlendFunction blend) {
        this.blendFunction = Optional.of(blend);
        this.hasColorTarget = true;
        return this;
    }

    public PipelineBuilder define(String name, int value) {
        this.builder.withShaderDefine(name, value);
        return this;
    }

    public PipelineBuilder vertexShader(Identifier shader) {
        this.builder.withVertexShader(shader);
        return this;
    }

    public PipelineBuilder define(String name) {
        this.builder.withShaderDefine(name);
        return this;
    }

    public PipelineBuilder writeDepth(boolean writeDepth) {
        this.writeDepth = writeDepth;
        this.hasDepthStencil = true;
        return this;
    }

    public PipelineBuilder vertexShader(String shader) {
        this.builder.withVertexShader(shader);
        return this;
    }

    public PipelineBuilder vertexFormat(VertexFormat format, PrimitiveTopology topology) {
        if (format.getVertexSize() != 0) {
            this.builder.withVertexBinding(0, format);
        }
        this.builder.withPrimitiveTopology(topology);
        return this;
    }

    public PipelineBuilder fragmentShader(Identifier shader) {
        this.builder.withFragmentShader(shader);
        return this;
    }

    public PipelineBuilder sampler(String name) {
        if (this.boundNames.add(name)) {
            this.bindGroupBuilder.withUniform(name, UniformType.COMBINED_IMAGE_SAMPLER);
            this.hasBindGroup = true;
        }
        return this;
    }

    public PipelineBuilder location(Identifier location) {
        this.builder.withLocation(location);
        return this;
    }
}
