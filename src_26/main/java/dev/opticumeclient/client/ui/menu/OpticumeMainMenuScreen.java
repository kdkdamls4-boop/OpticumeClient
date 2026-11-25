package dev.opticumeclient.client.ui.menu;

import com.mojang.blaze3d.platform.InputConstants;
import dev.opticumeclient.OpticumeClient;
import dev.opticumeclient.client.gui.ClickGuiScreen;
import net.fabricmc.loader.api.FabricLoader;
import dev.opticumeclient.client.render.ColorUtils;
import dev.opticumeclient.client.render.KawaseBlurRenderer;
import dev.opticumeclient.client.render.Render2DHelper;
import dev.opticumeclient.client.render.TextureAssets;
import dev.opticumeclient.client.render.ThemeManager;
import dev.opticumeclient.client.render.font.OpticumeFonts;
import dev.opticumeclient.client.services.media.SmtcBridge;
import dev.opticumeclient.client.services.network.ProxyManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fStack;

import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

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
    private final Spring orbHover = new Spring();
    private final Spring stackExpand = new Spring();

    private final float[] targetBtnX = new float[BUTTON_COUNT];
    private final float[] targetBtnY = new float[BUTTON_COUNT];
    private final float[] targetBtnW = new float[BUTTON_COUNT];
    private float targetBtnH = 24.0f;
    private final float[] animBtnX = new float[BUTTON_COUNT];
    private final float[] animBtnY = new float[BUTTON_COUNT];
    private final float[] animBtnW = new float[BUTTON_COUNT];
    private float animBtnH = 24.0f;
    private float targetGalX, targetGalY, targetGalW, targetGalH;
    private float animGalX, animGalY, animGalW, animGalH;
    private boolean layoutAnimInit = false;

    private boolean stackExpanded = true;
    private boolean stylePopoverOpen = false;
    private float stylePopoverAnim = 0.0f;
    private float orbCx, orbCy, orbRadius;
    private float stylePillX, stylePillY, stylePillW, stylePillH;
    private float gearBtnX, gearBtnY, gearBtnSize;
    private float profilePillX, profilePillY, profilePillW, profilePillH;
    private float dockX, dockW, dockAltY, dockAltH, dockGalY, dockGalH, dockProxyY, dockProxyH;

    private static final String CLIENT_VER_LABEL = FabricLoader.getInstance()
            .getModContainer(OpticumeClient.MOD_ID)
            .map(c -> "Opticume " + c.getMetadata().getVersion().getFriendlyString())
            .orElse("Opticume · MC 26.3");

    private final MenuMusicWidget musicWidget = new MenuMusicWidget();
    private final OsuLazerMenuView lazerView = new OsuLazerMenuView(new OsuLazerMenuView.Host() {
        @Override
        public void runAction(int id) {
            executeLazerAction(id);
        }

        @Override
        public void playClick() {
            OpticumeMainMenuScreen.this.playClick();
        }
    });
    private volatile String worldsHint = "";
    private volatile String serversHint = "";

    private static final int MOTE_COUNT = 26;
    private final float[] moteX = new float[MOTE_COUNT];
    private final float[] moteY = new float[MOTE_COUNT];
    private final float[] moteR = new float[MOTE_COUNT];
    private final float[] moteVy = new float[MOTE_COUNT];
    private final float[] moteAlpha = new float[MOTE_COUNT];

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
        initMotes();
    }

    private void initMotes() {
        long seed = 0x5EEDCAFE1234L;
        for (int i = 0; i < MOTE_COUNT; i++) {
            seed = seed * 6364136223846793005L + 1442695040888963407L;
            moteX[i] = ((seed >>> 32) & 0xFFFF) / 65535.0f;
            seed = seed * 6364136223846793005L + 1442695040888963407L;
            moteY[i] = ((seed >>> 32) & 0xFFFF) / 65535.0f;
            moteR[i] = 1.2f + ((i * 7) % 11) * 0.24f;
            moteVy[i] = 0.015f + ((i * 5) % 9) * 0.004f;
            moteAlpha[i] = 0.09f + ((i * 3) % 7) * 0.025f;
        }
    }

    private void refreshStatsAsync() {
        Minecraft mc = this.minecraft != null ? this.minecraft : Minecraft.getInstance();
        if (mc == null) return;
        CompletableFuture.supplyAsync(() -> {
            try {
                if (mc.getLevelSource() != null) {
                    int w = mc.getLevelSource().findLevelCandidates().levels().size();
                    return w == 1 ? "1 world" : (w + " worlds");
                }
            } catch (Throwable ignored) {
            }
            return "";
        }).thenAccept(s -> this.worldsHint = s);

        CompletableFuture.supplyAsync(() -> {
            try {
                ServerList list = new ServerList(mc);
                list.load();
                int c = list.size();
                return c == 1 ? "1 server" : (c + " servers");
            } catch (Throwable ignored) {
            }
            return "";
        }).thenAccept(s -> this.serversHint = s);
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
        this.orbHover.reset(0.0f);
        this.stackExpanded = true;
        this.stackExpand.reset(1.0f);
        this.stylePopoverOpen = false;
        this.stylePopoverAnim = 0.0f;
        this.layoutAnimInit = false;
        this.lazerView.reset(MenuLayoutPreferences.isOsuStartExpanded());
        this.lastNanos = System.nanoTime();
        recomputeLayout(1.0f);
        refreshStatsAsync();
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

    private MenuLayoutPreferences.MenuLayoutMode getCurrentMode() {
        MenuWallpapers.Wallpaper wp = MenuWallpapers.get(wallpaperShown);
        return MenuLayoutPreferences.getActiveMode(wp != null ? wp.id() : MenuWallpapers.selectedId());
    }

    private void recomputeLayout(float dt) {
        Minecraft mc = Minecraft.getInstance();
        float winW = (mc != null && mc.getWindow() != null) ? Math.max(640.0f, mc.getWindow().getWidth()) : 1920.0f;
        float winH = (mc != null && mc.getWindow() != null) ? Math.max(360.0f, mc.getWindow().getHeight()) : 1080.0f;

        this.fixedW = winW / FIXED_GUI_SCALE;
        this.fixedH = winH / FIXED_GUI_SCALE;

        float scale = Math.min(winW / LAYOUT_W, winH / LAYOUT_H);
        this.layoutK = scale / FIXED_GUI_SCALE; // 0.5f at 1920x1080
        this.uiScale = scale;                   // 1.0f at 1920x1080

        float k = this.layoutK;
        float u = this.uiScale;
        MenuLayoutPreferences.MenuLayoutMode mode = getCurrentMode();

        this.buttonH = BUTTON_HEIGHT * k;
        float maxColW = 0.0f;
        for (int i = 0; i < BUTTON_COUNT; i++) {
            float labelW = OptWidthMenu(BUTTON_TITLES[i], LABEL_SIZE * k);
            maxColW = Math.max(maxColW, BUTTON_EXTRA_W * k + labelW);
        }
        this.columnW = maxColW * BUTTON_WIDTH_SCALE;
        this.buttonX = (this.fixedW - this.columnW) * 0.5f;

        float headH = WELCOME_OFFSET * u;
        float buttonsH = (BUTTON_COUNT - 1) * BUTTON_STEP * k + this.buttonH + (GALLERY_BTN_GAP + GALLERY_BTN_H) * k;
        float firstY = (this.fixedH - (headH + buttonsH)) * 0.5f + headH;

        for (int i = 0; i < BUTTON_COUNT; i++) {
            this.buttonY[i] = firstY + i * BUTTON_STEP * k;
            this.buttonW[i] = this.columnW;
        }
        this.galleryBtnX = this.buttonX + (this.columnW - GALLERY_BTN_W * k) * 0.5f;
        this.galleryBtnY = this.buttonY[BUTTON_COUNT - 1] + this.buttonH + GALLERY_BTN_GAP * k;

        if (mode == MenuLayoutPreferences.MenuLayoutMode.DESIRE_CENTER) {
            for (int i = 0; i < BUTTON_COUNT; i++) {
                targetBtnX[i] = this.buttonX;
                targetBtnY[i] = this.buttonY[i];
                targetBtnW[i] = this.columnW;
            }
            targetBtnH = this.buttonH;
            targetGalW = GALLERY_BTN_W * k;
            targetGalH = GALLERY_BTN_H * k;
            targetGalX = this.galleryBtnX;
            targetGalY = this.galleryBtnY;
        } else {
            float stackW = Math.max(144.0f * u, this.columnW * 1.12f);
            float stackH = 24.5f * u;
            float stackStep = 29.8f * u;
            float edgePad = 24.0f * u;
            targetGalW = 112.0f * u;
            targetGalH = 18.0f * u;
            float totalStackH = (BUTTON_COUNT - 1) * stackStep + stackH + 10.0f * u + targetGalH;
            float stackTopY = (this.fixedH - totalStackH) * 0.5f;
            float stackX = (mode == MenuLayoutPreferences.MenuLayoutMode.OSU_LEFT)
                    ? edgePad
                    : (this.fixedW - edgePad - stackW);
            for (int i = 0; i < BUTTON_COUNT; i++) {
                targetBtnX[i] = stackX;
                targetBtnY[i] = stackTopY + i * stackStep;
                targetBtnW[i] = stackW;
            }
            targetBtnH = stackH;
            targetGalX = (mode == MenuLayoutPreferences.MenuLayoutMode.OSU_LEFT)
                    ? stackX
                    : (stackX + stackW - targetGalW);
            targetGalY = stackTopY + BUTTON_COUNT * stackStep + 2.0f * u;
        }

        // Pulse Orb coordinates per layout mode
        this.orbRadius = 47.0f * u;
        if (mode == MenuLayoutPreferences.MenuLayoutMode.OSU_RIGHT) {
            this.orbCx = this.fixedW * 0.27f;
            this.orbCy = this.fixedH * 0.50f;
        } else if (mode == MenuLayoutPreferences.MenuLayoutMode.OSU_LEFT) {
            this.orbCx = this.fixedW * 0.73f;
            this.orbCy = this.fixedH * 0.50f;
        } else {
            this.orbCx = this.fixedW * 0.50f;
            this.orbCy = this.fixedH * 0.48f;
        }

        // Split Dock left panel coordinates
        this.dockW = 152.0f * u;
        this.dockX = 22.0f * u;
        this.dockAltH = 38.0f * u;
        this.dockGalH = 38.0f * u;
        this.dockProxyH = 30.0f * u;
        float dockTotalH = dockAltH + dockGalH + dockProxyH + 14.0f * u;
        this.dockAltY = (this.fixedH - dockTotalH) * 0.5f;
        this.dockGalY = this.dockAltY + dockAltH + 7.0f * u;
        this.dockProxyY = this.dockGalY + dockGalH + 7.0f * u;

        // Corner pills coordinates
        this.gearBtnSize = 18.0f * u;
        this.stylePillH = 18.0f * u;
        this.stylePillW = 92.0f * u;
        float margin = 12.0f * u;
        this.gearBtnX = this.fixedW - margin - this.gearBtnSize;
        this.gearBtnY = this.fixedH - margin - this.gearBtnSize;
        this.stylePillX = this.gearBtnX - 5.0f * u - this.stylePillW;
        this.stylePillY = this.gearBtnY;

        String playerName = (mc != null && mc.getUser() != null) ? mc.getUser().getName() : "Player";
        this.profilePillH = 20.0f * u;
        this.profilePillW = Math.min(160.0f * u, Math.max(76.0f * u, OptWidthMenu(playerName, 6.8f * u) + 34.0f * u));
        this.profilePillX = margin;
        this.profilePillY = this.fixedH - margin - this.profilePillH;

        // Smooth morph across layouts
        if (!layoutAnimInit) {
            layoutAnimInit = true;
            for (int i = 0; i < BUTTON_COUNT; i++) {
                animBtnX[i] = targetBtnX[i];
                animBtnY[i] = targetBtnY[i];
                animBtnW[i] = targetBtnW[i];
            }
            animBtnH = targetBtnH;
            animGalX = targetGalX;
            animGalY = targetGalY;
            animGalW = targetGalW;
            animGalH = targetGalH;
        } else {
            float lerpF = 1.0f - (float) Math.exp(-13.0f * dt);
            for (int i = 0; i < BUTTON_COUNT; i++) {
                animBtnX[i] += (targetBtnX[i] - animBtnX[i]) * lerpF;
                animBtnY[i] += (targetBtnY[i] - animBtnY[i]) * lerpF;
                animBtnW[i] += (targetBtnW[i] - animBtnW[i]) * lerpF;
            }
            animBtnH += (targetBtnH - animBtnH) * lerpF;
            animGalX += (targetGalX - animGalX) * lerpF;
            animGalY += (targetGalY - animGalY) * lerpF;
            animGalW += (targetGalW - animGalW) * lerpF;
            animGalH += (targetGalH - animGalH) * lerpF;
        }

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

        long now = System.nanoTime();
        float dt = Math.min(0.05f, Math.max(0.0001f, (now - lastNanos) / 1.0e9f));
        lastNanos = now;
        time += dt;
        recomputeLayout(dt);

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
        MenuLayoutPreferences.MenuLayoutMode activeMode = getCurrentMode();
        boolean isOsuLazer = activeMode == MenuLayoutPreferences.MenuLayoutMode.OSU_LAZER;
        boolean orbAvailable = !isOsuLazer
                && activeMode != MenuLayoutPreferences.MenuLayoutMode.DESIRE_CENTER
                && MenuLayoutPreferences.isPulseLogoEnabled();
        if (!orbAvailable) {
            stackExpanded = true;
        }
        stackExpand.update((!orbAvailable || stackExpanded) ? 1.0f : 0.0f, dt, 220.0f, 0.78f);
        stylePopoverAnim += ((stylePopoverOpen ? 1.0f : 0.0f) - stylePopoverAnim) * (1.0f - (float) Math.exp(-16.0f * dt));

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

        // Stratum 1: Capture and light Kawase-blur rendered backdrop right before foreground buttons
        context.nextStratum();
        context.blurBeforeThisStratum();
        KawaseBlurRenderer.requestMenuBlur(1, 0.85f);

        // Foreground rendered in Desire's exact FIXED_GUI_SCALE = 2.0 coordinate space
        float fixToGui = FIXED_GUI_SCALE / getWindowGuiScale();
        Matrix3x2fStack pose = context.pose();
        pose.pushMatrix();
        pose.scale(fixToGui, fixToGui);

        MenuLayoutPreferences.MenuLayoutMode mode = getCurrentMode();
        boolean musicPlaying = SmtcBridge.getInstance().getSnapshot().playing();
        float beat = musicPlaying
                ? (float) Math.pow(Math.max(0.0, Math.sin(time * 6.7)), 6.0)
                : (0.5f + 0.5f * (float) Math.sin(time * 1.6)) * 0.35f;

        if (menuFade > 0.004f) {
            if (mode == MenuLayoutPreferences.MenuLayoutMode.OSU_LAZER) {
                drawFloatingMotes(context, dt, beat, menuFade);
                lazerView.render(context, fixedW, fixedH, uiScale, mxFixed, myFixed, dt, time, beat, menuFade);
            } else {
                if (mode != MenuLayoutPreferences.MenuLayoutMode.DESIRE_CENTER) {
                    drawFloatingMotes(context, dt, beat, menuFade);
                }
                if (mode == MenuLayoutPreferences.MenuLayoutMode.DESIRE_CENTER) {
                    drawWelcome(context);
                } else if (MenuLayoutPreferences.isPulseLogoEnabled()) {
                    drawPulseOrb(context, mxFixed, myFixed, dt, beat, menuFade);
                } else {
                    drawSideScriptWordmark(context, mode, menuFade);
                }
                if (mode == MenuLayoutPreferences.MenuLayoutMode.SPLIT) {
                    drawSplitLeftDock(context, mxFixed, myFixed, menuFade);
                }
                drawButtons(context, mxFixed, myFixed, dt, mode);
                if (mode != MenuLayoutPreferences.MenuLayoutMode.SPLIT) {
                    drawGalleryButton(context, mxFixed, myFixed, dt, mode);
                }
                if (mode != MenuLayoutPreferences.MenuLayoutMode.DESIRE_CENTER) {
                    if (MenuLayoutPreferences.isMusicWidgetEnabled()) {
                        float mX = (mode == MenuLayoutPreferences.MenuLayoutMode.OSU_LEFT)
                                ? (fixedW - 12.0f * uiScale - 190.0f * uiScale)
                                : (12.0f * uiScale);
                        musicWidget.updateAndRender(context, mX, 12.0f * uiScale, uiScale, mxFixed, myFixed, dt, menuFade);
                    }
                    if (mode != MenuLayoutPreferences.MenuLayoutMode.SPLIT) {
                        drawProfileCornerPill(context, mxFixed, myFixed, menuFade);
                    }
                }
            }
            drawStyleCornerControl(context, mxFixed, myFixed, mode, menuFade);
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
        return !galleryOpen && pendingAction < 0 && stackExpand.value > 0.45f && getMainButtonEnter(idx) > 0.4f;
    }

    private float getGalleryButtonEnter() {
        return clamp01((time - GALLERY_BTN_START) / BUTTON_DUR);
    }

    private boolean isGalleryButtonInteractive() {
        return !galleryOpen && pendingAction < 0 && stackExpand.value > 0.45f
                && MenuWallpapers.size() > 0 && getGalleryButtonEnter() > 0.4f;
    }

    private float getActiveSlant(MenuLayoutPreferences.MenuLayoutMode mode) {
        if (mode == MenuLayoutPreferences.MenuLayoutMode.DESIRE_CENTER || !MenuLayoutPreferences.isSlantEnabled()) {
            return 0.0f;
        }
        return mode == MenuLayoutPreferences.MenuLayoutMode.OSU_LEFT ? 0.16f : -0.16f;
    }

    private boolean isInsideSlanted(float mx, float my, float x, float y, float w, float h, float slant) {
        float cy = y + h * 0.5f;
        float adjX = mx - slant * (my - cy);
        return adjX >= x && adjX <= x + w && my >= y && my <= y + h;
    }

    private void drawFloatingMotes(GuiGraphicsExtractor context, float dt, float beat, float alpha) {
        float speedBoost = 1.0f + beat * 1.4f;
        for (int i = 0; i < MOTE_COUNT; i++) {
            moteY[i] -= moteVy[i] * dt * speedBoost;
            if (moteY[i] < -0.04f) {
                moteY[i] = 1.04f;
                moteX[i] = (moteX[i] + 0.37f) % 1.0f;
            }
            float px = moteX[i] * fixedW;
            float py = moteY[i] * fixedH;
            float r = moteR[i] * uiScale * (1.0f + beat * 0.2f);
            int a = Math.round(255.0f * (moteAlpha[i] + beat * 0.06f) * alpha);
            if (a > 2) {
                Render2DHelper.drawRoundedRectFloat(context, px - r, py - r, r * 2.0f, r * 2.0f, r, ColorUtils.rgba(255, 255, 255, a));
            }
        }
    }

    private void drawPulseOrb(GuiGraphicsExtractor context, float mx, float my, float dt, float beat, float alpha) {
        boolean hov = !galleryOpen && pendingAction < 0
                && Math.hypot(mx - orbCx, my - orbCy) <= orbRadius * 1.08f;
        orbHover.update(hov ? 1.0f : 0.0f, dt, 240.0f, 0.65f);
        float hv = clamp01(orbHover.value);

        int accent1 = ThemeManager.getInstance().getCurrentTheme().getPrimaryColor();
        int accent2 = ThemeManager.getInstance().getCurrentTheme().getSecondaryColor();

        float pulseScale = 1.0f + beat * 0.055f + hv * 0.045f;
        float r = orbRadius * pulseScale;
        float x = orbCx - r;
        float y = orbCy - r;
        float d = r * 2.0f;

        Render2DHelper.drawGlow(context, x, y, d, d, 18.0f * uiScale,
                ColorUtils.withAlpha(accent1, Math.round((55.0f + beat * 45.0f + hv * 55.0f) * alpha)));

        // Expanding beat ring
        float ringExtra = (beat * 7.5f + hv * 3.0f) * uiScale;
        Render2DHelper.drawRoundedOutlineFloat(context, x - ringExtra, y - ringExtra, d + ringExtra * 2.0f, d + ringExtra * 2.0f,
                r + ringExtra, 0.7f, ColorUtils.withAlpha(accent2, Math.round((38.0f + beat * 85.0f + hv * 60.0f) * alpha)));

        Render2DHelper.drawBlurredRoundedRect(context, x, y, d, d, r, 0x140A0E18, alpha);
        Render2DHelper.drawRoundedRectFloat(context, x, y, d, d, r,
                ColorUtils.withAlpha(0xFFFFFFFF, Math.round((16.0f + hv * 12.0f) * alpha)));
        Render2DHelper.drawGradientRoundedRectFloat(context, x, y, d, d, r,
                ColorUtils.withAlpha(accent1, Math.round((26.0f + hv * 24.0f) * alpha)),
                ColorUtils.withAlpha(accent2, Math.round((18.0f + hv * 18.0f) * alpha)),
                ColorUtils.withAlpha(accent2, Math.round((12.0f + hv * 14.0f) * alpha)),
                ColorUtils.withAlpha(accent1, Math.round((20.0f + hv * 20.0f) * alpha)));

        int rimCol = ColorUtils.interpolateColor(
                ColorUtils.withAlpha(0xFFFFFFFF, Math.round(78.0f * alpha)),
                ColorUtils.withAlpha(accent1, Math.round(215.0f * alpha)),
                Math.max(hv, beat * 0.5f));
        Render2DHelper.drawRoundedOutlineFloat(context, x, y, d, d, r, 0.85f + 0.4f * hv, rimCol);

        // Script "Opticume" centered inside orb
        float scriptSz = 24.5f * uiScale * pulseScale;
        float tw = OpticumeButtonStyle.getScriptTextWidth("Opticume", scriptSz);
        OpticumeButtonStyle.drawScriptTitle(context, "Opticume", orbCx - tw * 0.5f, orbCy - scriptSz * 0.62f,
                scriptSz, alpha, time, true);

        String sub = stackExpanded ? "26.3 · Vulkan" : "Click to Open";
        OpticumeButtonStyle.drawUiCenteredText(context, sub, orbCx, orbCy + 11.0f * uiScale * pulseScale, 5.6f * uiScale,
                ColorUtils.rgba(235, 240, 250, Math.round((185.0f + hv * 60.0f) * alpha)), true);
    }

    private void drawSideScriptWordmark(GuiGraphicsExtractor context, MenuLayoutPreferences.MenuLayoutMode mode, float alpha) {
        float wx = (mode == MenuLayoutPreferences.MenuLayoutMode.OSU_LEFT)
                ? (fixedW * 0.72f)
                : (fixedW * 0.27f);
        float wy = fixedH * 0.44f;
        float sz = 36.0f * uiScale;
        float tw = OpticumeButtonStyle.getScriptTextWidth("Opticume", sz);
        OpticumeButtonStyle.drawScriptTitle(context, "Opticume", wx - tw * 0.5f, wy, sz, alpha, time, true);
    }

    private float[] computeButtonRect(int i, MenuLayoutPreferences.MenuLayoutMode mode, float exp, float enter) {
        float u = this.uiScale;
        boolean isSide = mode != MenuLayoutPreferences.MenuLayoutMode.DESIRE_CENTER;
        float slideDir = (mode == MenuLayoutPreferences.MenuLayoutMode.OSU_LEFT) ? 1.0f : -1.0f;
        float baseX = animBtnX[i];
        float y = animBtnY[i];
        float baseW = animBtnW[i];
        float h = animBtnH;

        float collapseShift = isSide ? (-slideDir * (1.0f - exp) * 55.0f * u) : ((1.0f - exp) * 20.0f * u);
        float enterShift = isSide ? (-slideDir * (1.0f - enter) * 28.0f * u) : 0.0f;
        float hvPrev = clamp01(hover[i].value);
        float curSlide = isSide ? (slideDir * 13.0f * u * hvPrev) : 0.0f;
        float curW = baseW + (isSide ? (7.0f * u * hvPrev) : 0.0f);
        float curX = baseX + collapseShift + enterShift
                + (mode == MenuLayoutPreferences.MenuLayoutMode.OSU_LEFT ? 0.0f : (baseW - curW))
                + curSlide;
        return new float[]{curX, y, curW, h};
    }

    private void drawButtons(GuiGraphicsExtractor context, float mx, float my, float dt, MenuLayoutPreferences.MenuLayoutMode mode) {
        float exp = clamp01(stackExpand.value);
        if (exp <= 0.02f) return;

        float k = this.layoutK;
        float u = this.uiScale;
        int accent = ThemeManager.getInstance().getCurrentTheme().getPrimaryColor();
        Matrix3x2fStack pose = context.pose();
        boolean isSide = mode != MenuLayoutPreferences.MenuLayoutMode.DESIRE_CENTER;
        float slant = getActiveSlant(mode);

        for (int i = 0; i < BUTTON_COUNT; i++) {
            float p = getMainButtonEnter(i);
            if (p <= 0.0f) continue;
            float alpha = easeOutCubic(clamp01(p * 1.7f)) * menuFade * exp;
            if (alpha <= 0.004f) continue;

            float enter = easeOutBack(p, 1.5f);
            float[] rect = computeButtonRect(i, mode, exp, enter);
            float curX = rect[0];
            float y = rect[1];
            float curW = rect[2];
            float h = rect[3];

            boolean hovered = isMainButtonInteractive(i) && isInsideSlanted(mx, my, curX, y, curW, h, slant);
            if (hovered && !wasHovered[i]) sheen[i] = 0.0f;
            wasHovered[i] = hovered;
            sheen[i] = Math.min(1.0f, sheen[i] + dt / SHEEN_DUR);

            hover[i].update(hovered ? 1.0f : 0.0f, dt, 260.0f, 0.62f);
            press[i].update(0.0f, dt, 420.0f, 0.5f);
            float hv = clamp01(hover[i].value);

            float scale = (0.9f + 0.1f * enter) * (isSide ? 1.0f : (1.0f + 0.035f * hover[i].value)) * (1.0f - 0.05f * press[i].value);
            float cx = curX + curW * 0.5f;
            float cy = y + h * 0.5f + (isSide ? 0.0f : (1.0f - enter) * 26.0f * k);
            float radius = isSide ? (8.5f * u) : (BUTTON_RADIUS * k);

            if (hv > 0.01f) {
                Render2DHelper.drawGlow(context, curX, y + 1.5f * u * hv, curW, h, 10.0f * u,
                        ColorUtils.withAlpha(accent, Math.round(58.0f * hv * alpha)));
            }

            // Pass 1: sheared backdrop, fill, border, accent bar, sheen
            pose.pushMatrix();
            pose.translate(cx, cy);
            pose.scale(scale, scale);
            if (Math.abs(slant) > 0.001f) {
                pose.mul(new Matrix3x2f(1.0f, 0.0f, slant, 1.0f, 0.0f, 0.0f));
            }
            pose.translate(-(curX + curW * 0.5f), -(y + h * 0.5f));

            Render2DHelper.drawBlurredRoundedRect(context, curX, y, curW, h, radius, isSide ? 0x120A0E18 : 0x200A0D16, alpha);
            int fill = fadeColor(mixColor(FILL_IDLE, FILL_HOVER, hv), alpha);
            Render2DHelper.drawRoundedRectFloat(context, curX, y, curW, h, radius, fill);

            int borderCol = fadeColor(mixColor(OUTLINE_IDLE, ColorUtils.withAlpha(accent, 130), hv), alpha);
            Render2DHelper.drawRoundedOutlineFloat(context, curX, y, curW, h, radius, 0.5f + 0.35f * hv, borderCol);

            if (isSide && hv > 0.02f) {
                float barH = Math.max(4.0f, (h - 8.0f * u) * hv);
                float barX = (mode == MenuLayoutPreferences.MenuLayoutMode.OSU_LEFT) ? (curX + curW - 3.5f * u) : (curX + 2.0f * u);
                Render2DHelper.drawRoundedRectFloat(context, barX, y + (h - barH) * 0.5f, 1.8f * u, barH, 1.0f * u,
                        ColorUtils.withAlpha(accent, Math.round(235.0f * hv * alpha)));
            }

            if (sheen[i] < 1.0f) {
                OpticumeButtonStyle.drawSheen(context, curX, y, curW, h, sheen[i], alpha);
            }
            pose.popMatrix();

            // Pass 2: un-sheared centered transform around (cx, cy) so icons & labels have zero -slant*cy drift
            pose.pushMatrix();
            pose.translate(cx, cy);
            pose.scale(scale, scale);
            pose.translate(-(curX + curW * 0.5f), -(y + h * 0.5f));

            float iconSize = ICON_SIZE * k;
            float iconX = curX + (isSide ? 11.0f * u : ICON_X * k) + 3.0f * u * hv;
            float iconY = y + (h - iconSize) * 0.5f;
            int iconCol = fadeColor(mixColor(ICON_IDLE, ColorUtils.withAlpha(accent, 255), hv), alpha);
            Render2DHelper.drawTextureFloat(context, BUTTON_ICONS[i], iconX, iconY, iconSize, iconSize, iconCol);

            float lblSize = LABEL_SIZE * k;
            int lblCol = fadeColor(mixColor(LABEL_IDLE, 0xFFFFFFFF, hv), alpha);
            if (isSide) {
                float lblX = iconX + iconSize + 8.0f * u;
                float lblY = y + (h - lblSize) * 0.5f;
                drawMenuText(context, BUTTON_TITLES[i], lblX, lblY, lblSize, lblCol, false);

                String hint = i == 0 ? worldsHint : (i == 1 ? serversHint : "");
                float rightPad = 10.0f * u;
                if (hv > 0.08f) {
                    OpticumeButtonStyle.drawUiText(context, ">", curX + curW - rightPad - 3.0f * u * (1.0f - hv),
                            y + (h - 7.0f * u) * 0.5f, 7.2f * u, ColorUtils.withAlpha(accent, Math.round(235.0f * hv * alpha)), false);
                    rightPad += 9.0f * u;
                }
                if (hint != null && !hint.isEmpty()) {
                    float hw = OpticumeButtonStyle.getUiTextWidth(hint, 5.6f * u);
                    OpticumeButtonStyle.drawUiText(context, hint, curX + curW - rightPad - hw,
                            y + (h - 5.6f * u) * 0.5f, 5.6f * u, ColorUtils.rgba(215, 224, 240, Math.round((130.0f + 90.0f * hv) * alpha)), false);
                }
            } else {
                float lblW = OptWidthMenu(BUTTON_TITLES[i], lblSize);
                float lblX = curX + (curW - lblW) * 0.5f;
                float lblY = y + (h - lblSize) * 0.5f;
                drawMenuText(context, BUTTON_TITLES[i], lblX, lblY, lblSize, lblCol, false);
            }

            pose.popMatrix();
        }
    }

    private void drawGalleryButton(GuiGraphicsExtractor context, float mx, float my, float dt, MenuLayoutPreferences.MenuLayoutMode mode) {
        if (MenuWallpapers.size() <= 0) return;
        float exp = clamp01(stackExpand.value);
        if (exp <= 0.02f) return;

        float k = this.layoutK;
        float u = this.uiScale;
        float p = getGalleryButtonEnter();
        if (p <= 0.0f) return;
        float alpha = easeOutCubic(clamp01(p * 1.7f)) * menuFade * exp;
        if (alpha <= 0.004f) return;

        float enter = easeOutBack(p, 1.4f);
        float w = animGalW;
        float h = animGalH;
        float slideDir = (mode == MenuLayoutPreferences.MenuLayoutMode.OSU_LEFT) ? 1.0f : -1.0f;
        float gx = animGalX + (mode == MenuLayoutPreferences.MenuLayoutMode.DESIRE_CENTER ? 0.0f : (-slideDir * (1.0f - exp) * 55.0f * u));
        float gy = animGalY;

        boolean hovered = isGalleryButtonInteractive() && isInside(mx, my, gx, gy, w, h);
        galleryBtnHover.update(hovered ? 1.0f : 0.0f, dt, 260.0f, 0.62f);
        float hv = clamp01(galleryBtnHover.value);

        float sc = (0.9f + 0.1f * enter) * (1.0f + 0.04f * galleryBtnHover.value);
        float cx = gx + w * 0.5f;
        float cy = gy + h * 0.5f + (1.0f - enter) * 20.0f * k;
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

        Render2DHelper.drawBlurredRoundedRect(context, x, y, sw, sh, rad, 0x160A0E18, alpha);
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
                Render2DHelper.drawRoundedTextureFloat(context, wp.preview(), tx, ty, thumb, thumb,
                        Math.min(4.0f * k, thumb * 0.25f),
                        ColorUtils.rgba(255, 255, 255, Math.round(255.0f * alpha)));
            }
        }

        float lblSize = 12.0f * k;
        String label = mode == MenuLayoutPreferences.MenuLayoutMode.DESIRE_CENTER ? "Gallery" : ("Gallery · " + MenuWallpapers.size());
        float lw = OptWidthMenu(label, lblSize);
        float lx = tx + thumb + (x + sw - tx - thumb - lw) * 0.5f - 1.0f * k;
        int lblCol = fadeColor(mixColor(LABEL_IDLE, 0xFFFFFFFF, hv), alpha);
        drawMenuText(context, label, lx, y + (sh - lblSize) * 0.5f, lblSize, lblCol, false);
    }

    private void drawSplitLeftDock(GuiGraphicsExtractor context, float mx, float my, float alpha) {
        float u = this.uiScale;
        int accent = ThemeManager.getInstance().getCurrentTheme().getPrimaryColor();
        String playerName = (minecraft != null && minecraft.getUser() != null) ? minecraft.getUser().getName() : "Player";
        UUID uuid = UUIDUtil.createOfflinePlayerUUID(playerName);

        // 1. Account Card
        boolean altHov = !galleryOpen && isInside(mx, my, dockX, dockAltY, dockW, dockAltH);
        drawDockCardShell(context, dockX, dockAltY, dockW, dockAltH, 9.0f * u, altHov, accent, alpha);
        int headSz = Math.max(12, Math.round(22.0f * u));
        Render2DHelper.drawPlayerHeadByUuid(context, uuid, Math.round(dockX + 8.0f * u), Math.round(dockAltY + (dockAltH - headSz) * 0.5f), headSz, alpha);
        OpticumeButtonStyle.drawUiText(context, "ACTIVE ACCOUNT", dockX + 12.0f * u + headSz, dockAltY + 8.0f * u, 5.0f * u,
                ColorUtils.withAlpha(accent, Math.round(230.0f * alpha)), false);
        OpticumeButtonStyle.drawUiText(context, playerName, dockX + 12.0f * u + headSz, dockAltY + 18.0f * u, 7.2f * u,
                ColorUtils.rgba(255, 255, 255, Math.round(248.0f * alpha)), true);

        // 2. Gallery Card
        boolean galHov = !galleryOpen && isInside(mx, my, dockX, dockGalY, dockW, dockGalH);
        drawDockCardShell(context, dockX, dockGalY, dockW, dockGalH, 9.0f * u, galHov, accent, alpha);
        MenuWallpapers.Wallpaper wp = MenuWallpapers.selected();
        float thumbSz = 22.0f * u;
        float tx = dockX + 8.0f * u;
        float ty = dockGalY + (dockGalH - thumbSz) * 0.5f;
        if (wp != null && !wp.panorama()) {
            Render2DHelper.drawRoundedTextureFloat(context, wp.preview(), tx, ty, thumbSz, thumbSz, 5.0f * u,
                    ColorUtils.rgba(255, 255, 255, Math.round(255.0f * alpha)));
        } else {
            Render2DHelper.drawTextureFloat(context, TextureAssets.MENU_PANORAMA, tx, ty, thumbSz, thumbSz,
                    ColorUtils.withAlpha(accent, Math.round(235.0f * alpha)));
        }
        String wpName = wp != null ? wp.name() : "Panorama";
        OpticumeButtonStyle.drawUiText(context, "GALLERY · " + MenuWallpapers.size(), tx + thumbSz + 7.0f * u, dockGalY + 8.0f * u, 5.0f * u,
                ColorUtils.rgba(215, 224, 240, Math.round(195.0f * alpha)), false);
        OpticumeButtonStyle.drawUiText(context, wpName, tx + thumbSz + 7.0f * u, dockGalY + 18.0f * u, 7.2f * u,
                ColorUtils.rgba(255, 255, 255, Math.round(248.0f * alpha)), true);

        // 3. Proxy & Version Card
        boolean pxHov = !galleryOpen && isInside(mx, my, dockX, dockProxyY, dockW, dockProxyH);
        drawDockCardShell(context, dockX, dockProxyY, dockW, dockProxyH, 8.5f * u, pxHov, accent, alpha);
        boolean proxyOn = ProxyManager.getInstance().isEnabled();
        OpticumeButtonStyle.drawUiText(context, proxyOn ? "Proxy: ON" : "Proxy: OFF", dockX + 10.0f * u, dockProxyY + 7.5f * u, 6.6f * u,
                ColorUtils.rgba(proxyOn ? 134 : 235, proxyOn ? 239 : 240, proxyOn ? 172 : 250, Math.round(240.0f * alpha)), true);
        OpticumeButtonStyle.drawUiText(context, CLIENT_VER_LABEL, dockX + 10.0f * u, dockProxyY + 17.5f * u, 5.3f * u,
                ColorUtils.rgba(205, 215, 232, Math.round(180.0f * alpha)), false);
    }

    private static void drawDockCardShell(GuiGraphicsExtractor context, float x, float y, float w, float h, float rad, boolean hov, int accent, float alpha) {
        Render2DHelper.drawBlurredRoundedRect(context, x, y, w, h, rad, 0x120A0E18, alpha);
        Render2DHelper.drawRoundedRectFloat(context, x, y, w, h, rad,
                ColorUtils.withAlpha(0xFFFFFFFF, Math.round((hov ? 28.0f : 15.0f) * alpha)));
        Render2DHelper.drawRoundedOutlineFloat(context, x, y, w, h, rad, hov ? 0.8f : 0.5f,
                hov ? ColorUtils.withAlpha(accent, Math.round(150.0f * alpha)) : ColorUtils.withAlpha(0xFFFFFFFF, Math.round(28.0f * alpha)));
    }

    private void drawProfileCornerPill(GuiGraphicsExtractor context, float mx, float my, float alpha) {
        float u = this.uiScale;
        int accent = ThemeManager.getInstance().getCurrentTheme().getPrimaryColor();
        boolean hov = !galleryOpen && isInside(mx, my, profilePillX, profilePillY, profilePillW, profilePillH);
        float rad = profilePillH * 0.5f;
        Render2DHelper.drawBlurredRoundedRect(context, profilePillX, profilePillY, profilePillW, profilePillH, rad, 0x120A0E18, alpha);
        Render2DHelper.drawRoundedRectFloat(context, profilePillX, profilePillY, profilePillW, profilePillH, rad,
                ColorUtils.withAlpha(0xFFFFFFFF, Math.round((hov ? 30.0f : 16.0f) * alpha)));
        Render2DHelper.drawRoundedOutlineFloat(context, profilePillX, profilePillY, profilePillW, profilePillH, rad, 0.55f,
                hov ? ColorUtils.withAlpha(accent, Math.round(160.0f * alpha)) : ColorUtils.withAlpha(0xFFFFFFFF, Math.round(32.0f * alpha)));

        String playerName = (minecraft != null && minecraft.getUser() != null) ? minecraft.getUser().getName() : "Player";
        UUID uuid = UUIDUtil.createOfflinePlayerUUID(playerName);
        int headSz = Math.max(10, Math.round(13.0f * u));
        Render2DHelper.drawPlayerHeadByUuid(context, uuid,
                Math.round(profilePillX + 4.5f * u), Math.round(profilePillY + (profilePillH - headSz) * 0.5f), headSz, alpha);
        OpticumeButtonStyle.drawUiText(context, playerName, profilePillX + 8.5f * u + headSz,
                profilePillY + (profilePillH - 6.8f * u) * 0.5f, 6.8f * u, ColorUtils.rgba(255, 255, 255, Math.round(240.0f * alpha)), true);
    }

    private void drawStyleCornerControl(GuiGraphicsExtractor context, float mx, float my, MenuLayoutPreferences.MenuLayoutMode mode, float alpha) {
        float u = this.uiScale;
        int accent = ThemeManager.getInstance().getCurrentTheme().getPrimaryColor();
        boolean pillHov = !galleryOpen && isInside(mx, my, stylePillX, stylePillY, stylePillW, stylePillH);
        boolean gearHov = !galleryOpen && isInside(mx, my, gearBtnX, gearBtnY, gearBtnSize, gearBtnSize);

        float rad = stylePillH * 0.5f;
        Render2DHelper.drawBlurredRoundedRect(context, stylePillX, stylePillY, stylePillW, stylePillH, rad, 0x120A0E18, alpha);
        Render2DHelper.drawRoundedRectFloat(context, stylePillX, stylePillY, stylePillW, stylePillH, rad,
                ColorUtils.withAlpha(0xFFFFFFFF, Math.round((pillHov ? 30.0f : 16.0f) * alpha)));
        Render2DHelper.drawRoundedOutlineFloat(context, stylePillX, stylePillY, stylePillW, stylePillH, rad, 0.55f,
                pillHov ? ColorUtils.withAlpha(accent, Math.round(160.0f * alpha)) : ColorUtils.withAlpha(0xFFFFFFFF, Math.round(32.0f * alpha)));
        OpticumeButtonStyle.drawUiCenteredText(context, "Style: " + mode.shortLabel, stylePillX + stylePillW * 0.5f,
                stylePillY + (stylePillH - 6.4f * u) * 0.5f, 6.4f * u, ColorUtils.rgba(245, 248, 255, Math.round(235.0f * alpha)), true);

        float gRad = gearBtnSize * 0.5f;
        Render2DHelper.drawBlurredRoundedRect(context, gearBtnX, gearBtnY, gearBtnSize, gearBtnSize, gRad, 0x120A0E18, alpha);
        Render2DHelper.drawRoundedRectFloat(context, gearBtnX, gearBtnY, gearBtnSize, gearBtnSize, gRad,
                stylePopoverOpen ? ColorUtils.withAlpha(accent, Math.round(85.0f * alpha))
                        : ColorUtils.withAlpha(0xFFFFFFFF, Math.round((gearHov ? 30.0f : 16.0f) * alpha)));
        Render2DHelper.drawRoundedOutlineFloat(context, gearBtnX, gearBtnY, gearBtnSize, gearBtnSize, gRad, 0.55f,
                (stylePopoverOpen || gearHov) ? ColorUtils.withAlpha(accent, Math.round(170.0f * alpha)) : ColorUtils.withAlpha(0xFFFFFFFF, Math.round(32.0f * alpha)));
        // Clean 3-bar hamburger / tune lines inside the circular settings pill
        int iconCol = ColorUtils.rgba(255, 255, 255, Math.round(240.0f * alpha));
        float barW = 7.4f * u;
        float barH = Math.max(0.8f, 1.0f * u);
        float bx = gearBtnX + (gearBtnSize - barW) * 0.5f;
        float midY = gearBtnY + (gearBtnSize - barH) * 0.5f;
        Render2DHelper.drawRoundedRectFloat(context, bx, midY - 2.6f * u, barW, barH, barH * 0.5f, iconCol);
        Render2DHelper.drawRoundedRectFloat(context, bx, midY, barW, barH, barH * 0.5f, iconCol);
        Render2DHelper.drawRoundedRectFloat(context, bx, midY + 2.6f * u, barW, barH, barH * 0.5f, iconCol);

        float popA = clamp01(stylePopoverAnim) * alpha;
        if (popA <= 0.02f) return;

        float popW = 142.0f * u;
        float rowH = 16.5f * u;
        float popH = rowH * 5.0f + 12.0f * u;
        float popX = (gearBtnX + gearBtnSize) - popW;
        float popY = gearBtnY - 6.0f * u - popH + (1.0f - stylePopoverAnim) * 6.0f * u;

        Render2DHelper.drawBlurredRoundedRect(context, popX, popY, popW, popH, 9.0f * u, 0x160A0E18, popA);
        Render2DHelper.drawRoundedRectFloat(context, popX, popY, popW, popH, 9.0f * u, ColorUtils.withAlpha(0xFFFFFFFF, Math.round(18.0f * popA)));
        Render2DHelper.drawRoundedOutlineFloat(context, popX, popY, popW, popH, 9.0f * u, 0.55f, ColorUtils.withAlpha(0xFFFFFFFF, Math.round(42.0f * popA)));

        String[] names = {"Slant Buttons", "Pulse Logo", "Music SMTC", "Per-Wallpaper", "osu! Start Open"};
        boolean[] vals = {
                MenuLayoutPreferences.isSlantEnabled(),
                MenuLayoutPreferences.isPulseLogoEnabled(),
                MenuLayoutPreferences.isMusicWidgetEnabled(),
                MenuLayoutPreferences.isPerWallpaperMode(),
                MenuLayoutPreferences.isOsuStartExpanded()
        };
        for (int r = 0; r < 5; r++) {
            float ry = popY + 6.0f * u + r * rowH;
            boolean rHov = isInside(mx, my, popX + 4.0f * u, ry, popW - 8.0f * u, rowH);
            if (rHov) {
                Render2DHelper.drawRoundedRectFloat(context, popX + 4.0f * u, ry, popW - 8.0f * u, rowH - 1.5f * u, 5.0f * u,
                        ColorUtils.withAlpha(0xFFFFFFFF, Math.round(20.0f * popA)));
            }
            OpticumeButtonStyle.drawUiText(context, names[r], popX + 9.0f * u, ry + 4.2f * u, 6.2f * u,
                    ColorUtils.rgba(255, 255, 255, Math.round(235.0f * popA)), true);
            String state = vals[r] ? "ON" : "OFF";
            float sw = OpticumeButtonStyle.getUiTextWidth(state, 5.8f * u);
            OpticumeButtonStyle.drawUiText(context, state, popX + popW - 9.0f * u - sw, ry + 4.4f * u, 5.8f * u,
                    ColorUtils.withAlpha(vals[r] ? accent : 0xFFCBD5E1, Math.round(240.0f * popA)), false);
        }
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
                Render2DHelper.drawRoundedCroppedTextureFloat(context, wp.image(), x, y, cw, ch, radius, u, v, uw, uh,
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

        MenuLayoutPreferences.MenuLayoutMode mode = getCurrentMode();
        float u = this.uiScale;

        // Style popover click handling
        if (stylePopoverOpen) {
            float popW = 142.0f * u;
            float rowH = 16.5f * u;
            float popH = rowH * 5.0f + 12.0f * u;
            float popX = (gearBtnX + gearBtnSize) - popW;
            float popY = gearBtnY - 6.0f * u - popH;
            if (isInside(mx, my, popX, popY, popW, popH)) {
                int r = (int) ((my - (popY + 6.0f * u)) / rowH);
                if (r >= 0 && r < 5) {
                    playClick();
                    switch (r) {
                        case 0 -> MenuLayoutPreferences.setSlantEnabled(!MenuLayoutPreferences.isSlantEnabled());
                        case 1 -> MenuLayoutPreferences.setPulseLogoEnabled(!MenuLayoutPreferences.isPulseLogoEnabled());
                        case 2 -> MenuLayoutPreferences.setMusicWidgetEnabled(!MenuLayoutPreferences.isMusicWidgetEnabled());
                        case 3 -> MenuLayoutPreferences.setPerWallpaperMode(!MenuLayoutPreferences.isPerWallpaperMode());
                        case 4 -> MenuLayoutPreferences.setOsuStartExpanded(!MenuLayoutPreferences.isOsuStartExpanded());
                        default -> {
                        }
                    }
                }
                return true;
            }
            if (!isInside(mx, my, gearBtnX, gearBtnY, gearBtnSize, gearBtnSize)) {
                stylePopoverOpen = false;
            }
        }

        if (isInside(mx, my, stylePillX, stylePillY, stylePillW, stylePillH)) {
            playClick();
            MenuWallpapers.Wallpaper wp = MenuWallpapers.get(wallpaperShown);
            MenuLayoutPreferences.MenuLayoutMode nextMode = MenuLayoutPreferences.cycleActiveMode(
                    wp != null ? wp.id() : MenuWallpapers.selectedId());
            if (nextMode == MenuLayoutPreferences.MenuLayoutMode.OSU_LAZER) {
                lazerView.reset(MenuLayoutPreferences.isOsuStartExpanded());
            }
            return true;
        }
        if (isInside(mx, my, gearBtnX, gearBtnY, gearBtnSize, gearBtnSize)) {
            playClick();
            stylePopoverOpen = !stylePopoverOpen;
            return true;
        }

        if (mode == MenuLayoutPreferences.MenuLayoutMode.OSU_LAZER) {
            if (lazerView.mouseClicked(mx, my)) {
                return true;
            }
            return super.mouseClicked(event, doubled);
        }

        if (mode != MenuLayoutPreferences.MenuLayoutMode.DESIRE_CENTER) {
            if (MenuLayoutPreferences.isMusicWidgetEnabled() && musicWidget.mouseClicked(mx, my)) {
                return true;
            }
            if (MenuLayoutPreferences.isPulseLogoEnabled() && Math.hypot(mx - orbCx, my - orbCy) <= orbRadius * 1.08f) {
                playClick();
                stackExpanded = !stackExpanded;
                return true;
            }
            if (mode == MenuLayoutPreferences.MenuLayoutMode.SPLIT) {
                if (isInside(mx, my, dockX, dockAltY, dockW, dockAltH)) {
                    playClick();
                    if (minecraft != null && minecraft.gui != null) {
                        minecraft.gui.setScreen(new OpticumeAltScreen(this));
                    }
                    return true;
                }
                if (isInside(mx, my, dockX, dockGalY, dockW, dockGalH)) {
                    playClick();
                    if (minecraft != null && minecraft.gui != null) {
                        minecraft.gui.setScreen(new OpticumeGalleryScreen(this));
                    }
                    return true;
                }
                if (isInside(mx, my, dockX, dockProxyY, dockW, dockProxyH)) {
                    playClick();
                    if (minecraft != null && minecraft.gui != null) {
                        minecraft.gui.setScreen(new ProxyScreen(this));
                    }
                    return true;
                }
            } else if (isInside(mx, my, profilePillX, profilePillY, profilePillW, profilePillH)) {
                playClick();
                if (minecraft != null && minecraft.gui != null) {
                    minecraft.gui.setScreen(new OpticumeAltScreen(this));
                }
                return true;
            }
        }

        if (mode != MenuLayoutPreferences.MenuLayoutMode.SPLIT
                && isGalleryButtonInteractive()
                && isInside(mx, my, animGalX, animGalY, animGalW, animGalH)) {
            playClick();
            if (minecraft != null && minecraft.gui != null) {
                minecraft.gui.setScreen(new OpticumeGalleryScreen(this));
            }
            return true;
        }

        float slant = getActiveSlant(mode);
        float exp = clamp01(stackExpand.value);
        for (int i = 0; i < BUTTON_COUNT; i++) {
            float p = getMainButtonEnter(i);
            if (p <= 0.0f) continue;
            float enter = easeOutBack(p, 1.5f);
            float[] rect = computeButtonRect(i, mode, exp, enter);
            if (isMainButtonInteractive(i) && isInsideSlanted(mx, my, rect[0], rect[1], rect[2], rect[3], slant)) {
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
        if (!galleryOpen) {
            if (getCurrentMode() == MenuLayoutPreferences.MenuLayoutMode.OSU_LAZER
                    && lazerView.mouseDragged(toFixed(event.x()), toFixed(event.y()))) {
                return true;
            }
            return super.mouseDragged(event, dragX, dragY);
        }
        if (!draggingGallery) return super.mouseDragged(event, dragX, dragY);
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
        if (!draggingGallery) {
            if (!galleryOpen && getCurrentMode() == MenuLayoutPreferences.MenuLayoutMode.OSU_LAZER
                    && lazerView.mouseReleased(toFixed(event.x()), toFixed(event.y()))) {
                return true;
            }
            return super.mouseReleased(event);
        }
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
        if (getCurrentMode() == MenuLayoutPreferences.MenuLayoutMode.OSU_LAZER && lazerView.keyPressed(key)) {
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

    private void executeLazerAction(int id) {
        if (minecraft == null || minecraft.gui == null) return;
        switch (id) {
            case 5 -> minecraft.gui.setScreen(new OpticumeGalleryScreen(this));
            case 6 -> minecraft.gui.setScreen(new ProxyScreen(this));
            default -> executeButton(id);
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
