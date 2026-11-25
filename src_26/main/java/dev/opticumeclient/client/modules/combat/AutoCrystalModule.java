package dev.opticumeclient.client.modules.combat;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.opticumeclient.client.combat.RotationManager;
import dev.opticumeclient.client.friend.FriendManager;
import dev.opticumeclient.client.modules.Category;
import dev.opticumeclient.client.modules.Module;
import dev.opticumeclient.client.render.ColorUtils;
import dev.opticumeclient.client.render.Render3DHelper;
import dev.opticumeclient.client.render.ThemeManager;
import dev.opticumeclient.client.setting.BooleanSetting;
import dev.opticumeclient.client.setting.ModeSetting;
import dev.opticumeclient.client.setting.NumberSetting;
import dev.opticumeclient.client.util.InventoryUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Automated End Crystal PvP module with accurate exposure-based damage calculation,
 * Silent RotationManager integration, auto-switch, self-damage safety, and 3D target block highlight.
 */
public class AutoCrystalModule extends Module {
    private static AutoCrystalModule INSTANCE;

    private final NumberSetting placeRange = new NumberSetting("PlaceRange", "Дистанция установки кристалла", 4.5, 2.5, 6.0, 0.1);
    private final NumberSetting breakRange = new NumberSetting("BreakRange", "Дистанция подрыва кристалла", 4.5, 2.5, 6.0, 0.1);
    private final NumberSetting minTargetDmg = new NumberSetting("MinDamage", "Минимальный урон по цели", 5.5, 1.0, 20.0, 0.5);
    private final NumberSetting maxSelfDmg = new NumberSetting("MaxSelfDmg", "Максимальный урон по себе", 8.0, 1.0, 20.0, 0.5);
    private final NumberSetting placeDelay = new NumberSetting("PlaceDelay", "Задержка установки (тики)", 2.0, 0.0, 10.0, 1.0);
    private final NumberSetting breakDelay = new NumberSetting("BreakDelay", "Задержка подрыва (тики)", 1.0, 0.0, 10.0, 1.0);
    private final ModeSetting switchMode = new ModeSetting("Switch", "Выбор кристалла в хотбаре", "Silent", "Silent", "Normal", "None");
    private final BooleanSetting silentRotate = new BooleanSetting("SilentRotate", "Скрытый поворот через RotationManager", true);
    private final BooleanSetting pauseOnEat = new BooleanSetting("PauseOnEat", "Пауза при еде и зельях", true);
    private final BooleanSetting antiSuicide = new BooleanSetting("AntiSuicide", "Не взрывать при угрозе смерти", true);
    private final BooleanSetting renderPos = new BooleanSetting("Render", "Подсветка позиции установки кристалла", true);

    private int placeTimer = 0;
    private int breakTimer = 0;
    private BlockPos activeRenderPos = null;
    private float activeRenderDmg = 0.0f;

    public AutoCrystalModule() {
        super("AutoCrystal", "Автоматическая установка и подрыв кристаллов Энда с расчётом урона", Category.COMBAT);
        INSTANCE = this;

        addSetting(placeRange);
        addSetting(breakRange);
        addSetting(minTargetDmg);
        addSetting(maxSelfDmg);
        addSetting(placeDelay);
        addSetting(breakDelay);
        addSetting(switchMode);
        addSetting(silentRotate);
        addSetting(pauseOnEat);
        addSetting(antiSuicide);
        addSetting(renderPos);
    }

    public static AutoCrystalModule getInstance() {
        return INSTANCE;
    }

    @Override
    public void onDisable() {
        activeRenderPos = null;
        activeRenderDmg = 0.0f;
        placeTimer = 0;
        breakTimer = 0;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.level == null || mc.gameMode == null) {
            activeRenderPos = null;
            return;
        }

        if (pauseOnEat.isEnabled() && mc.player.isUsingItem()) {
            activeRenderPos = null;
            return;
        }

