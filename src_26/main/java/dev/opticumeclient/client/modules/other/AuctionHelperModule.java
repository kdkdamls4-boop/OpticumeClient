package dev.opticumeclient.client.modules.other;

import dev.opticumeclient.client.autobuy.Lot;
import dev.opticumeclient.client.autobuy.PriceParser;
import dev.opticumeclient.client.modules.Category;
import dev.opticumeclient.client.modules.Module;
import dev.opticumeclient.client.render.ColorUtils;
import dev.opticumeclient.client.setting.BindSetting;
import dev.opticumeclient.client.setting.BooleanSetting;
import dev.opticumeclient.client.setting.NumberSetting;
import dev.opticumeclient.client.setting.RunSetting;
import dev.opticumeclient.client.util.ChatUtils;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Auction House QoL helper:
 * - Highlights Top-3 cheapest unit-price lots in /ah container screens (Green / Gold / Cyan).
 * - Displays compact unit-price overlay badges on auction slots.
 * - Evaluates math expressions inside /ah sell commands (e.g. /ah sell 12*64, /ah sell 500k*3).
 * - Quick /ah search <held item> keybind and manual discounted AutoSell helper.
 */
public class AuctionHelperModule extends Module {
    private static AuctionHelperModule INSTANCE;

    private static final Pattern AH_SELL_MATH = Pattern.compile("(?i)^(ah|auc|auction)\\s+sell\\s+(.+)$");

    private final BooleanSetting highlightCheapest = new BooleanSetting("HighlightTop3", "Подсвечивать топ-3 самых выгодных лота за 1 ед.", true);
    private final BooleanSetting showUnitPrice = new BooleanSetting("UnitPriceBadge", "Показывать цену за 1 шт. поверх слотов аукциона", true);
    private final BooleanSetting sellCalculator = new BooleanSetting("SellCalc", "Калькулятор в команде /ah sell (например 12*64, 500k*3)", true);
    private final BindSetting quickSearchBind = new BindSetting("SearchHeldBind", "Быстрый /ah search по предмету в руке");
    private final NumberSetting quickSellPrice = new NumberSetting("AutoSellPrice", "Базовая цена для ручного выставления лота", 50000.0, 100.0, 10000000.0, 1000.0);
    private final NumberSetting undercutPercent = new NumberSetting("Undercut%", "Скидка (%) при ручном AutoSell от базовой/минимальной цены", 5.0, 0.0, 50.0, 1.0);
    private final RunSetting sellHeldNow = new RunSetting("Sell Held Item", "Выставить предмет в руке на /ah sell со скидкой", this::triggerManualSell);

    private long lastMinUnitPrice = -1L;
    private String lastMinItemSignature = "";
    private Object lastQuoteConnection = null;

    public AuctionHelperModule() {
        super("AuctionHelper", "Подсветка выгодных лотов /ah, калькулятор /ah sell, быстрый поиск и продажа", Category.OTHER);
        INSTANCE = this;
        addSetting(highlightCheapest);
        addSetting(showUnitPrice);
        addSetting(sellCalculator);
        addSetting(quickSearchBind);
        addSetting(quickSellPrice);
        addSetting(undercutPercent);
        addSetting(sellHeldNow);
    }

    public static AuctionHelperModule getInstance() {
        return INSTANCE;
    }

    public static void onInputKey(int keyOrButton, int action, boolean mouse) {
        AuctionHelperModule mod = INSTANCE;
        if (mod == null || !mod.isEnabled() || action != 1) return;
        if (mod.mc.player == null || mod.mc.player.connection == null) return;
        if (mod.mc.gui != null && mod.mc.gui.screen() != null) return;

        if (mod.quickSearchBind.matchesInput(keyOrButton, mouse)) {
            mod.searchHeldItem();
        }
    }

    private void searchHeldItem() {
        if (mc.player == null || mc.player.connection == null) return;
        ItemStack held = mc.player.getMainHandItem();
        if (held.isEmpty()) {
            ChatUtils.sendClientMessage("Возьмите предмет в руку для быстрого /ah search.");
            return;
        }
        String name = PriceParser.stripFormatting(held.getHoverName().getString()).trim();
        if (name.isEmpty()) return;
        mc.player.connection.sendCommand("ah search " + name);
    }

    private void triggerManualSell() {
        if (mc.player == null || mc.player.connection == null) return;
        ItemStack held = mc.player.getMainHandItem();
        if (held.isEmpty()) {
            ChatUtils.sendClientMessage("Нет предмета в основной руке для продажи.");
            return;
        }
        long baseUnit = quickSellPrice.getValue().longValue();
        Object conn = mc.getConnection();
        if (conn != lastQuoteConnection) {
            clearQuote();
        }
        String heldSig = computeItemSignature(held);
        if (lastMinUnitPrice > 0L && !heldSig.isEmpty() && heldSig.equals(lastMinItemSignature)) {
            baseUnit = lastMinUnitPrice;
        }
        double factor = 1.0 - (undercutPercent.getValue() / 100.0);
        long total = Math.max(1L, Math.round(baseUnit * Math.max(1, held.getCount()) * factor));
        mc.player.connection.sendCommand("ah sell " + total);
        ChatUtils.sendClientMessage("Выставлено на аукцион за $" + formatCompact(total));
    }

