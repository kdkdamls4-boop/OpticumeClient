package dev.opticumeclient.client.modules.visuals;

import dev.opticumeclient.client.friend.FriendManager;
import dev.opticumeclient.client.modules.Category;
import dev.opticumeclient.client.modules.Module;
import dev.opticumeclient.client.modules.ModuleManager;
import dev.opticumeclient.client.modules.combat.LegitAuraModule;
import dev.opticumeclient.client.modules.combat.OpticumeAuraModule;
import dev.opticumeclient.client.modules.visuals.targetesp.TargetEspFrame;
import dev.opticumeclient.client.modules.visuals.targetesp.TargetEspRenderer;
import dev.opticumeclient.client.render.Theme;
import dev.opticumeclient.client.render.ThemeManager;
import dev.opticumeclient.client.setting.BooleanSetting;
import dev.opticumeclient.client.setting.ColorSetting;
import dev.opticumeclient.client.setting.ModeSetting;
import dev.opticumeclient.client.setting.NumberSetting;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

public class TargetEspModule extends Module {
    private static TargetEspModule INSTANCE;

    private final ModeSetting mode = new ModeSetting(
            "Отображение цели",
            "Стиль 3D подсветки текущей цели",
            "Кольцо",
            "Карты", "Квадрат", "Кольцо", "Кристалики", "Молнии", "Пентограмма", "Призраки"
    );

    private final ModeSetting ringType = new ModeSetting(
            "Тип кольца",
            "Вариант отображения кольца вокруг цели",
            "Тип 1",
            "Тип 1", "Тип 2", "Тип 3"
    ).visibleWhen(() -> mode.is("Кольцо"));

    private final ModeSetting squareType = new ModeSetting(
            "Тип квадрата",
            "Вариант отображения 3D маркера-квадрата",
            "Тип 1",
            "Тип 1", "Тип 2"
    ).visibleWhen(() -> mode.is("Квадрат"));

    private final ModeSetting ghostType = new ModeSetting(
            "Тип призраков",
            "Вариант отображения призраков / душ вокруг цели",
            "Тип 1",
            "Тип 1", "Тип 2", "Тип 3", "Тип 4", "Тип 5", "Тип 6", "Тип 7", "Тип 8"
    ).visibleWhen(() -> mode.is("Призраки"));

    private final ModeSetting crystalType = new ModeSetting(
            "Тип кристалликов",
            "Вариант отображения орбитальных кристаллов",
            "Тип 1",
            "Тип 1", "Тип 2", "Тип 3", "Тип 4", "Тип 5"
    ).visibleWhen(() -> mode.is("Кристалики"));

    private final ModeSetting pentagramType = new ModeSetting(
            "Тип пентограммы",
            "Вариант отображения магической пентаграммы",
            "Тип 1",
            "Тип 1", "Тип 2"
    ).visibleWhen(() -> mode.is("Пентограмма"));

    private final ModeSetting lightningType = new ModeSetting(
            "Тип молний",
            "Вариант отображения электрических разрядов",
            "Тип 1",
            "Тип 1", "Тип 2", "Тип 3"
    ).visibleWhen(() -> mode.is("Молнии"));

    private final NumberSetting cardCount = new NumberSetting(
            "Кол-во карт",
            "Количество вращающихся карт вокруг цели",
            4.0, 2.0, 8.0, 1.0
    ).visibleWhen(() -> mode.is("Карты"));

    private final NumberSetting cardSpeed = new NumberSetting(
            "Скорость карт",
            "Скорость вращения карт вокруг цели",
            1.0, 0.1, 3.0, 0.1
    ).visibleWhen(() -> mode.is("Карты"));

    private final BooleanSetting cardsThroughWalls = new BooleanSetting(
            "Просвечивать карты",
            "Отрисовывать карты сквозь блоки",
            false
    ).visibleWhen(() -> mode.is("Карты"));

    private final BooleanSetting jellyThroughWalls = new BooleanSetting(
            "Сквозь стены",
            "Отрисовывать желе-кольцо сквозь стены",
            true
    ).visibleWhen(this::isJelly);

    private final NumberSetting jellyParticleSize = new NumberSetting(
            "Размер частиц желе",
            "Размер светящихся частиц желе-кольца",
            0.2, 0.08, 0.45, 0.01
    ).visibleWhen(this::isJelly);

    private final NumberSetting jellyLayers = new NumberSetting(
            "Слои желе",
            "Количество слоёв желе-кольца",
            14.0, 6.0, 22.0, 1.0
    ).visibleWhen(this::isJelly);

    private final BooleanSetting crystalsThroughWalls = new BooleanSetting(
            "Просвечивать кристаллы",
            "Отрисовывать кристаллы сквозь стены",
            true
    ).visibleWhen(() -> mode.is("Кристалики"));

    private final BooleanSetting lightningThroughWalls = new BooleanSetting(
            "Просвечивать",
            "Отрисовывать молнии сквозь стены",
            false
    ).visibleWhen(() -> mode.is("Молнии"));

