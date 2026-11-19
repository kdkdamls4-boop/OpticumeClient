package dev.opticumeclient.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.opticumeclient.client.modules.Module;
import dev.opticumeclient.client.render.ColorUtils;
import dev.opticumeclient.client.render.Render2DHelper;
import dev.opticumeclient.client.render.TextureAssets;
import dev.opticumeclient.client.render.ThemeManager;
import dev.opticumeclient.client.setting.BindSetting;
import dev.opticumeclient.client.setting.BooleanSetting;
import dev.opticumeclient.client.setting.ColorSetting;
import dev.opticumeclient.client.setting.ModeSetting;
import dev.opticumeclient.client.setting.MultiBoxSetting;
import dev.opticumeclient.client.setting.NumberSetting;
import dev.opticumeclient.client.setting.RunSetting;
import dev.opticumeclient.client.setting.Setting;
import dev.opticumeclient.client.setting.TextSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Interactive module card featuring Desire {@code LightSetting}-style segmented chip grids,
 * glowing active outlines, thin sliders with white dots, compact square checkboxes,
 * inline Text/Run/Color controls, breadcrumb path header, and Bind footer row.
 */
public final class SystemModuleCard {
    public static final float HEADER_HEIGHT = 32.0f;
    private static final float PAD_X = 9.0f;
    private static final float SETTING_GAP = 6.0f;
    private static final float SEG_CELL_H = 17.0f;

    public final Module module;
    public float x;
    public float y;
    public float width;

    private float toggleAnim;
    private float expandAnim;
    private float hoverAnim;
    private float flashAnim;

    private final Map<BooleanSetting, Float> boolAnims = new HashMap<>();
    private final Map<NumberSetting, Float> sliderVisual = new HashMap<>();
    private final Map<Setting<?>, float[]> segCellAnims = new HashMap<>();
    private final Map<RunSetting, float[]> runAnims = new HashMap<>();
    private ColorSetting expandedColorPicker;
    private ModeSetting expandedLargeMode;

    public SystemModuleCard(Module module) {
        this.module = module;
        this.toggleAnim = module.isEnabled() ? 1.0f : 0.0f;
        this.expandAnim = module.isExpanded() ? 1.0f : 0.0f;
    }

    public boolean hasVisibleSettings() {
        for (Setting<?> s : module.getSettings()) {
            if (s.isVisible()) return true;
        }
        return false;
    }

    public void triggerFlash() {
        this.flashAnim = 1.0f;
    }

    public void closePopups() {
        this.expandedLargeMode = null;
    }

    public float computeHeight() {
        if (!hasVisibleSettings() || expandAnim <= 0.002f) {
            return HEADER_HEIGHT;
        }
        float settingsH = computeExpandedBodyHeight();
        return HEADER_HEIGHT + (1.0f + settingsH) * expandAnim;
    }

    private float computeExpandedBodyHeight() {
        float h = 6.0f + 22.0f; // breadcrumb Category / Module + info line
        float innerW = Math.max(80.0f, width - PAD_X * 2.0f);
        for (Setting<?> s : module.getSettings()) {
            if (!s.isVisible()) continue;
            h += getSettingHeight(s, innerW) + SETTING_GAP;
        }
        h += 17.0f + 7.0f; // Bind row + bottom padding
        return h;
    }

    private float getSettingHeight(Setting<?> s, float innerW) {
        if (s instanceof BooleanSetting) return 16.0f;
        if (s instanceof BindSetting) return 18.0f;
        if (s instanceof NumberSetting) return 24.0f;
        if (s instanceof RunSetting) return 18.0f;
        if (s instanceof TextSetting) return 20.0f;
        if (s instanceof ModeSetting ms) {
            List<String> modes = ms.getModes();
            if (modes.size() <= 6) {
                int cols = computeSegmentedCols(modes, innerW);
                int rows = Math.max(1, (modes.size() + cols - 1) / cols);
                return 13.0f + rows * SEG_CELL_H + 2.0f;
            }
            if (ms == expandedLargeMode) {
                int cols = computeSegmentedCols(modes, innerW);
                int rows = Math.max(1, (modes.size() + cols - 1) / cols);
                return 18.0f + 3.0f + rows * SEG_CELL_H + 2.0f;
            }
            return 18.0f;
        }
        if (s instanceof MultiBoxSetting mbs) {
            List<String> opts = mbs.getOptions();
            int cols = computeSegmentedCols(opts, innerW);
            int rows = Math.max(1, (opts.size() + cols - 1) / cols);
            return 13.0f + rows * SEG_CELL_H + 2.0f;
        }
        if (s instanceof ColorSetting cs) {
            return (cs == expandedColorPicker) ? 124.0f : 18.0f;
        }
        return 16.0f;
    }

    private static int computeSegmentedCols(List<String> options, float width) {
        int n = options.size();
        if (n <= 1) return 1;
        if (n == 2) return 2;
        float widest = 0.0f;
        for (String opt : options) {
            widest = Math.max(widest, Render2DHelper.getTextWidth(opt, 0.68f) + 14.0f);
        }
        widest = Math.max(widest, 36.0f);
        int fit = Math.max(1, Math.min(n, (int) (width / widest)));
        fit = Math.min(fit, 3);
        int rows = (n + fit - 1) / fit;
        return Math.max(1, (n + rows - 1) / rows);
    }

    public void updateAnimations(float dt, float mouseX, float mouseY, boolean allowHover) {
        float tRate = 1.0f - (float) Math.exp(-15.0f * dt);
        float eRate = 1.0f - (float) Math.exp(-13.0f * dt);

        toggleAnim += ((module.isEnabled() ? 1.0f : 0.0f) - toggleAnim) * tRate;
        boolean wantExpand = module.isExpanded() && hasVisibleSettings();
        expandAnim += ((wantExpand ? 1.0f : 0.0f) - expandAnim) * eRate;
        if (Math.abs((wantExpand ? 1.0f : 0.0f) - expandAnim) < 0.002f) {
            expandAnim = wantExpand ? 1.0f : 0.0f;
        }

        boolean hovered = allowHover && isInside(mouseX, mouseY, x, y, width, HEADER_HEIGHT);
        hoverAnim += ((hovered ? 1.0f : 0.0f) - hoverAnim) * (1.0f - (float) Math.exp(-12.0f * dt));

        if (flashAnim > 0.0f) {
            flashAnim = Math.max(0.0f, flashAnim - dt * 1.1f);
        }
    }

