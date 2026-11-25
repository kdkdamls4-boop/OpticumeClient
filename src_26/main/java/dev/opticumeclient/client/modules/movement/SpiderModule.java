package dev.opticumeclient.client.modules.movement;

import dev.opticumeclient.client.combat.RotationManager;
import dev.opticumeclient.client.modules.Category;
import dev.opticumeclient.client.modules.Module;
import dev.opticumeclient.client.setting.ModeSetting;
import dev.opticumeclient.client.setting.NumberSetting;
import dev.opticumeclient.client.util.InventoryUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Wall-climbing module supporting Vanilla climb, Block Place scaffolding climb, and Grim Step pulse climb.
 */
public class SpiderModule extends Module {
    private final ModeSetting mode = new ModeSetting("Mode", "Режим подъёма по стене", "Vanilla", "Vanilla", "Block Place", "Grim Step");
    private final NumberSetting climbSpeed = new NumberSetting("Speed", "Скорость подъёма вверх", 0.22, 0.10, 0.50, 0.02);
    private final NumberSetting placeDelay = new NumberSetting("PlaceDelay", "Задержка установки блока (тики)", 4.0, 1.0, 10.0, 1.0);

    private int tickTimer = 0;

    public SpiderModule() {
        super("Spider", "Подъём по вертикальным стенам (Vanilla / Block Place / Grim Step)", Category.MOVEMENT);
        placeDelay.visibleWhen(() -> mode.is("Block Place"));
        addSetting(mode);
        addSetting(climbSpeed);
        addSetting(placeDelay);
    }

    @Override
    public String getDisplaySuffix() {
        return mode.getMode();
    }

    @Override
    public void onDisable() {
        tickTimer = 0;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.level == null || mc.gameMode == null) return;
        if (!mc.player.horizontalCollision) {
            tickTimer = 0;
            return;
        }
        if (mc.player.onClimbable() || mc.player.isInWater() || mc.player.isFallFlying()) return;

        Vec3 vel = mc.player.getDeltaMovement();
        switch (mode.getMode()) {
            case "Vanilla" -> {
                mc.player.setDeltaMovement(vel.x, climbSpeed.getValue(), vel.z);
                mc.player.resetFallDistance();
            }
            case "Grim Step" -> {
                tickTimer++;
                if (tickTimer % 4 == 0 && vel.y < 0.08) {
                    mc.player.setDeltaMovement(vel.x, 0.36, vel.z);
                    mc.player.setOnGround(true);
                    mc.player.resetFallDistance();
                }
            }
            case "Block Place" -> {
                if (tickTimer > 0) {
                    tickTimer--;
                    return;
                }
                int blockSlot = InventoryUtil.searchStack(s -> s.getItem() instanceof BlockItem, 0, 9);
                if (blockSlot == -1) return;

                BlockPos feet = mc.player.blockPosition();
                // Jump first when grounded so our hitbox clears the cell below before placing
                if (mc.player.onGround()) {
                    mc.player.jumpFromGround();
                    return;
                }

                // Place under the player's cleared feet cell (feet.below()) against an adjacent solid wall
                BlockPos targetCell = feet.below();
                double clearance = mc.player.getY() - (targetCell.getY() + 1.0);
                if (clearance >= 0.05 && mc.level.getBlockState(targetCell).canBeReplaced()) {
                    for (Direction dir : Direction.Plane.HORIZONTAL) {
                        BlockPos wall = targetCell.relative(dir);
                        if (!mc.level.getBlockState(wall).isAir()) {
                            int prev = mc.player.getInventory().getSelectedSlot();
                            Vec3 hitVec = Vec3.atCenterOf(wall).add(
                                    dir.getOpposite().getStepX() * 0.5,
                                    0.0,
                                    dir.getOpposite().getStepZ() * 0.5
                            );
                            float[] rot = RotationManager.getRotationsTo(mc.player.getEyePosition(), hitVec);
                            final int bSlot = blockSlot;
                            final BlockHitResult bhr = new BlockHitResult(hitVec, dir.getOpposite(), wall, false);
                            RotationManager.getInstance().runWithSilentAngles("Spider", rot[0], rot[1], 75,
                                    RotationManager.MoveFixMode.FREE, true, () -> {
                                        InventoryUtil.setCarriedSlot(mc, bSlot);
                                        mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, bhr);
                                        InventoryUtil.setCarriedSlot(mc, prev);
                                    });
                            tickTimer = placeDelay.getIntValue();
                            return;
                        }
                    }
                }
            }
        }
    }
}
