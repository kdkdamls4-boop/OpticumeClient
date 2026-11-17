package dev.opticumeclient.client.ui.menu;

import dev.opticumeclient.client.gui.ClickGuiScreen;
import dev.opticumeclient.client.render.ColorUtils;
import dev.opticumeclient.client.render.Render2DHelper;
import dev.opticumeclient.client.render.TextureAssets;
import dev.opticumeclient.client.render.ThemeManager;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import com.mojang.blaze3d.platform.InputConstants;

/**
 * OpticumeClient Main Menu faithfully modeled after Desire's sleek centered glass menu
 * ({@code Welcome!} wave header, gradient username, 5 centered animated glass buttons with
 * sweeping specular sheen + sliding icons, bottom {@code Gallery} pill + carousel) on top of
 * Minecraft's live animated 3D Panorama by default (with Gallery wallpaper switching support).
 */
public class OpticumeMainMenuScreen extends Screen {
    private static final String[] BUTTON_TITLES = {
            "Singleplayer", "Multiplayer", "AltManager", "Settings", "Quit"
    };
    private static final Identifier[] BUTTON_ICONS = {
            TextureAssets.MENU_SINGLEPLAYER,
            TextureAssets.MENU_MULTIPLAYER,
            TextureAssets.MENU_ALTMANAGER,
            TextureAssets.MENU_SETTINGS,
            TextureAssets.MENU_QUIT
    };

    private static volatile boolean bypassNextTitleScreen;

    private float layoutK = 1.0f;
    private float btnX, btnY0, btnW, btnH, btnStep, btnRadius;
    private float galleryBtnX, galleryBtnY, galleryBtnW, galleryBtnH;
    private float cardCenterX, cardCenterY, backBtnX, applyBtnX, actionBtnY, actionBtnW, actionBtnH;

    private final float[] btnHover = new float[BUTTON_TITLES.length];
    private final float[] btnPress = new float[BUTTON_TITLES.length];
    private final float[] btnSheen = new float[BUTTON_TITLES.length];
    private final boolean[] btnWasHovered = new boolean[BUTTON_TITLES.length];

    private float time = 0.0f;
    private float menuFade = 1.0f;
    private float galleryAnim = 0.0f;
    private float galleryBtnHover = 0.0f;
    private float backHover = 0.0f;
    private float applyHover = 0.0f;
    private float galleryPos = 0.0f;
    private float galleryTarget = 0.0f;
    private boolean galleryOpen = false;
    private boolean draggingGallery = false;
    private double dragStartX = 0.0;
    private double lastDragX = 0.0;
    private boolean didDrag = false;

    private int wallpaperFrom = 0;
    private int wallpaperShown = 0;
    private float wallpaperFade = 1.0f;

    private float parallaxX = 0.0f;
    private float parallaxY = 0.0f;
    private int pendingAction = -1;
    private float pendingTimer = 0.0f;
    private long lastNanos = System.nanoTime();

    public OpticumeMainMenuScreen() {
        super(Component.literal("OpticumeClient Menu"));
        this.wallpaperShown = MenuWallpapers.selectedIndex();
        this.wallpaperFrom = this.wallpaperShown;
        this.galleryPos = this.wallpaperShown;
        this.galleryTarget = this.wallpaperShown;
    }

    public static void requestVanillaBypass() {
        bypassNextTitleScreen = true;
    }

    public static boolean consumeVanillaBypass() {
        boolean v = bypassNextTitleScreen;
        bypassNextTitleScreen = false;
        return v;
    }

    @Override
    protected void init() {
        super.init();
        if (minecraft != null && minecraft.gameRenderer != null) {
            minecraft.gameRenderer.panorama().startSpin();
        }
        this.wallpaperShown = MenuWallpapers.selectedIndex();
        this.wallpaperFrom = this.wallpaperShown;
        this.wallpaperFade = 1.0f;
        this.lastNanos = System.nanoTime();
        recomputeLayout();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        // Rendered inside extractRenderState
    }

