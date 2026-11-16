package dev.opticumeclient.mixin;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import dev.opticumeclient.client.ui.menu.OpticumeButtonStyle;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Applies Desire frosted-glass track and handle styling to vanilla Minecraft sliders.
 */
@Environment(EnvType.CLIENT)
@Mixin(AbstractSliderButton.class)
public abstract class AbstractSliderButtonMixin {
    @Shadow
    protected double value;

    @Redirect(
            method = "extractWidgetRenderState",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIIII)V"
            )
    )
    private void opticume$renderDesireSlider(
            GuiGraphicsExtractor context,
            RenderPipeline pipeline,
            Identifier sprite,
            int x, int y, int width, int height, int color
    ) {
        if (sprite != null && sprite.getPath().contains("handle")) {
            return;
        }
        AbstractSliderButton self = (AbstractSliderButton) (Object) this;
        OpticumeButtonStyle.renderVanillaSlider(
                context,
                self,
                self.getX(),
                self.getY(),
                self.getWidth(),
                self.getHeight(),
                this.value,
                self.isActive(),
                self.isHoveredOrFocused(),
                self.getAlpha()
        );
    }
}
