package dev.opticumeclient.client.hud;

import dev.opticumeclient.client.modules.Category;
import dev.opticumeclient.client.modules.Module;
import dev.opticumeclient.client.render.ColorUtils;
import dev.opticumeclient.client.render.Render2DHelper;
import dev.opticumeclient.client.render.TextureAssets;
import dev.opticumeclient.client.render.Theme;
import dev.opticumeclient.client.render.ThemeManager;
import dev.opticumeclient.client.render.font.OpticumeFonts;
import dev.opticumeclient.client.services.media.CoverArtManager;
import dev.opticumeclient.client.services.media.LyricsService;
import dev.opticumeclient.client.services.media.SmtcBridge;
import dev.opticumeclient.client.setting.BooleanSetting;
import dev.opticumeclient.client.setting.NumberSetting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * Draggable Liquid Glass media player HUD powered by the persistent Windows SMTC bridge,
 * showing CoverArt, synced lrclib.net scissor-highlighted Lyrics, timeline progress, and interactive controls.
 */
public class MediaPlayerHud extends Module {
    private static MediaPlayerHud instance;

    private final BooleanSetting showLyrics = new BooleanSetting("Show Lyrics", "Караоке-подсветка текста песни с lrclib.net", true);
    private final NumberSetting posX = new NumberSetting("PosX", "X позиция", 12.0, 0.0, 1920.0, 1.0);
    private final NumberSetting posY = new NumberSetting("PosY", "Y позиция", 310.0, 0.0, 1080.0, 1.0);
    private final HudManager.DraggableWidget dragWidget;

    private boolean wasMouseDown = false;

    public MediaPlayerHud() {
        super("MediaPlayer", "Музыкальный плеер с SMTC-обложкой, караоке-лирикой и управлением", Category.HUD);
        instance = this;
        addSetting(showLyrics);
        addSetting(posX);
        addSetting(posY);
        setEnabled(false);

        this.dragWidget = HudManager.getInstance().registerWidget(new HudManager.DraggableWidget(
                "MediaPlayer",
                posX.getFloatValue(),
                posY.getFloatValue(),
                172.0f,
                50.0f,
                this::isEnabled,
                (nx, ny) -> {
                    posX.setValue((double) Math.round(nx));
                    posY.setValue((double) Math.round(ny));
                }
        ));
    }

    public static MediaPlayerHud getInstance() {
        return instance;
    }

    @Override
    public void onTick() {
        if (isEnabled()) {
            SmtcBridge.getInstance().notifyConsumerActive();
        }
    }

