package dev.opticumeclient.client.command.impl;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.opticumeclient.client.command.ClientCommand;
import dev.opticumeclient.client.command.CommandDataStore;
import dev.opticumeclient.client.util.ChatUtils;
import net.minecraft.commands.SharedSuggestionProvider;

import java.util.Set;

/**
 * {@code .blockesp <add|del|list|clear>} manages custom block IDs for BlockESP.
 */
public final class BlockEspCommand extends ClientCommand {
    public BlockEspCommand() {
        super("blockesp", "Блоки для BlockESP (.blockesp add|del|list|clear <id>)");
    }

    @Override
    public void build(LiteralArgumentBuilder<SharedSuggestionProvider> builder) {
        builder.then(literal("list").executes(ctx -> {
            Set<String> ids = CommandDataStore.getInstance().getBlockEspIds();
            if (ids.isEmpty()) {
                ChatUtils.sendClientMessage("Пользовательский список BlockESP пуст.");
            } else {
                ChatUtils.sendClientMessage("Блоки BlockESP (" + ids.size() + "): §b" + String.join("§7, §b", ids));
            }
            return SINGLE_SUCCESS;
        }));

        builder.then(literal("clear").executes(ctx -> {
            int count = CommandDataStore.getInstance().clearBlockEspIds();
            ChatUtils.sendClientMessage("Список BlockESP очищен (удалено: §e" + count + "§7).");
            return SINGLE_SUCCESS;
        }));

        builder.then(literal("add")
                .then(argument("id", StringArgumentType.greedyString()).executes(ctx -> {
                    String raw = StringArgumentType.getString(ctx, "id");
                    if (CommandDataStore.getInstance().addBlockEspId(raw)) {
                        ChatUtils.sendClientMessage("Блок §b" + CommandDataStore.normalizeBlockId(raw) + " §fдобавлен в BlockESP.");
                    } else {
                        ChatUtils.sendError("Блок уже добавлен или ID некорректен.");
                    }
                    return SINGLE_SUCCESS;
                })));

        builder.then(literal("del")
                .then(argument("id", StringArgumentType.greedyString()).executes(ctx -> {
                    String raw = StringArgumentType.getString(ctx, "id");
                    if (CommandDataStore.getInstance().removeBlockEspId(raw)) {
                        ChatUtils.sendClientMessage("Блок §e" + CommandDataStore.normalizeBlockId(raw) + " §fудалён из BlockESP.");
                    } else {
                        ChatUtils.sendError("Блок не найден в списке BlockESP.");
                    }
                    return SINGLE_SUCCESS;
                })));
    }
}
