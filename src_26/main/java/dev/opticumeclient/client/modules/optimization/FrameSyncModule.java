package dev.opticumeclient.client.modules.optimization;

import dev.opticumeclient.client.modules.Category;
import dev.opticumeclient.client.modules.Module;
import dev.opticumeclient.client.setting.BooleanSetting;
import dev.opticumeclient.client.setting.NumberSetting;
import net.minecraft.client.Options;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

public class FrameSyncModule extends Module {
    private static FrameSyncModule INSTANCE;

    private final BooleanSetting disableVsync = new BooleanSetting("Disable VSync Overhead", "Отключение ожидания swapchain и снятие лимита FPS", true);
    private final NumberSetting particleCap = new NumberSetting("Particle Cap", "Лимит ванильных частиц", 256.0, 32.0, 2048.0, 32.0);
    private final BooleanSetting particleCulling = new BooleanSetting("Particle Culling", "Отсечение далёких частиц", true);
    private final NumberSetting particleDistance = new NumberSetting("Particle Distance", "Дистанция видимости частиц", 28.0, 8.0, 64.0, 4.0);
    private final BooleanSetting particleThinning = new BooleanSetting("Thin Distant Particles", "Прореживание далёких частиц в 2–4 раза", true);
    private final BooleanSetting simpleParticlePhysics = new BooleanSetting("Simple Particle Physics", "Упрощённая физика частиц дальше 16 блоков", true);
    private final BooleanSetting particleLightCache = new BooleanSetting("Particle Light Cache", "Кэширование освещённости частиц на тик", true);
    private final BooleanSetting dynamicEntityDistance = new BooleanSetting("Dynamic Entity Distance", "Динамическое снижение дистанции сущностей при FPS < 60", true);
    private final BooleanSetting skipUnfocused = new BooleanSetting("Background FPS Limit", "Снижение FPS при неактивном окне", true);
    private final NumberSetting backgroundFps = new NumberSetting("Background FPS", "Лимит FPS в свёрнутом окне", 20.0, 5.0, 60.0, 5.0);

    public FrameSyncModule() {
        super("FrameSync", "Оптимизация синхронизации кадров, swapchain и частиц", Category.OPTIMIZATION);
        particleDistance.visibleWhen(particleCulling::isEnabled);
        backgroundFps.visibleWhen(skipUnfocused::isEnabled);
        addSetting(disableVsync);
        addSetting(particleCap);
        addSetting(particleCulling);
        addSetting(particleDistance);
        addSetting(particleThinning);
        addSetting(simpleParticlePhysics);
        addSetting(particleLightCache);
        addSetting(dynamicEntityDistance);
        addSetting(skipUnfocused);
        addSetting(backgroundFps);
        INSTANCE = this;
        setEnabled(true);
    }

    public static FrameSyncModule getInstance() {
        return INSTANCE;
    }

    private Boolean savedVsync = null;
    private Integer savedFramerateLimit = null;

    @Override
    public void onTick() {
        if (!isEnabled() || mc.options == null) return;

        if (disableVsync.isEnabled()) {
            if (savedVsync == null) {
                savedVsync = mc.options.enableVsync().get();
            }
            if (savedFramerateLimit == null) {
                savedFramerateLimit = mc.options.framerateLimit().get();
            }
            if (mc.options.enableVsync().get()) {
                mc.options.enableVsync().set(false);
            }
            if (mc.options.framerateLimit().get() != Options.UNLIMITED_FRAMERATE_CUTOFF) {
                mc.options.framerateLimit().set(Options.UNLIMITED_FRAMERATE_CUTOFF);
            }
        } else {
            restoreDisplaySync();
        }
    }

    @Override
    public void onDisable() {
        restoreDisplaySync();
    }

    private void restoreDisplaySync() {
        if (mc.options == null) return;
        if (savedVsync != null) {
            mc.options.enableVsync().set(savedVsync);
            savedVsync = null;
        }
        if (savedFramerateLimit != null) {
            mc.options.framerateLimit().set(savedFramerateLimit);
            savedFramerateLimit = null;
        }
    }

    public boolean isParticleCapReached(int currentCount) {
        if (!isEnabled()) return false;
        return currentCount >= particleCap.getIntValue();
    }

    public boolean shouldCullDynamicEntity(Entity entity, double camX, double camY, double camZ) {
        if (!isEnabled() || !dynamicEntityDistance.isEnabled() || mc.player == null) return false;
        if (entity == mc.player) return false;

        int currentFps = mc.getFps();
        if (currentFps < 60) {
            // Do not cull player entities
            if (entity instanceof Player) {
                return false;
            }
            double dx = entity.getX() - camX;
            double dy = entity.getY() - camY;
            double dz = entity.getZ() - camZ;
            double distSq = dx * dx + dy * dy + dz * dz;

            double maxDist = (currentFps < 30) ? 16.0 : 28.0;
            return distSq > maxDist * maxDist;
        }
        return false;
    }

    public BooleanSetting getDisableVsync() {
        return disableVsync;
    }

    public NumberSetting getParticleCap() {
        return particleCap;
    }

    public BooleanSetting getDynamicEntityDistance() {
        return dynamicEntityDistance;
    }

    public BooleanSetting getSkipUnfocused() {
        return skipUnfocused;
    }

    public boolean isParticleCullingEnabled() {
        return isEnabled() && particleCulling.isEnabled();
    }

    public double getParticleDistance() {
        return particleDistance.getValue();
    }

    public boolean isParticleThinningEnabled() {
        return isEnabled() && particleThinning.isEnabled();
    }

    public boolean isSimpleParticlePhysicsEnabled() {
        return isEnabled() && simpleParticlePhysics.isEnabled();
    }

    public boolean isParticleLightCacheEnabled() {
        return isEnabled() && particleLightCache.isEnabled();
    }

    public boolean isSkipUnfocusedEnabled() {
        return isEnabled() && skipUnfocused.isEnabled();
    }

    public int getBackgroundFpsLimit() {
        return Math.max(5, backgroundFps.getIntValue());
    }
}
