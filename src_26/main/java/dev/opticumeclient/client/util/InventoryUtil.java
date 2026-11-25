package dev.opticumeclient.client.util;

import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.network.protocol.game.ServerboundSelectBundleItemPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.List;
import java.util.function.Predicate;

/**
 * Inventory lookup, Bundle & Shulker container inspection/extraction,
 * enchantment helpers, and instant HvH-safe swap-and-use utility for MC 26.3.
 */
public final class InventoryUtil {
    public record BundleMatch(int inventorySlot, int containerSlot, int bundleItemIndex, ItemStack stack) {}

    private InventoryUtil() {
    }

    public static int toMenuSlot(int inventorySlot) {
        return (inventorySlot >= 0 && inventorySlot < 9) ? (36 + inventorySlot) : inventorySlot;
    }

    public static int searchStack(Predicate<ItemStack> match, int start, int end) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return -1;
        int bound = Math.min(end, 36);
        for (int i = Math.max(0, start); i < bound; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (!stack.isEmpty() && match.test(stack)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Searches inside all Bundles in the player's inventory (DataComponents.BUNDLE_CONTENTS).
     */
    public static BundleMatch findInBundles(Predicate<ItemStack> match) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return null;
        for (int invSlot = 0; invSlot < 36; invSlot++) {
            ItemStack bundle = mc.player.getInventory().getItem(invSlot);
            if (bundle.isEmpty()) continue;
            BundleContents contents = bundle.get(DataComponents.BUNDLE_CONTENTS);
            if (contents == null || contents.isEmpty()) continue;
            List<ItemStack> copies = contents.itemCopies().toList();
            for (int idx = 0; idx < copies.size(); idx++) {
                ItemStack inner = copies.get(idx);
                if (!inner.isEmpty() && match.test(inner)) {
                    return new BundleMatch(invSlot, toMenuSlot(invSlot), idx, inner);
                }
            }
        }
        return null;
    }

    /**
     * Searches for a Shulker Box in inventory that contains at least one matching item (DataComponents.CONTAINER).
     */
    public static int findShulkerContaining(Predicate<ItemStack> match) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return -1;
        for (int invSlot = 0; invSlot < 36; invSlot++) {
            ItemStack box = mc.player.getInventory().getItem(invSlot);
            if (box.isEmpty()) continue;
            ItemContainerContents contents = box.get(DataComponents.CONTAINER);
            if (contents == null) continue;
            boolean found = contents.nonEmptyItemCopyStream().anyMatch(match);
            if (found) {
                return invSlot;
            }
        }
        return -1;
    }