    public void render(
            GuiGraphicsExtractor context,
            float dt,
            float mouseX,
            float mouseY,
            float alpha,
            boolean isFavorite,
            boolean isBinding,
            ClickGuiScreen host,
            ClickGuiScreen.HoverTarget outHover
    ) {
        float h = computeHeight();
        float radius = 9.0f;

        Render2DHelper.drawRoundedRectFloat(context, x, y, width, h, radius, SystemTheme.fade(SystemTheme.bg(), alpha));

        int baseBorder = SystemTheme.border();
        int accent = ThemeManager.getInstance().getAccentColor(0);
        int activeOutline = module.isEnabled()
                ? ColorUtils.interpolateColor(baseBorder, accent, 0.35f)
                : baseBorder;
        Render2DHelper.drawRoundedOutlineFloat(context, x, y, width, h, radius, 1.0f, SystemTheme.fade(activeOutline, alpha));

        if (flashAnim > 0.01f) {
            Render2DHelper.drawRoundedOutlineFloat(context, x, y, width, h, radius, 1.2f,
                    ColorUtils.withAlphaFactor(accent, alpha * flashAnim));
        }

        renderHeader(context, alpha, mouseX, mouseY, isFavorite, isBinding, accent, outHover);

        if (hasVisibleSettings() && expandAnim > 0.01f) {
            float divY = y + HEADER_HEIGHT;
            float bodyAlpha = alpha * expandAnim;
            Render2DHelper.drawRect(context, x + PAD_X, divY, width - PAD_X * 2.0f, 1.0f,
                    SystemTheme.fade(SystemTheme.border(), bodyAlpha));

            boolean clipDuringAnim = expandAnim < 0.995f;
            if (clipDuringAnim) {
                int sy1 = Math.round(divY + 1.0f);
                int sy2 = Math.max(sy1, Math.round(y + h - 3.0f));
                context.enableScissor(Math.round(x + 1), sy1, Math.round(x + width - 1), sy2);
            }

            float innerW = width - PAD_X * 2.0f;
            float sy = divY + 6.0f;
            sy = renderBreadcrumbHeader(context, x + PAD_X, sy, innerW, accent, bodyAlpha);

            for (Setting<?> s : module.getSettings()) {
                if (!s.isVisible()) continue;
                float sh = getSettingHeight(s, innerW);
                renderSetting(context, dt, s, x + PAD_X, sy, innerW, mouseX, mouseY, bodyAlpha, accent, host, outHover);
                sy += sh + SETTING_GAP;
            }

            renderBindFooter(context, x + PAD_X, sy, innerW, mouseX, mouseY, isBinding, accent, bodyAlpha);

            if (clipDuringAnim) {
                context.disableScissor();
            }
        }
    }

    private float renderBreadcrumbHeader(GuiGraphicsExtractor context, float sx, float sy, float sw,
                                         int accent, float alpha) {
        // 2x2 grid glyph + Category / Module
        float cell = 2.8f;
        float gap = 1.2f;
        int acc = ColorUtils.withAlphaFactor(accent, alpha);
        for (int i = 0; i < 4; i++) {
            float bx = sx + (i % 2) * (cell + gap);
            float by = sy + 1.0f + (i / 2) * (cell + gap);
            Render2DHelper.drawRoundedRectFloat(context, bx, by, cell, cell, 0.7f, acc);
        }

        float tx = sx + cell * 2.0f + gap + 5.0f;
        String catPrefix = module.getCategory().getDisplayName() + " / ";
        float catW = Render2DHelper.getTextWidth(catPrefix, 0.72f);
        Render2DHelper.drawScaledText(context, catPrefix, tx, sy + 0.5f,
                SystemTheme.fade(SystemTheme.foreground(), alpha), 0.72f, false);
        Render2DHelper.drawScaledText(context, Render2DHelper.trimToWidth(module.getName(), 0.72f, Math.max(20.0f, sx + sw - (tx + catW))),
                tx + catW, sy + 0.5f, acc, 0.72f, false);

        float iy = sy + 10.5f;
        Render2DHelper.drawTextureFloat(context, TextureAssets.DESIRE_GUI_INFO, sx, iy, 6.5f, 6.5f,
                SystemTheme.fade(SystemTheme.mutedFg(), alpha * 0.65f));
        String desc = (module.getDescription() != null && !module.getDescription().isBlank())
                ? module.getDescription() : module.getSettings().size() + " settings";
        Render2DHelper.drawScaledText(context, Render2DHelper.trimToWidth(desc, 0.63f, sw - 10.0f),
                sx + 9.5f, iy, SystemTheme.fade(SystemTheme.mutedFg(), alpha * 0.65f), 0.63f, false);

        return sy + 22.0f;
    }

    private void renderBindFooter(GuiGraphicsExtractor context, float sx, float sy, float sw,
                                  float mouseX, float mouseY, boolean isBinding, int accent, float alpha) {
        Render2DHelper.drawScaledText(context, "Bind", sx, sy + 4.0f,
                SystemTheme.fade(SystemTheme.foreground(), alpha), 0.76f, false);

        String label = isBinding ? "Press key" : (module.getKeyBind() > 0 ? getKeyName(module.getKeyBind()) : "None");
        float txtW = Render2DHelper.getTextWidth(label, 0.74f);
        boolean active = isBinding || module.getKeyBind() > 0;
        boolean hov = isInside(mouseX, mouseY, sx, sy, sw, 16.0f);

        float pulse = isBinding ? (0.55f + 0.45f * (float) Math.sin(System.currentTimeMillis() / 160.0)) : 1.0f;
        int col = active ? ColorUtils.withAlphaFactor(accent, alpha * pulse)
                : SystemTheme.fade(hov ? SystemTheme.foreground() : SystemTheme.mutedFg(), alpha * 0.8f);

        Render2DHelper.drawScaledText(context, label, sx + sw - txtW, sy + 4.0f, col, 0.74f, false);
        Render2DHelper.drawTextureFloat(context, TextureAssets.DESIRE_GUI_KEYBOARD_BIG,
                sx + sw - txtW - 12.0f, sy + 3.0f, 9.0f, 9.0f, col);
    }

