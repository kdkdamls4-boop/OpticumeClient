package dev.opticumeclient.client.modules.visuals;

import com.mojang.blaze3d.vertex.BufferBuilder;
import dev.opticumeclient.client.modules.Category;
import dev.opticumeclient.client.modules.Module;
import dev.opticumeclient.client.modules.visuals.targetesp.TargetEspFrame;
import dev.opticumeclient.client.render.ColorUtils;
import dev.opticumeclient.client.render.TextureAssets;
import dev.opticumeclient.client.render.ThemeManager;
import dev.opticumeclient.client.render.WorldPrimitiveRenderer;
import dev.opticumeclient.client.setting.ColorSetting;
import dev.opticumeclient.client.setting.ModeSetting;
import dev.opticumeclient.client.setting.NumberSetting;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Upgraded Totem Pop effect: renders a 3D rising humanoid hologram (translucent quads + glowing edges)
 * and additive glowing burst particles through RenderPearl WorldPrimitiveRenderer.
 */
public class PopChamsModule extends Module {
    private static PopChamsModule INSTANCE;

    private final ModeSetting mode = new ModeSetting("Mode", "Стиль визуализации тотема", "Both", "Hologram", "Particles", "Both");
    private final ModeSetting colorMode = new ModeSetting("ColorMode", "Источник цвета", "Theme", "Theme", "Totem", "Rainbow", "Custom");
    private final ColorSetting customColor = (ColorSetting) new ColorSetting("CustomColor", "Пользовательский цвет", 0xCCFF2A85)
            .visibleWhen(() -> colorMode.is("Custom"));
    private final NumberSetting duration = new NumberSetting("Duration", "Время жизни эффекта в тиках", 35.0, 15.0, 60.0, 5.0);
    private final NumberSetting riseSpeed = new NumberSetting("RiseSpeed", "Скорость всплытия голограммы", 0.035, 0.01, 0.10, 0.005);
    private final NumberSetting particles = new NumberSetting("Particles", "Число разлетающихся частиц", 28.0, 5.0, 60.0, 5.0);
    private final NumberSetting size = new NumberSetting("Size", "Размер светящихся частиц", 0.24, 0.08, 0.50, 0.02);

    private static final class PopParticle {
        Vec3 pos;
        Vec3 prevPos;
        Vec3 vel;
        float sizeMul;
        int colorVariant;

        PopParticle(Vec3 pos, Vec3 vel, float sizeMul, int colorVariant) {
            this.pos = pos;
            this.prevPos = pos;
            this.vel = vel;
            this.sizeMul = sizeMul;
            this.colorVariant = colorVariant;
        }
    }

    private static final class TotemPopGhost {
        final Vec3 origin;
        final float yawDeg;
        final float scaleH;
        final int maxAge;
        final List<PopParticle> particles;
        int age = 0;

        TotemPopGhost(Vec3 origin, float yawDeg, float scaleH, int maxAge, List<PopParticle> particles) {
            this.origin = origin;
            this.yawDeg = yawDeg;
            this.scaleH = scaleH;
            this.maxAge = maxAge;
            this.particles = particles;
        }
    }

    private final List<TotemPopGhost> activeGhosts = new ArrayList<>();

    public PopChamsModule() {
        super("PopChams", "3D-голограмма игрока и неоновые частицы при срабатывании Тотема Бессмертия", Category.VISUALS);
        addSetting(mode);
        addSetting(colorMode);
        addSetting(customColor);
        addSetting(duration);
        addSetting(riseSpeed);
        addSetting(particles);
        addSetting(size);
        INSTANCE = this;
        setEnabled(false);
    }

    public static PopChamsModule getInstance() {
        return INSTANCE;
    }

    @Override
    public String getDisplaySuffix() {
        return mode.getMode();
    }

    @Override
    public void onDisable() {
        activeGhosts.clear();
        super.onDisable();
    }

