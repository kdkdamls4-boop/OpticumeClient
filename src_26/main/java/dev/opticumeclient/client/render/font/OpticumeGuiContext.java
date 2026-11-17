package dev.opticumeclient.client.render.font;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public interface OpticumeGuiContext {
    void opticume$submitElement(GuiElementRenderState state);

    ScreenRectangle opticume$peekScissor();

    final class ScissorHelper {
        private static final Field SCISSOR_FIELD;
        private static final Method PEEK_METHOD;

        static {
            Field f = null;
            Method m = null;
            try {
                f = GuiGraphicsExtractor.class.getDeclaredField("scissorStack");
                f.setAccessible(true);
                m = f.getType().getDeclaredMethod("peek");
                m.setAccessible(true);
            } catch (Throwable ignored) {
            }
            SCISSOR_FIELD = f;
            PEEK_METHOD = m;
        }

        private ScissorHelper() {
        }

        public static ScreenRectangle peek(GuiGraphicsExtractor graphics) {
            if (graphics == null || SCISSOR_FIELD == null || PEEK_METHOD == null) {
                return null;
            }
            try {
                Object stack = SCISSOR_FIELD.get(graphics);
                if (stack != null) {
                    return (ScreenRectangle) PEEK_METHOD.invoke(stack);
                }
            } catch (Throwable ignored) {
            }
            return null;
        }
    }
}
