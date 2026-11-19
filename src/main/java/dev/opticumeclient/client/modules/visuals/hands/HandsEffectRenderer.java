package dev.opticumeclient.client.modules.visuals.hands;

import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.UniformType;
import com.mojang.renderpearl.api.textures.FilterMode;
import dev.opticumeclient.client.modules.visuals.HandsModule;
import dev.opticumeclient.client.modules.visuals.targetesp.PipelineBuilder;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemUseAnimation;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Multi-pass RenderPearl post-processing pipeline for {@link HandsModule}.
 * Ported from Desire HandsEffectRenderer and adapted for MC 26.3 Vulkan/RenderPearl.
 */
public final class HandsEffectRenderer {
    private static final HandsEffectRenderer INSTANCE = new HandsEffectRenderer();

    private static final String SHADER_VERTEX = "hands_screen_quad";
    private static final String SHADER_COMPOSITE = "hands_composite";
    private static final String SHADER_NOTHING_COMPOSITE = "hands_nothing_composite";

    private static final int BLUR_UBO_SIZE = 48;
    private static final int GLOW_UBO_SIZE = 80;
    private static final int TRAIL_DISPLACE_UBO_SIZE = 48;
    private static final int TRAIL_FLAME_UBO_SIZE = 48;
    private static final int TRAIL_ENERGY_UBO_SIZE = 64;
    private static final int TRAIL_RIBBON_UBO_SIZE = 64;
    private static final int HANDS_UBO_SIZE = 240;

    private final RenderPipeline baseCompositePipeline = createCompositePipeline(-1);
    private final RenderPipeline[] styledCompositePipelines = new RenderPipeline[6];
    private final RenderPipeline nothingCompositePipeline;
    private final RenderPipeline maskPipeline;
    private final RenderPipeline downsampleColorPipeline;
    private final RenderPipeline downsampleMaskPipeline;
    private final RenderPipeline upsampleMaskPipeline;
    private final RenderPipeline gaussianColorPipeline;
    private final RenderPipeline gaussianMaskPipeline;
    private final RenderPipeline supportPipeline;
    private final RenderPipeline itemDownsamplePipeline;
    private final RenderPipeline itemUpsamplePipeline;
    private final RenderPipeline itemGaussianPipeline;
    private final RenderPipeline itemColorPipeline;
    private final RenderPipeline outerGlowPipeline;
    private final RenderPipeline outlinePipeline;
    private final RenderPipeline glowOutlinePipeline;
    private final RenderPipeline trailDisplacePipeline;
    private final RenderPipeline trailEnergyDisplacePipeline;
    private final RenderPipeline trailFlamePipeline;
    private final RenderPipeline trailEnergyPipeline;
    private final RenderPipeline trailRibbonDisplacePipeline;
    private final RenderPipeline trailRibbonPipeline;
    private final RenderPipeline trailBlitPipeline;

    private RenderTarget maskTarget;
    private RenderTarget supportTarget;
    private final BlurChain maskBloomChain = new BlurChain("hands-bloom", GpuFormat.R8_UNORM);
    private final BlurChain itemBloomChain = new BlurChain("hands-item-bloom", GpuFormat.RGBA16_FLOAT);
    private final BlurChain mirrorBlurChain = new BlurChain("hands-mirror", GpuFormat.RGBA8_UNORM);
    private TextureTarget trailHistoryA;
    private TextureTarget trailHistoryB;

    private GpuBuffer handsUbo;
    private GpuBuffer glowUbo;
    private GpuBuffer outlineUbo;
    private GpuBuffer trailDisplaceUbo;
    private GpuBuffer trailFlameUbo;
    private GpuBuffer trailEnergyUbo;
    private GpuBuffer trailRibbonUbo;

    private long lastTrailTimeNs;
    private double ribbonWaveTime;
    private int ribbonFrameSeed;
    private float smoothedRibbonLength;
    private float smoothedRibbonStrength;
    private boolean ribbonStateInitialized;
    private float hitImpulse;
    private boolean wasSwinging;
    private float prevCameraYaw;
    private float prevCameraPitch;
    private boolean hasPrevCamera;
    private int activeTrailMode = -1;
    private boolean activeAutoItemColor;

    private final GlowUniformCache glowUniformCache = new GlowUniformCache();
    private final GlowUniformCache outlineUniformCache = new GlowUniformCache();

