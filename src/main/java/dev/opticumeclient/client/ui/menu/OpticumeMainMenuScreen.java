package dev.opticumeclient.client.ui.menu;

import com.mojang.blaze3d.platform.InputConstants;
import dev.opticumeclient.client.gui.ClickGuiScreen;
import dev.opticumeclient.client.render.ColorUtils;
import dev.opticumeclient.client.render.KawaseBlurRenderer;
import dev.opticumeclient.client.render.Render2DHelper;
import dev.opticumeclient.client.render.TextureAssets;
import dev.opticumeclient.client.render.ThemeManager;
import dev.opticumeclient.client.render.font.OpticumeFonts;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import org.joml.Matrix3x2fStack;

import java.util.Arrays;

/**
 * OpticumeClient Main Menu faithfully styled 1:1 after Desire's reference menu:
 * - Virtual FIXED_GUI_SCALE = 2.0f layout over 1920x1080 reference resolution
 * - Cursive "Welcome!" wave animation using ETERNAL_HEAVEN MSDF font
 * - Gradient username directly below "Welcome!" in DREAMLINE_SF (without player head icon)
 * - Compact centered blurred liquid-glass button column (Singleplayer..Quit)
 * - Compact Gallery pill right below Quit + draggable wallpaper carousel
 * - Clean full-bleed backdrop without corner footer labels
 */
public class OpticumeMainMenuScreen extends Screen {
    private static final float FIXED_GUI_SCALE = 2.0f;
    private static final float LAYOUT_W = 1920.0f;
    private static final float LAYOUT_H = 1080.0f;

    private static final String[] BUTTON_TITLES = {
            "Singleplayer", "Multiplayer", "AltManager", "Settings", "Quit"
    };
    private static final Identifier[] BUTTON_ICONS = {
            TextureAssets.DESIRE_MENU_SINGLEPLAYER,
            TextureAssets.DESIRE_MENU_MULTIPLAYER,
            TextureAssets.DESIRE_MENU_ALTMANAGER,
            TextureAssets.DESIRE_MENU_SETTINGS,
            TextureAssets.DESIRE_MENU_QUIT
    };
    private static final int BUTTON_COUNT = BUTTON_TITLES.length;

    private static final float BUTTON_HEIGHT = 48.4f;
    private static final float BUTTON_STEP = 58.9f;
    private static final float BUTTON_RADIUS = 13.125f;
    private static final float BUTTON_EXTRA_W = 95.15f;
    private static final float BUTTON_WIDTH_SCALE = 1.5f;
    private static final float ICON_X = 21.0f;
    private static final float ICON_SIZE = 15.5f;
    private static final float LABEL_SIZE = 13.125f;

    private static final float WELCOME_SIZE = 52.0f;
    private static final float NAME_SIZE = 10.0f;
    private static final float NAME_OFFSET = 22.0f;
    private static final float WELCOME_GAP = 80.0f;
    private static final float WELCOME_OFFSET = NAME_OFFSET + WELCOME_GAP;

    private static final float GALLERY_BTN_W = 116.0f;
    private static final float GALLERY_BTN_H = 34.0f;
    private static final float GALLERY_BTN_GAP = 16.0f;

    private static final float CARD_VISIBLE = 2.6f;
    private static final float CARD_W = 330.0f;
    private static final float CARD_H = 185.6f;
    private static final float CARD_SPACING = 372.0f;
    private static final float CARD_RADIUS = 14.0f;
    private static final float CARD_SIDE_SCALE = 0.78f;
    private static final float ACTION_W = 150.0f;
    private static final float ACTION_H = 38.0f;
    private static final float ACTION_GAP = 14.0f;
    private static final float ACTION_RADIUS = 38.0f / 6.0f;
    private static final float DRAG_THRESHOLD = 4.0f;

    private static final float WELCOME_START = 0.12f;
    private static final float WELCOME_STAGGER = 0.045f;
    private static final float WELCOME_DUR = 0.75f;
    private static final float NAME_START = 0.55f;
    private static final float NAME_DUR = 0.70f;
    private static final float BUTTON_START = 0.32f;
    private static final float BUTTON_STAGGER = 0.065f;
    private static final float BUTTON_DUR = 0.85f;
    private static final float GALLERY_BTN_START = BUTTON_START + BUTTON_COUNT * BUTTON_STAGGER;
    private static final float PRESS_DELAY = 0.14f;
    private static final float SHEEN_DUR = 0.65f;

    private static final int FILL_IDLE = 0x12FFFFFF;
    private static final int FILL_HOVER = 0x20FFFFFF;
    private static final int OUTLINE_IDLE = 0x0AFFFFFF;
    private static final int OUTLINE_HOVER = 0x1AFFFFFF;
    private static final int ICON_IDLE = 0x7BFFFFFF;
    private static final int LABEL_IDLE = 0xB8FFFFFF;

    private static volatile boolean bypassNextTitleScreen;

    private float layoutK = 0.5f;
    private float uiScale = 1.0f;
    private float fixedW = 960.0f;
    private float fixedH = 540.0f;
    private float buttonX;
    private float columnW;
    private float buttonH;
    private final float[] buttonY = new float[BUTTON_COUNT];
    private final float[] buttonW = new float[BUTTON_COUNT];

    private float galleryBtnX;
    private float galleryBtnY;
    private float cardCenterX;
    private float cardCenterY;
    private float backBtnX;
    private float applyBtnX;
    private float actionBtnY;

    private final Spring[] hover = Spring.array(BUTTON_COUNT);
    private final Spring[] press = Spring.array(BUTTON_COUNT);
    private final boolean[] wasHovered = new boolean[BUTTON_COUNT];
    private final float[] sheen = new float[BUTTON_COUNT];

