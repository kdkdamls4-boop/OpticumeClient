package dev.opticumeclient.client.modules.visuals;

import dev.opticumeclient.client.command.CommandDataStore;
import dev.opticumeclient.client.friend.FriendManager;
import dev.opticumeclient.client.hud.HudManager;
import dev.opticumeclient.client.hud.HudPanelRenderer;
import dev.opticumeclient.client.modules.Category;
import dev.opticumeclient.client.modules.Module;
import dev.opticumeclient.client.modules.other.NameProtectModule;
import dev.opticumeclient.client.render.ColorUtils;
import dev.opticumeclient.client.render.Render2DHelper;
import dev.opticumeclient.client.render.ThemeManager;
import dev.opticumeclient.client.setting.BooleanSetting;
import dev.opticumeclient.client.setting.NumberSetting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.GameType;

import java.util.ArrayList;
import java.util.List;

/**
 * Custom translucent Liquid Glass Tab-list with numeric ping (ms), friend/staff highlighting,
 * NameProtect compatibility, movable/scalable HUD widget integration, smooth open/close animation,
 * and configurable player cap beyond vanilla's 80 limit.
 */
public class BetterTabModule extends Module {
    private static BetterTabModule instance;

    private final BooleanSetting customOverlay = new BooleanSetting("Liquid Glass Tab", "Кастомный полупрозрачный Tab-лист Opticume", true);
    private final BooleanSetting unlimitedPlayers = new BooleanSetting("Unlimited Players", "Снять лимит в 80 игроков в Tab", true);
    private final NumberSetting maxPlayers = new NumberSetting("Max Players", "Максимум отображаемых игроков", 200.0, 80.0, 400.0, 10.0)
            .visibleWhen(unlimitedPlayers::isEnabled);
    private final BooleanSetting numericPing = new BooleanSetting("Numeric Ping", "Отображать пинг в миллисекундах", true);
    private final BooleanSetting highlightFriends = new BooleanSetting("Highlight Friends", "Подсвечивать друзей и себя", true);
    private final BooleanSetting highlightStaff = new BooleanSetting("Highlight Staff", "Подсвечивать персонал и спектаторов", true);
    private final NumberSetting scale = new NumberSetting("Scale", "Масштаб кастомного Tab-листа", 1.0, 0.7, 1.3, 0.05)
            .visibleWhen(customOverlay::isEnabled);
    private final BooleanSetting autoCenter = new BooleanSetting("Auto Center", "Всегда центрировать Tab-лист по горизонтали экрана", true)
            .visibleWhen(customOverlay::isEnabled);
    private final NumberSetting posX = new NumberSetting("PosX", "Центр X (-1 = авто-центр)", -1.0, -1.0, 1920.0, 1.0)
            .visibleWhen(customOverlay::isEnabled);
    private final NumberSetting posY = new NumberSetting("PosY", "Y позиция", 48.0, 0.0, 1080.0, 1.0)
            .visibleWhen(customOverlay::isEnabled);

    private final HudManager.DraggableWidget dragWidget;
    private final List<PlayerInfo> cachedPlayers = new ArrayList<>();
    private Component cachedHeader;
    private Component cachedFooter;
    private boolean tabCapturedThisFrame = false;
    private float openAnim = 0.0f;
    private long lastAnimNs = 0L;

    public BetterTabModule() {
        super("BetterTab", "Прозрачный Tab-лист с пингом в мс, подсветкой друзей/персонала и снятием лимита", Category.VISUALS);
        instance = this;
        addSetting(customOverlay);
        addSetting(unlimitedPlayers);
        addSetting(maxPlayers);
        addSetting(numericPing);
        addSetting(highlightFriends);
        addSetting(highlightStaff);
        addSetting(scale);
        addSetting(autoCenter);
        addSetting(posX);
        addSetting(posY);

        this.dragWidget = HudManager.getInstance().registerWidget(new HudManager.DraggableWidget(
                "TabList",
                360.0f,
                posY.getFloatValue(),
                156.0f,
                72.0f,
                BetterTabModule::isCustomTabActive,
                this::onWidgetMoved
        ));
    }

    private void onWidgetMoved(float newX, float newY) {
        posY.setValue((double) Math.round(newY));
        if (dragWidget != null && dragWidget.isDragging()) {
            float screenW = (mc != null && mc.getWindow() != null) ? mc.getWindow().getGuiScaledWidth() : 960.0f;
            float newCenterX = newX + dragWidget.getScaledWidth() * 0.5f;
            if (Math.abs(newCenterX - screenW * 0.5f) <= 14.0f) {
                autoCenter.setValue(true);
                posX.setValue(-1.0);
            } else {
                autoCenter.setValue(false);
                posX.setValue((double) Math.round(newCenterX));
            }
        }
    }

