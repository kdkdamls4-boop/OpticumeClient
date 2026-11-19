package dev.opticumeclient.client.setting;

public class RunSetting extends Setting<Runnable> {
    public RunSetting(String name, String description, Runnable action) {
        super(name, description, action);
    }

    public RunSetting(String name, Runnable action) {
        this(name, "", action);
    }

    public void run() {
        Runnable r = getValue();
        if (r != null) {
            r.run();
        }
    }
}
