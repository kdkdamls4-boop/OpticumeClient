package dev.opticumeclient.client.autobuy;

import dev.opticumeclient.client.gui.ClickGuiScreen;
import dev.opticumeclient.client.modules.other.AutoBuyModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class AutoBuyOverlay {
    private static final float LEFT_PANEL_WIDTH = 120.0F;
    private static final float RIGHT_PANEL_WIDTH = 176.0F;
    private static final float BUTTON_HEIGHT = 20.0F;
    private static final float BUTTON_GAP = 4.0F;
    private static final float ROW_HEIGHT = 22.0F;

    private static float historyScroll = 0.0F;
    private static float maxHistoryScroll = 0.0F;
    private static boolean isDraggingSlider = false;
    private static boolean isDraggingDurabilitySlider = false;
    public static volatile long suspiciousNotificationUntil = 0L;

    public static void showSuspiciousNotification() {
        suspiciousNotificationUntil = System.currentTimeMillis() + 4500L;
    }

    private AutoBuyOverlay() {
    }

    public static boolean shouldShow(AbstractContainerScreen<?> screen) {
        if (!(screen instanceof ContainerScreen gcs)) {
            return false;
        }
        if (AutoBuyEngine.isAuctionScreen(gcs)) {
            return true;
        }
        if (gcs.getMenu().getRowCount() != 6) {
            return false;
        }
        String title = gcs.getTitle().getString();
        return title != null && title.toLowerCase(Locale.ROOT).contains("аукцион");
    }

    public static void render(GuiGraphicsExtractor context, int gx, int gy, int gw, int gh, int mouseX, int mouseY) {
        Minecraft mc = Minecraft.getInstance();
        Font tr = mc.font;
        AutoBuyConfig cfg = AutoBuyConfig.cfg();
        boolean parsingActive = AutoBuyEngine.INSTANCE.parser.isActive();

        int leftPanelX = Math.max(2, (int) (gx - LEFT_PANEL_WIDTH - 6.0F));
        int leftPanelY = gy;
        int leftPanelW = (int) LEFT_PANEL_WIDTH;
        int btnX = leftPanelX + 4;
        int btnW = leftPanelW - 8;
        int btnH = (int) BUTTON_HEIGHT;

        int btn0Y = leftPanelY + 20;
        int btn1Y = btn0Y + btnH + (int) BUTTON_GAP;
        int btn2Y = btn1Y + btnH + (int) BUTTON_GAP;
        int btn3Y = btn2Y + btnH + (int) BUTTON_GAP;
        int btn4Y = btn3Y + btnH + (int) BUTTON_GAP;
        int sliderDiscountY = btn4Y + btnH + (int) BUTTON_GAP;
        int sliderDurabilityY = sliderDiscountY + btnH + (int) BUTTON_GAP;
        int leftPanelH = Math.max(gh, sliderDurabilityY + btnH + 6 - leftPanelY);

        drawVanillaBox(context, leftPanelX, leftPanelY, leftPanelW, leftPanelH);
        drawText(context, tr, "§fУправление", leftPanelX + 6, leftPanelY + 5, 0xFFFFFF);
        context.fill(leftPanelX + 2, leftPanelY + 16, leftPanelX + leftPanelW - 2, leftPanelY + 17, 0xFF373737);

        boolean leftDown = mc.mouseHandler != null && mc.mouseHandler.isLeftPressed();
        if (isDraggingSlider) {
            if (!leftDown) {
                isDraggingSlider = false;
            } else {
                updateSliderFromMouse(mouseX, btnX, btnW);
            }
        }
        if (isDraggingDurabilitySlider) {
            if (!leftDown) {
                isDraggingDurabilitySlider = false;
            } else {
                updateDurabilitySliderFromMouse(mouseX, btnX, btnW);
            }
        }

        renderVanillaButton(context, tr, btnX, btn0Y, btnW, btnH,
                cfg.enabled ? "§aAutoBuy: ВКЛ" : "§cAutoBuy: ВЫКЛ", isHovered(mouseX, mouseY, btnX, btn0Y, btnW, btnH));
        renderVanillaButton(context, tr, btnX, btn1Y, btnW, btnH,
                cfg.autoSell ? "§aAutoSell: ВКЛ" : "§cAutoSell: ВЫКЛ", isHovered(mouseX, mouseY, btnX, btn1Y, btnW, btnH));
        renderVanillaButton(context, tr, btnX, btn2Y, btnW, btnH,
                cfg.safeMode ? "§aSafeMode: ВКЛ" : "§cSafeMode: ВЫКЛ", isHovered(mouseX, mouseY, btnX, btn2Y, btnW, btnH));
        renderVanillaButton(context, tr, btnX, btn3Y, btnW, btnH,
                parsingActive ? "§eПарсер: СТОП" : "§fПарсить цены", isHovered(mouseX, mouseY, btnX, btn3Y, btnW, btnH));
        boolean hov4 = isHovered(mouseX, mouseY, btnX, btn4Y, btnW, btnH);
        renderVanillaButton(context, tr, btnX, btn4Y, btnW, btnH,
                hov4 ? "§eВыбор предметов" : "§fВыбор предметов", hov4);

        renderPercentSlider(context, tr, btnX, sliderDiscountY, btnW, btnH,
                "Скидка: " + cfg.sellDiscountPercent + "%", cfg.sellDiscountPercent, 50,
                isHovered(mouseX, mouseY, btnX, sliderDiscountY, btnW, btnH), isDraggingSlider);
        renderPercentSlider(context, tr, btnX, sliderDurabilityY, btnW, btnH,
                "Броня: ≥" + cfg.armorDurabilityPercent + "%", cfg.armorDurabilityPercent, 100,
                isHovered(mouseX, mouseY, btnX, sliderDurabilityY, btnW, btnH), isDraggingDurabilitySlider);

        int infoY = sliderDurabilityY + btnH + 6;
        if (parsingActive) {
            String trimmed = trimVanillaText(tr, AutoBuyEngine.INSTANCE.parser.statusLine(), leftPanelW - 8);
            drawText(context, tr, "§e" + trimmed, leftPanelX + 4, infoY, 0xFFFFA0);
        }

        renderHistoryPanel(context, tr, gx, gy, gw, gh, mouseX, mouseY);

        if (System.currentTimeMillis() < suspiciousNotificationUntil) {
            String warnText = "§c§lВНИМАНИЕ: ПОДОЗРИТЕЛЬНАЯ ЦЕНА!";
            int textW = tr.width(warnText);
            int bannerW = Math.max(160, textW + 20);
            int bannerH = 18;
            int bannerX = gx + (gw - bannerW) / 2;
            int bannerY = Math.max(2, gy - bannerH - 4);
            drawVanillaBox(context, bannerX, bannerY, bannerW, bannerH);
            drawText(context, tr, warnText, bannerX + (bannerW - textW) / 2, bannerY + 5, 0xFF5555);
        }
    }

    private static void drawVanillaBox(GuiGraphicsExtractor ctx, int x, int y, int w, int h) {
        ctx.fill(x, y, x + w, y + h, 0xE81C1C22);
        ctx.fill(x, y, x + w, y + 1, 0xFF000000);
        ctx.fill(x, y + h - 1, x + w, y + h, 0xFF000000);
        ctx.fill(x, y, x + 1, y + h, 0xFF000000);
        ctx.fill(x + w - 1, y, x + w, y + h, 0xFF000000);
        ctx.fill(x + 1, y + 1, x + w - 1, y + 2, 0xFF3E3E48);
        ctx.fill(x + 1, y + 1, x + 2, y + h - 1, 0xFF3E3E48);
    }

    private static void renderVanillaButton(GuiGraphicsExtractor ctx, Font tr, int x, int y, int w, int h,
                                            String text, boolean hover) {
        ctx.fill(x, y, x + w, y + h, hover ? 0xFF383C4A : 0xFF282A33);
        ctx.fill(x, y, x + w, y + 1, 0xFF000000);
        ctx.fill(x, y + h - 1, x + w, y + h, 0xFF000000);
        ctx.fill(x, y, x + 1, y + h, 0xFF000000);
        ctx.fill(x + w - 1, y, x + w, y + h, 0xFF000000);
        int tw = tr.width(text);
        drawText(ctx, tr, text, x + (w - tw) / 2, y + (h - 8) / 2, 0xFFFFFF);
    }

    private static void renderPercentSlider(GuiGraphicsExtractor context, Font tr, int x, int y, int w, int h,
                                            String label, int percent, int maxPercent, boolean hover, boolean dragging) {
        context.fill(x, y, x + w, y + h, 0xD0101010);
        context.fill(x, y, x + w, y + 1, 0xFF000000);
        context.fill(x, y + h - 1, x + w, y + h, 0xFF000000);
        context.fill(x, y + 1, x + 1, y + h, 0xFF000000);
        context.fill(x + w - 1, y, x + w, y + h, 0xFF000000);
        int thumbW = 8;
        float progress = Mth.clamp((float) percent / (float) maxPercent, 0.0F, 1.0F);
        int thumbX = x + 1 + (int) (progress * (w - 2 - thumbW));
        int fillCol = (hover || dragging) ? 0xFF6B8BFF : 0xFF556688;
        context.fill(thumbX, y + 1, thumbX + thumbW, y + h - 1, fillCol);
        int tw = tr.width(label);
        drawText(context, tr, label, x + (w - tw) / 2, y + (h - 8) / 2, 0xFFFFFF);
    }

    private static void updateSliderFromMouse(double mouseX, int bx, int bw) {
        int val = Mth.clamp((int) Math.round(((mouseX - bx) / Math.max(1, bw)) * 50.0), 1, 50);
        AutoBuyConfig cfg = AutoBuyConfig.cfg();
        if (cfg.sellDiscountPercent != val) {
            cfg.sellDiscountPercent = val;
            AutoBuyModule mod = AutoBuyModule.getInstance();
            if (mod != null) mod.sellDiscount.setValue((double) val);
            AutoBuyConfig.save();
        }
    }

    private static void updateDurabilitySliderFromMouse(double mouseX, int bx, int bw) {
        int val = Mth.clamp((int) Math.round(((mouseX - bx) / Math.max(1, bw)) * 100.0), 0, 100);
        AutoBuyConfig cfg = AutoBuyConfig.cfg();
        if (cfg.armorDurabilityPercent != val) {
            cfg.armorDurabilityPercent = val;
            AutoBuyModule mod = AutoBuyModule.getInstance();
            if (mod != null) mod.armorDurability.setValue((double) val);
            AutoBuyConfig.save();
        }
    }

    private static void renderHistoryPanel(GuiGraphicsExtractor context, Font tr, int gx, int gy, int gw, int gh,
                                           int mouseX, int mouseY) {
        int screenWidth = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int panelW = (int) RIGHT_PANEL_WIDTH;
        int panelX = gx + gw + 4;
        if (panelX + panelW > screenWidth) {
            panelX = gx - panelW - 4;
        }
        if (panelX < 0) return;

        int panelY = gy;
        int panelH = gh;
        drawVanillaBox(context, panelX, panelY, panelW, panelH);

        List<PurchaseRecord> history = AutoBuyConfig.cfg().history;
        drawText(context, tr, "§fИстория", panelX + 6, panelY + 5, 0xFFFFFF);
        String countStr = "§7(" + history.size() + ")";
        drawText(context, tr, countStr, panelX + panelW - 6 - tr.width(countStr), panelY + 5, 0xFFFFFF);
        context.fill(panelX + 2, panelY + 16, panelX + panelW - 2, panelY + 17, 0xFF373737);

        int contentTop = panelY + 18;
        int contentBottom = panelY + panelH - 2;
        int contentHeight = contentBottom - contentTop;
        float totalContentHeight = history.size() * ROW_HEIGHT;
        maxHistoryScroll = Math.max(0.0F, totalContentHeight - contentHeight);
        historyScroll = Mth.clamp(historyScroll, 0.0F, maxHistoryScroll);

        boolean hasScrollbar = maxHistoryScroll > 0;
        int itemAreaWidth = panelW - (hasScrollbar ? 8 : 4);
        ItemStack hoveredStack = ItemStack.EMPTY;
        PurchaseRecord hoveredRecord = null;

        if (history.isEmpty()) {
            drawText(context, tr, "§7История пуста", panelX + 8, contentTop + 6, 0x888888);
        } else {
            context.enableScissor(panelX + 1, contentTop, panelX + panelW - 1, contentBottom);
            for (int i = 0; i < history.size(); i++) {
                PurchaseRecord rec = history.get(i);
                float rowY = contentTop - historyScroll + i * ROW_HEIGHT;
                if (rowY + ROW_HEIGHT < contentTop || rowY > contentBottom) continue;

                int iry = (int) rowY;
                boolean isRowHovered = mouseX >= panelX + 2 && mouseX <= panelX + itemAreaWidth
                        && mouseY >= iry && mouseY < iry + (int) ROW_HEIGHT;
                if (isRowHovered) {
                    context.fill(panelX + 2, iry, panelX + itemAreaWidth, iry + (int) ROW_HEIGHT, 0x25FFFFFF);
                    hoveredRecord = rec;
                }

                ItemStack stack = createDisplayStack(rec.itemId);
                if (!stack.isEmpty()) {
                    context.item(stack, panelX + 4, iry + 3);
                    if (mouseX >= panelX + 4 && mouseX <= panelX + 20 && mouseY >= iry + 3 && mouseY <= iry + 19) {
                        hoveredStack = stack;
                    }
                }

                String status = rec.success ? "§a✓ " : "§c✗ ";
                String name = rec.label != null && !rec.label.isBlank() ? rec.label : (rec.itemId != null ? rec.itemId : "?");
                drawText(context, tr, trimVanillaText(tr, status + name, itemAreaWidth - 26), panelX + 24, iry + 3, 0xFFFFFF);
                String priceText = "§6" + formatPrice(rec.totalPrice) + "$";
                drawText(context, tr, "§7×" + rec.count, panelX + 24, iry + 12, 0xAAAAAA);
                drawText(context, tr, priceText, panelX + itemAreaWidth - tr.width(priceText) - 2, iry + 12, 0xFFAA00);
            }
            context.disableScissor();

            if (hasScrollbar) {
                int scrollbarX = panelX + panelW - 6;
                context.fill(scrollbarX, contentTop, scrollbarX + 4, contentBottom, 0xFF000000);
                float thumbHeight = Math.max(14.0F, contentHeight * (contentHeight / totalContentHeight));
                float thumbY = contentTop + (historyScroll / maxHistoryScroll) * (contentHeight - thumbHeight);
                context.fill(scrollbarX, (int) thumbY, scrollbarX + 4, (int) (thumbY + thumbHeight), 0xFF8B8B8B);
            }
        }

        if (hoveredRecord != null) {
            List<Component> tooltip = new ArrayList<>();
            tooltip.add(Component.literal(hoveredRecord.success ? "§aУспешно куплено" : "§cОшибка покупки"));
            String title = hoveredRecord.label != null && !hoveredRecord.label.isBlank()
                    ? hoveredRecord.label : (hoveredRecord.itemId != null ? hoveredRecord.itemId : "?");
            tooltip.add(Component.literal("§f" + title + " §7(×" + hoveredRecord.count + ")"));
            if (hoveredRecord.seller != null && !hoveredRecord.seller.isBlank()) {
                tooltip.add(Component.literal("§7Продавец: §f" + hoveredRecord.seller));
            }
            tooltip.add(Component.literal("§7Цена: §6" + formatPrice(hoveredRecord.totalPrice) + "$"));
            if (!hoveredRecord.success && hoveredRecord.failReason != null) {
                tooltip.add(Component.literal("§cПричина: §7" + hoveredRecord.failReason));
            }
            context.setComponentTooltipForNextFrame(tr, tooltip, mouseX, mouseY);
        } else if (!hoveredStack.isEmpty()) {
            context.setTooltipForNextFrame(tr, hoveredStack, mouseX, mouseY);
        }
    }

    public static boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount,
                                        int gx, int gy, int gw, int gh) {
        int leftPanelX = Math.max(2, (int) (gx - LEFT_PANEL_WIDTH - 6.0F));
        int leftPanelW = (int) LEFT_PANEL_WIDTH;
        int btnX = leftPanelX + 4;
        int btnW = leftPanelW - 8;
        int btnH = (int) BUTTON_HEIGHT;
        int sliderY = gy + 20 + (btnH + (int) BUTTON_GAP) * 5;

        if (mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= sliderY && mouseY <= sliderY + btnH) {
            int step = (int) Math.signum(verticalAmount);
            AutoBuyConfig cfg = AutoBuyConfig.cfg();
            int newPercent = Mth.clamp(cfg.sellDiscountPercent + step, 1, 50);
            if (newPercent != cfg.sellDiscountPercent) {
                cfg.sellDiscountPercent = newPercent;
                AutoBuyModule mod = AutoBuyModule.getInstance();
                if (mod != null) mod.sellDiscount.setValue((double) newPercent);
                AutoBuyConfig.save();
            }
            return true;
        }

        int screenWidth = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int panelX = gx + gw + 4;
        if (panelX + (int) RIGHT_PANEL_WIDTH > screenWidth) {
            panelX = gx - (int) RIGHT_PANEL_WIDTH - 4;
        }
        if (panelX >= 0 && mouseX >= panelX && mouseX <= panelX + RIGHT_PANEL_WIDTH
                && mouseY >= gy && mouseY <= gy + gh && maxHistoryScroll > 0) {
            historyScroll = Mth.clamp((float) (historyScroll - verticalAmount * 22.0), 0.0F, maxHistoryScroll);
            return true;
        }
        return false;
    }

    public static boolean mouseClicked(double mouseX, double mouseY, int button, int gx, int gy, int gw, int gh) {
        int btn = ClickGuiScreen.normalizeMouseButton(button);
        Minecraft mc = Minecraft.getInstance();
        AutoBuyConfig cfg = AutoBuyConfig.cfg();
        int leftPanelX = Math.max(2, (int) (gx - LEFT_PANEL_WIDTH - 6.0F));
        int leftPanelW = (int) LEFT_PANEL_WIDTH;
        int btnX = leftPanelX + 4;
        int btnW = leftPanelW - 8;
        int btnH = (int) BUTTON_HEIGHT;

        int btn0Y = gy + 20;
        int btn1Y = btn0Y + btnH + (int) BUTTON_GAP;
        int btn2Y = btn1Y + btnH + (int) BUTTON_GAP;
        int btn3Y = btn2Y + btnH + (int) BUTTON_GAP;
        int btn4Y = btn3Y + btnH + (int) BUTTON_GAP;
        int sliderDiscountY = btn4Y + btnH + (int) BUTTON_GAP;
        int sliderDurabilityY = sliderDiscountY + btnH + (int) BUTTON_GAP;

        if (isHovered(mouseX, mouseY, btnX, btn0Y, btnW, btnH)) {
            cfg.enabled = !cfg.enabled;
            AutoBuyModule mod = AutoBuyModule.getInstance();
            if (mod != null) mod.setEnabled(cfg.enabled);
            AutoBuyConfig.save();
            return true;
        }
        if (isHovered(mouseX, mouseY, btnX, btn1Y, btnW, btnH)) {
            cfg.autoSell = !cfg.autoSell;
            AutoBuyModule mod = AutoBuyModule.getInstance();
            if (mod != null) mod.autoSell.setValue(cfg.autoSell);
            if (!cfg.autoSell) AutoBuyEngine.INSTANCE.seller.stop();
            AutoBuyConfig.save();
            return true;
        }
        if (isHovered(mouseX, mouseY, btnX, btn2Y, btnW, btnH)) {
            cfg.safeMode = !cfg.safeMode;
            AutoBuyModule mod = AutoBuyModule.getInstance();
            if (mod != null) mod.safeMode.setValue(cfg.safeMode);
            AutoBuyConfig.save();
            return true;
        }
        if (isHovered(mouseX, mouseY, btnX, btn3Y, btnW, btnH)) {
            if (AutoBuyEngine.INSTANCE.parser.isActive()) {
                AutoBuyEngine.INSTANCE.parser.stop();
            } else {
                AutoBuyEngine.INSTANCE.parser.start();
            }
            return true;
        }
        if (isHovered(mouseX, mouseY, btnX, btn4Y, btnW, btnH)) {
            ClickGuiScreen.openOnAutoBuy();
            return true;
        }
        if (isHovered(mouseX, mouseY, btnX, sliderDiscountY, btnW, btnH)) {
            if (btn == 0) {
                isDraggingSlider = true;
                updateSliderFromMouse(mouseX, btnX, btnW);
            } else if (btn == 1) {
                cfg.sellDiscountPercent = 5;
                AutoBuyModule mod = AutoBuyModule.getInstance();
                if (mod != null) mod.sellDiscount.setValue(5.0);
                AutoBuyConfig.save();
            }
            return true;
        }
        if (isHovered(mouseX, mouseY, btnX, sliderDurabilityY, btnW, btnH)) {
            if (btn == 0) {
                isDraggingDurabilitySlider = true;
                updateDurabilitySliderFromMouse(mouseX, btnX, btnW);
            } else if (btn == 1) {
                cfg.armorDurabilityPercent = 50;
                AutoBuyModule mod = AutoBuyModule.getInstance();
                if (mod != null) mod.armorDurability.setValue(50.0);
                AutoBuyConfig.save();
            }
            return true;
        }
        return false;
    }

    private static ItemStack createDisplayStack(String itemId) {
        if (itemId == null || itemId.isBlank()) return ItemStack.EMPTY;
        Identifier id = Identifier.tryParse(itemId);
        if (id == null) return ItemStack.EMPTY;
        Item item = BuiltInRegistries.ITEM.getValue(id);
        return item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
    }

    private static String formatPrice(long v) {
        if (v <= 0L) return "0";
        StringBuilder sb = new StringBuilder(Long.toString(v));
        for (int i = sb.length() - 3; i > 0; i -= 3) {
            sb.insert(i, ' ');
        }
        return sb.toString();
    }

    private static boolean isHovered(double mouseX, double mouseY, double x, double y, double w, double h) {
        return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
    }

    private static void drawText(GuiGraphicsExtractor context, Font tr, String text, int x, int y, int color) {
        context.text(tr, text, x, y, (color >>> 24) == 0 ? color | 0xFF000000 : color, true);
    }

    private static String trimVanillaText(Font tr, String text, float maxWidth) {
        if (text == null || text.isEmpty() || maxWidth <= 0.0F) return "";
        if (tr.width(text) <= maxWidth) return text;
        for (int i = text.length() - 1; i > 0; i--) {
            String sub = text.substring(0, i) + "…";
            if (tr.width(sub) <= maxWidth) return sub;
        }
        return "";
    }
}
