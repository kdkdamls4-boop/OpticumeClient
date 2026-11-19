package dev.opticumeclient.client.modules.visuals;

import dev.opticumeclient.client.modules.Category;
import dev.opticumeclient.client.modules.Module;
import dev.opticumeclient.client.setting.ModeSetting;
import dev.opticumeclient.client.setting.NumberSetting;
import dev.opticumeclient.client.util.SoundUtil;
import net.minecraft.sounds.SoundEvent;

public class ToggleSoundsModule extends Module {
    private static ToggleSoundsModule INSTANCE;
    private static volatile boolean suppressSounds = false;

    private final ModeSetting sound = new ModeSetting(
            "Sound",
            "Пресет звука переключения модулей",
            "Blop",
            "Smooth", "Celestial", "Nursultan", "Akrien", "Tech", "Blop"
    );
    private final NumberSetting volume = new NumberSetting("Volume", "Громкость звуков переключения (%)", 60.0, 1.0, 100.0, 1.0);

    public ToggleSoundsModule() {
        super("Toggle Sounds", "Звуки при включении и выключении модулей", Category.VISUALS);
        addSetting(sound);
        addSetting(volume);
        INSTANCE = this;
        setEnabled(true);
    }

    public static ToggleSoundsModule getInstance() {
        return INSTANCE;
    }

    public static void setSuppressSounds(boolean suppress) {
        suppressSounds = suppress;
    }

    @Override
    public String getDisplaySuffix() {
        return sound.getMode();
    }

    public static void playToggle(boolean state) {
        if (suppressSounds || INSTANCE == null || !INSTANCE.isEnabled()) return;
        SoundEvent event = switch (INSTANCE.sound.getMode()) {
            case "Nursultan" -> state ? SoundUtil.ENABLE_NU_EVENT : SoundUtil.DISABLE_NU_EVENT;
            case "Celestial" -> state ? SoundUtil.ENABLE_CEL_EVENT : SoundUtil.DISABLE_CEL_EVENT;
            case "Akrien" -> state ? SoundUtil.ENABLE_AK_EVENT : SoundUtil.DISABLE_AK_EVENT;
            case "Tech" -> state ? SoundUtil.ENABLE_TECH_EVENT : SoundUtil.DISABLE_TECH_EVENT;
            case "Blop" -> state ? SoundUtil.ENABLE_BLOP_EVENT : SoundUtil.DISABLE_BLOP_EVENT;
            default -> state ? SoundUtil.ENABLE_SMOOTH_EVENT : SoundUtil.DISABLE_SMOOTH_EVENT;
        };
        SoundUtil.playUiSound(event, INSTANCE.volume.getFloatValue() / 100.0f);
    }
}