    /**
     * Counts matching items across main inventory, Bundles, and Shulker Boxes.
     */
    public static int countTotalWithContainers(Predicate<ItemStack> match) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return 0;
        int total = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack st = mc.player.getInventory().getItem(i);
            if (st.isEmpty()) continue;
            if (match.test(st)) {
                total += st.getCount();
            }
            BundleContents bundle = st.get(DataComponents.BUNDLE_CONTENTS);
            if (bundle != null && !bundle.isEmpty()) {
                total += bundle.itemCopies().filter(match).mapToInt(ItemStack::getCount).sum();
            }
            ItemContainerContents shulker = st.get(DataComponents.CONTAINER);
            if (shulker != null) {
                total += shulker.nonEmptyItemCopyStream().filter(match).mapToInt(ItemStack::getCount).sum();
            }
        }
        return total;
    }

    public static boolean swapAndUseHvH(Predicate<ItemStack> match) {
        return swapAndUseHvH(match, true);
    }

    public static boolean swapAndUseHvH(Predicate<ItemStack> match, boolean allowBundleExtract) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || mc.gameMode == null || mc.player.connection == null) {
            return false;
        }

        if (match.test(mc.player.getMainHandItem())) {
            if (mc.player.getCooldowns().isOnCooldown(mc.player.getMainHandItem())) return false;
            mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
            return true;
        }
        if (match.test(mc.player.getOffhandItem())) {
            if (mc.player.getCooldowns().isOnCooldown(mc.player.getOffhandItem())) return false;
            mc.gameMode.useItem(mc.player, InteractionHand.OFF_HAND);
            return true;
        }

        int slotHotbar = searchStack(match, 0, 9);
        int slotInv = searchStack(match, 9, 36);
        int found = slotHotbar != -1 ? slotHotbar : slotInv;
        if (found == -1) {
            return allowBundleExtract && extractFromBundleAndUse(match);
        }
        if (mc.player.getCooldowns().isOnCooldown(mc.player.getInventory().getItem(found))) {
            return false;
        }

        int previousSlot = mc.player.getInventory().getSelectedSlot();

        if (slotHotbar != -1) {
            setCarriedSlot(mc, slotHotbar);
            mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
            setCarriedSlot(mc, previousSlot);
            return true;
        }

        int slotCorrectable = -1;
        for (int i = 0; i < 8; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.isEmpty() || stack.getUseAnimation() == ItemUseAnimation.NONE) {
                slotCorrectable = i;
                if (stack.isEmpty()) break;
            }
        }

        mc.player.setSprinting(false);
        int containerId = mc.player.inventoryMenu.containerId;

        if (slotCorrectable == -1) {
            mc.gameMode.handleContainerInput(containerId, slotInv, 8, ContainerInput.SWAP, mc.player);
            mc.player.connection.send(new ServerboundContainerClosePacket(containerId));
            setCarriedSlot(mc, 8);
            mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
            setCarriedSlot(mc, previousSlot);
        } else {
            mc.gameMode.handleContainerInput(containerId, slotInv, slotCorrectable, ContainerInput.SWAP, mc.player);
            mc.player.connection.send(new ServerboundContainerClosePacket(containerId));
            setCarriedSlot(mc, slotCorrectable);
            mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
            setCarriedSlot(mc, previousSlot);
            mc.gameMode.handleContainerInput(containerId, slotInv, slotCorrectable, ContainerInput.SWAP, mc.player);
            mc.player.connection.send(new ServerboundContainerClosePacket(containerId));
        }
        return true;
    }

    /**
     * Selects a matching item inside a Bundle via ServerboundSelectBundleItemPacket,
     * extracts it into an empty hotbar/inventory slot, uses it, and closes the menu cleanly.
     */
    public static boolean extractFromBundleAndUse(Predicate<ItemStack> match) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || mc.gameMode == null || mc.player.connection == null) {
            return false;
        }
        if (mc.player.inventoryMenu == null || !mc.player.inventoryMenu.getCarried().isEmpty()) {
            return false;
        }
        BundleMatch bm = findInBundles(match);
        if (bm == null) return false;
        if (mc.player.getCooldowns().isOnCooldown(bm.stack())) {
            return false;
        }

        int freeHotbar = -1;
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getItem(i).isEmpty()) {
                freeHotbar = i;
                break;
            }
        }
        int freeMainInv = -1;
        if (freeHotbar == -1) {
            for (int i = 9; i < 36; i++) {
                if (mc.player.getInventory().getItem(i).isEmpty()) {
                    freeMainInv = i;
                    break;
                }
            }
            if (freeMainInv == -1) return false;
            freeHotbar = 8;
        }

        int containerId = mc.player.inventoryMenu.containerId;
        int targetMenuSlot = toMenuSlot(freeHotbar);
        int prevSlot = mc.player.getInventory().getSelectedSlot();

        if (freeMainInv != -1) {
            // Temporarily park hotbar slot 8 in the empty main-inventory slot
            mc.gameMode.handleContainerInput(containerId, freeMainInv, freeHotbar, ContainerInput.SWAP, mc.player);
        }

        int bundleContainerSlot = (freeMainInv != -1 && bm.inventorySlot() == freeHotbar)
                ? freeMainInv
                : bm.containerSlot();

        mc.player.connection.send(new ServerboundSelectBundleItemPacket(bundleContainerSlot, bm.bundleItemIndex()));
        // Right-click bundle slot to extract selected item onto cursor, then left-click empty hotbar slot
        mc.gameMode.handleContainerInput(containerId, bundleContainerSlot, 1, ContainerInput.PICKUP, mc.player);
        mc.gameMode.handleContainerInput(containerId, targetMenuSlot, 0, ContainerInput.PICKUP, mc.player);
        mc.player.connection.send(new ServerboundContainerClosePacket(containerId));

        setCarriedSlot(mc, freeHotbar);
        mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
        setCarriedSlot(mc, prevSlot);

        if (freeMainInv != -1) {
            mc.gameMode.handleContainerInput(containerId, freeMainInv, freeHotbar, ContainerInput.SWAP, mc.player);
            mc.player.connection.send(new ServerboundContainerClosePacket(containerId));
        }
        return true;
    }

    public static void setCarriedSlot(Minecraft mc, int slot) {
        if (mc == null || mc.player == null || mc.player.connection == null) return;
        if (slot < 0 || slot > 8) return;
        mc.player.connection.send(new ServerboundSetCarriedItemPacket(slot));
        mc.player.getInventory().setSelectedSlot(slot);
    }

    public static int enchantLevel(ItemStack stack, ResourceKey<Enchantment> enchantment) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null || stack == null || stack.isEmpty()) return 0;
        try {
            Holder<Enchantment> holder = mc.level.registryAccess()
                    .lookupOrThrow(Registries.ENCHANTMENT)
                    .getOrThrow(enchantment);
            ItemEnchantments enchantments = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
            return enchantments.getLevel(holder);
        } catch (Throwable ignored) {
            return 0;
        }
    }
}
