package dev.opticumeclient.client.render;

import dev.opticumeclient.client.modules.visuals.CustomFontModule;
import dev.opticumeclient.client.render.font.OpticumeFonts;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2fStack;

import java.util.UUID;

public class Render2DHelper {

    public static void drawRect(GuiGraphicsExtractor context, float x, float y, float width, float height, int color) {
        if (width <= 0 || height <= 0) return;
        context.fill(Math.round(x), Math.round(y), Math.round(x + width), Math.round(y + height), color);
    }

    public static void drawRect(GuiGraphicsExtractor context, int x, int y, int width, int height, int color) {
        if (width <= 0 || height <= 0) return;
        context.fill(x, y, x + width, y + height, color);
    }

    public static void drawBorder(GuiGraphicsExtractor context, int x, int y, int width, int height, int thickness, int color) {
        drawRect(context, x, y, width, thickness, color);
        drawRect(context, x, y + height - thickness, width, thickness, color);
        drawRect(context, x, y, thickness, height, color);
        drawRect(context, x + width - thickness, y, thickness, height, color);
    }

    public static void drawLine(GuiGraphicsExtractor context, float x1, float y1, float x2, float y2, float thickness, int color) {
        if (context == null) return;
        float dx = x2 - x1;
        float dy = y2 - y1;
        float len = (float) Math.hypot(dx, dy);
        if (len < 0.25f) return;
        float t = Math.max(0.4f, thickness);
        Matrix3x2fStack matrices = context.pose();
        matrices.pushMatrix();
        matrices.translate(x1, y1);
        matrices.rotate((float) Math.atan2(dy, dx));
        matrices.translate(0.0f, -t * 0.5f);
        matrices.scale(1.0f, t);
        context.fill(0, 0, Math.max(1, Math.round(len)), 1, color);
        matrices.popMatrix();
    }

    public static void drawGradientRect(GuiGraphicsExtractor context, float x, float y, float w, float h, int colorTop, int colorBottom) {
        if (w <= 0 || h <= 0) return;
        context.fillGradient(Math.round(x), Math.round(y), Math.round(x + w), Math.round(y + h), colorTop, colorBottom);
    }

    public static void drawHorizontalGradient(GuiGraphicsExtractor context, float x, float y, float w, float h, int colorLeft, int colorRight) {
        if (w <= 0 || h <= 0) return;
        if (colorLeft == colorRight) {
            context.fill(Math.round(x), Math.round(y), Math.round(x + w), Math.round(y + h), colorLeft);
            return;
        }
        int steps = Math.max(1, Math.min(12, (int) (w / 6.0f)));
        float stepW = w / steps;
        int iy1 = Math.round(y);
        int iy2 = Math.round(y + h);
        for (int i = 0; i < steps; i++) {
            float t = (steps <= 1) ? 0.5f : (float) i / (steps - 1);
            int c = ColorUtils.interpolateColor(colorLeft, colorRight, t);
            int ix1 = Math.round(x + i * stepW);
            int ix2 = Math.round(x + (i + 1) * stepW);
            if (ix2 > ix1) {
                context.fill(ix1, iy1, ix2, iy2, c);
            }
        }
    }

