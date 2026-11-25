package dev.opticumeclient.client.modules.visuals;

import com.mojang.blaze3d.vertex.BufferBuilder;
import dev.opticumeclient.client.modules.Category;
import dev.opticumeclient.client.modules.Module;
import dev.opticumeclient.client.modules.visuals.targetesp.TargetEspFrame;
import dev.opticumeclient.client.render.ColorUtils;
import dev.opticumeclient.client.render.Render2DHelper;
import dev.opticumeclient.client.render.Render3DHelper;
import dev.opticumeclient.client.render.ThemeManager;
import dev.opticumeclient.client.render.WorldPrimitiveRenderer;
import dev.opticumeclient.client.setting.BooleanSetting;
import dev.opticumeclient.client.setting.ColorSetting;
import dev.opticumeclient.client.setting.NumberSetting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Highlights airborne firework rockets with a 3D box, glowing flight trail, and distance label.
 */
public class FireworkESPModule extends Module {
    private static FireworkESPModule instance;

    private final BooleanSetting showTrail = new BooleanSetting("Trail", "Линия траектории полёта фейерверка", true);
    private final BooleanSetting showLabel = new BooleanSetting("Distance Label", "Подпись дистанции до фейерверка", true);
    private final NumberSetting trailLength = new NumberSetting("Trail Length", "Длина шлейфа траектории", 18.0, 6.0, 40.0, 2.0)
            .visibleWhen(showTrail::isEnabled);
    private final ColorSetting color = new ColorSetting("Color", "Цвет подсветки (THM = цвет темы)", 0xFF38BDF8, true);

    private final Map<Integer, Deque<Vec3>> rocketTrails = new HashMap<>();

    public FireworkESPModule() {
        super("FireworkESP", "Подсветка и траектория летящих фейерверков", Category.VISUALS);
        instance = this;
        addSetting(showTrail);
        addSetting(showLabel);
        addSetting(trailLength);
        addSetting(color);
    }

    public static FireworkESPModule getInstance() {
        return instance;
    }

    @Override
    public void onDisable() {
        super.onDisable();
        rocketTrails.clear();
    }

    @Override
    public void onTick() {
        if (mc.level == null || !showTrail.isEnabled()) {
            rocketTrails.clear();
            return;
        }
        Set<Integer> alive = new HashSet<>();
        int maxLen = trailLength.getIntValue();
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof FireworkRocketEntity rocket) {
                alive.add(rocket.getId());
                Deque<Vec3> q = rocketTrails.computeIfAbsent(rocket.getId(), k -> new ArrayDeque<>());
                Vec3 pos = rocket.position();
                if (q.isEmpty() || q.peekLast().distanceToSqr(pos) > 0.02) {
                    q.addLast(pos);
                    while (q.size() > maxLen) {
                        q.removeFirst();
                    }
                }
            }
        }
        rocketTrails.keySet().retainAll(alive);
    }

    public void renderWorld(TargetEspFrame frame) {
        if (!isEnabled() || mc.level == null) return;

        Vec3 cam = frame.cameraPos();
        Matrix4f pose = frame.poseStack().last().pose();
        int baseCol = color.isThemeSync() ? ThemeManager.getInstance().getAccentColor(0) : color.getColor();
        BufferBuilder lineBuf = WorldPrimitiveRenderer.beginLines();
        boolean any = false;

        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof FireworkRocketEntity rocket)) continue;
            Vec3 p = Render3DHelper.getLerpedPos(rocket, frame.partialTicks());
            float x1 = (float) (p.x - 0.18 - cam.x);
            float y1 = (float) (p.y - 0.18 - cam.y);
            float z1 = (float) (p.z - 0.18 - cam.z);
            BlockESPModule.emitBoxLines(lineBuf, pose, x1, y1, z1, x1 + 0.36f, y1 + 0.36f, z1 + 0.36f, baseCol);
            any = true;

            if (showTrail.isEnabled()) {
                Deque<Vec3> q = rocketTrails.get(rocket.getId());
                if (q != null && q.size() >= 2) {
                    Iterator<Vec3> it = q.iterator();
                    Vec3 prev = it.next();
                    int idx = 0;
                    int count = q.size();
                    while (it.hasNext()) {
                        Vec3 next = it.next();
                        float alpha = (idx + 1.0f) / count;
                        int segCol = ColorUtils.withAlphaFactor(baseCol, alpha * 0.85f);
                        lineBuf.addVertex(pose, (float) (prev.x - cam.x), (float) (prev.y - cam.y), (float) (prev.z - cam.z)).setColor(segCol);
                        lineBuf.addVertex(pose, (float) (next.x - cam.x), (float) (next.y - cam.y), (float) (next.z - cam.z)).setColor(segCol);
                        prev = next;
                        idx++;
                    }
                }
            }
        }

        if (any) {
            WorldPrimitiveRenderer.drawLines(lineBuf, false);
        } else {
            WorldPrimitiveRenderer.discardQuietly(lineBuf);
        }
    }

    @Override
    public void onRender2D(GuiGraphicsExtractor context, float tickDelta) {
        if (!isEnabled() || !showLabel.isEnabled() || mc.level == null || mc.player == null) return;

        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        int baseCol = color.isThemeSync() ? ThemeManager.getInstance().getAccentColor(0) : color.getColor();

        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof FireworkRocketEntity rocket)) continue;
            Vec3 p = Render3DHelper.getLerpedPos(rocket, tickDelta).add(0.0, 0.35, 0.0);
            float[] screen = Render3DHelper.worldToScreen(p);
            if (screen == null || screen[0] < 0 || screen[0] > sw || screen[1] < 0 || screen[1] > sh) continue;

            String label = String.format(Locale.US, "Firework %.0fm", mc.player.distanceTo(rocket));
            float tw = Render2DHelper.getTextWidth(label, 0.70f);
            float pw = tw + 8.0f;
            float ph = 12.0f;
            Render2DHelper.drawRoundedRectFloat(context, screen[0] - pw * 0.5f, screen[1] - ph * 0.5f, pw, ph, 2.5f, 0xB80B0C15);
            Render2DHelper.drawScaledText(context, label, screen[0] - tw * 0.5f, screen[1] - 3.5f, baseCol, 0.70f, true);
        }
    }
}
