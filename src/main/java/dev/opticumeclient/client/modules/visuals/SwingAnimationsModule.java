package dev.opticumeclient.client.modules.visuals;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.opticumeclient.client.modules.Category;
import dev.opticumeclient.client.modules.Module;
import dev.opticumeclient.client.modules.combat.KillAuraModule;
import dev.opticumeclient.client.modules.combat.OpticumeAuraModule;
import dev.opticumeclient.client.setting.BooleanSetting;
import dev.opticumeclient.client.setting.ModeSetting;
import dev.opticumeclient.client.setting.NumberSetting;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;

/**
 * Ported from Desire SwingAnimationModule + synced with Opticume ViewModel & 1.7 BlockHit animations.
 * Keeps all 16 Desire swing modes ("Обычный", "Мод 1".."Мод 14", "Custom", "HMI")
 * alongside classic Opticume presets ("1.7 BlockHit", "Slide", "Swipe", "Spin 360", "Nursultan Smooth", "Exhibition").
 */
public class SwingAnimationsModule extends Module {
    private static SwingAnimationsModule instance;

    public final ModeSetting mode = new ModeSetting(
            "Свинги",
            "Режим анимации взмаха руки и оружия",
            "Мод 1",
            "Обычный", "Мод 1", "Мод 2", "Мод 3", "Мод 4", "Мод 5", "Мод 6", "Мод 7",
            "Мод 8", "Мод 9", "Мод 10", "Мод 11", "Мод 12", "Мод 13", "Мод 14",
            "Custom", "HMI",
            "1.7 BlockHit", "Slide", "Swipe", "Spin 360", "Nursultan Smooth", "Exhibition"
    );

    private final NumberSetting angle = new NumberSetting("Угол", "Начальный угол для Мод 3 / Мод 4", 100.0, 10.0, 180.0, 1.0)
            .visibleWhen(() -> mode.is("Мод 3") || mode.is("Мод 4"));
    private final NumberSetting itemScale = new NumberSetting("Размер предмета", "Масштаб предмета при анимации", 1.0, 0.2, 2.0, 0.05)
            .visibleWhen(() -> !mode.is("HMI"));
    private final NumberSetting tiltPower = new NumberSetting("Сила наклона", "Глубина и амплитуда взмаха", 5.0, 1.0, 10.0, 1.0)
            .visibleWhen(() -> !mode.is("HMI") && !mode.is("Custom"));
    private final BooleanSetting changeSpeed = new BooleanSetting("Скорость свинга", "Изменять длительность взмаха", false)
            .visibleWhen(() -> !mode.is("HMI"));
    private final BooleanSetting cubicEase = new BooleanSetting("Кубическое ускорение", "Плавная cubic-out кривая анимации", false)
            .visibleWhen(() -> !mode.is("HMI") && !mode.is("Custom"));
    private final NumberSetting speed = new NumberSetting("Скорость", "Значение ускорения свинга", 7.0, 0.0, 10.0, 1.0)
            .visibleWhen(() -> !mode.is("HMI") && changeSpeed.isEnabled());
    public final BooleanSetting auraOnly = new BooleanSetting("Только с аурой", "Анимировать только при активной цели ауры", false);
    private final BooleanSetting onlyOnHit = new BooleanSetting("Только при ударе", "Анимировать только во время свинга", false)
            .visibleWhen(() -> !mode.is("HMI"));
    private final BooleanSetting blockHit17 = new BooleanSetting("1.7 BlockHit", "Классический сдвиг блока щитом/мечом", true)
            .visibleWhen(() -> !mode.is("HMI"));
    private final ModeSetting useAnimation = new ModeSetting(
            "Анимация использования",
            "Снижение тряски еды/зелий",
            "Обычная",
            "Обычная", "Укороченная", "Полная отмена"
    );

