package dev.opticumeclient.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.opticumeclient.client.render.ColorUtils;
import dev.opticumeclient.client.render.Render2DHelper;
import dev.opticumeclient.client.render.TextureAssets;
import dev.opticumeclient.client.render.Theme;
import dev.opticumeclient.client.render.ThemeManager;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Desire-inspired Themes manager page inside ClickGUI:
 * - Left panel: Preset & Custom theme gradient cards with active glow & checkmark
 * - Right panel: Live element color editor with inline HSB + Alpha picker, Delete, and Create Custom Copy
 */
public final class ThemesPage {
    private float gridScroll = 0.0f;
    private float gridTargetScroll = 0.0f;
    private float gridMaxScroll = 0.0f;

    private StringBuilder newName = new StringBuilder();
    private boolean typingName = false;
    private String openColorKey = null;
    private int dragMode = 0; // 1=SV, 2=Hue, 3=Alpha
    private float pickerX, pickerY, pickerW, pickerSvH;

    private final Map<Theme, float[]> cardAnims = new HashMap<>();

    public void unfocus() {
        typingName = false;
        dragMode = 0;
    }

    public boolean isTyping() {
        return typingName;
    }

    public void render(GuiGraphicsExtractor context, float dt, float x, float y, float w, float h,
                       int mouseX, int mouseY, float alpha) {
        ThemeManager tm = ThemeManager.getInstance();
        List<Theme> themes = tm.getThemes();
        Theme current = tm.getCurrentTheme();

        float pad = 10.0f;
        float ix = x + pad;
        float iy = y + pad;
        float iw = w - pad * 2.0f;
        float ih = h - pad * 2.0f;

        float editorW = Math.min(205.0f, Math.max(165.0f, iw * 0.42f));
        float gridGap = 10.0f;
        float gridW = iw - editorW - gridGap;

        int deletedCount = tm.getDeletedPresetCount();
        float restoreH = 22.0f;
        float restoreGap = 6.0f;
        float gridH = deletedCount > 0 ? Math.max(40.0f, ih - restoreH - restoreGap) : ih;

        renderThemeGrid(context, dt, themes, current, ix, iy, gridW, gridH, mouseX, mouseY, alpha);
        if (deletedCount > 0) {
            float ry = iy + gridH + restoreGap;
            float rw = gridW - 6.0f;
            boolean rHov = isInside(mouseX, mouseY, ix, ry, rw, restoreH);
            int btnBg = rHov ? SystemTheme.elevated() : SystemTheme.surface();
            int acc = tm.getAccentColor(0);
            Render2DHelper.drawRoundedRectFloat(context, ix, ry, rw, restoreH, 6.0f,
                    SystemTheme.fade(btnBg, alpha * 0.94f));
            Render2DHelper.drawRoundedOutlineFloat(context, ix, ry, rw, restoreH, 6.0f, 1.0f,
                    ColorUtils.withAlphaFactor(rHov ? acc : SystemTheme.border(), alpha));
            String restoreLabel = "Restore built-in themes (" + deletedCount + ")";
            Render2DHelper.drawCenteredText(context, restoreLabel, ix + rw * 0.5f, ry + 7.0f,
                    SystemTheme.fade(rHov ? SystemTheme.foreground() : SystemTheme.mutedFg(), alpha), false);
        }
        renderThemeEditor(context, tm, current, ix + gridW + gridGap, iy, editorW, ih, mouseX, mouseY, alpha);
    }

