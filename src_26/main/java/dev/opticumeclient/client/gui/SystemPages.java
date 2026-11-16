package dev.opticumeclient.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.opticumeclient.client.config.ConfigManager;
import dev.opticumeclient.client.friend.FriendManager;
import dev.opticumeclient.client.render.Render2DHelper;
import dev.opticumeclient.client.render.TextureAssets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.core.UUIDUtil;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Configs and Friends management pages rendered directly inside System ClickGUI's ContentArea.
 */
public final class SystemPages {
    private String configInput = "";
    private boolean configInputFocused = false;
    private float configScroll = 0.0f;
    private float configMaxScroll = 0.0f;

    private String friendInput = "";
    private boolean friendInputFocused = false;
    private float friendScroll = 0.0f;
    private float friendMaxScroll = 0.0f;

    public void unfocusAll() {
        configInputFocused = false;
        friendInputFocused = false;
    }

    public boolean hasFocusedInput() {
        return configInputFocused || friendInputFocused;
    }

    public void renderConfigs(GuiGraphicsExtractor context, float x, float y, float w, float h, int mouseX, int mouseY, float alpha) {
        Font font = Minecraft.getInstance().font;
        float pad = 12.0f;
        float ix = x + pad;
        float iy = y + pad;
        float iw = w - pad * 2.0f;

        // Top Save/Create Bar
        float btnW = 68.0f;
        float barH = 26.0f;
        float boxW = iw - btnW - 8.0f;
        Render2DHelper.drawRoundedRect(context, ix, iy, boxW, barH, 7.0f, SystemTheme.fade(SystemTheme.bg(), alpha));
        Render2DHelper.drawRoundedOutline(context, ix, iy, boxW, barH, 7.0f, 1.0f,
                SystemTheme.fade(configInputFocused ? SystemTheme.primary() : SystemTheme.border(), alpha));

        String caret = configInputFocused && (System.currentTimeMillis() / 500L) % 2L == 0 ? "_" : "";
        String boxTxt = configInput.isEmpty() && !configInputFocused ? "Enter new config name..." : configInput + caret;
        int txtCol = configInput.isEmpty() && !configInputFocused ? SystemTheme.mutedFg() : SystemTheme.foreground();
        Render2DHelper.drawScaledText(context, SystemModuleCard.trimToWidth(font, boxTxt, 0.85f, boxW - 14.0f),
                ix + 8.0f, iy + 9.0f, SystemTheme.fade(txtCol, alpha), 0.85f, false);

        boolean saveHov = isInside(mouseX, mouseY, ix + boxW + 8.0f, iy, btnW, barH);
        Render2DHelper.drawRoundedRect(context, ix + boxW + 8.0f, iy, btnW, barH, 7.0f,
                SystemTheme.fade(saveHov ? SystemTheme.primary() : SystemTheme.elevated(), alpha));
        Render2DHelper.drawRoundedOutline(context, ix + boxW + 8.0f, iy, btnW, barH, 7.0f, 1.0f,
                SystemTheme.fade(SystemTheme.border(), alpha));
        Render2DHelper.drawCenteredText(context, "Save", ix + boxW + 8.0f + btnW * 0.5f, iy + 9.0f,
                SystemTheme.fade(saveHov ? SystemTheme.onPrimary() : SystemTheme.foreground(), alpha), false);

        // Config cards list
        ConfigManager cm = ConfigManager.getInstance();
        List<String> configs = cm.listConfigs();
        float listY = iy + barH + 10.0f;
        float listH = (y + h - pad) - listY;
        float rowH = 38.0f;
        float rowGap = 7.0f;
        float totalH = Math.max(0.0f, configs.size() * (rowH + rowGap) - rowGap);
        configMaxScroll = Math.max(0.0f, totalH - listH);
        configScroll = Mth.clamp(configScroll, 0.0f, configMaxScroll);

        context.enableScissor(Math.round(ix), Math.round(listY), Math.round(ix + iw), Math.round(listY + listH));
        for (int i = 0; i < configs.size(); i++) {
            String name = configs.get(i);
            float ry = listY + i * (rowH + rowGap) - configScroll;
            if (ry + rowH < listY || ry > listY + listH) continue;

            boolean active = name.equalsIgnoreCase(cm.getActiveConfigName());
            boolean auto = name.equalsIgnoreCase(cm.getAutoLoadConfigName());
            boolean builtin = cm.isBuiltInPreset(name);

            Render2DHelper.drawRoundedRect(context, ix, ry, iw, rowH, 8.0f, SystemTheme.fade(SystemTheme.bg(), alpha));
            Render2DHelper.drawRoundedOutline(context, ix, ry, iw, rowH, 8.0f, 1.0f,
                    SystemTheme.fade(active ? SystemTheme.primary() : SystemTheme.border(), alpha));

            Render2DHelper.drawScaledText(context, name, ix + 10.0f, ry + 8.0f,
                    SystemTheme.fade(SystemTheme.foreground(), alpha), 0.92f, false);

            String meta = (active ? "Active" : "Preset") + (auto ? "  •  AutoLoad" : "") + (builtin ? "  •  Built-in" : "");
            Render2DHelper.drawScaledText(context, meta, ix + 10.0f, ry + 21.0f,
                    SystemTheme.fade(active ? SystemTheme.SUCCESS : SystemTheme.mutedFg(), alpha), 0.72f, false);

            // Right action buttons: [Load] [Save] [Auto] [Del]
            float curX = ix + iw - 10.0f;
            float ay = ry + 10.0f;
            float ah = 18.0f;

            if (!"Default".equalsIgnoreCase(name)) {
                curX -= 32.0f;
                drawSmallBtn(context, curX, ay, 32.0f, ah, "Del", mouseX, mouseY, SystemTheme.DANGER, false, alpha);
                curX -= 5.0f;
            }
            curX -= 36.0f;
            drawSmallBtn(context, curX, ay, 36.0f, ah, "Auto", mouseX, mouseY, SystemTheme.FAVORITE_GOLD, auto, alpha);
            curX -= 5.0f;

            curX -= 38.0f;
            drawSmallBtn(context, curX, ay, 38.0f, ah, "Save", mouseX, mouseY, SystemTheme.primary(), false, alpha);
            curX -= 5.0f;

            curX -= 40.0f;
            drawSmallBtn(context, curX, ay, 40.0f, ah, "Load", mouseX, mouseY, SystemTheme.SUCCESS, active, alpha);
        }
        context.disableScissor();
    }

