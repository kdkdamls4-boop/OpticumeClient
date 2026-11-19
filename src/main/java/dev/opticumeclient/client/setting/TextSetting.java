package dev.opticumeclient.client.setting;

import java.util.Objects;

public class TextSetting extends Setting<String> {
    private String placeholder = "";
    private int maxLength = 64;

    public TextSetting(String name, String description, String defaultValue) {
        super(name, description, defaultValue == null ? "" : defaultValue);
    }

    public TextSetting(String name, String description, String defaultValue, String placeholder, int maxLength) {
        super(name, description, defaultValue == null ? "" : defaultValue);
        this.placeholder = placeholder == null ? "" : placeholder;
        this.maxLength = Math.max(1, maxLength);
    }

    public String getText() {
        String v = getValue();
        return v == null ? "" : v;
    }

    public void setText(String text) {
        setValue(text);
    }

    @Override
    public void setValue(String value) {
        String safe = value == null ? "" : value;
        if (safe.length() > maxLength) {
            safe = safe.substring(0, maxLength);
        }
        if (!Objects.equals(getValue(), safe)) {
            super.setValue(safe);
        }
    }

    public String getPlaceholder() {
        return placeholder;
    }

    public void setPlaceholder(String placeholder) {
        this.placeholder = placeholder == null ? "" : placeholder;
    }

    public int getMaxLength() {
        return maxLength;
    }
}
