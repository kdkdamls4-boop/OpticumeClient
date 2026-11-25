package dev.opticumeclient.client.ui.menu;

import com.mojang.blaze3d.platform.InputConstants;
import dev.opticumeclient.client.render.ColorUtils;
import dev.opticumeclient.client.render.Render2DHelper;
import dev.opticumeclient.client.render.TextureAssets;
import dev.opticumeclient.client.render.ThemeManager;
import dev.opticumeclient.client.services.media.SmtcBridge;
import dev.opticumeclient.client.services.network.ProxyManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fStack;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;

/**
 * Official osu! (lazer) inspired main menu layout ({@code MenuLayoutMode.OSU_LAZER}):
 * - Center-screen horizontal ButtonArea ribbon with tightly interlocking sheared parallelograms
 * - Two states: Initial (large centered pulsing OsuLogo) <-> TopLevel (shrunk logo docked in flow)
 * - Dynamic horizontal button expansion (1.45x on hover pushing siblings), click flash & beat rock
 * - 360-degree radial audio visualizer bars, drifting masked shapes inside logo, impact ring & rubber-band drag
 * - Sleek TopLevel Toolbar with Settings, Gallery, Proxy indicator, Profile chip, SmtcBridge controls & Clock
 */
final class OsuLazerMenuView {
    interface Host {
        void runAction(int id);
        void playClick();
    }

    private static final String[] BTN_LABELS = {
            "Settings", "Singleplayer", "Multiplayer", "AltManager", "Gallery", "Quit"
    };
    private static final int[] BTN_ACTION_IDS = {3, 0, 1, 2, 5, 4};
    private static final Identifier[] BTN_ICONS = {
            TextureAssets.DESIRE_MENU_SETTINGS,
            TextureAssets.DESIRE_MENU_SINGLEPLAYER,
            TextureAssets.DESIRE_MENU_MULTIPLAYER,
            TextureAssets.DESIRE_MENU_ALTMANAGER,
            TextureAssets.MENU_PANORAMA,
            TextureAssets.DESIRE_MENU_QUIT
    };
    private static final int[] BTN_RGB = {
            0x555555, 0x6644CC, 0x5E3FBA, 0xEEAA00, 0xA5CC00, 0xEE3399
    };
    private static final int BTN_COUNT = BTN_LABELS.length;

    private static final float BASE_BTN_W = 98.0f;
    private static final float BAND_H = 70.0f;
    private static final float WEDGE = 14.0f;
    private static final float SHEAR = -0.2f;
    private static final float LOGO_R_INIT = 105.0f;
    private static final float LOGO_R_TOP = 50.0f;
    private static final float TOOLBAR_H = 24.0f;
    private static final int VIS_BARS = 72;
    private static final int MOTE_SHAPES = 18;
    private static final DateTimeFormatter CLOCK_FMT = DateTimeFormatter.ofPattern("HH:mm");

    private final Host host;

    private boolean expanded = true;
    private int focusedActionIdx = -1;
    private final Spring expandAnim = new Spring();
    private final Spring logoHoverSpring = new Spring();
    private final Spring[] btnHoverWidth = Spring.array(BTN_COUNT);
    private final float[] btnFlash = new float[BTN_COUNT];

    private final float[] curBtnX = new float[BTN_COUNT];
    private final float[] curBtnY = new float[BTN_COUNT];
    private final float[] curBtnW = new float[BTN_COUNT];
    private final float[] curBtnH = new float[BTN_COUNT];
    private final boolean[] curBtnInteractive = new boolean[BTN_COUNT];

    private float revealTime = 2.0f;
    private float logoFlash = 0.0f;
    private float impactTimer = 1.0f;
    private float impactProgress = 1.0f;
    private boolean impactActive = false;

    private float logoDrawCx, logoDrawCy, logoDrawR;
    private boolean draggingLogo = false;
    private boolean dragMoved = false;
    private float dragDownX, dragDownY;
    private float dragCurX, dragCurY;
    private final Spring logoDragX = new Spring();
    private final Spring logoDragY = new Spring();

    private final float[] visAmp = new float[VIS_BARS];
    private final float[] shapeX = new float[MOTE_SHAPES];
    private final float[] shapeY = new float[MOTE_SHAPES];
    private final float[] shapeSize = new float[MOTE_SHAPES];
    private final float[] shapeSpeed = new float[MOTE_SHAPES];
    private final int[] shapeAlpha = new int[MOTE_SHAPES];

    private boolean wasHighBeat = false;
    private float sideFlashAlpha = 0.0f;

    private int pendingActionId = -1;
    private float pendingTimer = 0.0f;

    private float lastU = 1.0f;
    private float lastFixedW = 960.0f;
    private float tbY = 0.0f;
    private float tbBtnSize = 24.0f;
    private float tbSetX, tbGalX, tbProxyX, tbProfileX, tbProfileW;
    private float tbPrevX, tbPlayX, tbNextX, tbCtrlY, tbCtrlSize;
    private boolean tbMusicInteractive = false;
    private float hoverTbSet, hoverTbGal, hoverTbProxy, hoverTbProfile;
    private float hoverTbPrev, hoverTbPlay, hoverTbNext;

    OsuLazerMenuView(Host host) {
        this.host = host;
        initInnerShapes();
    }