    /**
     * Anti-aliased 9-slice rounded rectangle rendered via 3 solid fill calls (center + sides) and 4 procedural
     * 4x4-supersampled SDF ROUNDED_CORNER_MASK blits for corners (Vulkan/RenderPearl safe, zero jagged pixel notches).
     */
    public static void drawRoundedRect(GuiGraphicsExtractor context, float x, float y, float w, float h, float radius, int color) {
        if (w <= 0 || h <= 0) return;
        int alpha = (color >>> 24) & 0xFF;
        if (alpha == 0) return;

        float r = Math.max(0.0f, Math.min(radius, Math.min(w, h) * 0.5f));
        int ix = Math.round(x);
        int iy = Math.round(y);
        int iw = Math.round(w);
        int ih = Math.round(h);
        int ir = Math.round(r);

        if (ir <= 1) {
            if (ir <= 0) {
                context.fill(ix, iy, ix + iw, iy + ih, color);
                return;
            }
            context.fill(ix + 1, iy, ix + iw - 1, iy + ih, color);
            context.fill(ix, iy + 1, ix + 1, iy + ih - 1, color);
            context.fill(ix + iw - 1, iy + 1, ix + iw, iy + ih - 1, color);
            return;
        }

        TextureAssets.ensureInitialized();
        int cr = TextureAssets.clampCornerRadius(ir);
        int u0 = TextureAssets.getCornerAtlasU(cr);
        int aw = TextureAssets.CORNER_ATLAS_W;
        int ah = TextureAssets.CORNER_ATLAS_H;

        // Central vertical body
        if (iw > ir * 2) {
            context.fill(ix + ir, iy, ix + iw - ir, iy + ih, color);
        }
        // Left bar
        if (ih > ir * 2) {
            context.fill(ix, iy + ir, ix + ir, iy + ih - ir, color);
            // Right bar
            context.fill(ix + iw - ir, iy + ir, ix + iw, iy + ih - ir, color);
        }

        // 4 procedural 4x4-supersampled anti-aliased corners
        try {
            // Top-Left
            context.blit(RenderPipelines.GUI_TEXTURED, TextureAssets.ROUNDED_CORNER_MASK, ix, iy, (float) u0, 0.0f, ir, ir, cr, cr, aw, ah, color);
            // Top-Right
            context.blit(RenderPipelines.GUI_TEXTURED, TextureAssets.ROUNDED_CORNER_MASK, ix + iw - ir, iy, (float) (u0 + cr), 0.0f, ir, ir, cr, cr, aw, ah, color);
            // Bottom-Left
            context.blit(RenderPipelines.GUI_TEXTURED, TextureAssets.ROUNDED_CORNER_MASK, ix, iy + ih - ir, (float) u0, (float) cr, ir, ir, cr, cr, aw, ah, color);
            // Bottom-Right
            context.blit(RenderPipelines.GUI_TEXTURED, TextureAssets.ROUNDED_CORNER_MASK, ix + iw - ir, iy + ih - ir, (float) (u0 + cr), (float) cr, ir, ir, cr, cr, aw, ah, color);
        } catch (Throwable ignored) {
        }
    }

    public static void drawGradientRoundedRect(GuiGraphicsExtractor context, float x, float y, float w, float h, float radius, int colorLeft, int colorRight) {
        if (w <= 0 || h <= 0) return;
        int alphaL = (colorLeft >>> 24) & 0xFF;
        int alphaR = (colorRight >>> 24) & 0xFF;
        if (alphaL == 0 && alphaR == 0) return;

        float r = Math.max(0.0f, Math.min(radius, Math.min(w, h) * 0.5f));
        int ix = Math.round(x);
        int iy = Math.round(y);
        int iw = Math.round(w);
        int ih = Math.round(h);
        int ir = Math.round(r);

        if (ir <= 1 || h <= 2.5f) {
            drawHorizontalGradient(context, x, y, w, h, colorLeft, colorRight);
            return;
        }

        TextureAssets.ensureInitialized();
        int cr = TextureAssets.clampCornerRadius(ir);
        int u0 = TextureAssets.getCornerAtlasU(cr);
        int aw = TextureAssets.CORNER_ATLAS_W;
        int ah = TextureAssets.CORNER_ATLAS_H;

        // Center gradient body
        if (iw > ir * 2) {
            drawHorizontalGradient(context, ix + ir, iy, iw - ir * 2, ih, colorLeft, colorRight);
        }
        // Left & Right bars
        if (ih > ir * 2) {
            context.fill(ix, iy + ir, ix + ir, iy + ih - ir, colorLeft);
            context.fill(ix + iw - ir, iy + ir, ix + iw, iy + ih - ir, colorRight);
        }

        try {
            // Left corners tinted with colorLeft
            context.blit(RenderPipelines.GUI_TEXTURED, TextureAssets.ROUNDED_CORNER_MASK, ix, iy, (float) u0, 0.0f, ir, ir, cr, cr, aw, ah, colorLeft);
            context.blit(RenderPipelines.GUI_TEXTURED, TextureAssets.ROUNDED_CORNER_MASK, ix, iy + ih - ir, (float) u0, (float) cr, ir, ir, cr, cr, aw, ah, colorLeft);
            // Right corners tinted with colorRight
            context.blit(RenderPipelines.GUI_TEXTURED, TextureAssets.ROUNDED_CORNER_MASK, ix + iw - ir, iy, (float) (u0 + cr), 0.0f, ir, ir, cr, cr, aw, ah, colorRight);
            context.blit(RenderPipelines.GUI_TEXTURED, TextureAssets.ROUNDED_CORNER_MASK, ix + iw - ir, iy + ih - ir, (float) (u0 + cr), (float) cr, ir, ir, cr, cr, aw, ah, colorRight);
        } catch (Throwable ignored) {
        }
    }

