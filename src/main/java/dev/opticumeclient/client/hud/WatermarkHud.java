package dev.opticumeclient.client.hud;

import dev.opticumeclient.client.modules.Category;
import dev.opticumeclient.client.modules.Module;
import dev.opticumeclient.client.modules.hud.InterfaceModule;
import dev.opticumeclient.client.modules.visuals.NoRenderModule;
import dev.opticumeclient.client.render.ColorUtils;
import dev.opticumeclient.client.render.Render2DHelper;
import dev.opticumeclient.client.render.TextureAssets;
import dev.opticumeclient.client.render.Theme;
import dev.opticumeclient.client.render.ThemeManager;
import dev.opticumeclient.client.render.font.OpticumeFonts;
import dev.opticumeclient.client.setting.NumberSetting;
import dev.opticumeclient.mixin.BossHealthOverlayAccessor;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;

import java.time.LocalTime;
import java.util.Arrays;
import java.util.Locale;

/**
 * Draggable Watermark HUD widget (Relic two-row pills or Modern glass capsule).
 */
public class WatermarkHud extends Module {
    private final NumberSetting posX = new NumberSetting("PosX", "X позиция", 8.0, 0.0, 1920.0, 1.0);
    private final NumberSetting posY = new NumberSetting("PosY", "Y позиция", 8.0, 0.0, 1080.0, 1.0);
    private final HudManager.DraggableWidget dragWidget;

    private String cachedFpsStr = "0 FPS";
    private String cachedPingStr = "0 ms";
    private String cachedBpsStr = "0.0 BPS";
    private String cachedXyzStr = "0 0 0";
    private String cachedUser = "Player";
    private float cachedTitleW = 58.0f;
    private float cachedUserW = 30.0f;
    private float cachedFpsW = 32.0f;
    private float cachedPingW = 28.0f;
    private float cachedBpsW = 42.0f;
    private float cachedXyzW = 48.0f;
    private float cachedTotalW = 270.0f;
    private float wmBossShift = 0.0f;

    public WatermarkHud() {
        super("Watermark", "Ватермарка клиента (ник, FPS, пинг, сервер, координаты)", Category.HUD);
        addSetting(posX);
        addSetting(posY);
        setEnabled(false);

        this.dragWidget = HudManager.getInstance().registerWidget(new HudManager.DraggableWidget(
                "Watermark",
                posX.getFloatValue(),
                posY.getFloatValue(),
                270.0f,
                22.0f,
                this::isEnabled,
                (newX, newY) -> {
                    posX.setValue((double) Math.round(newX));
                    posY.setValue((double) Math.round(newY));
                }
        ));
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;
        double dx = mc.player.getX() - mc.player.xOld;
        double dz = mc.player.getZ() - mc.player.zOld;
        long bpsTenths = Math.round(Math.hypot(dx, dz) * 200.0);

        cachedFpsStr = mc.getFps() + " FPS";
        cachedPingStr = HudAlertBus.getPlayerPing(mc) + " ms";
        cachedBpsStr = (bpsTenths / 10) + "." + (bpsTenths % 10) + " BPS";
        BlockPos pos = mc.player.blockPosition();
        cachedXyzStr = pos.getX() + " " + pos.getY() + " " + pos.getZ();
        cachedUser = (mc.getUser() != null && mc.getUser().getName() != null) ? mc.getUser().getName() : "Player";

        float wmScale = 0.78f;
        cachedTitleW = Render2DHelper.getTextWidth("OpticumeClient", 0.82f);
        cachedUserW = Render2DHelper.getTextWidth(cachedUser, wmScale);
        cachedFpsW = Render2DHelper.getTextWidth(cachedFpsStr, wmScale);
        cachedPingW = Render2DHelper.getTextWidth(cachedPingStr, wmScale);
        cachedBpsW = Render2DHelper.getTextWidth(cachedBpsStr, wmScale);
        cachedXyzW = Render2DHelper.getTextWidth(cachedXyzStr, wmScale);
        cachedTotalW = 24.0f + cachedTitleW + 14.0f + cachedUserW + 22.0f + cachedFpsW + 22.0f
                + cachedPingW + 22.0f + cachedBpsW + 22.0f + cachedXyzW + 12.0f;
    }

