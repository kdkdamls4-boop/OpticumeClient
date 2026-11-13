package dev.optimusprime.client.modules.visuals;

import dev.optimusprime.client.modules.Category;
import dev.optimusprime.client.modules.Module;
import dev.optimusprime.client.render.ColorUtils;
import dev.optimusprime.client.setting.ColorSetting;

public class HitColorModule extends Module {
    private static HitColorModule INSTANCE;
    private final ColorSetting hitColor = new ColorSetting("Color", "Цвет вспышки при ударе", ColorUtils.OPTIMUS_RED);

    public HitColorModule() {
        super("HitColor", "Кастомный цвет вспышки урона мобов и игроков", Category.VISUALS);
        addSetting(hitColor);
        INSTANCE = this;
        setEnabled(true);
    }

    public static HitColorModule getInstance() {
        return INSTANCE;
    }

    public int getColor() {
        return isEnabled() ? hitColor.getValue() : 0xFFFF0000;
    }
}
