package dev.opticumeclient.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.opticumeclient.client.modules.combat.OpticumeAuraModule;
import dev.opticumeclient.client.modules.visuals.HandsModule;
import dev.opticumeclient.client.modules.visuals.SwingAnimationsModule;
import dev.opticumeclient.client.modules.visuals.ViewModelModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(FirstPersonHandsAndItemsRenderer.class)
public abstract class HeldItemRendererMixin {

    @Shadow
    @Final
    private Minecraft minecraft;

    @Shadow
    protected abstract void renderPlayerArm(
            PoseStack poseStack,
            SubmitNodeCollector collector,
            int light,
            float equipProgress,
            float swingProgress,
            HumanoidArm arm,
            PlayerRenderState playerRenderState
    );

    @Unique
    private boolean opticume$holdMyItemsLeft;

    @Unique
    private float opticume$holdMyItemsPrevSwingProgress;

    @Inject(method = "submitHandsWithItems", at = @At("HEAD"))
    private void opticume$onSubmitHandsBeginCapture(
            float partialTicks,
            PoseStack poseStack,
            SubmitNodeCollector collector,
            PlayerRenderState playerRenderState,
            FirstPersonHandsAndItemsRenderState handState,
            CallbackInfo ci
    ) {
        HandsModule.onBeginCapture();
    }

    @Inject(method = "submitArmWithItem", at = @At("HEAD"), cancellable = true)
    private void opticume$onHmiSubmitArmWithItem(
            PlayerRenderState playerRenderState,
            FirstPersonHandsAndItemsRenderState handState,
            float partialTicks,
            float pitch,
            InteractionHand hand,
            float swingProgress,
            ItemStack stack,
            float equipProgress,
            PoseStack matrices,
            SubmitNodeCollector collector,
            int light,
            CallbackInfo ci
    ) {
        SwingAnimationsModule swing = SwingAnimationsModule.getInstance();
        if (swing != null && swing.isHmi()) {
            opticume$fullNewModeSubmitArmWithItem(
                    playerRenderState, handState, partialTicks, hand, swingProgress, stack, equipProgress, matrices, collector, light, ci
            );
        }
    }

    @Inject(
            method = "submitArmWithItem",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V", shift = At.Shift.AFTER)
    )
    private void opticume$onTransformArmItem(
            PlayerRenderState playerRenderState,
            FirstPersonHandsAndItemsRenderState handState,
            float partialTicks,
            float pitch,
            InteractionHand hand,
            float swingProgress,
            ItemStack stack,
            float equipProgress,
            PoseStack matrices,
            SubmitNodeCollector collector,
            int light,
            CallbackInfo ci
    ) {
        ViewModelModule vm = ViewModelModule.getInstance();
        if (vm != null && vm.isEnabled()) {
            if (hand == InteractionHand.MAIN_HAND) {
                matrices.translate(vm.getMainX(), vm.getMainY(), vm.getMainZ());
            } else {
                matrices.translate(vm.getOffX(), vm.getOffY(), vm.getOffZ());
            }
        }
    }

    @Inject(method = "swingArm", at = @At("HEAD"), cancellable = true)
    private void opticume$onDesireSwingArm(float swingProgress, PoseStack poseStack, int invert, HumanoidArm arm, CallbackInfo ci) {
        SwingAnimationsModule swing = SwingAnimationsModule.getInstance();
        if (swing != null && swing.applySwing(poseStack, arm, swingProgress)) {
            ci.cancel();
        }
    }

    @Inject(method = "applyItemArmAttackTransform", at = @At("HEAD"), cancellable = true)
    private void opticume$onCustomSwing(PoseStack matrices, HumanoidArm arm, float swingProgress, CallbackInfo ci) {
        SwingAnimationsModule swing = SwingAnimationsModule.getInstance();
        if (swing != null && swing.overridesVanillaSwing()) {
            ci.cancel();
            return;
        }
        ViewModelModule vm = ViewModelModule.getInstance();
        if (vm != null && vm.isEnabled() && !"Vanilla".equals(vm.getSwingAnim())) {
            vm.applyCustomSwing(matrices, arm, swingProgress);
            ci.cancel();
        }
    }

