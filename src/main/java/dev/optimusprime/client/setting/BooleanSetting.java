package dev.optimusprime.client.setting;

public class BooleanSetting extends Setting<Boolean> {
    public BooleanSetting(String name, String description, boolean defaultValue) {
        super(name, description, defaultValue);
    }

    public boolean isEnabled() {
        return getValue();
    }

    public void setEnabled(boolean enabled) {
        setValue(enabled);
    }

    public void toggle() {
        setValue(!getValue());
    }
}
