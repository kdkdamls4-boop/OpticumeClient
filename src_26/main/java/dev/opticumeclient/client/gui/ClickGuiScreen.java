package dev.opticumeclient.client.gui;

import com.google.gson.JsonArray;
import com.mojang.blaze3d.platform.InputConstants;
import dev.opticumeclient.client.config.ConfigManager;
import dev.opticumeclient.client.modules.Category;
import dev.opticumeclient.client.modules.Module;
import dev.opticumeclient.client.modules.ModuleManager;
import dev.opticumeclient.client.render.ColorUtils;
import dev.opticumeclient.client.render.Render2DHelper;
import dev.opticumeclient.client.render.TextureAssets;
import dev.opticumeclient.client.render.ThemeManager;
import dev.opticumeclient.client.setting.ColorSetting;
import dev.opticumeclient.client.setting.NumberSetting;
import dev.opticumeclient.client.ui.menu.MenuConfigStore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * OpticumeClient ClickGUI ported from System:
 * <ul>
 *   <li>Draggable rounded window with animated Dark/Light {@link SystemTheme}</li>
 *   <li>Collapsible Left SideBar (38px &lt;-&gt; 148px) with All, Favorites, Categories, Configs, Friends, HUD Editor &amp; Spotlight Search</li>
 *   <li>36px TopBar with sidebar collapse, Collapse/Expand All, Bound filter, Accent Theme cycler &amp; Sun/Moon toggle</li>
 *   <li>3-column shortest-column masonry grid of {@link SystemModuleCard} widgets + custom scrollbar</li>
 *   <li>Centered Spotlight {@link SystemSearchModal} (Ctrl+F) and dedicated HUD Editor mode</li>
 * </ul>
 */
public class ClickGuiScreen extends Screen {
    public static float WINDOW_WIDTH = 790.0f;
    public static float WINDOW_HEIGHT = 475.0f;
    private static final float SIDEBAR_EXPANDED_W = 148.0f;
    private static final float SIDEBAR_COLLAPSED_W = 38.0f;
    private static final float TOPBAR_H = 36.0f;

    private enum NavPage {
        ALL_MODULES,
        FAVORITES,
        CATEGORY,
        CONFIGS,
        FRIENDS
    }

    public static final class HoverTarget {
        public String title;
        public String description;
    }

    // Persistent state across GUI openings
    private static NavPage activePage = NavPage.CATEGORY;
    private static Category selectedCategory = Category.COMBAT;
    private static boolean sidebarCollapsed = false;
    private static boolean sidebarLoaded = false;
    private static boolean favoritesLoaded = false;
    private static final Set<String> FAVORITE_MODULES = new HashSet<>();
    private static float windowOffsetX = 0.0f;
    private static float windowOffsetY = 0.0f;

    // Live bounds synced every frame for HudManager hit-exclusion
    private static float lastWinX = -1.0f;
    private static float lastWinY = -1.0f;
    private static float lastWinW = WINDOW_WIDTH;
    private static float lastWinH = WINDOW_HEIGHT;
    private static boolean lastHudEditorOnly = false;

    private final Map<Module, SystemModuleCard> cardMap = new HashMap<>();
    private final SystemSearchModal searchModal = new SystemSearchModal();
    private final SystemPages pages = new SystemPages();
    private final HoverTarget hoverTarget = new HoverTarget();

    private float sidebarWidth = SIDEBAR_EXPANDED_W;
    private float targetScroll = 0.0f;
    private float currentScroll = 0.0f;
    private float maxScroll = 0.0f;
    private Module pendingJumpModule = null;
    private float openProgress = 0.0f;
    private boolean closing = false;
    private boolean hudEditorMode = false;
    private boolean boundOnly = false;

    private boolean draggingWindow = false;
    private double dragStartMouseX, dragStartMouseY;
    private float dragStartOffX, dragStartOffY;
    private boolean draggingScrollbar = false;

    private Module bindingModule = null;
    private NumberSetting draggingSlider = null;
    private float draggingSliderX = 0.0f;
    private float draggingSliderW = 1.0f;

    private ColorSetting draggingColor = null;
    private int colorDragMode = 0;
    private float colorBoxX, colorBoxY, colorBoxW, colorBoxH;

    private String toastText = "";
    private long toastTimestamp = 0L;
    private long lastNanos = System.nanoTime();

    public ClickGuiScreen() {
        super(Component.literal("OpticumeClient ClickGUI"));
        ensureLoaded();
        this.sidebarWidth = sidebarCollapsed ? SIDEBAR_COLLAPSED_W : SIDEBAR_EXPANDED_W;
        for (Module m : ModuleManager.getInstance().getModules()) {
            cardMap.put(m, new SystemModuleCard(m));
        }
    }

    private static void ensureLoaded() {
        SystemTheme.ensureLoaded();
        if (!sidebarLoaded) {
            sidebarLoaded = true;
            sidebarCollapsed = MenuConfigStore.getBoolean("clickGuiSidebarCollapsed", false);
        }
        if (!favoritesLoaded) {
            favoritesLoaded = true;
            FAVORITE_MODULES.clear();
            FAVORITE_MODULES.addAll(MenuConfigStore.getStringSet("clickGuiFavorites"));
        }
    }

    public static int normalizeMouseButton(int rawButton) {
        if (rawButton == InputConstants.MOUSE_BUTTON_LEFT) return 0;
        if (rawButton == InputConstants.MOUSE_BUTTON_RIGHT) return 1;
        if (rawButton == InputConstants.MOUSE_BUTTON_MIDDLE) return 2;
        return rawButton;
    }

    public static boolean isMouseOverWindow(float mouseX, float mouseY) {
        if (lastHudEditorOnly) return false;
        return mouseX >= lastWinX && mouseX <= lastWinX + lastWinW
                && mouseY >= lastWinY && mouseY <= lastWinY + lastWinH;
    }

