package dev.opticumeclient.client.modules.visuals;

import com.mojang.blaze3d.vertex.BufferBuilder;
import dev.opticumeclient.client.modules.Category;
import dev.opticumeclient.client.modules.Module;
import dev.opticumeclient.client.modules.visuals.targetesp.TargetEspFrame;
import dev.opticumeclient.client.render.WorldPrimitiveRenderer;
import dev.opticumeclient.client.render.ThemeManager;
import dev.opticumeclient.client.setting.BooleanSetting;
import dev.opticumeclient.client.setting.ModeSetting;
import dev.opticumeclient.client.setting.NumberSetting;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.awt.Color;

/**
 * Ported from Desire BlockOverlayModule:
 * Smoothly tracks targeted block and renders animated Plasma, Crystal, or Fill faces + optional Theme Outline.
 */
public class BlockOverlayModule extends Module {
    private static final double MAX_DISTANCE_SQ = 36.0;
    private static final double INFLATE = 0.003;
    private static final int PLASMA_GRID = 6;

    private static BlockOverlayModule instance;

    private final ModeSetting mode = new ModeSetting("Mode", "Режим заливки граней блока", "Plasma", "Plasma", "Crystal", "Fill");
    private final BooleanSetting outline = new BooleanSetting("Theme Outline", "Контур граней в цвете темы", true);
    private final NumberSetting opacity = new NumberSetting("Opacity", "Прозрачность заливки", 0.72, 0.1, 1.0, 0.05);
    private final NumberSetting plasmaSpeed = new NumberSetting("Plasma Speed", "Скорость переливания плазмы", 1.15, 0.1, 5.0, 0.1)
            .visibleWhen(() -> mode.is("Plasma"));
    private final NumberSetting plasmaScale = new NumberSetting("Plasma Scale", "Частота волн плазмы", 2.4, 0.5, 5.0, 0.1)
            .visibleWhen(() -> mode.is("Plasma"));
    private final NumberSetting outlineOpacity = new NumberSetting("Outline Opacity", "Прозрачность контура (%)", 70.0, 5.0, 100.0, 1.0)
            .visibleWhen(outline::isEnabled);
    private final BooleanSetting smooth = new BooleanSetting("Smooth", "Плавная анимация перехода на новый блок", true);
    private final NumberSetting smoothSpeed = new NumberSetting("Smooth Speed", "Скорость плавного перехода", 10.0, 2.0, 25.0, 0.5)
            .visibleWhen(smooth::isEnabled);

    private AABB current;
    private long lastNanos = -1L;

    public BlockOverlayModule() {
        super("BlockOverlay", "Подсветка выбранного блока (Plasma / Crystal / Fill)", Category.VISUALS);
        instance = this;
        addSetting(mode);
        addSetting(outline);
        addSetting(opacity);
        addSetting(plasmaSpeed);
        addSetting(plasmaScale);
        addSetting(outlineOpacity);
        addSetting(smooth);
        addSetting(smoothSpeed);
    }

    public static BlockOverlayModule getInstance() {
        return instance;
    }

    public static boolean hidesVanillaOutline() {
        BlockOverlayModule m = instance;
        if (m == null || !m.isEnabled() || m.mc.level == null || m.mc.hitResult == null) {
            return false;
        }
        return m.mc.hitResult.getType() == HitResult.Type.BLOCK;
    }

    @Override
    public void onDisable() {
        super.onDisable();
        reset();
    }

    private void reset() {
        current = null;
        lastNanos = -1L;
    }

    public void renderWorld(TargetEspFrame frame) {
        if (!isEnabled() || mc.level == null || mc.player == null || !(mc.hitResult instanceof BlockHitResult hit)
                || hit.getType() != HitResult.Type.BLOCK || hit.isInside()) {
            reset();
            return;
        }

        Vec3 cam = frame.cameraPos();
        BlockPos pos = hit.getBlockPos();
        if (mc.player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > MAX_DISTANCE_SQ) {
            reset();
            return;
        }

        AABB target = blockBox(pos);
        if (target.contains(cam)) {
            reset();
            return;
        }

        follow(target);
        if (current == null) return;

        AABB box = current.inflate(INFLATE);
        Matrix4f pose = frame.poseStack().last().pose();
        float time = (System.currentTimeMillis() % 100000L) / 1000.0f;

        Color accent = new Color(ThemeManager.getInstance().getAccentColor(0), true);
        Color hot = new Color(
                Math.min(255, accent.getRed() + 70),
                Math.min(255, accent.getGreen() + 55),
                Math.min(255, accent.getBlue() + 40)
        );

        BufferBuilder fillBuf = WorldPrimitiveRenderer.beginQuads();
        boolean hasQuads = emitFill(pose, fillBuf, box, cam, accent, hot, time);
        if (hasQuads) {
            WorldPrimitiveRenderer.drawQuads(fillBuf, false, true);
        } else {
            WorldPrimitiveRenderer.discardQuietly(fillBuf);
        }

        if (outline.isEnabled()) {
            BufferBuilder lineBuf = WorldPrimitiveRenderer.beginLines();
            boolean hasLines = emitOutline(pose, lineBuf, box, cam, accent, hot);
            if (hasLines) {
                WorldPrimitiveRenderer.drawLines(lineBuf, true);
            } else {
                WorldPrimitiveRenderer.discardQuietly(lineBuf);
            }
        }
    }

