package dev.opticumeclient.client.command.impl;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.opticumeclient.client.command.ClientCommand;
import dev.opticumeclient.client.script.ScriptManager;
import dev.opticumeclient.client.util.ChatUtils;
import net.minecraft.commands.SharedSuggestionProvider;

import java.util.List;

/**
 * {@code .script <reload|list|enable|disable|run>} manages runtime scripts in opticumeclient/scripts/.
 */
public final class ScriptCommand extends ClientCommand {
    public ScriptCommand() {
        super("script", "Рантайм-скрипты (.script reload|list|enable|disable|run <имя>)");
    }

    @Override
    public void build(LiteralArgumentBuilder<SharedSuggestionProvider> builder) {
        builder.then(literal("reload").executes(ctx -> {
            int count = ScriptManager.getInstance().reload();
            if (count < 0) {
                ChatUtils.sendError("Не удалось прочитать папку opticumeclient/scripts/ (активные правила сохранены).");
            } else {
                ChatUtils.sendClientMessage("Перезагружено скриптов (.oc): §e" + count);
            }
            return SINGLE_SUCCESS;
        }));
        builder.then(literal("list").executes(ctx -> {
            List<ScriptManager.LoadedScript> list = ScriptManager.getInstance().getScripts();
            if (list.isEmpty()) {
                ChatUtils.sendClientMessage("Скриптов в opticumeclient/scripts/ не найдено.");
            } else {
                ChatUtils.sendClientMessage("Скрипты (" + list.size() + "):");
                for (ScriptManager.LoadedScript s : list) {
                    ChatUtils.sendClientMessage(" §b" + s.name + " §7[" + (s.enabled ? "§aON" : "§cOFF")
                            + "§7, правил: " + s.rules.size() + "]");
                }
            }
            return SINGLE_SUCCESS;
        }));
        builder.then(literal("enable")
                .then(argument("name", StringArgumentType.word()).executes(ctx -> {
                    String name = StringArgumentType.getString(ctx, "name");
                    if (ScriptManager.getInstance().setScriptEnabled(name, true)) {
                        ChatUtils.sendClientMessage("Скрипт §b" + name + " §aвключён.");
                    } else {
                        ChatUtils.sendError("Скрипт не найден.");
                    }
                    return SINGLE_SUCCESS;
                })));
        builder.then(literal("disable")
                .then(argument("name", StringArgumentType.word()).executes(ctx -> {
                    String name = StringArgumentType.getString(ctx, "name");
                    if (ScriptManager.getInstance().setScriptEnabled(name, false)) {
                        ChatUtils.sendClientMessage("Скрипт §e" + name + " §cвыключен.");
                    } else {
                        ChatUtils.sendError("Скрипт не найден.");
                    }
                    return SINGLE_SUCCESS;
                })));
        builder.then(literal("run")
                .then(argument("name", StringArgumentType.word()).executes(ctx -> {
                    String name = StringArgumentType.getString(ctx, "name");
                    if (ScriptManager.getInstance().runOnce(name)) {
                        ChatUtils.sendClientMessage("Скрипт §b" + name + " §fвыполнен.");
                    } else {
                        ChatUtils.sendError("Скрипт не найден.");
                    }
                    return SINGLE_SUCCESS;
                })));
    }
}