    private void renderThemeGrid(GuiGraphicsExtractor context, float dt, List<Theme> themes, Theme current,
                                 float gx, float gy, float gw, float gh, int mouseX, int mouseY, float alpha) {
        int cols = gw >= 275.0f ? 3 : 2;
        float gap = 6.5f;
        float sbReserve = 6.0f;
        float availW = gw - sbReserve;
        float tileW = (availW - gap * (cols - 1)) / cols;
        float tileH = 44.0f;

        int rows = (themes.size() + cols - 1) / cols;
        float totalH = rows * (tileH + gap) - gap;
        gridMaxScroll = Math.max(0.0f, totalH - gh);
        gridTargetScroll = Mth.clamp(gridTargetScroll, 0.0f, gridMaxScroll);
        gridScroll += (gridTargetScroll - gridScroll) * (1.0f - (float) Math.exp(-16.0f * dt));

        context.enableScissor(Math.round(gx - 2), Math.round(gy - 2), Math.round(gx + availW + 2), Math.round(gy + gh + 2));
        boolean allowHover = mouseX >= gx && mouseX <= gx + availW && mouseY >= gy && mouseY <= gy + gh;

        for (int i = 0; i < themes.size(); i++) {
            Theme t = themes.get(i);
            float tx = gx + (i % cols) * (tileW + gap);
            float ty = gy + (i / cols) * (tileH + gap) - gridScroll;
            if (ty + tileH < gy || ty > gy + gh) continue;

            boolean active = (t == current);
            boolean hov = allowHover && isInside(mouseX, mouseY, tx, ty, tileW, tileH);
            float[] anim = cardAnims.computeIfAbsent(t, k -> new float[]{0.0f, active ? 1.0f : 0.0f});
            float rate = 1.0f - (float) Math.exp(-16.0f * dt);
            anim[0] += ((hov ? 1.0f : 0.0f) - anim[0]) * rate;
            anim[1] += ((active ? 1.0f : 0.0f) - anim[1]) * rate;

            float rad = 7.0f;
            int c1 = ColorUtils.withAlpha(t.getPrimaryColor(), 255);
            int c2 = ColorUtils.withAlpha(t.getSecondaryColor(), 255);
            int mid = ColorUtils.interpolateColor(c1, c2, 0.5f);

            Render2DHelper.drawGradientRoundedRectFloat(context, tx, ty, tileW, tileH, rad,
                    ColorUtils.withAlphaFactor(c1, alpha),
                    ColorUtils.withAlphaFactor(mid, alpha),
                    ColorUtils.withAlphaFactor(c2, alpha),
                    ColorUtils.withAlphaFactor(mid, alpha));
            // Dark readability scrim across bottom
            Render2DHelper.drawGradientRoundedRectFloat(context, tx, ty, tileW, tileH, rad,
                    ColorUtils.rgba(8, 9, 14, Math.round(25 * alpha)),
                    ColorUtils.rgba(8, 9, 14, Math.round(180 * alpha)),
                    ColorUtils.rgba(8, 9, 14, Math.round(180 * alpha)),
                    ColorUtils.rgba(8, 9, 14, Math.round(25 * alpha)));

            if (anim[0] > 0.01f) {
                Render2DHelper.drawRoundedRectFloat(context, tx, ty, tileW, tileH, rad,
                        ColorUtils.rgba(255, 255, 255, Math.round(26 * anim[0] * alpha)));
            }

            if (anim[1] > 0.01f) {
                Render2DHelper.drawRoundedOutlineFloat(context, tx, ty, tileW, tileH, rad, 1.35f,
                        ColorUtils.rgba(255, 255, 255, Math.round(230 * anim[1] * alpha)));
                float bs = 11.0f;
                float bx = tx + tileW - bs - 5.0f;
                float by = ty + 5.0f;
                Render2DHelper.drawRoundedRectFloat(context, bx, by, bs, bs, bs * 0.5f,
                        ColorUtils.rgba(255, 255, 255, Math.round(235 * anim[1] * alpha)));
                Render2DHelper.drawTextureFloat(context, TextureAssets.DESIRE_GUI_CHECK,
                        bx + 2.2f, by + 2.2f, bs - 4.4f, bs - 4.4f,
                        ColorUtils.rgba(16, 18, 24, Math.round(255 * anim[1] * alpha)));
            }

            String badge = t.isPreset() ? "Preset" : "Custom";
            Render2DHelper.drawScaledText(context, badge, tx + 6.0f, ty + 5.0f,
                    ColorUtils.rgba(255, 255, 255, Math.round(180 * alpha)), 0.60f, false);
            Render2DHelper.drawScaledText(context, Render2DHelper.trimToWidth(t.getName(), 0.78f, tileW - 12.0f),
                    tx + 6.0f, ty + tileH - 13.0f,
                    ColorUtils.rgba(255, 255, 255, Math.round(248 * alpha)), 0.78f, false);
        }
        context.disableScissor();

        if (gridMaxScroll > 1.0f) {
            float trackH = gh;
            float thumbH = Math.max(20.0f, trackH * (gh / totalH));
            float thumbY = gy + (gridScroll / gridMaxScroll) * (trackH - thumbH);
            Render2DHelper.drawRoundedRectFloat(context, gx + gw - 3.0f, thumbY, 2.2f, thumbH, 1.1f,
                    SystemTheme.fade(SystemTheme.border(), alpha));
        }
    }

