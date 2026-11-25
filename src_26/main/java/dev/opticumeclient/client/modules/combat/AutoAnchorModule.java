package dev.opticumeclient.client.modules.combat;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.opticumeclient.client.combat.RotationManager;
import dev.opticumeclient.client.friend.FriendManager;
import dev.opticumeclient.client.modules.Category;
import dev.opticumeclient.client.modules.Module;
import dev.opticumeclient.client.render.ColorUtils;
import dev.opticumeclient.client.render.Render3DHelper;
import dev.opticumeclient.client.setting.BooleanSetting;
import dev.opticumeclient.client.setting.NumberSetting;
import dev.opticumeclient.client.util.InventoryUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Automated Respawn Anchor PvP module (Overworld & End):
 * Places anchor -> charges with Glowstone -> safely detonates with off-item swap & Silent RotationManager.
 */
public class AutoAnchorModule extends Module {
    private final NumberSetting range = new NumberSetting("Range", "Дистанция установки/подрыва якоря", 4.2, 2.5, 5.5, 0.1);
    private final NumberSetting minTargetDmg = new NumberSetting("MinDamage", "Минимальный урон по цели", 6.0, 1.0, 20.0, 0.5);
    private final NumberSetting maxSelfDmg = new NumberSetting("MaxSelfDmg", "Максимальный урон по себе", 8.0, 1.0, 20.0, 0.5);
    private final NumberSetting delayTicks = new NumberSetting("Delay", "Задержка между шагами (тики)", 2.0, 0.0, 10.0, 1.0);
    private final BooleanSetting silentRotate = new BooleanSetting("SilentRotate", "Скрытый поворот через RotationManager", true);
    private final BooleanSetting antiSuicide = new BooleanSetting("AntiSuicide", "Запретить смертельный подрыв себя", true);
    private final BooleanSetting pauseOnEat = new BooleanSetting("PauseOnEat", "Пауза при еде и зельях", true);

    private int timer = 0;
    private BlockPos activePos = null;

    public AutoAnchorModule() {
        super("AutoAnchor", "Автоматическая установка, зарядка светокамнем и подрыв якорей возрождения", Category.COMBAT);
        addSetting(range);
        addSetting(minTargetDmg);
        addSetting(maxSelfDmg);
        addSetting(delayTicks);
        addSetting(silentRotate);
        addSetting(antiSuicide);
        addSetting(pauseOnEat);
    }

    @Override
    public void onDisable() {
        activePos = null;
        timer = 0;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.level == null || mc.gameMode == null) {
            activePos = null;
            return;
        }
        // Anchors do not explode in the Nether
        if (mc.level.dimension() == Level.NETHER) {
            activePos = null;
            return;
        }
        if (pauseOnEat.isEnabled() && mc.player.isUsingItem()) {
            return;
        }
        if (timer > 0) {
            timer--;
            return;
        }

        Player target = findTarget();
        if (target == null) {
            activePos = null;
            return;
        }

        int anchorSlot = InventoryUtil.searchStack(s -> s.is(Items.RESPAWN_ANCHOR), 0, 9);
        int glowSlot = InventoryUtil.searchStack(s -> s.is(Items.GLOWSTONE), 0, 9);

        // 1. Check for existing Respawn Anchor within range
        BlockPos existingAnchor = findExistingAnchor(target);
        if (existingAnchor != null) {
            activePos = existingAnchor;
            BlockState st = mc.level.getBlockState(existingAnchor);
            int charges = st.hasProperty(RespawnAnchorBlock.CHARGE) ? st.getValue(RespawnAnchorBlock.CHARGE) : 0;

            if (charges <= 0) {
                if (glowSlot != -1) {
                    interactAnchor(existingAnchor, glowSlot);
                    timer = delayTicks.getIntValue();
                }
            } else {
                // Detonate using any non-glowstone hotbar slot
                int detonateSlot = findDetonateSlot(glowSlot);
                if (detonateSlot != -1) {
                    interactAnchor(existingAnchor, detonateSlot);
                    timer = delayTicks.getIntValue();
                }
            }
            return;
        }

        // 2. Place a new Respawn Anchor if both anchor and glowstone are available
        if (anchorSlot == -1 || glowSlot == -1) {
            activePos = null;
            return;
        }

