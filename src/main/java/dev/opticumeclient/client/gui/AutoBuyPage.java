package dev.opticumeclient.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.opticumeclient.client.autobuy.AutoBuyConfig;
import dev.opticumeclient.client.autobuy.AutoBuyEngine;
import dev.opticumeclient.client.autobuy.AutoBuyPresets;
import dev.opticumeclient.client.autobuy.AutoBuyReport;
import dev.opticumeclient.client.autobuy.ItemDatabase;
import dev.opticumeclient.client.autobuy.ItemEntry;
import dev.opticumeclient.client.modules.other.AutoBuyModule;
import dev.opticumeclient.client.render.ColorUtils;
import dev.opticumeclient.client.render.Render2DHelper;
import dev.opticumeclient.client.render.ThemeManager;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Matrix3x2fStack;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Desire-inspired AutoBuy Catalog & Presets page integrated directly inside {@link ClickGuiScreen}.
 */
public final class AutoBuyPage {
    public enum FilterMode {
        ALL("Все"),
        ENABLED("Вкл"),
        DISABLED("Выкл");

        final String label;

        FilterMode(String label) {
            this.label = label;
        }
    }

    public enum Section {
        ALL("Все предметы", Items.CHEST),
        WEAPON("Оружие", Items.NETHERITE_SWORD),
        ARMOR("Броня", Items.NETHERITE_CHESTPLATE),
        TOOLS("Инструменты", Items.NETHERITE_PICKAXE),
        TALISMANS("Талисманы", Items.TOTEM_OF_UNDYING),
        CONSUMABLES("Расходники", Items.EXPERIENCE_BOTTLE),
        CUSTOM("Кастомные", Items.NETHER_STAR),
        RESOURCES("Ресурсы", Items.NETHERITE_INGOT);

        final String label;
        final ItemStack icon;

        Section(String label, Item icon) {
            this.label = label;
            this.icon = new ItemStack(icon);
        }

        static Section of(ItemEntry e) {
            String id = e.itemId == null ? "" : e.itemId.toLowerCase(Locale.ROOT);
            int colon = id.indexOf(':');
            if (colon >= 0) id = id.substring(colon + 1);

            if (e.matchAnySword || id.endsWith("_sword") || id.equals("bow") || id.equals("crossbow")
                    || id.equals("trident") || id.equals("mace")) return WEAPON;
            if (e.isArmor() || id.equals("shield")) return ARMOR;
            if (id.endsWith("_pickaxe") || id.endsWith("_axe") || id.endsWith("_shovel") || id.endsWith("_hoe")
                    || id.equals("shears") || id.equals("fishing_rod") || id.equals("flint_and_steel")) return TOOLS;
            if (id.equals("totem_of_undying") || id.equals("player_head")) return TALISMANS;
            if (id.endsWith("potion") || id.equals("experience_bottle") || id.endsWith("golden_apple")
                    || id.equals("ender_pearl") || id.equals("chorus_fruit") || id.equals("firework_rocket")
                    || id.endsWith("_spawn_egg")) return CONSUMABLES;
            if (e.requireCustom) return CUSTOM;
            return RESOURCES;
        }
    }

    private enum Tier {
        INFINITY(0xFF5AFF78, 0xFF106928),
        ETERNITY(0xFFCD78FF, 0xFF4E188C),
        STINGER(0xFFFF4848, 0xFF760812);

        final int bright;
        final int dark;

        Tier(int bright, int dark) {
            this.bright = bright;
            this.dark = dark;
        }
    }

    private static final long PRICE_STEP = 50_000L;
    private static final float MARQUEE_SPEED = 24.0f;
    private static final float MARQUEE_DELAY = 0.35f;

    private Section section = Section.ALL;
    private FilterMode filter = FilterMode.ALL;
    private String searchQuery = "";
    private boolean searchFocused = false;

    private final Map<String, ItemStack> iconCache = new HashMap<>();
    private final Map<ItemEntry, float[]> anims = new HashMap<>();
    private final Map<Section, float[]> sectionAnims = new EnumMap<>(Section.class);
    private final Map<Section, Integer> counts = new EnumMap<>(Section.class);
    private final List<ItemEntry> shown = new ArrayList<>();

    private ItemEntry editingItem = null;
    private String priceBuffer = "";

    private float scroll = 0.0f;
    private float targetScroll = 0.0f;
    private float maxScroll = 0.0f;

    private float lastX, lastY, lastW, lastH;

    public void refresh() {
        AutoBuyConfig.ensureLoaded();
        if (ItemDatabase.LIST.isEmpty()) {
            ItemDatabase.reload();
        }
        if (AutoBuyConfig.cfg().entries.isEmpty()) {
            AutoBuyConfig.cfg().reconcileWithDatabase();
            AutoBuyConfig.save();
        }
    }

    public void resetScroll() {
        scroll = 0.0f;
        targetScroll = 0.0f;
    }

    public void unfocus() {
        stopEditing();
        searchFocused = false;
    }

    public boolean isTyping() {
        return editingItem != null || searchFocused;
    }

