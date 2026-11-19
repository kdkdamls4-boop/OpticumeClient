package dev.opticumeclient.client.modules.visuals;

import dev.opticumeclient.client.modules.Category;
import dev.opticumeclient.client.modules.Module;
import dev.opticumeclient.client.modules.visuals.atmosphere.AtmosphereRenderer;
import dev.opticumeclient.client.render.Theme;
import dev.opticumeclient.client.render.ThemeManager;
import dev.opticumeclient.client.setting.BooleanSetting;
import dev.opticumeclient.client.setting.ColorSetting;
import dev.opticumeclient.client.setting.ModeSetting;
import dev.opticumeclient.client.setting.NumberSetting;

/**
 * Desire-ported Atmosphere module (MC 26.3):
 * Volumetric height fog, low haze banks, GPU rain/snow layers,
 * surface puddle ripples, and procedural screen-projected lightning.
 */
public class AtmosphereModule extends Module {
    private static AtmosphereModule INSTANCE;

    private final ModeSetting preset = new ModeSetting("Preset", "Готовый профиль атмосферы", "Storm", "Storm", "Drizzle", "Snowfall", "Sunset Mist", "Cyber Fog", "Custom");
    private final ModeSetting fogColorMode = new ModeSetting("Color Mode", "Цвет объёмного тумана", "Preset", "Preset", "Sync", "Custom");
    private final ColorSetting fogColor = new ColorSetting("Fog Color", "Пользовательский цвет тумана", 0xFF2B3A55, true);

    private final BooleanSetting fogEnabled = new BooleanSetting("Volumetric Fog", "Экспоненциальный высотный туман", true);
    private final NumberSetting fogDensity = new NumberSetting("Fog Density", "Плотность тумана", 0.012, 0.001, 0.060, 0.001);
    private final NumberSetting fogHeight = new NumberSetting("Fog Height", "Масштаб высоты рассеяния", 32.0, 6.0, 96.0, 2.0);
    private final NumberSetting turbulence = new NumberSetting("Turbulence", "Клубы и неоднородность тумана", 0.55, 0.0, 1.0, 0.05);
    private final NumberSetting sunScatter = new NumberSetting("Sun Scatter", "Рассеяние солнечного света в тумане", 0.65, 0.0, 2.0, 0.05);

    private final BooleanSetting lowHaze = new BooleanSetting("Ground Haze", "Низкая стелющаяся дымка", true);
    private final NumberSetting hazeDensity = new NumberSetting("Haze Density", "Плотность нижней дымки", 0.018, 0.0, 0.080, 0.002);

    private final BooleanSetting precipitation = new BooleanSetting("Precipitation", "Шейдерный дождь и снег по глубине", true);
    private final ModeSetting precipType = new ModeSetting("Precip Type", "Тип осадков", "Rain", "Rain", "Snow");
    private final NumberSetting precipDensity = new NumberSetting("Precip Density", "Плотность капель/снежинок", 0.48, 0.05, 1.0, 0.05);

    private final BooleanSetting ripples = new BooleanSetting("Surface Ripples", "Круги на мокрой поверхности", true);
    private final NumberSetting rippleStrength = new NumberSetting("Ripple Strength", "Яркость кругов от капель", 0.65, 0.05, 1.5, 0.05);

    private final BooleanSetting lightning = new BooleanSetting("Lightning", "Вспышки и 3D-разряды молний", true);
    private final NumberSetting windStrength = new NumberSetting("Wind", "Сила ветра для осадков и тумана", 0.70, 0.0, 2.0, 0.05);

    public AtmosphereModule() {
        super("Atmosphere", "Шейдерный объёмный туман, дымка, осадки и молнии", Category.VISUALS);

        fogColor.visibleWhen(() -> fogColorMode.is("Custom"));
        fogDensity.visibleWhen(fogEnabled::isEnabled);
        fogHeight.visibleWhen(fogEnabled::isEnabled);
        turbulence.visibleWhen(fogEnabled::isEnabled);
        sunScatter.visibleWhen(fogEnabled::isEnabled);
        hazeDensity.visibleWhen(lowHaze::isEnabled);
        precipType.visibleWhen(() -> precipitation.isEnabled() && preset.is("Custom"));
        precipDensity.visibleWhen(precipitation::isEnabled);
        rippleStrength.visibleWhen(ripples::isEnabled);

        addSetting(preset);
        addSetting(fogColorMode);
        addSetting(fogColor);
        addSetting(fogEnabled);
        addSetting(fogDensity);
        addSetting(fogHeight);
        addSetting(turbulence);
        addSetting(sunScatter);
        addSetting(lowHaze);
        addSetting(hazeDensity);
        addSetting(precipitation);
        addSetting(precipType);
        addSetting(precipDensity);
        addSetting(ripples);
        addSetting(rippleStrength);
        addSetting(lightning);
        addSetting(windStrength);

        INSTANCE = this;
    }

