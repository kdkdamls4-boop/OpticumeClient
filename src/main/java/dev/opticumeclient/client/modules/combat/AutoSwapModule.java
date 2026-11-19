package dev.opticumeclient.client.modules.combat;

import dev.opticumeclient.client.modules.Category;
import dev.opticumeclient.client.modules.Module;
import dev.opticumeclient.client.setting.ModeSetting;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Ported from Desire & Skycore (AutoSwapModule):
 * Instant toggle-swap between primary & secondary offhand items (Shield, Gapple, Totem, Sphere/Head)
 * when pressing the module keybind.
 */
public class AutoSwapModule extends Module {
    private static AutoSwapModule instance;

    private final ModeSetting firstItemSetting;
    private final ModeSetting secondItemSetting;

    public AutoSwapModule() {
        super("AutoSwap", "Быстрый свап предметов во 2-ю руку (Щит <-> Гепл / Тотем / Сфера) по бинду", Category.COMBAT);
        instance = this;

        this.firstItemSetting = addSetting(new ModeSetting(
                "Primary Item",
                "Первый предмет для свапа во 2-ю руку",
                "Shield",
                "Shield", "Golden Apple", "Totem", "Sphere / Head", "Ender Pearl"
        ));
        this.secondItemSetting = addSetting(new ModeSetting(
                "Secondary Item",
                "Второй предмет (ставится, если 1-й уже во 2-й руке)",
                "Golden Apple",
                "Golden Apple", "Shield", "Totem", "Sphere / Head", "Ender Pearl"
        ));
    }

    public static AutoSwapModule getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        performSwap();
        // Auto-reset toggle state after executing the one-shot swap
        setEnabled(false);
    }

    private void performSwap() {
        if (mc.player == null || mc.gameMode == null) return;

        ItemStack offhand = mc.player.getOffhandItem();
        String targetMode = matchesMode(offhand, firstItemSetting.getMode())
                ? secondItemSetting.getMode()
                : firstItemSetting.getMode();

        int slot = findMatchingSlot(targetMode);
        if (slot == -1) {
            slot = findMatchingSlot(firstItemSetting.getMode());
        }
        if (slot != -1) {
            mc.gameMode.handleContainerInput(
                    mc.player.inventoryMenu.containerId,
                    slot,
                    40,
                    ContainerInput.SWAP,
                    mc.player
            );
        }
    }

    private int findMatchingSlot(String mode) {
        for (int i = 9; i <= 44; i++) {
            ItemStack st = mc.player.inventoryMenu.getSlot(i).getItem();
            if (!st.isEmpty() && matchesMode(st, mode)) {
                return i;
            }
        }
        return -1;
    }

    private boolean matchesMode(ItemStack st, String mode) {
        if (st == null || st.isEmpty()) return false;
        Item item = st.getItem();
        return switch (mode) {
            case "Shield" -> item == Items.SHIELD;
            case "Golden Apple" -> item == Items.GOLDEN_APPLE || item == Items.ENCHANTED_GOLDEN_APPLE;
            case "Totem" -> item == Items.TOTEM_OF_UNDYING;
            case "Sphere / Head" -> item == Items.PLAYER_HEAD || item == Items.TOTEM_OF_UNDYING;
            case "Ender Pearl" -> item == Items.ENDER_PEARL;
            default -> false;
        };
    }
}
