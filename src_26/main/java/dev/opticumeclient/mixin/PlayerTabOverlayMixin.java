package dev.opticumeclient.mixin;

import dev.opticumeclient.client.modules.other.NameProtectModule;
import dev.opticumeclient.client.modules.visuals.BetterTabModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Environment(EnvType.CLIENT)
@Mixin(PlayerTabOverlay.class)
public abstract class PlayerTabOverlayMixin {

    @Shadow private Component header;
    @Shadow private Component footer;

    @Shadow
    protected abstract List<PlayerInfo> getPlayerInfos();

    @ModifyConstant(method = "getPlayerInfos", constant = @Constant(longValue = 80L))
    private long opticume$expandTabLimit(long original) {
        return BetterTabModule.getEffectiveMaxPlayers(original);
    }

    @Inject(method = "getNameForDisplay", at = @At("RETURN"), cancellable = true)
    private void opticume$modifyTabDisplayName(PlayerInfo info, CallbackInfoReturnable<Component> cir) {
        Component current = cir.getReturnValue();
        if (current == null) return;

        if (NameProtectModule.shouldMaskTab()) {
            current = NameProtectModule.protectComponent(current);
        }
        if (BetterTabModule.isNumericPingEnabled() && !BetterTabModule.isCustomTabActive() && info != null) {
            int ping = Math.max(0, info.getLatency());
            String col = ping < 80 ? "§a" : (ping < 180 ? "§e" : "§c");
            current = current.copy().append(Component.literal(" " + col + ping + "ms"));
        }
        cir.setReturnValue(current);
    }

    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
    private void opticume$renderCustomTabOverlay(GuiGraphicsExtractor graphics, int screenWidth, Scoreboard scoreboard, Objective objective, CallbackInfo ci) {
        if (!BetterTabModule.isCustomTabActive()) return;
        List<PlayerInfo> infos = this.getPlayerInfos();
        BetterTabModule.getInstance().captureTabFrame(infos, this.header, this.footer);
        ci.cancel();
    }
}
