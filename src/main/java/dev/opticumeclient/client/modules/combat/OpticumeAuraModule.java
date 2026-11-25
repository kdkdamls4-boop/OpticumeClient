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
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class OpticumeAuraModule extends Module {
    private static OpticumeAuraModule INSTANCE;

    // Presets & Modes
    private final ModeSetting preset = new ModeSetting("Preset", "Пресет под античит сервера или свой алгоритм", "Dexland", "Dexland", "FunTime", "HolyWorld", "ReallyWorld", "SpookyTime", "Grim 1.21", "Matrix", "Custom");
    private final ModeSetting rotationMode = new ModeSetting("Rotation", "Режим вращения: Silent (серверное без сдвига камеры) или Client", "Silent", "Silent", "Client");
    private final ModeSetting moveFix = new ModeSetting("MoveFix", "Коррекция движения WASD при Silent-ротации", "Free", "Free", "Focus", "Off");
    private final ModeSetting aimMode = new ModeSetting("AimMode", "Алгоритм наводки камеры", "Organic", "Organic", "Matrix", "Snap", "Multipoint", "Neuro (GRU)", "Linear", "None");
    private final ModeSetting sortMode = new ModeSetting("SortPriority", "Приоритет выбора цели", "Distance", "Distance", "Health", "FOV", "HurtTime");
    private final ModeSetting critMode = new ModeSetting("CritMode", "Логика критических ударов", "Smart", "Smart", "OnlyCrits", "EveryCooldown");
    private final ModeSetting sprintReset = new ModeSetting("SprintReset", "Сброс спринта перед ударом для регистрации крита", "Legit + Packet", "Legit + Packet", "Legit W-Tap", "Packet", "None");

    // Sliders
    private final NumberSetting range = new NumberSetting("AttackRange", "Дистанция удара по цели", 3.15, 2.5, 6.0, 0.05);
    private final NumberSetting preAimRange = new NumberSetting("PreAimRange", "Дистанция предварительной наводки", 4.2, 3.0, 6.5, 0.1);
    private final NumberSetting fov = new NumberSetting("FOV", "Максимальный угол захвата цели", 180.0, 15.0, 180.0, 5.0);
    private final NumberSetting yawSpeed = new NumberSetting("YawSpeed", "Скорость поворота по горизонтали", 74.0, 5.0, 180.0, 2.0);
    private final NumberSetting pitchSpeed = new NumberSetting("PitchSpeed", "Скорость поворота по вертикали", 44.0, 5.0, 180.0, 2.0);
    private final NumberSetting cooldownThreshold = new NumberSetting("Cooldown", "Минимальный процент заряда удара", 0.91, 0.50, 1.00, 0.02);
    private final NumberSetting hitboxExpand = new NumberSetting("HitboxMargin", "Допуск проверки луча по хитбоксу", 0.04, 0.0, 0.25, 0.01);

    // Booleans
    private final BooleanSetting rayCastCheck = new BooleanSetting("RayCastCheck", "Бить только когда прицел на хитбоксе цели", true);
    private final BooleanSetting gcdFix = new BooleanSetting("GcdMouseFix", "Выравнивание углов под шаг мыши (обход Grim/Matrix)", true);
    private final BooleanSetting randomJitter = new BooleanSetting("HumanJitter", "Коррелированная микро-доводка без промахов", true);
    private final BooleanSetting velocityPredict = new BooleanSetting("VelocityPredict", "Упреждение движения цели на 1 тик", true);
    private final BooleanSetting lazyAim = new BooleanSetting("LazyAim", "Замедлять доводку, когда прицел уже на хитбоксе", true);
    private final BooleanSetting pauseOnEat = new BooleanSetting("PauseOnEat", "Не бить во время поедания гепла / питья зелий", true);
    private final BooleanSetting shieldBreaker = new BooleanSetting("ShieldBreaker", "Авто-пробитие щита топором из хотбара", true);
    private final BooleanSetting throughWalls = new BooleanSetting("ThroughWalls", "Атаковать сквозь стены", false);
    private final BooleanSetting targetPlayers = new BooleanSetting("Players", "Атаковать игроков", true);
    private final BooleanSetting targetMobs = new BooleanSetting("Mobs", "Атаковать мобов и животных", true);
    private final BooleanSetting ignoreNaked = new BooleanSetting("IgnoreNaked", "Игнорировать голых игроков без брони", false);
    private final BooleanSetting blockHitAnim = new BooleanSetting("BlockHitSync", "Синхронизация анимации блока 1.7", true);

    private final CombatRotationEngine.AimSession aimSession = new CombatRotationEngine.AimSession();
    private LivingEntity target = null;
    private String lastPreset = "";
    private long lastRenderTime = 0L;

    public OpticumeAuraModule() {
        super("OpticumeAura", "Флагманская комбо-аура: криты, сбитие щитов, блок-хит", Category.COMBAT);
        INSTANCE = this;

        // Register settings
        addSetting(preset);
        addSetting(rotationMode);
        moveFix.visibleWhen(() -> rotationMode.is("Silent"));
        addSetting(moveFix);
        addSetting(aimMode);
        addSetting(sortMode);
        addSetting(critMode);
        addSetting(sprintReset);
        addSetting(range);
        addSetting(preAimRange);
        addSetting(fov);
        addSetting(yawSpeed);
        addSetting(pitchSpeed);
        addSetting(cooldownThreshold);
        addSetting(hitboxExpand);
        addSetting(rayCastCheck);
        addSetting(gcdFix);
        addSetting(randomJitter);
        addSetting(velocityPredict);
        addSetting(lazyAim);
        addSetting(pauseOnEat);
        addSetting(shieldBreaker);
        addSetting(throughWalls);
        addSetting(targetPlayers);
        addSetting(targetMobs);
        addSetting(ignoreNaked);
        addSetting(blockHitAnim);

        applyPreset("Dexland");
    }

    public static OpticumeAuraModule getInstance() {
        return INSTANCE;
    }

    @Override
    public String getDisplaySuffix() {
        return preset.getMode();
    }

    public LivingEntity getTarget() {
        return target;
    }

    public boolean isBlockHitAnimEnabled() {
        return isEnabled() && blockHitAnim.isEnabled() && target != null;
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

        // Auto-tuning server presets
        String currentPreset = preset.getMode();
        if (!currentPreset.equalsIgnoreCase(lastPreset)) {
            applyPreset(currentPreset);
        }

        // Do not rotate/attack while eating or drinking to prevent anticheat resetting use
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
        boolean inAttackWindow = mc.player.getAttackStrengthScale(0.0f) >= cooldownThreshold.getFloatValue() - 0.14f;

        if (rotationMode.is("Silent")) {
            updateSilentRotation(target, inAttackWindow);
        } else if (System.nanoTime() - lastRenderTime > 120_000_000L) {
            updateClientRotation(target, 1.0f, inAttackWindow);
        }

        // Pre-Tick Legit W-Tap before LocalPlayer.aiStep() / sendIsSprintingIfNeeded()
        if ((sprintReset.is("Legit W-Tap") || sprintReset.is("Legit + Packet"))
                && mc.player.isSprinting()
                && mc.player.getAttackStrengthScale(0.0f) >= cooldownThreshold.getFloatValue() - 0.04f
                && getDistanceToBox(mc.player.getEyePosition(), target.getBoundingBox()) <= range.getValue() + 0.15
                && isLookingAtTarget(target)
                && canCrit()) {
            RotationManager.getInstance().requestWTap(1);
        }
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.level == null) {
            target = null;
            return;
        }

        // Do not attack while eating or drinking to prevent anticheat resetting use
        if (pauseOnEat.isEnabled() && mc.player.isUsingItem()) {
            return;
        }

        if (target == null || !target.isAlive()) {
            return;
        }

        // Check weapon cooldown threshold
        if (mc.player.getAttackStrengthScale(0.0f) < cooldownThreshold.getFloatValue()) {
            return;
        }

        // Check true 3D Euclidean eye-to-box distance
        double eyeDist = getDistanceToBox(mc.player.getEyePosition(), target.getBoundingBox());
        if (eyeDist > range.getValue()) {
            return;
        }

        // Raycast check: crosshair must be aimed at target hitbox without wall/entity occlusion
        if (!isLookingAtTarget(target)) {
            return;
        }

        // Critical hit timing
        if (!canCrit()) {
            return;
        }

        if (mc.gameMode != null) {
            int originalSlot = mc.player.getInventory().getSelectedSlot();

            // Shield breaker: switch to axe if target is blocking with shield
            if (shieldBreaker.isEnabled() && target.isBlocking()) {
                int axeSlot = findAxeSlot();
                if (axeSlot != -1 && axeSlot != originalSlot) {
                    mc.player.getInventory().setSelectedSlot(axeSlot);
                    if (mc.player.connection != null) {
                        mc.player.connection.send(new ServerboundSetCarriedItemPacket(axeSlot));
                    }
                }
            }

            // Sprint reset before impact for critical hit registration
            applySprintReset();

            // Attack packet
            mc.gameMode.attack(mc.player, target);

            // Arm swing animation
            mc.player.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, false);

            // Micro-shift aim point post-hit to break pattern checks
            aimSession.onHitLanded(target);

            // Restore slot back if we swapped to axe
            if (shieldBreaker.isEnabled() && target.isBlocking() && originalSlot != mc.player.getInventory().getSelectedSlot()) {
                mc.player.getInventory().setSelectedSlot(originalSlot);
                if (mc.player.connection != null) {
                    mc.player.connection.send(new ServerboundSetCarriedItemPacket(originalSlot));
                }
            }
        }
    }

    @Override
    public void onRender3D(PoseStack matrices, float tickDelta) {
        // High refresh rate smooth camera aiming (only in Client rotation mode; Silent runs in onPreTick)
        if (rotationMode.is("Client") && mc.player != null && target != null && target.isAlive()
                && !(pauseOnEat.isEnabled() && mc.player.isUsingItem()) && !aimMode.is("None")) {
            long now = System.nanoTime();
            float dt = lastRenderTime > 0L ? (now - lastRenderTime) / 1_000_000_000.0f : 0.0166f;
            lastRenderTime = now;
            float deltaTicks = Math.clamp(dt, 0.0005f, 0.05f) * 20.0f;
            boolean inAttackWindow = mc.player.getAttackStrengthScale(0.0f) >= cooldownThreshold.getFloatValue() - 0.14f;
            updateClientRotation(target, deltaTicks, inAttackWindow);
        } else {
            lastRenderTime = 0L;
        }
    }

    public void applyPreset(String mode) {
        lastPreset = mode;
        switch (mode) {
            case "ReallyWorld" -> {
                range.setValue(3.18);
                preAimRange.setValue(4.3);
                aimMode.setMode("Matrix");
                critMode.setMode("Smart");
                sprintReset.setMode("Legit + Packet");
                moveFix.setMode("Free");
                yawSpeed.setValue(88.0);
                pitchSpeed.setValue(52.0);
                cooldownThreshold.setValue(0.90);
                hitboxExpand.setValue(0.03);
                velocityPredict.setEnabled(true);
                lazyAim.setEnabled(true);
                rayCastCheck.setEnabled(true);
                gcdFix.setEnabled(true);
                randomJitter.setEnabled(true);
                pauseOnEat.setEnabled(true);
            }
            case "Matrix" -> {
                range.setValue(3.25);
                preAimRange.setValue(4.4);
                aimMode.setMode("Snap");
                critMode.setMode("Smart");
                sprintReset.setMode("Packet");
                moveFix.setMode("Free");
                yawSpeed.setValue(96.0);
                pitchSpeed.setValue(58.0);
                cooldownThreshold.setValue(0.90);
                hitboxExpand.setValue(0.05);
                velocityPredict.setEnabled(true);
                lazyAim.setEnabled(true);
                rayCastCheck.setEnabled(true);
                gcdFix.setEnabled(true);
                randomJitter.setEnabled(true);
                pauseOnEat.setEnabled(true);
            }
            case "Dexland" -> {
                range.setValue(3.15);
                preAimRange.setValue(4.2);
                aimMode.setMode("Organic");
                critMode.setMode("Smart");
                sprintReset.setMode("Legit + Packet");
                moveFix.setMode("Free");
                yawSpeed.setValue(74.0);
                pitchSpeed.setValue(44.0);
                cooldownThreshold.setValue(0.91);
                hitboxExpand.setValue(0.04);
                velocityPredict.setEnabled(true);
                lazyAim.setEnabled(true);
                rayCastCheck.setEnabled(true);
                gcdFix.setEnabled(true);
                randomJitter.setEnabled(true);
                pauseOnEat.setEnabled(true);
            }
            case "HolyWorld" -> {
                range.setValue(3.05);
                preAimRange.setValue(4.0);
                aimMode.setMode("Organic");
                critMode.setMode("Smart");
                sprintReset.setMode("Legit W-Tap");
                moveFix.setMode("Free");
                yawSpeed.setValue(62.0);
                pitchSpeed.setValue(38.0);
                cooldownThreshold.setValue(0.92);
                hitboxExpand.setValue(0.00);
                velocityPredict.setEnabled(true);
                lazyAim.setEnabled(true);
                rayCastCheck.setEnabled(true);
                gcdFix.setEnabled(true);
                randomJitter.setEnabled(true);
                pauseOnEat.setEnabled(true);
            }
            case "FunTime" -> {
                range.setValue(3.02);
                preAimRange.setValue(3.9);
                aimMode.setMode("Multipoint");
                critMode.setMode("Smart");
                sprintReset.setMode("Legit W-Tap");
                moveFix.setMode("Free");
                yawSpeed.setValue(56.0);
                pitchSpeed.setValue(34.0);
                cooldownThreshold.setValue(0.93);
                hitboxExpand.setValue(0.00);
                velocityPredict.setEnabled(true);
                lazyAim.setEnabled(true);
                rayCastCheck.setEnabled(true);
                gcdFix.setEnabled(true);
                randomJitter.setEnabled(true);
                pauseOnEat.setEnabled(true);
            }
            case "SpookyTime" -> {
                range.setValue(3.05);
                preAimRange.setValue(4.0);
                aimMode.setMode("Multipoint");
                critMode.setMode("Smart");
                sprintReset.setMode("Legit W-Tap");
                moveFix.setMode("Free");
                yawSpeed.setValue(58.0);
                pitchSpeed.setValue(36.0);
                cooldownThreshold.setValue(0.92);
                hitboxExpand.setValue(0.00);
                velocityPredict.setEnabled(true);
                lazyAim.setEnabled(true);
                rayCastCheck.setEnabled(true);
                gcdFix.setEnabled(true);
                randomJitter.setEnabled(true);
                pauseOnEat.setEnabled(true);
            }
            case "Grim 1.21" -> {
                range.setValue(3.00);
                preAimRange.setValue(3.8);
                aimMode.setMode("Organic");
                critMode.setMode("Smart");
                sprintReset.setMode("Legit W-Tap");
                moveFix.setMode("Free");
                yawSpeed.setValue(50.0);
                pitchSpeed.setValue(32.0);
                cooldownThreshold.setValue(0.94);
                hitboxExpand.setValue(0.00);
                velocityPredict.setEnabled(true);
                lazyAim.setEnabled(true);
                rayCastCheck.setEnabled(true);
                gcdFix.setEnabled(true);
                randomJitter.setEnabled(true);
                pauseOnEat.setEnabled(true);
            }
        }
    }

    private LivingEntity findTarget() {
        if (mc.player == null || mc.level == null) return null;
        Vec3 eyes = mc.player.getEyePosition();
        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;

        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof LivingEntity living) || e == mc.player || !living.isAlive()) continue;
            if (FriendManager.getInstance().isFriend(e) || AntiBotModule.isBot(e)) continue;

            boolean isPlayer = living instanceof Player;
            if (isPlayer && !targetPlayers.isEnabled()) continue;
            if (!isPlayer && !targetMobs.isEnabled()) continue;

            if (isPlayer && ignoreNaked.isEnabled() && living.getArmorValue() == 0) continue;

            if (!throughWalls.isEnabled() && !mc.player.hasLineOfSight(living)) continue;

            double eyeDist = getDistanceToBox(eyes, living.getBoundingBox());
            if (eyeDist > preAimRange.getValue()) continue;

            Vec3 aimPoint = getCandidateAimPoint(living, aimMode.getMode());
            float[] rotations = calculateRotations(eyes, aimPoint);
            float deltaYaw = Math.abs(Mth.wrapDegrees(rotations[0] - mc.player.getYRot()));
            if (deltaYaw > fov.getValue() / 2.0f) continue;

            double score = switch (sortMode.getMode()) {
                case "Health" -> (double) living.getHealth();
                case "FOV" -> (double) deltaYaw;
                case "HurtTime" -> (double) living.hurtTime;
                default -> eyeDist;
            };

            if (score < bestScore) {
                bestScore = score;
                best = living;
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

    private Vec3 getCandidateAimPoint(LivingEntity entity, String mode) {
        return CombatRotationEngine.computeAimPoint(
                mc,
                entity,
                CombatRotationEngine.RotationStyle.fromString(mode),
                velocityPredict.isEnabled(),
                throughWalls.isEnabled(),
                null
        );
    }

    private Vec3 getTargetAimPoint(LivingEntity entity, String mode) {
        return CombatRotationEngine.computeAimPoint(
                mc,
                entity,
                CombatRotationEngine.RotationStyle.fromString(mode),
                velocityPredict.isEnabled(),
                throughWalls.isEnabled(),
                aimSession
        );
    }

    public static float[] calculateRotations(Vec3 from, Vec3 to) {
        Vec3 diff = to.subtract(from);
        double diffXZ = Math.sqrt(diff.x * diff.x + diff.z * diff.z);
        float yaw = (float) (Math.toDegrees(Math.atan2(diff.z, diff.x)) - 90.0);
        float pitch = (float) -Math.toDegrees(Math.atan2(diff.y, diffXZ));
        return new float[]{yaw, pitch};
    }

    private void updateSilentRotation(LivingEntity target, boolean inAttackWindow) {
        if (target == null || aimMode.is("None")) return;
        CombatRotationEngine.RotationStyle style = CombatRotationEngine.RotationStyle.fromString(aimMode.getMode());
        RotationManager rm = RotationManager.getInstance();
        float currentYaw = rm.getServerYaw();
        float currentPitch = rm.getServerPitch();

        Vec3 aimPoint = getTargetAimPoint(target, aimMode.getMode());
        float[] step = CombatRotationEngine.computeStep(
                mc,
                target,
                style,
                currentYaw,
                currentPitch,
                aimPoint,
                yawSpeed.getFloatValue(),
                pitchSpeed.getFloatValue(),
                1.0f,
                randomJitter.isEnabled(),
                lazyAim.isEnabled(),
                inAttackWindow,
                range.getValue(),
                aimSession
        );

        float reqYaw = currentYaw + step[0];
        float reqPitch = Mth.clamp(currentPitch + step[1], -90.0f, 90.0f);
        float maxYaw = Math.max(yawSpeed.getFloatValue(), Math.abs(step[0]) + 2.0f);
        float maxPitch = Math.max(pitchSpeed.getFloatValue(), Math.abs(step[1]) + 2.0f);
        int holdTicks = (style == CombatRotationEngine.RotationStyle.SNAP) ? 1 : 2;

        rm.request(new RotationManager.RotationRequest(
                "OpticumeAura",
                reqYaw,
                reqPitch,
                60,
                RotationManager.MoveFixMode.fromString(moveFix.getMode()),
                maxYaw,
                maxPitch,
                gcdFix.isEnabled(),
                Math.max(24.0f, yawSpeed.getFloatValue() * 0.72f),
                holdTicks
        ));
    }

    private void updateClientRotation(LivingEntity target, float dtTicks, boolean inAttackWindow) {
        if (target == null || aimMode.is("None") || mc.player == null) return;
        CombatRotationEngine.RotationStyle style = CombatRotationEngine.RotationStyle.fromString(aimMode.getMode());
        float currentYaw = mc.player.getYRot();
        float currentPitch = mc.player.getXRot();

        Vec3 aimPoint = getTargetAimPoint(target, aimMode.getMode());
        float[] step = CombatRotationEngine.computeStep(
                mc,
                target,
                style,
                currentYaw,
                currentPitch,
                aimPoint,
                yawSpeed.getFloatValue(),
                pitchSpeed.getFloatValue(),
                dtTicks,
                randomJitter.isEnabled(),
                lazyAim.isEnabled(),
                inAttackWindow,
                range.getValue(),
                aimSession
        );

        float stepYaw = step[0];
        float stepPitch = step[1];
        if (gcdFix.isEnabled()) {
            float[] q = RotationManager.getInstance().quantizeClientDelta(stepYaw, stepPitch, currentPitch);
            stepYaw = q[0];
            stepPitch = q[1];
        }
        mc.player.setYRot(currentYaw + stepYaw);
        mc.player.setXRot(Mth.clamp(currentPitch + stepPitch, -90.0f, 90.0f));
    }

    private boolean isLookingAtTarget(LivingEntity target) {
        if (!rayCastCheck.isEnabled()) return true;
        Vec3 look = rotationMode.is("Silent")
                ? RotationManager.getInstance().getViewVector()
                : mc.player.getViewVector(1.0f);
        return CombatRotationEngine.raycastTarget(
                mc,
                target,
                look,
                range.getValue(),
                hitboxExpand.getValue(),
                throughWalls.isEnabled()
        );
    }

    private boolean canCrit() {
        String mode = critMode.getMode();
        if ("EveryCooldown".equalsIgnoreCase(mode)) {
            return true;
        }

        boolean isInWaterOrLadder = mc.player.isInWater() || mc.player.isInLava() || mc.player.onClimbable();
        if (isInWaterOrLadder) {
            return true;
        }

        boolean isAirborne = !mc.player.onGround();
        boolean isFalling = isAirborne && mc.player.fallDistance > 0.02f;
        boolean isJumpPressed = mc.options.keyJump.isDown();

        if ("OnlyCrits".equalsIgnoreCase(mode)) {
            return isFalling;
        }

        // "Smart": If jumping or in air, MUST wait for fall. If standing on ground without jumping, attack!
        if (isAirborne || isJumpPressed) {
            return isFalling;
        }
        return true;
    }

    private void applySprintReset() {
        String mode = sprintReset.getMode();
        if ("None".equalsIgnoreCase(mode)) return;

        if (mc.player.isSprinting()) {
            mc.player.setSprinting(false);
        }

        if ("Legit W-Tap".equalsIgnoreCase(mode) || "Legit + Packet".equalsIgnoreCase(mode)) {
            RotationManager.getInstance().requestWTap(1);
        }

        if ("Legit + Packet".equalsIgnoreCase(mode) || "Packet".equalsIgnoreCase(mode)) {
            if (mc.player.connection != null) {
                mc.player.connection.send(new ServerboundPlayerCommandPacket(
                    mc.player,
                    ServerboundPlayerCommandPacket.Action.STOP_SPRINTING
                ));
            }
        }
    }

    private int findAxeSlot() {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (!stack.isEmpty() && stack.is(ItemTags.AXES)) {
                return i;
            }
        }
        return -1;
    }
}
