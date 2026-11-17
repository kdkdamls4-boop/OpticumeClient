package dev.opticumeclient.client.modules.combat;

import dev.opticumeclient.client.modules.Category;
import dev.opticumeclient.client.modules.Module;
import dev.opticumeclient.client.setting.BooleanSetting;
import dev.opticumeclient.client.setting.ModeSetting;
import dev.opticumeclient.client.setting.NumberSetting;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class AutoTotemModule extends Module {
    private final ModeSetting mode = new ModeSetting("Mode", "Метод свапа тотема", "Swap", "Swap", "Paced Pickup");
    private final NumberSetting healthThreshold = new NumberSetting("Health", "Здоровье для свапа тотема", 16.0, 1.0, 20.0, 1.0);
    private final BooleanSetting checkCrystals = new BooleanSetting("Crystals", "Брать тотем при наличии рядом EndCrystal (<= 6 блоков)", true);
    private final BooleanSetting checkFall = new BooleanSetting("FallDistance", "Брать тотем при опасном падении", true);
    private final NumberSetting fallDistanceThreshold = new NumberSetting("FallDist", "Высота падения для триггера тотема", 6.0, 3.0, 20.0, 0.5);
    private final BooleanSetting saveGapples = new BooleanSetting("SaveGapples", "Не заменять золотое яблоко/щит во 2-й руке при безопасном ХП", true);
    private final NumberSetting tickDelay = new NumberSetting("Delay", "Задержка тиков между кликами для Paced Pickup", 1.0, 0.0, 5.0, 1.0);

    // Paced state machine
    private enum SwapState { IDLE, PICKUP_TOTEM, PLACE_OFFHAND, RETURN_SLOT }
    private SwapState currentState = SwapState.IDLE;
    private int activeTotemSlot = -1;
    private int delayTimer = 0;

    public AutoTotemModule() {
        super("AutoTotem", "Автоматически берет тотем бессмертия во вторую руку", Category.COMBAT);
        addSetting(mode);
        addSetting(healthThreshold);
        addSetting(checkCrystals);
        addSetting(checkFall);
        addSetting(fallDistanceThreshold);
        addSetting(saveGapples);
        addSetting(tickDelay);
    }

    @Override
    public void onDisable() {
        resetState();
    }

    private void resetState() {
        currentState = SwapState.IDLE;
        activeTotemSlot = -1;
        delayTimer = 0;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.gameMode == null || mc.level == null) {
            resetState();
            return;
        }

        // Advance paced state machine if in progress
        if (currentState != SwapState.IDLE) {
            handlePacedSwap();
            return;
        }

        ItemStack offhand = mc.player.getOffhandItem();
        if (offhand.is(Items.TOTEM_OF_UNDYING)) return;

        // Threat evaluation
        if (shouldEquipTotem()) {
            int totemSlot = findTotemSlot();
            if (totemSlot != -1) {
                if (mode.is("Swap")) {
                    executeInstantSwap(totemSlot);
                } else {
                    activeTotemSlot = totemSlot;
                    currentState = SwapState.PICKUP_TOTEM;
                    delayTimer = 0;
                    handlePacedSwap();
                }
            }
        }
    }

    private boolean shouldEquipTotem() {
        // SaveGapples inhibition check: preserve golden apple or shield in offhand if player is actively consuming/using
        if (saveGapples.isEnabled() && mc.player.isUsingItem() && mc.player.getUsedItemHand() == InteractionHand.OFF_HAND) {
            ItemStack active = mc.player.getActiveItem();
            if (active.is(Items.GOLDEN_APPLE) || active.is(Items.ENCHANTED_GOLDEN_APPLE) || active.is(Items.SHIELD)) {
                // If health is above critical emergency (e.g. > 6 HP) and no crystal nearby, preserve offhand item
                if (mc.player.getHealth() > 6.0f && !isCrystalNearby()) {
                    return false;
                }
            }
        }

        // 1. Health check
        if (mc.player.getHealth() <= healthThreshold.getValue()) {
            return true;
        }

        // 2. Crystal threat check
        if (checkCrystals.isEnabled() && isCrystalNearby()) {
            return true;
        }

        // 3. Fall distance check
        if (checkFall.isEnabled() && isFallDangerous()) {
            return true;
        }

        return false;
    }

    private boolean isCrystalNearby() {
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof EndCrystal && mc.player.distanceTo(entity) <= 6.0f) {
                return true;
            }
        }
        return false;
    }

    private boolean isFallDangerous() {
        if (mc.player.fallDistance <= fallDistanceThreshold.getValue()) return false;
        if (mc.player.isFallFlying() || mc.player.isInWater() || mc.player.hasEffect(MobEffects.SLOW_FALLING)) return false;
        return true;
    }

    private void executeInstantSwap(int totemSlot) {
        // Slot 40 in ContainerInput.SWAP corresponds to Inventory.SLOT_OFFHAND
        mc.gameMode.handleContainerInput(mc.player.inventoryMenu.containerId, totemSlot, 40, ContainerInput.SWAP, mc.player);
    }

    private void handlePacedSwap() {
        if (delayTimer > 0) {
            delayTimer--;
            return;
        }

        int containerId = mc.player.inventoryMenu.containerId;
        switch (currentState) {
            case PICKUP_TOTEM -> {
                mc.gameMode.handleContainerInput(containerId, activeTotemSlot, 0, ContainerInput.PICKUP, mc.player);
                currentState = SwapState.PLACE_OFFHAND;
                delayTimer = tickDelay.getIntValue();
            }
            case PLACE_OFFHAND -> {
                mc.gameMode.handleContainerInput(containerId, 45, 0, ContainerInput.PICKUP, mc.player);
                currentState = SwapState.RETURN_SLOT;
                delayTimer = tickDelay.getIntValue();
            }
            case RETURN_SLOT -> {
                mc.gameMode.handleContainerInput(containerId, activeTotemSlot, 0, ContainerInput.PICKUP, mc.player);
                resetState();
            }
            default -> resetState();
        }
    }

    private int findTotemSlot() {
        // Search inventory slots (9 to 44)
        for (int i = 9; i <= 44; i++) {
            ItemStack stack = mc.player.inventoryMenu.getSlot(i).getItem();
            if (!stack.isEmpty() && stack.is(Items.TOTEM_OF_UNDYING)) {
                return i;
            }
        }
        return -1;
    }
}
