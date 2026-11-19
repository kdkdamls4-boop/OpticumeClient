package dev.opticumeclient.client.modules;

public enum Category {
    COMBAT("Combat", 0xFFE53935),
    VISUALS("Visuals", 0xFF00E5FF),
    OPTIMIZATION("Optimization", 0xFF76FF03),
    HUD("HUD", 0xFFFFD600),
    MOVEMENT("Movement", 0xFFD500F9),
    OTHER("Other", 0xFFAB47BC),
    FRIENDS("Friends", 0xFF00E676);

    private final String displayName;
    private final int accentColor;

    Category(String displayName, int accentColor) {
        this.displayName = displayName;
        this.accentColor = accentColor;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getAccentColor() {
        return accentColor;
    }
}