    @Override
    public void onDisable() {
        openAnim = 0.0f;
        tabCapturedThisFrame = false;
        cachedPlayers.clear();
        cachedHeader = null;
        cachedFooter = null;
    }

    public static BetterTabModule getInstance() {
        return instance;
    }

    public static boolean isCustomTabActive() {
        return instance != null && instance.isEnabled() && instance.customOverlay.isEnabled();
    }

    public static long getEffectiveMaxPlayers(long vanillaDefault) {
        if (instance != null && instance.isEnabled() && instance.unlimitedPlayers.isEnabled()) {
            return Math.max(vanillaDefault, instance.maxPlayers.getIntValue());
        }
        return vanillaDefault;
    }

    public static boolean isNumericPingEnabled() {
        return instance != null && instance.isEnabled() && instance.numericPing.isEnabled();
    }

    public void captureTabFrame(List<PlayerInfo> players, Component header, Component footer) {
        cachedPlayers.clear();
        if (players != null && !players.isEmpty()) {
            cachedPlayers.addAll(players);
        }
        cachedHeader = header;
        cachedFooter = footer;
        tabCapturedThisFrame = true;
    }

    @Override
    public void onRender2D(GuiGraphicsExtractor context, float tickDelta) {
        if (!isCustomTabActive() || mc == null || mc.player == null || context == null) {
            tabCapturedThisFrame = false;
            return;
        }

        boolean tabActive = tabCapturedThisFrame;
        tabCapturedThisFrame = false;

        boolean inChat = mc.gui != null && mc.gui.screen() instanceof ChatScreen;
        float target = (tabActive || inChat) ? 1.0f : 0.0f;

        long now = System.nanoTime();
        float dt = lastAnimNs == 0L ? 0.016f : Math.max(0.0001f, Math.min(0.05f, (now - lastAnimNs) / 1_000_000_000.0f));
        lastAnimNs = now;

        float speed = target > openAnim ? 16.0f : 14.0f;
        openAnim += (target - openAnim) * (1.0f - (float) Math.exp(-speed * dt));
        if (target == 0.0f && openAnim < 0.01f) {
            openAnim = 0.0f;
            return;
        }
        if (target == 1.0f && openAnim > 0.996f) {
            openAnim = 1.0f;
        }
        if (openAnim < 0.03f) {
            return;
        }

        float inv = 1.0f - openAnim;
        float ease = 1.0f - inv * inv * inv;

        List<PlayerInfo> displayList = resolveDisplayPlayers(inChat && !tabActive);
        int screenWidth = mc.getWindow() != null ? mc.getWindow().getGuiScaledWidth() : 960;
        int screenHeight = mc.getWindow() != null ? mc.getWindow().getGuiScaledHeight() : 540;

        float baseScale = scale.getFloatValue();
        float animZoom = inChat ? 1.0f : (0.90f + 0.10f * ease);

        int total = displayList.size();
        boolean isSyntheticPreview = total == 0 && inChat;
        int effectiveCount = isSyntheticPreview ? 3 : total;
        if (effectiveCount <= 0) return;

        float widgetScale = Math.max(0.5f, dragWidget.getScale());
        String headerStr = (!isSyntheticPreview && cachedHeader != null) ? NameProtectModule.protectHudName(cachedHeader.getString()).trim() : "";
        String footerStr = (!isSyntheticPreview && cachedFooter != null) ? NameProtectModule.protectHudName(cachedFooter.getString()).trim() : "";
        boolean hasHeader = !headerStr.isEmpty();
        boolean hasFooter = !footerStr.isEmpty();

        float availW = Math.max(140.0f, (screenWidth - 12.0f) / widgetScale);
        float availH = Math.max(80.0f, (screenHeight - 12.0f) / widgetScale);
        TabLayout layout = computeViewportLayout(effectiveCount, baseScale, availW, availH, hasHeader, hasFooter);
        float drawScale = layout.effectiveScale;

        int cols = layout.cols;
        int rows = layout.rows;
        int pageCapacity = Math.max(1, cols * rows);
        int pageCount = Math.max(1, (effectiveCount + pageCapacity - 1) / pageCapacity);
        int pageIdx = pageCount > 1 ? (int) ((System.currentTimeMillis() / 4000L) % pageCount) : 0;
        int startIdx = pageIdx * pageCapacity;
        int pageItems = Math.min(pageCapacity, effectiveCount - startIdx);

        float colW = 148.0f * drawScale;
        float rowH = 12.5f * drawScale;
        float pad = 7.0f * drawScale;
        float gapX = 6.0f * drawScale;
        float headSize = 8.5f * drawScale;
        float textScale = 0.72f * drawScale;

        String effectiveFooter = footerStr;
        if (pageCount > 1) {
            String pageTag = "Page " + (pageIdx + 1) + "/" + pageCount + " (" + effectiveCount + ")";
            effectiveFooter = effectiveFooter.isEmpty() ? pageTag : (firstNonEmptyLine(effectiveFooter) + " · " + pageTag);
            hasFooter = true;
        }
        float headerH = hasHeader ? 14.0f * drawScale : 0.0f;
        float footerH = hasFooter ? 14.0f * drawScale : 0.0f;

        float panelW = pad * 2.0f + cols * colW + (cols - 1) * gapX;
        float panelH = pad * 2.0f + headerH + rows * rowH + footerH;
        float scaledW = panelW * widgetScale;
        float scaledH = panelH * widgetScale;

        dragWidget.setBounds(panelW, panelH);
        if (!dragWidget.isDragging()) {
            if (autoCenter.isEnabled() || posX.getFloatValue() < 0.0f) {
                float centeredX = Math.max(4.0f, (screenWidth - scaledW) * 0.5f);
                dragWidget.syncExternalPosition(centeredX, posY.getFloatValue());
            } else {
                float anchoredX = posX.getFloatValue() - scaledW * 0.5f;
                dragWidget.syncExternalPosition(anchoredX, posY.getFloatValue());
            }
        }

        float px = Math.max(4.0f, Math.min(Math.max(4.0f, screenWidth - scaledW - 4.0f), dragWidget.getX()));
        float py = Math.max(4.0f, Math.min(Math.max(4.0f, screenHeight - scaledH - 4.0f), dragWidget.getY()));
        if (!dragWidget.isDragging()) {
            dragWidget.syncExternalPosition(px, py);
        }

        int accent = ThemeManager.getInstance().getAccentColor(0);
        int alphaInt = Math.max(8, Math.min(255, Math.round(255.0f * ease)));
        float renderAlpha = alphaInt / 255.0f;

        float totalScale = widgetScale * animZoom;
        float pivotX = px + scaledW * 0.5f;
        float pivotY = py + scaledH * 0.5f;
        var pose = context.pose();
        pose.pushMatrix();
        pose.translate(pivotX, pivotY);
        pose.scale(totalScale, totalScale);
        pose.translate(-(px + panelW * 0.5f), -(py + panelH * 0.5f));

        HudPanelRenderer.beginWidget("TabList");
        try {
            HudPanelRenderer.drawSurface(
                    context,
                    "TabList",
                    px,
                    py,
                    panelW,
                    panelH,
                    8.0f * drawScale,
                    Math.round(225.0f * renderAlpha),
                    Math.round(44.0f * renderAlpha)
            );

            float curY = py + pad;
            if (headerH > 0.0f) {
                String line = firstNonEmptyLine(headerStr);
                String trimmed = Render2DHelper.trimToWidth(line, textScale, panelW - pad * 2.0f);
                float tw = Render2DHelper.getTextWidth(trimmed, textScale);
                Render2DHelper.drawScaledText(
                        context,
                        trimmed,
                        px + (panelW - tw) * 0.5f,
                        curY + 1.5f * drawScale,
                        ColorUtils.withAlphaFactor(0xFFE8ECF7, renderAlpha),
                        textScale,
                        true
                );
                curY += headerH;
            }

            if (isSyntheticPreview) {
                renderPreviewRows(context, px + pad, curY, colW, rowH, headSize, textScale, drawScale, accent, renderAlpha);
                return;
            }

            String selfName = mc.player.getGameProfile().name();
            for (int local = 0; local < pageItems; local++) {
                PlayerInfo info = displayList.get(startIdx + local);
                int colIdx = local / rows;
                int rowIdx = local % rows;
                if (colIdx >= cols) break;

                float rx = px + pad + colIdx * (colW + gapX);
                float ry = curY + rowIdx * rowH;

                String profileName = info.getProfile().name();
                boolean isSelf = !selfName.isEmpty() && selfName.equalsIgnoreCase(profileName);
                boolean isFriend = highlightFriends.isEnabled() && FriendManager.getInstance().isFriend(profileName);
                boolean isSpec = info.getGameMode() == GameType.SPECTATOR;
                boolean isStaff = highlightStaff.isEnabled()
                        && (isSpec || CommandDataStore.getInstance().isCustomStaff(profileName));

                int baseRowBg = isStaff ? 0x36FF5252
                        : (isFriend ? 0x344ADE80
                        : (isSelf ? ColorUtils.withAlpha(accent, 48) : 0x1AFFFFFF));
                Render2DHelper.drawRoundedRectFloat(
                        context,
                        rx,
                        ry,
                        colW,
                        rowH - 1.2f * drawScale,
                        3.0f * drawScale,
                        ColorUtils.withAlphaFactor(baseRowBg, renderAlpha)
                );

                try {
                    int headX = Math.round(rx + 2.5f * drawScale);
                    int headY = Math.round(ry + (rowH - 1.2f * drawScale - headSize) * 0.5f);
                    PlayerFaceExtractor.extractRenderState(
                            context,
                            info.getSkin(),
                            headX,
                            headY,
                            Math.max(6, Math.round(headSize)),
                            ColorUtils.withAlpha(0xFFFFFFFF, alphaInt)
                    );
                } catch (Throwable ignored) {
                }

                String pingText = numericPing.isEnabled() ? (Math.max(0, info.getLatency()) + "ms") : "";
                float pingW = pingText.isEmpty() ? 0.0f : Render2DHelper.getTextWidth(pingText, 0.65f * drawScale) + 4.0f * drawScale;

                Component rawComp = info.getTabListDisplayName() != null
                        ? info.getTabListDisplayName()
                        : Component.literal(profileName);
                String display = rawComp.getString();
                if (NameProtectModule.shouldMaskTab()) {
                    display = NameProtectModule.protect(display);
                }
                if (isSpec) display = "§c[S] §7" + display;
                else if (isStaff) display = "§b[STAFF] §f" + display;
                else if (isFriend) display = "§a[F] §f" + display;

                float maxNameW = Math.max(24.0f, colW - headSize - pingW - 10.0f * drawScale);
                String trimmedName = Render2DHelper.trimToWidth(display, textScale, maxNameW);
                int nameColor = isSpec ? 0xFFAAAAAA : (isFriend ? 0xFF86EFAC : (isSelf ? accent : 0xFFF1F5F9));
                Render2DHelper.drawScaledText(
                        context,
                        trimmedName,
                        rx + headSize + 5.0f * drawScale,
                        ry + 2.2f * drawScale,
                        ColorUtils.withAlphaFactor(nameColor, renderAlpha),
                        textScale,
                        false
                );

                if (!pingText.isEmpty()) {
                    int lat = info.getLatency();
                    int pingCol = lat < 80 ? 0xFF4ADE80 : (lat < 180 ? 0xFFFBBF24 : 0xFFFF5252);
                    Render2DHelper.drawScaledText(
                            context,
                            pingText,
                            rx + colW - pingW + 1.0f * drawScale,
                            ry + 2.6f * drawScale,
                            ColorUtils.withAlphaFactor(pingCol, renderAlpha),
                            0.65f * drawScale,
                            true
                    );
                }
            }

            if (footerH > 0.0f) {
                String line = firstNonEmptyLine(effectiveFooter);
                String trimmed = Render2DHelper.trimToWidth(line, textScale, panelW - pad * 2.0f);
                float tw = Render2DHelper.getTextWidth(trimmed, textScale);
                Render2DHelper.drawScaledText(
                        context,
                        trimmed,
                        px + (panelW - tw) * 0.5f,
                        curY + rows * rowH + 3.0f * drawScale,
                        ColorUtils.withAlphaFactor(0xFF94A3B8, renderAlpha),
                        textScale,
                        false
                );
            }
        } finally {
            HudPanelRenderer.endWidget();
            pose.popMatrix();
        }
    }

