package dev.opticumeclient.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.opticumeclient.client.modules.optimization.OptimizationCoordinator;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.client.renderer.entity.state.ItemClusterRenderState;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(ItemEntityRenderer.class)
public abstract class ItemEntityOptMixin {

    @Inject(
            method = "submitMultipleFromCount(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/ItemClusterRenderState;Lnet/minecraft/util/RandomSource;Lnet/minecraft/world/phys/AABB;)V",
            at = @At("HEAD")
    )
    private static void opticume$singleItemCopy(
            PoseStack poseStack,
            SubmitNodeCollector collector,
            int packedLight,
            ItemClusterRenderState state,
            RandomSource random,
            AABB modelBounds,
            CallbackInfo ci) {
        if (OptimizationCoordinator.isSingleItemCopy() && state != null && state.count > 1) {
            state.count = 1;
        }
    }
}
