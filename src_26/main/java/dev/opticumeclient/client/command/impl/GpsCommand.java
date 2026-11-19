package dev.opticumeclient.client.command.impl;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.opticumeclient.client.command.ClientCommand;
import dev.opticumeclient.client.gps.GpsManager;
import dev.opticumeclient.client.util.ChatUtils;
import net.minecraft.commands.SharedSuggestionProvider;
import org.joml.Vector3i;

/**
 * {@code .gps <x> <y> <z> [name]} / {@code .gps off}
 */
public final class GpsCommand extends ClientCommand {
    public GpsCommand() {
        super("gps", "Управление GPS-меткой (.gps <x> <y> <z> [имя] | .gps off)");
    }

    @Override
    public void build(LiteralArgumentBuilder<SharedSuggestionProvider> builder) {
        builder.then(literal("off").executes(context -> {
            if (GpsManager.getInstance().getGpsPosition() == null) {
                ChatUtils.sendError("Нет активной GPS-метки для удаления.");
            } else {
                GpsManager.getInstance().setGpsPosition(null);
                ChatUtils.sendClientMessage("Метка успешно удалена.");
            }
            return SINGLE_SUCCESS;
        }));

        builder.then(argument("x", IntegerArgumentType.integer())
                .then(argument("y", IntegerArgumentType.integer())
                        .then(argument("z", IntegerArgumentType.integer())
                                .executes(context -> setGps(context, false))
                                .then(argument("name", StringArgumentType.greedyString())
                                        .executes(context -> setGps(context, true))))));
    }

    private static int setGps(CommandContext<SharedSuggestionProvider> context, boolean hasName) {
        int x = IntegerArgumentType.getInteger(context, "x");
        int y = IntegerArgumentType.getInteger(context, "y");
        int z = IntegerArgumentType.getInteger(context, "z");
        String name = hasName ? StringArgumentType.getString(context, "name") : "GPS";

        if (x == 0 && y == 0 && z == 0) {
            ChatUtils.sendError("Нельзя указать метку на нулевые координаты.");
            return SINGLE_SUCCESS;
        }

        GpsManager.getInstance().setGpsName(name);
        GpsManager.getInstance().setGpsPosition(new Vector3i(x, y, z));
        ChatUtils.sendClientMessage("Метка \"" + name + "\" установлена: " + x + " " + y + " " + z + ".");
        return SINGLE_SUCCESS;
    }
}
