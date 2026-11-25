package dev.opticumeclient.client.combat;

import dev.opticumeclient.client.combat.neuro.GruRotationModel;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;
import java.util.Random;

/**
 * Unified Combat Rotation 2.0 engine providing:
 * - Velocity-compensated closest-safe-point aiming inside target AABB with block occlusion fallback
 * - Second-order spring-damper (Organic), variable-speed + S-arc (Matrix), burst Snap, Multipoint, and accel-clamped Neuro (GRU)
 * - Ornstein-Uhlenbeck correlated human drift (attenuated near target lock)
 * - Strict raycast verification checking block occlusion and obstructing entities via MC 26.3 APIs
 */
public final class CombatRotationEngine {

    public enum RotationStyle {
        ORGANIC,
        MATRIX,
        SNAP,
        MULTIPOINT,
        NEURO,
        LINEAR,
        NONE;

        public static RotationStyle fromString(String mode) {
            if (mode == null) return ORGANIC;
            return switch (mode.trim().toLowerCase()) {
                case "matrix" -> MATRIX;
                case "snap", "snaponhit" -> SNAP;
                case "multipoint" -> MULTIPOINT;
                case "neuro (gru)", "neuro" -> NEURO;
                case "linear" -> LINEAR;
                case "none" -> NONE;
                default -> ORGANIC; // covers "Organic", "Smooth", "Standard"
            };
        }
    }

    public static final class AimSession {
        public int lastTargetId = Integer.MIN_VALUE;
        public float velYaw = 0.0f;
        public float velPitch = 0.0f;
        public float ouYaw = 0.0f;
        public float ouPitch = 0.0f;
        public double aimOffsetX = 0.0;
        public double aimOffsetY = 0.0;
        public double aimOffsetZ = 0.0;
        public double goalOffsetX = 0.0;
        public double goalOffsetY = 0.0;
        public double goalOffsetZ = 0.0;
        public int ticksSinceHit = 20;
        public int switchTicks = 0;
        public Vec3 smoothedPoint = null;
        public final Random rng = new Random();

        public void reset() {
            lastTargetId = Integer.MIN_VALUE;
            velYaw = 0.0f;
            velPitch = 0.0f;
            ouYaw = 0.0f;
            ouPitch = 0.0f;
            aimOffsetX = 0.0;
            aimOffsetY = 0.0;
            aimOffsetZ = 0.0;
            goalOffsetX = 0.0;
            goalOffsetY = 0.0;
            goalOffsetZ = 0.0;
            ticksSinceHit = 20;
            switchTicks = 0;
            smoothedPoint = null;
        }

        public void onTargetTick(LivingEntity target) {
            if (target == null) {
                reset();
                return;
            }
            if (target.getId() != lastTargetId) {
                lastTargetId = target.getId();
                switchTicks = 0;
                ticksSinceHit = 20;
                smoothedPoint = null;
                velYaw *= 0.25f;
                velPitch *= 0.25f;
                goalOffsetX = 0.0;
                goalOffsetY = 0.0;
                goalOffsetZ = 0.0;
            } else {
                switchTicks++;
            }

            // Correlated Ornstein-Uhlenbeck drift
            ouYaw = ouYaw * 0.72f + (float) rng.nextGaussian() * 0.42f;
            ouPitch = ouPitch * 0.74f + (float) rng.nextGaussian() * 0.30f;

            // Smooth post-hit aim point micro-shift
            aimOffsetX = Mth.lerp(0.28, aimOffsetX, goalOffsetX);
            aimOffsetY = Mth.lerp(0.28, aimOffsetY, goalOffsetY);
            aimOffsetZ = Mth.lerp(0.28, aimOffsetZ, goalOffsetZ);
            goalOffsetX *= 0.90;
            goalOffsetY *= 0.90;
            goalOffsetZ *= 0.90;

            if (ticksSinceHit < 1000) {
                ticksSinceHit++;
            }
        }

        public void onHitLanded(LivingEntity target) {
            ticksSinceHit = 0;
            double maxHoriz = target != null ? Math.min(0.11, target.getBbWidth() * 0.18) : 0.09;
            double maxVert = target != null ? Math.min(0.20, target.getBbHeight() * 0.12) : 0.16;
            goalOffsetX = (rng.nextDouble() * 2.0 - 1.0) * maxHoriz;
            goalOffsetY = (rng.nextDouble() * 2.0 - 1.0) * maxVert;
            goalOffsetZ = (rng.nextDouble() * 2.0 - 1.0) * maxHoriz;
        }
    }

