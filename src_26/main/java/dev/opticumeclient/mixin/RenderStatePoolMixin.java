package dev.opticumeclient.mixin;

import dev.opticumeclient.client.modules.optimization.IRenderStatePool;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin({Entity.class, BlockEntity.class})
public abstract class RenderStatePoolMixin implements IRenderStatePool {
    @Unique private Object opticume$stateOwner;
    @Unique private Object opticume$state;

    @Override
    public Object opticume$pooledState(Object owner) {
        return opticume$stateOwner == owner ? opticume$state : null;
    }

    @Override
    public void opticume$poolState(Object owner, Object state) {
        opticume$stateOwner = owner;
        opticume$state = state;
    }
}
