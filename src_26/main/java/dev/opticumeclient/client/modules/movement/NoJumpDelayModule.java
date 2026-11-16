package dev.opticumeclient.client.modules.movement;

import dev.opticumeclient.client.modules.Category;
import dev.opticumeclient.client.modules.Module;

public class NoJumpDelayModule extends Module {
    private static NoJumpDelayModule INSTANCE;

    public NoJumpDelayModule() {
        super("NoJumpDelay", "Убирает задержку между прыжками при удержании пробела", Category.MOVEMENT);
        INSTANCE = this;
    }

    public static NoJumpDelayModule getInstance() {
        return INSTANCE;
    }
}