    private final BooleanSetting lighting = new BooleanSetting(
            "Освещение",
            "Динамическое освещение поверхностей блоков вокруг цели",
            true
    ).visibleWhen(() -> mode.is("Призраки") || mode.is("Кристалики") || mode.is("Молнии"));

    private final NumberSetting ringSpeed = new NumberSetting(
            "Скорость кольца",
            "Скорость движения кольца вверх-вниз",
            1.2, 0.1, 5.0, 0.1
    ).visibleWhen(() -> mode.is("Кольцо") && !isJelly());

    private final NumberSetting souls = new NumberSetting(
            "Кол-во душ",
            "Количество спиралей душ (Призраки Тип 6)",
            3.0, 2.0, 4.0, 1.0
    ).visibleWhen(() -> mode.is("Призраки") && ghostType.is("Тип 6"));

    private final BooleanSetting redOnHit = new BooleanSetting(
            "Красный при ударе",
            "Красная вспышка при получении урона целью",
            true
    );

    private final BooleanSetting themeColor = new BooleanSetting(
            "Авто цвет под тему",
            "Использовать цвета активной темы клиента",
            true
    );

    private final ColorSetting color = new ColorSetting(
            "Цвет отображения",
            "Основной цвет TargetESP",
            -65400
    ).visibleWhen(() -> !themeColor.isEnabled());

    private final ColorSetting gradientColor = new ColorSetting(
            "Цвет градиента кристаллов",
            "Дополнительный градиентный цвет",
            -11486721
    ).visibleWhen(() -> !themeColor.isEnabled() && (
            (mode.is("Кристалики") && crystalType.is("Тип 5"))
            || mode.is("Карты")
            || (mode.is("Кольцо") && ringType.is("Тип 2"))
            || (mode.is("Призраки") && (ghostType.is("Тип 2") || ghostType.is("Тип 3") || ghostType.is("Тип 4") || ghostType.is("Тип 5")))
    ));

    private final BooleanSetting ghostGlow = new BooleanSetting(
            "Свечение",
            "Дополнительный bloom-ореол призраков (Тип 2)",
            false
    ).visibleWhen(() -> mode.is("Призраки") && ghostType.is("Тип 2"));

    private final BooleanSetting ghostThroughWalls = new BooleanSetting(
            "Просвечивать призраков",
            "Отрисовывать призраков сквозь стены",
            true
    ).visibleWhen(() -> mode.is("Призраки") && (ghostType.is("Тип 2") || ghostType.is("Тип 4") || ghostType.is("Тип 5")));

    private final BooleanSetting distortion = new BooleanSetting(
            "Искажение",
            "Экранное преломление (refraction distortion) вокруг эффекта",
            true
    ).visibleWhen(this::isDistortionAvailable);

    private final NumberSetting distortionStrength = new NumberSetting(
            "Сила искажения",
            "Интенсивность экранного преломления",
            12.0, 3.0, 30.0, 1.0
    ).visibleWhen(() -> isDistortionAvailable() && distortion.isEnabled());

    private LivingEntity cachedTarget = null;
    private long lastTargetTime = 0L;

    public TargetEspModule() {
        super("TargetESP", "Подсветка текущей цели в стиле Desire (7 режимов, шейдеры, bloom и искажение)", Category.VISUALS);
        INSTANCE = this;

        addSetting(mode);
        addSetting(ringType);
        addSetting(squareType);
        addSetting(ghostType);
        addSetting(crystalType);
        addSetting(pentagramType);
        addSetting(lightningType);
        addSetting(cardCount);
        addSetting(cardSpeed);
        addSetting(cardsThroughWalls);
        addSetting(jellyThroughWalls);
        addSetting(jellyParticleSize);
        addSetting(jellyLayers);
        addSetting(crystalsThroughWalls);
        addSetting(lightningThroughWalls);
        addSetting(lighting);
        addSetting(ringSpeed);
        addSetting(souls);
        addSetting(redOnHit);
        addSetting(themeColor);
        addSetting(color);
        addSetting(gradientColor);
        addSetting(ghostGlow);
        addSetting(ghostThroughWalls);
        addSetting(distortion);
        addSetting(distortionStrength);

        setEnabled(true);
    }

    public static TargetEspModule getInstance() {
        if (INSTANCE == null && ModuleManager.getInstance() != null) {
            INSTANCE = ModuleManager.getInstance().getModule(TargetEspModule.class);
        }
        return INSTANCE;
    }

    private boolean isJelly() {
        return mode.is("Кольцо") && ringType.is("Тип 3");
    }

    private boolean isDistortionAvailable() {
        boolean ghostNoDistortion = mode.is("Призраки")
                && (ghostType.is("Тип 2") || ghostType.is("Тип 4") || ghostType.is("Тип 5"));
        return !isJelly() && !ghostNoDistortion;
    }

    @Override
    public String getDisplaySuffix() {
        return mode.getMode();
    }