    private float time = 0.0f;
    private float menuFade = 1.0f;
    private final Spring galleryAnim = new Spring();
    private final Spring galleryPos = new Spring();
    private float galleryTarget = 0.0f;
    private boolean galleryOpen = false;
    private boolean draggingGallery = false;
    private boolean didDrag = false;
    private float dragStartX = 0.0f;
    private float dragLastX = 0.0f;
    private float dragStartTarget = 0.0f;
    private float dragVelocity = 0.0f;

    private final Spring galleryBtnHover = new Spring();
    private final Spring backHover = new Spring();
    private final Spring applyHover = new Spring();
    private final Spring parallaxX = new Spring();
    private final Spring parallaxY = new Spring();

    private int wallpaperFrom = 0;
    private int wallpaperShown = 0;
    private float wallpaperFade = 1.0f;

    private int pendingAction = -1;
    private float pendingTimer = 0.0f;
    private long lastNanos = System.nanoTime();

    public OpticumeMainMenuScreen() {
        super(Component.literal("OpticumeClient Menu"));
        this.wallpaperShown = MenuWallpapers.selectedIndex();
        this.wallpaperFrom = this.wallpaperShown;
        this.galleryTarget = this.wallpaperShown;
        this.galleryPos.reset(this.wallpaperShown);
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
        this.time = 0.0f;
        this.pendingAction = -1;
        Arrays.fill(this.sheen, 1.0f);
        for (int i = 0; i < BUTTON_COUNT; i++) {
            hover[i].reset(0.0f);
            press[i].reset(0.0f);
            wasHovered[i] = false;
        }
        this.galleryOpen = false;
        this.draggingGallery = false;
        this.galleryAnim.reset(0.0f);
        this.galleryBtnHover.reset(0.0f);
        this.backHover.reset(0.0f);
        this.applyHover.reset(0.0f);
        this.wallpaperShown = MenuWallpapers.selectedIndex();
        this.wallpaperFrom = this.wallpaperShown;
        this.wallpaperFade = 1.0f;
        this.galleryTarget = this.wallpaperShown;
        this.galleryPos.reset(this.galleryTarget);
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
        // Rendered inside extractRenderState for ordered blur stratum composition
    }

    private float getWindowGuiScale() {
        if (minecraft == null || minecraft.getWindow() == null) return 2.0f;
        return Math.max(1.0f, (float) minecraft.getWindow().getGuiScale());
    }

    private float toFixed(double guiCoord) {
        return (float) (guiCoord * getWindowGuiScale() / FIXED_GUI_SCALE);
    }

    private void recomputeLayout() {
        Minecraft mc = Minecraft.getInstance();
        float winW = (mc != null && mc.getWindow() != null) ? Math.max(640.0f, mc.getWindow().getWidth()) : 1920.0f;
        float winH = (mc != null && mc.getWindow() != null) ? Math.max(360.0f, mc.getWindow().getHeight()) : 1080.0f;

        this.fixedW = winW / FIXED_GUI_SCALE;
        this.fixedH = winH / FIXED_GUI_SCALE;

        float scale = Math.min(winW / LAYOUT_W, winH / LAYOUT_H);
        this.layoutK = scale / FIXED_GUI_SCALE; // 0.5f at 1920x1080
        this.uiScale = scale;                   // 1.0f at 1920x1080

        float k = this.layoutK;
        this.buttonH = BUTTON_HEIGHT * k;

        float maxColW = 0.0f;
        for (int i = 0; i < BUTTON_COUNT; i++) {
            float labelW = OptWidthMenu(BUTTON_TITLES[i], LABEL_SIZE * k);
            maxColW = Math.max(maxColW, BUTTON_EXTRA_W * k + labelW);
        }
        this.columnW = maxColW * BUTTON_WIDTH_SCALE;
        this.buttonX = (this.fixedW - this.columnW) * 0.5f;

        float headH = WELCOME_OFFSET * this.uiScale;
        float buttonsH = (BUTTON_COUNT - 1) * BUTTON_STEP * k + this.buttonH
                + (GALLERY_BTN_GAP + GALLERY_BTN_H) * k;
        float firstY = (this.fixedH - (headH + buttonsH)) * 0.5f + headH;

        for (int i = 0; i < BUTTON_COUNT; i++) {
            this.buttonY[i] = firstY + i * BUTTON_STEP * k;
            this.buttonW[i] = this.columnW;
        }

        this.galleryBtnX = this.buttonX + (this.columnW - GALLERY_BTN_W * k) * 0.5f;
        this.galleryBtnY = this.buttonY[BUTTON_COUNT - 1] + this.buttonH + GALLERY_BTN_GAP * k;

        this.cardCenterX = this.fixedW * 0.5f;
        this.cardCenterY = this.fixedH * 0.5f;
        this.actionBtnY = this.cardCenterY + CARD_H * k * 0.5f + 64.0f * k;
        float pair = (ACTION_W * 2.0f + ACTION_GAP) * k;
        this.backBtnX = (this.fixedW - pair) * 0.5f;
        this.applyBtnX = this.backBtnX + (ACTION_W + ACTION_GAP) * k;
    }

    private float OptWidthMenu(String text, float pxSize) {
        float w = OpticumeFonts.getDirectWidth(OpticumeFonts.KEY_MENU_INTER, text, pxSize);
        if (w > 0.0f) return w;
        return Render2DHelper.getTextWidth(text, pxSize / OpticumeFonts.BASE_EM_PX);
    }

