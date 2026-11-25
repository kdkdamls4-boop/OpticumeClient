package dev.opticumeclient.client.combat;

import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

/**
 * Centralized Silent Rotation & MoveFix engine for OpticumeClient (MC 26.3).
 * Features:
 * - Priority-based rotation requests across all combat & utility modules
 * - Exact vanilla mouse-sensitivity GCD quantization with residual carry
 * - Smooth angular turn clamping & smooth return to camera angles (zero snap-back)
 * - Full MoveFix (OFF / FOCUS / FREE with Grim-compliant 8-way input quantization)
 * - 3rd-person visual head/pitch synchronization via LivingEntityRendererMixin
 */
public final class RotationManager {
    private static final RotationManager INSTANCE = new RotationManager();

    public enum MoveFixMode {
        OFF,
        FOCUS,
        FREE;

        public static MoveFixMode fromString(String s) {
            if (s == null) return FREE;
            return switch (s.toUpperCase()) {
                case "OFF", "NONE" -> OFF;
                case "FOCUS", "STRICT" -> FOCUS;
                default -> FREE;
            };
        }
    }

    public record RotationRequest(
            String owner,
            float yaw,
            float pitch,
            int priority,
            MoveFixMode moveFix,
            float maxTurnYaw,
            float maxTurnPitch,
            boolean gcdFix,
            float returnSpeed,
            int holdTicks
    ) {
    }

    private final Minecraft mc = Minecraft.getInstance();

    private boolean active = false;
    private boolean returning = false;
    private float serverYaw = 0.0f;
    private float serverPitch = 0.0f;
    private float prevServerYaw = 0.0f;
    private float prevServerPitch = 0.0f;

    private float gcdResidualYaw = 0.0f;
    private float gcdResidualPitch = 0.0f;

    private MoveFixMode activeMoveFix = MoveFixMode.FREE;
    private float activeReturnSpeed = 48.0f;
    private boolean activeGcdFix = true;
    private int holdRemaining = 0;
    private String activeOwner = "";

    private RotationRequest pendingRequest = null;
    private boolean steppedThisTick = false;
    private int appliedPriorityThisTick = Integer.MIN_VALUE;

    private float clientGcdResidualYaw = 0.0f;
    private float clientGcdResidualPitch = 0.0f;
    private int wTapTicksRemaining = 0;
    private float lastCorrectedForward = 1.0f;

    // Temporary storage during LocalPlayer.sendPosition()
    private float savedClientYaw;
    private float savedClientPitch;
    private boolean swappedForPacket = false;

    private RotationManager() {
    }

    public static RotationManager getInstance() {
        return INSTANCE;
    }

    public boolean isActive() {
        return active && mc.player != null;
    }

    public boolean isReturning() {
        return active && returning;
    }

    public float getServerYaw() {
        return active ? serverYaw : (mc.player != null ? mc.player.getYRot() : 0.0f);
    }

    public float getServerPitch() {
        return active ? serverPitch : (mc.player != null ? mc.player.getXRot() : 0.0f);
    }

    public float getRenderYaw(float partialTicks) {
        if (!active) return mc.player != null ? mc.player.getYRot() : 0.0f;
        return Mth.rotLerp(partialTicks, prevServerYaw, serverYaw);
    }

    public float getRenderPitch(float partialTicks) {
        if (!active) return mc.player != null ? mc.player.getXRot() : 0.0f;
        return Mth.lerp(partialTicks, prevServerPitch, serverPitch);
    }

    public MoveFixMode getMoveFixMode() {
        return active ? activeMoveFix : MoveFixMode.OFF;
    }

    public String getActiveOwner() {
        return active ? activeOwner : "";
    }

    public boolean shouldCorrectMove() {
        return isActive() && activeMoveFix != MoveFixMode.OFF;
    }

    public Vec3 getViewVector() {
        if (isActive()) {
            return Entity.calculateViewVector(serverPitch, serverYaw);
        }
        return mc.player != null ? mc.player.getViewVector(1.0f) : Vec3.ZERO;
    }

