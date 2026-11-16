package dev.opticumeclient.client.ui.menu;

import dev.opticumeclient.client.render.ColorUtils;
import dev.opticumeclient.client.render.Render2DHelper;
import dev.opticumeclient.client.render.ThemeManager;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;

import java.util.WeakHashMap;

/**
 * Shared Desire glass pill/button and slider renderer used both in OpticumeClient's custom
 * menus and globally across vanilla Minecraft screens via button/slider mixins.
 */
public final class OpticumeButtonStyle {
    private static final WeakHashMap<Object, AnimState> WIDGET_STATES = new WeakHashMap<>();

    private static final class AnimState {
        float hover;
        float sheen;
        boolean wasHovered;
        long lastNanos = System.nanoTime();
    }

    private OpticumeButtonStyle() {
    }

    private static float computeDt(AnimState st) {
        long now = System.nanoTime();
        float dt = Math.min(0.05f, Math.max(0.0001f, (now - st.lastNanos) / 1.0e9f));
        st.lastNanos = now;
        return dt;
    }

    /**
     * Renders the Desire glass background for any vanilla {@code AbstractButton} via
     * {@link dev.opticumeclient.mixin.AbstractButtonMixin}.
     */
    public static void renderVanillaButton(
            GuiGraphicsExtractor context,
            Object key,
            int x, int y, int width, int height,
            boolean active, boolean hoveredOrFocused, float alpha
    ) {
        if (width <= 0 || height <= 0 || alpha <= 0.01f) return;

        AnimState st = WIDGET_STATES.computeIfAbsent(key, k -> new AnimState());
        float dt = computeDt(st);
        boolean hovered = active && hoveredOrFocused;
        if (hovered && !st.wasHovered) {
            st.sheen = 0.001f;
        }
        st.wasHovered = hovered;
        float speed = hovered ? 16.0f : 11.0f;
        st.hover += ((hovered ? 1.0f : 0.0f) - st.hover) * (1.0f - (float) Math.exp(-speed * dt));
        if (st.sheen > 0.0f) {
            st.sheen += dt / 0.55f;
            if (st.sheen >= 1.0f) {
                st.sheen = 0.0f;
            }
        }

        float hv = Mth.clamp(st.hover, 0.0f, 1.0f);
        float radius = Math.min(8.0f, Math.min(width, height) * 0.36f);
        int accent = ThemeManager.getInstance().getAccentColor(0);

        if (!active) {
            int disFill = ColorUtils.withAlpha(0xFF0A0D16, Math.round(115 * alpha));
            int disBorder = ColorUtils.withAlpha(0xFFFFFFFF, Math.round(20 * alpha));
            Render2DHelper.drawRoundedRect(context, x, y, width, height, radius, disFill);
            Render2DHelper.drawRoundedOutline(context, x, y, width, height, radius, 1.0f, disBorder);
            return;
        }

        // Subtle outer glow on hover
        if (hv > 0.02f) {
            Render2DHelper.drawGlow(context, x, y + 1.0f, width, height, 4.5f,
                    ColorUtils.withAlpha(accent, Math.round(45 * hv * alpha)));
        }

        // Frosted dark glass backing + luminous Desire glass tint
        int darkBack = ColorUtils.interpolateColor(
                ColorUtils.withAlpha(0xFF090C14, Math.round(118 * alpha)),
                ColorUtils.withAlpha(0xFF101626, Math.round(148 * alpha)),
                hv
        );
        int whiteGlass = ColorUtils.withAlpha(0xFFFFFFFF, Math.round(Mth.lerp(hv, 12.0f, 34.0f) * alpha));
        Render2DHelper.drawRoundedRect(context, x, y, width, height, radius, darkBack);
        Render2DHelper.drawRoundedRect(context, x, y, width, height, radius, whiteGlass);

        // Specular sweeping sheen band on hover
        if (st.sheen > 0.002f && width > 14) {
            drawSheen(context, x, y, width, height, st.sheen, alpha * (0.45f + 0.55f * hv));
        }

        // Inner top highlight edge
        if (width > radius * 2.0f + 4.0f) {
            int topBevel = ColorUtils.withAlpha(0xFFFFFFFF, Math.round(Mth.lerp(hv, 16.0f, 46.0f) * alpha));
            Render2DHelper.drawRect(context, x + radius + 1.0f, y + 1.0f, width - radius * 2.0f - 2.0f, 1.0f, topBevel);
        }

        // Outline blended softly toward theme accent on hover
        int idleOutline = ColorUtils.withAlpha(0xFFFFFFFF, Math.round(32 * alpha));
        int hoverOutline = ColorUtils.withAlpha(ColorUtils.interpolateColor(0xFFFFFFFF, accent, 0.55f), Math.round(155 * alpha));
        Render2DHelper.drawRoundedOutline(context, x, y, width, height, radius, 1.0f,
                ColorUtils.interpolateColor(idleOutline, hoverOutline, hv));
    }

