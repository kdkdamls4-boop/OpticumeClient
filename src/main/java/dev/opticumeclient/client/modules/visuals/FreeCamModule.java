package dev.opticumeclient.client.modules.visuals;

import dev.opticumeclient.client.modules.Category;
import dev.opticumeclient.client.modules.Module;
import dev.opticumeclient.client.setting.BooleanSetting;
import dev.opticumeclient.client.setting.NumberSetting;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Detaches the camera from the player entity to fly freely through the world,
 * keeping the real player stationary and holding outgoing movement packets if enabled.
 */
public class FreeCamModule extends Module {
    private static FreeCamModule INSTANCE;

    private final NumberSetting speed = new NumberSetting("Speed", "Скорость полёта свободной камеры", 0.9, 0.2, 4.0, 0.1);
    private final NumberSetting verticalSpeed = new NumberSetting("VertSpeed", "Вертикальная скорость (Space/Shift)", 0.7, 0.2, 3.0, 0.1);
    private final BooleanSetting freezePackets = new BooleanSetting("FreezePackets", "Блокировать пакеты движения игрока", true);

    private Vec3 camPos = Vec3.ZERO;
    private Vec3 prevCamPos = Vec3.ZERO;
    private float camYaw = 0.0f;
    private float camPitch = 0.0f;
    private float savedPlayerYaw = 0.0f;
    private float savedPlayerPitch = 0.0f;

    public FreeCamModule() {
        super("FreeCam", "Свободный полёт камеры отдельно от тела игрока", Category.VISUALS);
        INSTANCE = this;
        addSetting(speed);
        addSetting(verticalSpeed);
        addSetting(freezePackets);
    }

    public static FreeCamModule getInstance() {
        return INSTANCE;
    }

    public boolean isActive() {
        return isEnabled() && mc.player != null;
    }

    public boolean shouldCancelMovementPackets() {
        return isActive() && freezePackets.isEnabled();
    }

    public Vec3 getInterpolatedPos(float partialTick) {
        return new Vec3(
                Mth.lerp(partialTick, prevCamPos.x, camPos.x),
                Mth.lerp(partialTick, prevCamPos.y, camPos.y),
                Mth.lerp(partialTick, prevCamPos.z, camPos.z)
        );
    }

    public float getCamYaw() {
        return camYaw;
    }

    public float getCamPitch() {
        return camPitch;
    }

    public void addCameraDelta(double dx, double dy) {
        camYaw += (float) (dx * 0.15);
        camPitch = Mth.clamp(camPitch + (float) (dy * 0.15), -90.0f, 90.0f);
    }

    @Override
    public void onEnable() {
        if (mc.player == null) {
            setEnabled(false);
            return;
        }
        camPos = mc.player.getEyePosition();
        prevCamPos = camPos;
        camYaw = mc.player.getYRot();
        camPitch = mc.player.getXRot();
        savedPlayerYaw = camYaw;
        savedPlayerPitch = camPitch;
    }

    @Override
    public void onDisable() {
        if (mc.player != null) {
            mc.player.setYRot(savedPlayerYaw);
            mc.player.setXRot(savedPlayerPitch);
        }
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.level == null) {
            setEnabled(false);
            return;
        }
        prevCamPos = camPos;

        float forward = 0.0f;
        float strafe = 0.0f;
        float vertical = 0.0f;

        if (mc.gui == null || mc.gui.screen() == null) {
            if (mc.options.keyUp.isDown()) forward += 1.0f;
            if (mc.options.keyDown.isDown()) forward -= 1.0f;
            if (mc.options.keyLeft.isDown()) strafe += 1.0f;
            if (mc.options.keyRight.isDown()) strafe -= 1.0f;
            if (mc.options.keyJump.isDown()) vertical += 1.0f;
            if (mc.options.keyShift.isDown()) vertical -= 1.0f;
        }

        double rad = Math.toRadians(camYaw);
        double sin = Math.sin(rad);
        double cos = Math.cos(rad);
        double moveX = (-sin * forward + cos * strafe);
        double moveZ = (cos * forward + sin * strafe);
        double horizLen = Math.hypot(moveX, moveZ);
        if (horizLen > 1.0E-4) {
            moveX = (moveX / horizLen) * speed.getValue();
            moveZ = (moveZ / horizLen) * speed.getValue();
        }
        double moveY = vertical * verticalSpeed.getValue();

        camPos = camPos.add(moveX, moveY, moveZ);
        mc.player.setDeltaMovement(0.0, mc.player.getDeltaMovement().y, 0.0);
    }
}
