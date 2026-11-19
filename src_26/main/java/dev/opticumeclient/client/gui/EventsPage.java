package dev.opticumeclient.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.opticumeclient.client.gps.GpsManager;
import dev.opticumeclient.client.render.ColorUtils;
import dev.opticumeclient.client.render.Render2DHelper;
import dev.opticumeclient.client.render.TextureAssets;
import dev.opticumeclient.client.render.ThemeManager;
import dev.opticumeclient.client.services.AnarchyReconnectService;
import dev.opticumeclient.client.services.AnarchyReconnectService.Target;
import dev.opticumeclient.client.services.ServerEventsService;
import dev.opticumeclient.client.services.ServerEventsService.Card;
import dev.opticumeclient.client.services.ServerEventsService.Source;
import dev.opticumeclient.client.services.ServerEventsService.Status;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;
import org.joml.Vector3i;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Live FunTime &amp; HolyWorld Server Events page ported from Desire's {@code EventsPage}.
 * Clicking any event card sets a GPS waypoint (when coordinates exist) and triggers {@code .rct}.
 */
public final class EventsPage {
    private static final Source[] SOURCES = {Source.FT, Source.HW};

    private final ServerEventsService service = ServerEventsService.getInstance();
    private final List<Card> shown = new ArrayList<>();
    private final Map<String, float[]> cardAnims = new HashMap<>();

    private float scroll = 0.0f;
    private float targetScroll = 0.0f;
    private float maxScroll = 0.0f;
    private float sourceAnim = 0.0f;
    private String filter = "";
    private boolean filterFocused = false;
    private long feedbackAt = 0L;
    private String feedbackText = "";

    public void unfocus() {
        filterFocused = false;
    }

    public boolean isTyping() {
        return filterFocused;
    }

    public void resetScroll() {
        scroll = 0.0f;
        targetScroll = 0.0f;
    }

    public void render(GuiGraphicsExtractor context, float dt, float x, float y, float w, float h,
                       int mouseX, int mouseY, float alpha) {
        service.tick();

        float pad = 10.0f;
        float ix = x + pad;
        float iy = y + pad;
        float iw = w - pad * 2.0f;
        float ih = h - pad * 2.0f;

        shown.clear();
        String q = filter.trim().toLowerCase(Locale.ROOT);
        for (Card c : service.getCards()) {
            if (q.isEmpty() || c.title().toLowerCase(Locale.ROOT).contains(q)
                    || c.server().toLowerCase(Locale.ROOT).contains(q)) {
                shown.add(c);
            }
        }

        float headerH = 22.0f;
        renderHeader(context, dt, ix, iy, iw, headerH, mouseX, mouseY, alpha);

        float gridY = iy + headerH + 9.0f;
        float gridH = Math.max(20.0f, ih - headerH - 9.0f);
        if (shown.isEmpty()) {
            renderEmpty(context, ix, gridY, iw, gridH, alpha);
        } else {
            renderGrid(context, dt, ix, gridY, iw, gridH, mouseX, mouseY, alpha);
        }
    }

