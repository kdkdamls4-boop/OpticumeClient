package dev.opticumeclient;

import dev.opticumeclient.client.autobuy.AutoBuyConfig;
import dev.opticumeclient.client.autobuy.AutoBuyEngine;
import dev.opticumeclient.client.bots.BotSessionManager;
import dev.opticumeclient.client.combat.RotationManager;
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
    private static boolean shadersPrewarmed = false;
    private static net.minecraft.client.player.LocalPlayer lastTickPlayer = null;

    @Override
    public void onInitializeClient() {
        System.out.println("[OpticumeClient] Initializing OpticumeClient v1.5.5 (Minecraft 26.3)...");
        SoundUtil.register();
        CommandManager.getInstance().load();

        // Register ClickGUI keybind on Right Shift
        clickGuiKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.opticumeclient.clickgui",
                InputConstants.Type.KEYBOARD,
                InputConstants.KEY_RSHIFT,
                KeyMapping.Category.MISC
        ));

        // Restore selected offline account & load configs early on client startup
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
            try {
                String selected = MenuConfigStore.getString("selectedAccount", null);
                if (selected != null && selected.matches("^[a-zA-Z0-9_]{1,16}$")) {
                    boolean isMsa = false;
                    com.google.gson.JsonArray alts = MenuConfigStore.getArray("alts");
                    if (alts != null) {
                        for (com.google.gson.JsonElement el : alts) {
                            if (!el.isJsonObject()) continue;
                            com.google.gson.JsonObject obj = el.getAsJsonObject();
                            String name = obj.has("name") ? obj.get("name").getAsString() : "";
                            if (name.equalsIgnoreCase(selected) && obj.has("msa") && obj.get("msa").getAsBoolean()) {
                                isMsa = true;
                                break;
                            }
                        }
                    }
                    if (isMsa) {
                        System.out.println("[OpticumeClient] Skipping offline restore for Microsoft account: " + selected);
                    } else {
                        UUID uuid = UUIDUtil.createOfflinePlayerUUID(selected);
                        User user = new User(selected, uuid, "0", Optional.empty(), Optional.empty());
                        ((MinecraftAccessor) (Object) client).opticume$setUser(user);
                        System.out.println("[OpticumeClient] Restored offline session: " + selected);
                    }
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
            try {
                dev.opticumeclient.client.services.media.SmtcBridge.getInstance().shutdown();
                dev.opticumeclient.client.services.media.CoverArtManager.getInstance().releaseOnMainThread();
                dev.opticumeclient.client.services.DiscordRpcService.getInstance().stop();
            } catch (Throwable ignored) {
            }
        });

        // Register client pre-tick event before LocalPlayer.applyInput() / aiStep() / sendPosition()
        ClientTickEvents.START_CLIENT_TICK.register(client -> {
            if (client.player == null || client.level == null) {
                if (lastTickPlayer != null) {
                    RotationManager.getInstance().reset();
                    lastTickPlayer = null;
                }
                return;
            }
            if (client.player != lastTickPlayer) {
                RotationManager.getInstance().reset();
                lastTickPlayer = client.player;
            }
            ModuleManager.getInstance().onPreTick();
            RotationManager.getInstance().onPreTick();
        });

        // Register client tick event
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!configsInitialized) {
                configsInitialized = true;
                ConfigManager.getInstance().init();
                AutoBuyConfig.ensureLoaded();
            }
            if (!shadersPrewarmed && client.isGameLoadFinished() && client.gui.overlay() == null) {
                shadersPrewarmed = true;
                try {
                    dev.opticumeclient.client.render.font.OpticumeFonts.prewarm();
                    dev.opticumeclient.client.render.RoundedRectRenderState.prewarm();
                    dev.opticumeclient.client.render.BlurredRectRenderState.prewarm();
                } catch (Throwable ignored) {
                }
            }
            if (client.player == null || client.level == null) {
                if (lastTickPlayer != null) {
                    RotationManager.getInstance().reset();
                    lastTickPlayer = null;
                }
                return;
            }
            if (client.player != lastTickPlayer) {
                RotationManager.getInstance().reset();
                lastTickPlayer = client.player;
            }

            // Check ClickGUI key press
            while (clickGuiKey.consumeClick()) {
                if (!(client.gui.screen() instanceof ClickGuiScreen)) {
                    client.setScreenAndShow(new ClickGuiScreen());
                }
            }

            // Tick active modules & services
            AnarchyReconnectService.getInstance().onTick();
            AutoBuyEngine.INSTANCE.onTick();
            dev.opticumeclient.client.command.FakePlayerManager.getInstance().onTick();
            dev.opticumeclient.client.combat.neuro.NeuroDatasetRecorder.getInstance().onTick();
            dev.opticumeclient.client.script.ScriptManager.getInstance().onTick();
            ModuleManager.getInstance().onTick();
            RotationManager.getInstance().onPostTick();
        });

        System.out.println("[OpticumeClient] Initialized successfully with " + ModuleManager.getInstance().getModules().size() + " modules!");
    }
}
