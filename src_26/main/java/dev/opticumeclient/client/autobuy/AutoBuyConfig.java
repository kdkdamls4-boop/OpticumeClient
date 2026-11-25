package dev.opticumeclient.client.autobuy;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.client.Minecraft;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class AutoBuyConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static AutoBuyConfig INSTANCE = new AutoBuyConfig();
    private static boolean loadedOnce = false;

    static {
        try {
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    save();
                } catch (Throwable ignored) {
                }
            }, "Opticume-AutoBuyConfig-ShutdownHook"));
        } catch (Throwable ignored) {
        }
    }

    public boolean enabled = false;
    public int clickIntervalTicks = 1;
    public boolean autoRefresh = true;
    public int refreshIntervalTicks = -1;
    public int refreshSlot = -1;
    public boolean minPriceEnabled = true;
    public long minPrice = 15000L;
    public boolean safeMode = true;
    public int armorDurabilityPercent = 50;
    public boolean autoSell = false;
    public int sellDiscountPercent = 5;
    public int relistDiscountPercent = 8;
    public boolean autoRelist = false;
    public int relistIntervalSeconds = 180;
    public boolean autoParse = true;
    public int parseIntervalSeconds = 600;
    public boolean autoPay = false;
    public long autoPayAmount = 5_000_000L;
    public long autoPayKeepBalance = 5_000_000L;
    public String autoPayNick = "";
    public int autoPayIntervalSeconds = 30;
    public int parsePercent = 80;
    public long defaultPriceIfMissing = 50_000_000L;
    public boolean compactChat = true;
    public float delayPoint0 = 0.1F;
    public float delayPoint1 = 0.14F;
    public float delayPoint2 = 0.3F;
    public float delayPoint3 = 0.28F;
    public List<ItemEntry> entries = new ArrayList<>();
    public List<String> banList = new ArrayList<>();
    public List<PurchaseRecord> history = new ArrayList<>();
    public int historyMaxSize = 500;
    public long totalSpent = 0L;
    public long totalEarned = 0L;
    public int totalBoughtCount = 0;
    public int totalSoldCount = 0;

    public static AutoBuyConfig cfg() {
        if (!loadedOnce) {
            ensureLoaded();
        }
        return INSTANCE;
    }

    public static synchronized void ensureLoaded() {
        if (loadedOnce) return;
        loadedOnce = true;
        if (ItemDatabase.LIST.isEmpty()) {
            ItemDatabase.reload();
        }
        load();
    }

    public void addSpent(long amount) {
        if (amount > 0L) {
            this.totalSpent += amount;
            this.totalBoughtCount++;
            save();
        }
    }

    public void addEarned(long amount) {
        if (amount > 0L) {
            this.totalEarned += amount;
            this.totalSoldCount++;
            save();
        }
    }

    public void resetStats() {
        this.totalSpent = 0L;
        this.totalEarned = 0L;
        this.totalBoughtCount = 0;
        this.totalSoldCount = 0;
        save();
    }

    public ItemEntry find(String itemId) {
        if (itemId == null) return null;
        for (ItemEntry e : this.entries) {
            if (e != null && e.enabled && e.maxUnitPrice > 0L && e.matches(itemId)) {
                return e;
            }
        }
        return null;
    }

    public boolean ignored(String nick) {
        if (nick == null || nick.isBlank() || this.banList == null || this.banList.isEmpty()) {
            return false;
        }
        String cleanNick = cleanNickForCheck(nick);
        if (cleanNick.isEmpty()) return false;

        for (String s : this.banList) {
            if (s == null || s.isBlank()) continue;
            String cleanBanned = cleanNickForCheck(s);
            if (cleanBanned.isEmpty()) continue;

            if (cleanBanned.equalsIgnoreCase(cleanNick)) return true;
            if (containsWord(cleanNick.toLowerCase(Locale.ROOT), cleanBanned.toLowerCase(Locale.ROOT))) return true;
            if (cleanNick.replace(" ", "").equalsIgnoreCase(cleanBanned.replace(" ", ""))) return true;
        }
        return false;
    }

    public static String cleanNickForCheck(String s) {
        if (s == null) return "";
        return PriceParser.stripFormatting(s)
                .replaceAll("[\\[\\]()<>{}\\*★⚔⭐☘⚡✦✔✓•|~#$\\-!?@+=;,\"'`:]", " ")
                .replaceAll("[\\s\\u00A0]+", " ")
                .trim();
    }

    public static boolean containsWord(String text, String word) {
        if (text == null || word == null || word.isEmpty()) return false;
        int idx = 0;
        while ((idx = text.indexOf(word, idx)) != -1) {
            int end = idx + word.length();
            boolean startOk = idx == 0 || !isWordChar(text.charAt(idx - 1));
            boolean endOk = end == text.length() || !isWordChar(text.charAt(end));
            if (startOk && endOk) return true;
            idx++;
        }
        return false;
    }

    public static boolean isWordChar(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_';
    }

    public void addBan(String nick) {
        if (nick != null) {
            String clean = cleanNickForCheck(nick);
            if (!clean.isEmpty() && !this.ignored(clean)) {
                this.banList.add(clean);
                save();
            }
        }
    }

    public void removeBan(String nick) {
        if (nick == null) return;
        String clean = cleanNickForCheck(nick);
        this.banList.removeIf(s -> s != null
                && (s.equalsIgnoreCase(nick.trim()) || cleanNickForCheck(s).equalsIgnoreCase(clean)));
        save();
    }

    public void addHistory(PurchaseRecord record) {
        if (record != null) {
            this.history.add(0, record);
            while (this.history.size() > Math.max(1, this.historyMaxSize)) {
                this.history.remove(this.history.size() - 1);
            }
            save();
        }
    }

    public void clampDelayCurve() {
        this.delayPoint0 = clamp01(this.delayPoint0);
        this.delayPoint1 = clamp01(this.delayPoint1);
        this.delayPoint2 = clamp01(this.delayPoint2);
        this.delayPoint3 = clamp01(this.delayPoint3);
    }

    public float getDelayPoint(int index) {
        return switch (index) {
            case 0 -> this.delayPoint0;
            case 1 -> this.delayPoint1;
            case 2 -> this.delayPoint2;
            default -> this.delayPoint3;
        };
    }

    public float sampleDelayCurve(float progress) {
        this.clampDelayCurve();
        float p = clamp01(progress);
        float scaled = p * 3.0F;
        int idx = Math.min(2, (int) Math.floor(scaled));
        float frac = scaled - idx;
        float p0 = this.getDelayPoint(idx);
        float p1 = this.getDelayPoint(idx + 1);
        return p0 + (p1 - p0) * frac;
    }

    public int delayMsForStage(float progress, int fastMs, int slowMs) {
        return Math.round(fastMs + (slowMs - fastMs) * this.sampleDelayCurve(progress));
    }

    private static Path getConfigFile() {
        Minecraft mc = Minecraft.getInstance();
        File gameDir = mc != null && mc.gameDirectory != null ? mc.gameDirectory : new File(".");
        return new File(new File(gameDir, "opticumeclient"), "autobuy.json").toPath();
    }

    public static void save() {
        try {
            Path path = getConfigFile();
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(INSTANCE));
        } catch (Exception e) {
            System.err.println("[AutoBuy] save failed: " + e.getMessage());
        }
    }

    public static void load() {
        loadedOnce = true;
        if (ItemDatabase.LIST.isEmpty()) {
            ItemDatabase.reload();
        }
        try {
            Path path = getConfigFile();
            if (Files.exists(path)) {
                String json = Files.readString(path);
                AutoBuyConfig loaded = GSON.fromJson(json, AutoBuyConfig.class);
                if (loaded != null) {
                    INSTANCE = loaded;
                    if (INSTANCE.entries == null) INSTANCE.entries = new ArrayList<>();
                    if (INSTANCE.banList == null) INSTANCE.banList = new ArrayList<>();
                    if (INSTANCE.history == null) INSTANCE.history = new ArrayList<>();
                    if (INSTANCE.autoPayNick == null) INSTANCE.autoPayNick = "";
                    if (INSTANCE.autoPayAmount < 1_000_000L) INSTANCE.autoPayAmount = 5_000_000L;
                    if (INSTANCE.autoPayAmount > 100_000_000L) INSTANCE.autoPayAmount = 100_000_000L;
                    if (INSTANCE.autoPayKeepBalance < 0L) INSTANCE.autoPayKeepBalance = 0L;
                    if (INSTANCE.autoPayKeepBalance > 100_000_000L) INSTANCE.autoPayKeepBalance = 100_000_000L;
                    if (INSTANCE.autoPayIntervalSeconds <= 0) INSTANCE.autoPayIntervalSeconds = 30;
                    INSTANCE.armorDurabilityPercent = Math.max(0, Math.min(100, INSTANCE.armorDurabilityPercent));
                    INSTANCE.clampDelayCurve();
                    INSTANCE.enabled = false;
                    INSTANCE.autoSell = false;
                    INSTANCE.autoRelist = false;
                    INSTANCE.autoPay = false;
                }
            }
        } catch (Exception e) {
            System.err.println("[AutoBuy] load failed: " + e.getMessage());
        }

        INSTANCE.reconcileWithDatabase();
        save();
    }

    public void reconcileWithDatabase() {
        if (this.entries != null) {
            this.entries.removeIf(e -> e == null || "SP".equalsIgnoreCase(e.server));
        }

        List<ItemEntry> deduplicated = new ArrayList<>();
        for (ItemEntry existing : this.entries) {
            if (existing == null) continue;
            String eNorm = TextNormalizer.normalize(existing.displayName);
            ItemEntry dup = null;
            for (ItemEntry c : deduplicated) {
                if (!eNorm.isEmpty() && TextNormalizer.normalize(c.displayName).equals(eNorm)) {
                    dup = c;
                    break;
                }
            }
            if (dup != null) {
                if (existing.enabled) dup.enabled = true;
                if (existing.maxUnitPrice > dup.maxUnitPrice) dup.maxUnitPrice = existing.maxUnitPrice;
                if (existing.minDurabilityPercent > dup.minDurabilityPercent) dup.minDurabilityPercent = existing.minDurabilityPercent;
            } else {
                deduplicated.add(existing);
            }
        }
        this.entries = deduplicated;

        for (ItemEntry dbEntry : ItemDatabase.LIST) {
            ItemEntry existing = this.findMatchingEntry(dbEntry);
            if (existing == null) {
                ItemEntry newEntry = new ItemEntry();
                newEntry.server = "HW";
                newEntry.itemId = dbEntry.itemId;
                newEntry.displayName = dbEntry.displayName;
                newEntry.searchQuery = dbEntry.searchQuery;
                newEntry.loreKeyword = dbEntry.loreKeyword;
                newEntry.requireCustom = dbEntry.requireCustom;
                newEntry.matchAnySword = dbEntry.matchAnySword;
                newEntry.isArmor = dbEntry.isArmor;
                newEntry.markers = new ArrayList<>(dbEntry.markers);
                newEntry.enabled = false;
                newEntry.maxUnitPrice = 0L;
                this.entries.add(newEntry);
            } else {
                existing.server = "HW";
                existing.itemId = dbEntry.itemId;
                existing.displayName = dbEntry.displayName;
                existing.searchQuery = dbEntry.searchQuery;
                existing.loreKeyword = dbEntry.loreKeyword;
                existing.requireCustom = dbEntry.requireCustom;
                existing.matchAnySword = dbEntry.matchAnySword;
                existing.isArmor = dbEntry.isArmor;
                existing.markers = new ArrayList<>(dbEntry.markers);
            }
        }
    }

    private ItemEntry findMatchingEntry(ItemEntry db) {
        String dbNorm = TextNormalizer.normalize(db.displayName);
        for (ItemEntry e : this.entries) {
            if (e == null) continue;
            String eNorm = TextNormalizer.normalize(e.displayName);
            if (!dbNorm.isEmpty() && dbNorm.equals(eNorm)) {
                return e;
            }
            boolean matchId = e.itemId != null && e.itemId.equalsIgnoreCase(db.itemId);
            boolean matchLore = cleanStr(e.loreKeyword).equals(cleanStr(db.loreKeyword));
            if (matchId && matchLore && e.matchAnySword == db.matchAnySword) {
                return e;
            }
        }
        return null;
    }

    private static String cleanStr(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }
}
