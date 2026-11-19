package dev.opticumeclient.mixin;

import dev.opticumeclient.client.modules.optimization.IRenderStatePool;
import dev.opticumeclient.client.modules.optimization.OptimizationCoordinator;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(BlockEntityRenderDispatcher.class)
public abstract class BlockEntityRenderDispatcherMixin {

    @Inject(method = "tryExtractRenderState", at = @At("HEAD"), cancellable = true)
    private <E extends BlockEntity, S extends BlockEntityRenderState> void opticume$cullFarBlockEntities(
            E blockEntity, float partialTick, ModelFeatureRenderer.CrumblingOverlay crumbling, boolean flag,
            CallbackInfoReturnable<S> cir) {
        if (OptimizationCoordinator.shouldCullBlockEntity(blockEntity)) {
            cir.setReturnValue(null);
        }
    }

    @Redirect(
            method = "tryExtractRenderState",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/blockentity/BlockEntityRenderer;createRenderState()Lnet/minecraft/client/renderer/blockentity/state/BlockEntityRenderState;"
            )
    )
    private BlockEntityRenderState opticume$pooledBlockEntityState(
            BlockEntityRenderer<?, ?> renderer, BlockEntity blockEntity, float partialTick,
            ModelFeatureRenderer.CrumblingOverlay crumbling, boolean flag) {
        if (!OptimizationCoordinator.isPoolingBlockEntityStates() || !(blockEntity instanceof IRenderStatePool pool)) {
            return renderer.createRenderState();
        }
        if (pool.opticume$pooledState(renderer) instanceof BlockEntityRenderState cached) {
            return cached;
        }
        BlockEntityRenderState state = renderer.createRenderState();
        pool.opticume$poolState(renderer, state);
        return state;
    }
}
