package dev.optimusprime.client.modules.optimization;

import dev.optimusprime.client.modules.Category;
import dev.optimusprime.client.modules.Module;
import dev.optimusprime.client.setting.NumberSetting;

public class ChunkAnimatorModule extends Module {
    private static ChunkAnimatorModule INSTANCE;
    private final NumberSetting animationSpeed = new NumberSetting("Speed", "Скорость анимации подъема чанков", 1.0, 0.2, 3.0, 0.1);

    public ChunkAnimatorModule() {
        super("ChunkAnimator", "Плавная анимация подъема загружающихся чанков", Category.OPTIMIZATION);
        addSetting(animationSpeed);
        INSTANCE = this;
        setEnabled(true);
    }

    public static ChunkAnimatorModule getInstance() {
        return INSTANCE;
    }

    public float getSpeed() {
        return animationSpeed.getFloatValue();
    }
}