    private HandsEffectRenderer() {
        this.nothingCompositePipeline = PipelineBuilder.create()
                .location("opticumeclient/hands_nothing_composite")
                .vertexShader(shaderId(SHADER_COMPOSITE))
                .fragmentShader(shaderId(SHADER_NOTHING_COMPOSITE))
                .depthTest(CompareOp.ALWAYS_PASS)
                .writeDepth(false)
                .cull(false)
                .writeMask(true, true)
                .blend(BlendFunction.TRANSLUCENT)
                .vertexFormat(PipelineBuilder.EMPTY_VERTEX_FORMAT, PrimitiveTopology.TRIANGLES)
                .uniform("HandsData", UniformType.UNIFORM_BUFFER)
                .sampler("HandSampler")
                .sampler("HandMaskSampler")
                .sampler("BloomSampler")
                .build();

        this.maskPipeline = PipelineBuilder.create()
                .location("opticumeclient/hands_mask")
                .format(GpuFormat.R8_UNORM)
                .vertexShader(shaderId(SHADER_VERTEX))
                .fragmentShader(shaderId("hands_mask"))
                .depthTest(CompareOp.ALWAYS_PASS)
                .writeDepth(false)
                .cull(false)
                .noBlend()
                .vertexFormat(PipelineBuilder.EMPTY_VERTEX_FORMAT, PrimitiveTopology.TRIANGLES)
                .sampler("HandSampler")
                .sampler("HandDepthSampler")
                .build();

        this.downsampleColorPipeline = createBlurPassPipeline("hands_downsample", GpuFormat.RGBA8_UNORM, false);
        this.downsampleMaskPipeline = createBlurPassPipeline("hands_downsample", GpuFormat.R8_UNORM, false);
        this.upsampleMaskPipeline = createBlurPassPipeline("hands_upsample", GpuFormat.R8_UNORM, false);
        this.gaussianColorPipeline = createBlurPassPipeline("hands_gaussian", GpuFormat.RGBA8_UNORM, true);
        this.gaussianMaskPipeline = createBlurPassPipeline("hands_gaussian", GpuFormat.R8_UNORM, true);
        this.supportPipeline = createBlurPassPipeline("hands_support", GpuFormat.R8_UNORM, false);

        this.itemDownsamplePipeline = createBlurPassPipeline("hands_downsample", GpuFormat.RGBA16_FLOAT, false);
        this.itemUpsamplePipeline = createBlurPassPipeline("hands_upsample", GpuFormat.RGBA16_FLOAT, false);
        this.itemGaussianPipeline = createBlurPassPipeline("hands_gaussian", GpuFormat.RGBA16_FLOAT, true);

        this.itemColorPipeline = PipelineBuilder.create()
                .location("opticumeclient/hands_item_color")
                .format(GpuFormat.RGBA16_FLOAT)
                .vertexShader(shaderId(SHADER_VERTEX))
                .fragmentShader(shaderId("hands_item_color"))
                .depthTest(CompareOp.ALWAYS_PASS)
                .writeDepth(false)
                .cull(false)
                .noBlend()
                .vertexFormat(PipelineBuilder.EMPTY_VERTEX_FORMAT, PrimitiveTopology.TRIANGLES)
                .sampler("HandSampler")
                .sampler("HandMaskSampler")
                .build();

        this.outerGlowPipeline = PipelineBuilder.create()
                .location("opticumeclient/hands_outer_glow")
                .vertexShader(shaderId(SHADER_VERTEX))
                .fragmentShader(shaderId("hands_outer_glow"))
                .depthTest(CompareOp.ALWAYS_PASS)
                .writeDepth(false)
                .cull(false)
                .blend(BlendFunction.LIGHTNING)
                .vertexFormat(PipelineBuilder.EMPTY_VERTEX_FORMAT, PrimitiveTopology.TRIANGLES)
                .uniform("HandsGlow", UniformType.UNIFORM_BUFFER)
                .sampler("BloomSampler")
                .sampler("HandMaskSampler")
                .build();

        this.outlinePipeline = PipelineBuilder.create()
                .location("opticumeclient/hands_outline")
                .vertexShader(shaderId(SHADER_VERTEX))
                .fragmentShader(shaderId("hands_outline"))
                .depthTest(CompareOp.ALWAYS_PASS)
                .writeDepth(false)
                .cull(false)
                .blend(BlendFunction.TRANSLUCENT)
                .vertexFormat(PipelineBuilder.EMPTY_VERTEX_FORMAT, PrimitiveTopology.TRIANGLES)
                .uniform("HandsGlow", UniformType.UNIFORM_BUFFER)
                .sampler("HandSampler")
                .sampler("BloomSampler")
                .sampler("HandMaskSampler")
                .build();

        this.glowOutlinePipeline = PipelineBuilder.create()
                .location("opticumeclient/hands_glow_outline")
                .vertexShader(shaderId(SHADER_VERTEX))
                .fragmentShader(shaderId("hands_glow_outline"))
                .depthTest(CompareOp.ALWAYS_PASS)
                .writeDepth(false)
                .cull(false)
                .blend(BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA)
                .vertexFormat(PipelineBuilder.EMPTY_VERTEX_FORMAT, PrimitiveTopology.TRIANGLES)
                .uniform("HandsGlow", UniformType.UNIFORM_BUFFER)
                .sampler("HandSampler")
                .sampler("BloomSampler")
                .sampler("HandMaskSampler")
                .build();

        this.trailDisplacePipeline = PipelineBuilder.create()
                .location("opticumeclient/hands_trail_displace")
                .vertexShader(shaderId(SHADER_VERTEX))
                .fragmentShader(shaderId("hands_trail_displace"))
                .depthTest(CompareOp.ALWAYS_PASS)
                .writeDepth(false)
                .cull(false)
                .noBlend()
                .vertexFormat(PipelineBuilder.EMPTY_VERTEX_FORMAT, PrimitiveTopology.TRIANGLES)
                .uniform("TrailDisplace", UniformType.UNIFORM_BUFFER)
                .sampler("TrailSampler")
                .build();

        this.trailEnergyDisplacePipeline = PipelineBuilder.create()
                .location("opticumeclient/hands_trail_energy_displace")
                .vertexShader(shaderId(SHADER_VERTEX))
                .fragmentShader(shaderId("hands_trail_energy_displace"))
                .depthTest(CompareOp.ALWAYS_PASS)
                .writeDepth(false)
                .cull(false)
                .noBlend()
                .vertexFormat(PipelineBuilder.EMPTY_VERTEX_FORMAT, PrimitiveTopology.TRIANGLES)
                .uniform("TrailDisplace", UniformType.UNIFORM_BUFFER)
                .sampler("TrailSampler")
                .build();

        this.trailFlamePipeline = PipelineBuilder.create()
                .location("opticumeclient/hands_trail_flame")
                .vertexShader(shaderId(SHADER_VERTEX))
                .fragmentShader(shaderId("hands_trail_flame"))
                .depthTest(CompareOp.ALWAYS_PASS)
                .writeDepth(false)
                .cull(false)
                .blend(BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA)
                .vertexFormat(PipelineBuilder.EMPTY_VERTEX_FORMAT, PrimitiveTopology.TRIANGLES)
                .uniform("TrailFlame", UniformType.UNIFORM_BUFFER)
                .sampler("BloomSampler")
                .build();

        this.trailEnergyPipeline = PipelineBuilder.create()
                .location("opticumeclient/hands_trail_energy")
                .vertexShader(shaderId(SHADER_VERTEX))
                .fragmentShader(shaderId("hands_trail_energy"))
                .depthTest(CompareOp.ALWAYS_PASS)
                .writeDepth(false)
                .cull(false)
                .blend(BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA)
                .vertexFormat(PipelineBuilder.EMPTY_VERTEX_FORMAT, PrimitiveTopology.TRIANGLES)
                .uniform("TrailEnergy", UniformType.UNIFORM_BUFFER)
                .sampler("BloomSampler")
                .build();

        this.trailRibbonDisplacePipeline = PipelineBuilder.create()
                .location("opticumeclient/hands_trail_ribbon_displace")
                .vertexShader(shaderId(SHADER_VERTEX))
                .fragmentShader(shaderId("hands_trail_ribbon_displace"))
                .depthTest(CompareOp.ALWAYS_PASS)
                .writeDepth(false)
                .cull(false)
                .noBlend()
                .vertexFormat(PipelineBuilder.EMPTY_VERTEX_FORMAT, PrimitiveTopology.TRIANGLES)
                .uniform("TrailDisplace", UniformType.UNIFORM_BUFFER)
                .sampler("TrailSampler")
                .build();

        this.trailRibbonPipeline = PipelineBuilder.create()
                .location("opticumeclient/hands_trail_ribbon")
                .vertexShader(shaderId(SHADER_VERTEX))
                .fragmentShader(shaderId("hands_trail_ribbon"))
                .depthTest(CompareOp.ALWAYS_PASS)
                .writeDepth(false)
                .cull(false)
                .blend(BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA)
                .vertexFormat(PipelineBuilder.EMPTY_VERTEX_FORMAT, PrimitiveTopology.TRIANGLES)
                .uniform("TrailRibbon", UniformType.UNIFORM_BUFFER)
                .sampler("BloomSampler")
                .build();

        this.trailBlitPipeline = PipelineBuilder.create()
                .location("opticumeclient/hands_trail_blit")
                .vertexShader(shaderId(SHADER_VERTEX))
                .fragmentShader(shaderId("hands_trail_blit"))
                .depthTest(CompareOp.ALWAYS_PASS)
                .writeDepth(false)
                .cull(false)
                .blend(BlendFunction.ADDITIVE)
                .vertexFormat(PipelineBuilder.EMPTY_VERTEX_FORMAT, PrimitiveTopology.TRIANGLES)
                .sampler("TrailSampler")
                .build();
    }