    public void render(GuiGraphicsExtractor context, float dt, float x, float y, float w, float h,
                       int mouseX, int mouseY, float alpha) {
        refresh();
        lastX = x;
        lastY = y;
        lastW = w;
        lastH = h;

        scroll += (targetScroll - scroll) * (1.0f - (float) Math.exp(-16.0f * dt));
        collect();

        float pad = 8.0f;
        float ix = x + pad;
        float iy = y + pad;
        float iw = w - pad * 2.0f;
        float ih = h - pad * 2.0f;

        float sideW = Math.min(130.0f, Math.max(108.0f, iw * 0.24f));
        float mainX = ix + sideW + 8.0f;
        float mainW = ix + iw - mainX;

        drawSidebar(context, dt, ix, iy, sideW, ih, mouseX, mouseY, alpha);
        Render2DHelper.drawRect(context, ix + sideW + 3.5f, iy, 1.0f, ih,
                SystemTheme.fade(SystemTheme.border(), alpha * 0.65f));

        float topBarH = 22.0f;
        drawToolbar(context, mainX, iy, mainW, topBarH, mouseX, mouseY, alpha);

        float secBarY = iy + topBarH + 5.0f;
        float secBarH = 18.0f;
        drawFilterAndPresetRow(context, mainX, secBarY, mainW, secBarH, mouseX, mouseY, alpha);

        float listY = secBarY + secBarH + 6.0f;
        float listH = Math.max(40.0f, iy + ih - listY);
        drawGrid(context, dt, mainX, listY, mainW, listH, mouseX, mouseY, alpha);
    }

    private void drawSidebar(GuiGraphicsExtractor context, float dt, float x, float y, float w, float h,
                             int mouseX, int mouseY, float alpha) {
        int accent = ThemeManager.getInstance().getAccentColor(0);
        List<Section> sections = visibleSections();
        float rowH = 17.0f;
        float gap = 2.0f;

        for (int i = 0; i < sections.size(); i++) {
            Section s = sections.get(i);
            float ry = y + i * (rowH + gap);
            float[] anim = sectionAnims.computeIfAbsent(s, k -> new float[2]);
            boolean hov = isInside(mouseX, mouseY, x, ry, w, rowH);
            anim[0] += ((hov ? 1.0f : 0.0f) - anim[0]) * (1.0f - (float) Math.exp(-16.0f * dt));
            anim[1] += ((s == section ? 1.0f : 0.0f) - anim[1]) * (1.0f - (float) Math.exp(-16.0f * dt));

            if (anim[0] > 0.01f || anim[1] > 0.01f) {
                int fill = s == section ? ColorUtils.withAlpha(accent, 34)
                        : ColorUtils.withAlphaFactor(SystemTheme.elevated(), 0.7f * anim[0]);
                Render2DHelper.drawRoundedRectFloat(context, x, ry, w, rowH, 4.5f,
                        ColorUtils.withAlphaFactor(fill, alpha));
                if (s == section) {
                    Render2DHelper.drawRoundedOutlineFloat(context, x, ry, w, rowH, 4.5f, 0.8f,
                            ColorUtils.withAlphaFactor(accent, alpha * 0.72f));
                }
            }

            drawScaledItem(context, s.icon, x + 4.5f, ry + 3.0f, 10.5f);

            int textCol = s == section ? SystemTheme.foreground() : (hov ? SystemTheme.foreground() : SystemTheme.mutedFg());
            Render2DHelper.drawScaledText(context, Render2DHelper.trimToWidth(s.label, 0.68f, w - 42.0f),
                    x + 17.5f, ry + 5.0f, SystemTheme.fade(textCol, alpha), 0.68f, false);

            String cnt = String.valueOf(counts.getOrDefault(s, 0));
            float cw = Math.max(14.0f, Render2DHelper.getTextWidth(cnt, 0.60f) + 6.0f);
            float cx = x + w - cw - 3.5f;
            Render2DHelper.drawRoundedRectFloat(context, cx, ry + 3.5f, cw, 10.0f, 3.0f,
                    s == section ? ColorUtils.withAlpha(accent, Math.round(48 * alpha))
                            : SystemTheme.fade(SystemTheme.elevated(), alpha));
            float tw = Render2DHelper.getTextWidth(cnt, 0.60f);
            Render2DHelper.drawScaledText(context, cnt, cx + (cw - tw) * 0.5f, ry + 5.5f,
                    ColorUtils.withAlphaFactor(s == section ? accent : SystemTheme.mutedFg(), alpha), 0.60f, false);
        }

        // Bottom active summary + stats
        AutoBuyConfig cfg = AutoBuyConfig.cfg();
        int total = cfg.entries.size();
        int enabled = 0;
        for (ItemEntry e : cfg.entries) {
            if (e != null && e.enabled) enabled++;
        }

        float boxH = 44.0f;
        float by = y + h - boxH;
        Render2DHelper.drawRoundedRectFloat(context, x, by, w, boxH, 5.5f,
                SystemTheme.fade(SystemTheme.elevated(), alpha * 0.85f));
        Render2DHelper.drawRoundedOutlineFloat(context, x, by, w, boxH, 5.5f, 0.8f,
                SystemTheme.fade(SystemTheme.border(), alpha * 0.7f));

        Render2DHelper.drawScaledText(context, "Активно", x + 6.0f, by + 5.5f,
                SystemTheme.fade(SystemTheme.mutedFg(), alpha), 0.64f, false);
        String frac = enabled + " / " + total;
        float fw = Render2DHelper.getTextWidth(frac, 0.64f);
        Render2DHelper.drawScaledText(context, frac, x + w - fw - 6.0f, by + 5.5f,
                SystemTheme.fade(SystemTheme.foreground(), alpha), 0.64f, false);

        float barW = w - 12.0f;
        Render2DHelper.drawRoundedRectFloat(context, x + 6.0f, by + 15.0f, barW, 3.0f, 1.5f,
                SystemTheme.fade(SystemTheme.bg(), alpha));
        if (total > 0 && enabled > 0) {
            float fillW = Math.max(3.0f, barW * enabled / (float) total);
            Render2DHelper.drawRoundedRectFloat(context, x + 6.0f, by + 15.0f, fillW, 3.0f, 1.5f,
                    ColorUtils.withAlphaFactor(accent, alpha));
        }

        long profit = cfg.totalEarned - cfg.totalSpent;
        String profitTxt = (profit >= 0 ? "+" : "") + AutoBuyReport.formatMoney(profit);
        Render2DHelper.drawScaledText(context, "Профит:", x + 6.0f, by + 23.0f,
                SystemTheme.fade(SystemTheme.mutedFg(), alpha), 0.62f, false);
        float pw = Render2DHelper.getTextWidth(profitTxt, 0.62f);
        int profCol = profit > 0 ? 0xFF4ADE80 : (profit < 0 ? 0xFFF87171 : SystemTheme.foreground());
        Render2DHelper.drawScaledText(context, profitTxt, x + w - pw - 6.0f, by + 23.0f,
                ColorUtils.withAlphaFactor(profCol, alpha), 0.62f, false);

        String ioTxt = "Потр: " + AutoBuyReport.formatMoney(cfg.totalSpent);
        Render2DHelper.drawScaledText(context, Render2DHelper.trimToWidth(ioTxt, 0.58f, w - 12.0f),
                x + 6.0f, by + 33.0f, SystemTheme.fade(SystemTheme.mutedFg(), alpha * 0.85f), 0.58f, false);
    }