    public static void drawRoundedOutline(GuiGraphicsExtractor context, float x, float y, float w, float h, float radius, float thickness, int color) {
        if (w <= 0 || h <= 0) return;
        int alpha = (color >>> 24) & 0xFF;
        if (alpha == 0) return;

        int ix = Math.round(x);
        int iy = Math.round(y);
        int iw = Math.round(w);
        int ih = Math.round(h);
        int ir = Math.max(0, Math.min(Math.round(radius), Math.min(iw, ih) / 2));
        int t = Math.max(1, Math.round(thickness));

        if (ir <= 1) {
            context.fill(ix, iy, ix + iw, iy + t, color);
            context.fill(ix, iy + ih - t, ix + iw, iy + ih, color);
            context.fill(ix, iy + t, ix + t, iy + ih - t, color);
            context.fill(ix + iw - t, iy + t, ix + iw, iy + ih - t, color);
            return;
        }

        // 4 straight 1px segments
        if (iw > ir * 2) {
            context.fill(ix + ir, iy, ix + iw - ir, iy + t, color);
            context.fill(ix + ir, iy + ih - t, ix + iw - ir, iy + ih, color);
        }
        if (ih > ir * 2) {
            context.fill(ix, iy + ir, ix + t, iy + ih - ir, color);
            context.fill(ix + iw - t, iy + ir, ix + iw, iy + ih - ir, color);
        }

        TextureAssets.ensureInitialized();
        int cr = TextureAssets.clampCornerRadius(ir);
        int u0 = TextureAssets.getCornerAtlasU(cr);
        int aw = TextureAssets.CORNER_ATLAS_W;
        int ah = TextureAssets.CORNER_ATLAS_H;

        // 4 procedural 4x4-supersampled anti-aliased corner outline blits (ROUNDED_OUTLINE_MASK)
        try {
            // Top-Left
            context.blit(RenderPipelines.GUI_TEXTURED, TextureAssets.ROUNDED_OUTLINE_MASK, ix, iy, (float) u0, 0.0f, ir, ir, cr, cr, aw, ah, color);
            // Top-Right
            context.blit(RenderPipelines.GUI_TEXTURED, TextureAssets.ROUNDED_OUTLINE_MASK, ix + iw - ir, iy, (float) (u0 + cr), 0.0f, ir, ir, cr, cr, aw, ah, color);
            // Bottom-Left
            context.blit(RenderPipelines.GUI_TEXTURED, TextureAssets.ROUNDED_OUTLINE_MASK, ix, iy + ih - ir, (float) u0, (float) cr, ir, ir, cr, cr, aw, ah, color);
            // Bottom-Right
            context.blit(RenderPipelines.GUI_TEXTURED, TextureAssets.ROUNDED_OUTLINE_MASK, ix + iw - ir, iy + ih - ir, (float) (u0 + cr), (float) cr, ir, ir, cr, cr, aw, ah, color);
        } catch (Throwable ignored) {
        }
    }

    public static void drawCircle(GuiGraphicsExtractor context, float cx, float cy, float radius, int color) {
        drawRoundedRect(context, cx - radius, cy - radius, radius * 2.0f, radius * 2.0f, radius, color);
    }

    /**
     * Soft Gaussian Bloom / DropShadow using procedural GLOW_RADIAL texture.
     */
    public static void drawGlow(GuiGraphicsExtractor context, float x, float y, float w, float h, float spread, int glowColor) {
        int baseAlpha = (glowColor >>> 24) & 0xFF;
        if (baseAlpha <= 2 || spread <= 0.5f) return;

        TextureAssets.ensureInitialized();
        float expand = spread * 1.5f;
        int haloColor = ColorUtils.withAlphaFactor(glowColor, 0.55f);
        drawTexture(context, TextureAssets.GLOW_RADIAL, x - expand, y - expand, w + expand * 2.0f, h + expand * 2.0f, haloColor);
    }

