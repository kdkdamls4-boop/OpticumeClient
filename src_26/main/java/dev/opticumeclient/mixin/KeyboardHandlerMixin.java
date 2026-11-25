package dev.opticumeclient.mixin;

import dev.opticumeclient.client.modules.ModuleManager;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(KeyboardHandler.class)
public abstract class KeyboardHandlerMixin {

    @Shadow
    @Final
    private Minecraft minecraft;

    @Inject(method = "keyPress", at = @At("HEAD"))
    private void opticume$onKeyPress(long window, int action, KeyEvent keyEvent, CallbackInfo ci) {
        if (keyEvent != null && this.minecraft != null && this.minecraft.gui != null && this.minecraft.gui.screen() == null) {
            if (action == 1) {
                ModuleManager.getInstance().onKeyPressed(keyEvent.key());
                dev.opticumeclient.client.command.CommandDataStore.getInstance().onKeyPress(keyEvent.key());
            }
            dev.opticumeclient.client.modules.other.AssistModule.onInputKey(keyEvent.key(), action, false);
            dev.opticumeclient.client.modules.other.AuctionHelperModule.onInputKey(keyEvent.key(), action, false);
        }
    }
}