    private void renderHeader(GuiGraphicsExtractor context, float dt, float x, float y, float w, float h,
                              int mouseX, int mouseY, float alpha) {
        float segW = 36.0f;
        float switchW = segW * 2.0f + 4.0f;
        int activeIdx = service.getSource().ordinal();
        sourceAnim += (activeIdx - sourceAnim) * (1.0f - (float) Math.exp(-16.0f * dt));

        Render2DHelper.drawRoundedRectFloat(context, x, y, switchW, h, 5.5f,
                SystemTheme.fade(SystemTheme.bg(), alpha));
        Render2DHelper.drawRoundedOutlineFloat(context, x, y, switchW, h, 5.5f, 1.0f,
                SystemTheme.fade(SystemTheme.border(), alpha));

        int accent = ThemeManager.getInstance().getAccentColor(0);
        Render2DHelper.drawRoundedRectFloat(context, x + 2.0f + segW * sourceAnim, y + 2.0f, segW, h - 4.0f, 4.0f,
                ColorUtils.withAlphaFactor(accent, alpha));

        for (int i = 0; i < SOURCES.length; i++) {
            float sx = x + 2.0f + segW * i;
            float on = Math.max(0.0f, 1.0f - Math.abs(sourceAnim - i));
            int col = ColorUtils.interpolateColor(SystemTheme.mutedFg(), SystemTheme.onPrimary(), on);
            Render2DHelper.drawCenteredText(context, SOURCES[i].name(), sx + segW * 0.5f, y + 7.0f,
                    SystemTheme.fade(col, alpha), false);
        }

        // Mini filter search pill
        float filterW = Math.min(125.0f, Math.max(76.0f, w * 0.24f));
        float filterX = x + switchW + 8.0f;
        Render2DHelper.drawRoundedRectFloat(context, filterX, y, filterW, h, 5.5f,
                SystemTheme.fade(SystemTheme.bg(), alpha));
        Render2DHelper.drawRoundedOutlineFloat(context, filterX, y, filterW, h, 5.5f, 1.0f,
                SystemTheme.fade(filterFocused ? accent : SystemTheme.border(), alpha));
        Render2DHelper.drawTextureFloat(context, TextureAssets.DESIRE_GUI_SEARCH, filterX + 6.0f, y + 6.5f, 9.0f, 9.0f,
                SystemTheme.fade(filterFocused ? SystemTheme.foreground() : SystemTheme.mutedFg(), alpha));
        String fShown = filter.isEmpty() && !filterFocused ? "Filter..." : filter;
        String trimF = Render2DHelper.trimToWidth(fShown, 0.72f, filterW - 22.0f);
        Render2DHelper.drawScaledText(context, trimF, filterX + 18.0f, y + 7.5f,
                SystemTheme.fade(filter.isEmpty() && !filterFocused ? SystemTheme.mutedFg() : SystemTheme.foreground(), alpha),
                0.72f, false);

        // Live event count & status on right
        long now = System.currentTimeMillis();
        String status;
        int dotCol;
        if (now - feedbackAt < 1800L) {
            status = feedbackText;
            dotCol = 0xFF60A5FA;
        } else if (service.getStatus() == Status.LOADING) {
            status = "Loading...";
            dotCol = 0xFFFACC15;
        } else if (service.getStatus() == Status.ERROR) {
            status = "Offline";
            dotCol = 0xFFF87171;
        } else {
            long ago = Math.max(0L, (now - service.getUpdatedAt()) / 1000L);
            status = shown.size() + " events · " + (ago < 2 ? "just now" : ago + "s ago");
            dotCol = 0xFF4ADE80;
        }

        float stW = Render2DHelper.getTextWidth(status, 0.72f);
        float rightX = x + w;
        if (filterX + filterW + stW + 20.0f < rightX) {
            Render2DHelper.drawScaledText(context, status, rightX - stW, y + 7.5f,
                    SystemTheme.fade(SystemTheme.mutedFg(), alpha), 0.72f, false);
            float pulse = pulse(1400L);
            float dx = rightX - stW - 9.0f;
            float cy = y + h * 0.5f;
            Render2DHelper.drawCircle(context, dx, cy, 3.0f + 2.5f * pulse,
                    ColorUtils.withAlpha(dotCol, Math.round(95.0f * (1.0f - pulse) * alpha)));
            Render2DHelper.drawCircle(context, dx, cy, 2.5f, ColorUtils.withAlphaFactor(dotCol, alpha));
        }
    }

