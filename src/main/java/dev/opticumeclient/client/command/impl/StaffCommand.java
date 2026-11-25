package dev.opticumeclient.client.command.impl;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.opticumeclient.client.command.ClientCommand;
import dev.opticumeclient.client.command.CommandDataStore;
import dev.opticumeclient.client.util.ChatUtils;
import net.minecraft.commands.SharedSuggestionProvider;

import java.util.Set;

/**
 * {@code .staff add <name>} / {@code .staff del <name>} / {@code .staff list} / {@code .staff clear}
 */
public final class StaffCommand extends ClientCommand {
    public StaffCommand() {
        super("staff", "Персонал для StaffList (.staff add|del|list|clear <ник>)");
    }

    @Override
    public void build(LiteralArgumentBuilder<SharedSuggestionProvider> builder) {
        builder.then(literal("list").executes(ctx -> {
            Set<String> list = CommandDataStore.getInstance().getStaffNames();
            if (list.isEmpty()) {
                ChatUtils.sendClientMessage("Кастомный список персонала пуст.");
            } else {
                ChatUtils.sendClientMessage("Персонал (" + list.size() + "): §b" + String.join("§7, §b", list));
            }
            return SINGLE_SUCCESS;
        }));

        builder.then(literal("clear").executes(ctx -> {
            int count = CommandDataStore.getInstance().clearStaff();
            ChatUtils.sendClientMessage("Список персонала очищен (удалено: §e" + count + "§7).");
            return SINGLE_SUCCESS;
        }));

        builder.then(literal("add")
                .then(argument("name", StringArgumentType.word()).executes(ctx -> {
                    String name = StringArgumentType.getString(ctx, "name");
                    if (CommandDataStore.getInstance().addStaff(name)) {
                        ChatUtils.sendClientMessage("Ник §b" + name + " §fдобавлен в StaffList.");
                    } else {
                        ChatUtils.sendError("Ник уже в списке или некорректен.");
                    }
                    return SINGLE_SUCCESS;
                })));

        builder.then(literal("del")
                .then(argument("name", StringArgumentType.word()).executes(ctx -> {
                    String name = StringArgumentType.getString(ctx, "name");
                    if (CommandDataStore.getInstance().removeStaff(name)) {
                        ChatUtils.sendClientMessage("Ник §e" + name + " §fудалён из StaffList.");
                    } else {
                        ChatUtils.sendError("Ник не найден в списке.");
                    }
                    return SINGLE_SUCCESS;
                })));
    }
}
