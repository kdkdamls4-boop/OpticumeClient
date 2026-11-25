package dev.opticumeclient.client.modules.other;

import dev.opticumeclient.client.bots.BotSessionManager;
import dev.opticumeclient.client.gui.ClickGuiScreen;
import dev.opticumeclient.client.modules.Category;
import dev.opticumeclient.client.modules.Module;
import dev.opticumeclient.client.setting.BooleanSetting;
import dev.opticumeclient.client.setting.NumberSetting;
import dev.opticumeclient.client.setting.RunSetting;
import dev.opticumeclient.client.ui.bots.ScreenBots;
import net.minecraft.client.Minecraft;

/**
 * Multi-account bot manager module ported from Desire's {@code BotsModule}.
 */
public class BotsModule extends Module {
    private static BotsModule instance;

    public final RunSetting openMenu = new RunSetting("Открыть меню", "Открыть панель управления ботами", BotsModule::open);
    public final NumberSetting maxBots = new NumberSetting("Макс. ботов", "Максимальное количество одновременных ботов", 5.0, 1.0, 15.0, 1.0);
    public final BooleanSetting botChat = new BooleanSetting("Чат ботов в чат", "Пересылать сообщения фоновых ботов в основной чат", false);

    public BotsModule() {
        super("Bots", "Управление дополнительными ботами и мульти-сессиями", Category.OTHER);
        instance = this;
        addSetting(openMenu);
        addSetting(maxBots);
        addSetting(botChat);
        apply();
    }

    public static BotsModule getInstance() {
        return instance;
    }

    public void apply() {
        BotSessionManager manager = BotSessionManager.getInstance();
        manager.setMaxBots(maxBots.getIntValue());
        manager.setShowBotChat(botChat.getValue());
    }

    public static void open() {
        Minecraft client = Minecraft.getInstance();
        client.execute(() -> {
            if (client.gui != null) {
                client.gui.setScreen(ScreenBots.getInstance());
            }
        });
    }

    @Override
    public void onEnable() {
        apply();
        if (mc.gui != null && (mc.gui.screen() == null || mc.gui.screen() instanceof ClickGuiScreen)) {
            open();
        }
        setEnabled(false);
    }

    public static void syncIfPresent() {
        BotsModule mod = instance;
        if (mod != null) {
            mod.apply();
        }
    }
}