    @Override
    public void onRender2D(GuiGraphicsExtractor context, float tickDelta) {
        if (!isEnabled() || mc.player == null) return;
        TextureAssets.ensureInitialized();

        dragWidget.syncExternalPosition(posX.getFloatValue(), posY.getFloatValue());

        HudPanelRenderer.beginWidget("Watermark");
        try {
            InterfaceModule iface = InterfaceModule.getInstance();
            if (iface == null || iface.isRelicWatermark()) {
                renderRelic(context);
            } else {
                renderModern(context);
            }
        } finally {
            HudPanelRenderer.endWidget();
        }
    }

    private void renderModern(GuiGraphicsExtractor context) {
        Theme theme = ThemeManager.getInstance().getCurrentTheme();
        int c1 = theme.getPrimaryColor();
        int c2 = theme.getSecondaryColor();

        float scale = 0.78f;
        float h = 22.0f;
        dragWidget.setBounds(cachedTotalW, h);
        float x = dragWidget.getX();
        float y = dragWidget.getY();

        HudPanelRenderer.drawSurface(context, "Watermark", x, y, cachedTotalW, h, 6.0f, 225, 44);

        float curX = x + 6.0f;
        Render2DHelper.drawTextureFloat(context, TextureAssets.MINI_LOGO, curX, y + 4.5f, 13.0f, 13.0f, c1);
        curX += 16.0f;

        Render2DHelper.drawScaledText(context, "OpticumeClient", curX, y + 6.5f, ColorUtils.interpolateColor(c1, c2, 0.35f), 0.82f, true);
        curX += cachedTitleW + 6.0f;
        drawDivider(context, curX, y + 5.0f, h - 10.0f, c1);
        curX += 6.0f;

        Render2DHelper.drawScaledText(context, cachedUser, curX, y + 7.0f, theme.getTextColor(), scale, false);
        curX += cachedUserW + 6.0f;
        drawDivider(context, curX, y + 5.0f, h - 10.0f, c1);
        curX += 6.0f;

        Render2DHelper.drawTextureFloat(context, TextureAssets.FPS_ICON, curX, y + 5.5f, 10.0f, 10.0f, c2);
        curX += 13.0f;
        Render2DHelper.drawScaledText(context, cachedFpsStr, curX, y + 7.0f, theme.getTextColor(), scale, false);
        curX += cachedFpsW + 6.0f;
        drawDivider(context, curX, y + 5.0f, h - 10.0f, c1);
        curX += 6.0f;

        Render2DHelper.drawTextureFloat(context, TextureAssets.PING_ICON, curX, y + 5.5f, 10.0f, 10.0f, c1);
        curX += 13.0f;
        Render2DHelper.drawScaledText(context, cachedPingStr, curX, y + 7.0f, theme.getTextColor(), scale, false);
        curX += cachedPingW + 6.0f;
        drawDivider(context, curX, y + 5.0f, h - 10.0f, c1);
        curX += 6.0f;

        Render2DHelper.drawTextureFloat(context, TextureAssets.SPEED_ICON, curX, y + 5.5f, 10.0f, 10.0f, c2);
        curX += 13.0f;
        Render2DHelper.drawScaledText(context, cachedBpsStr, curX, y + 7.0f, theme.getTextColor(), scale, false);
        curX += cachedBpsW + 6.0f;
        drawDivider(context, curX, y + 5.0f, h - 10.0f, c1);
        curX += 6.0f;

        Render2DHelper.drawTextureFloat(context, TextureAssets.COORDS_ICON, curX, y + 5.5f, 10.0f, 10.0f, c1);
        curX += 13.0f;
        Render2DHelper.drawScaledText(context, cachedXyzStr, curX, y + 7.0f, theme.getMutedTextColor(), scale, false);
    }