    private void recomputeLayout() {
        float w = Math.max(320.0f, this.width);
        float h = Math.max(200.0f, this.height);
        this.layoutK = Mth.clamp(Math.min(w / 640.0f, h / 360.0f), 0.72f, 1.38f);

        float k = this.layoutK;
        this.btnW = 160.0f * k;
        this.btnH = 27.0f * k;
        this.btnStep = 33.5f * k;
        this.btnRadius = 8.0f * k;
        this.btnX = (w - this.btnW) * 0.5f;

        float totalStackH = (BUTTON_TITLES.length - 1) * this.btnStep + this.btnH;
        float topBlockOffset = 34.0f * k;
        this.btnY0 = (h - totalStackH) * 0.5f + topBlockOffset * 0.45f;

        this.galleryBtnW = 92.0f * k;
        this.galleryBtnH = 23.0f * k;
        this.galleryBtnX = (w - this.galleryBtnW) * 0.5f;
        this.galleryBtnY = h - 38.0f * k;

        this.cardCenterX = w * 0.5f;
        this.cardCenterY = h * 0.44f;
        this.actionBtnW = 72.0f * k;
        this.actionBtnH = 22.0f * k;
        float actionGap = 8.0f * k;
        float totalActionsW = this.actionBtnW * 2.0f + actionGap;
        this.backBtnX = this.cardCenterX - totalActionsW * 0.5f;
        this.applyBtnX = this.backBtnX + this.actionBtnW + actionGap;
        this.actionBtnY = this.cardCenterY + 56.0f * k;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        TextureAssets.ensureInitialized();
        recomputeLayout();

        long now = System.nanoTime();
        float dt = Math.min(0.05f, Math.max(0.0001f, (now - lastNanos) / 1.0e9f));
        lastNanos = now;
        time += dt;

        // Parallax update
        float targetPx = Mth.clamp((mouseX / Math.max(1.0f, (float) width) - 0.5f) * 2.0f, -1.0f, 1.0f);
        float targetPy = Mth.clamp((mouseY / Math.max(1.0f, (float) height) - 0.5f) * 2.0f, -1.0f, 1.0f);
        float pLerp = 1.0f - (float) Math.exp(-6.0f * dt);
        parallaxX += (targetPx - parallaxX) * pLerp;
        parallaxY += (targetPy - parallaxY) * pLerp;

        // Gallery animation & menu fade
        float gTarget = galleryOpen ? 1.0f : 0.0f;
        galleryAnim += (gTarget - galleryAnim) * (1.0f - (float) Math.exp(-10.0f * dt));
        if (Math.abs(gTarget - galleryAnim) < 0.002f) galleryAnim = gTarget;
        menuFade = 1.0f - Mth.clamp(galleryAnim, 0.0f, 1.0f);

        if (!draggingGallery) {
            galleryPos += (galleryTarget - galleryPos) * (1.0f - (float) Math.exp(-12.0f * dt));
        }

        // Crossfade between previous & current wallpaper
        if (wallpaperFade < 1.0f) {
            wallpaperFade = Math.min(1.0f, wallpaperFade + dt * 2.4f);
        }

        // Pending clicked action delay for Desire press animation
        if (pendingAction >= 0) {
            pendingTimer += dt;
            if (pendingTimer >= 0.08f) {
                int act = pendingAction;
                pendingAction = -1;
                pendingTimer = 0.0f;
                executeButton(act);
            }
        }

        // Background (Vanilla Panorama default OR selected wallpaper)
        MenuWallpapers.Wallpaper shownWp = MenuWallpapers.get(wallpaperShown);
        if (wallpaperFade < 1.0f) {
            MenuWallpapers.Wallpaper fromWp = MenuWallpapers.get(wallpaperFrom);
            MenuBackdrop.render(context, () -> super.extractPanorama(context, delta),
                    fromWp, width, height, parallaxX, parallaxY, 1.0f, 1.04f);
            if (shownWp != null && !shownWp.panorama()) {
                MenuBackdrop.renderWallpaperCover(context, shownWp, width, height, parallaxX, parallaxY, wallpaperFade, 1.04f);
            }
        } else {
            MenuBackdrop.render(context, () -> super.extractPanorama(context, delta),
                    shownWp, width, height, parallaxX, parallaxY, 1.0f, 1.04f);
        }

        // Main menu header + 5 buttons + Gallery trigger
        if (menuFade > 0.01f) {
            drawHeader(context, menuFade);
            drawButtons(context, mouseX, mouseY, dt, menuFade);
            drawGalleryButton(context, mouseX, mouseY, dt, menuFade);
        }

        // Gallery overlay carousel
        if (galleryAnim > 0.01f) {
            drawGalleryOverlay(context, mouseX, mouseY, dt, galleryAnim);
        }

        // Vanilla Minecraft HUD footer strip
        drawFooter(context);
    }

