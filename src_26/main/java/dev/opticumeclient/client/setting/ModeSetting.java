package dev.opticumeclient.client.setting;

import java.util.Arrays;
import java.util.List;

public class ModeSetting extends Setting<String> {
    private final List<String> modes;
    private int index;

    public ModeSetting(String name, String description, String defaultMode, String... modes) {
        super(name, description, defaultMode);
        this.modes = Arrays.asList(modes);
        this.index = this.modes.indexOf(defaultMode);
        if (this.index == -1) {
            this.index = 0;
            if (!this.modes.isEmpty()) {
                setValue(this.modes.get(0));
            }
        }
    }

    public List<String> getModes() {
        return modes;
    }

    public int getIndex() {
        return index;
    }

    public String getMode() {
        return getValue();
    }

    public boolean is(String mode) {
        return getValue() != null && getValue().equalsIgnoreCase(mode);
    }

    public void cycle(boolean forward) {
        if (modes.isEmpty()) return;
        if (forward) {
            index = (index + 1) % modes.size();
        } else {
            index = (index - 1 + modes.size()) % modes.size();
        }
        setValue(modes.get(index));
    }

    public void cycle() {
        cycle(true);
    }

    public void setMode(String mode) {
        int idx = modes.indexOf(mode);
        if (idx != -1) {
            this.index = idx;
            setValue(mode);
        }
    }
}
