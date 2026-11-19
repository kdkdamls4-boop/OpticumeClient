package dev.opticumeclient.client.modules.movement;

import dev.opticumeclient.client.modules.Category;
import dev.opticumeclient.client.modules.Module;
import dev.opticumeclient.client.setting.BooleanSetting;
import dev.opticumeclient.client.setting.ModeSetting;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Ported from Desire & Skycore (NoWebModule):
 * Cancels or reduces slowdown inside Cobwebs and Sweet Berry Bushes.
 */
public class NoWebModule extends Module {
    private static NoWebModule instance;

    private final ModeSetting modeSetting;
    private final BooleanSetting sweetBerriesSetting;

    public NoWebModule() {
        super("NoWeb", "Отключает замедление в паутине и кустах сладких ягод", Category.MOVEMENT);
        instance = this;

        this.modeSetting = addSetting(new ModeSetting(
                "Mode",
                "Режим обхода паутины",
                "Vanilla",
                "Vanilla", "Grim / FastFall"
        ));
        this.sweetBerriesSetting = addSetting(new BooleanSetting(
                "Sweet Berries",
                "Отключать замедление также в кустах сладких ягод",
                true
        ));
    }

    public static NoWebModule getInstance() {
        return instance;
    }

    public boolean shouldCancelStuck(BlockState state) {
        if (!isEnabled() || state == null) return false;
        if (state.is(Blocks.COBWEB)) {
            return modeSetting.isMode("Vanilla");
        }
        if (sweetBerriesSetting.isEnabled() && state.is(Blocks.SWEET_BERRY_BUSH)) {
            return true;
        }
        return false;
    }

    public boolean isGrimMode() {
        return isEnabled() && modeSetting.isMode("Grim / FastFall");
    }
}
