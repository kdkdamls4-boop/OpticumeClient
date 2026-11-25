package dev.opticumeclient.client.modules.combat;

import dev.opticumeclient.client.friend.FriendManager;
import dev.opticumeclient.client.modules.Category;
import dev.opticumeclient.client.modules.Module;
import dev.opticumeclient.client.setting.BooleanSetting;
import dev.opticumeclient.client.setting.NumberSetting;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Adjusts entity collision/pick bounding box expansion for melee targeting,
 * automatically ignoring the local player, friends, and detected bots.
 */
public class HitBoxesModule extends Module {
    private static HitBoxesModule INSTANCE;

    private final NumberSetting playerExpand = new NumberSetting("Players", "Расширение хитбокса игроков", 0.25, 0.0, 1.5, 0.05);
    private final NumberSetting mobExpand = new NumberSetting("Mobs", "Расширение хитбокса мобов", 0.0, 0.0, 1.5, 0.05);
    private final BooleanSetting ignoreFriends = new BooleanSetting("IgnoreFriends", "Не расширять хитбокс друзей", true);

    public HitBoxesModule() {
        super("HitBoxes", "Увеличение размеров хитбоксов игроков и мобов", Category.COMBAT);
        INSTANCE = this;
        addSetting(playerExpand);
        addSetting(mobExpand);
        addSetting(ignoreFriends);
    }

    public static HitBoxesModule getInstance() {
        return INSTANCE;
    }

    public double getExpansionFor(Entity entity) {
        if (!isEnabled() || entity == null || entity == mc.player) return 0.0;
        if (!(entity instanceof LivingEntity) || !entity.isAlive()) return 0.0;
        if (AntiBotModule.isBot(entity)) return 0.0;
        if (ignoreFriends.isEnabled() && FriendManager.getInstance().isFriend(entity)) return 0.0;

        if (entity instanceof Player) {
            return playerExpand.getValue();
        }
        return mobExpand.getValue();
    }

    @Override
    public String getDisplaySuffix() {
        return String.format("%.2f", playerExpand.getValue());
    }
}