    // Custom Bezier curve parameters
    private final NumberSetting startX = custom("Начало X", 0.0, -2.0, 2.0, 0.01);
    private final NumberSetting startY = custom("Начало Y", 0.08, -2.0, 2.0, 0.01);
    private final NumberSetting startZ = custom("Начало Z", -0.05, -2.0, 2.0, 0.01);
    private final NumberSetting startPitch = custom("Начало Pitch", -65.0, -180.0, 180.0, 1.0);
    private final NumberSetting startYaw = custom("Начало Yaw", 24.0, -180.0, 180.0, 1.0);
    private final NumberSetting startRoll = custom("Начало Roll", -4.0, -180.0, 180.0, 1.0);
    private final NumberSetting endX = custom("Конец X", 0.04, -2.0, 2.0, 0.01);
    private final NumberSetting endY = custom("Конец Y", -0.92, -2.0, 2.0, 0.01);
    private final NumberSetting endZ = custom("Конец Z", -0.32, -2.0, 2.0, 0.01);
    private final NumberSetting endPitch = custom("Конец Pitch", -142.0, -180.0, 180.0, 1.0);
    private final NumberSetting endYaw = custom("Конец Yaw", 34.0, -180.0, 180.0, 1.0);
    private final NumberSetting endRoll = custom("Конец Roll", -16.0, -180.0, 180.0, 1.0);
    private final NumberSetting curve1X = custom("Кривая 1 X", 0.18, 0.0, 1.0, 0.01);
    private final NumberSetting curve1Y = custom("Кривая 1 Y", 0.02, 0.0, 1.0, 0.01);
    private final NumberSetting curve2X = custom("Кривая 2 X", 0.58, 0.0, 1.0, 0.01);
    private final NumberSetting curve2Y = custom("Кривая 2 Y", 1.0, 0.0, 1.0, 0.01);
    private final BooleanSetting customBack = new BooleanSetting("Возврат", "Плавный возврат после удара", true)
            .visibleWhen(() -> mode.is("Custom"));
    private final NumberSetting customBackSpeed = new NumberSetting("Скорость возврата", "Момент начала возврата", 8.0, 1.0, 10.0, 1.0)
            .visibleWhen(() -> mode.is("Custom") && customBack.isEnabled());

    // HMI (Hold My Items) parameters
    public final BooleanSetting newShowLeftArm = new BooleanSetting("Левая рука", "Отображать левую руку в режиме HMI", false)
            .visibleWhen(() -> mode.is("HMI"));
    public final ModeSetting attackMode = new ModeSetting("Атака", "Стиль выпада руки в HMI", "Swings", "Swings", "Forward", "Normal")
            .visibleWhen(() -> mode.is("HMI"));
    public final NumberSetting rightX = hmi("Правая X");
    public final NumberSetting rightY = hmi("Правая Y");
    public final NumberSetting rightZ = hmi("Правая Z");
    public final NumberSetting leftX = hmi("Левая X");
    public final NumberSetting leftY = hmi("Левая Y");
    public final NumberSetting leftZ = hmi("Левая Z");

    public SwingAnimationsModule() {
        super("SwingAnimations", "Плавные боевые анимации взмаха руки и оружия (16 режимов Desire + HMI)", Category.VISUALS);
        instance = this;
        addSetting(mode);
        addSetting(angle);
        addSetting(itemScale);
        addSetting(tiltPower);
        addSetting(changeSpeed);
        addSetting(cubicEase);
        addSetting(speed);
        addSetting(auraOnly);
        addSetting(onlyOnHit);
        addSetting(blockHit17);
        addSetting(useAnimation);

        addSetting(startX);
        addSetting(startY);
        addSetting(startZ);
        addSetting(startPitch);
        addSetting(startYaw);
        addSetting(startRoll);
        addSetting(endX);
        addSetting(endY);
        addSetting(endZ);
        addSetting(endPitch);
        addSetting(endYaw);
        addSetting(endRoll);
        addSetting(curve1X);
        addSetting(curve1Y);
        addSetting(curve2X);
        addSetting(curve2Y);
        addSetting(customBack);
        addSetting(customBackSpeed);

        addSetting(newShowLeftArm);
        addSetting(attackMode);
        addSetting(rightX);
        addSetting(rightY);
        addSetting(rightZ);
        addSetting(leftX);
        addSetting(leftY);
        addSetting(leftZ);
    }

