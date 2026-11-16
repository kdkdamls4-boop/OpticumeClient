package dev.opticumeclient.client.setting;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class MultiBoxSetting extends Setting<Set<String>> {
    private final List<String> options;

    public MultiBoxSetting(String name, String description, String[] allOptions, String... defaultEnabled) {
        super(name, description, new LinkedHashSet<>(Arrays.asList(defaultEnabled)));
        this.options = Collections.unmodifiableList(Arrays.asList(allOptions));
    }

    public List<String> getOptions() {
        return options;
    }

    public boolean isEnabled(String option) {
        for (String s : getValue()) {
            if (s.equalsIgnoreCase(option)) {
                return true;
            }
        }
        return false;
    }

    public void toggle(String option) {
        Set<String> set = getValue();
        String existing = null;
        for (String s : set) {
            if (s.equalsIgnoreCase(option)) {
                existing = s;
                break;
            }
        }
        if (existing != null) {
            set.remove(existing);
        } else {
            set.add(option);
        }
    }

    public void set(String option, boolean state) {
        if (state && !isEnabled(option)) {
            getValue().add(option);
        } else if (!state && isEnabled(option)) {
            toggle(option);
        }
    }
}
