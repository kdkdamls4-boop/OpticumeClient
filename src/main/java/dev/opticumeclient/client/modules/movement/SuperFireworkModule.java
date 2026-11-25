package dev.opticumeclient.client.modules.movement;

import dev.opticumeclient.client.modules.Category;
import dev.opticumeclient.client.modules.Module;
import dev.opticumeclient.client.setting.BooleanSetting;
import dev.opticumeclient.client.setting.NumberSetting;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Enhances Elytra firework rocket thrust with configurable boost factor,
 * pitch-angle scaling curve, and maximum velocity cap.
 */
public class SuperFireworkModule extends Module {
    private static SuperFireworkModule INSTANCE;

    private final NumberSetting boostFactor = new NumberSetting("Boost", "Множитель тяги фейерверка на элитрах", 1.35, 1.0, 2.6, 0.05);
    private final NumberSetting maxSpeed = new NumberSetting("MaxSpeed", "Ограничение максимальной скорости (блоков/тик)", 2.2, 1.2, 4.0, 0.1);
    private final BooleanSetting angleCurve = new BooleanSetting("AngleCurve", "Усиливать тягу при горизонтальном полёте и смягчать в пике", true);

    public SuperFireworkModule() {
        super("SuperFirework", "Усиление ускорения от фейерверков при полёте на элитрах", Category.MOVEMENT);
        INSTANCE = this;
        addSetting(boostFactor);
        addSetting(maxSpeed);
        addSetting(angleCurve);
    }

    public static SuperFireworkModule getInstance() {
        return INSTANCE;
    }

    @Override
    public String getDisplaySuffix() {
        return String.format("x%.2f", boostFactor.getValue());
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.level == null || !mc.player.isFallFlying()) return;
        if (!hasAttachedFirework()) return;

        Vec3 look = mc.player.getLookAngle();
        Vec3 vel = mc.player.getDeltaMovement();

        double extra = (boostFactor.getValue() - 1.0) * 0.24;
        if (angleCurve.isEnabled()) {
            double pitchCos = Math.cos(Math.toRadians(mc.player.getXRot()));
            extra *= (0.55 + 0.45 * Math.abs(pitchCos));
        }

        Vec3 boosted = vel.add(look.scale(extra));
        double cap = maxSpeed.getValue();
        if (boosted.length() > cap) {
            boosted = boosted.normalize().scale(cap);
        }
        mc.player.setDeltaMovement(boosted);
    }

    private boolean hasAttachedFirework() {
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof FireworkRocketEntity rocket && rocket.isAlive()) {
                if (rocket.distanceToSqr(mc.player) < 9.0) {
                    return true;
                }
            }
        }
        return false;
    }
}