    public static AtmosphereModule getInstance() {
        return INSTANCE;
    }

    public boolean isFogEnabled() {
        return fogEnabled.isEnabled();
    }

    public boolean isHazeEnabled() {
        return lowHaze.isEnabled();
    }

    public boolean isPrecipitationEnabled() {
        if (!precipitation.isEnabled()) {
            return false;
        }
        return !preset.is("Sunset Mist") && !preset.is("Cyber Fog");
    }

    public boolean isSnowMode() {
        if (preset.is("Snowfall")) {
            return true;
        }
        if (preset.is("Storm") || preset.is("Drizzle")) {
            return false;
        }
        return precipType.is("Snow");
    }

    public boolean isRipplesEnabled() {
        return ripples.isEnabled() && !isSnowMode() && isPrecipitationEnabled();
    }

    public boolean isLightningEnabled() {
        if (!lightning.isEnabled()) {
            return false;
        }
        return preset.is("Storm") || preset.is("Custom");
    }

    public int getEffectiveFogColor() {
        if (fogColorMode.is("Custom")) {
            return fogColor.getColor();
        }
        if (fogColorMode.is("Sync")) {
            Theme theme = ThemeManager.getInstance().getCurrentTheme();
            if (theme != null) {
                return theme.getPrimaryColor();
            }
        }
        return switch (preset.getValue()) {
            case "Drizzle" -> 0xFF3E4C5E;
            case "Snowfall" -> 0xFFC5D5E8;
            case "Sunset Mist" -> 0xFFC9785A;
            case "Cyber Fog" -> {
                Theme theme = ThemeManager.getInstance().getCurrentTheme();
                yield theme != null ? theme.getPrimaryColor() : 0xFF7038D4;
            }
            default -> 0xFF243044;
        };
    }

    public float getEffectiveFogDensity() {
        return switch (preset.getValue()) {
            case "Drizzle" -> fogDensity.getFloatValue() * 0.72f;
            case "Snowfall" -> fogDensity.getFloatValue() * 0.85f;
            case "Sunset Mist" -> fogDensity.getFloatValue() * 0.65f;
            case "Cyber Fog" -> fogDensity.getFloatValue() * 0.90f;
            default -> fogDensity.getFloatValue();
        };
    }

    public float getEffectiveFogHeight() {
        return switch (preset.getValue()) {
            case "Sunset Mist" -> fogHeight.getFloatValue() * 1.3f;
            case "Snowfall" -> fogHeight.getFloatValue() * 1.15f;
            default -> fogHeight.getFloatValue();
        };
    }

    public float getEffectiveHazeDensity() {
        return switch (preset.getValue()) {
            case "Drizzle" -> hazeDensity.getFloatValue() * 0.65f;
            case "Sunset Mist" -> hazeDensity.getFloatValue() * 1.2f;
            default -> hazeDensity.getFloatValue();
        };
    }

    public float getEffectivePrecipDensity() {
        return switch (preset.getValue()) {
            case "Drizzle" -> precipDensity.getFloatValue() * 0.55f;
            case "Snowfall" -> precipDensity.getFloatValue() * 0.80f;
            default -> precipDensity.getFloatValue();
        };
    }

    public float getTurbulence() {
        return turbulence.getFloatValue();
    }

    public float getSunScatter() {
        return sunScatter.getFloatValue();
    }

    public float getRippleStrength() {
        return rippleStrength.getFloatValue();
    }

    public float getWindStrength() {
        return windStrength.getFloatValue();
    }

    @Override
    public void onDisable() {
        AtmosphereRenderer.getInstance().reset();
    }
}