    private void initInnerShapes() {
        long s = 0x4F53554C415A4552L;
        for (int i = 0; i < MOTE_SHAPES; i++) {
            s = s * 6364136223846793005L + 1442695040888963407L;
            shapeX[i] = (((s >>> 32) & 0xFFFF) / 65535.0f) * 1.7f - 0.85f;
            s = s * 6364136223846793005L + 1442695040888963407L;
            shapeY[i] = (((s >>> 32) & 0xFFFF) / 65535.0f) * 1.8f - 0.9f;
            shapeSize[i] = 0.075f + (i % 5) * 0.032f;
            shapeSpeed[i] = 0.14f + (i % 4) * 0.055f;
            shapeAlpha[i] = 25 + (i % 5) * 5;
        }
    }

    void reset(boolean startExpanded) {
        this.expanded = startExpanded;
        this.expandAnim.reset(startExpanded ? 1.0f : 0.0f);
        this.revealTime = startExpanded ? 2.0f : 0.0f;
        this.logoHoverSpring.reset(1.0f);
        for (int i = 0; i < BTN_COUNT; i++) {
            btnHoverWidth[i].reset(1.0f);
            btnFlash[i] = 0.0f;
            curBtnInteractive[i] = false;
        }
        this.logoFlash = 0.0f;
        this.impactActive = false;
        this.impactTimer = 0.0f;
        this.impactProgress = 1.0f;
        this.draggingLogo = false;
        this.dragMoved = false;
        this.logoDragX.reset(0.0f);
        this.logoDragY.reset(0.0f);
        this.wasHighBeat = false;
        this.sideFlashAlpha = 0.0f;
        this.pendingActionId = -1;
        this.pendingTimer = 0.0f;
        Arrays.fill(this.visAmp, 0.0f);
    }

    private void setExpandedState(boolean nextExpanded) {
        if (this.expanded == nextExpanded) return;
        this.expanded = nextExpanded;
        if (!nextExpanded) {
            this.focusedActionIdx = -1;
        }
        if (nextExpanded) {
            this.revealTime = 0.0f;
            this.logoFlash = 1.0f;
            this.impactActive = true;
            this.impactTimer = 0.0f;
            this.impactProgress = 0.0f;
        }
    }

    void render(
            GuiGraphicsExtractor context,
            float fixedW,
            float fixedH,
            float u,
            float mx,
            float my,
            float dt,
            float time,
            float beat,
            float alpha
    ) {
        this.lastU = u;
        this.lastFixedW = fixedW;

        if (pendingActionId >= 0) {
            pendingTimer += dt;
            if (pendingTimer >= 0.14f) {
                int act = pendingActionId;
                pendingActionId = -1;
                pendingTimer = 0.0f;
                host.runAction(act);
                return;
            }
        }

        expandAnim.update(expanded ? 1.0f : 0.0f, dt, 170.0f, 0.85f);
        float exp = Mth.clamp(expandAnim.value, 0.0f, 1.0f);
        if (expanded) {
            revealTime += dt;
        }

        if (logoFlash > 0.0f) {
            logoFlash = Math.max(0.0f, logoFlash - dt / 1.5f);
        }
        if (impactActive) {
            impactTimer += dt;
            impactProgress = Mth.clamp(impactTimer / 0.25f, 0.0f, 1.0f);
            if (impactProgress >= 1.0f) {
                impactActive = false;
            }
        }

        SmtcBridge.TrackSnapshot snap = SmtcBridge.getInstance().getSnapshot();
        SmtcBridge.getInstance().notifyConsumerActive();
        boolean playing = snap.playing();

        int accent1 = ThemeManager.getInstance().getCurrentTheme().getPrimaryColor();
        int accent2 = ThemeManager.getInstance().getCurrentTheme().getSecondaryColor();

        renderSideFlashes(context, fixedW, fixedH, dt, beat, playing, accent1, accent2, alpha);

        // Compute base ribbon geometry in TopLevel state (all base widths -> stable center)
        float baseW = BASE_BTN_W * u;
        float wedge = WEDGE * u;
        float bandH = BAND_H * u;
        float rInit = LOGO_R_INIT * u;
        float rTop = LOGO_R_TOP * u;
        float logoSlotW = 2.0f * rTop * 0.95f;
        float totalBaseW = BTN_COUNT * baseW + logoSlotW - 6.0f * wedge;
        float rowStartX = (fixedW - totalBaseW) * 0.5f;
        float logoSlotCx = rowStartX + baseW - wedge + logoSlotW * 0.5f;

        // Render horizontal ButtonArea backdrop and animated slanted buttons when ribbon is visible
        if (exp > 0.01f) {
            float curBandH = bandH * exp;
            float bandY = fixedH * 0.5f - curBandH * 0.5f;
            Render2DHelper.drawBlurredRoundedRect(context, 0.0f, bandY, fixedW, curBandH, 0.0f, 0x200A0E18, alpha * exp);
            Render2DHelper.drawRoundedRectFloat(context, 0.0f, bandY, fixedW, curBandH, 0.0f,
                    ColorUtils.rgba(50, 50, 50, Math.round(110.0f * exp * alpha)));
        }

        // Compute rubber-band logo coordinates early so hover detection skips if logo is hovered/dragged
        if (draggingLogo) {
            float dx = dragCurX - dragDownX;
            float dy = dragCurY - dragDownY;
            float len = (float) Math.hypot(dx, dy);
            if (len > 0.001f) {
                float scale = (float) Math.pow(len, 0.6) / len;
                logoDragX.reset(dx * scale);
                logoDragY.reset(dy * scale);
            } else {
                logoDragX.reset(0.0f);
                logoDragY.reset(0.0f);
            }
        } else {
            logoDragX.update(0.0f, dt, 120.0f, 0.35f);
            logoDragY.update(0.0f, dt, 120.0f, 0.35f);
        }

        float baseLogoCx = Mth.lerp(exp, fixedW * 0.5f, logoSlotCx) + logoDragX.value;
        float baseLogoCy = fixedH * 0.5f + logoDragY.value;
        float baseLogoR = Mth.lerp(exp, rInit, rTop);
        boolean logoHovered = pendingActionId < 0
                && Math.hypot(mx - baseLogoCx, my - baseLogoCy) <= baseLogoR * 1.06f;
        logoHoverSpring.update(logoHovered ? 1.0f : 0.0f, dt, 220.0f, 0.55f);
        float hoverScaleFactor = 1.0f + 0.10f * Mth.clamp(logoHoverSpring.value, 0.0f, 1.35f);
        float curLogoR = baseLogoR * hoverScaleFactor * (1.0f - 0.02f * beat);
        this.logoDrawCx = baseLogoCx;
        this.logoDrawCy = baseLogoCy;
        this.logoDrawR = curLogoR;

        renderRibbonButtons(context, fixedH, u, mx, my, dt, time, playing, alpha,
                exp, baseW, wedge, bandH, logoSlotCx, logoSlotW, logoHovered);

        renderOsuLogo(context, u, dt, time, beat, playing, exp, accent1, accent2, alpha);

        // Initial "Click the logo" hint
        float hintAlpha = (1.0f - exp) * alpha;
        if (hintAlpha > 0.03f) {
            OpticumeButtonStyle.drawUiCenteredText(context, "Click the logo",
                    logoDrawCx, logoDrawCy + logoDrawR + 14.0f * u, 6.0f * u,
                    ColorUtils.rgba(235, 240, 250, Math.round(150.0f * hintAlpha)), true);
        }

        // Sleek osu! TopLevel Toolbar
        renderToolbar(context, fixedW, u, mx, my, dt, exp, snap, accent1, alpha);
    }

