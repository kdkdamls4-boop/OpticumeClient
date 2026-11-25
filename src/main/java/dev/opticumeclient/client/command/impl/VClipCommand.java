package dev.opticumeclient.client.command.impl;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.opticumeclient.client.command.ClientCommand;
import dev.opticumeclient.client.util.ChatUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;

/**
 * {@code .vclip <y>} teleports vertically in chunked movement packets.
 */
public final class VClipCommand extends ClientCommand {
    public VClipCommand() {
        super("vclip", "Вертикальный телепорт (.vclip <высота>)");
    }

    @Override
    public void build(LiteralArgumentBuilder<SharedSuggestionProvider> builder) {
        builder.then(argument("distance", DoubleArgumentType.doubleArg(-200.0, 200.0)).executes(ctx -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.player == null || mc.player.connection == null) return SINGLE_SUCCESS;
            double dy = DoubleArgumentType.getDouble(ctx, "distance");
            double targetY = mc.player.getY() + dy;
            double x = mc.player.getX();
            double z = mc.player.getZ();

            int steps = Math.max(1, (int) Math.ceil(Math.abs(dy) / 10.0));
            for (int i = 0; i < steps - 1; i++) {
                mc.player.connection.send(new ServerboundMovePlayerPacket.Pos(x, mc.player.getY(), z, mc.player.onGround(), false));
            }
            mc.player.connection.send(new ServerboundMovePlayerPacket.Pos(x, targetY, z, false, false));
            mc.player.setPos(x, targetY, z);
            ChatUtils.sendClientMessage(String.format(java.util.Locale.US, "VClip на %.1f блоков.", dy));
            return SINGLE_SUCCESS;
        }));
    }
}