    private void renderRelic(GuiGraphicsExtractor context) {
        Theme theme = ThemeManager.getInstance().getCurrentTheme();
        int c1 = theme.getPrimaryColor();
        int c2 = theme.getSecondaryColor();

        String fontKey = OpticumeFonts.KEY_HUD_MEDIUM;
        float textSize = 7.6f;
        float subSize = 5.4f;
        float h = 19.5f;
        float logoGap = 4.0f;
        float rowGap = 3.5f;

        String username = (cachedUser != null && !cachedUser.isBlank()) ? cachedUser : "Player";
        String fpsVal = cachedFpsStr.replaceAll("[^0-9]", "");
        if (fpsVal.isEmpty()) fpsVal = "0";
        String pingVal = cachedPingStr.replaceAll("[^0-9]", "");
        if (pingVal.isEmpty()) pingVal = "0";

        LocalTime now = LocalTime.now();
        String hourStr = String.format(Locale.ROOT, "%02d", now.getHour());
        String minStr = String.format(Locale.ROOT, "%02d", now.getMinute());

        String host = (mc.getCurrentServer() != null && mc.getCurrentServer().ip != null && !mc.getCurrentServer().ip.isBlank())
                ? mc.getCurrentServer().ip : "singleplayer";
        String[] dom = splitDomainParts(host);

        float nameW = HudPanelRenderer.measureHud(fontKey, username, textSize);
        float fpsValW = HudPanelRenderer.measureHud(fontKey, fpsVal, textSize);
        float fpsSubW = HudPanelRenderer.measureHud(fontKey, "FPS", subSize);
        float timeW = HudPanelRenderer.measureHud(fontKey, hourStr + ":" + minStr, textSize);
        float hourW = HudPanelRenderer.measureHud(fontKey, hourStr, textSize);
        float colonW = HudPanelRenderer.measureHud(fontKey, ":", textSize);

        float iconS = 7.2f;
        float sepGap = 6.5f;
        float w1 = 8.0f + iconS + 4.5f + nameW + sepGap + 1.5f + sepGap
                + iconS + 4.0f + fpsValW + 2.0f + fpsSubW + sepGap + 1.5f + sepGap
                + iconS + 4.0f + timeW + 8.0f;

        float domW = HudPanelRenderer.measureHud(fontKey, dom[0] + dom[1] + dom[2], textSize);
        float pingValW = HudPanelRenderer.measureHud(fontKey, pingVal, textSize);
        float pingSubW = HudPanelRenderer.measureHud(fontKey, "MS", subSize);
        float w2 = 7.5f + iconS + 4.0f + domW + sepGap + 1.5f + sepGap
                + iconS + 4.0f + pingValW + 2.0f + pingSubW + 7.5f;

        float rowW = h + logoGap + w1;
        float totalH = h + rowGap + h;
        dragWidget.setBounds(rowW, totalH);

        float x = dragWidget.getX();
        float rawY = dragWidget.getY();
        float targetShift = dragWidget.isDragging() ? 0.0f : computeBossBarShift(x, rowW, rawY);
        wmBossShift += (targetShift - wmBossShift) * 0.16f;
        if (wmBossShift < 0.05f) wmBossShift = 0.0f;
        float y = rawY + wmBossShift;

        float x2 = x + (rowW - w2) * 0.5f;
        float y2 = y + h + rowGap;

        HudPanelRenderer.drawSurface(context, "Watermark", x, y, h, h, 5.5f, 232, 42);
        HudPanelRenderer.drawSurface(context, "Watermark", x + h + logoGap, y, w1, h, 5.5f, 232, 34);
        HudPanelRenderer.drawSurface(context, "Watermark", x2, y2, w2, h, 5.5f, 232, 34);

        float cy1 = y + h * 0.5f;
        HudPanelRenderer.drawGlowIcon(context, TextureAssets.DESIRE_HUD_BOLT, x + (h - 10.5f) * 0.5f, cy1, 10.5f, c1, 1.0f);

        float cx = x + h + logoGap + 8.0f;
        HudPanelRenderer.drawGlowIcon(context, TextureAssets.DESIRE_HUD_USER, cx, cy1, iconS, c1, 1.0f);
        cx += iconS + 4.5f;
        HudPanelRenderer.drawHudText(context, fontKey, username, cx, cy1 - textSize * 0.5f, textSize, 0xFFD0D4DC);
        cx += nameW + sepGap;
        Render2DHelper.drawRoundedRectFloat(context, cx, cy1 - 2.5f, 1.5f, 5.0f, 0.75f, 0xFF282A30);
        cx += 1.5f + sepGap;

        HudPanelRenderer.drawGlowIcon(context, TextureAssets.DESIRE_HUD_GAUGE, cx, cy1, iconS, c1, 1.0f);
        cx += iconS + 4.0f;
        HudPanelRenderer.drawHudText(context, fontKey, fpsVal, cx, cy1 - textSize * 0.5f, textSize, 0xFFD0D4DC);
        cx += fpsValW + 2.0f;
        HudPanelRenderer.drawHudText(context, fontKey, "FPS", cx, cy1 - subSize * 0.35f, subSize, 0xFF6B7080);
        cx += fpsSubW + sepGap;
        Render2DHelper.drawRoundedRectFloat(context, cx, cy1 - 2.5f, 1.5f, 5.0f, 0.75f, 0xFF282A30);
        cx += 1.5f + sepGap;

        HudPanelRenderer.drawGlowIcon(context, TextureAssets.DESIRE_HUD_CLOCK, cx, cy1, iconS, c1, 1.0f);
        cx += iconS + 4.0f;
        HudPanelRenderer.drawHudText(context, fontKey, hourStr, cx, cy1 - textSize * 0.5f, textSize, 0xFFD0D4DC);
        cx += hourW;
        HudPanelRenderer.drawHudText(context, fontKey, ":", cx, cy1 - textSize * 0.5f, textSize, c1);
        cx += colonW;
        HudPanelRenderer.drawHudText(context, fontKey, minStr, cx, cy1 - textSize * 0.5f, textSize, 0xFFD0D4DC);

        float cy2 = y2 + h * 0.5f;
        cx = x2 + 7.5f;
        HudPanelRenderer.drawGlowIcon(context, TextureAssets.DESIRE_HUD_HOME, cx, cy2, iconS, c1, 1.0f);
        cx += iconS + 4.0f;
        if (!dom[0].isEmpty()) {
            HudPanelRenderer.drawHudText(context, fontKey, dom[0], cx, cy2 - textSize * 0.5f, textSize, c1);
            cx += HudPanelRenderer.measureHud(fontKey, dom[0], textSize);
        }
        HudPanelRenderer.drawHudText(context, fontKey, dom[1], cx, cy2 - textSize * 0.5f, textSize, 0xFFD0D4DC);
        cx += HudPanelRenderer.measureHud(fontKey, dom[1], textSize);
        if (!dom[2].isEmpty()) {
            HudPanelRenderer.drawHudText(context, fontKey, dom[2], cx, cy2 - textSize * 0.5f, textSize, c2);
        }
        cx = x2 + 7.5f + iconS + 4.0f + domW + sepGap;
        Render2DHelper.drawRoundedRectFloat(context, cx, cy2 - 2.5f, 1.5f, 5.0f, 0.75f, 0xFF282A30);
        cx += 1.5f + sepGap;

        HudPanelRenderer.drawGlowIcon(context, TextureAssets.DESIRE_HUD_GLOBE, cx, cy2, iconS, c1, 1.0f);
        cx += iconS + 4.0f;
        HudPanelRenderer.drawHudText(context, fontKey, pingVal, cx, cy2 - textSize * 0.5f, textSize, 0xFFD0D4DC);
        cx += pingValW + 2.0f;
        HudPanelRenderer.drawHudText(context, fontKey, "MS", cx, cy2 - subSize * 0.35f, subSize, 0xFF6B7080);
    }