    @ModifyVariable(method = "applyItemArmTransform", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private float opticume$modifyEquipProgress(float equipProgress) {
        SwingAnimationsModule swing = SwingAnimationsModule.getInstance();
        if (swing != null && swing.overridesVanillaSwing()) {
            return 0.0f;
        }
        ViewModelModule vm = ViewModelModule.getInstance();
        if (vm != null && vm.isEnabled() && vm.isNoEquipReset()) {
            return 0.0f;
        }
        return equipProgress;
    }

    @ModifyConstant(method = "applyEatTransform", constant = @Constant(floatValue = 0.1F))
    private float opticume$modifyEatBobbing(float vanilla) {
        SwingAnimationsModule swing = SwingAnimationsModule.getInstance();
        return swing != null ? swing.eatBobbing(vanilla) : vanilla;
    }

    @Inject(method = "applyEatTransform", at = @At("TAIL"))
    private void opticume$onCustomEat(PoseStack matrices, float partialTicks, HumanoidArm arm, float remainingTicks, int useDuration, CallbackInfo ci) {
        ViewModelModule vm = ViewModelModule.getInstance();
        if (vm != null && vm.isEnabled() && vm.isCustomEat()) {
            float scale = vm.getEatScale();
            if (scale != 1.0f) matrices.scale(scale, scale, scale);
            float y = vm.getEatY();
            if (y != 0.0f) matrices.translate(0.0f, y, 0.0f);
        }
    }

    @Inject(method = "applyItemArmTransform", at = @At("TAIL"))
    private void opticume$onApplyEquipOffset(PoseStack matrices, HumanoidArm arm, float equipProgress, CallbackInfo ci) {
        ViewModelModule vm = ViewModelModule.getInstance();
        if (vm != null && vm.isEnabled()) {
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

        SwingAnimationsModule swing = SwingAnimationsModule.getInstance();
        if (swing != null && swing.isEnabled()) {
            OpticumeAuraModule aura = OpticumeAuraModule.getInstance();
            boolean blockSync = aura != null && aura.isBlockHitAnimEnabled();
            if (minecraft.player != null) {
                ItemStack mainItem = minecraft.player.getMainHandItem();
                if ((swing.isBlockHitEnabled() && minecraft.player.isUsingItem() && mainItem.is(ItemTags.SWORDS)) || blockSync) {
                    matrices.translate(-0.15f, 0.15f, -0.1f);
                }
            }
        }
    }

    @Unique
    private void opticume$fullNewModeSubmitArmWithItem(
            PlayerRenderState playerRenderState,
            FirstPersonHandsAndItemsRenderState handState,
            float partialTicks,
            InteractionHand hand,
            float swingProgress,
            ItemStack stack,
            float equipProgress,
            PoseStack poseStack,
            SubmitNodeCollector collector,
            int lightCoords,
            CallbackInfo ci
    ) {
        LocalPlayer localPlayer = this.minecraft.player;
        AvatarRenderState avatar = playerRenderState.avatarRenderState;
        if (localPlayer == null || avatar == null) return;
        if (handState.isScoping || avatar.isAutoSpinAttack) return;

        SwingAnimationsModule swing = SwingAnimationsModule.getInstance();
        boolean isMainHand = hand == InteractionHand.MAIN_HAND;
        HumanoidArm arm = isMainHand ? avatar.mainArm : avatar.mainArm.getOpposite();
        if (stack.has(DataComponents.MAP_ID) || stack.is(Items.CROSSBOW)) return;
        if (!isMainHand && !(swing.newShowLeftArm.isEnabled() && !avatar.isInvisible)) return;

        poseStack.pushPose();
        if (stack.isEmpty()) {
            if (!avatar.isInvisible) {
                opticume$renderNewModeEmptyHand(localPlayer, playerRenderState, swingProgress, equipProgress, poseStack, collector, lightCoords, arm);
            }
            poseStack.popPose();
            ci.cancel();
            return;
        }

        ViewModelModule viewModel = ViewModelModule.getInstance();
        if (viewModel != null && viewModel.isEnabled() && !(avatar.isUsingItem && avatar.useItemHand == hand)) {
            if (isMainHand) {
                poseStack.translate(viewModel.getMainX(), viewModel.getMainY(), viewModel.getMainZ());
            } else {
                poseStack.translate(viewModel.getOffX(), viewModel.getOffY(), viewModel.getOffZ());
            }
        }

        boolean usingThisHand = avatar.isUsingItem && handState.useItemRemainingTicks > 0 && avatar.useItemHand == hand;
        if (usingThisHand) {
            int remaining = handState.useItemRemainingTicks;
            int totalDuration = isMainHand ? handState.mainHandUseDuration : handState.offHandUseDuration;
            switch (stack.getUseAnimation()) {
                case EAT, DRINK -> opticume$applyNewModeEatDrinkHandPose(poseStack, arm, partialTicks, remaining, totalDuration);
                case BLOCK -> opticume$applyNewModeBlockHandPose(poseStack, arm, partialTicks, remaining, totalDuration);
                default -> {
                    poseStack.popPose();
                    return;
                }
            }
            poseStack.pushPose();
            this.renderPlayerArm(poseStack, collector, lightCoords, 0.0f, swingProgress, arm, playerRenderState);
            poseStack.popPose();
        } else {
            if (isMainHand) {
                opticume$newModeApplySwing(swing, swingProgress, stack, poseStack, arm);
            } else {
                poseStack.translate(swing.leftX.getFloatValue(), swing.leftZ.getFloatValue(), swing.leftY.getFloatValue());
            }
            opticume$applyNewModeHandBase(poseStack, equipProgress, arm);
            poseStack.pushPose();
            this.renderPlayerArm(poseStack, collector, lightCoords, 0.0f, 0.0f, arm, playerRenderState);
            poseStack.popPose();
        }

        opticume$applyNewModeCommonItemAnchor(poseStack, arm);
        opticume$applyNewModeItemPose(poseStack, arm, stack);
        ItemStackRenderState itemRenderState = isMainHand ? handState.mainHandRenderState : handState.offHandRenderState;
        itemRenderState.submit(poseStack, collector, lightCoords, OverlayTexture.NO_OVERLAY, 0);
        poseStack.popPose();
        ci.cancel();
    }

    @Unique
    private void opticume$newModeApplySwing(SwingAnimationsModule swing, float attack, ItemStack stack, PoseStack poseStack, HumanoidArm arm) {
        if (stack.isEmpty()) return;
        if (swing.auraOnly.isEnabled() && !SwingAnimationsModule.auraHasTarget()) return;

        if (attack > 0.0f && this.opticume$holdMyItemsPrevSwingProgress == 0.0f) {
            this.opticume$holdMyItemsLeft = !this.opticume$holdMyItemsLeft;
        }
        this.opticume$holdMyItemsPrevSwingProgress = attack;

        if (arm == HumanoidArm.RIGHT) {
            poseStack.translate(swing.rightX.getFloatValue(), swing.rightY.getFloatValue(), swing.rightZ.getFloatValue());
        } else {
            poseStack.translate(swing.leftX.getFloatValue(), swing.leftZ.getFloatValue(), swing.leftY.getFloatValue());
        }

        float swingRot = opticume$getHoldMyItemsSwingRot(attack);
        float swingEase = opticume$holdMyItemsEase(Mth.sin(attack * (float) Math.PI));
        float ll = arm == HumanoidArm.RIGHT ? 1.0f : -1.0f;
        boolean hasAuraTarget = SwingAnimationsModule.auraHasTarget();
        boolean forwardHandsAttack = swing.attackMode.is("Forward") && hasAuraTarget;
        boolean normalHandsAttack = swing.attackMode.is("Normal") && hasAuraTarget;
        boolean isShield = stack.getItem() instanceof ShieldItem;
        boolean isSpear = stack.getItem() instanceof TridentItem;
        boolean isTool = stack.is(ItemTags.PICKAXES) || stack.is(ItemTags.AXES) || stack.is(ItemTags.SHOVELS) || isSpear;

        if (stack.is(ItemTags.SWORDS) && forwardHandsAttack) {
            poseStack.translate(0.12 * ll * swingRot, 0.04 * swingRot, -0.95 * swingEase);
            poseStack.translate(0.02 * ll * swingEase, 0.10 * swingEase, -0.10 * swingRot);
            poseStack.rotateDegrees(Axis.YP, 8.0f * swingRot * ll);
            poseStack.rotateDegrees(Axis.XP, 14.0f * swingRot);
            poseStack.rotateDegrees(Axis.ZP, -18.0f * swingRot * ll);
            poseStack.rotateDegrees(Axis.XP, -32.0f * swingEase);
        } else if (stack.is(ItemTags.SWORDS) && normalHandsAttack) {
            poseStack.translate(0.1 * ll * swingRot, 0.1 * swingRot, -0.1 * swingEase);
            poseStack.rotateDegrees(Axis.XP, 30.0f * swingRot);
            poseStack.rotateDegrees(Axis.ZP, -10.0f * swingRot * ll);
            poseStack.rotateDegrees(Axis.XP, -40.0f * swingEase);
            poseStack.rotateDegrees(Axis.YP, 10.0f * swingEase * ll);
        } else if ((this.opticume$holdMyItemsLeft || stack.is(ItemTags.AXES) || isShield || isSpear) && !stack.is(ItemTags.SHOVELS)) {
            if (stack.is(ItemTags.SWORDS) || stack.is(ItemTags.AXES)) {
                poseStack.translate(0.8 * ll * swingRot, 0.3 * swingRot, -0.5 * swingEase);
                poseStack.rotateDegrees(Axis.YP, 15.0f * swingRot * ll);
                poseStack.rotateDegrees(Axis.XP, 20.0f * swingRot);
                poseStack.rotateDegrees(Axis.ZP, -70.0f * swingRot * ll);
                poseStack.rotateDegrees(Axis.XP, (stack.is(ItemTags.SWORDS) ? -40.0f : -30.0f) * swingEase);
            } else if (isSpear) {
                poseStack.translate(0.0f, 0.0f, 0.45f * swingRot);
                poseStack.translate(-0.25f * ll * swingEase, -0.35f * swingRot, -0.6f * swingEase);
                poseStack.translate(0.0f, 0.1f * swingEase, 0.0f);
                poseStack.rotateDegrees(Axis.YP, 15.0f * swingRot * ll);
                poseStack.rotateDegrees(Axis.ZP, 30.0f * swingRot * ll);
            } else if (isTool) {
                poseStack.translate(0.1 * ll * swingRot, 0.1 * swingRot, -0.5 * swingEase);
                poseStack.rotateDegrees(Axis.XP, 30.0f * swingRot);
                poseStack.rotateDegrees(Axis.ZP, -20.0f * swingRot * ll);
                poseStack.rotateDegrees(Axis.XP, -40.0f * swingEase);
            } else if (!isShield) {
                poseStack.translate(0.1 * ll * swingRot, 0.1 * swingRot, -0.1 * swingEase);
                poseStack.rotateDegrees(Axis.XP, 30.0f * swingRot);
                poseStack.rotateDegrees(Axis.ZP, -10.0f * swingRot * ll);
                poseStack.rotateDegrees(Axis.XP, -40.0f * swingEase);
                poseStack.rotateDegrees(Axis.YP, 10.0f * swingEase * ll);
            } else {
                poseStack.translate(0.1 * ll * swingRot, 0.1 * swingRot, -0.2 * swingEase);
                poseStack.rotateDegrees(Axis.XP, 10.0f * swingRot);
                poseStack.rotateDegrees(Axis.ZP, -10.0f * swingRot * ll);
                poseStack.rotateDegrees(Axis.XP, -20.0f * swingEase);
            }
        } else if (stack.is(ItemTags.SHOVELS)) {
            poseStack.translate(0.0, 0.15 * swingRot, -0.25 * swingRot);
            poseStack.translate(0.0, 0.0, -0.2 * swingEase);
            poseStack.rotateDegrees(Axis.YP, 15.0f * swingRot);
            poseStack.rotateDegrees(Axis.XP, -35.0f * swingRot);
            poseStack.rotateDegrees(Axis.XP, 30.0f * swingEase);
        } else if (stack.is(ItemTags.SWORDS)) {
            poseStack.translate(-0.55 * ll * swingRot, -0.8 * swingRot, -0.77 * swingEase);
            poseStack.rotateDegrees(Axis.YP, 5.0f * swingRot * ll);
            poseStack.rotateDegrees(Axis.XP, 30.0f * swingRot);
            poseStack.rotateDegrees(Axis.ZP, 70.0f * swingRot * ll);
            poseStack.rotateDegrees(Axis.XP, -50.0f * swingEase);
        } else if (isTool) {
            poseStack.translate(0.1 * ll * swingRot, 0.1 * swingRot, -0.5 * swingEase);
            poseStack.rotateDegrees(Axis.XP, 30.0f * swingRot);
            poseStack.rotateDegrees(Axis.ZP, -20.0f * swingRot * ll);
            poseStack.rotateDegrees(Axis.XP, -40.0f * swingEase);
        } else {
            poseStack.translate(0.1 * ll * swingRot, 0.1 * swingRot, -0.1 * swingEase);
            poseStack.rotateDegrees(Axis.XP, 30.0f * swingRot);
            poseStack.rotateDegrees(Axis.ZP, -10.0f * swingRot * ll);
            poseStack.rotateDegrees(Axis.XP, -40.0f * swingEase);
            poseStack.rotateDegrees(Axis.YP, 10.0f * swingEase * ll);
        }
    }

    @Unique
    private void opticume$renderNewModeEmptyHand(
            LocalPlayer player,
            PlayerRenderState playerRenderState,
            float attack,
            float inverseArmHeight,
            PoseStack poseStack,
            SubmitNodeCollector collector,
            int lightCoords,
            HumanoidArm arm
    ) {
        boolean movementPose = player.isSwimming() || player.isVisuallySwimming() || (!player.onGround() && player.onClimbable());
        int direction = arm == HumanoidArm.RIGHT ? 1 : -1;

        if (arm == HumanoidArm.LEFT) {
            float swingRot = opticume$getHoldMyItemsSwingRot(attack);
            float swing = opticume$holdMyItemsEase(Mth.sin(attack * (float) Math.PI));
            if (movementPose) {
                opticume$applyNewModeHandBase(poseStack, inverseArmHeight, arm);
                poseStack.translate(0.05f, -0.08f, -0.1f);
                poseStack.rotateDegrees(Axis.YP, 12.0f);
            } else {
                poseStack.translate(-0.1f, -0.08f - inverseArmHeight * 0.05f, -0.12f);
                poseStack.translate(-0.03f * swing, 0.04f * swingRot, -0.05f * swing);
                poseStack.rotateDegrees(Axis.YP, 18.0f);
                poseStack.rotateDegrees(Axis.ZP, -10.0f);
                poseStack.rotateDegrees(Axis.XP, -12.0f);
                poseStack.rotateDegrees(Axis.YP, 8.0f * swing);
                poseStack.rotateDegrees(Axis.XP, -6.0f * swingRot);
            }
            this.renderPlayerArm(poseStack, collector, lightCoords, inverseArmHeight * 0.35f, 0.0f, arm, playerRenderState);
            return;
        }

        if (movementPose) {
            opticume$applyNewModeHandBase(poseStack, inverseArmHeight, arm);
        } else {
            float swingRot = opticume$getHoldMyItemsSwingRot(attack);
            float swing = opticume$holdMyItemsEase(Mth.sin(attack * (float) Math.PI));
            poseStack.translate(0.0f, 0.2f * swingRot, 0.15f * swingRot);
            poseStack.translate(0.1f * direction * swing, 0.15f * swing, -0.45f * swing);
            poseStack.rotateDegrees(Axis.YP, 35.0f * swing * direction);
            poseStack.rotateDegrees(Axis.XP, -30.0f * swing);
            poseStack.rotateDegrees(Axis.YP, -10.0f * swingRot * direction);
            poseStack.rotateDegrees(Axis.XP, 10.0f * swingRot);
        }
        this.renderPlayerArm(poseStack, collector, lightCoords, 0.0f, 0.0f, arm, playerRenderState);
    }

    @Unique
    private static void opticume$applyNewModeHandBase(PoseStack poseStack, float inverseArmHeight, HumanoidArm arm) {
        int direction = arm == HumanoidArm.RIGHT ? 1 : -1;
        poseStack.translate((float) direction, -inverseArmHeight * 0.3f, 0.3f);
        poseStack.rotateDegrees(Axis.YP, 45.0f * direction);
        poseStack.rotateDegrees(Axis.ZP, -40.0f * direction);
        poseStack.rotateDegrees(Axis.XP, 30.0f);
        poseStack.scale(0.9f, 0.9f, 0.9f);
    }

    @Unique
    private static void opticume$applyNewModeEatDrinkHandPose(PoseStack poseStack, HumanoidArm arm, float partialTicks, int remaining, int useDuration) {
        int direction = arm == HumanoidArm.RIGHT ? 1 : -1;
        float useTicks = (float) useDuration - ((float) remaining - partialTicks + 1.0f);
        float progress = Mth.clamp(useTicks / 5.0f, 0.0f, 1.0f);
        float wobble = Mth.sin(useTicks / 2.0f * (float) Math.PI) / 10.0f;

        poseStack.translate((float) direction, 0.1f, 0.3f);
        poseStack.translate(0.2f * direction * progress, -0.7f * progress, -0.2f * progress);
        poseStack.translate(0.0f, -0.2f * wobble, -0.2f * wobble);
        poseStack.translate(0.0f, 0.1f * opticume$holdMyItemsEase(Mth.sin(progress * (float) Math.PI)), 0.0f);
        poseStack.rotateDegrees(Axis.YP, 45.0f * direction);
        poseStack.rotateDegrees(Axis.ZP, -40.0f * direction);
        poseStack.rotateDegrees(Axis.XP, 30.0f);
        poseStack.scale(0.9f, 0.9f, 0.9f);
        poseStack.rotateDegrees(Axis.YP, 45.0f * progress * direction);
    }

    @Unique
    private static void opticume$applyNewModeBlockHandPose(PoseStack poseStack, HumanoidArm arm, float partialTicks, int remaining, int useDuration) {
        int direction = arm == HumanoidArm.RIGHT ? 1 : -1;
        float useTicks = (float) useDuration - ((float) remaining - partialTicks + 1.0f);
        float blockProgress = Mth.clamp(useTicks / 4.0f, 0.0f, 1.0f);
        float settleProgress = Mth.clamp(useTicks / 6.0f, 0.0f, 1.0f);

        poseStack.translate(0.0f, -0.2f, 0.0f);
        poseStack.translate((float) direction, 0.0f, 0.3f);
        poseStack.translate(0.7f * blockProgress * direction, 0.0f, -1.3f * blockProgress);
        poseStack.translate(-0.2f * direction * settleProgress, 0.0f, 0.0f);
        poseStack.rotateDegrees(Axis.XP, 10.0f * Mth.sin(settleProgress * (float) Math.PI));
        poseStack.rotateDegrees(Axis.YP, 70.0f * blockProgress * direction);
        poseStack.rotateDegrees(Axis.YP, 45.0f * direction);
        poseStack.rotateDegrees(Axis.ZP, -40.0f * direction);
        poseStack.rotateDegrees(Axis.XP, 30.0f);
        poseStack.rotateDegrees(Axis.YP, 5.0f * direction * blockProgress);
        poseStack.rotateDegrees(Axis.XP, -10.0f * blockProgress);
        poseStack.translate(0.0f, 0.0f, -0.2f * blockProgress);
        poseStack.scale(0.9f, 0.9f, 0.9f);
    }

    @Unique
    private static void opticume$applyNewModeCommonItemAnchor(PoseStack poseStack, HumanoidArm arm) {
        int direction = arm == HumanoidArm.RIGHT ? 1 : -1;
        poseStack.translate(-0.3f * direction, 0.65f, -0.1f);
        poseStack.rotateDegrees(Axis.YP, -65.0f * direction);
        poseStack.rotateDegrees(Axis.XP, 10.0f);
    }

    @Unique
    private static boolean opticume$isNewModeWeaponLike(ItemStack stack) {
        Item item = stack.getItem();
        ItemUseAnimation useAnimation = stack.getUseAnimation();
        return stack.is(ItemTags.SWORDS)
                || stack.is(ItemTags.AXES)
                || item instanceof ShieldItem
                || item instanceof TridentItem
                || item instanceof FishingRodItem
                || useAnimation == ItemUseAnimation.BOW
                || useAnimation == ItemUseAnimation.BLOCK
                || useAnimation == ItemUseAnimation.TRIDENT
                || useAnimation == ItemUseAnimation.SPEAR
                || item == Items.SHEARS
                || item == Items.CARROT_ON_A_STICK
                || item == Items.WARPED_FUNGUS_ON_A_STICK;
    }

    @Unique
    private static boolean opticume$isNewModeTorch(ItemStack stack) {
        Item item = stack.getItem();
        return item == Items.TORCH || item == Items.SOUL_TORCH || item == Items.REDSTONE_TORCH;
    }

    @Unique
    private static boolean opticume$isNewModeThinItem(ItemStack stack) {
        Item item = stack.getItem();
        Block block = Block.byItem(item);
        return item == Items.STRING
                || item == Items.REDSTONE
                || item == Items.LEVER
                || item == Items.TRIPWIRE_HOOK
                || block.defaultBlockState().is(BlockTags.RAILS)
                || block.defaultBlockState().is(BlockTags.CLIMBABLE)
                || block.defaultBlockState().is(BlockTags.DOORS)
                || block.defaultBlockState().is(BlockTags.BUTTONS)
                || block.defaultBlockState().is(BlockTags.PRESSURE_PLATES);
    }

    @Unique
    private static boolean opticume$isNewModeSmallItem(ItemStack stack) {
        Item item = stack.getItem();
        return item.components().has(DataComponents.FOOD)
                || item instanceof BucketItem
                || (!opticume$isNewModeWeaponLike(stack) && !(item instanceof BlockItem));
    }

    @Unique
    private static void opticume$applyNewModeItemPose(PoseStack poseStack, HumanoidArm arm, ItemStack stack) {
        int direction = arm == HumanoidArm.RIGHT ? 1 : -1;

        if (opticume$isNewModeTorch(stack)) {
            poseStack.scale(1.5f, 1.5f, 1.5f);
            poseStack.rotateDegrees(Axis.YN, 25.0f * direction);
            poseStack.rotateDegrees(Axis.XP, 5.0f);
            poseStack.rotateDegrees(Axis.ZP, 75.0f * direction);
            poseStack.translate(0.2f * direction, 0.2f, 0.05f);
            return;
        }

        if (opticume$isNewModeSmallItem(stack)) {
            poseStack.rotateDegrees(Axis.YN, 5.0f * direction);
            poseStack.rotateDegrees(Axis.XP, 15.0f);
            poseStack.rotateDegrees(Axis.ZP, 75.0f * direction);
            poseStack.translate(0.0f, -0.05f, -0.1f);
            poseStack.scale(0.7f, 0.7f, 0.7f);
            return;
        }

        if (opticume$isNewModeThinItem(stack)) {
            poseStack.translate(0.0f, 0.0f, -0.1f);
            poseStack.rotateDegrees(Axis.YN, 5.0f * direction);
            poseStack.rotateDegrees(Axis.XP, 15.0f);
            poseStack.rotateDegrees(Axis.ZP, 75.0f * direction);
            return;
        }

        if (stack.getItem() instanceof BlockItem) {
            poseStack.rotateDegrees(Axis.YN, 25.0f * direction);
            poseStack.rotateDegrees(Axis.XP, 5.0f);
            poseStack.rotateDegrees(Axis.ZP, 75.0f * direction);
            poseStack.translate(0.2f * direction, 0.2f, 0.05f);
            return;
        }

        ItemUseAnimation useAnimation = stack.getUseAnimation();
        if (useAnimation == ItemUseAnimation.BLOCK) {
            poseStack.rotateDegrees(Axis.ZP, 160.0f * direction);
            poseStack.rotateDegrees(Axis.YP, -60.0f * direction);
            poseStack.rotateDegrees(Axis.XP, -70.0f);
            poseStack.scale(0.75f, 0.75f, 0.75f);
            poseStack.translate(0.15f * direction, arm == HumanoidArm.RIGHT ? 0.35f : 0.45f, arm == HumanoidArm.RIGHT ? -0.15f : -0.1f);
            poseStack.translate(0.17f * direction, 0.0f, 0.3f);
            poseStack.rotateDegrees(Axis.YP, -90.0f * direction);
            return;
        }

        if (useAnimation == ItemUseAnimation.TRIDENT || useAnimation == ItemUseAnimation.SPEAR) {
            poseStack.rotateDegrees(Axis.YN, 75.0f * direction);
            poseStack.rotateDegrees(Axis.XP, 90.0f);
            poseStack.rotateDegrees(Axis.ZP, 45.0f * direction);
            poseStack.translate(-0.3f * direction, 0.0f, 0.0f);
        } else {
            poseStack.rotateDegrees(Axis.YN, 75.0f * direction);
            poseStack.rotateDegrees(Axis.XP, 70.0f);
            poseStack.rotateDegrees(Axis.ZP, 45.0f * direction);
        }
        poseStack.scale(1.2f, 1.2f, 1.2f);
        if (useAnimation == ItemUseAnimation.BOW) {
            poseStack.translate(-0.1f * direction, -0.2f, 0.0f);
        }
    }

    @Unique
    private static float opticume$holdMyItemsEase(float value) {
        float c1 = 1.70158f;
        float c2 = c1 * 1.525f;
        if (value < 0.5f) {
            float doubled = 2.0f * value;
            return doubled * doubled * ((c2 + 1.0f) * doubled - c2) * 0.5f;
        }
        float shifted = 2.0f * value - 2.0f;
        return (shifted * shifted * ((c2 + 1.0f) * shifted + c2) + 2.0f) * 0.5f;
    }

    @Unique
    private static float opticume$getHoldMyItemsSwingRot(float swingProgress) {
        if (swingProgress < 0.6f) {
            return Mth.sin(Mth.clamp(swingProgress, 0.0f, 0.12506f) * 12.56f);
        }
        return Mth.sin(Mth.clamp(swingProgress, 0.62532f, 0.75038f) * 12.56f);
    }
}
