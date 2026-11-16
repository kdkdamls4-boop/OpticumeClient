package dev.opticumeclient.client.gui;

import dev.opticumeclient.client.gui.component.BindComponent;
import dev.opticumeclient.client.gui.component.BooleanComponent;
import dev.opticumeclient.client.gui.component.ColorComponent;
import dev.opticumeclient.client.gui.component.Component;
import dev.opticumeclient.client.gui.component.ModeComponent;
import dev.opticumeclient.client.gui.component.MultiBoxComponent;
import dev.opticumeclient.client.gui.component.SliderComponent;
import dev.opticumeclient.client.modules.Module;
import dev.opticumeclient.client.render.Animation;
import dev.opticumeclient.client.render.ColorUtils;
import dev.opticumeclient.client.render.Easing;
import dev.opticumeclient.client.render.Render2DHelper;
import dev.opticumeclient.client.render.Theme;
import dev.opticumeclient.client.render.ThemeManager;
import dev.opticumeclient.client.setting.BooleanSetting;
import dev.opticumeclient.client.setting.ColorSetting;
import dev.opticumeclient.client.setting.ModeSetting;
import dev.opticumeclient.client.setting.MultiBoxSetting;
import dev.opticumeclient.client.setting.NumberSetting;
import dev.opticumeclient.client.setting.Setting;
import net.minecraft.client.gui.GuiGraphicsExtractor; 
import com.mojang.blaze3d.platform.InputConstants; 

import java.util.ArrayList;
import java.util.List;

/**
 * Module card with liquid glass background (Render2DHelper.drawLiquidGlass),
 * cursor-reactive specular rim (Render2DHelper.drawSpecularBorder),
 * active left neon strip (Render2DHelper.drawActiveCardIndicator),
 * quick MMB keybinding, LMB toggle, and smooth RMB settings expansion
 * for both enabled and disabled modules.
 */
public class ModuleCard {
    public static final float DEFAULT_CARD_WIDTH = 234.0f;
    public static final float HEADER_HEIGHT = 40.0f;
    public static final float COMPACT_HEADER_HEIGHT = 22.0f;
    public static final float CARD_RADIUS = 6.0f;

    private final Module module;
    private final List<Component> components = new ArrayList<>();

    private float x;
    private float y;
    private float width = DEFAULT_CARD_WIDTH;
    private float height = HEADER_HEIGHT;
    private boolean compactMode = false;

    private boolean bindingKey = false;
    private final Animation hoverAnimation = new Animation(0.0f, 150L, Easing.EASE_OUT_QUAD);

    public ModuleCard(Module module) {
        this(module, DEFAULT_CARD_WIDTH, false);
    }

    public ModuleCard(Module module, float width, boolean compactMode) {
        this.module = module;
        this.width = width;
        this.compactMode = compactMode;

        // Build components for all module settings
        for (Setting<?> setting : module.getSettings()) {
            if (setting instanceof BooleanSetting boolSetting) {
                components.add(new BooleanComponent(boolSetting, width - 8.0f));
            } else if (setting instanceof NumberSetting numberSetting) {
                components.add(new SliderComponent(numberSetting, width - 8.0f));
            } else if (setting instanceof ModeSetting modeSetting) {
                components.add(new ModeComponent(modeSetting, width - 8.0f));
            } else if (setting instanceof MultiBoxSetting multiBoxSetting) {
                components.add(new MultiBoxComponent(multiBoxSetting, width - 8.0f));
            } else if (setting instanceof ColorSetting colorSetting) {
                components.add(new ColorComponent(colorSetting, width - 8.0f));
            }
        }
        // Always include BindComponent at the end of the settings list
        components.add(new BindComponent(module, width - 8.0f));
    }

    public float getHeaderHeight() {
        return compactMode ? COMPACT_HEADER_HEIGHT : HEADER_HEIGHT;
    }

