package dev.opticumeclient.client.hud;

import dev.opticumeclient.client.modules.Category;
import dev.opticumeclient.client.modules.Module;
import dev.opticumeclient.client.render.ColorUtils;
import dev.opticumeclient.client.render.Render2DHelper;
import dev.opticumeclient.client.render.TextureAssets;
import dev.opticumeclient.client.render.Theme;
import dev.opticumeclient.client.render.ThemeManager;
import dev.opticumeclient.client.setting.NumberSetting;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Draggable BPS speed history graph widget.
 */
public class BpsGraphHud extends Module {
    private static final int BPS_SAMPLES = 44;

    private final NumberSetting posX = new NumberSetting("PosX", "X позиция", 8.0, 0.0, 1920.0, 1.0);
    private final NumberSetting posY = new NumberSetting("PosY", "Y позиция", 228.0, 0.0, 1080.0, 1.0);

    private final HudManager.DraggableWidget dragWidget;
    private final float[] bpsHistory = new float[BPS_SAMPLES];
    private int bpsWriteIndex = 0;
    private long lastBpsSampleMs = 0L;
    private double currentBps = 0.0;
    private double peakBps = 0.0;
    private String cachedBpsStr = "0.0 BPS";
    private float cachedBpsW = 42.0f;

    public BpsGraphHud() {
        super("BPSGraph", "График скорости игрока в блоках/сек", Category.HUD);
        addSetting(posX);
        addSetting(posY);
        setEnabled(true);

        this.dragWidget = HudManager.getInstance().registerWidget(new HudManager.DraggableWidget(
                "BPS Graph",
                posX.getFloatValue(),
                posY.getFloatValue(),
                136.0f,
                48.0f,
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
        double dx = mc.player.getX() - mc.player.xOld;
        double dz = mc.player.getZ() - mc.player.zOld;
        this.currentBps = Math.hypot(dx, dz) * 20.0;
        if (this.currentBps > this.peakBps) {
            this.peakBps = this.currentBps;
        } else {
            this.peakBps = Math.max(this.currentBps, this.peakBps * 0.992);
        }

        long now = System.currentTimeMillis();
        if (now - lastBpsSampleMs >= 45L) {
            bpsHistory[bpsWriteIndex] = (float) this.currentBps;
            bpsWriteIndex = (bpsWriteIndex + 1) % BPS_SAMPLES;
            lastBpsSampleMs = now;
        }

        long tenths = Math.round(currentBps * 10);
        cachedBpsStr = (tenths / 10) + "." + (tenths % 10) + " BPS";
        cachedBpsW = Render2DHelper.getTextWidth(cachedBpsStr, 0.74f);
    }

    @Override
    public void onRender2D(GuiGraphicsExtractor context, float tickDelta) {
        if (!isEnabled() || mc.player == null) return;
        TextureAssets.ensureInitialized();

        Theme theme = ThemeManager.getInstance().getCurrentTheme();
        int c1 = theme.getPrimaryColor();
        int c2 = theme.getSecondaryColor();

        float w = 136.0f;
        float h = 48.0f;
        dragWidget.syncExternalPosition(posX.getFloatValue(), posY.getFloatValue());
        dragWidget.setBounds(w, h);
        float x = dragWidget.getX();
        float y = dragWidget.getY();

        HudPanelRenderer.beginWidget("BPS Graph");
        try {
            HudPanelRenderer.drawSurface(context, "BPS Graph", x, y, w, h, 6.0f, 220, 38);

            Render2DHelper.drawTexture(context, TextureAssets.SPEED_ICON, x + 6.0f, y + 4.5f, 10.0f, 10.0f, c1);
            Render2DHelper.drawScaledText(context, "Speed", x + 19.0f, y + 5.5f, 0xFFFFFFFF, 0.76f, true);
            Render2DHelper.drawScaledText(context, cachedBpsStr, x + w - cachedBpsW - 6.0f, y + 5.5f, c2, 0.74f, true);

            float graphX = x + 6.0f;
            float graphY = y + 17.0f;
            float graphW = w - 12.0f;
            float graphH = h - 22.0f;
            float maxScale = Math.max(12.0f, (float) peakBps + 2.0f);

            Render2DHelper.drawRoundedRect(context, graphX, graphY, graphW, graphH, 3.0f, 0x4407090E);

            float stepW = graphW / (BPS_SAMPLES - 1);
            int step = 3;
            for (int i = 0; i < BPS_SAMPLES - 1; i += step) {
                int nextI = Math.min(BPS_SAMPLES - 1, i + step);
                int idx1 = (bpsWriteIndex + i) % BPS_SAMPLES;
                int idx2 = (bpsWriteIndex + nextI) % BPS_SAMPLES;

                float v1 = Math.min(maxScale, bpsHistory[idx1]);
                float v2 = Math.min(maxScale, bpsHistory[idx2]);

                float norm1 = v1 / maxScale;
                float norm2 = v2 / maxScale;

                float x1 = graphX + i * stepW;
                float spanW = (nextI - i) * stepW;
                float y1 = graphY + graphH - norm1 * (graphH - 3.0f) - 1.0f;
                float y2 = graphY + graphH - norm2 * (graphH - 3.0f) - 1.0f;

                int col = ColorUtils.interpolateColor(c1, c2, (float) i / (BPS_SAMPLES - 1));
                int fillCol = ColorUtils.withAlpha(col, 45);

                float topY = Math.min(y1, y2);
                Render2DHelper.drawRect(context, x1, topY, spanW + 0.6f, (graphY + graphH) - topY, fillCol);

                float segH = Math.max(1.4f, Math.abs(y2 - y1) + 1.0f);
                Render2DHelper.drawRect(context, x1, topY, spanW + 0.6f, segH, col);
            }
        } finally {
            HudPanelRenderer.endWidget();
        }
    }
}
