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
import dev.opticumeclient.client.setting.NumberSetting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.Locale;

/**
 * Shows remaining lifetime and 3D radius circles for active AreaEffectCloud (lingering potion / breath) clouds.
 */
public class PotsTimerModule extends Module {
    private static PotsTimerModule instance;

    private final BooleanSetting showCircle = new BooleanSetting("3D Radius", "3D-круг текущего радиуса облака зелья", true);
    private final NumberSetting scale = new NumberSetting("Scale", "Масштаб 2D-таймера", 1.0, 0.6, 1.6, 0.05);

    public PotsTimerModule() {
        super("PotsTimer", "Таймер и 3D-радиус активных облаков зелий (AreaEffectCloud)", Category.VISUALS);
        instance = this;
        addSetting(showCircle);
        addSetting(scale);
    }

    public static PotsTimerModule getInstance() {
        return instance;
    }

    public void renderWorld(TargetEspFrame frame) {
        if (!isEnabled() || !showCircle.isEnabled() || mc.level == null) return;

        Vec3 cam = frame.cameraPos();
        Matrix4f pose = frame.poseStack().last().pose();
        BufferBuilder lineBuf = WorldPrimitiveRenderer.beginLines();
        boolean any = false;
        int segments = 36;

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof AreaEffectCloud cloud)) continue;
            float r = cloud.getRadius();
            if (r <= 0.1f) continue;

            int col = cloudColor(cloud);
            Vec3 p = cloud.position();
            double cx = p.x - cam.x;
            double cy = p.y + 0.08 - cam.y;
            double cz = p.z - cam.z;

            for (int i = 0; i < segments; i++) {
                double a1 = (Math.PI * 2.0 * i) / segments;
                double a2 = (Math.PI * 2.0 * (i + 1)) / segments;
                lineBuf.addVertex(pose, (float) (cx + Math.cos(a1) * r), (float) cy, (float) (cz + Math.sin(a1) * r)).setColor(col);
                lineBuf.addVertex(pose, (float) (cx + Math.cos(a2) * r), (float) cy, (float) (cz + Math.sin(a2) * r)).setColor(col);
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
        if (!isEnabled() || mc.level == null) return;

        float sc = scale.getFloatValue();
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof AreaEffectCloud cloud)) continue;
            int total = cloud.getWaitTime() + cloud.getDuration();
            if (total <= 0 || cloud.getDuration() == AreaEffectCloud.INFINITE_DURATION) continue;
            int leftTicks = Math.max(0, total - cloud.tickCount);
            if (leftTicks <= 0) continue;

            Vec3 pos = Render3DHelper.getLerpedPos(cloud, tickDelta).add(0.0, 0.9, 0.0);
            float[] screen = Render3DHelper.worldToScreen(pos);
            if (screen == null || screen[0] < 0 || screen[0] > sw || screen[1] < 0 || screen[1] > sh) continue;

            int col = cloudColor(cloud);
            float frac = Mth.clamp(leftTicks / (float) Math.max(1, total), 0.0f, 1.0f);
            String label = String.format(Locale.US, "Pot %.1fs (%.1fm)", leftTicks / 20.0f, cloud.getRadius());
            float tw = Render2DHelper.getTextWidth(label, 0.72f * sc);
            float pw = tw + 10.0f * sc;
            float ph = 13.0f * sc;
            float px = screen[0] - pw * 0.5f;
            float py = screen[1] - ph * 0.5f;

            Render2DHelper.drawRoundedRectFloat(context, px, py, pw, ph, 3.0f * sc, 0xCC0D0E16);
            Render2DHelper.drawRoundedOutlineFloat(context, px, py, pw, ph, 3.0f * sc, 0.7f, ColorUtils.withAlpha(col, 175));
            Render2DHelper.drawRoundedRectFloat(context, px + 2.0f * sc, py + ph - 2.0f * sc,
                    Math.max(1.0f, (pw - 4.0f * sc) * frac), 1.2f * sc, 0.6f, col);
            Render2DHelper.drawScaledText(context, label, px + 5.0f * sc, py + 2.5f * sc, col, 0.72f * sc, true);
        }
    }

    private static int cloudColor(AreaEffectCloud cloud) {
        if (cloud.getParticle() instanceof ColorParticleOption cpo) {
            int r = Mth.clamp(Math.round(cpo.getRed() * 255.0f), 0, 255);
            int g = Mth.clamp(Math.round(cpo.getGreen() * 255.0f), 0, 255);
            int b = Mth.clamp(Math.round(cpo.getBlue() * 255.0f), 0, 255);
            return 0xFF000000 | (r << 16) | (g << 8) | b;
        }
        return ThemeManager.getInstance().getAccentColor(0);
    }
}
