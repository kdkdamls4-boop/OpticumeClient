package dev.opticumeclient.mixin;

import dev.opticumeclient.client.modules.ModuleManager;
import dev.opticumeclient.client.modules.combat.AntiBotModule;
import dev.opticumeclient.client.modules.combat.BackTrackModule;
import dev.opticumeclient.client.modules.combat.NoFriendDamageModule;
import dev.opticumeclient.client.modules.other.LockSlotModule;
import dev.opticumeclient.client.modules.other.NoInteractModule;
import dev.opticumeclient.client.modules.visuals.KillEffectModule;
import dev.opticumeclient.client.modules.visuals.ParticlesModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeMixin {

    @Inject(method = "attack", at = @At("HEAD"), cancellable = true)
    private void opticume$onAttack(Player player, Entity target, CallbackInfo ci) {
        NoFriendDamageModule noFriend = NoFriendDamageModule.getInstance();
        if (noFriend != null && noFriend.shouldCancelAttack(target)) {
            ci.cancel();
            return;
        }
        if (AntiBotModule.isBot(target)) {
            ci.cancel();
            return;
        }
        BackTrackModule bt = BackTrackModule.getInstance();
        if (bt != null && bt.isEnabled()) {
            bt.onAttackTarget(target);
        }
        KillEffectModule kill = ModuleManager.getInstance().getModule(KillEffectModule.class);
        if (kill != null && kill.isEnabled()) {
            kill.recordAttack(target);
        }
        ParticlesModule particles = ParticlesModule.getInstance();
        if (particles != null && particles.isEnabled()) {
            particles.onAttack(target);
        }
    }

    @Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true)
    private void opticume$onUseItemOn(LocalPlayer player, InteractionHand hand, BlockHitResult hitResult, CallbackInfoReturnable<InteractionResult> cir) {
        NoInteractModule noInteract = NoInteractModule.getInstance();
        if (noInteract != null && hitResult != null && noInteract.shouldBlockBlockInteract(hitResult.getBlockPos())) {
            cir.setReturnValue(InteractionResult.PASS);
        }
    }

    @Inject(method = "interact", at = @At("HEAD"), cancellable = true)
    private void opticume$onInteractEntity(Player player, Entity entity, EntityHitResult hitResult, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        NoInteractModule noInteract = NoInteractModule.getInstance();
        if (noInteract != null && noInteract.shouldBlockEntityInteract(entity)) {
            cir.setReturnValue(InteractionResult.PASS);
        }
    }

    @Inject(method = "dropItem", at = @At("HEAD"), cancellable = true)
    private void opticume$onDropItem(LocalPlayer player, boolean dropStack, CallbackInfo ci) {
        LockSlotModule lockSlot = LockSlotModule.getInstance();
        if (lockSlot != null && player != null && lockSlot.isHotbarSlotLocked(player.getInventory().getSelectedSlot())) {
            ci.cancel();
        }
    }
}
