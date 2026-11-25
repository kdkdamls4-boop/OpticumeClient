package dev.opticumeclient.client.modules.combat;

import dev.opticumeclient.client.gui.ClickGuiScreen;
import dev.opticumeclient.client.render.ColorUtils;
import dev.opticumeclient.client.render.Render2DHelper;
import dev.opticumeclient.client.render.ThemeManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 3-slot radial wheel selector for AutoSwapModule (Shield / Gapple / Totem / Sphere / Pearl / Head Swap).
 */
public class AutoSwapWheelScreen extends Screen {
    private final AutoSwapModule module;
    private final String[] slots;
    private int hoveredSector = -1;

    public AutoSwapWheelScreen(AutoSwapModule module, String slot1, String slot2, String slot3) {
        super(Component.literal("AutoSwap Wheel"));
        this.module = module;
        this.slots = new String[]{slot1, slot2, slot3};
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        int cx = this.width / 2;
        int cy = this.height / 2;
        int accent = ThemeManager.getInstance().getAccentColor(0);

        double dx = mouseX - cx;
        double dy = mouseY - cy;
        double dist = Math.hypot(dx, dy);

        hoveredSector = -1;
        if (dist >= 16.0) {
            // Sector 0: Top (-90°), Sector 1: Bottom-Right (+30°), Sector 2: Bottom-Left (+150°)
            double deg = Math.toDegrees(Math.atan2(dy, dx)); // -180..180
            if (deg >= -150.0 && deg < -30.0) {
                hoveredSector = 0;
            } else if (deg >= -30.0 && deg < 90.0) {
                hoveredSector = 1;
            } else {
                hoveredSector = 2;
            }
        }

        // Center hub
        Render2DHelper.drawRoundedRect(graphics, cx - 34, cy - 12, 68, 24, 10, ColorUtils.rgba(16, 18, 26, 215));
        Render2DHelper.drawRoundedOutline(graphics, cx - 34, cy - 12, 68, 24, 10, 1, ColorUtils.withAlpha(accent, 180));
        Render2DHelper.drawCenteredString(graphics, this.font, "AutoSwap", cx, cy - 4, 0xFFEAEFF8);

        int[][] offsets = new int[][]{
                {0, -64},   // Top
                {78, 36},   // Bottom-Right
                {-78, 36}   // Bottom-Left
        };

        for (int i = 0; i < 3; i++) {
            int bx = cx + offsets[i][0] - 54;
            int by = cy + offsets[i][1] - 22;
            boolean hovered = (i == hoveredSector);

            int bg = hovered ? ColorUtils.withAlpha(accent, 170) : ColorUtils.rgba(18, 20, 30, 210);
            int border = hovered ? 0xFFFFFFFF : ColorUtils.withAlpha(accent, 130);

            Render2DHelper.drawRoundedRect(graphics, bx, by, 108, 44, 9, bg);
            Render2DHelper.drawRoundedOutline(graphics, bx, by, 108, 44, 9, 1, border);

            ItemStack icon = getPreviewStack(slots[i]);
            if (!icon.isEmpty()) {
                graphics.item(icon, bx + 8, by + 14);
            }

            Render2DHelper.drawString(graphics, this.font, slots[i], bx + 28, by + 10, 0xFFFFFFFF, true);
            String sub = "Slot " + (i + 1);
            Render2DHelper.drawString(graphics, this.font, sub, bx + 28, by + 24, hovered ? 0xFFF2F6FF : 0xFFAAB4C8, false);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        int btn = ClickGuiScreen.normalizeMouseButton(event.button());
        if (btn == 0 && hoveredSector >= 0 && hoveredSector < slots.length) {
            module.executeWheelSelection(slots[hoveredSector]);
            this.onClose();
            return true;
        }
        if (btn == 1) {
            this.onClose();
            return true;
        }
        return super.mouseClicked(event, doubled);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (key == 256) { // ESC
            this.onClose();
            return true;
        }
        if (key == 49) { // '1'
            module.executeWheelSelection(slots[0]);
            this.onClose();
            return true;
        }
        if (key == 50) { // '2'
            module.executeWheelSelection(slots[1]);
            this.onClose();
            return true;
        }
        if (key == 51) { // '3'
            module.executeWheelSelection(slots[2]);
            this.onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null) {
            mc.setScreenAndShow(null);
        }
    }

    private ItemStack getPreviewStack(String mode) {
        return switch (mode) {
            case "Shield" -> new ItemStack(Items.SHIELD);
            case "Golden Apple" -> new ItemStack(Items.GOLDEN_APPLE);
            case "Totem" -> new ItemStack(Items.TOTEM_OF_UNDYING);
            case "Sphere / Head" -> new ItemStack(Items.PLAYER_HEAD);
            case "Ender Pearl" -> new ItemStack(Items.ENDER_PEARL);
            case "Head Swap" -> new ItemStack(Items.NETHERITE_HELMET);
            default -> ItemStack.EMPTY;
        };
    }
}
