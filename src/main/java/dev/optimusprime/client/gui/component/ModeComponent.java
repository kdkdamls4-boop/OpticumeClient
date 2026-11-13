package dev.optimusprime.client.gui.component;

import dev.optimusprime.client.render.Animation;
import dev.optimusprime.client.render.ColorUtils;
import dev.optimusprime.client.render.Easing;
import dev.optimusprime.client.render.Render2DHelper;
import dev.optimusprime.client.render.Theme;
import dev.optimusprime.client.render.ThemeManager;
import dev.optimusprime.client.setting.ModeSetting;
import net.minecraft.client.gui.GuiGraphicsExtractor; 

import java.util.List;

/**
 * Mode selector component: LMB opens a smooth dropdown list of all modes with an active dot indicator,
 * RMB cycles modes directly.
 */
public class ModeComponent extends Component {
    private static final float BASE_HEIGHT = 20.0f;
    private static final float ITEM_HEIGHT = 15.0f;

    private final ModeSetting modeSetting;
    private boolean dropdownOpen;
    private final Animation openAnimation;
    private final Animation hoverAnimation;

    public ModeComponent(ModeSetting setting, float width) {
        super(setting, width, BASE_HEIGHT);
        this.modeSetting = setting;
        this.openAnimation = new Animation(0.0f, 170L, Easing.EASE_OUT_CUBIC);
        this.hoverAnimation = new Animation(0.0f, 140L, Easing.EASE_OUT_QUAD);
    }

    @Override
    public float getHeight() {
        List<String> modes = modeSetting.getModes();
        float listH = modes.size() * ITEM_HEIGHT + 4.0f;
        return BASE_HEIGHT + listH * openAnimation.getValue();
    }

