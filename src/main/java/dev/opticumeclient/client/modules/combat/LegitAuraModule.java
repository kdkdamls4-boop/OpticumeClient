package dev.opticumeclient.client.modules.combat;

import com.mojang.blaze3d.vertex.PoseStack;
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

public class LegitAuraModule extends Module {
    private static LegitAuraModule INSTANCE;

    private final ModeSetting preset = new ModeSetting("Preset", "Пресет легитной ауры", "Dexland", "Dexland", "FunTime", "HolyWorld", "Custom");
    private final NumberSetting range = new NumberSetting("Range", "Радиус атаки", 3.25, 2.0, 4.0, 0.05);
    private final NumberSetting preAimRange = new NumberSetting("PreAimRange", "Радиус захвата цели", 4.2, 2.5, 5.5, 0.1);
    private final NumberSetting fov = new NumberSetting("FOV", "Угол обзора для атаки", 80.0, 20.0, 180.0, 5.0);
    private final NumberSetting smooth = new NumberSetting("Smooth", "Сглаживание прицеливания", 4.0, 1.0, 15.0, 0.5);
    private final BooleanSetting checkCooldown = new BooleanSetting("Cooldown", "Ждать 100% кулдауна оружия", true);
    private final BooleanSetting pauseOnEat = new BooleanSetting("PauseOnEat", "Пауза при еде и зельях", true);
    private final BooleanSetting onlyPlayers = new BooleanSetting("PlayersOnly", "Атаковать только игроков", true);

    private LivingEntity target = null;
    private String lastPreset = "";
    private long lastRenderTime = 0L;

    public LegitAuraModule() {
        super("LegitAura", "Легитная аура с проверкой FOV и кулдауна", Category.COMBAT);
        INSTANCE = this;

        addSetting(preset);
        addSetting(range);
        addSetting(preAimRange);
        addSetting(fov);
        addSetting(smooth);
        addSetting(checkCooldown);
        addSetting(pauseOnEat);
        addSetting(onlyPlayers);

        applyPreset("Dexland");
    }

    public static LegitAuraModule getInstance() {
        return INSTANCE;
    }

    @Override
    public String getDisplaySuffix() {
        return preset.getMode();
    }

    public LivingEntity getTarget() {
        return target;
    }

    @Override
    public void onDisable() {
        target = null;
        lastRenderTime = 0L;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.level == null) {
            target = null;
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

        target = findTarget();
        if (target == null) {
            return;
        }

        // Smooth aim is called continuously so crosshair is locked onto target BEFORE cooldown finishes!
        smoothAim(target, 1.0f);

        // Check weapon cooldown threshold
        if (checkCooldown.isEnabled() && mc.player.getAttackStrengthScale(0.0f) < 0.95f) {
            return;
        }

        // Distance check: eye to target bounding box
        double eyeDist = getDistanceToBox(mc.player.getEyePosition(), target.getBoundingBox());
        if (eyeDist <= range.getValue() && mc.gameMode != null) {
            mc.gameMode.attack(mc.player, target);
            mc.player.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, false);
        }
    }

    @Override
    public void onRender3D(PoseStack matrices, float tickDelta) {
        // High refresh rate smooth aim interpolation (240+ FPS)
        if (mc.player != null && target != null && target.isAlive()) {
            long now = System.nanoTime();
            float dt = lastRenderTime > 0L ? (now - lastRenderTime) / 1_000_000_000.0f : 0.0166f;
            lastRenderTime = now;
            float deltaFactor = Math.clamp(dt, 0.0005f, 0.05f) * 20.0f;
            smoothAim(target, deltaFactor * 0.25f);
        } else {
            lastRenderTime = 0L;
        }
    }

    private void applyPreset(String mode) {
        lastPreset = mode;
        switch (mode) {
            case "Dexland" -> {
                range.setValue(3.25);
                preAimRange.setValue(4.2);
                fov.setValue(80.0);
                smooth.setValue(4.0);
                checkCooldown.setEnabled(true);
                pauseOnEat.setEnabled(true);
            }
            case "FunTime" -> {
                range.setValue(3.10);
                preAimRange.setValue(3.9);
                fov.setValue(70.0);
                smooth.setValue(5.5);
                checkCooldown.setEnabled(true);
                pauseOnEat.setEnabled(true);
            }
            case "HolyWorld" -> {
                range.setValue(3.15);
                preAimRange.setValue(4.0);
                fov.setValue(75.0);
                smooth.setValue(5.0);
                checkCooldown.setEnabled(true);
                pauseOnEat.setEnabled(true);
            }
        }
    }

    private LivingEntity findTarget() {
        if (mc.player == null || mc.level == null) return null;
        Vec3 eyes = mc.player.getEyePosition();
        LivingEntity best = null;
        double bestDist = preAimRange.getValue();

        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof LivingEntity living) || e == mc.player || !living.isAlive()) continue;
            if (onlyPlayers.isEnabled() && !(living instanceof Player)) continue;
            if (FriendManager.getInstance().isFriend(living)) continue;

            double dist = getDistanceToBox(eyes, living.getBoundingBox());
            if (dist <= bestDist && isInFov(living, fov.getValue())) {
                best = living;
                bestDist = dist;
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

    private boolean isInFov(Entity entity, double maxFov) {
        Vec3 diff = entity.position().subtract(mc.player.position());
        double angle = Math.toDegrees(Math.atan2(diff.z, diff.x)) - 90.0;
        double deltaYaw = Mth.wrapDegrees(angle - mc.player.getYRot());
        return Math.abs(deltaYaw) <= maxFov / 2.0;
    }

    private void smoothAim(Entity target, float deltaFactor) {
        Vec3 eyes = mc.player.getEyePosition();
        Vec3 targetCenter = target.position().add(0, target.getBbHeight() * 0.72, 0);
        Vec3 diff = targetCenter.subtract(eyes);

        double diffXZ = Math.sqrt(diff.x * diff.x + diff.z * diff.z);
        float targetYaw = (float) (Math.toDegrees(Math.atan2(diff.z, diff.x)) - 90.0);
        float targetPitch = (float) -Math.toDegrees(Math.atan2(diff.y, diffXZ));

        float deltaYaw = Mth.wrapDegrees(targetYaw - mc.player.getYRot());
        float deltaPitch = Mth.wrapDegrees(targetPitch - mc.player.getXRot());

        float factor = (float) Math.max(1.0, smooth.getValue() / deltaFactor);
        mc.player.setYRot(mc.player.getYRot() + deltaYaw / factor);
        mc.player.setXRot(Mth.clamp(mc.player.getXRot() + deltaPitch / factor, -90.0f, 90.0f));
    }
}
