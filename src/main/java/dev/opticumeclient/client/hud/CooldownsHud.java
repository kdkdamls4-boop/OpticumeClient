package dev.opticumeclient.client.hud;

import dev.opticumeclient.client.modules.Category;
import dev.opticumeclient.client.modules.Module;
import dev.opticumeclient.client.render.ColorUtils;
import dev.opticumeclient.client.render.Render2DHelper;
import dev.opticumeclient.client.render.Theme;
import dev.opticumeclient.client.render.ThemeManager;
import dev.opticumeclient.client.setting.NumberSetting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemCooldowns;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Matrix3x2fStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Ported from Skycore / Deluxe / SystemDLC (CooldownsHud):
 * Draggable Liquid Glass HUD widget showing active item cooldowns (Ender Pearl, Chorus,
 * Golden Apple, Shield, Trident, Wind Charge, Firework) with real item icon and progress bar.
 */
public class CooldownsHud extends Module {
    private final NumberSetting posX = new NumberSetting("PosX", "X позиция", 8.0, 0.0, 1920.0, 1.0);
    private final NumberSetting posY = new NumberSetting("PosY", "Y позиция", 260.0, 0.0, 1080.0, 1.0);

    private final HudManager.DraggableWidget dragWidget;

    private static final Item[] TRACKED_ITEMS = {
            Items.ENDER_PEARL,
            Items.CHORUS_FRUIT,
            Items.GOLDEN_APPLE,
            Items.ENCHANTED_GOLDEN_APPLE,
            Items.SHIELD,
            Items.WIND_CHARGE,
            Items.FIREWORK_ROCKET,
            Items.TRIDENT
    };

    private record CooldownEntry(ItemStack stack, String name, float progress, float approxSeconds) {}

    private final Map<Item, Long> startTimestamps = new LinkedHashMap<>();
    private final Map<Item, Float> initialProgress = new LinkedHashMap<>();
    private final List<CooldownEntry> activeEntries = new ArrayList<>();
    private List<CooldownEntry> previewEntries;

    private List<CooldownEntry> getPreviewEntries() {
        if (previewEntries == null) {
            previewEntries = List.of(
                    new CooldownEntry(new ItemStack(Items.ENDER_PEARL), "Эндер-жемчуг", 0.68f, 10.2f),
                    new CooldownEntry(new ItemStack(Items.CHORUS_FRUIT), "Плод хоруса", 0.35f, 3.5f)
            );
        }
        return previewEntries;
    }

    public CooldownsHud() {
        super("CooldownsHUD", "Отображение активных кулдаунов перлов, хорусов, щита и геплов", Category.HUD);
        addSetting(posX);
        addSetting(posY);
        setEnabled(true);

        this.dragWidget = HudManager.getInstance().registerWidget(new HudManager.DraggableWidget(
                "CooldownsHUD",
                posX.getFloatValue(),
                posY.getFloatValue(),
                142.0f,
                50.0f,
                this::isEnabled,
                (newX, newY) -> {
                    posX.setValue((double) Math.round(newX));
                    posY.setValue((double) Math.round(newY));
                }
        ));
    }