    private void renderThemeEditor(GuiGraphicsExtractor context, ThemeManager tm, Theme current,
                                   float ex, float ey, float ew, float eh, int mouseX, int mouseY, float alpha) {
        Render2DHelper.drawRoundedRectFloat(context, ex, ey, ew, eh, 8.0f,
                SystemTheme.fade(SystemTheme.bg(), alpha * 0.92f));
        Render2DHelper.drawRoundedOutlineFloat(context, ex, ey, ew, eh, 8.0f, 1.0f,
                SystemTheme.fade(SystemTheme.border(), alpha));

        float pad = 9.0f;
        float ix = ex + pad;
        float iw = ew - pad * 2.0f;
        boolean preset = current.isPreset();

        Render2DHelper.drawScaledText(context, Render2DHelper.trimToWidth(current.getName(), 0.92f, iw - 20.0f),
                ix, ey + 9.0f, SystemTheme.fade(SystemTheme.foreground(), alpha), 0.92f, false);
        Render2DHelper.drawScaledText(context, preset ? "Built-in · copy to edit colors" : "Custom theme",
                ix, ey + 20.0f, SystemTheme.fade(SystemTheme.mutedFg(), alpha), 0.66f, false);

        if (tm.getThemes().size() > 1) {
            float tx = ix + iw - 14.0f;
            float ty = ey + 9.0f;
            boolean thov = isInside(mouseX, mouseY, tx - 3, ty - 3, 18, 18);
            Render2DHelper.drawTextureFloat(context, TextureAssets.DESIRE_GUI_TRASH, tx, ty, 11.0f, 11.0f,
                    SystemTheme.fade(thov ? SystemTheme.DANGER : SystemTheme.mutedFg(), alpha));
        }

        // Dual-accent gradient bar
        int p1 = ColorUtils.withAlpha(current.getPrimaryColor(), 255);
        int p2 = ColorUtils.withAlpha(current.getSecondaryColor(), 255);
        Render2DHelper.drawGradientRoundedRectFloat(context, ix, ey + 32.0f, iw, 4.5f, 2.25f,
                ColorUtils.withAlphaFactor(p1, alpha),
                ColorUtils.withAlphaFactor(p1, alpha),
                ColorUtils.withAlphaFactor(p2, alpha),
                ColorUtils.withAlphaFactor(p2, alpha));

        // Bottom bar: [Theme name..] [Create]
        float barH = 22.0f;
        float barY = ey + eh - pad - barH;
        float btnW = 50.0f;
        float fieldW = iw - btnW - 5.0f;

        // Element list area
        float listY = ey + 42.0f;
        float listBottom = barY - 7.0f;
        context.enableScissor(Math.round(ix - 2), Math.round(listY - 1), Math.round(ix + iw + 2), Math.round(listBottom));
        float ry = listY;
        float rowH = 19.0f;
        float rowGap = 4.0f;
        float rowAlpha = alpha * (preset ? 0.55f : 1.0f);

        for (Map.Entry<String, Integer> entry : current.getElements().entrySet()) {
            String key = entry.getKey();
            int col = entry.getValue();
            boolean hov = !preset && isInside(mouseX, mouseY, ix, ry, iw, rowH) && mouseY < listBottom;
            boolean isOpen = !preset && key.equals(openColorKey);

            int rowBg = hov || isOpen ? SystemTheme.elevated() : SystemTheme.surface();
            Render2DHelper.drawRoundedRectFloat(context, ix, ry, iw, rowH, 5.0f, SystemTheme.fade(rowBg, alpha));
            if (isOpen) {
                Render2DHelper.drawRoundedOutlineFloat(context, ix, ry, iw, rowH, 5.0f, 1.0f,
                        ColorUtils.withAlphaFactor(tm.getAccentColor(0), alpha));
            }
            Render2DHelper.drawScaledText(context, key, ix + 7.0f, ry + 6.0f,
                    SystemTheme.fade(SystemTheme.foreground(), rowAlpha), 0.72f, false);

            float swSize = 11.0f;
            float swX = ix + iw - 6.0f - swSize;
            float swY = ry + (rowH - swSize) * 0.5f;
            String hex = String.format(Locale.US, "#%06X", col & 0xFFFFFF);
            float hexW = Render2DHelper.getTextWidth(hex, 0.65f);
            Render2DHelper.drawScaledText(context, hex, swX - hexW - 5.0f, ry + 6.5f,
                    SystemTheme.fade(SystemTheme.mutedFg(), rowAlpha), 0.65f, false);
            Render2DHelper.drawRoundedRectFloat(context, swX, swY, swSize, swSize, 3.0f,
                    ColorUtils.withAlpha(col, Math.round(255 * rowAlpha)));
            Render2DHelper.drawRoundedOutlineFloat(context, swX, swY, swSize, swSize, 3.0f, 0.8f,
                    ColorUtils.rgba(255, 255, 255, Math.round(48 * rowAlpha)));

            ry += rowH + rowGap;

            if (isOpen) {
                ry = renderInlineElementPicker(context, tm, current, key, col, ix, ry, iw, alpha);
                ry += 4.0f;
            }
        }
        context.disableScissor();

        // Bottom Create Theme Bar
        Render2DHelper.drawRoundedRectFloat(context, ix, barY, fieldW, barH, 5.5f,
                SystemTheme.fade(SystemTheme.surface(), alpha));
        Render2DHelper.drawRoundedOutlineFloat(context, ix, barY, fieldW, barH, 5.5f, 1.0f,
                SystemTheme.fade(typingName ? tm.getAccentColor(0) : SystemTheme.border(), alpha));
        Render2DHelper.drawTextureFloat(context, TextureAssets.DESIRE_GUI_PENCIL, ix + 6.0f, barY + 6.0f, 10.0f, 10.0f,
                SystemTheme.fade(typingName ? SystemTheme.foreground() : SystemTheme.mutedFg(), alpha));

        String shown = newName.toString();
        float tx = ix + 19.0f;
        float maxTextW = fieldW - 24.0f;
        while (!shown.isEmpty() && Render2DHelper.getTextWidth(shown, 0.72f) > maxTextW) {
            shown = shown.substring(1);
        }
        if (shown.isEmpty() && !typingName) {
            Render2DHelper.drawScaledText(context, "Theme name..", tx, barY + 7.5f,
                    SystemTheme.fade(SystemTheme.mutedFg(), alpha * 0.75f), 0.72f, false);
        } else {
            Render2DHelper.drawScaledText(context, shown, tx, barY + 7.5f,
                    SystemTheme.fade(SystemTheme.foreground(), alpha), 0.72f, false);
            if (typingName && (System.currentTimeMillis() / 500L) % 2L == 0L) {
                float cx = tx + Render2DHelper.getTextWidth(shown, 0.72f) + 1.0f;
                Render2DHelper.drawRect(context, cx, barY + 5.5f, 1.0f, 11.0f,
                        SystemTheme.fade(SystemTheme.foreground(), alpha));
            }
        }

        float createX = ix + iw - btnW;
        boolean cHov = isInside(mouseX, mouseY, createX, barY, btnW, barH);
        int acc = tm.getAccentColor(0);
        Render2DHelper.drawRoundedRectFloat(context, createX, barY, btnW, barH, 5.5f,
                ColorUtils.withAlphaFactor(ColorUtils.interpolateColor(acc, 0xFFFFFFFF, cHov ? 0.22f : 0.0f), alpha));
        Render2DHelper.drawCenteredText(context, "Create", createX + btnW * 0.5f, barY + 7.0f,
                ColorUtils.rgba(14, 16, 22, Math.round(255 * alpha)), false);
    }

