package dev.opticumeclient.client.modules.movement;

import dev.opticumeclient.client.modules.Category;
import dev.opticumeclient.client.modules.Module;
import dev.opticumeclient.client.setting.BooleanSetting;
import dev.opticumeclient.client.setting.ModeSetting;
import dev.opticumeclient.client.setting.NumberSetting;

public class VelocityModule extends Module {
    private static VelocityModule INSTANCE;

    private final ModeSetting mode = new ModeSetting("Mode", "Режим снижения отбрасывания", "Cancel", "Cancel", "Percentage", "Grim Skip");
    private final NumberSetting horizontal = new NumberSetting("Horizontal", "Горизонтальное отбрасывание (%)", 0.0, 0.0, 100.0, 5.0);
    private final NumberSetting vertical = new NumberSetting("Vertical", "Вертикальное отбрасывание (%)", 0.0, 0.0, 100.0, 5.0);
    private final BooleanSetting explosions = new BooleanSetting("Explosions", "Убирать отбрасывание от взрывов кристаллов и ТНТ", true);

    public VelocityModule() {
        super("Velocity", "Убирает или снижает отбрасывание от ударов и взрывов", Category.MOVEMENT);
        addSetting(mode);
        addSetting(horizontal);
        addSetting(vertical);
        addSetting(explosions);
        INSTANCE = this;
    }

    public static VelocityModule getInstance() {
        return INSTANCE;
    }

    @Override
    public String getDisplaySuffix() {
        if (mode.is("Percentage")) {
            return horizontal.getIntValue() + "% " + vertical.getIntValue() + "%";
        }
        return mode.getMode();
    }

    public boolean isCancelMode() {
        return mode.is("Cancel");
    }

    public boolean isGrimSkipMode() {
        return mode.is("Grim Skip");
    }

    public double getHorizontalFactor() {
        return horizontal.getValue() / 100.0;
    }

    public double getVerticalFactor() {
        return vertical.getValue() / 100.0;
    }

    public boolean isExplosions() {
        return explosions.isEnabled();
    }
}
