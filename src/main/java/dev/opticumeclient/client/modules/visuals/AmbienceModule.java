package dev.opticumeclient.client.modules.visuals;

import dev.opticumeclient.client.modules.Category;
import dev.opticumeclient.client.modules.Module;
import dev.opticumeclient.client.modules.visuals.ambience.SkyPipelines;
import dev.opticumeclient.client.render.Theme;
import dev.opticumeclient.client.render.ThemeManager;
import dev.opticumeclient.client.setting.BooleanSetting;
import dev.opticumeclient.client.setting.ColorSetting;
import dev.opticumeclient.client.setting.ModeSetting;
import dev.opticumeclient.client.setting.NumberSetting;
import org.joml.Vector4f;

/**
 * Desire-ported Ambience visual module (MC 26.3):
 * Custom procedural shader skybox (9 presets; broken Aurora excluded),
 * time & moon phase override, custom fog + depth-weighted Kawase FogBlur,
 * screen-space Wet World / Wet Solution puddles & SSR, and post saturation.
 */
public class AmbienceModule extends Module {
    private static AmbienceModule INSTANCE;

    private final BooleanSetting customTime = new BooleanSetting("Custom Time", "Изменение времени суток на клиенте", false);
    private final ModeSetting timeMode = new ModeSetting("Time Mode", "Пресет времени суток", "Night", "Day", "Sunset", "Night", "Midnight", "Custom");
    private final NumberSetting timeTicks = new NumberSetting("Time Ticks", "Точное время в тиках (0..24000)", 18000, 0, 24000, 100);
    private final BooleanSetting moonPhaseOverride = new BooleanSetting("Moon Phase", "Своя фаза луны", false);
    private final NumberSetting moonPhase = new NumberSetting("Phase Index", "Индекс фазы луны (0..7)", 0, 0, 7, 1);

    private final ModeSetting skyMode = new ModeSetting(
            "Sky Mode",
            "Режим неба",
            "aurora2",
            "Vanilla",
            "aurora2",
            "Northern Lights",
            "Sakura",
            "Plasma",
            "Plasma 2",
            "Night",
            "Summer Sky",
            "Caustic",
            "Thunder",
            "Fog Only"
    );
    private final ModeSetting colorMode = new ModeSetting("Color Mode", "Источник цвета неба и тумана", "Sync", "Sync", "Custom");
    private final ColorSetting customColor = new ColorSetting("Primary Color", "Основной цвет неба", 0xFF7C4DFF, true);
    private final ColorSetting customColor2 = new ColorSetting("Secondary Color", "Второй цвет градиента", 0xFF00E5FF, false);
    private final NumberSetting skySpeed = new NumberSetting("Sky Speed", "Скорость анимации неба", 1.0, 0.1, 5.0, 0.1);
    private final NumberSetting skyScale = new NumberSetting("Sky Scale", "Масштаб узоров неба", 5.0, 1.0, 20.0, 0.5);
    private final NumberSetting skyIntensity = new NumberSetting("Sky Intensity", "Яркость и насыщенность свечения", 0.40, 0.05, 1.50, 0.05);
    private final NumberSetting skyOpacity = new NumberSetting("Sky Opacity", "Непрозрачность шейдерного неба", 1.0, 0.1, 1.0, 0.05);
    private final NumberSetting nightBlend = new NumberSetting("Night Blend", "Степень ночного тона (Summer/Sakura)", 1.0, 0.0, 1.0, 0.05);
    private final NumberSetting themeMix = new NumberSetting("Theme Mix", "Сила смешивания с цветом темы", 0.65, 0.0, 1.0, 0.05);
    private final NumberSetting thunderInterval = new NumberSetting("Thunder Interval", "Интервал молний в Thunder Sky", 8.0, 2.0, 20.0, 0.5);
    private final NumberSetting thunderChance = new NumberSetting("Thunder Chance", "Шанс вспышки молнии", 0.55, 0.05, 1.0, 0.05);
    private final NumberSetting thunderGlow = new NumberSetting("Thunder Glow", "Свечение облаков при молнии", 1.4, 0.2, 3.0, 0.1);
    private final BooleanSetting hideVanillaSky = new BooleanSetting("Hide Vanilla Sky", "Скрывать ванильный купол неба и звезды", true);
    private final BooleanSetting hideSunMoon = new BooleanSetting("Hide Sun/Moon", "Скрывать солнце и луну", false);
    private final NumberSetting sunScale = new NumberSetting("Sun Scale", "Масштаб солнца", 1.0, 0.2, 4.0, 0.1);
    private final NumberSetting moonScale = new NumberSetting("Moon Scale", "Масштаб луны", 1.0, 0.2, 4.0, 0.1);

