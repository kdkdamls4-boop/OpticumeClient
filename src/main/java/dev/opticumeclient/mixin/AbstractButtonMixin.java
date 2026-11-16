package dev.opticumeclient.mixin;

import dev.opticumeclient.client.ui.menu.OpticumeButtonStyle;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Applies Desire frosted-glass pill styling consistently across all standard vanilla
 * Minecraft buttons (Pause, Options, Singleplayer, Multiplayer, etc.).
 */
@Environment(EnvType.CLIENT)
@Mixin(AbstractButton.class)
public abstract class AbstractButtonMixin {
    @Inject(method = "extractDefaultSprite", at = @At("HEAD"), cancellable = true)
    private void opticume$renderDesireButtonSprite(GuiGraphicsExtractor context, CallbackInfo ci) {
        AbstractButton self = (AbstractButton) (Object) this;
        OpticumeButtonStyle.renderVanillaButton(
                context,
                self,
                self.getX(),
                self.getY(),
                self.getWidth(),
                self.getHeight(),
                self.isActive(),
                self.isHoveredOrFocused(),
                self.getAlpha()
        );
        ci.cancel();
    }
}