    public static SwingAnimationsModule getInstance() {
        return instance;
    }

    private NumberSetting hmi(String name) {
        return new NumberSetting(name, "Смещение руки в режиме HMI", 0.0, -2.0, 2.0, 0.1)
                .visibleWhen(() -> mode.is("HMI"));
    }

    private NumberSetting custom(String name, double value, double min, double max, double step) {
        return new NumberSetting(name, "Параметр пользовательской Bezier-анимации", value, min, max, step)
                .visibleWhen(() -> mode.is("Custom"));
    }

    public boolean isHmi() {
        return isEnabled() && mode.is("HMI");
    }

    public boolean isBlockHitEnabled() {
        return isEnabled() && !mode.is("HMI") && (blockHit17.isEnabled() || mode.is("1.7 BlockHit"));
    }

    public float getItemScale() {
        return itemScale.getFloatValue();
    }

    public static boolean auraHasTarget() {
        OpticumeAuraModule opticumeAura = OpticumeAuraModule.getInstance();
        if (opticumeAura != null && opticumeAura.isEnabled()) {
            LivingEntity t = opticumeAura.getTarget();
            if (t != null && t.isAlive()) return true;
        }
        KillAuraModule killAura = KillAuraModule.getInstance();
        if (killAura != null && killAura.isEnabled()) {
            LivingEntity t = killAura.getCurrentTarget();
            if (t != null && t.isAlive()) return true;
        }
        return false;
    }

    private boolean swingAllowed() {
        return !onlyOnHit.isEnabled() || (mc.player != null && mc.player.isSwinging());
    }

    public boolean overridesVanillaSwing() {
        if (!isEnabled() || mode.is("Обычный")) return false;
        if (mode.is("HMI")) return true;
        return swingAllowed() && (!auraOnly.isEnabled() || auraHasTarget());
    }

