package dev.opticumeclient.client.modules.movement;

import dev.opticumeclient.client.modules.Category;
import dev.opticumeclient.client.modules.Module;
import dev.opticumeclient.client.setting.BooleanSetting;

public class NoPushModule extends Module {
    private static NoPushModule INSTANCE;

    private final BooleanSetting entities = new BooleanSetting("Entities", "Отключает выталкивание от игроков и мобов", true);
    private final BooleanSetting fluids = new BooleanSetting("Fluids", "Отключает снос течением воды и лавы", true);
    private final BooleanSetting blocks = new BooleanSetting("Blocks", "Отключает выталкивание из твёрдых блоков", true);

    public NoPushModule() {
        super("NoPush", "Убирает выталкивание от сущностей, воды и блоков", Category.MOVEMENT);
        addSetting(entities);
        addSetting(fluids);
        addSetting(blocks);
        INSTANCE = this;
    }

    public static NoPushModule getInstance() {
        return INSTANCE;
    }

    public boolean isEntities() {
        return isEnabled() && entities.isEnabled();
    }

    public boolean isFluids() {
        return isEnabled() && fluids.isEnabled();
    }

    public boolean isBlocks() {
        return isEnabled() && blocks.isEnabled();
    }
}