    /**
     * Submits a true blurred Liquid Glass rounded rectangle via {@link BlurredRectRenderState}
     * sampling {@link KawaseBlurRenderer}. Falls back to a solid translucent rounded rect if
     * the backdrop buffer is unavailable.
     */
    public static void drawBlurredRoundedRect(
            GuiGraphicsExtractor context,
            float x, float y, float w, float h, float radius,
            int tintArgb, float overallAlpha
    ) {
        if (BlurredRectRenderState.trySubmit(context, x, y, w, h, radius, tintArgb, overallAlpha)) {
            return;
        }
        int baseA = (tintArgb >>> 24) & 0xFF;
        int fallbackA = Math.max(0, Math.min(255, Math.round(baseA * overallAlpha)));
        drawRoundedRectFloat(context, x, y, w, h, radius, ColorUtils.withAlpha(tintArgb, fallbackA));
    }

    public static void drawRoundedRectFloat(GuiGraphicsExtractor context, float x, float y, float w, float h, float radius, int color) {
        if (RoundedRectRenderState.submitFill(context, x, y, w, h, radius, color)) {
            return;
        }
        drawRoundedRect(context, x, y, w, h, radius, color);
    }

    public static void drawGradientRoundedRectFloat(
            GuiGraphicsExtractor context,
            float x, float y, float w, float h, float radius,
            int colTL, int colBL, int colBR, int colTR
    ) {
        if (RoundedRectRenderState.submitGradient(context, x, y, w, h, radius, colTL, colBL, colBR, colTR)) {
            return;
        }
        drawGradientRoundedRect(context, x, y, w, h, radius, colTL, colTR);
    }

    public static void drawRoundedOutlineFloat(
            GuiGraphicsExtractor context,
            float x, float y, float w, float h, float radius, float thickness, int color
    ) {
        if (RoundedRectRenderState.submitOutline(context, x, y, w, h, radius, thickness, color)) {
            return;
        }
        drawRoundedOutline(context, x, y, w, h, radius, thickness, color);
    }

    /**
     * Nursultan Nextgen & Catlavan 4-Layer Liquid Glass Card:
     * 1. Outer ambient theme glow (only when glowAlpha >= 45 to preserve high FPS)
     * 2. Deep frosted dark glass fill
     * 3. Top inner highlight bevel
     * 4. 1px gradient glass border with anti-aliased SDF corners
     */
    public static void drawLiquidGlass(GuiGraphicsExtractor context, float x, float y, float w, float h, float radius, int baseAlpha, int glowAlpha) {
        dev.opticumeclient.client.hud.HudPanelRenderer.drawSurface(context, x, y, w, h, radius, baseAlpha, glowAlpha);
    }

    public static void drawGlassCard(GuiGraphicsExtractor context, int x, int y, int width, int height, int accentColor) {
        drawLiquidGlass(context, x, y, width, height, 6.0f, 215, 38);
    }

    public static void drawSpecularBorder(GuiGraphicsExtractor context, float x, float y, float w, float h, float radius, float mouseX, float mouseY, int accentColor) {
        float cx = Math.max(x, Math.min(x + w, mouseX));
        float cy = Math.max(y, Math.min(y + h, mouseY));
        float dist = (float) Math.hypot(mouseX - cx, mouseY - cy);
        if (dist > 95.0f) return;

        float intensity = 1.0f - (dist / 95.0f);
        int alpha = (int) (intensity * intensity * 150.0f);
        if (alpha > 5) {
            drawRoundedOutline(context, x, y, w, h, radius, 1.0f, ColorUtils.withAlpha(accentColor, alpha));
        }
    }

    public static void drawActiveCardIndicator(GuiGraphicsExtractor context, float x, float y, float h, float progress, int colorTop, int colorBot) {
        if (progress <= 0.02f) return;
        float barH = Math.max(4.0f, (h - 10.0f) * progress);
        float barY = y + (h - barH) * 0.5f;
        int c = ColorUtils.withAlpha(colorTop, (int) (255 * progress));
        drawGlow(context, x, barY, 2.5f, barH, 5.0f, ColorUtils.withAlpha(colorTop, (int) (120 * progress)));
        drawRoundedRect(context, x, barY, 2.5f, barH, 1.2f, c);
    }

