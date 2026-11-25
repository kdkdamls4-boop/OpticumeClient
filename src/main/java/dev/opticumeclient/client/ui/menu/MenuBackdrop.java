package dev.opticumeclient.client.ui.menu;

import dev.opticumeclient.client.render.ColorUtils;
import dev.opticumeclient.client.render.Render2DHelper;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;

/**
 * Shared menu background renderer for {@link OpticumeMainMenuScreen} and {@link OpticumeAltScreen}.
 * Supports live vanilla Minecraft panorama (default) or bundled Gallery wallpapers with parallax cover crop.
 */
public final class MenuBackdrop {
    private MenuBackdrop() {
    }

    public static void render(
            GuiGraphicsExtractor context,
            Runnable panoramaRenderer,
            MenuWallpapers.Wallpaper wallpaper,
            float width,
            float height,
            float parallaxNormX,
            float parallaxNormY,
            float alpha,
            float zoom
    ) {
        if (wallpaper == null || wallpaper.panorama()) {
            if (panoramaRenderer != null) {
                panoramaRenderer.run();
            }
        } else {
            renderWallpaperCover(context, wallpaper, width, height, parallaxNormX, parallaxNormY, alpha, zoom);
        }

        // Subtle atmospheric gradient scrim for crisp glass button readability
        int topScrim = ColorUtils.withAlpha(0xFF04060C, Math.round(40 * alpha));
        int botScrim = ColorUtils.withAlpha(0xFF03050B, Math.round(92 * alpha));
        Render2DHelper.drawGradientRect(context, 0, 0, width, height, topScrim, botScrim);
    }

    public static void renderWallpaperCover(
            GuiGraphicsExtractor context,
            MenuWallpapers.Wallpaper wp,
            float width,
            float height,
            float parallaxNormX,
            float parallaxNormY,
            float alpha,
            float zoom
    ) {
        if (wp == null || wp.panorama() || alpha <= 0.004f) return;

        float screenAspect = width / Math.max(1.0f, height);
        float u, v, uw, uh;
        if (screenAspect > wp.aspect()) {
            u = 0.0f;
            uw = 1.0f;
            uh = wp.aspect() / screenAspect;
            v = (1.0f - uh) * 0.5f;
        } else {
            v = 0.0f;
            uh = 1.0f;
            uw = screenAspect / wp.aspect();
            u = (1.0f - uw) * 0.5f;
        }

        float safeZoom = Math.max(1.0f, zoom);
        float zw = uw / safeZoom;
        float zh = uh / safeZoom;
        float cu = u + uw * 0.5f - parallaxNormX * 0.015f;
        float cv = v + uh * 0.5f - parallaxNormY * 0.015f;
        u = Mth.clamp(cu - zw * 0.5f, 0.0f, Math.max(0.0f, 1.0f - zw));
        v = Mth.clamp(cv - zh * 0.5f, 0.0f, Math.max(0.0f, 1.0f - zh));

        int tint = ColorUtils.rgba(255, 255, 255, Math.max(0, Math.min(255, Math.round(255.0f * alpha))));
        Render2DHelper.drawCroppedTextureFloat(context, wp.image(), 0, 0, width, height, u, v, zw, zh, tint);
    }
}