    @Override
    protected void init() {
        super.init();
        lastNanos = System.nanoTime();
        openProgress = 0.0f;
        closing = false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        // Rendered inside extractRenderState
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        TextureAssets.ensureInitialized();
        hoverTarget.title = null;
        hoverTarget.description = null;

        long now = System.nanoTime();
        float dt = Math.min(0.05f, Math.max(0.0001f, (now - lastNanos) / 1.0e9f));
        lastNanos = now;

        SystemTheme.update(dt);
        searchModal.update(dt);

        float openTarget = closing ? 0.0f : 1.0f;
        openProgress += (openTarget - openProgress) * (1.0f - (float) Math.exp(-16.0f * dt));
        if (closing && openProgress < 0.05f) {
            if (minecraft != null && minecraft.gui != null) {
                minecraft.gui.setScreen(null);
            }
            return;
        }

        lastHudEditorOnly = hudEditorMode;
        if (hudEditorMode) {
            renderHudEditorBar(context, mouseX, mouseY);
            return;
        }

        float targetSideW = sidebarCollapsed ? SIDEBAR_COLLAPSED_W : SIDEBAR_EXPANDED_W;
        sidebarWidth += (targetSideW - sidebarWidth) * (1.0f - (float) Math.exp(-15.0f * dt));
        currentScroll += (targetScroll - currentScroll) * (1.0f - (float) Math.exp(-16.0f * dt));

        // Responsive window sizing that keeps System look intact across resolutions
        float winW = Math.min(790.0f, Math.max(480.0f, this.width - 28.0f));
        float winH = Math.min(475.0f, Math.max(280.0f, this.height - 28.0f));
        float maxOffX = Math.max(0.0f, (this.width - winW) * 0.5f - 4.0f);
        float maxOffY = Math.max(0.0f, (this.height - winH) * 0.5f - 4.0f);
        windowOffsetX = Mth.clamp(windowOffsetX, -maxOffX, maxOffX);
        windowOffsetY = Mth.clamp(windowOffsetY, -maxOffY, maxOffY);

        float winX = (this.width - winW) * 0.5f + windowOffsetX;
        float winY = (this.height - winH) * 0.5f + windowOffsetY;
        lastWinX = winX;
        lastWinY = winY;
        lastWinW = winW;
        lastWinH = winH;
        WINDOW_WIDTH = winW;
        WINDOW_HEIGHT = winH;

        float alpha = Mth.clamp(openProgress, 0.0f, 1.0f);

        // Soft backdrop vignette + System window shell
        Render2DHelper.drawRect(context, 0, 0, this.width, this.height, SystemTheme.fade(0xFF000000, alpha * 0.35f));
        Render2DHelper.drawGlow(context, winX, winY + 3.0f, winW, winH, 12.0f, SystemTheme.fade(0xFF000000, alpha * 0.55f));
        Render2DHelper.drawRoundedRect(context, winX, winY, winW, winH, 10.0f, SystemTheme.fade(SystemTheme.surface(), alpha));

        renderSideBar(context, winX, winY, sidebarWidth, winH, mouseX, mouseY, alpha);

        // Vertical 1px divider between SideBar & Right Pane
        float divX = winX + sidebarWidth;
        Render2DHelper.drawRect(context, divX, winY + 1.0f, 1.0f, winH - 2.0f, SystemTheme.fade(SystemTheme.border(), alpha));

        float rightX = divX + 1.0f;
        float rightW = winW - sidebarWidth - 1.0f;
        renderTopBar(context, rightX, winY, rightW, TOPBAR_H, mouseX, mouseY, alpha);

        // Horizontal 1px divider below TopBar
        Render2DHelper.drawRect(context, rightX, winY + TOPBAR_H, rightW, 1.0f, SystemTheme.fade(SystemTheme.border(), alpha));

        float contentY = winY + TOPBAR_H + 1.0f;
        float contentH = winH - TOPBAR_H - 1.0f;
        renderContentArea(context, dt, rightX, contentY, rightW, contentH, mouseX, mouseY, alpha);

        // Window crisp 1px outer border
        Render2DHelper.drawRoundedOutline(context, winX, winY, winW, winH, 10.0f, 1.0f,
                SystemTheme.fade(SystemTheme.border(), alpha));

        // Spotlight Search Modal overlay
        searchModal.render(context, winX, winY, winW, winH, this.width, this.height, mouseX, mouseY);

        // Floating tooltip
        if (!searchModal.isOpen() && hoverTarget.title != null && hoverTarget.description != null) {
            renderTooltip(context, hoverTarget.title, hoverTarget.description, mouseX, mouseY, alpha);
        }
    }