    private float renderInlineElementPicker(GuiGraphicsExtractor context, ThemeManager tm, Theme theme, String key, int col,
                                            float px, float py, float pw, float alpha) {
        TextureAssets.ensureInitialized();
        float[] hsb = ColorUtils.rgbToHsb(col);
        float hue = hsb[0];
        float sat = hsb[1];
        float bri = hsb[2];
        int aByte = Math.round(255 * alpha);
        int curAlpha = (col >>> 24) & 0xFF;

        float svH = 44.0f;
        float barH = 8.0f;
        int pureHue = ColorUtils.hsbToRgb(hue, 1.0f, 1.0f, aByte);
        int pickedOpaque = ColorUtils.hsbToRgb(hue, sat, bri, aByte);
        int whiteAlpha = ColorUtils.rgba(255, 255, 255, aByte);

        this.pickerX = px;
        this.pickerY = py;
        this.pickerW = pw;
        this.pickerSvH = svH;

        Render2DHelper.drawRect(context, px, py, pw, svH, pureHue);
        Render2DHelper.drawTextureRegion(context, TextureAssets.COLOR_SV_SAT_GRAD, px, py, pw, svH,
                1.0f, 1.5f, 256, 1, 258, 4, whiteAlpha);
        Render2DHelper.drawTextureRegion(context, TextureAssets.COLOR_SV_VAL_GRAD, px, py, pw, svH,
                1.5f, 1.0f, 1, 256, 4, 258, whiteAlpha);
        Render2DHelper.drawRoundedOutlineFloat(context, px, py, pw, svH, 3.0f, 1.0f, SystemTheme.fade(SystemTheme.border(), alpha));

        float kx = Mth.clamp(px + sat * pw, px + 3.5f, px + pw - 3.5f);
        float ky = Mth.clamp(py + (1.0f - bri) * svH, py + 3.5f, py + svH - 3.5f);
        Render2DHelper.drawCircle(context, kx, ky, 3.6f, whiteAlpha);
        Render2DHelper.drawCircle(context, kx, ky, 2.1f, pickedOpaque);

        float hueY = py + svH + 4.0f;
        Render2DHelper.drawTextureRegion(context, TextureAssets.COLOR_HUE_STRIP, px, hueY, pw, barH,
                1.0f, 1.5f, 256, 1, 258, 4, whiteAlpha);
        float hkx = Mth.clamp(px + hue * pw, px + 3.5f, px + pw - 3.5f);
        Render2DHelper.drawCircle(context, hkx, hueY + barH * 0.5f, 3.6f, whiteAlpha);

        float alphaY = hueY + barH + 4.0f;
        Render2DHelper.drawTextureRegion(context, TextureAssets.COLOR_ALPHA_CHECKER, px, alphaY, pw, barH,
                0.0f, 0.0f, Math.min(128, Math.round(pw)), Math.round(barH), 128, 16, whiteAlpha);
        Render2DHelper.drawTextureRegion(context, TextureAssets.COLOR_ALPHA_GRAD, px, alphaY, pw, barH,
                1.0f, 1.5f, 256, 1, 258, 4, pickedOpaque);
        float akx = Mth.clamp(px + (curAlpha / 255.0f) * pw, px + 3.5f, px + pw - 3.5f);
        Render2DHelper.drawCircle(context, akx, alphaY + barH * 0.5f, 3.6f, whiteAlpha);

        return alphaY + barH;
    }

