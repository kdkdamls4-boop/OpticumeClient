package dev.opticumeclient.client.setting;

import dev.opticumeclient.client.render.ColorUtils;
import dev.opticumeclient.client.render.ThemeManager;

public class ColorSetting extends Setting<Integer> {
    private boolean themeSync;
    private boolean rainbow;
    private float hue;
    private float saturation;
    private float brightness;

    public ColorSetting(String name, String description, int defaultRgba) {
        this(name, description, defaultRgba, false);
    }

    public ColorSetting(String name, String description, int defaultRgba, boolean defaultThemeSync) {
        super(name, description, defaultRgba);
        this.themeSync = defaultThemeSync;
        this.rainbow = false;
        syncHsbFromInt(defaultRgba);
    }

    private void syncHsbFromInt(int argb) {
        float[] hsb = ColorUtils.rgbToHsb(argb);
        if (hsb[1] > 0.005f && hsb[2] > 0.005f) {
            this.hue = hsb[0];
        }
        this.saturation = hsb[1];
        this.brightness = hsb[2];
    }

    @Override
    public void setValue(Integer value) {
        if (value == null) return;
        super.setValue(value);
        syncHsbFromInt(value);
    }

    public void setFromHsb(float h, float s, float b, int alpha) {
        this.hue = Math.max(0.0f, Math.min(0.9999f, h));
        this.saturation = Math.max(0.0f, Math.min(1.0f, s));
        this.brightness = Math.max(0.0f, Math.min(1.0f, b));
        this.themeSync = false;
        this.rainbow = false;
        super.setValue(ColorUtils.hsbToRgb(this.hue, this.saturation, this.brightness, alpha));
    }

    public float getHue() {
        return hue;
    }

    public float getSaturation() {
        return saturation;
    }

    public float getBrightness() {
        return brightness;
    }

    public boolean isThemeSync() {
        return themeSync;
    }

    public void setThemeSync(boolean themeSync) {
        this.themeSync = themeSync;
        if (themeSync) {
            this.rainbow = false;
        }
    }

    public boolean isRainbow() {
        return rainbow;
    }

    public void setRainbow(boolean rainbow) {
        this.rainbow = rainbow;
        if (rainbow) {
            this.themeSync = false;
        }
    }

    public int getStaticValue() {
        return super.getValue();
    }

    public int getColor() {
        return getColor(0);
    }

    public int getColor(int indexOffset) {
        int alpha = getAlpha();
        if (alpha == 0 && ((super.getValue() >>> 24) & 0xFF) == 0) {
            alpha = 0;
        }
        if (themeSync) {
            return ColorUtils.withAlpha(ThemeManager.getInstance().getAccentColor(indexOffset), alpha);
        }
        if (rainbow) {
            return ColorUtils.withAlpha(ColorUtils.getRainbow(4.0f, 0.85f, 1.0f, indexOffset * 12L), alpha);
        }
        return super.getValue();
    }

    public int getRed() {
        return (getColor() >> 16) & 0xFF;
    }

    public int getGreen() {
        return (getColor() >> 8) & 0xFF;
    }

    public int getBlue() {
        return getColor() & 0xFF;
    }

    public int getAlpha() {
        return (super.getValue() >>> 24) & 0xFF;
    }

    public static int rgba(int r, int g, int b, int a) {
        return ((a & 0xFF) << 24) | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
    }
}