    private CombatRotationEngine() {
    }

    public static Vec3 computeAimPoint(Minecraft mc,
                                       LivingEntity target,
                                       RotationStyle style,
                                       boolean velocityPredict,
                                       boolean throughWalls,
                                       AimSession session) {
        if (mc == null || mc.player == null || target == null) {
            return Vec3.ZERO;
        }
        Vec3 eyes = mc.player.getEyePosition();
        AABB liveBox = target.getBoundingBox();
        AABB activeBox = liveBox;

        if (velocityPredict) {
            Vec3 tVel = target.getDeltaMovement();
            Vec3 sVel = mc.player.getDeltaMovement();
            double leadX = Mth.clamp(tVel.x, -0.65, 0.65) * 0.85 - Mth.clamp(sVel.x, -0.65, 0.65) * 0.45;
            double leadY = Mth.clamp(tVel.y * 0.35, -0.25, 0.25);
            double leadZ = Mth.clamp(tVel.z, -0.65, 0.65) * 0.85 - Mth.clamp(sVel.z, -0.65, 0.65) * 0.45;
            activeBox = liveBox.move(leadX, leadY, leadZ);
        }

        Vec3 candidate = computeAimPointInBox(mc, eyes, activeBox, style, throughWalls, session);

        if (velocityPredict && !isAimPointOnBox(eyes, candidate, liveBox)) {
            activeBox = liveBox;
            candidate = computeAimPointInBox(mc, eyes, liveBox, style, throughWalls, session);
        }

        if (style == RotationStyle.MULTIPOINT && session != null) {
            double xPad = activeBox.getXsize() * 0.14;
            double zPad = activeBox.getZsize() * 0.14;
            Vec3 center = activeBox.getCenter();
            double minX = Math.min(center.x, activeBox.minX + xPad);
            double maxX = Math.max(center.x, activeBox.maxX - xPad);
            double minZ = Math.min(center.z, activeBox.minZ + zPad);
            double maxZ = Math.max(center.z, activeBox.maxZ - zPad);
            double minY = activeBox.minY + activeBox.getYsize() * 0.18;
            double maxY = activeBox.minY + activeBox.getYsize() * 0.86;

            if (session.smoothedPoint == null || session.switchTicks <= 1) {
                session.smoothedPoint = candidate;
            } else {
                Vec3 lerped = session.smoothedPoint.lerp(candidate, 0.42);
                Vec3 clamped = new Vec3(
                        Mth.clamp(lerped.x, minX, maxX),
                        Mth.clamp(lerped.y, minY, maxY),
                        Mth.clamp(lerped.z, minZ, maxZ)
                );
                if ((!throughWalls && mc.level != null && !hasBlockLineOfSight(mc, eyes, clamped))
                        || (velocityPredict && !isAimPointOnBox(eyes, clamped, liveBox))) {
                    clamped = candidate;
                }
                session.smoothedPoint = clamped;
            }
            candidate = session.smoothedPoint;
        }

        return candidate;
    }

    private static boolean isAimPointOnBox(Vec3 eyes, Vec3 aimPoint, AABB box) {
        if (box.contains(eyes)) return true;
        Vec3 dir = aimPoint.subtract(eyes);
        double len = dir.length();
        if (len < 1.0e-6) return true;
        Vec3 end = eyes.add(dir.scale((len + 1.5) / len));
        return box.clip(eyes, end).isPresent();
    }