    private void renderHeader(
            GuiGraphicsExtractor context,
            float alpha,
            float mouseX,
            float mouseY,
            boolean isFavorite,
            boolean isBinding,
            int accent,
            ClickGuiScreen.HoverTarget outHover
    ) {
        float switchW = 22.0f;
        float switchH = 12.0f;
        float switchX = x + width - PAD_X - switchW;
        float switchY = y + (HEADER_HEIGHT - switchH) * 0.5f;

        float starSize = 10.5f;
        float starX = switchX - 5.0f - starSize;
        float starY = y + (HEADER_HEIGHT - starSize) * 0.5f;
        float starAlpha = isFavorite ? 1.0f : (0.18f + 0.55f * hoverAnim);
        int starColor = isFavorite
                ? SystemTheme.fade(SystemTheme.FAVORITE_GOLD, alpha * starAlpha)
                : SystemTheme.fade(SystemTheme.mutedFg(), alpha * starAlpha);
        Render2DHelper.drawTextureFloat(context,
                isFavorite ? TextureAssets.ICON_STAR_FILLED : TextureAssets.ICON_FAVORITES,
                starX, starY, starSize, starSize, starColor);

        float rightCursor = starX - 4.0f;
        boolean showBind = isBinding || module.getKeyBind() > 0 || hoverAnim > 0.15f;
        if (showBind) {
            String bindText = isBinding ? "..." : (module.getKeyBind() > 0 ? getKeyName(module.getKeyBind()) : "+");
            float bindScale = 0.66f;
            float bw = Math.max(14.0f, Render2DHelper.getTextWidth(bindText, bindScale) + 6.0f);
            float bh = 11.5f;
            float bx = rightCursor - bw;
            float by = y + (HEADER_HEIGHT - bh) * 0.5f;
            float bAlpha = alpha * (isBinding || module.getKeyBind() > 0 ? 1.0f : hoverAnim);
            int bFill = isBinding ? accent : ColorUtils.withAlpha(accent, 42);
            int bText = isBinding ? 0xFFFFFFFF : accent;
            Render2DHelper.drawRoundedRectFloat(context, bx, by, bw, bh, 3.0f, ColorUtils.withAlphaFactor(bFill, bAlpha));
            Render2DHelper.drawScaledText(context, bindText,
                    bx + (bw - Render2DHelper.getTextWidth(bindText, bindScale)) * 0.5f, by + 2.8f,
                    ColorUtils.withAlphaFactor(bText, bAlpha), bindScale, false);
            rightCursor = bx - 4.0f;
        }

        // Toggle pill using client accent
        int trackColor = ColorUtils.interpolateColor(SystemTheme.elevated(), accent, toggleAnim);
        int trackOutline = ColorUtils.interpolateColor(SystemTheme.border(), accent, toggleAnim);
        Render2DHelper.drawRoundedRectFloat(context, switchX, switchY, switchW, switchH, 6.0f,
                ColorUtils.withAlphaFactor(trackColor, alpha));
        Render2DHelper.drawRoundedOutlineFloat(context, switchX, switchY, switchW, switchH, 6.0f, 1.0f,
                ColorUtils.withAlphaFactor(trackOutline, alpha));

        float knobSize = 8.0f;
        float knobX = switchX + 2.0f + toggleAnim * (switchW - knobSize - 4.0f);
        float knobY = switchY + (switchH - knobSize) * 0.5f;
        int knobCol = ColorUtils.interpolateColor(SystemTheme.mutedFg(), 0xFFFFFFFF, toggleAnim);
        Render2DHelper.drawCircle(context, knobX + knobSize * 0.5f, knobY + knobSize * 0.5f, knobSize * 0.5f,
                ColorUtils.withAlphaFactor(knobCol, alpha));

        float nameScale = 0.86f;
        float maxTextW = Math.max(24.0f, rightCursor - (x + PAD_X) - 2.0f);
        String name = Render2DHelper.trimToWidth(module.getName(), nameScale, maxTextW);
        int titleCol = ColorUtils.interpolateColor(
                ColorUtils.interpolateColor(SystemTheme.mutedFg(), SystemTheme.foreground(), 0.55f),
                accent,
                toggleAnim * 0.72f
        );
        Render2DHelper.drawScaledText(context, name, x + PAD_X, y + 10.0f,
                ColorUtils.withAlphaFactor(titleCol, alpha), nameScale, false);

        if (hasVisibleSettings() && expandAnim < 0.05f) {
            Render2DHelper.drawCircle(context, x + PAD_X + Render2DHelper.getTextWidth(name, nameScale) + 5.0f,
                    y + HEADER_HEIGHT * 0.5f, 1.4f, SystemTheme.fade(SystemTheme.mutedFg(), alpha * 0.7f));
        }

        if (isInside(mouseX, mouseY, x, y, width, HEADER_HEIGHT) && module.getDescription() != null && !module.getDescription().isBlank()) {
            outHover.title = module.getName();
            outHover.description = module.getDescription();
        }
    }

