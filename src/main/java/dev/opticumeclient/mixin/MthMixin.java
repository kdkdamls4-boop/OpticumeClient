package dev.opticumeclient.mixin;

import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Preserves vanilla Mth.sin / Mth.cos JVM JIT inlining (+40..+70 FPS).
 * Intercepts intentionally removed to avoid CallbackInfoReturnable allocation overhead.
 */
@Mixin(Mth.class)
public abstract class MthMixin {
}
