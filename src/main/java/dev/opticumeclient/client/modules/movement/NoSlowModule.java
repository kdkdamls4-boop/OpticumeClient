package dev.opticumeclient.client.modules.movement;

import dev.opticumeclient.client.modules.Category;
import dev.opticumeclient.client.modules.Module;
import dev.opticumeclient.client.setting.BooleanSetting;
import dev.opticumeclient.client.setting.ModeSetting;
import dev.opticumeclient.client.setting.NumberSetting;
import net.minecraft.client.player.LocalPlayer; 
import net.minecraft.world.item.BowItem; 
import net.minecraft.world.item.CrossbowItem; 
import net.minecraft.world.item.Item; 
import net.minecraft.world.item.ItemStack; 
import net.minecraft.world.item.ShieldItem; 
import net.minecraft.world.item.ItemUseAnimation; 

public class NoSlowModule extends Module {
    private static NoSlowModule INSTANCE;

    private final ModeSetting mode = new ModeSetting("Mode", "Режим обхода NoSlow", "Grim Safe", "Grim Safe", "FunTime", "HolyWorld", "Matrix", "Vanilla");
    private final BooleanSetting food = new BooleanSetting("Food", "Убирать замедление при еде и зельях", true);
    private final BooleanSetting bow = new BooleanSetting("Bow", "Убирать замедление при натяжении лука/арбалета", true);
    private final BooleanSetting shield = new BooleanSetting("Shield", "Убирать замедление при блокировании щитом", true);
    private final BooleanSetting sneak = new BooleanSetting("Sneak", "Убирать штраф скорости при скрытности", false);
    private final BooleanSetting cobweb = new BooleanSetting("Cobweb", "Убирать замедление в паутине", true);
    private final NumberSetting speedMultiplier = new NumberSetting("Speed", "Множитель скорости при использовании предмета", 1.0, 0.2, 1.0, 0.1);

    public NoSlowModule() {
        super("NoSlow", "Устранение замедления при использовании предметов, скрытности и паутине", Category.MOVEMENT);
        addSetting(mode);
        addSetting(food);
        addSetting(bow);
        addSetting(shield);
        addSetting(sneak);
        addSetting(cobweb);
        addSetting(speedMultiplier);
        INSTANCE = this;
        setEnabled(false);
    }

    public static NoSlowModule getInstance() {
        return INSTANCE;
    }

    @Override
    public String getDisplaySuffix() {
        return mode.getMode();
    }

    public boolean isGrimSafe() {
        return !mode.is("Vanilla");
    }

    public ModeSetting getModeSetting() {
        return mode;
    }

    public boolean shouldCancelItemSlowdown(LocalPlayer player) {
        if (!isEnabled() || player == null) return false;

        ItemStack active = player.getActiveItem();
        if (active.isEmpty()) return false;

        Item item = active.getItem();
        ItemUseAnimation action = active.getUseAnimation();

        if (action == ItemUseAnimation.EAT || action == ItemUseAnimation.DRINK) {
            return food.isEnabled();
        }
        if (action == ItemUseAnimation.BOW || action == ItemUseAnimation.CROSSBOW || item instanceof BowItem || item instanceof CrossbowItem) {
            return bow.isEnabled();
        }
        if (action == ItemUseAnimation.BLOCK || item instanceof ShieldItem) {
            return shield.isEnabled();
        }

        return true;
    }

    public boolean isSneakSlowdownDisabled() {
        return isEnabled() && sneak.isEnabled();
    }

    public boolean isCobwebEnabled() {
        return isEnabled() && cobweb.isEnabled();
    }

    public float getSpeedMultiplier() {
        return speedMultiplier.getFloatValue();
    }
}
