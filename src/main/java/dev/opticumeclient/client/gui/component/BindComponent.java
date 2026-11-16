package dev.opticumeclient.client.gui.component;

import dev.opticumeclient.client.modules.Module;
import dev.opticumeclient.client.render.Animation;
import dev.opticumeclient.client.render.ColorUtils;
import dev.opticumeclient.client.render.Easing;
import dev.opticumeclient.client.render.Render2DHelper;
import dev.opticumeclient.client.render.Theme;
import dev.opticumeclient.client.render.ThemeManager;
import net.minecraft.client.gui.GuiGraphicsExtractor; 
import com.mojang.blaze3d.platform.InputConstants; 

/**
 * Keybind assignment button component ("Keybind: [R]", LMB enters "[Press a key...]",
 * RMB / Delete / Backspace / Escape resets bind to 0).
 */
public class BindComponent extends Component {
    private final Module module;
    private boolean listening;
    private final Animation listenAnimation;

    public BindComponent(Module module, float width) {
        super(null, width, 18.0f);
        this.module = module;
        this.listenAnimation = new Animation(0.0f, 150L, Easing.EASE_OUT_QUAD);
    }

    public static String getKeyName(int key) {
        if (key <= 0 || key == InputConstants.UNKNOWN.getValue()) return "NONE";
        return switch (key) {
            case InputConstants.KEY_RSHIFT -> "RSHIFT";
            case InputConstants.KEY_LSHIFT -> "LSHIFT";
            case InputConstants.KEY_RCONTROL -> "RCTRL";
            case InputConstants.KEY_LCONTROL -> "LCTRL";
            case InputConstants.KEY_RALT -> "RALT";
            case InputConstants.KEY_LALT -> "LALT";
            case InputConstants.KEY_SPACE -> "SPACE";
            case InputConstants.KEY_TAB -> "TAB";
            case InputConstants.KEY_CAPSLOCK -> "CAPS";
            case InputConstants.KEY_INSERT -> "INS";
            case InputConstants.KEY_HOME -> "HOME";
            case InputConstants.KEY_END -> "END";
            case InputConstants.KEY_PAGEUP -> "PGUP";
            case InputConstants.KEY_PAGEDOWN -> "PGDN";
            case InputConstants.KEY_UP -> "UP";
            case InputConstants.KEY_DOWN -> "DOWN";
            case InputConstants.KEY_LEFT -> "LEFT";
            case InputConstants.KEY_RIGHT -> "RIGHT";
            default -> {
                if (key >= InputConstants.KEY_F1 && key <= InputConstants.KEY_F12) {
                    yield "F" + (key - InputConstants.KEY_F1 + 1);
                }
                String glfwName; try { glfwName = InputConstants.Type.KEYBOARD.getOrCreate(key).getDisplayName().getString(); } catch (Throwable t) { glfwName = null; }
                yield (glfwName != null && !glfwName.isBlank()) ? glfwName.toUpperCase() : "KEY_" + key;
            }
        };
    }

    @Override
    public void render(GuiGraphicsExtractor context, float mouseX, float mouseY, float delta) {
        if (!isVisible()) return;

        listenAnimation.animateTo(listening ? 1.0f : 0.0f);
        float anim = listenAnimation.getValue();
        Theme theme = ThemeManager.getInstance().getCurrentTheme();

        // Label on left
        Render2DHelper.drawScaledText(context, "Keybind:", x + 4.0f, y + 5.0f,
                theme.getMutedTextColor(), 0.76f, false);

        // Bind pill on right
        String keyLabel = listening ? "[Press a key...]" : "[" + getKeyName(module.getKeyBind()) + "]";
        float fontScale = 0.72f;
        float textW = Render2DHelper.getTextWidth(keyLabel, fontScale);
        float btnW = textW + 10.0f;
        float btnH = 13.0f;
        float btnX = x + width - btnW - 4.0f;
        float btnY = y + (height - btnH) / 2.0f;

        int btnBg = ColorUtils.interpolateColor(0x25FFFFFF, ColorUtils.withAlpha(theme.getPrimaryColor(), 195), anim);
        if (listening) {
            Render2DHelper.drawGlow(context, btnX, btnY, btnW, btnH, 3.5f,
                    ColorUtils.withAlpha(theme.getPrimaryColor(), (int) (130 * anim)));
        }

        Render2DHelper.drawRoundedRect(context, btnX, btnY, btnW, btnH, 3.0f, btnBg);
        Render2DHelper.drawRoundedOutline(context, btnX, btnY, btnW, btnH, 3.0f, 1.0f,
                listening ? theme.getSecondaryColor() : 0x55FFFFFF);

        int textColor = listening ? 0xFFFFFFFF : (module.getKeyBind() > 0 ? theme.getPrimaryColor() : theme.getTextColor());
        Render2DHelper.drawScaledText(context, keyLabel,
                btnX + (btnW - textW) * 0.5f, btnY + 3.0f, textColor, fontScale, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!isVisible()) return false;
        if (isHovered(mouseX, mouseY)) {
            if (button == 0) {
                listening = !listening;
                return true;
            } else if (button == 1 || button == 2) {
                module.setKeyBind(0);
                listening = false;
                return true;
            }
        } else if (listening) {
            listening = false;
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (listening) {
            if (keyCode == InputConstants.KEY_ESCAPE || keyCode == InputConstants.KEY_DELETE || keyCode == InputConstants.KEY_BACKSPACE) {
                module.setKeyBind(0);
            } else {
                module.setKeyBind(keyCode);
            }
            listening = false;
            return true;
        }
        return false;
    }

    public boolean isListening() {
        return listening;
    }

    public void setListening(boolean listening) {
        this.listening = listening;
    }

    public Module getModule() {
        return module;
    }
}
