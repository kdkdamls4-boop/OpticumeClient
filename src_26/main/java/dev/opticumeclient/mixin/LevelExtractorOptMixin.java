package dev.opticumeclient.mixin;

import dev.opticumeclient.client.modules.optimization.OptimizationCoordinator;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(LevelExtractor.class)
public abstract class LevelExtractorOptMixin {

    @Inject(method = "extractEntity", at = @At("HEAD"))
    private void opticume$beginWorldEntityExtract(Entity entity, float partialTick, CallbackInfoReturnable<EntityRenderState> cir) {
        OptimizationCoordinator.setWorldEntityExtract(true);
    }

    @Inject(method = "extractEntity", at = @At("RETURN"))
    private void opticume$endWorldEntityExtract(Entity entity, float partialTick, CallbackInfoReturnable<EntityRenderState> cir) {
        OptimizationCoordinator.setWorldEntityExtract(false);
    }
}
