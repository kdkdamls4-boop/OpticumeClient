package dev.opticumeclient.client.modules.visuals;

import com.mojang.blaze3d.platform.InputConstants;
import dev.opticumeclient.client.modules.Category;
import dev.opticumeclient.client.modules.Module;
import dev.opticumeclient.client.setting.BooleanSetting;
import dev.opticumeclient.client.setting.NumberSetting;

public class ZoomModule extends Module {
    private static ZoomModule INSTANCE;

    private final NumberSetting zoomFov = new NumberSetting("Zoom FOV", "Целевой угол обзора при приближении", 24.0, 5.0, 65.0, 1.0);
    private final NumberSetting smoothSpeed = new NumberSetting("Smooth Speed", "Плавность анимации приближения", 0.30, 0.08, 1.0, 0.02);
    private final BooleanSetting scrollZoom = new BooleanSetting("Scroll Zoom", "Регулировка кратности зума колесом мыши", true);
    private final BooleanSetting lowSensitivity = new BooleanSetting("Low Sensitivity", "Снижение чувствительности мыши при приближении", true);
    private final BooleanSetting holdMode = new BooleanSetting("Hold Mode", "Зум активен только при удержании клавиши бинда", false);

    private float currentZoomRatio = 1.0f;
    private double dynamicZoomFov = 24.0;
    private long lastUpdateNanos = 0L;
    private boolean wasZoomActive = false;

    public ZoomModule() {
        super("Zoom", "Плавное оптическое приближение камеры с прокруткой колёсиком", Category.VISUALS);
        addSetting(zoomFov);
        addSetting(smoothSpeed);
        addSetting(scrollZoom);
        addSetting(lowSensitivity);
        addSetting(holdMode);
        INSTANCE = this;
    }

    public static ZoomModule getInstance() {
        return INSTANCE;
    }

    @Override
    public String getDisplaySuffix() {
        return String.format(java.util.Locale.US, "%.0f°", zoomFov.getValue());
    }

    @Override
    public void onEnable() {
        dynamicZoomFov = zoomFov.getValue();
    }

    public boolean isHoldMode() {
        return holdMode.isEnabled();
    }

    public boolean isScrollZoom() {
        return scrollZoom.isEnabled();
    }

    public boolean isZoomActive() {
        if (mc == null || mc.player == null) return false;
        if (mc.gui != null && mc.gui.screen() != null) return false;

        if (holdMode.isEnabled() && getKeyBind() > 0) {
            return InputConstants.isKeyDown(getKeyBind());
        }
        return isEnabled();
    }

    public void adjustScrollZoom(double verticalDelta) {
        if (!scrollZoom.isEnabled() || verticalDelta == 0.0) return;
        dynamicZoomFov -= verticalDelta * 3.0;
        dynamicZoomFov = Math.max(4.0, Math.min(68.0, dynamicZoomFov));
    }

    public float applyZoomFov(float vanillaFov) {
        boolean active = isZoomActive();
        if (active && !wasZoomActive) {
            dynamicZoomFov = zoomFov.getValue();
        }
        wasZoomActive = active;

        long now = System.nanoTime();
        float dtSeconds = (lastUpdateNanos == 0L) ? 0.016f : Math.min(0.05f, (now - lastUpdateNanos) / 1_000_000_000.0f);
        lastUpdateNanos = now;

        float targetRatio = 1.0f;
        if (active && vanillaFov > 1.0f) {
            float targetFov = scrollZoom.isEnabled() ? (float) dynamicZoomFov : zoomFov.getFloatValue();
            targetRatio = Math.max(0.05f, Math.min(1.0f, targetFov / vanillaFov));
        }

        float speed = smoothSpeed.getFloatValue();
        if (speed >= 0.99f) {
            currentZoomRatio = targetRatio;
        } else {
            float alpha = 1.0f - (float) Math.pow(1.0 - speed, dtSeconds * 60.0f);
            currentZoomRatio += (targetRatio - currentZoomRatio) * Math.max(0.01f, Math.min(1.0f, alpha));
        }

        if (Math.abs(currentZoomRatio - 1.0f) < 0.001f && !active) {
            currentZoomRatio = 1.0f;
            return vanillaFov;
        }
        return vanillaFov * currentZoomRatio;
    }

    public double getSensitivityFactor() {
        if (!lowSensitivity.isEnabled() || !isZoomActive()) {
            return 1.0;
        }
        return Math.max(0.12, Math.min(1.0, (double) currentZoomRatio));
    }
}
