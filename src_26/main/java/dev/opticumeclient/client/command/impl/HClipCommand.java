package dev.opticumeclient.client.command.impl;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.opticumeclient.client.command.ClientCommand;
import dev.opticumeclient.client.util.ChatUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;

/**
 * {@code .hclip <dist>} shifts horizontally along camera look direction.
 */
public final class HClipCommand extends ClientCommand {
    public HClipCommand() {
        super("hclip", "Горизонтальный телепорт по взгляду (.hclip <дистанция>)");
    }

    @Override
    public void build(LiteralArgumentBuilder<SharedSuggestionProvider> builder) {
        builder.then(argument("distance", DoubleArgumentType.doubleArg(-100.0, 100.0)).executes(ctx -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.player == null || mc.player.connection == null) return SINGLE_SUCCESS;
            double dist = DoubleArgumentType.getDouble(ctx, "distance");
            double yawRad = Math.toRadians(mc.player.getYRot());
            double dx = -Math.sin(yawRad) * dist;
            double dz = Math.cos(yawRad) * dist;
            double nx = mc.player.getX() + dx;
            double ny = mc.player.getY();
            double nz = mc.player.getZ() + dz;

            int steps = Math.max(1, (int) Math.ceil(Math.abs(dist) / 8.0));
            for (int i = 1; i <= steps; i++) {
                double t = i / (double) steps;
                mc.player.connection.send(new ServerboundMovePlayerPacket.Pos(
                        mc.player.getX() + dx * t, ny, mc.player.getZ() + dz * t, mc.player.onGround(), false));
            }
            mc.player.setPos(nx, ny, nz);
            ChatUtils.sendClientMessage(String.format(java.util.Locale.US, "HClip на %.1f блоков.", dist));
            return SINGLE_SUCCESS;
        }));
    }
}
