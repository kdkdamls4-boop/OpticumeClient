package dev.opticumeclient.client.gui;

import dev.opticumeclient.client.gui.component.BindComponent;
import dev.opticumeclient.client.modules.Module;
import dev.opticumeclient.client.render.ColorUtils;
import dev.opticumeclient.client.render.Render2DHelper;
import dev.opticumeclient.client.render.TextureAssets;
import dev.opticumeclient.client.setting.BooleanSetting;
import dev.opticumeclient.client.setting.ColorSetting;
import dev.opticumeclient.client.setting.ModeSetting;
import dev.opticumeclient.client.setting.MultiBoxSetting;
import dev.opticumeclient.client.setting.NumberSetting;
import dev.opticumeclient.client.setting.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Interactive card widget implementing System's {@code ModuleRow} anatomy:
 * <ul>
 *   <li>Crisp 1px bordered container on {@code SystemTheme.bg()} with flash highlight pulse</li>
 *   <li>Header: trimmed name, favorite star, hover/bound keybind badge, animated pill toggle</li>
 *   <li>Smoothly animated expandable settings section with horizontal divider and scissor clip</li>
 *   <li>Inline controls for Boolean, Number slider, Mode choice dropdown, MultiBox chips, Color HSB picker</li>
 * </ul>
 */
