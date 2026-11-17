package dev.opticumeclient.mixin;

import dev.opticumeclient.client.render.font.OpticumeGuiContext;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(GuiGraphicsExtractor.class)
public abstract class GuiGraphicsExtractorMixin implements OpticumeGuiContext {
    @Shadow
    @Final
    private GuiRenderState guiRenderState;

    @Override
    public void opticume$submitElement(GuiElementRenderState state) {
        if (this.guiRenderState != null && state != null) {
            this.guiRenderState.addGuiElement(state);
        }
    }

    @Override
    public ScreenRectangle opticume$peekScissor() {
        return ScissorHelper.peek((GuiGraphicsExtractor) (Object) this);
    }
}
