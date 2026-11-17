package dev.opticumeclient.client.modules.visuals.targetesp;

public final class JellyShape {
    private JellyShape() {
    }

    public static void generate(float width, float height, double phaseDeg, float progress, float particleSize, int layers, ParticleConsumer consumer) {
        float radiusBase = width * 1.65F;
        float spanY = height - 0.15F;
        float scale = Math.max(0.5F, 0.7F - 0.2F * progress);

        for (int angleDeg = 0; angleDeg < 360; angleDeg += 2) {
            double rad = Math.toRadians(angleDeg + phaseDeg);
            float px = (float) (Math.cos(rad) * radiusBase * scale);
            float pz = (float) (Math.sin(rad) * radiusBase * scale);

            for (int layer = 0; layer < layers; layer++) {
                float py = spanY / 1.7F + spanY / 2.0F * (float) Math.cos(Math.toRadians(phaseDeg / 1.5 + layer * 2));
                int alpha = Math.clamp((int) (255.0F * progress * ((float) layer / layers) * 0.08F), 0, 255);
                consumer.accept(px, py, pz, particleSize, alpha);
            }
        }

        int coreAlpha = Math.clamp((int) (255.0F * progress * 0.22F), 0, 255);

        for (int angleDeg = 0; angleDeg < 360; angleDeg += 2) {
            double rad = Math.toRadians(angleDeg + phaseDeg);
            float px = (float) (Math.cos(rad) * radiusBase * scale);
            float pz = (float) (Math.sin(rad) * radiusBase * scale);
            float py = spanY / 1.75F + spanY / 2.0F * (float) Math.cos(Math.toRadians(phaseDeg / 1.5 + 30.0));
            consumer.accept(px, py, pz, particleSize * 1.08F, coreAlpha);
        }
    }

    @FunctionalInterface
    public interface ParticleConsumer {
        void accept(float x, float y, float z, float size, int alpha);
    }
}