    private void renderGrid(GuiGraphicsExtractor context, float dt, float gx, float gy, float gw, float gh,
                            int mouseX, int mouseY, float alpha) {
        int cols = gw >= 380.0f ? 3 : 2;
        float gap = 7.0f;
        float availW = gw - 6.0f;
        float cw = (availW - gap * (cols - 1)) / cols;
        float ch = 66.0f;

        int rows = (shown.size() + cols - 1) / cols;
        float contentH = rows * (ch + gap) - gap;
        maxScroll = Math.max(0.0f, contentH - gh);
        targetScroll = Mth.clamp(targetScroll, 0.0f, maxScroll);
        scroll += (targetScroll - scroll) * (1.0f - (float) Math.exp(-16.0f * dt));

        context.enableScissor(Math.round(gx - 2), Math.round(gy - 2), Math.round(gx + availW + 2), Math.round(gy + gh + 2));
        boolean live = isInside(mouseX, mouseY, gx, gy, availW, gh);
        Set<String> alive = new HashSet<>();

        for (int i = 0; i < shown.size(); i++) {
            Card c = shown.get(i);
            alive.add(c.key());
            float cx = gx + (i % cols) * (cw + gap);
            float cy = gy + (i / cols) * (ch + gap) - scroll;
            float[] anim = cardAnims.computeIfAbsent(c.key(), k -> new float[2]);
            boolean hov = live && isInside(mouseX, mouseY, cx, cy, cw, ch);
            anim[0] += ((hov ? 1.0f : 0.0f) - anim[0]) * (1.0f - (float) Math.exp(-16.0f * dt));
            anim[1] += (0.0f - anim[1]) * (1.0f - (float) Math.exp(-5.0f * dt));
            if (cy + ch < gy || cy > gy + gh) continue;
            renderCard(context, c, cx, cy, cw, ch, anim[0], anim[1], alpha);
        }
        cardAnims.keySet().retainAll(alive);
        context.disableScissor();

        if (maxScroll > 1.0f) {
            float thumbH = Math.max(22.0f, gh * (gh / contentH));
            float thumbY = gy + (gh - thumbH) * (scroll / maxScroll);
            Render2DHelper.drawRoundedRectFloat(context, gx + gw - 3.0f, thumbY, 2.2f, thumbH, 1.1f,
                    SystemTheme.fade(SystemTheme.border(), alpha));
        }
    }

