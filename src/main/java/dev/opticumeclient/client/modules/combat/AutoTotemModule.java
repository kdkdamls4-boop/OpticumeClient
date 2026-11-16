package dev.opticumeclient.client.modules.combat;

import dev.opticumeclient.client.modules.Category;
import dev.opticumeclient.client.modules.Module;
import dev.opticumeclient.client.setting.NumberSetting;
import net.minecraft.world.item.ItemStack; 
import net.minecraft.world.item.Items; 
import net.minecraft.world.inventory.ContainerInput; 

public class AutoTotemModule extends Module {
    private final NumberSetting healthThreshold = new NumberSetting("Health", "Здоровье для свапа тотема", 16.0, 1.0, 20.0, 1.0);

    public AutoTotemModule() {
        super("AutoTotem", "Автоматически берет тотем бессмертия во вторую руку", Category.COMBAT);
        addSetting(healthThreshold);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.gameMode == null) return;

        ItemStack offhand = mc.player.getOffhandItem();
        if (offhand.is(Items.TOTEM_OF_UNDYING)) return;

        if (mc.player.getHealth() <= healthThreshold.getValue()) {
            int totemSlot = findTotemSlot();
            if (totemSlot != -1) {
                // Swap into offhand slot (45 in player inventory container)
                mc.gameMode.handleContainerInput(mc.player.inventoryMenu.containerId, totemSlot, 0, ContainerInput.PICKUP, mc.player);
                mc.gameMode.handleContainerInput(mc.player.inventoryMenu.containerId, 45, 0, ContainerInput.PICKUP, mc.player);
                mc.gameMode.handleContainerInput(mc.player.inventoryMenu.containerId, totemSlot, 0, ContainerInput.PICKUP, mc.player);
            }
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
