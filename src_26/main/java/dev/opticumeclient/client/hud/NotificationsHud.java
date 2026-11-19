package dev.opticumeclient.client.hud;

import dev.opticumeclient.client.modules.Category;
import dev.opticumeclient.client.modules.Module;
import dev.opticumeclient.client.render.Render2DHelper;
import dev.opticumeclient.client.setting.NumberSetting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;

import java.util.List;

/**
 * Draggable Toast Notifications HUD widget for module toggle alerts.
 */
public class NotificationsHud extends Module {
    private final NumberSetting posX = new NumberSetting("PosX", "X позиция", 700.0, 0.0, 1920.0, 1.0);
    private final NumberSetting posY = new NumberSetting("PosY", "Y позиция", 380.0, 0.0, 1080.0, 1.0);
    private final HudManager.DraggableWidget dragWidget;

    public NotificationsHud() {
        super("Notifications", "Всплывающие уведомления о переключении модулей", Category.HUD);
        addSetting(posX);
        addSetting(posY);
        setEnabled(true);

        this.dragWidget = HudManager.getInstance().registerWidget(new HudManager.DraggableWidget(
                "Notifications",
                posX.getFloatValue(),
                posY.getFloatValue(),
                156.0f,
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
        if (mc.player == null) return;
        float screenH = mc.getWindow() != null ? (float) mc.getWindow().getGuiScaledHeight() : 540.0f;
        float startY = Math.max(4.0f, Math.min(screenH - 34.0f, posY.getFloatValue()));
        int maxVisible = Math.max(1, Math.min(4, (int) ((screenH - startY - 12.0f) / 30.0f)));
        HudAlertBus.getInstance().setMaxVisible(maxVisible);
    }

    @Override
    public void onRender2D(GuiGraphicsExtractor context, float tickDelta) {
        if (!isEnabled() || mc.player == null) return;

        boolean inChat = mc.gui.screen() instanceof ChatScreen;
        if (DynamicIslandHud.shouldSuppressToasts() && !inChat) {
            return;
        }

        List<HudAlertBus.ToggleAlert> alerts = HudAlertBus.getInstance().getActiveAlerts();
        if (alerts.isEmpty() && !inChat) {
            return;
        }

        float screenW = mc.getWindow() != null ? (float) mc.getWindow().getGuiScaledWidth() : 960.0f;
        float screenH = mc.getWindow() != null ? (float) mc.getWindow().getGuiScaledHeight() : 540.0f;

        float w = 156.0f;
        float cardH = 26.0f;

        dragWidget.syncExternalPosition(posX.getFloatValue(), posY.getFloatValue());
        float startX = Math.max(4.0f, Math.min(screenW - w - 4.0f, dragWidget.getX()));
        float startY = Math.max(4.0f, Math.min(screenH - cardH - 8.0f, dragWidget.getY()));

        int count = Math.max(1, alerts.size());
        dragWidget.setBounds(w, count * (cardH + 4.0f));

        HudPanelRenderer.beginWidget("Notifications");
        try {
            if (alerts.isEmpty() && inChat) {
                HudPanelRenderer.drawSurface(context, "Notifications", startX, startY, w, cardH, 5.5f, 210, 35);
                Render2DHelper.drawCircle(context, startX + 11.0f, startY + cardH * 0.5f, 3.5f, 0xFF22C55E);
                Render2DHelper.drawScaledText(context, "Notifications Preview", startX + 20.0f, startY + 5.5f, 0xFFFFFFFF, 0.74f, true);
                Render2DHelper.drawScaledText(context, "Module toggle alerts", startX + 20.0f, startY + 14.5f, 0xFF9CA3AF, 0.66f, false);
                return;
            }

            float curY = startY;
            for (HudAlertBus.ToggleAlert alert : alerts) {
                float anim = Math.max(0.0f, Math.min(1.0f, alert.slideAnim.getValue()));
                if (anim <= 0.001f) continue;

                float offsetX = (1.0f - anim) * (w + 24.0f);
                float cardX = startX + offsetX;
                float curSlotH = (cardH + 4.0f) * anim;

                int col = alert.enabled ? 0xFF22C55E : 0xFFEF4444;
                int textAlpha = (int) (255 * anim);
                int textColWhite = (textAlpha << 24) | 0x00FFFFFF;
                int textColStatus = (textAlpha << 24) | (col & 0x00FFFFFF);

                HudPanelRenderer.drawSurface(context, "Notifications", cardX, curY, w, cardH, 5.5f, Math.round(220 * anim), Math.round(40 * anim));
                Render2DHelper.drawActiveCardIndicator(context, cardX + 2.0f, curY + 2.0f, cardH - 4.0f, anim, col, col);

                Render2DHelper.drawScaledText(context, alert.moduleName, cardX + 9.0f, curY + 5.0f, textColWhite, 0.76f, true);
                Render2DHelper.drawScaledText(context, alert.enabled ? "Enabled [ON]" : "Disabled [OFF]",
                        cardX + 9.0f, curY + 14.5f, textColStatus, 0.68f, false);

                curY += curSlotH;
            }
        } finally {
            HudPanelRenderer.endWidget();
        }
    }
}
