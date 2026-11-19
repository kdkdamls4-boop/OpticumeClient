package dev.opticumeclient.mixin;

import dev.opticumeclient.client.bots.BotSession;
import dev.opticumeclient.client.bots.BotSessionManager;
import dev.opticumeclient.client.bots.IBotConnection;
import dev.opticumeclient.client.bots.IBotListener;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.Connection;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.PacketProcessor;
import net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket;
import net.minecraft.network.protocol.common.ServerboundResourcePackPacket;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(ClientCommonPacketListenerImpl.class)
public abstract class ClientCommonPacketListenerMixin implements IBotListener {
    @Shadow @Final protected Minecraft minecraft;
    @Shadow @Final protected Connection connection;

    @Override
    public BotSession opticume$getBotSession() {
        return ((IBotConnection) (Object) this.connection).opticume$getBotSession();
    }

    @Redirect(
            method = "*",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;packetProcessor()Lnet/minecraft/network/PacketProcessor;")
    )
    private PacketProcessor opticume$botPacketProcessor(Minecraft instance) {
        BotSession session = opticume$getBotSession();
        if (session != null && session.getPacketProcessor() != null) {
            return session.getPacketProcessor();
        }
        return instance.packetProcessor();
    }

    @Inject(method = "onDisconnect", at = @At("HEAD"), cancellable = true)
    private void opticume$botOnDisconnect(DisconnectionDetails details, CallbackInfo ci) {
        BotSession session = opticume$getBotSession();
        if (session == null) return;

        BotSessionManager manager = BotSessionManager.getInstance();
        if (session == manager.getMainSession()) {
            if (manager.isForeground(session)) return;
            ci.cancel();
            ClientCommonPacketListenerImpl self = (ClientCommonPacketListenerImpl) (Object) this;
            this.minecraft.execute(() -> {
                manager.switchToMain();
                self.onDisconnect(details);
            });
            return;
        }
        if (details != null && details.reason() != null) {
            session.setDisconnectReason(details.reason().getString());
        }
        this.minecraft.execute(() -> manager.handleBotDisconnect(session));
        ci.cancel();
    }

    @Inject(method = "handleResourcePackPush", at = @At("HEAD"), cancellable = true)
    private void opticume$botResourcePack(ClientboundResourcePackPushPacket packet, CallbackInfo ci) {
        BotSession session = opticume$getBotSession();
        if (session == null || session == BotSessionManager.getInstance().getMainSession()) {
            return;
        }
        this.connection.send(new ServerboundResourcePackPacket(packet.id(), ServerboundResourcePackPacket.Action.ACCEPTED));
        this.connection.send(new ServerboundResourcePackPacket(packet.id(), ServerboundResourcePackPacket.Action.DOWNLOADED));
        this.connection.send(new ServerboundResourcePackPacket(packet.id(), ServerboundResourcePackPacket.Action.SUCCESSFULLY_LOADED));
        ci.cancel();
    }
}
