package dev.opticumeclient.client.modules.visuals;

import dev.opticumeclient.client.modules.Category;
import dev.opticumeclient.client.modules.Module;
import dev.opticumeclient.client.setting.BooleanSetting;
import dev.opticumeclient.client.setting.ModeSetting;
import dev.opticumeclient.client.setting.NumberSetting;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;

/**
 * Ported from Skycore (InterpolateF5Module) + Circular Front-F5 Orbit:
 * - Smooth third-person (F5) camera zoom-out transition & configurable camera distance
 * - Circular 180-degree orbit animation around the player when switching to Front F5 (Left or Right side)
 * - Camera Stabilization (smooth inertial camera rotation) & smooth jump/sneak vertical offset
 * - NoCameraClip (camera passes through walls)
 */
public class InterpolateF5Module extends Module {
    private static InterpolateF5Module instance;

    private final NumberSetting distanceSetting;
    private final NumberSetting switchSmoothSetting;
    private final BooleanSetting frontOrbitSetting;
    private final ModeSetting orbitSideSetting;
    private final NumberSetting orbitSpeedSetting;
    private final BooleanSetting verticalSmoothSetting;
    private final BooleanSetting stabilizationSetting;
    private final NumberSetting stabilizationSpeedSetting;
    private final BooleanSetting noClipSetting;

    private float prevDistance;
    private float currentDistance;
    private float prevOrbitAngle;
    private float currentOrbitAngle;
    private float prevHeightOffset;
    private float heightOffset;
    private float prevYaw;
    private float currentYaw;
    private float prevPitch;
    private float currentPitch;
    private boolean wasThirdPerson;
    private boolean switchAnimating;
    private boolean needsInit = true;

    public InterpolateF5Module() {
        super("InterpolateF5", "Плавный F5, круговой облёт камеры перед лицом (слева/справа) и стабилизация камеры", Category.VISUALS);
        instance = this;

        this.distanceSetting = addSetting(new NumberSetting(
                "Distance",
                "Дистанция камеры от 3-го лица",
                4.1f, 1.5f, 10.0f, 0.1f
        ));
        this.switchSmoothSetting = addSetting(new NumberSetting(
                "Switch Speed",
                "Скорость плавного выезда камеры при нажатии F5",
                0.26f, 0.06f, 0.65f, 0.01f
        ));
        this.frontOrbitSetting = addSetting(new BooleanSetting(
                "Front Orbit",
                "Круговая анимация облёта камеры на 180° при переключении вида перед лицом",
                true
        ));
        this.orbitSideSetting = addSetting(new ModeSetting(
                "Orbit Side",
                "С какой стороны камера облетает персонажа по кругу (Справа или Слева)",
                "Right",
                "Right", "Left"
        ).visibleWhen(frontOrbitSetting::isEnabled));
        this.orbitSpeedSetting = addSetting(new NumberSetting(
                "Orbit Speed",
                "Скорость кругового облёта камеры вокруг игрока",
                0.28f, 0.06f, 0.65f, 0.01f
        ).visibleWhen(frontOrbitSetting::isEnabled));
        this.verticalSmoothSetting = addSetting(new BooleanSetting(
                "Vertical Smooth",
                "Плавная инерция камеры по высоте при прыжках и приседании",
                true
        ));
        this.stabilizationSetting = addSetting(new BooleanSetting(
                "Camera Stabilization",
                "Плавная кинематографичная стабилизация поворотов камеры в F5",
                false
        ));
        this.stabilizationSpeedSetting = addSetting(new NumberSetting(
                "Stabilization Speed",
                "Скорость доводки стабилизации камеры",
                0.28f, 0.05f, 0.85f, 0.01f
        ).visibleWhen(stabilizationSetting::isEnabled));
        this.noClipSetting = addSetting(new BooleanSetting(
                "No Camera Clip",
                "Позволяет камере от 3-го лица проходить сквозь стены и блоки",
                false
        ));
    }