    public float calculateTargetHeight() {
        float baseH = getHeaderHeight();
        float expandVal = module.getExpandAnimation().getValue();
        if (!module.isExpanded() && expandVal <= 0.002f) {
            return baseH;
        }

        float totalComponentsHeight = 0.0f;
        for (Component c : components) {
            if (!c.isVisible()) continue;
            totalComponentsHeight += c.getHeight() + 3.0f;
        }

        if (totalComponentsHeight <= 0.0f) {
            return baseH;
        }
        return baseH + (totalComponentsHeight + 6.0f) * expandVal;
    }

    public void render(GuiGraphicsExtractor context, float mouseX, float mouseY, float delta) {
        // Ensure animations stay synchronized with module state
        module.getToggleAnimation().animateTo(module.isEnabled() ? 1.0f : 0.0f);
        module.getExpandAnimation().animateTo(module.isExpanded() ? 1.0f : 0.0f);

        float baseH = getHeaderHeight();
        this.height = calculateTargetHeight();

        boolean hovered = isHovered(mouseX, mouseY);
        hoverAnimation.animateTo(hovered ? 1.0f : 0.0f);

        float hoverVal = hoverAnimation.getValue();
        float toggleProgress = module.getToggleAnimation().getValue();
        float expandProgress = module.getExpandAnimation().getValue();

        Theme theme = ThemeManager.getInstance().getCurrentTheme();
        int primary = theme.getPrimaryColor();
        int secondary = theme.getSecondaryColor();

        // 1. Liquid Glass Card Backdrop
        int baseAlpha = (int) (200 + 25 * hoverVal + 20 * toggleProgress);
        int glowAlpha = (int) (12 * hoverVal + 38 * toggleProgress);
        Render2DHelper.drawLiquidGlass(context, x, y, width, height, CARD_RADIUS, baseAlpha, glowAlpha);

        // 2. Specular Cursor-Reactive Border Highlight
        Render2DHelper.drawSpecularBorder(context, x, y, width, height, CARD_RADIUS, mouseX, mouseY, primary);

        // 3. Left Active Neon Indicator Bar
        if (toggleProgress > 0.01f) {
            Render2DHelper.drawActiveCardIndicator(context, x + 1.5f, y, baseH, toggleProgress, primary, secondary);
        }

        if (compactMode) {
            renderCompactHeader(context, theme, baseH, toggleProgress, expandProgress);
        } else {
            renderFullHeader(context, theme, baseH, toggleProgress, expandProgress);
        }

        // 4. Expanded Settings Components (works for both enabled and disabled modules)
        if (expandProgress > 0.02f) {
            float compY = y + baseH + 3.0f;
            float compX = x + 4.0f;
            float compW = width - 8.0f;

            // Subtle divider line below card header
            int divCol = ColorUtils.withAlpha(primary, (int) (65 * expandProgress));
            Render2DHelper.drawRect(context, x + 6.0f, y + baseH - 1.0f, width - 12.0f, 1.0f, divCol);

            for (Component c : components) {
                if (!c.isVisible()) continue;
                c.setPosition(compX, compY, compW);
                if (compY + c.getHeight() <= y + height + 8.0f) {
                    c.render(context, mouseX, mouseY, delta);
                }
                compY += c.getHeight() + 3.0f;
            }
        }
    }

