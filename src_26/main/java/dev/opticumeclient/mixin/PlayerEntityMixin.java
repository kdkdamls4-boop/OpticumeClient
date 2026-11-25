package dev.opticumeclient.mixin;

import dev.opticumeclient.client.bots.BotContext;
import dev.opticumeclient.client.modules.ModuleManager;
import dev.opticumeclient.client.modules.movement.NoPushModule;
import dev.opticumeclient.client.modules.movement.SprintModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft; 
import net.minecraft.world.entity.Entity; 
import net.minecraft.world.entity.player.Player; 
import net.minecraft.world.phys.Vec3; 
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(Player.class)
public abstract class PlayerEntityMixin {

    @Unique
    private Vec3 opticume$savedVelocity;

    @Unique
    private boolean opticume$savedSprinting;

    @Inject(method = "causeExtraKnockback", at = @At("HEAD"))
    private void opticume$beforeKnockbackTarget(Entity target, float strength, Vec3 velocity, net.minecraft.world.damagesource.DamageSource damageSource, float yaw, boolean isCritical, CallbackInfo ci) {
        Player self = (Player) (Object) this;
        if (!BotContext.isBackgroundContextActive() && self == Minecraft.getInstance().player) {
            opticume$savedVelocity = self.getDeltaMovement();
            opticume$savedSprinting = self.isSprinting();
        }
    }

    @Inject(method = "causeExtraKnockback", at = @At("TAIL"))
    private void opticume$afterKnockbackTarget(Entity target, float strength, Vec3 velocity, net.minecraft.world.damagesource.DamageSource damageSource, float yaw, boolean isCritical, CallbackInfo ci) {
        Player self = (Player) (Object) this;
        if (!BotContext.isBackgroundContextActive() && self == Minecraft.getInstance().player && opticume$savedVelocity != null) {
            SprintModule sprint = ModuleManager.getInstance().getModule(SprintModule.class);
            if (sprint != null && sprint.isKeepSprint()) {
                self.setDeltaMovement(opticume$savedVelocity);
                self.setSprinting(opticume$savedSprinting);
            }
            opticume$savedVelocity = null;
        }
    }

    @Inject(method = "isPushedByFluid", at = @At("HEAD"), cancellable = true)
    private void opticume$noPushFluid(CallbackInfoReturnable<Boolean> cir) {
        if (!BotContext.isBackgroundContextActive() && (Object) this == Minecraft.getInstance().player) {
            NoPushModule noPush = ModuleManager.getInstance().getModule(NoPushModule.class);
            if (noPush != null && noPush.isFluids()) {
                cir.setReturnValue(false);
            }
        }
    }
}