    private void renderSetting(
            GuiGraphicsExtractor context,
            float dt,
            Setting<?> s,
            float sx,
            float sy,
            float sw,
            float mouseX,
            float mouseY,
            float alpha,
            int accent,
            ClickGuiScreen host,
            ClickGuiScreen.HoverTarget outHover
    ) {
        float labelScale = 0.76f;

        if (isInside(mouseX, mouseY, sx, sy, sw, getSettingHeight(s, sw)) && s.getDescription() != null && !s.getDescription().isBlank()) {
            outHover.title = s.getName();
            outHover.description = s.getDescription();
        }

        // 1. Boolean -> Desire square-pill checkbox with centered white dot
        if (s instanceof BooleanSetting bs) {
            float anim = boolAnims.getOrDefault(bs, bs.isEnabled() ? 1.0f : 0.0f);
            anim += ((bs.isEnabled() ? 1.0f : 0.0f) - anim) * (1.0f - (float) Math.exp(-16.0f * dt));
            boolAnims.put(bs, anim);

            boolean hov = isInside(mouseX, mouseY, sx, sy, sw, 16.0f);
            String label = Render2DHelper.trimToWidth(bs.getName(), labelScale, sw - 20.0f);
            Render2DHelper.drawScaledText(context, label, sx, sy + 4.0f,
                    SystemTheme.fade(SystemTheme.foreground(), alpha), labelScale, false);

            float boxS = 12.5f;
            float bx = sx + sw - boxS;
            float by = sy + 1.5f;
            int offBox = ColorUtils.withAlpha(accent, hov ? 64 : 46);
            int boxCol = ColorUtils.interpolateColor(offBox, accent, anim);
            Render2DHelper.drawRoundedRectFloat(context, bx, by, boxS, boxS, 3.6f,
                    ColorUtils.withAlphaFactor(boxCol, alpha));
            if (anim > 0.05f) {
                Render2DHelper.drawCircle(context, bx + boxS * 0.5f, by + boxS * 0.5f, 2.3f * anim,
                        ColorUtils.rgba(255, 255, 255, Math.round(255.0f * alpha * anim)));
            }
            return;
        }

        // 2. Number -> Desire thin accent track + white circular dot knob + right value
        if (s instanceof NumberSetting ns) {
            double range = Math.max(1.0E-6, ns.getMax() - ns.getMin());
            float targetFrac = (float) Mth.clamp((ns.getValue() - ns.getMin()) / range, 0.0, 1.0);
            float vis = sliderVisual.getOrDefault(ns, targetFrac);
            vis += (targetFrac - vis) * (1.0f - (float) Math.exp(-20.0f * dt));
            sliderVisual.put(ns, vis);

            String valStr = ns.getStep() >= 1.0
                    ? String.valueOf(Math.round(ns.getValue()))
                    : String.format(Locale.ROOT, "%.2f", ns.getValue());
            float valW = Render2DHelper.getTextWidth(valStr, 0.74f);
            String label = Render2DHelper.trimToWidth(ns.getName(), labelScale, sw - valW - 8.0f);
            Render2DHelper.drawScaledText(context, label, sx, sy + 1.0f,
                    SystemTheme.fade(SystemTheme.foreground(), alpha), labelScale, false);
            Render2DHelper.drawScaledText(context, valStr, sx + sw - valW, sy + 1.0f,
                    ColorUtils.withAlphaFactor(accent, alpha), 0.74f, false);

            float trackY = sy + 14.5f;
            float trackH = 2.6f;
            boolean hov = isInside(mouseX, mouseY, sx - 2.0f, trackY - 5.0f, sw + 4.0f, 12.0f);
            int trackBg = ColorUtils.withAlpha(ColorUtils.interpolateColor(0xFFFFFFFF, accent, 0.14f), Math.round(24 * alpha));
            Render2DHelper.drawRoundedRectFloat(context, sx, trackY, sw, trackH, trackH * 0.5f, trackBg);
            float fillW = Math.max(0.0f, sw * vis);
            if (fillW > 0.8f) {
                Render2DHelper.drawRoundedRectFloat(context, sx, trackY, fillW, trackH, trackH * 0.5f,
                        ColorUtils.withAlphaFactor(accent, alpha));
            }
            float knobR = hov ? 3.5f : 2.9f;
            float kx = Mth.clamp(sx + fillW, sx + knobR, sx + sw - knobR);
            Render2DHelper.drawCircle(context, kx, trackY + trackH * 0.5f, knobR,
                    ColorUtils.rgba(255, 255, 255, Math.round(255.0f * alpha)));
            return;
        }

        // 3. Mode -> Segmented Pill Grid (when <=6 modes) OR Expandable Segmented Grid (>6 modes)
        if (s instanceof ModeSetting ms) {
            String curMode = ms.getMode() != null ? ms.getMode() : "";
            List<String> modes = ms.getModes();
            float curW = Render2DHelper.getTextWidth(curMode, 0.72f);
            String label = Render2DHelper.trimToWidth(ms.getName(), labelScale, Math.max(24.0f, sw - curW - 8.0f));
            Render2DHelper.drawScaledText(context, label, sx, sy + 1.0f,
                    SystemTheme.fade(SystemTheme.foreground(), alpha), labelScale, false);

            if (modes.size() <= 6) {
                Render2DHelper.drawScaledText(context, curMode, sx + sw - curW, sy + 1.0f,
                        ColorUtils.withAlphaFactor(accent, alpha), 0.72f, false);
                renderSegmentedGrid(context, dt, ms, modes, -1, ms.getIndex(),
                        sx, sy + 13.0f, sw, mouseX, mouseY, alpha, accent, true);
            } else {
                float chipW = Math.min(sw * 0.58f, Math.max(46.0f, curW + 18.0f));
                float chipX = sx + sw - chipW;
                boolean open = (ms == expandedLargeMode);
                Render2DHelper.drawRoundedRectFloat(context, chipX, sy + 0.5f, chipW, 15.0f, 4.5f,
                        ColorUtils.withAlphaFactor(ColorUtils.withAlpha(accent, open ? 56 : 30), alpha));
                Render2DHelper.drawRoundedOutlineFloat(context, chipX, sy + 0.5f, chipW, 15.0f, 4.5f, 0.9f,
                        ColorUtils.withAlphaFactor(accent, alpha * (open ? 0.9f : 0.45f)));
                Render2DHelper.drawScaledText(context, Render2DHelper.trimToWidth(curMode, 0.68f, chipW - 14.0f),
                        chipX + 5.0f, sy + 4.5f, ColorUtils.withAlphaFactor(accent, alpha), 0.68f, false);
                Render2DHelper.drawScaledText(context, open ? "▲" : "▼", chipX + chipW - 9.0f, sy + 4.5f,
                        ColorUtils.withAlphaFactor(accent, alpha * 0.8f), 0.58f, false);
                if (open) {
                    renderSegmentedGrid(context, dt, ms, modes, -1, ms.getIndex(),
                            sx, sy + 18.5f, sw, mouseX, mouseY, alpha, accent, true);
                }
            }
            return;
        }

        // 4. MultiBox -> Desire Segmented Chip Grid + `selected/total` counter
        if (s instanceof MultiBoxSetting mbs) {
            List<String> opts = mbs.getOptions();
            int onCount = 0;
            for (String o : opts) {
                if (mbs.isEnabled(o)) onCount++;
            }
            String countStr = onCount + "/" + opts.size();
            float cntW = Render2DHelper.getTextWidth(countStr, 0.72f);
            Render2DHelper.drawScaledText(context, Render2DHelper.trimToWidth(mbs.getName(), labelScale, sw - cntW - 8.0f),
                    sx, sy + 1.0f, SystemTheme.fade(SystemTheme.foreground(), alpha), labelScale, false);
            Render2DHelper.drawScaledText(context, countStr, sx + sw - cntW, sy + 1.0f,
                    ColorUtils.withAlphaFactor(accent, alpha), 0.72f, false);

            renderSegmentedGrid(context, dt, mbs, opts, -1, -1,
                    sx, sy + 13.0f, sw, mouseX, mouseY, alpha, accent, false);
            return;
        }

        // 5. TextSetting -> Rounded input field
        if (s instanceof TextSetting ts) {
            float fw = Math.min(110.0f, sw * 0.58f);
            float fx = sx + sw - fw;
            boolean focused = host != null && host.isTextSettingFocused(ts);
            Render2DHelper.drawScaledText(context, Render2DHelper.trimToWidth(ts.getName(), labelScale, sw - fw - 8.0f),
                    sx, sy + 5.5f, SystemTheme.fade(SystemTheme.foreground(), alpha), labelScale, false);
            Render2DHelper.drawRoundedRectFloat(context, fx, sy + 1.0f, fw, 17.0f, 4.5f,
                    ColorUtils.withAlphaFactor(ColorUtils.withAlpha(accent, focused ? 42 : 28), alpha));
            if (focused) {
                Render2DHelper.drawRoundedOutlineFloat(context, fx, sy + 1.0f, fw, 17.0f, 4.5f, 0.9f,
                        ColorUtils.withAlphaFactor(accent, alpha * 0.75f));
            }
            String val = ts.getText();
            String disp = val.isEmpty() && !focused ? ts.getPlaceholder() : val;
            int col = val.isEmpty() && !focused ? SystemTheme.mutedFg() : SystemTheme.foreground();
            String trim = Render2DHelper.trimToWidth(disp, 0.68f, fw - 10.0f);
            Render2DHelper.drawScaledText(context, trim, fx + 5.0f, sy + 6.0f, SystemTheme.fade(col, alpha), 0.68f, false);
            if (focused && (System.currentTimeMillis() / 500L) % 2L == 0L) {
                float cx = fx + 5.0f + Render2DHelper.getTextWidth(trim, 0.68f) + 1.0f;
                Render2DHelper.drawRect(context, cx, sy + 4.0f, 1.0f, 10.0f, ColorUtils.withAlphaFactor(accent, alpha));
            }
            return;
        }

        // 6. BindSetting -> Key/Mouse bind row matching Desire LightSetting
        if (s instanceof BindSetting bs) {
            boolean isBindActive = host != null && host.isBindingSettingActive(bs);
            String valStr = isBindActive ? "Press key" : bs.getBindLabel();
            float txtW = Render2DHelper.getTextWidth(valStr, 0.72f);
            float labelMax = Math.max(24.0f, sw - txtW - 18.0f);
            Render2DHelper.drawScaledText(context, Render2DHelper.trimToWidth(bs.getName(), labelScale, labelMax),
                    sx, sy + 4.0f, SystemTheme.fade(SystemTheme.foreground(), alpha), labelScale, false);

            boolean active = isBindActive || bs.isBound();
            boolean hov = isInside(mouseX, mouseY, sx, sy, sw, 18.0f);
            float pulse = isBindActive ? (0.55f + 0.45f * (float) Math.sin(System.currentTimeMillis() / 160.0)) : 1.0f;
            int col = active ? ColorUtils.withAlphaFactor(accent, alpha * pulse)
                    : SystemTheme.fade(hov ? SystemTheme.foreground() : SystemTheme.mutedFg(), alpha * 0.8f);
            Render2DHelper.drawScaledText(context, valStr, sx + sw - txtW, sy + 4.0f, col, 0.72f, false);
            Render2DHelper.drawTextureFloat(context, TextureAssets.DESIRE_GUI_KEYBOARD_BIG,
                    sx + sw - txtW - 12.0f, sy + 3.0f, 9.0f, 9.0f, col);
            return;
        }

        // 7. RunSetting -> Accent action button ("Run")
        if (s instanceof RunSetting rs) {
            float[] anim = runAnims.computeIfAbsent(rs, k -> new float[2]);
            float bw = 46.0f;
            float bh = 16.0f;
            float bx = sx + sw - bw;
            boolean hov = isInside(mouseX, mouseY, bx, sy + 1.0f, bw, bh);
            anim[0] += ((hov ? 1.0f : 0.0f) - anim[0]) * (1.0f - (float) Math.exp(-18.0f * dt));
            anim[1] += (0.0f - anim[1]) * (1.0f - (float) Math.exp(-7.0f * dt));
            Render2DHelper.drawScaledText(context, Render2DHelper.trimToWidth(rs.getName(), labelScale, sw - bw - 8.0f),
                    sx, sy + 4.5f, SystemTheme.fade(SystemTheme.foreground(), alpha), labelScale, false);
            int fill = ColorUtils.interpolateColor(accent, 0xFFFFFFFF, Math.min(0.6f, anim[0] * 0.25f + anim[1] * 0.5f));
            Render2DHelper.drawRoundedRectFloat(context, bx, sy + 1.0f, bw, bh, 4.5f,
                    ColorUtils.withAlphaFactor(fill, alpha));
            Render2DHelper.drawCenteredText(context, "Run", bx + bw * 0.5f, sy + 5.0f,
                    ColorUtils.rgba(255, 255, 255, Math.round(255.0f * alpha)), false);
            return;
        }

        // 7. ColorSetting -> THM / RBW / Swatch + inline HSB+Alpha picker
        if (s instanceof ColorSetting cs) {
            float swatchW = 20.0f;
            float swatchH = 12.0f;
            float rbwW = 22.0f;
            float thmW = 22.0f;
            float swatchX = sx + sw - swatchW;
            float rbwX = swatchX - rbwW - 3.0f;
            float thmX = rbwX - thmW - 3.0f;
            float topY = sy + 2.0f;

            Render2DHelper.drawScaledText(context, Render2DHelper.trimToWidth(cs.getName(), labelScale, thmX - sx - 6.0f),
                    sx, sy + 4.5f, SystemTheme.fade(SystemTheme.foreground(), alpha), labelScale, false);

            int thmFill = cs.isThemeSync() ? accent : SystemTheme.elevated();
            int thmTextCol = cs.isThemeSync() ? contrastTextOn(accent) : SystemTheme.mutedFg();
            Render2DHelper.drawRoundedRectFloat(context, thmX, topY, thmW, swatchH, 3.0f,
                    ColorUtils.withAlphaFactor(thmFill, alpha));
            if (!cs.isThemeSync()) {
                Render2DHelper.drawRoundedOutlineFloat(context, thmX, topY, thmW, swatchH, 3.0f, 0.8f,
                        SystemTheme.fade(SystemTheme.border(), alpha));
            }
            Render2DHelper.drawScaledText(context, "THM", thmX + (thmW - Render2DHelper.getTextWidth("THM", 0.56f)) * 0.5f,
                    topY + 3.0f, ColorUtils.withAlphaFactor(thmTextCol, alpha), 0.56f, false);

            int rbwAccent = ColorUtils.getRainbow(3.5f, 0.80f, 0.96f, 0L);
            int rbwFill = cs.isRainbow() ? rbwAccent : SystemTheme.elevated();
            int rbwTextCol = cs.isRainbow() ? contrastTextOn(rbwAccent) : SystemTheme.mutedFg();
            Render2DHelper.drawRoundedRectFloat(context, rbwX, topY, rbwW, swatchH, 3.0f,
                    ColorUtils.withAlphaFactor(rbwFill, alpha));
            if (!cs.isRainbow()) {
                Render2DHelper.drawRoundedOutlineFloat(context, rbwX, topY, rbwW, swatchH, 3.0f, 0.8f,
                        SystemTheme.fade(SystemTheme.border(), alpha));
            }
            Render2DHelper.drawScaledText(context, "RBW", rbwX + (rbwW - Render2DHelper.getTextWidth("RBW", 0.56f)) * 0.5f,
                    topY + 3.0f, ColorUtils.withAlphaFactor(rbwTextCol, alpha), 0.56f, false);

            TextureAssets.ensureInitialized();
            int liveColor = cs.getColor();
            Render2DHelper.drawTextureRegion(context, TextureAssets.COLOR_ALPHA_CHECKER, swatchX, topY, swatchW, swatchH,
                    0.0f, 0.0f, Math.round(swatchW), Math.round(swatchH), 128, 16,
                    ColorUtils.rgba(255, 255, 255, Math.round(255 * alpha)));
            Render2DHelper.drawRect(context, swatchX, topY, swatchW, swatchH, ColorUtils.withAlphaFactor(liveColor, alpha));
            Render2DHelper.drawRoundedOutlineFloat(context, swatchX, topY, swatchW, swatchH, 3.0f, 0.9f,
                    ColorUtils.withAlphaFactor(cs == expandedColorPicker ? accent : SystemTheme.border(), alpha));

            if (cs == expandedColorPicker) {
                renderInlineColorPicker(context, cs, sx, sy + 19.0f, sw, accent, alpha);
            }
        }
    }

