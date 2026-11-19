package dev.opticumeclient.mixin;

import dev.opticumeclient.client.modules.optimization.FrameSyncModule;
import dev.opticumeclient.client.modules.optimization.OptimizationCoordinator;
import dev.opticumeclient.client.modules.visuals.NoRenderModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.ParticleGroup;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

@Environment(EnvType.CLIENT)
@Mixin(ParticleEngine.class)
public abstract class ParticleEngineMixin {

    @Shadow @Final private Map<ParticleRenderType, ParticleGroup<?>> particles;

    @Inject(method = "createParticle", at = @At("HEAD"), cancellable = true)
    private void opticume$onCreateParticle(ParticleOptions type, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed, CallbackInfoReturnable<Particle> cir) {
        NoRenderModule noRender = NoRenderModule.getInstance();
        if (noRender != null && noRender.isExplosions()) {
            if (type != null && (type.getType() == ParticleTypes.EXPLOSION || type.getType() == ParticleTypes.EXPLOSION_EMITTER)) {
                cir.setReturnValue(null);
                return;
            }
        }

        if (type != null && type.getType() != ParticleTypes.FIREWORK && type.getType() != ParticleTypes.FLASH) {
            if (OptimizationCoordinator.shouldCullParticle(x, y, z) || OptimizationCoordinator.shouldThinParticle(x, y, z)) {
                cir.setReturnValue(null);
                return;
            }
        }

        FrameSyncModule frameSync = FrameSyncModule.getInstance();
        if (frameSync != null && frameSync.isEnabled()) {
            if (frameSync.isParticleCapReached(opticume$getTotalParticleCount())) {
                cir.setReturnValue(null);
            }
        }
    }

    private int opticume$getTotalParticleCount() {
        int total = 0;
        if (this.particles != null) {
            for (ParticleGroup<?> group : this.particles.values()) {
                if (group != null) {
                    total += group.size();
                }
            }
        }
        return total;
    }
}
