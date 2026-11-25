package dev.opticumeclient.client.modules.visuals.killeffect;

import com.mojang.blaze3d.vertex.BufferBuilder;
import dev.opticumeclient.client.render.ColorUtils;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Single animated 3D primitive piece for KillEffect death animations (Shatter / Dissolve / Soul / Ash).
 * Rendered via RenderPearl WorldPrimitiveRenderer using batched quads and outlines.
 */
public final class DeathPiece {
    public static final int MODE_SHATTER = 0;
    public static final int MODE_DISSOLVE = 1;
    public static final int MODE_SOUL = 2;
    public static final int MODE_ASH = 3;

    public final int mode;
    public Vec3 pos;
    public Vec3 prevPos;
    public Vec3 vel;
    public final Vector3f rot;
    public final Vector3f prevRot;
    public final Vector3f angVel;
    public final Vector3f halfExtents;
    public final double groundY;
    public final int baseColor;
    public final int secondaryColor;
    public final float phase;
    public final int maxAge;
    public int age = 0;

    private final Quaternionf tmpQuat = new Quaternionf();
    private final Vector3f tmpVec = new Vector3f();

    public DeathPiece(int mode,
                      Vec3 pos,
                      Vec3 vel,
                      Vector3f rot,
                      Vector3f angVel,
                      Vector3f halfExtents,
                      double groundY,
                      int baseColor,
                      int secondaryColor,
                      float phase,
                      int maxAge) {
        this.mode = mode;
        this.pos = pos;
        this.prevPos = pos;
        this.vel = vel;
        this.rot = new Vector3f(rot);
        this.prevRot = new Vector3f(rot);
        this.angVel = new Vector3f(angVel);
        this.halfExtents = new Vector3f(halfExtents);
        this.groundY = groundY;
        this.baseColor = baseColor;
        this.secondaryColor = secondaryColor;
        this.phase = phase;
        this.maxAge = Math.max(6, maxAge);
    }

    public boolean tick() {
        age++;
        prevPos = pos;
        prevRot.set(rot);

        if (mode == MODE_SHATTER) {
            vel = new Vec3(vel.x * 0.96, vel.y - 0.032, vel.z * 0.96);
            pos = pos.add(vel);
            double minFloor = groundY + halfExtents.y * 0.6;
            if (pos.y < minFloor) {
                pos = new Vec3(pos.x, minFloor, pos.z);
                if (vel.y < -0.04) {
                    vel = new Vec3(vel.x * 0.72, -vel.y * 0.34, vel.z * 0.72);
                } else {
                    vel = new Vec3(vel.x * 0.75, 0.0, vel.z * 0.75);
                }
                angVel.mul(0.65f);
            }
            rot.add(angVel);
        } else if (mode == MODE_DISSOLVE) {
            float swayX = (float) Math.sin(age * 0.25f + phase) * 0.012f;
            float swayZ = (float) Math.cos(age * 0.22f + phase * 1.3f) * 0.012f;
            vel = new Vec3(vel.x * 0.93 + swayX, Math.min(0.11, vel.y + 0.005), vel.z * 0.93 + swayZ);
            pos = pos.add(vel);
            rot.add(angVel);
        } else if (mode == MODE_SOUL) {
            float swayX = (float) Math.sin(age * 0.14f + phase) * 0.004f;
            vel = new Vec3(swayX, 0.048, 0.0);
            pos = pos.add(vel);
        } else {
            // MODE_ASH
            float windX = (float) Math.sin(age * 0.18f + phase) * 0.014f + 0.008f;
            float windZ = (float) Math.cos(age * 0.16f + phase) * 0.014f;
            vel = new Vec3(vel.x * 0.92 + windX, vel.y * 0.95 + 0.0035, vel.z * 0.92 + windZ);
            pos = pos.add(vel);
            rot.add(angVel);
        }

        return age >= maxAge;
    }

    public boolean emitQuads(BufferBuilder quadBuf, Matrix4f pose, Vec3 cam, float tickDelta) {
        return emit(quadBuf, true, pose, cam, tickDelta);
    }

    public boolean emitLines(BufferBuilder lineBuf, Matrix4f pose, Vec3 cam, float tickDelta) {
        return emit(lineBuf, false, pose, cam, tickDelta);
    }

