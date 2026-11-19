package dev.opticumeclient.client.hud;

import dev.opticumeclient.client.autobuy.AutoBuyConfig;
import dev.opticumeclient.client.autobuy.AutoBuyReport;
import dev.opticumeclient.client.modules.Category;
import dev.opticumeclient.client.modules.Module;
import dev.opticumeclient.client.render.Render2DHelper;
import dev.opticumeclient.client.render.TextureAssets;
import dev.opticumeclient.client.render.font.OpticumeFonts;
import dev.opticumeclient.client.setting.NumberSetting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;

/**
 * Liquid Glass HUD widget displaying live AutoBuy / AutoSell totals (spent, earned, net profit).
 */
public class AutoBuyReportHud extends Module {
    private final NumberSetting posX = new NumberSetting("PosX", "X позиция", 8.0, 0.0, 1920.0, 1.0);
    private final NumberSetting posY = new NumberSetting("PosY", "Y позиция", 335.0, 0.0, 1080.0, 1.0);

    private final HudManager.DraggableWidget dragWidget;

    public AutoBuyReportHud() {
        super("AutoBuyReportHUD", "Плашка отчёта AutoBuy / AutoSell (потрачено, заработано, профит)", Category.HUD);
        addSetting(posX);
        addSetting(posY);
        setEnabled(false);

        this.dragWidget = HudManager.getInstance().registerWidget(new HudManager.DraggableWidget(
                "AutoBuyReportHUD",
                posX.getFloatValue(),
                posY.getFloatValue(),
                138.0f,
                58.0f,
                this::isEnabled,
                (newX, newY) -> {
                    posX.setValue((double) Math.round(newX));
                    posY.setValue((double) Math.round(newY));
                }
        ));
    }

    @Override
    public void onRender2D(GuiGraphicsExtractor context, float tickDelta) {
        if (!isEnabled() || mc.player == null) return;
        AutoBuyConfig cfg = AutoBuyConfig.cfg();
        boolean inChat = mc.gui.screen() instanceof ChatScreen;
        if (!cfg.enabled && !cfg.autoSell && cfg.totalSpent == 0L && cfg.totalEarned == 0L && !inChat) {
            return;
        }

        long spent = cfg.totalSpent;
        long earned = cfg.totalEarned;
        long profit = earned - spent;

        String[] labels = {"Потрачено:", "Заработано:", "Профит:"};
        String[] values = {
                AutoBuyReport.formatMoney(spent),
                AutoBuyReport.formatMoney(earned),
                (profit > 0 ? "+" : "") + AutoBuyReport.formatMoney(profit)
        };

        float headerH = 19.0f;
        float rowH = 13.5f;
        float pad = 7.5f;
        float w = 138.0f;
        for (int i = 0; i < labels.length; i++) {
            float needed = Render2DHelper.getTextWidth(labels[i], 0.72f)
                    + 12.0f + Render2DHelper.getTextWidth(values[i], 0.72f) + pad * 2.0f;
            if (needed > w) w = needed;
        }
        float h = headerH + labels.length * rowH + 5.0f;

        dragWidget.syncExternalPosition(posX.getFloatValue(), posY.getFloatValue());
        dragWidget.setBounds(w, h);

        float x = dragWidget.getX();
        float y = dragWidget.getY();

        HudPanelRenderer.beginWidget("AutoBuyReportHUD");
        try {
            HudPanelRenderer.drawHeaderPanel(context, "AutoBuyReportHUD", x, y, w, h, headerH, 6.0f,
                    TextureAssets.DESIRE_GUI_AUTOBUY, "AutoBuy", 1.0f);

            float curY = y + headerH + 3.5f;
            int profitColor = profit > 0L ? 0xFF64FF78 : (profit < 0L ? 0xFFFF6464 : 0xFFEAEFF8);
            for (int i = 0; i < labels.length; i++) {
                int valCol = i == 2 ? profitColor : 0xFFF2F5FF;
                HudPanelRenderer.drawHudText(context, OpticumeFonts.KEY_HUD_MEDIUM, labels[i], x + pad, curY + 1.0f, 7.2f, 0xFFC8CDD7);
                float vw = HudPanelRenderer.measureHud(OpticumeFonts.KEY_HUD_MEDIUM, values[i], 7.2f);
                HudPanelRenderer.drawHudText(context, OpticumeFonts.KEY_HUD_MEDIUM, values[i], x + w - pad - vw, curY + 1.0f, 7.2f, valCol);
                curY += rowH;
            }
        } finally {
            HudPanelRenderer.endWidget();
        }
    }
}