    private void renderCard(GuiGraphicsExtractor context, Card card, float cx, float cy, float cw, float ch,
                            float hover, float flash, float alpha) {
        float rad = 7.5f;
        int from = ColorUtils.interpolateColor(card.fromColor(), 0xFF000000, 0.30f);
        int to = ColorUtils.interpolateColor(card.toColor(), 0xFF000000, 0.58f);
        int mid = ColorUtils.interpolateColor(from, to, 0.5f);

        Render2DHelper.drawGradientRoundedRectFloat(context, cx, cy, cw, ch, rad,
                ColorUtils.withAlphaFactor(from, alpha),
                ColorUtils.withAlphaFactor(mid, alpha),
                ColorUtils.withAlphaFactor(to, alpha),
                ColorUtils.withAlphaFactor(mid, alpha));

        if (card.banner() != null) {
            float aspect = cw / ch;
            float zoom = 1.0f - 0.05f * hover;
            float uw = (aspect < 3.0f ? aspect / 3.0f : 1.0f) * zoom;
            float vh = (aspect < 3.0f ? 1.0f : 3.0f / aspect) * zoom;
            Render2DHelper.drawCroppedTextureFloat(context, card.banner(), cx + 0.5f, cy + 0.5f, cw - 1.0f, ch - 1.0f,
                    (1.0f - uw) * 0.5f, (1.0f - vh) * 0.5f, uw, vh,
                    ColorUtils.rgba(255, 255, 255, Math.round(235.0f * alpha)));
        } else if (card.glyph() != null) {
            float gs = ch * 0.72f + 2.0f * hover;
            Render2DHelper.drawTextureFloat(context, card.glyph(), cx + cw - ch * 0.85f, cy + (ch - gs) * 0.5f, gs, gs,
                    ColorUtils.rgba(255, 255, 255, Math.round(36.0f * alpha)));
        }

        // Bottom contrast gradient
        Render2DHelper.drawGradientRoundedRectFloat(context, cx, cy, cw, ch, rad,
                ColorUtils.rgba(0, 0, 0, Math.round(35 * alpha)),
                ColorUtils.rgba(0, 0, 0, Math.round(212 * alpha)),
                ColorUtils.rgba(0, 0, 0, Math.round(170 * alpha)),
                ColorUtils.rgba(0, 0, 0, Math.round(15 * alpha)));

        if (hover > 0.01f) {
            Render2DHelper.drawRoundedRectFloat(context, cx, cy, cw, ch, rad,
                    ColorUtils.rgba(255, 255, 255, Math.round(18 * hover * alpha)));
        }
        if (flash > 0.01f) {
            Render2DHelper.drawRoundedRectFloat(context, cx, cy, cw, ch, rad,
                    ColorUtils.rgba(255, 255, 255, Math.round(64 * flash * alpha)));
        }

        int outline = ColorUtils.interpolateColor(0x28FFFFFF, ColorUtils.withAlpha(card.fromColor(), 210), hover);
        Render2DHelper.drawRoundedOutlineFloat(context, cx, cy, cw, ch, rad, 1.0f,
                ColorUtils.withAlphaFactor(outline, alpha));

        // Top badges: Phase + Count on left, Loot/Rarity tag on right
        float py = cy + 6.0f;
        float ph = 12.5f;
        float px = cx + 6.0f;
        float phW = Render2DHelper.getTextWidth(card.phase(), 0.62f) + 14.0f;
        Render2DHelper.drawRoundedRectFloat(context, px, py, phW, ph, 3.0f, ColorUtils.rgba(10, 10, 14, Math.round(180 * alpha)));
        Render2DHelper.drawCircle(context, px + 5.5f, py + ph * 0.5f, 2.0f, ColorUtils.withAlphaFactor(card.phaseColor(), alpha));
        Render2DHelper.drawScaledText(context, card.phase(), px + 10.0f, py + 3.5f,
                ColorUtils.rgba(238, 240, 246, Math.round(255 * alpha)), 0.62f, false);
        px += phW + 3.5f;

        if (card.count() > 1) {
            String cnt = "x" + card.count();
            float cwTag = Render2DHelper.getTextWidth(cnt, 0.62f) + 8.0f;
            Render2DHelper.drawRoundedRectFloat(context, px, py, cwTag, ph, 3.0f, ColorUtils.rgba(10, 10, 14, Math.round(180 * alpha)));
            Render2DHelper.drawCenteredText(context, cnt, px + cwTag * 0.5f, py + 3.5f,
                    ColorUtils.rgba(238, 240, 246, Math.round(255 * alpha)), false);
        }

        if (card.tag() != null) {
            float tagW = Render2DHelper.getTextWidth(card.tag(), 0.62f) + 10.0f;
            float tagX = cx + cw - 6.0f - tagW;
            int tagCol = card.tagColor() != 0 ? card.tagColor() : 0xFFD4D4D8;
            Render2DHelper.drawRoundedRectFloat(context, tagX, py, tagW, ph, 3.0f, ColorUtils.rgba(10, 10, 14, Math.round(180 * alpha)));
            Render2DHelper.drawRoundedRectFloat(context, tagX, py, tagW, ph, 3.0f, ColorUtils.withAlpha(tagCol, Math.round(35 * alpha)));
            Render2DHelper.drawScaledText(context, card.tag(), tagX + 5.0f, py + 3.5f,
                    ColorUtils.withAlphaFactor(tagCol, alpha), 0.62f, false);
        }

        // Bottom Title & Timer row
        float titleY = cy + ch - 23.0f;
        float timerW = 0.0f;
        if (card.endsAt() > 0L) {
            String timer = formatTimer(card.endsAt());
            timerW = Render2DHelper.getTextWidth(timer, 0.72f);
            int tCol = ColorUtils.interpolateColor(card.phaseColor(), 0xFFFFFFFF, 0.25f);
            Render2DHelper.drawScaledText(context, timer, cx + cw - 7.0f - timerW, titleY + 1.0f,
                    ColorUtils.withAlphaFactor(tCol, alpha), 0.72f, false);
            Render2DHelper.drawTextureFloat(context, TextureAssets.DESIRE_EVENT_CLOCK,
                    cx + cw - 15.0f - timerW, titleY + 1.0f, 7.0f, 7.0f,
                    ColorUtils.withAlphaFactor(tCol, alpha * 0.9f));
            timerW += 12.0f;
        }
        String title = Render2DHelper.trimToWidth(card.title(), 0.84f, cw - 14.0f - timerW);
        Render2DHelper.drawScaledText(context, title, cx + 7.0f, titleY,
                ColorUtils.rgba(255, 255, 255, Math.round(255 * alpha)), 0.84f, false);

        // Server & Coords/Extra row
        float subY = cy + ch - 11.5f;
        float srvX = cx + 7.0f;
        if (card.serverColor() != 0) {
            Render2DHelper.drawCircle(context, srvX + 3.0f, subY + 3.0f, 2.2f,
                    ColorUtils.withAlphaFactor(card.serverColor(), alpha));
        } else {
            Render2DHelper.drawTextureFloat(context, TextureAssets.DESIRE_EVENT_SERVER, srvX, subY, 6.5f, 6.5f,
                    ColorUtils.rgba(176, 178, 190, Math.round(255 * alpha)));
        }
        float srvTxtX = srvX + 9.0f;
        String srvTrim = Render2DHelper.trimToWidth(card.server(), 0.66f, cw * 0.52f);
        Render2DHelper.drawScaledText(context, srvTrim, srvTxtX, subY + 0.5f,
                ColorUtils.rgba(216, 218, 226, Math.round(255 * alpha)), 0.66f, false);

        boolean hasCoords = card.coords() != null;
        String right = hasCoords ? card.coords() : card.extra();
        if (right != null) {
            float limit = cx + cw - 7.0f;
            float avail = limit - (srvTxtX + Render2DHelper.getTextWidth(srvTrim, 0.66f)) - (hasCoords ? 14.0f : 6.0f);
            if (avail > 14.0f) {
                String rTrim = Render2DHelper.trimToWidth(right, 0.64f, avail);
                float rw = Render2DHelper.getTextWidth(rTrim, 0.64f);
                Render2DHelper.drawScaledText(context, rTrim, limit - rw, subY + 0.5f,
                        ColorUtils.rgba(202, 204, 214, Math.round(255 * alpha)), 0.64f, false);
                if (hasCoords) {
                    Render2DHelper.drawTextureFloat(context, TextureAssets.DESIRE_EVENT_PIN,
                            limit - rw - 8.5f, subY, 6.5f, 6.5f,
                            ColorUtils.rgba(202, 204, 214, Math.round(255 * alpha)));
                }
            }
        }
    }

