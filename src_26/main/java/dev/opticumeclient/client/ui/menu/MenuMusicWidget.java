package dev.opticumeclient.client.ui.menu;

import dev.opticumeclient.client.render.ColorUtils;
import dev.opticumeclient.client.render.Render2DHelper;
import dev.opticumeclient.client.render.ThemeManager;
import dev.opticumeclient.client.services.media.CoverArtManager;
import dev.opticumeclient.client.services.media.SmtcBridge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;

/**
 * Compact Liquid Glass "Now Playing" mini-player widget for the main menu,
 * powered by {@link SmtcBridge} and {@link CoverArtManager}.
 */
public final class MenuMusicWidget {
    private float visibility = 0.0f;
    private float progressSmooth = 0.0f;
    private float hoverPrev = 0.0f;
    private float hoverPlay = 0.0f;
    private float hoverNext = 0.0f;

    private float boxX, boxY, boxW, boxH;
    private float prevX, playX, nextX, ctrlY, ctrlSize;
    private boolean interactable = false;

    public void updateAndRender(
            GuiGraphicsExtractor context,
            float x,
            float y,
            float k,
            float mx,
            float my,
            float dt,
            float screenAlpha
    ) {
        SmtcBridge bridge = SmtcBridge.getInstance();
        bridge.notifyConsumerActive();
        SmtcBridge.TrackSnapshot snap = bridge.getSnapshot();
        boolean hasTrack = snap.active() && snap.title() != null && !snap.title().isBlank();

        float targetVis = hasTrack ? 1.0f : 0.0f;
        visibility += (targetVis - visibility) * (1.0f - (float) Math.exp(-10.0f * dt));
        float vis = Mth.clamp(visibility, 0.0f, 1.0f) * screenAlpha;
        if (vis <= 0.02f) {
            interactable = false;
            return;
        }

        interactable = hasTrack && vis > 0.35f;
        boxW = 190.0f * k;
        boxH = 42.0f * k;
        boxX = x;
        boxY = y - (1.0f - Mth.clamp(visibility, 0.0f, 1.0f)) * 8.0f * k;

        int accent = ThemeManager.getInstance().getCurrentTheme().getPrimaryColor();
        int accent2 = ThemeManager.getInstance().getCurrentTheme().getSecondaryColor();

        Render2DHelper.drawGlow(context, boxX, boxY + 2.0f * k, boxW, boxH, 8.0f * k,
                ColorUtils.withAlpha(0xFF000000, Math.round(45.0f * vis)));
        Render2DHelper.drawBlurredRoundedRect(context, boxX, boxY, boxW, boxH, 10.5f * k, 0x120A0E18, vis);
        Render2DHelper.drawRoundedRectFloat(context, boxX, boxY, boxW, boxH, 10.5f * k,
                ColorUtils.withAlpha(0xFFFFFFFF, Math.round(16.0f * vis)));
        Render2DHelper.drawRoundedOutlineFloat(context, boxX, boxY, boxW, boxH, 10.5f * k, 0.55f,
                ColorUtils.withAlpha(0xFFFFFFFF, Math.round(34.0f * vis)));

        float artPad = 5.5f * k;
        float artSize = boxH - artPad * 2.0f;
        float artX = boxX + artPad;
        float artY = boxY + artPad;

        Identifier cover = CoverArtManager.getInstance().getTextureId();
        float coverFade = CoverArtManager.getInstance().updateFade();
        if (cover != null && coverFade > 0.05f) {
            Render2DHelper.drawRoundedTextureFloat(context, cover, artX, artY, artSize, artSize, 6.0f * k,
                    ColorUtils.rgba(255, 255, 255, Math.round(255.0f * coverFade * vis)));
        } else {
            Render2DHelper.drawRoundedRectFloat(context, artX, artY, artSize, artSize, 6.0f * k,
                    ColorUtils.withAlpha(accent, Math.round(70.0f * vis)));
            OpticumeButtonStyle.drawUiCenteredText(context, "♫", artX + artSize * 0.5f, artY + artSize * 0.32f, 9.0f * k,
                    ColorUtils.rgba(255, 255, 255, Math.round(235.0f * vis)), false);
        }

        // Media transport controls on the right
        ctrlSize = 14.0f * k;
        float ctrlGap = 3.0f * k;
        nextX = boxX + boxW - artPad - ctrlSize;
        playX = nextX - ctrlGap - ctrlSize;
        prevX = playX - ctrlGap - ctrlSize;
        ctrlY = boxY + (boxH - ctrlSize) * 0.5f - 1.5f * k;

        boolean hovPrev = interactable && isInside(mx, my, prevX, ctrlY, ctrlSize, ctrlSize);
        boolean hovPlay = interactable && isInside(mx, my, playX, ctrlY, ctrlSize, ctrlSize);
        boolean hovNext = interactable && isInside(mx, my, nextX, ctrlY, ctrlSize, ctrlSize);
        float kAnim = 1.0f - (float) Math.exp(-16.0f * dt);
        hoverPrev += ((hovPrev ? 1.0f : 0.0f) - hoverPrev) * kAnim;
        hoverPlay += ((hovPlay ? 1.0f : 0.0f) - hoverPlay) * kAnim;
        hoverNext += ((hovNext ? 1.0f : 0.0f) - hoverNext) * kAnim;

        drawControlButton(context, prevX, ctrlY, ctrlSize, "<", hoverPrev, accent, vis, k);
        drawControlButton(context, playX, ctrlY, ctrlSize, snap.playing() ? "||" : ">", Math.max(0.35f, hoverPlay), accent, vis, k);
        drawControlButton(context, nextX, ctrlY, ctrlSize, ">", hoverNext, accent, vis, k);

        // Title & artist clipped cleanly between cover and controls
        float txtX = artX + artSize + 6.5f * k;
        float maxTxtW = Math.max(24.0f * k, prevX - txtX - 5.0f * k);
        float titleSize = 7.4f * k;
        float artistSize = 5.8f * k;
        String safeTitle = trimUiText(snap.title(), titleSize, maxTxtW);
        String safeArtist = trimUiText(snap.artist() == null || snap.artist().isBlank() ? "Playing" : snap.artist(), artistSize, maxTxtW);

        OpticumeButtonStyle.drawUiText(context, safeTitle, txtX, boxY + 8.5f * k, titleSize,
                ColorUtils.rgba(255, 255, 255, Math.round(248.0f * vis)), true);
        OpticumeButtonStyle.drawUiText(context, safeArtist, txtX, boxY + 19.5f * k, artistSize,
                ColorUtils.rgba(220, 228, 244, Math.round(195.0f * vis)), true);

        // Bottom timeline bar
        double dur = snap.durationSec();
        float targetProg = dur > 0.5 ? Mth.clamp((float) (snap.interpolatedPositionSec() / dur), 0.0f, 1.0f) : 0.0f;
        progressSmooth += (targetProg - progressSmooth) * (1.0f - (float) Math.exp(-10.0f * dt));
        float barX = txtX;
        float barY = boxY + boxH - 6.5f * k;
        float barW = Math.max(18.0f * k, (boxX + boxW - artPad) - barX);
        Render2DHelper.drawRoundedRectFloat(context, barX, barY, barW, 2.0f * k, 1.0f * k,
                ColorUtils.withAlpha(0xFFFFFFFF, Math.round(28.0f * vis)));
        if (progressSmooth > 0.01f) {
            Render2DHelper.drawGradientRoundedRectFloat(context, barX, barY, Math.max(2.5f * k, barW * progressSmooth), 2.0f * k, 1.0f * k,
                    ColorUtils.withAlpha(accent, Math.round(230.0f * vis)),
                    ColorUtils.withAlpha(accent, Math.round(230.0f * vis)),
                    ColorUtils.withAlpha(accent2, Math.round(230.0f * vis)),
                    ColorUtils.withAlpha(accent2, Math.round(230.0f * vis)));
        }
    }

