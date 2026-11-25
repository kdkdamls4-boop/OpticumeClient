package dev.opticumeclient.client.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.opticumeclient.client.command.impl.AutoBuyCommand;
import dev.opticumeclient.client.command.impl.BindCommand;
import dev.opticumeclient.client.command.impl.BlockEspCommand;
import dev.opticumeclient.client.command.impl.ConfigCommand;
import dev.opticumeclient.client.command.impl.FakePlayerCommand;
import dev.opticumeclient.client.command.impl.FriendCommand;
import dev.opticumeclient.client.command.impl.GpsCommand;
import dev.opticumeclient.client.command.impl.HClipCommand;
import dev.opticumeclient.client.command.impl.HelpCommand;
import dev.opticumeclient.client.command.impl.MacroCommand;
import dev.opticumeclient.client.command.impl.NeuroCommand;
import dev.opticumeclient.client.command.impl.PrefixCommand;
import dev.opticumeclient.client.command.impl.ProfilerCommand;
import dev.opticumeclient.client.command.impl.RctCommand;
import dev.opticumeclient.client.command.impl.ScriptCommand;
import dev.opticumeclient.client.command.impl.StaffCommand;
import dev.opticumeclient.client.command.impl.ToggleCommand;
import dev.opticumeclient.client.command.impl.VClipCommand;
import dev.opticumeclient.client.command.impl.WayCommand;
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
        CommandDataStore.getInstance().ensureLoaded();
        register(
                new GpsCommand(),
                new RctCommand(),
                new HelpCommand(),
                new AutoBuyCommand(),
                new FriendCommand(),
                new ConfigCommand(),
                new BindCommand(),
                new ToggleCommand(),
                new PrefixCommand(),
                new MacroCommand(),
                new StaffCommand(),
                new WayCommand(),
                new VClipCommand(),
                new HClipCommand(),
                new FakePlayerCommand(),
                new BlockEspCommand(),
                new ProfilerCommand(),
                new NeuroCommand(),
                new ScriptCommand()
        );
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
        return CommandDataStore.getInstance().getPrefix();
    }

    private SharedSuggestionProvider getSource() {
        if (source == null) {
            source = new ClientSuggestionProvider(null, Minecraft.getInstance(), LevelBasedPermissionSet.OWNER);
        }
        return source;
    }

    public void executeCommands(String message, CallbackInfo ci) {
        if (message == null || message.isBlank()) return;
        if (!loaded) load();

        String pfx = getPrefix();
        boolean directPrefix = message.startsWith(pfx);
        String candidate = message;
        if (!directPrefix) {
            String fixed = fixPrefixLayout(message, pfx);
            if (fixed == null) return;
            candidate = fixed;
        }

        String raw = candidate.substring(pfx.length()).trim();
        if (directPrefix) {
            raw = maybeTranslateCommandKeyword(raw);
        }

        if (!raw.isEmpty()) {
            try {
                dispatcher.execute(raw, getSource());
            } catch (CommandSyntaxException ex) {
                ChatUtils.sendError("Неверный синтаксис команды. Используй " + pfx + "help");
            }
        }
        ci.cancel();
    }

    private String fixPrefixLayout(String message, String pfx) {
        String translated = CommandFix.translateRuToEn(message);
        if (translated.equals(message) || !translated.startsWith(pfx)) {
            return null;
        }
        String raw = translated.substring(pfx.length()).trim();
        if (raw.isEmpty()) return null;
        int spaceIdx = raw.indexOf(' ');
        String head = (spaceIdx > 0 ? raw.substring(0, spaceIdx) : raw).toLowerCase(java.util.Locale.ROOT);
        for (ClientCommand cmd : commands) {
            if (cmd.getName().equalsIgnoreCase(head)) {
                return translated;
            }
        }
        return null;
    }

    private String maybeTranslateCommandKeyword(String raw) {
        if (raw.isEmpty()) return raw;
        int spaceIdx = raw.indexOf(' ');
        String first = spaceIdx > 0 ? raw.substring(0, spaceIdx) : raw;
        String translatedFirst = CommandFix.translateRuToEn(first);
        if (!translatedFirst.equals(first)) {
            for (ClientCommand cmd : commands) {
                if (cmd.getName().equalsIgnoreCase(translatedFirst)) {
                    return CommandFix.translateRuToEn(raw);
                }
            }
        }
        return raw;
    }
}
