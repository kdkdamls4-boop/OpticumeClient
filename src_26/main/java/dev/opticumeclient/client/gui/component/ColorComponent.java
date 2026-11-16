package dev.opticumeclient.client.gui.component;

import dev.opticumeclient.client.render.Animation;
import dev.opticumeclient.client.render.ColorUtils;
import dev.opticumeclient.client.render.Easing;
import dev.opticumeclient.client.render.Render2DHelper;
import dev.opticumeclient.client.render.Theme;
import dev.opticumeclient.client.render.ThemeManager;
import dev.opticumeclient.client.setting.ColorSetting;
import net.minecraft.client.gui.GuiGraphicsExtractor; 

import java.util.Locale;

/**
 * Full-featured HSB + Alpha ColorPicker component:
 * Clicking the color preview circle smoothly expands a panel with:
 * - 2D Saturation/Brightness square (draggable along X and Y)
 * - Rainbow Hue bar (0..360°)
 * - Alpha bar (0..255)
 * - [Rainbow] toggle pill
 * - Live HEX code display (#RRGGBB)
 */
public class ColorComponent extends Component {
    private static final float BASE_HEIGHT = 20.0f;
    private static final float EXPANDED_EXTRA = 94.0f;

    private final ColorSetting colorSetting;
    private boolean expanded;
    private final Animation expandAnimation;

    private float hue;
    private float saturation;
    private float brightness;
    private int alpha;

    // 0 = none, 1 = 2D SB square, 2 = Hue bar, 3 = Alpha bar
    private int dragMode = 0;

    public ColorComponent(ColorSetting setting, float width) {
        super(setting, width, BASE_HEIGHT);
        this.colorSetting = setting;
        this.expandAnimation = new Animation(0.0f, 190L, Easing.EASE_OUT_CUBIC);
        syncHsbFromSetting();
    }

    private void syncHsbFromSetting() {
        int val = colorSetting.getValue();
        float[] hsb = ColorUtils.rgbToHsb(val);
        this.hue = hsb[0];
        this.saturation = hsb[1];
        this.brightness = hsb[2];
        this.alpha = (val >>> 24) & 0xFF;
        if (this.alpha == 0) this.alpha = 255;
    }

    private void applyHsbToSetting() {
        int argb = ColorUtils.hsbToRgb(hue, saturation, brightness, alpha);
        colorSetting.setValue(argb);
    }

    @Override
    public float getHeight() {
        return BASE_HEIGHT + EXPANDED_EXTRA * expandAnimation.getValue();
    }

