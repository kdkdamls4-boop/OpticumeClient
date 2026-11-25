package dev.opticumeclient.client.hud;

import dev.opticumeclient.client.modules.Module;
import dev.opticumeclient.client.modules.ModuleManager;
import dev.opticumeclient.client.render.Animation;
import dev.opticumeclient.client.render.Easing;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared module toggle alert bus and lightweight HUD helpers so DynamicIslandHud
 * and NotificationsHud stay synchronized without duplicating state tracking.
 */
public final class HudAlertBus {
    private static final HudAlertBus INSTANCE = new HudAlertBus();
    private static volatile boolean suppressAlerts = false;

    public static final class ToggleAlert {
        public final String moduleName;
        public final boolean enabled;
        public final long createdAt;
        public final Animation slideAnim = new Animation(0.0f, 240L, Easing.EASE_OUT_BACK);
        private boolean evicting = false;

        ToggleAlert(String moduleName, boolean enabled) {
            this.moduleName = moduleName;
            this.enabled = enabled;
            this.createdAt = System.currentTimeMillis();
            this.slideAnim.animateTo(1.0f, 240L, Easing.EASE_OUT_BACK);
        }

        void evict() {
            if (!evicting) {
                evicting = true;
                slideAnim.animateTo(0.0f, 180L, Easing.EASE_IN_BACK);
            }
        }

        public boolean isEvicting() {
            return evicting;
        }

        boolean isDead() {
            return evicting
                    && (slideAnim.isFinished() || slideAnim.getValue() <= 0.01f)
                    && slideAnim.getTargetValue() == 0.0f;
        }

        public float getAgeSeconds() {
            return (System.currentTimeMillis() - createdAt) / 1000.0f;
        }
    }

    private final Map<Module, Boolean> moduleStates = new HashMap<>();
    private final List<ToggleAlert> activeAlerts = new ArrayList<>();
    private boolean initialized = false;
    private int maxVisible = 4;

    private HudAlertBus() {
    }

    public static HudAlertBus getInstance() {
        return INSTANCE;
    }

    public static void setSuppressAlerts(boolean suppress) {
        suppressAlerts = suppress;
        if (!suppress) {
            INSTANCE.syncCurrentStates();
        }
    }

    public void setMaxVisible(int maxVisible) {
        this.maxVisible = Math.max(1, Math.min(4, maxVisible));
    }

    public void clear() {
        activeAlerts.clear();
    }

    public void syncCurrentStates() {
        for (Module m : ModuleManager.getInstance().getModules()) {
            moduleStates.put(m, m.isEnabled());
            if (m.isEnabled() && m.getToggleAnimation().getValue() < 0.05f) {
                m.getToggleAnimation().setImmediate(1.0f);
            }
        }
        initialized = true;
    }

    public void onTick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return;

        List<Module> allModules = ModuleManager.getInstance().getModules();
        if (!initialized || suppressAlerts) {
            syncCurrentStates();
            return;
        }

        for (Module m : allModules) {
            Boolean prev = moduleStates.get(m);
            boolean nowState = m.isEnabled();
            if (prev != null && prev != nowState) {
                moduleStates.put(m, nowState);
                activeAlerts.add(new ToggleAlert(m.getName(), nowState));
            } else if (prev == null) {
                moduleStates.put(m, nowState);
            }
        }

        int activeCount = 0;
        for (ToggleAlert alert : activeAlerts) {
            if (!alert.isEvicting()) {
                activeCount++;
            }
        }
        for (ToggleAlert alert : activeAlerts) {
            if (!alert.isEvicting()) {
                if (alert.getAgeSeconds() > 2.1f || activeCount > maxVisible) {
                    alert.evict();
                    activeCount--;
                }
            }
        }
        activeAlerts.removeIf(ToggleAlert::isDead);
    }

    public List<ToggleAlert> getActiveAlerts() {
        return Collections.unmodifiableList(activeAlerts);
    }

    public ToggleAlert getLatestActiveAlert() {
        for (int i = activeAlerts.size() - 1; i >= 0; i--) {
            ToggleAlert candidate = activeAlerts.get(i);
            if (!candidate.isEvicting() && candidate.getAgeSeconds() < 2.1f) {
                return candidate;
            }
        }
        return null;
    }

    public static int getPlayerPing(Minecraft mc) {
        if (mc != null && mc.getConnection() != null && mc.player != null) {
            PlayerInfo entry = mc.getConnection().getPlayerInfo(mc.player.getUUID());
            if (entry != null) {
                return Math.max(0, entry.getLatency());
            }
        }
        return 0;
    }
}
