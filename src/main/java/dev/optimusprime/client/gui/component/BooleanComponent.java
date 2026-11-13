package dev.optimusprime.client.gui.component;

import dev.optimusprime.client.render.Animation;
import dev.optimusprime.client.render.ColorUtils;
import dev.optimusprime.client.render.Easing;
import dev.optimusprime.client.render.Render2DHelper;
import dev.optimusprime.client.render.Theme;
import dev.optimusprime.client.render.ThemeManager;
import dev.optimusprime.client.setting.BooleanSetting;
import net.minecraft.client.gui.GuiGraphicsExtractor; 

/**
 * Capsule toggle switch (20x10px) in Nursultan Nextgen style with smooth knob slide
 * and ThemeManager gradient track fill.
 */
public class BooleanComponent extends Component {
    private final BooleanSetting booleanSetting;
    private final Animation toggleAnimation;
    private final Animation hoverAnimation;

    public BooleanComponent(BooleanSetting setting, float width) {
        super(setting, width, 18.0f);
        this.booleanSetting = setting;
        this.toggleAnimation = new Animation(setting.isEnabled() ? 1.0f : 0.0f, 180L, Easing.EASE_OUT_CUBIC);
        this.hoverAnimation = new Animation(0.0f, 140L, Easing.EASE_OUT_QUAD);
    }

    @Override
    public void render(GuiGraphicsExtractor context, float mouseX, float mouseY, float delta) {
        if (!isVisible()) return;

        Theme theme = ThemeManager.getInstance().getCurrentTheme();
        boolean hovered = isHovered(mouseX, mouseY);
        hoverAnimation.animateTo(hovered ? 1.0f : 0.0f);
        toggleAnimation.animateTo(booleanSetting.isEnabled() ? 1.0f : 0.0f);

        float hoverVal = hoverAnimation.getValue();
        float anim = toggleAnimation.getValue();

        // Subtle row hover highlight
        if (hoverVal > 0.01f) {
            int rowHover = ColorUtils.withAlpha(theme.getTextColor(), (int) (14 * hoverVal));
            Render2DHelper.drawRoundedRect(context, x, y, width, height, 3.5f, rowHover);
        }

        // Setting name label
        int textColor = ColorUtils.interpolateColor(
                theme.getMutedTextColor(),
                theme.getTextColor(),
                Math.max(anim * 0.8f, hoverVal * 0.6f)
        );
        Render2DHelper.drawScaledText(context, booleanSetting.getName(), x + 4.0f, y + 5.0f, textColor, 0.78f, false);

        // Capsule toggle switch: 20x10px
        float switchW = 20.0f;
        float switchH = 10.0f;
        float switchX = x + width - switchW - 4.0f;
        float switchY = y + (height - switchH) / 2.0f;
        float switchRadius = switchH / 2.0f;

        int trackInactive = 0x35FFFFFF;
        int primary = theme.getPrimaryColor();
        int secondary = theme.getSecondaryColor();

        // Base inactive track
        Render2DHelper.drawRoundedRect(context, switchX, switchY, switchW, switchH, switchRadius, trackInactive);

        // Gradient active overlay
        if (anim > 0.02f) {
            int cLeft = ColorUtils.withAlpha(primary, (int) (245 * anim));
            int cRight = ColorUtils.withAlpha(secondary, (int) (245 * anim));
            Render2DHelper.drawGradientRoundedRect(context, switchX, switchY, switchW, switchH, switchRadius, cLeft, cRight);
            Render2DHelper.drawGlow(context, switchX, switchY, switchW, switchH, 3.5f * anim,
                    ColorUtils.withAlpha(primary, (int) (95 * anim)));
        }

        // Subtle border around switch
        int outlineCol = ColorUtils.interpolateColor(0x38FFFFFF, ColorUtils.withAlpha(primary, 180), anim);
        Render2DHelper.drawRoundedOutline(context, switchX, switchY, switchW, switchH, switchRadius, 1.0f, outlineCol);

        // White sliding knob
        float knobSize = switchH - 2.5f;
        float knobMinX = switchX + 1.25f;
        float knobMaxX = switchX + switchW - knobSize - 1.25f;
        float knobX = knobMinX + (knobMaxX - knobMinX) * anim;
        float knobY = switchY + 1.25f;

        Render2DHelper.drawRoundedRect(context, knobX, knobY, knobSize, knobSize, knobSize * 0.5f, 0xFFFFFFFF);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!isVisible()) return false;
        if (button == 0 && isHovered(mouseX, mouseY)) {
            booleanSetting.toggle();
            return true;
        }
        return false;
    }

    public BooleanSetting getBooleanSetting() {
        return booleanSetting;
    }
}