    private void renderSegmentedGrid(
            GuiGraphicsExtractor context,
            float dt,
            Setting<?> setting,
            List<String> options,
            int unused,
            int selectedModeIndex,
            float sx,
            float gridTopY,
            float sw,
            float mouseX,
            float mouseY,
            float alpha,
            int accent,
            boolean isMode
    ) {
        if (options.isEmpty()) return;
        int cols = computeSegmentedCols(options, sw);
        int rows = Math.max(1, (options.size() + cols - 1) / cols);
        float cellW = sw / cols;
        float totalH = rows * SEG_CELL_H;

        int containerCol = isMode
                ? ColorUtils.withAlpha(accent, Math.round(30.0f * alpha))
                : ColorUtils.withAlpha(ColorUtils.interpolateColor(0xFFFFFFFF, accent, 0.14f), Math.round(16.0f * alpha));
        Render2DHelper.drawRoundedRectFloat(context, sx, gridTopY, sw, totalH, 5.0f, containerCol);

        float[] anims = segCellAnims.computeIfAbsent(setting, k -> new float[options.size()]);
        if (anims.length != options.size()) {
            anims = new float[options.size()];
            segCellAnims.put(setting, anims);
        }

        MultiBoxSetting mbs = setting instanceof MultiBoxSetting m ? m : null;
        float rate = 1.0f - (float) Math.exp(-18.0f * dt);

        for (int i = 0; i < options.size(); i++) {
            String opt = options.get(i);
            boolean active = isMode ? (i == selectedModeIndex) : (mbs != null && mbs.isEnabled(opt));
            anims[i] += ((active ? 1.0f : 0.0f) - anims[i]) * rate;

            float cx = sx + (i % cols) * cellW;
            float cy = gridTopY + (i / cols) * SEG_CELL_H;
            boolean hov = isInside(mouseX, mouseY, cx, cy, cellW, SEG_CELL_H);

            float on = anims[i];
            if (on > 0.01f) {
                float pad = 1.2f;
                float bx = cx + pad;
                float by = cy + pad;
                float bw = cellW - pad * 2.0f;
                float bh = SEG_CELL_H - pad * 2.0f;
                float r = 4.2f;

                // Desire signature active chip fill + vertical bottom-up glow + fading neon outline
                Render2DHelper.drawRoundedRectFloat(context, bx, by, bw, bh, r,
                        ColorUtils.withAlpha(accent, Math.round(36.0f * alpha * on)));
                int clearBot = ColorUtils.withAlpha(accent, 0);
                int glowBot = ColorUtils.withAlpha(accent, Math.round(74.0f * alpha * on));
                Render2DHelper.drawGradientRoundedRectFloat(context, bx, by, bw, bh, r,
                        clearBot, glowBot, glowBot, clearBot);

                // Radial bottom bloom
                Render2DHelper.drawTextureFloat(context, TextureAssets.DESIRE_GLOW,
                        bx + bw * 0.05f, by + bh * 0.45f, bw * 0.9f, bh * 0.85f,
                        ColorUtils.withAlpha(accent, Math.round(62.0f * alpha * on)));

                // Top-fading neon outline ("openOutline" slices)
                drawOpenOutline(context, bx, by, bw, bh, r, ColorUtils.withAlpha(accent, Math.round(255.0f * alpha * on)));
            }

            int idleText = hov ? SystemTheme.foreground() : SystemTheme.mutedFg();
            int txtCol = ColorUtils.interpolateColor(idleText, accent, on);
            String trim = Render2DHelper.trimToWidth(opt, 0.68f, cellW - 7.0f);
            float tw = Render2DHelper.getTextWidth(trim, 0.68f);
            Render2DHelper.drawScaledText(context, trim, cx + (cellW - tw) * 0.5f, cy + (SEG_CELL_H - 6.5f) * 0.5f,
                    ColorUtils.withAlphaFactor(txtCol, alpha), 0.68f, false);
        }
    }

