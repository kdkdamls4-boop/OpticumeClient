package dev.opticumeclient.mixin;

import dev.opticumeclient.client.autobuy.AutoBuyOverlay;
import dev.opticumeclient.client.modules.other.AuctionHelperModule;
import dev.opticumeclient.client.modules.other.LockSlotModule;
import dev.opticumeclient.client.modules.visuals.ShulkerPreviewModule;
import dev.opticumeclient.client.util.IContainerScreenBounds;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(AbstractContainerScreen.class)
public abstract class HandledScreenMixin<T extends AbstractContainerMenu> extends Screen implements MenuAccess<T>, IContainerScreenBounds {
    @Shadow protected int leftPos;
    @Shadow protected int topPos;
    @Shadow @Final protected int imageWidth;
    @Shadow @Final protected int imageHeight;
    @Shadow protected Slot hoveredSlot;

    protected HandledScreenMixin(Component title) {
        super(title);
    }

    @Override
    public int opticume$getLeftPos() {
        return leftPos;
    }

    @Override
    public int opticume$getTopPos() {
        return topPos;
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void opticume$renderAutoBuyOverlay(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        AbstractContainerScreen<?> self = (AbstractContainerScreen<?>) (Object) this;
        AuctionHelperModule ah = AuctionHelperModule.getInstance();
        if (ah != null && ah.isEnabled()) {
            ah.renderContainerOverlay(self, graphics, leftPos, topPos);
        }
        if (AutoBuyOverlay.shouldShow(self)) {
            AutoBuyOverlay.render(graphics, leftPos, topPos, imageWidth, imageHeight, mouseX, mouseY);
        }
    }

    @Inject(method = "extractTooltip", at = @At("TAIL"))
    private void opticume$renderShulkerPreview(GuiGraphicsExtractor graphics, int mouseX, int mouseY, CallbackInfo ci) {
        if (this.hoveredSlot != null && this.hoveredSlot.hasItem()) {
            ShulkerPreviewModule preview = ShulkerPreviewModule.getInstance();
            if (preview != null && preview.isEnabled()) {
                preview.renderHoveredTooltip(graphics, this.hoveredSlot.getItem(), mouseX, mouseY);
            }
        }
    }

    @Inject(
            method = "slotClicked(Lnet/minecraft/world/inventory/Slot;IILnet/minecraft/world/inventory/ContainerInput;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void opticume$onSlotClickedLockSlot(Slot slot, int slotId, int button, ContainerInput input, CallbackInfo ci) {
        if (input != ContainerInput.THROW || slot == null) return;
        LockSlotModule lockSlot = LockSlotModule.getInstance();
        if (lockSlot != null && slot.container instanceof Inventory) {
            int invIndex = slot.getContainerSlot();
            if (invIndex >= 0 && invIndex < 9 && lockSlot.shouldBlockDropInGui(invIndex)) {
                ci.cancel();
            }
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void opticume$onAutoBuyOverlayClick(MouseButtonEvent click, boolean doubled, CallbackInfoReturnable<Boolean> cir) {
        AbstractContainerScreen<?> self = (AbstractContainerScreen<?>) (Object) this;
        if (AutoBuyOverlay.shouldShow(self)
                && AutoBuyOverlay.mouseClicked(click.x(), click.y(), click.button(), leftPos, topPos, imageWidth, imageHeight)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
    private void opticume$onAutoBuyOverlayScroll(double mouseX, double mouseY, double horizontalAmount, double verticalAmount, CallbackInfoReturnable<Boolean> cir) {
        AbstractContainerScreen<?> self = (AbstractContainerScreen<?>) (Object) this;
        if (AutoBuyOverlay.shouldShow(self)
                && AutoBuyOverlay.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount, leftPos, topPos, imageWidth, imageHeight)) {
            cir.setReturnValue(true);
        }
    }
}