    private void drawHeader(GuiGraphicsExtractor context, float fadeAlpha) {
        float intro = easeOutCubic(Mth.clamp(time / 0.55f, 0.0f, 1.0f));
        float alpha = intro * fadeAlpha;
        if (alpha <= 0.01f) return;

        float k = this.layoutK;
        String greeting = "Welcome!";
        float welcomeScale = 1.85f * k;
        float totalW = Render2DHelper.getTextWidth(greeting, welcomeScale);
        float baseX = width * 0.5f - totalW * 0.5f;
        float baseY = btnY0 - 44.0f * k + (1.0f - intro) * 10.0f * k;

        // Per-letter Desire wave animation on "Welcome!"
        float cx = baseX;
        for (int i = 0; i < greeting.length(); i++) {
            String ch = String.valueOf(greeting.charAt(i));
            float charDelay = i * 0.045f;
            float lp = easeOutCubic(Mth.clamp((time - charDelay) / 0.35f, 0.0f, 1.0f));
            float waveY = (float) Math.sin(time * 2.4f + i * 0.55f) * 1.4f * k;
            float dropY = (1.0f - lp) * 6.0f * k;
            int col = ColorUtils.withAlpha(0xFFFFFFFF, Math.round(245 * lp * alpha));
            Render2DHelper.drawScaledText(context, ch, cx, baseY + waveY + dropY, col, welcomeScale, true);
            cx += Render2DHelper.getTextWidth(ch, welcomeScale);
        }

        // Active account username in animated theme gradient + mini player face
        String username = (minecraft != null && minecraft.getUser() != null) ? minecraft.getUser().getName() : "Player";
        float nameScale = 1.02f * k;
        float nameW = Render2DHelper.getTextWidth(username, nameScale);
        int headSize = Math.max(9, Math.round(11 * k));
        float totalUserW = headSize + 5.0f * k + nameW;
        float ux = width * 0.5f - totalUserW * 0.5f;
        float uy = baseY + 21.5f * k;

        Render2DHelper.drawPlayerHeadByUuid(context, UUIDUtil.createOfflinePlayerUUID(username),
                Math.round(ux), Math.round(uy - 1.0f), headSize, alpha);

        int c1 = ThemeManager.getInstance().getCurrentTheme().getPrimaryColor();
        int c2 = ThemeManager.getInstance().getCurrentTheme().getSecondaryColor();
        float charX = ux + headSize + 5.0f * k;
        for (int i = 0; i < username.length(); i++) {
            String ch = String.valueOf(username.charAt(i));
            float t = (username.length() <= 1) ? 0.5f : (float) i / (username.length() - 1);
            float wave = (float) ((Math.sin(time * 2.5f - i * 0.25f) + 1.0) * 0.5);
            int letterColor = ColorUtils.interpolateColor(c1, c2, Mth.clamp(t * 0.65f + wave * 0.35f, 0.0f, 1.0f));
            Render2DHelper.drawScaledText(context, ch, charX, uy, ColorUtils.withAlpha(letterColor, Math.round(245 * alpha)), nameScale, true);
            charX += Render2DHelper.getTextWidth(ch, nameScale);
        }
    }

