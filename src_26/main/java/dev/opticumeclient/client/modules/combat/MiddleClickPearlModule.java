package dev.opticumeclient.client.modules.combat;

import dev.opticumeclient.client.modules.Category;
import dev.opticumeclient.client.modules.Module;
import dev.opticumeclient.client.modules.ModuleManager;
import dev.opticumeclient.client.setting.BooleanSetting;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
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

    private enum State { IDLE, SWAP_IN, THROW, SWAP_BACK }
    private State state = State.IDLE;
    private int stepDelay = 0;
    private int prevSlot = -1;
    private int pearlSlot = -1;
    private boolean swappedFromInv = false;
    private int invContainerSlot = -1;
    private boolean wasPressed = false;

    public MiddleClickPearlModule() {
        super("MiddleClickPearl", "Мгновенный бросок эндер-жемчуга по нажатию СКМ", Category.COMBAT);
        addSetting(fromInventory);
        addSetting(swapBack);
        addSetting(skipOnPlayer);
    }

    @Override
    public void onDisable() {
        resetState();
        wasPressed = false;
    }

    private void resetState() {
        state = State.IDLE;
        stepDelay = 0;
        prevSlot = -1;
        pearlSlot = -1;
        swappedFromInv = false;
        invContainerSlot = -1;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.level == null || mc.gameMode == null) {
            resetState();
            wasPressed = false;
            return;
        }

        // Advance state machine if executing throw sequence
        if (state != State.IDLE) {
            handleStateMachine();
            return;
        }

        if (mc.gui != null && mc.gui.screen() != null) {
            wasPressed = false;
            return;
        }

        boolean isPressed = (mc.mouseHandler != null && mc.mouseHandler.isMiddlePressed())
                || (mc.options != null && mc.options.keyPickItem.isDown());

        if (isPressed && !wasPressed) {
            startPearlSequence();
        }
        wasPressed = isPressed;
    }

    private void startPearlSequence() {
        MiddleClickFriendModule mcf = ModuleManager.getInstance().getModule(MiddleClickFriendModule.class);
        if (skipOnPlayer.isEnabled() && mcf != null && mcf.isEnabled()
                && mc.hitResult instanceof EntityHitResult entityHit
                && entityHit.getEntity() instanceof Player) {
            return;
        }

        // Global cooldown pre-check before touching slots or inventory!
        if (mc.player.getCooldowns().isOnCooldown(new ItemStack(Items.ENDER_PEARL))) {
            return;
        }

        prevSlot = mc.player.getInventory().getSelectedSlot();
        pearlSlot = findPearlInHotbar();
        swappedFromInv = false;
        invContainerSlot = -1;

        if (pearlSlot == -1 && fromInventory.isEnabled()) {
            invContainerSlot = findPearlInInventory();
            if (invContainerSlot != -1) {
                swappedFromInv = true;
                pearlSlot = prevSlot;
            }
        }

        if (pearlSlot == -1) return;

        state = State.SWAP_IN;
        stepDelay = 0;
        handleStateMachine();
    }

    private void handleStateMachine() {
        if (stepDelay > 0) {
            stepDelay--;
            return;
        }

        switch (state) {
            case SWAP_IN -> {
                if (swappedFromInv) {
                    mc.gameMode.handleContainerInput(
                            mc.player.inventoryMenu.containerId,
                            invContainerSlot,
                            prevSlot,
                            ContainerInput.SWAP,
                            mc.player
                    );
                } else if (pearlSlot != prevSlot) {
                    mc.player.getInventory().setSelectedSlot(pearlSlot);
                    if (mc.player.connection != null) {
                        mc.player.connection.send(new ServerboundSetCarriedItemPacket(pearlSlot));
                    }
                }
                state = State.THROW;
                stepDelay = 1; // 1-tick delay before useItem
            }
            case THROW -> {
                mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
                mc.player.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, false);
                state = State.SWAP_BACK;
                stepDelay = 1; // 1-tick delay before reverting slot
            }
            case SWAP_BACK -> {
                if (swapBack.isEnabled()) {
                    if (swappedFromInv) {
                        mc.gameMode.handleContainerInput(
                                mc.player.inventoryMenu.containerId,
                                invContainerSlot,
                                prevSlot,
                                ContainerInput.SWAP,
                                mc.player
                        );
                    } else if (pearlSlot != prevSlot) {
                        mc.player.getInventory().setSelectedSlot(prevSlot);
                        if (mc.player.connection != null) {
                            mc.player.connection.send(new ServerboundSetCarriedItemPacket(prevSlot));
                        }
                    }
                }
                resetState();
            }
            default -> resetState();
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