    private void drawToolbar(GuiGraphicsExtractor context, float x, float y, float w, float h,
                             int mouseX, int mouseY, float alpha) {
        int accent = ThemeManager.getInstance().getAccentColor(0);
        AutoBuyConfig cfg = AutoBuyConfig.cfg();
        boolean parsing = AutoBuyEngine.INSTANCE.parser.isActive();

        // Action buttons right-aligned: [AutoBuy ON/OFF] [Парсер] [Вкл все] [Выкл все]
        float gap = 4.0f;
        float disW = 48.0f;
        float enW = 46.0f;
        float parseW = 54.0f;
        float abW = 62.0f;

        float disX = x + w - disW;
        float enX = disX - gap - enW;
        float parseX = enX - gap - parseW;
        float abX = parseX - gap - abW;

        drawActionButton(context, disX, y + 1.0f, disW, h - 2.0f, "Выкл все", false,
                isInside(mouseX, mouseY, disX, y + 1.0f, disW, h - 2.0f), 0xFFEF4444, alpha);
        drawActionButton(context, enX, y + 1.0f, enW, h - 2.0f, "Вкл все", false,
                isInside(mouseX, mouseY, enX, y + 1.0f, enW, h - 2.0f), accent, alpha);
        drawActionButton(context, parseX, y + 1.0f, parseW, h - 2.0f, parsing ? "Стоп" : "Парсер", parsing,
                isInside(mouseX, mouseY, parseX, y + 1.0f, parseW, h - 2.0f), 0xFFF59E0B, alpha);
        drawActionButton(context, abX, y + 1.0f, abW, h - 2.0f, cfg.enabled ? "AutoBuy ON" : "AutoBuy OFF",
                cfg.enabled, isInside(mouseX, mouseY, abX, y + 1.0f, abW, h - 2.0f), SystemTheme.SUCCESS, alpha);

        // Search input box on left
        float searchW = Math.max(60.0f, abX - gap - x);
        boolean sHov = isInside(mouseX, mouseY, x, y + 1.0f, searchW, h - 2.0f);
        Render2DHelper.drawRoundedRectFloat(context, x, y + 1.0f, searchW, h - 2.0f, 5.0f,
                SystemTheme.fade(searchFocused ? SystemTheme.elevated() : (sHov ? SystemTheme.surface() : SystemTheme.bg()), alpha));
        Render2DHelper.drawRoundedOutlineFloat(context, x, y + 1.0f, searchW, h - 2.0f, 5.0f, 0.9f,
                searchFocused ? ColorUtils.withAlphaFactor(accent, alpha) : SystemTheme.fade(SystemTheme.border(), alpha));

        String caret = searchFocused && ((System.currentTimeMillis() / 450L) % 2 == 0) ? "|" : "";
        String shownText = searchQuery.isEmpty() && !searchFocused ? "Поиск предмета..." : searchQuery + caret;
        int txtCol = searchQuery.isEmpty() && !searchFocused ? SystemTheme.mutedFg() : SystemTheme.foreground();
        Render2DHelper.drawScaledText(context, Render2DHelper.trimToWidth(shownText, 0.68f, searchW - 12.0f),
                x + 6.0f, y + 7.5f, SystemTheme.fade(txtCol, alpha), 0.68f, false);
    }

