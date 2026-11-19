package dev.opticumeclient.client.autobuy;

import java.util.Locale;

/**
 * Ready-made HolyWorld / FunTime item & price presets selectable inside the AutoBuy GUI tab.
 */
public final class AutoBuyPresets {
    public enum Preset {
        HW_META("HW Мета"),
        HW_CONSUMABLES("HW Расход"),
        FT_FARM("FT Фарм"),
        CLEAR("Сброс");

        public final String label;

        Preset(String label) {
            this.label = label;
        }
    }

    private AutoBuyPresets() {
    }

    public static void apply(Preset preset) {
        AutoBuyConfig cfg = AutoBuyConfig.cfg();
        for (ItemEntry e : cfg.entries) {
            if (e == null) continue;
            if (preset == Preset.CLEAR) {
                e.enabled = false;
                e.maxUnitPrice = 0L;
                continue;
            }
            String name = e.displayName == null ? "" : e.displayName.toLowerCase(Locale.ROOT);
            String id = e.itemId == null ? "" : e.itemId.toLowerCase(Locale.ROOT);
            switch (preset) {
                case HW_META -> {
                    boolean match = name.contains("eternity") || name.contains("infinity") || name.contains("stinger")
                            || id.equals("minecraft:totem_of_undying") || id.equals("minecraft:player_head");
                    e.enabled = match;
                    if (match && e.maxUnitPrice <= 0L) {
                        if (name.contains("infinity")) e.maxUnitPrice = 8_000_000L;
                        else if (name.contains("eternity")) e.maxUnitPrice = 4_500_000L;
                        else if (name.contains("stinger")) e.maxUnitPrice = 2_500_000L;
                        else e.maxUnitPrice = 1_500_000L;
                    }
                }
                case HW_CONSUMABLES -> {
                    boolean match = id.contains("potion") || id.contains("experience_bottle")
                            || id.contains("golden_apple") || id.equals("minecraft:ender_pearl")
                            || id.equals("minecraft:chorus_fruit") || id.equals("minecraft:totem_of_undying")
                            || id.endsWith("_spawn_egg");
                    e.enabled = match;
                    if (match && e.maxUnitPrice <= 0L) {
                        if (id.contains("enchanted_golden_apple")) e.maxUnitPrice = 850_000L;
                        else if (id.contains("golden_apple")) e.maxUnitPrice = 95_000L;
                        else if (id.contains("experience_bottle")) e.maxUnitPrice = 250_000L;
                        else if (id.endsWith("_spawn_egg")) e.maxUnitPrice = 1_800_000L;
                        else e.maxUnitPrice = 300_000L;
                    }
                }
                case FT_FARM -> {
                    boolean match = !e.requireCustom && !e.matchAnySword && !e.isArmor();
                    e.enabled = match;
                    if (match && e.maxUnitPrice <= 0L) {
                        if (id.contains("netherite")) e.maxUnitPrice = 350_000L;
                        else if (id.contains("diamond")) e.maxUnitPrice = 45_000L;
                        else e.maxUnitPrice = 75_000L;
                    }
                }
                default -> {
                }
            }
        }
        AutoBuyConfig.save();
    }
}
