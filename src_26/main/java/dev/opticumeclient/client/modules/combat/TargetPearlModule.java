package dev.opticumeclient.client.modules.combat;

import dev.opticumeclient.client.combat.RotationManager;
import dev.opticumeclient.client.friend.FriendManager;
import dev.opticumeclient.client.modules.Category;
import dev.opticumeclient.client.modules.Module;
import dev.opticumeclient.client.setting.BooleanSetting;
import dev.opticumeclient.client.setting.NumberSetting;
import dev.opticumeclient.client.util.InventoryUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;

/**
 * Tracks enemy ThrownEnderpearl projectiles, simulates their landing trajectory,
 * and throws a follow-up ender pearl towards the predicted landing point.
 */
public class TargetPearlModule extends Module {
    private final NumberSetting minDistance = new NumberSetting("MinDistance", "Мин. дистанция перелёта цели для погони", 10.0, 4.0, 35.0, 1.0);
    private final NumberSetting maxDistance = new NumberSetting("MaxDistance", "Макс. дистанция броска перла", 45.0, 15.0, 70.0, 1.0);
    private final BooleanSetting onlyAuraTarget = new BooleanSetting("OnlyTarget", "Реагировать только на активную цель ауры", false);
    private final BooleanSetting silentRotate = new BooleanSetting("SilentRotate", "Скрытый поворот при броске перла", true);

    private final Set<Integer> handledPearls = new HashSet<>();
    private int throwCooldown = 0;

    public TargetPearlModule() {
        super("TargetPearl", "Автоматический бросок эндер-жемчуга вслед за убегающим противником", Category.COMBAT);
        addSetting(minDistance);
        addSetting(maxDistance);
        addSetting(onlyAuraTarget);
        addSetting(silentRotate);
    }

    @Override
    public void onDisable() {
        handledPearls.clear();
        throwCooldown = 0;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.level == null || mc.gameMode == null) return;
        if (throwCooldown > 0) {
            throwCooldown--;
            return;
        }

        if (handledPearls.size() > 64) {
            handledPearls.clear();
        }

        int pearlSlot = InventoryUtil.searchStack(s -> s.is(Items.ENDER_PEARL), 0, 9);
        if (pearlSlot == -1) return;
        if (mc.player.getCooldowns().isOnCooldown(mc.player.getInventory().getItem(pearlSlot))) return;

