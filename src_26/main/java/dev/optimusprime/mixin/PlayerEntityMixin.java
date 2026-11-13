package dev.optimusprime.mixin;

import dev.optimusprime.client.modules.ModuleManager;
import dev.optimusprime.client.modules.movement.SprintModule;
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

@Environment(EnvType.CLIENT)
@Mixin(Player.class)
public abstract class PlayerEntityMixin {

    @Unique
    private Vec3 optimus$savedVelocity;

    @Unique
    private boolean optimus$savedSprinting;

    @Inject(method = "causeExtraKnockback", at = @At("HEAD"))
    private void optimus$beforeKnockbackTarget(Entity target, float strength, Vec3 velocity, net.minecraft.world.damagesource.DamageSource damageSource, float yaw, boolean isCritical, CallbackInfo ci) {
        Player self = (Player) (Object) this;
        if (self == Minecraft.getInstance().player) {
            optimus$savedVelocity = self.getDeltaMovement();
            optimus$savedSprinting = self.isSprinting();
        }
    }

    @Inject(method = "causeExtraKnockback", at = @At("TAIL"))
    private void optimus$afterKnockbackTarget(Entity target, float strength, Vec3 velocity, net.minecraft.world.damagesource.DamageSource damageSource, float yaw, boolean isCritical, CallbackInfo ci) {
        Player self = (Player) (Object) this;
        if (self == Minecraft.getInstance().player && optimus$savedVelocity != null) {
            SprintModule sprint = ModuleManager.getInstance().getModule(SprintModule.class);
            if (sprint != null && sprint.isKeepSprint()) {
                self.setDeltaMovement(optimus$savedVelocity);
                self.setSprinting(optimus$savedSprinting);
            }
            optimus$savedVelocity = null;
        }
    }
}