    private void renderEmpty(GuiGraphicsExtractor context, float x, float y, float w, float h, float alpha) {
        float cx = x + w * 0.5f;
        float cy = y + h * 0.5f - 8.0f;
        Status st = service.getStatus();
        String name = service.getSource() == Source.FT ? "FunTime" : "HolyWorld";
        String title;
        String hint;
        if (!filter.isEmpty() && st == Status.OK) {
            title = "Ничего не найдено";
            hint = "Фильтр: " + filter;
        } else if (st == Status.LOADING) {
            title = "Загружаем ивенты";
            hint = "Запрашиваем " + name + "...";
        } else if (st == Status.ERROR) {
            title = "Нет ответа от сервера";
            hint = name + " временно недоступен";
        } else {
            title = "Нет активных ивентов";
            hint = "Список обновляется автоматически";
        }

        int col = st == Status.ERROR ? 0xFFF87171 : ThemeManager.getInstance().getAccentColor(0);
        Render2DHelper.drawTextureFloat(context, TextureAssets.DESIRE_EVENT_SPARKLE, cx - 10.0f, cy - 24.0f, 20.0f, 20.0f,
                ColorUtils.withAlphaFactor(col, alpha));
        Render2DHelper.drawCenteredText(context, title, cx, cy + 4.0f,
                SystemTheme.fade(SystemTheme.foreground(), alpha), false);
        Render2DHelper.drawScaledText(context, hint, cx - Render2DHelper.getTextWidth(hint, 0.72f) * 0.5f, cy + 16.0f,
                SystemTheme.fade(SystemTheme.mutedFg(), alpha), 0.72f, false);
    }

