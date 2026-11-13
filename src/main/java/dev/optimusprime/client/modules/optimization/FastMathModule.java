package dev.optimusprime.client.modules.optimization;

import dev.optimusprime.client.modules.Category;
import dev.optimusprime.client.modules.Module;

public class FastMathModule extends Module {
    private static FastMathModule INSTANCE;
    private static final float[] SIN_TABLE = new float[65536];

    static {
        for (int i = 0; i < 65536; ++i) {
            SIN_TABLE[i] = (float) Math.sin((double) i * Math.PI * 2.0 / 65536.0);
        }
    }

    public FastMathModule() {
        super("FastMath", "Оптимизация математических тригонометрических вычислений", Category.OPTIMIZATION);
        INSTANCE = this;
        setEnabled(true);
    }

    public static FastMathModule getInstance() {
        return INSTANCE;
    }

    public static float sin(float rad) {
        return SIN_TABLE[(int) (rad * 10430.378f) & 65535];
    }

    public static float cos(float rad) {
        return SIN_TABLE[(int) (rad * 10430.378f + 16384.0f) & 65535];
    }
}