    private void drawButtons(GuiGraphicsExtractor context, int mouseX, int mouseY, float dt, float fadeAlpha) {
        float k = this.layoutK;
        int accent = ThemeManager.getInstance().getAccentColor(0);

        for (int i = 0; i < BUTTON_TITLES.length; i++) {
            float stagger = easeOutCubic(Mth.clamp((time - i * 0.045f) / 0.38f, 0.0f, 1.0f));
            float alpha = stagger * fadeAlpha;
            if (alpha <= 0.01f) continue;

            float yBase = btnY0 + i * btnStep + (1.0f - stagger) * 14.0f * k;
            boolean hovered = !galleryOpen && pendingAction < 0
                    && isInside(mouseX, mouseY, btnX, yBase, btnW, btnH);

            if (hovered && !btnWasHovered[i]) {
                btnSheen[i] = 0.001f;
            }
            btnWasHovered[i] = hovered;

            float hvTarget = hovered ? 1.0f : 0.0f;
            btnHover[i] += (hvTarget - btnHover[i]) * (1.0f - (float) Math.exp((hovered ? -16.0f : -11.0f) * dt));
            float prTarget = (pendingAction == i) ? 1.0f : 0.0f;
            btnPress[i] += (prTarget - btnPress[i]) * (1.0f - (float) Math.exp(-22.0f * dt));

            if (btnSheen[i] > 0.0f) {
                btnSheen[i] += dt / 0.55f;
                if (btnSheen[i] >= 1.0f) btnSheen[i] = 0.0f;
            }

            float hv = Mth.clamp(btnHover[i], 0.0f, 1.0f);
            float scale = 1.0f + 0.032f * hv - 0.038f * btnPress[i];
            float sw = btnW * scale;
            float sh = btnH * scale;
            float sx = btnX - (sw - btnW) * 0.5f;
            float sy = yBase - (sh - btnH) * 0.5f;
            float rad = Math.min(12.0f, btnRadius * scale);

            if (hv > 0.02f) {
                Render2DHelper.drawGlow(context, sx, sy + 2.0f, sw, sh, 6.0f * k,
                        ColorUtils.withAlpha(accent, Math.round(52 * hv * alpha)));
            }

            // Translucent dark-glass base + Desire white glass layer
            int darkBack = ColorUtils.withAlpha(0xFF090D16, Math.round(Mth.lerp(hv, 108.0f, 142.0f) * alpha));
            int whiteLayer = ColorUtils.withAlpha(0xFFFFFFFF, Math.round(Mth.lerp(hv, 12.0f, 34.0f) * alpha));
            Render2DHelper.drawRoundedRect(context, sx, sy, sw, sh, rad, darkBack);
            Render2DHelper.drawRoundedRect(context, sx, sy, sw, sh, rad, whiteLayer);

            if (btnSheen[i] > 0.002f) {
                OpticumeButtonStyle.drawSheen(context, sx, sy, sw, sh, btnSheen[i], alpha * (0.45f + 0.55f * hv));
            }

            int borderCol = ColorUtils.interpolateColor(
                    ColorUtils.withAlpha(0xFFFFFFFF, Math.round(28 * alpha)),
                    ColorUtils.withAlpha(ColorUtils.interpolateColor(0xFFFFFFFF, accent, 0.55f), Math.round(150 * alpha)),
                    hv
            );
            Render2DHelper.drawRoundedOutline(context, sx, sy, sw, sh, rad, 1.0f, borderCol);

            // Animated icon on left + centered crisp label
            String title = BUTTON_TITLES[i];
            float textScale = 0.96f * k;
            float iconSize = 11.5f * k;
            float labelW = Render2DHelper.getTextWidth(title, textScale);
            float iconX = sx + 14.0f * k - 3.0f * k * hv;
            float iconY = sy + (sh - iconSize) * 0.5f;
            int iconCol = ColorUtils.interpolateColor(
                    ColorUtils.withAlpha(0xFFFFFFFF, Math.round(165 * alpha)),
                    ColorUtils.withAlpha(0xFFFFFFFF, Math.round(255 * alpha)),
                    hv
            );
            Render2DHelper.drawTexture(context, BUTTON_ICONS[i], iconX, iconY, iconSize, iconSize, iconCol);

            float textCenterX = sx + sw * 0.5f + 5.0f * k;
            float labelX = textCenterX - labelW * 0.5f;
            float labelY = sy + (sh - 8.0f * textScale) * 0.5f;
            int textCol = ColorUtils.interpolateColor(
                    ColorUtils.withAlpha(0xFFFFFFFF, Math.round(215 * alpha)),
                    ColorUtils.withAlpha(0xFFFFFFFF, Math.round(255 * alpha)),
                    hv
            );
            Render2DHelper.drawScaledText(context, title, labelX, labelY, textCol, textScale, true);
        }
    }

