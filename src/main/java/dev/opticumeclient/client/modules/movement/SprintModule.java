package dev.opticumeclient.client.modules.movement;

import dev.opticumeclient.client.modules.Category;
import dev.opticumeclient.client.modules.Module;
import dev.opticumeclient.client.modules.ModuleManager;
import dev.opticumeclient.client.setting.BooleanSetting;
import dev.opticumeclient.client.setting.ModeSetting;

public class SprintModule extends Module {
    private static SprintModule INSTANCE;

    private final ModeSetting mode = new ModeSetting("Mode", "Режим работы спринта", "Legit", "Legit", "Omni");
    private final BooleanSetting stopOnEat = new BooleanSetting("StopOnEat", "Останавливать бег при еде/зельях (фикс отката яблока)", true);
    private final BooleanSetting checkHunger = new BooleanSetting("Hunger", "Не бежать при низком уровне еды (<= 6)", true);
    private final BooleanSetting checkCollision = new BooleanSetting("Collision", "Останавливать спринт при утыкании в стену", true);
    private final BooleanSetting keepSprint = new BooleanSetting("KeepSprint", "Сохранять спринт и скорость при ударе по цели", true);

    public SprintModule() {
        super("Sprint", "Автоматический бег с поддержкой Omni-Sprint и KeepSprint", Category.MOVEMENT);
        addSetting(mode);
        addSetting(stopOnEat);
        addSetting(checkHunger);
        addSetting(checkCollision);
        addSetting(keepSprint);
        INSTANCE = this;
        setEnabled(true);
    }

    public static SprintModule getInstance() {
        return INSTANCE;
    }

    @Override
    public String getDisplaySuffix() {
        return mode.getMode();
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.level == null || mc.player.isPassenger()) return;

        // Stop sprinting on eat/drink to avoid anticheat resetting consumption
        if (mc.player.isUsingItem()) {
            if (stopOnEat.isEnabled()) {
                if (mc.player.isSprinting()) {
                    mc.player.setSprinting(false);
                }
                return;
            }
            NoSlowModule noSlow = ModuleManager.getInstance().getModule(NoSlowModule.class);
            if (noSlow == null || !noSlow.isEnabled() || noSlow.isGrimSafe() || !noSlow.shouldCancelItemSlowdown(mc.player)) {
                if (mc.player.isSprinting()) {
                    mc.player.setSprinting(false);
                }
                return;
            }
        }

        // Check hunger
        if (checkHunger.isEnabled()) {
            boolean canSprintHunger = (float) mc.player.getFoodData().getFoodLevel() > 6.0f || mc.player.getAbilities().mayfly;
            if (!canSprintHunger) return;
        }

        // Check collision
        if (checkCollision.isEnabled() && mc.player.horizontalCollision) {
            return;
        }

        boolean moving;
        if (mode.is("Omni")) {
            moving = mc.player.input.getMoveVector().y != 0.0f || mc.player.input.getMoveVector().x != 0.0f;
        } else {
            moving = mc.player.input.hasForwardImpulse();
        }

        if (moving && !mc.player.isShiftKeyDown()) {
            mc.player.setSprinting(true);
        }
    }

    public boolean isOmni() {
        return isEnabled() && mode.is("Omni");
    }

    public boolean isKeepSprint() {
        return isEnabled() && keepSprint.isEnabled();
    }
}
