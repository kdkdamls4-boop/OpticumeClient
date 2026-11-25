package dev.opticumeclient.client.modules.combat;

import dev.opticumeclient.client.modules.Category;
import dev.opticumeclient.client.modules.Module;
import dev.opticumeclient.client.setting.ModeSetting;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Enhanced AutoSwap:
 * - Toggle mode: instant toggle-swap between primary & secondary offhand items (or Head Swap helmet <-> sphere/head).
 * - Wheel mode: opens a 3-slot radial AutoSwapWheelScreen for quick selection.
 */
public class AutoSwapModule extends Module {
    private static AutoSwapModule instance;

    private final ModeSetting swapMode;
    private final ModeSetting firstItemSetting;
    private final ModeSetting secondItemSetting;
    private final ModeSetting thirdItemSetting;

    public AutoSwapModule() {
        super("AutoSwap", "Быстрый свап 2-й руки или шлема (Toggle / 3-слот Wheel)", Category.COMBAT);
        instance = this;

        this.swapMode = addSetting(new ModeSetting(
                "Mode",
                "Режим работы: мгновенный Toggle или радиальное колесо (Wheel)",
                "Toggle",
                "Toggle", "Wheel"
        ));
        this.firstItemSetting = addSetting(new ModeSetting(
                "Primary Item",
                "Первый предмет для свапа",
                "Shield",
                "Shield", "Golden Apple", "Totem", "Sphere / Head", "Ender Pearl", "Head Swap"
        ));
        this.secondItemSetting = addSetting(new ModeSetting(
                "Secondary Item",
                "Второй предмет (ставится, если 1-й уже активен)",
                "Golden Apple",
                "Golden Apple", "Shield", "Totem", "Sphere / Head", "Ender Pearl", "Head Swap"
        ));
        this.thirdItemSetting = addSetting(new ModeSetting(
                "Third Item",
                "Третий предмет для радиального колеса Wheel",
                "Totem",
                "Totem", "Shield", "Golden Apple", "Sphere / Head", "Ender Pearl", "Head Swap"
        ));
        this.thirdItemSetting.visibleWhen(() -> swapMode.is("Wheel"));
    }

    public static AutoSwapModule getInstance() {
        return instance;
    }

    @Override
    public String getDisplaySuffix() {
        return swapMode.getMode();
    }

    @Override
    public void onEnable() {
        if (swapMode.is("Wheel")) {
            if (mc.player != null) {
                mc.setScreenAndShow(new AutoSwapWheelScreen(
                        this,
                        firstItemSetting.getMode(),
                        secondItemSetting.getMode(),
                        thirdItemSetting.getMode()
                ));
            }
        } else {
            performSwap();
        }
        setEnabled(false);
    }

    public void executeWheelSelection(String selection) {
        if (mc.player == null || mc.gameMode == null) return;
        if ("Head Swap".equals(selection)) {
            performHeadSwap();
            return;
        }
        int slot = findMatchingSlot(selection);
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

    private void performSwap() {
        if (mc.player == null || mc.gameMode == null) return;

        if ("Head Swap".equals(firstItemSetting.getMode()) || "Head Swap".equals(secondItemSetting.getMode())) {
            performHeadSwap();
            return;
        }

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

    /**
     * Swaps head armor slot (menu slot 5) between Helmet and Player Head / Sphere.
     */
    private void performHeadSwap() {
        if (mc.player == null || mc.gameMode == null) return;
        ItemStack currentHead = mc.player.inventoryMenu.getSlot(5).getItem();
        boolean wearingHead = currentHead.is(Items.PLAYER_HEAD);

        int targetSlot = -1;
        for (int i = 9; i <= 44; i++) {
            ItemStack st = mc.player.inventoryMenu.getSlot(i).getItem();
            if (st.isEmpty()) continue;
            if (wearingHead && st.is(ItemTags.HEAD_ARMOR)) {
                targetSlot = i;
                break;
            }
            if (!wearingHead && st.is(Items.PLAYER_HEAD)) {
                targetSlot = i;
                break;
            }
        }
        if (targetSlot == -1) return;

        int cid = mc.player.inventoryMenu.containerId;
        mc.gameMode.handleContainerInput(cid, targetSlot, 0, ContainerInput.PICKUP, mc.player);
        mc.gameMode.handleContainerInput(cid, 5, 0, ContainerInput.PICKUP, mc.player);
        mc.gameMode.handleContainerInput(cid, targetSlot, 0, ContainerInput.PICKUP, mc.player);
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