public final class SystemModuleCard {
    public static final float HEADER_HEIGHT = 34.0f;
    private static final float PAD_X = 10.0f;
    private static final float SETTING_GAP = 7.0f;

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
    private ColorSetting expandedColorPicker;
    private ModeSetting openModeDropdown;

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
        this.openModeDropdown = null;
    }

    public float computeHeight() {
        if (!hasVisibleSettings() || expandAnim <= 0.002f) {
            return HEADER_HEIGHT;
        }
        float settingsH = computeSettingsContentHeight();
        return HEADER_HEIGHT + (1.0f + 8.0f + settingsH + 8.0f) * expandAnim;
    }

    private float computeSettingsContentHeight() {
        float h = 0.0f;
        boolean first = true;
        for (Setting<?> s : module.getSettings()) {
            if (!s.isVisible()) continue;
            if (!first) h += SETTING_GAP;
            first = false;
            h += getSettingHeight(s);
        }
        return h;
    }

    private float getSettingHeight(Setting<?> s) {
        if (s instanceof BooleanSetting) return 16.0f;
        if (s instanceof NumberSetting) return 24.0f;
        if (s instanceof ModeSetting ms) {
            float base = 18.0f;
            if (ms == openModeDropdown) {
                base += ms.getModes().size() * 14.0f + 6.0f;
            }
            return base;
        }
        if (s instanceof MultiBoxSetting mbs) {
            int rows = Math.max(1, (int) Math.ceil(mbs.getOptions().size() / 2.0));
            return 14.0f + rows * 15.0f;
        }
        if (s instanceof ColorSetting cs) {
            return (cs == expandedColorPicker) ? 104.0f : 18.0f;
        }
        return 16.0f;
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
            ClickGuiScreen.HoverTarget outHover
    ) {
        float h = computeHeight();
        float radius = 9.0f;

        // Container fill & hairline border
        Render2DHelper.drawRoundedRect(context, x, y, width, h, radius, SystemTheme.fade(SystemTheme.bg(), alpha));

        int baseBorder = SystemTheme.border();
        int activeOutline = module.isEnabled()
                ? ColorUtils.interpolateColor(baseBorder, SystemTheme.primary(), 0.25f)
                : baseBorder;
        Render2DHelper.drawRoundedOutline(context, x, y, width, h, radius, 1.0f, SystemTheme.fade(activeOutline, alpha));

        if (flashAnim > 0.01f) {
            Render2DHelper.drawRoundedOutline(context, x, y, width, h, radius, 1.0f,
                    SystemTheme.fade(SystemTheme.primary(), alpha * flashAnim));
        }

        renderHeader(context, alpha, mouseX, mouseY, isFavorite, isBinding, outHover);

        // Settings body with scissor reveal animation
        if (hasVisibleSettings() && expandAnim > 0.01f) {
            float divY = y + HEADER_HEIGHT;
            float bodyAlpha = alpha * expandAnim;
            Render2DHelper.drawRect(context, x + PAD_X, divY, width - PAD_X * 2.0f, 1.0f,
                    SystemTheme.fade(SystemTheme.border(), bodyAlpha));

            boolean clipDuringAnim = expandAnim < 0.995f;
            if (clipDuringAnim) {
                int sy1 = Math.round(divY + 1.0f);
                int sy2 = Math.max(sy1, Math.round(y + h - 4.0f));
                context.enableScissor(Math.round(x + 2), sy1, Math.round(x + width - 2), sy2);
            }

            float sy = divY + 8.0f;
            float innerW = width - PAD_X * 2.0f;
            for (Setting<?> s : module.getSettings()) {
                if (!s.isVisible()) continue;
                float sh = getSettingHeight(s);
                renderSetting(context, dt, s, x + PAD_X, sy, innerW, mouseX, mouseY, bodyAlpha, outHover);
                sy += sh + SETTING_GAP;
            }

            if (clipDuringAnim) {
                context.disableScissor();
            }
        }
    }

    private void renderHeader(
            GuiGraphicsExtractor context,
            float alpha,
            float mouseX,
            float mouseY,
            boolean isFavorite,
            boolean isBinding,
            ClickGuiScreen.HoverTarget outHover
    ) {
        Font font = Minecraft.getInstance().font;
        float switchW = 24.0f;
        float switchH = 13.0f;
        float switchX = x + width - PAD_X - switchW;
        float switchY = y + (HEADER_HEIGHT - switchH) * 0.5f;

        // Favorite star button
        float starSize = 11.0f;
        float starX = switchX - 5.0f - starSize;
        float starY = y + (HEADER_HEIGHT - starSize) * 0.5f;
        float starAlpha = isFavorite ? 1.0f : (0.18f + 0.55f * hoverAnim);
        int starColor = isFavorite
                ? SystemTheme.fade(SystemTheme.FAVORITE_GOLD, alpha * starAlpha)
                : SystemTheme.fade(SystemTheme.mutedFg(), alpha * starAlpha);
        Render2DHelper.drawTexture(context,
                isFavorite ? TextureAssets.ICON_STAR_FILLED : TextureAssets.ICON_FAVORITES,
                starX, starY, starSize, starSize, starColor);

        // Keybind pill (shown when bound, listening, or card hovered)
        float rightCursor = starX - 4.0f;
        boolean showBind = isBinding || module.getKeyBind() > 0 || hoverAnim > 0.15f;
        if (showBind) {
            String bindText = isBinding ? "..." : (module.getKeyBind() > 0 ? BindComponent.getKeyName(module.getKeyBind()) : "+");
            float bindScale = 0.68f;
            float bw = Math.max(15.0f, font.width(bindText) * bindScale + 7.0f);
            float bh = 12.0f;
            float bx = rightCursor - bw;
            float by = y + (HEADER_HEIGHT - bh) * 0.5f;
            float bAlpha = alpha * (isBinding || module.getKeyBind() > 0 ? 1.0f : hoverAnim);
            int bFill = isBinding ? SystemTheme.primary() : SystemTheme.elevated();
            int bText = isBinding ? SystemTheme.onPrimary() : SystemTheme.mutedFg();
            Render2DHelper.drawRoundedRect(context, bx, by, bw, bh, 3.0f, SystemTheme.fade(bFill, bAlpha));
            Render2DHelper.drawScaledText(context, bindText,
                    bx + (bw - font.width(bindText) * bindScale) * 0.5f, by + 3.0f,
                    SystemTheme.fade(bText, bAlpha), bindScale, false);
            rightCursor = bx - 4.0f;
        }

        // System toggle pill + knob
        int trackColor = ColorUtils.interpolateColor(SystemTheme.elevated(), SystemTheme.primary(), toggleAnim);
        int trackOutline = ColorUtils.interpolateColor(SystemTheme.border(), SystemTheme.primary(), toggleAnim);
        Render2DHelper.drawRoundedRect(context, switchX, switchY, switchW, switchH, 6.5f, SystemTheme.fade(trackColor, alpha));
        Render2DHelper.drawRoundedOutline(context, switchX, switchY, switchW, switchH, 6.5f, 1.0f, SystemTheme.fade(trackOutline, alpha));

        float knobSize = 9.0f;
        float knobX = switchX + 2.0f + toggleAnim * (switchW - knobSize - 4.0f);
        float knobY = switchY + (switchH - knobSize) * 0.5f;
        int knobCol = ColorUtils.interpolateColor(SystemTheme.mutedFg(), SystemTheme.onPrimary(), toggleAnim);
        Render2DHelper.drawCircle(context, knobX + knobSize * 0.5f, knobY + knobSize * 0.5f, knobSize * 0.5f,
                SystemTheme.fade(knobCol, alpha));

        // Module Name (clarity contrast between active & inactive)
        float nameScale = 0.88f;
        float maxTextW = Math.max(24.0f, rightCursor - (x + PAD_X) - 2.0f);
        String name = trimToWidth(font, module.getName(), nameScale, maxTextW);
        int titleCol = ColorUtils.interpolateColor(
                ColorUtils.interpolateColor(SystemTheme.mutedFg(), SystemTheme.foreground(), 0.45f),
                SystemTheme.foreground(),
                toggleAnim
        );
        Render2DHelper.drawScaledText(context, name, x + PAD_X, y + 10.5f, SystemTheme.fade(titleCol, alpha), nameScale, false);

        // Subtle dot indicating expandable settings
        if (hasVisibleSettings() && expandAnim < 0.05f) {
            Render2DHelper.drawCircle(context, x + PAD_X + font.width(name) * nameScale + 5.0f,
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
            ClickGuiScreen.HoverTarget outHover
    ) {
        Font font = Minecraft.getInstance().font;
        float labelScale = 0.78f;

        if (isInside(mouseX, mouseY, sx, sy, sw, getSettingHeight(s)) && s.getDescription() != null && !s.getDescription().isBlank()) {
            outHover.title = s.getName();
            outHover.description = s.getDescription();
        }

        if (s instanceof BooleanSetting bs) {
            float anim = boolAnims.getOrDefault(bs, bs.isEnabled() ? 1.0f : 0.0f);
            anim += ((bs.isEnabled() ? 1.0f : 0.0f) - anim) * (1.0f - (float) Math.exp(-15.0f * dt));
            boolAnims.put(bs, anim);

            String label = trimToWidth(font, bs.getName(), labelScale, sw - 28.0f);
            Render2DHelper.drawScaledText(context, label, sx, sy + 4.0f, SystemTheme.fade(SystemTheme.foreground(), alpha), labelScale, false);

            float tw = 22.0f;
            float th = 12.0f;
            float tx = sx + sw - tw;
            float ty = sy + 2.0f;
            int track = ColorUtils.interpolateColor(SystemTheme.elevated(), SystemTheme.primary(), anim);
            int border = ColorUtils.interpolateColor(SystemTheme.border(), SystemTheme.primary(), anim);
            Render2DHelper.drawRoundedRect(context, tx, ty, tw, th, 6.0f, SystemTheme.fade(track, alpha));
            Render2DHelper.drawRoundedOutline(context, tx, ty, tw, th, 6.0f, 1.0f, SystemTheme.fade(border, alpha));

            float knob = 8.0f;
            float kx = tx + 2.0f + anim * (tw - knob - 4.0f);
            int kCol = ColorUtils.interpolateColor(SystemTheme.mutedFg(), SystemTheme.onPrimary(), anim);
            Render2DHelper.drawCircle(context, kx + knob * 0.5f, ty + th * 0.5f, knob * 0.5f, SystemTheme.fade(kCol, alpha));
            return;
        }

        if (s instanceof NumberSetting ns) {
            double range = Math.max(1.0E-6, ns.getMax() - ns.getMin());
            float targetFrac = (float) Mth.clamp((ns.getValue() - ns.getMin()) / range, 0.0, 1.0);
            float vis = sliderVisual.getOrDefault(ns, targetFrac);
            vis += (targetFrac - vis) * (1.0f - (float) Math.exp(-18.0f * dt));
            sliderVisual.put(ns, vis);

            String valStr = ns.getStep() >= 1.0 ? String.valueOf(Math.round(ns.getValue())) : String.format(Locale.ROOT, "%.2f", ns.getValue());
            float valW = font.width(valStr) * 0.74f;
            String label = trimToWidth(font, ns.getName(), labelScale, sw - valW - 8.0f);
            Render2DHelper.drawScaledText(context, label, sx, sy + 1.0f, SystemTheme.fade(SystemTheme.foreground(), alpha), labelScale, false);
            Render2DHelper.drawScaledText(context, valStr, sx + sw - valW, sy + 1.0f, SystemTheme.fade(SystemTheme.mutedFg(), alpha), 0.74f, false);

            float trackY = sy + 15.0f;
            float trackH = 4.0f;
            Render2DHelper.drawRoundedRect(context, sx, trackY, sw, trackH, 2.0f, SystemTheme.fade(SystemTheme.elevated(), alpha));
            float fillW = Math.max(0.0f, sw * vis);
            if (fillW > 1.5f) {
                Render2DHelper.drawRoundedRect(context, sx, trackY, fillW, trackH, 2.0f, SystemTheme.fade(SystemTheme.primary(), alpha));
            }
            float thumbCx = sx + fillW;
            float thumbCy = trackY + trackH * 0.5f;
            Render2DHelper.drawCircle(context, thumbCx, thumbCy, 4.2f, SystemTheme.fade(SystemTheme.foreground(), alpha));
            Render2DHelper.drawCircle(context, thumbCx, thumbCy, 2.4f, SystemTheme.fade(SystemTheme.bg(), alpha));
            return;
        }

        if (s instanceof ModeSetting ms) {
            String curMode = ms.getMode() != null ? ms.getMode() : "";
            float chipScale = 0.72f;
            float maxChipW = sw * 0.56f;
            String modeTrim = trimToWidth(font, curMode, chipScale, maxChipW - 14.0f);
            float chipW = Math.max(36.0f, font.width(modeTrim) * chipScale + 14.0f);
            float chipH = 15.0f;
            float chipX = sx + sw - chipW;
            float chipY = sy + 1.5f;

            String label = trimToWidth(font, ms.getName(), labelScale, sw - chipW - 6.0f);
            Render2DHelper.drawScaledText(context, label, sx, sy + 5.0f, SystemTheme.fade(SystemTheme.foreground(), alpha), labelScale, false);

            Render2DHelper.drawRoundedRect(context, chipX, chipY, chipW, chipH, 4.0f, SystemTheme.fade(SystemTheme.elevated(), alpha));
            Render2DHelper.drawRoundedOutline(context, chipX, chipY, chipW, chipH, 4.0f, 1.0f,
                    SystemTheme.fade(ms == openModeDropdown ? SystemTheme.primary() : SystemTheme.border(), alpha));
            Render2DHelper.drawScaledText(context, modeTrim, chipX + 5.0f, chipY + 4.5f,
                    SystemTheme.fade(SystemTheme.foreground(), alpha), chipScale, false);
            Render2DHelper.drawScaledText(context, ms == openModeDropdown ? "▲" : "▼",
                    chipX + chipW - 8.5f, chipY + 4.5f, SystemTheme.fade(SystemTheme.mutedFg(), alpha), 0.60f, false);

            if (ms == openModeDropdown) {
                List<String> modes = ms.getModes();
                float dropY = chipY + chipH + 3.0f;
                float dropH = modes.size() * 14.0f + 4.0f;
                Render2DHelper.drawRoundedRect(context, sx, dropY, sw, dropH, 5.0f, SystemTheme.fade(SystemTheme.surface(), alpha));
                Render2DHelper.drawRoundedOutline(context, sx, dropY, sw, dropH, 5.0f, 1.0f, SystemTheme.fade(SystemTheme.border(), alpha));
                for (int i = 0; i < modes.size(); i++) {
                    String opt = modes.get(i);
                    float oy = dropY + 2.0f + i * 14.0f;
                    boolean activeOpt = opt.equalsIgnoreCase(curMode);
                    boolean rowHov = isInside(mouseX, mouseY, sx + 2, oy, sw - 4, 13.0f);
                    if (activeOpt || rowHov) {
                        Render2DHelper.drawRoundedRect(context, sx + 2, oy, sw - 4, 13.0f, 3.0f,
                                SystemTheme.fade(activeOpt ? SystemTheme.primary() : SystemTheme.elevated(), alpha));
                    }
                    int optCol = activeOpt ? SystemTheme.onPrimary() : SystemTheme.foreground();
                    Render2DHelper.drawScaledText(context, opt, sx + 7.0f, oy + 3.5f,
                            SystemTheme.fade(optCol, alpha), 0.72f, false);
                }
            }
            return;
        }

        if (s instanceof MultiBoxSetting mbs) {
            Render2DHelper.drawScaledText(context, trimToWidth(font, mbs.getName(), labelScale, sw),
                    sx, sy + 1.0f, SystemTheme.fade(SystemTheme.foreground(), alpha), labelScale, false);
            List<String> opts = mbs.getOptions();
            float chipW = (sw - 4.0f) * 0.5f;
            float chipH = 13.0f;
            for (int i = 0; i < opts.size(); i++) {
                String opt = opts.get(i);
                int col = i % 2;
                int row = i / 2;
                float cx = sx + col * (chipW + 4.0f);
                float cy = sy + 13.0f + row * 15.0f;
                boolean on = mbs.isEnabled(opt);
                Render2DHelper.drawRoundedRect(context, cx, cy, chipW, chipH, 4.0f,
                        SystemTheme.fade(on ? SystemTheme.primary() : SystemTheme.elevated(), alpha));
                Render2DHelper.drawRoundedOutline(context, cx, cy, chipW, chipH, 4.0f, 1.0f,
                        SystemTheme.fade(on ? SystemTheme.primary() : SystemTheme.border(), alpha));
                String optTrim = trimToWidth(font, opt, 0.68f, chipW - 6.0f);
                int tCol = on ? SystemTheme.onPrimary() : SystemTheme.mutedFg();
                Render2DHelper.drawScaledText(context, optTrim, cx + (chipW - font.width(optTrim) * 0.68f) * 0.5f,
                        cy + 3.5f, SystemTheme.fade(tCol, alpha), 0.68f, false);
            }
            return;
        }

        if (s instanceof ColorSetting cs) {
            float swatchW = 24.0f;
            float swatchH = 12.0f;
            float rbwW = 24.0f;
            float swatchX = sx + sw - swatchW;
            float rbwX = swatchX - rbwW - 4.0f;
            float topY = sy + 2.0f;

            Render2DHelper.drawScaledText(context, trimToWidth(font, cs.getName(), labelScale, sw - swatchW - rbwW - 10.0f),
                    sx, sy + 5.0f, SystemTheme.fade(SystemTheme.foreground(), alpha), labelScale, false);

            // Rainbow toggle pill
            Render2DHelper.drawRoundedRect(context, rbwX, topY, rbwW, swatchH, 3.0f,
                    SystemTheme.fade(cs.isRainbow() ? SystemTheme.primary() : SystemTheme.elevated(), alpha));
            Render2DHelper.drawScaledText(context, "RBW", rbwX + 4.0f, topY + 3.0f,
                    SystemTheme.fade(cs.isRainbow() ? SystemTheme.onPrimary() : SystemTheme.mutedFg(), alpha), 0.62f, false);

            // Color preview swatch
            Render2DHelper.drawRoundedRect(context, swatchX, topY, swatchW, swatchH, 3.5f,
                    ColorUtils.withAlphaFactor(cs.getColor() | 0xFF000000, alpha));
            Render2DHelper.drawRoundedOutline(context, swatchX, topY, swatchW, swatchH, 3.5f, 1.0f,
                    SystemTheme.fade(SystemTheme.border(), alpha));

            if (cs == expandedColorPicker) {
                renderInlineColorPicker(context, cs, sx, sy + 20.0f, sw, alpha);
            }
        }
    }

    private void renderInlineColorPicker(GuiGraphicsExtractor context, ColorSetting cs, float px, float py, float pw, float alpha) {
        float[] hsb = ColorUtils.rgbToHsb(cs.getColor());
        float svH = 54.0f;
        int steps = 16;
        float cellW = pw / steps;
        float cellH = svH / steps;
        for (int iy = 0; iy < steps; iy++) {
            float bri = 1.0f - (iy + 0.5f) / steps;
            for (int ix = 0; ix < steps; ix++) {
                float sat = (ix + 0.5f) / steps;
                int c = ColorUtils.hsbToRgb(hsb[0], sat, bri, Math.round(255 * alpha));
                Render2DHelper.drawRect(context, px + ix * cellW, py + iy * cellH, cellW + 0.6f, cellH + 0.6f, c);
            }
        }
        Render2DHelper.drawBorder(context, Math.round(px), Math.round(py), Math.round(pw), Math.round(svH), 1,
                SystemTheme.fade(SystemTheme.border(), alpha));

        float knobX = px + hsb[1] * pw;
        float knobY = py + (1.0f - hsb[2]) * svH;
        Render2DHelper.drawCircle(context, knobX, knobY, 3.5f, SystemTheme.fade(0xFFFFFFFF, alpha));
        Render2DHelper.drawCircle(context, knobX, knobY, 2.0f, ColorUtils.withAlphaFactor(cs.getColor() | 0xFF000000, alpha));

        // Hue bar
        float hueY = py + svH + 5.0f;
        float barH = 8.0f;
        int hueSegs = 24;
        float segW = pw / hueSegs;
        for (int i = 0; i < hueSegs; i++) {
            int hc = ColorUtils.hsbToRgb((i + 0.5f) / hueSegs, 1.0f, 1.0f, Math.round(255 * alpha));
            Render2DHelper.drawRect(context, px + i * segW, hueY, segW + 0.6f, barH, hc);
        }
        Render2DHelper.drawRoundedRect(context, px + hsb[0] * (pw - 4.0f), hueY - 1.0f, 4.0f, barH + 2.0f, 1.5f,
                SystemTheme.fade(0xFFFFFFFF, alpha));

        // Alpha bar
        float alphaY = hueY + barH + 5.0f;
        float curA = ((cs.getColor() >>> 24) & 0xFF) / 255.0f;
        Render2DHelper.drawRoundedRect(context, px, alphaY, pw, barH, 2.0f, SystemTheme.fade(SystemTheme.elevated(), alpha));
        Render2DHelper.drawHorizontalGradient(context, px, alphaY, pw, barH,
                ColorUtils.withAlpha(cs.getColor(), 10), ColorUtils.withAlpha(cs.getColor(), Math.round(255 * alpha)));
        Render2DHelper.drawRoundedRect(context, px + curA * (pw - 4.0f), alphaY - 1.0f, 4.0f, barH + 2.0f, 1.5f,
                SystemTheme.fade(0xFFFFFFFF, alpha));
    }

    /** Handles click on the card; returns true if consumed. */
    public boolean handleClick(double mx, double my, int button, ClickGuiScreen host) {
        if (isInside(mx, my, x, y, width, HEADER_HEIGHT)) {
            float switchW = 24.0f;
            float switchX = x + width - PAD_X - switchW;
            float starX = switchX - 5.0f - 11.0f;

            if (button == 0 && mx >= switchX - 2.0f) {
                module.setEnabled(!module.isEnabled());
                return true;
            }
            if (button == 0 && mx >= starX - 2.0f && mx < switchX - 2.0f) {
                host.toggleFavorite(module);
                return true;
            }

            // Keybind area click (only when bind pill visible)
            boolean showBind = host.isBinding(module) || module.getKeyBind() > 0 || hoverAnim > 0.15f;
            if (showBind) {
                Font font = Minecraft.getInstance().font;
                String bindText = host.isBinding(module) ? "..." : (module.getKeyBind() > 0 ? BindComponent.getKeyName(module.getKeyBind()) : "+");
                float bw = Math.max(15.0f, font.width(bindText) * 0.68f + 7.0f);
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

            if (button == 1) {
                if (hasVisibleSettings()) {
                    module.toggleExpanded();
                } else {
                    module.setEnabled(!module.isEnabled());
                }
                return true;
            }

            if (button == 0) {
                if (hasVisibleSettings()) {
                    module.toggleExpanded();
                } else {
                    module.setEnabled(!module.isEnabled());
                }
                return true;
            }
        }

        if (hasVisibleSettings() && expandAnim > 0.6f) {
            float sy = y + HEADER_HEIGHT + 9.0f;
            float innerW = width - PAD_X * 2.0f;
            float sx = x + PAD_X;
            for (Setting<?> s : module.getSettings()) {
                if (!s.isVisible()) continue;
                float sh = getSettingHeight(s);
                if (isInside(mx, my, sx, sy, innerW, sh)) {
                    return handleSettingClick(s, mx, my, sx, sy, innerW, button, host);
                }
                sy += sh + SETTING_GAP;
            }
        }
        return false;
    }

    private boolean handleSettingClick(Setting<?> s, double mx, double my, float sx, float sy, float sw, int button, ClickGuiScreen host) {
        if (s instanceof BooleanSetting bs && button == 0) {
            bs.toggle();
            return true;
        }
        if (s instanceof NumberSetting ns && button == 0) {
            host.startSliderDrag(ns, sx, sw);
            updateSliderValue(ns, mx, sx, sw);
            return true;
        }
        if (s instanceof ModeSetting ms) {
            if (button == 1) {
                ms.cycle(false);
                return true;
            }
            if (button == 0) {
                if (ms == openModeDropdown && my >= sy + 18.0f) {
                    float dropY = sy + 19.5f;
                    int idx = (int) ((my - dropY - 2.0f) / 14.0f);
                    List<String> modes = ms.getModes();
                    if (idx >= 0 && idx < modes.size()) {
                        ms.setMode(modes.get(idx));
                        openModeDropdown = null;
                        return true;
                    }
                }
                openModeDropdown = (openModeDropdown == ms) ? null : ms;
                return true;
            }
        }
        if (s instanceof MultiBoxSetting mbs && button == 0) {
            List<String> opts = mbs.getOptions();
            float chipW = (sw - 4.0f) * 0.5f;
            for (int i = 0; i < opts.size(); i++) {
                float cx = sx + (i % 2) * (chipW + 4.0f);
                float cy = sy + 13.0f + (i / 2) * 15.0f;
                if (isInside(mx, my, cx, cy, chipW, 13.0f)) {
                    mbs.toggle(opts.get(i));
                    return true;
                }
            }
        }
        if (s instanceof ColorSetting cs && button == 0) {
            float swatchW = 24.0f;
            float rbwW = 24.0f;
            float swatchX = sx + sw - swatchW;
            float rbwX = swatchX - rbwW - 4.0f;
            if (my <= sy + 18.0f) {
                if (mx >= rbwX && mx <= rbwX + rbwW) {
                    cs.setRainbow(!cs.isRainbow());
                    return true;
                }
                if (mx >= swatchX && mx <= swatchX + swatchW) {
                    expandedColorPicker = (expandedColorPicker == cs) ? null : cs;
                    return true;
                }
                expandedColorPicker = (expandedColorPicker == cs) ? null : cs;
                return true;
            }
            if (cs == expandedColorPicker) {
                float py = sy + 20.0f;
                float svH = 54.0f;
                float hueY = py + svH + 5.0f;
                float alphaY = hueY + 13.0f;
                int mode = 0;
                if (my >= py && my <= py + svH) mode = 1;
                else if (my >= hueY - 2 && my <= hueY + 10) mode = 2;
                else if (my >= alphaY - 2 && my <= alphaY + 10) mode = 3;
                if (mode != 0) {
                    host.startColorDrag(cs, mode, sx, py, sw, svH);
                    updateColorDrag(cs, mode, mx, my, sx, py, sw, svH);
                    return true;
                }
            }
        }
        return false;
    }

    public static void updateSliderValue(NumberSetting ns, double mx, float sx, float sw) {
        double frac = Mth.clamp((mx - sx) / Math.max(1.0, sw), 0.0, 1.0);
        ns.setValueClamped(ns.getMin() + frac * (ns.getMax() - ns.getMin()));
    }

    public static void updateColorDrag(ColorSetting cs, int mode, double mx, double my, float px, float py, float pw, float svH) {
        float[] hsb = ColorUtils.rgbToHsb(cs.getColor());
        int alpha = (cs.getColor() >>> 24) & 0xFF;
        if (mode == 1) {
            float sat = Mth.clamp((float) ((mx - px) / Math.max(1.0f, pw)), 0.0f, 1.0f);
            float bri = 1.0f - Mth.clamp((float) ((my - py) / Math.max(1.0f, svH)), 0.0f, 1.0f);
            cs.setValue(ColorUtils.hsbToRgb(hsb[0], sat, bri, alpha));
        } else if (mode == 2) {
            float hue = Mth.clamp((float) ((mx - px) / Math.max(1.0f, pw)), 0.0f, 0.999f);
            cs.setValue(ColorUtils.hsbToRgb(hue, hsb[1], hsb[2], alpha));
        } else if (mode == 3) {
            int newA = Mth.clamp(Math.round((float) ((mx - px) / Math.max(1.0f, pw)) * 255.0f), 0, 255);
            cs.setValue(ColorUtils.withAlpha(cs.getColor(), newA));
        }
    }

    public static String trimToWidth(Font font, String text, float scale, float maxWidth) {
        if (text == null || text.isEmpty()) return "";
        if (font.width(text) * scale <= maxWidth) return text;
        String dots = "..";
        float dotsW = font.width(dots) * scale;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            sb.append(text.charAt(i));
            if (font.width(sb.toString()) * scale + dotsW > maxWidth) {
                if (sb.length() > 1) sb.setLength(sb.length() - 1);
                return sb + dots;
            }
        }
        return text;
    }

    private static boolean isInside(double mx, double my, float x, float y, float w, float h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }
}
