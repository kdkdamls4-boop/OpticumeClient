package dev.opticumeclient.client.hud;

import dev.opticumeclient.client.modules.Category;
import dev.opticumeclient.client.modules.Module;
import dev.opticumeclient.client.render.Animation;
import dev.opticumeclient.client.render.ColorUtils;
import dev.opticumeclient.client.render.Easing;
import dev.opticumeclient.client.render.Render2DHelper;
import dev.opticumeclient.client.render.Theme;
import dev.opticumeclient.client.render.ThemeManager;
import dev.opticumeclient.client.setting.BooleanSetting;
import dev.opticumeclient.client.setting.NumberSetting;
import net.minecraft.client.gui.GuiGraphicsExtractor; 

import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Liquid Glass Keystrokes HUD widget (WASD, LMB, RMB, SPACE)
 * with smooth gradient glow on press, CPS counters, and Smart Snap dragging.
 */
public class KeystrokesHud extends Module {
    private final NumberSetting posX = new NumberSetting("PosX", "X позиция", 8.0, 0.0, 1920.0, 1.0);
    private final NumberSetting posY = new NumberSetting("PosY", "Y позиция", 126.0, 0.0, 1080.0, 1.0);
    private final BooleanSetting showSpace = new BooleanSetting("SpaceBar", "Отображать клавишу прыжка SPACE", true);
    private final BooleanSetting showCps = new BooleanSetting("ShowCPS", "Отображать счетчик кликов в секунду (CPS)", true);

    private static final ConcurrentLinkedDeque<Long> LEFT_CLICKS = new ConcurrentLinkedDeque<>();
    private static final ConcurrentLinkedDeque<Long> RIGHT_CLICKS = new ConcurrentLinkedDeque<>();

    private final Animation wAnim = new Animation(0.0f, 120L, Easing.EASE_OUT_QUAD);
    private final Animation aAnim = new Animation(0.0f, 120L, Easing.EASE_OUT_QUAD);
    private final Animation sAnim = new Animation(0.0f, 120L, Easing.EASE_OUT_QUAD);
    private final Animation dAnim = new Animation(0.0f, 120L, Easing.EASE_OUT_QUAD);
    private final Animation lmbAnim = new Animation(0.0f, 110L, Easing.EASE_OUT_QUAD);
    private final Animation rmbAnim = new Animation(0.0f, 110L, Easing.EASE_OUT_QUAD);
    private final Animation spaceAnim = new Animation(0.0f, 120L, Easing.EASE_OUT_QUAD);

    private final HudManager.DraggableWidget dragWidget;

    public static void recordLeftClick() {
        LEFT_CLICKS.add(System.currentTimeMillis());
    }

    public static void recordRightClick() {
        RIGHT_CLICKS.add(System.currentTimeMillis());
    }

    public static int getLeftCps() {
        long now = System.currentTimeMillis();
        LEFT_CLICKS.removeIf(t -> now - t > 1000L);
        return LEFT_CLICKS.size();
    }

    public static int getRightCps() {
        long now = System.currentTimeMillis();
        RIGHT_CLICKS.removeIf(t -> now - t > 1000L);
        return RIGHT_CLICKS.size();
    }

    public KeystrokesHud() {
        super("Keystrokes", "Стеклянные клавиши WASD / LMB / RMB / SPACE со свечением и CPS", Category.HUD);
        addSetting(showSpace);
        addSetting(showCps);
        addSetting(posX);
        addSetting(posY);
        setEnabled(true);

        this.dragWidget = HudManager.getInstance().registerWidget(new HudManager.DraggableWidget(
                "Keystrokes",
                posX.getFloatValue(),
                posY.getFloatValue(),
                76.0f,
                92.0f,
                this::isEnabled,
                (newX, newY) -> {
                    posX.setValue((double) Math.round(newX));
                    posY.setValue((double) Math.round(newY));
                }
        ));
    }

    @Override
    public void onTick() {
        // Clicks are recorded with sub-tick precision in MouseHandlerMixin
        long now = System.currentTimeMillis();
        LEFT_CLICKS.removeIf(t -> now - t > 1000L);
        RIGHT_CLICKS.removeIf(t -> now - t > 1000L);
    }

