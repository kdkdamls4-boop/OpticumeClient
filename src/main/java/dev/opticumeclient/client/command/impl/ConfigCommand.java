package dev.opticumeclient.client.command.impl;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.opticumeclient.client.command.ClientCommand;
import dev.opticumeclient.client.config.ConfigManager;
import dev.opticumeclient.client.util.ChatUtils;
import net.minecraft.commands.SharedSuggestionProvider;

import java.util.List;

/**
 * {@code .config load/save/delete/auto <name>} | {@code .config list}
 */
public final class ConfigCommand extends ClientCommand {
    public ConfigCommand() {
        super("config", "Управление конфигами (.config load/save/delete/auto <имя> | list)", "cfg");
    }

    @Override
    public void build(LiteralArgumentBuilder<SharedSuggestionProvider> builder) {
        builder.then(literal("list").executes(ctx -> {
            ConfigManager cm = ConfigManager.getInstance();
            List<String> configs = cm.listConfigs();
            String active = cm.getActiveConfigName();
            ChatUtils.sendClientMessage("Конфиги (" + configs.size() + ", активный: §a" + active + "§f): §b"
                    + String.join("§7, §b", configs));
            return SINGLE_SUCCESS;
        }));

        builder.then(literal("load").then(argument("name", StringArgumentType.greedyString()).executes(ctx -> {
            String name = StringArgumentType.getString(ctx, "name").trim();
            ConfigManager cm = ConfigManager.getInstance();
            if (cm.loadConfig(name)) {
                ChatUtils.sendClientMessage("Конфиг §a" + cm.getActiveConfigName() + " §fзагружен.");
            } else {
                ChatUtils.sendError("Не удалось загрузить конфиг \"" + name + "\".");
            }
            return SINGLE_SUCCESS;
        })));

        builder.then(literal("save").then(argument("name", StringArgumentType.greedyString()).executes(ctx -> {
            String name = StringArgumentType.getString(ctx, "name").trim();
            ConfigManager cm = ConfigManager.getInstance();
            if (cm.saveConfig(name)) {
                ChatUtils.sendClientMessage("Конфиг сохранён как §a" + cm.getActiveConfigName() + "§f.");
            } else {
                ChatUtils.sendError("Не удалось сохранить конфиг \"" + name + "\".");
            }
            return SINGLE_SUCCESS;
        })));

        builder.then(literal("delete").then(argument("name", StringArgumentType.greedyString()).executes(ctx -> {
            String name = StringArgumentType.getString(ctx, "name").trim();
            ConfigManager cm = ConfigManager.getInstance();
            String sanitized = cm.sanitizeConfigName(name);
            if (!sanitized.equalsIgnoreCase(name)) {
                ChatUtils.sendError("Недопустимое имя конфига \"" + name + "\".");
            } else if (cm.isBuiltInPreset(sanitized)) {
                ChatUtils.sendError("Встроенный пресет \"" + sanitized + "\" нельзя удалить.");
            } else if (cm.deleteConfig(sanitized)) {
                ChatUtils.sendClientMessage("Конфиг §e" + sanitized + " §fудалён.");
            } else {
                ChatUtils.sendError("Конфиг \"" + sanitized + "\" не найден.");
            }
            return SINGLE_SUCCESS;
        })));

        builder.then(literal("auto").then(argument("name", StringArgumentType.greedyString()).executes(ctx -> {
            String name = StringArgumentType.getString(ctx, "name").trim();
            ConfigManager cm = ConfigManager.getInstance();
            cm.setAutoLoadConfig(name);
            ChatUtils.sendClientMessage("Автозагрузка установлена на §b" + cm.getAutoLoadConfigName() + "§f.");
            return SINGLE_SUCCESS;
        })));
    }
}