        OpticumeAuraModule aura = OpticumeAuraModule.getInstance();
        Entity auraTarget = aura != null ? aura.getTarget() : null;

        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof ThrownEnderpearl pearl)) continue;
            if (handledPearls.contains(pearl.getId())) continue;

            Entity owner = pearl.getOwner();
            if (!(owner instanceof Player player) || owner == mc.player) continue;
            if (FriendManager.getInstance().isFriend(player) || AntiBotModule.isBot(player)) continue;
            if (onlyAuraTarget.isEnabled() && auraTarget != null && owner != auraTarget) continue;

            Vec3 landing = simulatePearlLanding(pearl);
            if (landing == null) continue;

            double dist = mc.player.getEyePosition().distanceTo(landing);
            if (dist < minDistance.getValue() || dist > maxDistance.getValue()) continue;

            float[] rot = computePearlAngles(mc.player.getEyePosition(), landing);
            if (rot == null) continue;

            int prevSlot = mc.player.getInventory().getSelectedSlot();
            final int targetPearlSlot = pearlSlot;
            Runnable throwAction = () -> {
                InventoryUtil.setCarriedSlot(mc, targetPearlSlot);
                mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
                InventoryUtil.setCarriedSlot(mc, prevSlot);
            };

            if (silentRotate.isEnabled()) {
                if (!RotationManager.getInstance().runWithSilentAngles("TargetPearl", rot[0], rot[1], 88,
                        RotationManager.MoveFixMode.FREE, true, throwAction)) {
                    return;
                }
            } else {
                mc.player.setYRot(rot[0]);
                mc.player.setXRot(rot[1]);
                throwAction.run();
            }
            handledPearls.add(pearl.getId());
            throwCooldown = 22;
            return;
        }
    }

    private Vec3 simulatePearlLanding(ThrownEnderpearl pearl) {
        Vec3 pos = pearl.position();
        Vec3 vel = pearl.getDeltaMovement();
        if (vel.lengthSqr() < 0.01) return null;

        for (int i = 0; i < 120; i++) {
            Vec3 next = pos.add(vel);
            HitResult hit = mc.level.clip(new ClipContext(
                    pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, pearl));
            if (hit.getType() != HitResult.Type.MISS) {
                return hit.getLocation();
            }
            pos = next;
            vel = vel.scale(0.99).add(0.0, -0.03, 0.0);
        }
        return pos;
    }

    /**
     * Solves yaw & pitch using the exact same drag (0.99/tick) and gravity (-0.03/tick) recurrence
     * used in {@link #simulatePearlLanding(ThrownEnderpearl)}, returning null if unreachable.
     */
    private float[] computePearlAngles(Vec3 from, Vec3 to) {
        if (mc.level == null || mc.player == null) return null;
        double dx = to.x - from.x;
        double dy = to.y - from.y;
        double dz = to.z - from.z;
        double horiz = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);

        double bestPitchDeg = Double.NaN;
        double bestErr = Double.MAX_VALUE;
        // Coarse scan over throw angles (-82..+60 deg) rejecting obstructed arcs
        for (double deg = -82.0; deg <= 60.0; deg += 2.0) {
            double err = evalClearPearlPitchError(from, to, yaw, deg, horiz, dy);
            if (err < bestErr) {
                bestErr = err;
                bestPitchDeg = deg;
            }
        }
        if (Double.isNaN(bestPitchDeg)) return null;

        double refined = bestPitchDeg;
        for (double deg = bestPitchDeg - 2.0; deg <= bestPitchDeg + 2.0; deg += 0.25) {
            double err = evalClearPearlPitchError(from, to, yaw, deg, horiz, dy);
            if (err < bestErr) {
                bestErr = err;
                refined = deg;
            }
        }
        if (!Double.isFinite(bestErr) || bestErr > 2.5) {
            return null;
        }
        return new float[]{yaw, Mth.clamp((float) refined, -85.0f, 85.0f)};
    }

    private double evalClearPearlPitchError(Vec3 from, Vec3 to, float yawDeg, double pitchDeg,
                                            double targetHoriz, double targetDy) {
        double rad = Math.toRadians(-pitchDeg);
        double vh = 1.5 * Math.cos(rad);
        double vy = 1.5 * Math.sin(rad);
        double yawRad = Math.toRadians(yawDeg);
        double fx = -Math.sin(yawRad);
        double fz = Math.cos(yawRad);

        Vec3 pos = from;
        Vec3 vel = new Vec3(fx * vh, vy, fz * vh);
        double minDist = Double.MAX_VALUE;
        for (int t = 0; t < 120; t++) {
            Vec3 next = pos.add(vel);
            HitResult hit = mc.level.clip(new ClipContext(
                    pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
            if (hit.getType() != HitResult.Type.MISS) {
                double hitDist = hit.getLocation().distanceTo(to);
                if (hitDist > 2.5) {
                    return Double.POSITIVE_INFINITY;
                }
                return Math.min(minDist, hitDist);
            }
            double d = next.distanceTo(to);
            if (d < minDist) minDist = d;
            double curHoriz = Math.hypot(next.x - from.x, next.z - from.z);
            if (curHoriz > targetHoriz + 4.0 && (next.y - from.y) < targetDy - 4.0) break;
            pos = next;
            vel = vel.scale(0.99).add(0.0, -0.03, 0.0);
        }
        return minDist;
    }
}
