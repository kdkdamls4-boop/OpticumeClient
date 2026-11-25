package dev.opticumeclient.client.ui.menu;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Persistent preferences for OpticumeClient's multi-layout main menu
 * (Classic Desire Center, osu! Right, osu! Left, and Split Dock) plus visual toggles.
 */
public final class MenuLayoutPreferences {
    public enum MenuLayoutMode {
        OSU_LAZER("osu! Lazer", "osu!"),
        OSU_RIGHT("Side Right", "Right"),
        OSU_LEFT("Side Left", "Left"),
        SPLIT("Split Dock", "Split"),
        DESIRE_CENTER("Desire Center", "Center");

        public final String label;
        public final String shortLabel;

        MenuLayoutMode(String label, String shortLabel) {
            this.label = label;
            this.shortLabel = shortLabel;
        }

        public MenuLayoutMode next() {
            MenuLayoutMode[] vals = values();
            return vals[(ordinal() + 1) % vals.length];
        }

        public static MenuLayoutMode fromName(String raw) {
            if (raw == null || raw.isBlank()) return OSU_LAZER;
            for (MenuLayoutMode m : values()) {
                if (m.name().equalsIgnoreCase(raw.trim()) || m.label.equalsIgnoreCase(raw.trim())) {
                    return m;
                }
            }
            return OSU_LAZER;
        }
    }

    private static final String KEY_MODE = "menu_layout_mode";
    private static final String KEY_MIGRATED_V2 = "menu_layout_v2_migrated";
    private static final String KEY_OSU_START_EXPANDED = "menu_osu_start_expanded";
    private static final String KEY_SLANT = "menu_slant";
    private static final String KEY_PULSE = "menu_pulse_logo";
    private static final String KEY_MUSIC = "menu_music_widget";
    private static final String KEY_PER_WALLPAPER = "menu_layout_per_wallpaper";
    private static final String KEY_WALLPAPER_MAP = "menu_wallpaper_layouts";

    private static volatile boolean loaded = false;
    private static MenuLayoutMode globalMode = MenuLayoutMode.OSU_LAZER;
    private static boolean osuStartExpanded = true;
    private static boolean slantEnabled = true;
    private static boolean pulseLogoEnabled = true;
    private static boolean musicWidgetEnabled = true;
    private static boolean perWallpaperMode = false;
    private static final Map<String, MenuLayoutMode> WALLPAPER_MODES = new ConcurrentHashMap<>();

    private MenuLayoutPreferences() {
    }

    public static void ensureLoaded() {
        if (loaded) return;
        synchronized (MenuLayoutPreferences.class) {
            if (loaded) return;
            if (!MenuConfigStore.getBoolean(KEY_MIGRATED_V2, false)) {
                globalMode = MenuLayoutMode.OSU_LAZER;
                MenuConfigStore.save(obj -> {
                    obj.addProperty(KEY_MODE, MenuLayoutMode.OSU_LAZER.name());
                    obj.addProperty(KEY_MIGRATED_V2, true);
                });
            } else {
                globalMode = MenuLayoutMode.fromName(MenuConfigStore.getString(KEY_MODE, MenuLayoutMode.OSU_LAZER.name()));
            }
            osuStartExpanded = MenuConfigStore.getBoolean(KEY_OSU_START_EXPANDED, true);
            slantEnabled = MenuConfigStore.getBoolean(KEY_SLANT, true);
            pulseLogoEnabled = MenuConfigStore.getBoolean(KEY_PULSE, true);
            musicWidgetEnabled = MenuConfigStore.getBoolean(KEY_MUSIC, true);
            perWallpaperMode = MenuConfigStore.getBoolean(KEY_PER_WALLPAPER, false);
            WALLPAPER_MODES.clear();
            JsonObject map = MenuConfigStore.getObject(KEY_WALLPAPER_MAP);
            if (map != null) {
                for (Map.Entry<String, JsonElement> e : map.entrySet()) {
                    if (e.getValue() != null && e.getValue().isJsonPrimitive()) {
                        WALLPAPER_MODES.put(e.getKey(), MenuLayoutMode.fromName(e.getValue().getAsString()));
                    }
                }
            }
            loaded = true;
        }
    }

    public static MenuLayoutMode getActiveMode(String wallpaperId) {
        ensureLoaded();
        if (perWallpaperMode && wallpaperId != null && !wallpaperId.isBlank()) {
            MenuLayoutMode mapped = WALLPAPER_MODES.get(wallpaperId);
            if (mapped != null) {
                return mapped;
            }
        }
        return globalMode;
    }

    public static MenuLayoutMode cycleActiveMode(String wallpaperId) {
        MenuLayoutMode next = getActiveMode(wallpaperId).next();
        setActiveMode(next, wallpaperId);
        return next;
    }

    public static void setActiveMode(MenuLayoutMode mode, String wallpaperId) {
        ensureLoaded();
        if (mode == null) return;
        if (perWallpaperMode && wallpaperId != null && !wallpaperId.isBlank()) {
            WALLPAPER_MODES.put(wallpaperId, mode);
            MenuConfigStore.save(obj -> {
                JsonObject map = obj.has(KEY_WALLPAPER_MAP) && obj.get(KEY_WALLPAPER_MAP).isJsonObject()
                        ? obj.getAsJsonObject(KEY_WALLPAPER_MAP)
                        : new JsonObject();
                map.addProperty(wallpaperId, mode.name());
                obj.add(KEY_WALLPAPER_MAP, map);
            });
            return;
        }
        globalMode = mode;
        MenuConfigStore.save(obj -> obj.addProperty(KEY_MODE, mode.name()));
    }

    public static boolean isSlantEnabled() {
        ensureLoaded();
        return slantEnabled;
    }

    public static void setSlantEnabled(boolean enabled) {
        ensureLoaded();
        slantEnabled = enabled;
        MenuConfigStore.save(obj -> obj.addProperty(KEY_SLANT, enabled));
    }

    public static boolean isPulseLogoEnabled() {
        ensureLoaded();
        return pulseLogoEnabled;
    }

    public static void setPulseLogoEnabled(boolean enabled) {
        ensureLoaded();
        pulseLogoEnabled = enabled;
        MenuConfigStore.save(obj -> obj.addProperty(KEY_PULSE, enabled));
    }

    public static boolean isMusicWidgetEnabled() {
        ensureLoaded();
        return musicWidgetEnabled;
    }

    public static void setMusicWidgetEnabled(boolean enabled) {
        ensureLoaded();
        musicWidgetEnabled = enabled;
        MenuConfigStore.save(obj -> obj.addProperty(KEY_MUSIC, enabled));
    }

    public static boolean isPerWallpaperMode() {
        ensureLoaded();
        return perWallpaperMode;
    }

    public static void setPerWallpaperMode(boolean enabled) {
        ensureLoaded();
        perWallpaperMode = enabled;
        MenuConfigStore.save(obj -> obj.addProperty(KEY_PER_WALLPAPER, enabled));
    }

    public static boolean isOsuStartExpanded() {
        ensureLoaded();
        return osuStartExpanded;
    }

    public static void setOsuStartExpanded(boolean enabled) {
        ensureLoaded();
        osuStartExpanded = enabled;
        MenuConfigStore.save(obj -> obj.addProperty(KEY_OSU_START_EXPANDED, enabled));
    }
}