    public boolean mouseClicked(double mx, double my, int button, float x, float y, float w, float h, ClickGuiScreen host) {
        if (button != 0) return false;
        float pad = 10.0f;
        float ix = x + pad;
        float iy = y + pad;
        float iw = w - pad * 2.0f;
        float headerH = 22.0f;
        float segW = 36.0f;

        for (int i = 0; i < SOURCES.length; i++) {
            if (isInside(mx, my, ix + 2.0f + segW * i, iy, segW, headerH)) {
                if (service.getSource() != SOURCES[i]) {
                    service.setSource(SOURCES[i]);
                    cardAnims.clear();
                    resetScroll();
                }
                return true;
            }
        }

        float switchW = segW * 2.0f + 4.0f;
        float filterW = Math.min(125.0f, Math.max(76.0f, iw * 0.24f));
        float filterX = ix + switchW + 8.0f;
        if (isInside(mx, my, filterX, iy, filterW, headerH)) {
            filterFocused = true;
            return true;
        }
        filterFocused = false;

        float gridY = iy + headerH + 9.0f;
        float gridH = Math.max(20.0f, h - pad * 2.0f - headerH - 9.0f);
        if (!isInside(mx, my, ix, gridY, iw, gridH)) return false;

        int cols = iw >= 380.0f ? 3 : 2;
        float gap = 7.0f;
        float availW = iw - 6.0f;
        float cw = (availW - gap * (cols - 1)) / cols;
        float ch = 66.0f;

        for (int i = 0; i < shown.size(); i++) {
            float cx = ix + (i % cols) * (cw + gap);
            float cy = gridY + (i / cols) * (ch + gap) - scroll;
            if (isInside(mx, my, cx, cy, cw, ch)) {
                openEvent(shown.get(i), host);
                return true;
            }
        }
        return false;
    }

    private void openEvent(Card card, ClickGuiScreen host) {
        float[] anim = cardAnims.get(card.key());
        if (anim != null) anim[1] = 1.0f;

        Vector3i gps = parseCoords(card.coords());
        if (gps != null) {
            GpsManager.getInstance().setGpsName(card.title());
            GpsManager.getInstance().setGpsPosition(gps);
        }

        int anarchy = extractDigits(card.server());
        if (anarchy > 0) {
            AnarchyReconnectService.getInstance().start(Target.ANARCHY, anarchy);
        }
        feedbackText = anarchy > 0 ? "Переход на #" + anarchy : "Метка поставлена";
        feedbackAt = System.currentTimeMillis();
        host.showToast(feedbackText);

        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.player != null && mc.level != null) {
            mc.setScreenAndShow(null);
        }
    }

    public boolean mouseScrolled(double verticalAmount) {
        targetScroll = Mth.clamp(targetScroll - (float) (verticalAmount * 26.0f), 0.0f, maxScroll);
        return true;
    }

    public boolean keyPressed(int key) {
        if (!filterFocused) return false;
        if (key == InputConstants.KEY_BACKSPACE && !filter.isEmpty()) {
            filter = filter.substring(0, filter.length() - 1);
            resetScroll();
        } else if (key == InputConstants.KEY_ESCAPE || key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) {
            filterFocused = false;
        }
        return true;
    }

    public boolean charTyped(String ch) {
        if (!filterFocused || ch == null || ch.isEmpty()) return false;
        if (filter.length() < 24 && !Character.isISOControl(ch.charAt(0))) {
            filter += ch;
            resetScroll();
        }
        return true;
    }

    private static Vector3i parseCoords(String coords) {
        if (coords == null) return null;
        String[] xyz = coords.trim().split("\\s+");
        if (xyz.length != 3) return null;
        try {
            return new Vector3i(Integer.parseInt(xyz[0]), Integer.parseInt(xyz[1]), Integer.parseInt(xyz[2]));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static int extractDigits(String text) {
        Matcher m = Pattern.compile("(\\d+)").matcher(text == null ? "" : text);
        if (!m.find()) return -1;
        try {
            return Integer.parseInt(m.group(1));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static String formatTimer(long endsAt) {
        long left = Math.max(0L, endsAt - System.currentTimeMillis()) / 1000L;
        long hours = left / 3600L;
        long minutes = (left / 60L) % 60L;
        long seconds = left % 60L;
        return hours > 0 ? String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
                : String.format(Locale.ROOT, "%02d:%02d", minutes, seconds);
    }

    private static float pulse(long periodMs) {
        return (System.currentTimeMillis() % periodMs) / (float) periodMs;
    }

    private static boolean isInside(double mx, double my, float x, float y, float w, float h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }
}
