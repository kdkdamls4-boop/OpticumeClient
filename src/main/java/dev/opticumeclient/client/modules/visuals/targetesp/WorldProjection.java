package dev.opticumeclient.client.modules.visuals.targetesp;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector4f;

public final class WorldProjection {
    private static final Matrix4f VIEW_MATRIX = new Matrix4f();
    private static final Matrix4f PROJECTION_MATRIX = new Matrix4f();
    private static boolean valid;
    private static final float NEAR_CLIP_Z = -0.15F;

    private WorldProjection() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    public static Vector2f project(Vec3 pos, boolean strictBounds) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (valid && mc.gameRenderer != null) {
                Vec3 rel = pos.subtract(mc.gameRenderer.mainCamera().position());
                Vector4f clip = new Vector4f((float) rel.x, (float) rel.y, (float) rel.z, 1.0F);
                clip.mul(VIEW_MATRIX);
                clip.mul(PROJECTION_MATRIX);
                if (clip.w <= 0.0F) {
                    if (strictBounds) {
                        return null;
                    }
                    Vector4f fallback = new Vector4f((float) rel.x, (float) rel.y, (float) rel.z, 1.0F);
                    fallback.mul(VIEW_MATRIX);
                    if (fallback.z > NEAR_CLIP_Z) {
                        fallback.z = NEAR_CLIP_Z;
                    }
                    fallback.w = 1.0F;
                    fallback.mul(PROJECTION_MATRIX);
                    if (fallback.w <= 0.0F) {
                        return null;
                    }
                    clip = fallback;
                }

                float ndcX = clip.x / clip.w;
                float ndcY = clip.y / clip.w;
                if (!strictBounds || !(Math.abs(ndcX) > 1.0F) && !(Math.abs(ndcY) > 1.0F)) {
                    int sw = mc.getWindow().getScreenWidth();
                    int sh = mc.getWindow().getScreenHeight();
                    return new Vector2f((ndcX + 1.0F) / 2.0F * sw, (1.0F - ndcY) / 2.0F * sh);
                } else {
                    return null;
                }
            } else {
                return null;
            }
        } catch (Exception e) {
            return null;
        }
    }

    public static Vector2f project(Vec3 pos) {
        return project(pos, true);
    }

    public static Matrix4f getViewMatrix() {
        return valid ? new Matrix4f(VIEW_MATRIX) : null;
    }

    public static Vector2f scaleGui(double x, double y) {
        Minecraft mc = Minecraft.getInstance();
        return new Vector2f((float) (x * mc.getWindow().getGuiScale()), (float) (y * mc.getWindow().getGuiScale()));
    }

    public static void set(Matrix4f view, Matrix4f projection) {
        if (view != null && projection != null) {
            VIEW_MATRIX.set(view);
            PROJECTION_MATRIX.set(projection);
            valid = true;
        } else {
            valid = false;
        }
    }

    public static Vector2f projectNormalized(Vec3 pos, boolean strictBounds) {
        Vector2f screen = project(pos, strictBounds);
        if (screen == null) {
            return null;
        }
        Minecraft mc = Minecraft.getInstance();
        int sw = Math.max(mc.getWindow().getScreenWidth(), 1);
        int sh = Math.max(mc.getWindow().getScreenHeight(), 1);
        return new Vector2f(screen.x / sw, 1.0F - screen.y / sh);
    }

    public static boolean isHovered(double mx, double my, double x, double y, double w, double h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    public static Matrix4f getProjectionMatrix() {
        return valid ? new Matrix4f(PROJECTION_MATRIX) : null;
    }
}
