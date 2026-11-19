package dev.opticumeclient.client.modules.visuals;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import dev.opticumeclient.client.modules.Category;
import dev.opticumeclient.client.modules.Module;
import dev.opticumeclient.client.modules.visuals.hands.HandsEffectRenderer;
import dev.opticumeclient.client.render.Theme;
import dev.opticumeclient.client.render.ThemeManager;
import dev.opticumeclient.client.setting.BooleanSetting;
import dev.opticumeclient.client.setting.ColorSetting;
import dev.opticumeclient.client.setting.ModeSetting;
import dev.opticumeclient.client.setting.NumberSetting;
import org.joml.Vector4f;

/**
 * Ported from Desire HandsModule:
 * First-person hand shader/fill/mirror/glow/outline and trail post-processing pipeline.
 */
public class HandsModule extends Module {
    private static HandsModule instance;

    private final ModeSetting mode = new ModeSetting(
            "Режим",
            "Эффект наложения на руки от первого лица",
            "Nothing",
            "Nothing", "Заливка", "Шейдер", "Зеркало", "Шейдер + Зеркало"
    );
    private final BooleanSetting autoThemeColor = new BooleanSetting("Авто цвет под тему", "Брать цвета из текущей темы", true);
    private final BooleanSetting autoItemColor = new BooleanSetting("Авто цвет под предмет", "Окрашивать свечение в цвет предмета", false);

    private final NumberSetting mirrorMix = new NumberSetting("Смешивание", "Сила смешивания зеркала", 0.5, 0.0, 1.0, 0.01)
            .visibleWhen(this::isMirror);
    private final NumberSetting mirrorDistortion = new NumberSetting("Искажение зеркала", "Сила преломления зеркала", 0.0, 0.0, 1.0, 0.05)
            .visibleWhen(this::isMirror);
    private final NumberSetting mirrorBlur = new NumberSetting("Сила размытия", "Размытие отражения зеркала", 1.0, 0.1, 3.0, 0.05)
            .visibleWhen(this::isMirror);

    private final BooleanSetting rainbowFill = new BooleanSetting("Радужная", "Переливание заливки", false)
            .visibleWhen(this::isFill);
    private final NumberSetting rainbowSpeed = new NumberSetting("Скорость радуги", "Скорость переливания", 0.4, 0.0, 2.0, 0.05)
            .visibleWhen(() -> isFill() && rainbowFill.isEnabled());
    private final NumberSetting rainbowScale = new NumberSetting("Масштаб радуги", "Частота радуги по высоте", 1.0, 0.2, 3.0, 0.1)
            .visibleWhen(() -> isFill() && rainbowFill.isEnabled());
    private final ColorSetting fillColor = new ColorSetting("Цвет заливки", "Свой цвет заливки рук", 0xFFFF4444, false)
            .visibleWhen(() -> isFill() && !rainbowFill.isEnabled() && !autoThemeColor.isEnabled());
    private final NumberSetting fillOpacity = new NumberSetting("Прозрачность заливки", "Сила смешивания заливки", 0.8, 0.0, 1.0, 0.05)
            .visibleWhen(this::isFill);
    private final BooleanSetting keepFillShadows = new BooleanSetting("Сохранить тени", "Сохранять затенение модели", true)
            .visibleWhen(this::isFill);
    private final NumberSetting fillShadowStrength = new NumberSetting("Сила теней", "Сила сохранения теней", 0.3, 0.0, 1.0, 0.05)
            .visibleWhen(() -> isFill() && keepFillShadows.isEnabled());