    /**
     * Evaluates math expressions in outgoing /ah sell commands.
     * Returns the rewritten command (without leading '/') or null if unchanged.
     */
    public String rewriteOutgoingCommand(String command) {
        if (!isEnabled() || !sellCalculator.isEnabled() || command == null) return null;
        String trimmed = command.trim();
        Matcher m = AH_SELL_MATH.matcher(trimmed);
        if (!m.matches()) return null;

        String prefix = m.group(1);
        String expr = m.group(2).trim();
        // Only rewrite if expression contains an operator or k/m suffix
        if (!expr.matches(".*[+*\\-/kKmM].*")) return null;

        Long evaluated = evaluateMathExpression(expr);
        if (evaluated == null || evaluated <= 0L) return null;
        return prefix + " sell " + evaluated;
    }

    public static Long evaluateMathExpression(String raw) {
        if (raw == null) return null;
        String s = raw.replaceAll("\\s+", "");
        if (s.isEmpty() || !s.matches("[0-9kKmM.+\\-*/()]+")) return null;

        try {
            double result = new ExprParser(s).parse();
            if (!Double.isFinite(result) || result <= 0.0 || result > 1.0E13) return null;
            return Math.round(result);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public boolean isAuctionScreen(AbstractContainerScreen<?> screen) {
        if (screen == null || screen.getTitle() == null) return false;
        String title = PriceParser.stripFormatting(screen.getTitle().getString()).toLowerCase(Locale.ROOT);
        return title.contains("аукцион") || title.contains("auction") || title.contains("лоты")
                || title.contains("торг") || title.contains("поиск:");
    }

    private record ScoredSlot(Slot slot, Lot lot) {}

    private void clearQuote() {
        lastMinUnitPrice = -1L;
        lastMinItemSignature = "";
        lastQuoteConnection = null;
    }

    public void renderContainerOverlay(AbstractContainerScreen<?> screen, GuiGraphicsExtractor graphics, int leftPos, int topPos) {
        if (!isEnabled() || (!highlightCheapest.isEnabled() && !showUnitPrice.isEnabled())) return;
        if (!isAuctionScreen(screen)) return;

        Object conn = mc.getConnection();
        if (conn != lastQuoteConnection) {
            clearQuote();
            lastQuoteConnection = conn;
        }

        List<Slot> slots = screen.getMenu().slots;
        int containerSlotCount = Math.max(0, slots.size() - 36);
        if (containerSlotCount <= 0) {
            clearQuote();
            lastQuoteConnection = conn;
            return;
        }

        List<ScoredSlot> parsed = new ArrayList<>();
        for (int i = 0; i < containerSlotCount; i++) {
            Slot slot = slots.get(i);
            ItemStack st = slot.getItem();
            if (st.isEmpty()) continue;
            Lot lot = PriceParser.parse(i, st);
            if (lot != null && lot.unitPrice > 0L) {
                parsed.add(new ScoredSlot(slot, lot));
            }
        }

        if (parsed.isEmpty()) {
            clearQuote();
            lastQuoteConnection = conn;
            return;
        }
        parsed.sort(Comparator.comparingLong(a -> a.lot().unitPrice));

        // Remember cheapest unit price with full item identity signature for manual AutoSell
        lastQuoteConnection = conn;
        lastMinUnitPrice = parsed.get(0).lot().unitPrice;
        lastMinItemSignature = computeItemSignature(parsed.get(0).slot().getItem());

        if (highlightCheapest.isEnabled()) {
            int[] rankColors = {
                    ColorUtils.rgba(46, 235, 130, 125),  // #1 Cheapest (Emerald)
                    ColorUtils.rgba(255, 200, 56, 110),  // #2 Second (Gold)
                    ColorUtils.rgba(68, 195, 255, 95)    // #3 Third (Cyan)
            };
            int[] borderColors = {
                    ColorUtils.rgba(46, 235, 130, 235),
                    ColorUtils.rgba(255, 200, 56, 220),
                    ColorUtils.rgba(68, 195, 255, 205)
            };
            int topCount = Math.min(3, parsed.size());
            for (int r = 0; r < topCount; r++) {
                Slot s = parsed.get(r).slot();
                int x = leftPos + s.x;
                int y = topPos + s.y;
                graphics.fill(x, y, x + 16, y + 16, rankColors[r]);
                graphics.fill(x, y, x + 16, y + 1, borderColors[r]);
                graphics.fill(x, y + 15, x + 16, y + 16, borderColors[r]);
                graphics.fill(x, y, x + 1, y + 16, borderColors[r]);
                graphics.fill(x + 15, y, x + 16, y + 16, borderColors[r]);
            }
        }

        if (showUnitPrice.isEnabled() && mc.font != null) {
            for (int i = 0; i < parsed.size(); i++) {
                ScoredSlot ss = parsed.get(i);
                int x = leftPos + ss.slot().x;
                int y = topPos + ss.slot().y;
                String label = formatCompact(ss.lot().unitPrice);
                int col = i == 0 ? 0xFF4CFF9B : (i == 1 ? 0xFFFFD859 : 0xFFE6ECF7);
                graphics.text(mc.font, label, x, y, col, true);
            }
        }
    }

    private static String computeItemSignature(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "";
        String id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        String name = PriceParser.stripFormatting(stack.getHoverName().getString()).trim().toLowerCase(Locale.ROOT);
        var ench = stack.getOrDefault(net.minecraft.core.component.DataComponents.ENCHANTMENTS,
                net.minecraft.world.item.enchantment.ItemEnchantments.EMPTY);
        var storedEnch = stack.getOrDefault(net.minecraft.core.component.DataComponents.STORED_ENCHANTMENTS,
                net.minecraft.world.item.enchantment.ItemEnchantments.EMPTY);
        var potion = stack.get(net.minecraft.core.component.DataComponents.POTION_CONTENTS);
        var customData = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        var trim = stack.get(net.minecraft.core.component.DataComponents.TRIM);
        var fireworks = stack.get(net.minecraft.core.component.DataComponents.FIREWORKS);
        var fireworkExplosion = stack.get(net.minecraft.core.component.DataComponents.FIREWORK_EXPLOSION);
        var dyedColor = stack.get(net.minecraft.core.component.DataComponents.DYED_COLOR);
        var instrument = stack.get(net.minecraft.core.component.DataComponents.INSTRUMENT);
        var ominousAmplifier = stack.get(net.minecraft.core.component.DataComponents.OMINOUS_BOTTLE_AMPLIFIER);
        return id + "|" + name + "|d=" + stack.getDamageValue()
                + "|" + ench + "|" + storedEnch
                + "|" + (potion != null ? potion.toString() : "")
                + "|" + (trim != null ? trim.toString() : "")
                + "|" + (fireworks != null ? fireworks.toString() : "")
                + "|" + (fireworkExplosion != null ? fireworkExplosion.toString() : "")
                + "|" + (dyedColor != null ? dyedColor.toString() : "")
                + "|" + (instrument != null ? instrument.toString() : "")
                + "|" + (ominousAmplifier != null ? ominousAmplifier.toString() : "")
                + "|" + (customData != null ? customData.toString() : "");
    }

    private static String formatCompact(long price) {
        if (price >= 1_000_000L) {
            return String.format(Locale.US, "%.1fm", price / 1_000_000.0);
        }
        if (price >= 1_000L) {
            return String.format(Locale.US, "%.0fk", price / 1_000.0);
        }
        return Long.toString(price);
    }

    /**
     * Minimal recursive-descent arithmetic parser (+, -, *, /, parentheses).
     */
    private static final class ExprParser {
        private final String src;
        private int pos = 0;

        ExprParser(String src) {
            this.src = src;
        }

        double parse() {
            double v = parseExpr();
            if (pos != src.length()) throw new IllegalArgumentException("Trailing chars");
            return v;
        }

        private double parseExpr() {
            double v = parseTerm();
            while (pos < src.length()) {
                char c = src.charAt(pos);
                if (c == '+') {
                    pos++;
                    v += parseTerm();
                } else if (c == '-') {
                    pos++;
                    v -= parseTerm();
                } else break;
            }
            return v;
        }

        private double parseTerm() {
            double v = parseFactor();
            while (pos < src.length()) {
                char c = src.charAt(pos);
                if (c == '*') {
                    pos++;
                    v *= parseFactor();
                } else if (c == '/') {
                    pos++;
                    double d = parseFactor();
                    if (Math.abs(d) < 1.0E-9) throw new ArithmeticException("Div by 0");
                    v /= d;
                } else break;
            }
            return v;
        }

        private double parseFactor() {
            if (pos < src.length() && src.charAt(pos) == '(') {
                pos++;
                double v = parseExpr();
                if (pos >= src.length() || src.charAt(pos) != ')') throw new IllegalArgumentException("Unclosed )");
                pos++;
                return v;
            }
            int start = pos;
            while (pos < src.length() && (Character.isDigit(src.charAt(pos)) || src.charAt(pos) == '.')) {
                pos++;
            }
            if (start == pos) throw new IllegalArgumentException("Expected number");
            double val = Double.parseDouble(src.substring(start, pos));
            if (pos < src.length()) {
                char suffix = src.charAt(pos);
                if (suffix == 'k' || suffix == 'K') {
                    val *= 1_000.0;
                    pos++;
                } else if (suffix == 'm' || suffix == 'M') {
                    val *= 1_000_000.0;
                    pos++;
                }
            }
            return val;
        }
    }
}
