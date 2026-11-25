package dev.opticumeclient.client.modules.other;

import dev.opticumeclient.client.gui.ClickGuiScreen;
import dev.opticumeclient.client.hud.HudAlertBus;
import dev.opticumeclient.client.modules.Category;
import dev.opticumeclient.client.modules.Module;
import dev.opticumeclient.client.modules.ModuleManager;
import dev.opticumeclient.client.setting.BooleanSetting;

import java.util.ArrayList;
import java.util.List;

/**
 * Safely disables all active modules, HUD elements, and transient overlays in memory on demand,
 * with optional restoration when toggled back off in private/offline checks.
 */
public class PanicModule extends Module {
    private static PanicModule instance;

    private final BooleanSetting restoreOnDisable = new BooleanSetting("Restore on Disable", "Восстановить сохранённые модули при выключении Panic", true);
    private final BooleanSetting silentMode = new BooleanSetting("Silent Mode", "Подавлять клиентские чат-уведомления пока включён Panic", true);

    private final List<Module> savedEnabledModules = new ArrayList<>();

    public PanicModule() {
        super("Panic", "Мгновенное отключение всех модулей и визуалов", Category.OTHER);
        instance = this;
        addSetting(restoreOnDisable);
        addSetting(silentMode);
    }

    public static PanicModule getInstance() {
        return instance;
    }

    public static boolean isPanicActive() {
        return instance != null && instance.isEnabled();
    }

    public static boolean isSilentModeActive() {
        return instance != null && instance.isEnabled() && instance.silentMode.isEnabled();
    }

    @Override
    public void onEnable() {
        super.onEnable();
        savedEnabledModules.clear();

        if (mc != null && mc.gui != null && mc.gui.screen() instanceof ClickGuiScreen) {
            mc.setScreenAndShow(null);
        }

        HudAlertBus.setSuppressAlerts(true);
        try {
            for (Module module : ModuleManager.getInstance().getModules()) {
                if (module == this || !module.isEnabled()) {
                    continue;
                }
                savedEnabledModules.add(module);
                module.setEnabled(false);
            }
        } finally {
            HudAlertBus.getInstance().clear();
            HudAlertBus.setSuppressAlerts(false);
        }
    }

    @Override
    public void onDisable() {
        super.onDisable();
        if (!restoreOnDisable.isEnabled() || savedEnabledModules.isEmpty()) {
            savedEnabledModules.clear();
            return;
        }

        HudAlertBus.setSuppressAlerts(true);
        try {
            for (Module module : savedEnabledModules) {
                if (module != this && !module.isEnabled()) {
                    module.setEnabled(true);
                }
            }
        } finally {
            savedEnabledModules.clear();
            HudAlertBus.setSuppressAlerts(false);
        }
    }
}
