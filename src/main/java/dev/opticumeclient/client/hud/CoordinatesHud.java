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
import net.minecraft.core.BlockPos;

import java.util.Locale;

/**
 * Draggable Coordinates & Facing capsule widget.
 */
public class CoordinatesHud extends Module {
    private final NumberSetting posX = new NumberSetting("PosX", "X позиция", 8.0, 0.0, 1920.0, 1.0);
    private final NumberSetting posY = new NumberSetting("PosY", "Y позиция", 470.0, 0.0, 1080.0, 1.0);
    private final HudManager.DraggableWidget dragWidget;

    public CoordinatesHud() {
        super("Coordinates", "Отдельная капсула координат XYZ и направления", Category.HUD);
        addSetting(posX);
        addSetting(posY);
        setEnabled(true);

        this.dragWidget = HudManager.getInstance().registerWidget(new HudManager.DraggableWidget(
                "Coordinates",
                posX.getFloatValue(),
                posY.getFloatValue(),
                168.0f,
                20.0f,
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
        TextureAssets.ensureInitialized();

        Theme theme = ThemeManager.getInstance().getCurrentTheme();
        int c1 = theme.getPrimaryColor();
        int c2 = theme.getSecondaryColor();

        BlockPos pos = mc.player.blockPosition();
        String facing = mc.player.getDirection().getSerializedName().toUpperCase(Locale.ROOT);
        String dirShort = facing.isEmpty() ? "N" : facing.substring(0, 1);
        String text = "XYZ: " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + "  [" + dirShort + "]";

        float scale = 0.76f;
        float tw = Render2DHelper.getTextWidth(text, scale);
        float w = tw + 26.0f;
        float h = 20.0f;

        dragWidget.syncExternalPosition(posX.getFloatValue(), posY.getFloatValue());
        dragWidget.setBounds(w, h);
        float x = dragWidget.getX();
        float y = dragWidget.getY();

        HudPanelRenderer.beginWidget("Coordinates");
        try {
            HudPanelRenderer.drawSurface(context, "Coordinates", x, y, w, h, 5.5f, 218, 36);
            Render2DHelper.drawTexture(context, TextureAssets.COORDS_ICON, x + 6.0f, y + 4.5f, 10.5f, 10.5f, c2);
            Render2DHelper.drawScaledText(context, text, x + 20.0f, y + 6.2f,
                    ColorUtils.interpolateColor(0xFFFFFFFF, c1, 0.2f), scale, true);
        } finally {
            HudPanelRenderer.endWidget();
        }
    }
}