    public static HandsEffectRenderer getInstance() {
        return INSTANCE;
    }

    private static Identifier shaderId(String name) {
        return Identifier.fromNamespaceAndPath("opticumeclient", "core/hands/" + name);
    }

    private static RenderPipeline createBlurPassPipeline(String fragmentName, GpuFormat format, boolean hasBlurUniform) {
        boolean isMask = format == GpuFormat.R8_UNORM;
        String suffix = isMask ? "_mask" : (format == GpuFormat.RGBA16_FLOAT ? "_item" : "_scene");
        PipelineBuilder builder = PipelineBuilder.create()
                .location("opticumeclient/" + fragmentName + suffix)
                .format(format)
                .vertexShader(shaderId(SHADER_VERTEX))
                .fragmentShader(shaderId(fragmentName))
                .depthTest(CompareOp.ALWAYS_PASS)
                .writeDepth(false)
                .cull(false)
                .noBlend()
                .vertexFormat(PipelineBuilder.EMPTY_VERTEX_FORMAT, PrimitiveTopology.TRIANGLES)
                .sampler("Sampler0");
        if (isMask) {
            builder.define("HANDS_MASK");
        }
        if (hasBlurUniform) {
            builder.uniform("HandsBlur", UniformType.UNIFORM_BUFFER);
        }
        return builder.build();
    }

    private static RenderPipeline createCompositePipeline(int styleIndex) {
        String tag = styleIndex < 0 ? "base" : String.valueOf(styleIndex);
        PipelineBuilder builder = PipelineBuilder.create()
                .location("opticumeclient/hands_composite_" + tag)
                .vertexShader(shaderId(SHADER_COMPOSITE))
                .fragmentShader(shaderId(SHADER_COMPOSITE))
                .depthTest(CompareOp.ALWAYS_PASS)
                .writeDepth(false)
                .cull(false)
                .writeMask(true, true)
                .blend(BlendFunction.TRANSLUCENT)
                .vertexFormat(PipelineBuilder.EMPTY_VERTEX_FORMAT, PrimitiveTopology.TRIANGLES)
                .uniform("HandsData", UniformType.UNIFORM_BUFFER)
                .sampler("HandSampler")
                .sampler("HandDepthSampler")
                .sampler("HandMaskSampler")
                .sampler("HandSupportSampler")
                .sampler("BlurredSceneSampler")
                .sampler("BloomSampler");
        builder.define("HANDS_STYLE", styleIndex);
        return builder.build();
    }

