package dev.opticumeclient.client.modules.combat;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.opticumeclient.client.combat.CombatRotationEngine;
import dev.opticumeclient.client.combat.RotationManager;
import dev.opticumeclient.client.friend.FriendManager;
import dev.opticumeclient.client.modules.Category;
import dev.opticumeclient.client.modules.Module;
import dev.opticumeclient.client.setting.BooleanSetting;
import dev.opticumeclient.client.setting.NumberSetting;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class AimAssistModule extends Module {
    private final NumberSetting range = new NumberSetting("Range", "Радиус захвата", 4.5, 2.0, 6.0, 0.1);
    private final NumberSetting fov = new NumberSetting("FOV", "Угол обзора", 60.0, 15.0, 120.0, 5.0);
    private final NumberSetting smooth = new NumberSetting("Smooth", "Сглаживание доводки", 6.0, 1.0, 15.0, 0.5);
    private final BooleanSetting lazyAim = new BooleanSetting("LazyAim", "Замедлять доводку на хитбоксе", true);
    private final BooleanSetting gcdFix = new BooleanSetting("GcdMouseFix", "Выравнивание шагов под чувствительность мыши", true);

    private final CombatRotationEngine.AimSession aimSession = new CombatRotationEngine.AimSession();
    private Player target = null;
    private long lastRenderTime = 0L;

    public AimAssistModule() {
        super("AimAssist", "Плавная легитная доводка прицела к цели", Category.COMBAT);
        addSetting(range);
        addSetting(fov);
        addSetting(smooth);
        addSetting(lazyAim);
        addSetting(gcdFix);
    }

    @Override
    public void onDisable() {
        target = null;
        lastRenderTime = 0L;
        aimSession.reset();
        RotationManager.getInstance().resetClientGcdResidual();
    }

    @Override
    public void onPreTick() {
        if (mc.player == null || mc.level == null) {
            target = null;
            aimSession.reset();
            return;
        }
        target = findTarget();
        if (target == null) {
            aimSession.reset();
            return;
        }
        aimSession.onTargetTick(target);
        if (System.nanoTime() - lastRenderTime > 120_000_000L) {
            applyAssistStep(1.0f);
        }
    }

    @Override
    public void onRender3D(PoseStack matrices, float tickDelta) {
        if (mc.player == null || target == null || !target.isAlive() || mc.gui.screen() != null) {
            lastRenderTime = 0L;
            return;
        }
        long now = System.nanoTime();
        float dt = lastRenderTime > 0L ? (now - lastRenderTime) / 1_000_000_000.0f : 0.0166f;
        lastRenderTime = now;
        float deltaTicks = Math.clamp(dt, 0.0005f, 0.05f) * 20.0f;
        applyAssistStep(deltaTicks);
    }

    private void applyAssistStep(float dtTicks) {
        if (mc.player == null || target == null) return;
        float maxYaw = (float) Math.max(4.0, 90.0 / Math.max(1.0, smooth.getValue()));
        float maxPitch = maxYaw * 0.72f;
        Vec3 aimPoint = CombatRotationEngine.computeAimPoint(
                mc, target, CombatRotationEngine.RotationStyle.ORGANIC, true, false, aimSession
        );
        float curY = mc.player.getYRot();
        float curP = mc.player.getXRot();
        float[] step = CombatRotationEngine.computeStep(
                mc, target, CombatRotationEngine.RotationStyle.ORGANIC,
                curY, curP, aimPoint, maxYaw, maxPitch, dtTicks,
                true, lazyAim.isEnabled(), false, range.getValue(), aimSession
        );
        float sy = step[0];
        float sp = step[1];
        if (gcdFix.isEnabled()) {
            float[] q = RotationManager.getInstance().quantizeClientDelta(sy, sp, curP);
            sy = q[0];
            sp = q[1];
        }
        mc.player.setYRot(curY + sy);
        mc.player.setXRot(Mth.clamp(curP + sp, -90.0f, 90.0f));
    }

    private Player findTarget() {
        Player best = null;
        double bestDist = range.getValue();
        Vec3 eyes = mc.player.getEyePosition();

        for (Player player : mc.level.players()) {
            if (player == mc.player || !player.isAlive()) continue;
            if (FriendManager.getInstance().isFriend(player) || AntiBotModule.isBot(player)) continue;
            if (!mc.player.hasLineOfSight(player)) continue;

            double dist = getDistanceToBox(eyes, player.getBoundingBox());
            if (dist <= bestDist && isInFov(player, fov.getValue())) {
                best = player;
                bestDist = dist;
            }
        }
        return best;
    }

    private static double getDistanceToBox(Vec3 eyePos, AABB box) {
        double cx = Mth.clamp(eyePos.x, box.minX, box.maxX);
        double cy = Mth.clamp(eyePos.y, box.minY, box.maxY);
        double cz = Mth.clamp(eyePos.z, box.minZ, box.maxZ);
        return eyePos.distanceTo(new Vec3(cx, cy, cz));
    }

    private boolean isInFov(Entity entity, double maxFov) {
        Vec3 diff = entity.position().subtract(mc.player.position());
        double angle = Math.toDegrees(Math.atan2(diff.z, diff.x)) - 90.0;
        double deltaYaw = Mth.wrapDegrees(angle - mc.player.getYRot());
        return Math.abs(deltaYaw) <= maxFov / 2.0;
    }
}
