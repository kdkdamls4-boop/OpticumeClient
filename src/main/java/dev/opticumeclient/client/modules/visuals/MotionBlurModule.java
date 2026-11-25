package dev.opticumeclient.client.modules.visuals;

import dev.opticumeclient.client.modules.Category;
import dev.opticumeclient.client.modules.Module;
import dev.opticumeclient.client.modules.visuals.ambience.MotionBlurRenderer;
import dev.opticumeclient.client.setting.NumberSetting;

/**
 * Smooth RenderPearl screen-space camera velocity MotionBlur post-processor (world-only, before GUI).
 */
public class MotionBlurModule extends Module {
    private static MotionBlurModule instance;

    private final NumberSetting strength = new NumberSetting("Strength", "Сила размытия при повороте камеры", 0.55, 0.1, 1.2, 0.05);
    private final NumberSetting maxBlur = new NumberSetting("Max Blur", "Предел радиуса размытия", 0.032, 0.01, 0.08, 0.002);
    private final NumberSetting samples = new NumberSetting("Samples", "Количество сэмплов сглаживания", 7.0, 4.0, 12.0, 1.0);

    public MotionBlurModule() {
        super("MotionBlur", "Плавное размытие движения камеры в 3D-мире (до HUD и GUI)", Category.VISUALS);
        instance = this;
        addSetting(strength);
        addSetting(maxBlur);
        addSetting(samples);
    }

    public static MotionBlurModule getInstance() {
        return instance;
    }

    public float getStrength() {
        return strength.getFloatValue();
    }

    public float getMaxBlur() {
        return maxBlur.getFloatValue();
    }

    public int getSamples() {
        return samples.getIntValue();
    }

    @Override
    public void onDisable() {
        super.onDisable();
        MotionBlurRenderer.getInstance().reset();
    }
}
