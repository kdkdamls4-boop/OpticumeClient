package dev.opticumeclient.mixin;

import dev.opticumeclient.client.modules.optimization.IRenderStatePool;
import dev.opticumeclient.client.modules.optimization.OptimizationCoordinator;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(EntityRenderer.class)
public abstract class EntityRendererPoolMixin<T extends Entity, S extends EntityRenderState> {

    @Redirect(
            method = "createRenderState(Lnet/minecraft/world/entity/Entity;F)Lnet/minecraft/client/renderer/entity/state/EntityRenderState;",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/entity/EntityRenderer;createRenderState()Lnet/minecraft/client/renderer/entity/state/EntityRenderState;"
            )
    )
    private EntityRenderState opticume$pooledEntityState(EntityRenderer<?, ?> renderer, Entity entity, float partialTick) {
        if (!OptimizationCoordinator.isPoolingEntityStates() || !(entity instanceof IRenderStatePool pool)) {
            return renderer.createRenderState();
        }
        if (pool.opticume$pooledState(renderer) instanceof EntityRenderState cached) {
            return cached;
        }
        EntityRenderState state = renderer.createRenderState();
        pool.opticume$poolState(renderer, state);
        return state;
    }

    @Inject(
            method = "extractNameTags(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/entity/state/EntityRenderState;FDD)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void opticume$cullFarNameTags(T entity, S state, float partialTick, double nameRange, double scoreRange, CallbackInfo ci) {
        if (!OptimizationCoordinator.shouldCullNameTag(entity)) {
            return;
        }
        state.nameTag = null;
        state.scoreText = null;
        state.nameTagAttachment = null;
        ci.cancel();
    }
}
