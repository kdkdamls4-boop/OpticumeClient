package dev.opticumeclient.client.util;

import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.function.Predicate;

/**
 * Inventory lookup, enchantment helpers, and instant HvH-safe swap-and-use utility for MC 26.3.
 */
public final class InventoryUtil {
    private InventoryUtil() {
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

    public static boolean swapAndUseHvH(Predicate<ItemStack> match) {
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
        if (found == -1 || mc.player.getCooldowns().isOnCooldown(mc.player.getInventory().getItem(found))) {
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
