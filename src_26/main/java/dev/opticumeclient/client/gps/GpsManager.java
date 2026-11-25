package dev.opticumeclient.client.gps;

import dev.opticumeclient.client.render.ColorUtils;
import dev.opticumeclient.client.render.Render2DHelper;
import dev.opticumeclient.client.render.Render3DHelper;
import dev.opticumeclient.client.render.TextureAssets;
import dev.opticumeclient.client.render.ThemeManager;
import dev.opticumeclient.client.render.font.OpticumeFonts;
import dev.opticumeclient.client.util.ChatUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3x2fStack;
import org.joml.Vector3i;

import java.util.Locale;

/**
 * GPS waypoint tracker with Desire-style floating HUD pill (plus offscreen directional indicator)
 * and interactive right-click context menu ("Удалить", "К метке") inside ChatScreen.
 */
public final class GpsManager {
    private static final GpsManager INSTANCE = new GpsManager();
    private static final String[] MENU_OPTIONS = {"Удалить", "К метке"};
    private static final Identifier[] MENU_ICONS = {TextureAssets.DESIRE_HUD_CROSS, TextureAssets.DESIRE_HUD_PIN};

    private Vector3i gpsPosition = null;
    private Vector3i lastGpsPosition = null;
    private String gpsName = "GPS";
    private String lastGpsName = "GPS";

    private float distanceAnim = 0.0f;
    private float switchAnim = 0.0f;
    private float menuAnim = 0.0f;
    private long lastNanos = System.nanoTime();

    private boolean markerVisible = false;
    private float boxX, boxY, boxW, boxH;
    private boolean menuOpen = false;
    private Screen menuScreen = null;
    private float menuX, menuY, menuW, menuRowH;
    private boolean lookAtMarker = false;

    private GpsManager() {
    }

    public static GpsManager getInstance() {
        return INSTANCE;
    }

    public Vector3i getGpsPosition() {
        return gpsPosition;
    }

    public void setGpsPosition(Vector3i gpsPosition) {
        this.gpsPosition = gpsPosition;
    }

    public String getGpsName() {
        return gpsName;
    }

    public void setGpsName(String gpsName) {
        this.gpsName = (gpsName == null || gpsName.isBlank()) ? "GPS" : gpsName;
    }

    public void renderOverlay(GuiGraphicsExtractor context) {
        Minecraft mc = Minecraft.getInstance();
        if (context == null || mc == null || mc.player == null || mc.level == null) return;

        long now = System.nanoTime();
        float dt = Math.min(0.05f, Math.max(0.0001f, (now - lastNanos) / 1.0e9f));
        lastNanos = now;

        if (gpsPosition != null) {
            lastGpsPosition = new Vector3i(gpsPosition);
            lastGpsName = gpsName;
        }

        markerVisible = false;
        boolean noGps = gpsPosition == null;
        boolean noLast = lastGpsPosition == null;
        float distance = !noLast ? getDistance(mc, lastGpsPosition) : 0.0f;
        float distTarget = distance <= 3.0f ? 0.0f : (distance >= 10.0f ? 1.0f : (distance - 3.0f) / 7.0f);
        float switchTarget = noGps ? 0.0f : 1.0f;

        float rate = 1.0f - (float) Math.exp(-10.0f * dt);
        float menuRate = 1.0f - (float) Math.exp(-18.0f * dt);
        distanceAnim += (distTarget - distanceAnim) * rate;
        switchAnim += (switchTarget - switchAnim) * rate;
        menuAnim += ((menuOpen ? 1.0f : 0.0f) - menuAnim) * menuRate;

        if (noGps) {
            lookAtMarker = false;
            closeMenu();
        }
        if (lookAtMarker && !noLast) {
            turnToMarker(mc);
        }

        renderWaypoints(context, mc);

        if (noLast || distanceAnim < 0.05f || switchAnim < 0.05f) return;

        float alpha = Mth.clamp(distanceAnim * switchAnim, 0.0f, 1.0f);
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();

        Vec3 worldPos = markerWorldPos();
        float[] screen = Render3DHelper.worldToScreen(worldPos);
        boolean inBounds = screen != null
                && screen[0] >= 4.0f && screen[0] <= sw - 4.0f
                && screen[1] >= 4.0f && screen[1] <= sh - 4.0f;

        int accent = ThemeManager.getInstance().getAccentColor(0);

        if (inBounds) {
            renderOnScreenMarker(context, mc, screen[0], screen[1], distance, accent, alpha);
            renderContextMenu(context, mc);
        } else {
            closeMenu();
            renderOffScreenArrow(context, mc, sw, sh, worldPos, distance, accent, alpha);
        }
    }

