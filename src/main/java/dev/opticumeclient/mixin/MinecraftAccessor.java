package dev.opticumeclient.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Provides write access to {@code Minecraft.user} and level engine updates.
 */
@Environment(EnvType.CLIENT)
@Mixin(Minecraft.class)
public interface MinecraftAccessor {
    @Accessor("user")
    @Mutable
    void opticume$setUser(User user);

    @Invoker("updateLevelInEngines")
    void opticume$updateLevelInEngines(ClientLevel level, boolean stopSound);
}