    private float computeBossBarShift(float x, float width, float y) {
        NoRenderModule noRender = NoRenderModule.getInstance();
        if ((noRender != null && noRender.isBossBar()) || mc.gui == null || mc.gui.hud == null) return 0.0f;
        Object overlay = mc.gui.hud.getBossOverlay();
        if (!(overlay instanceof BossHealthOverlayAccessor acc)) return 0.0f;
        var events = acc.opticume$getEvents();
        if (events == null || events.isEmpty()) return 0.0f;

        float screenWidth = mc.getWindow().getGuiScaledWidth();
        float screenHeight = mc.getWindow().getGuiScaledHeight();
        if (x + width < screenWidth * 0.5f - 91.0f || x > screenWidth * 0.5f + 91.0f) return 0.0f;

        int barY = 12;
        float bottom = 0.0f;
        for (int i = 0; i < events.size(); i++) {
            bottom = barY + 5.0f;
            barY += 19;
            if (barY >= screenHeight / 3.0f) break;
        }
        return Math.max(0.0f, bottom + 4.0f - y);
    }

    private static String[] splitDomainParts(String host) {
        int colon = host.lastIndexOf(':');
        if (colon > 0 && host.indexOf(':') == colon) host = host.substring(0, colon);
        String[] parts = host.split("\\.");
        int n = parts.length;
        if (n >= 3) {
            return new String[]{parts[0] + ".", String.join(".", Arrays.copyOfRange(parts, 1, n - 1)), "." + parts[n - 1]};
        }
        if (n == 2) return new String[]{"", parts[0], "." + parts[1]};
        return new String[]{"", host, ""};
    }

    private static void drawDivider(GuiGraphicsExtractor context, float x, float y, float h, int color) {
        Render2DHelper.drawRect(context, x, y, 1.0f, h, ColorUtils.withAlpha(color, 90));
    }
}