    private void renderSideBar(GuiGraphicsExtractor context, float sx, float sy, float sw, float sh,
                               int mouseX, int mouseY, float alpha) {
        float expandRatio = Mth.clamp((sw - SIDEBAR_COLLAPSED_W) / (SIDEBAR_EXPANDED_W - SIDEBAR_COLLAPSED_W), 0.0f, 1.0f);

        context.enableScissor(Math.round(sx), Math.round(sy), Math.round(sx + sw), Math.round(sy + sh));

        // Brand header
        Render2DHelper.drawTexture(context, TextureAssets.MINI_LOGO, sx + 10.0f, sy + 9.0f, 18.0f, 18.0f,
                ColorUtils.withAlphaFactor(0xFFFFFFFF, alpha));
        if (expandRatio > 0.15f) {
            Render2DHelper.drawScaledText(context, "Opticume", sx + 34.0f, sy + 11.0f,
                    SystemTheme.fade(SystemTheme.foreground(), alpha * expandRatio), 0.96f, false);
            Render2DHelper.drawScaledText(context, "26.3", sx + 34.0f, sy + 22.0f,
                    SystemTheme.fade(SystemTheme.mutedFg(), alpha * expandRatio), 0.68f, false);
        }

        // Scrollable/compact navigation items
        float curY = sy + TOPBAR_H + 8.0f;
        if (expandRatio > 0.25f) {
            Render2DHelper.drawScaledText(context, "CATEGORIES", sx + 10.0f, curY,
                    SystemTheme.fade(SystemTheme.mutedFg(), alpha * expandRatio * 0.85f), 0.68f, false);
            curY += 12.0f;
        } else {
            curY += 4.0f;
        }

        curY = renderSideItem(context, sx, curY, sw, "All Modules", TextureAssets.ICON_ALL,
                activePage == NavPage.ALL_MODULES, expandRatio, mouseX, mouseY, alpha);
        curY = renderSideItem(context, sx, curY, sw, "Favorites", TextureAssets.ICON_FAVORITES,
                activePage == NavPage.FAVORITES, expandRatio, mouseX, mouseY, alpha);

        for (Category cat : Category.values()) {
            if (cat == Category.FRIENDS) continue;
            boolean act = (activePage == NavPage.CATEGORY && selectedCategory == cat);
            curY = renderSideItem(context, sx, curY, sw, cat.getDisplayName(), getCategoryIcon(cat),
                    act, expandRatio, mouseX, mouseY, alpha);
        }

        // Divider before System pages
        curY += 4.0f;
        Render2DHelper.drawRect(context, sx + 8.0f, curY, sw - 16.0f, 1.0f, SystemTheme.fade(SystemTheme.border(), alpha));
        curY += 6.0f;

        if (expandRatio > 0.25f) {
            Render2DHelper.drawScaledText(context, "SYSTEM", sx + 10.0f, curY,
                    SystemTheme.fade(SystemTheme.mutedFg(), alpha * expandRatio * 0.85f), 0.68f, false);
            curY += 12.0f;
        }

        curY = renderSideItem(context, sx, curY, sw, "Configs", TextureAssets.ICON_CONFIGS,
                activePage == NavPage.CONFIGS, expandRatio, mouseX, mouseY, alpha);
        curY = renderSideItem(context, sx, curY, sw, "Friends", TextureAssets.ICON_FRIENDS,
                activePage == NavPage.FRIENDS, expandRatio, mouseX, mouseY, alpha);
        renderSideItem(context, sx, curY, sw, "HUD Editor", TextureAssets.ICON_HUD_EDITOR,
                hudEditorMode, expandRatio, mouseX, mouseY, alpha);

        // Bottom Search Pill & User Profile card
        float footH = 26.0f;
        float searchH = 22.0f;
        float footY = sy + sh - footH - 8.0f;
        float searchY = footY - searchH - 6.0f;

        boolean sHov = !searchModal.isOpen() && isInside(mouseX, mouseY, sx + 7.0f, searchY, sw - 14.0f, searchH);
        Render2DHelper.drawRoundedRect(context, sx + 7.0f, searchY, sw - 14.0f, searchH, 6.0f,
                SystemTheme.fade(sHov ? SystemTheme.elevated() : SystemTheme.bg(), alpha));
        Render2DHelper.drawRoundedOutline(context, sx + 7.0f, searchY, sw - 14.0f, searchH, 6.0f, 1.0f,
                SystemTheme.fade(SystemTheme.border(), alpha));
        Render2DHelper.drawTexture(context, TextureAssets.ICON_SEARCH, sx + 13.0f, searchY + 6.0f, 10.0f, 10.0f,
                SystemTheme.fade(SystemTheme.mutedFg(), alpha));
        if (expandRatio > 0.2f) {
            Render2DHelper.drawScaledText(context, "Search", sx + 28.0f, searchY + 7.5f,
                    SystemTheme.fade(SystemTheme.mutedFg(), alpha * expandRatio), 0.78f, false);
            Render2DHelper.drawScaledText(context, "Ctrl+F", sx + sw - 38.0f, searchY + 8.0f,
                    SystemTheme.fade(SystemTheme.mutedFg(), alpha * expandRatio * 0.65f), 0.64f, false);
        }

        // User Profile chip
        String uname = (minecraft != null && minecraft.getUser() != null) ? minecraft.getUser().getName() : "Player";
        Render2DHelper.drawRoundedRect(context, sx + 7.0f, footY, sw - 14.0f, footH, 6.0f,
                SystemTheme.fade(SystemTheme.bg(), alpha));
        Render2DHelper.drawRoundedOutline(context, sx + 7.0f, footY, sw - 14.0f, footH, 6.0f, 1.0f,
                SystemTheme.fade(SystemTheme.border(), alpha));
        Render2DHelper.drawPlayerHeadByUuid(context, UUIDUtil.createOfflinePlayerUUID(uname),
                Math.round(sx + 11), Math.round(footY + 5), 16, alpha);
        if (expandRatio > 0.2f) {
            Render2DHelper.drawScaledText(context, Render2DHelper.trimToWidth(uname, 0.80f, sw - 46.0f),
                    sx + 32.0f, footY + 5.5f, SystemTheme.fade(SystemTheme.foreground(), alpha * expandRatio), 0.80f, false);
            Render2DHelper.drawScaledText(context, ConfigManager.getInstance().getActiveConfigName(),
                    sx + 32.0f, footY + 15.0f, SystemTheme.fade(SystemTheme.mutedFg(), alpha * expandRatio), 0.66f, false);
        }

        context.disableScissor();
    }

    private float renderSideItem(GuiGraphicsExtractor context, float sx, float y, float sw, String label,
                                 Identifier icon, boolean active, float expandRatio, int mouseX, int mouseY, float alpha) {
        float itemH = 22.0f;
        float ix = sx + 7.0f;
        float iw = sw - 14.0f;
        boolean hov = !searchModal.isOpen() && isInside(mouseX, mouseY, ix, y, iw, itemH);

        if (active || hov) {
            int fill = active ? SystemTheme.elevated() : ColorUtils.withAlphaFactor(SystemTheme.elevated(), 0.55f);
            Render2DHelper.drawRoundedRect(context, ix, y, iw, itemH, 6.0f, SystemTheme.fade(fill, alpha));
            if (active) {
                Render2DHelper.drawRoundedOutline(context, ix, y, iw, itemH, 6.0f, 1.0f,
                        SystemTheme.fade(SystemTheme.border(), alpha));
            }
        }

        int iconCol = active ? SystemTheme.foreground() : SystemTheme.mutedFg();
        Render2DHelper.drawTexture(context, icon, ix + 6.5f, y + 5.5f, 11.0f, 11.0f, SystemTheme.fade(iconCol, alpha));
        if (expandRatio > 0.15f) {
            Render2DHelper.drawScaledText(context, label, ix + 24.0f, y + 7.5f,
                    SystemTheme.fade(iconCol, alpha * expandRatio), 0.82f, false);
        }
        return y + itemH + 2.0f;
    }