    private static void drawOpenOutline(GuiGraphicsExtractor context, float x, float y, float w, float h, float r, int color) {
        int baseA = (color >>> 24) & 0xFF;
        if (baseA <= 2) return;
        int slices = 5;
        float step = h / slices;
        for (int i = 1; i < slices; i++) {
            float t = (float) i / (slices - 1);
            int a = Math.round(baseA * t * t);
            if (a <= 2) continue;
            int sx1 = Math.round(x - 1.5f);
            int sx2 = Math.round(x + w + 1.5f);
            int sy1 = Math.round(y + step * i);
            int sy2 = Math.round(y + step * (i + 1) + (i == slices - 1 ? 1.5f : 0.0f));
            context.enableScissor(sx1, sy1, sx2, sy2);
            Render2DHelper.drawRoundedOutlineFloat(context, x, y, w, h, r, 0.85f, ColorUtils.withAlpha(color, a));
            context.disableScissor();
        }
    }

    private static int contrastTextOn(int bgColor) {
        int r = (bgColor >> 16) & 0xFF;
        int g = (bgColor >> 8) & 0xFF;
        int b = bgColor & 0xFF;
        double luma = 0.299 * r + 0.587 * g + 0.114 * b;
        return luma > 150.0 ? 0xFF0A0A0A : 0xFFFFFFFF;
    }

