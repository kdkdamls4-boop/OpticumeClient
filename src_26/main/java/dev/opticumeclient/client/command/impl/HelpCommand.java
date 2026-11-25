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
            String pfx = CommandManager.getInstance().getPrefix();
            ChatUtils.sendClientMessage("Доступные команды (префикс §e" + pfx + "§7):");
            for (ClientCommand cmd : CommandManager.getInstance().getCommands()) {
                ChatUtils.sendClientMessage(pfx + cmd.getName() + " — " + cmd.getDescription());
            }
            return SINGLE_SUCCESS;
        });
    }
}
