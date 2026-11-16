package dev.opticumeclient.client.modules.combat;

import dev.opticumeclient.client.friend.FriendManager;
import dev.opticumeclient.client.modules.Category;
import dev.opticumeclient.client.modules.Module;
import net.minecraft.world.entity.player.Player; 
import net.minecraft.world.phys.EntityHitResult; 
import net.minecraft.world.phys.HitResult; 

public class MiddleClickFriendModule extends Module {
    private boolean wasPressed = false;

    public MiddleClickFriendModule() {
        super("MiddleClickFriend", "Добавление в друзья по клику колесиком мыши (MMB)", Category.FRIENDS);
        setEnabled(true);
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;

        boolean isPressed = mc.options.keyPickItem.isDown();
        if (isPressed && !wasPressed) {
            HitResult hit = mc.hitResult;
            if (hit instanceof EntityHitResult entityHit && entityHit.getEntity() instanceof Player player) {
                FriendManager.getInstance().toggleFriend(player.getName().getString());
            }
        }
        wasPressed = isPressed;
    }
}