    private final BooleanSetting customFog = new BooleanSetting("Custom Fog", "Кастомный цвет и дистанция тумана", false);
    private final ColorSetting fogColor = new ColorSetting("Fog Color", "Цвет тумана", 0xFF1F2B47, true);
    private final NumberSetting fogStart = new NumberSetting("Fog Start", "Начало тумана (блоки)", 12.0, 0.0, 120.0, 1.0);
    private final NumberSetting fogEnd = new NumberSetting("Fog End", "Конец тумана (блоки)", 96.0, 4.0, 256.0, 2.0);

    private final BooleanSetting fogBlur = new BooleanSetting("Fog Blur", "Kawase-размытие дальнего ландшафта", false);
    private final NumberSetting fogBlurStrength = new NumberSetting("Blur Strength", "Сила размытия вдали", 0.65, 0.05, 1.0, 0.05);
    private final NumberSetting fogBlurStart = new NumberSetting("Blur Start", "Начало размытия", 24.0, 2.0, 128.0, 2.0);
    private final NumberSetting fogBlurEnd = new NumberSetting("Blur End", "Полное размытие", 110.0, 8.0, 256.0, 4.0);

    private final BooleanSetting wetWorld = new BooleanSetting("Wet World", "Мокрые поверхности, лужи и SSR-отражения", false);
    private final ModeSetting wetMode = new ModeSetting("Wet Shader", "Вариант шейдера отражений", "Modern", "Modern", "Classic");
    private final ModeSetting wetQuality = new ModeSetting("Wet Quality", "Качество трассировки лучей SSR", "High", "Low", "Medium", "High", "Ultra");
    private final NumberSetting wetIntensity = new NumberSetting("Wet Intensity", "Интенсивность намокания", 0.75, 0.1, 1.0, 0.05);
    private final NumberSetting puddleCoverage = new NumberSetting("Puddles", "Покрытие лужами", 0.55, 0.05, 1.0, 0.05);
    private final NumberSetting reflectionStrength = new NumberSetting("Reflections", "Сила отражений", 0.85, 0.05, 1.5, 0.05);
    private final NumberSetting wetDistance = new NumberSetting("Wet Distance", "Дальность эффекта мокрого мира", 48.0, 8.0, 96.0, 2.0);
    private final BooleanSetting wetRipples = new BooleanSetting("Rain Ripples", "Рябь от капель на лужах", true);
    private final NumberSetting rippleStrength = new NumberSetting("Ripple Strength", "Сила волн и кругов", 0.70, 0.05, 1.5, 0.05);

    private final BooleanSetting saturationEnabled = new BooleanSetting("Saturation", "Пост-насыщенность картинки мира", false);
    private final NumberSetting saturationLevel = new NumberSetting("Saturation Level", "Множитель насыщенности", 1.25, 0.0, 2.5, 0.05);

    private float lastFogStart = 12.0f;
    private float lastFogEnd = 96.0f;
    private final Vector4f lastFogColor = new Vector4f(0.15f, 0.20f, 0.30f, 1.0f);

