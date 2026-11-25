package dev.opticumeclient.client.util;

import com.sun.jna.Native;
import com.sun.jna.Platform;
import com.sun.jna.Pointer;
import com.sun.jna.win32.StdCallLibrary;
import net.minecraft.client.Minecraft;
import org.lwjgl.sdl.SDLProperties;
import org.lwjgl.sdl.SDLVideo;

/**
 * Windows-only JNA + SDL3 bridge to toggle SetWindowDisplayAffinity on the game HWND
 * (used by CaptureBypassModule to exclude the window from OBS/Discord/BitBlt captures).
 */
public final class Win32WindowBridge {
    public static final int WDA_NONE = 0x00000000;
    public static final int WDA_MONITOR = 0x00000001;
    public static final int WDA_EXCLUDEFROMCAPTURE = 0x00000011;

    private interface User32Ext extends StdCallLibrary {
        User32Ext INSTANCE = Native.load("user32", User32Ext.class);

        boolean SetWindowDisplayAffinity(Pointer hWnd, int dwAffinity);
    }

    private Win32WindowBridge() {
    }

    public static long getCurrentHwnd() {
        if (!Platform.isWindows()) {
            return 0L;
        }
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.getWindow() == null) {
                return 0L;
            }
            long sdlWindow = mc.getWindow().handle();
            if (sdlWindow == 0L) {
                return 0L;
            }
            int props = SDLVideo.SDL_GetWindowProperties(sdlWindow);
            if (props == 0) {
                return 0L;
            }
            return SDLProperties.SDL_GetPointerProperty(props, SDLVideo.SDL_PROP_WINDOW_WIN32_HWND_POINTER, 0L);
        } catch (Throwable ignored) {
            return 0L;
        }
    }

    public static boolean setExcludeFromCapture(long hwnd, boolean exclude) {
        if (!Platform.isWindows() || hwnd == 0L) {
            return false;
        }
        try {
            Pointer ptr = Pointer.createConstant(hwnd);
            if (exclude) {
                if (User32Ext.INSTANCE.SetWindowDisplayAffinity(ptr, WDA_EXCLUDEFROMCAPTURE)) {
                    return true;
                }
                return User32Ext.INSTANCE.SetWindowDisplayAffinity(ptr, WDA_MONITOR);
            }
            return User32Ext.INSTANCE.SetWindowDisplayAffinity(ptr, WDA_NONE);
        } catch (Throwable ignored) {
            return false;
        }
    }
}