    /**
     * Submits a rotation request for the current tick. Higher priority wins; equal priority refreshes.
     */
    public void request(RotationRequest request) {
        if (request == null || mc.player == null) return;
        if (request.priority() < appliedPriorityThisTick) return;
        if (pendingRequest == null || request.priority() >= pendingRequest.priority()) {
            pendingRequest = request;
        }
    }

    /**
     * Convenience helper for submitting a silent rotation request.
     */
    public void request(String owner, float yaw, float pitch, int priority, MoveFixMode moveFix,
                        float maxTurnYaw, float maxTurnPitch, boolean gcdFix) {
        request(new RotationRequest(owner, yaw, pitch, priority, moveFix,
                maxTurnYaw, maxTurnPitch, gcdFix, 48.0f, 2));
    }

    /**
     * Immediately snaps/steps the server rotation (e.g. for same-tick crystal/potion/pearl use)
     * ONLY if this request wins priority arbitration, and registers the request for sendPosition().
     * @return true if this request won priority and was applied.
     */
    public boolean requestImmediate(String owner, float yaw, float pitch, int priority, MoveFixMode moveFix, boolean gcdFix) {
        if (mc.player == null) return false;
        if (priority < appliedPriorityThisTick) {
            return false;
        }
        if (pendingRequest != null && priority < pendingRequest.priority()) {
            return false;
        }
        RotationRequest req = new RotationRequest(owner, yaw, pitch, priority, moveFix, 180.0f, 180.0f, gcdFix, 65.0f, 2);
        pendingRequest = req;
        appliedPriorityThisTick = priority;
        if (!active) {
            serverYaw = mc.player.getYRot();
            serverPitch = mc.player.getXRot();
            prevServerYaw = serverYaw;
            prevServerPitch = serverPitch;
            active = true;
        }
        stepTowards(yaw, pitch, 180.0f, 180.0f, gcdFix);
        activeMoveFix = moveFix;
        activeGcdFix = gcdFix;
        activeReturnSpeed = 65.0f;
        holdRemaining = 2;
        returning = false;
        activeOwner = owner;
        return true;
    }

    /**
     * Applies an immediate silent rotation, sends a matching ServerboundMovePlayerPacket.Rot,
     * temporarily applies the silent yaw/pitch to LocalPlayer while executing {@code action}
     * (so ServerboundUseItemPacket captures the exact silent angles), and restores camera angles.
     */
    public boolean runWithSilentAngles(String owner, float yaw, float pitch, int priority, MoveFixMode moveFix,
                                        boolean gcdFix, Runnable action) {
        if (mc.player == null || action == null) return false;
        boolean applied = requestImmediate(owner, yaw, pitch, priority, moveFix, gcdFix);
        if (!applied) {
            return false;
        }
        if (mc.player.connection != null) {
            mc.player.connection.send(new ServerboundMovePlayerPacket.Rot(
                    serverYaw, serverPitch, mc.player.onGround(), mc.player.horizontalCollision
            ));
        }
        float oldYaw = mc.player.getYRot();
        float oldPitch = mc.player.getXRot();
        mc.player.setYRot(serverYaw);
        mc.player.setXRot(serverPitch);
        try {
            action.run();
        } finally {
            mc.player.setYRot(oldYaw);
            mc.player.setXRot(oldPitch);
        }
        return true;
    }

    public void reset() {
        active = false;
        returning = false;
        pendingRequest = null;
        steppedThisTick = false;
        appliedPriorityThisTick = Integer.MIN_VALUE;
        holdRemaining = 0;
        gcdResidualYaw = 0.0f;
        gcdResidualPitch = 0.0f;
        clientGcdResidualYaw = 0.0f;
        clientGcdResidualPitch = 0.0f;
        wTapTicksRemaining = 0;
        lastCorrectedForward = 1.0f;
        activeOwner = "";
        swappedForPacket = false;
    }

    public void resetClientGcdResidual() {
        clientGcdResidualYaw = 0.0f;
        clientGcdResidualPitch = 0.0f;
    }

