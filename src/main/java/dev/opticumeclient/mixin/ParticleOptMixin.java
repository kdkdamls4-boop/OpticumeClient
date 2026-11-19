package dev.opticumeclient.mixin;

import dev.opticumeclient.client.modules.optimization.OptimizationCoordinator;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.particle.Particle;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(Particle.class)
public abstract class ParticleOptMixin {
    @Shadow protected double x;
    @Shadow protected double y;
    @Shadow protected double z;
    @Shadow protected int age;

    @Shadow public abstract AABB getBoundingBox();
    @Shadow public abstract void setBoundingBox(AABB box);
    @Shadow protected abstract void setLocationFromBoundingbox();

    @Unique private int opticume$cachedLightAge = -1;
    @Unique private int opticume$cachedLightValue;

    @Inject(method = "move", at = @At("HEAD"), cancellable = true)
    private void opticume$simpleParticlePhysics(double dx, double dy, double dz, CallbackInfo ci) {
        if (!OptimizationCoordinator.shouldSimplifyParticlePhysics(x, y, z)) {
            return;
        }
        setBoundingBox(getBoundingBox().move(dx, dy, dz));
        setLocationFromBoundingbox();
        ci.cancel();
    }

    @Inject(method = "getLightCoords", at = @At("HEAD"), cancellable = true)
    private void opticume$readCachedParticleLight(float partialTick, CallbackInfoReturnable<Integer> cir) {
        if (OptimizationCoordinator.isParticleLightCache() && opticume$cachedLightAge == age) {
            cir.setReturnValue(opticume$cachedLightValue);
        }
    }

    @Inject(method = "getLightCoords", at = @At("RETURN"))
    private void opticume$storeCachedParticleLight(float partialTick, CallbackInfoReturnable<Integer> cir) {
        opticume$cachedLightAge = age;
        opticume$cachedLightValue = cir.getReturnValue();
    }
}