    @Override
    public void onDisable() {
        cachedTarget = null;
        TargetEspRenderer.releaseGpuResources();
    }

    public void renderWorld(TargetEspFrame frame) {
        if (mc.level == null || mc.player == null) return;

        TargetEspRenderer.setDistortionSettings(distortion.isEnabled(), distortionStrength.getFloatValue());
        TargetEspRenderer.setGhostSettings(ghostGlow.isEnabled(), ghostThroughWalls.isEnabled());
        TargetEspRenderer.setJellySettings(jellyThroughWalls.isEnabled(), jellyParticleSize.getFloatValue(), jellyLayers.getIntValue());
        TargetEspRenderer.setExtraSettings(
                pentagramType.getMode(),
                cardCount.getIntValue(),
                cardSpeed.getFloatValue(),
                cardsThroughWalls.isEnabled()
        );
        TargetEspRenderer.setSettings(
                ringSpeed.getFloatValue(),
                ringType.getMode(),
                souls.getIntValue(),
                redOnHit.isEnabled(),
                ghostType.getMode(),
                crystalType.getMode(),
                crystalsThroughWalls.isEnabled(),
                lightningType.getMode(),
                lightningThroughWalls.isEnabled(),
                lighting.isEnabled(),
                squareType.getMode()
        );

        Theme currentTheme = ThemeManager.getInstance().getCurrentTheme();
        int first = themeColor.isEnabled() ? currentTheme.getPrimaryColor() : color.getColor();
        int second = themeColor.isEnabled() ? currentTheme.getSecondaryColor() : gradientColor.getColor();

        LivingEntity target = getActiveTarget();
        if (target != null && target.isAlive() && !target.isRemoved()) {
            TargetEspRenderer.render(frame, target, mode.getMode(), first, second);
        } else {
            TargetEspRenderer.renderFadeOut(frame);
        }
    }

    private LivingEntity getActiveTarget() {
        long now = System.currentTimeMillis();
        FriendManager friends = FriendManager.getInstance();

        // 1. Check OpticumeAura
        OpticumeAuraModule opticumeAura = ModuleManager.getInstance().getModule(OpticumeAuraModule.class);
        if (opticumeAura != null && opticumeAura.isEnabled()) {
            LivingEntity t = opticumeAura.getTarget();
            if (t != null && t.isAlive() && !t.isRemoved() && !friends.isFriend(t)) {
                cachedTarget = t;
                lastTargetTime = now;
                return cachedTarget;
            }
        }

        // 2. Check LegitAura
        LegitAuraModule legitAura = ModuleManager.getInstance().getModule(LegitAuraModule.class);
        if (legitAura != null && legitAura.isEnabled()) {
            LivingEntity t = legitAura.getTarget();
            if (t != null && t.isAlive() && !t.isRemoved() && !friends.isFriend(t)) {
                cachedTarget = t;
                lastTargetTime = now;
                return cachedTarget;
            }
        }

        // 3. Check player's last attacked entity
        LivingEntity lastHurt = mc.player.getLastHurtMob();
        if (lastHurt != null && lastHurt.isAlive() && !lastHurt.isRemoved() && lastHurt != mc.player && !friends.isFriend(lastHurt)) {
            if (mc.player.distanceTo(lastHurt) <= 8.0f) {
                cachedTarget = lastHurt;
                lastTargetTime = now;
                return cachedTarget;
            }
        }

        // 4. Check crosshairPickEntity / hitResult
        if (mc.crosshairPickEntity instanceof LivingEntity living && living != mc.player && living.isAlive() && !living.isRemoved() && !friends.isFriend(living)) {
            cachedTarget = living;
            lastTargetTime = now;
            return cachedTarget;
        }

        HitResult hit = mc.hitResult;
        if (hit instanceof EntityHitResult entityHit && entityHit.getEntity() instanceof LivingEntity living
                && living != mc.player && living.isAlive() && !living.isRemoved() && !friends.isFriend(living)) {
            cachedTarget = living;
            lastTargetTime = now;
            return cachedTarget;
        }

        // 5. Check nearby hurt living entity if LegitAura is enabled
        if (legitAura != null && legitAura.isEnabled()) {
            for (Entity e : mc.level.entitiesForRendering()) {
                if (e instanceof LivingEntity living && e != mc.player && living.isAlive() && !living.isRemoved() && !friends.isFriend(living)) {
                    if (living.hurtTime > 0 && mc.player.distanceTo(living) <= 4.5f) {
                        cachedTarget = living;
                        lastTargetTime = now;
                        return cachedTarget;
                    }
                }
            }
        }

        // 6. Keep last active LivingEntity for 3.5 seconds so fade-out / tracking stays smooth
        if (cachedTarget != null && cachedTarget.isAlive() && !cachedTarget.isRemoved() && !friends.isFriend(cachedTarget)
                && (now - lastTargetTime) <= 3500L && mc.player.distanceTo(cachedTarget) <= 12.0f) {
            return cachedTarget;
        }

        cachedTarget = null;
        return null;
    }
}