    private void renderOnScreenMarker(GuiGraphicsExtractor context, Minecraft mc, float sx, float sy,
                                      float distance, int accent, float alpha) {
        String distText = String.format(Locale.US, "%.0fm", distance);
        String namePrefix = (!"GPS".equals(lastGpsName) ? lastGpsName + " · " : "");
        String coordText = namePrefix + lastGpsPosition.x + " " + lastGpsPosition.y + " " + lastGpsPosition.z;

        float fontSize = 7.5f;
        float pinW = 6.0f;
        float pinH = 8.0f;
        float padX = 5.0f;
        float gap = 3.5f;
        float radius = 3.5f;

        float coordW = measureHud(coordText, fontSize);
        float distW = measureHud(distText, fontSize);
        boxW = padX + pinW + gap + coordW + gap + distW + padX;
        boxH = 14.0f;
        boxX = Mth.clamp(sx - boxW * 0.5f, 4.0f, Math.max(4.0f, mc.getWindow().getGuiScaledWidth() - boxW - 4.0f));
        boxY = Mth.clamp(sy - boxH * 0.5f, 4.0f, Math.max(4.0f, mc.getWindow().getGuiScaledHeight() - boxH - 4.0f));
        markerVisible = true;

        int a255 = Math.round(255.0f * alpha);
        Render2DHelper.drawBlurredRoundedRect(context, boxX, boxY, boxW, boxH, radius, 0xCC0B0C16, alpha);
        Render2DHelper.drawRoundedRectFloat(context, boxX, boxY, boxW, boxH, radius,
                ColorUtils.rgba(11, 12, 22, Math.round(196.0f * alpha)));
        Render2DHelper.drawRoundedOutlineFloat(context, boxX, boxY, boxW, boxH, radius, 0.8f,
                ColorUtils.withAlpha(accent, Math.round(95.0f * alpha)));

        float cx = boxX + padX;
        Render2DHelper.drawTextureFloat(context, TextureAssets.DESIRE_HUD_PIN, cx, boxY + (boxH - pinH) * 0.5f,
                pinW, pinH, ColorUtils.rgba(235, 238, 250, a255));
        cx += pinW + gap;

        float textY = boxY + (boxH - fontSize) * 0.5f;
        drawHudText(context, coordText, cx, textY, fontSize, ColorUtils.rgba(255, 255, 255, a255));
        cx += coordW + gap;
        drawHudText(context, distText, cx, textY, fontSize, ColorUtils.withAlpha(accent, a255));
    }

    private void renderOffScreenArrow(GuiGraphicsExtractor context, Minecraft mc, int sw, int sh,
                                      Vec3 worldPos, float distance, int accent, float alpha) {
        double dx = worldPos.x - mc.player.getX();
        double dz = worldPos.z - mc.player.getZ();
        double worldAngle = Math.toDegrees(Math.atan2(dz, dx)) - 90.0;
        double relRad = Math.toRadians(Mth.wrapDegrees((float) (worldAngle - mc.player.getYRot())));

        float radius = 42.0f;
        float cx = sw * 0.5f + (float) Math.sin(relRad) * radius;
        float cy = sh * 0.5f - (float) Math.cos(relRad) * radius;

        Matrix3x2fStack pose = context.pose();
        pose.pushMatrix();
        pose.translate(cx, cy);
        pose.rotate((float) relRad);
        Render2DHelper.drawTextureFloat(context, TextureAssets.DESIRE_GPS_ARROW, -6.5f, -6.5f, 13.0f, 13.0f,
                ColorUtils.withAlpha(accent, Math.round(235.0f * alpha)));
        pose.popMatrix();

        String distText = String.format(Locale.US, "%.0fm", distance);
        float tw = measureHud(distText, 6.8f);
        drawHudText(context, distText, cx - tw * 0.5f, cy + 8.5f, 6.8f, ColorUtils.rgba(235, 238, 250, Math.round(220.0f * alpha)));
    }