    public boolean clickConfigs(double mx, double my, float x, float y, float w, float h, ClickGuiScreen host) {
        float pad = 12.0f;
        float ix = x + pad;
        float iy = y + pad;
        float iw = w - pad * 2.0f;
        float btnW = 68.0f;
        float barH = 26.0f;
        float boxW = iw - btnW - 8.0f;

        if (isInside(mx, my, ix, iy, boxW, barH)) {
            configInputFocused = true;
            friendInputFocused = false;
            return true;
        }
        if (isInside(mx, my, ix + boxW + 8.0f, iy, btnW, barH)) {
            configInputFocused = false;
            ConfigManager cm = ConfigManager.getInstance();
            String target = configInput.isBlank() ? cm.getActiveConfigName() : configInput.trim();
            cm.saveConfig(target);
            host.showToast("Saved config '" + cm.getActiveConfigName() + "'");
            configInput = "";
            return true;
        }
        configInputFocused = false;

        ConfigManager cm = ConfigManager.getInstance();
        List<String> configs = cm.listConfigs();
        float listY = iy + barH + 10.0f;
        float listH = (y + h - pad) - listY;
        float rowH = 38.0f;
        float rowGap = 7.0f;
        if (isInside(mx, my, ix, listY, iw, listH)) {
            for (int i = 0; i < configs.size(); i++) {
                String name = configs.get(i);
                float ry = listY + i * (rowH + rowGap) - configScroll;
                if (my < ry || my > ry + rowH) continue;
                float curX = ix + iw - 10.0f;
                float ay = ry + 10.0f;
                float ah = 18.0f;

                if (!"Default".equalsIgnoreCase(name)) {
                    curX -= 32.0f;
                    if (isInside(mx, my, curX, ay, 32.0f, ah)) {
                        cm.deleteConfig(name);
                        host.showToast("Deleted '" + name + "'");
                        return true;
                    }
                    curX -= 5.0f;
                }
                curX -= 36.0f;
                if (isInside(mx, my, curX, ay, 36.0f, ah)) {
                    cm.setAutoLoadConfig(name);
                    host.showToast("AutoLoad set to '" + name + "'");
                    return true;
                }
                curX -= 5.0f;

                curX -= 38.0f;
                if (isInside(mx, my, curX, ay, 38.0f, ah)) {
                    cm.saveConfig(name);
                    host.showToast("Updated '" + name + "'");
                    return true;
                }
                curX -= 5.0f;

                curX -= 40.0f;
                if (isInside(mx, my, curX, ay, 40.0f, ah)) {
                    cm.loadConfig(name);
                    host.showToast("Loaded '" + name + "'");
                    return true;
                }
            }
        }
        return false;
    }

