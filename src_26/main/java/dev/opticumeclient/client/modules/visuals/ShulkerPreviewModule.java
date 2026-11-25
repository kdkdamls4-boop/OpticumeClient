package dev.opticumeclient.client.modules.visuals;

import dev.opticumeclient.client.modules.Category;
import dev.opticumeclient.client.modules.Module;
import dev.opticumeclient.client.render.ColorUtils;
import dev.opticumeclient.client.render.Render2DHelper;
import dev.opticumeclient.client.render.ThemeManager;
import dev.opticumeclient.client.setting.BooleanSetting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;

import java.util.List;

/**
 * Renders an inline 9x3 inventory preview overlay when hovering over Shulker Boxes,
 * Chests with stored DataComponents.CONTAINER, or Bundles with DataComponents.BUNDLE_CONTENTS.
 */
public class ShulkerPreviewModule extends Module {
    private static ShulkerPreviewModule instance;

    private final BooleanSetting showBundles = new BooleanSetting("Preview Bundles", "Предпросмотр содержимого мешков (Bundle)", true);
    private final BooleanSetting compactEmpty = new BooleanSetting("Hide Empty Only", "Не показывать превью для пустых контейнеров", true);

    public ShulkerPreviewModule() {
        super("ShulkerPreview", "Предпросмотр содержимого шалкеров, сундуков и мешков при наведении", Category.VISUALS);
        instance = this;
        addSetting(showBundles);
        addSetting(compactEmpty);
    }

    public static ShulkerPreviewModule getInstance() {
        return instance;
    }

    public void renderHoveredTooltip(GuiGraphicsExtractor graphics, ItemStack stack, int mouseX, int mouseY) {
        if (!isEnabled() || graphics == null || stack == null || stack.isEmpty() || mc == null || mc.font == null) {
            return;
        }

        ItemContainerContents container = stack.get(DataComponents.CONTAINER);
        if (container != null) {
            NonNullList<ItemStack> slots = NonNullList.withSize(27, ItemStack.EMPTY);
            container.copyInto(slots);
            boolean hasAny = false;
            for (ItemStack s : slots) {
                if (!s.isEmpty()) {
                    hasAny = true;
                    break;
                }
            }
            if (!hasAny && compactEmpty.isEnabled()) {
                return;
            }
            drawGridOverlay(graphics, stack.getHoverName().getString(), slots, 9, 3, mouseX, mouseY);
            return;
        }

        if (showBundles.isEnabled()) {
            BundleContents bundle = stack.get(DataComponents.BUNDLE_CONTENTS);
            if (bundle != null && !bundle.isEmpty()) {
                List<ItemStack> copies = bundle.itemCopies().toList();
                int count = Math.min(27, copies.size());
                if (count == 0) return;
                int cols = Math.min(9, Math.max(4, count));
                int rows = (int) Math.ceil(count / (double) cols);
                NonNullList<ItemStack> slots = NonNullList.withSize(cols * rows, ItemStack.EMPTY);
                for (int i = 0; i < count; i++) {
                    slots.set(i, copies.get(i));
                }
                drawGridOverlay(graphics, stack.getHoverName().getString(), slots, cols, rows, mouseX, mouseY);
            }
        }
    }

    private void drawGridOverlay(GuiGraphicsExtractor graphics,
                                 String title,
                                 List<ItemStack> slots,
                                 int cols,
                                 int rows,
                                 int mouseX,
                                 int mouseY) {
        int slotSize = 18;
        int pad = 6;
        int headerH = 14;
        int boxW = pad * 2 + cols * slotSize;
        int boxH = pad * 2 + headerH + rows * slotSize;

        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        int x = mouseX + 12;
        int y = mouseY - boxH - 10;
        if (x + boxW > sw - 6) x = Math.max(6, mouseX - boxW - 12);
        if (y < 6) y = Math.min(sh - boxH - 6, mouseY + 18);

        int accent = ThemeManager.getInstance().getAccentColor(0);
        Render2DHelper.drawRoundedRect(graphics, x, y, boxW, boxH, 5.0f, 0xEC0B0D14);
        Render2DHelper.drawRoundedOutline(graphics, x, y, boxW, boxH, 5.0f, 1.0f, ColorUtils.withAlpha(accent, 175));
        String shortTitle = Render2DHelper.trimToWidth(title, 0.72f, boxW - pad * 2.0f);
        Render2DHelper.drawScaledText(graphics, shortTitle, x + pad, y + 4.5f, 0xFFF2F4FA, 0.72f, false);

        int gridX = x + pad;
        int gridY = y + pad + headerH;
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                int idx = r * cols + c;
                int sx = gridX + c * slotSize;
                int sy = gridY + r * slotSize;
                Render2DHelper.drawRoundedRect(graphics, sx + 1, sy + 1, slotSize - 2, slotSize - 2, 2.5f, 0x55181C28);
                if (idx < slots.size()) {
                    ItemStack item = slots.get(idx);
                    if (!item.isEmpty()) {
                        graphics.item(item, sx + 1, sy + 1);
                        graphics.itemDecorations(mc.font, item, sx + 1, sy + 1);
                    }
                }
            }
        }
    }
}
