package dev.opticumeclient.client.modules.visuals;

import dev.opticumeclient.client.friend.FriendManager;
import dev.opticumeclient.client.modules.Category;
import dev.opticumeclient.client.modules.Module;
import dev.opticumeclient.client.render.ColorUtils;
import dev.opticumeclient.client.render.Render2DHelper;
import dev.opticumeclient.client.render.Render3DHelper;
import dev.opticumeclient.client.render.ThemeManager;
import dev.opticumeclient.client.setting.BooleanSetting;
import dev.opticumeclient.client.setting.ModeSetting;
import net.minecraft.client.gui.GuiGraphicsExtractor; 
import net.minecraft.client.multiplayer.PlayerInfo; 
import com.mojang.blaze3d.vertex.PoseStack; 
import net.minecraft.world.entity.player.Player; 
import net.minecraft.world.item.ItemStack; 
import net.minecraft.world.phys.AABB; 
import net.minecraft.util.Mth; 
import org.joml.Matrix3x2fStack;

public class PlayerEspModule extends Module {
    private final ModeSetting mode = new ModeSetting("Mode", "Стиль боксов ESP", "Corner 2D", "Corner 2D", "2D Box", "3D Box");
    private final BooleanSetting healthBar = new BooleanSetting("HealthBar", "Вертикальная градиентная полоска HP слева", true);
    private final BooleanSetting nameTags = new BooleanSetting("NameTags", "Стеклянный Liquid Glass тег с пингом, HP и предметами", true);

    public PlayerEspModule() {
        super("PlayerESP", "2D угловые боксы, полоска HP и стеклянные NameTags с предметами", Category.VISUALS);
        addSetting(mode);
        addSetting(healthBar);
        addSetting(nameTags);
        setEnabled(false);
    }

    @Override
    public String getDisplaySuffix() {
        return mode.getMode();
    }

    @Override
    public void onRender3D(PoseStack matrices, float tickDelta) {
        if (mc.level == null || mc.player == null) return;
        if (!"3D Box".equals(mode.getMode())) return;

        for (Player player : mc.level.players()) {
            if (player == mc.player || !player.isAlive()) continue;

            boolean isFriend = FriendManager.getInstance().isFriend(player);
            int color = isFriend ? 0xFF22C55E : ThemeManager.getInstance().getAccentColor(player.getId() * 15);
            AABB lerpedBox = Render3DHelper.getLerpedBox(player, tickDelta).inflate(0.04);
            Render3DHelper.drawBoxColor(lerpedBox, color, 1.8f, true);
        }
    }

