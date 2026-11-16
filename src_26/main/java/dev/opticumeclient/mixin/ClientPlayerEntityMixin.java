package dev.opticumeclient.mixin;

import dev.opticumeclient.client.modules.ModuleManager;
import dev.opticumeclient.client.modules.movement.NoPushModule;
import dev.opticumeclient.client.modules.movement.NoSlowModule;
import dev.opticumeclient.client.modules.movement.SprintModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.player.LocalPlayer; 
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(LocalPlayer.class)
public abstract class ClientPlayerEntityMixin {

    // 1. NoSlow: override active item speed multiplier (vanilla is 0.2f)
    @Inject(method = "itemUseSpeedMultiplier", at = @At("HEAD"), cancellable = true)
    private void opticume$noSlowItemMultiplier(CallbackInfoReturnable<Float> cir) {
        LocalPlayer self = (LocalPlayer) (Object) this;
        NoSlowModule noSlow = ModuleManager.getInstance().getModule(NoSlowModule.class);
        if (noSlow != null && noSlow.shouldCancelItemSlowdown(self)) {
            if (noSlow.isGrimSafe()) {
                // Alternating ticks to prevent Grim / modern anticheats from resetting consumption
                if (self.tickCount % 2 == 0) {
                    cir.setReturnValue(Math.min(0.75f, noSlow.getSpeedMultiplier()));
                }
            } else {
                cir.setReturnValue(noSlow.getSpeedMultiplier());
            }
        }
    }

    // 2. NoSlow: allow sprinting while using item ONLY in Vanilla mode!
    @Inject(method = "isSlowDueToUsingItem", at = @At("HEAD"), cancellable = true)
    private void opticume$noSlowAllowSprint(CallbackInfoReturnable<Boolean> cir) {
        LocalPlayer self = (LocalPlayer) (Object) this;
        NoSlowModule noSlow = ModuleManager.getInstance().getModule(NoSlowModule.class);
        if (noSlow != null && noSlow.shouldCancelItemSlowdown(self) && !noSlow.isGrimSafe()) {
            cir.setReturnValue(false);
        }
    }

    // 3. NoSlow: sneak without speed penalty
    @Inject(method = "isMovingSlowly", at = @At("HEAD"), cancellable = true)
    private void opticume$noSlowSneak(CallbackInfoReturnable<Boolean> cir) {
        NoSlowModule noSlow = ModuleManager.getInstance().getModule(NoSlowModule.class);
        if (noSlow != null && noSlow.isSneakSlowdownDisabled()) {
            cir.setReturnValue(false);
        }
    }

    // 4. Sprint: prevent vanilla from stopping omni-sprint when moving sideways/backwards
    @Inject(method = "shouldStopRunSprinting", at = @At("HEAD"), cancellable = true)
    private void opticume$omniSprintKeep(CallbackInfoReturnable<Boolean> cir) {
        LocalPlayer self = (LocalPlayer) (Object) this;
        SprintModule sprint = ModuleManager.getInstance().getModule(SprintModule.class);
        if (sprint != null && sprint.isOmni() && self.input != null) {
            boolean anyMove = self.input.getMoveVector().x != 0.0f || self.input.getMoveVector().y != 0.0f;
            if (anyMove && !self.isShiftKeyDown() && !self.horizontalCollision) {
                cir.setReturnValue(false);
            }
        }
    }

    // 5. NoPush: prevent block collision push-out
    @Inject(method = "moveTowardsClosestSpace", at = @At("HEAD"), cancellable = true)
    private void opticume$noPushBlocks(double x, double z, CallbackInfo ci) {
        NoPushModule noPush = ModuleManager.getInstance().getModule(NoPushModule.class);
        if (noPush != null && noPush.isBlocks()) {
            ci.cancel();
        }
    }
}
