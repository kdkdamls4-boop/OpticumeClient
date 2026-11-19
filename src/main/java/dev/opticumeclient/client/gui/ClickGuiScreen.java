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
import dev.opticumeclient.client.setting.BindSetting;
import dev.opticumeclient.client.setting.ColorSetting;
import dev.opticumeclient.client.setting.NumberSetting;
import dev.opticumeclient.client.setting.TextSetting;
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
import org.joml.Matrix3x2fStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * OpticumeClient ClickGUI:
 * <ul>
 *   <li>Draggable &amp; resizable rounded shell with Dark/Light {@link SystemTheme}</li>
 *   <li>Collapsible Left SideBar: Expanded navigation list &lt;-&gt; Collapsed Vertical macOS Dock icon bar with hover zoom &amp; floating labels</li>
 *   <li>Full-featured Desire pages: {@link ThemesPage} (presets + custom live theme editor), {@link EventsPage} (FT/HW live events), AutoBuy slot, Configs, Friends &amp; HUD Editor</li>
 *   <li>Masonry module cards in Desire {@code LightSetting} style ({@link SystemModuleCard}) + Spotlight Search ({@code Ctrl+F})</li>
 * </ul>
 */
public class ClickGuiScreen extends Screen {
    public static float WINDOW_WIDTH = 625.0f;
    public static float WINDOW_HEIGHT = 375.0f;
    private static final float SIDEBAR_EXPANDED_W = 136.0f;
    private static final float SIDEBAR_COLLAPSED_W = 42.0f;
    private static final float TOPBAR_H = 34.0f;
    private static final String[] SIZE_NAMES = {"Compact", "Normal", "Large", "Mini"};
    private static final float[][] SIZE_DIMS = {
            {625.0f, 375.0f},
            {700.0f, 420.0f},
            {785.0f, 470.0f},
            {545.0f, 335.0f}
    };

    private enum NavPage {
        ALL_MODULES,
        FAVORITES,
        CATEGORY,
        THEMES,
        EVENTS,
        AUTOBUY,
        CONFIGS,
        FRIENDS
    }