    private void renderTopBar(GuiGraphicsExtractor context, float tx, float ty, float tw, float th,
                              int mouseX, int mouseY, float alpha) {
        // Left: Collapse SideBar icon button + Page Title + Module count / toast
        float btnSize = 20.0f;
        float sideBtnX = tx + 10.0f;
        float btnY = ty + (th - btnSize) * 0.5f;
        drawIconToolButton(context, sideBtnX, btnY, btnSize, TextureAssets.ICON_SIDEBAR, mouseX, mouseY, sidebarCollapsed, alpha);
        if (isInside(mouseX, mouseY, sideBtnX, btnY, btnSize, btnSize)) {
            hoverTarget.title = sidebarCollapsed ? "Expand Sidebar" : "Collapse Sidebar";
            hoverTarget.description = sidebarCollapsed ? "Expand sidebar navigation panel" : "Collapse sidebar to compact icon bar";
        }

        String title = getCurrentPageTitle();
        float titleX = sideBtnX + btnSize + 10.0f;
        Render2DHelper.drawScaledText(context, title, titleX, ty + 13.0f,
                SystemTheme.fade(SystemTheme.foreground(), alpha), 1.02f, false);

        float badgeX = titleX + Render2DHelper.getTextWidth(title, 1.02f) + 8.0f;
        if (!toastText.isEmpty() && System.currentTimeMillis() - toastTimestamp < 2600L) {
            float pillW = Render2DHelper.getTextWidth(toastText, 0.72f) + 12.0f;
            Render2DHelper.drawRoundedRect(context, badgeX, ty + 10.0f, pillW, 15.0f, 4.0f,
                    SystemTheme.fade(SystemTheme.elevated(), alpha));
            Render2DHelper.drawScaledText(context, toastText, badgeX + 6.0f, ty + 14.0f,
                    SystemTheme.fade(SystemTheme.SUCCESS, alpha), 0.72f, false);
        } else if (isModulePage()) {
            String cnt = getDisplayedModules().size() + " modules";
            float pw = Render2DHelper.getTextWidth(cnt, 0.72f) + 10.0f;
            Render2DHelper.drawRoundedRect(context, badgeX, ty + 10.5f, pw, 14.0f, 4.0f,
                    SystemTheme.fade(SystemTheme.elevated(), alpha));
            Render2DHelper.drawScaledText(context, cnt, badgeX + 5.0f, ty + 14.0f,
                    SystemTheme.fade(SystemTheme.mutedFg(), alpha), 0.72f, false);
        }

        // Right toolbar controls: [Accent Theme] [Bound Only] [Collapse All] [Sun/Moon]
        float rx = tx + tw - 10.0f - btnSize;
        Identifier themeIcon = SystemTheme.isDark() ? TextureAssets.ICON_SUN : TextureAssets.ICON_MOON;
        drawIconToolButton(context, rx, btnY, btnSize, themeIcon, mouseX, mouseY, false, alpha);
        if (isInside(mouseX, mouseY, rx, btnY, btnSize, btnSize)) {
            hoverTarget.title = SystemTheme.isDark() ? "Light Theme" : "Dark Theme";
            hoverTarget.description = "Switch between System Dark and Light ClickGUI palettes";
        }

        rx -= (btnSize + 5.0f);
        drawIconToolButton(context, rx, btnY, btnSize, TextureAssets.ICON_COLLAPSE, mouseX, mouseY, false, alpha);
        if (isInside(mouseX, mouseY, rx, btnY, btnSize, btnSize)) {
            hoverTarget.title = "Collapse / Expand";
            hoverTarget.description = "Toggle settings expansion on all cards in this view";
        }

        rx -= (48.0f + 5.0f);
        boolean bHov = isInside(mouseX, mouseY, rx, btnY, 48.0f, btnSize);
        Render2DHelper.drawRoundedRect(context, rx, btnY, 48.0f, btnSize, 5.0f,
                SystemTheme.fade(boundOnly ? SystemTheme.primary() : (bHov ? SystemTheme.elevated() : SystemTheme.bg()), alpha));
        Render2DHelper.drawRoundedOutline(context, rx, btnY, 48.0f, btnSize, 5.0f, 1.0f,
                SystemTheme.fade(boundOnly ? SystemTheme.primary() : SystemTheme.border(), alpha));
        Render2DHelper.drawScaledText(context, "Bound", rx + 11.0f, btnY + 6.5f,
                SystemTheme.fade(boundOnly ? SystemTheme.onPrimary() : SystemTheme.foreground(), alpha), 0.74f, false);

        // Client HUD/Visuals Accent Palette Switcher pill
        String themeName = ThemeManager.getInstance().getCurrentTheme().getName();
        float accentPillW = Math.min(92.0f, Render2DHelper.getTextWidth(themeName, 0.70f) + 20.0f);
        rx -= (accentPillW + 5.0f);
        boolean aHov = isInside(mouseX, mouseY, rx, btnY, accentPillW, btnSize);
        Render2DHelper.drawRoundedRect(context, rx, btnY, accentPillW, btnSize, 5.0f,
                SystemTheme.fade(aHov ? SystemTheme.elevated() : SystemTheme.bg(), alpha));
        Render2DHelper.drawRoundedOutline(context, rx, btnY, accentPillW, btnSize, 5.0f, 1.0f,
                SystemTheme.fade(SystemTheme.border(), alpha));
        Render2DHelper.drawCircle(context, rx + 8.0f, btnY + btnSize * 0.5f, 3.5f,
                ColorUtils.withAlphaFactor(ThemeManager.getInstance().getAccentColor(0), alpha));
        Render2DHelper.drawScaledText(context, Render2DHelper.trimToWidth(themeName, 0.70f, accentPillW - 18.0f),
                rx + 14.0f, btnY + 6.5f, SystemTheme.fade(SystemTheme.foreground(), alpha), 0.70f, false);
        if (isInside(mouseX, mouseY, rx, btnY, accentPillW, btnSize)) {
            hoverTarget.title = "HUD & Visual Accent";
            hoverTarget.description = "Cycle OpticumeClient color accent: " + themeName;
        }
    }

