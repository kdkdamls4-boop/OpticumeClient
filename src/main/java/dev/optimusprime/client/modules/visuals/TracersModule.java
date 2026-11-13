package dev.optimusprime.client.modules.visuals;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.optimusprime.client.friend.FriendManager;
import dev.optimusprime.client.modules.Category;
import dev.optimusprime.client.modules.Module;
import dev.optimusprime.client.render.ColorUtils;
import dev.optimusprime.client.render.Render3DHelper;
import dev.optimusprime.client.render.ThemeManager;
import dev.optimusprime.client.setting.BooleanSetting;
import dev.optimusprime.client.setting.ModeSetting;
import dev.optimusprime.client.setting.NumberSetting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public class TracersModule extends Module {
    private static TracersModule INSTANCE;

    private final ModeSetting origin = new ModeSetting("Origin", "Точка исхода линии", "Crosshair", "Crosshair", "Bottom", "3D Eyes");
    private final BooleanSetting players = new BooleanSetting("Players", "Трассеры к игрокам", true);
    private final BooleanSetting friends = new BooleanSetting("Friends", "Трассеры к друзьям", true);
    private final BooleanSetting mobs = new BooleanSetting("Mobs", "Трассеры к монстрам", false);
    private final NumberSetting thickness = new NumberSetting("Thickness", "Толщина линии", 1.5, 0.5, 3.5, 0.2);
    private final BooleanSetting glow = new BooleanSetting("Glow", "Неоновый ореол", true);
    private final BooleanSetting distanceColor = new BooleanSetting("DistanceColor", "Градиент по расстоянию", false);

    public TracersModule() {
        super("Tracers", "3D линии трассировки от прицела или глаз к сущностям", Category.VISUALS);
        addSetting(origin);
        addSetting(players);
        addSetting(friends);
        addSetting(mobs);
        addSetting(thickness);
        addSetting(glow);
        addSetting(distanceColor);
        INSTANCE = this;
        setEnabled(false);
    }

    public static TracersModule getInstance() {
        return INSTANCE;
    }

    @Override
    public String getDisplaySuffix() {
        return origin.getMode();
    }

    private boolean shouldTrack(Entity entity) {
        if (entity == null || entity == mc.player || !entity.isAlive() || entity.isRemoved()) {
            return false;
        }
        if (entity instanceof Player player) {
            if (!players.isEnabled()) return false;
            boolean isFriend = FriendManager.getInstance().isFriend(player);
            return isFriend ? friends.isEnabled() : true;
        }
        if (mobs.isEnabled() && entity instanceof Enemy) {
            return true;
        }
        return false;
    }

    private int getTracerColor(Entity entity) {
        if (entity instanceof Player player && FriendManager.getInstance().isFriend(player)) {
            return 0xFF22C55E;
        }
        if (distanceColor.isEnabled()) {
            float dist = mc.player.distanceTo(entity);
            float t = Math.clamp(dist / 48.0f, 0.0f, 1.0f);
            return ColorUtils.interpolateColor(0xFFFF2E54, 0xFF38BDF8, t);
        }
        return ThemeManager.getInstance().getAccentColor(0);
    }

    @Override
    public void onRender3D(PoseStack matrices, float tickDelta) {
        if (!isEnabled() || mc.level == null || mc.player == null) return;
        if (!origin.is("3D Eyes")) return;

        Vec3 camPos = (mc.gameRenderer != null && mc.gameRenderer.mainCamera() != null)
                ? mc.gameRenderer.mainCamera().position()
                : mc.player.getEyePosition(tickDelta);

        float thick = thickness.getFloatValue();
        boolean doGlow = glow.isEnabled();

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!shouldTrack(entity)) continue;
            Vec3 targetPos = Render3DHelper.getLerpedPos(entity, tickDelta).add(0.0, entity.getBbHeight() * 0.5, 0.0);
            int color = getTracerColor(entity);
            Render3DHelper.queueLine3D(camPos, targetPos, thick, color, doGlow);
        }
    }

    @Override
    public void onRender2D(GuiGraphicsExtractor context, float tickDelta) {
        if (!isEnabled() || mc.level == null || mc.player == null) return;
        if (origin.is("3D Eyes")) return;

        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();

        float ox = sw * 0.5f;
        float oy = origin.is("Bottom") ? (float) sh : (sh * 0.5f);

        float thick = thickness.getFloatValue();
        boolean doGlow = glow.isEnabled();

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!shouldTrack(entity)) continue;
            Vec3 targetPos = Render3DHelper.getLerpedPos(entity, tickDelta).add(0.0, entity.getBbHeight() * 0.5, 0.0);
            float[] p = Render3DHelper.worldToScreen(targetPos);
            if (p != null) {
                int color = getTracerColor(entity);
                Render3DHelper.drawSmoothLine(context, ox, oy, p[0], p[1], thick, color, doGlow);
            }
        }
    }
}