    /**
     * Renders the Desire glass slider track, progress fill, and handle for vanilla
     * {@code AbstractSliderButton} widgets via {@link dev.opticumeclient.mixin.AbstractSliderButtonMixin}.
     */
    public static void renderVanillaSlider(
            GuiGraphicsExtractor context,
            Object key,
            int x, int y, int width, int height,
            double value, boolean active, boolean hoveredOrFocused, float alpha
    ) {
        if (width <= 0 || height <= 0 || alpha <= 0.01f) return;

        AnimState st = WIDGET_STATES.computeIfAbsent(key, k -> new AnimState());
        float dt = computeDt(st);
        boolean hovered = active && hoveredOrFocused;
        st.hover += ((hovered ? 1.0f : 0.0f) - st.hover) * (1.0f - (float) Math.exp(-14.0f * dt));
        float hv = Mth.clamp(st.hover, 0.0f, 1.0f);

        float radius = Math.min(8.0f, Math.min(width, height) * 0.36f);
        int accent1 = ThemeManager.getInstance().getCurrentTheme().getPrimaryColor();
        int accent2 = ThemeManager.getInstance().getCurrentTheme().getSecondaryColor();

        int bg = ColorUtils.withAlpha(0xFF0A0D16, Math.round(Mth.lerp(hv, 120.0f, 148.0f) * alpha));
        Render2DHelper.drawRoundedRect(context, x, y, width, height, radius, bg);

        float clampedVal = (float) Math.max(0.0, Math.min(1.0, value));
        float fillW = Math.max(radius * 1.2f, width * clampedVal);
        if (clampedVal > 0.005f) {
            int fillLeft = ColorUtils.withAlpha(accent1, Math.round((active ? 75 : 35) * alpha));
            int fillRight = ColorUtils.withAlpha(accent2, Math.round((active ? 95 : 42) * alpha));
            Render2DHelper.drawGradientRoundedRect(context, x, y, Math.min(width, fillW), height, radius, fillLeft, fillRight);
        }

        int handleX = x + (int) Math.round(clampedVal * Math.max(1, width - 6));
        int handleColor = active
                ? ColorUtils.withAlpha(ColorUtils.interpolateColor(0xFFE5EAF5, 0xFFFFFFFF, hv), Math.round((190 + 65 * hv) * alpha))
                : ColorUtils.withAlpha(0xFF8892A4, Math.round(110 * alpha));
        Render2DHelper.drawRoundedRect(context, handleX, y + 2.0f, 6.0f, Math.max(4.0f, height - 4.0f), 3.0f, handleColor);

        int outline = active
                ? ColorUtils.interpolateColor(
                        ColorUtils.withAlpha(0xFFFFFFFF, Math.round(28 * alpha)),
                        ColorUtils.withAlpha(ColorUtils.interpolateColor(0xFFFFFFFF, accent1, 0.5f), Math.round(145 * alpha)),
                        hv)
                : ColorUtils.withAlpha(0xFFFFFFFF, Math.round(18 * alpha));
        Render2DHelper.drawRoundedOutline(context, x, y, width, height, radius, 1.0f, outline);
    }

    /**
     * Sweeps a soft vertical specular highlight band horizontally across a button surface.
     */
    public static void drawSheen(GuiGraphicsExtractor context, float x, float y, float width, float height, float sheenProgress, float alpha) {
        if (sheenProgress <= 0.002f || sheenProgress >= 1.0f || alpha <= 0.01f) return;
        float t = Mth.clamp(sheenProgress, 0.0f, 1.0f);
        float bandW = Math.min(width * 0.45f, Math.max(24.0f, width * 0.34f));
        float center = (x - bandW * 0.45f) + (width + bandW * 0.9f) * t;
        float env = (float) Math.sin(t * Math.PI);
        int peakAlpha = Math.round(38.0f * env * alpha);
        if (peakAlpha <= 1) return;

        int ix1 = Math.round(x + 2.0f);
        int ix2 = Math.round(x + width - 2.0f);
        int iy1 = Math.round(y + 1.0f);
        int iy2 = Math.round(y + height - 1.0f);
        if (ix2 <= ix1 || iy2 <= iy1) return;

        context.enableScissor(ix1, iy1, ix2, iy2);
        int stripes = 5;
        float half = bandW * 0.5f;
        for (int i = -stripes; i <= stripes; i++) {
            float sx1 = center + (i / (float) (stripes + 1)) * half;
            float sx2 = center + ((i + 1) / (float) (stripes + 1)) * half;
            float factor = 1.0f - Math.abs(i + 0.5f) / (stripes + 1.0f);
            int a = Math.round(peakAlpha * factor * factor);
            if (a > 1) {
                Render2DHelper.drawRect(context, sx1, iy1, Math.max(1.0f, sx2 - sx1), iy2 - iy1, ColorUtils.withAlpha(0xFFFFFFFF, a));
            }
        }
        context.disableScissor();
    }
}
