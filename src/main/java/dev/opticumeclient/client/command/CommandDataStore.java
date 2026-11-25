package dev.opticumeclient.client.command;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.opticumeclient.client.modules.other.PanicModule;
import dev.opticumeclient.client.util.AtomicFileWriter;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.File;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Persistent storage for client command prefix, keyboard macros, custom staff names,
 * named multi-waypoints (.way) and target block IDs (.blockesp) in <gameDir>/opticumeclient/commands.json.
 */
public final class CommandDataStore {
    public record MacroEntry(int keyCode, String keyName, String message) {
    }

    public record WaypointEntry(String name, int x, int y, int z, int color) {
    }

    private static final CommandDataStore INSTANCE = new CommandDataStore();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private File storeFile;
    private boolean loaded = false;
    private boolean loadFailed = false;

    private String prefix = ".";
    private final Map<Integer, MacroEntry> macros = new LinkedHashMap<>();
    private final Set<String> staffNames = new LinkedHashSet<>();
    private final Map<String, WaypointEntry> waypoints = new LinkedHashMap<>();
    private final Set<String> blockEspIds = new LinkedHashSet<>();

    private CommandDataStore() {
    }

    public static CommandDataStore getInstance() {
        return INSTANCE;
    }

    public synchronized void ensureLoaded() {
        if (loaded && !loadFailed) return;
        loaded = true;
        if (storeFile == null) {
            Minecraft mc = Minecraft.getInstance();
            File gameDir = (mc != null && mc.gameDirectory != null) ? mc.gameDirectory : new File(".");
            File dir = new File(gameDir, "opticumeclient");
            if (!dir.exists()) {
                dir.mkdirs();
            }
            storeFile = new File(dir, "commands.json");
        }
        load();
    }

    public synchronized boolean isLoadFailed() {
        ensureLoaded();
        return loadFailed;
    }

    public String getPrefix() {
        ensureLoaded();
        return prefix;
    }

    public synchronized void setPrefix(String newPrefix) {
        ensureLoaded();
        if (loadFailed) return;
        if (newPrefix == null || newPrefix.isBlank() || "/".equals(newPrefix.trim())) {
            return;
        }
        this.prefix = newPrefix.trim();
        save();
    }

    public synchronized void resetPrefix() {
        ensureLoaded();
        if (loadFailed) return;
        this.prefix = ".";
        save();
    }

    public synchronized void putMacro(int keyCode, String keyName, String message) {
        ensureLoaded();
        if (loadFailed) return;
        macros.put(keyCode, new MacroEntry(keyCode, keyName.toUpperCase(Locale.ROOT), message));
        save();
    }

    public synchronized boolean removeMacroByKey(int keyCode) {
        ensureLoaded();
        if (loadFailed) return false;
        boolean removed = macros.remove(keyCode) != null;
        if (removed) save();
        return removed;
    }

    public synchronized int clearMacros() {
        ensureLoaded();
        if (loadFailed) return 0;
        int count = macros.size();
        if (count > 0) {
            macros.clear();
            save();
        }
        return count;
    }

    public synchronized List<MacroEntry> getMacros() {
        ensureLoaded();
        return new ArrayList<>(macros.values());
    }

    public void onKeyPress(int keyCode) {
        if (keyCode <= 0 || PanicModule.isPanicActive()) return;
        ensureLoaded();
        MacroEntry entry;
        synchronized (this) {
            entry = macros.get(keyCode);
        }
        if (entry == null || entry.message() == null || entry.message().isBlank()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || mc.player.connection == null) return;
        String msg = entry.message().trim();
        String pfx = getPrefix();
        if (msg.startsWith(pfx)) {
            CallbackInfo dummy = new CallbackInfo("macro", true);
            CommandManager.getInstance().executeCommands(msg, dummy);
        } else if (msg.startsWith("/")) {
            mc.player.connection.sendCommand(msg.substring(1));
        } else {
            mc.player.connection.sendChat(msg);
        }
    }

    public synchronized boolean addStaff(String name) {
        ensureLoaded();
        if (loadFailed || name == null || name.isBlank()) return false;
        boolean added = staffNames.add(name.trim().toLowerCase(Locale.ROOT));
        if (added) save();
        return added;
    }

    public synchronized boolean removeStaff(String name) {
        ensureLoaded();
        if (loadFailed || name == null || name.isBlank()) return false;
        boolean removed = staffNames.remove(name.trim().toLowerCase(Locale.ROOT));
        if (removed) save();
        return removed;
    }