    private void renderFullHeader(GuiGraphicsExtractor context, Theme theme, float baseH, float toggleProgress, float expandProgress) {
        int primary = theme.getPrimaryColor();
        int secondary = theme.getSecondaryColor();

        float textLeft = x + (toggleProgress > 0.05f ? 10.0f : 8.0f);
        int nameColor = ColorUtils.interpolateColor(theme.getTextColor(), primary, toggleProgress * 0.45f);
        Render2DHelper.drawScaledText(context, module.getName(), textLeft, y + 7.5f, nameColor, 0.88f, false);

        String desc = module.getDescription();
        if (desc != null && !desc.isEmpty()) {
            String trimmed = desc.length() > 30 ? desc.substring(0, 28) + "..." : desc;
            Render2DHelper.drawScaledText(context, trimmed, textLeft, y + 22.0f, theme.getMutedTextColor(), 0.66f, false);
        }

        float controlsRight = x + width - 6.0f;

        // Expand arrow indicator
        float arrowW = 10.0f;
        float arrowX = controlsRight - arrowW;
        String arrow = module.isExpanded() ? "▴" : "▾";
        int arrowCol = ColorUtils.interpolateColor(theme.getMutedTextColor(), primary, expandProgress);
        Render2DHelper.drawScaledText(context, arrow, arrowX + 1.0f, y + 14.0f, arrowCol, 0.78f, false);
        controlsRight -= (arrowW + 4.0f);

        // Capsule Toggle Switch (20x10px)
        float switchW = 20.0f;
        float switchH = 10.0f;
        float switchX = controlsRight - switchW;
        float switchY = y + (baseH - switchH) * 0.5f;
        float switchRadius = switchH * 0.5f;

        Render2DHelper.drawRoundedRect(context, switchX, switchY, switchW, switchH, switchRadius, 0x35FFFFFF);
        if (toggleProgress > 0.02f) {
            Render2DHelper.drawGradientRoundedRect(context, switchX, switchY, switchW, switchH, switchRadius,
                    ColorUtils.withAlpha(primary, (int) (245 * toggleProgress)),
                    ColorUtils.withAlpha(secondary, (int) (245 * toggleProgress)));
            Render2DHelper.drawGlow(context, switchX, switchY, switchW, switchH, 3.5f * toggleProgress,
                    ColorUtils.withAlpha(primary, (int) (90 * toggleProgress)));
        }
        Render2DHelper.drawRoundedOutline(context, switchX, switchY, switchW, switchH, switchRadius, 1.0f,
                ColorUtils.interpolateColor(0x40FFFFFF, primary, toggleProgress));

        float knobSize = switchH - 2.5f;
        float knobX = switchX + 1.25f + (switchW - knobSize - 2.5f) * toggleProgress;
        float knobY = switchY + 1.25f;
        Render2DHelper.drawRoundedRect(context, knobX, knobY, knobSize, knobSize, knobSize * 0.5f, 0xFFFFFFFF);
        controlsRight -= (switchW + 5.0f);

        // Keybind Pill Badge
        String keyLabel = bindingKey ? "[...]" : (module.getKeyBind() > 0 ? "[" + BindComponent.getKeyName(module.getKeyBind()) + "]" : "[-]");
        float keyScale = 0.66f;
        float keyW = Render2DHelper.getTextWidth(keyLabel, keyScale);
        float pillW = keyW + 7.0f;
        float pillH = 12.0f;
        float pillX = controlsRight - pillW;
        float pillY = y + (baseH - pillH) * 0.5f;

        int pillBg = bindingKey ? ColorUtils.withAlpha(primary, 200) : 0x22FFFFFF;
        Render2DHelper.drawRoundedRect(context, pillX, pillY, pillW, pillH, 2.5f, pillBg);
        Render2DHelper.drawRoundedOutline(context, pillX, pillY, pillW, pillH, 2.5f, 1.0f,
                bindingKey ? secondary : 0x45FFFFFF);

        int keyTextCol = bindingKey ? 0xFFFFFFFF : (module.getKeyBind() > 0 ? primary : theme.getMutedTextColor());
        Render2DHelper.drawScaledText(context, keyLabel, pillX + (pillW - keyW) * 0.5f, pillY + 2.5f, keyTextCol, keyScale, false);
    }

    private void renderCompactHeader(GuiGraphicsExtractor context, Theme theme, float baseH, float toggleProgress, float expandProgress) {
        int primary = theme.getPrimaryColor();
        float textLeft = x + (toggleProgress > 0.05f ? 8.5f : 6.0f);
        int nameColor = ColorUtils.interpolateColor(theme.getTextColor(), primary, toggleProgress * 0.65f);

        String displayTitle = bindingKey ? "[Press key...]" : module.getName();
        Render2DHelper.drawScaledText(context, displayTitle, textLeft, y + 6.5f, nameColor, 0.78f, false);

        float rightEdge = x + width - 5.0f;
        String arrow = module.isExpanded() ? "▴" : "▾";
        int arrowCol = ColorUtils.interpolateColor(theme.getMutedTextColor(), primary, expandProgress);
        Render2DHelper.drawScaledText(context, arrow, rightEdge - 6.0f, y + 6.5f, arrowCol, 0.72f, false);

        if (!bindingKey && module.getKeyBind() > 0) {
            String kb = "[" + BindComponent.getKeyName(module.getKeyBind()) + "]";
            float kbW = Render2DHelper.getTextWidth(kb, 0.62f);
            Render2DHelper.drawScaledText(context, kb, rightEdge - 9.0f - kbW, y + 7.0f,
                    theme.getMutedTextColor(), 0.62f, false);
        }
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!isHovered(mouseX, mouseY)) {
            if (bindingKey) bindingKey = false;
            return false;
        }