    @Override
    public void render(GuiGraphicsExtractor context, float mouseX, float mouseY, float delta) {
        if (!isVisible()) return;

        openAnimation.animateTo(dropdownOpen ? 1.0f : 0.0f);
        float openVal = openAnimation.getValue();
        this.height = getHeight();

        Theme theme = ThemeManager.getInstance().getCurrentTheme();
        boolean headerHovered = mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + BASE_HEIGHT;
        hoverAnimation.animateTo(headerHovered ? 1.0f : 0.0f);
        float hoverVal = hoverAnimation.getValue();

        // Label on left
        int labelColor = ColorUtils.interpolateColor(theme.getMutedTextColor(), theme.getTextColor(), hoverVal * 0.6f);
        Render2DHelper.drawScaledText(context, modeSetting.getName(), x + 4.0f, y + 5.5f, labelColor, 0.76f, false);

        // Mode badge on right
        String currentMode = modeSetting.getMode() != null ? modeSetting.getMode() : "";
        String arrow = dropdownOpen ? " ▴" : " ▾";
        String badgeText = currentMode + arrow;
        float fontScale = 0.72f;
        float textW = Render2DHelper.getTextWidth(badgeText, fontScale);
        float badgeW = Math.min(width * 0.56f, textW + 10.0f);
        float badgeH = 14.0f;
        float badgeX = x + width - badgeW - 4.0f;
        float badgeY = y + (BASE_HEIGHT - badgeH) / 2.0f;

        int badgeBg = ColorUtils.interpolateColor(0x28FFFFFF, ColorUtils.withAlpha(theme.getPrimaryColor(), 65),
                Math.max(hoverVal, openVal));
        int badgeOutline = ColorUtils.interpolateColor(0x45FFFFFF, theme.getPrimaryColor(),
                Math.max(hoverVal * 0.7f, openVal));

        Render2DHelper.drawRoundedRect(context, badgeX, badgeY, badgeW, badgeH, 3.0f, badgeBg);
        Render2DHelper.drawRoundedOutline(context, badgeX, badgeY, badgeW, badgeH, 3.0f, 1.0f, badgeOutline);

        int modeTextColor = ColorUtils.interpolateColor(theme.getTextColor(), theme.getPrimaryColor(), openVal * 0.5f);
        Render2DHelper.drawScaledText(context, badgeText, badgeX + 5.0f, badgeY + 3.5f, modeTextColor, fontScale, false);

        // Dropdown list of modes
        if (openVal > 0.02f) {
            List<String> modes = modeSetting.getModes();
            float boxX = x + 4.0f;
            float boxY = y + BASE_HEIGHT;
            float boxW = width - 8.0f;
            float boxH = (modes.size() * ITEM_HEIGHT + 2.0f) * openVal;

            int listBg = ColorUtils.withAlpha(0xFF0E1018, (int) (215 * openVal));
            Render2DHelper.drawRoundedRect(context, boxX, boxY, boxW, boxH, 3.5f, listBg);
            Render2DHelper.drawRoundedOutline(context, boxX, boxY, boxW, boxH, 3.5f, 1.0f,
                    ColorUtils.withAlpha(theme.getPrimaryColor(), (int) (105 * openVal)));

            float itemY = boxY + 1.0f;
            for (String m : modes) {
                if (itemY + ITEM_HEIGHT > boxY + boxH + 2.0f) break;
                boolean isSelected = modeSetting.is(m);
                boolean itemHovered = mouseX >= boxX && mouseX <= boxX + boxW && mouseY >= itemY && mouseY <= itemY + ITEM_HEIGHT;

                if (itemHovered || isSelected) {
                    int rowBg = isSelected
                            ? ColorUtils.withAlpha(theme.getPrimaryColor(), (int) (55 * openVal))
                            : ColorUtils.withAlpha(0xFFFFFFFF, (int) (20 * openVal));
                    Render2DHelper.drawRoundedRect(context, boxX + 2.0f, itemY + 1.0f, boxW - 4.0f, ITEM_HEIGHT - 2.0f, 2.5f, rowBg);
                }

                // Active dot indicator
                float dotCX = boxX + 8.0f;
                float dotCY = itemY + ITEM_HEIGHT * 0.5f;
                if (isSelected) {
                    int dotCol = ColorUtils.withAlpha(theme.getPrimaryColor(), (int) (255 * openVal));
                    Render2DHelper.drawGlow(context, dotCX - 2.2f, dotCY - 2.2f, 4.4f, 4.4f, 3.0f,
                            ColorUtils.withAlpha(theme.getPrimaryColor(), (int) (140 * openVal)));
                    Render2DHelper.drawCircle(context, dotCX, dotCY, 2.2f, dotCol);
                } else {
                    Render2DHelper.drawCircle(context, dotCX, dotCY, 1.5f,
                            ColorUtils.withAlpha(theme.getMutedTextColor(), (int) (120 * openVal)));
                }

                int itemTextCol = isSelected ? theme.getTextColor()
                        : (itemHovered ? theme.getTextColor() : theme.getMutedTextColor());
                itemTextCol = ColorUtils.withAlpha(itemTextCol, (int) (255 * openVal));
                Render2DHelper.drawScaledText(context, m, boxX + 15.0f, itemY + 4.0f, itemTextCol, 0.72f, false);

                itemY += ITEM_HEIGHT;
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!isVisible()) return false;

        // Header click
        if (mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + BASE_HEIGHT) {
            if (button == 0) {
                dropdownOpen = !dropdownOpen;
                return true;
            } else if (button == 1) {
                modeSetting.cycle(true);
                return true;
            }
        }

        // Dropdown options click
        if (dropdownOpen && openAnimation.getValue() > 0.5f) {
            List<String> modes = modeSetting.getModes();
            float boxX = x + 4.0f;
            float boxY = y + BASE_HEIGHT + 1.0f;
            float boxW = width - 8.0f;

            for (int i = 0; i < modes.size(); i++) {
                float iy = boxY + i * ITEM_HEIGHT;
                if (mouseX >= boxX && mouseX <= boxX + boxW && mouseY >= iy && mouseY <= iy + ITEM_HEIGHT) {
                    if (button == 0 || button == 1) {
                        modeSetting.setMode(modes.get(i));
                        if (button == 0) {
                            dropdownOpen = false;
                        }
                        return true;
                    }
                }
            }
        }

        return false;
    }

    public ModeSetting getModeSetting() {
        return modeSetting;
    }
}
