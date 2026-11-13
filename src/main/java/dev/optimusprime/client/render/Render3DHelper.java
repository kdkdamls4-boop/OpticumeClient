package dev.optimusprime.client.render;

import net.minecraft.client.Minecraft; 
import net.minecraft.client.renderer.RenderPipelines; 
import net.minecraft.client.gui.GuiGraphicsExtractor; 
import net.minecraft.client.Camera; 
import com.mojang.blaze3d.vertex.PoseStack; 
import net.minecraft.world.entity.Entity; 
import net.minecraft.resources.Identifier; 
import net.minecraft.world.phys.AABB; 
import net.minecraft.util.Mth; 
import net.minecraft.world.phys.Vec3; 
import org.joml.Matrix3x2fStack;

import java.util.ArrayList;
import java.util.List;

/**
 * OpenGL 3.3 Core Profile safe 3D rendering engine for Minecraft 1.21.11.
 * Supports smooth 240 FPS entity interpolation, textured 3D camera-facing billboards
 * (Ghosts fireflies, CaptureMark, HitBubbles, Star/Heart/Snowflake particles),
 * 3D ribbons, ChinaHat cones, JumpCircles, and Corner-Box ESP projections.
 */
public class Render3DHelper {

    private record Line2D(float x1, float y1, float x2, float y2, float thickness, int color, boolean glow) {}
    private record Sprite2D(float screenX, float screenY, float pixelSize, Identifier texture, int color, float rotationDeg, boolean glowHalo) {}

    private static final List<Line2D> QUEUED_LINES = new ArrayList<>(4096);
    private static final List<Sprite2D> QUEUED_SPRITES = new ArrayList<>(2048);

    private static final org.joml.Matrix4f CACHED_VIEW_PROJ = new org.joml.Matrix4f();
    private static final org.joml.Vector4f CLIP_COORD = new org.joml.Vector4f();

    private static double cachedCamX, cachedCamY, cachedCamZ;
    private static int cachedSw, cachedSh;
    private static boolean matrixValid = false;

