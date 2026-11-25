package dev.opticumeclient.client.modules.visuals;

import dev.opticumeclient.client.modules.Category;
import dev.opticumeclient.client.modules.Module;
import dev.opticumeclient.client.setting.BooleanSetting;
import net.minecraft.client.CameraType;
import net.minecraft.util.Mth;

/**
 * Independent 360-degree camera look while preserving the player's actual movement and facing direction.
 */
public class FreeLookModule extends Module {
    private static FreeLookModule INSTANCE;

    private final BooleanSetting autoThirdPerson = new BooleanSetting("ThirdPerson", "Автоматически включать 3-е лицо при активации", true);
    private final BooleanSetting invertY = new BooleanSetting("InvertY", "Инвертировать вертикальную ось", false);

    private float cameraYaw = 0.0f;
    private float cameraPitch = 0.0f;
    private CameraType previousCameraType = null;

    public FreeLookModule() {
        super("FreeLook", "Свободный обзор камерой на 360° без поворота персонажа", Category.VISUALS);
        INSTANCE = this;
        addSetting(autoThirdPerson);
        addSetting(invertY);
    }

    public static FreeLookModule getInstance() {
        return INSTANCE;
    }

    public boolean isActive() {
        return isEnabled() && mc.player != null;
    }

    public float getCameraYaw() {
        return cameraYaw;
    }

    public float getCameraPitch() {
        return cameraPitch;
    }

    public void addCameraDelta(double dx, double dy) {
        cameraYaw += (float) (dx * 0.15);
        double pitchSign = invertY.isEnabled() ? -1.0 : 1.0;
        cameraPitch = Mth.clamp(cameraPitch + (float) (dy * 0.15 * pitchSign), -90.0f, 90.0f);
    }

    @Override
    public void onEnable() {
        if (mc.player == null) {
            setEnabled(false);
            return;
        }
        cameraYaw = mc.player.getYRot();
        cameraPitch = mc.player.getXRot();
        if (autoThirdPerson.isEnabled() && mc.options != null) {
            previousCameraType = mc.options.getCameraType();
            if (previousCameraType == CameraType.FIRST_PERSON) {
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            }
        }
    }

    @Override
    public void onDisable() {
        if (previousCameraType != null && mc.options != null) {
            mc.options.setCameraType(previousCameraType);
            previousCameraType = null;
        }
    }
}
