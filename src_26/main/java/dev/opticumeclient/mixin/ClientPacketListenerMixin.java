package dev.opticumeclient.mixin;

import dev.opticumeclient.client.bots.BotContext;
import dev.opticumeclient.client.bots.BotSession;
import dev.opticumeclient.client.bots.IBotListener;
import dev.opticumeclient.client.command.CommandManager;
import dev.opticumeclient.client.modules.ModuleManager;
import dev.opticumeclient.client.modules.movement.VelocityModule;
import dev.opticumeclient.client.modules.visuals.KillEffectModule;
import dev.opticumeclient.client.modules.visuals.PopChamsModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.PacketProcessor;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {

    @Redirect(
            method = "*",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;packetProcessor()Lnet/minecraft/network/PacketProcessor;")
    )
    private PacketProcessor opticume$botPacketProcessor(Minecraft instance) {
        BotSession session = ((Object) this instanceof IBotListener botListener)
                ? botListener.opticume$getBotSession()
                : null;
        if (session != null && session.getPacketProcessor() != null) {
            return session.getPacketProcessor();
        }
        return instance.packetProcessor();
    }

    @Redirect(
            method = "handleConfigurationStart",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;clearClientLevel(Lnet/minecraft/client/gui/screens/Screen;)V")
    )
    private void opticume$botClearLevel(Minecraft minecraft, Screen screen) {
        if (!BotContext.isBackgroundContextActive()) {
            minecraft.clearClientLevel(screen);
            return;
        }
        ((ClientPacketListener) (Object) this).clearLevel();
        minecraft.level = null;
        minecraft.player = null;
        minecraft.gameMode = null;
    }

    @Inject(
            method = "setActionBarText",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V", shift = At.Shift.AFTER),
            cancellable = true
    )
    private void opticume$botActionBar(ClientboundSetActionBarTextPacket packet, CallbackInfo ci) {
        if (BotContext.isBackgroundContextActive() && BotContext.current() != null) {
            BotContext.current().setActionBar(packet.text().getString());
            ci.cancel();
        }
    }

    @Inject(
            method = {"setTitleText", "setSubtitleText", "setTitlesAnimation", "handleTitlesClear", "handleTabListCustomisation", "handleBossUpdate"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V", shift = At.Shift.AFTER),
            cancellable = true
    )
    private void opticume$botHudPackets(CallbackInfo ci) {
        if (BotContext.isBackgroundContextActive()) {
            ci.cancel();
        }
    }

    @Inject(method = "sendChat", at = @At("HEAD"), cancellable = true)
    private void opticume$onSendChat(String content, CallbackInfo ci) {
        CommandManager.getInstance().executeCommands(content, ci);
    }

    @Inject(
            method = "handleEntityEvent",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/game/ClientboundEntityEventPacket;getEntity(Lnet/minecraft/world/level/Level;)Lnet/minecraft/world/entity/Entity;")
    )
    private void opticume$onEntityEvent(ClientboundEntityEventPacket packet, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (!mc.isSameThread() || mc.level == null) return;

        byte eventId = packet.getEventId();
        Entity entity = packet.getEntity(mc.level);
        if (entity == null) return;

        // 1. Totem Pop (35)
        if (eventId == 35) {
            PopChamsModule pop = ModuleManager.getInstance().getModule(PopChamsModule.class);
            if (pop != null && pop.isEnabled()) {
                pop.onTotemPop(entity);
            }
        }

        // 2. Entity Death (3)
        if (eventId == 3) {
            KillEffectModule kill = ModuleManager.getInstance().getModule(KillEffectModule.class);
            if (kill != null && kill.isEnabled()) {
                kill.onEntityDeath(entity);
            }
        }
    }

    @Inject(
            method = "handleSetEntityMotion",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;lerpMotion(Lnet/minecraft/world/phys/Vec3;)V"),
            cancellable = true
    )
    private void opticume$onEntityMotion(ClientboundSetEntityMotionPacket packet, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || packet.id() != mc.player.getId()) return;

        VelocityModule vel = ModuleManager.getInstance().getModule(VelocityModule.class);
        if (vel == null || !vel.isEnabled()) return;

        if (vel.isCancelMode()) {
            ci.cancel();
            return;
        }
        if (vel.isGrimSkipMode()) {
            if (mc.player.tickCount % 2 == 0) {
                ci.cancel();
            }
            return;
        }

        double h = vel.getHorizontalFactor();
        double v = vel.getVerticalFactor();
        if (h == 0.0 && v == 0.0) {
            ci.cancel();
            return;
        }
        if (h != 1.0 || v != 1.0) {
            Vec3 m = packet.movement();
            mc.player.lerpMotion(new Vec3(m.x * h, m.y * v, m.z * h));
            ci.cancel();
        }
    }

    @Inject(
            method = "handleExplosion",
            at = @At(value = "INVOKE", target = "Ljava/util/Optional;ifPresent(Ljava/util/function/Consumer;)V"),
            cancellable = true
    )
    private void opticume$onExplosionKnockback(ClientboundExplodePacket packet, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        VelocityModule vel = ModuleManager.getInstance().getModule(VelocityModule.class);
        if (vel == null || !vel.isEnabled() || !vel.isExplosions()) return;

        if (vel.isCancelMode() || (vel.isGrimSkipMode() && mc.player.tickCount % 2 == 0)) {
            ci.cancel();
            return;
        }

        double h = vel.getHorizontalFactor();
        double v = vel.getVerticalFactor();
        if (h == 0.0 && v == 0.0) {
            ci.cancel();
            return;
        }
        if (h != 1.0 || v != 1.0) {
            packet.playerKnockback().ifPresent(kb ->
                    mc.player.pushFromExplosion(new Vec3(kb.x * h, kb.y * v, kb.z * h)));
            ci.cancel();
        }
    }
}