    public void render(RenderTarget captureTarget, RenderTarget mainTarget, HandsModule module) {
        if (captureTarget == null || mainTarget == null
                || captureTarget.getColorTextureView() == null
                || captureTarget.getDepthTextureView() == null
                || mainTarget.getColorTextureView() == null) {
            return;
        }

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        boolean autoItemColor = module.isAutoItemColor();
        if (this.activeAutoItemColor != autoItemColor) {
            this.activeAutoItemColor = autoItemColor;
            this.glowUniformCache.reset();
            this.outlineUniformCache.reset();
            clearTrailTargets(encoder);
            releaseBlurChain(this.activeAutoItemColor ? this.maskBloomChain : this.itemBloomChain);
        }

        boolean mirrorActive = module.isMirror();
        boolean trailActive = module.isTrailActive();
        boolean outerGlowActive = module.isGlowEnabled() && module.getGlowBrightness() > 0.001f;
        boolean bloomNeeded = module.isGlowEnabled() && (outerGlowActive || module.getInnerGlow() > 0.001f || trailActive);
        int outlineColor = module.getEffectiveOutlineColor();
        boolean outlineActive = module.isOutlineEnabled() && module.getOutlineWidth() > 0.001f && (outlineColor >>> 24) != 0;
        boolean combinedGlowOutline = outerGlowActive && outlineActive && (outlineColor >>> 24) == 255;

        RenderTarget blurredScene = captureTarget;
        if (mirrorActive && module.getMirrorMix() > 0.001f) {
            blurredScene = runBloomChain(mainTarget, this.mirrorBlurChain, 2, (0.6f + module.getMirrorBlur() * 3.8f) * 0.25f, false, null, encoder);
        }

        ensureMaskTarget(captureTarget.width, captureTarget.height);
        drawMask(captureTarget, encoder);

        boolean nothingMode = module.isNothing();
        if (!nothingMode) {
            this.supportTarget = ensureRenderTarget(
                    this.supportTarget,
                    "hands-support",
                    Math.max(2, (captureTarget.width + 1) / 2),
                    Math.max(2, (captureTarget.height + 1) / 2),
                    GpuFormat.R8_UNORM
            );
            drawBlurPass(this.maskTarget, this.supportTarget, this.supportPipeline, null, "handsSupport", encoder);
        }

        if (!trailActive && this.activeTrailMode != -1) {
            clearTrailTargets(encoder);
            this.activeTrailMode = -1;
            resetTrailMotionState();
        }

        RenderTarget bloomTarget = null;
        if (bloomNeeded) {
            float blur = Math.clamp(module.getGlowBlur(), 1.0f, 5.0f);
            if (module.isEnergyTrail()) {
                blur = Math.max(3.0f, blur);
            }
            int levels = blur <= 1.0f ? 1 : 2;
            float sigma = (1.5f + (blur - 1.0f) * 2.5f) / (1 << levels);
            bloomTarget = runActiveBloomChain(captureTarget, module, levels, sigma, trailActive, encoder);

            if (trailActive) {
                renderTrail(bloomTarget, mainTarget, module, encoder);
            }
            if (outerGlowActive && !combinedGlowOutline) {
                drawGlowOrOutlinePass(bloomTarget, this.maskTarget, captureTarget, mainTarget, module, true, false, this.outerGlowPipeline, encoder);
            }
        }

        if (outlineActive) {
            if (bloomTarget == null) {
                bloomTarget = runActiveBloomChain(captureTarget, module, 1, 0.75f, false, encoder);
            }
            drawGlowOrOutlinePass(
                    bloomTarget,
                    this.maskTarget,
                    captureTarget,
                    mainTarget,
                    module,
                    combinedGlowOutline,
                    true,
                    combinedGlowOutline ? this.glowOutlinePipeline : this.outlinePipeline,
                    encoder
            );
        }

        float fov = Minecraft.getInstance().options.fov().get();
        float tanHalfFov = (float) Math.tan(Math.toRadians(fov) * 0.5);
        float aspect = (float) mainTarget.width / Math.max(1, mainTarget.height);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            Std140Builder builder = Std140Builder.onStack(stack, HANDS_UBO_SIZE)
                    .putVec4(module.getModeIndex(), module.getShaderStyleIndex(), module.getElapsedSeconds(), module.getShaderSpeed())
                    .putVec4(module.getShaderScale(), module.getShaderGlow(), module.getShaderOpacity(), module.getShaderShadowStrength())
                    .putVec4(module.isStars() ? 1.0f : 0.0f, module.isKeepShaderShadows() ? 1.0f : 0.0f, module.isRainbowFill() ? 1.0f : 0.0f, module.isKeepFillShadows() ? 1.0f : 0.0f)
                    .putVec4(module.getFillOpacity(), module.getFillShadowStrength(), module.getRainbowSpeed(), module.getRainbowScale())
                    .putVec4(module.getMirrorMix(), module.getMirrorDistortion(), bloomNeeded ? 1.0f : 0.0f, module.getGlowBlur())
                    .putVec4(module.getGlowBrightness(), autoItemColor ? 1.0f : 0.0f, 0.0f, module.getInnerGlow())
                    .putVec4(aspect, tanHalfFov, module.getQualitySteps(), module.getMirrorBlur());
            putArgbVec4(builder, module.getShaderColor1());
            putArgbVec4(builder, module.getShaderColor2());
            putArgbVec4(builder, module.getFillColor());
            putArgbVec4(builder, module.getMirrorColor());
            putArgbVec4(builder, module.getGlowColor1());
            putArgbVec4(builder, module.getGlowColor2());
            ByteBuffer data = builder.putVec2(captureTarget.width, captureTarget.height).putVec2(0.0f, 0.0f).get();
            this.handsUbo = ensureUbo(this.handsUbo, HANDS_UBO_SIZE, "handsData");
            encoder.writeToBuffer(this.handsUbo.slice(), data);
        }

        RenderPipeline compositePipeline = nothingMode ? this.nothingCompositePipeline : this.baseCompositePipeline;
        if (!nothingMode && module.isShader() && module.getShaderOpacity() > 0.001f) {
            int styleIdx = Math.clamp(module.getShaderStyleIndex(), 0, this.styledCompositePipelines.length - 1);
            if (this.styledCompositePipelines[styleIdx] == null) {
                this.styledCompositePipelines[styleIdx] = createCompositePipeline(styleIdx);
            }
            compositePipeline = this.styledCompositePipelines[styleIdx];
        }

