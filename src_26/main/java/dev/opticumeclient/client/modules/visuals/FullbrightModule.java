package dev.opticumeclient.client.modules.visuals;

import dev.opticumeclient.client.modules.Category;
import dev.opticumeclient.client.modules.Module;
import dev.opticumeclient.client.setting.ModeSetting;
import dev.opticumeclient.client.setting.NumberSetting;
import net.minecraft.world.effect.MobEffectInstance; 
import net.minecraft.world.effect.MobEffects; 

public class FullbrightModule extends Module {
    private static FullbrightModule INSTANCE;

    private final ModeSetting mode = new ModeSetting("Mode", "Режим работы Fullbright", "LightMap", "LightMap", "Potion");
    private final NumberSetting brightness = new NumberSetting("Brightness", "Яркость в режиме LightMap", 1.0, 0.1, 10.0, 0.1);

    private boolean appliedByModule = false;

    public FullbrightModule() {
        super("Fullbright", "Постоянная 100% видимость в темноте и пещерах", Category.VISUALS);
        addSetting(mode);
        addSetting(brightness);
        INSTANCE = this;
        setEnabled(true);
    }

    public static FullbrightModule getInstance() {
        return INSTANCE;
    }

    public boolean isLightmapMode() {
        return isEnabled() && mode.is("LightMap");
    }

    public float getBrightness() {
        return brightness.getFloatValue();
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;

        if (mode.is("Potion")) {
            if (!mc.player.hasEffect(MobEffects.NIGHT_VISION)) {
                appliedByModule = true;
            }
            mc.player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 600, 0, false, false, false));
        } else if (appliedByModule) {
            mc.player.removeEffect(MobEffects.NIGHT_VISION);
            appliedByModule = false;
        }
    }

    @Override
    public void onDisable() {
        if (mc.player != null && appliedByModule) {
            mc.player.removeEffect(MobEffects.NIGHT_VISION);
            appliedByModule = false;
        }
    }
}
