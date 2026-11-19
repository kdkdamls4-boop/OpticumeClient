package dev.opticumeclient.client.modules.visuals;

import com.mojang.blaze3d.vertex.BufferBuilder;
import dev.opticumeclient.client.modules.Category;
import dev.opticumeclient.client.modules.Module;
import dev.opticumeclient.client.modules.visuals.targetesp.TargetEspFrame;
import dev.opticumeclient.client.render.ColorUtils;
import dev.opticumeclient.client.render.Render3DHelper;
import dev.opticumeclient.client.render.TextureAssets;
import dev.opticumeclient.client.render.ThemeManager;
import dev.opticumeclient.client.render.WorldPrimitiveRenderer;
import dev.opticumeclient.client.setting.BooleanSetting;
import dev.opticumeclient.client.setting.ModeSetting;
import dev.opticumeclient.client.setting.NumberSetting;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Unified movement trail + walk-particle module (Ribbon curtain, Desire walk particles, or Both).
 */
public class TrailsModule extends Module {
    private static TrailsModule instance;

    private final ModeSetting mode = new ModeSetting("Mode", "Режим шлейфа за игроком", "Ribbon", "Ribbon", "Walk Particles", "Both");
    private final NumberSetting duration = new NumberSetting("Duration", "Длительность шлейфа/частиц (сек)", 1.35, 0.4, 3.0, 0.05);
    private final BooleanSetting sparks = new BooleanSetting("Sparks", "Светящиеся искры вдоль ленты", true)
            .visibleWhen(() -> !mode.is("Walk Particles"));
    private final NumberSetting walkCount = new NumberSetting("Particle Count", "Плотность частиц при ходьбе", 2.0, 1.0, 6.0, 1.0)
            .visibleWhen(() -> !mode.is("Ribbon"));
    private final NumberSetting walkScale = new NumberSetting("Particle Size", "Размер частиц при ходьбе", 0.24, 0.10, 0.50, 0.02)
            .visibleWhen(() -> !mode.is("Ribbon"));
    private final BooleanSetting firstPerson = new BooleanSetting("FirstPerson", "Отображать шлейф от 1-го лица", true);

    private final List<TrailNode> nodes = new ArrayList<>();
    private final List<WalkParticle> walkParticles = new ArrayList<>();

    public TrailsModule() {
        super("Trails", "3D-лента и светящиеся частицы за игроком при ходьбе", Category.VISUALS);
        instance = this;
        addSetting(mode);
        addSetting(duration);
        addSetting(sparks);
        addSetting(walkCount);
        addSetting(walkScale);
        addSetting(firstPerson);
        setEnabled(false);
    }

    public static TrailsModule getInstance() {
        return instance;
    }

    @Override
    public String getDisplaySuffix() {
        return mode.getMode();
    }

    @Override
    public void onDisable() {
        nodes.clear();
        walkParticles.clear();
        super.onDisable();
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.level == null) {
            nodes.clear();
            walkParticles.clear();
            return;
        }

        long now = System.currentTimeMillis();
        long maxAgeMs = (long) (duration.getValue() * 1000.0);
        nodes.removeIf(n -> now - n.time > maxAgeMs);

        Iterator<WalkParticle> it = walkParticles.iterator();
        while (it.hasNext()) {
            WalkParticle wp = it.next();
            wp.prevPos = wp.pos;
            wp.pos = wp.pos.add(wp.vel);
            wp.vel = wp.vel.scale(0.95);
            if (now - wp.spawnMs > maxAgeMs) {
                it.remove();
            }
        }

