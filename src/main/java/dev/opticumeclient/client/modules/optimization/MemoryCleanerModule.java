package dev.opticumeclient.client.modules.optimization;

import dev.opticumeclient.client.modules.Category;
import dev.opticumeclient.client.modules.Module;
import dev.opticumeclient.client.modules.visuals.NameTagsModule;
import dev.opticumeclient.client.setting.BooleanSetting;
import dev.opticumeclient.client.setting.ModeSetting;
import dev.opticumeclient.client.setting.NumberSetting;

import java.util.concurrent.CompletableFuture;

public class MemoryCleanerModule extends Module {
    private static MemoryCleanerModule INSTANCE;
    private static final long AGGRESSIVE_GC_MIN_COOLDOWN_MS = 90_000L;

    private final ModeSetting mode = new ModeSetting("Mode", "Smart Trim чистит кэши без STW-фризов; Aggressive GC вызывает System.gc() в меню", "Smart Trim", "Smart Trim", "Aggressive GC");
    private final NumberSetting interval = new NumberSetting("Interval", "Интервал очистки (сек)", 30.0, 5.0, 120.0, 5.0);
    private final BooleanSetting cleanRenderCaches = new BooleanSetting("Clean Render Caches", "Очистка кэшей и пулов рендера", true);
    private final NumberSetting threshold = new NumberSetting("Threshold", "Порог памяти (%)", 85.0, 60.0, 95.0, 5.0);

    private long lastCleanTime = 0L;
    private long lastExplicitGcTime = 0L;

    public MemoryCleanerModule() {
        super("MemoryCleaner", "Автоматическая очистка оперативной памяти RAM", Category.OPTIMIZATION);
        addSetting(mode);
        addSetting(interval);
        addSetting(cleanRenderCaches);
        addSetting(threshold);
        INSTANCE = this;
        setEnabled(true);
    }

    public static MemoryCleanerModule getInstance() {
        return INSTANCE;
    }

    @Override
    public void onTick() {
        if (!isEnabled()) return;

        long now = System.currentTimeMillis();
        long intervalMillis = (long) (interval.getValue() * 1000L);
        if (now - lastCleanTime < intervalMillis) return;
        lastCleanTime = now;

        Runtime runtime = Runtime.getRuntime();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;
        double usagePercent = ((double) usedMemory / runtime.maxMemory()) * 100.0;

        boolean hasScreen = mc.gui != null && mc.gui.screen() != null;
        boolean shouldTrimCaches = usagePercent >= threshold.getValue() || (cleanRenderCaches.isEnabled() && usagePercent >= 70.0);
        if (shouldTrimCaches && cleanRenderCaches.isEnabled()) {
            if (mc.particleEngine != null && usagePercent >= threshold.getValue()) {
                try {
                    mc.particleEngine.clearParticles();
                } catch (Throwable ignored) {
                }
            }
            NameTagsModule nameTags = NameTagsModule.getInstance();
            if (nameTags != null) {
                nameTags.trimStaleStates();
            }
        }

        // Never invoke blocking System.gc() in Smart Trim (prevents 1-2s Stop-The-World Full GC freezes).
        // Explicit GC is strictly opt-in via Aggressive GC, only while a menu/screen is open, and rate-limited.
        if (mode.is("Aggressive GC")
                && hasScreen
                && usagePercent >= threshold.getValue()
                && now - lastExplicitGcTime >= AGGRESSIVE_GC_MIN_COOLDOWN_MS) {
            lastExplicitGcTime = now;
            CompletableFuture.runAsync(System::gc);
        }
    }

    @Override
    public String getDisplaySuffix() {
        return mode.getValue();
    }

    public ModeSetting getMode() {
        return mode;
    }

    public NumberSetting getInterval() {
        return interval;
    }

    public BooleanSetting getCleanRenderCaches() {
        return cleanRenderCaches;
    }

    public NumberSetting getThreshold() {
        return threshold;
    }
}