    @Override
    public void onRender2D(GuiGraphicsExtractor context, float tickDelta) {
        if (!isEnabled() || mc.player == null) return;
        SmtcBridge.getInstance().notifyConsumerActive();

        SmtcBridge.TrackSnapshot track = SmtcBridge.getInstance().getSnapshot();
        boolean inChat = mc.gui != null && mc.gui.screen() instanceof ChatScreen;
        if (!track.active() && !inChat) return;

        Theme theme = ThemeManager.getInstance().getCurrentTheme();
        int c1 = theme.getPrimaryColor();
        int c2 = theme.getSecondaryColor();

        String title = track.active() ? track.title() : "No active media";
        String artist = track.active() ? (track.artist().isBlank() ? "Windows SMTC" : track.artist()) : "Play music in Spotify / Browser";
        double posSec = track.interpolatedPositionSec();
        double durSec = track.durationSec();
        LyricsService.ActiveLyric lyric = (track.active() && showLyrics.isEnabled())
                ? LyricsService.getInstance().getActiveLine(track.artist(), track.title(), Math.round(posSec * 1000.0))
                : null;

        float w = 178.0f;
        float h = lyric != null ? 58.0f : 48.0f;
        dragWidget.syncExternalPosition(posX.getFloatValue(), posY.getFloatValue());
        dragWidget.setBounds(w, h);
        float x = dragWidget.getX();
        float y = dragWidget.getY();

        HudPanelRenderer.beginWidget("MediaPlayer");
        try {
            HudPanelRenderer.drawSurface(context, "MediaPlayer", x, y, w, h, 7.0f, 232, 40);

            float artSize = 34.0f;
            float artX = x + 7.0f;
            float artY = y + 7.0f;
            Identifier coverTex = CoverArtManager.getInstance().getTextureId();
            float coverAlpha = CoverArtManager.getInstance().updateFade();
            Render2DHelper.drawRoundedRectFloat(context, artX, artY, artSize, artSize, 5.0f, 0x99161925);
            if (coverTex != null && coverAlpha > 0.02f) {
                Render2DHelper.drawTextureFloat(context, coverTex, artX, artY, artSize, artSize,
                        ColorUtils.rgba(255, 255, 255, Math.round(255 * coverAlpha)));
            } else {
                Render2DHelper.drawTextureFloat(context, TextureAssets.DESIRE_EVENT_SPARKLE, artX + 9.0f, artY + 9.0f,
                        16.0f, 16.0f, ColorUtils.withAlpha(c1, 210));
            }

            float tx = artX + artSize + 7.0f;
            float textMaxW = w - (tx - x) - 8.0f;
            String trimTitle = Render2DHelper.trimToWidth(title, 0.78f, textMaxW);
            String trimArtist = Render2DHelper.trimToWidth(artist, 0.66f, textMaxW - 44.0f);
            HudPanelRenderer.drawHudText(context, OpticumeFonts.KEY_HUD_SEMIBOLD, trimTitle, tx, y + 7.5f, 7.6f, 0xFFF3F6FC);
            HudPanelRenderer.drawHudText(context, OpticumeFonts.KEY_HUD_MEDIUM, trimArtist, tx, y + 17.5f, 6.4f, 0xFF94A3B8);

            // Interactive media buttons on right row (Prev | Play/Pause | Next)
            float ctrlY = y + 16.5f;
            float ctrlX = x + w - 45.0f;
            drawControlsAndHandleClicks(context, inChat, ctrlX, ctrlY, track.playing(), c1);

            // Timeline bar + timestamp
            float barY = artY + artSize - 3.0f;
            float barW = textMaxW - 2.0f;
            float pct = durSec > 0.1 ? Mth.clamp((float) (posSec / durSec), 0.0f, 1.0f) : 0.0f;
            Render2DHelper.drawRoundedRectFloat(context, tx, barY, barW, 2.0f, 1.0f, 0x33FFFFFF);
            if (pct > 0.01f) {
                Render2DHelper.drawGradientRoundedRectFloat(context, tx, barY, Math.max(2.0f, barW * pct), 2.0f, 1.0f,
                        c1, c1, c2, c2);
            }

            // Dual-layer scissor karaoke lyric row
            if (lyric != null) {
                String line = Render2DHelper.trimToWidth(lyric.current(), 0.68f, w - 16.0f);
                float lx = x + 8.0f;
                float ly = y + 44.5f;
                float lw = HudPanelRenderer.measureHud(OpticumeFonts.KEY_HUD_MEDIUM, line, 6.6f);
                HudPanelRenderer.drawHudText(context, OpticumeFonts.KEY_HUD_MEDIUM, line, lx, ly, 6.6f, 0xAA8E97AB);
                int clipW = Math.max(1, Math.round(lw * lyric.progress()));
                context.enableScissor(Math.round(lx), Math.round(ly - 1.0f), Math.round(lx + clipW), Math.round(ly + 11.0f));
                HudPanelRenderer.drawHudText(context, OpticumeFonts.KEY_HUD_MEDIUM, line, lx, ly, 6.6f, c2);
                context.disableScissor();
            }
        } finally {
            HudPanelRenderer.endWidget();
        }
    }

    private void drawControlsAndHandleClicks(GuiGraphicsExtractor context, boolean interactive,
                                             float x, float y, boolean playing, int accent) {
        String[] glyphs = {"<", playing ? "||" : ">", ">"};
        float mx = interactive ? (float) mc.mouseHandler.getScaledXPos(mc.getWindow()) : -100.0f;
        float my = interactive ? (float) mc.mouseHandler.getScaledYPos(mc.getWindow()) : -100.0f;
        boolean lmb = interactive && mc.mouseHandler.isLeftPressed();
        boolean clickEdge = lmb && !wasMouseDown;
        wasMouseDown = lmb;

        for (int i = 0; i < 3; i++) {
            float bx = x + i * 13.0f;
            boolean hov = interactive && mx >= bx && mx <= bx + 11.0f && my >= y && my <= y + 11.0f;
            int bg = hov ? ColorUtils.withAlpha(accent, 90) : 0x26FFFFFF;
            Render2DHelper.drawRoundedRectFloat(context, bx, y, 11.0f, 11.0f, 3.0f, bg);
            Render2DHelper.drawCenteredText(context, glyphs[i], bx + 5.5f, y + 2.2f,
                    hov ? 0xFFFFFFFF : 0xFFCBD5E1, false);
            if (hov && clickEdge) {
                if (i == 0) SmtcBridge.getInstance().prevTrack();
                else if (i == 1) SmtcBridge.getInstance().togglePlayPause();
                else SmtcBridge.getInstance().nextTrack();
            }
        }
    }
}
