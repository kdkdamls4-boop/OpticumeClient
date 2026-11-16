package dev.opticumeclient.client.modules.combat;

import dev.opticumeclient.client.friend.FriendManager;
import dev.opticumeclient.client.modules.Category;
import dev.opticumeclient.client.modules.Module;
import dev.opticumeclient.client.setting.BooleanSetting;
import net.minecraft.world.entity.Entity; 
import net.minecraft.world.entity.LivingEntity; 
import net.minecraft.world.InteractionHand; 
import net.minecraft.world.phys.EntityHitResult; 
import net.minecraft.world.phys.HitResult; 

public class TriggerBotModule extends Module {
    private final BooleanSetting checkCooldown = new BooleanSetting("Cooldown", "Ждать 100% готовности оружия", true);

    public TriggerBotModule() {
        super("TriggerBot", "Авто-атака при наведении прицела на цель", Category.COMBAT);
        addSetting(checkCooldown);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.level == null || mc.gameMode == null) return;

        if (checkCooldown.isEnabled() && mc.player.getAttackStrengthScale(0.0f) < 0.95f) {
            return;
        }

        HitResult hit = mc.hitResult;
        if (hit instanceof EntityHitResult entityHit) {
            Entity target = entityHit.getEntity();
            if (target instanceof LivingEntity living && target.isAlive() && target != mc.player) {
                if (!FriendManager.getInstance().isFriend(target)) {
                    mc.gameMode.attack(mc.player, living);
                    mc.player.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, false);
                }
            }
        }
    }
}
