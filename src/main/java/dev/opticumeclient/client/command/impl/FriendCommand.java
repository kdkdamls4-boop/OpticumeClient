package dev.opticumeclient.client.command.impl;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.opticumeclient.client.command.ClientCommand;
import dev.opticumeclient.client.friend.FriendManager;
import dev.opticumeclient.client.util.ChatUtils;
import net.minecraft.commands.SharedSuggestionProvider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * {@code .friend add <nick>} | {@code .friend remove <nick>} | {@code .friend list} | {@code .friend clear}
 */
public final class FriendCommand extends ClientCommand {
    public FriendCommand() {
        super("friend", "Управление списком друзей (.friend add/remove <ник> | list | clear)", "f", "friends");
    }

    @Override
    public void build(LiteralArgumentBuilder<SharedSuggestionProvider> builder) {
        builder.then(literal("add").then(argument("name", StringArgumentType.word()).executes(ctx -> {
            String name = StringArgumentType.getString(ctx, "name");
            if (!FriendManager.getInstance().addFriend(name)) {
                ChatUtils.sendError("Игрок \"" + name + "\" уже в друзьях.");
            }
            return SINGLE_SUCCESS;
        })));

        builder.then(literal("remove").then(argument("name", StringArgumentType.word()).executes(ctx -> {
            String name = StringArgumentType.getString(ctx, "name");
            if (!FriendManager.getInstance().removeFriend(name)) {
                ChatUtils.sendError("Игрок \"" + name + "\" не найден в списке друзей.");
            }
            return SINGLE_SUCCESS;
        })));

        builder.then(literal("del").then(argument("name", StringArgumentType.word()).executes(ctx -> {
            String name = StringArgumentType.getString(ctx, "name");
            if (!FriendManager.getInstance().removeFriend(name)) {
                ChatUtils.sendError("Игрок \"" + name + "\" не найден в списке друзей.");
            }
            return SINGLE_SUCCESS;
        })));

        builder.then(literal("list").executes(ctx -> {
            List<String> list = new ArrayList<>(FriendManager.getInstance().getFriends());
            if (list.isEmpty()) {
                ChatUtils.sendClientMessage("Список друзей пуст.");
            } else {
                Collections.sort(list);
                ChatUtils.sendClientMessage("Друзья (" + list.size() + "): §b" + String.join("§7, §b", list));
            }
            return SINGLE_SUCCESS;
        }));

        builder.then(literal("clear").executes(ctx -> {
            int removed = FriendManager.getInstance().clearFriends();
            ChatUtils.sendClientMessage("Список друзей очищен (удалено: " + removed + ").");
            return SINGLE_SUCCESS;
        }));
    }
}
