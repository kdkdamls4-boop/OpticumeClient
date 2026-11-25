package dev.opticumeclient.client.modules.movement;

import com.mojang.blaze3d.platform.InputConstants;
import dev.opticumeclient.client.modules.Category;
import dev.opticumeclient.client.modules.Module;
import dev.opticumeclient.client.setting.NumberSetting;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;

/**
 * Ported from Desire & Skycore (ItemScrollerModule):
 * Fast item quick-moving across container and inventory slots while holding Shift + LMB.
 */
public class ItemScrollerModule extends Module {
    private static ItemScrollerModule instance;

    private final NumberSetting delayMsSetting;
    private long lastMoveTimeMs = 0L;
    private int lastMovedSlotId = -1;

    public ItemScrollerModule() {
        super("ItemScroller", "Быстрое перекладывание предметов при зажатом Shift + ЛКМ и проведении мышью", Category.MOVEMENT);
        instance = this;

        this.delayMsSetting = addSetting(new NumberSetting(
                "Delay (ms)",
                "Задержка между перемещением слотов в миллисекундах",
                35.0f, 0.0f, 200.0f, 5.0f
        ));
    }

    public static ItemScrollerModule getInstance() {
        return instance;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.gameMode == null || !(mc.gui.screen() instanceof AbstractContainerScreen<?> containerScreen)) {
            lastMovedSlotId = -1;
            return;
        }

        boolean shiftDown = InputConstants.isKeyDown(InputConstants.KEY_LSHIFT) || InputConstants.isKeyDown(InputConstants.KEY_RSHIFT);
        boolean lmbDown = mc.mouseHandler != null && mc.mouseHandler.isLeftPressed();

        if (!shiftDown || !lmbDown) {
            lastMovedSlotId = -1;
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastMoveTimeMs < delayMsSetting.getIntValue()) {
            return;
        }

        double mx = mc.mouseHandler.getScaledXPos(mc.getWindow());
        double my = mc.mouseHandler.getScaledYPos(mc.getWindow());

        for (Slot slot : containerScreen.getMenu().slots) {
            if (!slot.hasItem()) continue;
            if (slot.index == lastMovedSlotId) continue;
            if (isMouseOverSlot(containerScreen, slot, mx, my)) {
                mc.gameMode.handleContainerInput(
                        containerScreen.getMenu().containerId,
                        slot.index,
                        0,
                        ContainerInput.QUICK_MOVE,
                        mc.player
                );
                lastMovedSlotId = slot.index;
                lastMoveTimeMs = now;
                break;
            }
        }
    }

    private boolean isMouseOverSlot(AbstractContainerScreen<?> screen, Slot slot, double mx, double my) {
        int left;
        int top;
        if (screen instanceof dev.opticumeclient.client.util.IContainerScreenBounds bounds) {
            left = bounds.opticume$getLeftPos();
            top = bounds.opticume$getTopPos();
        } else {
            left = (screen.width - 176) / 2;
            top = (screen.height - 166) / 2;
        }
        double sx = left + slot.x;
        double sy = top + slot.y;
        return mx >= sx - 1 && mx <= sx + 17 && my >= sy - 1 && my <= sy + 17;
    }
}
