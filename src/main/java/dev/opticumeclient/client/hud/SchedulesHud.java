package dev.opticumeclient.client.hud;

import dev.opticumeclient.client.modules.Category;
import dev.opticumeclient.client.modules.Module;
import dev.opticumeclient.client.render.ColorUtils;
import dev.opticumeclient.client.render.Render2DHelper;
import dev.opticumeclient.client.render.TextureAssets;
import dev.opticumeclient.client.render.Theme;
import dev.opticumeclient.client.render.ThemeManager;
import dev.opticumeclient.client.services.ServerEventsService;
import dev.opticumeclient.client.setting.ModeSetting;
import dev.opticumeclient.client.setting.NumberSetting;
import dev.opticumeclient.client.util.ServerDetection;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.List;

/**
 * Draggable HUD widget showing upcoming and live anarchy server events (FunTime / HolyWorld)
 * pulled asynchronously via ServerEventsService.
 */
public class SchedulesHud extends Module {
    private static SchedulesHud instance;

    private final ModeSetting source = new ModeSetting("Source", "Источник расписания ивентов", "Auto", "Auto", "FunTime", "HolyWorld");
    private final NumberSetting maxEvents = new NumberSetting("Max Events", "Максимум отображаемых ивентов", 4.0, 1.0, 8.0, 1.0);
    private final NumberSetting posX = new NumberSetting("PosX", "X позиция", 12.0, 0.0, 1920.0, 1.0);
    private final NumberSetting posY = new NumberSetting("PosY", "Y позиция", 230.0, 0.0, 1080.0, 1.0);
    private final ServerEventsService eventsService = ServerEventsService.createDetached();
    private final HudManager.DraggableWidget dragWidget;

    public SchedulesHud() {
        super("Schedules", "Расписание живых ивентов FunTime и HolyWorld с таймерами", Category.HUD);
        instance = this;
        addSetting(source);
        addSetting(maxEvents);
        addSetting(posX);
        addSetting(posY);
        setEnabled(false);

        this.dragWidget = HudManager.getInstance().registerWidget(new HudManager.DraggableWidget(
                "Schedules",
                posX.getFloatValue(),
                posY.getFloatValue(),
                160.0f,
                64.0f,
                this::isEnabled,
                (nx, ny) -> {
                    posX.setValue((double) Math.round(nx));
                    posY.setValue((double) Math.round(ny));
                }
        ));
    }

    public static SchedulesHud getInstance() {
        return instance;
    }

    @Override
    public void onTick() {
        ServerEventsService.Source target = switch (source.getMode()) {
            case "HolyWorld" -> ServerEventsService.Source.HW;
            case "FunTime" -> ServerEventsService.Source.FT;
            default -> ServerDetection.isHolyWorld() ? ServerEventsService.Source.HW : ServerEventsService.Source.FT;
        };
        if (eventsService.getSource() != target) {
            eventsService.setSource(target);
        }
        eventsService.tick();
    }

    @Override
    public void onRender2D(GuiGraphicsExtractor context, float tickDelta) {
        if (!isEnabled() || mc.player == null) return;
        TextureAssets.ensureInitialized();

        List<ServerEventsService.Card> cards = eventsService.getCards();
        int limit = Math.min(cards.size(), maxEvents.getIntValue());

        Theme theme = ThemeManager.getInstance().getCurrentTheme();
        float w = 166.0f;
        float headerH = 19.0f;
        float rowH = 14.5f;
        int rows = Math.max(1, limit);
        float h = headerH + rows * rowH + 5.0f;

        dragWidget.syncExternalPosition(posX.getFloatValue(), posY.getFloatValue());
        dragWidget.setBounds(w, h);
        float x = dragWidget.getX();
        float y = dragWidget.getY();

        HudPanelRenderer.beginWidget("Schedules");
        try {
            String title = "Schedules · " + eventsService.getSource().name();
            HudPanelRenderer.drawHeaderPanel(context, "Schedules", x, y, w, h, headerH, 6.0f,
                    TextureAssets.DESIRE_HUD_CLOCK, title, 1.0f);

            float curY = y + headerH + 2.5f;
            if (limit == 0) {
                String msg = eventsService.getStatus() == ServerEventsService.Status.ERROR ? "Events API unavailable" : "Loading events...";
                Render2DHelper.drawScaledText(context, msg, x + 8.0f, curY + 2.5f, theme.getMutedTextColor(), 0.72f, false);
                return;
            }

            for (int i = 0; i < limit; i++) {
                ServerEventsService.Card c = cards.get(i);
                int dotCol = c.live() ? 0xFF4ADE80 : c.phaseColor();
                Render2DHelper.drawCircle(context, x + 9.5f, curY + 6.0f, 2.3f, dotCol);

                String name = Render2DHelper.trimToWidth(c.title() + " " + c.server(), 0.70f, w - 58.0f);
                Render2DHelper.drawScaledText(context, name, x + 16.0f, curY + 2.5f, 0xFFF1F5F9, 0.70f, false);

                String timeTag = c.phase() != null && !c.phase().isBlank() ? c.phase() : (c.live() ? "LIVE" : "--");
                float tw = Render2DHelper.getTextWidth(timeTag, 0.66f);
                Render2DHelper.drawScaledText(context, timeTag, x + w - tw - 6.0f, curY + 2.8f,
                        ColorUtils.withAlpha(dotCol, 240), 0.66f, true);
                curY += rowH;
            }
        } finally {
            HudPanelRenderer.endWidget();
        }
    }
}
