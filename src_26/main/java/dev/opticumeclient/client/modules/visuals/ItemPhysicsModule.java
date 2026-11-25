package dev.opticumeclient.client.modules.visuals;

import dev.opticumeclient.client.modules.Category;
import dev.opticumeclient.client.modules.Module;
import dev.opticumeclient.client.setting.BooleanSetting;
import dev.opticumeclient.client.setting.ModeSetting;

/**
 * Makes dropped item entities rest realistically flat on the ground without bobbing up and down.
 */
public class ItemPhysicsModule extends Module {
    private static ItemPhysicsModule instance;

    private final ModeSetting mode = new ModeSetting("Style", "Режим физики лежачих предметов", "Flat", "Flat", "NoBob");
    private final BooleanSetting randomAngle = new BooleanSetting("Random Rotation", "Случайный поворот лежащего предмета по горизонтали", true);

    public ItemPhysicsModule() {
        super("ItemPhysics", "Реалистичное лежание выброшенных предметов на земле без подпрыгивания", Category.VISUALS);
        instance = this;
        addSetting(mode);
        addSetting(randomAngle);
    }

    public static ItemPhysicsModule getInstance() {
        return instance;
    }

    public static boolean isActive() {
        return instance != null && instance.isEnabled();
    }

    public boolean isFlatMode() {
        return mode.is("Flat");
    }

    public boolean isRandomAngle() {
        return randomAngle.isEnabled();
    }
}
