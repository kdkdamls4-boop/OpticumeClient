package dev.opticumeclient.client.modules.combat;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.opticumeclient.client.combat.RotationManager;
import dev.opticumeclient.client.friend.FriendManager;
import dev.opticumeclient.client.modules.Category;
import dev.opticumeclient.client.modules.Module;
import dev.opticumeclient.client.render.ColorUtils;
import dev.opticumeclient.client.render.Render3DHelper;
import dev.opticumeclient.client.render.ThemeManager;
import dev.opticumeclient.client.setting.BooleanSetting;
import dev.opticumeclient.client.setting.NumberSetting;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Ballistic lead aim helper for Bow, Crossbow, and Trident.
 * Computes projectile gravity arc + target linear velocity prediction and routes through RotationManager.
 */
public class BowAimBotModule extends Module {
    private final NumberSetting range = new NumberSetting("Range", "Максимальная дистанция наводки", 45.0, 10.0, 90.0, 1.0);
    private final NumberSetting fov = new NumberSetting("FOV", "Угол захвата цели", 90.0, 15.0, 180.0, 5.0);
    private final NumberSetting predictFactor = new NumberSetting("Predict", "Множитель упреждения движения цели", 1.0, 0.0, 2.0, 0.05);
    private final BooleanSetting silentRotate = new BooleanSetting("SilentRotate", "Скрытая наводка через RotationManager", true);
    private final BooleanSetting playersOnly = new BooleanSetting("PlayersOnly", "Наводиться только на игроков", true);
    private final BooleanSetting renderMarker = new BooleanSetting("RenderMarker", "Показывать точку упреждения в 3D", true);

    private Vec3 lastPredictedPoint = null;

    public BowAimBotModule() {
        super("BowAimBot", "Баллистическое упреждение и наводка для лука, арбалета и трезубца", Category.COMBAT);
        addSetting(range);
        addSetting(fov);
        addSetting(predictFactor);
        addSetting(silentRotate);
        addSetting(playersOnly);
        addSetting(renderMarker);
    }

    @Override
    public void onDisable() {
        lastPredictedPoint = null;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.level == null) {
            lastPredictedPoint = null;
            return;
        }

        ItemStack active = getRangedStack();
        if (active == null) {
            lastPredictedPoint = null;
            return;
        }

        double speed = getProjectileSpeed(active);
        double gravity = getProjectileGravity(active);
        if (speed <= 0.1) {
            lastPredictedPoint = null;
            return;
        }

        LivingEntity target = findTarget(speed, gravity);
        if (target == null) {
            lastPredictedPoint = null;
            return;
        }

        Vec3 eyes = mc.player.getEyePosition();
        Vec3 predicted = predictTargetPoint(eyes, target, speed);
        float[] rot = computeBallisticAngles(eyes, predicted, speed, gravity);
        if (rot == null) {
            lastPredictedPoint = null;
            return;
        }
        lastPredictedPoint = predicted;