    private void renderInlineColorPicker(GuiGraphicsExtractor context, ColorSetting cs, float px, float py, float pw,
                                         int accent, float alpha) {
        TextureAssets.ensureInitialized();
        float hue = cs.getHue();
        float sat = cs.getSaturation();
        float bri = cs.getBrightness();
        float svH = 56.0f;
        int aByte = Math.round(255 * alpha);
        int whiteAlpha = ColorUtils.rgba(255, 255, 255, aByte);
        int pureHue = ColorUtils.hsbToRgb(hue, 1.0f, 1.0f, aByte);
        int pickedOpaque = ColorUtils.hsbToRgb(hue, sat, bri, aByte);

        Render2DHelper.drawRect(context, px, py, pw, svH, pureHue);
        Render2DHelper.drawTextureRegion(context, TextureAssets.COLOR_SV_SAT_GRAD, px, py, pw, svH,
                1.0f, 1.5f, 256, 1, 258, 4, whiteAlpha);
        Render2DHelper.drawTextureRegion(context, TextureAssets.COLOR_SV_VAL_GRAD, px, py, pw, svH,
                1.5f, 1.0f, 1, 256, 4, 258, whiteAlpha);
        Render2DHelper.drawRoundedOutlineFloat(context, px, py, pw, svH, 3.5f, 0.9f, SystemTheme.fade(SystemTheme.border(), alpha));

        float knobX = Mth.clamp(px + sat * pw, px + 4.0f, px + pw - 4.0f);
        float knobY = Mth.clamp(py + (1.0f - bri) * svH, py + 4.0f, py + svH - 4.0f);
        Render2DHelper.drawCircle(context, knobX, knobY, 4.0f, SystemTheme.fade(0xAA000000, alpha));
        Render2DHelper.drawCircle(context, knobX, knobY, 3.1f, whiteAlpha);
        Render2DHelper.drawCircle(context, knobX, knobY, 1.9f, pickedOpaque);

        float hueY = py + svH + 5.5f;
        float barH = 8.5f;
        Render2DHelper.drawTextureRegion(context, TextureAssets.COLOR_HUE_STRIP, px, hueY, pw, barH,
                1.0f, 1.5f, 256, 1, 258, 4, whiteAlpha);
        float hkX = Mth.clamp(px + hue * pw, px + 4.0f, px + pw - 4.0f);
        float hkY = hueY + barH * 0.5f;
        Render2DHelper.drawCircle(context, hkX, hkY, 3.8f, whiteAlpha);
        Render2DHelper.drawCircle(context, hkX, hkY, 2.1f, pureHue);

        float alphaY = hueY + barH + 5.5f;
        float curA = cs.getAlpha() / 255.0f;
        Render2DHelper.drawTextureRegion(context, TextureAssets.COLOR_ALPHA_CHECKER, px, alphaY, pw, barH,
                0.0f, 0.0f, Math.min(128, Math.round(pw)), Math.round(barH), 128, 16, whiteAlpha);
        Render2DHelper.drawTextureRegion(context, TextureAssets.COLOR_ALPHA_GRAD, px, alphaY, pw, barH,
                1.0f, 1.5f, 256, 1, 258, 4, pickedOpaque);
        float akX = Mth.clamp(px + curA * pw, px + 4.0f, px + pw - 4.0f);
        float akY = alphaY + barH * 0.5f;
        Render2DHelper.drawCircle(context, akX, akY, 3.8f, whiteAlpha);
        Render2DHelper.drawCircle(context, akX, akY, 2.1f, pickedOpaque);

        float infoY = alphaY + barH + 5.0f;
        int liveCol = cs.getColor();
        String hexText = String.format(Locale.US, "#%06X  %d%%", liveCol & 0xFFFFFF, Math.round(curA * 100.0f));
        Render2DHelper.drawScaledText(context, hexText, px + 1.0f, infoY, SystemTheme.fade(SystemTheme.mutedFg(), alpha), 0.64f, false);

        String modeLabel = cs.isThemeSync() ? "Theme" : (cs.isRainbow() ? "Rainbow" : "Custom");
        int modeCol = cs.isThemeSync() ? accent : (cs.isRainbow() ? ColorUtils.getRainbow(3.5f, 0.80f, 0.96f, 0L) : SystemTheme.mutedFg());
        float mw = Render2DHelper.getTextWidth(modeLabel, 0.64f);
        Render2DHelper.drawScaledText(context, modeLabel, px + pw - mw - 1.0f, infoY, ColorUtils.withAlphaFactor(modeCol, alpha), 0.64f, false);
    }

    public boolean handleClick(double mx, double my, int button, ClickGuiScreen host, float contentTop, float contentBottom) {
        float cardBottom = y + computeHeight();
        float visTop = Math.max(contentTop, y);
        float visBot = Math.min(contentBottom, cardBottom);
        if (my < visTop || my > visBot) {
            return false;
        }

        if (isInside(mx, my, x, y, width, HEADER_HEIGHT)) {
            host.setFocusedTextSetting(null, null);
            float switchW = 22.0f;
            float switchX = x + width - PAD_X - switchW;
            float starX = switchX - 5.0f - 10.5f;

            if (button == 0 && mx >= switchX - 2.0f) {
                module.setEnabled(!module.isEnabled());
                return true;
            }
            if (button == 0 && mx >= starX - 2.0f && mx < switchX - 2.0f) {
                host.toggleFavorite(module);
                return true;
            }

            boolean showBind = host.isBinding(module) || module.getKeyBind() > 0 || hoverAnim > 0.15f;
            if (showBind) {
                String bindText = host.isBinding(module) ? "..." : (module.getKeyBind() > 0 ? getKeyName(module.getKeyBind()) : "+");
                float bw = Math.max(14.0f, Render2DHelper.getTextWidth(bindText, 0.66f) + 6.0f);
                float bx = starX - 4.0f - bw;
                if (mx >= bx - 2.0f && mx < starX - 2.0f) {
                    if (button == 1) {
                        module.setKeyBind(0);
                        host.setBindingModule(null);
                    } else if (button == 0) {
                        host.setBindingModule(host.isBinding(module) ? null : module);
                    }
                    return true;
                }
            }

            if (button == 1 || button == 0) {
                if (hasVisibleSettings()) {
                    module.toggleExpanded();
                } else {
                    module.setEnabled(!module.isEnabled());
                }
                return true;
            }
        }

        float bodyTop = Math.max(visTop, y + HEADER_HEIGHT + 1.0f);
        float bodyBot = Math.min(contentBottom, Math.max(bodyTop, cardBottom - (expandAnim < 0.995f ? 3.0f : 0.0f)));
        if (hasVisibleSettings() && expandAnim > 0.05f && my >= bodyTop && my <= bodyBot) {
            float innerW = width - PAD_X * 2.0f;
            float sx = x + PAD_X;
            float sy = y + HEADER_HEIGHT + 6.0f + 22.0f;
            for (Setting<?> s : module.getSettings()) {
                if (!s.isVisible()) continue;
                float sh = getSettingHeight(s, innerW);
                float hitTop = Math.max(sy, bodyTop);
                float hitBot = Math.min(sy + sh, bodyBot);
                if (hitBot > hitTop && isInside(mx, my, sx, hitTop, innerW, hitBot - hitTop)) {
                    if (!(s instanceof TextSetting)) {
                        host.setFocusedTextSetting(null, null);
                    }
                    return handleSettingClick(s, mx, my, sx, sy, innerW, button, host);
                }
                sy += sh + SETTING_GAP;
            }
            host.setFocusedTextSetting(null, null);
            float bindTop = Math.max(sy, bodyTop);
            float bindBot = Math.min(sy + 17.0f, bodyBot);
            if (bindBot > bindTop && isInside(mx, my, sx, bindTop, innerW, bindBot - bindTop)) {
                if (button == 1) {
                    module.setKeyBind(0);
                    host.setBindingModule(null);
                } else if (button == 0) {
                    host.setBindingModule(host.isBinding(module) ? null : module);
                }
                return true;
            }
        }
        return false;
    }

