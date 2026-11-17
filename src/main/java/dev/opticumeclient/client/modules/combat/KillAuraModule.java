package dev.opticumeclient.client.modules.combat;

import dev.opticumeclient.client.friend.FriendManager;
import dev.opticumeclient.client.modules.Category;
import dev.opticumeclient.client.modules.Module;
import dev.opticumeclient.client.setting.BooleanSetting;
import dev.opticumeclient.client.setting.ModeSetting;
import dev.opticumeclient.client.setting.NumberSetting;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class KillAuraModule extends Module {
    private static KillAuraModule INSTANCE;

    private final ModeSetting preset = new ModeSetting("Preset", "Пресет килауры", "Dexland", "Dexland", "FunTime", "HolyWorld", "Custom");
    private final NumberSetting range = new NumberSetting("Range", "Радиус 360 атаки", 3.35, 2.5, 4.5, 0.05);
    private final BooleanSetting checkCooldown = new BooleanSetting("Cooldown", "Ждать 100% готовности оружия", true);
    private final BooleanSetting pauseOnEat = new BooleanSetting("PauseOnEat", "Пауза при еде и зельях", true);
    private final BooleanSetting onlyPlayers = new BooleanSetting("PlayersOnly", "Атаковать только игроков", true);
    private final BooleanSetting prioritizeLowestHp = new BooleanSetting("LowestHp", "Приоритет цели с наименьшим HP", true);

    private LivingEntity currentTarget = null;
    private String lastPreset = "";

    public KillAuraModule() {
        super("KillAura", "Классическая круговая килаура 360 градусов", Category.COMBAT);
        INSTANCE = this;

        addSetting(preset);
        addSetting(range);
        addSetting(checkCooldown);
        addSetting(pauseOnEat);
        addSetting(onlyPlayers);
        addSetting(prioritizeLowestHp);

        applyPreset("Dexland");
    }

    public static KillAuraModule getInstance() {
        return INSTANCE;
    }

    @Override
    public String getDisplaySuffix() {
        return preset.getMode();
    }

    @Override
    public void onDisable() {
        currentTarget = null;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.level == null) {
            currentTarget = null;
            return;
        }

        // Auto-tuning server presets
        String currentPreset = preset.getMode();
        if (!currentPreset.equalsIgnoreCase(lastPreset)) {
            applyPreset(currentPreset);
        }

        // Do not attack while eating or drinking
        if (pauseOnEat.isEnabled() && mc.player.isUsingItem()) {
            return;
        }

        if (checkCooldown.isEnabled() && mc.player.getAttackStrengthScale(0.0f) < 0.95f) {
            return;
        }

        currentTarget = findBestTarget();
        if (currentTarget != null && mc.gameMode != null) {
            mc.gameMode.attack(mc.player, currentTarget);
            mc.player.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, false);
        }
    }

    public LivingEntity getCurrentTarget() {
        return currentTarget;
    }

    private void applyPreset(String mode) {
        lastPreset = mode;
        switch (mode) {
            case "Dexland" -> {
                range.setValue(3.35);
                checkCooldown.setEnabled(true);
                pauseOnEat.setEnabled(true);
            }
            case "FunTime" -> {
                range.setValue(3.15);
                checkCooldown.setEnabled(true);
                pauseOnEat.setEnabled(true);
            }
            case "HolyWorld" -> {
                range.setValue(3.20);
                checkCooldown.setEnabled(true);
                pauseOnEat.setEnabled(true);
            }
        }
    }

    private LivingEntity findBestTarget() {
        if (mc.player == null || mc.level == null) return null;
        Vec3 eyes = mc.player.getEyePosition();
        LivingEntity best = null;
        double maxDist = range.getValue();
        float minHp = Float.MAX_VALUE;
        double nearestDist = maxDist;

        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof LivingEntity living) || e == mc.player || !living.isAlive()) continue;
            if (onlyPlayers.isEnabled() && !(living instanceof Player)) continue;

            // Never attack friends!
            if (FriendManager.getInstance().isFriend(living)) continue;

            double dist = getDistanceToBox(eyes, living.getBoundingBox());
            if (dist <= maxDist) {
                if (prioritizeLowestHp.isEnabled()) {
                    if (living.getHealth() < minHp) {
                        minHp = living.getHealth();
                        best = living;
                    }
                } else {
                    if (dist < nearestDist) {
                        nearestDist = dist;
                        best = living;
                    }
                }
            }
        }
        return best;
    }

    public static double getDistanceToBox(Vec3 eyePos, AABB box) {
        double cx = Mth.clamp(eyePos.x, box.minX, box.maxX);
        double cy = Mth.clamp(eyePos.y, box.minY, box.maxY);
        double cz = Mth.clamp(eyePos.z, box.minZ, box.maxZ);
        return eyePos.distanceTo(new Vec3(cx, cy, cz));
    }
}