    private record TabLayout(int cols, int rows, float effectiveScale) {}

    private static TabLayout computeViewportLayout(int count, float baseScale, float availW, float availH,
                                                   boolean hasHeader, boolean hasFooter) {
        float[] densities = {1.0f, 0.88f, 0.76f, 0.66f, 0.56f};
        for (float d : densities) {
            float s = baseScale * d;
            float colW = 148.0f * s;
            float rowH = 12.5f * s;
            float pad = 7.0f * s;
            float gapX = 6.0f * s;
            float headerH = hasHeader ? 14.0f * s : 0.0f;
            float footerH = hasFooter ? 14.0f * s : 0.0f;

            int maxCols = Math.max(1, (int) Math.floor((availW - pad * 2.0f + gapX) / (colW + gapX)));
            int maxRows = Math.max(4, (int) Math.floor((availH - pad * 2.0f - headerH - footerH) / rowH));
            if (maxCols * maxRows >= count) {
                int cols = Math.max(1, Math.min(maxCols, (count + maxRows - 1) / maxRows));
                int idealCols = Math.max(1, Math.min(maxCols, (count + 17) / 18));
                cols = Math.max(cols, Math.min(maxCols, idealCols));
                int rows = Math.max(1, Math.min(maxRows, (count + cols - 1) / cols));
                return new TabLayout(cols, rows, s);
            }
        }
        float s = baseScale * densities[densities.length - 1];
        float colW = 148.0f * s;
        float rowH = 12.5f * s;
        float pad = 7.0f * s;
        float gapX = 6.0f * s;
        float headerH = hasHeader ? 14.0f * s : 0.0f;
        float footerH = 14.0f * s;
        int maxCols = Math.max(1, (int) Math.floor((availW - pad * 2.0f + gapX) / (colW + gapX)));
        int maxRows = Math.max(4, (int) Math.floor((availH - pad * 2.0f - headerH - footerH) / rowH));
        return new TabLayout(maxCols, maxRows, s);
    }

