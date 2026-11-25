package dev.opticumeclient.client.command.impl;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.opticumeclient.client.command.ClientCommand;
import dev.opticumeclient.client.command.FakePlayerManager;
import dev.opticumeclient.client.util.ChatUtils;
import net.minecraft.commands.SharedSuggestionProvider;

/**
 * {@code .fakeplayer [spawn [name]|remove]} manages a client-side combat dummy player.
 */
public final class FakePlayerCommand extends ClientCommand {
    public FakePlayerCommand() {
        super("fakeplayer", "Клиентский фейк-игрок для тестов (.fakeplayer [spawn [имя] | remove])");
    }

    @Override
    public void build(LiteralArgumentBuilder<SharedSuggestionProvider> builder) {
        builder.executes(ctx -> {
            if (FakePlayerManager.getInstance().isSpawned()) {
                FakePlayerManager.getInstance().remove();
                ChatUtils.sendClientMessage("Фейк-игрок удалён.");
            } else if (FakePlayerManager.getInstance().spawn("OpticumeDummy")) {
                ChatUtils.sendClientMessage("Фейк-игрок §bOpticumeDummy §fзаспавнен.");
            }
            return SINGLE_SUCCESS;
        });

        builder.then(literal("remove").executes(ctx -> {
            if (FakePlayerManager.getInstance().remove()) {
                ChatUtils.sendClientMessage("Фейк-игрок удалён.");
            } else {
                ChatUtils.sendError("Фейк-игрок сейчас не заспавнен.");
            }
            return SINGLE_SUCCESS;
        }));

        builder.then(literal("spawn")
                .executes(ctx -> {
                    if (FakePlayerManager.getInstance().spawn("OpticumeDummy")) {
                        ChatUtils.sendClientMessage("Фейк-игрок §bOpticumeDummy §fзаспавнен.");
                    }
                    return SINGLE_SUCCESS;
                })
                .then(argument("name", StringArgumentType.word()).executes(ctx -> {
                    String name = StringArgumentType.getString(ctx, "name");
                    if (FakePlayerManager.getInstance().spawn(name)) {
                        ChatUtils.sendClientMessage("Фейк-игрок §b" + name + " §fзаспавнен.");
                    }
                    return SINGLE_SUCCESS;
                })));
    }
}
