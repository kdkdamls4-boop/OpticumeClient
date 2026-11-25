package dev.opticumeclient.mixin;

import dev.opticumeclient.client.autobuy.AutoBuyConfig;
import dev.opticumeclient.client.autobuy.PriceParser;
import dev.opticumeclient.client.modules.other.NameProtectModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Environment(EnvType.CLIENT)
@Mixin(ChatComponent.class)
public abstract class ChatCompactMixin {
    @Shadow @Final private List<GuiMessage> allMessages;
    @Shadow private int chatScrollbarPos;
    @Shadow protected abstract void refreshTrimmedMessages();

    @Unique private Component opticume$lastOriginalText = null;
    @Unique private String opticume$lastCleanString = null;
    @Unique private int opticume$lastMessageCount = 1;

    @ModifyVariable(method = "addMessage", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private Component opticume$protectChatName(Component message) {
        if (message != null && NameProtectModule.shouldMaskChat()) {
            return NameProtectModule.protectComponent(message);
        }
        return message;
    }

    @Inject(method = "addMessage", at = @At("HEAD"), cancellable = true)
    private void opticume$compactAutoBuyChat(Component message, MessageSignature signature, GuiMessageSource source, GuiMessageTag tag, CallbackInfo ci) {
        if (message == null) return;

        AutoBuyConfig cfg = AutoBuyConfig.cfg();
        if (cfg != null && !cfg.compactChat) {
            this.opticume$lastCleanString = null;
            this.opticume$lastOriginalText = null;
            this.opticume$lastMessageCount = 1;
            return;
        }

        String raw = message.getString();
        if (raw == null || raw.isBlank()) return;

        String clean = PriceParser.stripFormatting(raw).trim();
        if (clean.isEmpty()) return;

        if (clean.equals(this.opticume$lastCleanString) && !this.allMessages.isEmpty() && this.opticume$lastOriginalText != null) {
            this.opticume$lastMessageCount++;
            Component stacked = this.opticume$lastOriginalText.copy()
                    .append(Component.literal(" §7(x" + this.opticume$lastMessageCount + ")"));

            GuiMessage previousLine = this.allMessages.get(0);
            this.allMessages.set(0, new GuiMessage(previousLine.addedTime(), stacked, previousLine.signature(), previousLine.source(), previousLine.tag()));

            int scroll = this.chatScrollbarPos;
            this.refreshTrimmedMessages();
            if (scroll > 0) {
                this.chatScrollbarPos = scroll;
            }
            ci.cancel();
            return;
        }

        this.opticume$lastCleanString = clean;
        this.opticume$lastOriginalText = message.copy();
        this.opticume$lastMessageCount = 1;
    }

    @Inject(method = "clearMessages", at = @At("HEAD"))
    private void opticume$resetCompactAutoBuyChat(boolean clearHistory, CallbackInfo ci) {
        this.opticume$lastCleanString = null;
        this.opticume$lastOriginalText = null;
        this.opticume$lastMessageCount = 1;
    }
}
