package dev.optimusprime.mixin;

import dev.optimusprime.client.modules.animations.OldAnimationsModule;
import dev.optimusprime.client.modules.combat.OptimusAuraModule;
import dev.optimusprime.client.modules.visuals.ViewModelModule;
import dev.optimusprime.client.modules.ModuleManager;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft; 
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer; 
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import com.mojang.blaze3d.vertex.PoseStack; 
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack; 
import net.minecraft.tags.ItemTags; 
import net.minecraft.world.entity.HumanoidArm; 
import com.mojang.math.Axis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(FirstPersonHandsAndItemsRenderer.class)
public abstract class HeldItemRendererMixin {

    @Inject(method = "submitArmWithItem", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V", shift = At.Shift.AFTER))
    private void optimus$onTransformArmItem(PlayerRenderState playerRenderState, FirstPersonHandsAndItemsRenderState handState, float partialTicks, float pitch, InteractionHand hand, float swingProgress, ItemStack stack, float equipProgress, PoseStack matrices, SubmitNodeCollector collector, int light, CallbackInfo ci) {
        ViewModelModule vm = ViewModelModule.getInstance();
        if (vm != null && vm.isEnabled()) {
            if (hand == InteractionHand.MAIN_HAND) {
                matrices.translate(vm.getMainX(), vm.getMainY(), vm.getMainZ());
            } else {
                matrices.translate(vm.getOffX(), vm.getOffY(), vm.getOffZ());
            }
        }
    }

    @Inject(method = "applyItemArmAttackTransform", at = @At("HEAD"), cancellable = true)
    private void optimus$onCustomSwing(PoseStack matrices, HumanoidArm arm, float swingProgress, CallbackInfo ci) {
        ViewModelModule vm = ViewModelModule.getInstance();
        if (vm != null && vm.isEnabled() && !vm.getSwingAnim().equals("Vanilla")) {
            vm.applyCustomSwing(matrices, arm, swingProgress);
            ci.cancel();
        }
    }

    @Inject(method = "applyEatTransform", at = @At("TAIL"))
    private void optimus$onCustomEat(PoseStack matrices, float partialTicks, HumanoidArm arm, float equipProgress, int remainingTicks, CallbackInfo ci) {
        ViewModelModule vm = ViewModelModule.getInstance();
        if (vm != null && vm.isEnabled() && vm.isCustomEat()) {
            float scale = vm.getEatScale();
            if (scale != 1.0f) matrices.scale(scale, scale, scale);
            float y = vm.getEatY();
            if (y != 0.0f) matrices.translate(0.0f, y, 0.0f);
        }
    }

    @Inject(method = "applyItemArmTransform", at = @At("TAIL"))
    private void optimus$onApplyEquipOffset(PoseStack matrices, HumanoidArm arm, float equipProgress, CallbackInfo ci) {
        ViewModelModule vm = ViewModelModule.getInstance();
        if (vm != null && vm.isEnabled()) {
            if (vm.isNoEquipReset()) {
                matrices.translate(0.0f, equipProgress * 0.6f, 0.0f);
            }
            if (arm == HumanoidArm.RIGHT) {
                float s = vm.getMainScale();
                if (s != 1.0f) matrices.scale(s, s, s);
                if (vm.getMainRotX() != 0.0f) matrices.rotateDegrees(Axis.XP, vm.getMainRotX());
                if (vm.getMainRotY() != 0.0f) matrices.rotateDegrees(Axis.YP, vm.getMainRotY());
                if (vm.getMainRotZ() != 0.0f) matrices.rotateDegrees(Axis.ZP, vm.getMainRotZ());
            } else {
                float s = vm.getOffScale();
                if (s != 1.0f) matrices.scale(s, s, s);
                if (vm.getOffRotX() != 0.0f) matrices.rotateDegrees(Axis.XP, vm.getOffRotX());
                if (vm.getOffRotY() != 0.0f) matrices.rotateDegrees(Axis.YP, vm.getOffRotY());
                if (vm.getOffRotZ() != 0.0f) matrices.rotateDegrees(Axis.ZP, vm.getOffRotZ());
            }
        }

        OldAnimationsModule oldAnim = OldAnimationsModule.getInstance();
        if (oldAnim != null && oldAnim.isEnabled()) {
            float scale = oldAnim.getItemScale();
            if (scale != 1.0f) {
                matrices.scale(scale, scale, scale);
            }

            Minecraft mc = Minecraft.getInstance();
            OptimusAuraModule aura = OptimusAuraModule.getInstance();
            boolean blockSync = (aura != null && aura.isBlockHitAnimEnabled());

            if (mc.player != null) {
                ItemStack item = mc.player.getMainHandItem();
                // 1.7 Block-Hit animation transform
                if ((oldAnim.isBlockHitEnabled() && mc.player.isUsingItem() && item.is(ItemTags.SWORDS)) || blockSync) {
                    matrices.translate(-0.15f, 0.15f, -0.1f);
                }
            }
        }
    }
}
