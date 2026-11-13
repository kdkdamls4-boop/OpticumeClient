package dev.optimusprime.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {

    @Shadow
    private boolean isLeftPressed;
    @Shadow
    private boolean isMiddlePressed;
    @Shadow
    private boolean isRightPressed;

    @Inject(method = "onButton", at = @At("HEAD"))
    private void optimus$onButton(long window, MouseButtonInfo buttonInfo, int action, CallbackInfo ci) {
        if (buttonInfo != null) {
            int button = buttonInfo.button();
            boolean pressed = (action == 1);
            if (button == InputConstants.MOUSE_BUTTON_LEFT) {
                this.isLeftPressed = pressed;
            } else if (button == InputConstants.MOUSE_BUTTON_MIDDLE) {
                this.isMiddlePressed = pressed;
            } else if (button == InputConstants.MOUSE_BUTTON_RIGHT) {
                this.isRightPressed = pressed;
            }
        }
    }
}
