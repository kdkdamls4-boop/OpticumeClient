package dev.opticumeclient.client.command.impl;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.opticumeclient.client.command.ClientCommand;
import dev.opticumeclient.client.config.ConfigManager;
import dev.opticumeclient.client.modules.Module;
import dev.opticumeclient.client.modules.ModuleManager;
import dev.opticumeclient.client.util.ChatUtils;
import net.minecraft.commands.SharedSuggestionProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * {@code .bind <module> <key|none>} | {@code .bind list}
 */
public final class BindCommand extends ClientCommand {
    public BindCommand() {
        super("bind", "Назначение клавиш модулям (.bind <модуль> <клавиша|none> | .bind list)");
    }

    @Override
    public void build(LiteralArgumentBuilder<SharedSuggestionProvider> builder) {
        builder.then(literal("list").executes(ctx -> {
            List<String> bound = new ArrayList<>();
            for (Module m : ModuleManager.getInstance().getModules()) {
                if (m.getKeyBind() > 0) {
                    String keyName = InputConstants.Type.KEYBOARD.getOrCreate(m.getKeyBind()).getDisplayName().getString();
                    bound.add("§b" + m.getName() + "§7 [§e" + keyName.toUpperCase(Locale.ROOT) + "§7]");
                }
            }
            if (bound.isEmpty()) {
                ChatUtils.sendClientMessage("Нет назначенных биндов модулей.");
            } else {
                ChatUtils.sendClientMessage("Бинды (" + bound.size() + "): " + String.join("§7, ", bound));
            }
            return SINGLE_SUCCESS;
        }));

        builder.then(argument("module", StringArgumentType.word())
                .then(argument("key", StringArgumentType.word()).executes(ctx -> {
                    String modName = StringArgumentType.getString(ctx, "module");
                    String keyRaw = StringArgumentType.getString(ctx, "key").trim().toLowerCase(Locale.ROOT);
                    Module target = findModule(modName);
                    if (target == null) {
                        ChatUtils.sendError("Модуль \"" + modName + "\" не найден.");
                        return SINGLE_SUCCESS;
                    }
                    if ("none".equals(keyRaw) || "clear".equals(keyRaw) || "reset".equals(keyRaw) || "0".equals(keyRaw)) {
                        target.setKeyBind(0);
                        ConfigManager.getInstance().saveActiveConfigQuietly();
                        ChatUtils.sendClientMessage("Бинд для §b" + target.getName() + " §fсброшен.");
                        return SINGLE_SUCCESS;
                    }
                    int keyCode = parseKeyCode(keyRaw);
                    if (keyCode <= 0) {
                        ChatUtils.sendError("Неизвестная клавиша \"" + keyRaw + "\" (пример: r, g, f4, lshift, none).");
                        return SINGLE_SUCCESS;
                    }
                    target.setKeyBind(keyCode);
                    ConfigManager.getInstance().saveActiveConfigQuietly();
                    String display = InputConstants.Type.KEYBOARD.getOrCreate(keyCode).getDisplayName().getString();
                    ChatUtils.sendClientMessage("Модуль §b" + target.getName() + " §fпривязан к клавише §e"
                            + display.toUpperCase(Locale.ROOT) + "§f.");
                    return SINGLE_SUCCESS;
                })));
    }

    private static Module findModule(String query) {
        if (query == null) return null;
        String clean = query.replace("_", "").trim();
        for (Module m : ModuleManager.getInstance().getModules()) {
            if (m.getName().equalsIgnoreCase(query) || m.getName().replace(" ", "").equalsIgnoreCase(clean)) {
                return m;
            }
        }
        return null;
    }

    public static int parseKeyCode(String raw) {
        if (raw == null || raw.isBlank()) return -1;
        String clean = raw.trim().toLowerCase(Locale.ROOT);
        String keyName = clean.startsWith("key.keyboard.") ? clean : "key.keyboard." + clean
                .replace("lshift", "left.shift")
                .replace("rshift", "right.shift")
                .replace("lctrl", "left.control")
                .replace("rctrl", "right.control")
                .replace("lalt", "left.alt")
                .replace("ralt", "right.alt");
        try {
            InputConstants.Key key = InputConstants.getKey(keyName);
            if (key != null && key != InputConstants.UNKNOWN && key.getValue() > 0) {
                return key.getValue();
            }
        } catch (Throwable ignored) {
        }
        return -1;
    }

    public static String formatKeyName(int keyCode) {
        try {
            return InputConstants.Type.KEYBOARD.getOrCreate(keyCode).getDisplayName().getString().toUpperCase(Locale.ROOT);
        } catch (Throwable ignored) {
            return String.valueOf(keyCode);
        }
    }
}