    private void drawGalleryButton(GuiGraphicsExtractor context, int mouseX, int mouseY, float dt, float fadeAlpha) {
        float intro = easeOutCubic(Mth.clamp((time - 0.16f) / 0.4f, 0.0f, 1.0f));
        float alpha = intro * fadeAlpha;
        if (alpha <= 0.01f) return;

        float k = this.layoutK;
        float slideY = (1.0f - intro) * 14.0f * k;
        boolean hovered = !galleryOpen && pendingAction < 0
                && isInside(mouseX, mouseY, galleryBtnX, galleryBtnY, galleryBtnW, galleryBtnH);
        galleryBtnHover += ((hovered ? 1.0f : 0.0f) - galleryBtnHover) * (1.0f - (float) Math.exp(-14.0f * dt));
        float hv = Mth.clamp(galleryBtnHover, 0.0f, 1.0f);

        float scale = 1.0f + 0.05f * hv;
        float sw = galleryBtnW * scale;
        float sh = galleryBtnH * scale;
        float x = galleryBtnX - (sw - galleryBtnW) * 0.5f;
        float y = galleryBtnY + slideY - (sh - galleryBtnH) * 0.5f;
        float rad = Math.min(12.0f, sh * 0.48f);
        int accent = ThemeManager.getInstance().getAccentColor(0);

        if (hv > 0.01f) {
            Render2DHelper.drawGlow(context, x, y + 2.0f, sw, sh, 5.0f * k,
                    ColorUtils.withAlpha(accent, Math.round(55 * hv * alpha)));
        }

        int bg = ColorUtils.withAlpha(0xFF0A0E18, Math.round(Mth.lerp(hv, 118.0f, 154.0f) * alpha));
        Render2DHelper.drawRoundedRect(context, x, y, sw, sh, rad, bg);
        Render2DHelper.drawRoundedOutline(context, x, y, sw, sh, rad, 1.0f,
                ColorUtils.interpolateColor(
                        ColorUtils.withAlpha(0xFFFFFFFF, Math.round(30 * alpha)),
                        ColorUtils.withAlpha(accent, Math.round(145 * alpha)),
                        hv));

        float thumbSize = sh - 8.0f * k;
        float tx = x + 6.0f * k;
        float ty = y + (sh - thumbSize) * 0.5f;
        MenuWallpapers.Wallpaper current = MenuWallpapers.selected();
        if (current != null) {
            int tint = current.panorama()
                    ? ColorUtils.withAlpha(accent, Math.round(235 * alpha))
                    : ColorUtils.rgba(255, 255, 255, Math.round(255 * alpha));
            Render2DHelper.drawTexture(context, current.preview(), tx, ty, thumbSize, thumbSize, tint);
        }

        float textScale = 0.85f * k;
        float lw = Render2DHelper.getTextWidth("Gallery", textScale);
        float lx = tx + thumbSize + (x + sw - tx - thumbSize - lw) * 0.5f - 2.0f * k;
        float ly = y + (sh - 8.0f * textScale) * 0.5f;
        Render2DHelper.drawScaledText(context, "Gallery", lx, ly,
                ColorUtils.withAlpha(0xFFFFFFFF, Math.round(Mth.lerp(hv, 200.0f, 255.0f) * alpha)), textScale, false);
    }

