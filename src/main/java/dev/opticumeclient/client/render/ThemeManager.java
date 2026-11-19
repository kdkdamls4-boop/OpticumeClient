package dev.opticumeclient.client.render;

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
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Manages built-in Opticume + Desire preset themes and user-created custom themes stored under
 * {@code <gameDir>/opticumeclient/themes/*.json}.
 */
public class ThemeManager {
    private static final ThemeManager INSTANCE = new ThemeManager();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String DELETED_PRESETS_FILE = "deleted_presets.json";

    private final List<Theme> builtinPresets = new ArrayList<>();
    private final List<Theme> themes = new ArrayList<>();
    private final Set<String> deletedPresets = new LinkedHashSet<>();
    private Theme currentTheme;
    private boolean customLoaded = false;
    private String pendingThemeName = null;

    private ThemeManager() {
        // OpticumeClient signature presets
        addPreset(new Theme("Nursultan Alpha", 0xFF7B61FF, 0xFF00E5FF, 0xEC0B0C12, 0xD8131520, 0xFFF4F6FF, 0xFF8C92AC));
        addPreset(new Theme("Catlavan Acid", 0xFFFF2A85, 0xFF00F0FF, 0xF00A0A0F, 0xE012121A, 0xFFFFFFFF, 0xFF8E90A6));
        addPreset(new Theme("Expensive Purple", 0xFF8A5CF6, 0xFFD946EF, 0xEC0D0D14, 0xD8151622, 0xFFF3F4F6, 0xFF9CA3AF));
        addPreset(new Theme("Rockstar Crimson", 0xFFFF2E54, 0xFFFF8A00, 0xEC0E0B0E, 0xD8191218, 0xFFFFF5F7, 0xFFA68B93));
        addPreset(new Theme("Wexside Ice", 0xFF38BDF8, 0xFF6366F1, 0xEC0B0F17, 0xD8131B28, 0xFFF0F9FF, 0xFF8496B0));
        addPreset(new Theme("Delta Cyber", 0xFF00F5A0, 0xFF00D9F5, 0xEC091012, 0xD8111C20, 0xFFECFEFF, 0xFF7FA6B0));
        addPreset(new Theme("Celestial Neon", 0xFFFF007F, 0xFF7928CA, 0xEC0E0A14, 0xD8181224, 0xFFFDF2F8, 0xFFA288B8));
        addPreset(new Theme("ThunderHack", 0xFFA855F7, 0xFFEC4899, 0xEC0C0A12, 0xD8161322, 0xFFFAF5FF, 0xFF988BAE));
        addPreset(new Theme("Astolfo Sunset", 0xFFFF6B9D, 0xFF4ECDC4, 0xEC100E16, 0xD81A1724, 0xFFFFF7FB, 0xFF9B92AC));
        addPreset(new Theme("Rise Ocean", 0xFF2563EB, 0xFF06B6D4, 0xEC090D16, 0xD8111827, 0xFFEFF6FF, 0xFF8295B8));
        addPreset(new Theme("DeadCode Matrix", 0xFF22C55E, 0xFFA3E635, 0xEC09110C, 0xD8111E16, 0xFFF0FDF4, 0xFF7EA38C));
        addPreset(new Theme("Vape Ghost", 0xFF3B82F6, 0xFF60A5FA, 0xF0101114, 0xE018191E, 0xFFE5E7EB, 0xFF7B8190));

        // Desire presets ported from PresetThemes
        addDesirePreset("Desire", 0xFF2DD4BF, 0xFF0EA5E9);
        addDesirePreset("Aurora", 0xFF43E97B, 0xFF38F9D7);
        addDesirePreset("Lime Soda", 0xFFA8FF78, 0xFF78FFD6);
        addDesirePreset("Lagoon", 0xFF4FACFE, 0xFF00F2FE);
        addDesirePreset("Royal", 0xFF667EEA, 0xFF764BA2);
        addDesirePreset("Ultraviolet", 0xFF8E2DE2, 0xFF4A00E0);
        addDesirePreset("Lavender", 0xFFC3A6FF, 0xFFF5B8E8);
        addDesirePreset("Cosmic", 0xFFFF3CAC, 0xFF784BA0);
        addDesirePreset("Flamingo", 0xFFF093FB, 0xFFF5576C);
        addDesirePreset("Sakura", 0xFFFF9A9E, 0xFFFAD0C4);
        addDesirePreset("Blood Orange", 0xFFFF512F, 0xFFDD2476);
        addDesirePreset("Crimson", 0xFFED213A, 0xFF93291E);
        addDesirePreset("Sunset", 0xFFFF7E5F, 0xFFFEB47B);
        addDesirePreset("Golden Hour", 0xFFF7971E, 0xFFFFD200);
        addDesirePreset("Moonlight", 0xFFDDE6F5, 0xFF8A9BB8);

        this.currentTheme = themes.get(0);
    }

    private void addPreset(Theme preset) {
        builtinPresets.add(preset);
        themes.add(preset);
    }

    private void addDesirePreset(String name, int primary, int secondary) {
        addPreset(new Theme(name, primary, secondary, 0xEC101012, 0xD818181D, 0xFFFFFFFF, 0xFFBBBBBB));
    }

    public static ThemeManager getInstance() {
        return INSTANCE;
    }

    public synchronized void ensureCustomThemesLoaded() {
        if (customLoaded) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        customLoaded = true;
        File dir = getThemesDirectory();
        loadDeletedPresets(dir);
        if (!deletedPresets.isEmpty()) {
            themes.removeIf(t -> t.isPreset() && isPresetDeleted(t.getName()));
        }

        File[] files = dir.listFiles((d, name) -> name.endsWith(".json") && !name.equalsIgnoreCase(DELETED_PRESETS_FILE));
        if (files != null) {
            for (File f : files) {
                loadCustomFile(f);
            }
        }

        if (themes.isEmpty() && !builtinPresets.isEmpty()) {
            Theme fallback = builtinPresets.get(0);
            themes.add(fallback);
            deletedPresets.removeIf(n -> n.equalsIgnoreCase(fallback.getName()));
            saveDeletedPresets();
        }
        if (currentTheme == null || !themes.contains(currentTheme)) {
            currentTheme = themes.get(0);
        }

        if (pendingThemeName != null) {
            String p = pendingThemeName;
            pendingThemeName = null;
            setThemeByName(p);
        }
    }

    private boolean isPresetDeleted(String name) {
        for (String s : deletedPresets) {
            if (s.equalsIgnoreCase(name)) return true;
        }
        return false;
    }

    private void loadDeletedPresets(File dir) {
        File file = new File(dir, DELETED_PRESETS_FILE);
        if (!file.isFile()) return;
        try (Reader r = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            JsonElement el = JsonParser.parseReader(r);
            if (el != null && el.isJsonArray()) {
                for (JsonElement item : el.getAsJsonArray()) {
                    if (item.isJsonPrimitive()) {
                        String name = item.getAsString().trim();
                        if (!name.isEmpty()) {
                            deletedPresets.add(name);
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private void saveDeletedPresets() {
        File file = new File(getThemesDirectory(), DELETED_PRESETS_FILE);
        if (deletedPresets.isEmpty()) {
            try {
                Files.deleteIfExists(file.toPath());
            } catch (Throwable ignored) {
            }
            return;
        }
        JsonArray arr = new JsonArray();
        for (String name : deletedPresets) {
            arr.add(name);
        }
        try (Writer w = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8)) {
            GSON.toJson(arr, w);
        } catch (Throwable ignored) {
        }
    }

    private File getThemesDirectory() {
        Minecraft mc = Minecraft.getInstance();
        File base = (mc != null && mc.gameDirectory != null) ? mc.gameDirectory : new File(".");
        File dir = new File(new File(base, "opticumeclient"), "themes");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    private void loadCustomFile(File file) {
        try (Reader r = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            JsonElement el = JsonParser.parseReader(r);
            if (el == null || !el.isJsonObject()) return;
            JsonObject obj = el.getAsJsonObject();
            String name = obj.has("name") ? obj.get("name").getAsString().trim() : file.getName().replace(".json", "");
            if (name.isEmpty() || findTheme(name) != null) return;

            int primary = readColor(obj, Theme.KEY_PRIMARY, 0xFF2DD4BF);
            int secondary = readColor(obj, Theme.KEY_SECONDARY, 0xFF0EA5E9);
            int bg = readColor(obj, Theme.KEY_BACKGROUND, 0xEC101012);
            int card = readColor(obj, Theme.KEY_CARD, 0xD818181D);
            int knob = readColor(obj, Theme.KEY_KNOB, primary);
            int text = readColor(obj, Theme.KEY_TEXT, 0xFFFFFFFF);
            int muted = readColor(obj, Theme.KEY_MUTED, 0xFFBBBBBB);

            Theme loaded = new Theme(name, false, primary, secondary, bg, card, knob, text, muted);
            loaded.setStorageFileName(file.getName());
            themes.add(loaded);
        } catch (Throwable ignored) {
        }
    }

    private static int readColor(JsonObject obj, String key, int def) {
        if (!obj.has(key)) return def;
        try {
            JsonElement el = obj.get(key);
            if (el.isJsonPrimitive()) {
                if (el.getAsJsonPrimitive().isNumber()) return el.getAsInt();
                String s = el.getAsString().trim();
                if (s.startsWith("#")) s = s.substring(1);
                return (int) Long.parseLong(s, 16);
            }
        } catch (Throwable ignored) {
        }
        return def;
    }

    public List<Theme> getThemes() {
        ensureCustomThemesLoaded();
        return Collections.unmodifiableList(themes);
    }

    public Theme getCurrentTheme() {
        ensureCustomThemesLoaded();
        return currentTheme;
    }

    public void setCurrentTheme(Theme theme) {
        if (theme != null) {
            this.currentTheme = theme;
        }
    }

    public void setThemeByName(String name) {
        if (name == null || name.isBlank()) return;
        ensureCustomThemesLoaded();
        Theme found = findTheme(name);
        if (found != null) {
            this.currentTheme = found;
        } else {
            this.pendingThemeName = name;
        }
    }

    private Theme findTheme(String name) {
        for (Theme t : themes) {
            if (t.getName().equalsIgnoreCase(name)) return t;
        }
        return null;
    }

    public Theme createCustomTheme(String rawName) {
        ensureCustomThemesLoaded();
        String baseName = rawName == null ? "" : rawName.trim();
        if (isReservedThemeName(baseName)) {
            return null;
        }
        if (baseName.isEmpty()) {
            baseName = "Custom " + (themes.size() + 1);
        }
        String unique = baseName;
        int suffix = 2;
        while (findTheme(unique) != null || isReservedThemeName(unique)) {
            unique = baseName + " " + suffix++;
        }
        Theme created = getCurrentTheme().copyAsCustom(unique);
        created.setStorageFileName(allocateUniqueFileName(unique));
        themes.add(created);
        this.currentTheme = created;
        saveCustomTheme(created);
        return created;
    }

    public void saveCustomTheme(Theme theme) {
        if (theme == null || theme.isPreset()) return;
        String storageFileName = theme.getStorageFileName();
        if (storageFileName == null || storageFileName.isBlank() || DELETED_PRESETS_FILE.equalsIgnoreCase(storageFileName)) {
            storageFileName = allocateUniqueFileName(theme.getName());
            theme.setStorageFileName(storageFileName);
        }
        File file = new File(getThemesDirectory(), storageFileName);
        JsonObject obj = new JsonObject();
        obj.addProperty("name", theme.getName());
        for (Map.Entry<String, Integer> e : theme.getElements().entrySet()) {
            obj.addProperty(e.getKey(), String.format("#%08X", e.getValue()));
        }
        try (Writer w = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8)) {
            GSON.toJson(obj, w);
        } catch (Throwable ignored) {
        }
    }

    public synchronized boolean deleteCustomTheme(Theme theme) {
        return deleteTheme(theme);
    }

    public synchronized boolean deleteTheme(Theme theme) {
        if (theme == null) return false;
        ensureCustomThemesLoaded();
        if (themes.size() <= 1) return false;
        if (!themes.remove(theme)) return false;

        if (theme.isPreset()) {
            if (!isPresetDeleted(theme.getName())) {
                deletedPresets.add(theme.getName());
            }
            saveDeletedPresets();
        } else {
            String storageFileName = theme.getStorageFileName();
            if (storageFileName != null && !storageFileName.isBlank()
                    && !DELETED_PRESETS_FILE.equalsIgnoreCase(storageFileName)) {
                File file = new File(getThemesDirectory(), storageFileName);
                try {
                    Files.deleteIfExists(file.toPath());
                } catch (Throwable ignored) {
                }
            }
        }

        if (currentTheme == theme || !themes.contains(currentTheme)) {
            currentTheme = themes.get(0);
        }
        return true;
    }

    public synchronized int getDeletedPresetCount() {
        ensureCustomThemesLoaded();
        int count = 0;
        for (Theme preset : builtinPresets) {
            if (isPresetDeleted(preset.getName()) && !themes.contains(preset)) {
                count++;
            }
        }
        return count;
    }

    public synchronized boolean hasDeletedPresets() {
        return getDeletedPresetCount() > 0;
    }

    public synchronized int restoreBuiltinPresets() {
        ensureCustomThemesLoaded();
        int restored = 0;
        List<Theme> customs = new ArrayList<>();
        for (Theme t : themes) {
            if (!t.isPreset()) {
                customs.add(t);
            }
        }

        Set<String> remainingDeleted = new LinkedHashSet<>();
        List<Theme> rebuilt = new ArrayList<>();
        for (Theme preset : builtinPresets) {
            boolean wasMissing = !themes.contains(preset);
            Theme conflictCustom = null;
            for (Theme c : customs) {
                if (c.getName().equalsIgnoreCase(preset.getName())) {
                    conflictCustom = c;
                    break;
                }
            }
            if (conflictCustom == null) {
                rebuilt.add(preset);
                if (wasMissing) restored++;
            } else {
                // Keep preset flagged until conflicting custom theme is removed/renamed
                remainingDeleted.add(preset.getName());
            }
        }
        rebuilt.addAll(customs);

        themes.clear();
        themes.addAll(rebuilt);
        deletedPresets.clear();
        deletedPresets.addAll(remainingDeleted);
        saveDeletedPresets();
        if (currentTheme == null || !themes.contains(currentTheme)) {
            currentTheme = themes.get(0);
        }
        return restored;
    }

    private static boolean isReservedThemeName(String name) {
        if (name == null) return false;
        String trimmed = name.trim();
        return "deleted_presets".equalsIgnoreCase(trimmed)
                || DELETED_PRESETS_FILE.equalsIgnoreCase(trimmed);
    }

    private boolean isStorageFileInUse(String fileName) {
        for (Theme t : themes) {
            String f = t.getStorageFileName();
            if (f != null && f.equalsIgnoreCase(fileName)) {
                return true;
            }
        }
        return false;
    }

    private String allocateUniqueFileName(String displayName) {
        String stem = sanitizeFileName(displayName);
        if ("deleted_presets".equalsIgnoreCase(stem)) {
            stem = "custom_deleted_presets";
        }
        File dir = getThemesDirectory();
        int idx = 1;
        while (true) {
            String candidate = (idx == 1) ? (stem + ".json") : (stem + "_" + idx + ".json");
            if (!DELETED_PRESETS_FILE.equalsIgnoreCase(candidate)
                    && !isStorageFileInUse(candidate)
                    && !new File(dir, candidate).exists()) {
                return candidate;
            }
            idx++;
        }
    }

    private static String sanitizeFileName(String name) {
        String safe = name.replaceAll("[^a-zA-Z0-9._ -]", "_").trim();
        return safe.isEmpty() ? "custom_theme" : safe;
    }

    public void nextTheme() {
        ensureCustomThemesLoaded();
        int idx = themes.indexOf(currentTheme);
        if (idx < 0) idx = 0;
        currentTheme = themes.get((idx + 1) % themes.size());
    }

    public int getAccentColor(int indexOffset) {
        return getAccentGradient(indexOffset, 3.0f);
    }

    public int getAccentGradient(int indexOffset, float speed) {
        double time = System.currentTimeMillis() / (1000.0 / Math.max(0.2f, speed));
        double wave = (Math.sin(time + indexOffset * 0.08) + 1.0) * 0.5;
        return ColorUtils.interpolateColor(currentTheme.getPrimaryColor(), currentTheme.getSecondaryColor(), (float) wave);
    }
}
