package dev.opticumeclient.client.hud;

import dev.opticumeclient.client.modules.Category;
import dev.opticumeclient.client.modules.Module;
import dev.opticumeclient.client.render.ColorUtils;
import dev.opticumeclient.client.render.Render2DHelper;
import dev.opticumeclient.client.render.Theme;
import dev.opticumeclient.client.render.ThemeManager;
import dev.opticumeclient.client.setting.NumberSetting;
import net.minecraft.client.gui.GuiGraphicsExtractor; 
import net.minecraft.client.gui.screens.ChatScreen; 
import net.minecraft.world.effect.MobEffectInstance; 
import net.minecraft.world.effect.MobEffectUtil; 

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Active Status Effects / Potions HUD widget with Roman numerals (I, II, III),
 * authentic potion colors, duration timers, and Smart Snap dragging.
 */
public class PotionHud extends Module {
    private final NumberSetting posX = new NumberSetting("PosX", "X позиция", 8.0, 0.0, 1920.0, 1.0);
    private final NumberSetting posY = new NumberSetting("PosY", "Y позиция", 412.0, 0.0, 1080.0, 1.0);

    private final HudManager.DraggableWidget dragWidget;

    private record PotionLine(String label, String durationText, int color, float progress) {}

    private final List<PotionLine> cachedPotionLines = new ArrayList<>();
    private final List<PotionLine> previewLines = List.of(
            new PotionLine("Speed II", "1:24", 0xFF33EBFF, 0.72f),
            new PotionLine("Strength II", "0:48", 0xFFFF5252, 0.45f)
    );

    public PotionHud() {
        super("PotionHUD", "Отображение активных эффектов зелий с римскими цифрами и таймерами", Category.HUD);
        addSetting(posX);
        addSetting(posY);
        setEnabled(true);

        this.dragWidget = HudManager.getInstance().registerWidget(new HudManager.DraggableWidget(
                "PotionHUD",
                posX.getFloatValue(),
                posY.getFloatValue(),
                138.0f,
                52.0f,
                this::isEnabled,
                (newX, newY) -> {
                    posX.setValue((double) Math.round(newX));
                    posY.setValue((double) Math.round(newY));
                }
        ));
    }

    @Override
    public void onTick() {
        if (!isEnabled() || mc.player == null) {
            cachedPotionLines.clear();
            return;
        }

        Collection<MobEffectInstance> activeEffects = mc.player.getActiveEffects();
        cachedPotionLines.clear();
        if (activeEffects.isEmpty()) {
            return;
        }

        float tickRate = (mc.level != null && mc.level.tickRateManager() != null)
                ? mc.level.tickRateManager().tickrate()
                : 20.0f;

        for (MobEffectInstance effect : activeEffects) {
            String baseName = effect.getEffect().value().getDisplayName().getString();
            String roman = toRoman(effect.getAmplifier() + 1);
            String fullName = baseName + " " + roman;
            String dur = MobEffectUtil.formatDuration(effect, 1.0f, tickRate).getString();
            int rawColor = effect.getEffect().value().getColor() | 0xFF000000;
            float prog = effect.isInfiniteDuration() ? 1.0f : Math.max(0.05f, Math.min(1.0f, effect.getDuration() / 2400.0f));
            cachedPotionLines.add(new PotionLine(fullName, dur, rawColor, prog));
        }
    }

    @Override
    public void onRender2D(GuiGraphicsExtractor context, float tickDelta) {
        if (!isEnabled() || mc.player == null) return;

        boolean inChat = mc.gui.screen() instanceof ChatScreen;
        boolean hasRealEffects = !cachedPotionLines.isEmpty();
        if (!hasRealEffects && !inChat) {
            return;
        }

        List<PotionLine> lines = hasRealEffects ? cachedPotionLines : previewLines;

        float w = 140.0f;
        float headerH = 19.0f;
        float rowH = 16.0f;
        for (PotionLine line : lines) {
            float needed = Render2DHelper.getTextWidth(line.label() + "  " + line.durationText(), 0.74f) + 26.0f;
            if (needed > w) w = needed;
        }
        float h = headerH + lines.size() * rowH + 5.0f;

        dragWidget.syncExternalPosition(posX.getFloatValue(), posY.getFloatValue());
        dragWidget.setBounds(w, h);

        float x = dragWidget.getX();
        float y = dragWidget.getY();

        Theme theme = ThemeManager.getInstance().getCurrentTheme();
        int c1 = theme.getPrimaryColor();
        int c2 = theme.getSecondaryColor();

        HudPanelRenderer.beginWidget("PotionHUD");
        try {
        HudPanelRenderer.drawHeaderPanel(context, "PotionHUD", x, y, w, h, headerH, 6.0f,
                dev.opticumeclient.client.render.TextureAssets.DESIRE_HUD_STAR, "Effects", 1.0f);

        float curY = y + headerH + 3.0f;
        for (PotionLine line : lines) {
            // Glowing potion color dot
            Render2DHelper.drawCircle(context, x + 10.0f, curY + 5.0f, 3.0f, ColorUtils.withAlpha(line.color(), 80));
            Render2DHelper.drawCircle(context, x + 10.0f, curY + 5.0f, 1.8f, line.color());

            // Effect Name + Roman Numeral
            Render2DHelper.drawScaledText(context, line.label(), x + 17.0f, curY + 1.5f, line.color(), 0.74f, true);

            // Duration pill on right
            float durW = Render2DHelper.getTextWidth(line.durationText(), 0.70f);
            Render2DHelper.drawScaledText(context, line.durationText(), x + w - durW - 7.0f, curY + 2.0f, 0xFFE5E7EB, 0.70f, false);

            // Subtle progress bar under each potion row
            float barX = x + 17.0f;
            float barY = curY + 11.0f;
            float barW = w - 24.0f;
            Render2DHelper.drawRoundedRectFloat(context, barX, barY, barW, 2.2f, 1.1f, 0x28FFFFFF);
            Render2DHelper.drawRoundedRectFloat(context, barX, barY, Math.max(2.5f, barW * line.progress()), 2.2f, 1.1f, line.color());

            curY += rowH;
        }
        } finally {
            HudPanelRenderer.endWidget();
        }
    }

    private String toRoman(int level) {
        return switch (level) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            case 6 -> "VI";
            case 7 -> "VII";
            case 8 -> "VIII";
            case 9 -> "IX";
            case 10 -> "X";
            default -> String.valueOf(level);
        };
    }
}