    private boolean handleSettingClick(Setting<?> s, double mx, double my, float sx, float sy, float sw, int button, ClickGuiScreen host) {
        if (s instanceof BindSetting bs) {
            if (button == 1) {
                bs.setValue(BindSetting.UNBOUND);
                host.setBindingSettingActive(null);
            } else if (button == 0) {
                host.setBindingSettingActive(host.isBindingSettingActive(bs) ? null : bs);
            }
            return true;
        }
        if (s instanceof BooleanSetting bs && button == 0) {
            bs.toggle();
            return true;
        }
        if (s instanceof NumberSetting ns && button == 0) {
            host.startSliderDrag(ns, sx, sw);
            updateSliderValue(ns, mx, sx, sw);
            return true;
        }
        if (s instanceof RunSetting rs && button == 0) {
            float[] anim = runAnims.computeIfAbsent(rs, k -> new float[2]);
            anim[1] = 1.0f;
            rs.run();
            return true;
        }
        if (s instanceof TextSetting ts && button == 0) {
            float fw = Math.min(110.0f, sw * 0.58f);
            float fx = sx + sw - fw;
            if (mx >= fx) {
                host.setFocusedTextSetting(module, host.isTextSettingFocused(ts) ? null : ts);
                return true;
            }
            host.setFocusedTextSetting(null, null);
            return false;
        }
        if (s instanceof ModeSetting ms) {
            if (button == 1) {
                ms.cycle(false);
                return true;
            }
            if (button == 0) {
                List<String> modes = ms.getModes();
                if (modes.size() <= 6) {
                    return clickSegmentedGrid(modes, sx, sy + 13.0f, sw, mx, my, idx -> ms.setMode(modes.get(idx)));
                } else {
                    if (my <= sy + 17.0f) {
                        expandedLargeMode = (expandedLargeMode == ms) ? null : ms;
                        return true;
                    }
                    if (ms == expandedLargeMode) {
                        return clickSegmentedGrid(modes, sx, sy + 18.5f, sw, mx, my, idx -> ms.setMode(modes.get(idx)));
                    }
                }
            }
        }
        if (s instanceof MultiBoxSetting mbs && button == 0) {
            List<String> opts = mbs.getOptions();
            return clickSegmentedGrid(opts, sx, sy + 13.0f, sw, mx, my, idx -> mbs.toggle(opts.get(idx)));
        }
        if (s instanceof ColorSetting cs && button == 0) {
            float swatchW = 20.0f;
            float rbwW = 22.0f;
            float thmW = 22.0f;
            float swatchX = sx + sw - swatchW;
            float rbwX = swatchX - rbwW - 3.0f;
            float thmX = rbwX - thmW - 3.0f;
            if (my <= sy + 18.0f) {
                if (mx >= thmX && mx <= thmX + thmW) {
                    cs.setThemeSync(!cs.isThemeSync());
                    return true;
                }
                if (mx >= rbwX && mx <= rbwX + rbwW) {
                    cs.setRainbow(!cs.isRainbow());
                    return true;
                }
                expandedColorPicker = (expandedColorPicker == cs) ? null : cs;
                return true;
            }
            if (cs == expandedColorPicker) {
                float py = sy + 19.0f;
                float svH = 56.0f;
                float hueY = py + svH + 5.5f;
                float alphaY = hueY + 8.5f + 5.5f;
                int mode = 0;
                if (my >= py && my <= py + svH) mode = 1;
                else if (my >= hueY - 2.0f && my <= hueY + 11.0f) mode = 2;
                else if (my >= alphaY - 2.0f && my <= alphaY + 11.0f) mode = 3;
                if (mode != 0) {
                    host.startColorDrag(cs, mode, sx, py, sw, svH);
                    updateColorDrag(cs, mode, mx, my, sx, py, sw, svH);
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean clickSegmentedGrid(List<String> options, float sx, float topY, float sw,
                                              double mx, double my, java.util.function.IntConsumer onPick) {
        if (options.isEmpty()) return false;
        int cols = computeSegmentedCols(options, sw);
        float cellW = sw / cols;
        for (int i = 0; i < options.size(); i++) {
            float cx = sx + (i % cols) * cellW;
            float cy = topY + (i / cols) * SEG_CELL_H;
            if (isInside(mx, my, cx, cy, cellW, SEG_CELL_H)) {
                onPick.accept(i);
                return true;
            }
        }
        return false;
    }

    public static void updateSliderValue(NumberSetting ns, double mx, float sx, float sw) {
        double frac = Mth.clamp((mx - sx) / Math.max(1.0, sw), 0.0, 1.0);
        ns.setValueClamped(ns.getMin() + frac * (ns.getMax() - ns.getMin()));
    }

    public static void updateColorDrag(ColorSetting cs, int mode, double mx, double my, float px, float py, float pw, float svH) {
        float hue = cs.getHue();
        float sat = cs.getSaturation();
        float bri = cs.getBrightness();
        int alpha = cs.getAlpha();
        if (mode == 1) {
            float newSat = Mth.clamp((float) ((mx - px) / Math.max(1.0f, pw)), 0.0f, 1.0f);
            float newBri = 1.0f - Mth.clamp((float) ((my - py) / Math.max(1.0f, svH)), 0.0f, 1.0f);
            cs.setFromHsb(hue, newSat, newBri, alpha);
        } else if (mode == 2) {
            float newHue = Mth.clamp((float) ((mx - px) / Math.max(1.0f, pw)), 0.0f, 0.9999f);
            cs.setFromHsb(newHue, sat, bri, alpha);
        } else if (mode == 3) {
            int newA = Mth.clamp(Math.round((float) ((mx - px) / Math.max(1.0f, pw)) * 255.0f), 0, 255);
            cs.setFromHsb(hue, sat, bri, newA);
        }
    }

    private static boolean isInside(double mx, double my, float x, float y, float w, float h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    public static String getKeyName(int key) {
        if (key <= 0 || key == InputConstants.UNKNOWN.getValue()) return "NONE";
        return switch (key) {
            case InputConstants.KEY_RSHIFT -> "RSHIFT";
            case InputConstants.KEY_LSHIFT -> "LSHIFT";
            case InputConstants.KEY_RCONTROL -> "RCTRL";
            case InputConstants.KEY_LCONTROL -> "LCTRL";
            case InputConstants.KEY_RALT -> "RALT";
            case InputConstants.KEY_LALT -> "LALT";
            case InputConstants.KEY_SPACE -> "SPACE";
            case InputConstants.KEY_TAB -> "TAB";
            case InputConstants.KEY_CAPSLOCK -> "CAPS";
            case InputConstants.KEY_INSERT -> "INS";
            case InputConstants.KEY_HOME -> "HOME";
            case InputConstants.KEY_END -> "END";
            case InputConstants.KEY_PAGEUP -> "PGUP";
            case InputConstants.KEY_PAGEDOWN -> "PGDN";
            case InputConstants.KEY_UP -> "UP";
            case InputConstants.KEY_DOWN -> "DOWN";
            case InputConstants.KEY_LEFT -> "LEFT";
            case InputConstants.KEY_RIGHT -> "RIGHT";
            default -> {
                if (key >= InputConstants.KEY_F1 && key <= InputConstants.KEY_F12) {
                    yield "F" + (key - InputConstants.KEY_F1 + 1);
                }
                String glfwName;
                try {
                    glfwName = InputConstants.Type.KEYBOARD.getOrCreate(key).getDisplayName().getString();
                } catch (Throwable t) {
                    glfwName = null;
                }
                yield (glfwName != null && !glfwName.isBlank()) ? glfwName.toUpperCase() : "KEY_" + key;
            }
        };
    }
}
