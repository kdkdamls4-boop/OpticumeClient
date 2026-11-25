package dev.opticumeclient.client.modules.combat;

import dev.opticumeclient.client.combat.RotationManager;
import dev.opticumeclient.client.friend.FriendManager;
import dev.opticumeclient.client.modules.Category;
import dev.opticumeclient.client.modules.Module;
import dev.opticumeclient.client.setting.BooleanSetting;
import dev.opticumeclient.client.setting.ModeSetting;
import dev.opticumeclient.client.setting.NumberSetting;
import dev.opticumeclient.client.util.InventoryUtil;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * Mace PvP combat helper:
 * - MaceAura: auto-swaps to Mace while falling above minFallDistance, aims silently, smashes target, restores slot.
 * - BreachSwap: swaps to Mace right when attacking/falling in range to trigger Breach armor penetration.
 * - WindCharge Combo: auto-throws Wind Charge down when grounded near target to launch into Mace smash.
 */
public class MaceHelperModule extends Module {
    private final ModeSetting mode = new ModeSetting("Mode", "Режим работы с булавой", "MaceAura", "MaceAura", "BreachSwap");
    private final NumberSetting range = new NumberSetting("Range", "Дистанция удара булавой", 3.6, 2.5, 5.5, 0.1);
    private final NumberSetting minFall = new NumberSetting("MinFall", "Минимальная высота падения для смэша", 1.6, 0.5, 8.0, 0.1);
    private final BooleanSetting windChargeCombo = new BooleanSetting("WindCombo", "Авто-бросок заряда ветра под себя для подброса", false);
    private final BooleanSetting silentRotate = new BooleanSetting("SilentRotate", "Скрытый поворот при ударе/подбросе", true);
    private final BooleanSetting swapBack = new BooleanSetting("SwapBack", "Возвращать предыдущий слот после удара", true);
    private final BooleanSetting playersOnly = new BooleanSetting("PlayersOnly", "Атаковать только игроков", true);

    private int cooldownTicks = 0;
    private int windCooldownTicks = 0;

    public MaceHelperModule() {
        super("MaceHelper", "Помощник боя с булавой: MaceAura, BreachSwap и WindCharge-комбо", Category.COMBAT);
        addSetting(mode);
        addSetting(range);
        addSetting(minFall);
        addSetting(windChargeCombo);
        addSetting(silentRotate);
        addSetting(swapBack);
        addSetting(playersOnly);
    }

    @Override
    public String getDisplaySuffix() {
        return mode.getMode();
    }

    @Override
    public void onDisable() {
        cooldownTicks = 0;
        windCooldownTicks = 0;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.level == null || mc.gameMode == null) return;
        if (cooldownTicks > 0) cooldownTicks--;
        if (windCooldownTicks > 0) windCooldownTicks--;

        int maceSlot = InventoryUtil.searchStack(s -> s.is(Items.MACE), 0, 9);
        if (maceSlot == -1) return;

        LivingEntity target = findTarget();
        if (target == null) return;

        // Optional Wind Charge launch when grounded near target
        if (windChargeCombo.isEnabled() && windCooldownTicks <= 0 && mc.player.onGround()) {
            int windSlot = InventoryUtil.searchStack(s -> s.is(Items.WIND_CHARGE), 0, 9);
            if (windSlot != -1 && !mc.player.getCooldowns().isOnCooldown(mc.player.getInventory().getItem(windSlot))) {
                int prev = mc.player.getInventory().getSelectedSlot();
                final int wSlot = windSlot;
                Runnable windAction = () -> {
                    mc.player.jumpFromGround();
                    InventoryUtil.setCarriedSlot(mc, wSlot);
                    mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
                    InventoryUtil.setCarriedSlot(mc, prev);
                };
                if (silentRotate.isEnabled()) {
                    RotationManager.getInstance().runWithSilentAngles("MaceHelper", mc.player.getYRot(), 89.0f,
                            85, RotationManager.MoveFixMode.FREE, true, windAction);
                } else {
                    windAction.run();
                }
                windCooldownTicks = 24;
                return;
            }
        }

        if (cooldownTicks > 0) return;

        boolean canSmash = !mc.player.onGround() && mc.player.fallDistance >= minFall.getValue();
        if (mode.is("MaceAura") && !canSmash) return;
        if (mode.is("BreachSwap") && mc.player.getAttackStrengthScale(0.0f) < 0.9f) return;

        double dist = OpticumeAuraModule.getDistanceToBox(mc.player.getEyePosition(), target.getBoundingBox());
        if (dist > range.getValue()) return;

        int prevSlot = mc.player.getInventory().getSelectedSlot();
        if (silentRotate.isEnabled()) {
            Vec3 aim = target.position().add(0, target.getBbHeight() * 0.65, 0);
            float[] rot = RotationManager.getRotationsTo(mc.player.getEyePosition(), aim);
            RotationManager.getInstance().requestImmediate("MaceHelper", rot[0], rot[1], 85,
                    RotationManager.MoveFixMode.FREE, true);
        }

        if (prevSlot != maceSlot) {
            InventoryUtil.setCarriedSlot(mc, maceSlot);
        }

        mc.gameMode.attack(mc.player, target);
        mc.player.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, false);

        if (swapBack.isEnabled() && prevSlot != maceSlot) {
            InventoryUtil.setCarriedSlot(mc, prevSlot);
        }
        cooldownTicks = mode.is("MaceAura") ? 5 : 10;
    }

    private LivingEntity findTarget() {
        LivingEntity best = null;
        double bestDist = range.getValue() + 1.5;
        Vec3 eyes = mc.player.getEyePosition();

        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof LivingEntity living) || e == mc.player || !living.isAlive()) continue;
            if (playersOnly.isEnabled() && !(living instanceof Player)) continue;
            if (FriendManager.getInstance().isFriend(living) || AntiBotModule.isBot(living)) continue;

            double d = OpticumeAuraModule.getDistanceToBox(eyes, living.getBoundingBox());
            if (d < bestDist) {
                bestDist = d;
                best = living;
            }
        }
        return best;
    }
}
