package dev.optimusprime.client.render;

public class Theme {
    private final String name;
    private final int primaryColor;
    private final int secondaryColor;
    private final int backgroundColor;
    private final int cardColor;
    private final int textColor;
    private final int mutedTextColor;

    public Theme(String name, int primaryColor, int secondaryColor, int backgroundColor, int cardColor, int textColor, int mutedTextColor) {
        this.name = name;
        this.primaryColor = primaryColor;
        this.secondaryColor = secondaryColor;
        this.backgroundColor = backgroundColor;
        this.cardColor = cardColor;
        this.textColor = textColor;
        this.mutedTextColor = mutedTextColor;
    }

    public String getName() {
        return name;
    }

    public int getPrimaryColor() {
        return primaryColor;
    }

    public int getSecondaryColor() {
        return secondaryColor;
    }

    public int getBackgroundColor() {
        return backgroundColor;
    }

    public int getCardColor() {
        return cardColor;
    }

    public int getTextColor() {
        return textColor;
    }

    public int getMutedTextColor() {
        return mutedTextColor;
    }
}