    private void drawGalleryOverlay(GuiGraphicsExtractor context, int mouseX, int mouseY, float dt, float open) {
        float k = this.layoutK;
        float ease = easeOutCubic(open);
        int accent = ThemeManager.getInstance().getAccentColor(0);

        Render2DHelper.drawGradientRect(context, 0, height * 0.2f, width, height * 0.8f,
                0x00020409, ColorUtils.withAlpha(0xFF020409, Math.round(185 * open)));

        int count = MenuWallpapers.size();
        int centerIdx = wrapIndex(Math.round(galleryPos));
        MenuWallpapers.Wallpaper focused = MenuWallpapers.get(centerIdx);
        float baseCardW = 148.0f * k;
        float baseCardH = 84.0f * k;

        if (focused != null) {
            String title = focused.name();
            float tScale = 1.15f * k;
            float titleY = cardCenterY - baseCardH * 0.5f - 26.0f * k + (1.0f - ease) * 10.0f * k;
            float tw = Render2DHelper.getTextWidth(title, tScale);
            Render2DHelper.drawScaledText(context, title, cardCenterX - tw * 0.5f, titleY,
                    ColorUtils.withAlpha(0xFFFFFFFF, Math.round(240 * open)), tScale, true);

            if (centerIdx == MenuWallpapers.selectedIndex()) {
                float tagScale = 0.78f * k;
                float tagW = Render2DHelper.getTextWidth("Current", tagScale);
                Render2DHelper.drawScaledText(context, "Current", cardCenterX - tagW * 0.5f, titleY + 11.5f * k,
                        ColorUtils.withAlpha(accent, Math.round(235 * open)), tagScale, false);
            }
        }

        // Render farthest cards first so center card draws cleanly on top
        Integer[] order = new Integer[count];
        for (int i = 0; i < count; i++) order[i] = i;
        final float p = galleryPos;
        java.util.Arrays.sort(order, (l, r) -> Float.compare(Math.abs(wrapOffset(r, p)), Math.abs(wrapOffset(l, p))));

        float spacing = 132.0f * k;
        for (int idx : order) {
            float off = wrapOffset(idx, p);
            float dist = Math.abs(off);
            if (dist > 2.35f) continue;

            float focus = 1.0f - Mth.clamp(dist, 0.0f, 1.0f);
            float scale = (0.70f + 0.30f * focus) * (0.90f + 0.10f * ease);
            float cw = baseCardW * scale;
            float ch = baseCardH * scale;
            float offScaled = Math.signum(off) * (Math.min(dist, 1.0f) * spacing + Math.max(0.0f, dist - 1.0f) * spacing * 0.80f);
            float cx = cardCenterX + offScaled * ease;
            float cy = cardCenterY + (1.0f - ease) * 20.0f * k;
            float x = cx - cw * 0.5f;
            float y = cy - ch * 0.5f;
            float cAlpha = open * (0.45f + 0.55f * focus) * Mth.clamp((2.35f - dist) * 1.4f, 0.0f, 1.0f);
            if (cAlpha <= 0.01f) continue;

            Render2DHelper.drawGlow(context, x, y + 4.0f * k, cw, ch, 10.0f * k,
                    ColorUtils.withAlpha(0xFF000000, Math.round(135 * cAlpha)));

            MenuWallpapers.Wallpaper wp = MenuWallpapers.get(idx);
            if (wp != null && wp.panorama()) {
                // Stylized Panorama Card Preview
                Render2DHelper.drawGradientRect(context, x, y, cw, ch,
                        ColorUtils.withAlpha(0xFF1A365D, Math.round(240 * cAlpha)),
                        ColorUtils.withAlpha(0xFF0E172A, Math.round(245 * cAlpha)));
                float cubeSize = Math.min(cw, ch) * 0.42f;
                Render2DHelper.drawTexture(context, TextureAssets.MENU_PANORAMA,
                        cx - cubeSize * 0.5f, cy - cubeSize * 0.55f, cubeSize, cubeSize,
                        ColorUtils.withAlpha(0xFFFFFFFF, Math.round(235 * cAlpha)));
                if (focus > 0.35f) {
                    Render2DHelper.drawCenteredText(context, "3D Live Panorama", cx, cy + cubeSize * 0.35f,
                            ColorUtils.withAlpha(0xFFE2E8F0, Math.round(220 * focus * cAlpha)), false);
                }
            } else if (wp != null) {
                Render2DHelper.drawCroppedTexture(context, wp.preview(), x, y, cw, ch,
                        0.0f, 0.21875f, 1.0f, 0.5625f,
                        ColorUtils.rgba(255, 255, 255, Math.round(255 * cAlpha)));
            }

            float dim = 1.0f - focus;
            if (dim > 0.01f) {
                Render2DHelper.drawRect(context, x, y, cw, ch, ColorUtils.withAlpha(0xFF060910, Math.round(125 * dim * cAlpha)));
            }

            boolean applied = idx == MenuWallpapers.selectedIndex();
            int edge = applied
                    ? ColorUtils.withAlpha(accent, Math.round(235 * cAlpha))
                    : ColorUtils.withAlpha(0xFFFFFFFF, Math.round(68 * focus * cAlpha));
            Render2DHelper.drawBorder(context, Math.round(x), Math.round(y), Math.round(cw), Math.round(ch), 1, edge);
        }

        // Gallery Action Buttons (Back & Apply)
        float ay = actionBtnY + (1.0f - ease) * 16.0f * k;
        boolean backHov = galleryOpen && isInside(mouseX, mouseY, backBtnX, actionBtnY, actionBtnW, actionBtnH);
        boolean applyHov = galleryOpen && isInside(mouseX, mouseY, applyBtnX, actionBtnY, actionBtnW, actionBtnH);
        backHover += ((backHov ? 1.0f : 0.0f) - backHover) * (1.0f - (float) Math.exp(-14.0f * dt));
        applyHover += ((applyHov ? 1.0f : 0.0f) - applyHover) * (1.0f - (float) Math.exp(-14.0f * dt));

        OpticumeButtonStyle.renderVanillaButton(context, "gal_back", Math.round(backBtnX), Math.round(ay),
                Math.round(actionBtnW), Math.round(actionBtnH), true, backHov, open);
        Render2DHelper.drawCenteredText(context, "Back", backBtnX + actionBtnW * 0.5f, ay + (actionBtnH - 8.0f) * 0.5f,
                ColorUtils.withAlpha(0xFFFFFFFF, Math.round(235 * open)), false);

        boolean already = centerIdx == MenuWallpapers.selectedIndex();
        OpticumeButtonStyle.renderVanillaButton(context, "gal_apply", Math.round(applyBtnX), Math.round(ay),
                Math.round(actionBtnW), Math.round(actionBtnH), !already, applyHov, open);
        Render2DHelper.drawCenteredText(context, already ? "Applied" : "Apply",
                applyBtnX + actionBtnW * 0.5f, ay + (actionBtnH - 8.0f) * 0.5f,
                ColorUtils.withAlpha(0xFFFFFFFF, Math.round((already ? 140 : 250) * open)), false);

        // Dots indicator
        float dotGap = 9.0f * k;
        float dotSize = 4.0f * k;
        float dotsX0 = cardCenterX - ((count - 1) * dotGap + dotSize) * 0.5f;
        float dotsY = actionBtnY + actionBtnH + 13.0f * k;
        for (int i = 0; i < count; i++) {
            float near = 1.0f - Mth.clamp(Math.abs(wrapOffset(i, galleryPos)), 0.0f, 1.0f);
            float sz = dotSize * (1.0f + 0.35f * near);
            int dc = ColorUtils.withAlpha(0xFFFFFFFF, Math.round((95 + 150 * near) * open));
            Render2DHelper.drawCircle(context, dotsX0 + i * dotGap + sz * 0.5f, dotsY + sz * 0.5f, sz * 0.5f, dc);
        }
    }