    private void renderContentArea(GuiGraphicsExtractor context, float dt, float cx, float cy, float cw, float ch,
                                   int mouseX, int mouseY, float alpha) {
        if (activePage == NavPage.CONFIGS) {
            pages.renderConfigs(context, cx, cy, cw, ch, mouseX, mouseY, alpha);
            return;
        }
        if (activePage == NavPage.FRIENDS) {
            pages.renderFriends(context, cx, cy, cw, ch, mouseX, mouseY, alpha);
            return;
        }

        List<Module> modules = getDisplayedModules();
        float pad = 11.0f;
        float gap = 8.0f;
        int cols = cw >= 420.0f ? 3 : 2;
        float colW = (cw - pad * 2.0f - gap * (cols - 1)) / cols;
        float[] colHeights = new float[cols];
        for (int c = 0; c < cols; c++) colHeights[c] = 0.0f;

        boolean allowHover = !searchModal.isOpen() && isInside(mouseX, mouseY, cx, cy, cw, ch);

        for (Module m : modules) {
            SystemModuleCard card = cardMap.get(m);
            if (card == null) continue;
            int minCol = 0;
            for (int c = 1; c < cols; c++) {
                if (colHeights[c] < colHeights[minCol]) minCol = c;
            }
            card.width = colW;
            card.x = cx + pad + minCol * (colW + gap);
            card.y = cy + pad + colHeights[minCol] - currentScroll;
            card.updateAnimations(dt, mouseX, mouseY, allowHover);
            colHeights[minCol] += card.computeHeight() + gap;
        }

        float tallest = 0.0f;
        for (float h : colHeights) tallest = Math.max(tallest, h);
        maxScroll = Math.max(0.0f, tallest - gap + pad * 2.0f - ch);

        if (pendingJumpModule != null) {
            SystemModuleCard targetCard = cardMap.get(pendingJumpModule);
            if (targetCard != null) {
                // targetCard.y was calculated as: cy + pad + colTop - currentScroll
                // So the content top position of the card is: targetCard.y + currentScroll - cy
                float cardTopInContent = targetCard.y + currentScroll - cy;
                targetScroll = Mth.clamp(cardTopInContent - pad, 0.0f, maxScroll);
            }
            pendingJumpModule = null;
        }

        targetScroll = Mth.clamp(targetScroll, 0.0f, maxScroll);
        currentScroll = Mth.clamp(currentScroll, 0.0f, maxScroll);

        context.enableScissor(Math.round(cx), Math.round(cy), Math.round(cx + cw), Math.round(cy + ch));
        if (modules.isEmpty()) {
            Render2DHelper.drawCenteredText(context,
                    activePage == NavPage.FAVORITES ? "Star modules with ☆ to add Favorites" : "No modules match filter",
                    cx + cw * 0.5f, cy + ch * 0.45f, SystemTheme.fade(SystemTheme.mutedFg(), alpha), false);
        } else {
            for (Module m : modules) {
                SystemModuleCard card = cardMap.get(m);
                if (card == null) continue;
                float cardH = card.computeHeight();
                if (card.y + cardH < cy || card.y > cy + ch) continue;
                card.render(context, dt, mouseX, mouseY, alpha,
                        FAVORITE_MODULES.contains(m.getName()), m == bindingModule, hoverTarget);
            }
        }
        context.disableScissor();

        // Slim System Scrollbar
        if (maxScroll > 1.0f) {
            float trackH = ch - 12.0f;
            float viewRatio = ch / (ch + maxScroll);
            float thumbH = Math.max(22.0f, trackH * viewRatio);
            float thumbY = cy + 6.0f + (currentScroll / maxScroll) * (trackH - thumbH);
            float sbX = cx + cw - 5.0f;
            Render2DHelper.drawRoundedRect(context, sbX, thumbY, 2.5f, thumbH, 1.2f,
                    SystemTheme.fade(SystemTheme.border(), alpha));
        }
    }

    private void renderHudEditorBar(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        float barW = 360.0f;
        float barH = 26.0f;
        float bx = (this.width - barW) * 0.5f;
        float by = 10.0f;
        Render2DHelper.drawRoundedRect(context, bx, by, barW, barH, 8.0f, SystemTheme.fade(SystemTheme.surface(), 0.94f));
        Render2DHelper.drawRoundedOutline(context, bx, by, barW, barH, 8.0f, 1.0f, SystemTheme.border());
        Render2DHelper.drawScaledText(context, "HUD Editor  •  Drag widgets cleanly", bx + 12.0f, by + 9.0f,
                SystemTheme.foreground(), 0.82f, false);

        float doneW = 54.0f;
        float resetW = 74.0f;
        float dx = bx + barW - doneW - 6.0f;
        float rx = bx + barW - doneW - resetW - 12.0f;
        float dy = by + 4.0f;

        // Reset HUD button
        boolean hovReset = isInside(mouseX, mouseY, rx, dy, resetW, 18.0f);
        Render2DHelper.drawRoundedRect(context, rx, dy, resetW, 18.0f, 5.0f,
                hovReset ? SystemTheme.DANGER : SystemTheme.elevated());
        Render2DHelper.drawCenteredText(context, "Reset HUD", rx + resetW * 0.5f, dy + 5.0f,
                hovReset ? SystemTheme.onPrimary() : SystemTheme.foreground(), false);

        // Done button
        boolean hov = isInside(mouseX, mouseY, dx, dy, doneW, 18.0f);
        Render2DHelper.drawRoundedRect(context, dx, dy, doneW, 18.0f, 5.0f,
                hov ? SystemTheme.primary() : SystemTheme.elevated());
        Render2DHelper.drawCenteredText(context, "Done", dx + doneW * 0.5f, dy + 5.0f,
                hov ? SystemTheme.onPrimary() : SystemTheme.foreground(), false);
    }

