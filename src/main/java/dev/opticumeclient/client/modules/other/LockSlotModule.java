package dev.opticumeclient.client.modules.other;

import dev.opticumeclient.client.modules.Category;
import dev.opticumeclient.client.modules.Module;
import dev.opticumeclient.client.setting.BooleanSetting;
import dev.opticumeclient.client.setting.MultiBoxSetting;

/**
 * Prevents accidentally dropping valuable items from locked hotbar slots (1..9),
 * both via the Q drop key and via Throw actions in container screens.
 */
public class LockSlotModule extends Module {
    private static LockSlotModule INSTANCE;

    private static final String[] SLOT_OPTIONS = {
            "Slot 1", "Slot 2", "Slot 3", "Slot 4", "Slot 5",
            "Slot 6", "Slot 7", "Slot 8", "Slot 9"
    };

    private final MultiBoxSetting lockedSlots = new MultiBoxSetting(
            "LockedSlots",
            "Заблокированные слоты хотбара (1–9)",
            SLOT_OPTIONS,
            "Slot 1", "Slot 2"
    );
    private final BooleanSetting blockInInventory = new BooleanSetting(
            "BlockInGui",
            "Блокировать выброс этих слотов и внутри инвентаря",
            true
    );

    public LockSlotModule() {
        super("LockSlot", "Защита выбранных слотов хотбара (1–9) от случайного выброса (Q)", Category.OTHER);
        INSTANCE = this;
        addSetting(lockedSlots);
        addSetting(blockInInventory);
    }

    public static LockSlotModule getInstance() {
        return INSTANCE;
    }

    public boolean isHotbarSlotLocked(int hotbarIndex) {
        if (!isEnabled() || hotbarIndex < 0 || hotbarIndex > 8) return false;
        return lockedSlots.isEnabled("Slot " + (hotbarIndex + 1));
    }

    public boolean shouldBlockDropInGui(int hotbarIndex) {
        return blockInInventory.isEnabled() && isHotbarSlotLocked(hotbarIndex);
    }
}
