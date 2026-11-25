package dev.opticumeclient.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.opticumeclient.client.modules.optimization.OptimizationCoordinator;
import dev.opticumeclient.client.modules.visuals.ItemPhysicsModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.client.renderer.entity.state.ItemClusterRenderState;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(ItemEntityRenderer.class)
public abstract class ItemEntityOptMixin {

    @Shadow
    @Final
    private RandomSource random;

    @Inject(
            method = "submit(Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void opticume$itemPhysicsSubmit(
            ItemEntityRenderState state,
            PoseStack poseStack,
            SubmitNodeCollector collector,
            CameraRenderState cameraState,
            CallbackInfo ci) {
        ItemPhysicsModule phys = ItemPhysicsModule.getInstance();
        if (phys == null || !phys.isEnabled() || state == null || state.item.isEmpty()) {
            return;
        }
        poseStack.pushPose();
        AABB box = state.item.getModelBoundingBox();
        float baseOffset = -((float) box.minY) + 0.04f;
        poseStack.translate(0.0f, phys.isFlatMode() ? 0.025f : baseOffset, 0.0f);
        if (phys.isFlatMode()) {
            poseStack.rotate(Axis.XP, (float) Math.toRadians(90.0));
            if (phys.isRandomAngle()) {
                poseStack.rotate(Axis.ZP, state.bobOffset * 6.283185f);
            }
        } else if (phys.isRandomAngle()) {
            poseStack.rotate(Axis.YP, state.bobOffset * 6.283185f);
        }
        ItemEntityRenderer.submitMultipleFromCount(poseStack, collector, state.lightCoords, state, this.random, box);
        poseStack.popPose();
        ci.cancel();
    }

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
