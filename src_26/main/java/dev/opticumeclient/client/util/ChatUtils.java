package dev.opticumeclient.client.util;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Client-side chat notification utility for commands, GPS markers and reconnect feedback.
 */
public final class ChatUtils {
    private ChatUtils() {
    }

    public static void sendClientMessage(String msg) {
        if (msg == null || dev.opticumeclient.client.modules.other.PanicModule.isSilentModeActive()) return;
        MutableComponent comp = Component.literal("[")
                .withStyle(ChatFormatting.DARK_GRAY)
                .append(Component.literal("Opticume").withStyle(ChatFormatting.AQUA))
                .append(Component.literal("] ").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal(msg).withStyle(ChatFormatting.GRAY));
        sendComponent(comp);
    }

    public static void sendError(String msg) {
        if (msg == null || dev.opticumeclient.client.modules.other.PanicModule.isSilentModeActive()) return;
        MutableComponent comp = Component.literal("[")
                .withStyle(ChatFormatting.DARK_GRAY)
                .append(Component.literal("Opticume").withStyle(ChatFormatting.AQUA))
                .append(Component.literal("] ").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal(msg).withStyle(ChatFormatting.RED));
        sendComponent(comp);
    }

    public static void sendComponent(Component component) {
        if (component == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.gui == null || mc.gui.hud == null || mc.gui.hud.getChat() == null) return;
        if (mc.isSameThread()) {
            mc.gui.hud.getChat().addClientSystemMessage(component);
        } else {
            mc.execute(() -> {
                if (dev.opticumeclient.client.modules.other.PanicModule.isSilentModeActive()) return;
                if (mc.gui != null && mc.gui.hud != null && mc.gui.hud.getChat() != null) {
                    mc.gui.hud.getChat().addClientSystemMessage(component);
                }
            });
        }
    }
}
