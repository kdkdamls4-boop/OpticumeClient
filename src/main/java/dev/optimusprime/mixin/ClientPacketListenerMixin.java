package dev.optimusprime.mixin;

import dev.optimusprime.client.modules.ModuleManager;
import dev.optimusprime.client.modules.visuals.KillEffectModule;
import dev.optimusprime.client.modules.visuals.PopChamsModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {

    @Inject(method = "handleEntityEvent", at = @At("HEAD"))
    private void optimus$onEntityEvent(ClientboundEntityEventPacket packet, CallbackInfo ci) {
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
}