    private void renderTooltip(GuiGraphicsExtractor context, String title, String desc, int mouseX, int mouseY, float alpha) {
        float maxW = 200.0f;
        float maxTextW = maxW - 14.0f;

        // Word-wrap description to fit within maxTextW at 0.70f scale
        List<String> lines = new ArrayList<>();
        if (desc != null && !desc.isEmpty()) {
            String[] words = desc.split(" ");
            StringBuilder sb = new StringBuilder();
            for (String w : words) {
                String test = sb.length() == 0 ? w : sb + " " + w;
                if (Render2DHelper.getTextWidth(test, 0.70f) > maxTextW) {
                    if (sb.length() > 0) {
                        lines.add(sb.toString());
                        sb.setLength(0);
                    }
                    sb.append(w);
                } else {
                    sb = new StringBuilder(test);
                }
            }
            if (sb.length() > 0) {
                lines.add(sb.toString());
            }
        }
        if (lines.isEmpty()) {
            lines.add(desc != null ? desc : "");
        }

        float longestLineW = 0.0f;
        for (String line : lines) {
            float lw = Render2DHelper.getTextWidth(line, 0.70f);
            if (lw > longestLineW) longestLineW = lw;
        }

        float tw = Math.min(maxW, Math.max(Render2DHelper.getTextWidth(title, 0.80f), longestLineW) + 14.0f);
        float th = 18.0f + lines.size() * 9.5f;
        float tx = Math.min(this.width - tw - 6.0f, Math.max(6.0f, mouseX + 10.0f));
        float ty = Math.min(this.height - th - 6.0f, Math.max(6.0f, mouseY + 12.0f));

        Render2DHelper.drawRoundedRect(context, tx, ty, tw, th, 6.0f, SystemTheme.fade(SystemTheme.bg(), alpha * 0.96f));
        Render2DHelper.drawRoundedOutline(context, tx, ty, tw, th, 6.0f, 1.0f, SystemTheme.fade(SystemTheme.border(), alpha));
        Render2DHelper.drawScaledText(context, Render2DHelper.trimToWidth(title, 0.80f, tw - 12.0f),
                tx + 7.0f, ty + 5.0f, SystemTheme.fade(SystemTheme.foreground(), alpha), 0.80f, false);

        for (int i = 0; i < lines.size(); i++) {
            Render2DHelper.drawScaledText(context, lines.get(i),
                    tx + 7.0f, ty + 16.0f + i * 9.5f, SystemTheme.fade(SystemTheme.mutedFg(), alpha), 0.70f, false);
        }
    }

    private static void drawIconToolButton(GuiGraphicsExtractor context, float x, float y, float size,
                                           Identifier icon, int mouseX, int mouseY, boolean active, float alpha) {
        boolean hov = isInside(mouseX, mouseY, x, y, size, size);
        int bg = active ? SystemTheme.elevated() : (hov ? SystemTheme.elevated() : SystemTheme.bg());
        Render2DHelper.drawRoundedRect(context, x, y, size, size, 5.0f, SystemTheme.fade(bg, alpha));
        Render2DHelper.drawRoundedOutline(context, x, y, size, size, 5.0f, 1.0f,
                SystemTheme.fade(active ? SystemTheme.primary() : SystemTheme.border(), alpha));
        Render2DHelper.drawTexture(context, icon, x + 4.5f, y + 4.5f, size - 9.0f, size - 9.0f,
                SystemTheme.fade(hov || active ? SystemTheme.foreground() : SystemTheme.mutedFg(), alpha));
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        double mx = click.x();
        double my = click.y();
        int button = normalizeMouseButton(click.button());

        if (hudEditorMode) {
            float barW = 360.0f;
            float bx = (this.width - barW) * 0.5f;
            float doneW = 54.0f;
            float resetW = 74.0f;
            float dx = bx + barW - doneW - 6.0f;
            float rx = bx + barW - doneW - resetW - 12.0f;

            if (isInside(mx, my, rx, 14.0f, resetW, 18.0f)) {
                playClick();
                dev.opticumeclient.client.hud.HudManager.getInstance().resetAllLayouts();
                showToast("HUD layout reset to defaults");
                return true;
            }
            if (isInside(mx, my, dx, 14.0f, doneW, 18.0f)) {
                playClick();
                hudEditorMode = false;
                return true;
            }
            return super.mouseClicked(click, doubled);
        }

        if (searchModal.isOpen()) {
            return searchModal.mouseClicked(mx, my, this.width, this.height, this);
        }

        float winW = lastWinW;
        float winH = lastWinH;
        float winX = lastWinX;
        float winY = lastWinY;

        if (!isInside(mx, my, winX, winY, winW, winH)) {
            pages.unfocusAll();
            return super.mouseClicked(click, doubled);
        }

        float rightX = winX + sidebarWidth + 1.0f;
        float rightW = winW - sidebarWidth - 1.0f;

        // 1. TopBar clicks & window drag
        if (isInside(mx, my, rightX, winY, rightW, TOPBAR_H)) {
            float btnSize = 20.0f;
            float btnY = winY + (TOPBAR_H - btnSize) * 0.5f;
            float sideBtnX = rightX + 10.0f;
            if (isInside(mx, my, sideBtnX, btnY, btnSize, btnSize)) {
                playClick();
                sidebarCollapsed = !sidebarCollapsed;
                MenuConfigStore.save(obj -> obj.addProperty("clickGuiSidebarCollapsed", sidebarCollapsed));
                return true;
            }

            // Right controls
            float rx = rightX + rightW - 10.0f - btnSize;
            if (isInside(mx, my, rx, btnY, btnSize, btnSize)) {
                playClick();
                SystemTheme.toggleDark();
                return true;
            }
            rx -= (btnSize + 5.0f);
            if (isInside(mx, my, rx, btnY, btnSize, btnSize)) {
                playClick();
                toggleExpandAllDisplayed();
                return true;
            }
            rx -= (48.0f + 5.0f);
            if (isInside(mx, my, rx, btnY, 48.0f, btnSize)) {
                playClick();
                boundOnly = !boundOnly;
                targetScroll = 0.0f;
                return true;
            }
            String themeName = ThemeManager.getInstance().getCurrentTheme().getName();
            float accentPillW = Math.min(92.0f, Render2DHelper.getTextWidth(themeName, 0.70f) + 20.0f);
            rx -= (accentPillW + 5.0f);
            if (isInside(mx, my, rx, btnY, accentPillW, btnSize)) {
                playClick();
                ThemeManager.getInstance().nextTheme();
                showToast("Accent: " + ThemeManager.getInstance().getCurrentTheme().getName());
                return true;
            }

            if (button == 0) {
                draggingWindow = true;
                dragStartMouseX = mx;
                dragStartMouseY = my;
                dragStartOffX = windowOffsetX;
                dragStartOffY = windowOffsetY;
                return true;
            }
        }

        // 2. SideBar clicks
        if (mx < rightX) {
            pages.unfocusAll();
            if (handleSideBarClick(mx, my, winX, winY, sidebarWidth, winH)) {
                playClick();
                return true;
            }
            return true;
        }

        // 3. Content Area clicks
        float contentY = winY + TOPBAR_H + 1.0f;
        float contentH = winH - TOPBAR_H - 1.0f;
        if (activePage == NavPage.CONFIGS) {
            return pages.clickConfigs(mx, my, rightX, contentY, rightW, contentH, this);
        }
        if (activePage == NavPage.FRIENDS) {
            return pages.clickFriends(mx, my, rightX, contentY, rightW, contentH, this);
        }

        if (isInside(mx, my, rightX + rightW - 10.0f, contentY, 10.0f, contentH) && maxScroll > 1.0f) {
            draggingScrollbar = true;
            updateScrollbarDrag(my, contentY, contentH);
            return true;
        }

        for (Module m : getDisplayedModules()) {
            SystemModuleCard card = cardMap.get(m);
            if (card == null) continue;
            if (card.handleClick(mx, my, button, this)) {
                return true;
            }
        }
        return true;
    }

