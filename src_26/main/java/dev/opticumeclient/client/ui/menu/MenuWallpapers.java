package dev.opticumeclient.client.ui.menu;

import dev.opticumeclient.client.render.TextureAssets;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * Gallery background options for {@link OpticumeMainMenuScreen} and {@link OpticumeAltScreen}.
 * Index 0 is the vanilla animated Minecraft panorama (default).
 */
public final class MenuWallpapers {
    public record Wallpaper(String id, String name, Identifier image, Identifier preview, float aspect, boolean panorama) {
    }

    private static final String CONFIG_KEY = "wallpaper";

    private static final List<Wallpaper> WALLPAPERS = List.of(
            new Wallpaper("panorama", "Vanilla Panorama", TextureAssets.MENU_PANORAMA, TextureAssets.MENU_PANORAMA, 1.7778f, true),
            wallpaper(1, "Jungle Mist", 1.7778f),
            wallpaper(2, "Overgrown", 1.7582f),
            wallpaper(3, "Golden Hour", 1.7778f),
            wallpaper(4, "Sakura", 1.7778f),
            wallpaper(5, "Dusk Ridge", 1.7778f),
            wallpaper(6, "Aurora", 1.7778f),
            wallpaper(7, "Nebula", 1.7778f),
            wallpaper(8, "Blue Hour", 1.7778f)
    );

    private static boolean loaded;
    private static int selected = 0;

    private MenuWallpapers() {
    }

    private static Wallpaper wallpaper(int index, String name, float aspect) {
        String base = "textures/menu/wallpapers/wallpaper_" + index;
        return new Wallpaper(
                String.valueOf(index),
                name,
                Identifier.fromNamespaceAndPath("opticumeclient", base + ".png"),
                Identifier.fromNamespaceAndPath("opticumeclient", base + "_preview.png"),
                aspect,
                false
        );
    }

    public static List<Wallpaper> all() {
        return WALLPAPERS;
    }

    public static int size() {
        return WALLPAPERS.size();
    }

    public static Wallpaper get(int index) {
        if (WALLPAPERS.isEmpty()) return null;
        return WALLPAPERS.get(Math.max(0, Math.min(WALLPAPERS.size() - 1, index)));
    }

    public static int selectedIndex() {
        loadSelection();
        return selected;
    }

    public static Wallpaper selected() {
        return get(selectedIndex());
    }

    public static void select(int index) {
        loadSelection();
        if (index < 0 || index >= WALLPAPERS.size() || index == selected) return;
        selected = index;
        String id = WALLPAPERS.get(index).id();
        MenuConfigStore.save(data -> data.addProperty(CONFIG_KEY, id));
    }

    private static void loadSelection() {
        if (loaded) return;
        loaded = true;
        String raw = MenuConfigStore.getString(CONFIG_KEY, "panorama").trim();
        for (int i = 0; i < WALLPAPERS.size(); i++) {
            Wallpaper wp = WALLPAPERS.get(i);
            if (wp.id().equalsIgnoreCase(raw) || wp.name().equalsIgnoreCase(raw)) {
                selected = i;
                return;
            }
        }
        selected = 0;
    }
}
