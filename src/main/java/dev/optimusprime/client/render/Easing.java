package dev.optimusprime.client.render;

public enum Easing {
    LINEAR {
        @Override
        public float apply(float x) {
            return x;
        }
    },
    EASE_OUT_QUAD {
        @Override
        public float apply(float x) {
            return 1.0f - (1.0f - x) * (1.0f - x);
        }
    },
    EASE_OUT_CUBIC {
        @Override
        public float apply(float x) {
            float inv = 1.0f - x;
            return 1.0f - inv * inv * inv;
        }
    },
    EASE_OUT_QUART {
        @Override
        public float apply(float x) {
            float inv = 1.0f - x;
            return 1.0f - inv * inv * inv * inv;
        }
    },
    EASE_IN_OUT_CUBIC {
        @Override
        public float apply(float x) {
            return x < 0.5f ? 4.0f * x * x * x : 1.0f - (float) Math.pow(-2.0f * x + 2.0f, 3) / 2.0f;
        }
    },
    EASE_OUT_BACK {
        @Override
        public float apply(float x) {
            float c1 = 1.70158f;
            float c3 = c1 + 1.0f;
            float xm1 = x - 1.0f;
            return 1.0f + c3 * xm1 * xm1 * xm1 + c1 * xm1 * xm1;
        }
    },
    EASE_IN_BACK {
        @Override
        public float apply(float x) {
            float c1 = 1.70158f;
            float c3 = c1 + 1.0f;
            return c3 * x * x * x - c1 * x * x;
        }
    },
    EASE_OUT_EXPO {
        @Override
        public float apply(float x) {
            return x >= 1.0f ? 1.0f : 1.0f - (float) Math.pow(2.0f, -10.0f * x);
        }
    },
    DECELERATE {
        @Override
        public float apply(float x) {
            return 1.0f - (1.0f - x) * (1.0f - x);
        }
    };

    public abstract float apply(float progress);
}
