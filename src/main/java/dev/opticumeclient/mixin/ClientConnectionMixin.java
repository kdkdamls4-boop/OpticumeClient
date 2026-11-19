package dev.opticumeclient.mixin;

import dev.opticumeclient.client.autobuy.AutoBuyEngine;
import dev.opticumeclient.client.bots.BotSession;
import dev.opticumeclient.client.bots.BotSessionManager;
import dev.opticumeclient.client.bots.IBotConnection;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(Connection.class)
public class ClientConnectionMixin implements IBotConnection {
    @Unique
    private BotSession opticume$botSession;

    @Override
    public BotSession opticume$getBotSession() {
        return this.opticume$botSession;
    }

    @Override
    public void opticume$setBotSession(BotSession session) {
        this.opticume$botSession = session;
    }

    @Inject(method = "genericsFtw", at = @At("HEAD"), cancellable = true)
    private static void opticume$onReceivePacket(Packet<?> packet, PacketListener listener, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        BotSessionManager bots = BotSessionManager.getInstance();
        if (bots.isBackground(bots.sessionOf(listener))) return;

        if (packet instanceof ClientboundBundlePacket bundlePacket) {
            for (Packet<?> inner : bundlePacket.subPackets()) {
                if (AutoBuyEngine.INSTANCE.onReceivePacket(inner)) {
                    ci.cancel();
                    return;
                }
            }
        } else if (AutoBuyEngine.INSTANCE.onReceivePacket(packet)) {
            ci.cancel();
        }
    }
}
