package dev.opticumeclient.mixin;

import dev.opticumeclient.client.bots.BotContext;
import dev.opticumeclient.client.combat.RotationManager;
import dev.opticumeclient.client.modules.ModuleManager;
import dev.opticumeclient.client.modules.combat.HitBoxesModule;
import dev.opticumeclient.client.modules.movement.NoPushModule;
import dev.opticumeclient.client.modules.movement.NoWebModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(Entity.class)
public abstract class EntityMixin {

    @Unique
    private float opticume$savedMoveYaw;

    @Unique
    private boolean opticume$swappedMoveYaw;

    @Inject(method = "makeStuckInBlock", at = @At("HEAD"), cancellable = true)
    private void opticume$noSlowMovement(BlockState state, Vec3 multiplier, CallbackInfo ci) {
        if (!BotContext.isBackgroundContextActive() && (Object) this == Minecraft.getInstance().player) {
            NoWebModule noWeb = NoWebModule.getInstance();
            if (noWeb != null && noWeb.shouldCancelStuck(state)) {
                ci.cancel();
            }
        }
    }

    @Inject(method = "push(Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"), cancellable = true)
    private void opticume$noPushEntity(Entity entity, CallbackInfo ci) {
        if (!BotContext.isBackgroundContextActive() && (Object) this == Minecraft.getInstance().player) {
            NoPushModule noPush = ModuleManager.getInstance().getModule(NoPushModule.class);
            if (noPush != null && noPush.isEntities()) {
                ci.cancel();
            }
        }
    }

    // MoveFix: align Entity.moveRelative with RotationManager serverYaw
    @Inject(method = "moveRelative", at = @At("HEAD"))
    private void opticume$beforeMoveRelative(float amount, Vec3 relative, CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        if (!BotContext.isBackgroundContextActive() && self == Minecraft.getInstance().player && RotationManager.getInstance().shouldCorrectMove()) {
            opticume$savedMoveYaw = self.getYRot();
            self.setYRot(RotationManager.getInstance().getServerYaw());
            opticume$swappedMoveYaw = true;
        } else {
            opticume$swappedMoveYaw = false;
        }
    }

    @Inject(method = "moveRelative", at = @At("TAIL"))
    private void opticume$afterMoveRelative(float amount, Vec3 relative, CallbackInfo ci) {
        if (opticume$swappedMoveYaw) {
            ((Entity) (Object) this).setYRot(opticume$savedMoveYaw);
            opticume$swappedMoveYaw = false;
        }
    }

    // HitBoxes: expand melee pick radius cleanly without disturbing block/world collision
    @Inject(method = "getPickRadius", at = @At("RETURN"), cancellable = true)
    private void opticume$expandPickRadius(CallbackInfoReturnable<Float> cir) {
        HitBoxesModule hb = HitBoxesModule.getInstance();
        if (hb != null && hb.isEnabled()) {
            double expand = hb.getExpansionFor((Entity) (Object) this);
            if (expand > 0.0) {
                cir.setReturnValue(cir.getReturnValueF() + (float) expand);
            }
        }
    }
}
