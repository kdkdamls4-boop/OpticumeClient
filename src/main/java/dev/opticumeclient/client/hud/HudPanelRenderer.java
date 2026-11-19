package dev.opticumeclient.client.hud;

import dev.opticumeclient.client.modules.hud.InterfaceModule;
import dev.opticumeclient.client.render.ColorUtils;
import dev.opticumeclient.client.render.KawaseBlurRenderer;
import dev.opticumeclient.client.render.Render2DHelper;
import dev.opticumeclient.client.render.TextureAssets;
import dev.opticumeclient.client.render.Theme;
import dev.opticumeclient.client.render.ThemeManager;
import dev.opticumeclient.client.render.font.OpticumeFonts;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

/**
 * Desire {@code RelicStyle} + {@code HudPanelStyle} renderer ported to MC 26.3 float SDF + Kawase Blur:
 * supports real Liquid Glass backdrop blur vs crisp Solid Relic cards, smooth custom corner radii,
 * divided headers with glowing icons, and floating widget context menus.
 */
public final class HudPanelRenderer {
    private static String currentWidgetScope = null;

    private HudPanelRenderer() {
    }

    public static void beginWidget(String widgetName) {
        currentWidgetScope = widgetName;
    }

    public static void endWidget() {
        currentWidgetScope = null;
    }

    public static String getCurrentWidgetScope() {
        return currentWidgetScope;
    }

    public static void drawSurface(GuiGraphicsExtractor context, float x, float y, float w, float h,
                                   float baseRadius, int baseAlpha, int glowAlpha) {
        drawSurface(context, currentWidgetScope, x, y, w, h, baseRadius, baseAlpha, glowAlpha);
    }

    public static void drawSurface(GuiGraphicsExtractor context, String widgetName,
                                   float x, float y, float w, float h,
                                   float baseRadius, int baseAlpha, int glowAlpha) {
        if (w <= 0.5f || h <= 0.5f) return;

        InterfaceModule iface = InterfaceModule.getInstance();
        boolean glass = iface == null || iface.isEffectiveLiquidGlass(widgetName);
        boolean relic = iface == null || iface.isRelicDesign();
        float radius = iface != null ? iface.getEffectiveRadius(baseRadius) : baseRadius;
        radius = Math.min(radius, Math.min(w, h) * 0.48f);

        Theme theme = ThemeManager.getInstance().getCurrentTheme();
        int accent1 = theme.getPrimaryColor();
        int accent2 = theme.getSecondaryColor();
        float alphaFrac = Math.max(0.0f, Math.min(1.0f, baseAlpha / 235.0f));
        if (alphaFrac <= 0.01f) return;

        if (glass) {
            int passes = iface != null ? iface.getBlurPasses() : 3;
            float offset = iface != null ? iface.getBlurOffset() : 2.0f;
            KawaseBlurRenderer.requestWorldBlur(passes, offset);

            // 1. Soft dark elevation shadow + optional accent halo
            Render2DHelper.drawGlow(context, x, y + 1.5f, w, h, 5.5f,
                    ColorUtils.rgba(0, 0, 0, Math.round(92.0f * alphaFrac)));
            if (glowAlpha >= 42) {
                Render2DHelper.drawGlow(context, x, y, w, h, 6.0f,
                        ColorUtils.withAlpha(accent1, Math.round(glowAlpha * 0.72f)));
            }

            // 2. Real Gaussian/Kawase backdrop blur clipped tightly to float SDF rounded box
            int glassOpacity = iface != null ? iface.getGlassOpacityAlpha() : 150;
            int blurFillTint = ColorUtils.withAlpha(theme.getCardColor(), Math.round(glassOpacity * 0.35f));
            Render2DHelper.drawBlurredRoundedRect(context, x, y, w, h, radius, blurFillTint, alphaFrac);

            // 3. Translucent tinted glass body layer
            int glassTint = ColorUtils.withAlpha(
                    ColorUtils.interpolateColor(0xFF0E0F15, theme.getCardColor(), 0.42f),
                    Math.round(glassOpacity * alphaFrac)
            );
            Render2DHelper.drawRoundedRectFloat(context, x, y, w, h, radius, glassTint);

            // 4. Inner top specular highlight bevel + smooth float SDF rim
            if (w > radius * 2.0f + 4.0f) {
                Render2DHelper.drawRect(context, x + radius + 1.0f, y + 0.8f, w - radius * 2.0f - 2.0f, 0.8f,
                        ColorUtils.rgba(255, 255, 255, Math.round(32.0f * alphaFrac)));
            }
            int outlineCol = relic
                    ? ColorUtils.rgba(255, 255, 255, Math.round(36.0f * alphaFrac))
                    : ColorUtils.withAlpha(ColorUtils.interpolateColor(accent1, accent2, 0.5f),
                    Math.round(Math.min(130.0f, glowAlpha + 44.0f) * alphaFrac));
            Render2DHelper.drawRoundedOutlineFloat(context, x, y, w, h, radius, 0.85f, outlineCol);
        } else {
            // Crisp Solid Obsidian / Relic panel
            if (glowAlpha >= 46) {
                Render2DHelper.drawGlow(context, x, y, w, h, 6.0f,
                        ColorUtils.withAlpha(accent1, Math.round(glowAlpha * 0.65f)));
            }
            int fillCol = relic
                    ? ColorUtils.rgba(20, 21, 23, Math.round(244.0f * alphaFrac))
                    : ColorUtils.withAlpha(theme.getCardColor(), Math.round(240.0f * alphaFrac));
            int borderCol = relic
                    ? ColorUtils.rgba(42, 43, 48, Math.round(245.0f * alphaFrac))
                    : ColorUtils.withAlpha(ColorUtils.interpolateColor(accent1, accent2, 0.5f),
                    Math.round(Math.min(125.0f, glowAlpha + 40.0f) * alphaFrac));
            Render2DHelper.drawRoundedRectFloat(context, x, y, w, h, radius, fillCol);
            Render2DHelper.drawRoundedOutlineFloat(context, x, y, w, h, radius, 0.9f, borderCol);
        }
    }

