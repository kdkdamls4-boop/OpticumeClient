package dev.opticumeclient.client.setting;

public class ColorSetting extends Setting<Integer> {
    private boolean rainbow;

    public ColorSetting(String name, String description, int defaultRgba) {
        super(name, description, defaultRgba);
        this.rainbow = false;
    }

    public boolean isRainbow() {
        return rainbow;
    }

    public int getColor() {
        return getValue();
    }

    public void setRainbow(boolean rainbow) {
        this.rainbow = rainbow;
    }

    public int getRed() {
        return (getValue() >> 16) & 0xFF;
    }

    public int getGreen() {
        return (getValue() >> 8) & 0xFF;
    }

    public int getBlue() {
        return getValue() & 0xFF;
    }

    public int getAlpha() {
        return (getValue() >> 24) & 0xFF;
    }

    public static int rgba(int r, int g, int b, int a) {
        return ((a & 0xFF) << 24) | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
    }
}