    @Override
    public void render(GuiGraphicsExtractor context, float mouseX, float mouseY, float delta) {
        if (!isVisible()) return;

        expandAnimation.animateTo(expanded ? 1.0f : 0.0f);
        float anim = expandAnimation.getValue();
        this.height = getHeight();

        if (dragMode == 0) {
            // Keep alpha synced if external code modified it
            this.alpha = colorSetting.getAlpha();
        }

        Theme theme = ThemeManager.getInstance().getCurrentTheme();

        // Setting name on left
        Render2DHelper.drawScaledText(context, colorSetting.getName(), x + 4.0f, y + 5.5f,
                theme.getMutedTextColor(), 0.76f, false);

        int previewColor = colorSetting.isRainbow()
                ? ColorUtils.withAlpha(ColorUtils.rainbow(0, 0.85f, 1.0f), alpha)
                : colorSetting.getValue();

        // HEX code string (#RRGGBB) next to preview circle
        String hexStr = String.format(Locale.ROOT, "#%02X%02X%02X",
                (previewColor >> 16) & 0xFF,
                (previewColor >> 8) & 0xFF,
                previewColor & 0xFF);
        float hexScale = 0.68f;
        float hexW = Render2DHelper.getTextWidth(hexStr, hexScale);
        float circleRadius = 5.5f;
        float circleCX = x + width - 4.0f - circleRadius;
        float circleCY = y + BASE_HEIGHT * 0.5f;

        Render2DHelper.drawScaledText(context, hexStr, circleCX - circleRadius - hexW - 5.0f, y + 6.0f,
                theme.getTextColor(), hexScale, false);

        // Color preview circle with subtle glow and white rim
        Render2DHelper.drawGlow(context, circleCX - circleRadius, circleCY - circleRadius,
                circleRadius * 2.0f, circleRadius * 2.0f, 3.5f, ColorUtils.withAlpha(previewColor, 140));
        Render2DHelper.drawCircle(context, circleCX, circleCY, circleRadius, previewColor);
        Render2DHelper.drawRoundedOutline(context, circleCX - circleRadius, circleCY - circleRadius,
                circleRadius * 2.0f, circleRadius * 2.0f, circleRadius, 1.0f, 0xDDFFFFFF);

        // Expanded HSB + Alpha Picker Panel
        if (anim > 0.03f) {
            float panelX = x + 4.0f;
            float panelY = y + BASE_HEIGHT + 1.0f;
            float panelW = width - 8.0f;

            // 1. 2D Saturation/Brightness Square
            float sbX = panelX;
            float sbY = panelY;
            float sbW = panelW;
            float sbH = 46.0f * anim;

            if (dragMode == 1) {
                updateSBFromMouse(mouseX, mouseY, sbX, sbY, sbW, Math.max(1.0f, sbH));
            }

            int pureHueColor = ColorUtils.hsbToRgb(hue, 1.0f, 1.0f, (int) (255 * anim));
            int whiteColor = ColorUtils.rgba(255, 255, 255, (int) (255 * anim));

            // Base horizontal saturation gradient (White -> Pure Hue)
            Render2DHelper.drawHorizontalGradient(context, sbX, sbY, sbW, sbH, whiteColor, pureHueColor);
            // Vertical brightness overlay (Transparent -> Black)
            Render2DHelper.drawGradientRect(context, sbX, sbY, sbW, sbH,
                    0x00000000, ColorUtils.rgba(0, 0, 0, (int) (255 * anim)));
            Render2DHelper.drawRoundedOutline(context, sbX, sbY, sbW, sbH, 2.5f, 1.0f,
                    ColorUtils.withAlpha(0xFFFFFFFF, (int) (90 * anim)));

            // 2D Cursor on SB square
            float curSBX = sbX + saturation * sbW;
            float curSBY = sbY + (1.0f - brightness) * sbH;
            Render2DHelper.drawCircle(context, curSBX, curSBY, 3.2f, ColorUtils.withAlpha(0xFF000000, (int) (220 * anim)));
            Render2DHelper.drawCircle(context, curSBX, curSBY, 2.2f, ColorUtils.withAlpha(0xFFFFFFFF, (int) (255 * anim)));

            // 2. Rainbow Hue Bar (0..360°)
            float hueY = sbY + sbH + 5.0f * anim;
            float barH = 6.0f * anim;
            if (dragMode == 2) {
                updateHueFromMouse(mouseX, panelX, panelW);
            }

            int segments = 12;
            float segW = panelW / segments;
            for (int i = 0; i < segments; i++) {
                float h1 = (float) i / segments;
                float h2 = (float) (i + 1) / segments;
                int c1 = ColorUtils.hsbToRgb(h1, 1.0f, 1.0f, (int) (255 * anim));
                int c2 = ColorUtils.hsbToRgb(h2, 1.0f, 1.0f, (int) (255 * anim));
                Render2DHelper.drawHorizontalGradient(context, panelX + i * segW, hueY, segW + 0.6f, barH, c1, c2);
            }
            Render2DHelper.drawRoundedOutline(context, panelX, hueY, panelW, barH, 2.0f, 1.0f,
                    ColorUtils.withAlpha(0xFFFFFFFF, (int) (80 * anim)));

            float hueThumbX = panelX + hue * panelW;
            Render2DHelper.drawRoundedRect(context, hueThumbX - 1.5f, hueY - 1.0f, 3.0f, barH + 2.0f, 1.0f,
                    ColorUtils.withAlpha(0xFFFFFFFF, (int) (255 * anim)));

            // 3. Alpha Bar (0..255)
            float alphaY = hueY + barH + 5.0f * anim;
            if (dragMode == 3) {
                updateAlphaFromMouse(mouseX, panelX, panelW);
            }

            Render2DHelper.drawRoundedRect(context, panelX, alphaY, panelW, barH, 2.0f,
                    ColorUtils.withAlpha(0xFF1A1D26, (int) (220 * anim)));
            int rgbOpaque = ColorUtils.hsbToRgb(hue, saturation, brightness, (int) (255 * anim));
            int rgbTransparent = ColorUtils.hsbToRgb(hue, saturation, brightness, 0);
            Render2DHelper.drawHorizontalGradient(context, panelX, alphaY, panelW, barH, rgbTransparent, rgbOpaque);
            Render2DHelper.drawRoundedOutline(context, panelX, alphaY, panelW, barH, 2.0f, 1.0f,
                    ColorUtils.withAlpha(0xFFFFFFFF, (int) (80 * anim)));

            float alphaFraction = alpha / 255.0f;
            float alphaThumbX = panelX + alphaFraction * panelW;
            Render2DHelper.drawRoundedRect(context, alphaThumbX - 1.5f, alphaY - 1.0f, 3.0f, barH + 2.0f, 1.0f,
                    ColorUtils.withAlpha(0xFFFFFFFF, (int) (255 * anim)));

            // 4. Bottom Row: [Rainbow] toggle button + Alpha % & HEX readout
            float botY = alphaY + barH + 5.0f * anim;
            float rbBtnW = 48.0f;
            float rbBtnH = 13.0f * anim;
            boolean rbActive = colorSetting.isRainbow();
            int rbBg = rbActive
                    ? ColorUtils.withAlpha(theme.getPrimaryColor(), (int) (200 * anim))
                    : ColorUtils.withAlpha(0xFFFFFFFF, (int) (35 * anim));
            Render2DHelper.drawRoundedRect(context, panelX, botY, rbBtnW, rbBtnH, 3.0f, rbBg);
            Render2DHelper.drawRoundedOutline(context, panelX, botY, rbBtnW, rbBtnH, 3.0f, 1.0f,
                    rbActive ? ColorUtils.withAlpha(theme.getSecondaryColor(), (int) (220 * anim))
                            : ColorUtils.withAlpha(0xFFFFFFFF, (int) (75 * anim)));

            String rbLabel = "[Rainbow]";
            float rbLabelW = Render2DHelper.getTextWidth(rbLabel, 0.65f);
            Render2DHelper.drawScaledText(context, rbLabel,
                    panelX + (rbBtnW - rbLabelW) * 0.5f, botY + 3.0f * anim,
                    ColorUtils.withAlpha(0xFFFFFFFF, (int) (245 * anim)), 0.65f, false);

            String infoText = hexStr + " A:" + alpha;
            float infoW = Render2DHelper.getTextWidth(infoText, 0.65f);
            Render2DHelper.drawScaledText(context, infoText,
                    panelX + panelW - infoW, botY + 3.0f * anim,
                    ColorUtils.withAlpha(theme.getMutedTextColor(), (int) (235 * anim)), 0.65f, false);
        }
    }