    public boolean mouseClicked(double mx, double my, int button, float x, float y, float w, float h, ClickGuiScreen host) {
        if (button != 0) return false;
        ThemeManager tm = ThemeManager.getInstance();
        List<Theme> themes = tm.getThemes();
        Theme current = tm.getCurrentTheme();

        float pad = 10.0f;
        float ix = x + pad;
        float iy = y + pad;
        float iw = w - pad * 2.0f;
        float ih = h - pad * 2.0f;
        float editorW = Math.min(205.0f, Math.max(165.0f, iw * 0.42f));
        float gridGap = 10.0f;
        float gridW = iw - editorW - gridGap;

        int deletedCount = tm.getDeletedPresetCount();
        float restoreH = 22.0f;
        float restoreGap = 6.0f;
        float gridH = deletedCount > 0 ? Math.max(40.0f, ih - restoreH - restoreGap) : ih;

        if (deletedCount > 0 && isInside(mx, my, ix, iy + gridH + restoreGap, gridW - 6.0f, restoreH)) {
            int restored = tm.restoreBuiltinPresets();
            openColorKey = null;
            host.showToast(restored > 0 ? ("Restored built-in themes: " + restored) : "All built-in themes already present");
            return true;
        }

        if (isInside(mx, my, ix, iy, gridW, gridH)) {
            typingName = false;
            int cols = gridW >= 275.0f ? 3 : 2;
            float gap = 6.5f;
            float availW = gridW - 6.0f;
            float tileW = (availW - gap * (cols - 1)) / cols;
            float tileH = 44.0f;
            for (int i = 0; i < themes.size(); i++) {
                float tx = ix + (i % cols) * (tileW + gap);
                float ty = iy + (i / cols) * (tileH + gap) - gridScroll;
                if (isInside(mx, my, tx, ty, tileW, tileH)) {
                    Theme chosen = themes.get(i);
                    tm.setCurrentTheme(chosen);
                    openColorKey = null;
                    host.showToast("Theme: " + chosen.getName());
                    return true;
                }
            }
            return true;
        }

        float ex = ix + gridW + gridGap;
        if (!isInside(mx, my, ex, iy, editorW, ih)) return false;

        float ePad = 9.0f;
        float eix = ex + ePad;
        float eiw = editorW - ePad * 2.0f;
        boolean preset = current.isPreset();

        if (themes.size() > 1 && isInside(mx, my, eix + eiw - 17.0f, iy + 6.0f, 18.0f, 18.0f)) {
            String deleted = current.getName();
            if (tm.deleteTheme(current)) {
                cardAnims.remove(current);
                openColorKey = null;
                host.showToast("Deleted: " + deleted);
            } else {
                host.showToast("Cannot delete the last theme");
            }
            return true;
        }

        float barH = 22.0f;
        float barY = iy + ih - ePad - barH;
        float btnW = 50.0f;
        float fieldW = eiw - btnW - 5.0f;

        if (isInside(mx, my, eix, barY, fieldW, barH)) {
            typingName = true;
            return true;
        }
        typingName = false;

        if (isInside(mx, my, eix + eiw - btnW, barY, btnW, barH)) {
            createTheme(host);
            return true;
        }

        // Check active inline picker drag
        if (!preset && openColorKey != null) {
            float hueY = pickerY + pickerSvH + 4.0f;
            float alphaY = hueY + 8.0f + 4.0f;
            if (isInside(mx, my, pickerX, pickerY, pickerW, pickerSvH)) {
                dragMode = 1;
                updateDrag(mx, my);
                return true;
            }
            if (isInside(mx, my, pickerX, hueY - 2.0f, pickerW, 12.0f)) {
                dragMode = 2;
                updateDrag(mx, my);
                return true;
            }
            if (isInside(mx, my, pickerX, alphaY - 2.0f, pickerW, 12.0f)) {
                dragMode = 3;
                updateDrag(mx, my);
                return true;
            }
        }

        float listY = iy + 42.0f;
        float listBottom = barY - 7.0f;
        float ry = listY;
        float rowH = 19.0f;
        float rowGap = 4.0f;
        for (Map.Entry<String, Integer> entry : current.getElements().entrySet()) {
            String key = entry.getKey();
            if (isInside(mx, my, eix, ry, eiw, rowH) && my < listBottom) {
                if (preset) {
                    host.showToast("Create a custom copy to edit colors");
                } else {
                    openColorKey = key.equals(openColorKey) ? null : key;
                }
                return true;
            }
            ry += rowH + rowGap;
            if (!preset && key.equals(openColorKey)) {
                ry += 44.0f + 4.0f + 8.0f + 4.0f + 8.0f + 4.0f;
            }
        }
        return true;
    }

