package dev.opticumeclient.client.hud;

import dev.opticumeclient.client.modules.Category;
import dev.opticumeclient.client.modules.Module;
import dev.opticumeclient.client.render.ColorUtils;
import dev.opticumeclient.client.render.Render2DHelper;
import dev.opticumeclient.client.render.Theme;
import dev.opticumeclient.client.render.ThemeManager;
import dev.opticumeclient.client.setting.NumberSetting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Draggable inventory counter for Totems, Golden Apples, and Ender Pearls.
 */
public class TotemCounterHud extends Module {
    private final NumberSetting posX = new NumberSetting("PosX", "X позиция", 380.0, 0.0, 1920.0, 1.0);
    private final NumberSetting posY = new NumberSetting("PosY", "Y позиция", 435.0, 0.0, 1080.0, 1.0);
    private final HudManager.DraggableWidget dragWidget;

    private int cachedTotems = 0;
    private int cachedGapples = 0;
    private int cachedPearls = 0;
    private ItemStack totemStack;
    private ItemStack gappleStack;
    private ItemStack pearlStack;

    public TotemCounterHud() {
        super("TotemCounter", "Счетчик тотемов, геплов и эндер-перлов в инвентаре", Category.HUD);
        addSetting(posX);
        addSetting(posY);
        setEnabled(true);

        this.dragWidget = HudManager.getInstance().registerWidget(new HudManager.DraggableWidget(
                "TotemCounter",
                posX.getFloatValue(),
                posY.getFloatValue(),
                138.0f,
                26.0f,
                this::isEnabled,
                (newX, newY) -> {
                    posX.setValue((double) Math.round(newX));
                    posY.setValue((double) Math.round(newY));
                }
        ));
    }

    private void ensureStacks() {
        if (totemStack != null) return;
        try {
            totemStack = Items.TOTEM_OF_UNDYING.getDefaultInstance();
            gappleStack = Items.GOLDEN_APPLE.getDefaultInstance();
            pearlStack = Items.ENDER_PEARL.getDefaultInstance();
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;
        cachedTotems = countItem(Items.TOTEM_OF_UNDYING);
        cachedGapples = countItem(Items.GOLDEN_APPLE) + countItem(Items.ENCHANTED_GOLDEN_APPLE);
        cachedPearls = countItem(Items.ENDER_PEARL);
    }

    private int countItem(Item item) {
        if (mc.player == null) return 0;
        int total = 0;
        for (ItemStack stack : mc.player.getInventory().getNonEquipmentItems()) {
            if (stack != null && !stack.isEmpty() && stack.is(item)) {
                total += stack.getCount();
            }
        }
        ItemStack offhand = mc.player.getOffhandItem();
        if (offhand != null && !offhand.isEmpty() && offhand.is(item)) {
            total += offhand.getCount();
        }
        return total;
    }

    @Override
    public void onRender2D(GuiGraphicsExtractor context, float tickDelta) {
        if (!isEnabled() || mc.player == null) return;
        ensureStacks();

        Theme theme = ThemeManager.getInstance().getCurrentTheme();
        int c1 = theme.getPrimaryColor();

        float w = 138.0f;
        float h = 26.0f;
        dragWidget.syncExternalPosition(posX.getFloatValue(), posY.getFloatValue());
        dragWidget.setBounds(w, h);
        float x = dragWidget.getX();
        float y = dragWidget.getY();

        HudPanelRenderer.beginWidget("TotemCounter");
        try {
            HudPanelRenderer.drawSurface(context, "TotemCounter", x, y, w, h, 6.0f, 222, 40);

            float itemY = y + (h - 16.0f) * 0.5f;
            drawSlot(context, totemStack, cachedTotems, x + 5.0f, itemY, cachedTotems > 0 ? 0xFFFBBF24 : 0xFF9CA3AF);
            Render2DHelper.drawRect(context, x + 47.0f, y + 5.0f, 1.0f, h - 10.0f, ColorUtils.withAlpha(c1, 90));
            drawSlot(context, gappleStack, cachedGapples, x + 51.0f, itemY, cachedGapples > 0 ? 0xFF4ADE80 : 0xFF9CA3AF);
            Render2DHelper.drawRect(context, x + 92.0f, y + 5.0f, 1.0f, h - 10.0f, ColorUtils.withAlpha(c1, 90));
            drawSlot(context, pearlStack, cachedPearls, x + 96.0f, itemY, cachedPearls > 0 ? 0xFF38BDF8 : 0xFF9CA3AF);
        } finally {
            HudPanelRenderer.endWidget();
        }
    }

    private static void drawSlot(GuiGraphicsExtractor context, ItemStack iconStack, int count, float sx, float sy, int textColor) {
        if (iconStack != null && !iconStack.isEmpty()) {
            context.item(iconStack, Math.round(sx), Math.round(sy));
        }
        Render2DHelper.drawScaledText(context, "x" + count, sx + 18.0f, sy + 4.5f, textColor, 0.76f, true);
    }
}
