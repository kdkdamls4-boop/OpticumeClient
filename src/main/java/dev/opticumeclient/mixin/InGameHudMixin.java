package dev.opticumeclient.mixin;

import dev.opticumeclient.client.modules.ModuleManager;
import dev.opticumeclient.client.modules.visuals.CrosshairModule;
import dev.opticumeclient.client.modules.visuals.NoRenderModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.world.scores.Objective;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(Hud.class)
public abstract class InGameHudMixin {

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void opticume$onRenderHud(GuiGraphicsExtractor context, DeltaTracker tickCounter, CallbackInfo ci) {
        ModuleManager.getInstance().onRender2D(context, tickCounter.getGameTimeDeltaPartialTick(false));
    }

    @Inject(method = "extractCrosshair", at = @At("HEAD"), cancellable = true)
    private void opticume$cancelVanillaCrosshair(GuiGraphicsExtractor context, DeltaTracker tickCounter, CallbackInfo ci) {
        CrosshairModule crosshair = CrosshairModule.getInstance();
        if (crosshair != null && crosshair.isEnabled()) {
            ci.cancel();
        }
    }

    @Inject(method = "extractPortalOverlay", at = @At("HEAD"), cancellable = true)
    private void opticume$cancelPortalOverlay(GuiGraphicsExtractor context, float alpha, CallbackInfo ci) {
        NoRenderModule noRender = NoRenderModule.getInstance();
        if (noRender != null && noRender.isBadEffects()) {
            ci.cancel();
        }
    }

    @Inject(method = "extractConfusionOverlay", at = @At("HEAD"), cancellable = true)
    private void opticume$cancelConfusionOverlay(GuiGraphicsExtractor context, float progress, CallbackInfo ci) {
        NoRenderModule noRender = NoRenderModule.getInstance();
        if (noRender != null && noRender.isBadEffects()) {
            ci.cancel();
        }
    }

    @Inject(method = "displayScoreboardSidebar", at = @At("HEAD"), cancellable = true)
    private void opticume$cancelScoreboardSidebar(GuiGraphicsExtractor context, Objective objective, CallbackInfo ci) {
        NoRenderModule noRender = NoRenderModule.getInstance();
        if (noRender != null && noRender.isScoreboard()) {
            ci.cancel();
        }
    }
}