    private void drawFilterAndPresetRow(GuiGraphicsExtractor context, float x, float y, float w, float h,
                                        int mouseX, int mouseY, float alpha) {
        int accent = ThemeManager.getInstance().getAccentColor(0);

        // Filter segmented bar on left
        FilterMode[] modes = FilterMode.values();
        float segW = 34.0f;
        float barW = segW * modes.length + 4.0f;
        Render2DHelper.drawRoundedRectFloat(context, x, y, barW, h, 4.5f,
                SystemTheme.fade(SystemTheme.bg(), alpha));
        Render2DHelper.drawRoundedOutlineFloat(context, x, y, barW, h, 4.5f, 0.8f,
                SystemTheme.fade(SystemTheme.border(), alpha * 0.7f));
        for (FilterMode mode : modes) {
            float sx = x + 2.0f + mode.ordinal() * segW;
            boolean active = filter == mode;
            boolean hov = isInside(mouseX, mouseY, sx, y + 2.0f, segW, h - 4.0f);
            if (active || hov) {
                Render2DHelper.drawRoundedRectFloat(context, sx, y + 2.0f, segW, h - 4.0f, 3.5f,
                        active ? ColorUtils.withAlpha(accent, Math.round(52 * alpha))
                                : SystemTheme.fade(SystemTheme.elevated(), alpha * 0.6f));
            }
            float tw = Render2DHelper.getTextWidth(mode.label, 0.62f);
            Render2DHelper.drawScaledText(context, mode.label, sx + (segW - tw) * 0.5f, y + 6.0f,
                    ColorUtils.withAlphaFactor(active ? accent : (hov ? SystemTheme.foreground() : SystemTheme.mutedFg()), alpha),
                    0.62f, false);
        }

        // Preset buttons on right: [HW Мета] [HW Расход] [FT Фарм] [Сброс]
        AutoBuyPresets.Preset[] presets = AutoBuyPresets.Preset.values();
        float px = x + w;
        for (int i = presets.length - 1; i >= 0; i--) {
            AutoBuyPresets.Preset p = presets[i];
            float pw = Math.max(40.0f, Render2DHelper.getTextWidth(p.label, 0.60f) + 12.0f);
            px -= pw;
            boolean hov = isInside(mouseX, mouseY, px, y, pw, h);
            drawActionButton(context, px, y, pw, h, p.label, false, hov,
                    p == AutoBuyPresets.Preset.CLEAR ? 0xFFEF4444 : accent, alpha);
            px -= 4.0f;
        }
    }

    private void drawGrid(GuiGraphicsExtractor context, float dt, float x, float y, float w, float h,
                          int mouseX, int mouseY, float alpha) {
        int cols = w >= 320.0f ? 3 : 2;
        float gap = 6.0f;
        float cardW = (w - gap * (cols - 1)) / cols;
        float cardH = 54.0f;

        int rows = (shown.size() + cols - 1) / cols;
        float contentH = Math.max(0.0f, rows * (cardH + gap) - gap);
        maxScroll = Math.max(0.0f, contentH - h);
        targetScroll = Mth.clamp(targetScroll, 0.0f, maxScroll);
        scroll = Mth.clamp(scroll, 0.0f, maxScroll);

        if (shown.isEmpty()) {
            Render2DHelper.drawCenteredText(context,
                    searchQuery.isEmpty() ? "В этом разделе пусто" : "Ничего не найдено",
                    x + w * 0.5f, y + h * 0.42f, SystemTheme.fade(SystemTheme.mutedFg(), alpha), false);
            return;
        }

        context.enableScissor(Math.round(x - 1.0f), Math.round(y), Math.round(x + w + 2.0f), Math.round(y + h));
        boolean live = isInside(mouseX, mouseY, x, y, w, h);
        for (int i = 0; i < shown.size(); i++) {
            int col = i % cols;
            int row = i / cols;
            float cx = x + col * (cardW + gap);
            float cy = y + row * (cardH + gap) - scroll;
            if (cy + cardH < y || cy > y + h) continue;
            drawCard(context, dt, shown.get(i), cx, cy, cardW, cardH, live, mouseX, mouseY, alpha);
        }
        context.disableScissor();

        if (maxScroll > 1.0f) {
            float trackH = Math.max(20.0f, h - 8.0f);
            float thumbH = Math.max(18.0f, trackH * (h / (h + maxScroll)));
            float thumbY = y + 4.0f + (scroll / maxScroll) * (trackH - thumbH);
            Render2DHelper.drawRoundedRectFloat(context, x + w - 2.0f, thumbY, 2.5f, thumbH, 1.2f,
                    SystemTheme.fade(SystemTheme.border(), alpha));
        }
    }

