package dev.opticumeclient.client.modules.visuals.killeffect;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Captures the dying entity's proportions and pose (without reflection) and builds
 * 3D DeathPiece geometry for Shatter, Dissolve, Soul and Ash animations.
 */
public final class DeathPoseFactory {
    private DeathPoseFactory() {
    }

    private record LimbSpec(float ox, float oy, float oz, float hx, float hy, float hz, float pitchOffset) {
    }

    public static List<DeathPiece> build(LivingEntity entity,
                                         String animMode,
                                         float durationSec,
                                         float force,
                                         int primaryColor) {
        List<DeathPiece> out = new ArrayList<>();
        if (entity == null || animMode == null || "Off".equalsIgnoreCase(animMode)) {
            return out;
        }

        int modeIndex = switch (animMode) {
            case "Shatter" -> DeathPiece.MODE_SHATTER;
            case "Dissolve" -> DeathPiece.MODE_DISSOLVE;
            case "Soul" -> DeathPiece.MODE_SOUL;
            case "Ash" -> DeathPiece.MODE_ASH;
            default -> -1;
        };
        if (modeIndex < 0) return out;

        Vec3 base = entity.position();
        double groundY = base.y;
        float scaleY = Mth.clamp(entity.getBbHeight() / 1.8f, 0.50f, 1.85f);
        float scaleXZ = Mth.clamp(entity.getBbWidth() / 0.6f, 0.55f, 2.0f);
        float bodyYawRad = (float) Math.toRadians(-entity.yBodyRot);
        float headYawRad = (float) Math.toRadians(-entity.yHeadRot);
        float headPitchRad = (float) Math.toRadians(entity.getXRot() * 0.6f);
        float walk = entity.walkAnimation.position();
        float limbSwing = (float) Math.sin(walk * 0.6662f) * 0.45f;

        int maxTicks = Math.max(10, Math.round(durationSec * 20.0f));
        ThreadLocalRandom rng = ThreadLocalRandom.current();

        LimbSpec[] limbs = {
                new LimbSpec(0.0f, 1.56f, 0.0f, 0.22f, 0.22f, 0.22f, headPitchRad),   // Head
                new LimbSpec(0.0f, 1.04f, 0.0f, 0.23f, 0.34f, 0.13f, 0.0f),           // Torso
                new LimbSpec(-0.34f, 1.04f, 0.0f, 0.11f, 0.33f, 0.11f, limbSwing),    // Right Arm
                new LimbSpec(0.34f, 1.04f, 0.0f, 0.11f, 0.33f, 0.11f, -limbSwing),    // Left Arm
                new LimbSpec(-0.12f, 0.36f, 0.0f, 0.11f, 0.34f, 0.11f, -limbSwing),   // Right Leg
                new LimbSpec(0.12f, 0.36f, 0.0f, 0.11f, 0.34f, 0.11f, limbSwing)      // Left Leg
        };

        float cos = Mth.cos(bodyYawRad);
        float sin = Mth.sin(bodyYawRad);

        if (modeIndex == DeathPiece.MODE_SOUL) {
            for (int i = 0; i < limbs.length; i++) {
                LimbSpec l = limbs[i];
                double wx = base.x + (l.ox * cos - l.oz * sin) * scaleXZ;
                double wy = base.y + l.oy * scaleY;
                double wz = base.z + (l.ox * sin + l.oz * cos) * scaleXZ;
                float yaw = (i == 0) ? headYawRad : bodyYawRad;
                out.add(new DeathPiece(
                        DeathPiece.MODE_SOUL,
                        new Vec3(wx, wy, wz),
                        new Vec3(0.0, 0.045 * force, 0.0),
                        new Vector3f(l.pitchOffset, yaw, 0.0f),
                        new Vector3f(0.0f, 0.0f, 0.0f),
                        new Vector3f(l.hx * scaleXZ, l.hy * scaleY, l.hz * scaleXZ),
                        groundY,
                        primaryColor,
                        primaryColor,
                        i * 0.4f,
                        maxTicks
                ));
            }
            return out;
        }

        if (modeIndex == DeathPiece.MODE_SHATTER) {
            for (int li = 0; li < limbs.length; li++) {
                LimbSpec l = limbs[li];
                float subHx = (l.hx * scaleXZ) * 0.48f;
                float subHy = (l.hy * scaleY) * 0.48f;
                float subHz = (l.hz * scaleXZ) * 0.48f;
                for (int sx = -1; sx <= 1; sx += 2) {
                    for (int sy = -1; sy <= 1; sy += 2) {
                        for (int sz = -1; sz <= 1; sz += 2) {
                            float lx = l.ox * scaleXZ + sx * subHx;
                            float ly = l.oy * scaleY + sy * subHy;
                            float lz = l.oz * scaleXZ + sz * subHz;
                            double wx = base.x + (lx * cos - lz * sin);
                            double wy = base.y + ly;
                            double wz = base.z + (lx * sin + lz * cos);

                            double outX = (wx - base.x) * 0.38 + (rng.nextDouble() - 0.5) * 0.16;
                            double outZ = (wz - base.z) * 0.38 + (rng.nextDouble() - 0.5) * 0.16;
                            double outY = 0.09 + rng.nextDouble() * 0.18;
                            Vec3 vel = new Vec3(outX * force, outY * force, outZ * force);
                            Vector3f ang = new Vector3f(
                                    (rng.nextFloat() - 0.5f) * 0.28f * force,
                                    (rng.nextFloat() - 0.5f) * 0.28f * force,
                                    (rng.nextFloat() - 0.5f) * 0.28f * force
                            );
                            out.add(new DeathPiece(
                                    DeathPiece.MODE_SHATTER,
                                    new Vec3(wx, wy, wz),
                                    vel,
                                    new Vector3f(l.pitchOffset, bodyYawRad, 0.0f),
                                    ang,
                                    new Vector3f(subHx, subHy, subHz),
                                    groundY,
                                    primaryColor,
                                    primaryColor,
                                    rng.nextFloat() * 6.28f,
                                    maxTicks
                            ));
                        }
                    }
                }
            }
            return out;
        }

        // MODE_DISSOLVE (tiny rising cubes) or MODE_ASH (thin glowing->cooling plates)
        boolean isAsh = (modeIndex == DeathPiece.MODE_ASH);
        int secondary = isAsh ? 0xFF3E3B39 : primaryColor;
        int col = isAsh ? 0xFFFF6D28 : primaryColor;
        int samplesPerLimb = 10;

        for (LimbSpec l : limbs) {
            for (int k = 0; k < samplesPerLimb; k++) {
                float lx = l.ox * scaleXZ + (rng.nextFloat() * 2.0f - 1.0f) * l.hx * scaleXZ;
                float ly = l.oy * scaleY + (rng.nextFloat() * 2.0f - 1.0f) * l.hy * scaleY;
                float lz = l.oz * scaleXZ + (rng.nextFloat() * 2.0f - 1.0f) * l.hz * scaleXZ;
                double wx = base.x + (lx * cos - lz * sin);
                double wy = base.y + ly;
                double wz = base.z + (lx * sin + lz * cos);

                double vx = (rng.nextDouble() - 0.5) * 0.055 * force;
                double vy = (0.03 + rng.nextDouble() * 0.055) * force;
                double vz = (rng.nextDouble() - 0.5) * 0.055 * force;
                Vector3f ext = isAsh
                        ? new Vector3f(0.06f * scaleXZ, 0.015f * scaleY, 0.055f * scaleXZ)
                        : new Vector3f(0.055f * scaleXZ, 0.055f * scaleY, 0.055f * scaleXZ);
                Vector3f ang = new Vector3f(
                        (rng.nextFloat() - 0.5f) * 0.22f,
                        (rng.nextFloat() - 0.5f) * 0.22f,
                        (rng.nextFloat() - 0.5f) * 0.22f
                );
                out.add(new DeathPiece(
                        modeIndex,
                        new Vec3(wx, wy, wz),
                        new Vec3(vx, vy, vz),
                        new Vector3f(rng.nextFloat() * 3.14f, rng.nextFloat() * 3.14f, rng.nextFloat() * 3.14f),
                        ang,
                        ext,
                        groundY,
                        col,
                        secondary,
                        rng.nextFloat() * 6.28f,
                        Math.max(12, Math.round(maxTicks * (0.75f + rng.nextFloat() * 0.45f)))
                ));
            }
        }
        return out;
    }
}