    private final ModeSetting shaderStyle = new ModeSetting(
            "Стиль шейдера",
            "Выбор алгоритмического шейдера рук",
            "Шейдер",
            "Шейдер", "HandShader", "Туманность", "Космос", "Градиент", "Homie"
    ).visibleWhen(this::isShader);
    private final ColorSetting shaderColor1 = new ColorSetting("Цвет шейдера 1", "Основной цвет шейдера", 0xFFC892FF, false)
            .visibleWhen(() -> isShader() && !autoThemeColor.isEnabled());
    private final BooleanSetting secondColor = new BooleanSetting("Второй цвет", "Использовать двухцветный шейдер", false)
            .visibleWhen(this::isShader);
    private final ColorSetting shaderColor2 = new ColorSetting("Цвет шейдера 2", "Дополнительный цвет шейдера", 0xFF5000C8, false)
            .visibleWhen(() -> isShader() && secondColor.isEnabled() && !autoThemeColor.isEnabled());
    private final NumberSetting shaderSpeed = new NumberSetting("Скорость шейдера", "Скорость анимации шейдера", 1.5, 0.05, 2.5, 0.05)
            .visibleWhen(this::isShader);
    private final NumberSetting shaderScale = new NumberSetting("Масштаб шейдера", "Масштаб паттерна шейдера", 1.5, 0.2, 5.0, 0.1)
            .visibleWhen(this::isShader);
    private final NumberSetting shaderGlow = new NumberSetting("Свечение шейдера", "Интенсивность деталей шейдера", 1.2, 0.0, 3.0, 0.05)
            .visibleWhen(this::isShader);
    private final NumberSetting shaderQuality = new NumberSetting("Качество шейдера", "Количество октав шума", 2.0, 1.0, 3.0, 1.0)
            .visibleWhen(() -> isShader() && !shaderStyle.is("Градиент"));
    private final BooleanSetting stars = new BooleanSetting("Звёзды", "Добавлять звёзды в шейдере", true)
            .visibleWhen(this::isShader);
    private final NumberSetting shaderOpacity = new NumberSetting("Прозрачность шейдера", "Сила наложения шейдера", 1.0, 0.0, 1.0, 0.05)
            .visibleWhen(this::isShader);
    private final BooleanSetting keepShaderShadows = new BooleanSetting("Сохранить тени шейдера", "Учитывать освещение ванили", false)
            .visibleWhen(this::isShader);
    private final NumberSetting shaderShadowStrength = new NumberSetting("Сила теней шейдера", "Степень влияния теней", 0.5, 0.0, 1.0, 0.05)
            .visibleWhen(() -> isShader() && keepShaderShadows.isEnabled());

    private final BooleanSetting glow = new BooleanSetting("Глов", "Внешнее и внутреннее свечение рук", true);
    private final NumberSetting glowBlur = new NumberSetting("Размытие", "Радиус bloom-свечения", 3.0, 1.0, 5.0, 1.0)
            .visibleWhen(glow::isEnabled);
    private final NumberSetting glowBrightness = new NumberSetting("Яркость", "Яркость bloom-свечения", 1.0, 0.5, 5.0, 0.1)
            .visibleWhen(glow::isEnabled);
    private final ColorSetting glowColor1 = new ColorSetting("Цвет глова 1", "Основной цвет свечения", 0xFFFFD23F, false)
            .visibleWhen(() -> glow.isEnabled() && !autoThemeColor.isEnabled() && !autoItemColor.isEnabled());
    private final ColorSetting glowColor2 = new ColorSetting("Цвет глова 2", "Внешний цвет градиента свечения", 0xFFFF2A00, false)
            .visibleWhen(() -> glow.isEnabled() && !autoThemeColor.isEnabled() && !autoItemColor.isEnabled());
    private final NumberSetting innerGlow = new NumberSetting("Внутреннее свечение", "Подсветка краёв внутри силуэта", 0.65, 0.0, 1.5, 0.05)
            .visibleWhen(glow::isEnabled);

