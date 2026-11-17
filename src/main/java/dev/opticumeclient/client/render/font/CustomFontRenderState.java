package dev.opticumeclient.client.render.font;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.util.Mth;
import org.joml.Matrix3x2f;

public final class CustomFontRenderState implements GuiElementRenderState {
    private final MsdfFontFace face;
    private final GpuTextureView textureView;
    private final String text;
    private final float x;
    private final float y;
    private final float pxSize;
    private final int color;
    private final boolean shadow;
    private final Matrix3x2f pose;
    private final ScreenRectangle scissorArea;
    private ScreenRectangle cachedBounds;
    private boolean boundsComputed;

    public CustomFontRenderState(
            MsdfFontFace face,
            GpuTextureView textureView,
            String text,
            float x,
            float y,
            float pxSize,
            int color,
            boolean shadow,
            Matrix3x2f pose,
            ScreenRectangle scissorArea) {
        this.face = face;
        this.textureView = textureView;
        this.text = text;
        this.x = x;
        this.y = y;
        this.pxSize = pxSize;
        this.color = color;
        this.shadow = shadow;
        this.pose = pose;
        this.scissorArea = scissorArea;
    }

    @Override
    public void buildVertices(VertexConsumer vertexConsumer) {
        face.emitText(vertexConsumer, pose, text, x, y, pxSize, color, shadow);
    }

    @Override
    public RenderPipeline pipeline() {
        return face.getPipeline();
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
            float w = face.getWidth(text, pxSize);
            float h = Math.max(pxSize * face.getLineHeight(), pxSize);
            int rx = Mth.floor(x - 2.0f);
            int ry = Mth.floor(y - 2.0f);
            int rw = Math.max(1, Mth.ceil(w + 5.0f));
            int rh = Math.max(1, Mth.ceil(h + 5.0f));
            ScreenRectangle rect = new ScreenRectangle(rx, ry, rw, rh).transformMaxBounds(pose);
            cachedBounds = scissorArea != null ? rect.intersection(scissorArea) : rect;
            boundsComputed = true;
        }
        return cachedBounds;
    }
}
