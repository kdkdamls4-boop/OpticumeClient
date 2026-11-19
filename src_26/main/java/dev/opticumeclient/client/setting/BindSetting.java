package dev.opticumeclient.client.setting;

import com.mojang.blaze3d.platform.InputConstants;
import dev.opticumeclient.client.gui.SystemModuleCard;
import net.minecraft.client.Minecraft;

/**
 * Bind setting supporting both keyboard keys (> 0) and mouse buttons (-100 + button).
 * Value -999 (or <= 0 except -100..-93) represents unbound.
 */
public class BindSetting extends Setting<Integer> {
    public static final int UNBOUND = -999;

    public BindSetting(String name, String description) {
        this(name, description, UNBOUND);
    }

    public BindSetting(String name, String description, int defaultBind) {
        super(name, description, defaultBind);
    }

    public boolean isBound() {
        Integer v = getValue();
        return v != null && v != UNBOUND && v != 0;
    }

    public int getBindCode() {
        Integer v = getValue();
        return v == null ? UNBOUND : v;
    }

    public boolean isMouseBind() {
        int code = getBindCode();
        return code >= -100 && code <= -90;
    }

    public boolean matchesInput(int keyOrMouseButton, boolean mouse) {
        if (!isBound()) return false;
        int encoded = mouse ? (-100 + keyOrMouseButton) : keyOrMouseButton;
        return getBindCode() == encoded;
    }

    public boolean isDownNow() {
        if (!isBound()) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return false;
        int code = getBindCode();
        try {
            if (isMouseBind()) {
                if (mc.mouseHandler == null) return false;
                int btn = code + 100;
                if (btn == InputConstants.MOUSE_BUTTON_LEFT || btn == 0) return mc.mouseHandler.isLeftPressed();
                if (btn == InputConstants.MOUSE_BUTTON_RIGHT) return mc.mouseHandler.isRightPressed();
                if (btn == InputConstants.MOUSE_BUTTON_MIDDLE) return mc.mouseHandler.isMiddlePressed();
                return false;
            }
            return InputConstants.isKeyDown(code);
        } catch (Throwable ignored) {
            return false;
        }
    }

    public String getBindLabel() {
        int code = getBindCode();
        if (code == UNBOUND || code == 0) {
            return "None";
        }
        if (code >= -100 && code <= -90) {
            int btn = code + 100;
            if (btn == InputConstants.MOUSE_BUTTON_LEFT || btn == 0) return "LMB";
            if (btn == InputConstants.MOUSE_BUTTON_RIGHT) return "RMB";
            if (btn == InputConstants.MOUSE_BUTTON_MIDDLE) return "MMB";
            return "MB" + btn;
        }
        return SystemModuleCard.getKeyName(code);
    }
}
