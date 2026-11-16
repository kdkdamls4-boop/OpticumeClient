package dev.opticumeclient.client.gui.component;

import dev.opticumeclient.client.render.Animation;
import dev.opticumeclient.client.render.ColorUtils;
import dev.opticumeclient.client.render.Easing;
import dev.opticumeclient.client.render.Render2DHelper;
import dev.opticumeclient.client.render.Theme;
import dev.opticumeclient.client.render.ThemeManager;
import dev.opticumeclient.client.setting.NumberSetting;
import net.minecraft.client.gui.GuiGraphicsExtractor; 

import java.util.Locale;

/**
 * Smooth gradient slider with mouse dragging, fill interpolation,
 * glowing thumb circle, and right-click reset to default value.
 */
public class SliderComponent extends Component {
    private final NumberSetting numberSetting;
    private boolean dragging;
    private final Animation fillAnimation;
    private final Animation hoverAnimation;

    public SliderComponent(NumberSetting setting, float width) {
        super(setting, width, 24.0f);
        this.numberSetting = setting;
        this.fillAnimation = new Animation(getNormalizedPercent(), 90L, Easing.EASE_OUT_QUAD);
        this.hoverAnimation = new Animation(0.0f, 140L, Easing.EASE_OUT_QUAD);
    }

    private float getNormalizedPercent() {
        double min = numberSetting.getMin();
        double max = numberSetting.getMax();
        double diff = max - min;
        if (diff <= 0.0) return 0.0f;
        return (float) Math.max(0.0, Math.min(1.0, (numberSetting.getValue() - min) / diff));
    }

    private String getFormattedValue() {
        double step = numberSetting.getStep();
        double val = numberSetting.getValue();
        if (step >= 1.0 && Math.abs(step - Math.round(step)) < 0.0001) {
            return String.valueOf((int) Math.round(val));
        } else if (step >= 0.1) {
            return String.format(Locale.ROOT, "%.1f", val);
        } else {
            return String.format(Locale.ROOT, "%.2f", val);
        }
    }

    @Override
    public void render(GuiGraphicsExtractor context, float mouseX, float mouseY, float delta) {
        if (!isVisible()) return;

        Theme theme = ThemeManager.getInstance().getCurrentTheme();
        boolean hovered = isHovered(mouseX, mouseY);
        hoverAnimation.animateTo((hovered || dragging) ? 1.0f : 0.0f);

        if (dragging) {
            updateValueFromMouse(mouseX);
        }

        fillAnimation.animateTo(getNormalizedPercent());
        float fill = fillAnimation.getValue();
        float hoverVal = hoverAnimation.getValue();

        // Label on left
        int labelColor = ColorUtils.interpolateColor(
                theme.getMutedTextColor(),
                theme.getTextColor(),
                hoverVal * 0.65f
        );
        Render2DHelper.drawScaledText(context, numberSetting.getName(), x + 4.0f, y + 2.5f, labelColor, 0.76f, false);

        // Formatted value on right
        String valText = getFormattedValue();
        float valScale = 0.76f;
        float valW = Render2DHelper.getTextWidth(valText, valScale);
        int valColor = ColorUtils.interpolateColor(theme.getPrimaryColor(), theme.getSecondaryColor(), fill);
        Render2DHelper.drawScaledText(context, valText, x + width - valW - 4.0f, y + 2.5f, valColor, valScale, false);

        // Slider Track Bar
        float trackX = x + 4.0f;
        float trackY = y + 15.0f;
        float trackW = width - 8.0f;
        float trackH = 4.0f;
        float trackRadius = 2.0f;

        // Track background
        Render2DHelper.drawRoundedRect(context, trackX, trackY, trackW, trackH, trackRadius, 0x32FFFFFF);

        // Filled gradient bar
        float filledW = Math.max(trackRadius * 2.0f, trackW * fill);
        int c1 = theme.getPrimaryColor();
        int c2 = theme.getSecondaryColor();

        if (hoverVal > 0.05f) {
            Render2DHelper.drawGlow(context, trackX, trackY - 0.5f, filledW, trackH + 1.0f, 3.5f,
                    ColorUtils.withAlpha(c1, (int) (85 * hoverVal)));
        }

        Render2DHelper.drawGradientRoundedRect(context, trackX, trackY, filledW, trackH, trackRadius, c1, c2);

        // Glowing circular thumb knob
        float knobRadius = 3.8f + hoverVal * 0.7f;
        float knobCX = trackX + Math.max(knobRadius, Math.min(trackW - knobRadius, trackW * fill));
        float knobCY = trackY + trackH * 0.5f;

        Render2DHelper.drawGlow(context, knobCX - knobRadius, knobCY - knobRadius,
                knobRadius * 2.0f, knobRadius * 2.0f, 4.0f,
                ColorUtils.withAlpha(c1, (int) (110 + 90 * hoverVal)));
        Render2DHelper.drawCircle(context, knobCX, knobCY, knobRadius, 0xFFFFFFFF);
        Render2DHelper.drawRoundedOutline(context, knobCX - knobRadius, knobCY - knobRadius,
                knobRadius * 2.0f, knobRadius * 2.0f, knobRadius, 1.0f,
                ColorUtils.withAlpha(c1, 220));
    }

    private void updateValueFromMouse(double mouseX) {
        float trackX = x + 4.0f;
        float trackW = width - 8.0f;
        if (trackW <= 0.0f) return;

        double fraction = Math.max(0.0, Math.min(1.0, (mouseX - trackX) / trackW));
        double min = numberSetting.getMin();
        double max = numberSetting.getMax();
        double targetVal = min + fraction * (max - min);
        numberSetting.setValueClamped(targetVal);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!isVisible()) return false;
        if (isHovered(mouseX, mouseY)) {
            if (button == 0) {
                dragging = true;
                updateValueFromMouse(mouseX);
                return true;
            } else if (button == 1) {
                // Right click resets slider to default value
                numberSetting.reset();
                dragging = false;
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && dragging) {
            dragging = false;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (button == 0 && dragging) {
            updateValueFromMouse(mouseX);
            return true;
        }
        return false;
    }

    public NumberSetting getNumberSetting() {
        return numberSetting;
    }
}