    private void drawCard(GuiGraphicsExtractor context, float dt, ItemEntry item,
                          float cx, float cy, float cw, float ch,
                          boolean live, int mouseX, int mouseY, float alpha) {
        int accent = ThemeManager.getInstance().getAccentColor(0);
        // [0] hover, [1] enabled, [2] marqueeOffset, [3] hoverTime
        float[] anim = anims.computeIfAbsent(item, k -> new float[]{0f, item.enabled ? 1f : 0f, 0f, 0f});
        boolean hovered = live && isInside(mouseX, mouseY, cx, cy, cw, ch);
        anim[0] += ((hovered ? 1.0f : 0.0f) - anim[0]) * (1.0f - (float) Math.exp(-15.0f * dt));
        anim[1] += ((item.enabled ? 1.0f : 0.0f) - anim[1]) * (1.0f - (float) Math.exp(-15.0f * dt));

        int baseBg = ColorUtils.interpolateColor(SystemTheme.surface(), SystemTheme.elevated(), anim[0] * 0.7f);
        if (anim[1] > 0.02f) {
            baseBg = ColorUtils.interpolateColor(baseBg, ColorUtils.withAlpha(accent, 42), anim[1] * 0.35f);
        }
        Render2DHelper.drawRoundedRectFloat(context, cx, cy, cw, ch, 5.5f, SystemTheme.fade(baseBg, alpha));
        Render2DHelper.drawRoundedOutlineFloat(context, cx, cy, cw, ch, 5.5f, 0.85f,
                item.enabled ? ColorUtils.withAlphaFactor(accent, alpha * (0.45f + 0.45f * anim[1]))
                        : SystemTheme.fade(SystemTheme.border(), alpha * 0.7f));

        float pad = 6.0f;
        float slotSize = 20.0f;
        float sx = cx + pad;
        float sy = cy + pad;
        Render2DHelper.drawRoundedRectFloat(context, sx, sy, slotSize, slotSize, 4.0f,
                item.enabled ? ColorUtils.withAlpha(accent, Math.round(32 * alpha))
                        : SystemTheme.fade(SystemTheme.bg(), alpha));
        drawScaledItem(context, getStack(item), sx + 3.0f, sy + 3.0f, 14.0f);

        // Toggle switch on top-right
        float togW = 16.0f;
        float togH = 9.0f;
        float tx = cx + cw - pad - togW;
        float ty = sy + 2.0f;
        Render2DHelper.drawRoundedRectFloat(context, tx, ty, togW, togH, 4.5f,
                item.enabled ? ColorUtils.withAlphaFactor(accent, alpha)
                        : SystemTheme.fade(SystemTheme.border(), alpha));
        float knobX = tx + 1.5f + anim[1] * (togW - 9.0f);
        Render2DHelper.drawRoundedRectFloat(context, knobX, ty + 1.5f, 6.0f, 6.0f, 3.0f,
                SystemTheme.fade(0xFFFFFFFF, alpha));

        // Name (with tier shimmer) + market price
        float textX = sx + slotSize + 6.0f;
        float textMaxW = Math.max(20.0f, tx - textX - 4.0f);
        String name = item.displayName != null && !item.displayName.isBlank() ? item.displayName : item.itemId;
        if (name == null) name = "Предмет";

        drawItemTitle(context, dt, name, anim, hovered, textX, sy + 2.0f, textMaxW,
                SystemTheme.fade(item.enabled ? accent : SystemTheme.foreground(), alpha), alpha);

        boolean hasMarket = item.marketPrice > 0L;
        String marketStr = hasMarket ? "Рынок " + formatPrice(item.marketPrice) + "$" : "Рынок —";
        int marketCol = hasMarket ? 0xFF6EC882 : SystemTheme.mutedFg();
        Render2DHelper.drawScaledText(context, Render2DHelper.trimToWidth(marketStr, 0.56f, textMaxW),
                textX, sy + 12.0f, ColorUtils.withAlphaFactor(marketCol, alpha), 0.56f, false);

        // Bottom row: [Price Field] [+50k] [0]
        float fy = cy + ch - pad - 15.0f;
        float fh = 15.0f;
        float zeroW = 16.0f;
        float plusW = 28.0f;
        float zeroX = cx + cw - pad - zeroW;
        float plusX = zeroX - 3.0f - plusW;
        float fieldX = cx + pad;
        float fieldW = Math.max(30.0f, plusX - 3.0f - fieldX);

        boolean editing = editingItem == item;
        Render2DHelper.drawRoundedRectFloat(context, fieldX, fy, fieldW, fh, 3.5f,
                editing ? ColorUtils.withAlpha(accent, Math.round(48 * alpha))
                        : SystemTheme.fade(SystemTheme.bg(), alpha));
        Render2DHelper.drawRoundedOutlineFloat(context, fieldX, fy, fieldW, fh, 3.5f, 0.7f,
                editing ? ColorUtils.withAlphaFactor(accent, alpha)
                        : SystemTheme.fade(SystemTheme.border(), alpha * 0.7f));

        String priceText;
        int priceCol;
        if (editing) {
            priceText = (priceBuffer.isEmpty() ? "" : formatPrice(Long.parseLong(priceBuffer)))
                    + ((System.currentTimeMillis() / 450L) % 2 == 0 ? "|" : "");
            priceCol = SystemTheme.foreground();
        } else if (item.maxUnitPrice > 0L) {
            priceText = "до " + formatPrice(item.maxUnitPrice) + "$";
            priceCol = SystemTheme.foreground();
        } else {
            priceText = "Любая цена";
            priceCol = SystemTheme.mutedFg();
        }
        Render2DHelper.drawScaledText(context, Render2DHelper.trimToWidth(priceText, 0.58f, fieldW - 8.0f),
                fieldX + 4.0f, fy + 4.5f, SystemTheme.fade(priceCol, alpha), 0.58f, false);

        boolean pHov = live && isInside(mouseX, mouseY, plusX, fy, plusW, fh);
        drawActionButton(context, plusX, fy, plusW, fh, "+50k", false, pHov, accent, alpha);

        boolean zHov = live && isInside(mouseX, mouseY, zeroX, fy, zeroW, fh);
        drawActionButton(context, zeroX, fy, zeroW, fh, "0", false, zHov, 0xFFEF4444, alpha);
    }

