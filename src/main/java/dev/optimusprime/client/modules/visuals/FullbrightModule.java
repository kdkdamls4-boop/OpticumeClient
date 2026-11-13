package dev.optimusprime.client.modules.visuals;

import dev.optimusprime.client.modules.Category;
import dev.optimusprime.client.modules.Module;
import net.minecraft.world.effect.MobEffectInstance; 
import net.minecraft.world.effect.MobEffects; 

public class FullbrightModule extends Module {
    private static FullbrightModule INSTANCE;

    public FullbrightModule() {
        super("Fullbright", "Постоянная 100% видимость в темноте и пещерах", Category.VISUALS);
        INSTANCE = this;
        setEnabled(true);
    }

    public static FullbrightModule getInstance() {
        return INSTANCE;
    }

    @Override
    public void onTick() {
        if (mc.player != null) {
            mc.player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 600, 0, false, false, false));
        }
    }

    @Override
    public void onDisable() {
        if (mc.player != null) {
            mc.player.removeEffect(MobEffects.NIGHT_VISION);
        }
    }
}
