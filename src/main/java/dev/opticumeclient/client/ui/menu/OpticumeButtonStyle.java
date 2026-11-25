package dev.opticumeclient.client.ui.menu;

import dev.opticumeclient.client.render.ColorUtils;
import dev.opticumeclient.client.render.Render2DHelper;
import dev.opticumeclient.client.render.ThemeManager;
import dev.opticumeclient.client.render.font.OpticumeFonts;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;
import org.joml.Matrix3x2fStack;

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

        // Translucent frosted glass backing matching MainMenu's clean Desire look
        int darkBack = ColorUtils.interpolateColor(
                ColorUtils.withAlpha(0xFF0A0E18, Math.round(52 * alpha)),
                ColorUtils.withAlpha(0xFF121829, Math.round(76 * alpha)),
                hv
        );
        int whiteGlass = ColorUtils.withAlpha(0xFFFFFFFF, Math.round(Mth.lerp(hv, 20.0f, 40.0f) * alpha));
        Render2DHelper.drawRoundedRectFloat(context, x, y, width, height, radius, darkBack);
        Render2DHelper.drawRoundedRectFloat(context, x, y, width, height, radius, whiteGlass);

        // Specular sweeping sheen band on hover
        if (st.sheen > 0.002f && width > 14) {
            drawSheen(context, x, y, width, height, st.sheen, alpha * (0.45f + 0.55f * hv));
        }

        // Inner top highlight edge
        if (width > radius * 2.0f + 4.0f) {
            int topBevel = ColorUtils.withAlpha(0xFFFFFFFF, Math.round(Mth.lerp(hv, 20.0f, 50.0f) * alpha));
            Render2DHelper.drawRect(context, x + radius + 1.0f, y + 1.0f, width - radius * 2.0f - 2.0f, 0.8f, topBevel);
        }

        // Delicate hairline outline blended softly toward theme accent on hover
        int idleOutline = ColorUtils.withAlpha(0xFFFFFFFF, Math.round(28 * alpha));
        int hoverOutline = ColorUtils.withAlpha(ColorUtils.interpolateColor(0xFFFFFFFF, accent, 0.55f), Math.round(145 * alpha));
        Render2DHelper.drawRoundedOutlineFloat(context, x, y, width, height, radius, 0.55f + 0.3f * hv,
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

        int bg = ColorUtils.withAlpha(0xFF0A0E18, Math.round(Mth.lerp(hv, 54.0f, 78.0f) * alpha));
        Render2DHelper.drawRoundedRectFloat(context, x, y, width, height, radius, bg);
        Render2DHelper.drawRoundedRectFloat(context, x, y, width, height, radius,
                ColorUtils.withAlpha(0xFFFFFFFF, Math.round(Mth.lerp(hv, 16.0f, 28.0f) * alpha)));

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
     * Renders the signature translucent Desire blurred Liquid Glass button matching
     * {@link OpticumeMainMenuScreen} for custom menu screens such as {@link OpticumeAltScreen}
     * and {@link ProxyScreen}.
     */
    public static void renderGlassButton(
            GuiGraphicsExtractor context,
            Object key,
            float x, float y, float width, float height,
            boolean interactive, boolean highlighted, boolean hoveredOrFocused,
            int tintAccent, float alpha
    ) {
        if (width <= 0.0f || height <= 0.0f || alpha <= 0.01f) return;

        AnimState st = WIDGET_STATES.computeIfAbsent(key, k -> new AnimState());
        float dt = computeDt(st);
        boolean hovered = interactive && hoveredOrFocused;
        if (hovered && !st.wasHovered) {
            st.sheen = 0.001f;
        }
        st.wasHovered = hovered;
        float speed = hovered ? 16.0f : 11.0f;
        st.hover += ((hovered ? 1.0f : 0.0f) - st.hover) * (1.0f - (float) Math.exp(-speed * dt));
        if (st.sheen > 0.0f) {
            st.sheen += dt / 0.55f;
            if (st.sheen >= 1.0f) st.sheen = 0.0f;
        }

        float hv = Mth.clamp(st.hover, 0.0f, 1.0f);
        float radius = Math.min(8.0f, Math.min(width, height) * 0.36f);
        int accent = tintAccent != 0 ? tintAccent : ThemeManager.getInstance().getAccentColor(0);

        if (hv > 0.02f || highlighted) {
            float glowFactor = highlighted ? Math.max(0.45f, hv) : hv;
            Render2DHelper.drawGlow(context, x, y + 1.0f, width, height, 6.0f,
                    ColorUtils.withAlpha(accent, Math.round(52.0f * glowFactor * alpha)));
        }

        // Clear blurred Liquid Glass matching Main Menu's luminous floating buttons
        Render2DHelper.drawBlurredRoundedRect(context, x, y, width, height, radius, 0x100B0F1A, alpha);

        int idleFill = highlighted
                ? ColorUtils.withAlpha(accent, Math.round(44.0f * alpha))
                : ColorUtils.withAlpha(0xFFFFFFFF, Math.round((interactive ? 20.0f : 11.0f) * alpha));
        int hoverFill = highlighted
                ? ColorUtils.withAlpha(accent, Math.round(72.0f * alpha))
                : ColorUtils.withAlpha(0xFFFFFFFF, Math.round(36.0f * alpha));
        Render2DHelper.drawRoundedRectFloat(context, x, y, width, height, radius,
                ColorUtils.interpolateColor(idleFill, hoverFill, hv));

        if (st.sheen > 0.002f && width > 14.0f) {
            drawSheen(context, x, y, width, height, st.sheen, alpha * (0.45f + 0.55f * hv));
        }

        if (width > radius * 2.0f + 4.0f) {
            int topBevel = ColorUtils.withAlpha(0xFFFFFFFF, Math.round(Mth.lerp(hv, 22.0f, 52.0f) * alpha));
            Render2DHelper.drawRect(context, x + radius + 1.0f, y + 0.8f, width - radius * 2.0f - 2.0f, 0.75f, topBevel);
        }

        int idleBorder = highlighted
                ? ColorUtils.withAlpha(accent, Math.round(135.0f * alpha))
                : ColorUtils.withAlpha(0xFFFFFFFF, Math.round((interactive ? 28.0f : 16.0f) * alpha));
        int hoverBorder = ColorUtils.withAlpha(ColorUtils.interpolateColor(0xFFFFFFFF, accent, 0.55f), Math.round(150.0f * alpha));
        Render2DHelper.drawRoundedOutlineFloat(context, x, y, width, height, radius, 0.55f + 0.25f * hv,
                ColorUtils.interpolateColor(idleBorder, hoverBorder, hv));
    }

    public static float getUiTextWidth(String text, float pxSize) {
        if (text == null || text.isEmpty()) return 0.0f;
        float w = OpticumeFonts.getDirectWidth(OpticumeFonts.KEY_MENU_INTER, text, pxSize);
        if (w > 0.0f) return w;
        return Render2DHelper.getTextWidth(text, pxSize / OpticumeFonts.BASE_EM_PX);
    }

    public static void drawUiText(GuiGraphicsExtractor context, String text, float x, float y, float pxSize, int color, boolean shadow) {
        if (text == null || text.isEmpty()) return;
        if (shadow) {
            int a = (color >>> 24) & 0xFF;
            int sh = ColorUtils.withAlpha(0xFF000000, Math.round(a * 0.42f));
            if (!OpticumeFonts.drawDirect(context, OpticumeFonts.KEY_MENU_INTER, text, x + 0.7f, y + 0.7f, pxSize, sh)) {
                Render2DHelper.drawScaledText(context, text, x, y, color, pxSize / OpticumeFonts.BASE_EM_PX, true);
                return;
            }
        }
        if (!OpticumeFonts.drawDirect(context, OpticumeFonts.KEY_MENU_INTER, text, x, y, pxSize, color)) {
            Render2DHelper.drawScaledText(context, text, x, y, color, pxSize / OpticumeFonts.BASE_EM_PX, shadow);
        }
    }

    public static void drawUiCenteredText(GuiGraphicsExtractor context, String text, float centerX, float y, float pxSize, int color, boolean shadow) {
        float w = getUiTextWidth(text, pxSize);
        drawUiText(context, text, centerX - w * 0.5f, y, pxSize, color, shadow);
    }

    public static float getScriptTextWidth(String text, float pxSize) {
        if (text == null || text.isEmpty()) return 0.0f;
        float w = OpticumeFonts.getDirectWidth(OpticumeFonts.KEY_ETERNAL_HEAVEN, text, pxSize);
        if (w > 0.0f) return w;
        return getUiTextWidth(text, pxSize * 0.55f);
    }

    /**
     * Renders handwritten EternalHeaven script text matching MainMenu's "Welcome!" heading
     * (all 95 ASCII printable glyphs), falling back per-character to Inter MSDF if non-ASCII text is passed.
     */
    public static void drawScriptTitle(
            GuiGraphicsExtractor context,
            String text,
            float x,
            float y,
            float pxSize,
            float alpha,
            float elapsedSec,
            boolean shadow
    ) {
        if (context == null || text == null || text.isEmpty() || alpha <= 0.01f) return;

        var scriptFace = OpticumeFonts.getDirectFace(OpticumeFonts.KEY_ETERNAL_HEAVEN);
        boolean canScript = scriptFace != null;
        if (!canScript) {
            drawUiText(context, text, x, y + pxSize * 0.25f, pxSize * 0.55f,
                    ColorUtils.rgba(247, 247, 252, Math.round(255.0f * alpha)), shadow);
            return;
        }

        float floatY = (elapsedSec > 0.35f)
                ? (float) Math.sin(elapsedSec * 1.15f) * 0.65f * Math.min(1.0f, (elapsedSec - 0.35f) / 0.6f)
                : 0.0f;
        float baseY = y + floatY;
        Matrix3x2fStack pose = context.pose();
        float fallbackAdvanceOffset = 0.0f;

        for (int c = 0; c < text.length(); c++) {
            float p = Mth.clamp((elapsedSec - c * 0.032f) / 0.45f, 0.0f, 1.0f);
            String ch = text.substring(c, c + 1);
            boolean chSupported = scriptFace.supportsText(ch);
            float chW = chSupported
                    ? OpticumeFonts.getDirectWidth(OpticumeFonts.KEY_ETERNAL_HEAVEN, ch, pxSize)
                    : getUiTextWidth(ch, pxSize * 0.55f);
            float prefixW = OpticumeFonts.getDirectWidth(OpticumeFonts.KEY_ETERNAL_HEAVEN, text.substring(0, c + 1), pxSize);
            float chX = x + (chSupported ? Math.max(0.0f, prefixW - chW) + fallbackAdvanceOffset : prefixW + fallbackAdvanceOffset);
            if (!chSupported) {
                fallbackAdvanceOffset += chW;
            }
            if (p <= 0.0f) continue;

            float inv = 1.0f - p;
            float easeCubic = 1.0f - inv * inv * inv;
            float back = 1.0f + 2.4f * (float) Math.pow(p - 1.0f, 3.0) + 1.4f * (float) Math.pow(p - 1.0f, 2.0);
            float rise = (1.0f - back) * 7.5f;
            float sc = 0.72f + 0.28f * back;
            int a = Math.round(255.0f * Mth.clamp(easeCubic * 1.4f, 0.0f, 1.0f) * alpha);
            if (a <= 2) continue;

            float pivotX = chX + chW * 0.5f;
            float pivotY = baseY + pxSize * 0.55f;

            pose.pushMatrix();
            pose.translate(pivotX, pivotY + rise);
            pose.scale(sc, sc);
            pose.translate(-pivotX, -pivotY);
            if (chSupported) {
                if (shadow) {
                    int softHalo = ColorUtils.withAlpha(0xFF060810, Math.round(a * 0.36f));
                    int dropSh = ColorUtils.withAlpha(0xFF060810, Math.round(a * 0.58f));
                    OpticumeFonts.drawDirect(context, OpticumeFonts.KEY_ETERNAL_HEAVEN, ch, chX - 0.55f, baseY + 0.9f, pxSize, softHalo);
                    OpticumeFonts.drawDirect(context, OpticumeFonts.KEY_ETERNAL_HEAVEN, ch, chX + 1.25f, baseY + 1.45f, pxSize, dropSh);
                }
                OpticumeFonts.drawDirect(context, OpticumeFonts.KEY_ETERNAL_HEAVEN, ch, chX, baseY, pxSize, ColorUtils.rgba(247, 247, 252, a));
            } else {
                drawUiText(context, ch, chX, baseY + pxSize * 0.25f, pxSize * 0.55f, ColorUtils.rgba(247, 247, 252, a), shadow);
            }
            pose.popMatrix();
        }
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