    private static Vec3 computeAimPointInBox(Minecraft mc,
                                            Vec3 eyes,
                                            AABB box,
                                            RotationStyle style,
                                            boolean throughWalls,
                                            AimSession session) {
        double xPad = box.getXsize() * 0.14;
        double zPad = box.getZsize() * 0.14;
        Vec3 center = box.getCenter();
        double minX = Math.min(center.x, box.minX + xPad);
        double maxX = Math.max(center.x, box.maxX - xPad);
        double minZ = Math.min(center.z, box.minZ + zPad);
        double maxZ = Math.max(center.z, box.maxZ - zPad);
        double minY = box.minY + box.getYsize() * 0.18;
        double maxY = box.minY + box.getYsize() * 0.86;

        double cx = Mth.clamp(eyes.x, minX, maxX);
        double cy = Mth.clamp(eyes.y, minY, maxY);
        double cz = Mth.clamp(eyes.z, minZ, maxZ);

        if (style != RotationStyle.MULTIPOINT) {
            double chestY = box.minY + box.getYsize() * 0.68;
            cy = Mth.lerp(0.30, cy, chestY);
        }

        if (session != null && style != RotationStyle.LINEAR && style != RotationStyle.NONE) {
            cx = Mth.clamp(cx + session.aimOffsetX, minX, maxX);
            cy = Mth.clamp(cy + session.aimOffsetY, minY, maxY);
            cz = Mth.clamp(cz + session.aimOffsetZ, minZ, maxZ);
        }

        Vec3 candidate = new Vec3(cx, cy, cz);

        if (!throughWalls && mc.level != null && !hasBlockLineOfSight(mc, eyes, candidate)) {
            Vec3[] fallbacks = new Vec3[]{
                    new Vec3(cx, box.minY + box.getYsize() * 0.84, cz),
                    new Vec3(cx, box.minY + box.getYsize() * 0.70, cz),
                    new Vec3(cx, box.minY + box.getYsize() * 0.56, cz),
                    new Vec3(cx, box.minY + box.getYsize() * 0.34, cz),
                    new Vec3(minX, box.minY + box.getYsize() * 0.64, minZ),
                    new Vec3(maxX, box.minY + box.getYsize() * 0.64, maxZ),
                    new Vec3(minX, box.minY + box.getYsize() * 0.64, maxZ),
                    new Vec3(maxX, box.minY + box.getYsize() * 0.64, minZ)
            };
            Vec3 bestVisible = null;
            double bestDistSq = Double.MAX_VALUE;
            for (Vec3 fb : fallbacks) {
                if (hasBlockLineOfSight(mc, eyes, fb)) {
                    double dSq = eyes.distanceToSqr(fb);
                    if (dSq < bestDistSq) {
                        bestDistSq = dSq;
                        bestVisible = fb;
                    }
                }
            }
            if (bestVisible != null) {
                candidate = bestVisible;
            }
        }
        return candidate;
    }