    public static void drawTexture(GuiGraphicsExtractor context, Identifier texture, float x, float y, float width, float height, int color) {
        int ix = Math.round(x);
        int iy = Math.round(y);
        int iw = Math.max(1, Math.round(width));
        int ih = Math.max(1, Math.round(height));
        try {
            context.blit(RenderPipelines.GUI_TEXTURED, texture, ix, iy, 0.0f, 0.0f, iw, ih, iw, ih, color);
        } catch (Throwable ignored) {
        }
    }

    /**
     * Sub-pixel floating-point texture blit without integer position/size rounding.
     */
    public static void drawTextureFloat(GuiGraphicsExtractor context, Identifier texture, float x, float y, float width, float height, int color) {
        if (context == null || texture == null || width <= 0.1f || height <= 0.1f) return;
        final int texVirtual = 128;
        try {
            Matrix3x2fStack pose = context.pose();
            pose.pushMatrix();
            pose.translate(x, y);
            pose.scale(width / texVirtual, height / texVirtual);
            context.blit(RenderPipelines.GUI_TEXTURED, texture, 0, 0, 0.0f, 0.0f, texVirtual, texVirtual, texVirtual, texVirtual, color);
            pose.popMatrix();
        } catch (Throwable ignored) {
        }
    }

    public static void drawRoundedTextureFloat(
            GuiGraphicsExtractor context, Identifier texture,
            float x, float y, float width, float height, float radius, int color
    ) {
        drawRoundedCroppedTextureFloat(context, texture, x, y, width, height, radius, 0.0f, 0.0f, 1.0f, 1.0f, color);
    }

    public static void drawRoundedCroppedTextureFloat(
            GuiGraphicsExtractor context, Identifier texture,
            float x, float y, float width, float height, float radius,
            float u, float v, float uw, float uh, int color
    ) {
        if (RoundedTextureRenderState.submit(context, texture, x, y, width, height, radius, u, v, uw, uh, color)) {
            return;
        }
        drawCroppedTextureFloatFallback(context, texture, x, y, width, height, u, v, uw, uh, color);
    }

    public static void drawCroppedTextureFloat(
            GuiGraphicsExtractor context, Identifier texture,
            float x, float y, float width, float height,
            float u, float v, float uw, float uh, int color
    ) {
        if (RoundedTextureRenderState.submit(context, texture, x, y, width, height, 0.0f, u, v, uw, uh, color)) {
            return;
        }
        drawCroppedTextureFloatFallback(context, texture, x, y, width, height, u, v, uw, uh, color);
    }

    private static void drawCroppedTextureFloatFallback(
            GuiGraphicsExtractor context, Identifier texture,
            float x, float y, float width, float height,
            float u, float v, float uw, float uh, int color
    ) {
        if (context == null || texture == null || width <= 0.1f || height <= 0.1f) return;
        float safeUw = Math.max(0.001f, uw);
        float safeUh = Math.max(0.001f, uh);
        final int virtualRegion = 256;
        int fullW = Math.max(1, Math.round(virtualRegion / safeUw));
        int fullH = Math.max(1, Math.round(virtualRegion / safeUh));
        try {
            Matrix3x2fStack pose = context.pose();
            pose.pushMatrix();
            pose.translate(x, y);
            pose.scale(width / virtualRegion, height / virtualRegion);
            context.blit(RenderPipelines.GUI_TEXTURED, texture, 0, 0, u * fullW, v * fullH, virtualRegion, virtualRegion, fullW, fullH, color);
            pose.popMatrix();
        } catch (Throwable ignored) {
        }
    }

    public static void drawTextureRegion(GuiGraphicsExtractor context, Identifier texture, float x, float y, float width, float height,
                                         float u, float v, int srcW, int srcH, int texW, int texH, int color) {
        int ix = Math.round(x);
        int iy = Math.round(y);
        int iw = Math.max(1, Math.round(width));
        int ih = Math.max(1, Math.round(height));
        try {
            context.blit(RenderPipelines.GUI_TEXTURED, texture, ix, iy, u, v, iw, ih, srcW, srcH, texW, texH, color);
        } catch (Throwable ignored) {
        }
    }

