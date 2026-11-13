package dev.optimusprime.client.modules.combat;

import dev.optimusprime.client.modules.Category;
import dev.optimusprime.client.modules.Module;
import net.minecraft.world.entity.player.Player; 
import net.minecraft.tags.ItemTags; 
import net.minecraft.world.item.ItemStack; 
import net.minecraft.world.InteractionHand; 
import net.minecraft.world.phys.EntityHitResult; 
import net.minecraft.world.phys.HitResult; 

public class ShieldBreakerModule extends Module {
    public ShieldBreakerModule() {
        super("ShieldBreaker", "Автоматически сбивает поднятый щит противника топором", Category.COMBAT);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.gameMode == null) return;

        HitResult hit = mc.hitResult;
        if (hit instanceof EntityHitResult entityHit && entityHit.getEntity() instanceof Player target) {
            if (target.isBlocking() && mc.options.keyAttack.isDown()) {
                int axeSlot = findAxeSlot();
                if (axeSlot != -1 && axeSlot != mc.player.getInventory().getSelectedSlot()) {
                    int oldSlot = mc.player.getInventory().getSelectedSlot();
                    mc.player.getInventory().setSelectedSlot(axeSlot);
                    mc.gameMode.attack(mc.player, target);
                    mc.player.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, false);
                    mc.player.getInventory().setSelectedSlot(oldSlot);
                }
            }
        }
    }

    private int findAxeSlot() {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (!stack.isEmpty() && stack.is(ItemTags.AXES)) {
                return i;
            }
        }
        return -1;
    }
}