    public void requestWTap(int ticks) {
        if (ticks > wTapTicksRemaining) {
            wTapTicksRemaining = Math.min(4, ticks);
        }
    }

    public boolean isWTapActive() {
        return wTapTicksRemaining > 0;
    }

    public boolean shouldBlockSprint() {
        return wTapTicksRemaining > 0 || (isActive() && activeMoveFix == MoveFixMode.FREE && lastCorrectedForward <= 0.0f);
    }

    /**
     * Pre-tick rotation step executed in START_CLIENT_TICK before LocalPlayer.applyInput() / aiStep() / sendPosition().
     * Only processes active pending requests so END_CLIENT_TICK modules can still submit late requests cleanly.
     */
    public void onPreTick() {
        steppedThisTick = false;
        if (mc.player == null || mc.level == null) {
            reset();
            return;
        }
        if (pendingRequest != null) {
            prevServerYaw = active ? serverYaw : mc.player.getYRot();
            prevServerPitch = active ? serverPitch : mc.player.getXRot();
            RotationRequest req = pendingRequest;
            pendingRequest = null;
            appliedPriorityThisTick = Math.max(appliedPriorityThisTick, req.priority());
            applyRequest(req);
            steppedThisTick = true;
        }
    }

    /**
     * Compatibility alias forwarding to post-tick arbitration.
     */
    public void onTick() {
        onPostTick();
    }

    /**
     * Post-tick arbitration executed in END_CLIENT_TICK: handles late requests, W-Tap decay, hold ticks, and smooth return.
     */
    public void onPostTick() {
        try {
            if (mc.player == null || mc.level == null) {
                reset();
                return;
            }

            if (wTapTicksRemaining > 0) {
                wTapTicksRemaining--;
            }

            if (pendingRequest != null) {
                if (!steppedThisTick) {
                    prevServerYaw = active ? serverYaw : mc.player.getYRot();
                    prevServerPitch = active ? serverPitch : mc.player.getXRot();
                }
                RotationRequest req = pendingRequest;
                pendingRequest = null;
                applyRequest(req);
                steppedThisTick = false;
                return;
            }

            if (steppedThisTick) {
                steppedThisTick = false;
                return;
            }

            if (!active) {
                return;
            }

            prevServerYaw = serverYaw;
            prevServerPitch = serverPitch;

            if (holdRemaining > 0) {
                holdRemaining--;
                return;
            }

            // Smooth return to real client camera angles (prevents anticheat snap-back flags)
            returning = true;
            float targetYaw = mc.player.getYRot();
            float targetPitch = mc.player.getXRot();
            float dy = Math.abs(Mth.wrapDegrees(targetYaw - serverYaw));
            float dp = Math.abs(targetPitch - serverPitch);

            if (dy < 1.5f && dp < 1.5f) {
                active = false;
                returning = false;
                activeOwner = "";
                gcdResidualYaw = 0.0f;
                gcdResidualPitch = 0.0f;
            } else {
                stepTowards(targetYaw, targetPitch, activeReturnSpeed, activeReturnSpeed * 0.85f, activeGcdFix);
            }
        } finally {
            appliedPriorityThisTick = Integer.MIN_VALUE;
        }
    }

    private void applyRequest(RotationRequest req) {
        if (!active) {
            serverYaw = mc.player.getYRot();
            serverPitch = mc.player.getXRot();
            prevServerYaw = serverYaw;
            prevServerPitch = serverPitch;
            gcdResidualYaw = 0.0f;
            gcdResidualPitch = 0.0f;
            active = true;
        }

        returning = false;
        activeMoveFix = req.moveFix();
        activeGcdFix = req.gcdFix();
        activeReturnSpeed = Math.max(8.0f, req.returnSpeed());
        holdRemaining = Math.max(0, req.holdTicks());
        activeOwner = req.owner();

        stepTowards(req.yaw(), req.pitch(), req.maxTurnYaw(), req.maxTurnPitch(), req.gcdFix());
    }

