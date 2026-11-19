package dev.opticumeclient.mixin;

import dev.opticumeclient.client.gps.GpsChatLinks;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.ClickEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(Screen.class)
public abstract class ScreenGpsClickMixin {
    @Inject(method = "defaultHandleGameClickEvent", at = @At("HEAD"), cancellable = true)
    private static void opticume$gameClick(ClickEvent event, Minecraft mc, Screen screen, CallbackInfo ci) {
        if (GpsChatLinks.handleClick(event)) ci.cancel();
    }

    @Inject(method = "defaultHandleClickEvent", at = @At("HEAD"), cancellable = true)
    private static void opticume$click(ClickEvent event, Minecraft mc, Screen screen, CallbackInfo ci) {
        if (GpsChatLinks.handleClick(event)) ci.cancel();
    }
}
