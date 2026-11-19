package dev.opticumeclient.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import dev.opticumeclient.client.modules.visuals.ZoomModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {

    @Shadow
    @Final
    private Minecraft minecraft;
    @Shadow
    private double accumulatedDX;
    @Shadow
    private double accumulatedDY;
    @Shadow
    private boolean isLeftPressed;
    @Shadow
    private boolean isMiddlePressed;
    @Shadow
    private boolean isRightPressed;

    @Inject(method = "onButton", at = @At("HEAD"))
    private void opticume$onButton(long window, MouseButtonInfo buttonInfo, int action, CallbackInfo ci) {
        if (buttonInfo != null) {
            int button = buttonInfo.button();
            boolean pressed = (action == 1);
            if (pressed && this.minecraft != null && this.minecraft.gui != null) {
                var curScreen = this.minecraft.gui.screen();
                if (curScreen instanceof net.minecraft.client.gui.screens.ChatScreen) {
                    dev.opticumeclient.client.gps.GpsManager.getInstance().onChatScreenClick(
                            dev.opticumeclient.client.gui.ClickGuiScreen.normalizeMouseButton(button));
                }
                if (curScreen instanceof net.minecraft.client.gui.screens.ChatScreen
                        || curScreen instanceof dev.opticumeclient.client.gui.ClickGuiScreen) {
                    dev.opticumeclient.client.hud.HudManager.getInstance().onMousePress(
                            dev.opticumeclient.client.gui.ClickGuiScreen.normalizeMouseButton(button));
                }
            }
            boolean inGame = pressed && this.minecraft != null && this.minecraft.gui != null && this.minecraft.gui.screen() == null;
            if (this.minecraft != null && this.minecraft.gui != null && this.minecraft.gui.screen() == null) {
                dev.opticumeclient.client.modules.other.AssistModule.onInputKey(button, action, true);
            }
            if (button == InputConstants.MOUSE_BUTTON_LEFT) {
                this.isLeftPressed = pressed;
                if (inGame) {
                    dev.opticumeclient.client.hud.KeystrokesHud.recordLeftClick();
                }
            } else if (button == InputConstants.MOUSE_BUTTON_MIDDLE) {
                this.isMiddlePressed = pressed;
            } else if (button == InputConstants.MOUSE_BUTTON_RIGHT) {
                this.isRightPressed = pressed;
                if (inGame) {
                    dev.opticumeclient.client.hud.KeystrokesHud.recordRightClick();
                }
            }
        }
    }

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void opticume$onZoomScroll(long window, double xOffset, double yOffset, CallbackInfo ci) {
        if (this.minecraft == null || this.minecraft.getWindow() == null || window != this.minecraft.getWindow().handle()) {
            return;
        }
        ZoomModule zoom = ZoomModule.getInstance();
        if (zoom != null && zoom.isZoomActive() && zoom.isScrollZoom() && yOffset != 0.0) {
            zoom.adjustScrollZoom(yOffset);
            ci.cancel();
        }
    }

    @Inject(method = "turnPlayer", at = @At("HEAD"))
    private void opticume$onZoomTurnPlayer(double movementTime, CallbackInfo ci) {
        ZoomModule zoom = ZoomModule.getInstance();
        if (zoom != null) {
            double factor = zoom.getSensitivityFactor();
            if (factor < 0.999) {
                this.accumulatedDX *= factor;
                this.accumulatedDY *= factor;
            }
        }
    }
}