        float baseH = getHeaderHeight();

        // Click on card header
        if (mouseY >= y && mouseY <= y + baseH) {
            // MMB (Middle Mouse Button) -> Quick Keybind Mode
            if (button == 2) {
                bindingKey = !bindingKey;
                return true;
            }

            if (!compactMode) {
                float controlsRight = x + width - 6.0f;
                float arrowW = 10.0f;
                float arrowX = controlsRight - arrowW;
                if (mouseX >= arrowX && mouseX <= controlsRight) {
                    module.toggleExpanded();
                    return true;
                }
                controlsRight -= (arrowW + 4.0f);

                float switchW = 20.0f;
                float switchX = controlsRight - switchW;
                if (mouseX >= switchX && mouseX <= controlsRight) {
                    if (button == 0) {
                        module.toggle();
                        return true;
                    } else if (button == 1) {
                        module.toggleExpanded();
                        return true;
                    }
                }
                controlsRight -= (switchW + 5.0f);

                String keyLabel = bindingKey ? "[...]" : (module.getKeyBind() > 0 ? "[" + BindComponent.getKeyName(module.getKeyBind()) + "]" : "[-]");
                float pillW = Render2DHelper.getTextWidth(keyLabel, 0.66f) + 7.0f;
                float pillX = controlsRight - pillW;
                if (mouseX >= pillX && mouseX <= controlsRight) {
                    if (button == 0) {
                        bindingKey = !bindingKey;
                        return true;
                    } else if (button == 1) {
                        module.setKeyBind(0);
                        bindingKey = false;
                        return true;
                    }
                }
            }

            // LMB anywhere on header toggles module, RMB smoothly expands/collapses settings (both enabled & disabled!)
            if (button == 0) {
                module.toggle();
                return true;
            } else if (button == 1) {
                module.toggleExpanded();
                return true;
            }
        }

        // Click inside expanded settings area
        if (module.isExpanded() && mouseY > y + baseH) {
            for (Component c : components) {
                if (!c.isVisible()) continue;
                if (c.mouseClicked(mouseX, mouseY, button)) {
                    return true;
                }
            }
        }

        return false;
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (module.isExpanded()) {
            for (Component c : components) {
                if (!c.isVisible()) continue;
                if (c.mouseReleased(mouseX, mouseY, button)) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (module.isExpanded()) {
            for (Component c : components) {
                if (!c.isVisible()) continue;
                if (c.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (bindingKey) {
            if (keyCode == InputConstants.KEY_ESCAPE || keyCode == InputConstants.KEY_DELETE || keyCode == InputConstants.KEY_BACKSPACE) {
                module.setKeyBind(0);
            } else {
                module.setKeyBind(keyCode);
            }
            bindingKey = false;
            return true;
        }

        if (module.isExpanded()) {
            for (Component c : components) {
                if (!c.isVisible()) continue;
                if (c.keyPressed(keyCode, scanCode, modifiers)) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean charTyped(char chr, int modifiers) {
        if (module.isExpanded()) {
            for (Component c : components) {
                if (!c.isVisible()) continue;
                if (c.charTyped(chr, modifiers)) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean isHovered(double mouseX, double mouseY) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    public void setPosition(float x, float y) {
        this.x = x;
        this.y = y;
    }

    public void setPosition(float x, float y, float width, boolean compactMode) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.compactMode = compactMode;
    }

    public Module getModule() {
        return module;
    }

    public float getX() {
        return x;
    }

    public float getY() {
        return y;
    }

    public float getWidth() {
        return width;
    }

    public float getHeight() {
        return height;
    }

    public List<Component> getComponents() {
        return components;
    }
}