    public boolean mouseClicked(float mx, float my) {
        if (!interactable) return false;
        if (!isInside(mx, my, boxX, boxY, boxW, boxH)) return false;

        if (isInside(mx, my, prevX, ctrlY, ctrlSize, ctrlSize)) {
            playClick();
            SmtcBridge.getInstance().prevTrack();
            return true;
        }
        if (isInside(mx, my, playX, ctrlY, ctrlSize, ctrlSize)) {
            playClick();
            SmtcBridge.getInstance().togglePlayPause();
            return true;
        }
        if (isInside(mx, my, nextX, ctrlY, ctrlSize, ctrlSize)) {
            playClick();
            SmtcBridge.getInstance().nextTrack();
            return true;
        }
        return true;
    }

    private static void drawControlButton(
            GuiGraphicsExtractor context,
            float x, float y, float size,
            String glyph, float hv, int accent, float vis, float k
    ) {
        int bg = ColorUtils.interpolateColor(
                ColorUtils.withAlpha(0xFFFFFFFF, Math.round(14.0f * vis)),
                ColorUtils.withAlpha(accent, Math.round(95.0f * vis)),
                hv
        );
        Render2DHelper.drawRoundedRectFloat(context, x, y, size, size, 4.0f * k, bg);
        OpticumeButtonStyle.drawUiCenteredText(context, glyph, x + size * 0.5f, y + size * 0.25f, 6.2f * k,
                ColorUtils.rgba(255, 255, 255, Math.round(Mth.lerp(hv, 200.0f, 255.0f) * vis)), false);
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

    private static void playClick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.getSoundManager() != null) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
        }
    }
}