    private static void drawMenuText(GuiGraphicsExtractor context, String text, float x, float y, float pxSize, int color, boolean shadowFallback) {
        if (!OpticumeFonts.drawDirect(context, OpticumeFonts.KEY_MENU_INTER, text, x, y, pxSize, color)) {
            Render2DHelper.drawScaledText(context, text, x, y, color, pxSize / OpticumeFonts.BASE_EM_PX, shadowFallback);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        TextureAssets.ensureInitialized();
        recomputeLayout();

        long now = System.nanoTime();
        float dt = Math.min(0.05f, Math.max(0.0001f, (now - lastNanos) / 1.0e9f));
        lastNanos = now;
        time += dt;

        float mxFixed = toFixed(mouseX);
        float myFixed = toFixed(mouseY);

        float nx = Mth.clamp(mxFixed / Math.max(1.0f, fixedW) * 2.0f - 1.0f, -1.0f, 1.0f);
        float ny = Mth.clamp(myFixed / Math.max(1.0f, fixedH) * 2.0f - 1.0f, -1.0f, 1.0f);
        parallaxX.update(nx, dt, 18.0f, 1.0f);
        parallaxY.update(ny, dt, 18.0f, 1.0f);

        galleryAnim.update(galleryOpen ? 1.0f : 0.0f, dt, 190.0f, 1.0f);
        if (draggingGallery) {
            galleryPos.update(galleryTarget, dt, 700.0f, 1.0f);
        } else {
            galleryPos.update(galleryTarget, dt, 170.0f, 0.78f);
        }
        menuFade = 1.0f - clamp01(galleryAnim.value);

        if (wallpaperFade < 1.0f) {
            wallpaperFade = Math.min(1.0f, wallpaperFade + dt / 0.85f);
        }

        if (pendingAction >= 0) {
            pendingTimer += dt;
            if (pendingTimer >= PRESS_DELAY) {
                int act = pendingAction;
                pendingAction = -1;
                pendingTimer = 0.0f;
                executeButton(act);
                return;
            }
        }

        // Stratum 0: Fullscreen animated backdrop (Live Panorama or Gallery wallpaper)
        MenuWallpapers.Wallpaper shownWp = MenuWallpapers.get(wallpaperShown);
        if (wallpaperFade < 1.0f) {
            MenuWallpapers.Wallpaper fromWp = MenuWallpapers.get(wallpaperFrom);
            MenuBackdrop.render(context, () -> super.extractPanorama(context, delta),
                    fromWp, width, height, parallaxX.value, parallaxY.value, 1.0f, 1.045f);
            if (shownWp != null && !shownWp.panorama()) {
                MenuBackdrop.renderWallpaperCover(context, shownWp, width, height, parallaxX.value, parallaxY.value, wallpaperFade, 1.045f);
            }
        } else {
            MenuBackdrop.render(context, () -> super.extractPanorama(context, delta),
                    shownWp, width, height, parallaxX.value, parallaxY.value, 1.0f, 1.045f);
        }

        // Stratum 1: Capture and Kawase-blur rendered backdrop right before foreground buttons
        context.nextStratum();
        context.blurBeforeThisStratum();
        KawaseBlurRenderer.requestMenuBlur(3, 2.0f);

        // Foreground rendered in Desire's exact FIXED_GUI_SCALE = 2.0 coordinate space
        float fixToGui = FIXED_GUI_SCALE / getWindowGuiScale();
        Matrix3x2fStack pose = context.pose();
        pose.pushMatrix();
        pose.scale(fixToGui, fixToGui);

        if (menuFade > 0.004f) {
            drawWelcome(context);
            drawButtons(context, mxFixed, myFixed, dt);
            drawGalleryButton(context, mxFixed, myFixed, dt);
        }
        if (galleryAnim.value > 0.004f) {
            drawGalleryOverlay(context, mxFixed, myFixed, dt);
        }

        pose.popMatrix();
    }

    private void drawWelcome(GuiGraphicsExtractor context) {
        float centerX = buttonX + columnW * 0.5f;
        float nameY = buttonY[0] - NAME_OFFSET * uiScale;
        float wSize = WELCOME_SIZE * uiScale;
        String title = "Welcome!";

        float titleW = OpticumeFonts.getDirectWidth(OpticumeFonts.KEY_ETERNAL_HEAVEN, title, wSize);
        boolean fallback = titleW <= 0.0f;
        if (fallback) {
            titleW = Render2DHelper.getTextWidth(title, 1.85f * uiScale);
        }

        float titleX = centerX - titleW * 0.5f;
        float titleY = nameY - WELCOME_GAP * uiScale
                + (float) Math.sin(time * 1.15f) * 1.4f * uiScale * smoothstep(clamp01(time - 1.2f));
        Matrix3x2fStack pose = context.pose();

        for (int c = 0; c < title.length(); c++) {
            float p = clamp01((time - WELCOME_START - c * WELCOME_STAGGER) / WELCOME_DUR);
            if (p <= 0.0f) continue;

            String ch = title.substring(c, c + 1);
            float chW = fallback
                    ? Render2DHelper.getTextWidth(ch, 1.85f * uiScale)
                    : OpticumeFonts.getDirectWidth(OpticumeFonts.KEY_ETERNAL_HEAVEN, ch, wSize);
            float chX = titleX + (fallback
                    ? Render2DHelper.getTextWidth(title.substring(0, c + 1), 1.85f * uiScale)
                    : OpticumeFonts.getDirectWidth(OpticumeFonts.KEY_ETERNAL_HEAVEN, title.substring(0, c + 1), wSize)) - chW;
            float rise = (1.0f - easeOutBack(p, 2.2f)) * 18.0f * uiScale;
            float sc = 0.6f + 0.4f * easeOutBack(p, 1.6f);
            int a = Math.round(255.0f * easeOutCubic(clamp01(p * 1.8f)) * menuFade);
            int col = ColorUtils.rgba(247, 247, 252, a);

            float pivotX = chX + chW * 0.5f;
            float pivotY = titleY + wSize * 0.6f;
            pose.pushMatrix();
            pose.translate(pivotX, pivotY + rise);
            pose.scale(sc, sc);
            pose.translate(-pivotX, -pivotY);
            if (!OpticumeFonts.drawDirect(context, OpticumeFonts.KEY_ETERNAL_HEAVEN, ch, chX, titleY, wSize, col)) {
                Render2DHelper.drawScaledText(context, ch, chX, titleY + 12.0f * uiScale, col, 1.85f * uiScale, true);
            }
            pose.popMatrix();
        }

        // Gradient username in DREAMLINE_SF directly under Welcome! (without head icon)
        float np = clamp01((time - NAME_START) / NAME_DUR);
        if (np <= 0.0f) return;
        float ne = easeOutExpo(np);
        int na = Math.round(255.0f * easeOutCubic(np) * menuFade);
        if (na <= 2) return;

        String username = (minecraft != null && minecraft.getUser() != null) ? minecraft.getUser().getName() : "Player";
        float nSize = NAME_SIZE * uiScale;
        float nOffsetY = (1.0f - ne) * 8.0f * uiScale;
        int c1 = ColorUtils.withAlpha(ThemeManager.getInstance().getCurrentTheme().getPrimaryColor(), na);
        int c2 = ColorUtils.withAlpha(ThemeManager.getInstance().getCurrentTheme().getSecondaryColor(), na);

        float nameW = OpticumeFonts.getDirectWidth(OpticumeFonts.KEY_DREAMLINE_SF, username, nSize);
        if (nameW > 0.0f) {
            OpticumeFonts.drawDirectGradient(
                    context, OpticumeFonts.KEY_DREAMLINE_SF, username,
                    centerX - nameW * 0.5f, nameY + nOffsetY, nSize,
                    c1, c2, Math.max(1.1f, nameW * 0.25f), 3000L, false
            );
        } else {
            float fw = Render2DHelper.getTextWidth(username, 0.9f * uiScale);
            Render2DHelper.drawScaledText(context, username, centerX - fw * 0.5f, nameY + nOffsetY, c1, 0.9f * uiScale, true);
        }
    }

    private float getMainButtonEnter(int idx) {
        return clamp01((time - BUTTON_START - idx * BUTTON_STAGGER) / BUTTON_DUR);
    }

    private boolean isMainButtonInteractive(int idx) {
        return !galleryOpen && pendingAction < 0 && getMainButtonEnter(idx) > 0.4f;
    }

    private float getGalleryButtonEnter() {
        return clamp01((time - GALLERY_BTN_START) / BUTTON_DUR);
    }

    private boolean isGalleryButtonInteractive() {
        return !galleryOpen && pendingAction < 0 && MenuWallpapers.size() > 0 && getGalleryButtonEnter() > 0.4f;
    }

    private void drawButtons(GuiGraphicsExtractor context, float mx, float my, float dt) {
        float k = this.layoutK;
        int accent = ThemeManager.getInstance().getCurrentTheme().getPrimaryColor();
        Matrix3x2fStack pose = context.pose();

        for (int i = 0; i < BUTTON_COUNT; i++) {
            float p = getMainButtonEnter(i);
            if (p <= 0.0f) continue;
            float alpha = easeOutCubic(clamp01(p * 1.7f)) * menuFade;
            if (alpha <= 0.004f) continue;

            float enter = easeOutBack(p, 1.5f);
            float x = buttonX;
            float y = buttonY[i];
            float w = buttonW[i];
            float h = buttonH;

            boolean hovered = isMainButtonInteractive(i) && isInside(mx, my, x, y, w, h);
            if (hovered && !wasHovered[i]) sheen[i] = 0.0f;
            wasHovered[i] = hovered;
            sheen[i] = Math.min(1.0f, sheen[i] + dt / SHEEN_DUR);

            hover[i].update(hovered ? 1.0f : 0.0f, dt, 260.0f, 0.62f);
            press[i].update(0.0f, dt, 420.0f, 0.5f);
            float hv = clamp01(hover[i].value);

            float scale = (0.9f + 0.1f * enter) * (1.0f + 0.035f * hover[i].value) * (1.0f - 0.06f * press[i].value);
            float cx = x + w * 0.5f;
            float cy = y + h * 0.5f + (1.0f - enter) * 26.0f * k;
            float sw = w * scale;
            float sh = h * scale;
            float sx = cx - sw * 0.5f;
            float sy = cy - sh * 0.5f;
            float radius = BUTTON_RADIUS * k * scale;

            if (hv > 0.01f) {
                Render2DHelper.drawGlow(context, sx, sy + 2.0f * k * hv, sw, sh, 12.0f * k,
                        ColorUtils.withAlpha(accent, Math.round(62.0f * hv * alpha)));
            }

            // Frosted real backdrop blur inside button
            Render2DHelper.drawBlurredRoundedRect(context, sx, sy, sw, sh, radius, 0x380A0D16, alpha);

            pose.pushMatrix();
            pose.translate(cx, cy);
            pose.scale(scale, scale);
            pose.translate(-(x + w * 0.5f), -(y + h * 0.5f));

            float r = BUTTON_RADIUS * k;
            int fill = fadeColor(mixColor(FILL_IDLE, FILL_HOVER, hv), alpha);
            Render2DHelper.drawRoundedRectFloat(context, x, y, w, h, r, fill);

            int borderCol = fadeColor(mixColor(OUTLINE_IDLE, ColorUtils.withAlpha(accent, 90), hv), alpha);
            Render2DHelper.drawRoundedOutlineFloat(context, x, y, w, h, r, 0.45f + 0.35f * hv, borderCol);

            if (sheen[i] < 1.0f) {
                OpticumeButtonStyle.drawSheen(context, x, y, w, h, sheen[i], alpha);
            }

            // Icon + label
            float iconSize = ICON_SIZE * k;
            float iconX = x + ICON_X * k + 5.0f * k * hv;
            float iconY = y + (h - iconSize) * 0.5f;
            int iconCol = fadeColor(mixColor(ICON_IDLE, ColorUtils.withAlpha(accent, 255), hv), alpha);
            Render2DHelper.drawTextureFloat(context, BUTTON_ICONS[i], iconX, iconY, iconSize, iconSize, iconCol);

            float lblSize = LABEL_SIZE * k;
            float lblW = OptWidthMenu(BUTTON_TITLES[i], lblSize);
            int lblCol = fadeColor(mixColor(LABEL_IDLE, 0xFFFFFFFF, hv), alpha);
            float lblX = x + (w - lblW) * 0.5f;
            float lblY = y + (h - lblSize) * 0.5f;
            drawMenuText(context, BUTTON_TITLES[i], lblX, lblY, lblSize, lblCol, false);

            pose.popMatrix();
        }
    }

    private void drawGalleryButton(GuiGraphicsExtractor context, float mx, float my, float dt) {
        if (MenuWallpapers.size() <= 0) return;
        float k = this.layoutK;
        float p = getGalleryButtonEnter();
        if (p <= 0.0f) return;
        float alpha = easeOutCubic(clamp01(p * 1.7f)) * menuFade;
        if (alpha <= 0.004f) return;

        float enter = easeOutBack(p, 1.4f);
        float w = GALLERY_BTN_W * k;
        float h = GALLERY_BTN_H * k;
        boolean hovered = isGalleryButtonInteractive() && isInside(mx, my, galleryBtnX, galleryBtnY, w, h);
        galleryBtnHover.update(hovered ? 1.0f : 0.0f, dt, 260.0f, 0.62f);
        float hv = clamp01(galleryBtnHover.value);

        float sc = (0.9f + 0.1f * enter) * (1.0f + 0.04f * galleryBtnHover.value);
        float cx = galleryBtnX + w * 0.5f;
        float cy = galleryBtnY + h * 0.5f + (1.0f - enter) * 20.0f * k;
        float sw = w * sc;
        float sh = h * sc;
        float x = cx - sw * 0.5f;
        float y = cy - sh * 0.5f;
        float rad = sh * 0.5f;
        int accent = ThemeManager.getInstance().getCurrentTheme().getPrimaryColor();

        if (hv > 0.01f) {
            Render2DHelper.drawGlow(context, x, y + 2.0f * k * hv, sw, sh, 10.0f * k,
                    ColorUtils.withAlpha(accent, Math.round(65.0f * hv * alpha)));
        }

        Render2DHelper.drawBlurredRoundedRect(context, x, y, sw, sh, rad, 0x380A0D16, alpha);
        Render2DHelper.drawRoundedRectFloat(context, x, y, sw, sh, rad, fadeColor(mixColor(FILL_IDLE, FILL_HOVER, hv), alpha));
        Render2DHelper.drawRoundedOutlineFloat(context, x, y, sw, sh, rad, 0.45f + 0.35f * hv,
                fadeColor(mixColor(OUTLINE_IDLE, ColorUtils.withAlpha(accent, 90), hv), alpha));

        float thumb = sh - 10.0f * k;
        float tx = x + (sh - thumb) * 0.5f;
        float ty = y + (sh - thumb) * 0.5f;
        MenuWallpapers.Wallpaper wp = MenuWallpapers.selected();
        if (wp != null) {
            if (wp.panorama()) {
                int tint = ColorUtils.withAlpha(accent, Math.round(230.0f * alpha));
                Render2DHelper.drawTextureFloat(context, TextureAssets.MENU_PANORAMA, tx, ty, thumb, thumb, tint);
            } else {
                Render2DHelper.drawCroppedTextureFloat(context, wp.preview(), tx, ty, thumb, thumb,
                        0.0f, 0.21875f, 1.0f, 0.5625f, ColorUtils.rgba(255, 255, 255, Math.round(255.0f * alpha)));
            }
        }

        float lblSize = 12.0f * k;
        float lw = OptWidthMenu("Gallery", lblSize);
        float lx = tx + thumb + (x + sw - tx - thumb - lw) * 0.5f - 1.0f * k;
        int lblCol = fadeColor(mixColor(LABEL_IDLE, 0xFFFFFFFF, hv), alpha);
        drawMenuText(context, "Gallery", lx, y + (sh - lblSize) * 0.5f, lblSize, lblCol, false);
    }

    private void drawGalleryOverlay(GuiGraphicsExtractor context, float mx, float my, float dt) {
        float open = clamp01(galleryAnim.value);
        int count = MenuWallpapers.size();
        if (open <= 0.004f || count == 0) return;

        float k = this.layoutK;
        float ease = easeOutCubic(open);
        int accent = ThemeManager.getInstance().getCurrentTheme().getPrimaryColor();

        Render2DHelper.drawGradientRect(context, 0, fixedH * 0.35f, fixedW, fixedH * 0.65f,
                0x00020409, ColorUtils.withAlpha(0xFF020409, Math.round(170.0f * open)));

        float pos = galleryPos.value;
        int centerIdx = wrapIndex(Math.round(pos));
        MenuWallpapers.Wallpaper focused = MenuWallpapers.get(centerIdx);
        if (focused != null) {
            float titleSize = 15.0f * k;
            float tw = OptWidthMenu(focused.name(), titleSize);
            float titleY = cardCenterY - CARD_H * k * 0.5f - 46.0f * k + (1.0f - ease) * 14.0f * k;
            int tCol = ColorUtils.rgba(255, 255, 255, Math.round(235.0f * open));
            drawMenuText(context, focused.name(), cardCenterX - tw * 0.5f, titleY, titleSize, tCol, true);
            if (centerIdx == MenuWallpapers.selectedIndex()) {
                float tagSize = 9.5f * k;
                float tagW = OptWidthMenu("Current", tagSize);
                int tagCol = ColorUtils.withAlpha(accent, Math.round(230.0f * open));
                drawMenuText(context, "Current", cardCenterX - tagW * 0.5f, titleY + titleSize + 5.0f * k, tagSize, tagCol, false);
            }
        }

        Integer[] order = new Integer[count];
        for (int i = 0; i < count; i++) order[i] = i;
        final float p = pos;
        Arrays.sort(order, (l, r) -> Float.compare(Math.abs(wrapOffset(r, p)), Math.abs(wrapOffset(l, p))));

        float spacing = CARD_SPACING * k;
        for (int idx : order) {
            float off = wrapOffset(idx, p);
            float dist = Math.abs(off);
            if (dist > CARD_VISIBLE) continue;
            float focus = 1.0f - clamp01(dist);

            float sc = (CARD_SIDE_SCALE + (1.0f - CARD_SIDE_SCALE) * focus) * (0.9f + 0.1f * ease);
            float cw = CARD_W * k * sc;
            float ch = CARD_H * k * sc;
            float offScaled = Math.signum(off) * (Math.min(dist, 1.0f) * spacing + Math.max(0.0f, dist - 1.0f) * spacing * 0.82f);
            float cx = cardCenterX + offScaled * ease;
            float cy = cardCenterY + (1.0f - ease) * 30.0f * k;
            float x = cx - cw * 0.5f;
            float y = cy - ch * 0.5f;
            float cAlpha = open * (0.45f + 0.55f * focus) * clamp01((CARD_VISIBLE - dist) * 1.6f);
            if (cAlpha <= 0.01f || x > fixedW || x + cw < 0.0f) continue;

            float radius = CARD_RADIUS * k * sc;
            Render2DHelper.drawGlow(context, x, y + 8.0f * k, cw, ch, 14.0f * k * (0.5f + 0.5f * focus),
                    ColorUtils.rgba(0, 0, 0, Math.round(140.0f * cAlpha)));

            MenuWallpapers.Wallpaper wp = MenuWallpapers.get(idx);
            if (wp != null && wp.panorama()) {
                Render2DHelper.drawGradientRoundedRectFloat(context, x, y, cw, ch, radius,
                        ColorUtils.rgba(26, 54, 93, Math.round(240.0f * cAlpha)),
                        ColorUtils.rgba(14, 23, 42, Math.round(245.0f * cAlpha)),
                        ColorUtils.rgba(14, 23, 42, Math.round(245.0f * cAlpha)),
                        ColorUtils.rgba(26, 54, 93, Math.round(240.0f * cAlpha)));
                float cubeSize = Math.min(cw, ch) * 0.42f;
                Render2DHelper.drawTextureFloat(context, TextureAssets.MENU_PANORAMA, cx - cubeSize * 0.5f, cy - cubeSize * 0.55f, cubeSize, cubeSize,
                        ColorUtils.rgba(255, 255, 255, Math.round(235.0f * cAlpha)));
                if (focus > 0.35f) {
                    float lblSz = 10.5f * k;
                    float lw = OptWidthMenu("3D Live Panorama", lblSz);
                    drawMenuText(context, "3D Live Panorama", cx - lw * 0.5f, cy + cubeSize * 0.32f, lblSz,
                            ColorUtils.rgba(226, 232, 240, Math.round(220.0f * focus * cAlpha)), false);
                }
            } else if (wp != null) {
                float cardAspect = CARD_W / CARD_H;
                float u = 0.0f, v = 0.0f, uw = 1.0f, uh = 1.0f;
                if (cardAspect > wp.aspect()) {
                    uh = wp.aspect() / cardAspect;
                    v = (1.0f - uh) * 0.5f;
                } else {
                    uw = cardAspect / wp.aspect();
                    u = (1.0f - uw) * 0.5f;
                }
                Render2DHelper.drawCroppedTextureFloat(context, wp.preview(), x, y, cw, ch, u, v, uw, uh,
                        ColorUtils.rgba(255, 255, 255, Math.round(255.0f * cAlpha)));
            }

            float dim = 1.0f - focus;
            if (dim > 0.01f) {
                Render2DHelper.drawRoundedRectFloat(context, x, y, cw, ch, radius, ColorUtils.rgba(6, 9, 16, Math.round(120.0f * dim * cAlpha)));
            }
            boolean applied = idx == MenuWallpapers.selectedIndex();
            int edge = applied
                    ? ColorUtils.withAlpha(accent, Math.round(230.0f * cAlpha))
                    : ColorUtils.rgba(255, 255, 255, Math.round(50.0f * focus * cAlpha));
            Render2DHelper.drawRoundedOutlineFloat(context, x, y, cw, ch, radius, applied ? 1.2f : 0.6f, edge);
        }

        // Back / Apply action buttons
        float w = ACTION_W * k;
        float h = ACTION_H * k;
        float y = actionBtnY + (1.0f - ease) * 24.0f * k;
        float radius = ACTION_RADIUS * k;
        float lblSize = 12.5f * k;

        boolean backHov = galleryOpen && isInside(mx, my, backBtnX, actionBtnY, w, h);
        boolean applyHov = galleryOpen && isInside(mx, my, applyBtnX, actionBtnY, w, h);
        backHover.update(backHov ? 1.0f : 0.0f, dt, 300.0f, 0.6f);
        applyHover.update(applyHov ? 1.0f : 0.0f, dt, 300.0f, 0.6f);
        float bh = clamp01(backHover.value);
        float ah = clamp01(applyHover.value);

        Render2DHelper.drawBlurredRoundedRect(context, backBtnX, y, w, h, radius, 0x380A0D16, open);
        Render2DHelper.drawRoundedRectFloat(context, backBtnX, y, w, h, radius, fadeColor(mixColor(FILL_IDLE, FILL_HOVER, bh), open));
        Render2DHelper.drawRoundedOutlineFloat(context, backBtnX, y, w, h, radius, 0.55f,
                fadeColor(mixColor(OUTLINE_IDLE, 0x44FFFFFF, bh), open));
        float bw = OptWidthMenu("Back", lblSize);
        drawMenuText(context, "Back", backBtnX + (w - bw) * 0.5f, y + (h - lblSize) * 0.5f, lblSize,
                fadeColor(mixColor(LABEL_IDLE, 0xFFFFFFFF, bh), open), false);

        boolean already = galleryIndex() == MenuWallpapers.selectedIndex();
        float enabled = already ? 0.45f : 1.0f;
        float ax = applyBtnX - 2.0f * k * ah;
        float aw = w + 4.0f * k * ah;
        float ay = y - 1.0f * k * ah;
        float ah2 = h + 2.0f * k * ah;
        if (!already) {
            Render2DHelper.drawGlow(context, ax, ay + 3.0f * k, aw, ah2, (12.0f + 5.0f * ah) * k,
                    ColorUtils.withAlpha(accent, Math.round(110.0f * open)));
        }
        int applyFill = ColorUtils.withAlpha(mixColor(accent, 0xFFFFFFFF, 0.12f * ah),
                Math.round((already ? 70.0f : 215.0f + 25.0f * ah) * open));
        Render2DHelper.drawRoundedRectFloat(context, ax, ay, aw, ah2, radius, applyFill);
        String applyText = already ? "Applied" : "Apply";
        float pw = OptWidthMenu(applyText, lblSize);
        drawMenuText(context, applyText, applyBtnX + (w - pw) * 0.5f, y + (h - lblSize) * 0.5f, lblSize,
                ColorUtils.rgba(255, 255, 255, Math.round(255.0f * enabled * open)), false);

        // Dots indicator
        float dotGap = 14.0f * k;
        float dot = 5.0f * k;
        float x0 = cardCenterX - ((count - 1) * dotGap + dot) * 0.5f;
        float dotsY = actionBtnY + h + 26.0f * k;
        for (int i = 0; i < count; i++) {
            float near = 1.0f - clamp01(Math.abs(wrapOffset(i, pos)));
            float sz = dot * (1.0f + 0.4f * near);
            int a = Math.round((90.0f + 150.0f * near) * open);
            Render2DHelper.drawRoundedRectFloat(context,
                    x0 + i * dotGap - (sz - dot) * 0.5f, dotsY - (sz - dot) * 0.5f,
                    sz, sz, sz * 0.5f, ColorUtils.rgba(255, 255, 255, a));
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        if (ClickGuiScreen.normalizeMouseButton(event.button()) != 0) return super.mouseClicked(event, doubled);
        float mx = toFixed(event.x());
        float my = toFixed(event.y());
        float k = this.layoutK;

        if (galleryOpen) {
            float w = ACTION_W * k;
            float h = ACTION_H * k;
            if (isInside(mx, my, backBtnX, actionBtnY, w, h)) {
                playClick();
                galleryOpen = false;
                return true;
            }
            if (isInside(mx, my, applyBtnX, actionBtnY, w, h)) {
                int targetIdx = galleryIndex();
                if (targetIdx != MenuWallpapers.selectedIndex()) {
                    playClick();
                    showWallpaper(targetIdx);
                    MenuWallpapers.select(targetIdx);
                }
                galleryOpen = false;
                return true;
            }
            float cardBand = CARD_H * k + 40.0f * k;
            if (my >= cardCenterY - cardBand * 0.5f && my <= cardCenterY + cardBand * 0.5f) {
                draggingGallery = true;
                didDrag = false;
                dragStartX = mx;
                dragLastX = mx;
                dragStartTarget = galleryTarget;
                dragVelocity = 0.0f;
                return true;
            }
            return true;
        }

        if (pendingAction >= 0) return true;

        if (isGalleryButtonInteractive() && isInside(mx, my, galleryBtnX, galleryBtnY, GALLERY_BTN_W * k, GALLERY_BTN_H * k)) {
            playClick();
            galleryOpen = true;
            galleryTarget = MenuWallpapers.selectedIndex();
            galleryPos.reset(galleryTarget);
            return true;
        }

        for (int i = 0; i < BUTTON_COUNT; i++) {
            if (isMainButtonInteractive(i) && isInside(mx, my, buttonX, buttonY[i], buttonW[i], buttonH)) {
                playClick();
                press[i].reset(1.0f);
                pendingAction = i;
                pendingTimer = 0.0f;
                return true;
            }
        }

        return super.mouseClicked(event, doubled);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (!galleryOpen || !draggingGallery) return super.mouseDragged(event, dragX, dragY);
        float mx = toFixed(event.x());
        float spacing = Math.max(40.0f, CARD_SPACING * layoutK);
        if (!didDrag && Math.abs(mx - dragStartX) > DRAG_THRESHOLD) didDrag = true;
        if (didDrag) {
            dragVelocity = (dragLastX - mx) / spacing;
            dragLastX = mx;
            galleryTarget = dragStartTarget - (mx - dragStartX) / spacing;
            showWallpaper(galleryIndex());
        }
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (!draggingGallery) return super.mouseReleased(event);
        draggingGallery = false;
        if (didDrag) {
            galleryTarget = Math.round(galleryTarget + clamp(dragVelocity * 6.0f, -1.5f, 1.5f));
            showWallpaper(galleryIndex());
        } else {
            int hit = findCardAt(toFixed(event.x()), toFixed(event.y()));
            if (hit >= 0) {
                playClick();
                galleryTarget = hit;
                showWallpaper(galleryIndex());
            }
        }
        didDrag = false;
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (!galleryOpen) return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        double amount = Math.abs(horizontalAmount) > Math.abs(verticalAmount) ? horizontalAmount : -verticalAmount;
        if (amount != 0.0) {
            galleryTarget = Math.round(galleryTarget) + (amount > 0.0 ? 1 : -1);
            showWallpaper(galleryIndex());
        }
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent input) {
        int key = input.key();
        if (galleryOpen) {
            switch (key) {
                case InputConstants.KEY_ESCAPE -> galleryOpen = false;
                case InputConstants.KEY_RETURN -> {
                    int idx = galleryIndex();
                    if (idx != MenuWallpapers.selectedIndex()) {
                        showWallpaper(idx);
                        MenuWallpapers.select(idx);
                    }
                    galleryOpen = false;
                }
                case InputConstants.KEY_LEFT, InputConstants.KEY_A -> {
                    galleryTarget = Math.round(galleryTarget) - 1;
                    showWallpaper(galleryIndex());
                }
                case InputConstants.KEY_RIGHT, InputConstants.KEY_D -> {
                    galleryTarget = Math.round(galleryTarget) + 1;
                    showWallpaper(galleryIndex());
                }
                default -> {
                }
            }
            return true;
        }
        if (key == InputConstants.KEY_ESCAPE) return true;
        return super.keyPressed(input);
    }

    private int findCardAt(float mx, float my) {
        int count = MenuWallpapers.size();
        float k = this.layoutK;
        float pos = galleryPos.value;
        float spacing = CARD_SPACING * k;
        for (int i = 0; i < count; i++) {
            float off = wrapOffset(i, pos);
            float dist = Math.abs(off);
            if (dist > CARD_VISIBLE) continue;
            float focus = 1.0f - clamp01(dist);
            float sc = CARD_SIDE_SCALE + (1.0f - CARD_SIDE_SCALE) * focus;
            float offScaled = Math.signum(off) * (Math.min(dist, 1.0f) * spacing + Math.max(0.0f, dist - 1.0f) * spacing * 0.82f);
            float w = CARD_W * k * sc;
            float h = CARD_H * k * sc;
            if (isInside(mx, my, cardCenterX + offScaled - w * 0.5f, cardCenterY - h * 0.5f, w, h)) {
                return Math.round(pos + off);
            }
        }
        return -1;
    }

    private void showWallpaper(int index) {
        if (index == wallpaperShown) return;
        wallpaperFrom = wallpaperShown;
        wallpaperShown = index;
        wallpaperFade = 0.0f;
    }

    private int galleryIndex() {
        return wrapIndex(Math.round(galleryTarget));
    }

    private static int wrapIndex(int i) {
        int count = MenuWallpapers.size();
        return count == 0 ? 0 : Math.floorMod(i, count);
    }

    private static float wrapOffset(int index, float pos) {
        int count = MenuWallpapers.size();
        if (count == 0) return 0.0f;
        float off = index - pos;
        return off - count * Math.round(off / count);
    }

    private void executeButton(int index) {
        if (minecraft == null || minecraft.gui == null) return;
        switch (index) {
            case 0 -> minecraft.gui.setScreen(new SelectWorldScreen(this));
            case 1 -> minecraft.gui.setScreen(new JoinMultiplayerScreen(this));
            case 2 -> minecraft.gui.setScreen(new OpticumeAltScreen(this));
            case 3 -> minecraft.gui.setScreen(new OptionsScreen(this, minecraft.options));
            case 4 -> minecraft.stop();
            default -> {
            }
        }
    }

    private void playClick() {
        if (minecraft != null && minecraft.getSoundManager() != null) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }
    }

    private static int mixColor(int c1, int c2, float t) {
        return ColorUtils.interpolateColor(c1, c2, t);
    }

    private static int fadeColor(int argb, float alpha) {
        int a = (argb >>> 24) & 0xFF;
        return ColorUtils.withAlpha(argb, Math.round(a * clamp01(alpha)));
    }

    private static boolean isInside(float mx, float my, float x, float y, float w, float h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }

    private static float clamp01(float v) {
        return clamp(v, 0.0f, 1.0f);
    }

    private static float easeOutCubic(float t) {
        float u = 1.0f - t;
        return 1.0f - u * u * u;
    }

    private static float easeOutExpo(float t) {
        return t >= 1.0f ? 1.0f : 1.0f - (float) Math.pow(2.0, -10.0 * t);
    }

    private static float easeOutBack(float t, float overshoot) {
        float u = t - 1.0f;
        return 1.0f + (overshoot + 1.0f) * u * u * u + overshoot * u * u;
    }

    private static float smoothstep(float t) {
        return t * t * (3.0f - 2.0f * t);
    }

    private static final class Spring {
        private static final float STEP = 1.0f / 240.0f;
        float value;
        float velocity;

        static Spring[] array(int n) {
            Spring[] springs = new Spring[n];
            for (int i = 0; i < n; i++) springs[i] = new Spring();
            return springs;
        }

        void reset(float v) {
            value = v;
            velocity = 0.0f;
        }

        void update(float target, float dt, float stiffness, float damping) {
            float c = 2.0f * (float) Math.sqrt(stiffness) * damping;
            while (dt > 0.0f) {
                float h = Math.min(STEP, dt);
                velocity += ((target - value) * stiffness - velocity * c) * h;
                value += velocity * h;
                dt -= h;
            }
        }
    }
}
