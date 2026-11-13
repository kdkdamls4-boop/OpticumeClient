package dev.optimusprime.client.modules.visuals;

import dev.optimusprime.client.modules.Category;
import dev.optimusprime.client.modules.Module;
import dev.optimusprime.client.render.ColorUtils;
import dev.optimusprime.client.render.Render3DHelper;
import dev.optimusprime.client.render.TextureAssets;
import dev.optimusprime.client.render.ThemeManager;
import dev.optimusprime.client.setting.ModeSetting;
import dev.optimusprime.client.setting.NumberSetting;
import com.mojang.blaze3d.vertex.PoseStack; 
import net.minecraft.world.phys.Vec3; 

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class JumpCirclesModule extends Module {
    private final ModeSetting style = new ModeSetting("Style", "Стиль неонового круга прыжка", "Neon Portal", "Neon Portal", "Rune Ring", "Double Wave");
    private final NumberSetting maxRadius = new NumberSetting("MaxRadius", "Максимальный радиус раскрытия", 1.8, 0.8, 3.5, 0.1);
    private final NumberSetting duration = new NumberSetting("Duration", "Время жизни круга (сек)", 2.2, 0.8, 4.5, 0.1);
    private final NumberSetting spinSpeed = new NumberSetting("SpinSpeed", "Скорость вращения", 1.5, 0.2, 4.0, 0.1);

    private final List<JumpCircle> circles = new ArrayList<>();
    private boolean wasOnGround = true;

    public JumpCirclesModule() {
        super("JumpCircles", "Расходящиеся 3D неоновые порталы под ногами при прыжке", Category.VISUALS);
        addSetting(style);
        addSetting(maxRadius);
        addSetting(duration);
        addSetting(spinSpeed);
        setEnabled(false);
    }

    @Override
    public String getDisplaySuffix() {
        return style.getMode();
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.level == null) {
            circles.clear();
            wasOnGround = true;
            return;
        }

        boolean onGround = mc.player.onGround();
        if (wasOnGround && !onGround && mc.player.getDeltaMovement().y > 0.01) {
            Vec3 spawnPos = new Vec3(mc.player.getX(), mc.player.getY() + 0.03, mc.player.getZ());
            circles.add(new JumpCircle(spawnPos, System.currentTimeMillis()));
            if (circles.size() > 24) {
                circles.remove(0);
            }
        }
        wasOnGround = onGround;

        long maxLifeMs = (long) (duration.getValue() * 1000.0);
        long now = System.currentTimeMillis();
        circles.removeIf(c -> now - c.spawnTime > maxLifeMs);
    }

    @Override
    public void onRender3D(PoseStack matrices, float tickDelta) {
        if (mc.player == null || mc.level == null || circles.isEmpty()) return;

        long now = System.currentTimeMillis();
        float maxLifeMs = (float) (duration.getValue() * 1000.0);
        float targetMaxRad = maxRadius.getFloatValue();
        float spin = spinSpeed.getFloatValue();
        String currentStyle = style.getMode();

        Iterator<JumpCircle> it = circles.iterator();
        while (it.hasNext()) {
            JumpCircle c = it.next();
            float t = (now - c.spawnTime) / maxLifeMs;
            if (t < 0.0f || t >= 1.0f) {
                it.remove();
                continue;
            }

            // Quartic Ease-Out: 1 - (1 - t)^4
            float inv = 1.0f - t;
            float easeOut = 1.0f - (inv * inv * inv * inv);
            float rad = Math.max(0.08f, targetMaxRad * easeOut);

            // Bell-shaped opacity curve: fast fade-in, smooth fade-out
            float alphaFactor;
            if (t < 0.12f) {
                alphaFactor = t / 0.12f;
            } else {
                float fadeT = (t - 0.12f) / 0.88f;
                alphaFactor = (1.0f - fadeT) * (1.0f - fadeT);
            }
            if (alphaFactor <= 0.02f) continue;

            double rot = t * Math.PI * 2.0 * spin;
            renderCircle(c.pos, rad, rot, alphaFactor, currentStyle);
        }
    }

    private void renderCircle(Vec3 pos, float rad, double rot, float alphaFactor, String currentStyle) {
        int segments = 40;
        double innerRatio = "Rune Ring".equals(currentStyle) ? 0.68 : 0.78;
        double innerRad = rad * innerRatio;

        for (int i = 0; i < segments; i++) {
            double a1 = rot + (i * Math.PI * 2.0) / segments;
            double a2 = rot + ((i + 1) * Math.PI * 2.0) / segments;

            int baseColor = ThemeManager.getInstance().getAccentColor(i * 9);
            int outerColor = ColorUtils.withAlphaFactor(baseColor, alphaFactor * 0.95f);
            int innerColor = ColorUtils.withAlphaFactor(baseColor, alphaFactor * 0.38f);

            Vec3 out1 = new Vec3(pos.x + Math.cos(a1) * rad, pos.y, pos.z + Math.sin(a1) * rad);
            Vec3 out2 = new Vec3(pos.x + Math.cos(a2) * rad, pos.y, pos.z + Math.sin(a2) * rad);
            Vec3 in1 = new Vec3(pos.x + Math.cos(a1) * innerRad, pos.y, pos.z + Math.sin(a1) * innerRad);
            Vec3 in2 = new Vec3(pos.x + Math.cos(a2) * innerRad, pos.y, pos.z + Math.sin(a2) * innerRad);

            // Outer glowing perimeter
            Render3DHelper.queueLine3D(out1, out2, 2.2f, outerColor, true);
            // Inner soft halo ring
            Render3DHelper.queueLine3D(in1, in2, 1.4f, innerColor, false);

            // Radial aura bridge lines
            if ("Rune Ring".equals(currentStyle) && i % 2 == 0) {
                Render3DHelper.queueLine3D(in1, out1, 1.3f, innerColor, false);
            } else if ("Double Wave".equals(currentStyle)) {
                double secondWaveRad = rad * 0.52;
                Vec3 w1 = new Vec3(pos.x + Math.cos(-a1) * secondWaveRad, pos.y, pos.z + Math.sin(-a1) * secondWaveRad);
                Vec3 w2 = new Vec3(pos.x + Math.cos(-a2) * secondWaveRad, pos.y, pos.z + Math.sin(-a2) * secondWaveRad);
                Render3DHelper.queueLine3D(w1, w2, 1.8f, ColorUtils.withAlphaFactor(baseColor, alphaFactor * 0.65f), true);
            }
        }

        // 8 rotating runic rays & firefly nodes around the circumference
        int runePoints = 8;
        for (int r = 0; r < runePoints; r++) {
            double angle = rot * 1.35 + (r * Math.PI * 2.0) / runePoints;
            Vec3 runePos = new Vec3(
                    pos.x + Math.cos(angle) * rad,
                    pos.y + 0.02,
                    pos.z + Math.sin(angle) * rad
            );
            Vec3 innerSpoke = new Vec3(
                    pos.x + Math.cos(angle) * (rad * 0.58),
                    pos.y + 0.01,
                    pos.z + Math.sin(angle) * (rad * 0.58)
            );
            int runeColor = ColorUtils.withAlphaFactor(ThemeManager.getInstance().getAccentColor(r * 45), alphaFactor);
            Render3DHelper.queueLine3D(innerSpoke, runePos, 1.5f, runeColor, true);
            Render3DHelper.queueBillboardSprite(runePos, 0.22f, TextureAssets.FIREFLY, runeColor, (float) Math.toDegrees(angle), true);
        }
    }

    private record JumpCircle(Vec3 pos, long spawnTime) {}
}
