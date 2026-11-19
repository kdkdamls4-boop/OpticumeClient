package dev.opticumeclient.mixin;

import dev.opticumeclient.client.bots.BotSession;
import dev.opticumeclient.client.bots.IBotListener;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientConfigurationPacketListenerImpl;
import net.minecraft.network.PacketProcessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Environment(EnvType.CLIENT)
@Mixin(ClientConfigurationPacketListenerImpl.class)
public abstract class ClientConfigurationPacketListenerMixin {
    @Redirect(
            method = "*",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;packetProcessor()Lnet/minecraft/network/PacketProcessor;")
    )
    private PacketProcessor opticume$botPacketProcessor(Minecraft instance) {
        BotSession session = ((IBotListener) this).opticume$getBotSession();
        if (session != null && session.getPacketProcessor() != null) {
            return session.getPacketProcessor();
        }
        return instance.packetProcessor();
    }
}