    public void onTotemPop(Entity entity) {
        if (!isEnabled() || entity == null) return;

        Vec3 base = entity.position();
        Vec3 center = base.add(0.0, entity.getBbHeight() * 0.55, 0.0);
        int count = particles.getIntValue();
        List<PopParticle> partList = new ArrayList<>(count);
        ThreadLocalRandom rng = ThreadLocalRandom.current();

        for (int i = 0; i < count; i++) {
            double theta = rng.nextDouble(Math.PI * 2.0);
            double horiz = rng.nextDouble(0.05, 0.18);
            double vy = rng.nextDouble(0.04, 0.22);
            Vec3 pPos = center.add((rng.nextDouble() - 0.5) * 0.35, (rng.nextDouble() - 0.5) * 0.55, (rng.nextDouble() - 0.5) * 0.35);
            Vec3 vel = new Vec3(Math.cos(theta) * horiz, vy, Math.sin(theta) * horiz);
            partList.add(new PopParticle(pPos, vel, rng.nextFloat(0.75f, 1.35f), i));
        }

        float scaleH = Mth.clamp(entity.getBbHeight() / 1.8f, 0.65f, 1.3f);
        activeGhosts.add(new TotemPopGhost(base, entity.getYRot(), scaleH, duration.getIntValue(), partList));
        if (activeGhosts.size() > 12) {
            activeGhosts.remove(0);
        }
    }

    @Override
    public void onTick() {
        if (activeGhosts.isEmpty()) return;

        Iterator<TotemPopGhost> it = activeGhosts.iterator();
        while (it.hasNext()) {
            TotemPopGhost ghost = it.next();
            ghost.age++;

            for (PopParticle p : ghost.particles) {
                p.prevPos = p.pos;
                p.pos = p.pos.add(p.vel);
                p.vel = new Vec3(p.vel.x * 0.92, p.vel.y * 0.92 - 0.004, p.vel.z * 0.92);
            }

            if (ghost.age >= ghost.maxAge) {
                it.remove();
            }
        }
    }

    public void renderWorld(TargetEspFrame frame) {
        if (!isEnabled() || mc.level == null || activeGhosts.isEmpty()) return;

        String m = mode.getMode();
        boolean doHolo = "Hologram".equalsIgnoreCase(m) || "Hologram Box".equalsIgnoreCase(m) || "Both".equalsIgnoreCase(m);
        boolean doParticles = "Particles".equalsIgnoreCase(m) || "Both".equalsIgnoreCase(m);

        Vec3 cam = frame.cameraPos();
        Matrix4f pose = frame.poseStack().last().pose();
        float tickDelta = frame.partialTicks();
        float risePerTick = riseSpeed.getFloatValue();
        float partSize = size.getFloatValue();

        if (doHolo) {
            // Sequential BufferPool passes: finish quads before opening lines
            BufferBuilder quadBuf = WorldPrimitiveRenderer.beginQuads();
            if (emitAllGhosts(quadBuf, pose, cam, tickDelta, risePerTick, true)) {
                WorldPrimitiveRenderer.drawQuads(quadBuf, true, true);
            } else {
                WorldPrimitiveRenderer.discardQuietly(quadBuf);
            }

            BufferBuilder lineBuf = WorldPrimitiveRenderer.beginLines();
            if (emitAllGhosts(lineBuf, pose, cam, tickDelta, risePerTick, false)) {
                WorldPrimitiveRenderer.drawLines(lineBuf, true);
            } else {
                WorldPrimitiveRenderer.discardQuietly(lineBuf);
            }
        }

        if (doParticles && mc.gameRenderer != null && mc.gameRenderer.mainCamera() != null) {
            Quaternionf orient = new Quaternionf(mc.gameRenderer.mainCamera().rotation());

            BufferBuilder starBuf = WorldPrimitiveRenderer.beginTexturedQuads();
            if (emitPopSprites(starBuf, pose, cam, tickDelta, orient, partSize, 1.0f, 1.0f)) {
                WorldPrimitiveRenderer.drawTexturedQuads(starBuf, TextureAssets.DESIRE_PARTICLE_HEX_SPARK, true, true);
            } else {
                WorldPrimitiveRenderer.discardQuietly(starBuf);
            }

            BufferBuilder glowBuf = WorldPrimitiveRenderer.beginTexturedQuads();
            if (emitPopSprites(glowBuf, pose, cam, tickDelta, orient, partSize, 2.1f, 0.45f)) {
                WorldPrimitiveRenderer.drawTexturedQuads(glowBuf, TextureAssets.DESIRE_PARTICLE_GLOW, true, true);
            } else {
                WorldPrimitiveRenderer.discardQuietly(glowBuf);
            }
        }
    }

