package dev.opticumeclient.mixin;

import dev.opticumeclient.client.autobuy.AutoBuyEngine;
import dev.opticumeclient.client.bots.BotSession;
import dev.opticumeclient.client.bots.BotSessionManager;
import dev.opticumeclient.client.bots.IBotConnection;
import dev.opticumeclient.client.modules.combat.BackTrackModule;
import dev.opticumeclient.client.modules.movement.AirStuckModule;
import dev.opticumeclient.client.modules.movement.NoFallModule;
import dev.opticumeclient.client.modules.visuals.FreeCamModule;
import dev.opticumeclient.client.services.auth.BanTrackerStore;
import dev.opticumeclient.client.services.network.ProxyManager;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelPipeline;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Connection;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.server.network.EventLoopGroupHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.net.InetSocketAddress;

@Environment(EnvType.CLIENT)
@Mixin(Connection.class)
public abstract class ClientConnectionMixin implements IBotConnection, ProxyManager.IProxyConnection {
    @Unique
    private BotSession opticume$botSession;
    @Unique
    private ProxyManager.PendingTunnel opticume$pendingTunnel;

    @Unique
    private static final ThreadLocal<Boolean> OPTICUME_BYPASS_SEND = ThreadLocal.withInitial(() -> false);

    @Shadow
    public abstract void send(Packet<?> packet, ChannelFutureListener listener, boolean flush);

    @Override
    public BotSession opticume$getBotSession() {
        return this.opticume$botSession;
    }

    @Override
    public void opticume$setBotSession(BotSession session) {
        this.opticume$botSession = session;
    }

    @Override
    public void opticume$setPendingTunnel(ProxyManager.PendingTunnel tunnel) {
        this.opticume$pendingTunnel = tunnel;
    }

    @Override
    public ProxyManager.PendingTunnel opticume$consumePendingTunnel() {
        ProxyManager.PendingTunnel t = this.opticume$pendingTunnel;
        this.opticume$pendingTunnel = null;
        return t;
    }

    @Inject(method = "connect", at = @At("HEAD"), order = 500)
    private static void opticume$bindProxyTunnel(InetSocketAddress target, EventLoopGroupHolder group, Connection connection, CallbackInfoReturnable<ChannelFuture> cir) {
        ProxyManager.PendingTunnel tunnel = ProxyManager.getInstance().prepareTunnel(target);
        if (connection != null && tunnel != null) {
            ((ProxyManager.IProxyConnection) (Object) connection).opticume$setPendingTunnel(tunnel);
        }
    }

    @ModifyVariable(method = "connect", at = @At("HEAD"), argsOnly = true, ordinal = 0, order = 1500)
    private static InetSocketAddress opticume$rewriteConnectProxy(InetSocketAddress original) {
        return ProxyManager.getInstance().consumeRewriteTarget(original);
    }

    @Inject(method = "configurePacketHandler", at = @At("HEAD"))
    private void opticume$installProxyPipeline(ChannelPipeline pipeline, CallbackInfo ci) {
        ProxyManager.PendingTunnel tunnel = opticume$consumePendingTunnel();
        if (tunnel != null) {
            ProxyManager.getInstance().installPipeline(pipeline, tunnel);
        }
    }

    @Inject(method = "disconnect(Lnet/minecraft/network/DisconnectionDetails;)V", at = @At("HEAD"))
    private void opticume$trackBanDisconnect(DisconnectionDetails details, CallbackInfo ci) {
        if (this.opticume$botSession == null || !BotSessionManager.getInstance().isBackground(this.opticume$botSession)) {
            if (details != null && details.reason() != null) {
                BanTrackerStore.getInstance().onDisconnectReason(details.reason().getString());
            }
        }
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
            return;
        }

        BackTrackModule bt = BackTrackModule.getInstance();
        if (bt != null && bt.onReceivePacket(packet, listener)) {
            ci.cancel();
        }
    }

    @Inject(
            method = "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;Z)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void opticume$onSendPacket(Packet<?> packet, ChannelFutureListener listener, boolean flush, CallbackInfo ci) {
        if (OPTICUME_BYPASS_SEND.get()) return;
        if (this.opticume$botSession != null && BotSessionManager.getInstance().isBackground(this.opticume$botSession)) {
            return;
        }
        if (!(packet instanceof ServerboundMovePlayerPacket mp)) {
            return;
        }

        AirStuckModule airStuck = AirStuckModule.getInstance();
        if (airStuck != null && airStuck.shouldCancelMovementPackets()) {
            ci.cancel();
            return;
        }

        FreeCamModule freeCam = FreeCamModule.getInstance();
        if (freeCam != null && freeCam.shouldCancelMovementPackets()) {
            ci.cancel();
            return;
        }

        NoFallModule noFall = NoFallModule.getInstance();
        if (noFall != null && noFall.shouldForceGroundPacket() && !mp.isOnGround()) {
            ServerboundMovePlayerPacket replacement;
            if (mp.hasPosition() && mp.hasRotation()) {
                replacement = new ServerboundMovePlayerPacket.PosRot(
                        mp.getX(0.0), mp.getY(0.0), mp.getZ(0.0),
                        mp.getYRot(0.0f), mp.getXRot(0.0f),
                        true, mp.horizontalCollision()
                );
            } else if (mp.hasPosition()) {
                replacement = new ServerboundMovePlayerPacket.Pos(
                        mp.getX(0.0), mp.getY(0.0), mp.getZ(0.0),
                        true, mp.horizontalCollision()
                );
            } else if (mp.hasRotation()) {
                replacement = new ServerboundMovePlayerPacket.Rot(
                        mp.getYRot(0.0f), mp.getXRot(0.0f),
                        true, mp.horizontalCollision()
                );
            } else {
                replacement = new ServerboundMovePlayerPacket.StatusOnly(true, mp.horizontalCollision());
            }

            ci.cancel();
            OPTICUME_BYPASS_SEND.set(true);
            try {
                this.send(replacement, listener, flush);
            } finally {
                OPTICUME_BYPASS_SEND.set(false);
            }
        }
    }
}
