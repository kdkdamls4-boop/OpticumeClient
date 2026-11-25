package dev.opticumeclient.client.modules.other;

import com.sun.jna.Platform;
import dev.opticumeclient.client.modules.Category;
import dev.opticumeclient.client.modules.Module;
import dev.opticumeclient.client.util.ChatUtils;
import dev.opticumeclient.client.util.Win32WindowBridge;

/**
 * Excludes the Minecraft window from Windows capture (OBS, Discord screen share, BitBlt)
 * using SDL3 HWND and Win32 SetWindowDisplayAffinity(WDA_EXCLUDEFROMCAPTURE).
 */
public class CaptureBypassModule extends Module {
    private static CaptureBypassModule instance;
    private long appliedHwnd = 0L;

    public CaptureBypassModule() {
        super("CaptureBypass", "Скрытие окна игры от захвата OBS/Discord/скриншотов (Windows)", Category.OTHER);
        instance = this;
    }

    public static CaptureBypassModule getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        super.onEnable();
        if (!Platform.isWindows()) {
            ChatUtils.sendError("CaptureBypass доступен только на Windows.");
            setEnabled(false);
            return;
        }
        long hwnd = Win32WindowBridge.getCurrentHwnd();
        if (hwnd == 0L || !Win32WindowBridge.setExcludeFromCapture(hwnd, true)) {
            ChatUtils.sendError("Не удалось применить SetWindowDisplayAffinity к окну SDL3.");
            setEnabled(false);
            return;
        }
        appliedHwnd = hwnd;
    }

    @Override
    public void onTick() {
        long currentHwnd = Win32WindowBridge.getCurrentHwnd();
        if (currentHwnd != 0L && currentHwnd != appliedHwnd) {
            if (Win32WindowBridge.setExcludeFromCapture(currentHwnd, true)) {
                appliedHwnd = currentHwnd;
            }
        }
    }

    @Override
    public void onDisable() {
        super.onDisable();
        long hwnd = Win32WindowBridge.getCurrentHwnd();
        if (hwnd != 0L) {
            Win32WindowBridge.setExcludeFromCapture(hwnd, false);
        } else if (appliedHwnd != 0L) {
            Win32WindowBridge.setExcludeFromCapture(appliedHwnd, false);
        }
        appliedHwnd = 0L;
    }
}
