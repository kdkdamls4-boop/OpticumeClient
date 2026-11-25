package dev.opticumeclient.client.ui.menu;

import com.google.gson.JsonArray;
import dev.opticumeclient.client.render.TextureAssets;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Gallery background options for {@link OpticumeMainMenuScreen}, {@link OpticumeAltScreen},
 * and {@link OpticumeGalleryScreen}. Index 0 is the vanilla animated Minecraft panorama (default),
 * followed by 20 built-in wallpapers (8 original + 12 from wyvern-dlc) and user-uploaded custom wallpapers.
 */
public final class MenuWallpapers {
    public enum Category {
        MINECRAFT("Minecraft"),
        SKY_ABSTRACT("Sky & Abstract"),
        ANIME("Anime"),
        CUSTOM("Custom");

        private final String label;

        Category(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public record Wallpaper(
            String id,
            String name,
            Identifier image,
            Identifier preview,
            float aspect,
            boolean panorama,
            Category category,
            boolean custom
    ) {
    }

    private static final String CONFIG_KEY = "wallpaper";
    private static final String FAVORITES_KEY = "favorite_wallpapers";

    private static final List<Wallpaper> BUILTIN = List.of(
            new Wallpaper("panorama", "Vanilla Panorama", TextureAssets.MENU_PANORAMA, TextureAssets.MENU_PANORAMA, 1.7778f, true, Category.MINECRAFT, false),
            wallpaper(1, "Jungle Mist", 1.7778f, Category.MINECRAFT),
            wallpaper(2, "Overgrown", 1.7582f, Category.MINECRAFT),
            wallpaper(3, "Golden Hour", 1.7778f, Category.MINECRAFT),
            wallpaper(4, "Sakura", 1.7778f, Category.SKY_ABSTRACT),
            wallpaper(5, "Dusk Ridge", 1.7778f, Category.MINECRAFT),
            wallpaper(6, "Aurora", 1.7778f, Category.SKY_ABSTRACT),
            wallpaper(7, "Nebula", 1.7778f, Category.SKY_ABSTRACT),
            wallpaper(8, "Blue Hour", 1.7778f, Category.SKY_ABSTRACT),
            wallpaper(9, "Sunset Valley", 1.7910f, Category.MINECRAFT),
            wallpaper(10, "Snowy Islands", 1.7910f, Category.MINECRAFT),
            wallpaper(11, "Misty Peaks", 1.7910f, Category.MINECRAFT),
            wallpaper(12, "BloodCore", 1.7917f, Category.ANIME),
            wallpaper(13, "Five Brides", 1.7917f, Category.ANIME),
            wallpaper(14, "Itsuki", 1.7917f, Category.ANIME),
            wallpaper(15, "Tokyo Ghoul", 1.7910f, Category.ANIME),
            wallpaper(16, "Sylphie Sunset", 1.7917f, Category.ANIME),
            wallpaper(17, "Sylphie Winter", 1.7917f, Category.ANIME),
            wallpaper(18, "Roxy Field", 1.7917f, Category.ANIME),
            wallpaper(19, "Roxy Village", 1.7917f, Category.ANIME),
            wallpaper(20, "Alya", 1.7917f, Category.ANIME)
    );

    private static boolean loaded;
    private static String selectedId = "panorama";
    private static final Set<String> FAVORITES = new LinkedHashSet<>();

    private MenuWallpapers() {
    }

    private static Wallpaper wallpaper(int index, String name, float aspect, Category category) {
        String base = "textures/menu/wallpapers/wallpaper_" + index;
        return new Wallpaper(
                String.valueOf(index),
                name,
                Identifier.fromNamespaceAndPath("opticumeclient", base + ".png"),
                Identifier.fromNamespaceAndPath("opticumeclient", base + "_preview.png"),
                aspect,
                false,
                category,
                false
        );
    }

    public static List<Wallpaper> all() {
        List<Wallpaper> custom = CustomWallpaperStore.getAll();
        if (custom.isEmpty()) {
            return BUILTIN;
        }
        List<Wallpaper> combined = new ArrayList<>(BUILTIN.size() + custom.size());
        combined.addAll(BUILTIN);
        combined.addAll(custom);
        return combined;
    }

    public static int size() {
        return BUILTIN.size() + CustomWallpaperStore.getAll().size();
    }

    public static Wallpaper get(int index) {
        List<Wallpaper> list = all();
        if (list.isEmpty()) return null;
        return list.get(Math.max(0, Math.min(list.size() - 1, index)));
    }

    public static Wallpaper getById(String id) {
        if (id == null) return BUILTIN.get(0);
        for (Wallpaper w : all()) {
            if (w.id().equalsIgnoreCase(id)) {
                return w;
            }
        }
        return BUILTIN.get(0);
    }

    public static int indexOfId(String id) {
        List<Wallpaper> list = all();
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id().equalsIgnoreCase(id)) {
                return i;
            }
        }
        return 0;
    }

