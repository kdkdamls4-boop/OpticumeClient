package dev.opticumeclient.client.util;

import dev.opticumeclient.mixin.BossHealthOverlayAccessor;
import dev.opticumeclient.mixin.PlayerTabOverlayAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Utility helpers for reading server IP, tab header, scoreboard sidebar title, and PvP bossbar state
 * safely across MC 26.3 screens and services.
 */
public final class ServerDetection {
    private ServerDetection() {
    }

    public static String serverAddress() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return "";
        ServerData server = mc.getCurrentServer();
        if ((server == null || server.ip == null) && mc.getConnection() != null) {
            server = mc.getConnection().getServerData();
        }
        return server != null && server.ip != null ? server.ip.toLowerCase(Locale.ROOT) : "";
    }

    public static boolean addressContains(String... keys) {
        String addr = serverAddress();
        for (String key : keys) {
            if (addr.contains(key)) return true;
        }
        return false;
    }

    public static String tabHeader() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.gui == null || mc.gui.hud == null) return "";
        Object tabList = mc.gui.hud.getTabList();
        if (tabList instanceof PlayerTabOverlayAccessor acc) {
            Component header = acc.opticume$getHeader();
            return header != null ? header.getString() : "";
        }
        return "";
    }

    public static String sidebarTitle() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null) return "";
        Objective obj = mc.level.getScoreboard().getDisplayObjective(DisplaySlot.SIDEBAR);
        return obj != null && obj.getDisplayName() != null ? obj.getDisplayName().getString() : "";
    }

    public static boolean isInPvp() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.gui == null || mc.gui.hud == null || mc.gui.hud.getBossOverlay() == null) return false;
        Object overlay = mc.gui.hud.getBossOverlay();
        if (overlay instanceof BossHealthOverlayAccessor acc) {
            Map<UUID, LerpingBossEvent> events = acc.opticume$getEvents();
            if (events == null || events.isEmpty()) return false;
            for (LerpingBossEvent ev : events.values()) {
                if (ev != null && ev.getName() != null) {
                    String low = ev.getName().getString().toLowerCase(Locale.ROOT);
                    if (low.contains("pvp") || low.contains("пвп")) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public static boolean isFunTime() {
        return addressContains("funtime", "playft");
    }

    public static boolean isSpooky() {
        return addressContains("spooky");
    }

    public static boolean isReallyWorld() {
        return addressContains("reallyworld", "playrw");
    }

    public static boolean isHolyWorld() {
        if (addressContains("holy", "holly", "playhw")) return true;
        String header = tabHeader().toLowerCase(Locale.ROOT);
        return header.contains("holyworld") || header.contains("режимpvp");
    }
}
