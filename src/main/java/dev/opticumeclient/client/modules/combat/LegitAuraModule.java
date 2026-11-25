package dev.opticumeclient.client.modules.combat;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.opticumeclient.client.combat.CombatRotationEngine;
import dev.opticumeclient.client.combat.RotationManager;
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

    private final ModeSetting preset = new ModeSetting("Preset", "Пресет легитной ауры", "Dexland", "Dexland", "ReallyWorld", "Matrix", "FunTime", "HolyWorld", "Custom");
    private final ModeSetting rotationMode = new ModeSetting("Rotation", "Режим наводки: Client или Silent", "Client", "Client", "Silent");
    private final ModeSetting moveFix = new ModeSetting("MoveFix", "Коррекция WASD при Silent-ротации", "Free", "Free", "Focus", "Off");
    private final ModeSetting aimMode = new ModeSetting("AimMode", "Алгоритм наводки", "Organic", "Organic", "Matrix", "Neuro (GRU)");
    private final NumberSetting range = new NumberSetting("Range", "Радиус атаки", 3.15, 2.0, 4.0, 0.05);
    private final NumberSetting preAimRange = new NumberSetting("PreAimRange", "Радиус захвата цели", 4.2, 2.5, 5.5, 0.1);
    private final NumberSetting fov = new NumberSetting("FOV", "Угол обзора для атаки", 80.0, 20.0, 180.0, 5.0);
    private final NumberSetting smooth = new NumberSetting("Smooth", "Сглаживание прицеливания", 4.0, 1.0, 15.0, 0.5);
    private final BooleanSetting rayCastCheck = new BooleanSetting("RayCastCheck", "Бить только при попадании луча в хитбокс", true);
    private final BooleanSetting checkCooldown = new BooleanSetting("Cooldown", "Ждать 100% кулдауна оружия", true);
    private final BooleanSetting pauseOnEat = new BooleanSetting("PauseOnEat", "Пауза при еде и зельях", true);
    private final BooleanSetting onlyPlayers = new BooleanSetting("PlayersOnly", "Атаковать только игроков", true);

    private final CombatRotationEngine.AimSession aimSession = new CombatRotationEngine.AimSession();
    private LivingEntity target = null;
    private String lastPreset = "";
    private long lastRenderTime = 0L;

    public LegitAuraModule() {
        super("LegitAura", "Легитная аура с проверкой FOV и кулдауна", Category.COMBAT);
        INSTANCE = this;

        addSetting(preset);
        addSetting(rotationMode);
        moveFix.visibleWhen(() -> rotationMode.is("Silent"));
        addSetting(moveFix);
        addSetting(aimMode);
        addSetting(range);
        addSetting(preAimRange);
        addSetting(fov);
        addSetting(smooth);
        addSetting(rayCastCheck);
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
        aimSession.reset();
        RotationManager.getInstance().resetClientGcdResidual();
    }

    @Override
    public void onPreTick() {
        if (mc.player == null || mc.level == null) {
            target = null;
            aimSession.reset();
            return;
        }

        String currentPreset = preset.getMode();
        if (!currentPreset.equalsIgnoreCase(lastPreset)) {
            applyPreset(currentPreset);
        }

        if (pauseOnEat.isEnabled() && mc.player.isUsingItem()) {
            return;
        }

        target = findTarget();
        if (target == null) {
            aimSession.reset();
            RotationManager.getInstance().resetClientGcdResidual();
            return;
        }

        aimSession.onTargetTick(target);
        if (rotationMode.is("Silent")) {
            updateRotation(target, 1.0f);
        } else if (System.nanoTime() - lastRenderTime > 120_000_000L) {
            updateRotation(target, 1.0f);
        }
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.level == null) {
            target = null;
            return;
        }

        if (pauseOnEat.isEnabled() && mc.player.isUsingItem()) {
            return;
        }

        if (target == null || !target.isAlive()) {
            return;
        }

        // Check weapon cooldown threshold
        if (checkCooldown.isEnabled() && mc.player.getAttackStrengthScale(0.0f) < 0.95f) {
            return;
        }

        // Distance check: eye to target bounding box
        double eyeDist = getDistanceToBox(mc.player.getEyePosition(), target.getBoundingBox());
        if (eyeDist > range.getValue()) {
            return;
        }

        if (rayCastCheck.isEnabled()) {
            Vec3 look = rotationMode.is("Silent")
                    ? RotationManager.getInstance().getViewVector()
                    : mc.player.getViewVector(1.0f);
            if (!CombatRotationEngine.raycastTarget(mc, target, look, range.getValue(), 0.0, false)) {
                return;
            }
        }

        if (mc.gameMode != null) {
            mc.gameMode.attack(mc.player, target);
            mc.player.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, false);
            aimSession.onHitLanded(target);
        }
    }

    @Override
    public void onRender3D(PoseStack matrices, float tickDelta) {
        // High refresh rate smooth aim interpolation (Client mode only)
        if (rotationMode.is("Client") && mc.player != null && target != null && target.isAlive()
                && !(pauseOnEat.isEnabled() && mc.player.isUsingItem())) {
            long now = System.nanoTime();
            float dt = lastRenderTime > 0L ? (now - lastRenderTime) / 1_000_000_000.0f : 0.0166f;
            lastRenderTime = now;
            float deltaTicks = Math.clamp(dt, 0.0005f, 0.05f) * 20.0f;
            updateRotation(target, deltaTicks);
        } else {
            lastRenderTime = 0L;
        }
    }

    private void applyPreset(String mode) {
        lastPreset = mode;
        switch (mode) {
            case "ReallyWorld" -> {
                aimMode.setMode("Matrix");
                range.setValue(3.15);
                preAimRange.setValue(4.2);
                fov.setValue(85.0);
                smooth.setValue(3.5);
                rayCastCheck.setEnabled(true);
                checkCooldown.setEnabled(true);
                pauseOnEat.setEnabled(true);
            }
            case "Matrix" -> {
                aimMode.setMode("Matrix");
                range.setValue(3.20);
                preAimRange.setValue(4.3);
                fov.setValue(90.0);
                smooth.setValue(3.0);
                rayCastCheck.setEnabled(true);
                checkCooldown.setEnabled(true);
                pauseOnEat.setEnabled(true);
            }
            case "Dexland" -> {
                aimMode.setMode("Organic");
                range.setValue(3.15);
                preAimRange.setValue(4.2);
                fov.setValue(80.0);
                smooth.setValue(4.0);
                rayCastCheck.setEnabled(true);
                checkCooldown.setEnabled(true);
                pauseOnEat.setEnabled(true);
            }
            case "FunTime" -> {
                aimMode.setMode("Organic");
                range.setValue(3.02);
                preAimRange.setValue(3.9);
                fov.setValue(70.0);
                smooth.setValue(5.5);
                rayCastCheck.setEnabled(true);
                checkCooldown.setEnabled(true);
                pauseOnEat.setEnabled(true);
            }
            case "HolyWorld" -> {
                aimMode.setMode("Organic");
                range.setValue(3.05);
                preAimRange.setValue(4.0);
                fov.setValue(75.0);
                smooth.setValue(5.0);
                rayCastCheck.setEnabled(true);
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
            if (FriendManager.getInstance().isFriend(living) || AntiBotModule.isBot(living)) continue;
            if (!mc.player.hasLineOfSight(living)) continue;

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

    private void updateRotation(LivingEntity target, float dtTicks) {
        if (mc.player == null || target == null) return;
        CombatRotationEngine.RotationStyle style = CombatRotationEngine.RotationStyle.fromString(aimMode.getMode());
        float maxYaw = (float) Math.max(10.0, 140.0 / Math.max(1.0, smooth.getValue()));
        float maxPitch = maxYaw * 0.78f;
        Vec3 aimPoint = CombatRotationEngine.computeAimPoint(mc, target, style, true, false, aimSession);
        boolean inWindow = !checkCooldown.isEnabled() || mc.player.getAttackStrengthScale(0.0f) >= 0.85f;

        if (rotationMode.is("Silent")) {
            RotationManager rm = RotationManager.getInstance();
            float curY = rm.getServerYaw();
            float curP = rm.getServerPitch();
            float[] step = CombatRotationEngine.computeStep(
                    mc, target, style, curY, curP, aimPoint,
                    maxYaw, maxPitch, 1.0f, true, true, inWindow, range.getValue(), aimSession
            );
            rm.request(
                    "LegitAura",
                    curY + step[0],
                    Mth.clamp(curP + step[1], -90.0f, 90.0f),
                    45,
                    RotationManager.MoveFixMode.fromString(moveFix.getMode()),
                    Math.max(maxYaw, Math.abs(step[0]) + 2.0f),
                    Math.max(maxPitch, Math.abs(step[1]) + 2.0f),
                    true
            );
            return;
        }

        float curY = mc.player.getYRot();
        float curP = mc.player.getXRot();
        float[] step = CombatRotationEngine.computeStep(
                mc, target, style, curY, curP, aimPoint,
                maxYaw, maxPitch, dtTicks, true, true, inWindow, range.getValue(), aimSession
        );
        float[] q = RotationManager.getInstance().quantizeClientDelta(step[0], step[1], curP);
        mc.player.setYRot(curY + q[0]);
        mc.player.setXRot(Mth.clamp(curP + q[1], -90.0f, 90.0f));
    }
}