    public boolean mouseDragged(double mx, double my) {
        if (dragMode == 0 || openColorKey == null) return false;
        updateDrag(mx, my);
        return true;
    }

    public void mouseReleased() {
        if (dragMode != 0) {
            Theme current = ThemeManager.getInstance().getCurrentTheme();
            ThemeManager.getInstance().saveCustomTheme(current);
            dragMode = 0;
        }
    }

    private void updateDrag(double mx, double my) {
        ThemeManager tm = ThemeManager.getInstance();
        Theme current = tm.getCurrentTheme();
        if (current.isPreset() || openColorKey == null) return;
        int col = current.getElementColor(openColorKey, 0xFF2DD4BF);
        float[] hsb = ColorUtils.rgbToHsb(col);
        int alphaByte = (col >>> 24) & 0xFF;

        float h = hsb[0];
        float s = hsb[1];
        float b = hsb[2];
        if (dragMode == 1) {
            s = Mth.clamp((float) ((mx - pickerX) / Math.max(1.0f, pickerW)), 0.0f, 1.0f);
            b = 1.0f - Mth.clamp((float) ((my - pickerY) / Math.max(1.0f, pickerSvH)), 0.0f, 1.0f);
        } else if (dragMode == 2) {
            h = Mth.clamp((float) ((mx - pickerX) / Math.max(1.0f, pickerW)), 0.0f, 0.9999f);
        } else if (dragMode == 3) {
            alphaByte = Mth.clamp(Math.round((float) ((mx - pickerX) / Math.max(1.0f, pickerW)) * 255.0f), 15, 255);
        }
        current.setElementColor(openColorKey, ColorUtils.hsbToRgb(h, s, b, alphaByte));
    }

