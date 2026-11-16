package dev.opticumeclient.client.render;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ThemeManager {
    private static final ThemeManager INSTANCE = new ThemeManager();
    private final List<Theme> themes = new ArrayList<>();
    private Theme currentTheme;

    private ThemeManager() {
        // 1. Nursultan Alpha (Deep violet + neon cyan-purple)
        themes.add(new Theme("Nursultan Alpha", 0xFF7B61FF, 0xFF00E5FF, 0xEC0B0C12, 0xD8131520, 0xFFF4F6FF, 0xFF8C92AC));
        // 2. Catlavan Acid (High-contrast dark graphite + acid magenta-cyan)
        themes.add(new Theme("Catlavan Acid", 0xFFFF2A85, 0xFF00F0FF, 0xF00A0A0F, 0xE012121A, 0xFFFFFFFF, 0xFF8E90A6));
        // 3. Expensive 3.1 Purple (Classic CIS lavender-indigo)
        themes.add(new Theme("Expensive Purple", 0xFF8A5CF6, 0xFFD946EF, 0xEC0D0D14, 0xD8151622, 0xFFF3F4F6, 0xFF9CA3AF));
        // 4. Rockstar Crimson (Liquid glass ruby-crimson)
        themes.add(new Theme("Rockstar Crimson", 0xFFFF2E54, 0xFFFF8A00, 0xEC0E0B0E, 0xD8191218, 0xFFFFF5F7, 0xFFA68B93));
        // 5. Wexside Ice (Arctic blue-cyan minimal)
        themes.add(new Theme("Wexside Ice", 0xFF38BDF8, 0xFF6366F1, 0xEC0B0F17, 0xD8131B28, 0xFFF0F9FF, 0xFF8496B0));
        // 6. Delta Cyber (Neon mint-emerald + electric blue)
        themes.add(new Theme("Delta Cyber", 0xFF00F5A0, 0xFF00D9F5, 0xEC091012, 0xD8111C20, 0xFFECFEFF, 0xFF7FA6B0));
        // 7. Celestial Neon (Hot pink + violet bloom)
        themes.add(new Theme("Celestial Neon", 0xFFFF007F, 0xFF7928CA, 0xEC0E0A14, 0xD8181224, 0xFFFDF2F8, 0xFFA288B8));
        // 8. ThunderHack Violet (Deep amethyst + magenta)
        themes.add(new Theme("ThunderHack", 0xFFA855F7, 0xFFEC4899, 0xEC0C0A12, 0xD8161322, 0xFFFAF5FF, 0xFF988BAE));
        // 9. Astolfo Sunset (Pastel pink-turquoise-gold)
        themes.add(new Theme("Astolfo Sunset", 0xFFFF6B9D, 0xFF4ECDC4, 0xEC100E16, 0xD81A1724, 0xFFFFF7FB, 0xFF9B92AC));
        // 10. Rise 6 Ocean (Sapphire + aqua wave)
        themes.add(new Theme("Rise Ocean", 0xFF2563EB, 0xFF06B6D4, 0xEC090D16, 0xD8111827, 0xFFEFF6FF, 0xFF8295B8));
        // 11. DeadCode Matrix (Cyber lime + emerald)
        themes.add(new Theme("DeadCode Matrix", 0xFF22C55E, 0xFFA3E635, 0xEC09110C, 0xD8111E16, 0xFFF0FDF4, 0xFF7EA38C));
        // 12. Vape V4 Ghost (Stealth graphite + electric blue)
        themes.add(new Theme("Vape Ghost", 0xFF3B82F6, 0xFF60A5FA, 0xF0101114, 0xE018191E, 0xFFE5E7EB, 0xFF7B8190));

        this.currentTheme = themes.get(0);
    }

    public static ThemeManager getInstance() {
        return INSTANCE;
    }

    public List<Theme> getThemes() {
        return Collections.unmodifiableList(themes);
    }

    public Theme getCurrentTheme() {
        return currentTheme;
    }

    public void setCurrentTheme(Theme theme) {
        if (theme != null) {
            this.currentTheme = theme;
        }
    }

    public void setThemeByName(String name) {
        for (Theme t : themes) {
            if (t.getName().equalsIgnoreCase(name)) {
                this.currentTheme = t;
                return;
            }
        }
    }

    public void nextTheme() {
        int idx = themes.indexOf(currentTheme);
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