    private final BooleanSetting outline = new BooleanSetting("Обводка", "Чёткий контур вокруг рук и предметов", true);
    private final ColorSetting outlineColor = new ColorSetting("Цвет обводки", "Цвет контура", 0xFFFFFFFF, false)
            .visibleWhen(() -> outline.isEnabled() && !autoThemeColor.isEnabled() && !autoItemColor.isEnabled());
    private final NumberSetting outlineWidth = new NumberSetting("Толщина обводки", "Толщина контура в пикселях", 1.0, 0.5, 2.0, 0.5)
            .visibleWhen(outline::isEnabled);

    private final BooleanSetting trail = new BooleanSetting("Шлейф", "Динамический шлейф за руками", true)
            .visibleWhen(glow::isEnabled);
    private final ModeSetting trailMode = new ModeSetting(
            "Режим шлейфа",
            "Стиль шлейфа за руками",
            "Обычный",
            "Обычный", "Энергия", "Ленты"
    ).visibleWhen(this::isTrailActive);

    private final NumberSetting energyLength = new NumberSetting("Длина энергии", "Длительность шлейфа энергии", 1.0, 0.4, 2.0, 0.05)
            .visibleWhen(this::isEnergyTrail);
    private final NumberSetting energyBrightness = new NumberSetting("Яркость энергии", "Яркость нитей энергии", 1.35, 0.5, 2.5, 0.05)
            .visibleWhen(this::isEnergyTrail);
    private final NumberSetting energySpeed = new NumberSetting("Движение энергии", "Скорость смещения энергии", 1.3, 0.0, 2.0, 0.05)
            .visibleWhen(this::isEnergyTrail);
    private final NumberSetting energyPulse = new NumberSetting("Пульсация энергии", "Сила пульсации энергии", 1.0, 0.0, 2.0, 0.05)
            .visibleWhen(this::isEnergyTrail);
    private final NumberSetting energyCameraLag = new NumberSetting("Шлейф за камерой", "Смещение энергии при повороте камеры", 0.05, 0.0, 2.0, 0.05)
            .visibleWhen(this::isEnergyTrail);

    private final NumberSetting ribbonLength = new NumberSetting("Длина лент", "Длительность лент", 0.3, 0.15, 3.0, 0.05)
            .visibleWhen(this::isRibbonTrail);
    private final NumberSetting ribbonWidth = new NumberSetting("Ширина лент", "Толщина лент", 0.55, 0.25, 1.5, 0.05)
            .visibleWhen(this::isRibbonTrail);
    private final NumberSetting ribbonBrightness = new NumberSetting("Яркость лент", "Яркость лент", 1.0, 0.0, 2.5, 0.05)
            .visibleWhen(this::isRibbonTrail);
    private final NumberSetting ribbonCameraLag = new NumberSetting("Сила лент за камерой", "Инерция лент при движении камеры", 0.1, 0.0, 2.0, 0.05)
            .visibleWhen(this::isRibbonTrail);
    private final NumberSetting ribbonLift = new NumberSetting("Подъём лент", "Вертикальный дрейф лент", 0.32, 0.0, 1.0, 0.01)
            .visibleWhen(this::isRibbonTrail);
    private final NumberSetting ribbonBend = new NumberSetting("Изгиб лент", "Амплитуда волны лент", 2.0, 0.0, 2.0, 0.05)
            .visibleWhen(this::isRibbonTrail);
    private final NumberSetting ribbonWaveSpeed = new NumberSetting("Скорость волн", "Частота анимации лент", 1.5, 0.0, 3.0, 0.05)
            .visibleWhen(this::isRibbonTrail);
    private final BooleanSetting ribbonOnHit = new BooleanSetting("Ленты при ударе", "Изменять ленты во время удара", true)
            .visibleWhen(this::isRibbonTrail);
    private final NumberSetting ribbonHitLength = new NumberSetting("Длина при ударе", "Длина лент при ударе", 0.15, 0.15, 3.0, 0.05)
            .visibleWhen(() -> isRibbonTrail() && ribbonOnHit.isEnabled());
    private final NumberSetting ribbonHitStrength = new NumberSetting("Сила при ударе", "Яркость лент при ударе", 1.15, 0.0, 2.5, 0.05)
            .visibleWhen(() -> isRibbonTrail() && ribbonOnHit.isEnabled());
    private final BooleanSetting ribbonOnEat = new BooleanSetting("Ленты при еде", "Изменять ленты при использовании еды", true)
            .visibleWhen(this::isRibbonTrail);
    private final NumberSetting ribbonEatLength = new NumberSetting("Длина при еде", "Длина лент при еде", 0.65, 0.15, 3.0, 0.05)
            .visibleWhen(() -> isRibbonTrail() && ribbonOnEat.isEnabled());
    private final NumberSetting ribbonEatStrength = new NumberSetting("Сила при еде", "Яркость лент при еде", 0.7, 0.0, 2.5, 0.05)
            .visibleWhen(() -> isRibbonTrail() && ribbonOnEat.isEnabled());

