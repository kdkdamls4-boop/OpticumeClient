package dev.opticumeclient.client.modules.visuals;

import dev.opticumeclient.client.modules.Category;
import dev.opticumeclient.client.modules.Module;
import dev.opticumeclient.client.render.ColorUtils;
import dev.opticumeclient.client.render.Render3DHelper;
import dev.opticumeclient.client.render.TextureAssets;
import dev.opticumeclient.client.render.ThemeManager;
import dev.opticumeclient.client.setting.BooleanSetting;
import dev.opticumeclient.client.setting.NumberSetting;
import com.mojang.blaze3d.vertex.PoseStack; 
import net.minecraft.world.phys.Vec3; 

import java.util.ArrayList;
import java.util.List;

public class TrailsModule extends Module {
    private final NumberSetting duration = new NumberSetting("Duration", "Длительность шлейфа (сек)", 1.35, 0.4, 3.0, 0.05);
    private final BooleanSetting sparks = new BooleanSetting("Sparks", "Светящиеся искры Firefly вдоль ленты", true);
    private final BooleanSetting firstPerson = new BooleanSetting("FirstPerson", "Отображать шлейф от 1-го лица", true);

    private final List<TrailNode> nodes = new ArrayList<>();

    public TrailsModule() {
        super("Trails", "Объемная 3D неоновая лента (Ribbon Trail) с искрами за игроком", Category.VISUALS);
        addSetting(duration);
        addSetting(sparks);
        addSetting(firstPerson);
        setEnabled(false);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.level == null) {
            nodes.clear();
            return;
        }

        long now = System.currentTimeMillis();
        long maxAgeMs = (long) (duration.getValue() * 1000.0);
        nodes.removeIf(n -> now - n.time > maxAgeMs);
    }

    @Override
    public void onRender3D(PoseStack matrices, float tickDelta) {
        if (mc.player == null || mc.level == null) return;
        if (!firstPerson.isEnabled() && mc.options.getCameraType() != null && mc.options.getCameraType().isFirstPerson()) {
            return;
        }

        long now = System.currentTimeMillis();
        Vec3 lerpedPos = Render3DHelper.getLerpedPos(mc.player, tickDelta);
        double playerHeight = mc.player.getBbHeight() * (mc.player.isShiftKeyDown() ? 0.84 : 0.92);

        // Record smooth frame-rate-independent ribbon node when moving
        if (nodes.isEmpty() || nodes.get(nodes.size() - 1).pos.distanceToSqr(lerpedPos) > 0.0025) {
            nodes.add(new TrailNode(lerpedPos, playerHeight, now));
            if (nodes.size() > 140) {
                nodes.remove(0);
            }
        }

        if (nodes.size() < 2) return;

        float maxAgeMs = (float) (duration.getValue() * 1000.0);
        boolean drawSparks = sparks.isEnabled();

        for (int i = 1; i < nodes.size(); i++) {
            TrailNode n0 = nodes.get(i - 1);
            TrailNode n1 = nodes.get(i);

            float life = 1.0f - (now - n1.time) / maxAgeMs;
            if (life <= 0.03f) continue;

            int themeColor = ThemeManager.getInstance().getAccentColor(i * 6);
            int edgeColor = ColorUtils.withAlphaFactor(themeColor, life * 0.92f);
            int midColor = ColorUtils.withAlphaFactor(themeColor, life * 0.52f);
            int fillColor = ColorUtils.withAlphaFactor(themeColor, life * 0.34f);

            double h0 = n0.height * (0.35 + 0.65 * life);
            double h1 = n1.height * (0.35 + 0.65 * life);

            Vec3 bot0 = n0.pos.add(0.0, 0.08, 0.0);
            Vec3 bot1 = n1.pos.add(0.0, 0.08, 0.0);
            Vec3 mid0 = n0.pos.add(0.0, h0 * 0.5, 0.0);
            Vec3 mid1 = n1.pos.add(0.0, h1 * 0.5, 0.0);
            Vec3 top0 = n0.pos.add(0.0, h0, 0.0);
            Vec3 top1 = n1.pos.add(0.0, h1, 0.0);

            // Top, middle, and bottom glowing ribbon rails
            Render3DHelper.queueLine3D(top0, top1, 2.2f, edgeColor, true);
            Render3DHelper.queueLine3D(mid0, mid1, 1.5f, midColor, false);
            Render3DHelper.queueLine3D(bot0, bot1, 2.0f, edgeColor, true);

            // Vertical gradient ribbon curtain segments
            Render3DHelper.queueLine3D(bot1, top1, 1.8f, fillColor, false);

            // Glowing firefly sparks along the trail
            if (drawSparks && i % 6 == 0) {
                double waveY = (Math.sin(now * 0.006 + i) * 0.5 + 0.5) * h1;
                Vec3 sparkPos = n1.pos.add(0.0, waveY, 0.0);
                Render3DHelper.queueBillboardSprite(
                        sparkPos,
                        0.18f * life,
                        TextureAssets.FIREFLY,
                        ColorUtils.withAlphaFactor(themeColor, life * 0.85f),
                        0.0f,
                        true
                );
            }
        }
    }

    private record TrailNode(Vec3 pos, double height, long time) {}
}
