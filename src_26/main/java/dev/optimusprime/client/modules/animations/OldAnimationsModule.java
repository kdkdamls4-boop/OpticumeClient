package dev.optimusprime.client.modules.animations;

import dev.optimusprime.client.modules.Category;
import dev.optimusprime.client.modules.Module;
import dev.optimusprime.client.setting.BooleanSetting;
import dev.optimusprime.client.setting.NumberSetting;

public class OldAnimationsModule extends Module {
    private static OldAnimationsModule INSTANCE;

    private final BooleanSetting blockHit = new BooleanSetting("BlockHit", "Классический блок-хит 1.7 мечом", true);
    private final NumberSetting swingSpeed = new NumberSetting("SwingSpeed", "Скорость взмаха руки", 1.0, 0.5, 2.5, 0.1);
    private final NumberSetting scale = new NumberSetting("Scale", "Масштаб предмета в руке", 1.0, 0.5, 1.5, 0.05);

    public OldAnimationsModule() {
        super("1.7Animations", "Боевые анимации ударов и блок-хита в стиле Minecraft 1.7", Category.VISUALS);
        addSetting(blockHit);
        addSetting(swingSpeed);
        addSetting(scale);
        INSTANCE = this;
        setEnabled(true);
    }

    public static OldAnimationsModule getInstance() {
        return INSTANCE;
    }

    public boolean isBlockHitEnabled() {
        return isEnabled() && blockHit.isEnabled();
    }

    public float getSwingSpeed() {
        return swingSpeed.getFloatValue();
    }

    public float getItemScale() {
        return scale.getFloatValue();
    }
}