    private boolean emit(BufferBuilder buf, boolean quads, Matrix4f pose, Vec3 cam, float tickDelta) {
        float progress = Mth.clamp((age + tickDelta) / maxAge, 0.0f, 1.0f);
        float alphaCurve = 1.0f - progress * progress;
        if (alphaCurve <= 0.02f) {
            return false;
        }

        float scale = 1.0f;
        if (mode == MODE_DISSOLVE) {
            scale = Math.max(0.08f, 1.0f - progress * 0.92f);
        } else if (mode == MODE_ASH) {
            scale = Math.max(0.15f, 1.0f - progress * 0.72f);
        } else if (mode == MODE_SOUL) {
            scale = 1.0f + progress * 0.08f;
        }

        float rx = Mth.lerp(tickDelta, prevRot.x, rot.x);
        float ry = Mth.lerp(tickDelta, prevRot.y, rot.y);
        float rz = Mth.lerp(tickDelta, prevRot.z, rot.z);
        tmpQuat.identity().rotateYXZ(ry, rx, rz);

        float cx = (float) (Mth.lerp(tickDelta, prevPos.x, pos.x) - cam.x);
        float cy = (float) (Mth.lerp(tickDelta, prevPos.y, pos.y) - cam.y);
        float cz = (float) (Mth.lerp(tickDelta, prevPos.z, pos.z) - cam.z);

        float hx = halfExtents.x * scale;
        float hy = halfExtents.y * scale;
        float hz = halfExtents.z * scale;

        int col = (mode == MODE_ASH)
                ? ColorUtils.interpolateColor(baseColor, secondaryColor, Math.min(1.0f, progress * 1.2f))
                : baseColor;
        int drawCol = ColorUtils.withAlphaFactor(col, alphaCurve * (quads
                ? (mode == MODE_SOUL ? 0.28f : 0.48f)
                : (mode == MODE_SOUL ? 0.85f : 0.95f)));

        float[][] corners = new float[8][3];
        int idx = 0;
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sy = -1; sy <= 1; sy += 2) {
                for (int sz = -1; sz <= 1; sz += 2) {
                    tmpVec.set(sx * hx, sy * hy, sz * hz).rotate(tmpQuat);
                    corners[idx][0] = cx + tmpVec.x;
                    corners[idx][1] = cy + tmpVec.y;
                    corners[idx][2] = cz + tmpVec.z;
                    idx++;
                }
            }
        }

        if (quads) {
            emitQuad(buf, pose, corners[0], corners[4], corners[5], corners[1], drawCol);
            emitQuad(buf, pose, corners[2], corners[3], corners[7], corners[6], drawCol);
            emitQuad(buf, pose, corners[0], corners[2], corners[6], corners[4], drawCol);
            emitQuad(buf, pose, corners[1], corners[5], corners[7], corners[3], drawCol);
            emitQuad(buf, pose, corners[0], corners[1], corners[3], corners[2], drawCol);
            emitQuad(buf, pose, corners[4], corners[6], corners[7], corners[5], drawCol);
        } else {
            emitEdge(buf, pose, corners[0], corners[1], drawCol);
            emitEdge(buf, pose, corners[1], corners[5], drawCol);
            emitEdge(buf, pose, corners[5], corners[4], drawCol);
            emitEdge(buf, pose, corners[4], corners[0], drawCol);
            emitEdge(buf, pose, corners[2], corners[3], drawCol);
            emitEdge(buf, pose, corners[3], corners[7], drawCol);
            emitEdge(buf, pose, corners[7], corners[6], drawCol);
            emitEdge(buf, pose, corners[6], corners[2], drawCol);
            emitEdge(buf, pose, corners[0], corners[2], drawCol);
            emitEdge(buf, pose, corners[1], corners[3], drawCol);
            emitEdge(buf, pose, corners[5], corners[7], drawCol);
            emitEdge(buf, pose, corners[4], corners[6], drawCol);
        }
        return true;
    }

    private static void emitQuad(BufferBuilder buf, Matrix4f pose, float[] a, float[] b, float[] c, float[] d, int argb) {
        buf.addVertex(pose, a[0], a[1], a[2]).setColor(argb);
        buf.addVertex(pose, b[0], b[1], b[2]).setColor(argb);
        buf.addVertex(pose, c[0], c[1], c[2]).setColor(argb);
        buf.addVertex(pose, d[0], d[1], d[2]).setColor(argb);
    }

    private static void emitEdge(BufferBuilder buf, Matrix4f pose, float[] a, float[] b, int argb) {
        buf.addVertex(pose, a[0], a[1], a[2]).setColor(argb);
        buf.addVertex(pose, b[0], b[1], b[2]).setColor(argb);
    }
}