    private AABB blockBox(BlockPos pos) {
        BlockState state = mc.level.getBlockState(pos);
        AABB bounds = state.getShape(mc.level, pos).bounds();
        boolean degenerate = bounds.hasNaN() || (bounds.minX >= bounds.maxX - 1.0E-7
                && bounds.minY >= bounds.maxY - 1.0E-7 && bounds.minZ >= bounds.maxZ - 1.0E-7);
        return degenerate ? new AABB(pos) : bounds.move(pos.getX(), pos.getY(), pos.getZ());
    }

    private void follow(AABB target) {
        long now = System.nanoTime();
        if (!smooth.isEnabled() || current == null || lastNanos < 0L) {
            current = target;
        } else {
            double dt = Math.max(0.0, (now - lastNanos) / 1.0E9);
            double t = 1.0 - Math.exp(-smoothSpeed.getFloatValue() * dt);
            current = new AABB(
                    Mth.lerp(t, current.minX, target.minX), Mth.lerp(t, current.minY, target.minY),
                    Mth.lerp(t, current.minZ, target.minZ), Mth.lerp(t, current.maxX, target.maxX),
                    Mth.lerp(t, current.maxY, target.maxY), Mth.lerp(t, current.maxZ, target.maxZ));
        }
        lastNanos = now;
    }

    private boolean emitFill(Matrix4f m, BufferBuilder b, AABB box, Vec3 cam, Color accent, Color hot, float time) {
        double x1 = box.minX, y1 = box.minY, z1 = box.minZ;
        double x2 = box.maxX, y2 = box.maxY, z2 = box.maxZ;
        boolean any = false;
        if (cam.y < y1) { face(m, b, cam, accent, hot, time, x1, y1, z1, x2 - x1, 0, 0, 0, 0, z2 - z1); any = true; }
        if (cam.y > y2) { face(m, b, cam, accent, hot, time, x1, y2, z1, x2 - x1, 0, 0, 0, 0, z2 - z1); any = true; }
        if (cam.z < z1) { face(m, b, cam, accent, hot, time, x1, y1, z1, x2 - x1, 0, 0, 0, y2 - y1, 0); any = true; }
        if (cam.z > z2) { face(m, b, cam, accent, hot, time, x1, y1, z2, x2 - x1, 0, 0, 0, y2 - y1, 0); any = true; }
        if (cam.x < x1) { face(m, b, cam, accent, hot, time, x1, y1, z1, 0, 0, z2 - z1, 0, y2 - y1, 0); any = true; }
        if (cam.x > x2) { face(m, b, cam, accent, hot, time, x2, y1, z1, 0, 0, z2 - z1, 0, y2 - y1, 0); any = true; }
        return any;
    }

