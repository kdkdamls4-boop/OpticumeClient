package dev.opticumeclient.client.ui.menu;

import com.mojang.blaze3d.platform.InputConstants;
import dev.opticumeclient.client.command.CommandFix;
import dev.opticumeclient.client.gui.ClickGuiScreen;
import dev.opticumeclient.client.render.ColorUtils;
import dev.opticumeclient.client.render.KawaseBlurRenderer;
import dev.opticumeclient.client.render.Render2DHelper;
import dev.opticumeclient.client.render.TextureAssets;
import dev.opticumeclient.client.render.ThemeManager;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import org.lwjgl.sdl.SDLDialog;
import org.lwjgl.sdl.SDL_DialogFileCallback;
import org.lwjgl.sdl.SDL_DialogFileFilter;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.Pointer;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Liquid Glass wallpaper gallery screen featuring a left category/favorites sidebar,
 * 3-column scrollable grid, live backdrop preview crossfade, search bar (SDL3 TextInput + RU->EN),
 * native SDL3 open-file uploader (+ Upload), and Drag & Drop support via {@link #onFilesDrop(List)}.
 */
public class OpticumeGalleryScreen extends Screen {
    private enum FilterTab {
        ALL("All Wallpapers"),
        FAVORITES("Favorites"),
        MINECRAFT("Minecraft"),
        SKY_ABSTRACT("Sky & Abstract"),
        ANIME("Anime"),
        CUSTOM("Custom");

        final String label;

        FilterTab(String label) {
            this.label = label;
        }
    }

    private static final int GRID_COLS = 3;
    private static final float CARD_ASPECT = 1.62f; // width / height (~16:10)

    private final Screen parent;
    private FilterTab activeFilter = FilterTab.ALL;
    private String searchQuery = "";
    private boolean searchFocused = false;

    private float targetScroll = 0.0f;
    private float smoothScroll = 0.0f;
    private float maxScroll = 0.0f;
    private boolean draggingScrollbar = false;

    private float openProgress = 0.0f;
    private float openAgeSec = 0.0f;
    private long lastNanos = System.nanoTime();

    private String previewFromId;
    private String previewShownId;
    private float backdropFade = 1.0f;
    private float smoothNormX = 0.0f;
    private float smoothNormY = 0.0f;

    private record DialogRequest(
            OpticumeGalleryScreen screen,
            SDL_DialogFileFilter.Buffer filters,
            ByteBuffer nameBuf,
            ByteBuffer patternBuf
    ) {
    }

    private static final AtomicLong NEXT_DIALOG_TOKEN = new AtomicLong(1L);
    private static final Map<Long, DialogRequest> ACTIVE_DIALOGS = new ConcurrentHashMap<>();
    private static SDL_DialogFileCallback sharedDialogCallback;

    private String statusToast = "";
    private long toastAtMs = 0L;
    private int focusedCardIdx = -1;

    public OpticumeGalleryScreen(Screen parent) {
        super(Component.literal("Wallpaper Gallery"));
        this.parent = parent;
        String selId = MenuWallpapers.selectedId();
        this.previewShownId = selId;
        this.previewFromId = selId;
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
        CustomWallpaperStore.ensureLoaded();

        long now = System.nanoTime();
        float dt = Math.min(0.05f, Math.max(0.0001f, (now - lastNanos) / 1.0e9f));
        lastNanos = now;

        openProgress = Math.min(1.0f, openProgress + dt * 5.2f);
        openAgeSec += dt;
        float reveal = openProgress * openProgress * (3.0f - 2.0f * openProgress);

        if (minecraft != null) {
            if (searchFocused) minecraft.textInputManager().startTextInput(this);
            else minecraft.textInputManager().stopTextInput(this);
        }

        if (backdropFade < 1.0f) {
            backdropFade = Math.min(1.0f, backdropFade + dt / 0.65f);
        }

        float targetNormX = (mouseX / Math.max(1.0f, (float) width) - 0.5f) * 2.0f;
        float targetNormY = (mouseY / Math.max(1.0f, (float) height) - 0.5f) * 2.0f;
        float parFactor = 1.0f - (float) Math.exp(-dt * 12.0f);
        smoothNormX += (targetNormX - smoothNormX) * parFactor;
        smoothNormY += (targetNormY - smoothNormY) * parFactor;
        MenuWallpapers.Wallpaper shownWp = MenuWallpapers.getById(previewShownId);
        if (backdropFade < 1.0f) {
            MenuWallpapers.Wallpaper fromWp = MenuWallpapers.getById(previewFromId);
            MenuBackdrop.render(context, () -> super.extractPanorama(context, delta),
                    fromWp, width, height, smoothNormX, smoothNormY, 1.0f, 1.045f);
            if (shownWp != null && !shownWp.panorama()) {
                MenuBackdrop.renderWallpaperCover(context, shownWp, width, height, smoothNormX, smoothNormY, backdropFade, 1.045f);
            }
        } else {
            MenuBackdrop.render(context, () -> super.extractPanorama(context, delta),
                    shownWp, width, height, smoothNormX, smoothNormY, 1.0f, 1.045f);
        }

        context.nextStratum();
        context.blurBeforeThisStratum();
        KawaseBlurRenderer.requestMenuBlur(1, 0.75f);

        Layout lay = computeLayout(reveal);
        int accent = ThemeManager.getInstance().getAccentColor(0);

        // Main Liquid Glass shell matched to MainMenu aesthetic
        Render2DHelper.drawGlow(context, lay.panelX, lay.panelY + 3.0f, lay.panelW, lay.panelH, 12.0f,
                ColorUtils.withAlpha(0xFF000000, Math.round(52 * reveal)));
        Render2DHelper.drawBlurredRoundedRect(context, lay.panelX, lay.panelY, lay.panelW, lay.panelH, 14.0f, 0x100A0E18, reveal);
        Render2DHelper.drawRoundedRectFloat(context, lay.panelX, lay.panelY, lay.panelW, lay.panelH, 14.0f,
                ColorUtils.withAlpha(0xFFFFFFFF, Math.round(16 * reveal)));
        Render2DHelper.drawRect(context, lay.panelX + 16.0f, lay.panelY + 1.0f, lay.panelW - 32.0f, 0.8f,
                ColorUtils.withAlpha(0xFFFFFFFF, Math.round(34 * reveal)));
        Render2DHelper.drawRoundedOutlineFloat(context, lay.panelX, lay.panelY, lay.panelW, lay.panelH, 14.0f, 0.55f,
                ColorUtils.withAlpha(0xFFFFFFFF, Math.round(42 * reveal)));

        renderHeader(context, lay, mouseX, mouseY, reveal, accent);
        renderSidebar(context, lay, mouseX, mouseY, reveal, accent);
        renderGrid(context, lay, dt, mouseX, mouseY, reveal, accent);
        renderToast(context, lay, reveal, accent);
    }

    private void renderHeader(GuiGraphicsExtractor context, Layout lay, int mouseX, int mouseY, float alpha, int accent) {
        List<MenuWallpapers.Wallpaper> filtered = getFilteredWallpapers();
        int totalCount = MenuWallpapers.size();

        float availTitleW = lay.searchX - (lay.panelX + lay.pad) - 10.0f;
        String galTitle = availTitleW >= 115.0f ? "Wallpaper Gallery" : "Gallery";
        OpticumeButtonStyle.drawScriptTitle(context, galTitle,
                lay.panelX + lay.pad, lay.panelY + 6.0f, 18.5f, alpha, openAgeSec, true);
        OpticumeButtonStyle.drawUiText(context, filtered.size() + " / " + totalCount + " backgrounds · 3x Grid · Drop PNG/JPG",
                lay.panelX + lay.pad, lay.panelY + 26.5f, 6.2f, ColorUtils.withAlpha(0xFFE2E8F0, Math.round(195 * alpha)), true);

        // Search input box
        boolean searchHov = isInside(mouseX, mouseY, lay.searchX, lay.headerBtnY, lay.searchW, lay.headerBtnH);
        Render2DHelper.drawRoundedRectFloat(context, lay.searchX, lay.headerBtnY, lay.searchW, lay.headerBtnH, 8.0f,
                ColorUtils.withAlpha(0xFFFFFFFF, Math.round((searchFocused ? 28 : (searchHov ? 22 : 16)) * alpha)));
        Render2DHelper.drawRoundedOutlineFloat(context, lay.searchX, lay.headerBtnY, lay.searchW, lay.headerBtnH, 8.0f, 0.55f,
                searchFocused
                        ? ColorUtils.withAlpha(accent, Math.round(220 * alpha))
                        : ColorUtils.withAlpha(0xFFFFFFFF, Math.round((searchHov ? 80 : 42) * alpha)));
        String caret = searchFocused && ((System.currentTimeMillis() / 500L) % 2L == 0) ? "|" : "";
        String txt = (searchQuery.isEmpty() && !searchFocused) ? "Search..." : (searchQuery + caret);
        int txtCol = (searchQuery.isEmpty() && !searchFocused)
                ? ColorUtils.withAlpha(0xFFA0AEC0, Math.round(200 * alpha))
                : ColorUtils.withAlpha(0xFFFFFFFF, Math.round(240 * alpha));
        float maxTxtW = lay.searchW - (searchQuery.isEmpty() ? 14.0f : 24.0f);
        Render2DHelper.drawScaledText(context, Render2DHelper.trimToWidth(txt, 0.78f, maxTxtW),
                lay.searchX + 7.0f, lay.headerBtnY + 6.5f, txtCol, 0.78f, false);

        if (!searchQuery.isEmpty()) {
            boolean clrHov = isInside(mouseX, mouseY, lay.searchX + lay.searchW - 16.0f, lay.headerBtnY, 16.0f, lay.headerBtnH);
            Render2DHelper.drawScaledText(context, "×", lay.searchX + lay.searchW - 11.5f, lay.headerBtnY + 6.0f,
                    ColorUtils.withAlpha(clrHov ? 0xFFFFFFFF : 0xFF94A3B8, Math.round(230 * alpha)), 0.85f, false);
        }

        // + Upload, Folder, Back glass buttons
        boolean uploadHov = isInside(mouseX, mouseY, lay.uploadX, lay.headerBtnY, lay.uploadW, lay.headerBtnH);
        boolean folderHov = isInside(mouseX, mouseY, lay.folderX, lay.headerBtnY, lay.folderW, lay.headerBtnH);
        boolean backHov = isInside(mouseX, mouseY, lay.backX, lay.headerBtnY, lay.backW, lay.headerBtnH);

        drawGlassButton(context, "upload", lay.uploadX, lay.headerBtnY, lay.uploadW, lay.headerBtnH,
                "+ Upload", true, true, uploadHov, alpha, accent);
        drawGlassButton(context, "folder", lay.folderX, lay.headerBtnY, lay.folderW, lay.headerBtnH,
                "Folder", true, false, folderHov, alpha, accent);
        drawGlassButton(context, "back", lay.backX, lay.headerBtnY, lay.backW, lay.headerBtnH,
                "Back", true, false, backHov, alpha, accent);

        // Divider line under header
        Render2DHelper.drawRect(context, lay.panelX + lay.pad, lay.bodyY - 6.0f, lay.panelW - lay.pad * 2.0f, 1.0f,
                ColorUtils.withAlpha(0xFFFFFFFF, Math.round(30 * alpha)));
    }

    private void renderSidebar(GuiGraphicsExtractor context, Layout lay, int mouseX, int mouseY, float alpha, int accent) {
        Render2DHelper.drawRoundedRectFloat(context, lay.sideX, lay.bodyY, lay.sideW, lay.bodyH, 10.0f,
                ColorUtils.withAlpha(0xFFFFFFFF, Math.round(14 * alpha)));
        Render2DHelper.drawRoundedOutlineFloat(context, lay.sideX, lay.bodyY, lay.sideW, lay.bodyH, 10.0f, 0.55f,
                ColorUtils.withAlpha(0xFFFFFFFF, Math.round(28 * alpha)));

        OpticumeButtonStyle.drawUiText(context, "FILTERS", lay.sideX + 10.0f, lay.bodyY + 9.0f, 6.2f,
                ColorUtils.withAlpha(0xFFE2E8F0, Math.round(185 * alpha)), true);

        FilterTab[] tabs = FilterTab.values();
        float curY = lay.bodyY + 23.0f;
        float rowGap = lay.bodyH < 175.0f ? 2.0f : 3.0f;
        float rowH = Math.min(23.0f, Math.max(13.0f, (lay.bodyH - 26.0f) / tabs.length - rowGap));

        for (FilterTab tab : tabs) {
            boolean active = activeFilter == tab;
            boolean hov = isInside(mouseX, mouseY, lay.sideX + 6.0f, curY, lay.sideW - 12.0f, rowH);
            if (active || hov) {
                int fill = active
                        ? ColorUtils.withAlpha(accent, Math.round(52 * alpha))
                        : ColorUtils.withAlpha(0xFFFFFFFF, Math.round(18 * alpha));
                Render2DHelper.drawRoundedRectFloat(context, lay.sideX + 6.0f, curY, lay.sideW - 12.0f, rowH, 6.0f, fill);
                if (active) {
                    Render2DHelper.drawRoundedOutlineFloat(context, lay.sideX + 6.0f, curY, lay.sideW - 12.0f, rowH, 6.0f, 0.85f,
                            ColorUtils.withAlpha(accent, Math.round(170 * alpha)));
                    Render2DHelper.drawRoundedRectFloat(context, lay.sideX + 8.0f, curY + 5.0f, 2.5f, rowH - 10.0f, 1.2f,
                            ColorUtils.withAlpha(accent, Math.round(240 * alpha)));
                }
            }

            int count = countByFilter(tab);
            int lblCol = active ? ColorUtils.withAlpha(0xFFFFFFFF, Math.round(250 * alpha))
                    : ColorUtils.withAlpha(hov ? 0xFFE2E8F0 : 0xFFB8C4DC, Math.round(225 * alpha));
            String countStr = String.valueOf(count);
            float countW = Render2DHelper.getTextWidth(countStr, 0.68f);

            Render2DHelper.drawScaledText(context,
                    Render2DHelper.trimToWidth(tab.label, 0.76f, lay.sideW - countW - 28.0f),
                    lay.sideX + 15.0f, curY + 7.5f, lblCol, 0.76f, false);
            Render2DHelper.drawScaledText(context, countStr,
                    lay.sideX + lay.sideW - countW - 13.0f, curY + 8.0f,
                    ColorUtils.withAlpha(active ? accent : 0xFF94A3B8, Math.round(215 * alpha)), 0.68f, false);

            curY += rowH + rowGap;
        }

        // Bottom active wallpaper info + Reset Panorama button
        MenuWallpapers.Wallpaper selected = MenuWallpapers.selected();
        float infoH = 66.0f;
        float infoY = lay.bodyY + lay.bodyH - infoH - 7.0f;
        if (infoY > curY + 6.0f && selected != null) {
            Render2DHelper.drawRoundedRectFloat(context, lay.sideX + 6.0f, infoY, lay.sideW - 12.0f, infoH, 7.0f,
                    ColorUtils.withAlpha(0xFFFFFFFF, Math.round(12 * alpha)));
            Render2DHelper.drawRoundedOutlineFloat(context, lay.sideX + 6.0f, infoY, lay.sideW - 12.0f, infoH, 7.0f, 0.65f,
                    ColorUtils.withAlpha(0xFFFFFFFF, Math.round(28 * alpha)));

            Render2DHelper.drawScaledText(context, "ACTIVE", lay.sideX + 12.0f, infoY + 6.0f,
                    ColorUtils.withAlpha(accent, Math.round(230 * alpha)), 0.62f, false);
            Render2DHelper.drawScaledText(context,
                    Render2DHelper.trimToWidth(selected.name(), 0.76f, lay.sideW - 24.0f),
                    lay.sideX + 12.0f, infoY + 16.0f,
                    ColorUtils.withAlpha(0xFFFFFFFF, Math.round(240 * alpha)), 0.76f, false);
            Render2DHelper.drawScaledText(context, selected.category().label(),
                    lay.sideX + 12.0f, infoY + 27.0f,
                    ColorUtils.withAlpha(0xFF94A3B8, Math.round(205 * alpha)), 0.64f, false);

            float resetY = infoY + 40.0f;
            float resetH = 19.0f;
            boolean isPano = selected.panorama();
            boolean resetHov = !isPano && isInside(mouseX, mouseY, lay.sideX + 10.0f, resetY, lay.sideW - 20.0f, resetH);
            drawGlassButton(context, "reset_pano", lay.sideX + 10.0f, resetY, lay.sideW - 20.0f, resetH,
                    isPano ? "Panorama Active" : "Reset Panorama", !isPano, false, resetHov, isPano ? alpha * 0.55f : alpha, accent);
        }
    }

    private void drawGlassButton(GuiGraphicsExtractor context, String idKey, float x, float y, float w, float h,
                                 String label, boolean interactive, boolean highlighted, boolean hovered,
                                 float alpha, int accent) {
        OpticumeButtonStyle.renderGlassButton(context, "gal_" + idKey, x, y, w, h,
                interactive, highlighted, hovered, accent, alpha);
        int textCol = ColorUtils.withAlpha(interactive ? 0xFFFFFFFF : 0xFFE2E8F0, Math.round((interactive ? 245 : 175) * alpha));
        OpticumeButtonStyle.drawUiCenteredText(context, label, x + w * 0.5f, y + (h - 7.0f) * 0.5f, 7.0f, textCol, true);
    }

    private void renderGrid(GuiGraphicsExtractor context, Layout lay, float dt, int mouseX, int mouseY, float alpha, int accent) {
        Render2DHelper.drawRoundedRectFloat(context, lay.gridX, lay.bodyY, lay.gridW, lay.bodyH, 10.0f,
                ColorUtils.withAlpha(0xFFFFFFFF, Math.round(12 * alpha)));
        Render2DHelper.drawRoundedOutlineFloat(context, lay.gridX, lay.bodyY, lay.gridW, lay.bodyH, 10.0f, 0.55f,
                ColorUtils.withAlpha(0xFFFFFFFF, Math.round(26 * alpha)));

        List<MenuWallpapers.Wallpaper> items = getFilteredWallpapers();
        float padIn = lay.panelW < 340.0f ? 6.0f : 10.0f;
        float gap = lay.panelW < 340.0f ? 6.0f : 10.0f;
        float innerW = Math.max(48.0f, lay.gridW - padIn * 2.0f - 8.0f);
        float cardW = (innerW - gap * (GRID_COLS - 1)) / GRID_COLS;
        float cardH = cardW / CARD_ASPECT;

        int rows = (items.size() + GRID_COLS - 1) / GRID_COLS;
        float contentH = rows > 0 ? (padIn * 2.0f + rows * cardH + Math.max(0, rows - 1) * gap) : 0.0f;
        maxScroll = Math.max(0.0f, contentH - lay.bodyH);
        targetScroll = Mth.clamp(targetScroll, 0.0f, maxScroll);
        smoothScroll += (targetScroll - smoothScroll) * (1.0f - (float) Math.exp(-18.0f * dt));
        smoothScroll = Mth.clamp(smoothScroll, 0.0f, maxScroll);

        context.enableScissor(
                Math.round(lay.gridX + 2.0f),
                Math.round(lay.bodyY + 2.0f),
                Math.round(lay.gridX + lay.gridW - 2.0f),
                Math.round(lay.bodyY + lay.bodyH - 2.0f)
        );
        try {
            if (items.isEmpty()) {
                String emptyTitle = activeFilter == FilterTab.CUSTOM
                        ? "No custom wallpapers yet"
                        : (activeFilter == FilterTab.FAVORITES ? "No favorite wallpapers yet" : "No matches found");
                String emptySub = activeFilter == FilterTab.CUSTOM
                        ? "Click '+ Upload' or drag & drop PNG/JPG files here"
                        : "Try clearing search or picking another filter";
                float tw = Render2DHelper.getTextWidth(emptyTitle, 0.9f);
                float sw = Render2DHelper.getTextWidth(emptySub, 0.72f);
                Render2DHelper.drawScaledText(context, emptyTitle,
                        lay.gridX + (lay.gridW - tw) * 0.5f, lay.bodyY + lay.bodyH * 0.43f,
                        ColorUtils.withAlpha(0xFFE2E8F0, Math.round(230 * alpha)), 0.9f, false);
                Render2DHelper.drawScaledText(context, emptySub,
                        lay.gridX + (lay.gridW - sw) * 0.5f, lay.bodyY + lay.bodyH * 0.43f + 16.0f,
                        ColorUtils.withAlpha(0xFF94A3B8, Math.round(205 * alpha)), 0.72f, false);
                return;
            }

            String selectedId = MenuWallpapers.selectedId();
            boolean inGrid = isInside(mouseX, mouseY, lay.gridX, lay.bodyY, lay.gridW, lay.bodyH);

            for (int i = 0; i < items.size(); i++) {
                MenuWallpapers.Wallpaper wp = items.get(i);
                int col = i % GRID_COLS;
                int row = i / GRID_COLS;
                float cx = lay.gridX + padIn + col * (cardW + gap);
                float cy = lay.bodyY + padIn + row * (cardH + gap) - smoothScroll;

                if (cy + cardH < lay.bodyY - 4.0f || cy > lay.bodyY + lay.bodyH + 4.0f) {
                    continue;
                }

                boolean isSelected = wp.id().equalsIgnoreCase(selectedId);
                boolean isKeyFocused = !searchFocused && i == focusedCardIdx;
                boolean hov = inGrid && isInside(mouseX, mouseY, cx, cy, cardW, cardH);

                if (isSelected || isKeyFocused || hov) {
                    Render2DHelper.drawGlow(context, cx, cy + 2.0f, cardW, cardH, 7.0f,
                            ColorUtils.withAlpha((isSelected || isKeyFocused) ? accent : 0xFF000000, Math.round(((isSelected || isKeyFocused) ? 85 : 70) * alpha)));
                }

                if (wp.panorama()) {
                    Render2DHelper.drawGradientRoundedRectFloat(context, cx, cy, cardW, cardH, 7.0f,
                            ColorUtils.withAlpha(0xFF1A365D, Math.round(235 * alpha)),
                            ColorUtils.withAlpha(0xFF0E172A, Math.round(240 * alpha)),
                            ColorUtils.withAlpha(0xFF0E172A, Math.round(240 * alpha)),
                            ColorUtils.withAlpha(0xFF1A365D, Math.round(235 * alpha)));
                    float iconSz = Math.min(cardW, cardH) * 0.40f;
                    Render2DHelper.drawTextureFloat(context, TextureAssets.MENU_PANORAMA,
                            cx + (cardW - iconSz) * 0.5f, cy + (cardH - iconSz) * 0.42f, iconSz, iconSz,
                            ColorUtils.withAlpha(0xFFFFFFFF, Math.round(235 * alpha)));
                } else {
                    float wpAspect = Math.max(0.1f, wp.aspect());
                    float u = 0.0f;
                    float v = 0.0f;
                    float uw = 1.0f;
                    float uh = 1.0f;
                    if (CARD_ASPECT > wpAspect) {
                        uh = wpAspect / CARD_ASPECT;
                        v = (1.0f - uh) * 0.5f;
                    } else {
                        uw = CARD_ASPECT / wpAspect;
                        u = (1.0f - uw) * 0.5f;
                    }
                    Render2DHelper.drawRoundedCroppedTextureFloat(context, wp.image(), cx, cy, cardW, cardH, 7.0f,
                            u, v, uw, uh, ColorUtils.withAlpha(0xFFFFFFFF, Math.round(255 * alpha)));
                }

                // Bottom readable gradient overlay with matching rounded bottom corners
                int botFade = ColorUtils.withAlpha(0xFF05070D, Math.round(215 * alpha));
                Render2DHelper.drawGradientRoundedRectFloat(context, cx, cy + cardH * 0.48f, cardW, cardH * 0.52f, 7.0f,
                        0x0005070D, botFade, botFade, 0x0005070D);

                // Top-right Favorite & Custom Delete pills
                float badgeSize = 15.0f;
                float favX = cx + cardW - badgeSize - 5.0f;
                float badgeY = cy + 5.0f;
                boolean isFav = MenuWallpapers.isFavorite(wp.id());
                boolean favHov = inGrid && isInside(mouseX, mouseY, favX, badgeY, badgeSize, badgeSize);
                if (isFav || hov || favHov) {
                    int favBg = isFav ? ColorUtils.withAlpha(0xFF201806, Math.round(190 * alpha))
                            : ColorUtils.withAlpha(0xFF090C14, Math.round((favHov ? 195 : 140) * alpha));
                    Render2DHelper.drawRoundedRectFloat(context, favX, badgeY, badgeSize, badgeSize, 4.5f, favBg);
                    int starCol = isFav ? 0xFFFACC15 : (favHov ? 0xFFFFFFFF : 0xFFCBD5E1);
                    Render2DHelper.drawScaledText(context, isFav ? "★" : "☆", favX + 3.8f, badgeY + 3.2f,
                            ColorUtils.withAlpha(starCol, Math.round(240 * alpha)), 0.78f, false);
                }

                if (wp.custom()) {
                    float delX = favX - badgeSize - 4.0f;
                    boolean delHov = inGrid && isInside(mouseX, mouseY, delX, badgeY, badgeSize, badgeSize);
                    if (hov || delHov) {
                        Render2DHelper.drawRoundedRectFloat(context, delX, badgeY, badgeSize, badgeSize, 4.5f,
                                ColorUtils.withAlpha(delHov ? 0xFF991B1B : 0xFF090C14, Math.round(185 * alpha)));
                        Render2DHelper.drawScaledText(context, "×", delX + 4.3f, badgeY + 3.0f,
                                ColorUtils.withAlpha(delHov ? 0xFFFFFFFF : 0xFFFCA5A5, Math.round(240 * alpha)), 0.82f, false);
                    }
                }

                // Bottom title + tag
                String tag = isSelected ? "ACTIVE" : (wp.panorama() ? "LIVE" : shortTag(wp.category()));
                float tagW = Render2DHelper.getTextWidth(tag, 0.58f) + 8.0f;
                float textMaxW = Math.max(24.0f, cardW - tagW - 14.0f);
                Render2DHelper.drawScaledText(context, Render2DHelper.trimToWidth(wp.name(), 0.74f, textMaxW),
                        cx + 7.0f, cy + cardH - 15.0f,
                        ColorUtils.withAlpha(0xFFFFFFFF, Math.round(245 * alpha)), 0.74f, true);

                float pillX = cx + cardW - tagW - 6.0f;
                float pillY = cy + cardH - 16.5f;
                Render2DHelper.drawRoundedRectFloat(context, pillX, pillY, tagW, 11.5f, 3.5f,
                        isSelected ? ColorUtils.withAlpha(accent, Math.round(180 * alpha))
                                : ColorUtils.withAlpha(0xFFFFFFFF, Math.round(30 * alpha)));
                Render2DHelper.drawScaledText(context, tag, pillX + 4.0f, pillY + 2.8f,
                        ColorUtils.withAlpha(0xFFFFFFFF, Math.round(245 * alpha)), 0.58f, false);

                Render2DHelper.drawRoundedOutlineFloat(context, cx, cy, cardW, cardH, 7.0f, (isSelected || isKeyFocused) ? 1.4f : 0.75f,
                        (isSelected || isKeyFocused)
                                ? ColorUtils.withAlpha(accent, Math.round(240 * alpha))
                                : ColorUtils.withAlpha(0xFFFFFFFF, Math.round((hov ? 110 : 42) * alpha)));
            }

            if (maxScroll > 1.0f) {
                float trackX = lay.gridX + lay.gridW - 6.0f;
                float trackY = lay.bodyY + 8.0f;
                float trackH = Math.max(24.0f, lay.bodyH - 16.0f);
                float thumbH = Math.max(24.0f, trackH * (lay.bodyH / Math.max(lay.bodyH, contentH)));
                float thumbY = trackY + (trackH - thumbH) * (smoothScroll / maxScroll);
                Render2DHelper.drawRoundedRectFloat(context, trackX, trackY, 3.0f, trackH, 1.5f,
                        ColorUtils.withAlpha(0xFFFFFFFF, Math.round(24 * alpha)));
                Render2DHelper.drawRoundedRectFloat(context, trackX, thumbY, 3.0f, thumbH, 1.5f,
                        ColorUtils.withAlpha(accent, Math.round(205 * alpha)));
            }
        } finally {
            context.disableScissor();
        }
    }

    private void renderToast(GuiGraphicsExtractor context, Layout lay, float alpha, int accent) {
        if (statusToast.isEmpty()) return;
        long age = System.currentTimeMillis() - toastAtMs;
        if (age > 3400L) {
            statusToast = "";
            return;
        }
        float fade = age > 2800L ? (1.0f - (age - 2800L) / 600.0f) : 1.0f;
        float ta = alpha * Mth.clamp(fade, 0.0f, 1.0f);
        float tw = Render2DHelper.getTextWidth(statusToast, 0.76f) + 22.0f;
        float tx = lay.panelX + (lay.panelW - tw) * 0.5f;
        float ty = lay.panelY + lay.panelH + 6.0f;
        if (ty + 22.0f > height) ty = lay.panelY + lay.panelH - 28.0f;

        Render2DHelper.drawRoundedRectFloat(context, tx, ty, tw, 20.0f, 7.0f,
                ColorUtils.withAlpha(0xFF0A0E17, Math.round(215 * ta)));
        Render2DHelper.drawRoundedOutlineFloat(context, tx, ty, tw, 20.0f, 7.0f, 0.85f,
                ColorUtils.withAlpha(accent, Math.round(190 * ta)));
        Render2DHelper.drawScaledText(context, statusToast, tx + 11.0f, ty + 6.0f,
                ColorUtils.withAlpha(0xFFFFFFFF, Math.round(245 * ta)), 0.76f, false);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        int btn = ClickGuiScreen.normalizeMouseButton(event.button());
        if (btn != 0) return super.mouseClicked(event, doubled);

        double mx = event.x();
        double my = event.y();
        float reveal = openProgress * openProgress * (3.0f - 2.0f * openProgress);
        Layout lay = computeLayout(reveal);

        // Search box & clear button
        if (!searchQuery.isEmpty() && isInside(mx, my, lay.searchX + lay.searchW - 16.0f, lay.headerBtnY, 16.0f, lay.headerBtnH)) {
            searchQuery = "";
            targetScroll = 0.0f;
            playClick();
            return true;
        }
        if (isInside(mx, my, lay.searchX, lay.headerBtnY, lay.searchW, lay.headerBtnH)) {
            searchFocused = true;
            return true;
        }
        searchFocused = false;

        if (isInside(mx, my, lay.uploadX, lay.headerBtnY, lay.uploadW, lay.headerBtnH)) {
            playClick();
            openNativeFilePicker();
            return true;
        }
        if (isInside(mx, my, lay.folderX, lay.headerBtnY, lay.folderW, lay.headerBtnH)) {
            playClick();
            CustomWallpaperStore.openWallpapersFolder();
            setToast("Opened custom wallpapers folder");
            return true;
        }
        if (isInside(mx, my, lay.backX, lay.headerBtnY, lay.backW, lay.headerBtnH)) {
            playClick();
            onClose();
            return true;
        }

        // Sidebar filter tabs
        FilterTab[] tabs = FilterTab.values();
        float curY = lay.bodyY + 23.0f;
        float rowGap = lay.bodyH < 175.0f ? 2.0f : 3.0f;
        float rowH = Math.min(23.0f, Math.max(13.0f, (lay.bodyH - 26.0f) / tabs.length - rowGap));
        for (FilterTab tab : tabs) {
            if (isInside(mx, my, lay.sideX + 6.0f, curY, lay.sideW - 12.0f, rowH)) {
                playClick();
                activeFilter = tab;
                targetScroll = 0.0f;
                smoothScroll = 0.0f;
                return true;
            }
            curY += rowH + rowGap;
        }

        // Reset Panorama button inside active info box
        MenuWallpapers.Wallpaper selected = MenuWallpapers.selected();
        float infoH = 66.0f;
        float infoY = lay.bodyY + lay.bodyH - infoH - 7.0f;
        if (infoY > curY + 6.0f && selected != null && !selected.panorama()) {
            if (isInside(mx, my, lay.sideX + 10.0f, infoY + 40.0f, lay.sideW - 20.0f, 19.0f)) {
                playClick();
                selectWallpaper("panorama");
                return true;
            }
        }

        // Scrollbar drag start
        if (maxScroll > 1.0f && isInside(mx, my, lay.gridX + lay.gridW - 10.0f, lay.bodyY, 10.0f, lay.bodyH)) {
            draggingScrollbar = true;
            updateScrollFromMouse(my, lay);
            return true;
        }

        // Grid cards click
        if (isInside(mx, my, lay.gridX, lay.bodyY, lay.gridW, lay.bodyH)) {
            List<MenuWallpapers.Wallpaper> items = getFilteredWallpapers();
            float padIn = lay.panelW < 340.0f ? 6.0f : 10.0f;
            float gap = lay.panelW < 340.0f ? 6.0f : 10.0f;
            float innerW = Math.max(48.0f, lay.gridW - padIn * 2.0f - 8.0f);
            float cardW = (innerW - gap * (GRID_COLS - 1)) / GRID_COLS;
            float cardH = cardW / CARD_ASPECT;
            float badgeSize = 15.0f;

            for (int i = 0; i < items.size(); i++) {
                MenuWallpapers.Wallpaper wp = items.get(i);
                int col = i % GRID_COLS;
                int row = i / GRID_COLS;
                float cx = lay.gridX + padIn + col * (cardW + gap);
                float cy = lay.bodyY + padIn + row * (cardH + gap) - smoothScroll;
                if (cy + cardH < lay.bodyY || cy > lay.bodyY + lay.bodyH) continue;

                float favX = cx + cardW - badgeSize - 5.0f;
                float badgeY = cy + 5.0f;
                if (isInside(mx, my, favX, badgeY, badgeSize, badgeSize)) {
                    playClick();
                    MenuWallpapers.toggleFavorite(wp.id());
                    return true;
                }
                if (wp.custom()) {
                    float delX = favX - badgeSize - 4.0f;
                    if (isInside(mx, my, delX, badgeY, badgeSize, badgeSize)) {
                        playClick();
                        if (MenuWallpapers.deleteCustom(wp.id())) {
                            if (wp.id().equalsIgnoreCase(previewShownId)) {
                                triggerBackdropTransition("panorama");
                            }
                            setToast("Deleted '" + wp.name() + "'");
                        }
                        return true;
                    }
                }
                if (isInside(mx, my, cx, cy, cardW, cardH)) {
                    playClick();
                    selectWallpaper(wp.id());
                    if (doubled) {
                        onClose();
                    }
                    return true;
                }
            }
        }

        return super.mouseClicked(event, doubled);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (draggingScrollbar) {
            updateScrollFromMouse(event.y(), computeLayout(1.0f));
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        draggingScrollbar = false;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        Layout lay = computeLayout(1.0f);
        if (isInside(mouseX, mouseY, lay.gridX, lay.bodyY, lay.gridW, lay.bodyH)) {
            targetScroll = Mth.clamp(targetScroll - (float) (verticalAmount * 34.0f), 0.0f, maxScroll);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void onFilesDrop(List<Path> paths) {
        if (paths == null || paths.isEmpty()) return;
        setToast("Importing image(s)...");
        CustomWallpaperStore.importFilesAsync(paths, msg -> {
            setToast(msg);
            String currentId = MenuWallpapers.selectedId();
            if (!currentId.equalsIgnoreCase(previewShownId)) {
                triggerBackdropTransition(currentId);
                activeFilter = FilterTab.CUSTOM;
                targetScroll = 0.0f;
                smoothScroll = 0.0f;
            }
        });
    }

    @Override
    public boolean keyPressed(KeyEvent input) {
        if (input.key() == InputConstants.KEY_ESCAPE) {
            if (searchFocused && !searchQuery.isEmpty()) {
                searchQuery = "";
                return true;
            }
            onClose();
            return true;
        }
        if (searchFocused) {
            if (input.key() == InputConstants.KEY_BACKSPACE) {
                if (input.hasControlDown()) {
                    searchQuery = "";
                } else if (!searchQuery.isEmpty()) {
                    searchQuery = searchQuery.substring(0, searchQuery.length() - 1);
                }
                targetScroll = 0.0f;
                return true;
            }
            if (input.isPaste() && minecraft != null) {
                String clip = minecraft.keyboardHandler.getClipboard();
                if (clip != null) {
                    String cleaned = clip.replaceAll("\\p{Cntrl}", "").trim();
                    if (!cleaned.isEmpty()) {
                        searchQuery = (searchQuery + cleaned);
                        if (searchQuery.length() > 36) searchQuery = searchQuery.substring(0, 36);
                        targetScroll = 0.0f;
                    }
                }
                return true;
            }
        } else {
            List<MenuWallpapers.Wallpaper> items = getFilteredWallpapers();
            if (!items.isEmpty()) {
                int k = input.key();
                int nextIdx = -1;
                if (k == InputConstants.KEY_LEFT || k == InputConstants.KEY_A) {
                    nextIdx = (focusedCardIdx < 0) ? 0 : Math.max(0, focusedCardIdx - 1);
                } else if (k == InputConstants.KEY_RIGHT || k == InputConstants.KEY_D) {
                    nextIdx = (focusedCardIdx < 0) ? 0 : Math.min(items.size() - 1, focusedCardIdx + 1);
                } else if (k == InputConstants.KEY_UP || k == InputConstants.KEY_W) {
                    nextIdx = (focusedCardIdx < 0) ? 0 : Math.max(0, focusedCardIdx - GRID_COLS);
                } else if (k == InputConstants.KEY_DOWN || k == InputConstants.KEY_S) {
                    nextIdx = (focusedCardIdx < 0) ? 0 : Math.min(items.size() - 1, focusedCardIdx + GRID_COLS);
                } else if (k == InputConstants.KEY_RETURN || k == 335) {
                    int applyIdx = (focusedCardIdx >= 0 && focusedCardIdx < items.size()) ? focusedCardIdx : 0;
                    playClick();
                    selectWallpaper(items.get(applyIdx).id());
                    return true;
                }
                if (nextIdx >= 0) {
                    focusedCardIdx = nextIdx;
                    ensureCardVisible(nextIdx);
                    return true;
                }
            }
        }
        return super.keyPressed(input);
    }

    private void ensureCardVisible(int index) {
        Layout lay = computeLayout(1.0f);
        float padIn = 10.0f;
        float gap = 10.0f;
        float innerW = Math.max(120.0f, lay.gridW - padIn * 2.0f - 8.0f);
        float cardW = (innerW - gap * (GRID_COLS - 1)) / GRID_COLS;
        float cardH = cardW / CARD_ASPECT;
        int row = index / GRID_COLS;
        float cardTop = padIn + row * (cardH + gap);
        float cardBot = cardTop + cardH;
        if (cardTop < targetScroll) {
            targetScroll = Math.max(0.0f, cardTop - padIn);
        } else if (cardBot > targetScroll + lay.bodyH) {
            targetScroll = Math.min(maxScroll, cardBot - lay.bodyH + padIn);
        }
    }

    @Override
    public boolean charTyped(CharacterEvent input) {
        if (searchFocused) {
            String s = input.codepointAsString();
            if (s != null && !s.isEmpty()) {
                if (s.charAt(0) > 127) {
                    s = CommandFix.translateRuToEn(s);
                }
                for (int i = 0; i < s.length(); i++) {
                    char cp = s.charAt(i);
                    if (cp >= 32 && cp < 127 && searchQuery.length() < 36) {
                        searchQuery += cp;
                    }
                }
                targetScroll = 0.0f;
                return true;
            }
        }
        return super.charTyped(input);
    }

    @Override
    public void removed() {
        searchFocused = false;
        if (minecraft != null) {
            minecraft.textInputManager().stopTextInput(this);
        }
        super.removed();
    }

    @Override
    public void onClose() {
        if (minecraft != null && minecraft.gui != null) {
            minecraft.gui.setScreen(parent);
        }
    }

    private void selectWallpaper(String id) {
        MenuWallpapers.selectById(id);
        triggerBackdropTransition(id);
        MenuWallpapers.Wallpaper wp = MenuWallpapers.getById(id);
        if (wp != null) {
            setToast("Applied: " + wp.name());
        }
    }

    private void triggerBackdropTransition(String nextId) {
        if (nextId == null || nextId.equalsIgnoreCase(previewShownId)) return;
        previewFromId = previewShownId;
        previewShownId = nextId;
        backdropFade = 0.0f;
    }

    private static synchronized SDL_DialogFileCallback getOrCreateDialogCallback() {
        if (sharedDialogCallback != null) {
            return sharedDialogCallback;
        }
        // Keep one reusable native callback closure for the process lifetime so it is never freed mid-upcall.
        sharedDialogCallback = SDL_DialogFileCallback.create((userdata, filelist, filter) -> {
            List<Path> picked = new ArrayList<>();
            if (filelist != 0L) {
                for (int i = 0; i < 64; i++) {
                    long ptr = MemoryUtil.memGetAddress(filelist + (long) i * Pointer.POINTER_SIZE);
                    if (ptr == 0L) break;
                    String pathStr = MemoryUtil.memUTF8Safe(ptr);
                    if (pathStr != null && !pathStr.isBlank()) {
                        picked.add(Path.of(pathStr));
                    }
                }
            }
            DialogRequest req = ACTIVE_DIALOGS.remove(userdata);
            if (req == null) {
                return;
            }
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (mc != null) {
                mc.execute(() -> {
                    freeDialogBuffers(req);
                    if (!picked.isEmpty()) {
                        if (mc.gui != null && mc.gui.screen() == req.screen) {
                            req.screen.onFilesDrop(picked);
                        } else {
                            CustomWallpaperStore.importFilesAsync(picked, null);
                        }
                    }
                });
            } else {
                freeDialogBuffers(req);
            }
        });
        return sharedDialogCallback;
    }

    private static void freeDialogBuffers(DialogRequest req) {
        if (req == null) return;
        if (req.filters != null) {
            try {
                req.filters.free();
            } catch (Throwable ignored) {
            }
        }
        if (req.nameBuf != null) {
            MemoryUtil.memFree(req.nameBuf);
        }
        if (req.patternBuf != null) {
            MemoryUtil.memFree(req.patternBuf);
        }
    }

    private void openNativeFilePicker() {
        SDL_DialogFileFilter.Buffer filters = null;
        ByteBuffer nameBuf = null;
        ByteBuffer patternBuf = null;
        long token = 0L;
        try {
            filters = SDL_DialogFileFilter.calloc(1);
            nameBuf = MemoryUtil.memUTF8("Images (PNG, JPG, BMP, GIF)", true);
            patternBuf = MemoryUtil.memUTF8("png;jpg;jpeg;bmp;gif", true);
            filters.get(0).name(nameBuf).pattern(patternBuf);

            SDL_DialogFileCallback callback = getOrCreateDialogCallback();
            token = NEXT_DIALOG_TOKEN.incrementAndGet();
            ACTIVE_DIALOGS.put(token, new DialogRequest(this, filters, nameBuf, patternBuf));

            String defaultLoc = CustomWallpaperStore.getWallpapersDir().getAbsolutePath();
            long winHandle = (minecraft != null && minecraft.getWindow() != null) ? minecraft.getWindow().handle() : 0L;
            SDLDialog.SDL_ShowOpenFileDialog(callback, token, winHandle, filters, defaultLoc, true);
        } catch (Throwable t) {
            if (token != 0L) {
                ACTIVE_DIALOGS.remove(token);
            }
            if (filters != null) {
                try {
                    filters.free();
                } catch (Throwable ignored) {
                }
            }
            if (nameBuf != null) MemoryUtil.memFree(nameBuf);
            if (patternBuf != null) MemoryUtil.memFree(patternBuf);
            CustomWallpaperStore.openWallpapersFolder();
            setToast("Drop PNG/JPG into opened folder or onto this window");
        }
    }

    private void updateScrollFromMouse(double mouseY, Layout lay) {
        if (maxScroll <= 0.0f) return;
        float trackY = lay.bodyY + 8.0f;
        float trackH = Math.max(24.0f, lay.bodyH - 16.0f);
        float norm = Mth.clamp((float) ((mouseY - trackY) / trackH), 0.0f, 1.0f);
        targetScroll = norm * maxScroll;
        smoothScroll = targetScroll;
    }

    private List<MenuWallpapers.Wallpaper> getFilteredWallpapers() {
        String q = searchQuery.trim().toLowerCase(Locale.ROOT);
        List<MenuWallpapers.Wallpaper> out = new ArrayList<>();
        for (MenuWallpapers.Wallpaper wp : MenuWallpapers.all()) {
            if (!matchesTab(wp, activeFilter)) continue;
            if (!q.isEmpty() && !wp.name().toLowerCase(Locale.ROOT).contains(q)
                    && !wp.category().label().toLowerCase(Locale.ROOT).contains(q)) {
                continue;
            }
            out.add(wp);
        }
        return out;
    }

    private static int countByFilter(FilterTab tab) {
        int c = 0;
        for (MenuWallpapers.Wallpaper wp : MenuWallpapers.all()) {
            if (matchesTab(wp, tab)) c++;
        }
        return c;
    }

    private static boolean matchesTab(MenuWallpapers.Wallpaper wp, FilterTab tab) {
        return switch (tab) {
            case ALL -> true;
            case FAVORITES -> MenuWallpapers.isFavorite(wp.id());
            case MINECRAFT -> wp.category() == MenuWallpapers.Category.MINECRAFT;
            case SKY_ABSTRACT -> wp.category() == MenuWallpapers.Category.SKY_ABSTRACT;
            case ANIME -> wp.category() == MenuWallpapers.Category.ANIME;
            case CUSTOM -> wp.custom();
        };
    }

    private static String shortTag(MenuWallpapers.Category cat) {
        return switch (cat) {
            case MINECRAFT -> "MC";
            case SKY_ABSTRACT -> "SKY";
            case ANIME -> "ANIME";
            case CUSTOM -> "CUSTOM";
        };
    }

    private void setToast(String msg) {
        this.statusToast = msg == null ? "" : msg;
        this.toastAtMs = System.currentTimeMillis();
    }

    private void playClick() {
        if (minecraft != null && minecraft.getSoundManager() != null) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
        }
    }

    private Layout computeLayout(float reveal) {
        float maxPanelW = Math.max(200.0f, width - 16.0f);
        float maxPanelH = Math.max(120.0f, height - 16.0f);
        float panelW = Math.min(maxPanelW, Math.min(690.0f, Math.max(380.0f, width - 28.0f)));
        float panelH = Math.min(maxPanelH, Math.min(430.0f, Math.max(260.0f, height - 28.0f)));
        float panelX = Math.max(4.0f, (width - panelW) * 0.5f);
        float basePanelY = (height - panelH) * 0.5f;
        float panelY = Mth.clamp(basePanelY + (1.0f - reveal) * 10.0f, 4.0f, Math.max(4.0f, height - panelH - 4.0f));
        boolean compact = panelW < 340.0f;
        float pad = compact ? 8.0f : 12.0f;

        float headerBtnH = compact ? 18.0f : 21.0f;
        float headerBtnY = panelY + (compact ? 10.0f : 12.0f);
        float backW = compact ? 36.0f : 48.0f;
        float folderW = compact ? 42.0f : 54.0f;
        float uploadW = compact ? 52.0f : 68.0f;
        float btnGap = compact ? 4.0f : 6.0f;

        float backX = panelX + panelW - pad - backW;
        float folderX = backX - folderW - btnGap;
        float uploadX = folderX - uploadW - btnGap;
        float titleReserve = compact ? 48.0f : 126.0f;
        float searchGap = compact ? 4.0f : 8.0f;
        float availSearch = Math.max(42.0f, uploadX - (panelX + pad + titleReserve) - searchGap);
        float searchW = Math.min(148.0f, availSearch);
        float searchX = uploadX - searchW - searchGap;

        float headerH = compact ? 36.0f : 44.0f;
        float bodyY = panelY + headerH;
        float bodyH = Math.max(40.0f, panelH - headerH - pad);
        float sideW = Math.min(148.0f, Math.max(compact ? 74.0f : 118.0f, panelW * (compact ? 0.27f : 0.22f)));
        float sideX = panelX + pad;
        float gridGap = compact ? 6.0f : 8.0f;
        float gridX = sideX + sideW + gridGap;
        float gridW = Math.max(64.0f, panelX + panelW - pad - gridX);

        return new Layout(panelX, panelY, panelW, panelH, pad,
                headerBtnY, headerBtnH, searchX, searchW, uploadX, uploadW, folderX, folderW, backX, backW,
                bodyY, bodyH, sideX, sideW, gridX, gridW);
    }

    private record Layout(
            float panelX, float panelY, float panelW, float panelH, float pad,
            float headerBtnY, float headerBtnH,
            float searchX, float searchW, float uploadX, float uploadW,
            float folderX, float folderW, float backX, float backW,
            float bodyY, float bodyH, float sideX, float sideW, float gridX, float gridW
    ) {
    }

    private static boolean isInside(double mx, double my, float x, float y, float w, float h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }
}