    public synchronized int clearStaff() {
        ensureLoaded();
        if (loadFailed) return 0;
        int count = staffNames.size();
        if (count > 0) {
            staffNames.clear();
            save();
        }
        return count;
    }

    public synchronized boolean isCustomStaff(String name) {
        ensureLoaded();
        if (name == null || name.isBlank()) return false;
        return staffNames.contains(name.trim().toLowerCase(Locale.ROOT));
    }

    public synchronized Set<String> getStaffNames() {
        ensureLoaded();
        return Collections.unmodifiableSet(new LinkedHashSet<>(staffNames));
    }

    public synchronized boolean putWaypoint(String name, int x, int y, int z, int color) {
        ensureLoaded();
        if (loadFailed || name == null || name.isBlank()) return false;
        String key = name.trim().toLowerCase(Locale.ROOT);
        waypoints.put(key, new WaypointEntry(name.trim(), x, y, z, color));
        save();
        return true;
    }

    public synchronized WaypointEntry getWaypoint(String name) {
        ensureLoaded();
        if (name == null) return null;
        return waypoints.get(name.trim().toLowerCase(Locale.ROOT));
    }

    public synchronized boolean removeWaypoint(String name) {
        ensureLoaded();
        if (loadFailed || name == null) return false;
        boolean removed = waypoints.remove(name.trim().toLowerCase(Locale.ROOT)) != null;
        if (removed) save();
        return removed;
    }

    public synchronized int clearWaypoints() {
        ensureLoaded();
        if (loadFailed) return 0;
        int count = waypoints.size();
        if (count > 0) {
            waypoints.clear();
            save();
        }
        return count;
    }

    public synchronized List<WaypointEntry> getWaypoints() {
        ensureLoaded();
        return new ArrayList<>(waypoints.values());
    }

    public static String normalizeBlockId(String raw) {
        if (raw == null) return "";
        String clean = raw.trim().toLowerCase(Locale.ROOT);
        if (!clean.isEmpty() && !clean.contains(":")) {
            return "minecraft:" + clean;
        }
        return clean;
    }

    public synchronized boolean addBlockEspId(String id) {
        ensureLoaded();
        if (loadFailed) return false;
        String norm = normalizeBlockId(id);
        if (norm.isEmpty()) return false;
        boolean added = blockEspIds.add(norm);
        if (added) save();
        return added;
    }

    public synchronized boolean removeBlockEspId(String id) {
        ensureLoaded();
        if (loadFailed) return false;
        String norm = normalizeBlockId(id);
        if (norm.isEmpty()) return false;
        boolean removed = blockEspIds.remove(norm);
        if (removed) save();
        return removed;
    }

    public synchronized int clearBlockEspIds() {
        ensureLoaded();
        if (loadFailed) return 0;
        int count = blockEspIds.size();
        if (count > 0) {
            blockEspIds.clear();
            save();
        }
        return count;
    }

    public synchronized Set<String> getBlockEspIds() {
        ensureLoaded();
        return Collections.unmodifiableSet(new LinkedHashSet<>(blockEspIds));
    }