        BlockPos placePos = findBestAnchorPlace(target);
        if (placePos != null) {
            activePos = placePos;
            BlockPos support = placePos.below();
            Vec3 hitVec = Vec3.atCenterOf(support).add(0, 0.5, 0);
            int prevSlot = mc.player.getInventory().getSelectedSlot();
            if (silentRotate.isEnabled()) {
                float[] rot = RotationManager.getRotationsTo(mc.player.getEyePosition(), hitVec);
                RotationManager.getInstance().requestImmediate("AutoAnchor", rot[0], rot[1], 80,
                        RotationManager.MoveFixMode.FREE, true);
            }
            InventoryUtil.setCarriedSlot(mc, anchorSlot);
            mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(hitVec, Direction.UP, support, false));
            mc.player.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, false);
            InventoryUtil.setCarriedSlot(mc, prevSlot);
            timer = delayTicks.getIntValue();
        } else {
            activePos = null;
        }
    }

    @Override
    public void onRender3D(PoseStack matrices, float tickDelta) {
        if (activePos == null || mc.player == null) return;
        Render3DHelper.drawBoxColor(new AABB(activePos), ColorUtils.rgba(255, 168, 48, 220), 1.8f, true);
    }

    private void interactAnchor(BlockPos pos, int slot) {
        int prevSlot = mc.player.getInventory().getSelectedSlot();
        Vec3 hitVec = Vec3.atCenterOf(pos);
        if (silentRotate.isEnabled()) {
            float[] rot = RotationManager.getRotationsTo(mc.player.getEyePosition(), hitVec);
            RotationManager.getInstance().requestImmediate("AutoAnchor", rot[0], rot[1], 80,
                    RotationManager.MoveFixMode.FREE, true);
        }
        InventoryUtil.setCarriedSlot(mc, slot);
        mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
                new BlockHitResult(hitVec, Direction.UP, pos, false));
        mc.player.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, false);
        InventoryUtil.setCarriedSlot(mc, prevSlot);
    }

    private int findDetonateSlot(int glowSlot) {
        for (int i = 0; i < 9; i++) {
            if (i != glowSlot && !mc.player.getInventory().getItem(i).is(Items.GLOWSTONE)) {
                return i;
            }
        }
        return -1;
    }

    private Player findTarget() {
        Player best = null;
        double bestDist = 9.0;
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

    private BlockPos findExistingAnchor(Player target) {
        double r = range.getValue();
        int rad = (int) Math.ceil(r);
        BlockPos origin = mc.player.blockPosition();
        Vec3 eyes = mc.player.getEyePosition();
        BlockPos best = null;
        float bestScore = -1.0f;

        for (int dx = -rad; dx <= rad; dx++) {
            for (int dy = -rad; dy <= rad; dy++) {
                for (int dz = -rad; dz <= rad; dz++) {
                    BlockPos pos = origin.offset(dx, dy, dz);
                    if (!mc.level.getBlockState(pos).is(Blocks.RESPAWN_ANCHOR)) continue;
                    Vec3 center = Vec3.atCenterOf(pos);
                    if (eyes.distanceTo(center) > r) continue;

                    float selfDmg = CrystalDamageUtil.calculateAnchorDamage(mc.player, center);
                    if (!isSelfSafe(selfDmg)) continue;
                    float targetDmg = CrystalDamageUtil.calculateAnchorDamage(target, center);
                    if (targetDmg < minTargetDmg.getValue()) continue;

                    float score = targetDmg - selfDmg * 0.5f;
                    if (score > bestScore) {
                        bestScore = score;
                        best = pos;
                    }
                }
            }
        }
        return best;
    }

    private BlockPos findBestAnchorPlace(Player target) {
        double r = range.getValue();
        int rad = (int) Math.ceil(r);
        BlockPos origin = mc.player.blockPosition();
        Vec3 eyes = mc.player.getEyePosition();
        BlockPos best = null;
        float bestScore = -1.0f;

        for (int dx = -rad; dx <= rad; dx++) {
            for (int dy = -rad; dy <= rad; dy++) {
                for (int dz = -rad; dz <= rad; dz++) {
                    BlockPos pos = origin.offset(dx, dy, dz);
                    Vec3 center = Vec3.atCenterOf(pos);
                    if (eyes.distanceTo(center) > r) continue;
                    if (!mc.level.getBlockState(pos).canBeReplaced()) continue;
                    if (mc.level.getBlockState(pos.below()).isAir()) continue;
                    if (!mc.level.getEntities(null, new AABB(pos)).isEmpty()) continue;

                    float selfDmg = CrystalDamageUtil.calculateAnchorDamage(mc.player, center);
                    if (!isSelfSafe(selfDmg)) continue;
                    float targetDmg = CrystalDamageUtil.calculateAnchorDamage(target, center);
                    if (targetDmg < minTargetDmg.getValue()) continue;

                    float score = targetDmg - selfDmg * 0.6f;
                    if (score > bestScore) {
                        bestScore = score;
                        best = pos;
                    }
                }
            }
        }
        return best;
    }

    private boolean isSelfSafe(float selfDmg) {
        if (selfDmg > maxSelfDmg.getValue()) return false;
        if (antiSuicide.isEnabled()) {
            float hp = mc.player.getHealth() + mc.player.getAbsorptionAmount();
            if (selfDmg >= hp - 1.5f) return false;
        }
        return true;
    }
}
