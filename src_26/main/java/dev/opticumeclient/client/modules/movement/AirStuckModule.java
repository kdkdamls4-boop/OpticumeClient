package dev.opticumeclient.client.modules.movement;

import dev.opticumeclient.client.modules.Category;
import dev.opticumeclient.client.modules.Module;
import dev.opticumeclient.client.setting.BooleanSetting;
import dev.opticumeclient.client.setting.NumberSetting;
import net.minecraft.world.phys.Vec3;

/**
 * Freezes player velocity in mid-air and pauses outgoing ServerboundMovePlayerPacket updates
 * with an optional automatic safety timeout.
 */
public class AirStuckModule extends Module {
    private static AirStuckModule INSTANCE;

    private final BooleanSetting freezePackets = new BooleanSetting("CancelPackets", "Приостанавливать отправку пакетов перемещения", true);
    private final BooleanSetting autoTimeout = new BooleanSetting("AutoDisable", "Автоматически отключать по лимиту тиков", true);
    private final NumberSetting maxTicks = new NumberSetting("MaxTicks", "Максимум тиков зависания (20 тиков = 1 сек)", 60.0, 10.0, 200.0, 5.0);

    private int activeTicks = 0;

    public AirStuckModule() {
        super("AirStuck", "Зависание в воздухе с блокировкой перемещения и пакетов движения", Category.MOVEMENT);
        INSTANCE = this;
        maxTicks.visibleWhen(autoTimeout::isEnabled);
        addSetting(freezePackets);
        addSetting(autoTimeout);
        addSetting(maxTicks);
    }

    public static AirStuckModule getInstance() {
        return INSTANCE;
    }

    @Override
    public void onEnable() {
        activeTicks = 0;
    }

    @Override
    public void onDisable() {
        activeTicks = 0;
    }

    public boolean shouldCancelMovementPackets() {
        return isEnabled() && freezePackets.isEnabled() && mc.player != null;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.level == null) {
            setEnabled(false);
            return;
        }
        activeTicks++;
        mc.player.setDeltaMovement(Vec3.ZERO);
        mc.player.resetFallDistance();

        if (autoTimeout.isEnabled() && activeTicks >= maxTicks.getIntValue()) {
            setEnabled(false);
        }
    }

    @Override
    public String getDisplaySuffix() {
        return activeTicks + "t";
    }
}