    private final NumberSetting trailFadeSpeed = new NumberSetting("Скорость затухания", "Скорость исчезновения шлейфа", 0.04, 0.002, 0.2, 0.002)
            .visibleWhen(this::isNormalTrail);
    private final NumberSetting trailLift = new NumberSetting("Подъём", "Скорость подъёма пламени", 0.2, 0.0, 1.5, 0.05)
            .visibleWhen(this::isNormalTrail);
    private final NumberSetting trailSway = new NumberSetting("Качание", "Амплитуда бокового качания", 0.0, 0.0, 0.2, 0.005)
            .visibleWhen(this::isNormalTrail);
    private final NumberSetting trailCameraLag = new NumberSetting("Шлейф от камеры", "Инерция при повороте камеры", 0.0, 0.0, 2.0, 0.05)
            .visibleWhen(this::isNormalTrail);
    private final NumberSetting trailTurbulence = new NumberSetting("Турбулентность", "Волнистость шлейфа", 0.02, 0.0, 0.6, 0.01)
            .visibleWhen(this::isNormalTrail);
    private final NumberSetting trailFlicker = new NumberSetting("Мерцание", "Сила мерцания шлейфа", 0.2, 0.0, 0.2, 0.01)
            .visibleWhen(this::isNormalTrail);
    private final BooleanSetting trailHitBlow = new BooleanSetting("Сдув при ударе", "Ускорять рассеивание при ударе", false)
            .visibleWhen(this::isNormalTrail);
    private final NumberSetting trailHitBlowStrength = new NumberSetting("Сила сдува", "Интенсивность сдува", 4.0, 1.0, 10.0, 0.5)
            .visibleWhen(() -> isNormalTrail() && trailHitBlow.isEnabled());

    private TextureTarget captureTarget;
    private boolean capturing;
    private long enabledAtMs = System.currentTimeMillis();