        if (placeTimer > 0) placeTimer--;
        if (breakTimer > 0) breakTimer--;

        Player target = findBestTarget();
        if (target == null) {
            activeRenderPos = null;
            return;
        }

        // 1. Break existing optimal crystal first
        if (breakTimer <= 0) {
            EndCrystal bestCrystal = findBestCrystalToBreak(target);
            if (bestCrystal != null) {
                if (silentRotate.isEnabled()) {
                    float[] rot = RotationManager.getRotationsTo(mc.player.getEyePosition(), bestCrystal.getBoundingBox().getCenter());
                    RotationManager.getInstance().requestImmediate("AutoCrystal", rot[0], rot[1], 80,
                            RotationManager.MoveFixMode.FREE, true);
                }
                mc.gameMode.attack(mc.player, bestCrystal);
                mc.player.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, false);
                breakTimer = breakDelay.getIntValue();
                return;
            }
        }

        // 2. Place new optimal crystal
        if (placeTimer <= 0) {
            PlaceCandidate bestPlace = findBestPlacePos(target);
            if (bestPlace != null) {
                activeRenderPos = bestPlace.pos();
                activeRenderDmg = bestPlace.targetDmg();

                InteractionHand hand = getCrystalHand();
                int prevSlot = mc.player.getInventory().getSelectedSlot();
                int crystalSlot = -1;

                if (hand == null) {
                    if (switchMode.is("None")) return;
                    crystalSlot = InventoryUtil.searchStack(s -> s.is(Items.END_CRYSTAL), 0, 9);
                    if (crystalSlot == -1) return;
                    InventoryUtil.setCarriedSlot(mc, crystalSlot);
                    hand = InteractionHand.MAIN_HAND;
                }

                Vec3 hitVec = Vec3.atCenterOf(bestPlace.pos()).add(0, 0.5, 0);
                if (silentRotate.isEnabled()) {
                    float[] rot = RotationManager.getRotationsTo(mc.player.getEyePosition(), hitVec);
                    RotationManager.getInstance().requestImmediate("AutoCrystal", rot[0], rot[1], 80,
                            RotationManager.MoveFixMode.FREE, true);
                }

                BlockHitResult bhr = new BlockHitResult(hitVec, Direction.UP, bestPlace.pos(), false);
                mc.gameMode.useItemOn(mc.player, hand, bhr);
                mc.player.swing(hand, net.minecraft.world.item.component.SwingAnimation.DEFAULT, false);

                if (crystalSlot != -1 && switchMode.is("Silent")) {
                    InventoryUtil.setCarriedSlot(mc, prevSlot);
                }
                placeTimer = placeDelay.getIntValue();
            } else {
                activeRenderPos = null;
            }
        }
    }

    @Override
    public void onRender3D(PoseStack matrices, float tickDelta) {
        if (!renderPos.isEnabled() || activeRenderPos == null || mc.player == null) return;
        int accent = ThemeManager.getInstance().getAccentColor(0);
        int outline = ColorUtils.withAlpha(accent, 225);
        AABB box = new AABB(activeRenderPos);
        Render3DHelper.drawBoxColor(box, outline, 1.8f, true);
    }

    private Player findBestTarget() {
        Player best = null;
        double bestDist = 11.0;
        for (Player p : mc.level.players()) {
            if (p == mc.player || !p.isAlive() || p.isSpectator() || p.isCreative()) continue;
            if (FriendManager.getInstance().isFriend(p) || AntiBotModule.isBot(p)) continue;
            double d = mc.player.distanceTo(p);
            if (d < bestDist) {
                bestDist = d;
                best = p;
            }
        }
        return best;
    }

    private EndCrystal findBestCrystalToBreak(Player target) {
        EndCrystal best = null;
        float bestScore = -1.0f;
        double maxDist = breakRange.getValue();

        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof EndCrystal crystal) || !crystal.isAlive()) continue;
            if (mc.player.getEyePosition().distanceTo(crystal.position()) > maxDist) continue;

            float selfDmg = CrystalDamageUtil.calculateCrystalDamage(mc.player, crystal.position());
            if (!isSelfDamageSafe(selfDmg)) continue;

            float targetDmg = CrystalDamageUtil.calculateCrystalDamage(target, crystal.position());
            if (targetDmg < minTargetDmg.getValue() && targetDmg < target.getHealth() + target.getAbsorptionAmount()) {
                continue;
            }

            float score = targetDmg - selfDmg * 0.5f;
            if (score > bestScore) {
                bestScore = score;
                best = crystal;
            }
        }
        return best;
    }

    private record PlaceCandidate(BlockPos pos, float targetDmg, float selfDmg) {}

    private PlaceCandidate findBestPlacePos(Player target) {
        double r = placeRange.getValue();
        int rad = (int) Math.ceil(r);
        BlockPos origin = mc.player.blockPosition();
        Vec3 eyes = mc.player.getEyePosition();

        PlaceCandidate best = null;
        float bestScore = -1.0f;

        for (int dx = -rad; dx <= rad; dx++) {
            for (int dy = -rad; dy <= rad; dy++) {
                for (int dz = -rad; dz <= rad; dz++) {
                    BlockPos pos = origin.offset(dx, dy, dz);
                    if (eyes.distanceTo(Vec3.atCenterOf(pos)) > r) continue;
                    if (!canPlaceCrystal(pos)) continue;

                    Vec3 crystalVec = new Vec3(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5);
                    if (crystalVec.distanceTo(target.position()) > 12.0) continue;

                    float selfDmg = CrystalDamageUtil.calculateCrystalDamage(mc.player, crystalVec);
                    if (!isSelfDamageSafe(selfDmg)) continue;

                    float targetDmg = CrystalDamageUtil.calculateCrystalDamage(target, crystalVec);
                    if (targetDmg < minTargetDmg.getValue() && targetDmg < target.getHealth() + target.getAbsorptionAmount()) {
                        continue;
                    }

                    float score = targetDmg - selfDmg * 0.6f;
                    if (score > bestScore) {
                        bestScore = score;
                        best = new PlaceCandidate(pos, targetDmg, selfDmg);
                    }
                }
            }
        }
        return best;
    }

    private boolean canPlaceCrystal(BlockPos pos) {
        BlockState base = mc.level.getBlockState(pos);
        if (!base.is(Blocks.OBSIDIAN) && !base.is(Blocks.BEDROCK)) return false;

        BlockPos above = pos.above();
        if (!mc.level.getBlockState(above).isAir()) return false;

        AABB checkBox = new AABB(above.getX(), above.getY(), above.getZ(),
                above.getX() + 1.0, above.getY() + 2.0, above.getZ() + 1.0);
        List<Entity> blocking = mc.level.getEntities((Entity) null, checkBox, e -> e.isAlive() && !(e instanceof EndCrystal));
        return blocking.isEmpty();
    }

    private boolean isSelfDamageSafe(float selfDmg) {
        if (selfDmg > maxSelfDmg.getValue()) return false;
        if (antiSuicide.isEnabled()) {
            float effectiveHp = mc.player.getHealth() + mc.player.getAbsorptionAmount();
            if (selfDmg >= effectiveHp - 1.5f) return false;
        }
        return true;
    }

    private InteractionHand getCrystalHand() {
        if (mc.player.getOffhandItem().is(Items.END_CRYSTAL)) return InteractionHand.OFF_HAND;
        if (mc.player.getMainHandItem().is(Items.END_CRYSTAL)) return InteractionHand.MAIN_HAND;
        return null;
    }

    @Override
    public String getDisplaySuffix() {
        return activeRenderPos != null ? String.format("%.1f", activeRenderDmg) : null;
    }
}