        boolean doWalk = !mode.is("Ribbon");
        if (doWalk && (mc.player.getX() != mc.player.xo || mc.player.getY() != mc.player.yo || mc.player.getZ() != mc.player.zo)) {
            int count = walkCount.getIntValue();
            ThreadLocalRandom rng = ThreadLocalRandom.current();
            for (int i = 0; i < count; i++) {
                double ox = rng.nextDouble(-0.35, 0.35);
                double oy = rng.nextDouble(0.05, Math.max(0.25, mc.player.getBbHeight() * 0.85));
                double oz = rng.nextDouble(-0.35, 0.35);
                Vec3 pos = new Vec3(mc.player.getX() + ox, mc.player.getY() + oy, mc.player.getZ() + oz);
                Vec3 vel = new Vec3(rng.nextDouble(-0.02, 0.02), rng.nextDouble(0.004, 0.018), rng.nextDouble(-0.02, 0.02));
                walkParticles.add(new WalkParticle(pos, vel, now, rng.nextInt(360)));
            }
            while (walkParticles.size() > 180) {
                walkParticles.remove(0);
            }
        }
    }

    public void renderWorld(TargetEspFrame frame) {
        if (!isEnabled() || mc.player == null || mc.level == null) return;
        if (!firstPerson.isEnabled() && mc.options.getCameraType() != null && mc.options.getCameraType().isFirstPerson()) {
            return;
        }

        long now = System.currentTimeMillis();
        float tickDelta = frame.partialTicks();
        Vec3 cam = frame.cameraPos();
        Matrix4f pose = frame.poseStack().last().pose();
        float maxAgeMs = Math.max(200.0f, (float) (duration.getValue() * 1000.0));

        boolean doRibbon = !mode.is("Walk Particles");
        boolean doWalk = !mode.is("Ribbon");

        if (doRibbon) {
            Vec3 lerpedPos = Render3DHelper.getLerpedPos(mc.player, tickDelta);
            double playerHeight = mc.player.getBbHeight() * (mc.player.isShiftKeyDown() ? 0.84 : 0.92);
            if (nodes.isEmpty() || nodes.get(nodes.size() - 1).pos.distanceToSqr(lerpedPos) > 0.0025) {
                nodes.add(new TrailNode(lerpedPos, playerHeight, now));
                if (nodes.size() > 160) {
                    nodes.remove(0);
                }
            }

            if (nodes.size() >= 2) {
                BufferBuilder quadBuf = WorldPrimitiveRenderer.beginQuads();
                BufferBuilder lineBuf = WorldPrimitiveRenderer.beginLines();
                boolean any = false;

                for (int i = 1; i < nodes.size(); i++) {
                    TrailNode n0 = nodes.get(i - 1);
                    TrailNode n1 = nodes.get(i);
                    float life = 1.0f - (now - n1.time) / maxAgeMs;
                    if (life <= 0.03f) continue;

                    int themeCol = ThemeManager.getInstance().getAccentColor(i * 6);
                    int fillTop = ColorUtils.withAlphaFactor(themeCol, life * 0.24f);
                    int fillBot = ColorUtils.withAlphaFactor(themeCol, life * 0.42f);
                    int edgeCol = ColorUtils.withAlphaFactor(themeCol, life * 0.92f);

                    double h0 = n0.height * (0.35 + 0.65 * life);
                    double h1 = n1.height * (0.35 + 0.65 * life);

                    float bx0 = (float) (n0.pos.x - cam.x), by0 = (float) (n0.pos.y + 0.06 - cam.y), bz0 = (float) (n0.pos.z - cam.z);
                    float bx1 = (float) (n1.pos.x - cam.x), by1 = (float) (n1.pos.y + 0.06 - cam.y), bz1 = (float) (n1.pos.z - cam.z);
                    float tx0 = bx0, ty0 = (float) (n0.pos.y + h0 - cam.y), tz0 = bz0;
                    float tx1 = bx1, ty1 = (float) (n1.pos.y + h1 - cam.y), tz1 = bz1;

                    quadBuf.addVertex(pose, bx0, by0, bz0).setColor(fillBot);
                    quadBuf.addVertex(pose, bx1, by1, bz1).setColor(fillBot);
                    quadBuf.addVertex(pose, tx1, ty1, tz1).setColor(fillTop);
                    quadBuf.addVertex(pose, tx0, ty0, tz0).setColor(fillTop);

                    lineBuf.addVertex(pose, tx0, ty0, tz0).setColor(edgeCol);
                    lineBuf.addVertex(pose, tx1, ty1, tz1).setColor(edgeCol);
                    lineBuf.addVertex(pose, bx0, by0, bz0).setColor(edgeCol);
                    lineBuf.addVertex(pose, bx1, by1, bz1).setColor(edgeCol);
                    any = true;
                }

                if (any) {
                    WorldPrimitiveRenderer.drawQuads(quadBuf, true, true);
                    WorldPrimitiveRenderer.drawLines(lineBuf, true);
                } else {
                    WorldPrimitiveRenderer.discardQuietly(quadBuf);
                    WorldPrimitiveRenderer.discardQuietly(lineBuf);
                }
            }
        }

        if ((sparks.isEnabled() && doRibbon) || (doWalk && !walkParticles.isEmpty())) {
            if (mc.gameRenderer == null || mc.gameRenderer.mainCamera() == null) return;
            Quaternionf orient = new Quaternionf(mc.gameRenderer.mainCamera().rotation());
            BufferBuilder spriteBuf = WorldPrimitiveRenderer.beginTexturedQuads();
            BufferBuilder glowBuf = WorldPrimitiveRenderer.beginTexturedQuads();
            boolean any = false;

            if (doRibbon && sparks.isEnabled()) {
                for (int i = 1; i < nodes.size(); i += 6) {
                    TrailNode n1 = nodes.get(i);
                    float life = 1.0f - (now - n1.time) / maxAgeMs;
                    if (life <= 0.04f) continue;
                    double h1 = n1.height * (0.35 + 0.65 * life);
                    double waveY = (Math.sin(now * 0.006 + i) * 0.5 + 0.5) * h1;
                    int col = ColorUtils.withAlphaFactor(ThemeManager.getInstance().getAccentColor(i * 6), life * 0.9f);
                    WorldPrimitiveRenderer.glowSprite(spriteBuf, pose, n1.pos.x - cam.x, n1.pos.y + waveY - cam.y, n1.pos.z - cam.z, orient, 0.22f * life, col);
                    any = true;
                }
            }

            if (doWalk) {
                float pScale = walkScale.getFloatValue();
                for (WalkParticle wp : walkParticles) {
                    float life = 1.0f - (now - wp.spawnMs) / maxAgeMs;
                    if (life <= 0.03f) continue;
                    double wx = wp.prevPos.x + (wp.pos.x - wp.prevPos.x) * tickDelta - cam.x;
                    double wy = wp.prevPos.y + (wp.pos.y - wp.prevPos.y) * tickDelta - cam.y;
                    double wz = wp.prevPos.z + (wp.pos.z - wp.prevPos.z) * tickDelta - cam.z;
                    int col = ColorUtils.withAlphaFactor(ThemeManager.getInstance().getAccentColor(wp.colorSeed), life * 0.9f);
                    float sz = pScale * (0.35f + 0.65f * life);
                    WorldPrimitiveRenderer.glowSprite(spriteBuf, pose, wx, wy, wz, orient, sz, col);
                    WorldPrimitiveRenderer.glowSprite(glowBuf, pose, wx, wy, wz, orient, sz * 1.9f, ColorUtils.withAlphaFactor(col, 0.42f));
                    any = true;
                }
            }

            if (any) {
                WorldPrimitiveRenderer.drawTexturedQuads(spriteBuf, TextureAssets.DESIRE_PARTICLE_HEX_ORBIZ, true, true);
                WorldPrimitiveRenderer.drawTexturedQuads(glowBuf, TextureAssets.DESIRE_PARTICLE_GLOW, true, true);
            } else {
                WorldPrimitiveRenderer.discardQuietly(spriteBuf);
                WorldPrimitiveRenderer.discardQuietly(glowBuf);
            }
        }
    }

    private record TrailNode(Vec3 pos, double height, long time) {}

    private static final class WalkParticle {
        Vec3 pos;
        Vec3 prevPos;
        Vec3 vel;
        final long spawnMs;
        final int colorSeed;

        WalkParticle(Vec3 pos, Vec3 vel, long spawnMs, int colorSeed) {
            this.pos = pos;
            this.prevPos = pos;
            this.vel = vel;
            this.spawnMs = spawnMs;
            this.colorSeed = colorSeed;
        }
    }
}
