package dev.opticumeclient.client.hud;

import dev.opticumeclient.client.friend.FriendManager;
import dev.opticumeclient.client.modules.Category;
import dev.opticumeclient.client.modules.Module;
import dev.opticumeclient.client.render.ColorUtils;
import dev.opticumeclient.client.render.Render2DHelper;
import dev.opticumeclient.client.render.Theme;
import dev.opticumeclient.client.render.ThemeManager;
import dev.opticumeclient.client.setting.NumberSetting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Draggable 2D Radar HUD widget showing nearby players and friends relative to facing yaw.
 */
public class RadarHud extends Module {
    private record RadarPoint(double dx, double dz, boolean isFriend) {}

    private final NumberSetting posX = new NumberSetting("PosX", "X позиция", 8.0, 0.0, 1920.0, 1.0);
    private final NumberSetting posY = new NumberSetting("PosY", "Y позиция", 36.0, 0.0, 1080.0, 1.0);
    private final NumberSetting range = new NumberSetting("Range", "Дистанция радара", 55.0, 20.0, 120.0, 5.0);

    private final HudManager.DraggableWidget dragWidget;
    private final List<RadarPoint> cachedRadarPoints = new ArrayList<>();

    public RadarHud() {
        super("Radar", "2D радар игроков с вращающимся конусом обзора", Category.HUD);
        addSetting(posX);
        addSetting(posY);
        addSetting(range);
        setEnabled(false);

        this.dragWidget = HudManager.getInstance().registerWidget(new HudManager.DraggableWidget(
                "2D Radar",
                posX.getFloatValue(),
                posY.getFloatValue(),
                86.0f,
                86.0f,
                this::isEnabled,
                (newX, newY) -> {
                    posX.setValue((double) Math.round(newX));
                    posY.setValue((double) Math.round(newY));
                }
        ));
    }

    @Override
    public void onTick() {
        cachedRadarPoints.clear();
        if (mc.level == null || mc.player == null) return;
        double px = mc.player.getX();
        double pz = mc.player.getZ();
        double maxRange = range.getValue();
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity == mc.player || !(entity instanceof Player p) || !p.isAlive()) continue;
            double dx = p.getX() - px;
            double dz = p.getZ() - pz;
            if (Math.hypot(dx, dz) <= maxRange) {
                cachedRadarPoints.add(new RadarPoint(dx, dz, FriendManager.getInstance().isFriend(p)));
            }
        }
    }

    @Override
    public void onRender2D(GuiGraphicsExtractor context, float tickDelta) {
        if (!isEnabled() || mc.player == null) return;

        Theme theme = ThemeManager.getInstance().getCurrentTheme();
        int c1 = theme.getPrimaryColor();
        int c2 = theme.getSecondaryColor();

        float size = 86.0f;
        dragWidget.syncExternalPosition(posX.getFloatValue(), posY.getFloatValue());
        dragWidget.setBounds(size, size);
        float x = dragWidget.getX();
        float y = dragWidget.getY();
        float cx = x + size * 0.5f;
        float cy = y + size * 0.5f;
        float radius = size * 0.5f - 6.0f;

        HudPanelRenderer.beginWidget("2D Radar");
        try {
            HudPanelRenderer.drawSurface(context, "2D Radar", x, y, size, size, 8.0f, 222, 42);

            Render2DHelper.drawRoundedOutline(context, cx - radius, cy - radius, radius * 2.0f, radius * 2.0f, radius, 1.0f, 0x26FFFFFF);
            Render2DHelper.drawRoundedOutline(context, cx - radius * 0.5f, cy - radius * 0.5f, radius, radius, radius * 0.5f, 1.0f, 0x1AFFFFFF);

            Render2DHelper.drawRect(context, cx - radius, cy, radius * 2.0f, 1.0f, 0x20FFFFFF);
            Render2DHelper.drawRect(context, cx, cy - radius, 1.0f, radius * 2.0f, 0x20FFFFFF);

            for (int step = 1; step <= 6; step++) {
                float t = step / 6.0f;
                float r = radius * 0.85f * t;
                float spread = r * 0.58f;
                int coneAlpha = (int) ((1.0f - t) * 42.0f);
                Render2DHelper.drawRect(context, cx - spread, cy - r, spread * 2.0f, 2.0f, ColorUtils.withAlpha(c1, coneAlpha));
            }

            double sweepAngle = (System.currentTimeMillis() % 2800L) / 2800.0 * Math.PI * 2.0;
            for (int rStep = 3; rStep < (int) radius; rStep += 3) {
                float sx = cx + (float) Math.cos(sweepAngle) * rStep;
                float sy = cy + (float) Math.sin(sweepAngle) * rStep;
                Render2DHelper.drawRect(context, sx, sy, 1.5f, 1.5f, ColorUtils.withAlpha(c2, 135));
            }

            if (!cachedRadarPoints.isEmpty()) {
                float yaw = mc.player.getYRot(tickDelta);
                double rad = Math.toRadians(yaw + 180.0);
                double cos = Math.cos(rad);
                double sin = Math.sin(rad);
                float maxRange = range.getFloatValue();

                for (RadarPoint pt : cachedRadarPoints) {
                    double rx = pt.dx() * cos - pt.dz() * sin;
                    double ry = pt.dx() * sin + pt.dz() * cos;

                    float px = cx + (float) (rx / maxRange) * (radius - 3.0f);
                    float py = cy + (float) (ry / maxRange) * (radius - 3.0f);

                    int dotCol = pt.isFriend() ? 0xFF00E676 : 0xFFFF2E54;
                    Render2DHelper.drawCircle(context, px, py, 2.8f, ColorUtils.withAlpha(dotCol, 90));
                    Render2DHelper.drawCircle(context, px, py, 1.8f, dotCol);
                }
            }

            Render2DHelper.drawCircle(context, cx, cy, 3.0f, c1);
            Render2DHelper.drawCircle(context, cx, cy, 1.5f, 0xFFFFFFFF);
            Render2DHelper.drawScaledText(context, "N", cx - 2.0f, y + 3.0f, c2, 0.65f, true);
        } finally {
            HudPanelRenderer.endWidget();
        }
    }
}
