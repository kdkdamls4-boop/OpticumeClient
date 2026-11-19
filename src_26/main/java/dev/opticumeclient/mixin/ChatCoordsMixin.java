package dev.opticumeclient.mixin;

import dev.opticumeclient.client.gps.GpsChatLinks;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Environment(EnvType.CLIENT)
@Mixin(ChatComponent.class)
public abstract class ChatCoordsMixin {
    @ModifyVariable(method = {"addServerSystemMessage", "addPlayerMessage"}, at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private Component opticume$clickableCoords(Component message) {
        return GpsChatLinks.rewrite(message);
    }
}
