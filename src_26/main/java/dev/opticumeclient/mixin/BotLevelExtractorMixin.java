package dev.opticumeclient.mixin;

import dev.opticumeclient.client.bots.BotContext;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.extract.LevelExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(LevelExtractor.class)
public abstract class BotLevelExtractorMixin {
    @Inject(
            method = {
                    "blockChanged",
                    "setBlocksDirty",
                    "setBlockDirty(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/state/BlockState;)V",
                    "setSectionDirtyWithNeighbors",
                    "setSectionRangeDirty",
                    "setSectionDirty(III)V"
            },
            at = @At("HEAD"),
            cancellable = true
    )
    private void opticume$botDirty(CallbackInfo ci) {
        if (BotContext.isBackgroundContextActive()) {
            ci.cancel();
        }
    }
}