    public void renderFriends(GuiGraphicsExtractor context, float x, float y, float w, float h, int mouseX, int mouseY, float alpha) {
        Font font = Minecraft.getInstance().font;
        float pad = 12.0f;
        float ix = x + pad;
        float iy = y + pad;
        float iw = w - pad * 2.0f;

        // Add Friend Bar
        float btnW = 76.0f;
        float barH = 26.0f;
        float boxW = iw - btnW - 8.0f;
        Render2DHelper.drawRoundedRect(context, ix, iy, boxW, barH, 7.0f, SystemTheme.fade(SystemTheme.bg(), alpha));
        Render2DHelper.drawRoundedOutline(context, ix, iy, boxW, barH, 7.0f, 1.0f,
                SystemTheme.fade(friendInputFocused ? SystemTheme.primary() : SystemTheme.border(), alpha));

        String caret = friendInputFocused && (System.currentTimeMillis() / 500L) % 2L == 0 ? "_" : "";
        String txt = friendInput.isEmpty() && !friendInputFocused ? "Enter player nickname..." : friendInput + caret;
        Render2DHelper.drawScaledText(context, SystemModuleCard.trimToWidth(font, txt, 0.85f, boxW - 14.0f),
                ix + 8.0f, iy + 9.0f, SystemTheme.fade(friendInput.isEmpty() && !friendInputFocused ? SystemTheme.mutedFg() : SystemTheme.foreground(), alpha), 0.85f, false);

        boolean addHov = isInside(mouseX, mouseY, ix + boxW + 8.0f, iy, btnW, barH);
        Render2DHelper.drawRoundedRect(context, ix + boxW + 8.0f, iy, btnW, barH, 7.0f,
                SystemTheme.fade(addHov ? SystemTheme.primary() : SystemTheme.elevated(), alpha));
        Render2DHelper.drawRoundedOutline(context, ix + boxW + 8.0f, iy, btnW, barH, 7.0f, 1.0f,
                SystemTheme.fade(SystemTheme.border(), alpha));
        Render2DHelper.drawCenteredText(context, "Add Friend", ix + boxW + 8.0f + btnW * 0.5f, iy + 9.0f,
                SystemTheme.fade(addHov ? SystemTheme.onPrimary() : SystemTheme.foreground(), alpha), false);

        // Two clean sections side by side: Saved Friends + Online Players
        float listY = iy + barH + 10.0f;
        float listH = (y + h - pad) - listY;
        float colGap = 10.0f;
        float colW = (iw - colGap) * 0.5f;

        FriendManager fm = FriendManager.getInstance();
        List<String> friends = new ArrayList<>(fm.getFriends());
        Collections.sort(friends, String.CASE_INSENSITIVE_ORDER);
        List<String> online = getOnlinePlayers();

        // Left: Saved Friends
        Render2DHelper.drawRoundedRect(context, ix, listY, colW, listH, 8.0f, SystemTheme.fade(SystemTheme.bg(), alpha));
        Render2DHelper.drawRoundedOutline(context, ix, listY, colW, listH, 8.0f, 1.0f, SystemTheme.fade(SystemTheme.border(), alpha));
        Render2DHelper.drawScaledText(context, "Saved Friends (" + friends.size() + ")", ix + 10.0f, listY + 8.0f,
                SystemTheme.fade(SystemTheme.mutedFg(), alpha), 0.78f, false);

        float rowH = 26.0f;
        float innerTop = listY + 24.0f;
        float innerH = listH - 28.0f;
        float maxS = Math.max(0.0f, friends.size() * (rowH + 4.0f) - innerH);
        friendMaxScroll = maxS;
        friendScroll = Mth.clamp(friendScroll, 0.0f, maxS);

        context.enableScissor(Math.round(ix + 6), Math.round(innerTop), Math.round(ix + colW - 6), Math.round(innerTop + innerH));
        for (int i = 0; i < friends.size(); i++) {
            String fName = friends.get(i);
            float ry = innerTop + i * (rowH + 4.0f) - friendScroll;
            if (ry + rowH < innerTop || ry > innerTop + innerH) continue;
            Render2DHelper.drawRoundedRect(context, ix + 8.0f, ry, colW - 16.0f, rowH, 6.0f, SystemTheme.fade(SystemTheme.surface(), alpha));
            UUID uuid = UUIDUtil.createOfflinePlayerUUID(fName);
            Render2DHelper.drawPlayerHeadByUuid(context, uuid, Math.round(ix + 13), Math.round(ry + 5), 16, alpha);
            Render2DHelper.drawScaledText(context, SystemModuleCard.trimToWidth(font, fName, 0.82f, colW - 86.0f),
                    ix + 34.0f, ry + 8.5f, SystemTheme.fade(SystemTheme.foreground(), alpha), 0.82f, false);
            drawSmallBtn(context, ix + colW - 44.0f, ry + 5.0f, 30.0f, 16.0f, "Del", mouseX, mouseY, SystemTheme.DANGER, false, alpha);
        }
        context.disableScissor();

        // Right: Online Players
        float rx = ix + colW + colGap;
        Render2DHelper.drawRoundedRect(context, rx, listY, colW, listH, 8.0f, SystemTheme.fade(SystemTheme.bg(), alpha));
        Render2DHelper.drawRoundedOutline(context, rx, listY, colW, listH, 8.0f, 1.0f, SystemTheme.fade(SystemTheme.border(), alpha));
        Render2DHelper.drawScaledText(context, "Online Players (" + online.size() + ")", rx + 10.0f, listY + 8.0f,
                SystemTheme.fade(SystemTheme.mutedFg(), alpha), 0.78f, false);

        context.enableScissor(Math.round(rx + 6), Math.round(innerTop), Math.round(rx + colW - 6), Math.round(innerTop + innerH));
        for (int i = 0; i < online.size(); i++) {
            String pName = online.get(i);
            float ry = innerTop + i * (rowH + 4.0f) - friendScroll;
            if (ry + rowH < innerTop || ry > innerTop + innerH) continue;
            boolean isFr = fm.isFriend(pName);
            Render2DHelper.drawRoundedRect(context, rx + 8.0f, ry, colW - 16.0f, rowH, 6.0f, SystemTheme.fade(SystemTheme.surface(), alpha));
            Render2DHelper.drawPlayerHeadByUuid(context, UUIDUtil.createOfflinePlayerUUID(pName),
                    Math.round(rx + 13), Math.round(ry + 5), 16, alpha);
            Render2DHelper.drawScaledText(context, SystemModuleCard.trimToWidth(font, pName, 0.82f, colW - 92.0f),
                    rx + 34.0f, ry + 8.5f, SystemTheme.fade(SystemTheme.foreground(), alpha), 0.82f, false);
            drawSmallBtn(context, rx + colW - 48.0f, ry + 5.0f, 34.0f, 16.0f,
                    isFr ? "Friend" : "+ Add", mouseX, mouseY, SystemTheme.SUCCESS, isFr, alpha);
        }
        context.disableScissor();
    }