    /**
     * Applies swing animation transforms on swingArm().
     * Returns true if vanilla swingArm should be cancelled.
     */
    public boolean applySwing(PoseStack pose, HumanoidArm arm, float swingProgress) {
        if (!isEnabled() || mode.is("HMI") || !swingAllowed()) return false;
        if (auraOnly.isEnabled() && !auraHasTarget()) return false;

        int side = arm == HumanoidArm.RIGHT ? 1 : -1;
        float progress = Mth.clamp(swingProgress, 0.0f, 1.0f);
        float eased = cubicEase.isEnabled() ? cubicOut(progress) : progress;
        float wave = (float) Math.sin(eased * Math.PI);
        float tilt = tiltPower.getFloatValue();
        float scale = itemScale.getFloatValue();
        boolean left = arm == HumanoidArm.LEFT;

        if (scale != 1.0f) {
            pose.scale(scale, scale, scale);
        }
        if (mc.player == null || arm != mc.player.getMainArm()) return false;

        switch (mode.getMode()) {
            case "Обычный" -> {
                return false;
            }
            case "Мод 1" -> {
                float sqrt = Mth.sqrt(eased);
                pose.rotateDegrees(Axis.YP, side * (45.0f + Mth.sin(eased * eased * (float) Math.PI) / 4.0f * -20.0f));
                pose.rotateDegrees(Axis.ZP, side * Mth.sin(sqrt * (float) Math.PI) * -20.0f);
                pose.rotateDegrees(Axis.XP, Mth.sin(sqrt * (float) Math.PI) * -tilt * 10.0f);
                pose.rotateDegrees(Axis.YP, -side * 45.0f);
            }
            case "Мод 2" -> pose.rotateDegrees(Axis.XP, side * (eased * (float) Math.PI - tilt * 10.0f * wave));
            case "Мод 3" -> {
                pose.translate(0.0f, 0.15f, -0.3f);
                pose.rotateDegrees(Axis.YP, left ? -90.0f : 90.0f);
                pose.rotateDegrees(Axis.ZP, left ? 60.0f : -60.0f);
                pose.rotateDegrees(Axis.XP, -angle.getFloatValue() - tilt * 10.0f * wave);
            }
            case "Мод 4" -> {
                pose.translate(0.0f, 0.15f, -0.3f);
                pose.rotateDegrees(Axis.YP, left ? -70.0f : 70.0f);
                pose.rotateDegrees(Axis.ZP, left ? 10.0f : -10.0f);
                pose.rotateDegrees(Axis.XP, -angle.getFloatValue() - tilt * 10.0f * wave);
            }
            case "Мод 5" -> {
                pose.translate(0.0f, 0.15f, -0.3f);
                pose.rotateDegrees(Axis.YP, left ? -70.0f : 70.0f);
                pose.rotateDegrees(Axis.ZP, left ? 10.0f : -10.0f);
                pose.rotateDegrees(Axis.XP, -70.0f - tilt * 10.0f * wave);
            }
            case "Мод 6" -> {
                pose.translate(0.0f, 0.15f, -0.3f);
                pose.rotateDegrees(Axis.YP, left ? -70.0f - 20.0f * wave : 70.0f + 20.0f * wave);
                pose.rotateDegrees(Axis.ZP, left ? 10.0f + 60.0f * wave : -10.0f - 60.0f * wave);
                pose.rotateDegrees(Axis.XP, -100.0f - tilt * 10.0f * wave);
            }
            case "Мод 7" -> {
                pose.translate(0.0f, 0.15f, -0.3f);
                pose.rotateDegrees(Axis.YP, left ? -90.0f : 90.0f);
                pose.rotateDegrees(Axis.ZP, left ? 60.0f : -60.0f - tilt * 10.0f * wave);
                pose.rotateDegrees(Axis.XP, -50.0f);
            }
            case "Мод 8" -> {
                pose.translate(0.5f, -0.15f, -0.65f);
                pose.rotateDegrees(Axis.YP, 45.0f);
                float a = Mth.sin(eased * eased * (float) Math.PI);
                float b = Mth.sin(Mth.sqrt(eased) * (float) Math.PI);
                pose.rotateDegrees(Axis.YP, a * -20.0f);
                pose.rotateDegrees(Axis.ZP, b * -20.0f);
                pose.rotateDegrees(Axis.XP, b * (-tilt * 10.0f));
                pose.translate(-0.5f, 0.2f, 0.0f);
                pose.rotateDegrees(Axis.YP, left ? -60.0f : 30.0f);
                pose.rotateDegrees(Axis.XP, -80.0f);
                pose.rotateDegrees(Axis.YP, left ? -60.0f : 60.0f);
            }
            case "Мод 9" -> {
                pose.rotateDegrees(Axis.YP, side * 45.0f);
                pose.rotateDegrees(Axis.XP, wave * -20.0f);
                pose.rotateDegrees(Axis.ZP, side * wave * -20.0f);
                pose.rotateDegrees(Axis.XP, wave * -80.0f);
                pose.translate(side * 0.4f, 0.2f, 0.2f);
                pose.translate(side * -0.5f, 0.08f, 0.0f);
                pose.rotateDegrees(Axis.YP, side * 20.0f);
                pose.rotateDegrees(Axis.XP, -80.0f);
                pose.rotateDegrees(Axis.YP, side * 20.0f);
            }
            case "Мод 10" -> {
                pose.translate(0.5f, -0.15f, -0.65f);
                float bounce = -0.2f * Mth.sin(eased * (float) Math.PI);
                pose.rotateDegrees(Axis.YP, 45.0f);
                pose.rotateDegrees(Axis.YP, 30.0f);
                pose.rotateDegrees(Axis.XP, -80.0f);
                pose.rotateDegrees(Axis.YP, 60.0f);
                long now = System.currentTimeMillis();
                int spin = (int) Math.min(255L, (now % 255L > 127L ? Math.abs(Math.abs(now) % 255L - 255L) : now % 255L) * 2L);
                float back = bounce > 0.5f ? 1.0f - bounce : bounce;
                pose.rotateDegrees(Axis.XP, -10.0f);
                pose.rotateDegrees(Axis.XP, -back * 10.0f);
                pose.rotateDegrees(Axis.YP, 10.0f);
                pose.rotateDegrees(Axis.ZP, mc.player.isSwinging() ? -spin / (110.0f - tilt * 10.0f) : 1.0f);
            }
            case "Мод 11" -> {
                float drop = eased < 0.8f ? -eased * 1.25f : -(1.0f - (eased - 0.8f) * 5.0f);
                pose.translate(0.0f, drop, 0.0f);
            }
            case "Мод 12" -> {
                float invert = left ? -1.0f : 1.0f;
                float swingWave = Mth.sin(Mth.sqrt(eased) * (float) Math.PI);
                pose.translate(-invert * 0.56f, 0.72f, 0.72f);
                pose.translate(0.56f * invert, -0.52f - swingWave * tilt / 24.0f, -0.72f);
                pose.rotateDegrees(Axis.YP, 90.0f * invert);
                pose.rotateDegrees(Axis.ZP, -30.0f * invert);
                pose.rotateDegrees(Axis.XP, -90.0f);
            }
            case "Мод 13" -> {
                pose.translate(0.0f, 0.0f, -wave * tilt / 10.0f);
                pose.rotateDegrees(Axis.YP, side * wave * tilt * 5.0f);
            }
            case "Мод 14" -> {
                pose.translate(0.0f, 0.1f, 0.0f);
                pose.rotateDegrees(Axis.XP, eased * 360.0f);
                pose.translate(0.0f, -0.1f, 0.0f);
            }
            case "Custom" -> applyCustom(pose, side, progress);
            // Preserved Opticume ViewModel swing styles
            case "1.7 BlockHit" -> {
                float f = Mth.sin(eased * eased * (float) Math.PI);
                pose.rotateDegrees(Axis.YP, side * (45.0f + f * -20.0f));
                float g = Mth.sin(Mth.sqrt(eased) * (float) Math.PI);
                pose.rotateDegrees(Axis.ZP, side * g * -20.0f);
                pose.rotateDegrees(Axis.XP, g * -80.0f);
                pose.rotateDegrees(Axis.YP, side * -45.0f);
                pose.translate(-0.15f * side, 0.15f, -0.1f);
                pose.rotateDegrees(Axis.XP, -30.0f * g);
                pose.rotateDegrees(Axis.YP, 30.0f * side * g);
            }
            case "Slide" -> {
                float g = Mth.sin(Mth.sqrt(eased) * (float) Math.PI);
                pose.translate(side * 0.2f * g, -0.2f * g, -0.25f * g);
                pose.rotateDegrees(Axis.XP, -60.0f * g);
                pose.rotateDegrees(Axis.YP, side * 30.0f * g);
                pose.rotateDegrees(Axis.ZP, side * -30.0f * g);
            }
            case "Swipe" -> {
                float f = Mth.sin(eased * (float) Math.PI);
                pose.translate(side * 0.1f * f, -0.1f * f, -0.1f * f);
                pose.rotateDegrees(Axis.XP, -85.0f * f);
                pose.rotateDegrees(Axis.YP, side * 45.0f * (1.0f - f));
                pose.rotateDegrees(Axis.ZP, side * -50.0f * f);
            }
            case "Spin 360" -> {
                pose.rotateDegrees(Axis.XP, -360.0f * eased);
                pose.rotateDegrees(Axis.YP, side * 25.0f * Mth.sin(eased * (float) Math.PI));
            }
            case "Nursultan Smooth" -> {
                pose.rotateDegrees(Axis.YP, side * 45.0f);
                float g = Mth.sin(Mth.sqrt(eased) * (float) Math.PI);
                pose.translate(side * 0.15f * g, -0.05f * g, -0.1f * g);
                pose.rotateDegrees(Axis.XP, -65.0f * g);
                pose.rotateDegrees(Axis.YP, side * (35.0f * g - 15.0f * g * g));
                pose.rotateDegrees(Axis.ZP, side * -35.0f * g);
                pose.rotateDegrees(Axis.YP, side * -45.0f);
            }
            case "Exhibition" -> {
                float g = Mth.sin(Mth.sqrt(eased) * (float) Math.PI);
                pose.translate(side * -0.1f * g, 0.1f * g, -0.15f * g);
                pose.rotateDegrees(Axis.YP, side * 30.0f);
                pose.rotateDegrees(Axis.ZP, side * -20.0f);
                pose.rotateDegrees(Axis.XP, -50.0f * g - 20.0f);
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    /**
     * Also synced to applyItemArmAttackTransform when ViewModel's own non-Vanilla swing is active or SwingAnimations is enabled.
     */
    public int swingDuration(boolean mainHand) {
        if (!isEnabled() || mode.is("HMI") || !changeSpeed.isEnabled() || !mainHand || !swingAllowed()) return -1;
        if (auraOnly.isEnabled() && !auraHasTarget()) return -1;
        return Math.max(1, 25 - speed.getIntValue() * 2);
    }

    public float eatBobbing(float vanilla) {
        if (!isEnabled()) return vanilla;
        return switch (useAnimation.getMode()) {
            case "Укороченная" -> 0.02f;
            case "Полная отмена" -> 0.0f;
            default -> vanilla;
        };
    }

    private void applyCustom(PoseStack pose, int side, float progress) {
        float t = customProgress(progress);
        float x = Mth.lerp(t, startX.getFloatValue(), endX.getFloatValue()) * side;
        float y = Mth.lerp(t, startY.getFloatValue(), endY.getFloatValue());
        float z = Mth.lerp(t, startZ.getFloatValue(), endZ.getFloatValue());
        float pitch = Mth.lerp(t, startPitch.getFloatValue(), endPitch.getFloatValue());
        float yaw = Mth.lerp(t, startYaw.getFloatValue(), endYaw.getFloatValue()) * side;
        float roll = Mth.lerp(t, startRoll.getFloatValue(), endRoll.getFloatValue()) * side;
        pose.translate(x, y, z);
        pose.rotateDegrees(Axis.YP, yaw);
        pose.rotateDegrees(Axis.ZP, roll);
        pose.rotateDegrees(Axis.XP, pitch);
    }

    private float customProgress(float progress) {
        if (customBack.isEnabled()) {
            float split = Mth.lerp((customBackSpeed.getFloatValue() - 1.0f) / 9.0f, 0.45f, 0.78f);
            if (progress <= split) return bezierAt(Mth.clamp(progress / split, 0.0f, 1.0f));
            float back = 1.0f - Mth.clamp((progress - split) / (1.0f - split), 0.0f, 1.0f);
            return back * back * back;
        }
        return bezierAt(Mth.clamp(progress, 0.0f, 1.0f));
    }

    private float bezierAt(float x) {
        float lo = 0.0f, hi = 1.0f;
        for (int i = 0; i < 12; i++) {
            float mid = (lo + hi) * 0.5f;
            if (bezier(mid, 0.0f, curve1X.getFloatValue(), curve2X.getFloatValue(), 1.0f) < x) lo = mid;
            else hi = mid;
        }
        return Mth.clamp(bezier((lo + hi) * 0.5f, 0.0f, curve1Y.getFloatValue(), curve2Y.getFloatValue(), 1.0f), 0.0f, 1.0f);
    }

    private static float bezier(float t, float p0, float p1, float p2, float p3) {
        float u = 1.0f - t;
        return u * u * u * p0 + 3.0f * u * u * t * p1 + 3.0f * u * t * t * p2 + t * t * t * p3;
    }

    private static float cubicOut(float t) {
        t = Mth.clamp(t, 0.0f, 1.0f);
        float u = 1.0f - t;
        return 1.0f - u * u * u;
    }
}