    @Override
    public void onRender2D(GuiGraphicsExtractor context, float tickDelta) {
        if (!isEnabled() || mc.player == null) return;

        activeEntries.clear();
        ItemCooldowns cooldowns = mc.player.getCooldowns();
        long now = System.currentTimeMillis();

        for (Item item : TRACKED_ITEMS) {
            ItemStack st = new ItemStack(item);
            float pct = cooldowns.getCooldownPercent(st, tickDelta);
            if (pct > 0.005f) {
                if (!startTimestamps.containsKey(item) || pct > initialProgress.getOrDefault(item, 0.0f) + 0.05f) {
                    startTimestamps.put(item, now);
                    initialProgress.put(item, pct);
                }
                long elapsedMs = Math.max(1L, now - startTimestamps.get(item));
                float startPct = initialProgress.getOrDefault(item, 1.0f);
                float consumed = Math.max(0.001f, startPct - pct);
                float estRemainSec;
                if (consumed > 0.03f && elapsedMs > 120L) {
                    float ratePerSec = consumed / (elapsedMs / 1000.0f);
                    estRemainSec = pct / Math.max(0.02f, ratePerSec);
                } else {
                    float defaultTotalSec = item == Items.ENDER_PEARL ? 15.0f : (item == Items.SHIELD ? 5.0f : 1.5f);
                    estRemainSec = pct * defaultTotalSec;
                }
                activeEntries.add(new CooldownEntry(st, st.getHoverName().getString(), pct, Math.min(99.9f, estRemainSec)));
            } else {
                startTimestamps.remove(item);
                initialProgress.remove(item);
            }
        }

        boolean inChat = mc.gui.screen() instanceof ChatScreen;
        if (activeEntries.isEmpty() && !inChat) {
            return;
        }

        List<CooldownEntry> list = activeEntries.isEmpty() ? getPreviewEntries() : activeEntries;
        float w = 144.0f;
        float headerH = 19.0f;
        float rowH = 18.0f;
        for (CooldownEntry e : list) {
            float needed = Render2DHelper.getTextWidth(e.name(), 0.72f) + 52.0f;
            if (needed > w) w = needed;
        }
        float h = headerH + list.size() * rowH + 5.0f;

        dragWidget.syncExternalPosition(posX.getFloatValue(), posY.getFloatValue());
        dragWidget.setBounds(w, h);

        float x = dragWidget.getX();
        float y = dragWidget.getY();

        Theme theme = ThemeManager.getInstance().getCurrentTheme();
        int c1 = theme.getPrimaryColor();
        int c2 = theme.getSecondaryColor();

        HudPanelRenderer.beginWidget("CooldownsHUD");
        try {
        HudPanelRenderer.drawHeaderPanel(context, "CooldownsHUD", x, y, w, h, headerH, 6.0f,
                dev.opticumeclient.client.render.TextureAssets.DESIRE_ICON_COOLDOWNS, "Cooldowns", 1.0f);

        float curY = y + headerH + 3.0f;
        for (CooldownEntry e : list) {
            drawScaledItem(context, e.stack(), x + 6.5f, curY + 1.5f, 11.5f);
            Render2DHelper.drawScaledText(context, e.name(), x + 21.0f, curY + 3.0f, 0xFFF2F5FF, 0.72f, false);

            String secStr = String.format(Locale.US, "%.1fc", e.approxSeconds());
            float sw = Render2DHelper.getTextWidth(secStr, 0.70f);
            Render2DHelper.drawScaledText(context, secStr, x + w - sw - 6.5f, curY + 3.0f, c1, 0.70f, false);

            float barX = x + 21.0f;
            float barY = curY + 12.5f;
            float barW = w - 27.5f;
            Render2DHelper.drawRoundedRectFloat(context, barX, barY, barW, 2.2f, 1.1f, ColorUtils.rgba(255, 255, 255, 28));
            Render2DHelper.drawGradientRoundedRectFloat(context, barX, barY, Math.max(2.5f, barW * e.progress()), 2.2f, 1.1f, c1, c1, c2, c2);
            curY += rowH;
        }
        } finally {
            HudPanelRenderer.endWidget();
        }
    }

    private static void drawScaledItem(GuiGraphicsExtractor context, ItemStack stack, float x, float y, float size) {
        if (stack == null || stack.isEmpty()) return;
        Matrix3x2fStack matrices = context.pose();
        float scale = size / 16.0f;
        matrices.pushMatrix();
        matrices.translate(x, y);
        matrices.scale(scale, scale);
        try {
            context.fakeItem(stack, 0, 0);
        } catch (Throwable ignored) {
        }
        matrices.popMatrix();
    }
}
