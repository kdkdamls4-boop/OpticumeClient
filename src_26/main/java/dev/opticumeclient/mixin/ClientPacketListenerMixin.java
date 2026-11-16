package dev.opticumeclient.mixin;

import dev.opticumeclient.client.modules.ModuleManager;
import dev.opticumeclient.client.modules.movement.VelocityModule;
import dev.opticumeclient.client.modules.visuals.KillEffectModule;
import dev.opticumeclient.client.modules.visuals.PopChamsModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {

    @Inject(method = "handleEntityEvent", at = @At("HEAD"))
    private void opticume$onEntityEvent(ClientboundEntityEventPacket packet, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

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