    private record SideEntry(String label, Identifier icon, NavPage page, Category category,
                             boolean hudEditor, boolean searchBtn, boolean dividerAfter) {
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
    private static int sizePresetIdx = 0;
    private static float targetWinW = 625.0f;
    private static float targetWinH = 375.0f;

    // Live bounds synced every frame for HudManager hit-exclusion
    private static float lastWinX = -1.0f;
    private static float lastWinY = -1.0f;
    private static float lastWinW = WINDOW_WIDTH;
    private static float lastWinH = WINDOW_HEIGHT;
    private static boolean lastHudEditorOnly = false;

    private final Map<Module, SystemModuleCard> cardMap = new HashMap<>();
    private final SystemSearchModal searchModal = new SystemSearchModal();
    private final SystemPages pages = new SystemPages();
    private final ThemesPage themesPage = new ThemesPage();
    private final EventsPage eventsPage = new EventsPage();
    private final AutoBuyPage autoBuyPage = new AutoBuyPage();
    private final HoverTarget hoverTarget = new HoverTarget();

    private final List<SideEntry> sideEntries = new ArrayList<>();
    private final float[] dockScaleAnims;
    private String dockTooltipLabel = null;
    private float dockTooltipY = 0.0f;

    private float sidebarWidth = SIDEBAR_EXPANDED_W;
    private float sidebarScroll = 0.0f;
    private float sidebarTargetScroll = 0.0f;
    private float sidebarMaxScroll = 0.0f;

    private float targetScroll = 0.0f;
    private float currentScroll = 0.0f;
    private float maxScroll = 0.0f;
    private Module pendingJumpModule = null;
    private float openProgress = 0.0f;
    private boolean closing = false;
    private boolean hudEditorMode = false;
    private boolean boundOnly = false;

    private boolean draggingWindow = false;
    private boolean resizingWindow = false;
    private double dragStartMouseX, dragStartMouseY;
    private float dragStartOffX, dragStartOffY;
    private float resizeStartW, resizeStartH;
    private boolean draggingScrollbar = false;

    private Module bindingModule = null;
    private BindSetting activeBindingSetting = null;
    private Module focusedTextModule = null;
    private TextSetting focusedTextSetting = null;
    private NumberSetting draggingSlider = null;
    private float draggingSliderX = 0.0f;
    private float draggingSliderW = 1.0f;

    private ColorSetting draggingColor = null;
    private int colorDragMode = 0;
    private float colorBoxX, colorBoxY, colorBoxW, colorBoxH;

    private String toastText = "";
    private long toastTimestamp = 0L;
    private long lastNanos = System.nanoTime();

    public static void openOnAutoBuy() {
        activePage = NavPage.AUTOBUY;
        Minecraft.getInstance().setScreenAndShow(new ClickGuiScreen());
    }

    public ClickGuiScreen() {
        super(Component.literal("OpticumeClient ClickGUI"));
        ensureLoaded();
        this.sidebarWidth = sidebarCollapsed ? SIDEBAR_COLLAPSED_W : SIDEBAR_EXPANDED_W;
        for (Module m : ModuleManager.getInstance().getModules()) {
            cardMap.put(m, new SystemModuleCard(m));
        }

        sideEntries.add(new SideEntry("All Modules", TextureAssets.ICON_ALL, NavPage.ALL_MODULES, null, false, false, false));
        sideEntries.add(new SideEntry("Favorites", TextureAssets.ICON_FAVORITES, NavPage.FAVORITES, null, false, false, true));

        List<Category> cats = new ArrayList<>();
        for (Category c : Category.values()) {
            if (c != Category.FRIENDS) cats.add(c);
        }
        for (int i = 0; i < cats.size(); i++) {
            Category cat = cats.get(i);
            sideEntries.add(new SideEntry(cat.getDisplayName(), getCategoryIcon(cat), NavPage.CATEGORY, cat,
                    false, false, i == cats.size() - 1));
        }

        sideEntries.add(new SideEntry("Themes", TextureAssets.DESIRE_GUI_PALETTE, NavPage.THEMES, null, false, false, false));
        sideEntries.add(new SideEntry("Events", TextureAssets.DESIRE_GUI_EVENTS, NavPage.EVENTS, null, false, false, false));
        sideEntries.add(new SideEntry("AutoBuy", TextureAssets.DESIRE_GUI_AUTOBUY, NavPage.AUTOBUY, null, false, false, true));
        sideEntries.add(new SideEntry("Configs", TextureAssets.ICON_CONFIGS, NavPage.CONFIGS, null, false, false, false));
        sideEntries.add(new SideEntry("Friends", TextureAssets.ICON_FRIENDS, NavPage.FRIENDS, null, false, false, false));
        sideEntries.add(new SideEntry("HUD Editor", TextureAssets.ICON_HUD_EDITOR, null, null, true, false, true));
        sideEntries.add(new SideEntry("Search (Ctrl+F)", TextureAssets.ICON_SEARCH, null, null, false, true, false));

        dockScaleAnims = new float[sideEntries.size()];
        for (int i = 0; i < dockScaleAnims.length; i++) dockScaleAnims[i] = 1.0f;
    }

    private static void ensureLoaded() {
        SystemTheme.ensureLoaded();
        if (!sidebarLoaded) {
            sidebarLoaded = true;
            sidebarCollapsed = MenuConfigStore.getBoolean("clickGuiSidebarCollapsed", false);
            try {
                sizePresetIdx = Integer.parseInt(MenuConfigStore.getString("clickGuiSizePreset", "0"));
                if (sizePresetIdx < 0 || sizePresetIdx >= SIZE_NAMES.length) sizePresetIdx = 0;
                targetWinW = Float.parseFloat(MenuConfigStore.getString("clickGuiWinW", String.valueOf(SIZE_DIMS[sizePresetIdx][0])));
                targetWinH = Float.parseFloat(MenuConfigStore.getString("clickGuiWinH", String.valueOf(SIZE_DIMS[sizePresetIdx][1])));
            } catch (Throwable ignored) {
                sizePresetIdx = 0;
                targetWinW = SIZE_DIMS[0][0];
                targetWinH = SIZE_DIMS[0][1];
            }
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
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        TextureAssets.ensureInitialized();
        hoverTarget.title = null;
        hoverTarget.description = null;
        dockTooltipLabel = null;

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
        sidebarScroll += (sidebarTargetScroll - sidebarScroll) * (1.0f - (float) Math.exp(-16.0f * dt));

        float maxWinW = Math.max(220.0f, this.width - 12.0f);
        float maxWinH = Math.max(160.0f, this.height - 12.0f);
        float minWinW = Math.min(480.0f, maxWinW);
        float minWinH = Math.min(290.0f, maxWinH);
        float winW = Mth.clamp(targetWinW, minWinW, maxWinW);
        float winH = Mth.clamp(targetWinH, minWinH, maxWinH);
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

        float effSideW = Math.min(sidebarWidth, Math.max(SIDEBAR_COLLAPSED_W, winW * 0.36f));
        float alpha = Mth.clamp(openProgress, 0.0f, 1.0f);
        float shellAlpha = alpha * 0.92f;

        Render2DHelper.drawGlow(context, winX, winY + 3.0f, winW, winH, 10.0f, SystemTheme.fade(0xFF000000, alpha * 0.45f));
        Render2DHelper.drawRoundedRectFloat(context, winX, winY, winW, winH, 10.0f, SystemTheme.fade(SystemTheme.surface(), shellAlpha));

        renderSideBar(context, dt, winX, winY, effSideW, winH, mouseX, mouseY, alpha);

        float divX = winX + effSideW;
        Render2DHelper.drawRect(context, divX, winY + 1.0f, 1.0f, winH - 2.0f, SystemTheme.fade(SystemTheme.border(), alpha));

        float rightX = divX + 1.0f;
        float rightW = Math.max(80.0f, winW - effSideW - 1.0f);
        renderTopBar(context, rightX, winY, rightW, TOPBAR_H, mouseX, mouseY, alpha);

        Render2DHelper.drawRect(context, rightX, winY + TOPBAR_H, rightW, 1.0f, SystemTheme.fade(SystemTheme.border(), alpha));

        float contentY = winY + TOPBAR_H + 1.0f;
        float contentH = winH - TOPBAR_H - 1.0f;
        renderContentArea(context, dt, rightX, contentY, rightW, contentH, mouseX, mouseY, alpha);

        Render2DHelper.drawRoundedOutlineFloat(context, winX, winY, winW, winH, 10.0f, 1.0f,
                SystemTheme.fade(SystemTheme.border(), alpha));
        boolean gripHov = isInside(mouseX, mouseY, winX + winW - 14.0f, winY + winH - 14.0f, 14.0f, 14.0f);
        int gripCol = SystemTheme.fade(gripHov || resizingWindow ? SystemTheme.primary() : SystemTheme.mutedFg(),
                alpha * (gripHov || resizingWindow ? 0.95f : 0.45f));
        Render2DHelper.drawRect(context, winX + winW - 9.0f, winY + winH - 5.0f, 4.0f, 1.0f, gripCol);
        Render2DHelper.drawRect(context, winX + winW - 5.0f, winY + winH - 9.0f, 1.0f, 5.0f, gripCol);
        if (gripHov) {
            hoverTarget.title = "Resize Window";
            hoverTarget.description = "Drag corner to resize ClickGUI window";
        }

        // macOS Dock floating label rendered outside sidebar clip
        if (!searchModal.isOpen() && dockTooltipLabel != null && sidebarWidth < 68.0f) {
            float tw = Render2DHelper.getTextWidth(dockTooltipLabel, 0.78f) + 12.0f;
            float th = 16.0f;
            float dx = rightX + 5.0f;
            float dy = Mth.clamp(dockTooltipY - th * 0.5f, winY + 4.0f, winY + winH - th - 4.0f);
            Render2DHelper.drawGlow(context, dx, dy + 1.0f, tw, th, 6.0f, SystemTheme.fade(0xFF000000, alpha * 0.55f));
            Render2DHelper.drawRoundedRectFloat(context, dx, dy, tw, th, 5.0f, SystemTheme.fade(SystemTheme.bg(), alpha * 0.96f));
            Render2DHelper.drawRoundedOutlineFloat(context, dx, dy, tw, th, 5.0f, 0.9f,
                    ColorUtils.withAlphaFactor(ThemeManager.getInstance().getAccentColor(0), alpha * 0.75f));
            Render2DHelper.drawScaledText(context, dockTooltipLabel, dx + 6.0f, dy + 4.5f,
                    SystemTheme.fade(SystemTheme.foreground(), alpha), 0.78f, false);
        }

        searchModal.render(context, winX, winY, winW, winH, this.width, this.height, mouseX, mouseY);

        if (!searchModal.isOpen() && dockTooltipLabel == null && hoverTarget.title != null && hoverTarget.description != null) {
            renderTooltip(context, hoverTarget.title, hoverTarget.description, mouseX, mouseY, alpha);
        }
    }

    private void renderSideBar(GuiGraphicsExtractor context, float dt, float sx, float sy, float sw, float sh,
                               int mouseX, int mouseY, float alpha) {
        float expandRatio = Mth.clamp((sw - SIDEBAR_COLLAPSED_W) / (SIDEBAR_EXPANDED_W - SIDEBAR_COLLAPSED_W), 0.0f, 1.0f);
        int accent = ThemeManager.getInstance().getAccentColor(0);

        context.enableScissor(Math.round(sx), Math.round(sy), Math.round(sx + sw), Math.round(sy + sh));

        // Top Brand Logo
        float logoX = expandRatio < 0.35f ? sx + (sw - 18.0f) * 0.5f : sx + 10.0f;
        Render2DHelper.drawTextureFloat(context, TextureAssets.MINI_LOGO, logoX, sy + 8.5f, 18.0f, 18.0f,
                ColorUtils.withAlphaFactor(0xFFFFFFFF, alpha));
        if (expandRatio > 0.25f) {
            Render2DHelper.drawScaledText(context, "Opticume", sx + 34.0f, sy + 10.0f,
                    SystemTheme.fade(SystemTheme.foreground(), alpha * expandRatio), 0.94f, false);
            Render2DHelper.drawScaledText(context, "26.3", sx + 34.0f, sy + 21.0f,
                    SystemTheme.fade(SystemTheme.mutedFg(), alpha * expandRatio), 0.66f, false);
        }

        if (expandRatio < 0.35f) {
            renderVerticalDockSidebar(context, dt, sx, sy, sw, sh, mouseX, mouseY, accent, alpha);
            context.disableScissor();
            return;
        }

        // Expanded Navigation List + Bottom Search & Profile
        float footH = 25.0f;
        float footY = sy + sh - footH - 7.0f;
        float listTop = sy + TOPBAR_H + 5.0f;
        float listBot = footY - 6.0f;
        float listH = Math.max(40.0f, listBot - listTop);

        float contentH = computeExpandedSidebarHeight();
        sidebarMaxScroll = Math.max(0.0f, contentH - listH);
        sidebarTargetScroll = Mth.clamp(sidebarTargetScroll, 0.0f, sidebarMaxScroll);
        sidebarScroll = Mth.clamp(sidebarScroll, 0.0f, sidebarMaxScroll);

        context.disableScissor();
        context.enableScissor(Math.round(sx), Math.round(listTop), Math.round(sx + sw), Math.round(listBot));

        float curY = listTop - sidebarScroll;
        float itemH = 18.0f;
        float step = 19.5f;
        for (int i = 0; i < sideEntries.size(); i++) {
            SideEntry entry = sideEntries.get(i);
            boolean active = isEntryActive(entry);
            float ix = sx + 7.0f;
            float iw = sw - 14.0f;
            boolean hov = !searchModal.isOpen() && isInside(mouseX, mouseY, ix, curY, iw, itemH)
                    && mouseY >= listTop && mouseY <= listBot;

            if (active || hov) {
                int fill = active ? ColorUtils.withAlpha(accent, 34) : ColorUtils.withAlphaFactor(SystemTheme.elevated(), 0.55f);
                Render2DHelper.drawRoundedRectFloat(context, ix, curY, iw, itemH, 5.0f, ColorUtils.withAlphaFactor(fill, alpha));
                if (active) {
                    Render2DHelper.drawRoundedOutlineFloat(context, ix, curY, iw, itemH, 5.0f, 0.9f,
                            ColorUtils.withAlphaFactor(accent, alpha * 0.72f));
                }
            }
            int col = active ? accent : (hov ? SystemTheme.foreground() : SystemTheme.mutedFg());
            Render2DHelper.drawTextureFloat(context, entry.icon(), ix + 6.0f, curY + 4.0f, 10.0f, 10.0f,
                    ColorUtils.withAlphaFactor(col, alpha));
            Render2DHelper.drawScaledText(context, entry.label(), ix + 21.0f, curY + 5.5f,
                    ColorUtils.withAlphaFactor(col, alpha * expandRatio), 0.78f, false);

            curY += step;
            if (entry.dividerAfter()) {
                curY += 2.0f;
                Render2DHelper.drawRect(context, sx + 9.0f, curY, sw - 18.0f, 1.0f,
                        SystemTheme.fade(SystemTheme.border(), alpha * 0.75f));
                curY += 4.5f;
            }
        }
        context.disableScissor();

        // Bottom User Profile Chip
        context.enableScissor(Math.round(sx), Math.round(sy), Math.round(sx + sw), Math.round(sy + sh));
        String uname = (minecraft != null && minecraft.getUser() != null) ? minecraft.getUser().getName() : "Player";
        int chipBg = SystemTheme.fade(SystemTheme.bg(), alpha);
        int chipBorder = SystemTheme.fade(SystemTheme.border(), alpha);
        Render2DHelper.drawRoundedRectFloat(context, sx + 7.0f, footY, sw - 14.0f, footH, 6.0f, chipBg);
        Render2DHelper.drawRoundedOutlineFloat(context, sx + 7.0f, footY, sw - 14.0f, footH, 6.0f, 1.0f, chipBorder);
        Render2DHelper.drawRoundedPlayerHeadByUuid(context, UUIDUtil.createOfflinePlayerUUID(uname),
                Math.round(sx + 10.5f), Math.round(footY + 4.5f), 16, 4.0f,
                chipBg, SystemTheme.fade(SystemTheme.border(), alpha * 0.85f), alpha);
        Render2DHelper.drawScaledText(context, Render2DHelper.trimToWidth(uname, 0.78f, sw - 46.0f),
                sx + 31.0f, footY + 5.0f, SystemTheme.fade(SystemTheme.foreground(), alpha * expandRatio), 0.78f, false);
        Render2DHelper.drawScaledText(context, ConfigManager.getInstance().getActiveConfigName(),
                sx + 31.0f, footY + 14.5f, SystemTheme.fade(SystemTheme.mutedFg(), alpha * expandRatio), 0.64f, false);
        context.disableScissor();
    }

    private void renderVerticalDockSidebar(GuiGraphicsExtractor context, float dt, float sx, float sy, float sw, float sh,
                                           int mouseX, int mouseY, int accent, float alpha) {
        float dockX = sx + 5.0f;
        float dockW = sw - 10.0f;
        float dockY = sy + TOPBAR_H + 2.0f;
        float dockH = Math.max(40.0f, sh - TOPBAR_H - 9.0f);
        float dockRadius = 11.5f;

        // Vertical macOS Dock translucent glass capsule
        Render2DHelper.drawGlow(context, dockX, dockY + 2.0f, dockW, dockH, 6.0f,
                SystemTheme.fade(0xFF000000, alpha * 0.35f));
        Render2DHelper.drawRoundedRectFloat(context, dockX, dockY, dockW, dockH, dockRadius,
                SystemTheme.fade(SystemTheme.bg(), alpha * 0.92f));
        Render2DHelper.drawRoundedOutlineFloat(context, dockX, dockY, dockW, dockH, dockRadius, 0.9f,
                SystemTheme.fade(SystemTheme.border(), alpha * 0.90f));

        float innerTop = dockY + 5.0f;
        float innerBot = dockY + dockH - 5.0f;
        float availH = Math.max(20.0f, innerBot - innerTop);
        float contentH = computeDockContentHeight();
        sidebarMaxScroll = Math.max(0.0f, contentH - availH);
        sidebarTargetScroll = Mth.clamp(sidebarTargetScroll, 0.0f, sidebarMaxScroll);
        sidebarScroll = Mth.clamp(sidebarScroll, 0.0f, sidebarMaxScroll);

        context.disableScissor();
        context.enableScissor(Math.round(dockX), Math.round(innerTop), Math.round(dockX + dockW), Math.round(innerBot));

        boolean inDock = !searchModal.isOpen() && isInside(mouseX, mouseY, dockX - 2.0f, innerTop, dockW + 4.0f, availH);
        float curY = innerTop - sidebarScroll;
        float slotStep = computeDockStep(availH);
        float rate = 1.0f - (float) Math.exp(-18.0f * dt);

        Matrix3x2fStack pose = context.pose();
        for (int i = 0; i < sideEntries.size(); i++) {
            SideEntry entry = sideEntries.get(i);
            boolean active = isEntryActive(entry);
            float cy = curY + slotStep * 0.5f;

            float targetZoom = 1.0f;
            boolean directHover = inDock && Math.abs(mouseY - cy) <= slotStep * 0.52f;
            if (inDock) {
                float normDist = Math.abs(mouseY - cy) / 24.0f;
                targetZoom = 1.0f + 0.34f * (float) Math.exp(-normDist * normDist * 2.2f);
            }
            dockScaleAnims[i] += (targetZoom - dockScaleAnims[i]) * rate;

            if (directHover) {
                dockTooltipLabel = entry.label();
                dockTooltipY = cy;
            }

            float zoom = dockScaleAnims[i];
            float boost = Math.max(0.0f, (zoom - 1.0f) / 0.34f);

            if (active || boost > 0.15f) {
                float pillS = Math.min(dockW - 5.0f, 16.5f * zoom);
                int fill = active ? ColorUtils.withAlpha(accent, 48)
                        : ColorUtils.withAlphaFactor(SystemTheme.elevated(), 0.55f * boost);
                Render2DHelper.drawRoundedRectFloat(context, dockX + (dockW - pillS) * 0.5f, cy - pillS * 0.5f,
                        pillS, pillS, 5.0f, ColorUtils.withAlphaFactor(fill, alpha));
            }
            if (active) {
                // Classic macOS Dock active accent dot on the left edge
                Render2DHelper.drawRoundedRectFloat(context, dockX + 1.5f, cy - 3.5f, 2.0f, 7.0f, 1.0f,
                        ColorUtils.withAlphaFactor(accent, alpha));
            }

            float iconSize = 10.5f;
            float cx = dockX + dockW * 0.5f + boost * 1.4f;
            int iconCol = active ? accent : ColorUtils.interpolateColor(SystemTheme.mutedFg(), 0xFFFFFFFF, boost);
            pose.pushMatrix();
            pose.translate(cx, cy);
            pose.scale(zoom, zoom);
            Render2DHelper.drawTextureFloat(context, entry.icon(), -iconSize * 0.5f, -iconSize * 0.5f,
                    iconSize, iconSize, ColorUtils.withAlphaFactor(iconCol, alpha));
            pose.popMatrix();

            curY += slotStep;
            if (entry.dividerAfter()) {
                curY += 3.5f;
                Render2DHelper.drawRect(context, dockX + 6.0f, curY - 2.0f, dockW - 12.0f, 1.0f,
                        SystemTheme.fade(SystemTheme.border(), alpha * 0.55f));
            }
        }
        context.disableScissor();
        context.enableScissor(Math.round(sx), Math.round(sy), Math.round(sx + sw), Math.round(sy + sh));
    }

    private float computeExpandedSidebarHeight() {
        float h = 0.0f;
        for (SideEntry e : sideEntries) {
            h += 19.5f;
            if (e.dividerAfter()) h += 6.5f;
        }
        return h;
    }

    private float computeDockStep(float availH) {
        int dividers = 0;
        for (SideEntry e : sideEntries) {
            if (e.dividerAfter()) dividers++;
        }
        float rawStep = (availH - dividers * 3.5f) / Math.max(1, sideEntries.size());
        return Mth.clamp(rawStep, 16.5f, 22.0f);
    }

    private float computeDockContentHeight() {
        int dividers = 0;
        for (SideEntry e : sideEntries) {
            if (e.dividerAfter()) dividers++;
        }
        return sideEntries.size() * 17.5f + dividers * 3.5f;
    }

    private boolean isEntryActive(SideEntry e) {
        if (e.hudEditor()) return hudEditorMode;
        if (e.searchBtn()) return searchModal.isOpen();
        if (e.page() == NavPage.CATEGORY) return activePage == NavPage.CATEGORY && selectedCategory == e.category();
        return activePage == e.page();
    }

    private void renderTopBar(GuiGraphicsExtractor context, float tx, float ty, float tw, float th,
                              int mouseX, int mouseY, float alpha) {
        context.enableScissor(Math.round(tx), Math.round(ty), Math.round(tx + tw), Math.round(ty + th));

        float btnSize = 20.0f;
        float sideBtnX = tx + 10.0f;
        float btnY = ty + (th - btnSize) * 0.5f;
        drawIconToolButton(context, sideBtnX, btnY, btnSize, TextureAssets.ICON_SIDEBAR, mouseX, mouseY, sidebarCollapsed, alpha);
        if (isInside(mouseX, mouseY, sideBtnX, btnY, btnSize, btnSize)) {
            hoverTarget.title = sidebarCollapsed ? "Expand Sidebar" : "MacOS Dock Mode";
            hoverTarget.description = sidebarCollapsed ? "Restore full sidebar navigation" : "Collapse sidebar into vertical macOS Dock icons";
        }

        String themeName = ThemeManager.getInstance().getCurrentTheme().getName();
        float accentPillW = Math.min(86.0f, Render2DHelper.getTextWidth(themeName, 0.70f) + 20.0f);
        String sizeLabel = SIZE_NAMES[sizePresetIdx];
        float sizePillW = Math.max(46.0f, Render2DHelper.getTextWidth(sizeLabel, 0.70f) + 12.0f);

        float themeBtnX = tx + tw - 10.0f - btnSize;
        float collapseBtnX = themeBtnX - (btnSize + 5.0f);
        float boundBtnX = collapseBtnX - (48.0f + 5.0f);
        float accentBtnX = boundBtnX - (accentPillW + 5.0f);
        float sizeBtnX = accentBtnX - (sizePillW + 5.0f);

        float titleX = sideBtnX + btnSize + 10.0f;
        float titleMaxW = Math.max(0.0f, sizeBtnX - titleX - 6.0f);
        String title = getCurrentPageTitle();
        if (titleMaxW >= 24.0f) {
            String shownTitle = Render2DHelper.trimToWidth(title, 1.02f, titleMaxW);
            Render2DHelper.drawScaledText(context, shownTitle, titleX, ty + 13.0f,
                    SystemTheme.fade(SystemTheme.foreground(), alpha), 1.02f, false);

            float badgeX = titleX + Render2DHelper.getTextWidth(shownTitle, 1.02f) + 8.0f;
            float badgeMaxW = sizeBtnX - badgeX - 6.0f;
            if (!toastText.isEmpty() && System.currentTimeMillis() - toastTimestamp < 2600L) {
                float pillW = Render2DHelper.getTextWidth(toastText, 0.72f) + 12.0f;
                if (pillW <= badgeMaxW) {
                    Render2DHelper.drawRoundedRectFloat(context, badgeX, ty + 10.0f, pillW, 15.0f, 4.0f,
                            SystemTheme.fade(SystemTheme.elevated(), alpha));
                    Render2DHelper.drawScaledText(context, toastText, badgeX + 6.0f, ty + 14.0f,
                            SystemTheme.fade(SystemTheme.SUCCESS, alpha), 0.72f, false);
                }
            } else if (isModulePage()) {
                String cnt = getDisplayedModules().size() + " modules";
                float pw = Render2DHelper.getTextWidth(cnt, 0.72f) + 10.0f;
                if (pw <= badgeMaxW) {
                    Render2DHelper.drawRoundedRectFloat(context, badgeX, ty + 10.5f, pw, 14.0f, 4.0f,
                            SystemTheme.fade(SystemTheme.elevated(), alpha));
                    Render2DHelper.drawScaledText(context, cnt, badgeX + 5.0f, ty + 14.0f,
                            SystemTheme.fade(SystemTheme.mutedFg(), alpha), 0.72f, false);
                }
            }
        }

        float rx = themeBtnX;
        Identifier themeIcon = SystemTheme.isDark() ? TextureAssets.ICON_SUN : TextureAssets.ICON_MOON;
        drawIconToolButton(context, rx, btnY, btnSize, themeIcon, mouseX, mouseY, false, alpha);
        if (isInside(mouseX, mouseY, rx, btnY, btnSize, btnSize)) {
            hoverTarget.title = SystemTheme.isDark() ? "Light Theme" : "Dark Theme";
            hoverTarget.description = "Switch between Dark and Light ClickGUI palettes";
        }

        rx = collapseBtnX;
        drawIconToolButton(context, rx, btnY, btnSize, TextureAssets.ICON_COLLAPSE, mouseX, mouseY, false, alpha);
        if (isInside(mouseX, mouseY, rx, btnY, btnSize, btnSize)) {
            hoverTarget.title = "Collapse / Expand";
            hoverTarget.description = "Toggle settings expansion on all cards";
        }

        rx = boundBtnX;
        boolean bHov = isInside(mouseX, mouseY, rx, btnY, 48.0f, btnSize);
        Render2DHelper.drawRoundedRectFloat(context, rx, btnY, 48.0f, btnSize, 5.0f,
                SystemTheme.fade(boundOnly ? SystemTheme.primary() : (bHov ? SystemTheme.elevated() : SystemTheme.bg()), alpha));
        Render2DHelper.drawRoundedOutlineFloat(context, rx, btnY, 48.0f, btnSize, 5.0f, 1.0f,
                SystemTheme.fade(boundOnly ? SystemTheme.primary() : SystemTheme.border(), alpha));
        Render2DHelper.drawScaledText(context, "Bound", rx + 11.0f, btnY + 6.5f,
                SystemTheme.fade(boundOnly ? SystemTheme.onPrimary() : SystemTheme.foreground(), alpha), 0.74f, false);

        rx = accentBtnX;
        boolean aHov = isInside(mouseX, mouseY, rx, btnY, accentPillW, btnSize);
        Render2DHelper.drawRoundedRectFloat(context, rx, btnY, accentPillW, btnSize, 5.0f,
                SystemTheme.fade(aHov ? SystemTheme.elevated() : SystemTheme.bg(), alpha));
        Render2DHelper.drawRoundedOutlineFloat(context, rx, btnY, accentPillW, btnSize, 5.0f, 1.0f,
                SystemTheme.fade(activePage == NavPage.THEMES ? ThemeManager.getInstance().getAccentColor(0) : SystemTheme.border(), alpha));
        Render2DHelper.drawCircle(context, rx + 8.0f, btnY + btnSize * 0.5f, 3.5f,
                ColorUtils.withAlphaFactor(ThemeManager.getInstance().getAccentColor(0), alpha));
        Render2DHelper.drawScaledText(context, Render2DHelper.trimToWidth(themeName, 0.70f, accentPillW - 18.0f),
                rx + 14.0f, btnY + 6.5f, SystemTheme.fade(SystemTheme.foreground(), alpha), 0.70f, false);
        if (aHov) {
            hoverTarget.title = "Theme: " + themeName;
            hoverTarget.description = "LMB cycle theme accent · RMB open Themes Editor";
        }

        rx = sizeBtnX;
        if (rx >= sideBtnX + btnSize + 4.0f) {
            boolean szHov = isInside(mouseX, mouseY, rx, btnY, sizePillW, btnSize);
            Render2DHelper.drawRoundedRectFloat(context, rx, btnY, sizePillW, btnSize, 5.0f,
                    SystemTheme.fade(szHov ? SystemTheme.elevated() : SystemTheme.bg(), alpha));
            Render2DHelper.drawRoundedOutlineFloat(context, rx, btnY, sizePillW, btnSize, 5.0f, 1.0f,
                    SystemTheme.fade(SystemTheme.border(), alpha));
            Render2DHelper.drawCenteredText(context, sizeLabel, rx + sizePillW * 0.5f, btnY + 6.5f,
                    SystemTheme.fade(SystemTheme.foreground(), alpha), false);
            if (szHov) {
                hoverTarget.title = "GUI Size: " + sizeLabel;
                hoverTarget.description = "Click to cycle preset or drag bottom-right corner";
            }
        }
        context.disableScissor();
    }

    private void renderContentArea(GuiGraphicsExtractor context, float dt, float cx, float cy, float cw, float ch,
                                   int mouseX, int mouseY, float alpha) {
        if (activePage == NavPage.THEMES) {
            themesPage.render(context, dt, cx, cy, cw, ch, mouseX, mouseY, alpha);
            return;
        }
        if (activePage == NavPage.EVENTS) {
            eventsPage.render(context, dt, cx, cy, cw, ch, mouseX, mouseY, alpha);
            return;
        }
        if (activePage == NavPage.AUTOBUY) {
            autoBuyPage.render(context, dt, cx, cy, cw, ch, mouseX, mouseY, alpha);
            return;
        }
        if (activePage == NavPage.CONFIGS) {
            pages.renderConfigs(context, cx, cy, cw, ch, mouseX, mouseY, alpha);
            return;
        }
        if (activePage == NavPage.FRIENDS) {
            pages.renderFriends(context, cx, cy, cw, ch, mouseX, mouseY, alpha);
            return;
        }

        List<Module> modules = getDisplayedModules();
        float pad = 8.5f;
        float gap = 6.5f;
        int cols = cw >= 390.0f ? 3 : 2;
        float colW = (cw - pad * 2.0f - gap * (cols - 1)) / cols;
        float[] colHeights = new float[cols];

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
                        FAVORITE_MODULES.contains(m.getName()), m == bindingModule, this, hoverTarget);
            }
        }
        context.disableScissor();