    public AmbienceModule() {
        super("Ambience", "Шейдерное небо, кастомное время, туман и мокрые отражения", Category.VISUALS);

        timeMode.visibleWhen(customTime::isEnabled);
        timeTicks.visibleWhen(() -> customTime.isEnabled() && timeMode.is("Custom"));
        moonPhase.visibleWhen(moonPhaseOverride::isEnabled);

        customColor.visibleWhen(() -> colorMode.is("Custom"));
        customColor2.visibleWhen(() -> colorMode.is("Custom"));
        thunderInterval.visibleWhen(() -> skyMode.is("Thunder"));
        thunderChance.visibleWhen(() -> skyMode.is("Thunder"));
        thunderGlow.visibleWhen(() -> skyMode.is("Thunder"));
        hideVanillaSky.visibleWhen(this::isShaderSkyActive);

        fogColor.visibleWhen(customFog::isEnabled);
        fogStart.visibleWhen(customFog::isEnabled);
        fogEnd.visibleWhen(customFog::isEnabled);

        fogBlurStrength.visibleWhen(fogBlur::isEnabled);
        fogBlurStart.visibleWhen(fogBlur::isEnabled);
        fogBlurEnd.visibleWhen(fogBlur::isEnabled);

        wetMode.visibleWhen(wetWorld::isEnabled);
        wetQuality.visibleWhen(wetWorld::isEnabled);
        wetIntensity.visibleWhen(wetWorld::isEnabled);
        puddleCoverage.visibleWhen(() -> wetWorld.isEnabled() && wetMode.is("Modern"));
        reflectionStrength.visibleWhen(wetWorld::isEnabled);
        wetDistance.visibleWhen(wetWorld::isEnabled);
        wetRipples.visibleWhen(wetWorld::isEnabled);
        rippleStrength.visibleWhen(() -> wetWorld.isEnabled() && wetRipples.isEnabled());

        saturationLevel.visibleWhen(saturationEnabled::isEnabled);

        addSetting(customTime);
        addSetting(timeMode);
        addSetting(timeTicks);
        addSetting(moonPhaseOverride);
        addSetting(moonPhase);

        addSetting(skyMode);
        addSetting(colorMode);
        addSetting(customColor);
        addSetting(customColor2);
        addSetting(skySpeed);
        addSetting(skyScale);
        addSetting(skyIntensity);
        addSetting(skyOpacity);
        addSetting(nightBlend);
        addSetting(themeMix);
        addSetting(thunderInterval);
        addSetting(thunderChance);
        addSetting(thunderGlow);
        addSetting(hideVanillaSky);
        addSetting(hideSunMoon);
        addSetting(sunScale);
        addSetting(moonScale);

        addSetting(customFog);
        addSetting(fogColor);
        addSetting(fogStart);
        addSetting(fogEnd);

        addSetting(fogBlur);
        addSetting(fogBlurStrength);
        addSetting(fogBlurStart);
        addSetting(fogBlurEnd);

        addSetting(wetWorld);
        addSetting(wetMode);
        addSetting(wetQuality);
        addSetting(wetIntensity);
        addSetting(puddleCoverage);
        addSetting(reflectionStrength);
        addSetting(wetDistance);
        addSetting(wetRipples);
        addSetting(rippleStrength);

        addSetting(saturationEnabled);
        addSetting(saturationLevel);

        INSTANCE = this;
    }

    public static AmbienceModule getInstance() {
        return INSTANCE;
    }

    public static boolean shouldOverrideTime() {
        return INSTANCE != null && INSTANCE.isEnabled() && INSTANCE.customTime.isEnabled();
    }

    public long getCustomTimeTicks() {
        return switch (timeMode.getValue()) {
            case "Day" -> 6000L;
            case "Sunset" -> 12500L;
            case "Midnight" -> 18000L;
            case "Night" -> 15500L;
            default -> Math.round(timeTicks.getValue());
        };
    }

    public boolean isMoonPhaseOverride() {
        return isEnabled() && moonPhaseOverride.isEnabled();
    }

    public int getMoonPhaseIndex() {
        return Math.max(0, Math.min(7, (int) Math.round(moonPhase.getValue())));
    }

    public String getSkyModeName() {
        return skyMode.getValue();
    }

    public boolean isShaderSkyActive() {
        if (!isEnabled()) {
            return false;
        }
        String mode = skyMode.getValue();
        return !"Vanilla".equals(mode) && !"Fog Only".equals(mode);
    }

    public boolean isFogOnlyMode() {
        return isEnabled() && skyMode.is("Fog Only");
    }

    public boolean isHideVanillaSky() {
        return isEnabled() && isShaderSkyActive() && hideVanillaSky.isEnabled();
    }

    public boolean isHideSunMoon() {
        return isEnabled() && hideSunMoon.isEnabled();
    }

    public float getSunScale() {
        return isEnabled() ? sunScale.getFloatValue() : 1.0f;
    }

