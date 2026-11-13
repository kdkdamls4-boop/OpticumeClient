package dev.optimusprime.mixin;

import dev.optimusprime.client.modules.optimization.EntityCullingModule;
import dev.optimusprime.client.modules.optimization.FrameSyncModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {

    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private void optimus$shouldRenderEntity(Entity entity, Frustum frustum, double camX, double camY, double camZ, float partialTick, CallbackInfoReturnable<Boolean> cir) {
        EntityCullingModule culling = EntityCullingModule.getInstance();
        if (culling != null && culling.shouldCull(entity)) {
            cir.setReturnValue(false);
            return;
        }

        FrameSyncModule frameSync = FrameSyncModule.getInstance();
        if (frameSync != null && frameSync.shouldCullDynamicEntity(entity, camX, camY, camZ)) {
            cir.setReturnValue(false);
        }
    }
}