    private void drawItemTitle(GuiGraphicsExtractor context, float dt, String name, float[] anim, boolean hovered,
                               float nx, float ny, float maxW, int color, float alpha) {
        float fullW = Render2DHelper.getTextWidth(name, 0.66f);
        anim[3] = hovered ? anim[3] + dt : 0.0f;
        if (fullW <= maxW) {
            anim[2] = 0.0f;
            drawRichTierText(context, name, nx, ny, 0.66f, color, alpha);
            return;
        }
        float loop = fullW + 14.0f;
        if (hovered && anim[3] > MARQUEE_DELAY) {
            anim[2] = (anim[2] + MARQUEE_SPEED * dt) % loop;
        } else if (anim[2] > 0.0f) {
            anim[2] = Math.max(0.0f, anim[2] - MARQUEE_SPEED * 2.5f * dt);
        }

        if (anim[2] <= 0.1f) {
            drawRichTierText(context, Render2DHelper.trimToWidth(name, 0.66f, maxW), nx, ny, 0.66f, color, alpha);
            return;
        }

        context.enableScissor(Math.round(nx), Math.round(ny - 2.0f), Math.round(nx + maxW), Math.round(ny + 11.0f));
        drawRichTierText(context, name, nx - anim[2], ny, 0.66f, color, alpha);
        drawRichTierText(context, name, nx - anim[2] + loop, ny, 0.66f, color, alpha);
        context.disableScissor();
    }

    private static void drawRichTierText(GuiGraphicsExtractor context, String value, float tx, float ty,
                                         float scale, int baseColor, float alpha) {
        String upper = value.toUpperCase(Locale.ROOT);
        int at = -1;
        Tier tier = null;
        for (Tier t : Tier.values()) {
            int idx = upper.indexOf(t.name());
            if (idx >= 0 && (at < 0 || idx < at)) {
                at = idx;
                tier = t;
            }
        }
        if (tier == null) {
            Render2DHelper.drawScaledText(context, value, tx, ty, baseColor, scale, false);
            return;
        }
        int end = at + tier.name().length();
        String before = value.substring(0, at);
        String word = value.substring(at, end);
        String after = value.substring(end);
        if (!before.isEmpty()) {
            Render2DHelper.drawScaledText(context, before, tx, ty, baseColor, scale, false);
            tx += Render2DHelper.getTextWidth(before, scale);
        }
        float pulse = (float) ((Math.sin(System.currentTimeMillis() / 420.0) + 1.0) * 0.5);
        int tierCol = ColorUtils.interpolateColor(tier.dark, tier.bright, 0.35f + 0.65f * pulse);
        Render2DHelper.drawScaledText(context, word, tx, ty, ColorUtils.withAlphaFactor(tierCol, alpha), scale, false);
        tx += Render2DHelper.getTextWidth(word, scale);
        if (!after.isEmpty()) {
            drawRichTierText(context, after, tx, ty, scale, baseColor, alpha);
        }
    }

