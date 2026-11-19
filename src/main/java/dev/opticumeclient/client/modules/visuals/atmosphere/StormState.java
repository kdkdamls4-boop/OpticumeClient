package dev.opticumeclient.client.modules.visuals.atmosphere;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Procedural storm, wind, precipitation offset, and lightning strike state for AtmosphereModule.
 */
public final class StormState {
    private float elapsedSeconds = 0.0f;
    private long lastNanoTime = 0L;

    private float nextStrikeAt = 4.5f;
    private float boltAge = -1.0f;
    private float boltDuration = 0.48f;
    private float boltSeed = 1.0f;
    private float boltAzimuth = 0.4f;
    private float boltBranches = 3.0f;

    private float windAngle = 0.65f;
    private float windDirX = 0.78f;
    private float windDirZ = 0.62f;

    public void update(boolean lightningEnabled) {
        long now = System.nanoTime();
        if (lastNanoTime == 0L) {
            lastNanoTime = now;
            return;
        }
        float dt = Math.min(0.1f, Math.max(0.0f, (now - lastNanoTime) / 1.0e9f));
        lastNanoTime = now;
        elapsedSeconds += dt;

        windAngle += dt * 0.04f;
        windDirX = (float) Math.cos(windAngle);
        windDirZ = (float) Math.sin(windAngle);

        if (!lightningEnabled) {
            boltAge = -1.0f;
            return;
        }

        if (boltAge >= 0.0f) {
            boltAge += dt;
            if (boltAge >= boltDuration) {
                boltAge = -1.0f;
            }
        }

        if (elapsedSeconds >= nextStrikeAt) {
            ThreadLocalRandom rng = ThreadLocalRandom.current();
            boltAge = 0.0f;
            boltDuration = 0.36f + rng.nextFloat() * 0.28f;
            boltSeed = 10.0f + rng.nextFloat() * 200.0f;
            boltAzimuth = (float) (rng.nextDouble() * Math.PI * 2.0);
            boltBranches = 1 + rng.nextInt(4);
            nextStrikeAt = elapsedSeconds + 3.5f + rng.nextFloat() * 7.5f;
        }
    }

    public void reset() {
        lastNanoTime = 0L;
        boltAge = -1.0f;
        nextStrikeAt = 3.0f;
    }

    public float getElapsedSeconds() {
        return elapsedSeconds;
    }

    public float getWindDirX() {
        return windDirX;
    }

    public float getWindDirZ() {
        return windDirZ;
    }

    public boolean isBoltActive() {
        return boltAge >= 0.0f && boltAge < boltDuration;
    }

    public float getFlashIntensity() {
        if (!isBoltActive()) {
            return 0.0f;
        }
        float t = boltAge / Math.max(0.05f, boltDuration);
        float primary = (float) Math.exp(-t * 9.0);
        float restrike = (float) Math.exp(-Math.abs(t - 0.28f) * 16.0) * 0.72f;
        return Math.min(1.0f, (primary + restrike) * 0.85f);
    }

    public float getBoltIntensity() {
        if (!isBoltActive()) {
            return 0.0f;
        }
        float t = boltAge / Math.max(0.05f, boltDuration);
        float flicker = 0.8f + 0.2f * (float) Math.sin(elapsedSeconds * 90.0f);
        return Math.min(1.0f, (float) Math.exp(-t * 6.5) * 1.15f * flicker);
    }

    public float getBoltSeed() {
        return boltSeed;
    }

    public float getBoltAzimuth() {
        return boltAzimuth;
    }

    public float getBoltBranches() {
        return boltBranches;
    }
}