    public HandsModule() {
        super("Hands", "Настройка рендера рук, шейдеров, зеркала, глова и шлейфа (Desire)", Category.VISUALS);
        instance = this;
        addSetting(mode);
        addSetting(autoThemeColor);
        addSetting(autoItemColor);
        addSetting(mirrorMix);
        addSetting(mirrorDistortion);
        addSetting(mirrorBlur);
        addSetting(rainbowFill);
        addSetting(rainbowSpeed);
        addSetting(rainbowScale);
        addSetting(fillColor);
        addSetting(fillOpacity);
        addSetting(keepFillShadows);
        addSetting(fillShadowStrength);
        addSetting(shaderStyle);
        addSetting(shaderColor1);
        addSetting(secondColor);
        addSetting(shaderColor2);
        addSetting(shaderSpeed);
        addSetting(shaderScale);
        addSetting(shaderGlow);
        addSetting(shaderQuality);
        addSetting(stars);
        addSetting(shaderOpacity);
        addSetting(keepShaderShadows);
        addSetting(shaderShadowStrength);
        addSetting(glow);
        addSetting(glowBlur);
        addSetting(glowBrightness);
        addSetting(glowColor1);
        addSetting(glowColor2);
        addSetting(innerGlow);
        addSetting(outline);
        addSetting(outlineColor);
        addSetting(outlineWidth);
        addSetting(trail);
        addSetting(trailMode);
        addSetting(energyLength);
        addSetting(energyBrightness);
        addSetting(energySpeed);
        addSetting(energyPulse);
        addSetting(energyCameraLag);
        addSetting(ribbonLength);
        addSetting(ribbonWidth);
        addSetting(ribbonBrightness);
        addSetting(ribbonCameraLag);
        addSetting(ribbonLift);
        addSetting(ribbonBend);
        addSetting(ribbonWaveSpeed);
        addSetting(ribbonOnHit);
        addSetting(ribbonHitLength);
        addSetting(ribbonHitStrength);
        addSetting(ribbonOnEat);
        addSetting(ribbonEatLength);
        addSetting(ribbonEatStrength);
        addSetting(trailFadeSpeed);
        addSetting(trailLift);
        addSetting(trailSway);
        addSetting(trailCameraLag);
        addSetting(trailTurbulence);
        addSetting(trailFlicker);
        addSetting(trailHitBlow);
        addSetting(trailHitBlowStrength);
    }

    public static HandsModule getInstance() {
        return instance;
    }

    public static void onBeginCapture() {
        if (instance != null) {
            instance.beginCapture();
        }
    }

    public static void onEndCapture(RenderTarget mainTarget) {
        if (instance != null) {
            instance.endCaptureAndComposite(mainTarget);
        }
    }

    public static boolean isCapturingActive() {
        return instance != null && instance.capturing && instance.captureTarget != null;
    }

    public static TextureTarget getActiveCaptureTarget() {
        return instance != null ? instance.captureTarget : null;
    }

    public void beginCapture() {
        if (!shouldRenderEffects() || this.capturing || mc.player == null || mc.level == null) {
            return;
        }
        RenderTarget main = mc.gameRenderer != null ? mc.gameRenderer.mainRenderTarget() : null;
        if (main == null) {
            return;
        }
        int width = Math.max(1, main.width);
        int height = Math.max(1, main.height);
        if (this.captureTarget == null) {
            this.captureTarget = new TextureTarget("hands-capture", width, height, GpuFormat.RGBA8_UNORM, GpuFormat.D32_FLOAT);
        } else if (this.captureTarget.width != width || this.captureTarget.height != height) {
            this.captureTarget.resize(width, height);
        }
        RenderSystem.getDevice()
                .createCommandEncoder()
                .clearColorAndDepthTextures(
                        this.captureTarget.getColorTexture(),
                        new Vector4f(0.0f),
                        this.captureTarget.getDepthTexture(),
                        0.0
                );
        this.capturing = true;
    }

    public void endCaptureAndComposite(RenderTarget mainTarget) {
        if (!this.capturing) {
            return;
        }
        this.capturing = false;
        RenderTarget main = mainTarget != null ? mainTarget : (mc.gameRenderer != null ? mc.gameRenderer.mainRenderTarget() : null);
        if (isEnabled() && this.captureTarget != null && main != null) {
            HandsEffectRenderer.getInstance().render(this.captureTarget, main, this);
        }
    }

    @Override
    public void onEnable() {
        super.onEnable();
        this.enabledAtMs = System.currentTimeMillis();
    }

    @Override
    public void onDisable() {
        super.onDisable();
        this.capturing = false;
        if (this.captureTarget != null) {
            this.captureTarget.destroyBuffers();
            this.captureTarget = null;
        }
        HandsEffectRenderer.getInstance().releaseGpuResources();
    }

    public boolean shouldRenderEffects() {
        return isEnabled() && (!isNothing() || glow.isEnabled() || outline.isEnabled());
    }