    private void load() {
        if (storeFile == null || !storeFile.exists()) {
            loadFailed = false;
            return;
        }
        try (Reader reader = Files.newBufferedReader(storeFile.toPath(), StandardCharsets.UTF_8)) {
            JsonElement parsed = JsonParser.parseReader(reader);
            if (!parsed.isJsonObject()) {
                loadFailed = true;
                System.err.println("[OpticumeClient] Failed to load commands.json: root JSON is not an object");
                return;
            }
            JsonObject root = parsed.getAsJsonObject();

            String tmpPrefix = prefix;
            Map<Integer, MacroEntry> tmpMacros = new LinkedHashMap<>(macros);
            Set<String> tmpStaff = new LinkedHashSet<>(staffNames);
            Map<String, WaypointEntry> tmpWaypoints = new LinkedHashMap<>(waypoints);
            Set<String> tmpBlockEsp = new LinkedHashSet<>(blockEspIds);

            if (root.has("prefix") && root.get("prefix").isJsonPrimitive()) {
                String p = root.get("prefix").getAsString().trim();
                if (!p.isEmpty() && !"/".equals(p)) {
                    tmpPrefix = p;
                }
            }
            if (root.has("macros") && root.get("macros").isJsonArray()) {
                tmpMacros.clear();
                for (JsonElement el : root.getAsJsonArray("macros")) {
                    if (!el.isJsonObject()) continue;
                    JsonObject obj = el.getAsJsonObject();
                    int keyCode = obj.has("keyCode") ? obj.get("keyCode").getAsInt() : 0;
                    String keyName = obj.has("keyName") ? obj.get("keyName").getAsString() : "";
                    String message = obj.has("message") ? obj.get("message").getAsString() : "";
                    if (keyCode > 0 && !message.isBlank()) {
                        tmpMacros.put(keyCode, new MacroEntry(keyCode, keyName, message));
                    }
                }
            }
            if (root.has("staff") && root.get("staff").isJsonArray()) {
                tmpStaff.clear();
                for (JsonElement el : root.getAsJsonArray("staff")) {
                    if (el.isJsonPrimitive()) {
                        String s = el.getAsString().trim().toLowerCase(Locale.ROOT);
                        if (!s.isEmpty()) tmpStaff.add(s);
                    }
                }
            }
            if (root.has("waypoints") && root.get("waypoints").isJsonArray()) {
                tmpWaypoints.clear();
                for (JsonElement el : root.getAsJsonArray("waypoints")) {
                    if (!el.isJsonObject()) continue;
                    JsonObject obj = el.getAsJsonObject();
                    String name = obj.has("name") ? obj.get("name").getAsString() : "";
                    if (!name.isBlank()) {
                        int x = obj.has("x") ? obj.get("x").getAsInt() : 0;
                        int y = obj.has("y") ? obj.get("y").getAsInt() : 64;
                        int z = obj.has("z") ? obj.get("z").getAsInt() : 0;
                        int color = obj.has("color") ? obj.get("color").getAsInt() : 0xFF38BDF8;
                        tmpWaypoints.put(name.trim().toLowerCase(Locale.ROOT), new WaypointEntry(name.trim(), x, y, z, color));
                    }
                }
            }
            if (root.has("blockesp") && root.get("blockesp").isJsonArray()) {
                tmpBlockEsp.clear();
                for (JsonElement el : root.getAsJsonArray("blockesp")) {
                    if (el.isJsonPrimitive()) {
                        String id = normalizeBlockId(el.getAsString());
                        if (!id.isEmpty()) tmpBlockEsp.add(id);
                    }
                }
            }
            prefix = tmpPrefix;
            macros.clear();
            macros.putAll(tmpMacros);
            staffNames.clear();
            staffNames.addAll(tmpStaff);
            waypoints.clear();
            waypoints.putAll(tmpWaypoints);
            blockEspIds.clear();
            blockEspIds.addAll(tmpBlockEsp);
            loadFailed = false;
        } catch (Throwable t) {
            loadFailed = true;
            System.err.println("[OpticumeClient] Failed to load commands.json: " + t.getMessage());
        }
    }

    public synchronized void save() {
        if (storeFile == null || loadFailed) return;
        try {
            JsonObject root = new JsonObject();
            root.addProperty("prefix", prefix);

            JsonArray macrosArr = new JsonArray();
            for (MacroEntry m : macros.values()) {
                JsonObject obj = new JsonObject();
                obj.addProperty("keyCode", m.keyCode());
                obj.addProperty("keyName", m.keyName());
                obj.addProperty("message", m.message());
                macrosArr.add(obj);
            }
            root.add("macros", macrosArr);

            JsonArray staffArr = new JsonArray();
            for (String s : staffNames) {
                staffArr.add(s);
            }
            root.add("staff", staffArr);

            JsonArray wayArr = new JsonArray();
            for (WaypointEntry w : waypoints.values()) {
                JsonObject obj = new JsonObject();
                obj.addProperty("name", w.name());
                obj.addProperty("x", w.x());
                obj.addProperty("y", w.y());
                obj.addProperty("z", w.z());
                obj.addProperty("color", w.color());
                wayArr.add(obj);
            }
            root.add("waypoints", wayArr);

            JsonArray blockArr = new JsonArray();
            for (String b : blockEspIds) {
                blockArr.add(b);
            }
            root.add("blockesp", blockArr);

            AtomicFileWriter.writeUtf8(storeFile, writer -> GSON.toJson(root, writer));
        } catch (Throwable t) {
            System.err.println("[OpticumeClient] Failed to save commands.json: " + t.getMessage());
        }
    }
}
