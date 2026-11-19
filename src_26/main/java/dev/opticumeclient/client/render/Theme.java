package dev.opticumeclient.client.render;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * OpticumeClient Theme holding preset/custom status and editable element colors.
 */
public class Theme {
    public static final String KEY_PRIMARY = "Primary";
    public static final String KEY_SECONDARY = "Secondary";
    public static final String KEY_BACKGROUND = "Background";
    public static final String KEY_CARD = "Card";
    public static final String KEY_KNOB = "Knob";
    public static final String KEY_TEXT = "Text";
    public static final String KEY_MUTED = "Inactive text";

    private final String name;
    private final boolean preset;
    private String storageFileName;
    private final Map<String, Integer> elements = new LinkedHashMap<>();

    public Theme(String name, int primaryColor, int secondaryColor, int backgroundColor, int cardColor, int textColor, int mutedTextColor) {
        this(name, true, primaryColor, secondaryColor, backgroundColor, cardColor, lighten(primaryColor, 0.25f), textColor, mutedTextColor);
    }

    public Theme(String name, boolean preset, int primaryColor, int secondaryColor, int backgroundColor, int cardColor,
                 int knobColor, int textColor, int mutedTextColor) {
        this.name = name;
        this.preset = preset;
        elements.put(KEY_PRIMARY, primaryColor);
        elements.put(KEY_SECONDARY, secondaryColor);
        elements.put(KEY_BACKGROUND, backgroundColor);
        elements.put(KEY_CARD, cardColor);
        elements.put(KEY_KNOB, knobColor);
        elements.put(KEY_TEXT, textColor);
        elements.put(KEY_MUTED, mutedTextColor);
    }

    public String getName() {
        return name;
    }

    public boolean isPreset() {
        return preset;
    }

    public String getStorageFileName() {
        return storageFileName;
    }

    void setStorageFileName(String storageFileName) {
        this.storageFileName = storageFileName;
    }

    public Map<String, Integer> getElements() {
        return Collections.unmodifiableMap(elements);
    }

    public int getElementColor(String key, int fallback) {
        Integer v = elements.get(key);
        return v != null ? v : fallback;
    }

    public void setElementColor(String key, int argb) {
        if (preset || key == null) return;
        if (elements.containsKey(key)) {
            elements.put(key, argb);
        }
    }

    public Theme copyAsCustom(String customName) {
        return new Theme(
                customName,
                false,
                getPrimaryColor(),
                getSecondaryColor(),
                getBackgroundColor(),
                getCardColor(),
                getKnobColor(),
                getTextColor(),
                getMutedTextColor()
        );
    }

    public int getPrimaryColor() {
        return getElementColor(KEY_PRIMARY, 0xFF2DD4BF);
    }

    public int getSecondaryColor() {
        return getElementColor(KEY_SECONDARY, 0xFF0EA5E9);
    }

    public int getBackgroundColor() {
        return getElementColor(KEY_BACKGROUND, 0xEC0B0C12);
    }

    public int getCardColor() {
        return getElementColor(KEY_CARD, 0xD8131520);
    }

    public int getKnobColor() {
        return getElementColor(KEY_KNOB, getPrimaryColor());
    }

    public int getTextColor() {
        return getElementColor(KEY_TEXT, 0xFFF4F6FF);
    }

    public int getMutedTextColor() {
        return getElementColor(KEY_MUTED, 0xFF8C92AC);
    }

    private static int lighten(int argb, float factor) {
        int a = (argb >>> 24) & 0xFF;
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        int nr = Math.min(255, Math.round(r + (255 - r) * factor));
        int ng = Math.min(255, Math.round(g + (255 - g) * factor));
        int nb = Math.min(255, Math.round(b + (255 - b) * factor));
        return ((a == 0 ? 255 : a) << 24) | (nr << 16) | (ng << 8) | nb;
    }
}