    @Override
    public void onRender2D(GuiGraphicsExtractor context, float tickDelta) {
        if (!isEnabled() || mc.player == null || mc.options == null) return;

        float keySize = 23.0f;
        float gap = 3.0f;
        float totalW = keySize * 3.0f + gap * 2.0f;
        float mouseH = 20.0f;
        float spaceH = 14.0f;
        float totalH = (keySize + gap) * 2.0f + mouseH + (showSpace.isEnabled() ? (gap + spaceH) : 0.0f);

        dragWidget.syncExternalPosition(posX.getFloatValue(), posY.getFloatValue());
        dragWidget.setBounds(totalW, totalH);

        float x = dragWidget.getX();
        float y = dragWidget.getY();

        // Row 1: W
        drawGlassKey(context, "W", null, x + keySize + gap, y, keySize, keySize,
                mc.options.keyUp.isDown(), wAnim, false);

        // Row 2: A, S, D
        float row2Y = y + keySize + gap;
        drawGlassKey(context, "A", null, x, row2Y, keySize, keySize,
                mc.options.keyLeft.isDown(), aAnim, false);
        drawGlassKey(context, "S", null, x + keySize + gap, row2Y, keySize, keySize,
                mc.options.keyDown.isDown(), sAnim, false);
        drawGlassKey(context, "D", null, x + (keySize + gap) * 2.0f, row2Y, keySize, keySize,
                mc.options.keyRight.isDown(), dAnim, false);

        // Row 3: LMB & RMB with CPS
        float row3Y = row2Y + keySize + gap;
        float mouseW = (totalW - gap) * 0.5f;
        String lmbSub = showCps.isEnabled() ? (getLeftCps() + " CPS") : null;
        String rmbSub = showCps.isEnabled() ? (getRightCps() + " CPS") : null;

        drawGlassKey(context, "LMB", lmbSub, x, row3Y, mouseW, mouseH,
                mc.options.keyAttack.isDown(), lmbAnim, false);
        drawGlassKey(context, "RMB", rmbSub, x + mouseW + gap, row3Y, mouseW, mouseH,
                mc.options.keyUse.isDown(), rmbAnim, false);

        // Row 4: SPACE bar
        if (showSpace.isEnabled()) {
            float row4Y = row3Y + mouseH + gap;
            drawGlassKey(context, "SPACE", null, x, row4Y, totalW, spaceH,
                    mc.options.keyJump.isDown(), spaceAnim, true);
        }
    }

    private void drawGlassKey(GuiGraphicsExtractor context, String label, String subLabel,
                              float kx, float ky, float kw, float kh,
                              boolean pressed, Animation anim, boolean isSpaceBar) {
        anim.animateTo(pressed ? 1.0f : 0.0f, 110L, Easing.EASE_OUT_QUAD);
        float progress = anim.getValue();

        Theme theme = ThemeManager.getInstance().getCurrentTheme();
        int c1 = theme.getPrimaryColor();
        int c2 = theme.getSecondaryColor();

        HudPanelRenderer.beginWidget("Keystrokes");
        try {
        // Base Liquid Glass Key
        Render2DHelper.drawLiquidGlass(context, kx, ky, kw, kh, 4.5f, 215, (int) (28 + 52 * progress));

        // Gradient glow overlay when pressed
        if (progress > 0.02f) {
            int grad1 = ColorUtils.withAlpha(c1, (int) (185 * progress));
            int grad2 = ColorUtils.withAlpha(c2, (int) (185 * progress));
            Render2DHelper.drawGlow(context, kx, ky, kw, kh, 6.0f, ColorUtils.withAlpha(c1, (int) (95 * progress)));
            Render2DHelper.drawGradientRoundedRectFloat(context, kx + 0.5f, ky + 0.5f, kw - 1.0f, kh - 1.0f, 4.2f, grad1, grad1, grad2, grad2);
        }

        if (isSpaceBar) {
            float barW = kw * (0.42f + 0.16f * progress);
            float barH = 2.2f;
            float barX = kx + (kw - barW) * 0.5f;
            float barY = ky + (kh - barH) * 0.5f;
            int barCol = ColorUtils.interpolateColor(0xFFD1D5DB, 0xFFFFFFFF, progress);
            Render2DHelper.drawRoundedRectFloat(context, barX, barY, barW, barH, 1.1f, barCol);
            return;
        }
        } finally {
            HudPanelRenderer.endWidget();
        }

        if (subLabel != null) {
            float mainScale = 0.72f;
            float subScale = 0.58f;
            float mw = Render2DHelper.getTextWidth(label, mainScale);
            float sw = Render2DHelper.getTextWidth(subLabel, subScale);
            Render2DHelper.drawScaledText(context, label, kx + (kw - mw) * 0.5f, ky + 3.0f, 0xFFFFFFFF, mainScale, true);
            int subCol = ColorUtils.interpolateColor(theme.getMutedTextColor(), 0xFFFFFFFF, progress);
            Render2DHelper.drawScaledText(context, subLabel, kx + (kw - sw) * 0.5f, ky + 11.5f, subCol, subScale, false);
        } else {
            float scale = 0.82f;
            float tw = Render2DHelper.getTextWidth(label, scale);
            Render2DHelper.drawScaledText(context, label, kx + (kw - tw) * 0.5f, ky + (kh - 7.0f) * 0.5f, 0xFFFFFFFF, scale, true);
        }
    }
}
