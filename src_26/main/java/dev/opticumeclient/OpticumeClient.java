package dev.opticumeclient;

import dev.opticumeclient.client.autobuy.AutoBuyConfig;
import dev.opticumeclient.client.autobuy.AutoBuyEngine;
import dev.opticumeclient.client.bots.BotSessionManager;
import dev.opticumeclient.client.command.CommandManager;
import dev.opticumeclient.client.config.ConfigManager;
import dev.opticumeclient.client.gui.ClickGuiScreen;
import dev.opticumeclient.client.modules.ModuleManager;
import dev.opticumeclient.client.services.AnarchyReconnectService;
import dev.opticumeclient.client.ui.menu.MenuConfigStore;
import dev.opticumeclient.client.util.SoundUtil;
import dev.opticumeclient.mixin.MinecraftAccessor;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.User;
import net.minecraft.core.UUIDUtil;
import com.mojang.blaze3d.platform.InputConstants;

import java.util.Optional;
import java.util.UUID;

public class OpticumeClient implements ClientModInitializer {
    public static final String MOD_ID = "opticumeclient";
    public static KeyMapping clickGuiKey;
    private static boolean configsInitialized = false;

    @Override
    public void onInitializeClient() {
        System.out.println("[OpticumeClient] Initializing OpticumeClient v1.0.0 (Minecraft 26.3)...");
        SoundUtil.register();
        CommandManager.getInstance().load();

        // Register ClickGUI keybind on Right Shift
        clickGuiKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.opticumeclient.clickgui",
                InputConstants.Type.KEYBOARD,
                InputConstants.KEY_RSHIFT,
                KeyMapping.Category.MISC
        ));

        // Restore selected account & load configs early on client startup
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
            try {
                String selected = MenuConfigStore.getString("selectedAccount", null);
                if (selected != null && selected.matches("^[a-zA-Z0-9_]{1,16}$")) {
                    UUID uuid = UUIDUtil.createOfflinePlayerUUID(selected);
                    User user = new User(selected, uuid, "0", Optional.empty(), Optional.empty());
                    ((MinecraftAccessor) (Object) client).opticume$setUser(user);
                    System.out.println("[OpticumeClient] Restored offline session: " + selected);
                }
            } catch (Throwable t) {
                System.err.println("[OpticumeClient] Failed to restore offline session: " + t.getMessage());
            }

            if (!configsInitialized) {
                configsInitialized = true;
                ConfigManager.getInstance().init();
                AutoBuyConfig.ensureLoaded();
            }
        });

        // Save configs & stop background bot sessions on client stopping
        // (GPU resources & BufferPool are freed later in MinecraftMixin.opticume$releaseGpuResourcesOnClose(),
        // after disconnectWithProgressScreen() renders the final ProgressScreen frame.)
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            try {
                BotSessionManager.shutdownIfInitialized();
            } catch (Throwable ignored) {
            }
            try {
                ConfigManager.getInstance().saveActiveConfigQuietly();
                AutoBuyConfig.save();
            } catch (Throwable ignored) {
            }
        });

        // Register client tick event
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!configsInitialized) {
                configsInitialized = true;
                ConfigManager.getInstance().init();
                AutoBuyConfig.ensureLoaded();
            }
            if (client.player == null) return;

            // Check ClickGUI key press
            while (clickGuiKey.consumeClick()) {
                if (!(client.gui.screen() instanceof ClickGuiScreen)) {
                    client.setScreenAndShow(new ClickGuiScreen());
                }
            }

            // Tick active modules & services
            AnarchyReconnectService.getInstance().onTick();
            AutoBuyEngine.INSTANCE.onTick();
            ModuleManager.getInstance().onTick();
        });

        System.out.println("[OpticumeClient] Initialized successfully with " + ModuleManager.getInstance().getModules().size() + " modules!");
    }
}
