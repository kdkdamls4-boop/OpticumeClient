package dev.opticumeclient.client.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.opticumeclient.client.command.impl.AutoBuyCommand;
import dev.opticumeclient.client.command.impl.GpsCommand;
import dev.opticumeclient.client.command.impl.HelpCommand;
import dev.opticumeclient.client.command.impl.RctCommand;
import dev.opticumeclient.client.util.ChatUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Dispatcher for client-side {@code .} commands intercepted before chat packets are sent.
 */
public final class CommandManager {
    private static final CommandManager INSTANCE = new CommandManager();

    private final CommandDispatcher<SharedSuggestionProvider> dispatcher = new CommandDispatcher<>();
    private final List<ClientCommand> commands = new ArrayList<>();
    private ClientSuggestionProvider source;
    private boolean loaded = false;

    private CommandManager() {
    }

    public static CommandManager getInstance() {
        return INSTANCE;
    }

    public synchronized void load() {
        if (loaded) return;
        loaded = true;
        register(new GpsCommand(), new RctCommand(), new HelpCommand(), new AutoBuyCommand());
    }

    public void register(ClientCommand... cmds) {
        for (ClientCommand cmd : cmds) {
            cmd.register(dispatcher);
            commands.add(cmd);
        }
    }

    public List<ClientCommand> getCommands() {
        return Collections.unmodifiableList(commands);
    }

    public String getPrefix() {
        return ".";
    }

    private SharedSuggestionProvider getSource() {
        if (source == null) {
            source = new ClientSuggestionProvider(null, Minecraft.getInstance(), LevelBasedPermissionSet.OWNER);
        }
        return source;
    }

    public void executeCommands(String message, CallbackInfo ci) {
        if (message == null || !message.startsWith(getPrefix())) return;
        if (!loaded) load();

        String raw = message.substring(getPrefix().length()).trim();
        if (!raw.isEmpty()) {
            try {
                dispatcher.execute(raw, getSource());
            } catch (CommandSyntaxException ex) {
                ChatUtils.sendError("Неверный синтаксис команды. Используй .help");
            }
        }
        ci.cancel();
    }
}
