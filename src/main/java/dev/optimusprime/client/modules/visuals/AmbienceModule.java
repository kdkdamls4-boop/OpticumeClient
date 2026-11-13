package dev.optimusprime.client.modules.visuals;

import dev.optimusprime.client.modules.Category;
import dev.optimusprime.client.modules.Module;
import dev.optimusprime.client.render.ThemeManager;
import dev.optimusprime.client.setting.BooleanSetting;
import dev.optimusprime.client.setting.ColorSetting;
import dev.optimusprime.client.setting.ModeSetting;
import dev.optimusprime.client.setting.NumberSetting;

public class AmbienceModule extends Module {
    private static AmbienceModule INSTANCE;

    private final ModeSetting timeMode = new ModeSetting("TimeMode", "Режим времени суток", "None", "None", "Day", "Sunset", "Night", "Custom");
    private final NumberSetting time = new NumberSetting("Time", "Время суток (тиков)", 18000.0, 0.0, 24000.0, 500.0);
    private final ModeSetting skyMode = new ModeSetting("SkyMode", "Цвет неба", "Theme", "Theme", "Theme Secondary", "Custom", "Vanilla");
    private final NumberSetting skyBrightness = new NumberSetting("SkyBrightness", "Яркость неба", 0.65, 0.2, 1.0, 0.05);
    private final ColorSetting customSky = new ColorSetting("CustomSky", "Кастомный цвет неба", 0xFF140D24);
    private final BooleanSetting customFog = new BooleanSetting("CustomFog", "Кастомизация тумана", true);
    private final ModeSetting fogMode = new ModeSetting("FogMode", "Цвет тумана", "Sync Sky", "Sync Sky", "Theme", "Theme Secondary", "Custom", "Vanilla");
    private final NumberSetting fogBrightness = new NumberSetting("FogBrightness", "Яркость тумана", 0.65, 0.2, 1.0, 0.05);
    private final ColorSetting customFogColor = new ColorSetting("CustomFogColor", "Кастомный цвет тумана", 0xFF0D0E15);
    private final NumberSetting fogDist = new NumberSetting("FogDist", "Дистанция тумана", 45.0, 10.0, 120.0, 5.0);
    private final NumberSetting fogDensity = new NumberSetting("FogDensity", "Плотность тумана", 0.65, 0.1, 1.0, 0.05);

    public AmbienceModule() {
        super("Ambience", "Оверрайд времени суток без десинка и неоновый оттенок неба/тумана", Category.VISUALS);
        addSetting(timeMode);
        addSetting(time);
        addSetting(skyMode);
        addSetting(skyBrightness);
        addSetting(customSky);
        addSetting(customFog);
        addSetting(fogMode);
        addSetting(fogBrightness);
        addSetting(customFogColor);
        addSetting(fogDist);
        addSetting(fogDensity);
        INSTANCE = this;
    }

    public static AmbienceModule getInstance() {
        return INSTANCE;
    }

    @Override
    public String getDisplaySuffix() {
        return timeMode.is("None") ? skyMode.getMode() : timeMode.getMode();
    }

    public String getTimeMode() {
        return timeMode.getMode();
    }

    public float getCustomTime() {
        return time.getFloatValue();
    }

    public String getSkyMode() {
        return skyMode.getMode();
    }

    public float getSkyBrightness() {
        return skyBrightness.getFloatValue();
    }

    public int getCustomSkyColor() {
        return customSky.getColor();
    }

    public boolean isCustomFogEnabled() {
        return customFog.isEnabled();
    }

    public String getFogMode() {
        return fogMode.getMode();
    }

    public float getFogBrightness() {
        return fogBrightness.getFloatValue();
    }

    public int getCustomFogColor() {
        return customFogColor.getColor();
    }

    public int getSkyBaseColor() {
        return switch (skyMode.getMode()) {
            case "Theme Secondary" -> ThemeManager.getInstance().getCurrentTheme().getSecondaryColor();
            case "Custom" -> customSky.getColor();
            default -> ThemeManager.getInstance().getCurrentTheme().getPrimaryColor();
        };
    }

    public int getFogBaseColor() {
        return switch (fogMode.getMode()) {
            case "Theme Secondary" -> ThemeManager.getInstance().getCurrentTheme().getSecondaryColor();
            case "Custom" -> customFogColor.getColor();
            default -> ThemeManager.getInstance().getCurrentTheme().getPrimaryColor();
        };
    }

    public int getFogColor() {
        return getFogBaseColor();
    }

    public float getFogDistance() {
        return fogDist.getFloatValue();
    }

    public float getFogDensity() {
        return fogDensity.getFloatValue();
    }
}
