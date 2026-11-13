package dev.optimusprime.client.render;

public class ColorUtils {
    // Legacy & Default Theme Palette Constants (preserved for backwards compatibility)
    public static final int PRIMARY = 0xFF7B61FF;
    public static final int SECONDARY = 0xFF00E5FF;
    public static final int ACCENT_GLOW = 0x667B61FF;
    public static final int OPTIMUS_CYAN = 0xFF00E5FF;
    public static final int OPTIMUS_RED = 0xFFFF2E54;
    public static final int BORDER_GLOW = 0x887B61FF;

    public static final int BG_DARK = 0xE60B0C12;
    public static final int BG_CARD = 0xCC131520;
    public static final int BG_PANEL = 0xF00E1018;

    public static final int TEXT_MAIN = 0xFFF4F6FF;
    public static final int TEXT_MUTED = 0xFF8C92AC;

    public static final int SUCCESS = 0xFF22C55E;
    public static final int DANGER = 0xFFEF4444;
    public static final int WARNING = 0xFFF59E0B;

    public static int rgba(int r, int g, int b, int a) {
        return ((a & 0xFF) << 24) | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
    }

    public static int withAlpha(int color, int alpha) {
        int a = Math.max(0, Math.min(255, alpha));
        return (color & 0x00FFFFFF) | (a << 24);
    }

    public static int withAlphaFactor(int color, float alphaFactor) {
        int baseAlpha = (color >>> 24) & 0xFF;
        if (baseAlpha == 0) baseAlpha = 255;
        int newAlpha = Math.max(0, Math.min(255, (int) (baseAlpha * Math.max(0.0f, Math.min(1.0f, alphaFactor)))));
        return (color & 0x00FFFFFF) | (newAlpha << 24);
    }

    public static int interpolateColor(int c1, int c2, float factor) {
        float f = Math.max(0.0f, Math.min(1.0f, factor));
        int a1 = (c1 >>> 24) & 0xFF;
        int r1 = (c1 >>> 16) & 0xFF;
        int g1 = (c1 >>> 8) & 0xFF;
        int b1 = c1 & 0xFF;

        int a2 = (c2 >>> 24) & 0xFF;
        int r2 = (c2 >>> 16) & 0xFF;
        int g2 = (c2 >>> 8) & 0xFF;
        int b2 = c2 & 0xFF;

        int a = (int) (a1 + (a2 - a1) * f);
        int r = (int) (r1 + (r2 - r1) * f);
        int g = (int) (g1 + (g2 - g1) * f);
        int b = (int) (b1 + (b2 - b1) * f);
        return rgba(r, g, b, a);
    }

    public static int getGradientOffset(int color1, int color2, double offset) {
        if (offset > 1.0) {
            double left = offset % 1.0;
            int off = (int) offset;
            offset = (off % 2 == 0) ? left : 1.0 - left;
        }
        return interpolateColor(color1, color2, (float) offset);
    }

    public static int getThemeColor(int indexOffset) {
        return ThemeManager.getInstance().getAccentColor(indexOffset);
    }

    public static int getThemeColor(int indexOffset, float speed) {
        return ThemeManager.getInstance().getAccentGradient(indexOffset, speed);
    }

    public static int getAccentGradient(float offset) {
        return ThemeManager.getInstance().getAccentGradient((int) (offset * 100.0f), 3.0f);
    }

    public static int rainbow(int delay, float saturation, float brightness) {
        return getRainbow(4.0f, saturation, brightness, delay);
    }

    public static int getRainbow(float seconds, float saturation, float brightness, long index) {
        float period = Math.max(0.2f, seconds) * 1000.0f;
        float hue = ((System.currentTimeMillis() + index) % (long) period) / period;
        return java.awt.Color.HSBtoRGB(hue, saturation, brightness);
    }

    public static int getRainbowColor(int delay, float speed) {
        return getRainbow(Math.max(0.5f, 10.0f / Math.max(0.5f, speed)), 0.78f, 1.0f, delay);
    }

    public static int hsbToRgb(float hue, float saturation, float brightness, int alpha) {
        int rgb = java.awt.Color.HSBtoRGB(hue, saturation, brightness);
        return (rgb & 0x00FFFFFF) | ((Math.max(0, Math.min(255, alpha))) << 24);
    }

    public static float[] rgbToHsb(int color) {
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        return java.awt.Color.RGBtoHSB(r, g, b, null);
    }
}
