package dev.opticumeclient.client.gui.component;

import dev.opticumeclient.client.setting.Setting;
import net.minecraft.client.gui.GuiGraphicsExtractor; 

/**
 * Base class for interactive setting components inside module cards and dropdown panels in ClickGUI.
 */
public abstract class Component {
    protected final Setting<?> setting;
    protected float x;
    protected float y;
    protected float width;
    protected float height;

    public Component(Setting<?> setting, float width, float height) {
        this.setting = setting;
        this.width = width;
        this.height = height;
    }

    public boolean isVisible() {
        return setting == null || setting.isVisible();
    }

    public abstract void render(GuiGraphicsExtractor context, float mouseX, float mouseY, float delta);

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return false;
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return false;
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        return false;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return false;
    }

    public boolean charTyped(char chr, int modifiers) {
        return false;
    }

    public boolean isHovered(double mouseX, double mouseY) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    public void setPosition(float x, float y, float width) {
        this.x = x;
        this.y = y;
        this.width = width;
    }

    public Setting<?> getSetting() {
        return setting;
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
}