    private boolean emitAllGhosts(BufferBuilder buf, Matrix4f pose, Vec3 cam, float tickDelta, float risePerTick, boolean quads) {
        boolean any = false;
        for (TotemPopGhost ghost : activeGhosts) {
            float progress = Mth.clamp((ghost.age + tickDelta) / ghost.maxAge, 0.0f, 1.0f);
            float fade = 1.0f - progress;
            if (fade <= 0.02f) continue;

            int baseColor = resolveColor(0);
            int col = ColorUtils.withAlphaFactor(baseColor, fade * (quads ? 0.28f : 0.85f));
            float yOff = (ghost.age + tickDelta) * risePerTick;
            float expand = 1.0f + progress * 0.16f;
            float s = ghost.scaleH * expand;
            double cx = ghost.origin.x - cam.x;
            double cy = ghost.origin.y + yOff - cam.y;
            double cz = ghost.origin.z - cam.z;
            double yawRad = Math.toRadians(-ghost.yawDeg);
            float cos = (float) Math.cos(yawRad);
            float sin = (float) Math.sin(yawRad);

            emitPartBox(buf, quads, pose, cx, cy, cz, cos, sin, s, 0.0f, 1.44f, 0.0f, 0.22f, 0.44f, 0.22f, col); // Head
            emitPartBox(buf, quads, pose, cx, cy, cz, cos, sin, s, 0.0f, 0.72f, 0.0f, 0.24f, 0.70f, 0.13f, col); // Torso
            emitPartBox(buf, quads, pose, cx, cy, cz, cos, sin, s, -0.35f, 0.72f, 0.0f, 0.11f, 0.68f, 0.11f, col); // Right Arm
            emitPartBox(buf, quads, pose, cx, cy, cz, cos, sin, s, 0.35f, 0.72f, 0.0f, 0.11f, 0.68f, 0.11f, col); // Left Arm
            emitPartBox(buf, quads, pose, cx, cy, cz, cos, sin, s, -0.12f, 0.0f, 0.0f, 0.11f, 0.70f, 0.11f, col); // Right Leg
            emitPartBox(buf, quads, pose, cx, cy, cz, cos, sin, s, 0.12f, 0.0f, 0.0f, 0.11f, 0.70f, 0.11f, col); // Left Leg
            any = true;
        }
        return any;
    }

    private boolean emitPopSprites(BufferBuilder buf, Matrix4f pose, Vec3 cam, float tickDelta, Quaternionf orient,
                                   float partSize, float sizeMul, float alphaMul) {
        boolean any = false;
        for (TotemPopGhost ghost : activeGhosts) {
            float progress = Mth.clamp((ghost.age + tickDelta) / ghost.maxAge, 0.0f, 1.0f);
            float fade = 1.0f - progress;
            if (fade <= 0.02f) continue;

            for (PopParticle p : ghost.particles) {
                double px = Mth.lerp(tickDelta, p.prevPos.x, p.pos.x) - cam.x;
                double py = Mth.lerp(tickDelta, p.prevPos.y, p.pos.y) - cam.y;
                double pz = Mth.lerp(tickDelta, p.prevPos.z, p.pos.z) - cam.z;
                int pCol = ColorUtils.withAlphaFactor(resolveColor(p.colorVariant * 22), fade * alphaMul);
                float sz = partSize * p.sizeMul * (0.45f + 0.55f * fade) * sizeMul;
                WorldPrimitiveRenderer.glowSprite(buf, pose, px, py, pz, orient, sz, pCol);
                any = true;
            }
        }
        return any;
    }

