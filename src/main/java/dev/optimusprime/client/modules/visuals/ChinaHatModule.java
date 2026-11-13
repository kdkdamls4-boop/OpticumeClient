package dev.optimusprime.client.modules.visuals;

import dev.optimusprime.client.friend.FriendManager;
import dev.optimusprime.client.modules.Category;
import dev.optimusprime.client.modules.Module;
import dev.optimusprime.client.render.ColorUtils;
import dev.optimusprime.client.render.Render3DHelper;
import dev.optimusprime.client.render.ThemeManager;
import dev.optimusprime.client.setting.BooleanSetting;
import dev.optimusprime.client.setting.NumberSetting;
import com.mojang.blaze3d.vertex.PoseStack; 
import net.minecraft.world.entity.player.Player; 
import net.minecraft.util.Mth; 
import net.minecraft.world.phys.Vec3; 

public class ChinaHatModule extends Module {
    private final BooleanSetting showInFirstPerson = new BooleanSetting("FirstPerson", "Отображать шляпу от 1-го лица", false);
    private final NumberSetting radius = new NumberSetting("Radius", "Радиус полей шляпы", 0.62, 0.35, 1.1, 0.02);
    private final NumberSetting height = new NumberSetting("Height", "Высота конуса шляпы", 0.34, 0.15, 0.70, 0.02);
    private final BooleanSetting rotateWithHead = new BooleanSetting("RotateWithHead", "Наклонять шляпу вслед за головой", true);
    private final BooleanSetting showOnFriends = new BooleanSetting("Friends", "Рендерить также на друзьях", true);

    public ChinaHatModule() {
        super("ChinaHat", "Неоновая азиатская 3D-шляпа с градиентным переливом над головой", Category.VISUALS);
        addSetting(showInFirstPerson);
        addSetting(radius);
        addSetting(height);
        addSetting(rotateWithHead);
        addSetting(showOnFriends);
        setEnabled(false);
    }

    @Override
    public void onRender3D(PoseStack matrices, float tickDelta) {
        if (mc.player == null || mc.level == null) return;

        // Render on local player (respect first-person setting so it doesn't block screen)
        boolean isFirstPerson = mc.options.getCameraType() != null && mc.options.getCameraType().isFirstPerson();
        if (!isFirstPerson || showInFirstPerson.isEnabled()) {
            renderHatForPlayer(mc.player, tickDelta);
        }

        // Optionally render on friends
        if (showOnFriends.isEnabled()) {
            for (Player player : mc.level.players()) {
                if (player == mc.player || !player.isAlive()) continue;
                if (FriendManager.getInstance().isFriend(player)) {
                    renderHatForPlayer(player, tickDelta);
                }
            }
        }
    }

    private void renderHatForPlayer(Player player, float tickDelta) {
        Vec3 lerpedPos = Render3DHelper.getLerpedPos(player, tickDelta);
        double sneakOffset = player.isShiftKeyDown() ? -0.18 : 0.0;
        double baseY = lerpedPos.y + player.getBbHeight() + 0.06 + sneakOffset;

        float r = radius.getFloatValue();
        float h = height.getFloatValue();

        // Head pitch & yaw tilt if rotateWithHead is enabled
        double tiltX = 0.0;
        double tiltZ = 0.0;
        if (rotateWithHead.isEnabled()) {
            float yaw = Mth.rotLerp(tickDelta, player.yHeadRotO, player.yHeadRot);
            float pitch = Mth.lerp(tickDelta, player.xRotO, player.getXRot());
            double yawRad = Math.toRadians(yaw + 90.0f);
            double pitchOffset = Math.sin(Math.toRadians(pitch)) * 0.14;
            tiltX = -Math.cos(yawRad) * pitchOffset;
            tiltZ = -Math.sin(yawRad) * pitchOffset;
        }

        Vec3 centerBase = new Vec3(lerpedPos.x, baseY, lerpedPos.z);
        Vec3 apex = new Vec3(lerpedPos.x + tiltX, baseY + h, lerpedPos.z + tiltZ);

        int segments = 24;
        int concentricRings = 2;

        // 1. Radial ribs from apex to rim: every 2nd vertex (12 ribs) with glow = false
        for (int i = 0; i < segments; i += 2) {
            double angle = (i * Math.PI * 2.0) / segments;
            double px = centerBase.x + Math.cos(angle) * r;
            double pz = centerBase.z + Math.sin(angle) * r;
            double py = centerBase.y + (Math.cos(angle) * tiltX + Math.sin(angle) * tiltZ) * 0.35;

            Vec3 rimPoint = new Vec3(px, py, pz);
            int color = ColorUtils.withAlpha(ThemeManager.getInstance().getAccentColor(i * 15), 165);
            Render3DHelper.queueLine3D(apex, rimPoint, 1.35f, color, false);
        }

        // 2. Concentric rings with 24 segments (glow = true ONLY on outer rim ring)
        for (int ring = 1; ring <= concentricRings; ring++) {
            float ringFrac = (float) ring / concentricRings;
            float ringRad = r * ringFrac;
            boolean isOuterBrim = (ring == concentricRings);
            float thickness = isOuterBrim ? 2.2f : 1.3f;
            int alpha = isOuterBrim ? 245 : 135;

            for (int i = 0; i < segments; i++) {
                double a1 = (i * Math.PI * 2.0) / segments;
                double a2 = ((i + 1) * Math.PI * 2.0) / segments;

                Vec3 p1 = pointOnCone(centerBase, apex, ringFrac, ringRad, a1, tiltX, tiltZ);
                Vec3 p2 = pointOnCone(centerBase, apex, ringFrac, ringRad, a2, tiltX, tiltZ);

                int c = ColorUtils.withAlpha(ThemeManager.getInstance().getAccentColor(i * 15 + ring * 30), alpha);
                Render3DHelper.queueLine3D(p1, p2, thickness, c, isOuterBrim);
            }
        }
    }

    private Vec3 pointOnCone(Vec3 base, Vec3 apex, float fracFromApex, float ringRad, double angle, double tiltX, double tiltZ) {
        double cx = Mth.lerp(fracFromApex, apex.x, base.x);
        double cy = Mth.lerp(fracFromApex, apex.y, base.y);
        double cz = Mth.lerp(fracFromApex, apex.z, base.z);
        double tiltY = (Math.cos(angle) * tiltX + Math.sin(angle) * tiltZ) * 0.35 * fracFromApex;
        return new Vec3(
                cx + Math.cos(angle) * ringRad,
                cy + tiltY,
                cz + Math.sin(angle) * ringRad
        );
    }
}
