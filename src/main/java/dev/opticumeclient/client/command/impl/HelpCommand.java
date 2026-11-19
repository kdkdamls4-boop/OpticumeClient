package dev.opticumeclient.client.command.impl;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.opticumeclient.client.command.ClientCommand;
import dev.opticumeclient.client.command.CommandManager;
import dev.opticumeclient.client.util.ChatUtils;
import net.minecraft.commands.SharedSuggestionProvider;

/**
 * {@code .help} lists client commands.
 */
public final class HelpCommand extends ClientCommand {
    public HelpCommand() {
        super("help", "Список клиентских команд");
    }

    @Override
    public void build(LiteralArgumentBuilder<SharedSuggestionProvider> builder) {
        builder.executes(context -> {
            ChatUtils.sendClientMessage("Доступные команды:");
            for (ClientCommand cmd : CommandManager.getInstance().getCommands()) {
                ChatUtils.sendClientMessage("." + cmd.getName() + " — " + cmd.getDescription());
            }
            return SINGLE_SUCCESS;
        });
    }
}
