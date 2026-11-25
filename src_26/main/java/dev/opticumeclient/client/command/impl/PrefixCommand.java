package dev.opticumeclient.client.command.impl;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.opticumeclient.client.command.ClientCommand;
import dev.opticumeclient.client.command.CommandDataStore;
import dev.opticumeclient.client.util.ChatUtils;
import net.minecraft.commands.SharedSuggestionProvider;

/**
 * {@code .prefix <char|reset>} configures the client command prefix.
 */
public final class PrefixCommand extends ClientCommand {
    public PrefixCommand() {
        super("prefix", "Смена префикса команд (.prefix <символ> | .prefix reset)");
    }

    @Override
    public void build(LiteralArgumentBuilder<SharedSuggestionProvider> builder) {
        builder.executes(ctx -> {
            ChatUtils.sendClientMessage("Текущий префикс команд: §e" + CommandDataStore.getInstance().getPrefix());
            return SINGLE_SUCCESS;
        });
        builder.then(literal("reset").executes(ctx -> {
            CommandDataStore.getInstance().resetPrefix();
            ChatUtils.sendClientMessage("Префикс команд сброшен на §e.");
            return SINGLE_SUCCESS;
        }));
        builder.then(argument("prefix", StringArgumentType.word()).executes(ctx -> {
            String raw = StringArgumentType.getString(ctx, "prefix").trim();
            if (raw.isEmpty() || raw.length() > 2) {
                ChatUtils.sendError("Префикс должен содержать 1-2 символа.");
                return SINGLE_SUCCESS;
            }
            if (raw.startsWith("/")) {
                ChatUtils.sendError("Нельзя использовать '/' как префикс (конфликт с серверными командами).");
                return SINGLE_SUCCESS;
            }
            CommandDataStore.getInstance().setPrefix(raw);
            ChatUtils.sendClientMessage("Новый префикс команд: §e" + raw);
            return SINGLE_SUCCESS;
        }));
    }
}