    private void drawFooter(GuiGraphicsExtractor context) {
        String ver = "Minecraft " + SharedConstants.getCurrentVersion().name() + " • OpticumeClient";
        Render2DHelper.drawText(context, ver, 8.0f, height - 14.0f, 0xA8E5E7EB, true);

        String acc = (minecraft != null && minecraft.getUser() != null)
                ? "Account: " + minecraft.getUser().getName()
                : "";
        if (!acc.isEmpty()) {
            float w = Render2DHelper.getTextWidth(acc, 1.0f);
            Render2DHelper.drawText(context, acc, width - w - 8.0f, height - 14.0f, 0xA8E5E7EB, true);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        int btn = ClickGuiScreen.normalizeMouseButton(click.button());
        if (btn != 0) return super.mouseClicked(click, doubled);
        double mx = click.x();
        double my = click.y();

        if (galleryOpen) {
            if (isInside(mx, my, backBtnX, actionBtnY, actionBtnW, actionBtnH)) {
                playClick();
                galleryOpen = false;
                return true;
            }
            if (isInside(mx, my, applyBtnX, actionBtnY, actionBtnW, actionBtnH)) {
                int targetIdx = wrapIndex(Math.round(galleryPos));
                if (targetIdx != MenuWallpapers.selectedIndex()) {
                    playClick();
                    wallpaperFrom = wallpaperShown;
                    wallpaperShown = targetIdx;
                    wallpaperFade = 0.0f;
                    MenuWallpapers.select(targetIdx);
                }
                return true;
            }
            float cardsTop = cardCenterY - 52.0f * layoutK;
            float cardsBot = cardCenterY + 52.0f * layoutK;
            if (my >= cardsTop && my <= cardsBot) {
                draggingGallery = true;
                dragStartX = mx;
                lastDragX = mx;
                didDrag = false;
                return true;
            }
            return true;
        }

        if (pendingAction >= 0) return true;

        for (int i = 0; i < BUTTON_TITLES.length; i++) {
            float by = btnY0 + i * btnStep;
            if (isInside(mx, my, btnX, by, btnW, btnH)) {
                playClick();
                pendingAction = i;
                pendingTimer = 0.0f;
                return true;
            }
        }

        if (isInside(mx, my, galleryBtnX, galleryBtnY, galleryBtnW, galleryBtnH)) {
            playClick();
            galleryOpen = true;
            galleryPos = MenuWallpapers.selectedIndex();
            galleryTarget = galleryPos;
            return true;
        }

        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent click, double offsetX, double offsetY) {
        if (galleryOpen && draggingGallery) {
            double dx = click.x() - lastDragX;
            lastDragX = click.x();
            if (Math.abs(click.x() - dragStartX) > 4.0) didDrag = true;
            float spacing = Math.max(40.0f, 132.0f * layoutK);
            galleryPos -= (float) (dx / spacing);
            galleryTarget = galleryPos;
            return true;
        }
        return super.mouseDragged(click, offsetX, offsetY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent click) {
        int btn = ClickGuiScreen.normalizeMouseButton(click.button());
        if (btn == 0 && galleryOpen && draggingGallery) {
            draggingGallery = false;
            if (didDrag) {
                galleryTarget = Math.round(galleryPos);
            } else {
                // Clicked on a card directly -> focus or center it
                int clickedIdx = findCardAt(click.x());
                if (clickedIdx >= 0) {
                    playClick();
                    galleryTarget = Math.round(galleryPos) + wrapOffset(clickedIdx, Math.round(galleryPos));
                }
            }
            didDrag = false;
            return true;
        }
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (galleryOpen && verticalAmount != 0.0) {
            galleryTarget = Math.round(galleryTarget) - (float) Math.signum(verticalAmount);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(KeyEvent input) {
        int key = input.key();
        if (galleryOpen) {
            if (key == InputConstants.KEY_ESCAPE) {
                galleryOpen = false;
                return true;
            }
            if (key == InputConstants.KEY_LEFT) {
                galleryTarget = Math.round(galleryTarget) - 1;
                return true;
            }
            if (key == InputConstants.KEY_RIGHT) {
                galleryTarget = Math.round(galleryTarget) + 1;
                return true;
            }
            if (key == InputConstants.KEY_RETURN) {
                int targetIdx = wrapIndex(Math.round(galleryPos));
                if (targetIdx != MenuWallpapers.selectedIndex()) {
                    wallpaperFrom = wallpaperShown;
                    wallpaperShown = targetIdx;
                    wallpaperFade = 0.0f;
                    MenuWallpapers.select(targetIdx);
                }
                galleryOpen = false;
                return true;
            }
        }
        return super.keyPressed(input);
    }

    private void executeButton(int index) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.gui == null) return;
        switch (index) {
            case 0 -> mc.gui.setScreen(new SelectWorldScreen(this));
            case 1 -> mc.gui.setScreen(new JoinMultiplayerScreen(this));
            case 2 -> mc.gui.setScreen(new OpticumeAltScreen(this));
            case 3 -> mc.gui.setScreen(new OptionsScreen(this, mc.options));
            case 4 -> mc.stop();
            default -> {
            }
        }
    }

    private int findCardAt(double mx) {
        float spacing = 132.0f * layoutK;
        float halfW = 74.0f * layoutK;
        int count = MenuWallpapers.size();
        for (int i = 0; i < count; i++) {
            float off = wrapOffset(i, galleryPos);
            if (Math.abs(off) > 2.2f) continue;
            float cx = cardCenterX + off * spacing;
            if (Math.abs(mx - cx) <= halfW) {
                return i;
            }
        }
        return -1;
    }

    private int wrapIndex(int raw) {
        int count = MenuWallpapers.size();
        return count <= 0 ? 0 : Math.floorMod(raw, count);
    }

    private float wrapOffset(int index, float pos) {
        int count = MenuWallpapers.size();
        if (count <= 0) return 0.0f;
        float d = (index - pos) % count;
        if (d > count * 0.5f) d -= count;
        if (d < -count * 0.5f) d += count;
        return d;
    }

    private void playClick() {
        if (minecraft != null && minecraft.getSoundManager() != null) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }
    }

    private static float easeOutCubic(float t) {
        float inv = 1.0f - Mth.clamp(t, 0.0f, 1.0f);
        return 1.0f - inv * inv * inv;
    }

    private static boolean isInside(double mx, double my, float x, float y, float w, float h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }
}