    public static float[] computeStep(Minecraft mc,
                                      LivingEntity target,
                                      RotationStyle style,
                                      float currentYaw,
                                      float currentPitch,
                                      Vec3 aimPoint,
                                      float maxYawSpeed,
                                      float maxPitchSpeed,
                                      float dtTicks,
                                      boolean humanJitter,
                                      boolean lazyAim,
                                      boolean inAttackWindow,
                                      double reach,
                                      AimSession session) {
        if (mc == null || mc.player == null || target == null || style == RotationStyle.NONE) {
            return new float[]{0.0f, 0.0f};
        }

        Vec3 eyes = mc.player.getEyePosition();
        float[] needed = RotationManager.getRotationsTo(eyes, aimPoint);
        float deltaYaw = Mth.wrapDegrees(needed[0] - currentYaw);
        float deltaPitch = Mth.clamp(needed[1], -90.0f, 90.0f) - currentPitch;

        if (humanJitter && session != null && style != RotationStyle.LINEAR) {
            float errMag = (float) Math.hypot(deltaYaw, deltaPitch);
            float driftScale = errMag < 1.5f ? 0.18f : (inAttackWindow ? 0.35f : 0.70f);
            deltaYaw += session.ouYaw * driftScale;
            deltaPitch += session.ouPitch * driftScale;
        }

        boolean onBox = lazyAim && isRayHittingBox(eyes, currentYaw, currentPitch, target.getBoundingBox(), reach + 0.6);
        float scale = Mth.clamp(dtTicks, 0.02f, 1.5f);
        float effMaxYaw = Math.max(1.0f, maxYawSpeed);
        float effMaxPitch = Math.max(1.0f, maxPitchSpeed);

        float stepYaw;
        float stepPitch;

        switch (style) {
            case MATRIX -> {
                float variance = session != null ? (0.86f + session.rng.nextFloat() * 0.28f) : 1.0f;
                float yawRatio = Mth.clamp(Math.abs(deltaYaw) / 28.0f, 0.26f, 1.0f);
                float pitchRatio = Mth.clamp(Math.abs(deltaPitch) / 22.0f, 0.24f, 1.0f);
                float lazyScale = onBox ? 0.28f : 1.0f;

                float desiredYaw = Mth.clamp(deltaYaw * (0.48f + 0.22f * yawRatio), -effMaxYaw * variance, effMaxYaw * variance) * lazyScale;
                float desiredPitch = Mth.clamp(deltaPitch * (0.42f + 0.20f * pitchRatio), -effMaxPitch * variance, effMaxPitch * variance) * lazyScale;

                // Subtle organic S-curve pitch arc during wide horizontal flicks
                if (!onBox && Math.abs(deltaYaw) > 14.0f) {
                    float dirSign = (deltaPitch != 0.0f) ? Math.signum(deltaPitch) : (session != null && (session.ticksSinceHit & 1) == 0 ? 1.0f : -1.0f);
                    float arcMag = (0.9f + (session != null ? session.rng.nextFloat() * 0.7f : 0.3f))
                            * (float) Math.sin(Mth.clamp(Math.abs(deltaYaw) / 60.0f, 0.0f, 1.0f) * Math.PI);
                    desiredPitch += dirSign * arcMag;
                }

                if (session != null && scale >= 0.85f) {
                    float accelLimY = Math.max(8.0f, effMaxYaw * 0.65f);
                    float accelLimP = Math.max(6.0f, effMaxPitch * 0.60f);
                    stepYaw = session.velYaw + Mth.clamp(desiredYaw - session.velYaw, -accelLimY, accelLimY);
                    stepPitch = session.velPitch + Mth.clamp(desiredPitch - session.velPitch, -accelLimP, accelLimP);
                } else {
                    stepYaw = desiredYaw;
                    stepPitch = desiredPitch;
                }
            }
            case SNAP -> {
                if (!inAttackWindow && session != null && session.ticksSinceHit > 2) {
                    float idleY = effMaxYaw * 0.22f;
                    float idleP = effMaxPitch * 0.20f;
                    stepYaw = Mth.clamp(deltaYaw * 0.30f, -idleY, idleY);
                    stepPitch = Mth.clamp(deltaPitch * 0.28f, -idleP, idleP);
                } else {
                    float varY = session != null ? (0.84f + session.rng.nextFloat() * 0.16f) : 0.94f;
                    float varP = session != null ? (0.82f + session.rng.nextFloat() * 0.18f) : 0.92f;
                    float lazyScale = onBox ? 0.38f : 1.0f;
                    stepYaw = Mth.clamp(deltaYaw * 0.92f, -effMaxYaw * varY, effMaxYaw * varY) * lazyScale;
                    stepPitch = Mth.clamp(deltaPitch * 0.88f, -effMaxPitch * varP, effMaxPitch * varP) * lazyScale;
                }
            }
            case ORGANIC, MULTIPOINT -> {
                float kpYaw = style == RotationStyle.MULTIPOINT ? 0.58f : 0.52f;
                float kpPitch = style == RotationStyle.MULTIPOINT ? 0.48f : 0.44f;
                float lazyScale = onBox ? (style == RotationStyle.MULTIPOINT ? 0.30f : 0.26f) : 1.0f;

                float prevVy = session != null ? session.velYaw : 0.0f;
                float prevVp = session != null ? session.velPitch : 0.0f;

                float targetStepY = Mth.clamp(deltaYaw * kpYaw + prevVy * 0.28f, -effMaxYaw, effMaxYaw) * lazyScale;
                float targetStepP = Mth.clamp(deltaPitch * kpPitch + prevVp * 0.24f, -effMaxPitch, effMaxPitch) * lazyScale;

                if (session != null && scale >= 0.85f) {
                    float maxAccelY = Math.max(8.0f, effMaxYaw * 0.48f);
                    float maxAccelP = Math.max(6.0f, effMaxPitch * 0.44f);
                    stepYaw = prevVy + Mth.clamp(targetStepY - prevVy, -maxAccelY, maxAccelY);
                    stepPitch = prevVp + Mth.clamp(targetStepP - prevVp, -maxAccelP, maxAccelP);
                } else {
                    stepYaw = targetStepY;
                    stepPitch = targetStepP;
                }
            }
            case NEURO -> {
                Vec3 vel = target.getDeltaMovement();
                float velH = (float) Math.hypot(vel.x, vel.z);
                float hurtFrac = target.hurtTime > 0 ? target.hurtTime / 10.0f : 0.0f;
                float[] neuro = GruRotationModel.getInstance().step(
                        target.getId(), deltaYaw, deltaPitch, (float) mc.player.distanceTo(target), velH, hurtFrac, effMaxYaw, effMaxPitch
                );
                float lazyScale = onBox ? 0.35f : 1.0f;
                float desiredYaw = neuro[0] * lazyScale;
                float desiredPitch = neuro[1] * lazyScale;
                if (session != null && scale >= 0.85f) {
                    float maxAccelY = Math.max(8.0f, effMaxYaw * 0.55f);
                    float maxAccelP = Math.max(6.0f, effMaxPitch * 0.50f);
                    stepYaw = session.velYaw + Mth.clamp(desiredYaw - session.velYaw, -maxAccelY, maxAccelY);
                    stepPitch = session.velPitch + Mth.clamp(desiredPitch - session.velPitch, -maxAccelP, maxAccelP);
                } else {
                    stepYaw = desiredYaw;
                    stepPitch = desiredPitch;
                }
            }
            case LINEAR -> {
                float distAngle = (float) Math.hypot(deltaYaw, deltaPitch);
                float uniform = Math.max(0.5f, Math.min(effMaxYaw, effMaxPitch));
                if (distAngle > uniform && distAngle > 1.0E-4f) {
                    float r = uniform / distAngle;
                    stepYaw = deltaYaw * r;
                    stepPitch = deltaPitch * r;
                } else {
                    stepYaw = deltaYaw;
                    stepPitch = deltaPitch;
                }
            }
            default -> {
                return new float[]{0.0f, 0.0f};
            }
        }

        stepYaw = Mth.clamp(stepYaw * scale, -effMaxYaw * scale, effMaxYaw * scale);
        stepPitch = Mth.clamp(stepPitch * scale, -effMaxPitch * scale, effMaxPitch * scale);

        if (session != null) {
            if (scale >= 0.85f) {
                session.velYaw = stepYaw;
                session.velPitch = stepPitch;
            } else {
                session.velYaw = Mth.lerp(0.35f, session.velYaw, stepYaw / scale);
                session.velPitch = Mth.lerp(0.35f, session.velPitch, stepPitch / scale);
            }
        }
        return new float[]{stepYaw, stepPitch};
    }