    private void renderContextMenu(GuiGraphicsExtractor context, Minecraft mc) {
        if (menuOpen && (mc.gui == null || mc.gui.screen() != menuScreen)) {
            closeMenu();
        }
        if (menuAnim <= 0.01f) return;

        float textS = 7.0f;
        float pad = 5.0f;
        float iconS = 6.5f;
        float iconGap = 4.0f;

        float optW = 0.0f;
        for (String opt : MENU_OPTIONS) {
            optW = Math.max(optW, measureHud(opt, textS));
        }
        menuW = pad + iconS + iconGap + optW + pad + 4.0f;
        menuRowH = 12.5f;
        menuX = boxX;
        menuY = boxY + boxH + 2.5f;

        float top = menuY - (1.0f - menuAnim) * 4.0f;
        float menuH = menuRowH * MENU_OPTIONS.length;
        int accent = ThemeManager.getInstance().getAccentColor(0);

        Render2DHelper.drawBlurredRoundedRect(context, menuX, top, menuW, menuH, 3.5f, 0xCC0D0E16, menuAnim);
        Render2DHelper.drawRoundedRectFloat(context, menuX, top, menuW, menuH, 3.5f,
                ColorUtils.rgba(14, 16, 24, Math.round(220.0f * menuAnim)));
        Render2DHelper.drawRoundedOutlineFloat(context, menuX, top, menuW, menuH, 3.5f, 0.8f,
                ColorUtils.withAlpha(accent, Math.round(90.0f * menuAnim)));

        double scale = Math.max(1.0, mc.getWindow().getGuiScale());
        float mx = (float) (mc.mouseHandler.xpos() / scale);
        float my = (float) (mc.mouseHandler.ypos() / scale);

        for (int i = 0; i < MENU_OPTIONS.length; i++) {
            float rowY = top + i * menuRowH;
            boolean hov = mx >= menuX && mx <= menuX + menuW && my >= rowY && my < rowY + menuRowH;
            if (hov) {
                Render2DHelper.drawRoundedRectFloat(context, menuX + 1.5f, rowY + 1.5f, menuW - 3.0f, menuRowH - 3.0f, 2.0f,
                        ColorUtils.withAlpha(accent, Math.round(52.0f * menuAnim)));
            }
            float cy = rowY + menuRowH * 0.5f;
            int iconCol = ColorUtils.withAlpha(hov ? accent : 0xFFB4BCD0, Math.round(255.0f * menuAnim));
            Render2DHelper.drawTextureFloat(context, MENU_ICONS[i], menuX + pad, cy - iconS * 0.5f, iconS, iconS, iconCol);
            int txtCol = ColorUtils.rgba(240, 242, 248, Math.round((hov ? 255.0f : 205.0f) * menuAnim));
            drawHudText(context, MENU_OPTIONS[i], menuX + pad + iconS + iconGap, cy - textS * 0.5f, textS, txtCol);
        }
    }