    private void renderSideFlashes(
            GuiGraphicsExtractor context,
            float fixedW,
            float fixedH,
            float dt,
            float beat,
            boolean playing,
            int accent1,
            int accent2,
            float alpha
    ) {
        boolean highBeat = playing && beat > 0.8f;
        if (highBeat && !wasHighBeat) {
            sideFlashAlpha = 1.0f;
        }
        wasHighBeat = highBeat;
        if (sideFlashAlpha > 0.0f) {
            sideFlashAlpha = Math.max(0.0f, sideFlashAlpha - dt / 0.5f);
        }
        int fa = Math.round(70.0f * sideFlashAlpha * alpha);
        if (fa > 2) {
            float stripW = 0.12f * fixedW;
            int leftCol = ColorUtils.withAlpha(accent1, fa);
            int rightCol = ColorUtils.withAlpha(accent2, fa);
            Render2DHelper.drawGradientRoundedRectFloat(context, 0.0f, 0.0f, stripW, fixedH, 0.0f, leftCol, leftCol, 0, 0);
            Render2DHelper.drawGradientRoundedRectFloat(context, fixedW - stripW, 0.0f, stripW, fixedH, 0.0f, 0, 0, rightCol, rightCol);
        }
    }

    private void renderRibbonButtons(
            GuiGraphicsExtractor context,
            float fixedH,
            float u,
            float mx,
            float my,
            float dt,
            float time,
            boolean playing,
            float alpha,
            float exp,
            float baseW,
            float wedge,
            float bandH,
            float logoSlotCx,
            float logoSlotW,
            boolean logoHovered
    ) {
        if (exp <= 0.02f) {
            Arrays.fill(curBtnInteractive, false);
            return;
        }

        float btnY = fixedH * 0.5f - bandH * 0.5f;
        boolean interactiveAllowed = expanded && exp > 0.45f && pendingActionId < 0 && !logoHovered;

        // First compute dynamic widths and positions along both sides of the logo slot
        float[] revealF = new float[BTN_COUNT];
        float[] widths = new float[BTN_COUNT];
        for (int i = 0; i < BTN_COUNT; i++) {
            float p = Mth.clamp((revealTime - i * 0.04f) / 0.42f, 0.0f, 1.0f);
            float inv = 1.0f - p;
            float eased = p >= 1.0f ? 1.0f : (1.0f - (float) Math.pow(2.0, -10.0 * p));
            revealF[i] = Mth.clamp((expanded ? Math.min(exp, eased) : exp) * (1.0f - 0.0f * inv), 0.0f, 1.0f);
            widths[i] = baseW * revealF[i] * Math.max(0.25f, btnHoverWidth[i].value);
            curBtnY[i] = btnY;
            curBtnH[i] = bandH;
            curBtnW[i] = widths[i];
        }

        // Settings button (index 0) grows strictly leftward from left edge of logo slot
        float settingsRight = logoSlotCx - logoSlotW * 0.5f + wedge;
        curBtnX[0] = settingsRight - widths[0];

        // Right stack (indices 1..5) flows rightward from right edge of logo slot
        float cursorX = logoSlotCx + logoSlotW * 0.5f - wedge;
        for (int i = 1; i < BTN_COUNT; i++) {
            curBtnX[i] = cursorX;
            cursorX += widths[i] - wedge;
        }

        // Evaluate hover state with exact slanted coordinates and update spring widths
        int hoveredIdx = -1;
        if (interactiveAllowed) {
            for (int i = BTN_COUNT - 1; i >= 0; i--) {
                if (widths[i] >= baseW * 0.6f && isInsideSlanted(mx, my, curBtnX[i], btnY, curBtnW[i], bandH)) {
                    hoveredIdx = i;
                    break;
                }
            }
        }

        for (int i = 0; i < BTN_COUNT; i++) {
            boolean active = (i == hoveredIdx || (expanded && i == focusedActionIdx));
            curBtnInteractive[i] = interactiveAllowed && widths[i] >= baseW * 0.6f;
            btnHoverWidth[i].update(active ? 1.45f : 1.0f, dt, 260.0f, 0.45f);
            if (btnFlash[i] > 0.0f) {
                btnFlash[i] = Math.max(0.0f, btnFlash[i] - dt / 0.8f);
            }
        }

        // Draw buttons (draw non-hovered first, hovered/focused last on top for crisp border overlap; logo will cover slot edges)
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0; i < BTN_COUNT; i++) {
                boolean isHov = (i == hoveredIdx || (expanded && i == focusedActionIdx));
                if ((pass == 0 && isHov) || (pass == 1 && !isHov)) continue;
                drawSingleRibbonButton(context, i, revealF[i], isHov, expanded && i == focusedActionIdx, u, time, playing, alpha);
            }
        }
    }

    private void drawSingleRibbonButton(
            GuiGraphicsExtractor context,
            int idx,
            float widthFactor,
            boolean isHov,
            boolean isFocused,
            float u,
            float time,
            boolean playing,
            float alpha
    ) {
        if (widthFactor <= 0.05f) return;
        float bx = curBtnX[idx];
        float by = curBtnY[idx];
        float bw = curBtnW[idx];
        float bh = curBtnH[idx];
        float cx = bx + bw * 0.5f;
        float cy = by + bh * 0.5f;
        float rad = 3.0f * u;
        float hv = Mth.clamp((btnHoverWidth[idx].value - 1.0f) / 0.45f, 0.0f, 1.0f);
        float a = alpha * Mth.clamp(widthFactor, 0.0f, 1.0f);

        int rgb = BTN_RGB[idx];
        int tintAlpha = Math.round((140.0f + 40.0f * hv) * a);
        int fillCol = ColorUtils.withAlpha(0xFF000000 | rgb, tintAlpha);

        Matrix3x2fStack pose = context.pose();

        // Pass 1: sheared parallelogram shell
        pose.pushMatrix();
        pose.translate(cx, cy);
        pose.mul(new Matrix3x2f(1.0f, 0.0f, SHEAR, 1.0f, 0.0f, 0.0f));
        pose.translate(-cx, -cy);

        Render2DHelper.drawGlow(context, bx, by + 2.0f * u, bw, bh, 8.0f * u,
                ColorUtils.withAlpha(0xFF000000, Math.round(50.0f * a)));
        Render2DHelper.drawBlurredRoundedRect(context, bx, by, bw, bh, rad, 0x180A0E18, a);
        Render2DHelper.drawRoundedRectFloat(context, bx, by, bw, bh, rad, fillCol);
        Render2DHelper.drawRoundedOutlineFloat(context, bx, by, bw, bh, rad, isFocused ? 1.4f : 0.6f,
                ColorUtils.withAlpha(0xFFFFFFFF, Math.round((isFocused ? 230.0f : (40.0f + 55.0f * hv)) * a)));

        if (btnFlash[idx] > 0.005f) {
            Render2DHelper.drawRoundedRectFloat(context, bx, by, bw, bh, rad,
                    ColorUtils.withAlpha(0xFFFFFFFF, Math.round(230.0f * btnFlash[idx] * a)));
        }
        pose.popMatrix();

        // Pass 2: completely un-sheared icon & label
        float contentAlpha = Mth.clamp((widthFactor - 0.5f) / 0.3f, 0.0f, 1.0f) * alpha;
        if (contentAlpha <= 0.02f) return;

        float iconSize = 22.0f * u;
        float iconPivotX = cx;
        float iconPivotY = cy - 6.0f * u;
        float iconScale = 1.0f + 0.20f * hv;
        float freq = playing ? 6.7f : 3.0f;
        float rockDeg = isHov ? (8.0f * hv * (float) Math.sin(time * freq)) : 0.0f;
        float bounceY = isHov ? (-Math.abs((float) Math.sin(time * freq)) * 2.5f * u * hv) : 0.0f;

        pose.pushMatrix();
        pose.translate(iconPivotX, iconPivotY + bounceY);
        if (Math.abs(rockDeg) > 0.01f) {
            pose.rotate((float) Math.toRadians(rockDeg));
        }
        pose.scale(iconScale, iconScale);
        Render2DHelper.drawTextureFloat(context, BTN_ICONS[idx],
                -iconSize * 0.5f, -iconSize * 0.5f, iconSize, iconSize,
                ColorUtils.rgba(255, 255, 255, Math.round(248.0f * contentAlpha)));
        pose.popMatrix();

        float lblSize = 7.5f * u;
        OpticumeButtonStyle.drawUiCenteredText(context, BTN_LABELS[idx], cx, by + bh - 5.0f * u - lblSize, lblSize,
                ColorUtils.rgba(255, 255, 255, Math.round(248.0f * contentAlpha)), true);
    }

    private void renderOsuLogo(
            GuiGraphicsExtractor context,
            float u,
            float dt,
            float time,
            float beat,
            boolean playing,
            float exp,
            int accent1,
            int accent2,
            float alpha
    ) {
        float cx = logoDrawCx;
        float cy = logoDrawCy;
        float r = logoDrawR;
        float x = cx - r;
        float y = cy - r;
        float d = r * 2.0f;
        Matrix3x2fStack pose = context.pose();

        // 1. Radial 360-degree visualizer bars around logo
        int timeBucket = (int) Math.floor(time * 8.0f);
        float lerpK = 1.0f - (float) Math.exp(-16.0f * dt);
        float lenScale = u * (r / Math.max(1.0f, LOGO_R_TOP * u));
        int barCol = ColorUtils.withAlpha(0xFFFFFFFF, Math.round(51.0f * alpha));
        for (int i = 0; i < VIS_BARS; i++) {
            float targetAmp;
            if (playing) {
                float h = barNoise(i, timeBucket);
                targetAmp = beat * (0.45f + 0.55f * h);
            } else {
                targetAmp = 0.12f * (0.5f + 0.5f * (float) Math.sin(time * 1.3f + i * 0.4f));
            }
            visAmp[i] += (targetAmp - visAmp[i]) * lerpK;
            float len = (4.0f + 22.0f * visAmp[i]) * lenScale;
            float angle = (float) (i / (double) VIS_BARS * Math.PI * 2.0);
            pose.pushMatrix();
            pose.translate(cx, cy);
            pose.rotate(angle);
            Render2DHelper.drawRoundedRectFloat(context, r, -1.1f * u, len, 2.2f * u, 1.1f * u, barCol);
            pose.popMatrix();
        }

        // 2. Outer theme glow + gradient circle body
        Render2DHelper.drawGlow(context, x, y, d, d, 20.0f * u,
                ColorUtils.withAlpha(accent1, Math.round((60.0f + 45.0f * beat) * alpha)));

        int topCol = ColorUtils.withAlpha(accent1, Math.round(238.0f * alpha));
        int botCol = ColorUtils.withAlpha(accent2, Math.round(244.0f * alpha));
        Render2DHelper.drawGradientRoundedRectFloat(context, x, y, d, d, r, topCol, botCol, botCol, topCol);

        // 3. Drifting procedural diamond/triangle-style particles masked strictly inside circle
        float speedMul = playing ? 2.0f : 1.0f;
        float diamondAngle = (float) (Math.PI * 0.25);
        for (int i = 0; i < MOTE_SHAPES; i++) {
            shapeY[i] -= shapeSpeed[i] * dt * speedMul;
            if (shapeY[i] < -1.0f) {
                shapeY[i] = 1.0f;
                shapeX[i] = (((shapeX[i] + 0.85f + 0.61f) % 1.7f) - 0.85f);
            }
            float distNorm = (float) Math.hypot(shapeX[i], shapeY[i]);
            float pRadius = shapeSize[i] * r;
            if (distNorm * r + pRadius * 0.8f < r * 0.85f) {
                float sx = cx + shapeX[i] * r;
                float sy = cy + shapeY[i] * r;
                pose.pushMatrix();
                pose.translate(sx, sy);
                pose.rotate(diamondAngle);
                Render2DHelper.drawRoundedRectFloat(context, -pRadius * 0.5f, -pRadius * 0.5f, pRadius, pRadius,
                        pRadius * 0.12f, ColorUtils.withAlpha(0xFFFFFFFF, Math.round(shapeAlpha[i] * alpha)));
                pose.popMatrix();
            }
        }

        // 4. White click flash + crisp circular border
        if (logoFlash > 0.004f) {
            Render2DHelper.drawRoundedRectFloat(context, x, y, d, d, r,
                    ColorUtils.withAlpha(0xFFFFFFFF, Math.round(102.0f * logoFlash * alpha)));
        }
        Render2DHelper.drawRoundedOutlineFloat(context, x, y, d, d, r, 1.5f * u,
                ColorUtils.withAlpha(0xFFFFFFFF, Math.round(215.0f * alpha)));

        // 5. Impact expanding ring upon Initial -> TopLevel reveal
        if (impactActive && impactProgress < 1.0f) {
            float impR = r * Mth.lerp(impactProgress, 1.0f, 1.12f);
            float impA = (1.0f - impactProgress) * alpha;
            Render2DHelper.drawRoundedOutlineFloat(context, cx - impR, cy - impR, impR * 2.0f, impR * 2.0f, impR,
                    Math.max(1.0f, 2.5f * u), ColorUtils.withAlpha(0xFFFFFFFF, Math.round(230.0f * impA)));
        }

        // 6. Signature script "Opticume" centered inside OsuLogo
        float scriptSz = r * 0.52f;
        float tw = OpticumeButtonStyle.getScriptTextWidth("Opticume", scriptSz);
        OpticumeButtonStyle.drawScriptTitle(context, "Opticume", cx - tw * 0.5f, cy - scriptSz * 0.62f,
                scriptSz, alpha, time, true);
    }

    private void renderToolbar(
            GuiGraphicsExtractor context,
            float fixedW,
            float u,
            float mx,
            float my,
            float dt,
            float exp,
            SmtcBridge.TrackSnapshot snap,
            int accent,
            float alpha
    ) {
        if (exp <= 0.02f) {
            tbY = -TOOLBAR_H * u;
            tbMusicInteractive = false;
            return;
        }
        float tbH = TOOLBAR_H * u;
        float tbAlpha = exp * alpha;
        tbY = -tbH * (1.0f - exp);
        tbBtnSize = tbH;

        Render2DHelper.drawBlurredRoundedRect(context, 0.0f, tbY, fixedW, tbH, 0.0f, 0x200A0E18, tbAlpha);
        Render2DHelper.drawRoundedRectFloat(context, 0.0f, tbY, fixedW, tbH, 0.0f,
                ColorUtils.rgba(16, 18, 26, Math.round(150.0f * tbAlpha)));
        Render2DHelper.drawRect(context, 0.0f, tbY + tbH - 0.6f, fixedW, 0.6f,
                ColorUtils.withAlpha(0xFFFFFFFF, Math.round(24.0f * tbAlpha)));

        // Left action tiles (Settings, Gallery, Proxy)
        tbSetX = 0.0f;
        tbGalX = tbSetX + tbBtnSize;
        tbProxyX = tbGalX + tbBtnSize;

        boolean hovSet = expanded && isInside(mx, my, tbSetX, tbY, tbBtnSize, tbH);
        boolean hovGal = expanded && isInside(mx, my, tbGalX, tbY, tbBtnSize, tbH);
        boolean hovProxy = expanded && isInside(mx, my, tbProxyX, tbY, tbBtnSize, tbH);

        float animK = 1.0f - (float) Math.exp(-16.0f * dt);
        hoverTbSet += ((hovSet ? 1.0f : 0.0f) - hoverTbSet) * animK;
        hoverTbGal += ((hovGal ? 1.0f : 0.0f) - hoverTbGal) * animK;
        hoverTbProxy += ((hovProxy ? 1.0f : 0.0f) - hoverTbProxy) * animK;

        drawToolbarIconTile(context, tbSetX, tbY, tbBtnSize, TextureAssets.DESIRE_MENU_SETTINGS, hoverTbSet, tbAlpha, u);
        drawToolbarIconTile(context, tbGalX, tbY, tbBtnSize, TextureAssets.MENU_PANORAMA, hoverTbGal, tbAlpha, u);

        // Proxy square with status dot
        if (hoverTbProxy > 0.02f) {
            Render2DHelper.drawRect(context, tbProxyX, tbY, tbBtnSize, tbBtnSize,
                    ColorUtils.withAlpha(0xFFFFFFFF, Math.round(30.0f * hoverTbProxy * tbAlpha)));
        }
        boolean proxyOn = ProxyManager.getInstance().isEnabled();
        OpticumeButtonStyle.drawUiCenteredText(context, "PX", tbProxyX + tbBtnSize * 0.5f, tbY + (tbBtnSize - 6.0f * u) * 0.5f,
                6.0f * u, ColorUtils.rgba(240, 244, 252, Math.round(Mth.lerp(hoverTbProxy, 195.0f, 255.0f) * tbAlpha)), true);
        Render2DHelper.drawRoundedRectFloat(context, tbProxyX + tbBtnSize - 6.0f * u, tbY + tbBtnSize - 6.0f * u, 3.6f * u, 3.6f * u, 1.8f * u,
                ColorUtils.withAlpha(proxyOn ? 0xFF4ADE80 : 0xFF64748B, Math.round(230.0f * tbAlpha)));

        // Profile chip (player head + username -> AltManager)
        Minecraft mc = Minecraft.getInstance();
        String playerName = (mc != null && mc.getUser() != null) ? mc.getUser().getName() : "Player";
        tbProfileX = tbProxyX + tbBtnSize + 4.0f * u;
        float headSz = Math.max(8, Math.round(14.0f * u));
        float nameW = OpticumeButtonStyle.getUiTextWidth(playerName, 6.5f * u);
        tbProfileW = Math.min(130.0f * u, headSz + 14.0f * u + nameW);
        boolean hovProf = expanded && isInside(mx, my, tbProfileX, tbY, tbProfileW, tbH);
        hoverTbProfile += ((hovProf ? 1.0f : 0.0f) - hoverTbProfile) * animK;
        if (hoverTbProfile > 0.02f) {
            Render2DHelper.drawRect(context, tbProfileX, tbY, tbProfileW, tbH,
                    ColorUtils.withAlpha(0xFFFFFFFF, Math.round(28.0f * hoverTbProfile * tbAlpha)));
        }
        Render2DHelper.drawPlayerHeadByUuid(context, UUIDUtil.createOfflinePlayerUUID(playerName),
                Math.round(tbProfileX + 5.0f * u), Math.round(tbY + (tbH - headSz) * 0.5f), Math.round(headSz), tbAlpha);
        String safeName = trimUiText(playerName, 6.5f * u, Math.max(18.0f * u, tbProfileW - headSz - 12.0f * u));
        OpticumeButtonStyle.drawUiText(context, safeName, tbProfileX + 9.0f * u + headSz, tbY + (tbH - 6.5f * u) * 0.5f,
                6.5f * u, ColorUtils.rgba(248, 250, 255, Math.round(240.0f * tbAlpha)), true);

        // Right side: Clock + Music controls & current track
        String clockText = LocalTime.now().format(CLOCK_FMT);
        float clockSz = 6.8f * u;
        float clockW = OpticumeButtonStyle.getUiTextWidth(clockText, clockSz);
        float clockX = fixedW - 10.0f * u - clockW;
        OpticumeButtonStyle.drawUiText(context, clockText, clockX, tbY + (tbH - clockSz) * 0.5f,
                clockSz, ColorUtils.rgba(240, 245, 255, Math.round(235.0f * tbAlpha)), true);

        boolean hasTrack = snap.active() && snap.title() != null && !snap.title().isBlank();
        tbMusicInteractive = hasTrack && expanded && exp > 0.45f;
        if (hasTrack) {
            tbCtrlSize = 14.0f * u;
            float gap = 2.0f * u;
            tbNextX = clockX - 10.0f * u - tbCtrlSize;
            tbPlayX = tbNextX - gap - tbCtrlSize;
            tbPrevX = tbPlayX - gap - tbCtrlSize;
            tbCtrlY = tbY + (tbH - tbCtrlSize) * 0.5f;

            boolean hovPrev = tbMusicInteractive && isInside(mx, my, tbPrevX, tbCtrlY, tbCtrlSize, tbCtrlSize);
            boolean hovPlay = tbMusicInteractive && isInside(mx, my, tbPlayX, tbCtrlY, tbCtrlSize, tbCtrlSize);
            boolean hovNext = tbMusicInteractive && isInside(mx, my, tbNextX, tbCtrlY, tbCtrlSize, tbCtrlSize);
            hoverTbPrev += ((hovPrev ? 1.0f : 0.0f) - hoverTbPrev) * animK;
            hoverTbPlay += ((hovPlay ? 1.0f : 0.0f) - hoverTbPlay) * animK;
            hoverTbNext += ((hovNext ? 1.0f : 0.0f) - hoverTbNext) * animK;

            drawToolbarMusicBtn(context, tbPrevX, tbCtrlY, tbCtrlSize, "<", hoverTbPrev, accent, tbAlpha, u);
            drawToolbarMusicBtn(context, tbPlayX, tbCtrlY, tbCtrlSize, snap.playing() ? "||" : ">", Math.max(0.3f, hoverTbPlay), accent, tbAlpha, u);
            drawToolbarMusicBtn(context, tbNextX, tbCtrlY, tbCtrlSize, ">", hoverTbNext, accent, tbAlpha, u);

            String fullTrack = (snap.artist() != null && !snap.artist().isBlank())
                    ? (snap.title() + " — " + snap.artist())
                    : snap.title();
            float trackSz = 6.2f * u;
            float availTrackW = Math.min(170.0f * u, Math.max(20.0f * u, tbPrevX - (tbProfileX + tbProfileW + 14.0f * u)));
            String trimmedTrack = trimUiText(fullTrack, trackSz, availTrackW);
            float tw = OpticumeButtonStyle.getUiTextWidth(trimmedTrack, trackSz);
            OpticumeButtonStyle.drawUiText(context, trimmedTrack, tbPrevX - 8.0f * u - tw, tbY + (tbH - trackSz) * 0.5f,
                    trackSz, ColorUtils.rgba(235, 240, 250, Math.round(220.0f * tbAlpha)), true);
        }
    }

    private static void drawToolbarIconTile(
            GuiGraphicsExtractor context,
            float x,
            float y,
            float size,
            Identifier icon,
            float hv,
            float tbAlpha,
            float u
    ) {
        if (hv > 0.02f) {
            Render2DHelper.drawRect(context, x, y, size, size,
                    ColorUtils.withAlpha(0xFFFFFFFF, Math.round(30.0f * hv * tbAlpha)));
        }
        float icSz = 11.0f * u;
        Render2DHelper.drawTextureFloat(context, icon, x + (size - icSz) * 0.5f, y + (size - icSz) * 0.5f, icSz, icSz,
                ColorUtils.rgba(255, 255, 255, Math.round(Mth.lerp(hv, 195.0f, 255.0f) * tbAlpha)));
    }

    private static void drawToolbarMusicBtn(
            GuiGraphicsExtractor context,
            float x,
            float y,
            float size,
            String glyph,
            float hv,
            int accent,
            float tbAlpha,
            float u
    ) {
        int bg = ColorUtils.interpolateColor(
                ColorUtils.withAlpha(0xFFFFFFFF, Math.round(14.0f * tbAlpha)),
                ColorUtils.withAlpha(accent, Math.round(95.0f * tbAlpha)),
                hv
        );
        Render2DHelper.drawRoundedRectFloat(context, x, y, size, size, 4.0f * u, bg);
        OpticumeButtonStyle.drawUiCenteredText(context, glyph, x + size * 0.5f, y + size * 0.25f, 6.0f * u,
                ColorUtils.rgba(255, 255, 255, Math.round(Mth.lerp(hv, 200.0f, 255.0f) * tbAlpha)), false);
    }

    boolean mouseClicked(float mx, float my) {
        if (pendingActionId >= 0) return true;

        // 1. Check Toolbar clicks in TopLevel
        if (expanded && expandAnim.value > 0.45f && my >= tbY && my <= tbY + tbBtnSize) {
            if (isInside(mx, my, tbSetX, tbY, tbBtnSize, tbBtnSize)) {
                host.playClick();
                host.runAction(3);
                return true;
            }
            if (isInside(mx, my, tbGalX, tbY, tbBtnSize, tbBtnSize)) {
                host.playClick();
                host.runAction(5);
                return true;
            }
            if (isInside(mx, my, tbProxyX, tbY, tbBtnSize, tbBtnSize)) {
                host.playClick();
                host.runAction(6);
                return true;
            }
            if (isInside(mx, my, tbProfileX, tbY, tbProfileW, tbBtnSize)) {
                host.playClick();
                host.runAction(2);
                return true;
            }
            if (tbMusicInteractive) {
                if (isInside(mx, my, tbPrevX, tbCtrlY, tbCtrlSize, tbCtrlSize)) {
                    host.playClick();
                    SmtcBridge.getInstance().prevTrack();
                    return true;
                }
                if (isInside(mx, my, tbPlayX, tbCtrlY, tbCtrlSize, tbCtrlSize)) {
                    host.playClick();
                    SmtcBridge.getInstance().togglePlayPause();
                    return true;
                }
                if (isInside(mx, my, tbNextX, tbCtrlY, tbCtrlSize, tbCtrlSize)) {
                    host.playClick();
                    SmtcBridge.getInstance().nextTrack();
                    return true;
                }
            }
            return true;
        }

        // 2. Check OsuLogo hit (begin rubber-band drag tracking; toggle happens on release if drag < 4px)
        if (Math.hypot(mx - logoDrawCx, my - logoDrawCy) <= logoDrawR * 1.06f) {
            draggingLogo = true;
            dragMoved = false;
            dragDownX = mx;
            dragDownY = my;
            dragCurX = mx;
            dragCurY = my;
            return true;
        }

        // 3. Check slanted ribbon buttons
        if (expanded && expandAnim.value > 0.45f) {
            for (int i = BTN_COUNT - 1; i >= 0; i--) {
                if (curBtnInteractive[i] && isInsideSlanted(mx, my, curBtnX[i], curBtnY[i], curBtnW[i], curBtnH[i])) {
                    focusedActionIdx = i;
                    btnFlash[i] = 1.0f;
                    host.playClick();
                    pendingActionId = BTN_ACTION_IDS[i];
                    pendingTimer = 0.0f;
                    return true;
                }
            }
        }

        return false;
    }

    boolean mouseDragged(float mx, float my) {
        if (!draggingLogo) return false;
        dragCurX = mx;
        dragCurY = my;
        if (Math.hypot(mx - dragDownX, my - dragDownY) >= 4.0f) {
            dragMoved = true;
        }
        return true;
    }

    boolean mouseReleased(float mx, float my) {
        if (!draggingLogo) return false;
        draggingLogo = false;
        if (!dragMoved && Math.hypot(mx - dragDownX, my - dragDownY) < 4.0f) {
            host.playClick();
            setExpandedState(!expanded);
        }
        dragMoved = false;
        return true;
    }

    boolean keyPressed(int key) {
        if (key == InputConstants.KEY_ESCAPE && expanded) {
            host.playClick();
            setExpandedState(false);
            return true;
        }
        if (!expanded) {
            if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER || key == InputConstants.KEY_SPACE) {
                host.playClick();
                setExpandedState(true);
                focusedActionIdx = 1;
                return true;
            }
            return false;
        }
        if (key == InputConstants.KEY_TAB || key == InputConstants.KEY_RIGHT || key == InputConstants.KEY_DOWN) {
            focusedActionIdx = focusedActionIdx < 0 ? 0 : (focusedActionIdx + 1) % BTN_COUNT;
            host.playClick();
            return true;
        }
        if (key == InputConstants.KEY_LEFT || key == InputConstants.KEY_UP) {
            focusedActionIdx = focusedActionIdx < 0 ? BTN_COUNT - 1 : (focusedActionIdx + BTN_COUNT - 1) % BTN_COUNT;
            host.playClick();
            return true;
        }
        if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER || key == InputConstants.KEY_SPACE) {
            if (focusedActionIdx < 0 || focusedActionIdx >= BTN_COUNT) {
                focusedActionIdx = 1;
                host.playClick();
                return true;
            }
            if (pendingActionId < 0 && expandAnim.value > 0.45f) {
                btnFlash[focusedActionIdx] = 1.0f;
                host.playClick();
                pendingActionId = BTN_ACTION_IDS[focusedActionIdx];
                pendingTimer = 0.0f;
            }
            return true;
        }
        return false;
    }

    private static float barNoise(int index, int bucket) {
        int h = index * 374761393 + bucket * 668265263;
        h = (h ^ (h >>> 13)) * 1274126177;
        return ((h ^ (h >>> 16)) & 0xFFFF) / 65535.0f;
    }

    private static String trimUiText(String text, float pxSize, float maxWidth) {
        if (text == null || text.isEmpty()) return "";
        if (OpticumeButtonStyle.getUiTextWidth(text, pxSize) <= maxWidth) return text;
        String ellipsis = "..";
        float ellW = OpticumeButtonStyle.getUiTextWidth(ellipsis, pxSize);
        int end = text.length();
        while (end > 0 && OpticumeButtonStyle.getUiTextWidth(text.substring(0, end), pxSize) + ellW > maxWidth) {
            end--;
        }
        return end <= 0 ? ellipsis : (text.substring(0, end) + ellipsis);
    }

    private static boolean isInside(float mx, float my, float x, float y, float w, float h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    private static boolean isInsideSlanted(float mx, float my, float x, float y, float w, float h) {
        float cy = y + h * 0.5f;
        float adjX = mx - SHEAR * (my - cy);
        return adjX >= x && adjX <= x + w && my >= y && my <= y + h;
    }

    private static final class Spring {
        private static final float STEP = 1.0f / 240.0f;
        float value;
        float velocity;

        static Spring[] array(int n) {
            Spring[] arr = new Spring[n];
            for (int i = 0; i < n; i++) arr[i] = new Spring();
            return arr;
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
