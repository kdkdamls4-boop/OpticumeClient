package dev.optimusprime.mixin;

import dev.optimusprime.client.modules.ModuleManager;
import dev.optimusprime.client.modules.movement.NoSlowModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.level.block.state.BlockState; 
import net.minecraft.world.level.block.Blocks; 
import net.minecraft.client.Minecraft; 
import net.minecraft.world.entity.Entity; 
import net.minecraft.world.phys.Vec3; 
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(Entity.class)
public abstract class EntityMixin {

    @Inject(method = "makeStuckInBlock", at = @At("HEAD"), cancellable = true)
    private void optimus$noSlowMovement(BlockState state, Vec3 multiplier, CallbackInfo ci) {
        if ((Object) this == Minecraft.getInstance().player) {
            NoSlowModule noSlow = ModuleManager.getInstance().getModule(NoSlowModule.class);
            if (noSlow != null && noSlow.isCobwebEnabled() && state != null && state.is(Blocks.COBWEB)) {
                ci.cancel();
            }
        }
    }
}
