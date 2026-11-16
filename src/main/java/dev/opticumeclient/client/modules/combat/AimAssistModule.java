package dev.opticumeclient.client.modules.combat;

import dev.opticumeclient.client.friend.FriendManager;
import dev.opticumeclient.client.modules.Category;
import dev.opticumeclient.client.modules.Module;
import dev.opticumeclient.client.setting.NumberSetting;
import net.minecraft.world.entity.Entity; 
import net.minecraft.world.entity.player.Player; 
import net.minecraft.util.Mth; 
import net.minecraft.world.phys.Vec3; 

public class AimAssistModule extends Module {
    private final NumberSetting range = new NumberSetting("Range", "Радиус захвата", 4.5, 2.0, 6.0, 0.1);
    private final NumberSetting fov = new NumberSetting("FOV", "Угол обзора", 60.0, 15.0, 120.0, 5.0);
    private final NumberSetting smooth = new NumberSetting("Smooth", "Сглаживание доводки", 6.0, 1.0, 15.0, 0.5);

    public AimAssistModule() {
        super("AimAssist", "Плавная легитная доводка прицела к цели", Category.COMBAT);
        addSetting(range);
        addSetting(fov);
        addSetting(smooth);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.level == null) return;

        Player target = findTarget();
        if (target != null) {
            Vec3 eyes = mc.player.getEyePosition();
            Vec3 targetPos = target.position().add(0, target.getBbHeight() * 0.75, 0);
            Vec3 diff = targetPos.subtract(eyes);

            double diffXZ = Math.sqrt(diff.x * diff.x + diff.z * diff.z);
            float targetYaw = (float) (Math.toDegrees(Math.atan2(diff.z, diff.x)) - 90.0);
            float targetPitch = (float) -Math.toDegrees(Math.atan2(diff.y, diffXZ));

            float deltaYaw = Mth.wrapDegrees(targetYaw - mc.player.getYRot());
            float deltaPitch = Mth.wrapDegrees(targetPitch - mc.player.getXRot());

            float factor = (float) Math.max(1.0, smooth.getValue());
            mc.player.setYRot(mc.player.getYRot() + deltaYaw / factor);
            mc.player.setXRot(mc.player.getXRot() + deltaPitch / factor);
        }
    }

    private Player findTarget() {
        Player best = null;
        double bestDist = range.getValue();

        for (Player player : mc.level.players()) {
            if (player == mc.player || !player.isAlive()) continue;
            if (FriendManager.getInstance().isFriend(player)) continue;

            double dist = mc.player.distanceTo(player);
            if (dist <= bestDist && isInFov(player, fov.getValue())) {
                best = player;
                bestDist = dist;
            }
        }
        return best;
    }

    private boolean isInFov(Entity entity, double maxFov) {
        Vec3 diff = entity.position().subtract(mc.player.position());
        double angle = Math.toDegrees(Math.atan2(diff.z, diff.x)) - 90.0;
        double deltaYaw = Mth.wrapDegrees(angle - mc.player.getYRot());
        return Math.abs(deltaYaw) <= maxFov / 2.0;
    }
}
