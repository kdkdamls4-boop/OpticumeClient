package dev.opticumeclient.client.modules.visuals;

import dev.opticumeclient.client.modules.Category;
import dev.opticumeclient.client.modules.Module;
import dev.opticumeclient.client.setting.BooleanSetting;
import dev.opticumeclient.client.setting.ModeSetting;
import dev.opticumeclient.client.setting.NumberSetting;

/**
 * Ported from QuickClient / Deluxe / SystemDLC:
 * Custom 3D perspective aspect ratio (4:3, 16:10, 5:4, 16:9, 21:9, or Custom slider)
 * without distorting the 2D HUD or ClickGUI.
 */
public class AspectRatioModule extends Module {
    private static AspectRatioModule instance;

    private final ModeSetting presetSetting;
    private final NumberSetting customRatioSetting;
    private final BooleanSetting affectHandsSetting;

    public AspectRatioModule() {
        super("AspectRatio", "Кастомное соотношение сторон 3D-камеры (растяг 4:3, 5:4, 16:10, Custom) без искажения HUD", Category.VISUALS);
        instance = this;

        this.presetSetting = addSetting(new ModeSetting(
                "Preset",
                "Пресет соотношения сторон экрана",
                "4:3",
                "4:3", "16:10", "5:4", "16:9", "21:9", "Custom"
        ));
        this.customRatioSetting = addSetting(new NumberSetting(
                "Custom Ratio",
                "Точный коэффициент ширины к высоте (W / H)",
                1.33f, 0.80f, 2.50f, 0.01f
        ).visibleWhen(() -> presetSetting.isMode("Custom")));
        this.affectHandsSetting = addSetting(new BooleanSetting(
                "Affect Hands",
                "Применять соотношение сторон также к 3D-рендеру рук от первого лица",
                true
        ));
    }

    public static AspectRatioModule getInstance() {
        return instance;
    }

    public float getTargetRatio() {
        return switch (presetSetting.getMode()) {
            case "4:3" -> 4.0f / 3.0f;
            case "16:10" -> 16.0f / 10.0f;
            case "5:4" -> 5.0f / 4.0f;
            case "16:9" -> 16.0f / 9.0f;
            case "21:9" -> 21.0f / 9.0f;
            default -> customRatioSetting.getFloatValue();
        };
    }

    public float modifyProjectionWidth(float width, float height) {
        if (!isEnabled() || height <= 0.0f) {
            return width;
        }
        return height * getTargetRatio();
    }

    public boolean isAffectHands() {
        return isEnabled() && affectHandsSetting.isEnabled();
    }
}
