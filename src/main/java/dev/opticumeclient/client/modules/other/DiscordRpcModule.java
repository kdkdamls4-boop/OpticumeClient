package dev.opticumeclient.client.modules.other;

import dev.opticumeclient.client.modules.Category;
import dev.opticumeclient.client.modules.Module;
import dev.opticumeclient.client.services.DiscordRpcService;
import dev.opticumeclient.client.setting.BooleanSetting;

/**
 * Enables native Windows Discord Rich Presence showing current session state and enabled module count.
 */
public class DiscordRpcModule extends Module {
    private static DiscordRpcModule instance;

    private final BooleanSetting hideServerIp = new BooleanSetting("Hide Server IP", "Скрывать адрес текущего сервера в Discord", false);
    private final BooleanSetting showModules = new BooleanSetting("Show Modules", "Показывать количество включённых модулей", true);

    public DiscordRpcModule() {
        super("DiscordRPC", "Статус игры и сервера в профиле Discord (Windows Named Pipe)", Category.OTHER);
        instance = this;
        addSetting(hideServerIp);
        addSetting(showModules);
    }

    public static DiscordRpcModule getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        super.onEnable();
        DiscordRpcService.getInstance().configure(hideServerIp.isEnabled(), showModules.isEnabled());
        DiscordRpcService.getInstance().start();
    }

    @Override
    public void onTick() {
        DiscordRpcService.getInstance().configure(hideServerIp.isEnabled(), showModules.isEnabled());
    }

    @Override
    public void onDisable() {
        super.onDisable();
        DiscordRpcService.getInstance().stop();
    }
}