    private void updateSBFromMouse(double mouseX, double mouseY, float sbX, float sbY, float sbW, float sbH) {
        this.saturation = (float) Math.max(0.0, Math.min(1.0, (mouseX - sbX) / sbW));
        this.brightness = 1.0f - (float) Math.max(0.0, Math.min(1.0, (mouseY - sbY) / sbH));
        applyHsbToSetting();
    }

    private void updateHueFromMouse(double mouseX, float barX, float barW) {
        this.hue = (float) Math.max(0.0, Math.min(1.0, (mouseX - barX) / barW));
        applyHsbToSetting();
    }

    private void updateAlphaFromMouse(double mouseX, float barX, float barW) {
        float frac = (float) Math.max(0.0, Math.min(1.0, (mouseX - barX) / barW));
        this.alpha = Math.round(frac * 255.0f);
        applyHsbToSetting();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!isVisible()) return false;

        // Top row click -> toggle expand on LMB or RMB
        if (mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + BASE_HEIGHT) {
            if (button == 0 || button == 1) {
                expanded = !expanded;
                if (expanded) {
                    syncHsbFromSetting();
                }
                return true;
            }
        }

        if (expanded && expandAnimation.getValue() > 0.6f) {
            float panelX = x + 4.0f;
            float panelY = y + BASE_HEIGHT + 1.0f;
            float panelW = width - 8.0f;
            float sbH = 46.0f;
            float hueY = panelY + sbH + 5.0f;
            float barH = 6.0f;
            float alphaY = hueY + barH + 5.0f;
            float botY = alphaY + barH + 5.0f;

            if (button == 0) {
                // 2D Saturation/Brightness box
                if (mouseX >= panelX && mouseX <= panelX + panelW && mouseY >= panelY && mouseY <= panelY + sbH) {
                    dragMode = 1;
                    updateSBFromMouse(mouseX, mouseY, panelX, panelY, panelW, sbH);
                    return true;
                }
                // Hue bar
                if (mouseX >= panelX && mouseX <= panelX + panelW && mouseY >= hueY - 2.0f && mouseY <= hueY + barH + 2.0f) {
                    dragMode = 2;
                    updateHueFromMouse(mouseX, panelX, panelW);
                    return true;
                }
                // Alpha bar
                if (mouseX >= panelX && mouseX <= panelX + panelW && mouseY >= alphaY - 2.0f && mouseY <= alphaY + barH + 2.0f) {
                    dragMode = 3;
                    updateAlphaFromMouse(mouseX, panelX, panelW);
                    return true;
                }
                // [Rainbow] button
                if (mouseX >= panelX && mouseX <= panelX + 48.0f && mouseY >= botY && mouseY <= botY + 14.0f) {
                    colorSetting.setRainbow(!colorSetting.isRainbow());
                    return true;
                }
            }
        }

        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (dragMode != 0) {
            dragMode = 0;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (dragMode != 0 && expanded) {
            float panelX = x + 4.0f;
            float panelY = y + BASE_HEIGHT + 1.0f;
            float panelW = width - 8.0f;
            float sbH = 46.0f;
            if (dragMode == 1) {
                updateSBFromMouse(mouseX, mouseY, panelX, panelY, panelW, sbH);
            } else if (dragMode == 2) {
                updateHueFromMouse(mouseX, panelX, panelW);
            } else if (dragMode == 3) {
                updateAlphaFromMouse(mouseX, panelX, panelW);
            }
            return true;
        }
        return false;
    }

    public ColorSetting getColorSetting() {
        return colorSetting;
    }
}
