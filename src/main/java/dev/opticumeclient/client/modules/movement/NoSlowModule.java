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
        return mode.is("Grim Safe");
    }

    public boolean isFunTime() {
        return mode.is("FunTime");
    }

    public boolean isHolyWorld() {
        return mode.is("HolyWorld");
    }

    public boolean isMatrix() {
        return mode.is("Matrix");
    }

    public boolean isVanilla() {
        return mode.is("Vanilla");
    }

    public float getActiveSpeedMultiplier(LocalPlayer player) {
        if (!shouldCancelItemSlowdown(player)) return 0.2f;

        float baseSetting = getSpeedMultiplier(); // default 1.0f

        if (isVanilla()) {
            return baseSetting;
        }

        if (isGrimSafe()) {
            // Alternating ticks: even ticks 0.75f, odd ticks 0.2f; sprint prohibited
            if (player.tickCount % 2 == 0) {
                return Math.min(0.75f, baseSetting);
            }
            return 0.2f;
        }

        if (isFunTime()) {
            // Adaptive multiplier (0.68f under speed effect, 0.72f on ground, 0.76f in air); sprint prohibited
            if (player.hasEffect(net.minecraft.world.effect.MobEffects.SPEED)) {
                return Math.min(0.68f, baseSetting);
            }
            return player.onGround() ? Math.min(0.72f, baseSetting) : Math.min(0.76f, baseSetting);
        }

        if (isHolyWorld()) {
            // 2-tick initial warmup pause, then smooth 0.82f; sprint prohibited
            if (player.getTicksUsingItem() <= 2) {
                return 0.2f;
            }
            return Math.min(0.82f, baseSetting);
        }

        if (isMatrix()) {
            // 0.62f on ground, vanilla 0.2f in air; sprint prohibited
            if (player.onGround()) {
                return Math.min(0.62f, baseSetting);
            }
            return 0.2f;
        }

        return baseSetting;
    }

    public boolean canSprintWhileUsingItem() {
        return isEnabled() && isVanilla();
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
