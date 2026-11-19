package dev.opticumeclient.client.hud;

import dev.opticumeclient.client.modules.Category;
import dev.opticumeclient.client.modules.Module;
import dev.opticumeclient.client.modules.ModuleManager;
import dev.opticumeclient.client.render.ColorUtils;
import dev.opticumeclient.client.render.Render2DHelper;
import dev.opticumeclient.client.render.TextureAssets;
import dev.opticumeclient.client.render.Theme;
import dev.opticumeclient.client.render.ThemeManager;
import dev.opticumeclient.client.render.font.OpticumeFonts;
import dev.opticumeclient.client.setting.BooleanSetting;
import dev.opticumeclient.client.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Symmetrically expanding Dynamic Island HUD pill with MSDF typography, equalizer bars,
 * smooth crossfaded alert state, and inset progress track.
 */
public class DynamicIslandHud extends Module {
    private static final float COMPACT_BASE_W = 164.0f;
    private static final float COMPACT_H = 22.0f;
    private static final float EXPANDED_H = 30.0f;
    private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("HH:mm:ss");

    private static DynamicIslandHud instance;

    private final BooleanSetting autoCenter = new BooleanSetting(
            "Auto Center", "Всегда центрировать островок по горизонтали экрана", true);
    private final BooleanSetting suppressToasts = new BooleanSetting(
            "Suppress Toasts", "Скрывать отдельные Toast-уведомления о модулях при активном островке", true);
    private final NumberSetting posX = new NumberSetting("PosX", "X позиция", 397.0, 0.0, 1920.0, 1.0);
    private final NumberSetting posY = new NumberSetting("PosY", "Y позиция", 8.0, 0.0, 1080.0, 1.0);

    private final HudManager.DraggableWidget dragWidget;

    private float curW = COMPACT_BASE_W;
    private float curH = COMPACT_H;
    private float expandBlend = 0.0f;
    private long lastFrameNanos = System.nanoTime();

    private String cachedTimeStr = "12:00:00";
    private long lastTimeUpdateMs = 0L;
    private HudAlertBus.ToggleAlert lastAlert = null;

    public DynamicIslandHud() {
        super("DynamicIsland", "Динамический островок (время, FPS, пинг, уведомления модулей)", Category.HUD);
        instance = this;
        addSetting(autoCenter);
        addSetting(suppressToasts);
        addSetting(posX);
        addSetting(posY);
        setEnabled(true);

        this.dragWidget = HudManager.getInstance().registerWidget(new HudManager.DraggableWidget(
                "DynamicIsland",
                posX.getFloatValue(),
                posY.getFloatValue(),
                COMPACT_BASE_W,
                COMPACT_H,
                this::isEnabled,
                this::onWidgetMoved
        ));
    }

    public static boolean shouldSuppressToasts() {
        if (instance == null) {
            instance = ModuleManager.getInstance().getModule(DynamicIslandHud.class);
        }
        return instance != null && instance.isEnabled() && instance.suppressToasts.isEnabled();
    }

    private void onWidgetMoved(float newX, float newY) {
        posY.setValue((double) Math.round(newY));
        float newCenterX = newX + dragWidget.getWidth() * 0.5f;
        if (dragWidget.isDragging() && autoCenter.isEnabled()) {
            Minecraft mc = Minecraft.getInstance();
            float screenW = (mc != null && mc.getWindow() != null) ? mc.getWindow().getGuiScaledWidth() : 960.0f;
            if (Math.abs(newCenterX - screenW * 0.5f) > 14.0f) {
                autoCenter.setValue(false);
                posX.setValue((double) Math.round(newCenterX - COMPACT_BASE_W * 0.5f));
            }
        } else if (!autoCenter.isEnabled()) {
            posX.setValue((double) Math.round(newCenterX - COMPACT_BASE_W * 0.5f));
        }
    }

    @Override
    public void onTick() {
        long now = System.currentTimeMillis();
        if (now - lastTimeUpdateMs >= 500L) {
            cachedTimeStr = TIME_FORMAT.format(new Date(now));
            lastTimeUpdateMs = now;
        }
    }

