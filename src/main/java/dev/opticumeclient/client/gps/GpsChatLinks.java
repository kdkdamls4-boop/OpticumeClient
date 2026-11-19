package dev.opticumeclient.client.gps;

import dev.opticumeclient.client.util.ChatUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import org.joml.Vector3i;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns coordinate triplets in incoming chat messages into clickable aqua links that set GPS waypoints.
 */
public final class GpsChatLinks {
    public static final String COMMAND_PREFIX = "opticume_gps ";

    private static final Pattern COORDS = Pattern.compile(
            "(?<![\\d.\\-])(?:x\\s*[:=]?\\s*)?(-?\\d{1,7})(?:\\s*,\\s*|\\s+)(?:y\\s*[:=]?\\s*)?(-?\\d{1,3})(?:\\s*,\\s*|\\s+)(?:z\\s*[:=]?\\s*)?(-?\\d{1,7})(?!\\d|\\.\\d)",
            Pattern.CASE_INSENSITIVE);

    private GpsChatLinks() {
    }

    public static Component rewrite(Component message) {
        if (message == null) return null;
        if (!COORDS.matcher(message.getString()).find()) return message;

        List<Component> parts = new ArrayList<>();
        boolean[] changed = {false};
        message.visit((style, text) -> {
            if (style.getClickEvent() != null) {
                parts.add(Component.literal(text).setStyle(style));
                return Optional.empty();
            }
            Matcher m = COORDS.matcher(text);
            int last = 0;
            while (m.find()) {
                int y = Integer.parseInt(m.group(2));
                if (y < -64 || y > 320) continue;
                if (m.start() > last) {
                    parts.add(Component.literal(text.substring(last, m.start())).setStyle(style));
                }
                String command = COMMAND_PREFIX + m.group(1) + " " + y + " " + m.group(3);
                Style link = style
                        .withColor(ChatFormatting.AQUA)
                        .withUnderlined(true)
                        .withClickEvent(new ClickEvent.RunCommand(command))
                        .withHoverEvent(new HoverEvent.ShowText(Component.literal("Нажми, чтобы поставить GPS-метку")));
                parts.add(Component.literal(m.group()).setStyle(link));
                last = m.end();
                changed[0] = true;
            }
            if (last < text.length()) {
                parts.add(Component.literal(text.substring(last)).setStyle(style));
            }
            return Optional.empty();
        }, Style.EMPTY);

        if (!changed[0]) return message;
        MutableComponent result = Component.empty();
        parts.forEach(result::append);
        return result;
    }

    public static boolean handleClick(ClickEvent event) {
        if (!(event instanceof ClickEvent.RunCommand run)) return false;
        String command = run.command();
        if (!command.startsWith(COMMAND_PREFIX)) return false;
        try {
            String[] p = command.substring(COMMAND_PREFIX.length()).trim().split("\\s+");
            if (p.length < 3) return true;
            int x = Integer.parseInt(p[0]);
            int y = Integer.parseInt(p[1]);
            int z = Integer.parseInt(p[2]);
            GpsManager.getInstance().setGpsName("GPS");
            GpsManager.getInstance().setGpsPosition(new Vector3i(x, y, z));
            ChatUtils.sendClientMessage("Метка установлена: " + x + " " + y + " " + z + ".");
        } catch (RuntimeException ignored) {
        }
        return true;
    }
}
