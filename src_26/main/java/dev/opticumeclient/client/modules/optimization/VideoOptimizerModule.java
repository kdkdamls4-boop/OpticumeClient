package dev.opticumeclient.client.modules.optimization;

import dev.opticumeclient.client.modules.Category;
import dev.opticumeclient.client.modules.Module;
import dev.opticumeclient.client.setting.BooleanSetting;
import dev.opticumeclient.client.setting.MultiBoxSetting;
import dev.opticumeclient.client.setting.NumberSetting;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.server.level.ParticleStatus;

import java.util.ArrayList;
import java.util.List;

/**
 * Ported from Desire OptimizationModule:
 * Safely tunes vanilla graphics & view-distance options when enabled and cleanly restores
 * the user's original values when disabled.
 */
public class VideoOptimizerModule extends Module {
    private static VideoOptimizerModule INSTANCE;

    private final MultiBoxSetting video = new MultiBoxSetting(
            "Video Tweaks",
            "Отключаемые тяжёлые эффекты ванильного рендера",
            new String[]{
                    "Clouds Off",
                    "Minimal Particles",
                    "Entity Shadows Off",
                    "Smooth Lighting Off",
                    "Biome Blend Off",
                    "Vignette Off",
                    "Weather Radius Min",
                    "Menu Blur Off",
                    "Improved Transparency Off",
                    "Chunk Fade Off",
                    "Fast Leaves"
            },
            "Clouds Off", "Minimal Particles", "Entity Shadows Off", "Vignette Off",
            "Weather Radius Min", "Menu Blur Off", "Improved Transparency Off"
    );

    private final BooleanSetting limitRender = new BooleanSetting("Limit Render Dist", "Ограничить дальность прорисовки", false);
    private final NumberSetting renderDistance = new NumberSetting("Render Distance", "Макс. чанков прорисовки", 10.0, 2.0, 32.0, 1.0);
    private final BooleanSetting limitSimulation = new BooleanSetting("Limit Sim Dist", "Ограничить дальность симуляции", false);
    private final NumberSetting simulationDistance = new NumberSetting("Simulation Dist", "Макс. чанков симуляции", 8.0, 5.0, 32.0, 1.0);
    private final NumberSetting entityScaling = new NumberSetting("Entity Dist Scale", "Множитель дистанции сущностей", 0.75, 0.5, 5.0, 0.25);

    private final List<Runnable> restores = new ArrayList<>();
    private int lastOptionsSignature = 0;

    public VideoOptimizerModule() {
        super("VideoOptimizer", "Тонкая оптимизация настроек графики с автовосстановлением", Category.OPTIMIZATION);
        renderDistance.visibleWhen(limitRender::isEnabled);
        simulationDistance.visibleWhen(limitSimulation::isEnabled);

        addSetting(video);
        addSetting(limitRender);
        addSetting(renderDistance);
        addSetting(limitSimulation);
        addSetting(simulationDistance);
        addSetting(entityScaling);

        INSTANCE = this;
        setEnabled(true);
    }

    public static VideoOptimizerModule getInstance() {
        return INSTANCE;
    }

    @Override
    public void onEnable() {
        applyOptions();
    }

    @Override
    public void onDisable() {
        restoreOptions();
    }

    @Override
    public void onTick() {
        if (!isEnabled() || mc.options == null) return;
        int sig = computeSignature();
        if (sig != lastOptionsSignature) {
            restoreOptions();
            applyOptions();
        }
    }

    private int computeSignature() {
        int h = 1;
        for (String opt : video.getOptions()) {
            h = 31 * h + (video.isEnabled(opt) ? 1 : 0);
        }
        h = 31 * h + (limitRender.isEnabled() ? renderDistance.getIntValue() : -1);
        h = 31 * h + (limitSimulation.isEnabled() ? simulationDistance.getIntValue() : -1);
        h = 31 * h + (int) Math.round(entityScaling.getValue() * 100.0);
        return h;
    }

    private void applyOptions() {
        if (mc.options == null) return;
        Options options = mc.options;
        lastOptionsSignature = computeSignature();

        try {
            if (video.isEnabled("Clouds Off")) override(options.cloudStatus(), CloudStatus.OFF);
            if (video.isEnabled("Minimal Particles")) override(options.particles(), ParticleStatus.MINIMAL);
            if (video.isEnabled("Entity Shadows Off")) override(options.entityShadows(), false);
            if (video.isEnabled("Smooth Lighting Off")) override(options.ambientOcclusion(), false);
            if (video.isEnabled("Biome Blend Off")) override(options.biomeBlendRadius(), 0);
            if (video.isEnabled("Vignette Off")) override(options.vignette(), false);
            if (video.isEnabled("Weather Radius Min")) override(options.weatherRadius(), 3);
            if (video.isEnabled("Menu Blur Off")) override(options.menuBackgroundBlurriness(), 0);
            if (video.isEnabled("Improved Transparency Off")) override(options.improvedTransparency(), false);
            if (video.isEnabled("Chunk Fade Off")) override(options.chunkSectionFadeInTime(), 0.0);
            if (video.isEnabled("Fast Leaves")) override(options.cutoutLeaves(), false);

            if (limitRender.isEnabled()) {
                int cap = renderDistance.getIntValue();
                if (options.renderDistance().get() > cap) {
                    override(options.renderDistance(), cap);
                }
            }
            if (limitSimulation.isEnabled()) {
                int cap = simulationDistance.getIntValue();
                if (options.simulationDistance().get() > cap) {
                    override(options.simulationDistance(), cap);
                }
            }
            double scaleCap = entityScaling.getValue();
            if (options.entityDistanceScaling().get() > scaleCap) {
                override(options.entityDistanceScaling(), scaleCap);
            }
        } catch (Throwable ignored) {
        }
    }

    private void restoreOptions() {
        for (int i = restores.size() - 1; i >= 0; i--) {
            try {
                restores.get(i).run();
            } catch (Throwable ignored) {
            }
        }
        restores.clear();
    }

    private <T> void override(OptionInstance<T> option, T value) {
        if (option == null || value == null) return;
        T original = option.get();
        if (value.equals(original)) return;
        restores.add(() -> option.set(original));
        option.set(value);
    }
}
