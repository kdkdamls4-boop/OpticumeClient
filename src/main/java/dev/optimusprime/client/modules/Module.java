package dev.optimusprime.client.modules;

import dev.optimusprime.client.render.Animation;
import dev.optimusprime.client.render.Easing;
import dev.optimusprime.client.setting.Setting;
import net.minecraft.client.Minecraft; 
import net.minecraft.client.gui.GuiGraphicsExtractor; 
import com.mojang.blaze3d.vertex.PoseStack; 

import java.util.ArrayList;
import java.util.List;

public abstract class Module {
    protected final Minecraft mc = Minecraft.getInstance();
    private final String name;
    private final String description;
    private final Category category;
    private int keyBind;
    private boolean enabled;
    private boolean expanded;
    private final Animation toggleAnimation = new Animation(0.0f, 220L, Easing.EASE_OUT_CUBIC);
    private final Animation expandAnimation = new Animation(0.0f, 220L, Easing.EASE_OUT_CUBIC);
    private final List<Setting<?>> settings = new ArrayList<>();

    public Module(String name, String description, Category category) {
        this(name, description, category, 0);
    }

    public Module(String name, String description, Category category, int defaultKey) {
        this.name = name;
        this.description = description;
        this.category = category;
        this.keyBind = defaultKey;
        this.enabled = false;
        this.expanded = false;
    }

    public void addSetting(Setting<?> setting) {
        this.settings.add(setting);
    }

    public List<Setting<?>> getSettings() {
        return settings;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Category getCategory() {
        return category;
    }

    public int getKeyBind() {
        return keyBind;
    }

    public void setKeyBind(int keyBind) {
        this.keyBind = keyBind;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        if (this.enabled != enabled) {
            this.enabled = enabled;
            this.toggleAnimation.animateTo(enabled ? 1.0f : 0.0f, 220L, Easing.EASE_OUT_CUBIC);
            if (enabled) {
                onEnable();
            } else {
                onDisable();
            }
        }
    }

    public boolean isExpanded() {
        return expanded;
    }

    public void setExpanded(boolean expanded) {
        if (this.expanded != expanded) {
            this.expanded = expanded;
            this.expandAnimation.animateTo(expanded ? 1.0f : 0.0f, 220L, Easing.EASE_OUT_CUBIC);
        }
    }

    public void toggleExpanded() {
        setExpanded(!expanded);
    }

    public Animation getToggleAnimation() {
        return toggleAnimation;
    }

    public Animation getExpandAnimation() {
        return expandAnimation;
    }

    public String getDisplaySuffix() {
        return null;
    }

    public void toggle() {
        setEnabled(!enabled);
    }

    public void onEnable() {}
    public void onDisable() {}
    public void onTick() {}
    public void onRender3D(PoseStack matrices, float tickDelta) {}
    public void onRender2D(GuiGraphicsExtractor context, float tickDelta) {}
}