    public static void drawCroppedTexture(GuiGraphicsExtractor context, Identifier texture, float x, float y, float width, float height, float u, float v, float uw, float uh, int color) {
        if (RoundedTextureRenderState.submit(context, texture, x, y, width, height, 0.0f, u, v, uw, uh, color)) {
            return;
        }
        int ix = Math.round(x);
        int iy = Math.round(y);
        int iw = Math.max(1, Math.round(width));
        int ih = Math.max(1, Math.round(height));
        float safeUw = Math.max(0.001f, uw);
        float safeUh = Math.max(0.001f, uh);
        int fullW = Math.max(1, Math.round(iw / safeUw));
        int fullH = Math.max(1, Math.round(ih / safeUh));
        try {
            context.blit(RenderPipelines.GUI_TEXTURED, texture, ix, iy, u * fullW, v * fullH, iw, ih, fullW, fullH, color);
        } catch (Throwable ignored) {
        }
    }

    public static void drawPlayerHeadByUuid(GuiGraphicsExtractor context, UUID uuid, int x, int y, int size, float alpha) {
        if (uuid == null) return;
        try {
            int a = Math.max(0, Math.min(255, Math.round(alpha * 255.0f)));
            int tint = ColorUtils.rgba(255, 255, 255, a);
            PlayerFaceExtractor.extractRenderState(context, DefaultPlayerSkin.get(uuid), x, y, size, tint);
        } catch (Throwable t) {
            drawRoundedRect(context, x, y, size, size, 3.0f, ThemeManager.getInstance().getAccentColor(0));
        }
    }

    public static void drawRoundedPlayerHeadByUuid(GuiGraphicsExtractor context, UUID uuid, int x, int y, int size, float radius, int maskBgColor, int borderColor, float alpha) {
        drawPlayerHeadByUuid(context, uuid, x, y, size, alpha);
        drawRoundedCornerCutout(context, x, y, size, size, radius, maskBgColor);
        if (((borderColor >>> 24) & 0xFF) > 0) {
            drawRoundedOutline(context, x, y, size, size, radius, 1.0f, borderColor);
        }
    }

    public static void drawRoundedCornerCutout(GuiGraphicsExtractor context, float x, float y, float w, float h, float radius, int maskBgColor) {
        if (w <= 0 || h <= 0) return;
        int alpha = (maskBgColor >>> 24) & 0xFF;
        if (alpha == 0) return;
        int ix = Math.round(x);
        int iy = Math.round(y);
        int iw = Math.round(w);
        int ih = Math.round(h);
        int ir = Math.max(1, Math.min(Math.round(radius), Math.min(iw, ih) / 2));

        TextureAssets.ensureInitialized();
        int cr = TextureAssets.clampCornerRadius(ir);
        int u0 = TextureAssets.getCornerAtlasU(cr);
        int aw = TextureAssets.CORNER_ATLAS_W;
        int ah = TextureAssets.CORNER_ATLAS_H;

        try {
            context.blit(RenderPipelines.GUI_TEXTURED, TextureAssets.ROUNDED_INVERSE_CORNER_MASK, ix, iy, (float) u0, 0.0f, ir, ir, cr, cr, aw, ah, maskBgColor);
            context.blit(RenderPipelines.GUI_TEXTURED, TextureAssets.ROUNDED_INVERSE_CORNER_MASK, ix + iw - ir, iy, (float) (u0 + cr), 0.0f, ir, ir, cr, cr, aw, ah, maskBgColor);
            context.blit(RenderPipelines.GUI_TEXTURED, TextureAssets.ROUNDED_INVERSE_CORNER_MASK, ix, iy + ih - ir, (float) u0, (float) cr, ir, ir, cr, cr, aw, ah, maskBgColor);
            context.blit(RenderPipelines.GUI_TEXTURED, TextureAssets.ROUNDED_INVERSE_CORNER_MASK, ix + iw - ir, iy + ih - ir, (float) (u0 + cr), (float) cr, ir, ir, cr, cr, aw, ah, maskBgColor);
        } catch (Throwable ignored) {
        }
    }

    public static void drawPlayerHead(GuiGraphicsExtractor context, AbstractClientPlayer player, int x, int y, int size, float hurtProgress) {
        if (player == null) return;
        try {
            int g = Math.max(70, Math.min(255, (int) (255 * (1.0f - hurtProgress * 0.65f))));
            int tint = ColorUtils.rgba(255, g, g, 255);
            PlayerFaceExtractor.extractRenderState(context, player.getSkin(), x, y, size, tint);
        } catch (Throwable t) {
            drawRoundedRect(context, x, y, size, size, 4.0f, ThemeManager.getInstance().getAccentColor(0));
        }
    }