    public static void drawHeaderPanel(GuiGraphicsExtractor context, String widgetName,
                                       float x, float y, float w, float h, float headerH,
                                       float baseRadius, Identifier iconTex, String title, float alpha) {
        drawSurface(context, widgetName, x, y, w, h, baseRadius, Math.round(235 * alpha), 38);

        InterfaceModule iface = InterfaceModule.getInstance();
        float radius = iface != null ? iface.getEffectiveRadius(baseRadius) : baseRadius;
        radius = Math.min(radius, Math.min(w, h) * 0.48f);
        boolean relic = iface == null || iface.isRelicDesign();

        // Darkened top header band clipped to headerH
        context.enableScissor(Math.round(x), Math.round(y), Math.round(x + w), Math.round(y + headerH));
        Render2DHelper.drawRoundedRectFloat(context, x + 0.5f, y + 0.5f, w - 1.0f, headerH + radius, radius,
                ColorUtils.rgba(8, 9, 13, Math.round((relic ? 96.0f : 65.0f) * alpha)));
        context.disableScissor();

        // Hairline separator below header
        Theme theme = ThemeManager.getInstance().getCurrentTheme();
        int c1 = theme.getPrimaryColor();
        int c2 = theme.getSecondaryColor();
        if (relic) {
            Render2DHelper.drawRect(context, x + 1.0f, y + headerH - 0.5f, w - 2.0f, 0.8f,
                    ColorUtils.rgba(255, 255, 255, Math.round(24.0f * alpha)));
        } else {
            Render2DHelper.drawGradientRoundedRectFloat(context, x + 6.0f, y + headerH - 1.2f, w - 12.0f, 1.2f, 0.6f,
                    ColorUtils.withAlphaFactor(c1, alpha), ColorUtils.withAlphaFactor(c1, alpha),
                    ColorUtils.withAlphaFactor(c2, alpha), ColorUtils.withAlphaFactor(c2, alpha));
        }

        // Icon with radial accent halo + pill divider + SEMIBOLD title
        float cy = y + headerH * 0.5f;
        float iconS = 8.5f;
        float iconX = x + 7.5f;
        if (iconTex != null) {
            drawGlowIcon(context, iconTex, iconX, cy, iconS, c1, alpha);
            iconX += iconS + 5.5f;
            Render2DHelper.drawRoundedRectFloat(context, iconX, cy - 2.5f, 1.6f, 5.0f, 0.8f,
                    ColorUtils.rgba(70, 72, 80, Math.round(210.0f * alpha)));
            iconX += 5.5f;
        }
        drawHudText(context, OpticumeFonts.KEY_HUD_SEMIBOLD, title, iconX, cy - 4.0f, 8.0f,
                ColorUtils.rgba(232, 235, 244, Math.round(245.0f * alpha)));
    }

    public static void drawGlowIcon(GuiGraphicsExtractor context, Identifier tex, float x, float centerY, float size,
                                    int accentColor, float alpha) {
        Render2DHelper.drawTextureFloat(context, TextureAssets.DESIRE_GLOW,
                x - size * 0.55f, centerY - size * 0.9f, size * 2.1f, size * 1.8f,
                ColorUtils.withAlpha(accentColor, Math.round(44.0f * alpha)));
        Render2DHelper.drawTextureFloat(context, tex, x, centerY - size * 0.5f, size, size,
                ColorUtils.withAlphaFactor(accentColor, alpha));
    }

    public static float measureHud(String fontKey, String text, float pxSize) {
        if (text == null || text.isEmpty()) return 0.0f;
        float w = OpticumeFonts.getDirectWidth(fontKey, text, pxSize);
        return w > 0.0f ? w : Render2DHelper.getTextWidth(text, pxSize / 9.0f);
    }

    public static void drawHudText(GuiGraphicsExtractor context, String fontKey, String text,
                                   float x, float y, float pxSize, int color) {
        if (!OpticumeFonts.drawDirect(context, fontKey, text, x, y, pxSize, color)) {
            Render2DHelper.drawScaledText(context, text, x, y + 0.5f, color, pxSize / 9.0f, false);
        }
    }
}