        if (maxScroll > 1.0f) {
            float trackH = ch - 12.0f;
            float viewRatio = ch / (ch + maxScroll);
            float thumbH = Math.max(22.0f, trackH * viewRatio);
            float thumbY = cy + 6.0f + (currentScroll / maxScroll) * (trackH - thumbH);
            float sbX = cx + cw - 5.0f;
            Render2DHelper.drawRoundedRectFloat(context, sbX, thumbY, 2.5f, thumbH, 1.2f,
                    SystemTheme.fade(SystemTheme.border(), alpha));
        }
    }

    private void renderHudEditorBar(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        float barW = 360.0f;
        float barH = 26.0f;
        float bx = (this.width - barW) * 0.5f;
        float by = 10.0f;
        Render2DHelper.drawRoundedRectFloat(context, bx, by, barW, barH, 8.0f, SystemTheme.fade(SystemTheme.surface(), 0.94f));
        Render2DHelper.drawRoundedOutlineFloat(context, bx, by, barW, barH, 8.0f, 1.0f, SystemTheme.border());
        Render2DHelper.drawScaledText(context, "HUD Editor  •  Drag widgets cleanly", bx + 12.0f, by + 9.0f,
                SystemTheme.foreground(), 0.82f, false);

        float doneW = 54.0f;
        float resetW = 74.0f;
        float dx = bx + barW - doneW - 6.0f;
        float rx = bx + barW - doneW - resetW - 12.0f;
        float dy = by + 4.0f;

        boolean hovReset = isInside(mouseX, mouseY, rx, dy, resetW, 18.0f);
        Render2DHelper.drawRoundedRectFloat(context, rx, dy, resetW, 18.0f, 5.0f,
                hovReset ? SystemTheme.DANGER : SystemTheme.elevated());
        Render2DHelper.drawCenteredText(context, "Reset HUD", rx + resetW * 0.5f, dy + 5.0f,
                hovReset ? SystemTheme.onPrimary() : SystemTheme.foreground(), false);

        boolean hov = isInside(mouseX, mouseY, dx, dy, doneW, 18.0f);
        Render2DHelper.drawRoundedRectFloat(context, dx, dy, doneW, 18.0f, 5.0f,
                hov ? SystemTheme.primary() : SystemTheme.elevated());
        Render2DHelper.drawCenteredText(context, "Done", dx + doneW * 0.5f, dy + 5.0f,
                hov ? SystemTheme.onPrimary() : SystemTheme.foreground(), false);
    }

    private void renderTooltip(GuiGraphicsExtractor context, String title, String desc, int mouseX, int mouseY, float alpha) {
        float maxW = 200.0f;
        float maxTextW = maxW - 14.0f;

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
            if (sb.length() > 0) lines.add(sb.toString());
        }
        if (lines.isEmpty()) lines.add(desc != null ? desc : "");

        float longestLineW = 0.0f;
        for (String line : lines) {
            longestLineW = Math.max(longestLineW, Render2DHelper.getTextWidth(line, 0.70f));
        }

        float tw = Math.min(maxW, Math.max(Render2DHelper.getTextWidth(title, 0.80f), longestLineW) + 14.0f);
        float th = 18.0f + lines.size() * 9.5f;
        float tx = Math.min(this.width - tw - 6.0f, Math.max(6.0f, mouseX + 10.0f));
        float ty = Math.min(this.height - th - 6.0f, Math.max(6.0f, mouseY + 12.0f));

        Render2DHelper.drawRoundedRectFloat(context, tx, ty, tw, th, 6.0f, SystemTheme.fade(SystemTheme.bg(), alpha * 0.96f));
        Render2DHelper.drawRoundedOutlineFloat(context, tx, ty, tw, th, 6.0f, 1.0f, SystemTheme.fade(SystemTheme.border(), alpha));
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
        Render2DHelper.drawRoundedRectFloat(context, x, y, size, size, 5.0f, SystemTheme.fade(bg, alpha));
        Render2DHelper.drawRoundedOutlineFloat(context, x, y, size, size, 5.0f, 1.0f,
                SystemTheme.fade(active ? SystemTheme.primary() : SystemTheme.border(), alpha));
        Render2DHelper.drawTextureFloat(context, icon, x + 4.5f, y + 4.5f, size - 9.0f, size - 9.0f,
                SystemTheme.fade(hov || active ? SystemTheme.foreground() : SystemTheme.mutedFg(), alpha));
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        double mx = click.x();
        double my = click.y();
        int button = normalizeMouseButton(click.button());

        if (activeBindingSetting != null && !hudEditorMode) {
            if (click.button() == InputConstants.MOUSE_BUTTON_LEFT) {
                activeBindingSetting = null;
            } else {
                activeBindingSetting.setValue(-100 + click.button());
                activeBindingSetting = null;
            }
            return true;
        }

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
            unfocusAllInputs();
            return super.mouseClicked(click, doubled);
        }

        if (button == 0 && isInside(mx, my, winX + winW - 14.0f, winY + winH - 14.0f, 14.0f, 14.0f)) {
            resizingWindow = true;
            dragStartMouseX = mx;
            dragStartMouseY = my;
            resizeStartW = targetWinW;
            resizeStartH = targetWinH;
            return true;
        }

        float effSideW = Math.min(sidebarWidth, Math.max(SIDEBAR_COLLAPSED_W, winW * 0.36f));
        float rightX = winX + effSideW + 1.0f;
        float rightW = Math.max(80.0f, winW - effSideW - 1.0f);

        if (isInside(mx, my, rightX, winY, rightW, TOPBAR_H)) {
            float btnSize = 20.0f;
            float btnY = winY + (TOPBAR_H - btnSize) * 0.5f;
            float sideBtnX = rightX + 10.0f;
            if (isInside(mx, my, sideBtnX, btnY, btnSize, btnSize)) {
                playClick();
                sidebarCollapsed = !sidebarCollapsed;
                sidebarScroll = 0.0f;
                sidebarTargetScroll = 0.0f;
                MenuConfigStore.save(obj -> obj.addProperty("clickGuiSidebarCollapsed", sidebarCollapsed));
                return true;
            }

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
            float accentPillW = Math.min(86.0f, Render2DHelper.getTextWidth(themeName, 0.70f) + 20.0f);
            rx -= (accentPillW + 5.0f);
            if (isInside(mx, my, rx, btnY, accentPillW, btnSize)) {
                playClick();
                if (button == 1) {
                    unfocusAllInputs();
                    activePage = NavPage.THEMES;
                } else {
                    ThemeManager.getInstance().nextTheme();
                    showToast("Accent: " + ThemeManager.getInstance().getCurrentTheme().getName());
                }
                return true;
            }
            String sizeLabel = SIZE_NAMES[sizePresetIdx];
            float sizePillW = Math.max(46.0f, Render2DHelper.getTextWidth(sizeLabel, 0.70f) + 12.0f);
            rx -= (sizePillW + 5.0f);
            if (rx >= sideBtnX + btnSize + 4.0f && isInside(mx, my, rx, btnY, sizePillW, btnSize)) {
                playClick();
                sizePresetIdx = (sizePresetIdx + 1) % SIZE_NAMES.length;
                targetWinW = SIZE_DIMS[sizePresetIdx][0];
                targetWinH = SIZE_DIMS[sizePresetIdx][1];
                saveWindowDimensions();
                showToast("GUI Size: " + SIZE_NAMES[sizePresetIdx]);
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

        if (mx < rightX) {
            unfocusAllInputs();
            if (handleSideBarClick(mx, my, winX, winY, rightX - winX - 1.0f, winH)) {
                playClick();
                return true;
            }
            return true;
        }

        float contentY = winY + TOPBAR_H + 1.0f;
        float contentH = winH - TOPBAR_H - 1.0f;
        if (activePage == NavPage.THEMES) {
            return themesPage.mouseClicked(mx, my, button, rightX, contentY, rightW, contentH, this);
        }
        if (activePage == NavPage.EVENTS) {
            return eventsPage.mouseClicked(mx, my, button, rightX, contentY, rightW, contentH, this);
        }
        if (activePage == NavPage.AUTOBUY) {
            return autoBuyPage.mouseClicked(mx, my, button);
        }
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
            if (card.handleClick(mx, my, button, this, contentY, contentY + contentH)) {
                return true;
            }
        }
        unfocusModuleTexts();
        return true;
    }

    private boolean handleSideBarClick(double mx, double my, float sx, float sy, float sw, float sh) {
        boolean dockMode = (sw < 68.0f);
        float curY;
        float step;
        float listTop;
        float listBot;

        if (dockMode) {
            float dockY = sy + TOPBAR_H + 2.0f;
            float dockH = Math.max(40.0f, sh - TOPBAR_H - 9.0f);
            listTop = dockY + 5.0f;
            listBot = dockY + dockH - 5.0f;
            step = computeDockStep(listBot - listTop);
            curY = listTop - sidebarScroll;
        } else {
            float footY = sy + sh - 25.0f - 7.0f;
            listTop = sy + TOPBAR_H + 5.0f;
            listBot = footY - 6.0f;
            step = 19.5f;
            curY = listTop - sidebarScroll;
        }

        if (my < listTop || my > listBot) return false;

        for (SideEntry entry : sideEntries) {
            float itemH = dockMode ? step : 18.0f;
            if (my >= curY && my <= curY + itemH) {
                if (entry.hudEditor()) {
                    hudEditorMode = true;
                } else if (entry.searchBtn()) {
                    searchModal.open();
                } else if (entry.page() == NavPage.CATEGORY) {
                    activePage = NavPage.CATEGORY;
                    selectedCategory = entry.category();
                    targetScroll = 0.0f;
                } else {
                    activePage = entry.page();
                    targetScroll = 0.0f;
                    if (activePage == NavPage.EVENTS) eventsPage.resetScroll();
                    if (activePage == NavPage.AUTOBUY) autoBuyPage.resetScroll();
                }
                return true;
            }
            curY += step;
            if (entry.dividerAfter()) {
                curY += dockMode ? 3.5f : 6.5f;
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent click, double offsetX, double offsetY) {
        if (resizingWindow) {
            float maxResizeW = Math.min(840.0f, Math.max(220.0f, this.width - 12.0f));
            float maxResizeH = Math.min(520.0f, Math.max(160.0f, this.height - 12.0f));
            targetWinW = Mth.clamp(resizeStartW + (float) (click.x() - dragStartMouseX) * 2.0f,
                    Math.min(480.0f, maxResizeW), maxResizeW);
            targetWinH = Mth.clamp(resizeStartH + (float) (click.y() - dragStartMouseY) * 2.0f,
                    Math.min(290.0f, maxResizeH), maxResizeH);
            return true;
        }
        if (draggingWindow) {
            windowOffsetX = dragStartOffX + (float) (click.x() - dragStartMouseX);
            windowOffsetY = dragStartOffY + (float) (click.y() - dragStartMouseY);
            return true;
        }
        if (activePage == NavPage.THEMES && themesPage.mouseDragged(click.x(), click.y())) {
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
        if (resizingWindow) {
            resizingWindow = false;
            saveWindowDimensions();
        }
        themesPage.mouseReleased();
        draggingWindow = false;
        draggingScrollbar = false;
        draggingSlider = null;
        draggingColor = null;
        colorDragMode = 0;
        return super.mouseReleased(click);
    }

    private static void saveWindowDimensions() {
        MenuConfigStore.save(obj -> {
            obj.addProperty("clickGuiSizePreset", String.valueOf(sizePresetIdx));
            obj.addProperty("clickGuiWinW", String.valueOf(Math.round(targetWinW)));
            obj.addProperty("clickGuiWinH", String.valueOf(Math.round(targetWinH)));
        });
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (searchModal.isOpen()) {
            return searchModal.mouseScrolled(verticalAmount);
        }
        float effSideW = Math.min(sidebarWidth, Math.max(SIDEBAR_COLLAPSED_W, lastWinW * 0.36f));
        if (isInside(mouseX, mouseY, lastWinX, lastWinY, effSideW, lastWinH)) {
            sidebarTargetScroll = Mth.clamp(sidebarTargetScroll - (float) (verticalAmount * 20.0f), 0.0f, sidebarMaxScroll);
            return true;
        }
        if (activePage == NavPage.THEMES) {
            return themesPage.mouseScrolled(verticalAmount);
        }
        if (activePage == NavPage.EVENTS) {
            return eventsPage.mouseScrolled(verticalAmount);
        }
        if (activePage == NavPage.AUTOBUY) {
            return autoBuyPage.mouseScrolled(verticalAmount);
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

        if (activeBindingSetting != null) {
            if (key == InputConstants.KEY_ESCAPE || key == InputConstants.KEY_DELETE || key == InputConstants.KEY_BACKSPACE) {
                activeBindingSetting.setValue(BindSetting.UNBOUND);
            } else {
                activeBindingSetting.setValue(key);
            }
            activeBindingSetting = null;
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

        if (activePage == NavPage.THEMES && themesPage.keyPressed(key, this)) {
            return true;
        }
        if (activePage == NavPage.EVENTS && eventsPage.keyPressed(key)) {
            return true;
        }
        if (activePage == NavPage.AUTOBUY && autoBuyPage.keyPressed(key)) {
            return true;
        }

        if (isFocusedTextVisible()) {
            boolean ctrlDown = input.hasControlDownWithQuirk();
            String val = focusedTextSetting.getText();
            if (key == InputConstants.KEY_BACKSPACE && !val.isEmpty()) {
                focusedTextSetting.setText(ctrlDown ? "" : val.substring(0, val.length() - 1));
            } else if (key == InputConstants.KEY_V && ctrlDown && minecraft != null && minecraft.keyboardHandler != null) {
                String clip = minecraft.keyboardHandler.getClipboard();
                if (clip != null) {
                    focusedTextSetting.setText(val + clip.replaceAll("\\p{Cntrl}", ""));
                }
            } else if (key == InputConstants.KEY_ESCAPE || key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) {
                unfocusModuleTexts();
            }
            return true;
        }

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
        if (activePage == NavPage.THEMES && themesPage.charTyped(ch)) {
            return true;
        }
        if (activePage == NavPage.EVENTS && eventsPage.charTyped(ch)) {
            return true;
        }
        if (activePage == NavPage.AUTOBUY && autoBuyPage.charTyped(ch)) {
            return true;
        }
        if (isFocusedTextVisible()) {
            if (ch != null && !ch.isEmpty()) {
                char chr = ch.charAt(0);
                if (!Character.isISOControl(chr)) {
                    focusedTextSetting.setText(focusedTextSetting.getText() + chr);
                }
            }
            return true;
        }
        if ((activePage == NavPage.CONFIGS || activePage == NavPage.FRIENDS)
                && pages.charTyped(ch, activePage == NavPage.CONFIGS)) {
            return true;
        }
        return super.charTyped(input);
    }

    @Override
    public void onClose() {
        this.bindingModule = null;
        this.activeBindingSetting = null;
        try {
            ConfigManager.getInstance().saveActiveConfigQuietly();
        } catch (Throwable ignored) {
        }
        closing = true;
    }

    @Override
    public void removed() {
        this.bindingModule = null;
        this.activeBindingSetting = null;
        unfocusAllInputs();
        super.removed();
    }

    private void unfocusAllInputs() {
        pages.unfocusAll();
        themesPage.unfocus();
        eventsPage.unfocus();
        autoBuyPage.unfocus();
        unfocusModuleTexts();
    }

    private void unfocusModuleTexts() {
        this.focusedTextModule = null;
        this.focusedTextSetting = null;
    }

    private boolean isFocusedTextVisible() {
        if (focusedTextSetting == null || focusedTextModule == null) {
            return false;
        }
        if (!isModulePage() || !focusedTextModule.isExpanded() || !focusedTextSetting.isVisible()
                || !getDisplayedModules().contains(focusedTextModule)) {
            unfocusModuleTexts();
            return false;
        }
        return true;
    }

    public boolean isTextSettingFocused(TextSetting ts) {
        return ts != null && ts == focusedTextSetting && isModulePage();
    }

    public void setFocusedTextSetting(Module owner, TextSetting ts) {
        this.focusedTextModule = ts != null ? owner : null;
        this.focusedTextSetting = ts;
    }

    public void showToast(String msg) {
        this.toastText = msg;
        this.toastTimestamp = System.currentTimeMillis();
    }

    public boolean isBinding(Module m) {
        return m == bindingModule;
    }

    public boolean isTypingOrBinding() {
        return isFocusedTextVisible()
                || bindingModule != null
                || activeBindingSetting != null
                || searchModal.isOpen()
                || themesPage.isTyping()
                || eventsPage.isTyping()
                || activePage == NavPage.CONFIGS
                || activePage == NavPage.FRIENDS;
    }

    public void setBindingModule(Module m) {
        this.bindingModule = m;
    }

    public boolean isBindingSettingActive(BindSetting bs) {
        return bs != null && bs == activeBindingSetting;
    }

    public void setBindingSettingActive(BindSetting bs) {
        this.activeBindingSetting = bs;
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
        unfocusAllInputs();
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
            case THEMES -> "Themes";
            case EVENTS -> "Server Events";
            case AUTOBUY -> "AutoBuy";
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
            case OTHER -> TextureAssets.DESIRE_HUD_LAYERS;
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
