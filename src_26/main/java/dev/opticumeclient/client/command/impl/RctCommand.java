package dev.opticumeclient.client.command.impl;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.opticumeclient.client.command.ClientCommand;
import dev.opticumeclient.client.services.AnarchyReconnectService;
import dev.opticumeclient.client.services.AnarchyReconnectService.Target;
import net.minecraft.commands.SharedSuggestionProvider;

/**
 * {@code .rct [an|grief] [number]} / {@code .rct stop}
 */
public final class RctCommand extends ClientCommand {
    public RctCommand() {
        super("rct", "Перезаход на анархию / гриф (.rct [an|grief] [номер] | .rct stop)", "reconnect");
    }

    @Override
    public void build(LiteralArgumentBuilder<SharedSuggestionProvider> builder) {
        AnarchyReconnectService service = AnarchyReconnectService.getInstance();

        builder.executes(context -> {
            service.start(Target.AUTO, null);
            return SINGLE_SUCCESS;
        });

        builder.then(argument("number", IntegerArgumentType.integer(1)).executes(context -> {
            service.start(Target.AUTO, IntegerArgumentType.getInteger(context, "number"));
            return SINGLE_SUCCESS;
        }));

        for (String mode : new String[]{"an", "grief"}) {
            Target target = Target.parse(mode);
            builder.then(literal(mode)
                    .executes(context -> {
                        service.start(target, null);
                        return SINGLE_SUCCESS;
                    })
                    .then(argument("number", IntegerArgumentType.integer(1)).executes(context -> {
                        service.start(target, IntegerArgumentType.getInteger(context, "number"));
                        return SINGLE_SUCCESS;
                    })));
        }

        builder.then(literal("stop").executes(context -> {
            service.stop();
            return SINGLE_SUCCESS;
        }));
    }
}
