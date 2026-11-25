package dev.opticumeclient.client.modules.movement;

import dev.opticumeclient.client.modules.Category;
import dev.opticumeclient.client.modules.Module;
import dev.opticumeclient.client.setting.ModeSetting;
import dev.opticumeclient.client.setting.NumberSetting;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

/**
 * Movement acceleration module:
 * - Entity Collision: Grim/FunTime/HolyWorld-friendly boost when brushing past nearby living entities.
 * - Strafe Hop: auto-jump with directional air strafe velocity alignment.
 * - Vanilla Boost: direct horizontal speed scaling.
 */
public class SpeedModule extends Module {
    private final ModeSetting mode = new ModeSetting("Mode", "Режим ускорения", "Entity Collision", "Entity Collision", "Strafe Hop", "Vanilla Boost");
    private final NumberSetting speed = new NumberSetting("Speed", "Множитель скорости", 1.15, 1.0, 2.5, 0.02);
    private final NumberSetting entityRange = new NumberSetting("EntityExpand", "Радиус коллизии с сущностями", 1.15, 0.5, 2.5, 0.05);
    private final NumberSetting entityBoost = new NumberSetting("EntityBoost", "Сила толчка от сущности", 0.08, 0.01, 0.25, 0.01);

    public SpeedModule() {
        super("Speed", "Ускорение передвижения (Entity Collision / Strafe Hop / Vanilla Boost)", Category.MOVEMENT);
        entityRange.visibleWhen(() -> mode.is("Entity Collision"));
        entityBoost.visibleWhen(() -> mode.is("Entity Collision"));
        speed.visibleWhen(() -> !mode.is("Entity Collision"));

        addSetting(mode);
        addSetting(speed);
        addSetting(entityRange);
        addSetting(entityBoost);
    }

    @Override
    public String getDisplaySuffix() {
        return mode.getMode();
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.level == null) return;
        if (mc.player.isInWater() || mc.player.isInLava() || mc.player.isFallFlying() || mc.player.isShiftKeyDown()) {
            return;
        }

        Vec2 moveVec = mc.player.input != null ? mc.player.input.getMoveVector() : Vec2.ZERO;
        if (Math.abs(moveVec.x) < 1.0E-3f && Math.abs(moveVec.y) < 1.0E-3f) {
            return;
        }

        double moveYawRad = getMoveDirectionRadians(moveVec);
        double dirX = -Math.sin(moveYawRad);
        double dirZ = Math.cos(moveYawRad);

        switch (mode.getMode()) {
            case "Entity Collision" -> {
                AABB expanded = mc.player.getBoundingBox().inflate(entityRange.getValue());
                int collisions = 0;
                for (Entity e : mc.level.getEntities(mc.player, expanded)) {
                    if (e instanceof LivingEntity && e.isAlive()) {
                        collisions++;
                    }
                }
                if (collisions > 0) {
                    double impulse = Math.min(0.24, entityBoost.getValue() * Math.min(collisions, 3));
                    Vec3 vel = mc.player.getDeltaMovement();
                    mc.player.setDeltaMovement(vel.x + dirX * impulse, vel.y, vel.z + dirZ * impulse);
                }
            }
            case "Strafe Hop" -> {
                if (mc.player.onGround()) {
                    mc.player.jumpFromGround();
                }
                Vec3 vel = mc.player.getDeltaMovement();
                double currentSpeed = Math.max(0.26, Math.hypot(vel.x, vel.z));
                double targetSpeed = Math.min(0.45, currentSpeed * (0.98 + (speed.getValue() - 1.0) * 0.25));
                mc.player.setDeltaMovement(dirX * targetSpeed, vel.y, dirZ * targetSpeed);
            }
            case "Vanilla Boost" -> {
                Vec3 vel = mc.player.getDeltaMovement();
                double targetSpeed = 0.2873 * speed.getValue();
                mc.player.setDeltaMovement(dirX * targetSpeed, vel.y, dirZ * targetSpeed);
            }
        }
    }

    private double getMoveDirectionRadians(Vec2 moveVec) {
        float yaw = mc.player.getYRot();
        float forward = moveVec.y;
        float strafe = moveVec.x;
        if (forward < 0.0f) yaw += 180.0f;
        float mul = forward < 0.0f ? -0.5f : (forward > 0.0f ? 0.5f : 1.0f);
        if (strafe > 0.0f) yaw -= 90.0f * mul;
        if (strafe < 0.0f) yaw += 90.0f * mul;
        return Math.toRadians(yaw);
    }
}