    public void onChatScreenClick(int button) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || mc.gui == null || !(mc.gui.screen() instanceof ChatScreen)) return;
        double scale = Math.max(1.0, mc.getWindow().getGuiScale());
        float mx = (float) (mc.mouseHandler.xpos() / scale);
        float my = (float) (mc.mouseHandler.ypos() / scale);

        if (markerVisible && mx >= boxX && mx <= boxX + boxW && my >= boxY && my <= boxY + boxH) {
            if (button == 1 && !menuOpen) {
                menuScreen = mc.gui.screen();
                menuOpen = true;
            } else {
                closeMenu();
            }
            return;
        }
        if (!menuOpen) return;

        int option = (mx >= menuX && mx <= menuX + menuW && my >= menuY && my <= menuY + menuRowH * MENU_OPTIONS.length)
                ? (int) ((my - menuY) / menuRowH)
                : -1;
        closeMenu();
        if (option == 0) {
            gpsPosition = null;
            ChatUtils.sendClientMessage("Метка успешно удалена.");
        } else if (option == 1) {
            lookAtMarker = true;
        }
    }

    private void closeMenu() {
        menuOpen = false;
    }

    private void turnToMarker(Minecraft mc) {
        Vec3 eyes = mc.player.getEyePosition(1.0f);
        Vec3 target = markerWorldPos();
        double dx = target.x - eyes.x;
        double dy = target.y - eyes.y;
        double dz = target.z - eyes.z;
        double horiz = Math.hypot(dx, dz);
        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0f;
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, horiz));

        float dYaw = Mth.wrapDegrees(yaw - mc.player.getYRot());
        float dPitch = pitch - mc.player.getXRot();
        if (Math.abs(dYaw) < 0.5f && Math.abs(dPitch) < 0.5f) {
            lookAtMarker = false;
            return;
        }
        mc.player.setYRot(mc.player.getYRot() + dYaw * 0.2f);
        mc.player.setXRot(Mth.clamp(mc.player.getXRot() + dPitch * 0.2f, -90.0f, 90.0f));
    }

    private void renderWaypoints(GuiGraphicsExtractor context, Minecraft mc) {
        var ways = dev.opticumeclient.client.command.CommandDataStore.getInstance().getWaypoints();
        if (ways.isEmpty()) return;
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        for (dev.opticumeclient.client.command.CommandDataStore.WaypointEntry w : ways) {
            if (gpsPosition != null && gpsPosition.x == w.x() && gpsPosition.y == w.y() && gpsPosition.z == w.z()) {
                continue;
            }
            Vec3 worldPos = new Vec3(w.x() + 0.5, w.y() + 1.0, w.z() + 0.5);
            float[] screen = Render3DHelper.worldToScreen(worldPos);
            if (screen == null || screen[0] < 8.0f || screen[0] > sw - 8.0f || screen[1] < 8.0f || screen[1] > sh - 8.0f) {
                continue;
            }
            float dist = Mth.sqrt((float) mc.player.distanceToSqr(worldPos));
            String label = w.name() + " " + String.format(Locale.US, "%.0fm", dist);
            float fontSize = 6.8f;
            float lw = measureHud(label, fontSize);
            float pw = lw + 10.0f;
            float ph = 12.0f;
            float px = screen[0] - pw * 0.5f;
            float py = screen[1] - ph * 0.5f;
            Render2DHelper.drawRoundedRectFloat(context, px, py, pw, ph, 3.0f, 0xB80B0C16);
            Render2DHelper.drawRoundedOutlineFloat(context, px, py, pw, ph, 3.0f, 0.7f, ColorUtils.withAlpha(w.color(), 140));
            drawHudText(context, label, px + 5.0f, py + (ph - fontSize) * 0.5f, fontSize, 0xFFF2F4FA);
        }
    }

    private Vec3 markerWorldPos() {
        return new Vec3(lastGpsPosition.x + 0.5, lastGpsPosition.y + 1.0, lastGpsPosition.z + 0.5);
    }

    private static float getDistance(Minecraft mc, Vector3i target) {
        double dx = mc.player.getX() - target.x;
        double dy = mc.player.getY() - target.y;
        double dz = mc.player.getZ() - target.z;
        return Mth.sqrt((float) (dx * dx + dy * dy + dz * dz));
    }

    private static float measureHud(String text, float pxSize) {
        float w = OpticumeFonts.getDirectWidth(OpticumeFonts.KEY_HUD_SEMIBOLD, text, pxSize);
        return w > 0.0f ? w : Render2DHelper.getTextWidth(text, pxSize / 9.0f);
    }

    private static void drawHudText(GuiGraphicsExtractor context, String text, float x, float y, float pxSize, int color) {
        if (!OpticumeFonts.drawDirect(context, OpticumeFonts.KEY_HUD_SEMIBOLD, text, x, y, pxSize, color)) {
            Render2DHelper.drawScaledText(context, text, x, y, color, pxSize / 9.0f, false);
        }
    }
}