    public static boolean isRayHittingBox(Vec3 eyes, float yaw, float pitch, AABB box, double dist) {
        if (eyes == null || box == null) return false;
        Vec3 dir = Entity.calculateViewVector(pitch, yaw);
        Vec3 end = eyes.add(dir.scale(Math.max(0.5, dist)));
        return box.contains(eyes) || box.clip(eyes, end).isPresent();
    }

    public static boolean hasBlockLineOfSight(Minecraft mc, Vec3 from, Vec3 to) {
        if (mc == null || mc.level == null || mc.player == null) return true;
        BlockHitResult bhr = mc.level.clip(new ClipContext(
                from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player
        ));
        return bhr.getType() == HitResult.Type.MISS;
    }

    public static boolean raycastTarget(Minecraft mc,
                                        LivingEntity target,
                                        Vec3 lookDir,
                                        double reach,
                                        double margin,
                                        boolean throughWalls) {
        if (mc == null || mc.player == null || target == null || lookDir == null) return false;
        Vec3 eyes = mc.player.getEyePosition();
        double rayDist = reach + 0.05;
        Vec3 end = eyes.add(lookDir.scale(rayDist));

        if (!throughWalls && mc.level != null) {
            BlockHitResult bhr = mc.level.clip(new ClipContext(
                    eyes, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player
            ));
            if (bhr.getType() != HitResult.Type.MISS) {
                end = bhr.getLocation();
            }
        }

        AABB box = margin > 1.0E-4 ? target.getBoundingBox().inflate(margin) : target.getBoundingBox();
        Optional<Vec3> hit = box.clip(eyes, end);
        if (hit.isEmpty() && !box.contains(eyes)) {
            return false;
        }

        Vec3 hitPoint = hit.orElse(eyes);
        double hitDistSq = hit.isPresent() ? eyes.distanceToSqr(hitPoint) : 0.0;

        AABB searchBox = mc.player.getBoundingBox().expandTowards(lookDir.scale(rayDist)).inflate(1.0);
        EntityHitResult blocker = ProjectileUtil.getEntityHitResult(
                mc.player,
                eyes,
                hit.isPresent() ? hitPoint : end,
                searchBox,
                e -> e != mc.player && e != target && e.isPickable() && e.isAlive() && !e.isSpectator(),
                Math.max(0.01, hitDistSq)
        );
        if (blocker != null) {
            double blockerDistSq = eyes.distanceToSqr(blocker.getLocation());
            if (blockerDistSq < hitDistSq - 1.0E-4) {
                return false;
            }
        }
        return true;
    }
}
