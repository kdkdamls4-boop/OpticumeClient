package dev.opticumeclient.client.modules.movement;

import dev.opticumeclient.client.combat.RotationManager;
import dev.opticumeclient.client.modules.Category;
import dev.opticumeclient.client.modules.Module;
import dev.opticumeclient.client.setting.BooleanSetting;
import dev.opticumeclient.client.setting.ModeSetting;
import dev.opticumeclient.client.setting.NumberSetting;
import dev.opticumeclient.client.util.InventoryUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Fall-damage mitigation module:
 * - Packet Ground: flags outgoing ServerboundMovePlayerPacket as onGround=true when fallDistance > threshold.
 * - Water Bucket: performs silent MLG water placement right before landing and picks the water back up.
 * - Cancel: resets fall distance and damps downward speed near impact.
 */
public class NoFallModule extends Module {
    private static NoFallModule INSTANCE;

    private final ModeSetting mode = new ModeSetting("Mode", "Метод защиты от урона падения", "Packet Ground", "Packet Ground", "Water Bucket", "Cancel");
    private final NumberSetting minFall = new NumberSetting("MinFall", "Минимальная высота падения", 3.0, 2.0, 10.0, 0.5);
    private final BooleanSetting pickupWater = new BooleanSetting("PickupWater", "Автоматически забирать воду обратно после MLG", true);

    private BlockPos placedWaterPos = null;
    private int pickupDelay = 0;
    private boolean cancelSuppressActive = false;

    public NoFallModule() {
        super("NoFall", "Защита от урона при падении (Packet Ground / Silent MLG / Cancel)", Category.MOVEMENT);
        INSTANCE = this;
        pickupWater.visibleWhen(() -> mode.is("Water Bucket"));
        addSetting(mode);
        addSetting(minFall);
        addSetting(pickupWater);
    }

    public static NoFallModule getInstance() {
        return INSTANCE;
    }

    @Override
    public String getDisplaySuffix() {
        return mode.getMode();
    }

    @Override
    public void onDisable() {
        placedWaterPos = null;
        pickupDelay = 0;
        cancelSuppressActive = false;
    }

    public boolean shouldForceGroundPacket() {
        if (!isEnabled() || mc.player == null || mc.player.isFallFlying() || mc.player.isInWater()) {
            return false;
        }
        if (mode.is("Packet Ground")) {
            return mc.player.fallDistance >= minFall.getValue();
        }
        if (mode.is("Cancel")) {
            return cancelSuppressActive || mc.player.fallDistance >= minFall.getValue();
        }
        return false;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.level == null || mc.gameMode == null) return;

        // Pickup MLG water once landed safely
        if (placedWaterPos != null) {
            if (pickupDelay > 0) {
                pickupDelay--;
            } else if (pickupWater.isEnabled()) {
                int bucketSlot = InventoryUtil.searchStack(s -> s.is(Items.BUCKET), 0, 9);
                if (bucketSlot != -1) {
                    int prev = mc.player.getInventory().getSelectedSlot();
                    Vec3 target = Vec3.atCenterOf(placedWaterPos);
                    float[] rot = RotationManager.getRotationsTo(mc.player.getEyePosition(), target);
                    final int bSlot = bucketSlot;
                    RotationManager.getInstance().runWithSilentAngles("NoFall", rot[0], rot[1], 95,
                            RotationManager.MoveFixMode.FREE, true, () -> {
                                InventoryUtil.setCarriedSlot(mc, bSlot);
                                mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
                                InventoryUtil.setCarriedSlot(mc, prev);
                            });
                }
                placedWaterPos = null;
            } else {
                placedWaterPos = null;
            }
            return;
        }

        if (mc.player.onGround() || mc.player.isFallFlying() || mc.player.isInWater()) {
            cancelSuppressActive = false;
            return;
        }
        if (mc.player.fallDistance < minFall.getValue() && !cancelSuppressActive) return;

        if (mode.is("Cancel")) {
            cancelSuppressActive = true;
            Vec3 vel = mc.player.getDeltaMovement();
            if (vel.y < -0.5) {
                mc.player.setDeltaMovement(vel.x, -0.08, vel.z);
                mc.player.resetFallDistance();
            }
            return;
        }

        if (mode.is("Water Bucket")) {
            int waterSlot = InventoryUtil.searchStack(s -> s.is(Items.WATER_BUCKET), 0, 9);
            if (waterSlot == -1) return;

            Vec3 eyes = mc.player.getEyePosition();
            Vec3 end = eyes.add(0, -4.6, 0);
            HitResult hit = mc.level.clip(new ClipContext(
                    eyes, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
            if (hit instanceof BlockHitResult bhr && bhr.getType() == HitResult.Type.BLOCK) {
                double distToGround = mc.player.getY() - bhr.getLocation().y;
                if (distToGround <= 2.4 && distToGround >= 0.1) {
                    int prev = mc.player.getInventory().getSelectedSlot();
                    final int wSlot = waterSlot;
                    final BlockHitResult placeHit = new BlockHitResult(bhr.getLocation(), Direction.UP, bhr.getBlockPos(), false);
                    final BlockPos waterTargetPos = bhr.getBlockPos().above();
                    RotationManager.getInstance().runWithSilentAngles("NoFall", mc.player.getYRot(), 89.5f, 100,
                            RotationManager.MoveFixMode.FREE, true, () -> {
                                InventoryUtil.setCarriedSlot(mc, wSlot);
                                InteractionResult res = mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, placeHit);
                                if (res == null || !res.consumesAction()) {
                                    res = mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
                                }
                                InventoryUtil.setCarriedSlot(mc, prev);
                                if (res != null && res.consumesAction()) {
                                    placedWaterPos = waterTargetPos;
                                    pickupDelay = 5;
                                }
                            });
                }
            }
        }
    }
}
