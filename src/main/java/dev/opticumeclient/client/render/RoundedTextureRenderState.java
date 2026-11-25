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
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.joml.Matrix3x2f;

/**
 * GuiElementRenderState rendering arbitrary cropped textures with anti-aliased SDF rounded corners
 * and bilinear {@link FilterMode#LINEAR} filtering inside MC 26.3 GuiRenderer.
 */
public final class RoundedTextureRenderState implements GuiElementRenderState {
    private static final VertexFormat FORMAT = VertexFormat.builder(0)
            .addAttribute(DefaultVertexFormat.POSITION_SEMANTIC_NAME, GpuFormat.RGB32_FLOAT)
            .addAttribute(DefaultVertexFormat.UV0_SEMANTIC_NAME, GpuFormat.RG32_FLOAT)
            .addAttribute(DefaultVertexFormat.COLOR_SEMANTIC_NAME, GpuFormat.RGBA8_UNORM)
            .addAttribute(DefaultVertexFormat.UV3_SEMANTIC_NAME, GpuFormat.RG32_FLOAT)
            .addAttribute(DefaultVertexFormat.NORMAL_SEMANTIC_NAME, GpuFormat.RGBA8_SNORM)
            .addAttribute(DefaultVertexFormat.LINE_WIDTH_SEMANTIC_NAME, GpuFormat.R32_FLOAT)
            .build();

    private static volatile RenderPipeline pipeline;
    private static volatile boolean pipelineWarned = false;

    private final GpuTextureView textureView;
    private final float x;
    private final float y;
    private final float w;
    private final float h;
    private final float radius;
    private final float u0;
    private final float v0;
    private final float u1;
    private final float v1;
    private final int color;
    private final Matrix3x2f pose;
    private final ScreenRectangle scissorArea;
    private ScreenRectangle cachedBounds;
    private boolean boundsComputed;

    private RoundedTextureRenderState(
            GpuTextureView textureView,
            float x,
            float y,
            float w,
            float h,
            float radius,
            float u0,
            float v0,
            float u1,
            float v1,
            int color,
            Matrix3x2f pose,
            ScreenRectangle scissorArea
    ) {
        this.textureView = textureView;
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        this.radius = Mth.clamp(radius, 0.0f, Math.min(w, h) * 0.5f);
        this.u0 = u0;
        this.v0 = v0;
        this.u1 = u1;
        this.v1 = v1;
        this.color = color;
        this.pose = pose;
        this.scissorArea = scissorArea;
    }

    public static boolean submit(
            GuiGraphicsExtractor context,
            Identifier textureId,
            float x,
            float y,
            float w,
            float h,
            float radius,
            float u,
            float v,
            float uWidth,
            float vHeight,
            int color
    ) {
        if (context == null || textureId == null || w <= 0.25f || h <= 0.25f) {
            return false;
        }
        if (((color >>> 24) & 0xFF) <= 0) {
            return true;
        }
        if (!(((Object) context) instanceof OpticumeGuiContext guiCtx)) {
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getTextureManager() == null) {
            return false;
        }
        AbstractTexture absTex;
        try {
            absTex = mc.getTextureManager().getTexture(textureId);
        } catch (Throwable ignored) {
            return false;
        }
        GpuTextureView view = absTex != null ? absTex.getTextureView() : null;
        if (view == null) {
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
                System.err.println("[RoundedTextureRenderState] Pipeline compile failed: " + t);
            }
            return false;
        }

        RoundedTextureRenderState state = new RoundedTextureRenderState(
                view,
                x, y, w, h,
                radius,
                u, v, u + uWidth, v + vHeight,
                color,
                new Matrix3x2f(context.pose()),
                guiCtx.opticume$peekScissor()
        );
        guiCtx.opticume$submitElement(state);
        return true;
    }

    private static RenderPipeline getPipeline() {
        if (pipeline == null) {
            synchronized (RoundedTextureRenderState.class) {
                if (pipeline == null) {
                    Identifier shaderId = Identifier.fromNamespaceAndPath("opticumeclient", "core/ui_rounded_texture");
                    pipeline = RenderPipeline.builder()
                            .withLocation(Identifier.fromNamespaceAndPath("opticumeclient", "pipeline/ui_rounded_texture"))
                            .withBindGroupLayout(BindGroupLayouts.GLOBALS)
                            .withBindGroupLayout(BindGroupLayouts.PROJECTION)
                            .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
                            .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
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

    @Override
    public void buildVertices(VertexConsumer vc) {
        float x0 = x;
        float y0 = y;
        float x1 = x + w;
        float y1 = y + h;
        vc.addVertexWith2DPose(pose, x0, y0).setUv(u0, v0).setColor(color).setUv3(w, h).setNormal(0.0f, 0.0f, 0.0f).setLineWidth(radius);
        vc.addVertexWith2DPose(pose, x0, y1).setUv(u0, v1).setColor(color).setUv3(w, h).setNormal(0.0f, 1.0f, 0.0f).setLineWidth(radius);
        vc.addVertexWith2DPose(pose, x1, y1).setUv(u1, v1).setColor(color).setUv3(w, h).setNormal(1.0f, 1.0f, 0.0f).setLineWidth(radius);
        vc.addVertexWith2DPose(pose, x1, y0).setUv(u1, v0).setColor(color).setUv3(w, h).setNormal(1.0f, 0.0f, 0.0f).setLineWidth(radius);
    }

    @Override
    public RenderPipeline pipeline() {
        return getPipeline();
    }

    @Override
    public TextureSetup textureSetup() {
        GpuSampler sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        return TextureSetup.singleTexture(textureView, sampler);
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
