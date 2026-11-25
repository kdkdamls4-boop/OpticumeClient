package dev.opticumeclient.client.command.impl;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.opticumeclient.client.command.ClientCommand;
import dev.opticumeclient.client.command.CommandDataStore;
import dev.opticumeclient.client.util.ChatUtils;
import net.minecraft.commands.SharedSuggestionProvider;

import java.util.List;

/**
 * {@code .macro add <key> <message>} / {@code .macro del <key>} / {@code .macro list} / {@code .macro clear}
 */
public final class MacroCommand extends ClientCommand {
    public MacroCommand() {
        super("macro", "Макросы на клавиши (.macro add <key> <msg> | del | list | clear)");
    }

    @Override
    public void build(LiteralArgumentBuilder<SharedSuggestionProvider> builder) {
        builder.then(literal("list").executes(ctx -> {
            List<CommandDataStore.MacroEntry> list = CommandDataStore.getInstance().getMacros();
            if (list.isEmpty()) {
                ChatUtils.sendClientMessage("Список макросов пуст.");
            } else {
                ChatUtils.sendClientMessage("Макросы (" + list.size() + "):");
                for (CommandDataStore.MacroEntry m : list) {
                    ChatUtils.sendClientMessage(" §e" + m.keyName() + " §7-> §f" + m.message());
                }
            }
            return SINGLE_SUCCESS;
        }));

        builder.then(literal("clear").executes(ctx -> {
            int removed = CommandDataStore.getInstance().clearMacros();
            ChatUtils.sendClientMessage("Удалено макросов: §e" + removed);
            return SINGLE_SUCCESS;
        }));

        builder.then(literal("del")
                .then(argument("key", StringArgumentType.word()).executes(ctx -> {
                    String keyRaw = StringArgumentType.getString(ctx, "key");
                    int code = BindCommand.parseKeyCode(keyRaw);
                    if (code <= 0) {
                        ChatUtils.sendError("Неизвестная клавиша: " + keyRaw);
                        return SINGLE_SUCCESS;
                    }
                    if (CommandDataStore.getInstance().removeMacroByKey(code)) {
                        ChatUtils.sendClientMessage("Макрос на клавише §e" + BindCommand.formatKeyName(code) + " §fудалён.");
                    } else {
                        ChatUtils.sendError("Макрос на этой клавише не найден.");
                    }
                    return SINGLE_SUCCESS;
                })));

        builder.then(literal("add")
                .then(argument("key", StringArgumentType.word())
                        .then(argument("message", StringArgumentType.greedyString()).executes(ctx -> {
                            String keyRaw = StringArgumentType.getString(ctx, "key");
                            String msg = StringArgumentType.getString(ctx, "message");
                            int code = BindCommand.parseKeyCode(keyRaw);
                            if (code <= 0) {
                                ChatUtils.sendError("Неизвестная клавиша \"" + keyRaw + "\" (пример: h, f6, lctrl).");
                                return SINGLE_SUCCESS;
                            }
                            if (msg == null || msg.isBlank()) {
                                ChatUtils.sendError("Текст макроса не может быть пустым.");
                                return SINGLE_SUCCESS;
                            }
                            String name = BindCommand.formatKeyName(code);
                            CommandDataStore.getInstance().putMacro(code, name, msg.trim());
                            ChatUtils.sendClientMessage("Макрос §e" + name + " §7-> §f" + msg.trim() + " §aдобавлен.");
                            return SINGLE_SUCCESS;
                        }))));
    }
}