    @Override
    public void onRender2D(GuiGraphicsExtractor context, float tickDelta) {
        if (mc.level == null || mc.player == null) return;

        String currentMode = mode.getMode();
        boolean draw2DBox = "Corner 2D".equals(currentMode) || "2D Box".equals(currentMode);
        boolean isCorner = "Corner 2D".equals(currentMode);

        for (Player player : mc.level.players()) {
            if (player == mc.player || !player.isAlive()) continue;

            AABB lerpedBox = Render3DHelper.getLerpedBox(player, tickDelta).inflate(0.05);
            float[] rect = Render3DHelper.projectBox2D(lerpedBox);
            if (rect == null) continue;

            float minX = rect[0];
            float minY = rect[1];
            float maxX = rect[2];
            float maxY = rect[3];
            float bw = maxX - minX;
            float bh = maxY - minY;
            if (bw < 3.0f || bh < 5.0f) continue;

            boolean isFriend = FriendManager.getInstance().isFriend(player);
            int c1 = isFriend ? 0xFF22C55E : ThemeManager.getInstance().getAccentColor(0);
            int c2 = isFriend ? 0xFF4ADE80 : ThemeManager.getInstance().getAccentColor(120);

            // 1. 2D Corner Box or Full 2D Box with 1px black outline
            if (draw2DBox) {
                if (isCorner) {
                    drawCornerBox(context, minX, minY, bw, bh, c1, c2);
                } else {
                    drawFullBox2D(context, minX, minY, bw, bh, c1, c2);
                }
            }

            // 2. Vertical HealthBar on the left with HP value label
            if (healthBar.isEnabled()) {
                float realHp = TargetHudModule.getRealHealth(player);
                float hp = Math.max(0.0f, realHp + player.getAbsorptionAmount());
                float maxHp = Math.max(1.0f, Math.max(player.getMaxHealth(), realHp));
                float hpPct = Mth.clamp(hp / maxHp, 0.0f, 1.0f);

                int barX = Math.round(minX) - 5;
                int barY = Math.round(minY);
                int barH = Math.round(bh);
                int filledH = Math.max(1, Math.round(barH * hpPct));

                // 1px black background frame
                Render2DHelper.drawRect(context, barX - 1, barY - 1, 4, barH + 2, 0xD0000000);

                int hpColorTop = ColorUtils.interpolateColor(0xFFFF2E54, 0xFF22C55E, hpPct);
                int hpColorBot = ColorUtils.interpolateColor(c1, hpColorTop, 0.6f);
                Render2DHelper.drawGradientRect(context, barX, barY + (barH - filledH), 2, filledH, hpColorTop, hpColorBot);

                if (bh > 22.0f) {
                    String hpStr = String.valueOf((int) Math.ceil(hp));
                    float textW = Render2DHelper.getTextWidth(hpStr, 0.65f);
                    Render2DHelper.drawScaledText(
                            context,
                            hpStr,
                            barX - textW - 2.0f,
                            barY + (barH - filledH) - 2.0f,
                            hpColorTop,
                            0.65f,
                            true
                    );
                }
            }

            // 3. Liquid Glass NameTag above player with Nick, Ping, HP, and held items
            if (nameTags.isEnabled() && (NameTagsModule.getInstance() == null || !NameTagsModule.getInstance().isEnabled())) {
                renderNameTag(context, player, (minX + maxX) * 0.5f, minY, isFriend);
            }
        }
    }

    private void drawCornerBox(GuiGraphicsExtractor context, float x, float y, float w, float h, int topColor, int botColor) {
        int ix = Math.round(x);
        int iy = Math.round(y);
        int iw = Math.round(w);
        int ih = Math.round(h);
        int cw = Math.max(3, Math.min(14, iw / 4));
        int ch = Math.max(3, Math.min(16, ih / 4));
        int black = 0xD8000000;

        // Top-Left corner
        Render2DHelper.drawRect(context, ix - 1, iy - 1, cw + 2, 3, black);
        Render2DHelper.drawRect(context, ix - 1, iy - 1, 3, ch + 2, black);
        Render2DHelper.drawRect(context, ix, iy, cw, 1, topColor);
        Render2DHelper.drawRect(context, ix, iy, 1, ch, topColor);

        // Top-Right corner
        Render2DHelper.drawRect(context, ix + iw - cw - 1, iy - 1, cw + 2, 3, black);
        Render2DHelper.drawRect(context, ix + iw - 2, iy - 1, 3, ch + 2, black);
        Render2DHelper.drawRect(context, ix + iw - cw, iy, cw, 1, topColor);
        Render2DHelper.drawRect(context, ix + iw - 1, iy, 1, ch, topColor);

        // Bottom-Left corner
        Render2DHelper.drawRect(context, ix - 1, iy + ih - 2, cw + 2, 3, black);
        Render2DHelper.drawRect(context, ix - 1, iy + ih - ch - 1, 3, ch + 2, black);
        Render2DHelper.drawRect(context, ix, iy + ih - 1, cw, 1, botColor);
        Render2DHelper.drawRect(context, ix, iy + ih - ch, 1, ch, botColor);

        // Bottom-Right corner
        Render2DHelper.drawRect(context, ix + iw - cw - 1, iy + ih - 2, cw + 2, 3, black);
        Render2DHelper.drawRect(context, ix + iw - 2, iy + ih - ch - 1, 3, ch + 2, black);
        Render2DHelper.drawRect(context, ix + iw - cw, iy + ih - 1, cw, 1, botColor);
        Render2DHelper.drawRect(context, ix + iw - 1, iy + ih - ch, 1, ch, botColor);
    }