    private void createTheme(ClickGuiScreen host) {
        Theme created = ThemeManager.getInstance().createCustomTheme(newName.toString());
        if (created == null) {
            host.showToast("Reserved theme name: deleted_presets");
            return;
        }
        newName.setLength(0);
        typingName = false;
        openColorKey = null;
        gridTargetScroll = Float.MAX_VALUE;
        host.showToast("Created: " + created.getName());
    }

    public boolean mouseScrolled(double verticalAmount) {
        gridTargetScroll = Mth.clamp(gridTargetScroll - (float) (verticalAmount * 28.0f), 0.0f, gridMaxScroll);
        return true;
    }

    public boolean keyPressed(int key, ClickGuiScreen host) {
        if (!typingName) return false;
        if (key == InputConstants.KEY_BACKSPACE && !newName.isEmpty()) {
            newName.deleteCharAt(newName.length() - 1);
        } else if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) {
            createTheme(host);
        } else if (key == InputConstants.KEY_ESCAPE) {
            typingName = false;
        }
        return true;
    }

    public boolean charTyped(String ch) {
        if (!typingName || ch == null || ch.isEmpty()) return false;
        char chr = ch.charAt(0);
        if (newName.length() < 20 && (Character.isLetterOrDigit(chr) || chr == '_' || chr == '-' || chr == ' ')) {
            newName.append(chr);
        }
        return true;
    }

    private static boolean isInside(double mx, double my, float x, float y, float w, float h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }
}
