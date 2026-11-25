package dev.opticumeclient.client.modules.other;

import dev.opticumeclient.client.modules.Category;
import dev.opticumeclient.client.modules.Module;
import dev.opticumeclient.client.setting.BooleanSetting;
import dev.opticumeclient.client.util.ChatUtils;

/**
 * Intercepts incoming server resource-pack prompts on the main session and reports
 * accepted/downloaded/loaded status without downloading or reloading heavy server packs.
 */
public class ServerRPSpoofModule extends Module {
    private static ServerRPSpoofModule INSTANCE;

    private final BooleanSetting notifyChat = new BooleanSetting("Notify", "Сообщать в чат о пропуске серверного ресурспака", true);

    public ServerRPSpoofModule() {
        super("ServerRPSpoof", "Пропускает обязательную загрузку серверных ресурспаков с ответом SUCCESS", Category.OTHER);
        INSTANCE = this;
        addSetting(notifyChat);
    }

    public static ServerRPSpoofModule getInstance() {
        return INSTANCE;
    }

    public void onSpoofedPack(String url) {
        if (notifyChat.isEnabled()) {
            ChatUtils.sendClientMessage("Серверный ресурспак пропущен (ServerRPSpoof).");
        }
    }
}
