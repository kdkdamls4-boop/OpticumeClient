package dev.opticumeclient.client.modules.combat;

import dev.opticumeclient.client.modules.Category;
import dev.opticumeclient.client.modules.Module;
import dev.opticumeclient.client.modules.ModuleManager;
import dev.opticumeclient.client.setting.BooleanSetting;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.EntityHitResult;

public class MiddleClickPearlModule extends Module {
    private final BooleanSetting fromInventory = new BooleanSetting("From Inventory", "Автоматически доставать жемчуг из инвентаря", true);
    private final BooleanSetting swapBack = new BooleanSetting("Swap Back", "Возвращать прежний слот после броска", true);
    private final BooleanSetting skipOnPlayer = new BooleanSetting("Skip On Player", "Не бросать при наведении на игрока (для MiddleClickFriend)", true);

    private boolean wasPressed = false;

    public MiddleClickPearlModule() {
        super("MiddleClickPearl", "Мгновенный бросок эндер-жемчуга по нажатию СКМ", Category.COMBAT);
        addSetting(fromInventory);
        addSetting(swapBack);
        addSetting(skipOnPlayer);
    }

    @Override
    public void onDisable() {
        wasPressed = false;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.level == null || mc.gameMode == null) {
            wasPressed = false;
            return;
        }
        if (mc.gui != null && mc.gui.screen() != null) {
            wasPressed = false;
            return;
        }

        boolean isPressed = (mc.mouseHandler != null && mc.mouseHandler.isMiddlePressed())
                || (mc.options != null && mc.options.keyPickItem.isDown());

        if (isPressed && !wasPressed) {
            tryThrowPearl();
        }
        wasPressed = isPressed;
    }

    private void tryThrowPearl() {
        MiddleClickFriendModule mcf = ModuleManager.getInstance().getModule(MiddleClickFriendModule.class);
        if (skipOnPlayer.isEnabled() && mcf != null && mcf.isEnabled()
                && mc.hitResult instanceof EntityHitResult entityHit
                && entityHit.getEntity() instanceof Player) {
            return;
        }

        int prevSlot = mc.player.getInventory().getSelectedSlot();
        int hotbarSlot = findPearlInHotbar();
        boolean swappedFromInv = false;
        int invContainerSlot = -1;

        if (hotbarSlot == -1 && fromInventory.isEnabled()) {
            invContainerSlot = findPearlInInventory();
            if (invContainerSlot != -1) {
                mc.gameMode.handleContainerInput(
                        mc.player.inventoryMenu.containerId,
                        invContainerSlot,
                        prevSlot,
                        ContainerInput.SWAP,
                        mc.player
                );
                hotbarSlot = prevSlot;
                swappedFromInv = true;
            }
        }

        if (hotbarSlot == -1) return;

        ItemStack stack = mc.player.getInventory().getItem(hotbarSlot);
        if (stack.isEmpty() || !stack.is(Items.ENDER_PEARL) || mc.player.getCooldowns().isOnCooldown(stack)) {
            if (swappedFromInv) {
                mc.gameMode.handleContainerInput(
                        mc.player.inventoryMenu.containerId,
                        invContainerSlot,
                        prevSlot,
                        ContainerInput.SWAP,
                        mc.player
                );
            }
            return;
        }

        if (hotbarSlot != prevSlot) {
            mc.player.getInventory().setSelectedSlot(hotbarSlot);
        }

        mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);

        if (swapBack.isEnabled()) {
            if (hotbarSlot != prevSlot) {
                mc.player.getInventory().setSelectedSlot(prevSlot);
            }
            if (swappedFromInv) {
                mc.gameMode.handleContainerInput(
                        mc.player.inventoryMenu.containerId,
                        invContainerSlot,
                        prevSlot,
                        ContainerInput.SWAP,
                        mc.player
                );
            }
        }
    }

    private int findPearlInHotbar() {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (!stack.isEmpty() && stack.is(Items.ENDER_PEARL)) {
                return i;
            }
        }
        return -1;
    }

    private int findPearlInInventory() {
        for (int i = 9; i <= 35; i++) {
            ItemStack stack = mc.player.inventoryMenu.getSlot(i).getItem();
            if (!stack.isEmpty() && stack.is(Items.ENDER_PEARL)) {
                return i;
            }
        }
        return -1;
    }
}