    public static InterpolateF5Module getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        this.needsInit = true;
        this.wasThirdPerson = false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            CameraType cameraType = mc.options.getCameraType();
            if (!cameraType.isFirstPerson()) {
                this.initCamera(true, cameraType.isMirrored());
                this.wasThirdPerson = true;
            }
        }
    }

    @Override
    public void onDisable() {
        this.needsInit = true;
        this.wasThirdPerson = false;
        this.switchAnimating = false;
        this.currentDistance = 0.0f;
        this.prevDistance = 0.0f;
        this.currentOrbitAngle = 0.0f;
        this.prevOrbitAngle = 0.0f;
        this.heightOffset = 0.0f;
        this.prevHeightOffset = 0.0f;
    }

    @Override
    public void onTick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.level != null) {
            CameraType cameraType = mc.options.getCameraType();
            boolean thirdPerson = !cameraType.isFirstPerson();
            if (thirdPerson && !this.wasThirdPerson) {
                this.initCamera(true, cameraType.isMirrored());
            }
            if (!thirdPerson && this.wasThirdPerson) {
                this.needsInit = true;
                this.switchAnimating = false;
            }
            this.wasThirdPerson = thirdPerson;
            if (thirdPerson) {
                this.updateCamera(mc.player, cameraType.isMirrored());
            }
        } else {
            this.needsInit = true;
            this.wasThirdPerson = false;
        }
    }

    private float getTargetOrbitAngle(boolean mirrored) {
        if (!mirrored) return 0.0f;
        return orbitSideSetting.is("Left") ? -180.0f : 180.0f;
    }

    private void initCamera(boolean animateSwitch, boolean mirrored) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        float yaw = player.getYRot();
        this.prevYaw = yaw;
        this.currentYaw = yaw;
        float pitch = player.getXRot();
        this.prevPitch = pitch;
        this.currentPitch = pitch;
        float targetDist = distanceSetting.getFloatValue();
        float startDist = animateSwitch ? 0.0f : targetDist;
        this.prevDistance = startDist;
        this.currentDistance = startDist;
        float startOrbit = mirrored ? (frontOrbitSetting.isEnabled() ? 0.0f : getTargetOrbitAngle(true)) : 0.0f;
        this.prevOrbitAngle = startOrbit;
        this.currentOrbitAngle = startOrbit;
        this.prevHeightOffset = 0.0f;
        this.heightOffset = 0.0f;
        this.switchAnimating = animateSwitch;
        this.needsInit = false;
    }

    private void updateCamera(LocalPlayer player, boolean mirrored) {
        if (this.needsInit) {
            this.initCamera(true, mirrored);
            return;
        }
        this.prevYaw = this.currentYaw;
        this.prevPitch = this.currentPitch;
        this.prevDistance = this.currentDistance;
        this.prevOrbitAngle = this.currentOrbitAngle;
        this.prevHeightOffset = this.heightOffset;

        float rotSpeed = stabilizationSpeedSetting.getFloatValue();
        this.currentYaw += Mth.wrapDegrees(player.getYRot() - this.currentYaw) * rotSpeed;
        this.currentPitch = Mth.clamp(this.currentPitch + (player.getXRot() - this.currentPitch) * rotSpeed, -90.0f, 90.0f);

        float targetDist = distanceSetting.getFloatValue();
        float speed = this.switchAnimating ? switchSmoothSetting.getFloatValue() : 0.16f;
        this.currentDistance += (targetDist - this.currentDistance) * speed;
        if (this.switchAnimating && Math.abs(targetDist - this.currentDistance) <= 0.02f) {
            this.currentDistance = targetDist;
            this.switchAnimating = false;
        }

        float targetOrbit = getTargetOrbitAngle(mirrored);
        if (frontOrbitSetting.isEnabled()) {
            float oSpeed = orbitSpeedSetting.getFloatValue();
            this.currentOrbitAngle += (targetOrbit - this.currentOrbitAngle) * oSpeed;
            if (Math.abs(targetOrbit - this.currentOrbitAngle) <= 0.25f) {
                this.currentOrbitAngle = targetOrbit;
            }
        } else {
            this.currentOrbitAngle = targetOrbit;
            this.prevOrbitAngle = targetOrbit;
        }

        float targetHeight = 0.0f;
        if (verticalSmoothSetting.isEnabled()) {
            if (player.isShiftKeyDown()) {
                targetHeight = -0.35f;
            }
            if (!player.onGround()) {
                targetHeight += (float) (-player.getDeltaMovement().y * 1.4);
                targetHeight = Mth.clamp(targetHeight, -1.2f, 1.2f);
            }
        }
        this.heightOffset += (targetHeight - this.heightOffset) * 0.15f;
    }

    private void ensureInitialized() {
        Minecraft mc = Minecraft.getInstance();
        if (this.needsInit && mc.player != null) {
            CameraType ct = mc.options.getCameraType();
            if (!ct.isFirstPerson()) {
                this.initCamera(true, ct.isMirrored());
                this.wasThirdPerson = true;
            }
        }
    }

    public float getInterpolatedDistance(float partialTick) {
        ensureInitialized();
        float t = Mth.clamp(partialTick, 0.0f, 1.0f);
        return Math.max(0.05f, this.prevDistance + (this.currentDistance - this.prevDistance) * t);
    }

    public float getInterpolatedOrbitAngle(float partialTick) {
        ensureInitialized();
        float t = Mth.clamp(partialTick, 0.0f, 1.0f);
        return this.prevOrbitAngle + (this.currentOrbitAngle - this.prevOrbitAngle) * t;
    }

    public float getInterpolatedHeightOffset(float partialTick) {
        if (!verticalSmoothSetting.isEnabled()) return 0.0f;
        ensureInitialized();
        float t = Mth.clamp(partialTick, 0.0f, 1.0f);
        return this.prevHeightOffset + (this.heightOffset - this.prevHeightOffset) * t;
    }

    public boolean isFrontOrbitEnabled() {
        return isEnabled() && frontOrbitSetting.isEnabled();
    }

    public boolean isCameraStabilizationEnabled() {
        return isEnabled() && stabilizationSetting.isEnabled();
    }

    public float getInterpolatedYaw(float partialTick) {
        ensureInitialized();
        float t = Mth.clamp(partialTick, 0.0f, 1.0f);
        return this.prevYaw + (this.currentYaw - this.prevYaw) * t;
    }

    public float getInterpolatedPitch(float partialTick) {
        ensureInitialized();
        float t = Mth.clamp(partialTick, 0.0f, 1.0f);
        return Mth.clamp(this.prevPitch + (this.currentPitch - this.prevPitch) * t, -90.0f, 90.0f);
    }

    public boolean isNoClip() {
        return isEnabled() && noClipSetting.isEnabled();
    }
}
