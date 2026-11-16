package dev.opticumeclient.client.modules.optimization;

import dev.opticumeclient.client.modules.Category;
import dev.opticumeclient.client.modules.Module;
import dev.opticumeclient.client.setting.NumberSetting;

public class LowFireModule extends Module {
    private static LowFireModule INSTANCE;
    private final NumberSetting height = new NumberSetting("Height", "Смещение огня вниз", -0.3, -0.6, 0.0, 0.05);

    public LowFireModule() {
        super("LowFire", "Понижение текстуры огня от первого лица", Category.OPTIMIZATION);
        addSetting(height);
        INSTANCE = this;
        setEnabled(true);
    }

    public static LowFireModule getInstance() {
        return INSTANCE;
    }

    public float getHeightOffset() {
        return isEnabled() ? height.getFloatValue() : 0.0f;
    }
}
