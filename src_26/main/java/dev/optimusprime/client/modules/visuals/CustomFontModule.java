package dev.optimusprime.client.modules.visuals;

import dev.optimusprime.client.modules.Category;
import dev.optimusprime.client.modules.Module;
import dev.optimusprime.client.setting.BooleanSetting;
import dev.optimusprime.client.setting.ModeSetting;
import dev.optimusprime.client.setting.NumberSetting;

public class CustomFontModule extends Module {
    private static CustomFontModule INSTANCE;

    private final ModeSetting fontStyle = new ModeSetting("Style", "Стиль отображения шрифта", "Modern", "Vanilla", "Modern", "Smooth");
    private final BooleanSetting shadow = new BooleanSetting("Shadow", "Отображение теней у текста", true);
    private final NumberSetting scale = new NumberSetting("Scale", "Масштаб шрифта", 1.0, 0.8, 1.2, 0.05);

    public CustomFontModule() {
        super("CustomFont", "Кастомизация шрифтов: масштабирование, стили и управление тенями", Category.VISUALS);
        addSetting(fontStyle);
        addSetting(shadow);
        addSetting(scale);
        INSTANCE = this;
        setEnabled(true);
    }

    public static CustomFontModule getInstance() {
        return INSTANCE;
    }

    public boolean isCustomShadow() {
        return isEnabled() ? shadow.isEnabled() : true;
    }

    public float getFontScale() {
        return isEnabled() ? scale.getFloatValue() : 1.0f;
    }

    public String getFontStyle() {
        return fontStyle.getMode();
    }
}