    private boolean handleSideBarClick(double mx, double my, float sx, float sy, float sw, float sh) {
        float curY = sy + TOPBAR_H + (sw > 60.0f ? 20.0f : 12.0f);
        float itemH = 22.0f;
        float step = itemH + 2.0f;

        if (my >= curY && my <= curY + itemH) {
            activePage = NavPage.ALL_MODULES;
            targetScroll = 0.0f;
            return true;
        }
        curY += step;
        if (my >= curY && my <= curY + itemH) {
            activePage = NavPage.FAVORITES;
            targetScroll = 0.0f;
            return true;
        }
        curY += step;

        for (Category cat : Category.values()) {
            if (cat == Category.FRIENDS) continue;
            if (my >= curY && my <= curY + itemH) {
                activePage = NavPage.CATEGORY;
                selectedCategory = cat;
                targetScroll = 0.0f;
                return true;
            }
            curY += step;
        }

        curY += 10.0f + (sw > 60.0f ? 12.0f : 0.0f);
        if (my >= curY && my <= curY + itemH) {
            activePage = NavPage.CONFIGS;
            return true;
        }
        curY += step;
        if (my >= curY && my <= curY + itemH) {
            activePage = NavPage.FRIENDS;
            return true;
        }
        curY += step;
        if (my >= curY && my <= curY + itemH) {
            hudEditorMode = true;
            return true;
        }

        float footY = sy + sh - 34.0f;
        float searchY = footY - 28.0f;
        if (my >= searchY && my <= searchY + 22.0f) {
            searchModal.open();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent click, double offsetX, double offsetY) {
        if (draggingWindow) {
            windowOffsetX = dragStartOffX + (float) (click.x() - dragStartMouseX);
            windowOffsetY = dragStartOffY + (float) (click.y() - dragStartMouseY);
            return true;
        }
        if (draggingScrollbar) {
            updateScrollbarDrag(click.y(), lastWinY + TOPBAR_H + 1.0f, lastWinH - TOPBAR_H - 1.0f);
            return true;
        }
        if (draggingSlider != null) {
            SystemModuleCard.updateSliderValue(draggingSlider, click.x(), draggingSliderX, draggingSliderW);
            return true;
        }
        if (draggingColor != null && colorDragMode != 0) {
            SystemModuleCard.updateColorDrag(draggingColor, colorDragMode, click.x(), click.y(),
                    colorBoxX, colorBoxY, colorBoxW, colorBoxH);
            return true;
        }
        return super.mouseDragged(click, offsetX, offsetY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent click) {
        draggingWindow = false;
        draggingScrollbar = false;
        draggingSlider = null;
        draggingColor = null;
        colorDragMode = 0;
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (searchModal.isOpen()) {
            return searchModal.mouseScrolled(verticalAmount);
        }
        if (activePage == NavPage.CONFIGS || activePage == NavPage.FRIENDS) {
            return pages.scrollActive(activePage == NavPage.CONFIGS, verticalAmount);
        }
        if (isModulePage()) {
            targetScroll = Mth.clamp(targetScroll - (float) (verticalAmount * 26.0f), 0.0f, maxScroll);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(KeyEvent input) {
        int key = input.key();

        if (hudEditorMode && key == InputConstants.KEY_ESCAPE) {
            hudEditorMode = false;
            return true;
        }

        if (bindingModule != null) {
            if (key == InputConstants.KEY_ESCAPE || key == InputConstants.KEY_DELETE || key == InputConstants.KEY_BACKSPACE) {
                bindingModule.setKeyBind(0);
            } else {
                bindingModule.setKeyBind(key);
            }
            bindingModule = null;
            return true;
        }

        if (searchModal.isOpen()) {
            return searchModal.keyPressed(key, this);
        }

        // Ctrl + F opens Spotlight search
        if (key == InputConstants.KEY_F && input.hasControlDownWithQuirk()) {
            searchModal.open();
            return true;
        }

        if (pages.keyPressed(key, activePage == NavPage.CONFIGS, this)) {
            return true;
        }

        if (key == InputConstants.KEY_ESCAPE || key == InputConstants.KEY_RSHIFT) {
            onClose();
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean charTyped(CharacterEvent input) {
        if (!input.isAllowedChatCharacter()) return super.charTyped(input);
        String ch = input.codepointAsString();
        if (searchModal.isOpen()) {
            return searchModal.charTyped(ch);
        }
        if ((activePage == NavPage.CONFIGS || activePage == NavPage.FRIENDS)
                && pages.charTyped(ch, activePage == NavPage.CONFIGS)) {
            return true;
        }
        return super.charTyped(input);
    }

    @Override
    public void onClose() {
        try {
            ConfigManager.getInstance().saveActiveConfigQuietly();
        } catch (Throwable ignored) {
        }
        closing = true;
    }

    // ---- Coordinator callbacks for cards, search & pages ----

    public void showToast(String msg) {
        this.toastText = msg;
        this.toastTimestamp = System.currentTimeMillis();
    }

    public boolean isBinding(Module m) {
        return m == bindingModule;
    }

    public void setBindingModule(Module m) {
        this.bindingModule = m;
    }

    public void startSliderDrag(NumberSetting ns, float sx, float sw) {
        this.draggingSlider = ns;
        this.draggingSliderX = sx;
        this.draggingSliderW = sw;
    }

    public void startColorDrag(ColorSetting cs, int mode, float x, float y, float w, float h) {
        this.draggingColor = cs;
        this.colorDragMode = mode;
        this.colorBoxX = x;
        this.colorBoxY = y;
        this.colorBoxW = w;
        this.colorBoxH = h;
    }

    public void toggleFavorite(Module m) {
        String name = m.getName();
        if (FAVORITE_MODULES.contains(name)) {
            FAVORITE_MODULES.remove(name);
        } else {
            FAVORITE_MODULES.add(name);
        }
        MenuConfigStore.save(obj -> {
            JsonArray arr = new JsonArray();
            for (String f : FAVORITE_MODULES) arr.add(f);
            obj.add("clickGuiFavorites", arr);
        });
    }

    public void jumpToSearchResult(SystemSearchModal.SearchResult res) {
        if (res.category() != null) {
            activePage = NavPage.CATEGORY;
            selectedCategory = res.category();
        }
        if (res.module() != null) {
            if (res.settingMatch()) {
                res.module().setExpanded(true);
            }
            SystemModuleCard card = cardMap.get(res.module());
            if (card != null) {
                card.triggerFlash();
            }
            pendingJumpModule = res.module();
        }
    }

    private void toggleExpandAllDisplayed() {
        List<Module> list = getDisplayedModules();
        boolean anyExpanded = false;
        for (Module m : list) {
            if (m.isExpanded()) {
                anyExpanded = true;
                break;
            }
        }
        boolean target = !anyExpanded;
        for (Module m : list) {
            SystemModuleCard c = cardMap.get(m);
            if (c != null && c.hasVisibleSettings()) {
                m.setExpanded(target);
            }
        }
    }

    private boolean isModulePage() {
        return activePage == NavPage.ALL_MODULES || activePage == NavPage.FAVORITES || activePage == NavPage.CATEGORY;
    }

    private String getCurrentPageTitle() {
        return switch (activePage) {
            case ALL_MODULES -> "All Modules";
            case FAVORITES -> "Favorites";
            case CATEGORY -> selectedCategory.getDisplayName();
            case CONFIGS -> "Configs";
            case FRIENDS -> "Friends";
        };
    }

    private List<Module> getDisplayedModules() {
        List<Module> all = ModuleManager.getInstance().getModules();
        List<Module> filtered = new ArrayList<>();
        for (Module m : all) {
            if (boundOnly && m.getKeyBind() <= 0) continue;
            if (activePage == NavPage.FAVORITES && !FAVORITE_MODULES.contains(m.getName())) continue;
            if (activePage == NavPage.CATEGORY && m.getCategory() != selectedCategory) continue;
            filtered.add(m);
        }
        if (activePage == NavPage.ALL_MODULES || activePage == NavPage.FAVORITES) {
            filtered.sort(Comparator.comparing(Module::getName, String.CASE_INSENSITIVE_ORDER));
        }
        return filtered;
    }

    private void updateScrollbarDrag(double my, float cy, float ch) {
        if (maxScroll <= 1.0f) return;
        float frac = Mth.clamp((float) ((my - cy - 12.0) / Math.max(1.0f, ch - 24.0f)), 0.0f, 1.0f);
        targetScroll = frac * maxScroll;
        currentScroll = targetScroll;
    }

    private static Identifier getCategoryIcon(Category cat) {
        return switch (cat) {
            case COMBAT -> TextureAssets.ICON_COMBAT;
            case VISUALS -> TextureAssets.ICON_VISUALS;
            case OPTIMIZATION -> TextureAssets.ICON_OPTIMIZATION;
            case HUD -> TextureAssets.ICON_HUD;
            case MOVEMENT -> TextureAssets.ICON_MOVEMENT;
            case FRIENDS -> TextureAssets.ICON_FRIENDS;
        };
    }

    private void playClick() {
        if (minecraft != null && minecraft.getSoundManager() != null) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }
    }

    private static boolean isInside(double mx, double my, float x, float y, float w, float h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }
}