    private void face(Matrix4f m, BufferBuilder b, Vec3 cam, Color accent, Color hot, float time,
                      double ox, double oy, double oz,
                      double ux, double uy, double uz,
                      double vx, double vy, double vz) {
        int n = mode.is("Plasma") ? PLASMA_GRID : 1;
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                double a0 = (double) i / n, a1 = (double) (i + 1) / n;
                double b0 = (double) j / n, b1 = (double) (j + 1) / n;
                vertex(m, b, cam, ox + ux * a0 + vx * b0, oy + uy * a0 + vy * b0, oz + uz * a0 + vz * b0, accent, hot, time);
                vertex(m, b, cam, ox + ux * a1 + vx * b0, oy + uy * a1 + vy * b0, oz + uz * a1 + vz * b0, accent, hot, time);
                vertex(m, b, cam, ox + ux * a1 + vx * b1, oy + uy * a1 + vy * b1, oz + uz * a1 + vz * b1, accent, hot, time);
                vertex(m, b, cam, ox + ux * a0 + vx * b1, oy + uy * a0 + vy * b1, oz + uz * a0 + vz * b1, accent, hot, time);
            }
        }
    }

    private void vertex(Matrix4f m, BufferBuilder b, Vec3 cam, double x, double y, double z,
                        Color accent, Color hot, float time) {
        b.addVertex(m, (float) (x - cam.x), (float) (y - cam.y), (float) (z - cam.z))
                .setColor(fillColor(x, y, z, accent, hot, time));
    }

    private int fillColor(double x, double y, double z, Color accent, Color hot, float time) {
        float alpha = opacity.getFloatValue();
        switch (mode.getMode()) {
            case "Plasma" -> {
                float t = time * plasmaSpeed.getFloatValue();
                float k = plasmaScale.getFloatValue() * 3.0f;
                float v = (Mth.sin((float) (x * k) + t)
                        + Mth.sin((float) (y * k * 1.3) - t * 0.7f)
                        + Mth.sin((float) (z * k * 0.9) + t * 1.1f)
                        + Mth.sin((float) ((x + y + z) * k * 0.6) - t * 0.5f)) / 4.0f;
                float p = Mth.clamp((0.5f + 0.5f * v - 0.15f) / 0.8f, 0.0f, 1.0f);
                p = p * p * (3.0f - 2.0f * p);
                float glow = p * p * p * 0.35f;
                return pack(
                        mix(accent.getRed(), hot.getRed(), p) + hot.getRed() * glow,
                        mix(accent.getGreen(), hot.getGreen(), p) + hot.getGreen() * glow,
                        mix(accent.getBlue(), hot.getBlue(), p) + hot.getBlue() * glow,
                        alpha
                );
            }
            case "Crystal" -> {
                float pulse = 0.5f + 0.5f * Mth.sin(time * 2.4f);
                float f = 0.75f + pulse * 0.35f + pulse * pulse * 0.25f;
                return pack(
                        accent.getRed() * f,
                        accent.getGreen() * f,
                        accent.getBlue() * f,
                        alpha * (0.85f + pulse * 0.15f)
                );
            }
            default -> {
                return pack(accent.getRed(), accent.getGreen(), accent.getBlue(), alpha);
            }
        }
    }

    private boolean emitOutline(Matrix4f m, BufferBuilder b, AABB box, Vec3 cam, Color accent, Color hot) {
        Color mixed = new Color(
                Mth.clamp((int) (mix(accent.getRed(), hot.getRed(), 0.65f) * 1.15f), 0, 255),
                Mth.clamp((int) (mix(accent.getGreen(), hot.getGreen(), 0.65f) * 1.15f), 0, 255),
                Mth.clamp((int) (mix(accent.getBlue(), hot.getBlue(), 0.65f) * 1.15f), 0, 255));
        int argb = pack(mixed.getRed(), mixed.getGreen(), mixed.getBlue(), outlineOpacity.getFloatValue() / 100.0f);

        float x1 = (float) (box.minX - cam.x), y1 = (float) (box.minY - cam.y), z1 = (float) (box.minZ - cam.z);
        float x2 = (float) (box.maxX - cam.x), y2 = (float) (box.maxY - cam.y), z2 = (float) (box.maxZ - cam.z);
        boolean down = cam.y < box.minY, up = cam.y > box.maxY;
        boolean north = cam.z < box.minZ, south = cam.z > box.maxZ;
        boolean west = cam.x < box.minX, east = cam.x > box.maxX;

        boolean any = false;
        if (down || north) { line(b, m, x1, y1, z1, x2, y1, z1, argb); any = true; }
        if (down || south) { line(b, m, x1, y1, z2, x2, y1, z2, argb); any = true; }
        if (down || west)  { line(b, m, x1, y1, z1, x1, y1, z2, argb); any = true; }
        if (down || east)  { line(b, m, x2, y1, z1, x2, y1, z2, argb); any = true; }
        if (up || north)   { line(b, m, x1, y2, z1, x2, y2, z1, argb); any = true; }
        if (up || south)   { line(b, m, x1, y2, z2, x2, y2, z2, argb); any = true; }
        if (up || west)    { line(b, m, x1, y2, z1, x1, y2, z2, argb); any = true; }
        if (up || east)    { line(b, m, x2, y2, z1, x2, y2, z2, argb); any = true; }
        if (north || west) { line(b, m, x1, y1, z1, x1, y2, z1, argb); any = true; }
        if (north || east) { line(b, m, x2, y1, z1, x2, y2, z1, argb); any = true; }
        if (south || west) { line(b, m, x1, y1, z2, x1, y2, z2, argb); any = true; }
        if (south || east) { line(b, m, x2, y1, z2, x2, y2, z2, argb); any = true; }
        return any;
    }

    private static void line(BufferBuilder b, Matrix4f m, float x1, float y1, float z1,
                             float x2, float y2, float z2, int argb) {
        b.addVertex(m, x1, y1, z1).setColor(argb);
        b.addVertex(m, x2, y2, z2).setColor(argb);
    }

    private static float mix(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static int pack(float r, float g, float b, float a) {
        int ai = Mth.clamp((int) (a * 255.0f), 0, 255);
        int ri = Mth.clamp((int) r, 0, 255);
        int gi = Mth.clamp((int) g, 0, 255);
        int bi = Mth.clamp((int) b, 0, 255);
        return (ai << 24) | (ri << 16) | (gi << 8) | bi;
    }
}
