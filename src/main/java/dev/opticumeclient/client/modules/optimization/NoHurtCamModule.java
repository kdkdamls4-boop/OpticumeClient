package dev.opticumeclient.client.modules.optimization;

import dev.opticumeclient.client.modules.Category;
import dev.opticumeclient.client.modules.Module;

public class NoHurtCamModule extends Module {
    private static NoHurtCamModule INSTANCE;

    public NoHurtCamModule() {
        super("NoHurtCam", "Отключение раздражающей тряски экрана при получении урона", Category.OPTIMIZATION);
        INSTANCE = this;
        setEnabled(true);
    }

    public static NoHurtCamModule getInstance() {
        return INSTANCE;
    }
}
