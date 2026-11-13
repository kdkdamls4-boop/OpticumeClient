package dev.optimusprime.client.gui.component;

import dev.optimusprime.client.render.ColorUtils;
import dev.optimusprime.client.render.Render2DHelper;
import dev.optimusprime.client.render.Theme;
import dev.optimusprime.client.render.ThemeManager;
import dev.optimusprime.client.setting.MultiBoxSetting;
import net.minecraft.client.gui.GuiGraphicsExtractor; 

import java.util.HashMap;
import java.util.Map;

/**
 * Grid of clickable pill chips (RoundedRect) with gradient highlight for selected options.
 */
public class MultiBoxComponent extends Component {
    private final MultiBoxSetting multiBoxSetting;
    private final Map<String, ChipRect> chipRects = new HashMap<>();

    public static class ChipRect {
        float x, y, w, h;

        ChipRect(float x, float y, float w, float h) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }

        boolean contains(double mx, double my) {
            return mx >= x && mx <= x + w && my >= y && my <= y + h;
        }
    }

    public MultiBoxComponent(MultiBoxSetting setting, float width) {
        super(setting, width, 26.0f);
        this.multiBoxSetting = setting;
        calculateLayout();
    }

    private void calculateLayout() {
        chipRects.clear();
        float startX = x + 4.0f;
        float curX = startX;
        float curY = y + 14.0f;
        float chipH = 13.0f;
        float gap = 3.5f;
        float maxW = Math.max(40.0f, width - 8.0f);

        for (String opt : multiBoxSetting.getOptions()) {
            float textW = Render2DHelper.getTextWidth(opt, 0.70f);
            float chipW = Math.min(maxW, textW + 10.0f);

            if (curX + chipW > startX + maxW && curX > startX) {
                curX = startX;
                curY += chipH + gap;
            }

            chipRects.put(opt, new ChipRect(curX, curY, chipW, chipH));
            curX += chipW + gap;
        }

        this.height = (curY + chipH + 4.0f) - y;
    }

    @Override
    public void setPosition(float x, float y, float width) {
        super.setPosition(x, y, width);
        calculateLayout();
    }

    @Override
    public float getHeight() {
        calculateLayout();
        return height;
    }

    @Override
    public void render(GuiGraphicsExtractor context, float mouseX, float mouseY, float delta) {
        if (!isVisible()) return;

        calculateLayout();
        Theme theme = ThemeManager.getInstance().getCurrentTheme();

        // Setting title label
        Render2DHelper.drawScaledText(context, multiBoxSetting.getName(), x + 4.0f, y + 2.5f,
                theme.getMutedTextColor(), 0.76f, false);

        int primary = theme.getPrimaryColor();
        int secondary = theme.getSecondaryColor();

        for (String opt : multiBoxSetting.getOptions()) {
            ChipRect rect = chipRects.get(opt);
            if (rect == null) continue;

            boolean selected = multiBoxSetting.isEnabled(opt);
            boolean hovered = rect.contains(mouseX, mouseY);

            if (selected) {
                int cLeft = ColorUtils.withAlpha(primary, hovered ? 235 : 195);
                int cRight = ColorUtils.withAlpha(secondary, hovered ? 235 : 195);

                Render2DHelper.drawGlow(context, rect.x, rect.y, rect.w, rect.h, 3.0f,
                        ColorUtils.withAlpha(primary, hovered ? 110 : 75));
                Render2DHelper.drawGradientRoundedRect(context, rect.x, rect.y, rect.w, rect.h, 3.5f, cLeft, cRight);
                Render2DHelper.drawRoundedOutline(context, rect.x, rect.y, rect.w, rect.h, 3.5f, 1.0f,
                        ColorUtils.withAlpha(0xFFFFFFFF, 140));

                float textW = Render2DHelper.getTextWidth(opt, 0.70f);
                Render2DHelper.drawScaledText(context, opt,
                        rect.x + (rect.w - textW) * 0.5f, rect.y + 3.0f, 0xFFFFFFFF, 0.70f, false);
            } else {
                int chipBg = hovered ? 0x32FFFFFF : 0x1AFFFFFF;
                int chipOutline = hovered ? 0x66FFFFFF : 0x30FFFFFF;
                int textColor = hovered ? theme.getTextColor() : theme.getMutedTextColor();

                Render2DHelper.drawRoundedRect(context, rect.x, rect.y, rect.w, rect.h, 3.5f, chipBg);
                Render2DHelper.drawRoundedOutline(context, rect.x, rect.y, rect.w, rect.h, 3.5f, 1.0f, chipOutline);

                float textW = Render2DHelper.getTextWidth(opt, 0.70f);
                Render2DHelper.drawScaledText(context, opt,
                        rect.x + (rect.w - textW) * 0.5f, rect.y + 3.0f, textColor, 0.70f, false);
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!isVisible()) return false;
        if (button == 0 || button == 1) {
            calculateLayout();
            for (String opt : multiBoxSetting.getOptions()) {
                ChipRect rect = chipRects.get(opt);
                if (rect != null && rect.contains(mouseX, mouseY)) {
                    multiBoxSetting.toggle(opt);
                    return true;
                }
            }
        }
        return false;
    }

    public MultiBoxSetting getMultiBoxSetting() {
        return multiBoxSetting;
    }
}