    private List<PlayerInfo> resolveDisplayPlayers(boolean previewMode) {
        if (!cachedPlayers.isEmpty()) {
            if (previewMode && cachedPlayers.size() > 10) {
                return cachedPlayers.subList(0, 10);
            }
            return cachedPlayers;
        }
        if (previewMode && mc.getConnection() != null && !mc.getConnection().getListedOnlinePlayers().isEmpty()) {
            List<PlayerInfo> listed = new ArrayList<>(mc.getConnection().getListedOnlinePlayers());
            return listed.size() > 10 ? listed.subList(0, 10) : listed;
        }
        return cachedPlayers;
    }

    private void renderPreviewRows(GuiGraphicsExtractor context, float rx, float startY, float colW, float rowH,
                                   float headSize, float textScale, float animScale, int accent, float alpha) {
        String selfName = NameProtectModule.protectHudName(mc.player.getGameProfile().name());
        String[] labels = {
                selfName + " (TabList)",
                "§a[F] §fFriendPreview",
                "§b[STAFF] §fStaffPreview"
        };
        int[] bgs = {
                ColorUtils.withAlpha(accent, 48),
                0x344ADE80,
                0x36FF5252
        };
        int[] cols = {accent, 0xFF86EFAC, 0xFFF1F5F9};
        String[] pings = {"18ms", "32ms", "54ms"};

        for (int i = 0; i < labels.length; i++) {
            float ry = startY + i * rowH;
            Render2DHelper.drawRoundedRectFloat(context, rx, ry, colW, rowH - 1.2f * animScale, 3.0f * animScale,
                    ColorUtils.withAlphaFactor(bgs[i], alpha));
            if (i == 0 && mc.getConnection() != null) {
                PlayerInfo selfInfo = mc.getConnection().getPlayerInfo(mc.player.getUUID());
                if (selfInfo != null) {
                    try {
                        int headX = Math.round(rx + 2.5f * animScale);
                        int headY = Math.round(ry + (rowH - 1.2f * animScale - headSize) * 0.5f);
                        PlayerFaceExtractor.extractRenderState(context, selfInfo.getSkin(), headX, headY,
                                Math.max(6, Math.round(headSize)), ColorUtils.withAlpha(0xFFFFFFFF, Math.round(255.0f * alpha)));
                    } catch (Throwable ignored) {
                    }
                }
            }
            float pingW = Render2DHelper.getTextWidth(pings[i], 0.65f * animScale) + 4.0f * animScale;
            String trimmed = Render2DHelper.trimToWidth(labels[i], textScale, Math.max(24.0f, colW - headSize - pingW - 10.0f * animScale));
            Render2DHelper.drawScaledText(context, trimmed, rx + headSize + 5.0f * animScale, ry + 2.2f * animScale,
                    ColorUtils.withAlphaFactor(cols[i], alpha), textScale, false);
            Render2DHelper.drawScaledText(context, pings[i], rx + colW - pingW + 1.0f * animScale, ry + 2.6f * animScale,
                    ColorUtils.withAlphaFactor(0xFF4ADE80, alpha), 0.65f * animScale, true);
        }
    }

    private static String firstNonEmptyLine(String s) {
        for (String part : s.split("\n")) {
            String t = part.trim();
            if (!t.isEmpty()) return t;
        }
        return s.replace('\n', ' ').trim();
    }
}