    public boolean isNothing() {
        return mode.is("Nothing");
    }

    public boolean isFill() {
        return mode.is("Заливка");
    }

    public boolean isShader() {
        return mode.is("Шейдер") || isShaderMirror();
    }

    public boolean isMirror() {
        return mode.is("Зеркало") || isShaderMirror();
    }

    public boolean isShaderMirror() {
        return mode.is("Шейдер + Зеркало");
    }

    public int getModeIndex() {
        if (isNothing()) {
            return 4;
        }
        if (isFill()) {
            return 0;
        }
        if (mode.is("Шейдер")) {
            return 1;
        }
        if (mode.is("Зеркало")) {
            return 2;
        }
        return 3;
    }

    public int getShaderStyleIndex() {
        if (shaderStyle.is("HandShader")) {
            return 4;
        }
        if (shaderStyle.is("Туманность")) {
            return 1;
        }
        if (shaderStyle.is("Космос")) {
            return 2;
        }
        if (shaderStyle.is("Градиент")) {
            return 3;
        }
        if (shaderStyle.is("Homie")) {
            return 5;
        }
        return 0;
    }

    public int getQualitySteps() {
        int q = Math.round(shaderQuality.getFloatValue());
        return q == 1 ? 3 : (q == 3 ? 8 : 5);
    }

    public float getElapsedSeconds() {
        return (System.currentTimeMillis() - this.enabledAtMs) / 1000.0f;
    }

    private int themePrimary() {
        Theme theme = ThemeManager.getInstance().getCurrentTheme();
        return theme != null ? theme.getPrimaryColor() : 0xFF2DD4BF;
    }

    private int themeSecondary() {
        Theme theme = ThemeManager.getInstance().getCurrentTheme();
        return theme != null ? theme.getSecondaryColor() : 0xFF0EA5E9;
    }

    public boolean isAutoItemColor() {
        return autoItemColor.isEnabled();
    }

    public float getMirrorMix() {
        return mirrorMix.getFloatValue();
    }

    public float getMirrorDistortion() {
        return mirrorDistortion.getFloatValue();
    }

    public float getMirrorBlur() {
        return mirrorBlur.getFloatValue();
    }

    public boolean isRainbowFill() {
        return rainbowFill.isEnabled();
    }

    public float getRainbowSpeed() {
        return rainbowSpeed.getFloatValue();
    }

    public float getRainbowScale() {
        return rainbowScale.getFloatValue();
    }

    public int getFillColor() {
        return autoThemeColor.isEnabled() ? themePrimary() : fillColor.getColor();
    }

    public float getFillOpacity() {
        return fillOpacity.getFloatValue();
    }

    public boolean isKeepFillShadows() {
        return keepFillShadows.isEnabled();
    }

    public float getFillShadowStrength() {
        return fillShadowStrength.getFloatValue();
    }

    public int getShaderColor1() {
        return autoThemeColor.isEnabled() ? themePrimary() : shaderColor1.getColor();
    }

    public int getShaderColor2() {
        if (autoThemeColor.isEnabled()) {
            return secondColor.isEnabled() ? themeSecondary() : themePrimary();
        }
        return secondColor.isEnabled() ? shaderColor2.getColor() : shaderColor1.getColor();
    }

    public int getMirrorColor() {
        return themePrimary();
    }

    public float getShaderSpeed() {
        return shaderSpeed.getFloatValue();
    }

    public float getShaderScale() {
        return shaderScale.getFloatValue();
    }

    public float getShaderGlow() {
        return shaderGlow.getFloatValue();
    }

    public boolean isStars() {
        return stars.isEnabled();
    }

    public float getShaderOpacity() {
        return shaderOpacity.getFloatValue();
    }

    public boolean isKeepShaderShadows() {
        return keepShaderShadows.isEnabled();
    }

    public float getShaderShadowStrength() {
        return shaderShadowStrength.getFloatValue();
    }

