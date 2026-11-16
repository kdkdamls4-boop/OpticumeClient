package dev.opticumeclient.client.ui.menu;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;

import java.io.File;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Persistent JSON store for OpticumeClient menu/UI preferences inside
 * {@code <gameDir>/opticumeclient/menu.json}.
 */
public final class MenuConfigStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Object LOCK = new Object();
    private static JsonObject cache;
    private static File storeFile;

    private MenuConfigStore() {
    }

    private static File resolveFile() {
        if (storeFile != null) return storeFile;
        Minecraft mc = Minecraft.getInstance();
        File gameDir = (mc != null && mc.gameDirectory != null) ? mc.gameDirectory : new File(".");
        File root = new File(gameDir, "opticumeclient");
        if (!root.exists()) {
            root.mkdirs();
        }
        storeFile = new File(root, "menu.json");
        return storeFile;
    }

    private static JsonObject loadInternal() {
        if (cache != null) return cache;
        File file = resolveFile();
        if (file.exists()) {
            try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
                JsonElement el = JsonParser.parseReader(reader);
                if (el != null && el.isJsonObject()) {
                    cache = el.getAsJsonObject();
                    return cache;
                }
            } catch (Throwable ignored) {
            }
        }
        cache = new JsonObject();
        return cache;
    }

    public static String getString(String key, String def) {
        synchronized (LOCK) {
            JsonObject obj = loadInternal();
            if (obj.has(key) && obj.get(key).isJsonPrimitive()) {
                return obj.get(key).getAsString();
            }
            return def;
        }
    }

    public static boolean getBoolean(String key, boolean def) {
        synchronized (LOCK) {
            JsonObject obj = loadInternal();
            if (obj.has(key) && obj.get(key).isJsonPrimitive()) {
                try {
                    return obj.get(key).getAsBoolean();
                } catch (Throwable ignored) {
                }
            }
            return def;
        }
    }

    public static JsonArray getArray(String key) {
        synchronized (LOCK) {
            JsonObject obj = loadInternal();
            if (obj.has(key) && obj.get(key).isJsonArray()) {
                return obj.getAsJsonArray(key).deepCopy();
            }
            return null;
        }
    }

    public static Set<String> getStringSet(String key) {
        Set<String> result = new HashSet<>();
        JsonArray arr = getArray(key);
        if (arr != null) {
            for (JsonElement el : arr) {
                if (el != null && el.isJsonPrimitive()) {
                    result.add(el.getAsString());
                }
            }
        }
        return result;
    }

    public static void save(Consumer<JsonObject> mutator) {
        synchronized (LOCK) {
            JsonObject obj = loadInternal();
            mutator.accept(obj);
            File file = resolveFile();
            try (Writer writer = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8)) {
                GSON.toJson(obj, writer);
            } catch (Throwable ignored) {
            }
        }
    }
}
