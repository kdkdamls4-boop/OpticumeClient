package dev.opticumeclient.mixin;

import dev.opticumeclient.client.modules.optimization.OptimizationCoordinator;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(ItemEntity.class)
public abstract class ItemEntityTickMixin {
    @Unique private boolean opticume$skipFlip;

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void opticume$skipFarRestingItemTick(CallbackInfo ci) {
        ItemEntity self = (ItemEntity) (Object) this;
        if (!OptimizationCoordinator.shouldSkipRestingItemTick(self)) {
            return;
        }
        opticume$skipFlip = !opticume$skipFlip;
        if (opticume$skipFlip) {
            ci.cancel();
        }
    }
}