    private void drawFullBox2D(GuiGraphicsExtractor context, float x, float y, float w, float h, int topColor, int botColor) {
        int ix = Math.round(x);
        int iy = Math.round(y);
        int iw = Math.round(w);
        int ih = Math.round(h);
        int black = 0xD8000000;

        Render2DHelper.drawBorder(context, ix - 1, iy - 1, iw + 2, ih + 2, 1, black);
        Render2DHelper.drawBorder(context, ix + 1, iy + 1, iw - 2, ih - 2, 1, black);
        Render2DHelper.drawRect(context, ix, iy, iw, 1, topColor);
        Render2DHelper.drawRect(context, ix, iy + ih - 1, iw, 1, botColor);
        Render2DHelper.drawGradientRect(context, ix, iy, 1, ih, topColor, botColor);
        Render2DHelper.drawGradientRect(context, ix + iw - 1, iy, 1, ih, topColor, botColor);
    }

    private void renderNameTag(GuiGraphicsExtractor context, Player player, float centerX, float boxTopY, boolean isFriend) {
        String name = player.getName().getString();
        int ping = getPlayerPing(player);
        int hp = (int) Math.ceil(TargetHudModule.getRealHealth(player) + player.getAbsorptionAmount());

        String prefix = isFriend ? "[F] " : "";
        String infoText = prefix + name + "  " + ping + "ms  " + hp + "HP";
        float textScale = 0.78f;
        float textW = Render2DHelper.getTextWidth(infoText, textScale);

        float tagW = textW + 10.0f;
        float tagH = 13.0f;
        float tagX = centerX - tagW * 0.5f;
        float tagY = boxTopY - tagH - 4.0f;

        Render2DHelper.drawLiquidGlass(context, tagX, tagY, tagW, tagH, 4.0f, 215, 36);

        int textColor = isFriend ? 0xFF4ADE80 : 0xFFF8FAFC;
        Render2DHelper.drawScaledText(context, infoText, tagX + 5.0f, tagY + 3.2f, textColor, textScale, true);

        // Render held items above the NameTag (MainHand + OffHand)
        ItemStack mainHand = player.getMainHandItem();
        ItemStack offHand = player.getOffhandItem();
        boolean hasMain = mainHand != null && !mainHand.isEmpty();
        boolean hasOff = offHand != null && !offHand.isEmpty();

        if (hasMain || hasOff) {
            int count = (hasMain ? 1 : 0) + (hasOff ? 1 : 0);
            float iconSize = 12.0f;
            float totalW = count * iconSize + (count - 1) * 3.0f;
            float startX = centerX - totalW * 0.5f;
            float itemY = tagY - iconSize - 2.0f;

            Matrix3x2fStack matrices = context.pose();
            float curX = startX;
            if (hasMain) {
                drawScaledItem(context, matrices, mainHand, curX, itemY, 0.75f);
                curX += iconSize + 3.0f;
            }
            if (hasOff) {
                drawScaledItem(context, matrices, offHand, curX, itemY, 0.75f);
            }
        }
    }

    private void drawScaledItem(GuiGraphicsExtractor context, Matrix3x2fStack matrices, ItemStack stack, float x, float y, float scale) {
        matrices.pushMatrix();
        matrices.translate(x, y);
        matrices.scale(scale, scale);
        try {
            context.item(stack, 0, 0);
        } catch (Throwable ignored) {
        }
        matrices.popMatrix();
    }

    private int getPlayerPing(Player player) {
        if (mc.getConnection() == null) return 0;
        PlayerInfo entry = mc.getConnection().getPlayerInfo(player.getUUID());
        return entry != null ? entry.getLatency() : 0;
    }
}