    private static void drawActionButton(GuiGraphicsExtractor context, float x, float y, float w, float h,
                                         String label, boolean active, boolean hovered, int accentColor, float alpha) {
        int bg = active ? ColorUtils.withAlpha(accentColor, 46)
                : (hovered ? SystemTheme.elevated() : SystemTheme.bg());
        Render2DHelper.drawRoundedRectFloat(context, x, y, w, h, 4.0f, SystemTheme.fade(bg, alpha));
        Render2DHelper.drawRoundedOutlineFloat(context, x, y, w, h, 4.0f, 0.75f,
                (active || hovered) ? ColorUtils.withAlphaFactor(accentColor, alpha * 0.8f)
                        : SystemTheme.fade(SystemTheme.border(), alpha * 0.7f));
        float tw = Render2DHelper.getTextWidth(label, 0.58f);
        int txtCol = active ? accentColor : (hovered ? SystemTheme.foreground() : SystemTheme.mutedFg());
        Render2DHelper.drawScaledText(context, label, x + (w - tw) * 0.5f, y + (h - 6.0f) * 0.5f,
                ColorUtils.withAlphaFactor(txtCol, alpha), 0.58f, false);
    }

    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0 && button != 1) return false;
        refresh();
        collect();

        float pad = 8.0f;
        float ix = lastX + pad;
        float iy = lastY + pad;
        float iw = lastW - pad * 2.0f;
        float sideW = Math.min(130.0f, Math.max(108.0f, iw * 0.24f));
        float mainX = ix + sideW + 8.0f;
        float mainW = ix + iw - mainX;

        // Sidebar section clicks
        List<Section> sections = visibleSections();
        float rowH = 17.0f;
        float rowGap = 2.0f;
        for (int i = 0; i < sections.size(); i++) {
            float ry = iy + i * (rowH + rowGap);
            if (isInside(mx, my, ix, ry, sideW, rowH)) {
                unfocus();
                if (section != sections.get(i)) {
                    section = sections.get(i);
                    resetScroll();
                }
                return true;
            }
        }

        // Toolbar clicks
        float topBarH = 22.0f;
        float gap = 4.0f;
        float disW = 48.0f;
        float enW = 46.0f;
        float parseW = 54.0f;
        float abW = 62.0f;
        float disX = mainX + mainW - disW;
        float enX = disX - gap - enW;
        float parseX = enX - gap - parseW;
        float abX = parseX - gap - abW;
        float searchW = Math.max(60.0f, abX - gap - mainX);

        if (isInside(mx, my, disX, iy + 1.0f, disW, topBarH - 2.0f)) {
            setAllShown(false);
            return true;
        }
        if (isInside(mx, my, enX, iy + 1.0f, enW, topBarH - 2.0f)) {
            setAllShown(true);
            return true;
        }
        if (isInside(mx, my, parseX, iy + 1.0f, parseW, topBarH - 2.0f)) {
            unfocus();
            if (AutoBuyEngine.INSTANCE.parser.isActive()) AutoBuyEngine.INSTANCE.parser.stop();
            else AutoBuyEngine.INSTANCE.parser.start();
            return true;
        }
        if (isInside(mx, my, abX, iy + 1.0f, abW, topBarH - 2.0f)) {
            unfocus();
            AutoBuyConfig cfg = AutoBuyConfig.cfg();
            cfg.enabled = !cfg.enabled;
            AutoBuyModule mod = AutoBuyModule.getInstance();
            if (mod != null) mod.setEnabled(cfg.enabled);
            AutoBuyConfig.save();
            return true;
        }
        if (isInside(mx, my, mainX, iy + 1.0f, searchW, topBarH - 2.0f)) {
            stopEditing();
            if (button == 1) searchQuery = "";
            searchFocused = true;
            return true;
        }

        // Filter & preset row
        float secBarY = iy + topBarH + 5.0f;
        float secBarH = 18.0f;
        float segW = 34.0f;
        for (FilterMode mode : FilterMode.values()) {
            float sx = mainX + 2.0f + mode.ordinal() * segW;
            if (isInside(mx, my, sx, secBarY + 2.0f, segW, secBarH - 4.0f)) {
                unfocus();
                filter = mode;
                resetScroll();
                return true;
            }
        }
        AutoBuyPresets.Preset[] presets = AutoBuyPresets.Preset.values();
        float px = mainX + mainW;
        for (int i = presets.length - 1; i >= 0; i--) {
            AutoBuyPresets.Preset p = presets[i];
            float pw = Math.max(40.0f, Render2DHelper.getTextWidth(p.label, 0.60f) + 12.0f);
            px -= pw;
            if (isInside(mx, my, px, secBarY, pw, secBarH)) {
                unfocus();
                AutoBuyPresets.apply(p);
                resetScroll();
                return true;
            }
            px -= 4.0f;
        }

        // Cards grid clicks
        float listY = secBarY + secBarH + 6.0f;
        float listH = Math.max(40.0f, iy + lastH - pad * 2.0f - listY);
        int cols = mainW >= 320.0f ? 3 : 2;
        float cardGap = 6.0f;
        float cardW = (mainW - cardGap * (cols - 1)) / cols;
        float cardH = 54.0f;

        if (isInside(mx, my, mainX, listY, mainW, listH)) {
            for (int i = 0; i < shown.size(); i++) {
                float cx = mainX + (i % cols) * (cardW + cardGap);
                float cy = listY + (i / cols) * (cardH + cardGap) - scroll;
                if (!isInside(mx, my, cx, cy, cardW, cardH)) continue;

                ItemEntry entry = shown.get(i);
                float cardPad = 6.0f;
                float fy = cy + cardH - cardPad - 15.0f;
                float fh = 15.0f;
                float zeroW = 16.0f;
                float plusW = 28.0f;
                float zeroX = cx + cardW - cardPad - zeroW;
                float plusX = zeroX - 3.0f - plusW;
                float fieldX = cx + cardPad;
                float fieldW = Math.max(30.0f, plusX - 3.0f - fieldX);

                if (isInside(mx, my, fieldX, fy, fieldW, fh)) {
                    unfocus();
                    if (button == 1) {
                        entry.maxUnitPrice = 0L;
                        AutoBuyConfig.save();
                    } else {
                        startEditing(entry);
                    }
                    return true;
                }
                if (isInside(mx, my, plusX, fy, plusW, fh)) {
                    unfocus();
                    long step = button == 1 ? -PRICE_STEP : PRICE_STEP;
                    entry.maxUnitPrice = Math.max(0L, entry.maxUnitPrice + step);
                    AutoBuyConfig.save();
                    return true;
                }
                if (isInside(mx, my, zeroX, fy, zeroW, fh)) {
                    unfocus();
                    entry.maxUnitPrice = 0L;
                    AutoBuyConfig.save();
                    return true;
                }
                if (my < fy) {
                    unfocus();
                    entry.enabled = !entry.enabled;
                    AutoBuyConfig.save();
                    return true;
                }
                return true;
            }
        }

        unfocus();
        return false;
    }

    public boolean mouseScrolled(double verticalAmount) {
        targetScroll = Mth.clamp(targetScroll - (float) (verticalAmount * 28.0), 0.0f, maxScroll);
        return true;
    }

    public boolean keyPressed(int key) {
        if (searchFocused) {
            if (key == InputConstants.KEY_BACKSPACE && !searchQuery.isEmpty()) {
                searchQuery = searchQuery.substring(0, searchQuery.length() - 1);
                resetScroll();
                return true;
            }
            if (key == InputConstants.KEY_ESCAPE || key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) {
                searchFocused = false;
                return true;
            }
            return true;
        }

        if (editingItem == null) return false;
        if (key == InputConstants.KEY_BACKSPACE) {
            if (!priceBuffer.isEmpty()) {
                priceBuffer = priceBuffer.substring(0, priceBuffer.length() - 1);
                commitPrice();
            }
        } else if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER || key == InputConstants.KEY_ESCAPE) {
            stopEditing();
        }
        return true;
    }

    public boolean charTyped(String ch) {
        if (ch == null || ch.isEmpty()) return false;
        if (searchFocused) {
            char c = ch.charAt(0);
            if (c >= ' ' && c != 127 && searchQuery.length() < 40) {
                searchQuery += c;
                resetScroll();
            }
            return true;
        }
        if (editingItem == null) return false;
        char chr = ch.charAt(0);
        if (chr >= '0' && chr <= '9') {
            if (priceBuffer.length() < 12) {
                priceBuffer = priceBuffer.isEmpty() && chr == '0' ? "" : priceBuffer + chr;
                commitPrice();
            }
        } else if (chr == 'k' || chr == 'K' || chr == 'к' || chr == 'К') {
            multiplyPrice(1000L);
        } else if (chr == 'm' || chr == 'M' || chr == 'м' || chr == 'М') {
            multiplyPrice(1_000_000L);
        }
        return true;
    }

    private void collect() {
        shown.clear();
        counts.clear();
        for (Section s : Section.values()) counts.put(s, 0);
        String q = searchQuery.trim().toLowerCase(Locale.ROOT);
        for (ItemEntry e : AutoBuyConfig.cfg().entries) {
            if (e == null || !matchesQuery(e, q) || !matchesFilter(e)) continue;
            Section s = Section.of(e);
            counts.merge(Section.ALL, 1, Integer::sum);
            counts.merge(s, 1, Integer::sum);
            if (section == Section.ALL || section == s) shown.add(e);
        }
    }

    private List<Section> visibleSections() {
        List<Section> list = new ArrayList<>();
        int[] total = new int[Section.values().length];
        for (ItemEntry e : AutoBuyConfig.cfg().entries) {
            if (e != null) total[Section.of(e).ordinal()]++;
        }
        for (Section s : Section.values()) {
            if (s == Section.ALL || total[s.ordinal()] > 0 || s == section) list.add(s);
        }
        return list;
    }

    private static boolean matchesQuery(ItemEntry e, String q) {
        if (q.isEmpty()) return true;
        return (e.displayName != null && e.displayName.toLowerCase(Locale.ROOT).contains(q))
                || (e.itemId != null && e.itemId.toLowerCase(Locale.ROOT).contains(q))
                || (e.searchQuery != null && e.searchQuery.toLowerCase(Locale.ROOT).contains(q))
                || (e.loreKeyword != null && e.loreKeyword.toLowerCase(Locale.ROOT).contains(q));
    }

    private boolean matchesFilter(ItemEntry e) {
        return filter == FilterMode.ALL || (filter == FilterMode.ENABLED) == e.enabled;
    }

    private void setAllShown(boolean enabled) {
        unfocus();
        for (ItemEntry e : new ArrayList<>(shown)) {
            e.enabled = enabled;
        }
        AutoBuyConfig.save();
    }

    private ItemStack getStack(ItemEntry entry) {
        if (entry == null) return ItemStack.EMPTY;
        String key = (entry.itemId != null ? entry.itemId : "") + "#" + entry.matchAnySword + "#" + entry.requireCustom;
        return iconCache.computeIfAbsent(key, k -> {
            ItemStack stack = new ItemStack(Items.CHEST);
            if (entry.matchAnySword) {
                stack = new ItemStack(Items.NETHERITE_SWORD);
            } else if (entry.itemId != null && !entry.itemId.isBlank()) {
                Identifier id = Identifier.tryParse(entry.itemId);
                if (id != null) {
                    Item item = BuiltInRegistries.ITEM.getValue(id);
                    if (item != Items.AIR) stack = new ItemStack(item);
                }
            }
            if (entry.requireCustom) {
                stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
            }
            return stack;
        });
    }

    private static void drawScaledItem(GuiGraphicsExtractor context, ItemStack stack, float x, float y, float size) {
        if (stack == null || stack.isEmpty()) return;
        Matrix3x2fStack pose = context.pose();
        float scale = size / 16.0f;
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale, scale);
        try {
            context.fakeItem(stack, 0, 0);
        } catch (Throwable ignored) {
        }
        pose.popMatrix();
    }

    private void startEditing(ItemEntry item) {
        this.editingItem = item;
        this.priceBuffer = item.maxUnitPrice > 0 ? String.valueOf(item.maxUnitPrice) : "";
    }

    private void stopEditing() {
        if (this.editingItem != null) {
            commitPrice();
            this.editingItem = null;
            this.priceBuffer = "";
            AutoBuyConfig.save();
        }
    }

    private void commitPrice() {
        if (this.editingItem != null) {
            try {
                long val = priceBuffer.isEmpty() ? 0L : Long.parseLong(priceBuffer);
                this.editingItem.maxUnitPrice = Math.max(0L, val);
                AutoBuyConfig.save();
            } catch (NumberFormatException ignored) {
            }
        }
    }

    private void multiplyPrice(long mult) {
        if (this.editingItem != null) {
            long cur = priceBuffer.isEmpty() ? 1L : Long.parseLong(priceBuffer);
            cur = Math.min(100_000_000_000L, cur * mult);
            this.priceBuffer = String.valueOf(cur);
            this.editingItem.maxUnitPrice = cur;
            AutoBuyConfig.save();
        }
    }

    private static String formatPrice(long v) {
        if (v <= 0L) return "0";
        StringBuilder sb = new StringBuilder(Long.toString(v));
        for (int i = sb.length() - 3; i > 0; i -= 3) {
            sb.insert(i, ' ');
        }
        return sb.toString();
    }

    private static boolean isInside(double mx, double my, float x, float y, float w, float h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }
}