    private int resolveColor(int indexOffset) {
        return switch (colorMode.getMode()) {
            case "Rainbow" -> ColorUtils.getRainbow(4.0f, 0.85f, 1.0f, indexOffset * 40L);
            case "Totem" -> ((indexOffset / 22) % 3 == 0) ? 0xFFFACC15 : 0xFF22C55E;
            case "Custom" -> customColor.getColor();
            default -> ThemeManager.getInstance().getAccentColor(indexOffset);
        };
    }

    private static void emitPartBox(
            BufferBuilder buf, boolean quads, Matrix4f pose,
            double bx, double by, double bz, float cos, float sin, float scale,
            float lx, float ly, float lz, float halfW, float h, float halfD,
            int col
    ) {
        float xMin = (lx - halfW) * scale, xMax = (lx + halfW) * scale;
        float zMin = (lz - halfD) * scale, zMax = (lz + halfD) * scale;
        float y0 = (float) (by + ly * scale);
        float y1 = (float) (by + (ly + h) * scale);

        float rx0 = (float) (bx + xMin * cos - zMin * sin), rz0 = (float) (bz + xMin * sin + zMin * cos);
        float rx1 = (float) (bx + xMax * cos - zMin * sin), rz1 = (float) (bz + xMax * sin + zMin * cos);
        float rx2 = (float) (bx + xMax * cos - zMax * sin), rz2 = (float) (bz + xMax * sin + zMax * cos);
        float rx3 = (float) (bx + xMin * cos - zMax * sin), rz3 = (float) (bz + xMin * sin + zMax * cos);

        if (quads) {
            addQuad(buf, pose, rx0, y0, rz0, rx1, y0, rz1, rx2, y0, rz2, rx3, y0, rz3, col);
            addQuad(buf, pose, rx0, y1, rz0, rx1, y1, rz1, rx2, y1, rz2, rx3, y1, rz3, col);
            addQuad(buf, pose, rx0, y0, rz0, rx1, y0, rz1, rx1, y1, rz1, rx0, y1, rz0, col);
            addQuad(buf, pose, rx1, y0, rz1, rx2, y0, rz2, rx2, y1, rz2, rx1, y1, rz1, col);
            addQuad(buf, pose, rx2, y0, rz2, rx3, y0, rz3, rx3, y1, rz3, rx2, y1, rz2, col);
            addQuad(buf, pose, rx3, y0, rz3, rx0, y0, rz0, rx0, y1, rz0, rx3, y1, rz3, col);
        } else {
            addLine(buf, pose, rx0, y0, rz0, rx1, y0, rz1, col);
            addLine(buf, pose, rx1, y0, rz1, rx2, y0, rz2, col);
            addLine(buf, pose, rx2, y0, rz2, rx3, y0, rz3, col);
            addLine(buf, pose, rx3, y0, rz3, rx0, y0, rz0, col);

            addLine(buf, pose, rx0, y1, rz0, rx1, y1, rz1, col);
            addLine(buf, pose, rx1, y1, rz1, rx2, y1, rz2, col);
            addLine(buf, pose, rx2, y1, rz2, rx3, y1, rz3, col);
            addLine(buf, pose, rx3, y1, rz3, rx0, y1, rz0, col);

            addLine(buf, pose, rx0, y0, rz0, rx0, y1, rz0, col);
            addLine(buf, pose, rx1, y0, rz1, rx1, y1, rz1, col);
            addLine(buf, pose, rx2, y0, rz2, rx2, y1, rz2, col);
            addLine(buf, pose, rx3, y0, rz3, rx3, y1, rz3, col);
        }
    }

    private static void addQuad(BufferBuilder b, Matrix4f p,
                                float x0, float y0, float z0, float x1, float y1, float z1,
                                float x2, float y2, float z2, float x3, float y3, float z3, int col) {
        b.addVertex(p, x0, y0, z0).setColor(col);
        b.addVertex(p, x1, y1, z1).setColor(col);
        b.addVertex(p, x2, y2, z2).setColor(col);
        b.addVertex(p, x3, y3, z3).setColor(col);
    }

    private static void addLine(BufferBuilder b, Matrix4f p, float x0, float y0, float z0, float x1, float y1, float z1, int col) {
        b.addVertex(p, x0, y0, z0).setColor(col);
        b.addVertex(p, x1, y1, z1).setColor(col);
    }
}
