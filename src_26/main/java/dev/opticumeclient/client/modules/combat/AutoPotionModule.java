package dev.opticumeclient.client.modules.combat;

import dev.opticumeclient.client.combat.RotationManager;
import dev.opticumeclient.client.modules.Category;
import dev.opticumeclient.client.modules.Module;
import dev.opticumeclient.client.setting.BooleanSetting;
import dev.opticumeclient.client.setting.NumberSetting;
import dev.opticumeclient.client.util.InventoryUtil;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;

/**
 * Automatically throws splash buff and healing potions at the player's feet
 * using RotationManager pitch alignment and hotbar slot restoration.
 */
public class AutoPotionModule extends Module {
    private final BooleanSetting strength = new BooleanSetting("Strength", "Авто-бросок зелья силы", true);
    private final BooleanSetting speed = new BooleanSetting("Speed", "Авто-бросок зелья скорости", true);
    private final BooleanSetting fireRes = new BooleanSetting("FireRes", "Авто-бросок огнестойкости", true);
    private final BooleanSetting healing = new BooleanSetting("Healing", "Авто-бросок зелья исцеления при низком ХП", false);
    private final NumberSetting healHealth = new NumberSetting("HealHP", "Порог здоровья для зелья исцеления", 11.0, 2.0, 18.0, 0.5);
    private final NumberSetting delayTicks = new NumberSetting("Delay", "Задержка между бросками (тики)", 8.0, 2.0, 30.0, 1.0);
    private final BooleanSetting onlyOnGround = new BooleanSetting("OnlyOnGround", "Бросать только стоя на земле", true);
    private final BooleanSetting silentRotate = new BooleanSetting("SilentRotate", "Скрытый наклон камеры вниз через RotationManager", true);

    private int cooldown = 0;

    public AutoPotionModule() {
        super("AutoPotion", "Автоматический бросок взрывных бафф-зелий (Сила, Скорость, Огнестойкость, Хил)", Category.COMBAT);
        healHealth.visibleWhen(healing::isEnabled);

        addSetting(strength);
        addSetting(speed);
        addSetting(fireRes);
        addSetting(healing);
        addSetting(healHealth);
        addSetting(delayTicks);
        addSetting(onlyOnGround);
        addSetting(silentRotate);
    }

    @Override
    public void onDisable() {
        cooldown = 0;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.level == null || mc.gameMode == null) return;
        if (cooldown > 0) {
            cooldown--;
            return;
        }
        if (onlyOnGround.isEnabled() && !mc.player.onGround()) return;
        if (mc.player.isUsingItem()) return;

        int slot = -1;
        if (healing.isEnabled() && mc.player.getHealth() <= healHealth.getValue()) {
            slot = findSplashPotionSlot(MobEffects.INSTANT_HEALTH);
        }
        if (slot == -1 && strength.isEnabled() && !mc.player.hasEffect(MobEffects.STRENGTH)) {
            slot = findSplashPotionSlot(MobEffects.STRENGTH);
        }
        if (slot == -1 && speed.isEnabled() && !mc.player.hasEffect(MobEffects.SPEED)) {
            slot = findSplashPotionSlot(MobEffects.SPEED);
        }
        if (slot == -1 && fireRes.isEnabled() && !mc.player.hasEffect(MobEffects.FIRE_RESISTANCE)) {
            slot = findSplashPotionSlot(MobEffects.FIRE_RESISTANCE);
        }

        if (slot == -1) return;

        int prevSlot = mc.player.getInventory().getSelectedSlot();
        final int targetSlot = slot;
        Runnable throwAction = () -> {
            InventoryUtil.setCarriedSlot(mc, targetSlot);
            mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
            InventoryUtil.setCarriedSlot(mc, prevSlot);
        };

        if (silentRotate.isEnabled()) {
            if (!RotationManager.getInstance().runWithSilentAngles("AutoPotion", mc.player.getYRot(), 88.5f,
                    95, RotationManager.MoveFixMode.FREE, true, throwAction)) {
                return;
            }
        } else {
            float prevPitch = mc.player.getXRot();
            mc.player.setXRot(88.5f);
            try {
                throwAction.run();
            } finally {
                mc.player.setXRot(prevPitch);
            }
        }
        cooldown = delayTicks.getIntValue();
    }

    private int findSplashPotionSlot(Holder<MobEffect> targetEffect) {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.isEmpty() || !stack.is(Items.SPLASH_POTION)) continue;
            PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
            if (contents == null) continue;
            for (MobEffectInstance inst : contents.getAllEffects()) {
                if (inst.getEffect().equals(targetEffect)) {
                    return i;
                }
            }
        }
        return -1;
    }
}
