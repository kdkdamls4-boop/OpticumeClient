package dev.opticumeclient.client.services.auth;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.opticumeclient.client.util.AtomicFileWriter;
import net.minecraft.client.Minecraft;

import java.io.File;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Tracks per-account server bans (manual toggles in AltManager or auto-detected on ban disconnect messages)
 * inside <gameDir>/opticumeclient/bans.json.
 */
public final class BanTrackerStore {
    public record BanEntry(String account, String server, String reason, long recordedMs) {
    }

    private static final BanTrackerStore INSTANCE = new BanTrackerStore();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Map<String, BanEntry> bans = new LinkedHashMap<>();
    private File file;
    private boolean loaded = false;
    private boolean loadFailed = false;

    private BanTrackerStore() {
    }

    public static BanTrackerStore getInstance() {
        return INSTANCE;
    }

    public synchronized void ensureLoaded() {
        if (loaded && !loadFailed) return;
        loaded = true;
        if (file == null) {
            Minecraft mc = Minecraft.getInstance();
            File gameDir = (mc != null && mc.gameDirectory != null) ? mc.gameDirectory : new File(".");
            File dir = new File(gameDir, "opticumeclient");
            if (!dir.exists()) dir.mkdirs();
            file = new File(dir, "bans.json");
        }
        load();
    }

    public synchronized BanEntry getBan(String account) {
        ensureLoaded();
        if (account == null) return null;
        return bans.get(account.trim().toLowerCase(Locale.ROOT));
    }

    public synchronized boolean isBanned(String account) {
        return getBan(account) != null;
    }

    public synchronized void markBan(String account, String server, String reason) {
        ensureLoaded();
        if (account == null || account.isBlank()) return;
        String key = account.trim().toLowerCase(Locale.ROOT);
        bans.put(key, new BanEntry(account.trim(), server == null ? "" : server, reason == null ? "Banned" : reason, System.currentTimeMillis()));
        save();
    }

    public synchronized void clearBan(String account) {
        ensureLoaded();
        if (account == null) return;
        if (bans.remove(account.trim().toLowerCase(Locale.ROOT)) != null) {
            save();
        }
    }

    public synchronized void toggleManualBan(String account) {
        if (isBanned(account)) {
            clearBan(account);
        } else {
            markBan(account, "Manual", "Marked in AltManager");
        }
    }

    public void onDisconnectReason(String reasonText) {
        if (reasonText == null || reasonText.isBlank()) return;
        String low = reasonText.toLowerCase(Locale.ROOT);
        if (low.contains("забан") || low.contains("бан") || low.contains("banned") || low.contains("blacklisted")) {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.getUser() == null) return;
            String acc = mc.getUser().getName();
            String srv = mc.getCurrentServer() != null ? mc.getCurrentServer().ip : "Server";
            String shortReason = reasonText.replaceAll("\\s+", " ").trim();
            if (shortReason.length() > 48) shortReason = shortReason.substring(0, 46) + "..";
            markBan(acc, srv, shortReason);
        }
    }

    private void load() {
        if (file == null || !file.exists()) {
            loadFailed = false;
            return;
        }
        try (Reader r = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            JsonElement el = JsonParser.parseReader(r);
            if (!el.isJsonArray()) {
                loadFailed = true;
                System.err.println("[OpticumeClient] Failed to load bans.json: root JSON is not an array");
                return;
            }
            Map<String, BanEntry> parsed = new LinkedHashMap<>();
            for (JsonElement item : el.getAsJsonArray()) {
                if (!item.isJsonObject()) continue;
                try {
                    JsonObject o = item.getAsJsonObject();
                    String acc = o.has("account") && o.get("account").isJsonPrimitive() ? o.get("account").getAsString() : "";
                    if (acc.isBlank()) continue;
                    String srv = o.has("server") && o.get("server").isJsonPrimitive() ? o.get("server").getAsString() : "";
                    String rsn = o.has("reason") && o.get("reason").isJsonPrimitive() ? o.get("reason").getAsString() : "Banned";
                    long ts = System.currentTimeMillis();
                    if (o.has("recordedMs") && o.get("recordedMs").isJsonPrimitive()) {
                        try {
                            ts = o.get("recordedMs").getAsLong();
                        } catch (NumberFormatException ignored) {
                        }
                    }
                    parsed.put(acc.toLowerCase(Locale.ROOT), new BanEntry(acc, srv, rsn, ts));
                } catch (Throwable ignored) {
                }
            }
            bans.clear();
            bans.putAll(parsed);
            loadFailed = false;
        } catch (Throwable t) {
            loadFailed = true;
            System.err.println("[OpticumeClient] Failed to load bans.json: " + t.getMessage());
        }
    }

    private synchronized void save() {
        if (file == null || loadFailed) return;
        try {
            JsonArray arr = new JsonArray();
            for (BanEntry b : bans.values()) {
                JsonObject o = new JsonObject();
                o.addProperty("account", b.account());
                o.addProperty("server", b.server());
                o.addProperty("reason", b.reason());
                o.addProperty("recordedMs", b.recordedMs());
                arr.add(o);
            }
            AtomicFileWriter.writeUtf8(file, w -> GSON.toJson(arr, w));
        } catch (Throwable t) {
            System.err.println("[OpticumeClient] Failed to save bans.json: " + t.getMessage());
        }
    }
}
