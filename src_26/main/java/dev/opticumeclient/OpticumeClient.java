package dev.opticumeclient;

import dev.opticumeclient.client.config.ConfigManager;
import dev.opticumeclient.client.gui.ClickGuiScreen;
import dev.opticumeclient.client.modules.ModuleManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import com.mojang.blaze3d.platform.InputConstants;

public class OpticumeClient implements ClientModInitializer {
    public static final String MOD_ID = "opticumeclient";
    public static KeyMapping clickGuiKey;
    private static boolean configsInitialized = false;

    @Override
    public void onInitializeClient() {
        System.out.println("[OpticumeClient] Initializing OpticumeClient v1.0.0 (Minecraft 26.3)...");

        // Register ClickGUI keybind on Right Shift
        clickGuiKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.opticumeclient.clickgui",
                InputConstants.Type.KEYBOARD,
                InputConstants.KEY_RSHIFT,
                KeyMapping.Category.MISC
        ));

        // Register client tick event
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!configsInitialized && client.player != null) {
                configsInitialized = true;
                ConfigManager.getInstance().init();
            }
            if (client.player == null) return;

            // Check ClickGUI key press
            while (clickGuiKey.consumeClick()) {
                if (!(client.gui.screen() instanceof ClickGuiScreen)) {
                    client.setScreenAndShow(new ClickGuiScreen());
                }
            }

            // Tick active modules
            ModuleManager.getInstance().onTick();
        });

        System.out.println("[OpticumeClient] Initialized successfully with " + ModuleManager.getInstance().getModules().size() + " modules!");
    }
}
