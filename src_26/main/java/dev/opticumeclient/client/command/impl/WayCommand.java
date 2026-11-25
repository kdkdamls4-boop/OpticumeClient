package dev.opticumeclient.client.command.impl;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.opticumeclient.client.command.ClientCommand;
import dev.opticumeclient.client.command.CommandDataStore;
import dev.opticumeclient.client.gps.GpsManager;
import dev.opticumeclient.client.render.ThemeManager;
import dev.opticumeclient.client.util.ChatUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.SharedSuggestionProvider;
import org.joml.Vector3i;

import java.util.List;

/**
 * {@code .way add <name> [x y z]} / {@code .way del <name>} / {@code .way list} / {@code .way tp <name>} / {@code .way clear}
 */
public final class WayCommand extends ClientCommand {
    public WayCommand() {
        super("way", "Вейпоинты (.way add <имя> [x y z] | del | list | tp | clear)");
    }

    @Override
    public void build(LiteralArgumentBuilder<SharedSuggestionProvider> builder) {
        builder.then(literal("list").executes(ctx -> {
            List<CommandDataStore.WaypointEntry> list = CommandDataStore.getInstance().getWaypoints();
            if (list.isEmpty()) {
                ChatUtils.sendClientMessage("Сохранённых вейпоинтов нет.");
            } else {
                ChatUtils.sendClientMessage("Вейпоинты (" + list.size() + "):");
                for (CommandDataStore.WaypointEntry w : list) {
                    ChatUtils.sendClientMessage(" §b" + w.name() + "§7 -> §f" + w.x() + " " + w.y() + " " + w.z());
                }
            }
            return SINGLE_SUCCESS;
        }));

        builder.then(literal("clear").executes(ctx -> {
            int count = CommandDataStore.getInstance().clearWaypoints();
            ChatUtils.sendClientMessage("Удалено вейпоинтов: §e" + count);
            return SINGLE_SUCCESS;
        }));

        builder.then(literal("del")
                .then(argument("name", StringArgumentType.word()).executes(ctx -> {
                    String name = StringArgumentType.getString(ctx, "name");
                    if (CommandDataStore.getInstance().removeWaypoint(name)) {
                        ChatUtils.sendClientMessage("Вейпоинт §b" + name + " §fудалён.");
                    } else {
                        ChatUtils.sendError("Вейпоинт \"" + name + "\" не найден.");
                    }
                    return SINGLE_SUCCESS;
                })));

        builder.then(literal("tp")
                .then(argument("name", StringArgumentType.word()).executes(ctx -> {
                    String name = StringArgumentType.getString(ctx, "name");
                    CommandDataStore.WaypointEntry w = CommandDataStore.getInstance().getWaypoint(name);
                    if (w == null) {
                        ChatUtils.sendError("Вейпоинт \"" + name + "\" не найден.");
                        return SINGLE_SUCCESS;
                    }
                    GpsManager.getInstance().setGpsName(w.name());
                    GpsManager.getInstance().setGpsPosition(new Vector3i(w.x(), w.y(), w.z()));
                    ChatUtils.sendClientMessage("GPS наведён на вейпоинт §b" + w.name() + " §7(" + w.x() + " " + w.y() + " " + w.z() + ")");
                    return SINGLE_SUCCESS;
                })));

        builder.then(literal("add")
                .then(argument("name", StringArgumentType.word())
                        .executes(ctx -> {
                            Minecraft mc = Minecraft.getInstance();
                            if (mc == null || mc.player == null) return SINGLE_SUCCESS;
                            String name = StringArgumentType.getString(ctx, "name");
                            int x = (int) Math.floor(mc.player.getX());
                            int y = (int) Math.floor(mc.player.getY());
                            int z = (int) Math.floor(mc.player.getZ());
                            int color = ThemeManager.getInstance().getAccentColor(CommandDataStore.getInstance().getWaypoints().size() * 60);
                            if (CommandDataStore.getInstance().putWaypoint(name, x, y, z, color)) {
                                ChatUtils.sendClientMessage("Вейпоинт §b" + name + " §fсоздан: " + x + " " + y + " " + z);
                            } else {
                                ChatUtils.sendError("Не удалось сохранить вейпоинт: файл opticumeclient/commands.json повреждён.");
                            }
                            return SINGLE_SUCCESS;
                        })
                        .then(argument("x", IntegerArgumentType.integer())
                                .then(argument("y", IntegerArgumentType.integer())
                                        .then(argument("z", IntegerArgumentType.integer()).executes(ctx -> {
                                            String name = StringArgumentType.getString(ctx, "name");
                                            int x = IntegerArgumentType.getInteger(ctx, "x");
                                            int y = IntegerArgumentType.getInteger(ctx, "y");
                                            int z = IntegerArgumentType.getInteger(ctx, "z");
                                            int color = ThemeManager.getInstance().getAccentColor(CommandDataStore.getInstance().getWaypoints().size() * 60);
                                            if (CommandDataStore.getInstance().putWaypoint(name, x, y, z, color)) {
                                                ChatUtils.sendClientMessage("Вейпоинт §b" + name + " §fсоздан: " + x + " " + y + " " + z);
                                            } else {
                                                ChatUtils.sendError("Не удалось сохранить вейпоинт: файл opticumeclient/commands.json повреждён.");
                                            }
                                            return SINGLE_SUCCESS;
                                        }))))));
    }
}
