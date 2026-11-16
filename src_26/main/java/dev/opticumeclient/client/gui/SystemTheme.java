package dev.opticumeclient.client.gui;

import dev.opticumeclient.client.render.ColorUtils;
import dev.opticumeclient.client.ui.menu.MenuConfigStore;
import net.minecraft.util.Mth;

/**
 * Faithfully ported color palette & smooth Dark/Light theme animator from System's
 * {@code ThemePalette} and {@code ThemeConfig}.
 */
public final class SystemTheme {
    private static final String CONFIG_KEY_DARK = "clickGuiDark";

    // System Dark Palette (INSTANCE2)
    private static final int D_BG = 0xFF0A0A0A;
    private static final int D_SURFACE = 0xFF171717;
    private static final int D_ELEVATED = 0xFF262626;
    private static final int D_BORDER = 0xFF404040;
    private static final int D_MUTED = 0xFFA1A1A1;
    private static final int D_PRIMARY = 0xFFFAFAFA;
    private static final int D_FG = 0xFFFAFAFA;

    // System Light Palette (INSTANCE)
    private static final int L_BG = 0xFFFFFFFF;
    private static final int L_SURFACE = 0xFFFAFAFA;
    private static final int L_ELEVATED = 0xFFF5F5F5;
    private static final int L_BORDER = 0xFFE5E5E5;
    private static final int L_MUTED = 0xFF737373;
    private static final int L_PRIMARY = 0xFF171717;
    private static final int L_FG = 0xFF0A0A0A;

    public static final int SUCCESS = 0xFF22C55E;
    public static final int WARNING = 0xFFF59E0B;
    public static final int DANGER = 0xFFEF4444;
    public static final int FAVORITE_GOLD = 0xFFFACC15;

    private static boolean initialized = false;
    private static boolean darkMode = true;
    private static float darkMix = 1.0f;

    private SystemTheme() {
    }

    public static void ensureLoaded() {
        if (initialized) return;
        initialized = true;
        darkMode = MenuConfigStore.getBoolean(CONFIG_KEY_DARK, true);
        darkMix = darkMode ? 1.0f : 0.0f;
    }

    public static boolean isDark() {
        ensureLoaded();
        return darkMode;
    }

    public static void toggleDark() {
        ensureLoaded();
        darkMode = !darkMode;
        MenuConfigStore.save(obj -> obj.addProperty(CONFIG_KEY_DARK, darkMode));
    }

    public static void update(float dt) {
        ensureLoaded();
        float target = darkMode ? 1.0f : 0.0f;
        darkMix += (target - darkMix) * (1.0f - (float) Math.exp(-12.0f * dt));
        if (Math.abs(target - darkMix) < 0.002f) darkMix = target;
    }

    public static int bg() {
        ensureLoaded();
        return ColorUtils.interpolateColor(L_BG, D_BG, darkMix);
    }

    public static int surface() {
        ensureLoaded();
        return ColorUtils.interpolateColor(L_SURFACE, D_SURFACE, darkMix);
    }

    public static int elevated() {
        ensureLoaded();
        return ColorUtils.interpolateColor(L_ELEVATED, D_ELEVATED, darkMix);
    }

    public static int border() {
        ensureLoaded();
        return ColorUtils.interpolateColor(L_BORDER, D_BORDER, darkMix);
    }

    public static int mutedFg() {
        ensureLoaded();
        return ColorUtils.interpolateColor(L_MUTED, D_MUTED, darkMix);
    }

    public static int primary() {
        ensureLoaded();
        return ColorUtils.interpolateColor(L_PRIMARY, D_PRIMARY, darkMix);
    }

    public static int foreground() {
        ensureLoaded();
        return ColorUtils.interpolateColor(L_FG, D_FG, darkMix);
    }

    /** Text/knob color drawn on top of {@link #primary()} surface. */
    public static int onPrimary() {
        ensureLoaded();
        return ColorUtils.interpolateColor(D_FG, L_FG, darkMix);
    }

    public static int fade(int argb, float alpha) {
        int baseA = (argb >>> 24) & 0xFF;
        if (baseA == 0) baseA = 255;
        int outA = Mth.clamp(Math.round(baseA * Mth.clamp(alpha, 0.0f, 1.0f)), 0, 255);
        return (argb & 0x00FFFFFF) | (outA << 24);
    }
}
