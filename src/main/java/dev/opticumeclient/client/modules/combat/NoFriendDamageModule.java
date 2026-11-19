package dev.opticumeclient.client.modules.combat;

import dev.opticumeclient.client.friend.FriendManager;
import dev.opticumeclient.client.modules.Category;
import dev.opticumeclient.client.modules.Module;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/**
 * Ported from Desire (NoFriendDamageModule):
 * Prevents accidental melee attacks against players added to FriendManager.
 */
public class NoFriendDamageModule extends Module {
    private static NoFriendDamageModule instance;

    public NoFriendDamageModule() {
        super("NoFriendDamage", "Запрещает наносить удары по друзьям из списка друзей", Category.COMBAT);
        instance = this;
    }

    public static NoFriendDamageModule getInstance() {
        return instance;
    }

    public boolean shouldCancelAttack(Entity target) {
        if (!isEnabled() || !(target instanceof Player player)) return false;
        return FriendManager.getInstance().isFriend(player.getName().getString());
    }
}