    private void stepTowards(float targetYaw, float targetPitch, float maxYawStep, float maxPitchStep, boolean gcdFix) {
        float clampedTargetPitch = Mth.clamp(targetPitch, -90.0f, 90.0f);
        float deltaYaw = Mth.wrapDegrees(targetYaw - serverYaw);
        float deltaPitch = clampedTargetPitch - serverPitch;

        float stepYaw = Mth.clamp(deltaYaw, -maxYawStep, maxYawStep);
        float stepPitch = Mth.clamp(deltaPitch, -maxPitchStep, maxPitchStep);

        if (gcdFix) {
            double sens = (mc != null && mc.options != null) ? mc.options.sensitivity().get() : 0.5;
            double f = sens * 0.6 + 0.2;
            double h = f * f * f * 8.0;
            double gcd = h * 0.15;
            if (gcd > 1.0E-5) {
                float totalYaw = stepYaw + gcdResidualYaw;
                float totalPitch = stepPitch + gcdResidualPitch;
                int pxYaw = (int) Math.round(totalYaw / gcd);
                int pxPitch = (int) Math.round(totalPitch / gcd);

                // Anti-zero-pitch guard during large horizontal swings for Matrix/Grim
                if (!returning && Math.abs(pxYaw * gcd) >= 3.5 && pxPitch == 0) {
                    int nudge = deltaPitch >= 0.0f ? 1 : -1;
                    float candidatePitch = serverPitch + (float) (nudge * h) * 0.15f;
                    if (candidatePitch > 90.0f || candidatePitch < -90.0f) {
                        nudge = -nudge;
                        candidatePitch = serverPitch + (float) (nudge * h) * 0.15f;
                    }
                    if (candidatePitch >= -90.0f && candidatePitch <= 90.0f) {
                        pxPitch = nudge;
                    }
                }

                // Pole clamp by pixel increments so GCD divisibility never breaks at +/-90
                while (pxPitch > 0 && serverPitch + (float) (pxPitch * h) * 0.15f > 90.0f) {
                    pxPitch--;
                }
                while (pxPitch < 0 && serverPitch + (float) (pxPitch * h) * 0.15f < -90.0f) {
                    pxPitch++;
                }

                float qYaw = (float) (pxYaw * h) * 0.15f;
                float qPitch = (float) (pxPitch * h) * 0.15f;
                gcdResidualYaw = Mth.clamp(totalYaw - qYaw, (float) -gcd, (float) gcd);
                gcdResidualPitch = Mth.clamp(totalPitch - qPitch, (float) -gcd, (float) gcd);
                stepYaw = qYaw;
                stepPitch = qPitch;
            }
        }

        serverYaw += stepYaw;
        serverPitch = Mth.clamp(serverPitch + stepPitch, -90.0f, 90.0f);
    }

    /**
     * Quantizes high-FPS Client-mode camera deltas to exact vanilla mouse pixels with persistent sub-pixel residual.
     */
    public float[] quantizeClientDelta(float stepYaw, float stepPitch, float currentPitch) {
        double sens = (mc != null && mc.options != null) ? mc.options.sensitivity().get() : 0.5;
        double f = sens * 0.6 + 0.2;
        double h = f * f * f * 8.0;
        double gcd = h * 0.15;
        if (gcd <= 1.0E-5) {
            return new float[]{stepYaw, Mth.clamp(currentPitch + stepPitch, -90.0f, 90.0f) - currentPitch};
        }
        float totalYaw = stepYaw + clientGcdResidualYaw;
        float totalPitch = stepPitch + clientGcdResidualPitch;
        int pxYaw = (int) Math.round(totalYaw / gcd);
        int pxPitch = (int) Math.round(totalPitch / gcd);

        while (pxPitch > 0 && currentPitch + (float) (pxPitch * h) * 0.15f > 90.0f) {
            pxPitch--;
        }
        while (pxPitch < 0 && currentPitch + (float) (pxPitch * h) * 0.15f < -90.0f) {
            pxPitch++;
        }

        float qYaw = (float) (pxYaw * h) * 0.15f;
        float qPitch = (float) (pxPitch * h) * 0.15f;
        clientGcdResidualYaw = Mth.clamp(totalYaw - qYaw, (float) -gcd, (float) gcd);
        clientGcdResidualPitch = Mth.clamp(totalPitch - qPitch, (float) -gcd, (float) gcd);
        return new float[]{qYaw, qPitch};
    }

