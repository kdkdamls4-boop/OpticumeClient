package dev.opticumeclient.mixin;

import dev.opticumeclient.client.bots.BotContext;
import dev.opticumeclient.client.combat.RotationManager;
import dev.opticumeclient.client.modules.ModuleManager;
import dev.opticumeclient.client.modules.movement.NoPushModule;
import dev.opticumeclient.client.modules.movement.NoSlowModule;
import dev.opticumeclient.client.modules.movement.SprintModule;
import dev.opticumeclient.client.modules.visuals.FreeCamModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(LocalPlayer.class)
public abstract class ClientPlayerEntityMixin {

    // 1. NoSlow: override active item speed multiplier (vanilla is 0.2f)
    @Inject(method = "itemUseSpeedMultiplier", at = @At("HEAD"), cancellable = true)
    private void opticume$noSlowItemMultiplier(CallbackInfoReturnable<Float> cir) {
        if (BotContext.isBackgroundContextActive()) return;
        LocalPlayer self = (LocalPlayer) (Object) this;
        NoSlowModule noSlow = ModuleManager.getInstance().getModule(NoSlowModule.class);
        if (noSlow != null && noSlow.shouldCancelItemSlowdown(self)) {
            float multiplier = noSlow.getActiveSpeedMultiplier(self);
            if (multiplier > 0.2f) {
                cir.setReturnValue(multiplier);
            }
        }
    }

    // 2. NoSlow: allow sprinting while using item ONLY in Vanilla mode!
    @Inject(method = "isSlowDueToUsingItem", at = @At("HEAD"), cancellable = true)
    private void opticume$noSlowAllowSprint(CallbackInfoReturnable<Boolean> cir) {
        if (BotContext.isBackgroundContextActive()) return;
        LocalPlayer self = (LocalPlayer) (Object) this;
        NoSlowModule noSlow = ModuleManager.getInstance().getModule(NoSlowModule.class);
        if (noSlow != null && noSlow.shouldCancelItemSlowdown(self) && noSlow.canSprintWhileUsingItem()) {
            cir.setReturnValue(false);
        }
    }

    // 3. NoSlow: sneak without speed penalty
    @Inject(method = "isMovingSlowly", at = @At("HEAD"), cancellable = true)
    private void opticume$noSlowSneak(CallbackInfoReturnable<Boolean> cir) {
        if (BotContext.isBackgroundContextActive()) return;
        NoSlowModule noSlow = ModuleManager.getInstance().getModule(NoSlowModule.class);
        if (noSlow != null && noSlow.isSneakSlowdownDisabled()) {
            cir.setReturnValue(false);
        }
    }

    // 4. Sprint: prevent vanilla from stopping omni-sprint when moving sideways/backwards,
    // and force sprint stop during W-Tap or backwards FREE MoveFix.
    @Inject(method = "shouldStopRunSprinting", at = @At("HEAD"), cancellable = true)
    private void opticume$omniSprintKeep(CallbackInfoReturnable<Boolean> cir) {
        if (BotContext.isBackgroundContextActive()) return;
        LocalPlayer self = (LocalPlayer) (Object) this;
        SprintModule sprint = ModuleManager.getInstance().getModule(SprintModule.class);
        if (RotationManager.getInstance().shouldBlockSprint() && (sprint == null || !sprint.isOmni())) {
            cir.setReturnValue(true);
            return;
        }
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
        if (BotContext.isBackgroundContextActive()) return;
        NoPushModule noPush = ModuleManager.getInstance().getModule(NoPushModule.class);
        if (noPush != null && noPush.isBlocks()) {
            ci.cancel();
        }
    }

    // 6. RotationManager: silent rotation packet spoofing in sendPosition()
    @Inject(method = "sendPosition", at = @At("HEAD"))
    private void opticume$beforeSendPosition(CallbackInfo ci) {
        if (BotContext.isBackgroundContextActive()) return;
        RotationManager.getInstance().onBeforeSendPosition();
    }

    @Inject(method = "sendPosition", at = @At("TAIL"))
    private void opticume$afterSendPosition(CallbackInfo ci) {
        if (BotContext.isBackgroundContextActive()) return;
        RotationManager.getInstance().onAfterSendPosition();
    }

    // 7. FreeCam input freeze + RotationManager FREE MoveFix input rotation
    @ModifyVariable(method = "modifyInput", at = @At("HEAD"), ordinal = 0, argsOnly = true)
    private Vec2 opticume$modifyMoveInput(Vec2 rawInput) {
        if (BotContext.isBackgroundContextActive()) {
            return rawInput;
        }
        FreeCamModule fc = FreeCamModule.getInstance();
        if (fc != null && fc.isActive()) {
            return Vec2.ZERO;
        }
        return RotationManager.getInstance().correctMoveInput(rawInput);
    }
}
