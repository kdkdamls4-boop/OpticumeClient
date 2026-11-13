package dev.optimusprime.client.modules.optimization;

import dev.optimusprime.client.modules.Category;
import dev.optimusprime.client.modules.Module;
import dev.optimusprime.client.setting.BooleanSetting;
import dev.optimusprime.client.setting.ModeSetting;
import dev.optimusprime.client.setting.NumberSetting;

import java.util.concurrent.CompletableFuture;

public class MemoryCleanerModule extends Module {
    private static MemoryCleanerModule INSTANCE;

    private final ModeSetting mode = new ModeSetting("Mode", "Режим очистки", "Smart Trim", "Smart Trim", "Aggressive GC");
    private final NumberSetting interval = new NumberSetting("Interval", "Интервал очистки (сек)", 30.0, 5.0, 120.0, 5.0);
    private final BooleanSetting cleanRenderCaches = new BooleanSetting("Clean Render Caches", "Очистка кэшей и пулов рендера", true);
    private final NumberSetting threshold = new NumberSetting("Threshold", "Порог памяти (%)", 85.0, 60.0, 95.0, 5.0);

    private long lastCleanTime = 0;

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

        boolean shouldClean = false;
        if (mode.is("Smart Trim")) {
            boolean hasScreen = mc.gui != null && mc.gui.screen() != null;
            if (usagePercent >= threshold.getValue() && (hasScreen || usagePercent >= 92.0)) {
                shouldClean = true;
            }
        } else if (mode.is("Aggressive GC")) {
            if (usagePercent >= threshold.getValue()) {
                shouldClean = true;
            }
        }

        if (shouldClean) {
            if (cleanRenderCaches.isEnabled()) {
                if (mc.particleEngine != null) {
                    try {
                        mc.particleEngine.clearParticles();
                    } catch (Throwable ignored) {
                    }
                }
            }
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
