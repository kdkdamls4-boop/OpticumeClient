package dev.opticumeclient.client.modules.visuals.targetesp;

import dev.opticumeclient.client.render.ThemeManager;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.Mth;
import org.joml.Vector4f;

import java.awt.Color;

public final class ArgbUtil {
    private ArgbUtil() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    public static int alpha(int argb) {
        return argb >> 24 & 0xFF;
    }

    public static int red(int argb) {
        return argb >> 16 & 0xFF;
    }

    public static int green(int argb) {
        return argb >> 8 & 0xFF;
    }

    public static int blue(int argb) {
        return argb & 0xFF;
    }

    public static Vector4f toVector4f(int argb) {
        float a = (argb >> 24 & 0xFF) / 255.0F;
        float r = (argb >> 16 & 0xFF) / 255.0F;
        float g = (argb >> 8 & 0xFF) / 255.0F;
        float b = (argb & 0xFF) / 255.0F;
        return new Vector4f(r, g, b, a);
    }

    public static float[] toFloatArray(int argb) {
        return new float[]{
                (argb >> 16 & 0xFF) / 255.0F,
                (argb >> 8 & 0xFF) / 255.0F,
                (argb & 0xFF) / 255.0F,
                (argb >> 24 & 0xFF) / 255.0F
        };
    }

    public static int pack(int r, int g, int b, double a) {
        return (int) a << 24 | r << 16 | g << 8 | b;
    }

    public static int packOpaque(int r, int g, int b) {
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    public static int withAlphaFactor(int argb, float factor) {
        int a = Math.min(255, Math.max(0, (int) (factor * 255.0F)));
        return pack(red(argb), green(argb), blue(argb), a);
    }

    public static int scaleAll(int argb, float factor) {
        int r = (int) (red(argb) * factor);
        int g = (int) (green(argb) * factor);
        int b = (int) (blue(argb) * factor);
        int a = (int) (alpha(argb) * factor);
        return pack(r, g, b, a);
    }

    public static int lerp(int c1, int c2, float delta) {
        float[] a = toFloatArray(c1);
        float[] b = toFloatArray(c2);
        return pack(
                (int) Mth.lerp(delta, a[0] * 255.0F, b[0] * 255.0F),
                (int) Mth.lerp(delta, a[1] * 255.0F, b[1] * 255.0F),
                (int) Mth.lerp(delta, a[2] * 255.0F, b[2] * 255.0F),
                (int) Mth.lerp(delta, a[3] * 255.0F, b[3] * 255.0F)
        );
    }

    public static int lerpVec(int c1, int c2, float delta) {
        Vector4f v1 = toVector4f(c1).mul(255.0F);
        Vector4f v2 = toVector4f(c2).mul(255.0F);
        return pack(
                (int) Mth.lerp(delta, v1.x, v2.x),
                (int) Mth.lerp(delta, v1.y, v2.y),
                (int) Mth.lerp(delta, v1.z, v2.z),
                Mth.lerp(delta, v1.w, v2.w)
        );
    }

    public static int waveGradient(int c1, int c2, float phaseOffset, int alpha) {
        if ((c1 & 0xFFFFFF) == (c2 & 0xFFFFFF)) {
            return pack(red(c1), green(c1), blue(c1), alpha);
        }
        float phase = ((float) (System.currentTimeMillis() % 10000L) / 10000.0F + phaseOffset) % 1.0F;
        if (phase < 0.0F) {
            phase++;
        }
        float t = 0.5F - 0.5F * (float) Math.cos(phase * Math.PI * 2.0);
        int mixed = lerp(c1, c2, t);
        return pack(red(mixed), green(mixed), blue(mixed), alpha);
    }

    public static int hsbGradient(int c1, int c2, int offset, int speedDivisor) {
        int angle = (int) ((System.currentTimeMillis() / speedDivisor + offset) % 360L);
        angle = (angle > 180 ? 360 - angle : angle) + 180;
        int mixed = lerp(c1, c2, Mth.clamp(angle / 180.0F - 1.0F, 0.0F, 1.0F));
        float[] rgba = toFloatArray(mixed);
        float[] hsb = Color.RGBtoHSB((int) (rgba[0] * 255.0F), (int) (rgba[1] * 255.0F), (int) (rgba[2] * 255.0F), null);
        hsb[1] = Math.min(hsb[1] * 1.5F, 1.0F);
        return Color.HSBtoRGB(hsb[0], hsb[1], hsb[2]);
    }

    public static int themeWave(int index) {
        var theme = ThemeManager.getInstance().getCurrentTheme();
        return waveGradient(theme.getPrimaryColor(), theme.getSecondaryColor(), index * 16.0F / 360.0F, 255);
    }

    public static int parseHex(String hex) {
        String s = hex.trim();
        if (s.startsWith("#")) {
            s = s.substring(1);
        }
        if (s.startsWith("0x") || s.startsWith("0X")) {
            s = s.substring(2);
        }
        if (s.length() == 3 || s.length() == 4) {
            char r = s.charAt(0);
            char g = s.charAt(1);
            char b = s.charAt(2);
            char a = s.length() == 4 ? s.charAt(3) : 'F';
            s = "" + r + r + g + g + b + b + a + a;
        }
        if (s.length() == 6) {
            int r = Integer.parseInt(s.substring(0, 2), 16);
            int g = Integer.parseInt(s.substring(2, 4), 16);
            int b = Integer.parseInt(s.substring(4, 6), 16);
            return pack(r, g, b, 255.0);
        } else if (s.length() == 8) {
            int r = Integer.parseInt(s.substring(0, 2), 16);
            int g = Integer.parseInt(s.substring(2, 4), 16);
            int b = Integer.parseInt(s.substring(4, 6), 16);
            int a = Integer.parseInt(s.substring(6, 8), 16);
            return pack(r, g, b, a);
        } else {
            throw new IllegalArgumentException(hex);
        }
    }

    public static Component gradientText(String text) {
        MutableComponent comp = Component.empty();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            MutableComponent part = Component.literal(String.valueOf(c))
                    .setStyle(Style.EMPTY.withColor(new Color(themeWave(i)).getRGB()));
            comp.append(part);
        }
        return comp;
    }
}