    public static void drawScaledText(GuiGraphicsExtractor context, String text, float x, float y, int color, float scale, boolean shadow) {
        if (text == null || text.isEmpty()) return;
        CustomFontModule customFont = CustomFontModule.getInstance();
        float fontScale = (customFont != null && customFont.isEnabled()) ? customFont.getFontScale() : 1.0f;
        float effectiveScale = scale * fontScale;
        boolean actualShadow = shadow && (customFont == null || customFont.isCustomShadow());
        String style = (customFont != null && customFont.isEnabled()) ? customFont.getFontStyle() : "Vanilla";

        if (OpticumeFonts.tryRender(context, style, text, x, y, effectiveScale, color, actualShadow)) {
            return;
        }

        Font tr = Minecraft.getInstance().font;
        if (tr == null) return;
        if (Math.abs(effectiveScale - 1.0f) < 0.01f) {
            context.text(tr, text, Math.round(x), Math.round(y), color, actualShadow);
            return;
        }
        Matrix3x2fStack matrices = context.pose();
        matrices.pushMatrix();
        matrices.translate(x, y);
        matrices.scale(effectiveScale, effectiveScale);
        context.text(tr, text, 0, 0, color, actualShadow);
        matrices.popMatrix();
    }

    public static void drawText(GuiGraphicsExtractor context, String text, float x, float y, int color, boolean shadow) {
        drawScaledText(context, text, x, y, color, 1.0f, shadow);
    }

    public static void drawCenteredText(GuiGraphicsExtractor context, String text, float centerX, float y, int color, boolean shadow) {
        if (text == null || text.isEmpty()) return;
        float w = getTextWidth(text, 1.0f);
        drawScaledText(context, text, centerX - w * 0.5f, y, color, 1.0f, shadow);
    }

    public static float getTextWidth(String text, float scale) {
        if (text == null || text.isEmpty()) return 0.0f;
        CustomFontModule customFont = CustomFontModule.getInstance();
        float fontScale = (customFont != null && customFont.isEnabled()) ? customFont.getFontScale() : 1.0f;
        float effectiveScale = scale * fontScale;
        String style = (customFont != null && customFont.isEnabled()) ? customFont.getFontStyle() : "Vanilla";

        float customW = OpticumeFonts.getWidth(style, text, effectiveScale);
        if (customW >= 0.0f) {
            return customW;
        }

        Font tr = Minecraft.getInstance().font;
        if (tr == null) return 0.0f;
        return tr.width(text) * effectiveScale;
    }

    public static String trimToWidth(String text, float scale, float maxWidth) {
        if (text == null || text.isEmpty()) return "";
        if (getTextWidth(text, scale) <= maxWidth) return text;
        final String dots = "..";
        float budget = maxWidth - getTextWidth(dots, scale);
        if (budget <= 0.0f) return dots;
        int lo = 0;
        int hi = text.length();
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if (getTextWidth(text.substring(0, mid), scale) <= budget) {
                lo = mid;
            } else {
                hi = mid - 1;
            }
        }
        return lo <= 0 ? dots : text.substring(0, lo) + dots;
    }

    public static void drawString(GuiGraphicsExtractor context, Font textRenderer, String text, int x, int y, int color, boolean shadow) {
        if (text == null || text.isEmpty()) return;
        if (textRenderer == null || textRenderer == Minecraft.getInstance().font) {
            drawScaledText(context, text, x, y, color, 1.0f, shadow);
            return;
        }
        context.text(textRenderer, text, x, y, color, shadow);
    }

    public static void drawCenteredString(GuiGraphicsExtractor context, Font textRenderer, String text, int centerX, int y, int color) {
        if (text == null || text.isEmpty()) return;
        if (textRenderer == null || textRenderer == Minecraft.getInstance().font) {
            drawCenteredText(context, text, centerX, y, color, true);
            return;
        }
        int w = textRenderer.width(text);
        context.text(textRenderer, text, centerX - w / 2, y, color, true);
    }
}