    public float getMoonScale() {
        return isEnabled() ? moonScale.getFloatValue() : 1.0f;
    }

    public int resolvePrimaryColor() {
        if (colorMode.is("Custom")) {
            return customColor.getColor();
        }
        Theme theme = ThemeManager.getInstance().getCurrentTheme();
        return theme != null ? theme.getPrimaryColor() : 0xFF7C4DFF;
    }

    public int resolveSecondaryColor() {
        if (colorMode.is("Custom")) {
            return customColor2.getColor();
        }
        Theme theme = ThemeManager.getInstance().getCurrentTheme();
        return theme != null ? theme.getSecondaryColor() : 0xFF00E5FF;
    }

    public float getSkySpeed() {
        return skySpeed.getFloatValue();
    }

    public float getSkyScale() {
        return skyScale.getFloatValue();
    }

    public float getSkyIntensity() {
        return skyIntensity.getFloatValue();
    }

    public float getSkyOpacity() {
        return skyOpacity.getFloatValue();
    }

    public float getNightBlend() {
        return nightBlend.getFloatValue();
    }

    public float getThemeMix() {
        return themeMix.getFloatValue();
    }

    public float getThunderInterval() {
        return thunderInterval.getFloatValue();
    }

    public float getThunderChance() {
        return thunderChance.getFloatValue();
    }

    public float getThunderGlow() {
        return thunderGlow.getFloatValue();
    }

    public boolean isCustomFogActive() {
        return isEnabled() && (customFog.isEnabled() || isFogOnlyMode());
    }

    public int resolveFogColor() {
        if (customFog.isEnabled()) {
            return fogColor.getColor();
        }
        return resolvePrimaryColor();
    }

    public float getCustomFogStart() {
        return fogStart.getFloatValue();
    }

    public float getCustomFogEnd() {
        return Math.max(getCustomFogStart() + 1.0f, fogEnd.getFloatValue());
    }

    public void recordFogDistances(float start, float end, Vector4f color) {
        this.lastFogStart = start;
        this.lastFogEnd = end;
        if (color != null) {
            this.lastFogColor.set(color);
        }
    }

    public float getLastFogStart() {
        return lastFogStart;
    }

    public float getLastFogEnd() {
        return lastFogEnd;
    }

    public Vector4f getLastFogColor() {
        return lastFogColor;
    }

    public boolean isFogBlurActive() {
        return isEnabled() && (fogBlur.isEnabled() || isFogOnlyMode());
    }

    public float getFogBlurStrength() {
        return fogBlurStrength.getFloatValue();
    }

    public float getEffectiveFogBlurStart() {
        return isFogOnlyMode() ? Math.min(fogBlurStart.getFloatValue(), lastFogStart) : fogBlurStart.getFloatValue();
    }

    public float getEffectiveFogBlurEnd() {
        return isFogOnlyMode() ? Math.max(getEffectiveFogBlurStart() + 4.0f, lastFogEnd) : fogBlurEnd.getFloatValue();
    }

    public boolean isWetWorldActive() {
        return isEnabled() && wetWorld.isEnabled();
    }

    public boolean isModernWetMode() {
        return wetMode.is("Modern");
    }

    public int getWetQualityIndex() {
        return switch (wetQuality.getValue()) {
            case "Low" -> 0;
            case "Medium" -> 1;
            case "Ultra" -> 3;
            default -> 2;
        };
    }

    public float getWetIntensity() {
        return wetIntensity.getFloatValue();
    }

    public float getPuddleCoverage() {
        return puddleCoverage.getFloatValue();
    }

    public float getWetReflectionStrength() {
        return reflectionStrength.getFloatValue();
    }

    public float getWetDistance() {
        return wetDistance.getFloatValue();
    }

    public boolean isWetRipplesEnabled() {
        return wetRipples.isEnabled();
    }

    public float getWetRippleStrength() {
        return rippleStrength.getFloatValue();
    }

    public boolean isSaturationActive() {
        return isEnabled() && saturationEnabled.isEnabled();
    }

    public float getSaturationLevel() {
        return saturationLevel.getFloatValue();
    }

    @Override
    public void onDisable() {
        SkyPipelines.getInstance().releaseGpuResources();
    }
}