    public boolean isGlowEnabled() {
        return glow.isEnabled();
    }

    public float getGlowBlur() {
        return glowBlur.getFloatValue();
    }

    public float getGlowBrightness() {
        return glowBrightness.getFloatValue();
    }

    public int getGlowColor1() {
        return autoThemeColor.isEnabled() ? themePrimary() : glowColor1.getColor();
    }

    public int getGlowColor2() {
        return autoThemeColor.isEnabled() ? themeSecondary() : glowColor2.getColor();
    }

    public float getInnerGlow() {
        return innerGlow.getFloatValue();
    }

    public boolean isOutlineEnabled() {
        return outline.isEnabled();
    }

    public int getEffectiveOutlineColor() {
        if (autoItemColor.isEnabled()) {
            return -1;
        }
        return autoThemeColor.isEnabled() ? themePrimary() : outlineColor.getColor();
    }

    public float getOutlineWidth() {
        return outlineWidth.getFloatValue();
    }

    public boolean isTrailActive() {
        return glow.isEnabled() && trail.isEnabled();
    }

    public boolean isNormalTrail() {
        return isTrailActive() && trailMode.is("Обычный");
    }

    public boolean isEnergyTrail() {
        return isTrailActive() && trailMode.is("Энергия");
    }

    public boolean isRibbonTrail() {
        return isTrailActive() && trailMode.is("Ленты");
    }

    public int getTrailModeIndex() {
        return isRibbonTrail() ? 2 : (isEnergyTrail() ? 1 : 0);
    }

    public float getEnergyLength() {
        return energyLength.getFloatValue();
    }

    public float getEnergyBrightness() {
        return energyBrightness.getFloatValue();
    }

    public float getEnergySpeed() {
        return energySpeed.getFloatValue();
    }

    public float getEnergyPulse() {
        return energyPulse.getFloatValue();
    }

    public float getEnergyCameraLag() {
        return energyCameraLag.getFloatValue();
    }

    public float getRibbonLength() {
        return ribbonLength.getFloatValue();
    }

    public float getRibbonWidth() {
        return ribbonWidth.getFloatValue();
    }

    public float getRibbonBrightness() {
        return ribbonBrightness.getFloatValue();
    }

    public float getRibbonCameraLag() {
        return ribbonCameraLag.getFloatValue();
    }

    public float getRibbonLift() {
        return ribbonLift.getFloatValue();
    }

    public float getRibbonBend() {
        return ribbonBend.getFloatValue();
    }

    public float getRibbonWaveSpeed() {
        return ribbonWaveSpeed.getFloatValue();
    }

    public boolean isRibbonOnHit() {
        return ribbonOnHit.isEnabled();
    }

    public float getRibbonHitLength() {
        return ribbonHitLength.getFloatValue();
    }

    public float getRibbonHitStrength() {
        return ribbonHitStrength.getFloatValue();
    }

    public boolean isRibbonOnEat() {
        return ribbonOnEat.isEnabled();
    }

    public float getRibbonEatLength() {
        return ribbonEatLength.getFloatValue();
    }

    public float getRibbonEatStrength() {
        return ribbonEatStrength.getFloatValue();
    }

    public float getTrailFadeSpeed() {
        return trailFadeSpeed.getFloatValue();
    }

    public float getTrailLift() {
        return trailLift.getFloatValue();
    }

    public float getTrailSway() {
        return trailSway.getFloatValue();
    }

    public float getTrailCameraLag() {
        return trailCameraLag.getFloatValue();
    }

    public float getTrailTurbulence() {
        return trailTurbulence.getFloatValue();
    }

    public float getTrailFlicker() {
        return trailFlicker.getFloatValue();
    }

    public boolean isTrailHitBlow() {
        return trailHitBlow.isEnabled();
    }

    public float getTrailHitBlowStrength() {
        return trailHitBlowStrength.getFloatValue();
    }
}
