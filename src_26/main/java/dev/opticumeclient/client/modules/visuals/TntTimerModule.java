package dev.opticumeclient.client.modules.visuals;

import com.mojang.blaze3d.vertex.BufferBuilder;
import dev.opticumeclient.client.modules.Category;
import dev.opticumeclient.client.modules.Module;
import dev.opticumeclient.client.modules.visuals.targetesp.TargetEspFrame;
import dev.opticumeclient.client.render.ColorUtils;
import dev.opticumeclient.client.render.Render2DHelper;
import dev.opticumeclient.client.render.Render3DHelper;
import dev.opticumeclient.client.render.WorldPrimitiveRenderer;
import dev.opticumeclient.client.setting.BooleanSetting;
import dev.opticumeclient.client.setting.NumberSetting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.vehicle.minecart.MinecartTNT;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.Locale;

/**
 * Renders countdown timers and blast radius indicators above primed TNT and TNT minecarts.
 */
public class TntTimerModule extends Module {
    private static TntTimerModule instance;

    private final BooleanSetting showRadius = new BooleanSetting("Show Radius", "Отображать 3D-радиус взрыва (4.5м)", true);
    private final NumberSetting scale = new NumberSetting("Scale", "Масштаб 2D-таймера", 1.0, 0.6, 1.6, 0.05);

    public TntTimerModule() {
        super("TntTimer", "Обратный отсчёт до взрыва TNT и радиус поражения", Category.VISUALS);
        instance = this;
        addSetting(showRadius);
        addSetting(scale);
    }

    public static TntTimerModule getInstance() {
        return instance;
    }

    public void renderWorld(TargetEspFrame frame) {
        if (!isEnabled() || !showRadius.isEnabled() || mc.level == null || mc.player == null) return;

        Vec3 cam = frame.cameraPos();
        Matrix4f pose = frame.poseStack().last().pose();
        BufferBuilder lineBuf = WorldPrimitiveRenderer.beginLines();
        boolean any = false;
        int segments = 40;

        for (Entity entity : mc.level.entitiesForRendering()) {
            int fuse = fuseTicksOf(entity);
            if (fuse < 0) continue;
            float frac = Mth.clamp(fuse / 80.0f, 0.0f, 1.0f);
            int col = ColorUtils.interpolateColor(0xFFFF3838, 0xFF4ADE80, frac);

            Vec3 p = entity.position();
            double cx = p.x - cam.x;
            double cy = p.y + 0.05 - cam.y;
            double cz = p.z - cam.z;
            float radius = 4.5f;

            for (int i = 0; i < segments; i++) {
                double a1 = (Math.PI * 2.0 * i) / segments;
                double a2 = (Math.PI * 2.0 * (i + 1)) / segments;
                lineBuf.addVertex(pose, (float) (cx + Math.cos(a1) * radius), (float) cy, (float) (cz + Math.sin(a1) * radius)).setColor(col);
                lineBuf.addVertex(pose, (float) (cx + Math.cos(a2) * radius), (float) cy, (float) (cz + Math.sin(a2) * radius)).setColor(col);
            }
            any = true;
        }

        if (any) {
            WorldPrimitiveRenderer.drawLines(lineBuf, true);
        } else {
            WorldPrimitiveRenderer.discardQuietly(lineBuf);
        }
    }

    @Override
    public void onRender2D(GuiGraphicsExtractor context, float tickDelta) {
        if (!isEnabled() || mc.level == null || mc.player == null) return;

        float sc = scale.getFloatValue();
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();

        for (Entity entity : mc.level.entitiesForRendering()) {
            int fuse = fuseTicksOf(entity);
            if (fuse < 0) continue;

            Vec3 pos = Render3DHelper.getLerpedPos(entity, tickDelta).add(0.0, 1.25, 0.0);
            float[] screen = Render3DHelper.worldToScreen(pos);
            if (screen == null || screen[0] < 0 || screen[0] > sw || screen[1] < 0 || screen[1] > sh) continue;

            float sec = fuse / 20.0f;
            float frac = Mth.clamp(fuse / 80.0f, 0.0f, 1.0f);
            int col = ColorUtils.interpolateColor(0xFFFF3838, 0xFF4ADE80, frac);
            String label = String.format(Locale.US, "TNT %.1fs", sec);

            float tw = Render2DHelper.getTextWidth(label, 0.78f * sc);
            float pw = tw + 10.0f * sc;
            float ph = 14.0f * sc;
            float px = screen[0] - pw * 0.5f;
            float py = screen[1] - ph * 0.5f;

            Render2DHelper.drawRoundedRectFloat(context, px, py, pw, ph, 3.0f * sc, 0xCC0D0E16);
            Render2DHelper.drawRoundedOutlineFloat(context, px, py, pw, ph, 3.0f * sc, 0.8f, ColorUtils.withAlpha(col, 190));
            Render2DHelper.drawRoundedRectFloat(context, px + 2.0f * sc, py + ph - 2.2f * sc,
                    Math.max(1.0f, (pw - 4.0f * sc) * frac), 1.3f * sc, 0.6f, col);
            Render2DHelper.drawScaledText(context, label, px + 5.0f * sc, py + 2.8f * sc, col, 0.78f * sc, true);
        }
    }

    private static int fuseTicksOf(Entity entity) {
        if (entity instanceof PrimedTnt tnt) {
            return Math.max(0, tnt.getFuse());
        }
        if (entity instanceof MinecartTNT minecart) {
            int fuse = minecart.getFuse();
            return fuse >= 0 ? fuse : -1;
        }
        return -1;
    }
}
