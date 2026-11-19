package dev.opticumeclient.client.modules.movement;

import dev.opticumeclient.client.modules.Category;
import dev.opticumeclient.client.modules.Module;
import dev.opticumeclient.client.setting.BooleanSetting;
import dev.opticumeclient.client.setting.NumberSetting;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.SwingAnimation;

/**
 * Ported from Desire (ElytraSwapModule / ElytraBoosterModule) & Skycore (ElytraHelperModule):
 * - Automatically swaps Chestplate <-> Elytra when toggled or can keep Elytra equipped
 * - Automatically boosts with Firework Rockets at configurable intervals while gliding
 */
public class ElytraHelperModule extends Module {
    private static ElytraHelperModule instance;

    private final BooleanSetting swapOnToggleSetting;
    private final BooleanSetting autoFireworkSetting;
    private final NumberSetting fireworkDelaySecSetting;
    private final BooleanSetting minSpeedOnlySetting;

    private long lastFireworkMs = 0L;

    public ElytraHelperModule() {
        super("ElytraHelper", "Быстрый свап Нагрудник <-> Элитра и авто-фейерверк при полёте на элитрах", Category.MOVEMENT);
        instance = this;

        this.swapOnToggleSetting = addSetting(new BooleanSetting(
                "Swap On Enable",
                "При включении модуля автоматически надевать Элитру, а при выключении возвращать Нагрудник",
                true
        ));
        this.autoFireworkSetting = addSetting(new BooleanSetting(
                "Auto Firework",
                "Автоматически запускать фейерверк из хотбара/инвентаря во время полёта",
                false
        ));
        this.fireworkDelaySecSetting = addSetting(new NumberSetting(
                "Firework Interval (s)",
                "Интервал между авто-запусками фейерверков в секундах",
                2.2f, 0.5f, 6.0f, 0.1f
        ).visibleWhen(autoFireworkSetting::isEnabled));
        this.minSpeedOnlySetting = addSetting(new BooleanSetting(
                "Smart Boost",
                "Использовать фейерверк только при падении скорости полёта",
                true
        ).visibleWhen(autoFireworkSetting::isEnabled));
    }

    public static ElytraHelperModule getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        if (swapOnToggleSetting.isEnabled()) {
            equipElytraOrChestplate(true);
        }
    }

    @Override
    public void onDisable() {
        if (swapOnToggleSetting.isEnabled()) {
            equipElytraOrChestplate(false);
        }
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.level == null || mc.gameMode == null) return;
        if (!autoFireworkSetting.isEnabled() || !mc.player.isFallFlying()) return;

        long now = System.currentTimeMillis();
        long delayMs = Math.round(fireworkDelaySecSetting.getFloatValue() * 1000.0f);
        if (now - lastFireworkMs < delayMs) return;

        if (minSpeedOnlySetting.isEnabled() && mc.player.getDeltaMovement().length() > 1.38) {
            return;
        }

        if (useFireworkRocket()) {
            lastFireworkMs = now;
        }
    }

    private void equipElytraOrChestplate(boolean preferElytra) {
        if (mc.player == null || mc.gameMode == null) return;
        ItemStack chest = mc.player.getItemBySlot(EquipmentSlot.CHEST);
        boolean wearingElytra = chest.is(Items.ELYTRA);
        if (preferElytra && wearingElytra) return;
        if (!preferElytra && !wearingElytra && !chest.isEmpty()) return;

        int targetSlot = -1;
        for (int i = 9; i <= 44; i++) {
            ItemStack st = mc.player.inventoryMenu.getSlot(i).getItem();
            if (st.isEmpty()) continue;
            if (preferElytra && st.is(Items.ELYTRA)) {
                targetSlot = i;
                break;
            } else if (!preferElytra && isChestplate(st)) {
                targetSlot = i;
                break;
            }
        }

        if (targetSlot != -1) {
            int containerId = mc.player.inventoryMenu.containerId;
            mc.gameMode.handleContainerInput(containerId, targetSlot, 0, ContainerInput.PICKUP, mc.player);
            mc.gameMode.handleContainerInput(containerId, 6, 0, ContainerInput.PICKUP, mc.player);
            mc.gameMode.handleContainerInput(containerId, targetSlot, 0, ContainerInput.PICKUP, mc.player);
        }
    }

    private boolean isChestplate(ItemStack st) {
        return st.is(Items.NETHERITE_CHESTPLATE)
                || st.is(Items.DIAMOND_CHESTPLATE)
                || st.is(Items.IRON_CHESTPLATE)
                || st.is(Items.GOLDEN_CHESTPLATE)
                || st.is(Items.CHAINMAIL_CHESTPLATE)
                || st.is(Items.LEATHER_CHESTPLATE);
    }

    private boolean useFireworkRocket() {
        if (mc.player.getOffhandItem().is(Items.FIREWORK_ROCKET)) {
            mc.gameMode.useItem(mc.player, InteractionHand.OFF_HAND);
            mc.player.swing(InteractionHand.OFF_HAND, SwingAnimation.DEFAULT, false);
            return true;
        }

        int prevSlot = mc.player.getInventory().getSelectedSlot();
        for (int i = 0; i < 9; i++) {
            ItemStack st = mc.player.getInventory().getItem(i);
            if (st.is(Items.FIREWORK_ROCKET)) {
                if (i != prevSlot) {
                    mc.player.getInventory().setSelectedSlot(i);
                    if (mc.player.connection != null) {
                        mc.player.connection.send(new ServerboundSetCarriedItemPacket(i));
                    }
                }
                mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
                mc.player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, false);
                if (i != prevSlot) {
                    mc.player.getInventory().setSelectedSlot(prevSlot);
                    if (mc.player.connection != null) {
                        mc.player.connection.send(new ServerboundSetCarriedItemPacket(prevSlot));
                    }
                }
                return true;
            }
        }
        return false;
    }
}
