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
 * Sub-pixel SDF rounded fill and outline GuiElementRenderState for Desire-matched UI rendering.
 */
public final class RoundedRectRenderState implements GuiElementRenderState {
    private static final VertexFormat FORMAT = VertexFormat.builder(0)
            .addAttribute(DefaultVertexFormat.POSITION_SEMANTIC_NAME, GpuFormat.RGB32_FLOAT)
            .addAttribute(DefaultVertexFormat.UV0_SEMANTIC_NAME, GpuFormat.RG32_FLOAT)
            .addAttribute(DefaultVertexFormat.COLOR_SEMANTIC_NAME, GpuFormat.RGBA8_UNORM)
            .addAttribute(DefaultVertexFormat.UV3_SEMANTIC_NAME, GpuFormat.RG32_FLOAT)
            .addAttribute(DefaultVertexFormat.LINE_WIDTH_SEMANTIC_NAME, GpuFormat.R32_FLOAT)
            .build();

    private static volatile RenderPipeline pipeline;

    private final float x;
    private final float y;
    private final float w;
    private final float h;
    private final float encodedParam;
    private final int colTL;
    private final int colBL;
    private final int colBR;
    private final int colTR;
    private final Matrix3x2f pose;
    private final ScreenRectangle scissorArea;
    private ScreenRectangle cachedBounds;
    private boolean boundsComputed;

    private RoundedRectRenderState(
            float x, float y, float w, float h,
            float encodedParam,
            int colTL, int colBL, int colBR, int colTR,
            Matrix3x2f pose,
            ScreenRectangle scissorArea
    ) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        this.encodedParam = encodedParam;
        this.colTL = colTL;
        this.colBL = colBL;
        this.colBR = colBR;
        this.colTR = colTR;
        this.pose = pose;
        this.scissorArea = scissorArea;
    }

    public static boolean submitFill(GuiGraphicsExtractor context, float x, float y, float w, float h, float radius, int color) {
        return submitGradient(context, x, y, w, h, radius, color, color, color, color);
    }

    public static boolean submitGradient(
            GuiGraphicsExtractor context,
            float x, float y, float w, float h, float radius,
            int colTL, int colBL, int colBR, int colTR
    ) {
        if (context == null || w <= 0.25f || h <= 0.25f) return false;
        if (((colTL >>> 24) & 0xFF) <= 0 && ((colBL >>> 24) & 0xFF) <= 0
                && ((colBR >>> 24) & 0xFF) <= 0 && ((colTR >>> 24) & 0xFF) <= 0) {
            return true;
        }
        float safeRadius = Mth.clamp(radius, 0.0f, Math.min(2000.0f, Math.min(w, h) * 0.5f));
        return submitInternal(context, x, y, w, h, safeRadius, colTL, colBL, colBR, colTR);
    }

    public static boolean submitOutline(
            GuiGraphicsExtractor context,
            float x, float y, float w, float h, float radius, float thickness, int color
    ) {
        if (context == null || w <= 0.25f || h <= 0.25f) return false;
        if (((color >>> 24) & 0xFF) <= 0) return true;
        int qThick = Math.max(6, Math.min(512, Math.round(thickness * 16.0f)));
        float safeRadius = Mth.clamp(radius, 0.0f, Math.min(2000.0f, Math.min(w, h) * 0.5f));
        float encoded = -(qThick * 2048.0f + safeRadius);
        return submitInternal(context, x, y, w, h, encoded, color, color, color, color);
    }

    private static boolean submitInternal(
            GuiGraphicsExtractor context,
            float x, float y, float w, float h, float encodedParam,
            int colTL, int colBL, int colBR, int colTR
    ) {
        if (!(((Object) context) instanceof OpticumeGuiContext guiCtx)) {
            return false;
        }
        RenderPipeline pl = getPipeline();
        try {
            if (RenderSystem.getCompiledPipelineNullable(pl) == null) {
                return false;
            }
        } catch (Throwable ignored) {
            return false;
        }
        RoundedRectRenderState state = new RoundedRectRenderState(
                x, y, w, h, encodedParam,
                colTL, colBL, colBR, colTR,
                new Matrix3x2f(context.pose()),
                guiCtx.opticume$peekScissor()
        );
        guiCtx.opticume$submitElement(state);
        return true;
    }

    private static RenderPipeline getPipeline() {
        if (pipeline == null) {
            synchronized (RoundedRectRenderState.class) {
                if (pipeline == null) {
                    Identifier shaderId = Identifier.fromNamespaceAndPath("opticumeclient", "core/ui_rounded_rect");
                    pipeline = RenderPipeline.builder()
                            .withLocation(Identifier.fromNamespaceAndPath("opticumeclient", "pipeline/ui_rounded_rect"))
                            .withBindGroupLayout(BindGroupLayouts.GLOBALS)
                            .withBindGroupLayout(BindGroupLayouts.PROJECTION)
                            .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
                            .withVertexShader(shaderId)
                            .withFragmentShader(shaderId)
                            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                            .withCull(false)
                            .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
                            .withVertexBinding(0, FORMAT)
                            .withPrimitiveTopology(PrimitiveTopology.QUADS)
                            .build();
                }
            }
        }
        return pipeline;
    }

    public static void prewarm() {
        try {
            RenderSystem.getCompiledPipelineNullable(getPipeline());
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void buildVertices(VertexConsumer vc) {
        float x0 = x;
        float y0 = y;
        float x1 = x + w;
        float y1 = y + h;
        vc.addVertexWith2DPose(pose, x0, y0).setUv(0.0f, 0.0f).setColor(colTL).setUv3(w, h).setLineWidth(encodedParam);
        vc.addVertexWith2DPose(pose, x0, y1).setUv(0.0f, 1.0f).setColor(colBL).setUv3(w, h).setLineWidth(encodedParam);
        vc.addVertexWith2DPose(pose, x1, y1).setUv(1.0f, 1.0f).setColor(colBR).setUv3(w, h).setLineWidth(encodedParam);
        vc.addVertexWith2DPose(pose, x1, y0).setUv(1.0f, 0.0f).setColor(colTR).setUv3(w, h).setLineWidth(encodedParam);
    }

    @Override
    public RenderPipeline pipeline() {
        return getPipeline();
    }

    @Override
    public TextureSetup textureSetup() {
        return TextureSetup.noTexture();
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