    @Override
    public void onRender2D(GuiGraphicsExtractor context, float tickDelta) {
        if (!isEnabled() || mc.player == null) return;
        TextureAssets.ensureInitialized();

        long nowNanos = System.nanoTime();
        float dt = Math.min(0.05f, Math.max(0.0001f, (nowNanos - lastFrameNanos) / 1.0e9f));
        lastFrameNanos = nowNanos;
        float rate = 1.0f - (float) Math.exp(-15.0f * dt);

        Theme theme = ThemeManager.getInstance().getCurrentTheme();
        int c1 = theme.getPrimaryColor();
        int c2 = theme.getSecondaryColor();

        HudAlertBus.ToggleAlert activeAlert = HudAlertBus.getInstance().getLatestActiveAlert();
        boolean expanded = activeAlert != null;
        if (activeAlert != null) {
            lastAlert = activeAlert;
        }

        String mediumFont = OpticumeFonts.KEY_HUD_MEDIUM;
        String semiboldFont = OpticumeFonts.KEY_HUD_SEMIBOLD;

        String timeStr = cachedTimeStr;
        String fpsVal = String.valueOf(mc.getFps());
        String pingVal = String.valueOf(HudAlertBus.getPlayerPing(mc));

        float compactTextSize = 7.2f;
        float compactSubSize = 5.4f;
        float timeW = HudPanelRenderer.measureHud(mediumFont, timeStr, compactTextSize);
        float fpsNumW = HudPanelRenderer.measureHud(mediumFont, fpsVal, compactTextSize);
        float fpsUnitW = HudPanelRenderer.measureHud(mediumFont, "FPS", compactSubSize);
        float pingNumW = HudPanelRenderer.measureHud(mediumFont, pingVal, compactTextSize);
        float pingUnitW = HudPanelRenderer.measureHud(mediumFont, "MS", compactSubSize);

        float eqWidth = 14.0f;
        float compactContentW = 9.0f + eqWidth + 7.0f + timeW + 7.0f + 1.5f + 7.0f
                + fpsNumW + 1.8f + fpsUnitW + 6.0f + pingNumW + 1.8f + pingUnitW + 16.0f;
        float compactTargetW = Math.max(COMPACT_BASE_W, compactContentW);

        float expandedTargetW = compactTargetW + 36.0f;
        if (lastAlert != null) {
            String alertTitle = lastAlert.enabled ? "Module Enabled" : "Module Disabled";
            String alertDesc = lastAlert.moduleName + (lastAlert.enabled ? "  [ON]" : "  [OFF]");
            float titleW = HudPanelRenderer.measureHud(mediumFont, alertTitle, 6.2f);
            float descW = HudPanelRenderer.measureHud(semiboldFont, alertDesc, 7.6f);
            expandedTargetW = Math.max(expandedTargetW, 44.0f + Math.max(titleW, descW) + 18.0f);
        }

        float targetW = expanded ? expandedTargetW : compactTargetW;
        float targetH = expanded ? EXPANDED_H : COMPACT_H;

        curW += (targetW - curW) * rate;
        curH += (targetH - curH) * rate;
        if (Math.abs(targetW - curW) < 0.1f) curW = targetW;
        if (Math.abs(targetH - curH) < 0.1f) curH = targetH;

        expandBlend += ((expanded ? 1.0f : 0.0f) - expandBlend) * rate;
        if (Math.abs((expanded ? 1.0f : 0.0f) - expandBlend) < 0.005f) {
            expandBlend = expanded ? 1.0f : 0.0f;
        }
        if (expandBlend <= 0.005f && !expanded) {
            lastAlert = null;
        }

        float screenW = mc.getWindow() != null ? mc.getWindow().getGuiScaledWidth() : 960.0f;
        float screenH = mc.getWindow() != null ? mc.getWindow().getGuiScaledHeight() : 540.0f;

        // Invariant horizontal center anchor guarantees strictly symmetrical expansion/contraction on both left and right sides
        float anchorCenterX = autoCenter.isEnabled()
                ? (screenW * 0.5f)
                : (posX.getFloatValue() + COMPACT_BASE_W * 0.5f);

        float drawX = Mth.clamp(anchorCenterX - curW * 0.5f, 4.0f, Math.max(4.0f, screenW - curW - 4.0f));
        float drawY = Mth.clamp(posY.getFloatValue(), 2.0f, Math.max(2.0f, screenH - curH - 2.0f));

        if (!dragWidget.isDragging()) {
            dragWidget.setBounds(curW, curH);
            dragWidget.syncExternalPosition(drawX, drawY);
        } else {
            drawX = dragWidget.getX();
            drawY = dragWidget.getY();
        }

        float radius = curH * 0.5f;
        HudPanelRenderer.beginWidget("DynamicIsland");
        try {
            HudPanelRenderer.drawSurface(context, "DynamicIsland", drawX, drawY, curW, curH, radius,
                    236, expanded ? 62 : 40);

            context.enableScissor(Math.round(drawX + 3.0f), Math.round(drawY + 1.0f),
                    Math.round(drawX + curW - 3.0f), Math.round(drawY + curH - 1.0f));

            // 4-bar equalizer on left edge (moves outward cleanly with drawX)
            float centerY = drawY + curH * 0.5f;
            float eqX = drawX + 10.0f;
            double waveTime = System.currentTimeMillis() / 135.0;
            for (int i = 0; i < 4; i++) {
                float wave = (float) ((Math.sin(waveTime + i * 1.15) + 1.0) * 0.5);
                float barH = 3.8f + wave * Mth.lerp(expandBlend, 6.2f, 9.5f);
                int barCol = ColorUtils.interpolateColor(c1, c2, i / 3.0f);
                Render2DHelper.drawRoundedRectFloat(context, eqX + i * 3.1f, centerY - barH * 0.5f, 1.9f, barH, 0.95f, barCol);
            }

            float idleAlpha = Mth.clamp(1.0f - expandBlend * 2.2f, 0.0f, 1.0f);
            float alertAlpha = Mth.clamp((expandBlend - 0.22f) * 1.55f, 0.0f, 1.0f);

            if (idleAlpha > 0.02f) {
                float cx = drawX + 29.0f;
                int mainCol = ColorUtils.withAlpha(0xFFEAEEF8, Math.round(245 * idleAlpha));
                int mutedCol = ColorUtils.withAlpha(0xFF8B93A8, Math.round(230 * idleAlpha));
                int divCol = ColorUtils.withAlpha(c1, Math.round(95 * idleAlpha));

                HudPanelRenderer.drawHudText(context, mediumFont, timeStr, cx, centerY - compactTextSize * 0.5f, compactTextSize, mainCol);
                cx += timeW + 7.0f;
                Render2DHelper.drawRoundedRectFloat(context, cx, centerY - 3.0f, 1.4f, 6.0f, 0.7f, divCol);
                cx += 1.4f + 7.0f;

                HudPanelRenderer.drawHudText(context, mediumFont, fpsVal, cx, centerY - compactTextSize * 0.5f, compactTextSize, mainCol);
                cx += fpsNumW + 1.8f;
                HudPanelRenderer.drawHudText(context, mediumFont, "FPS", cx, centerY - compactSubSize * 0.35f, compactSubSize, mutedCol);
                cx += fpsUnitW + 6.0f;

                HudPanelRenderer.drawHudText(context, mediumFont, pingVal, cx, centerY - compactTextSize * 0.5f, compactTextSize, mainCol);
                cx += pingNumW + 1.8f;
                HudPanelRenderer.drawHudText(context, mediumFont, "MS", cx, centerY - compactSubSize * 0.35f, compactSubSize, mutedCol);

                // Live indicator dot on right edge
                float pulse = 0.75f + 0.25f * (float) Math.sin(System.currentTimeMillis() / 360.0);
                float dotX = drawX + curW - 10.5f;
                Render2DHelper.drawCircle(context, dotX, centerY, 4.2f,
                        ColorUtils.withAlpha(0xFF22C55E, Math.round(52 * pulse * idleAlpha)));
                Render2DHelper.drawCircle(context, dotX, centerY, 2.1f,
                        ColorUtils.withAlpha(0xFF22C55E, Math.round(245 * idleAlpha)));
            }

            if (alertAlpha > 0.02f && lastAlert != null) {
                boolean state = lastAlert.enabled;
                int badgeCol = state ? 0xFF22C55E : 0xFFEF4444;

                float dotX = drawX + 31.0f;
                Render2DHelper.drawCircle(context, dotX, centerY - 1.0f, 5.2f,
                        ColorUtils.withAlpha(badgeCol, Math.round(58 * alertAlpha)));
                Render2DHelper.drawCircle(context, dotX, centerY - 1.0f, 2.7f,
                        ColorUtils.withAlpha(badgeCol, Math.round(248 * alertAlpha)));

                float tx = drawX + 42.0f;
                String title = state ? "Module Enabled" : "Module Disabled";
                String descName = lastAlert.moduleName;
                String statusTag = state ? " ON" : " OFF";

                HudPanelRenderer.drawHudText(context, mediumFont, title, tx, drawY + 5.2f, 6.2f,
                        ColorUtils.withAlpha(badgeCol, Math.round(235 * alertAlpha)));
                HudPanelRenderer.drawHudText(context, semiboldFont, descName, tx, drawY + 13.8f, 7.6f,
                        ColorUtils.withAlpha(0xFFF3F5FC, Math.round(250 * alertAlpha)));

                float nameW = HudPanelRenderer.measureHud(semiboldFont, descName, 7.6f);
                HudPanelRenderer.drawHudText(context, mediumFont, statusTag, tx + nameW + 1.5f, drawY + 14.3f, 6.6f,
                        ColorUtils.withAlpha(badgeCol, Math.round(230 * alertAlpha)));

                // Inset progress bar kept cleanly inside the straight flat span (away from pill corners)
                float agePct = Mth.clamp(1.0f - (lastAlert.getAgeSeconds() / 2.1f), 0.0f, 1.0f);
                float barInset = Math.max(16.0f, radius + 3.0f);
                float trackW = Math.max(12.0f, curW - barInset * 2.0f);
                float trackY = drawY + curH - 3.2f;
                Render2DHelper.drawRoundedRectFloat(context, drawX + barInset, trackY, trackW, 1.6f, 0.8f,
                        ColorUtils.rgba(255, 255, 255, Math.round(24 * alertAlpha)));
                if (agePct > 0.01f) {
                    Render2DHelper.drawGradientRoundedRectFloat(context, drawX + barInset, trackY, trackW * agePct, 1.6f, 0.8f,
                            ColorUtils.withAlpha(badgeCol, Math.round(235 * alertAlpha)),
                            ColorUtils.withAlpha(badgeCol, Math.round(235 * alertAlpha)),
                            ColorUtils.withAlpha(c2, Math.round(235 * alertAlpha)),
                            ColorUtils.withAlpha(c2, Math.round(235 * alertAlpha)));
                }
            }

            context.disableScissor();
        } finally {
            HudPanelRenderer.endWidget();
        }
    }
}
