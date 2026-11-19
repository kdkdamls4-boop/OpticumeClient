package dev.opticumeclient.client.hud;

import dev.opticumeclient.client.modules.Category;
import dev.opticumeclient.client.modules.Module;
import dev.opticumeclient.client.render.ColorUtils;
import dev.opticumeclient.client.render.Render2DHelper;
import dev.opticumeclient.client.render.Theme;
import dev.opticumeclient.client.render.ThemeManager;
import dev.opticumeclient.client.setting.NumberSetting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3x2fStack;

/**
 * Ported from SystemDLC / Rockstar (InventoryHud):
 * Draggable 9x3 inventory grid widget displaying all 27 main inventory slots with item counts.
 */
public class InventoryHud extends Module {
    private final NumberSetting posX = new NumberSetting("PosX", "X позиция", 160.0, 0.0, 1920.0, 1.0);
    private final NumberSetting posY = new NumberSetting("PosY", "Y позиция", 8.0, 0.0, 1080.0, 1.0);

    private final HudManager.DraggableWidget dragWidget;

    public InventoryHud() {
        super("InventoryHUD", "Отображение сетки инвентаря 9x3 прямо на экране", Category.HUD);
        addSetting(posX);
        addSetting(posY);
        setEnabled(false);

        this.dragWidget = HudManager.getInstance().registerWidget(new HudManager.DraggableWidget(
                "InventoryHUD",
                posX.getFloatValue(),
                posY.getFloatValue(),
                162.0f,
                72.0f,
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

        float cell = 16.5f;
        float gap = 1.5f;
        float pad = 5.0f;
        float headerH = 17.0f;
        float w = pad * 2.0f + 9 * cell + 8 * gap;
        float h = headerH + pad + 3 * cell + 2 * gap;

        dragWidget.syncExternalPosition(posX.getFloatValue(), posY.getFloatValue());
        dragWidget.setBounds(w, h);

        float x = dragWidget.getX();
        float y = dragWidget.getY();

        Theme theme = ThemeManager.getInstance().getCurrentTheme();
        int c1 = theme.getPrimaryColor();
        int c2 = theme.getSecondaryColor();

        HudPanelRenderer.beginWidget("InventoryHUD");
        try {
        HudPanelRenderer.drawHeaderPanel(context, "InventoryHUD", x, y, w, h, headerH, 6.0f,
                dev.opticumeclient.client.render.TextureAssets.DESIRE_HUD_LAYERS, "Inventory", 1.0f);

        float startY = y + headerH + 2.0f;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int slotIdx = 9 + row * 9 + col;
                float cx = x + pad + col * (cell + gap);
                float cy = startY + row * (cell + gap);

                Render2DHelper.drawRoundedRectFloat(context, cx, cy, cell, cell, 3.0f, ColorUtils.rgba(10, 12, 18, 125));
                ItemStack stack = mc.player.getInventory().getItem(slotIdx);
                if (!stack.isEmpty()) {
                    drawSlotItem(context, stack, cx + 1.2f, cy + 1.2f, 14.0f);
                }
            }
        }
        } finally {
            HudPanelRenderer.endWidget();
        }
    }

    private static void drawSlotItem(GuiGraphicsExtractor context, ItemStack stack, float x, float y, float size) {
        Matrix3x2fStack matrices = context.pose();
        float scale = size / 16.0f;
        matrices.pushMatrix();
        matrices.translate(x, y);
        matrices.scale(scale, scale);
        try {
            context.fakeItem(stack, 0, 0);
        } catch (Throwable ignored) {
        }
        matrices.popMatrix();

        if (stack.getCount() > 1) {
            String cnt = String.valueOf(stack.getCount());
            float tw = Render2DHelper.getTextWidth(cnt, 0.58f);
            Render2DHelper.drawScaledText(context, cnt, x + size - tw, y + size - 5.5f, 0xFFFFFFFF, 0.58f, true);
        }
    }
}