        if (silentRotate.isEnabled()) {
            RotationManager.getInstance().request("BowAimBot", rot[0], rot[1], 55,
                    RotationManager.MoveFixMode.FREE, 65.0f, 55.0f, true);
        } else {
            mc.player.setYRot(rot[0]);
            mc.player.setXRot(rot[1]);
        }
    }

    private Vec3 predictTargetPoint(Vec3 eyes, LivingEntity target, double speed) {
        Vec3 targetBase = target.position().add(0, target.getBbHeight() * 0.68, 0);
        Vec3 targetVel = target.position().subtract(target.xOld, target.yOld, target.zOld);
        double dist = eyes.distanceTo(targetBase);
        double flightTicks = dist / speed;
        Vec3 predicted = targetBase.add(targetVel.scale(flightTicks * predictFactor.getValue()));
        dist = eyes.distanceTo(predicted);
        flightTicks = dist / speed;
        return targetBase.add(targetVel.scale(flightTicks * predictFactor.getValue()));
    }

    @Override
    public void onRender3D(PoseStack matrices, float tickDelta) {
        if (!renderMarker.isEnabled() || lastPredictedPoint == null) return;
        int color = ColorUtils.withAlpha(ThemeManager.getInstance().getAccentColor(0), 220);
        double s = 0.16;
        AABB marker = new AABB(
                lastPredictedPoint.x - s, lastPredictedPoint.y - s, lastPredictedPoint.z - s,
                lastPredictedPoint.x + s, lastPredictedPoint.y + s, lastPredictedPoint.z + s
        );
        Render3DHelper.drawBoxColor(marker, color, 1.8f, true);
    }

    private ItemStack getRangedStack() {
        if (mc.player.isUsingItem()) {
            ItemStack use = mc.player.getUseItem();
            if (use.getItem() instanceof BowItem || use.getItem() instanceof TridentItem) {
                return use;
            }
        }
        ItemStack main = mc.player.getMainHandItem();
        if (main.getItem() instanceof CrossbowItem && CrossbowItem.isCharged(main)) {
            return main;
        }
        return null;
    }

    private double getProjectileSpeed(ItemStack stack) {
        if (stack.getItem() instanceof BowItem) {
            int useTicks = stack.getUseDuration(mc.player) - mc.player.getUseItemRemainingTicks();
            float power = BowItem.getPowerForTime(useTicks);
            return power < 0.15f ? 0.0 : power * 3.0;
        }
        if (stack.getItem() instanceof CrossbowItem) {
            return 3.15;
        }
        if (stack.getItem() instanceof TridentItem) {
            int useTicks = stack.getUseDuration(mc.player) - mc.player.getUseItemRemainingTicks();
            return useTicks >= 10 ? 2.5 : 0.0;
        }
        return 0.0;
    }

    private double getProjectileGravity(ItemStack stack) {
        if (stack.getItem() instanceof CrossbowItem) return 0.05;
        return 0.05;
    }

    private float[] computeBallisticAngles(Vec3 from, Vec3 to, double speed, double gravity) {
        double dx = to.x - from.x;
        double dy = to.y - from.y;
        double dz = to.z - from.z;
        double horiz = Math.sqrt(dx * dx + dz * dz);

        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        if (horiz <= 1.0E-4) {
            return new float[]{yaw, dy >= 0.0 ? -90.0f : 90.0f};
        }
        double v2 = speed * speed;
        double v4 = v2 * v2;
        double disc = v4 - gravity * (gravity * horiz * horiz + 2.0 * dy * v2);
        if (disc < 0.0) {
            return null;
        }
        double rad = Math.atan((v2 - Math.sqrt(disc)) / (gravity * horiz));
        float pitch = (float) -Math.toDegrees(rad);
        return new float[]{yaw, Mth.clamp(pitch, -90.0f, 90.0f)};
    }

    private LivingEntity findTarget(double speed, double gravity) {
        Vec3 eyes = mc.player.getEyePosition();
        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;
        double maxRange = range.getValue();
        double halfFov = fov.getValue() / 2.0;

        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof LivingEntity living) || e == mc.player || !living.isAlive()) continue;
            if (playersOnly.isEnabled() && !(living instanceof Player)) continue;
            if (FriendManager.getInstance().isFriend(living) || AntiBotModule.isBot(living)) continue;
            if (!mc.player.hasLineOfSight(living)) continue;

            double dist = eyes.distanceTo(living.position());
            if (dist > maxRange) continue;

            Vec3 predicted = predictTargetPoint(eyes, living, speed);
            if (computeBallisticAngles(eyes, predicted, speed, gravity) == null) continue;

            float[] rot = RotationManager.getRotationsTo(eyes, living.getEyePosition());
            double yawDiff = Math.abs(Mth.wrapDegrees(rot[0] - mc.player.getYRot()));
            if (yawDiff > halfFov) continue;

            double score = yawDiff + dist * 0.6;
            if (score < bestScore) {
                bestScore = score;
                best = living;
            }
        }
        return best;
    }
}
