package dev.optimusprime.client.modules.optimization;

import dev.optimusprime.client.modules.Category;
import dev.optimusprime.client.modules.Module;
import dev.optimusprime.client.setting.BooleanSetting;
import dev.optimusprime.client.setting.NumberSetting;
import net.minecraft.client.Options;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

public class FrameSyncModule extends Module {
    private static FrameSyncModule INSTANCE;

    private final BooleanSetting disableVsync = new BooleanSetting("Disable VSync Overhead", "Отключение ожидания swapchain и снятие лимита FPS", true);
    private final NumberSetting particleCap = new NumberSetting("Particle Cap", "Лимит ванильных частиц", 128.0, 32.0, 512.0, 32.0);
    private final BooleanSetting dynamicEntityDistance = new BooleanSetting("Dynamic Entity Distance", "Динамическое снижение дистанции сущностей при FPS < 60", true);
    private final BooleanSetting skipUnfocused = new BooleanSetting("Skip Unfocused Load", "Снижение нагрузки при неактивном окне", true);

    public FrameSyncModule() {
        super("FrameSync", "Оптимизация синхронизации кадров, swapchain и частиц", Category.OPTIMIZATION);
        addSetting(disableVsync);
        addSetting(particleCap);
        addSetting(dynamicEntityDistance);
        addSetting(skipUnfocused);
        INSTANCE = this;
        setEnabled(true);
    }

    public static FrameSyncModule getInstance() {
        return INSTANCE;
    }

    private int savedFramerateLimit = -1;

    @Override
    public void onTick() {
        if (!isEnabled() || mc.options == null) return;

        if (skipUnfocused.isEnabled() && mc.getWindow() != null) {
            if (!mc.getWindow().isFocused()) {
                if (savedFramerateLimit == -1) {
                    savedFramerateLimit = mc.options.framerateLimit().get();
                }
                if (mc.options.framerateLimit().get() > 30) {
                    mc.options.framerateLimit().set(30);
                }
                return;
            } else if (savedFramerateLimit != -1) {
                if (!disableVsync.isEnabled()) {
                    mc.options.framerateLimit().set(savedFramerateLimit);
                }
                savedFramerateLimit = -1;
            }
        }

        if (disableVsync.isEnabled()) {
            if (mc.options.enableVsync().get()) {
                mc.options.enableVsync().set(false);
            }
            if (mc.options.framerateLimit().get() != Options.UNLIMITED_FRAMERATE_CUTOFF) {
                mc.options.framerateLimit().set(Options.UNLIMITED_FRAMERATE_CUTOFF);
            }
        }
    }

    @Override
    public void onDisable() {
        if (savedFramerateLimit != -1 && mc.options != null && !disableVsync.isEnabled()) {
            mc.options.framerateLimit().set(savedFramerateLimit);
        }
        savedFramerateLimit = -1;
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
}