        RenderTarget effectiveBloom = bloomTarget != null ? bloomTarget : this.maskTarget;
        try (RenderPass pass = encoder.createRenderPass(() -> "handsComposite", mainTarget.getColorTextureView(), Optional.empty())) {
            pass.setPipeline(PipelineBuilder.compile(compositePipeline));
            pass.setUniform("HandSampler", captureTarget.getColorTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
            pass.setUniform("HandMaskSampler", this.maskTarget.getColorTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
            pass.setUniform("BloomSampler", effectiveBloom.getColorTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
            if (!nothingMode) {
                pass.setUniform("HandSupportSampler", this.supportTarget.getColorTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
                pass.setUniform("HandDepthSampler", captureTarget.getDepthTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
                pass.setUniform("BlurredSceneSampler", blurredScene.getColorTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
            }
            pass.setUniform("HandsData", this.handsUbo);
            pass.draw(3, 1, 0, 0);
        }
    }

    private RenderTarget runActiveBloomChain(RenderTarget captureTarget, HandsModule module, int levels, float sigma, boolean upsampleTop, CommandEncoder encoder) {
        if (module.isAutoItemColor()) {
            return runBloomChain(this.maskTarget, this.itemBloomChain, levels, sigma, upsampleTop, captureTarget, encoder);
        }
        return runBloomChain(this.maskTarget, this.maskBloomChain, levels, sigma, upsampleTop, null, encoder);
    }

    private RenderTarget runBloomChain(
            RenderTarget source,
            BlurChain chain,
            int levels,
            float sigma,
            boolean upsampleToFirst,
            RenderTarget itemColorSource,
            CommandEncoder encoder
    ) {
        RenderTarget current = source;
        for (int i = 0; i < levels; i++) {
            int downW = Math.max(2, (current.width + 1) / 2);
            int downH = Math.max(2, (current.height + 1) / 2);
            while (chain.downLevels.size() <= i) {
                chain.downLevels.add(null);
            }
            RenderTarget levelTarget = ensureRenderTarget(chain.downLevels.get(i), chain.prefix + "-down-" + i, downW, downH, chain.format);
            chain.downLevels.set(i, levelTarget);
            if (itemColorSource != null && i == 0) {
                drawItemColorPass(itemColorSource, levelTarget, encoder);
            } else {
                RenderPipeline downPipeline = chain.isMask
                        ? this.downsampleMaskPipeline
                        : (chain == this.itemBloomChain ? this.itemDownsamplePipeline : this.downsampleColorPipeline);
                drawBlurPass(current, levelTarget, downPipeline, null, "handsDownsample", encoder);
            }
            current = levelTarget;
        }

        if (chain != this.mirrorBlurChain && levels == 1 && sigma <= 0.75f) {
            return current;
        }

        chain.scratch = ensureRenderTarget(chain.scratch, chain.prefix + "-scratch", current.width, current.height, chain.format);
        updateBlurUniforms(chain, current.width, current.height, sigma, encoder);
        RenderPipeline gaussianPipeline = chain.isMask
                ? this.gaussianMaskPipeline
                : (chain == this.itemBloomChain ? this.itemGaussianPipeline : this.gaussianColorPipeline);
        drawBlurPass(current, chain.scratch, gaussianPipeline, chain.horizontalUbo, "handsBlurH", encoder);
        drawBlurPass(chain.scratch, current, gaussianPipeline, chain.verticalUbo, "handsBlurV", encoder);

        if (upsampleToFirst && levels > 1) {
            RenderTarget top = chain.downLevels.getFirst();
            RenderPipeline upPipeline = chain == this.itemBloomChain ? this.itemUpsamplePipeline : this.upsampleMaskPipeline;
            drawBlurPass(current, top, upPipeline, null, "handsUpsample", encoder);
            return top;
        }
        return current;
    }

    private void drawItemColorPass(RenderTarget captureTarget, RenderTarget dst, CommandEncoder encoder) {
        try (RenderPass pass = encoder.createRenderPass(() -> "handsItemColor", dst.getColorTextureView(), Optional.empty())) {
            pass.setPipeline(PipelineBuilder.compile(this.itemColorPipeline));
            pass.setUniform("HandSampler", captureTarget.getColorTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
            pass.setUniform("HandMaskSampler", this.maskTarget.getColorTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
            pass.draw(3, 1, 0, 0);
        }
    }

    private void drawMask(RenderTarget captureTarget, CommandEncoder encoder) {
        try (RenderPass pass = encoder.createRenderPass(() -> "handsMask", this.maskTarget.getColorTextureView(), Optional.empty())) {
            pass.setPipeline(PipelineBuilder.compile(this.maskPipeline));
            pass.setUniform("HandSampler", captureTarget.getColorTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
            pass.setUniform("HandDepthSampler", captureTarget.getDepthTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
            pass.draw(3, 1, 0, 0);
        }
    }

    private void drawBlurPass(RenderTarget src, RenderTarget dst, RenderPipeline pipeline, GpuBuffer ubo, String label, CommandEncoder encoder) {
        try (RenderPass pass = encoder.createRenderPass(() -> label, dst.getColorTextureView(), Optional.empty())) {
            pass.setPipeline(PipelineBuilder.compile(pipeline));
            pass.setUniform("Sampler0", src.getColorTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
            if (ubo != null) {
                pass.setUniform("HandsBlur", ubo);
            }
            pass.draw(3, 1, 0, 0);
        }
    }

    private void drawGlowOrOutlinePass(
            RenderTarget bloomTarget,
            RenderTarget maskTarget,
            RenderTarget captureTarget,
            RenderTarget dstTarget,
            HandsModule module,
            boolean includeGlow,
            boolean includeOutline,
            RenderPipeline pipeline,
            CommandEncoder encoder
    ) {
        float brightness = includeGlow ? module.getGlowBrightness() : 0.0f;
        int c1 = module.getGlowColor1();
        int c2 = module.getGlowColor2();
        int outCol = module.getEffectiveOutlineColor();
        float outWidth = includeOutline ? module.getOutlineWidth() : 0.0f;
        GpuBuffer existing = includeOutline ? this.outlineUbo : this.glowUbo;
        GpuBuffer buffer = ensureUbo(existing, GLOW_UBO_SIZE, includeOutline ? "handsOutlineData" : "handsGlowData");
        GlowUniformCache cache = includeOutline ? this.outlineUniformCache : this.glowUniformCache;
        if (buffer != existing || !cache.matches(brightness, c1, c2, outCol, maskTarget.width, maskTarget.height, outWidth, includeOutline)) {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                Std140Builder builder = Std140Builder.onStack(stack, GLOW_UBO_SIZE)
                        .putVec4(brightness, module.isAutoItemColor() ? 1.0f : 0.0f, 0.0f, 0.0f);
                putArgbVec4(builder, c1);
                putArgbVec4(builder, c2);
                putArgbVec4(builder, outCol);
                ByteBuffer data = builder.putVec4(maskTarget.width, maskTarget.height, outWidth, includeOutline ? 1.0f : 0.0f).get();
                encoder.writeToBuffer(buffer.slice(), data);
            }
            cache.update(brightness, c1, c2, outCol, maskTarget.width, maskTarget.height, outWidth, includeOutline);
        }
        if (includeOutline) {
            this.outlineUbo = buffer;
        } else {
            this.glowUbo = buffer;
        }

        String label = (includeGlow && includeOutline) ? "handsGlowOutline" : (includeOutline ? "handsOutline" : "handsOuterGlow");
        try (RenderPass pass = encoder.createRenderPass(() -> label, dstTarget.getColorTextureView(), Optional.empty())) {
            pass.setPipeline(PipelineBuilder.compile(pipeline));
            pass.setUniform("BloomSampler", bloomTarget.getColorTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
            pass.setUniform("HandMaskSampler", maskTarget.getColorTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
            if (includeOutline) {
                pass.setUniform("HandSampler", captureTarget.getColorTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
            }
            pass.setUniform("HandsGlow", buffer);
            pass.draw(3, 1, 0, 0);
        }
    }

    private void renderTrail(RenderTarget bloomTarget, RenderTarget mainTarget, HandsModule module, CommandEncoder encoder) {
        ensureTrailTargets(bloomTarget.width, bloomTarget.height, encoder);
        boolean energy = module.isEnergyTrail();
        boolean ribbon = module.isRibbonTrail();
        int trailModeId = module.getTrailModeIndex();
        if (this.activeTrailMode != trailModeId) {
            clearTrailTargets(encoder);
            this.activeTrailMode = trailModeId;
            resetTrailMotionState();
        }

        long nowNs = System.nanoTime();
        float delta = this.lastTrailTimeNs > 0L
                ? Math.min(0.1f, (float) (nowNs - this.lastTrailTimeNs) / 1.0E9f)
                : (1.0f / 60.0f);
        this.lastTrailTimeNs = nowNs;

        float timeSec = (float) (nowNs % 100000000000L) / 1.0E9f;
        Minecraft mc = Minecraft.getInstance();
        if (ribbon) {
            updateRibbonState(module, mc, delta);
            timeSec = (float) this.ribbonWaveTime;
        }

        float speedFactor = module.getEnergySpeed();
        float liftSpeed = ribbon
                ? module.getRibbonLift()
                : (energy ? 0.035f * speedFactor : module.getTrailLift());
        float sway = ribbon ? 0.0f : (energy ? 0.018f * speedFactor : module.getTrailSway());
        float camLag = ribbon
                ? module.getRibbonCameraLag()
                : (energy ? module.getEnergyCameraLag() : module.getTrailCameraLag());
        float tau5 = (float) (Math.PI * 8.0 / 5.0);
        float offsetY = liftSpeed * delta;
        float offsetX = ((float) Math.sin(timeSec * tau5) - (float) Math.sin((timeSec - delta) * tau5)) * sway;

        float camDx = 0.0f;
        float camDy = 0.0f;
        Camera camera = mc.gameRenderer != null ? mc.gameRenderer.mainCamera() : null;
        if (camera != null) {
            float yaw = camera.yRot();
            float pitch = camera.xRot();
            if (this.hasPrevCamera) {
                float dyaw = yaw - this.prevCameraYaw;
                while (dyaw > 180.0f) {
                    dyaw -= 360.0f;
                }
                while (dyaw < -180.0f) {
                    dyaw += 360.0f;
                }
                float dpitch = pitch - this.prevCameraPitch;
                float fov = Math.max(1.0f, mc.options.fov().get());
                camDx = Math.clamp(-(dyaw / fov) * camLag, -0.25f, 0.25f);
                camDy = Math.clamp((dpitch / fov) * camLag, -0.25f, 0.25f);
            }
            this.prevCameraYaw = yaw;
            this.prevCameraPitch = pitch;
            this.hasPrevCamera = true;
        }

        boolean swinging = mc.player != null && mc.player.isSwinging();
        boolean hitImpulseTriggered = module.isNormalTrail() && module.isTrailHitBlow() && swinging && !this.wasSwinging;
        this.wasSwinging = swinging;
        if (hitImpulseTriggered) {
            this.hitImpulse = 1.0f;
        }

        float fadeRate = (ribbon
                ? 0.045f / Math.max(0.15f, this.smoothedRibbonLength)
                : (energy ? 0.048f / Math.max(0.4f, module.getEnergyLength()) : module.getTrailFadeSpeed()))
                + this.hitImpulse * module.getTrailHitBlowStrength() * 0.012f;
        this.hitImpulse = Math.max(0.0f, this.hitImpulse - delta / 0.45f);
        float turbulence = ribbon
                ? module.getRibbonBend()
                : (energy ? 0.32f * speedFactor : module.getTrailTurbulence());
        float flicker = (!ribbon && !energy) ? module.getTrailFlicker() : 0.0f;

        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer data = Std140Builder.onStack(stack, TRAIL_DISPLACE_UBO_SIZE)
                    .putVec2(offsetX + camDx, offsetY + camDy)
                    .putVec2(this.trailHistoryA.width, this.trailHistoryA.height)
                    .putVec4(fadeRate, timeSec, delta, turbulence)
                    .putVec4(ribbon ? module.getRibbonWaveSpeed() : flicker, ribbon ? this.ribbonFrameSeed : 0.0f, 0.0f, 0.0f)
                    .get();
            this.trailDisplaceUbo = ensureUbo(this.trailDisplaceUbo, TRAIL_DISPLACE_UBO_SIZE, "handsTrailDisplaceData");
            encoder.writeToBuffer(this.trailDisplaceUbo.slice(), data);
        }

        try (RenderPass pass = encoder.createRenderPass(
                () -> "handsTrailDisplace",
                this.trailHistoryB.getColorTextureView(),
                Optional.of(new Vector4f(0.0f))
        )) {
            pass.setPipeline(PipelineBuilder.compile(
                    ribbon ? this.trailRibbonDisplacePipeline : (energy ? this.trailEnergyDisplacePipeline : this.trailDisplacePipeline)
            ));
            pass.setUniform("TrailSampler", this.trailHistoryA.getColorTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
            pass.setUniform("TrailDisplace", this.trailDisplaceUbo);
            pass.draw(3, 1, 0, 0);
        }

        if (ribbon) {
            drawRibbonStampPass(encoder, bloomTarget, module, delta, timeSec);
        } else if (energy) {
            drawEnergyStampPass(encoder, bloomTarget, module, delta, timeSec);
        } else {
            drawFlameStampPass(encoder, bloomTarget, module, delta);
        }

        try (RenderPass pass = encoder.createRenderPass(() -> "handsTrailBlit", mainTarget.getColorTextureView(), Optional.empty())) {
            pass.setPipeline(PipelineBuilder.compile(this.trailBlitPipeline));
            pass.setUniform("TrailSampler", this.trailHistoryB.getColorTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
            pass.draw(3, 1, 0, 0);
        }

        TextureTarget swap = this.trailHistoryA;
        this.trailHistoryA = this.trailHistoryB;
        this.trailHistoryB = swap;
    }

    private void drawFlameStampPass(CommandEncoder encoder, RenderTarget bloomTarget, HandsModule module, float delta) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            Std140Builder builder = Std140Builder.onStack(stack, TRAIL_FLAME_UBO_SIZE)
                    .putVec4(module.getGlowBrightness(), module.isAutoItemColor() ? 1.0f : 0.0f, 0.0f, delta);
            putArgbVec4(builder, module.getGlowColor1());
            putArgbVec4(builder, module.getGlowColor2());
            this.trailFlameUbo = ensureUbo(this.trailFlameUbo, TRAIL_FLAME_UBO_SIZE, "handsTrailFlameData");
            encoder.writeToBuffer(this.trailFlameUbo.slice(), builder.get());
        }

        try (RenderPass pass = encoder.createRenderPass(() -> "handsTrailFlame", this.trailHistoryB.getColorTextureView(), Optional.empty())) {
            pass.setPipeline(PipelineBuilder.compile(this.trailFlamePipeline));
            pass.setUniform("BloomSampler", bloomTarget.getColorTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
            pass.setUniform("TrailFlame", this.trailFlameUbo);
            pass.draw(3, 1, 0, 0);
        }
    }

    private void drawEnergyStampPass(CommandEncoder encoder, RenderTarget bloomTarget, HandsModule module, float delta, float timeSec) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            Std140Builder builder = Std140Builder.onStack(stack, TRAIL_ENERGY_UBO_SIZE)
                    .putVec4(module.getGlowBrightness(), module.isAutoItemColor() ? 1.0f : 0.0f, 0.0f, delta)
                    .putVec4(timeSec, module.getEnergyBrightness(), module.getEnergyPulse(), 0.0f);
            putArgbVec4(builder, module.getGlowColor1());
            putArgbVec4(builder, module.getGlowColor2());
            this.trailEnergyUbo = ensureUbo(this.trailEnergyUbo, TRAIL_ENERGY_UBO_SIZE, "handsTrailEnergyData");
            encoder.writeToBuffer(this.trailEnergyUbo.slice(), builder.get());
        }

        try (RenderPass pass = encoder.createRenderPass(() -> "handsTrailEnergy", this.trailHistoryB.getColorTextureView(), Optional.empty())) {
            pass.setPipeline(PipelineBuilder.compile(this.trailEnergyPipeline));
            pass.setUniform("BloomSampler", bloomTarget.getColorTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
            pass.setUniform("TrailEnergy", this.trailEnergyUbo);
            pass.draw(3, 1, 0, 0);
        }
    }

    private void drawRibbonStampPass(CommandEncoder encoder, RenderTarget bloomTarget, HandsModule module, float delta, float timeSec) {
        float effectiveExposure = module.getGlowBrightness() * module.getRibbonBrightness() * this.smoothedRibbonStrength;
        if (effectiveExposure <= 0.001f) {
            return;
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            Std140Builder builder = Std140Builder.onStack(stack, TRAIL_RIBBON_UBO_SIZE)
                    .putVec4(effectiveExposure, timeSec, delta, module.getRibbonWidth())
                    .putVec4(module.getRibbonBend(), (float) bloomTarget.width / Math.max(1, bloomTarget.height), module.isAutoItemColor() ? 1.0f : 0.0f, 0.0f);
            putArgbVec4(builder, module.getGlowColor1());
            putArgbVec4(builder, module.getGlowColor2());
            this.trailRibbonUbo = ensureUbo(this.trailRibbonUbo, TRAIL_RIBBON_UBO_SIZE, "handsTrailRibbonData");
            encoder.writeToBuffer(this.trailRibbonUbo.slice(), builder.get());
        }

        try (RenderPass pass = encoder.createRenderPass(() -> "handsTrailRibbon", this.trailHistoryB.getColorTextureView(), Optional.empty())) {
            pass.setPipeline(PipelineBuilder.compile(this.trailRibbonPipeline));
            pass.setUniform("BloomSampler", bloomTarget.getColorTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
            pass.setUniform("TrailRibbon", this.trailRibbonUbo);
            pass.draw(3, 1, 0, 0);
        }
    }

    private void updateRibbonState(HandsModule module, Minecraft mc, float delta) {
        float targetLength = module.getRibbonLength();
        float targetStrength = 1.0f;
        boolean consuming = false;
        if (mc.player != null && mc.player.isUsingItem()) {
            ItemUseAnimation anim = mc.player.getUseItem().getUseAnimation();
            consuming = anim == ItemUseAnimation.EAT || anim == ItemUseAnimation.DRINK;
        }
        if (consuming) {
            targetLength = module.isRibbonOnEat() ? module.getRibbonEatLength() : 0.15f;
            targetStrength = module.isRibbonOnEat() ? module.getRibbonEatStrength() : 0.0f;
        } else if (mc.player != null && mc.player.isSwinging()) {
            targetLength = module.isRibbonOnHit() ? module.getRibbonHitLength() : 0.15f;
            targetStrength = module.isRibbonOnHit() ? module.getRibbonHitStrength() : 0.0f;
        }
        float alpha = this.ribbonStateInitialized ? (float) (-Math.expm1(-delta * 16.0f)) : 1.0f;
        this.smoothedRibbonLength += (targetLength - this.smoothedRibbonLength) * alpha;
        this.smoothedRibbonStrength += (targetStrength - this.smoothedRibbonStrength) * alpha;
        this.ribbonStateInitialized = true;
        this.ribbonWaveTime += delta * module.getRibbonWaveSpeed();
        this.ribbonFrameSeed = (this.ribbonFrameSeed + 1) & 4095;
    }

    private void updateBlurUniforms(BlurChain chain, int width, int height, float sigma, CommandEncoder encoder) {
        boolean ready = chain.horizontalUbo != null && !chain.horizontalUbo.isClosed()
                && chain.verticalUbo != null && !chain.verticalUbo.isClosed();
        if (ready && chain.lastWidth == width && chain.lastHeight == height && Float.compare(chain.lastSigma, sigma) == 0) {
            return;
        }
        chain.horizontalUbo = ensureUbo(chain.horizontalUbo, BLUR_UBO_SIZE, chain.prefix + "-blur-h");
        chain.verticalUbo = ensureUbo(chain.verticalUbo, BLUR_UBO_SIZE, chain.prefix + "-blur-v");

        float[] rawWeights = new float[7];
        rawWeights[0] = 1.0f;
        float totalWeight = 1.0f;
        for (int i = 1; i < rawWeights.length; i++) {
            rawWeights[i] = (float) Math.exp(-(i * i) / (2.0f * sigma * sigma));
            totalWeight += rawWeights[i] * 2.0f;
        }
        float[] offsets = new float[3];
        float[] weights = new float[3];
        for (int i = 0; i < weights.length; i++) {
            int idx = i * 2 + 1;
            float pairSum = rawWeights[idx] + rawWeights[idx + 1];
            offsets[i] = idx + (pairSum > 0.0f ? rawWeights[idx + 1] / pairSum : 0.0f);
            weights[i] = pairSum / totalWeight;
        }

        try (MemoryStack stack = MemoryStack.stackPush()) {
            for (int axis = 0; axis < 2; axis++) {
                ByteBuffer data = Std140Builder.onStack(stack, BLUR_UBO_SIZE)
                        .putVec4(axis == 0 ? 1.0f / width : 0.0f, axis == 1 ? 1.0f / height : 0.0f, 0.0f, 0.0f)
                        .putVec4(1.0f / totalWeight, weights[0], weights[1], weights[2])
                        .putVec4(offsets[0], offsets[1], offsets[2], 0.0f)
                        .get();
                encoder.writeToBuffer((axis == 0 ? chain.horizontalUbo : chain.verticalUbo).slice(), data);
            }
        }
        chain.lastWidth = width;
        chain.lastHeight = height;
        chain.lastSigma = sigma;
    }

    private void ensureMaskTarget(int width, int height) {
        if (this.maskTarget == null) {
            this.maskTarget = new TextureTarget("hands-mask", width, height, GpuFormat.R8_UNORM, null);
        } else if (this.maskTarget.width != width || this.maskTarget.height != height) {
            this.maskTarget.resize(width, height);
            this.glowUniformCache.reset();
            this.outlineUniformCache.reset();
        }
    }

    private RenderTarget ensureRenderTarget(RenderTarget existing, String name, int width, int height, GpuFormat format) {
        if (existing == null) {
            return new TextureTarget(name, width, height, format, null);
        }
        if (existing.width != width || existing.height != height) {
            existing.resize(width, height);
        }
        return existing;
    }

    private void ensureTrailTargets(int width, int height, CommandEncoder encoder) {
        if (this.trailHistoryA == null) {
            this.trailHistoryA = new TextureTarget("hands-trail-a", width, height, GpuFormat.RGBA8_UNORM, null);
            this.trailHistoryB = new TextureTarget("hands-trail-b", width, height, GpuFormat.RGBA8_UNORM, null);
            clearTrailTargets(encoder);
        } else if (this.trailHistoryA.width != width || this.trailHistoryA.height != height) {
            this.trailHistoryA.resize(width, height);
            this.trailHistoryB.resize(width, height);
            clearTrailTargets(encoder);
        }
    }

    private void clearTrailTargets(CommandEncoder encoder) {
        if (this.trailHistoryA != null && this.trailHistoryB != null) {
            encoder.clearColorTexture(this.trailHistoryA.getColorTexture(), new Vector4f(0.0f));
            encoder.clearColorTexture(this.trailHistoryB.getColorTexture(), new Vector4f(0.0f));
        }
    }

    private void resetTrailMotionState() {
        this.lastTrailTimeNs = 0L;
        this.hitImpulse = 0.0f;
        this.wasSwinging = false;
        this.hasPrevCamera = false;
        this.ribbonWaveTime = 0.0;
        this.ribbonFrameSeed = 0;
        this.ribbonStateInitialized = false;
    }

    private GpuBuffer ensureUbo(GpuBuffer existing, int size, String label) {
        if (existing != null && !existing.isClosed()) {
            return existing;
        }
        return RenderSystem.getDevice().createBuffer(() -> label, GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, size);
    }

    private static void putArgbVec4(Std140Builder builder, int argb) {
        builder.putVec4(
                ((argb >>> 16) & 0xFF) / 255.0f,
                ((argb >>> 8) & 0xFF) / 255.0f,
                (argb & 0xFF) / 255.0f,
                ((argb >>> 24) & 0xFF) / 255.0f
        );
    }

    public void releaseGpuResources() {
        if (this.maskTarget != null) {
            this.maskTarget.destroyBuffers();
            this.maskTarget = null;
        }
        if (this.supportTarget != null) {
            this.supportTarget.destroyBuffers();
            this.supportTarget = null;
        }
        releaseBlurChain(this.maskBloomChain);
        releaseBlurChain(this.itemBloomChain);
        releaseBlurChain(this.mirrorBlurChain);
        if (this.trailHistoryA != null) {
            this.trailHistoryA.destroyBuffers();
            this.trailHistoryA = null;
        }
        if (this.trailHistoryB != null) {
            this.trailHistoryB.destroyBuffers();
            this.trailHistoryB = null;
        }
        resetTrailMotionState();
        this.activeTrailMode = -1;
        this.glowUniformCache.reset();
        this.outlineUniformCache.reset();
        closeBuffer(this.handsUbo);
        closeBuffer(this.glowUbo);
        closeBuffer(this.outlineUbo);
        closeBuffer(this.trailDisplaceUbo);
        closeBuffer(this.trailFlameUbo);
        closeBuffer(this.trailEnergyUbo);
        closeBuffer(this.trailRibbonUbo);
        this.handsUbo = null;
        this.glowUbo = null;
        this.outlineUbo = null;
        this.trailDisplaceUbo = null;
        this.trailFlameUbo = null;
        this.trailEnergyUbo = null;
        this.trailRibbonUbo = null;
    }

    private void releaseBlurChain(BlurChain chain) {
        for (RenderTarget target : chain.downLevels) {
            if (target != null) {
                target.destroyBuffers();
            }
        }
        chain.downLevels.clear();
        if (chain.scratch != null) {
            chain.scratch.destroyBuffers();
            chain.scratch = null;
        }
        closeBuffer(chain.horizontalUbo);
        closeBuffer(chain.verticalUbo);
        chain.horizontalUbo = null;
        chain.verticalUbo = null;
        chain.lastSigma = Float.NaN;
    }

    private static void closeBuffer(GpuBuffer buffer) {
        if (buffer != null && !buffer.isClosed()) {
            buffer.close();
        }
    }

    private static final class BlurChain {
        final String prefix;
        final GpuFormat format;
        final boolean isMask;
        final List<RenderTarget> downLevels = new ArrayList<>(2);
        RenderTarget scratch;
        GpuBuffer horizontalUbo;
        GpuBuffer verticalUbo;
        int lastWidth;
        int lastHeight;
        float lastSigma = Float.NaN;

        BlurChain(String prefix, GpuFormat format) {
            this.prefix = prefix;
            this.format = format;
            this.isMask = format == GpuFormat.R8_UNORM;
        }
    }

    private static final class GlowUniformCache {
        float intensity = Float.NaN;
        int glowColor1;
        int glowColor2;
        int outlineColor;
        int width = -1;
        int height = -1;
        float outlineWidth = Float.NaN;
        boolean includeOutline;

        boolean matches(float intensity, int glowColor1, int glowColor2, int outlineColor, int width, int height, float outlineWidth, boolean includeOutline) {
            return Float.compare(this.intensity, intensity) == 0
                    && this.glowColor1 == glowColor1
                    && this.glowColor2 == glowColor2
                    && this.outlineColor == outlineColor
                    && this.width == width
                    && this.height == height
                    && Float.compare(this.outlineWidth, outlineWidth) == 0
                    && this.includeOutline == includeOutline;
        }

        void update(float intensity, int glowColor1, int glowColor2, int outlineColor, int width, int height, float outlineWidth, boolean includeOutline) {
            this.intensity = intensity;
            this.glowColor1 = glowColor1;
            this.glowColor2 = glowColor2;
            this.outlineColor = outlineColor;
            this.width = width;
            this.height = height;
            this.outlineWidth = outlineWidth;
            this.includeOutline = includeOutline;
        }

        void reset() {
            this.intensity = Float.NaN;
            this.width = -1;
            this.height = -1;
            this.outlineWidth = Float.NaN;
        }
    }
}
