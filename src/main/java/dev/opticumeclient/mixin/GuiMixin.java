package dev.opticumeclient.mixin;

import dev.opticumeclient.client.bots.BotContext;
import dev.opticumeclient.client.bots.BotSession;
import dev.opticumeclient.client.ui.menu.OpticumeMainMenuScreen;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Replaces vanilla {@link TitleScreen} transitions with {@link OpticumeMainMenuScreen}
 * and isolates background bot screen updates from the foreground UI.
 */
@Environment(EnvType.CLIENT)
@Mixin(Gui.class)
public abstract class GuiMixin {
    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void opticume$replaceTitleScreen(Screen screen, CallbackInfo ci) {
        if (BotContext.isBackgroundContextActive()) {
            BotSession bg = BotContext.current();
            if (bg != null && !(screen instanceof LevelLoadingScreen)) {
                bg.setScreen(screen);
            }
            ci.cancel();
            return;
        }
        if (screen instanceof TitleScreen && !OpticumeMainMenuScreen.consumeVanillaBypass()) {
            ((Gui) (Object) this).setScreen(new OpticumeMainMenuScreen());
            ci.cancel();
        }
    }
}