    public static void updateFrameCache() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gameRenderer == null) {
            matrixValid = false;
            return;
        }
        Camera camera = mc.gameRenderer.mainCamera();
        if (camera == null) {
            matrixValid = false;
            return;
        }
        Vec3 camPos = camera.position();
        cachedCamX = camPos.x;
        cachedCamY = camPos.y;
        cachedCamZ = camPos.z;

        camera.getViewRotationProjectionMatrix(CACHED_VIEW_PROJ);

        if (mc.getWindow() != null) {
            cachedSw = mc.getWindow().getGuiScaledWidth();
            cachedSh = mc.getWindow().getGuiScaledHeight();
        } else {
            cachedSw = 1920;
            cachedSh = 1080;
        }

        matrixValid = true;
    }

    public static void setupRender3D() {
        updateFrameCache();
    }

    public static void endRender3D() {
        matrixValid = false;
    }

    /**
     * Smooth 60-240+ FPS interpolated entity world position.
     */
    public static Vec3 getLerpedPos(Entity entity, float tickDelta) {
        double x = Mth.lerp(tickDelta, entity.xOld, entity.getX());
        double y = Mth.lerp(tickDelta, entity.yOld, entity.getY());
        double z = Mth.lerp(tickDelta, entity.zOld, entity.getZ());
        return new Vec3(x, y, z);
    }

    public static AABB getLerpedBox(Entity entity, float tickDelta) {
        Vec3 lerped = getLerpedPos(entity, tickDelta);
        Vec3 current = entity.position();
        return entity.getBoundingBox().move(lerped.x - current.x, lerped.y - current.y, lerped.z - current.z);
    }

    /**
     * Projects a 3D world position to 2D screen coordinates [screenX, screenY, depthDistance].
     * Uses Camera.getViewRotationProjectionMatrix for accurate dynamic FOV and pitch projection.
     * Returns null if behind camera near plane.
     */
    public static float[] worldToScreen(Vec3 worldPos) {
        if (!matrixValid) {
            updateFrameCache();
        }
        if (!matrixValid) return null;

        float relX = (float) (worldPos.x - cachedCamX);
        float relY = (float) (worldPos.y - cachedCamY);
        float relZ = (float) (worldPos.z - cachedCamZ);

        CLIP_COORD.set(relX, relY, relZ, 1.0f);
        CACHED_VIEW_PROJ.transform(CLIP_COORD);

        if (CLIP_COORD.w <= 0.05f) {
            return null;
        }

        float ndcX = CLIP_COORD.x / CLIP_COORD.w;
        float ndcY = CLIP_COORD.y / CLIP_COORD.w;

        if (ndcX < -3.5f || ndcX > 3.5f || ndcY < -3.5f || ndcY > 3.5f) {
            return null;
        }

        float sx = (ndcX + 1.0f) * 0.5f * cachedSw;
        float sy = (1.0f - ndcY) * 0.5f * cachedSh;
        return new float[]{sx, sy, CLIP_COORD.w};
    }

    public static void queueLine3D(Vec3 start, Vec3 end, int color) {
        queueLine3D(start, end, 1.5f, color, false);
    }

    public static void queueLine3D(Vec3 start, Vec3 end, float thickness, int color, boolean glow) {
        if (QUEUED_LINES.size() >= 4096) return;
        float[] p1 = worldToScreen(start);
        float[] p2 = worldToScreen(end);
        if (p1 != null && p2 != null) {
            QUEUED_LINES.add(new Line2D(p1[0], p1[1], p2[0], p2[1], thickness, color, glow));
        }
    }

    /**
     * Queues a camera-facing textured 3D billboard sprite (firefly, star, heart, snowflake, capture mark, hitbubble).
     */
    public static void queueBillboardSprite(Vec3 worldPos, float worldScale, Identifier texture, int color, float rotationDeg, boolean glowHalo) {
        if (QUEUED_SPRITES.size() >= 2048) return;
        float[] proj = worldToScreen(worldPos);
        if (proj == null) return;

        int sh = cachedSh > 0 ? cachedSh : (Minecraft.getInstance().getWindow() != null ? Minecraft.getInstance().getWindow().getGuiScaledHeight() : 1080);
        float dist = Math.max(0.35f, proj[2]);
        float pixelSize = (worldScale * sh * 0.65f) / dist;
        if (pixelSize < 1.5f || pixelSize > sh * 1.4f) return;

        QUEUED_SPRITES.add(new Sprite2D(proj[0], proj[1], pixelSize, texture, color, rotationDeg, glowHalo));
    }

    public static void drawBox(PoseStack matrices, AABB box, int color) {
        drawBoxColor(box, color, 1.5f, true);
    }

    public static void drawBox(PoseStack matrices, AABB box, float r, float g, float b, float a, boolean outline) {
        int color = ColorUtils.rgba(
                (int) (Math.max(0f, Math.min(1f, r)) * 255),
                (int) (Math.max(0f, Math.min(1f, g)) * 255),
                (int) (Math.max(0f, Math.min(1f, b)) * 255),
                (int) (Math.max(0f, Math.min(1f, a)) * 255)
        );
        drawBoxColor(box, color, 1.5f, true);
    }

    public static void drawBoxColor(AABB box, int color, float thickness, boolean glow) {
        Vec3 v000 = new Vec3(box.minX, box.minY, box.minZ);
        Vec3 v100 = new Vec3(box.maxX, box.minY, box.minZ);
        Vec3 v101 = new Vec3(box.maxX, box.minY, box.maxZ);
        Vec3 v001 = new Vec3(box.minX, box.minY, box.maxZ);

        Vec3 v010 = new Vec3(box.minX, box.maxY, box.minZ);
        Vec3 v110 = new Vec3(box.maxX, box.maxY, box.minZ);
        Vec3 v111 = new Vec3(box.maxX, box.maxY, box.maxZ);
        Vec3 v011 = new Vec3(box.minX, box.maxY, box.maxZ);

        queueLine3D(v000, v100, thickness, color, glow);
        queueLine3D(v100, v101, thickness, color, glow);
        queueLine3D(v101, v001, thickness, color, glow);
        queueLine3D(v001, v000, thickness, color, glow);

        queueLine3D(v010, v110, thickness, color, glow);
        queueLine3D(v110, v111, thickness, color, glow);
        queueLine3D(v111, v011, thickness, color, glow);
        queueLine3D(v011, v010, thickness, color, glow);

        queueLine3D(v000, v010, thickness, color, glow);
        queueLine3D(v100, v110, thickness, color, glow);
        queueLine3D(v101, v111, thickness, color, glow);
        queueLine3D(v001, v011, thickness, color, glow);
    }

    /**
     * Projects all 8 corners of a 3D Box into a 2D bounding rectangle [minX, minY, maxX, maxY].
     */
    public static float[] projectBox2D(AABB box) {
        Vec3[] corners = new Vec3[]{
                new Vec3(box.minX, box.minY, box.minZ),
                new Vec3(box.maxX, box.minY, box.minZ),
                new Vec3(box.maxX, box.minY, box.maxZ),
                new Vec3(box.minX, box.minY, box.maxZ),
                new Vec3(box.minX, box.maxY, box.minZ),
                new Vec3(box.maxX, box.maxY, box.minZ),
                new Vec3(box.maxX, box.maxY, box.maxZ),
                new Vec3(box.minX, box.maxY, box.maxZ)
        };
        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        int valid = 0;
        for (Vec3 c : corners) {
            float[] p = worldToScreen(c);
            if (p != null) {
                valid++;
                if (p[0] < minX) minX = p[0];
                if (p[1] < minY) minY = p[1];
                if (p[0] > maxX) maxX = p[0];
                if (p[1] > maxY) maxY = p[1];
            }
        }
        if (valid < 2 || maxX <= minX || maxY <= minY) return null;
        return new float[]{minX, minY, maxX, maxY};
    }

    public static void drawTargetRing(Vec3 pos, double radius, int color, float rotationAngle) {
        int segments = 32;
        Vec3 prev = null;
        double rotRad = Math.toRadians(rotationAngle);
        for (int i = 0; i <= segments; i++) {
            double angle = rotRad + i * Math.PI * 2.0 / segments;
            Vec3 curr = new Vec3(
                    pos.x + Math.cos(angle) * radius,
                    pos.y + 0.05,
                    pos.z + Math.sin(angle) * radius
            );
            if (prev != null) {
                queueLine3D(prev, curr, 2.0f, color, true);
            }
            prev = curr;
        }
    }

    public static void drawTargetRing(PoseStack matrices, Vec3 pos, float radius, float r, float g, float b, float a) {
        int color = ColorUtils.rgba(
                (int) (Math.max(0f, Math.min(1f, r)) * 255),
                (int) (Math.max(0f, Math.min(1f, g)) * 255),
                (int) (Math.max(0f, Math.min(1f, b)) * 255),
                (int) (Math.max(0f, Math.min(1f, a)) * 255)
        );
        int segments = 32;
        Vec3 prev = null;
        for (int i = 0; i <= segments; i++) {
            double angle = i * Math.PI * 2.0 / segments;
            Vec3 curr = new Vec3(
                    pos.x + Math.cos(angle) * radius,
                    pos.y + 0.05,
                    pos.z + Math.sin(angle) * radius
            );
            if (prev != null) {
                queueLine3D(prev, curr, 2.0f, color, true);
            }
            prev = curr;
        }
    }

    public static void flush2D(GuiGraphicsExtractor context) {
        TextureAssets.ensureInitialized();

        if (!QUEUED_LINES.isEmpty()) {
            for (Line2D line : QUEUED_LINES) {
                drawSmoothLine(context, line.x1, line.y1, line.x2, line.y2, line.thickness, line.color, line.glow);
            }
            QUEUED_LINES.clear();
        }

        if (!QUEUED_SPRITES.isEmpty()) {
            // Step 1: Draw all glowHalos in ONE batch (TextureAssets.GLOW_RADIAL bound once)
            for (Sprite2D s : QUEUED_SPRITES) {
                if (s.glowHalo) {
                    int size = Math.max(2, Math.round(s.pixelSize));
                    int glowSize = Math.round(size * 1.85f);
                    float glowHalf = glowSize * 0.5f;
                    int haloColor = ColorUtils.withAlphaFactor(s.color, 0.42f);
                    Render2DHelper.drawTexture(context, TextureAssets.GLOW_RADIAL, s.screenX - glowHalf, s.screenY - glowHalf, glowSize, glowSize, haloColor);
                }
            }

            // Step 2: Sort QUEUED_SPRITES by texture identifier so identical textures are drawn consecutively (zero String allocs)
            QUEUED_SPRITES.sort((a, b) -> {
                if (a.texture == b.texture) return 0;
                if (a.texture == null) return -1;
                if (b.texture == null) return 1;
                int hDiff = Integer.compare(a.texture.hashCode(), b.texture.hashCode());
                return hDiff != 0 ? hDiff : a.texture.compareTo(b.texture);
            });

            // Step 3: Draw main sprites consecutively by texture batch
            Matrix3x2fStack matrices = context.pose();
            for (Sprite2D s : QUEUED_SPRITES) {
                int size = Math.max(2, Math.round(s.pixelSize));
                float half = size * 0.5f;

                if (Math.abs(s.rotationDeg) > 0.5f) {
                    matrices.pushMatrix();
                    matrices.translate(s.screenX, s.screenY);
                    matrices.rotate((float) Math.toRadians(s.rotationDeg));
                    try {
                        context.blit(RenderPipelines.GUI_TEXTURED, s.texture, Math.round(-half), Math.round(-half), 0.0f, 0.0f, size, size, size, size, s.color);
                    } catch (Throwable ignored) {
                    }
                    matrices.popMatrix();
                } else {
                    Render2DHelper.drawTexture(context, s.texture, s.screenX - half, s.screenY - half, size, size, s.color);
                }
            }
            QUEUED_SPRITES.clear();
        }
        matrixValid = false;
    }

    public static void drawSmoothLine(GuiGraphicsExtractor context, float x1, float y1, float x2, float y2, float thickness, int color, boolean glow) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float dist = (float) Math.hypot(dx, dy);
        if (dist < 0.5f || dist > 2600.0f) return;

        float angle = (float) Math.atan2(dy, dx);
        float halfT = Math.max(0.5f, thickness * 0.5f);
        int len = Math.round(dist);

        Matrix3x2fStack matrices = context.pose();
        matrices.pushMatrix();
        matrices.translate(x1, y1);
        matrices.rotate(angle);

        if (glow) {
            int glowColor = ColorUtils.withAlphaFactor(color, 0.28f);
            float glowH = halfT + 1.5f;
            context.fill(-1, Math.round(-glowH), len + 1, Math.round(glowH), glowColor);
        }
        context.fill(0, Math.round(-halfT), len, Math.round(halfT), color);

        matrices.popMatrix();
    }
}