    public boolean clickFriends(double mx, double my, float x, float y, float w, float h, ClickGuiScreen host) {
        float pad = 12.0f;
        float ix = x + pad;
        float iy = y + pad;
        float iw = w - pad * 2.0f;
        float btnW = 76.0f;
        float barH = 26.0f;
        float boxW = iw - btnW - 8.0f;

        if (isInside(mx, my, ix, iy, boxW, barH)) {
            friendInputFocused = true;
            configInputFocused = false;
            return true;
        }
        if (isInside(mx, my, ix + boxW + 8.0f, iy, btnW, barH)) {
            friendInputFocused = false;
            if (!friendInput.isBlank()) {
                FriendManager.getInstance().addFriend(friendInput.trim());
                host.showToast("Added friend '" + friendInput.trim() + "'");
                friendInput = "";
            }
            return true;
        }
        friendInputFocused = false;

        float listY = iy + barH + 10.0f;
        float listH = (y + h - pad) - listY;
        float colGap = 10.0f;
        float colW = (iw - colGap) * 0.5f;
        float rowH = 26.0f;
        float innerTop = listY + 24.0f;

        FriendManager fm = FriendManager.getInstance();
        List<String> friends = new ArrayList<>(fm.getFriends());
        Collections.sort(friends, String.CASE_INSENSITIVE_ORDER);
        if (isInside(mx, my, ix, innerTop, colW, listH - 28.0f)) {
            for (int i = 0; i < friends.size(); i++) {
                float ry = innerTop + i * (rowH + 4.0f) - friendScroll;
                if (isInside(mx, my, ix + colW - 44.0f, ry + 5.0f, 30.0f, 16.0f)) {
                    String rem = friends.get(i);
                    fm.removeFriend(rem);
                    host.showToast("Removed '" + rem + "'");
                    return true;
                }
            }
        }

        float rx = ix + colW + colGap;
        List<String> online = getOnlinePlayers();
        if (isInside(mx, my, rx, innerTop, colW, listH - 28.0f)) {
            for (int i = 0; i < online.size(); i++) {
                float ry = innerTop + i * (rowH + 4.0f) - friendScroll;
                if (isInside(mx, my, rx + colW - 48.0f, ry + 5.0f, 34.0f, 16.0f)) {
                    String pl = online.get(i);
                    fm.toggleFriend(pl);
                    host.showToast(fm.isFriend(pl) ? "Added '" + pl + "'" : "Removed '" + pl + "'");
                    return true;
                }
            }
        }
        return false;
    }

