package dev.optimusprime.client.render;

public class Animation {
    private float startValue;
    private float targetValue;
    private float currentValue;
    private long startTime;
    private long durationMs;
    private Easing easing;

    public Animation(float initialValue, long durationMs, Easing easing) {
        this.startValue = initialValue;
        this.targetValue = initialValue;
        this.currentValue = initialValue;
        this.durationMs = Math.max(1L, durationMs);
        this.easing = easing != null ? easing : Easing.EASE_OUT_CUBIC;
        this.startTime = System.currentTimeMillis();
    }

    public void animateTo(float newTarget) {
        animateTo(newTarget, this.durationMs, this.easing);
    }

    public void animateTo(float newTarget, long durationMs, Easing easing) {
        if (Math.abs(this.targetValue - newTarget) < 0.0001f) {
            return;
        }
        this.startValue = getValue();
        this.targetValue = newTarget;
        this.durationMs = Math.max(1L, durationMs);
        if (easing != null) {
            this.easing = easing;
        }
        this.startTime = System.currentTimeMillis();
    }

    public float getValue() {
        long elapsed = System.currentTimeMillis() - startTime;
        if (elapsed >= durationMs) {
            currentValue = targetValue;
            return currentValue;
        }
        float progress = Math.max(0.0f, Math.min(1.0f, (float) elapsed / (float) durationMs));
        float eased = easing.apply(progress);
        currentValue = startValue + (targetValue - startValue) * eased;
        return currentValue;
    }

    public void setImmediate(float value) {
        this.startValue = value;
        this.targetValue = value;
        this.currentValue = value;
        this.startTime = 0L;
    }

    public float getTargetValue() {
        return targetValue;
    }

    public boolean isFinished() {
        return (System.currentTimeMillis() - startTime) >= durationMs;
    }

    public static float lerp(float current, float target, float speed) {
        float diff = target - current;
        if (Math.abs(diff) < 0.001f) return target;
        return current + diff * Math.max(0.01f, Math.min(1.0f, speed));
    }
}