    public static double getMouseGcd(Minecraft mc) {
        if (mc == null || mc.options == null) return 0.15;
        double sens = mc.options.sensitivity().get();
        double f = sens * 0.6 + 0.2;
        return f * f * f * 8.0 * 0.15;
    }

    public static float[] getRotationsTo(Vec3 from, Vec3 to) {
        Vec3 diff = to.subtract(from);
        double horiz = Math.sqrt(diff.x * diff.x + diff.z * diff.z);
        float yaw = (float) (Math.toDegrees(Math.atan2(diff.z, diff.x)) - 90.0);
        float pitch = (float) -Math.toDegrees(Math.atan2(diff.y, horiz));
        return new float[]{yaw, Mth.clamp(pitch, -90.0f, 90.0f)};
    }

    /**
     * Rotates raw WASD input vector (x=strafe, y=forward) so that moving with serverYaw
     * results in the player moving toward their real camera direction (FREE MoveFix).
     * Quantized to discrete {-1, 0, +1} steps to pass strict Grim/Polar input simulation checks.
     */
    public Vec2 correctMoveInput(Vec2 rawInput) {
        if (wTapTicksRemaining > 0) {
            lastCorrectedForward = 0.0f;
            return new Vec2(rawInput != null ? rawInput.x : 0.0f, 0.0f);
        }
        if (!isActive() || activeMoveFix != MoveFixMode.FREE || mc.player == null || rawInput == null) {
            lastCorrectedForward = rawInput != null ? rawInput.y : 0.0f;
            return rawInput;
        }
        float strafe = rawInput.x;
        float forward = rawInput.y;
        if (Math.abs(strafe) < 1.0E-4f && Math.abs(forward) < 1.0E-4f) {
            lastCorrectedForward = 0.0f;
            return rawInput;
        }

        float diffRad = (float) Math.toRadians(Mth.wrapDegrees(mc.player.getYRot() - serverYaw));
        float cos = Mth.cos(diffRad);
        float sin = Mth.sin(diffRad);

        float rotForward = forward * cos + strafe * sin;
        float rotStrafe = strafe * cos - forward * sin;

        // Discrete 8-way quantization matching vanilla keyboard states
        float qForward = Math.abs(rotForward) > 0.2f ? Math.signum(rotForward) : 0.0f;
        float qStrafe = Math.abs(rotStrafe) > 0.2f ? Math.signum(rotStrafe) : 0.0f;
        if (qForward == 0.0f && qStrafe == 0.0f) {
            if (Math.abs(rotForward) >= Math.abs(rotStrafe)) {
                qForward = Math.signum(rotForward);
            } else {
                qStrafe = Math.signum(rotStrafe);
            }
        }
        lastCorrectedForward = qForward;
        return new Vec2(qStrafe, qForward);
    }

    /**
     * Called at HEAD of LocalPlayer.sendPosition() to spoof outgoing rotation packets silently.
     */
    public void onBeforeSendPosition() {
        if (!isActive() || mc.player == null) {
            swappedForPacket = false;
            return;
        }
        savedClientYaw = mc.player.getYRot();
        savedClientPitch = mc.player.getXRot();
        mc.player.setYRot(serverYaw);
        mc.player.setXRot(serverPitch);
        swappedForPacket = true;
    }

    /**
     * Called at RETURN/TAIL of LocalPlayer.sendPosition() to restore the player's camera rotation.
     */
    public void onAfterSendPosition() {
        if (swappedForPacket && mc.player != null) {
            mc.player.setYRot(savedClientYaw);
            mc.player.setXRot(savedClientPitch);
            swappedForPacket = false;
        }
    }
}