    public boolean scrollActive(boolean isConfigs, double verticalAmount) {
        if (isConfigs) {
            configScroll = Mth.clamp(configScroll - (float) (verticalAmount * 22.0f), 0.0f, configMaxScroll);
            return true;
        }
        friendScroll = Mth.clamp(friendScroll - (float) (verticalAmount * 22.0f), 0.0f, friendMaxScroll);
        return true;
    }

    public boolean keyPressed(int key, boolean isConfigs, ClickGuiScreen host) {
        if (configInputFocused && isConfigs) {
            if (key == InputConstants.KEY_BACKSPACE && !configInput.isEmpty()) {
                configInput = configInput.substring(0, configInput.length() - 1);
                return true;
            }
            if (key == InputConstants.KEY_RETURN && !configInput.isBlank()) {
                ConfigManager.getInstance().saveConfig(configInput.trim());
                host.showToast("Saved '" + ConfigManager.getInstance().getActiveConfigName() + "'");
                configInput = "";
                configInputFocused = false;
                return true;
            }
            if (key == InputConstants.KEY_ESCAPE) {
                configInputFocused = false;
                return true;
            }
        }
        if (friendInputFocused && !isConfigs) {
            if (key == InputConstants.KEY_BACKSPACE && !friendInput.isEmpty()) {
                friendInput = friendInput.substring(0, friendInput.length() - 1);
                return true;
            }
            if (key == InputConstants.KEY_RETURN && !friendInput.isBlank()) {
                FriendManager.getInstance().addFriend(friendInput.trim());
                host.showToast("Added '" + friendInput.trim() + "'");
                friendInput = "";
                friendInputFocused = false;
                return true;
            }
            if (key == InputConstants.KEY_ESCAPE) {
                friendInputFocused = false;
                return true;
            }
        }
        return false;
    }

    public boolean charTyped(String ch, boolean isConfigs) {
        if (configInputFocused && isConfigs && configInput.length() < 24) {
            configInput += ch;
            return true;
        }
        if (friendInputFocused && !isConfigs && ch.matches("[a-zA-Z0-9_]") && friendInput.length() < 16) {
            friendInput += ch;
            return true;
        }
        return false;
    }

    private static List<String> getOnlinePlayers() {
        List<String> out = new ArrayList<>();
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.getConnection() != null) {
            Collection<PlayerInfo> infos = mc.getConnection().getOnlinePlayers();
            String self = mc.player != null ? mc.player.getName().getString() : "";
            for (PlayerInfo pi : infos) {
                if (pi.getProfile() != null && pi.getProfile().name() != null) {
                    String n = pi.getProfile().name();
                    if (!n.equalsIgnoreCase(self)) out.add(n);
                }
            }
            Collections.sort(out, String.CASE_INSENSITIVE_ORDER);
        }
        return out;
    }

    private static void drawSmallBtn(GuiGraphicsExtractor context, float x, float y, float w, float h,
                                     String label, int mouseX, int mouseY, int accent, boolean active, float alpha) {
        boolean hov = isInside(mouseX, mouseY, x, y, w, h);
        int fill = active ? accent : (hov ? SystemTheme.elevated() : SystemTheme.surface());
        int outline = active || hov ? accent : SystemTheme.border();
        Render2DHelper.drawRoundedRect(context, x, y, w, h, 4.0f, SystemTheme.fade(fill, alpha));
        Render2DHelper.drawRoundedOutline(context, x, y, w, h, 4.0f, 1.0f, SystemTheme.fade(outline, alpha));
        int tc = active ? (accent == SystemTheme.primary() ? SystemTheme.onPrimary() : 0xFF0A0A0A)
                : (hov ? accent : SystemTheme.foreground());
        Render2DHelper.drawScaledText(context, label,
                x + (w - Minecraft.getInstance().font.width(label) * 0.72f) * 0.5f,
                y + 5.0f, SystemTheme.fade(tc, alpha), 0.72f, false);
    }

    private static boolean isInside(double mx, double my, float x, float y, float w, float h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }
}
