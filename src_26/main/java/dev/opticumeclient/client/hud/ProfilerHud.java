package dev.opticumeclient.client.hud;

import dev.opticumeclient.client.command.ModuleProfiler;
import dev.opticumeclient.client.modules.Category;
import dev.opticumeclient.client.modules.Module;
import dev.opticumeclient.client.render.ColorUtils;
import dev.opticumeclient.client.render.Render2DHelper;
import dev.opticumeclient.client.render.TextureAssets;
import dev.opticumeclient.client.render.Theme;
import dev.opticumeclient.client.render.ThemeManager;
import dev.opticumeclient.client.setting.NumberSetting;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.List;
import java.util.Locale;

/**
 * Draggable visual profiler HUD widget displaying real-time FPS, frame budget bar graph,
 * and top modules ranked by average tick + render execution time in milliseconds.
 */
public class ProfilerHud extends Module {
    private static ProfilerHud instance;

    private final NumberSetting topCount = new NumberSetting("Rows", "Количество модулей в списке", 5.0, 3.0, 8.0, 1.0);
    private final NumberSetting posX = new NumberSetting("PosX", "X позиция", 560.0, 0.0, 1920.0, 1.0);
    private final NumberSetting posY = new NumberSetting("PosY", "Y позиция", 36.0, 0.0, 1080.0, 1.0);
    private final HudManager.DraggableWidget dragWidget;

    public ProfilerHud() {
        super("Profiler", "Визуальный оверлей нагрузки модулей по тикам и рендеру (ms)", Category.HUD);
        instance = this;
        addSetting(topCount);
        addSetting(posX);
        addSetting(posY);
        setEnabled(false);

        this.dragWidget = HudManager.getInstance().registerWidget(new HudManager.DraggableWidget(
                "Profiler",
                posX.getFloatValue(),
                posY.getFloatValue(),
                156.0f,
                76.0f,
                this::isEnabled,
                (nx, ny) -> {
                    posX.setValue((double) Math.round(nx));
                    posY.setValue((double) Math.round(ny));
                }
        ));
    }

    public static ProfilerHud getInstance() {
        return instance;
    }

    @Override
    public void onRender2D(GuiGraphicsExtractor context, float tickDelta) {
        if (!isEnabled() || mc.player == null) return;
        TextureAssets.ensureInitialized();

        List<ModuleProfiler.Sample> samples = ModuleProfiler.getInstance().getTopSamples(topCount.getIntValue());
        Theme theme = ThemeManager.getInstance().getCurrentTheme();
        int c1 = theme.getPrimaryColor();
        int c2 = theme.getSecondaryColor();

        float w = 162.0f;
        float headerH = 19.0f;
        float rowH = 15.0f;
        int rows = Math.max(1, samples.size());
        float h = headerH + rows * rowH + 5.0f;

        dragWidget.syncExternalPosition(posX.getFloatValue(), posY.getFloatValue());
        dragWidget.setBounds(w, h);
        float x = dragWidget.getX();
        float y = dragWidget.getY();

        double maxAvg = 0.25;
        for (ModuleProfiler.Sample s : samples) {
            if (s.avgTotalMs() > maxAvg) maxAvg = s.avgTotalMs();
        }

        HudPanelRenderer.beginWidget("Profiler");
        try {
            HudPanelRenderer.drawHeaderPanel(context, "Profiler", x, y, w, h, headerH, 6.0f,
                    TextureAssets.DESIRE_HUD_GAUGE, "Profiler · " + mc.getFps() + " FPS", 1.0f);

            float curY = y + headerH + 2.5f;
            if (samples.isEmpty()) {
                Render2DHelper.drawScaledText(context, "Collecting samples...", x + 8.0f, curY + 2.5f,
                        theme.getMutedTextColor(), 0.72f, false);
                return;
            }

            for (ModuleProfiler.Sample s : samples) {
                double ms = s.avgTotalMs();
                float loadFrac = (float) Math.min(1.0, ms / maxAvg);
                int barCol = ms < 0.25 ? 0xFF4ADE80 : (ms < 0.80 ? 0xFFFBBF24 : 0xFFFF5252);

                Render2DHelper.drawScaledText(context, Render2DHelper.trimToWidth(s.moduleName, 0.70f, w - 58.0f),
                        x + 7.0f, curY + 2.0f, 0xFFF1F5F9, 0.70f, false);
                String msStr = String.format(Locale.US, "%.2fms", ms);
                float mw = Render2DHelper.getTextWidth(msStr, 0.65f);
                Render2DHelper.drawScaledText(context, msStr, x + w - mw - 6.0f, curY + 2.2f, barCol, 0.65f, true);

                Render2DHelper.drawRoundedRectFloat(context, x + 7.0f, curY + 11.0f, w - 14.0f, 2.0f, 1.0f, 0x28FFFFFF);
                Render2DHelper.drawGradientRoundedRectFloat(context, x + 7.0f, curY + 11.0f,
                        Math.max(2.0f, (w - 14.0f) * loadFrac), 2.0f, 1.0f, c1, c1, barCol, c2);
                curY += rowH;
            }
        } finally {
            HudPanelRenderer.endWidget();
        }
    }
}