    public static int selectedIndex() {
        loadSelection();
        return indexOfId(selectedId);
    }

    public static String selectedId() {
        loadSelection();
        return get(selectedIndex()).id();
    }

    public static Wallpaper selected() {
        return get(selectedIndex());
    }

    public static void select(int index) {
        List<Wallpaper> list = all();
        if (index < 0 || index >= list.size()) return;
        selectById(list.get(index).id());
    }

    public static void selectById(String id) {
        loadSelection();
        Wallpaper match = null;
        for (Wallpaper w : all()) {
            if (w.id().equalsIgnoreCase(id)) {
                match = w;
                break;
            }
        }
        if (match == null) return;
        if (match.id().equals(selectedId)) return;
        selectedId = match.id();
        String saveId = match.id();
        MenuConfigStore.save(data -> data.addProperty(CONFIG_KEY, saveId));
    }

    public static boolean deleteCustom(String id) {
        loadSelection();
        boolean wasSelected = selectedId.equalsIgnoreCase(id);
        boolean ok = CustomWallpaperStore.deleteCustom(id);
        if (ok) {
            if (FAVORITES.remove(id.toLowerCase(Locale.ROOT))) {
                saveFavorites();
            }
            if (wasSelected) {
                selectedId = "panorama";
                MenuConfigStore.save(data -> data.addProperty(CONFIG_KEY, "panorama"));
            }
        }
        return ok;
    }

    public static boolean isFavorite(String id) {
        loadSelection();
        return id != null && FAVORITES.contains(id.toLowerCase(Locale.ROOT));
    }

    public static void toggleFavorite(String id) {
        loadSelection();
        if (id == null || id.isBlank()) return;
        String key = id.toLowerCase(Locale.ROOT);
        if (!FAVORITES.remove(key)) {
            FAVORITES.add(key);
        }
        saveFavorites();
    }

    private static void saveFavorites() {
        JsonArray arr = new JsonArray();
        for (String fav : FAVORITES) {
            arr.add(fav);
        }
        MenuConfigStore.save(data -> data.add(FAVORITES_KEY, arr));
    }

    private static void loadSelection() {
        CustomWallpaperStore.ensureLoaded();
        if (loaded) return;
        loaded = true;

        FAVORITES.clear();
        for (String fav : MenuConfigStore.getStringSet(FAVORITES_KEY)) {
            if (fav != null && !fav.isBlank()) {
                FAVORITES.add(fav.toLowerCase(Locale.ROOT));
            }
        }

        String raw = MenuConfigStore.getString(CONFIG_KEY, "panorama").trim();
        for (Wallpaper wp : all()) {
            if (wp.id().equalsIgnoreCase(raw) || wp.name().equalsIgnoreCase(raw)) {
                selectedId = wp.id();
                return;
            }
        }
        selectedId = "panorama";
    }
}
