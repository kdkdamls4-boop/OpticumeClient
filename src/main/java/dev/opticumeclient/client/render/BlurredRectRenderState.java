package dev.opticumeclient.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import dev.opticumeclient.client.render.font.OpticumeGuiContext;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.joml.Matrix3x2f;

/**
 * GuiElementRenderState submitting SDF rounded Liquid Glass quads directly into
 * MC 26.3 GuiRenderer, sampling the blurred screen buffer produced by {@link KawaseBlurRenderer}.
 */
public final class BlurredRectRenderState implements GuiElementRenderState {
    private static final VertexFormat BLUR_RECT_FORMAT = VertexFormat.builder(0)
            .addAttribute(DefaultVertexFormat.POSITION_SEMANTIC_NAME, GpuFormat.RGB32_FLOAT)
            .addAttribute(DefaultVertexFormat.UV0_SEMANTIC_NAME, GpuFormat.RG32_FLOAT)
            .addAttribute(DefaultVertexFormat.COLOR_SEMANTIC_NAME, GpuFormat.RGBA8_UNORM)
            .addAttribute(DefaultVertexFormat.UV3_SEMANTIC_NAME, GpuFormat.RG32_FLOAT)
            .addAttribute(DefaultVertexFormat.LINE_WIDTH_SEMANTIC_NAME, GpuFormat.R32_FLOAT)
            .build();

    private static volatile RenderPipeline pipeline;
    private static volatile boolean pipelineWarned = false;

    private final GpuTextureView blurredView;
    private final float x;
    private final float y;
    private final float w;
    private final float h;
    private final float packedAlphaRadius;
    private final int tintArgb;
    private final Matrix3x2f pose;
    private final ScreenRectangle scissorArea;
    private ScreenRectangle cachedBounds;
    private boolean boundsComputed;

    private BlurredRectRenderState(
            GpuTextureView blurredView,
            float x,
            float y,
            float w,
            float h,
            float radius,
            int tintArgb,
            float overallAlpha,
            Matrix3x2f pose,
            ScreenRectangle scissorArea
    ) {
        this.blurredView = blurredView;
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        int qAlpha = Math.max(0, Math.min(255, Math.round(Mth.clamp(overallAlpha, 0.0f, 1.0f) * 255.0f)));
        float safeRadius = Mth.clamp(radius, 0.0f, Math.min(w, h) * 0.5f);
        this.packedAlphaRadius = qAlpha * 2048.0f + Math.min(2000.0f, safeRadius);
        this.tintArgb = tintArgb;
        this.pose = pose;
        this.scissorArea = scissorArea;
    }

    public static boolean trySubmit(
            GuiGraphicsExtractor context,
            float x,
            float y,
            float w,
            float h,
            float radius,
            int tintArgb,
            float overallAlpha
    ) {
        if (context == null || w <= 0.5f || h <= 0.5f || overallAlpha <= 0.01f) {
            return false;
        }
        GpuTextureView view = KawaseBlurRenderer.getBlurredTextureView();
        if (view == null) {
            return false;
        }
        if (!(((Object) context) instanceof OpticumeGuiContext guiCtx)) {
            return false;
        }
        RenderPipeline pl = getPipeline();
        try {
            if (RenderSystem.getCompiledPipelineNullable(pl) == null) {
                return false;
            }
        } catch (Throwable t) {
            if (!pipelineWarned) {
                pipelineWarned = true;
                System.err.println("[BlurredRectRenderState] Pipeline compile failed: " + t);
            }
            return false;
        }
        BlurredRectRenderState state = new BlurredRectRenderState(
                view,
                x, y, w, h,
                radius,
                tintArgb,
                overallAlpha,
                new Matrix3x2f(context.pose()),
                guiCtx.opticume$peekScissor()
        );
        guiCtx.opticume$submitElement(state);
        return true;
    }

    private static RenderPipeline getPipeline() {
        if (pipeline == null) {
            synchronized (BlurredRectRenderState.class) {
                if (pipeline == null) {
                    Identifier shaderId = Identifier.fromNamespaceAndPath("opticumeclient", "core/ui_liquid_glass");
                    pipeline = RenderPipeline.builder()
                            .withLocation(Identifier.fromNamespaceAndPath("opticumeclient", "pipeline/ui_liquid_glass"))
                            .withBindGroupLayout(BindGroupLayouts.GLOBALS)
                            .withBindGroupLayout(BindGroupLayouts.PROJECTION)
                            .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
                            .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
                            .withVertexShader(shaderId)
                            .withFragmentShader(shaderId)
                            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                            .withCull(false)
                            .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
                            .withVertexBinding(0, BLUR_RECT_FORMAT)
                            .withPrimitiveTopology(PrimitiveTopology.QUADS)
                            .build();
                }
            }
        }
        return pipeline;
    }

    @Override
    public void buildVertices(VertexConsumer vc) {
        float x0 = x;
        float y0 = y;
        float x1 = x + w;
        float y1 = y + h;
        vc.addVertexWith2DPose(pose, x0, y0).setUv(0.0f, 0.0f).setColor(tintArgb).setUv3(w, h).setLineWidth(packedAlphaRadius);
        vc.addVertexWith2DPose(pose, x0, y1).setUv(0.0f, 1.0f).setColor(tintArgb).setUv3(w, h).setLineWidth(packedAlphaRadius);
        vc.addVertexWith2DPose(pose, x1, y1).setUv(1.0f, 1.0f).setColor(tintArgb).setUv3(w, h).setLineWidth(packedAlphaRadius);
        vc.addVertexWith2DPose(pose, x1, y0).setUv(1.0f, 0.0f).setColor(tintArgb).setUv3(w, h).setLineWidth(packedAlphaRadius);
    }

    @Override
    public RenderPipeline pipeline() {
        return getPipeline();
    }

    @Override
    public TextureSetup textureSetup() {
        GpuSampler sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        return TextureSetup.singleTexture(blurredView, sampler);
    }

    @Override
    public ScreenRectangle scissorArea() {
        return scissorArea;
    }

    @Override
    public ScreenRectangle bounds() {
        if (!boundsComputed) {
            int rx = Mth.floor(x - 1.0f);
            int ry = Mth.floor(y - 1.0f);
            int rw = Math.max(1, Mth.ceil(w + 2.0f));
            int rh = Math.max(1, Mth.ceil(h + 2.0f));
            ScreenRectangle rect = new ScreenRectangle(rx, ry, rw, rh).transformMaxBounds(pose);
            cachedBounds = scissorArea != null ? rect.intersection(scissorArea) : rect;
            boundsComputed = true;
        }
        return cachedBounds;
    }
}
