package dev.opticumeclient.client.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.minecraft.commands.SharedSuggestionProvider;

import java.util.List;

/**
 * Base class for client-side Brigadier dot-commands (e.g. {@code .rct}, {@code .gps}, {@code .help}).
 */
public abstract class ClientCommand {
    public static final int SINGLE_SUCCESS = 1;

    private final String name;
    private final String description;
    private final List<String> aliases;

    protected ClientCommand(String name, String description, String... aliases) {
        this.name = name;
        this.description = description;
        this.aliases = List.of(aliases);
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public List<String> getAliases() {
        return aliases;
    }

    public abstract void build(LiteralArgumentBuilder<SharedSuggestionProvider> builder);

    public final void register(CommandDispatcher<SharedSuggestionProvider> dispatcher) {
        LiteralArgumentBuilder<SharedSuggestionProvider> root = LiteralArgumentBuilder.literal(name);
        build(root);
        dispatcher.register(root);

        for (String alias : aliases) {
            LiteralArgumentBuilder<SharedSuggestionProvider> aliasBuilder = LiteralArgumentBuilder.literal(alias);
            build(aliasBuilder);
            dispatcher.register(aliasBuilder);
        }
    }

    protected static <T> RequiredArgumentBuilder<SharedSuggestionProvider, T> argument(String name, ArgumentType<T> type) {
        return RequiredArgumentBuilder.argument(name, type);
    }

    protected static LiteralArgumentBuilder<SharedSuggestionProvider> literal(String name) {
        return LiteralArgumentBuilder.literal(name);
    }
}
