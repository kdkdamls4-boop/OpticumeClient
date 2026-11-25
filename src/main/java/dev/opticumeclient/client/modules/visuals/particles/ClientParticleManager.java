package dev.opticumeclient.client.modules.visuals.particles;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

public final class ClientParticleManager {
    private static final ClientParticleManager INSTANCE = new ClientParticleManager();

    private final ArrayDeque<Entry> entries = new ArrayDeque<>();
    private final ArrayDeque<Entry> pendingEntries = new ArrayDeque<>();
    private int limit = 16384;
    private boolean ticking;

    private ClientParticleManager() {}

    public static ClientParticleManager getInstance() {
        return INSTANCE;
    }

    public void setLimit(int limit) {
        this.limit = Math.max(1, limit);
        trim(0);
    }

    public int size() {
        return entries.size() + pendingEntries.size();
    }

    public boolean isEmpty() {
        return entries.isEmpty() && pendingEntries.isEmpty();
    }

    public Iterable<Entry> entries() {
        return entries;
    }

    public ClientParticle spawn(ClientParticle.Builder builder, int color, float size, int fadeTicks) {
        Objects.requireNonNull(builder, "builder");
        ClientParticle particle = new ClientParticle(builder);
        add(particle, color, size, fadeTicks);
        return particle;
    }

    public boolean add(ClientParticle particle, int color, float size, int fadeTicks) {
        if (particle == null || !particle.isAlive()) return false;

        Entry entry = new Entry(particle, color, Math.max(0.01f, size), resolveFadeTicks(particle, fadeTicks));
        trim(1);
        if (ticking) {
            pendingEntries.addLast(entry);
        } else {
            entry.prevAlpha = entry.alpha;
            entry.alpha = computeAlpha(particle, entry.fadeTicks);
            entries.addLast(entry);
        }
        return true;
    }

    public void clear() {
        entries.clear();
        pendingEntries.clear();
    }

    public void tick() {
        ticking = true;
        try {
            tickParticles();
        } finally {
            ticking = false;
        }
        flushPendingEntries();
    }

    public void updateVisibility() {
        if (entries.isEmpty()) return;
        Entity viewer = Minecraft.getInstance().player;
        for (Entry entry : entries) {
            ClientParticle particle = entry.particle;
            entry.visible = particle.isAlive() && withinDistance(viewer, particle);
        }
    }

    private static boolean withinDistance(Entity viewer, ClientParticle particle) {
        double maxSq = particle.getMaxDistanceSq();
        if (maxSq <= 0.0 || viewer == null) return true;
        return viewer.distanceToSqr(particle.getX(), particle.getY(), particle.getZ()) <= maxSq;
    }

    private void tickParticles() {
        Iterator<Entry> iterator = entries.iterator();
        while (iterator.hasNext()) {
            Entry entry = iterator.next();
            ClientParticle particle = entry.particle;
            particle.tick();
            if (!particle.isAlive()) {
                iterator.remove();
            } else {
                entry.prevAlpha = entry.alpha;
                entry.alpha = computeAlpha(particle, entry.fadeTicks);
            }
        }
    }

    private void flushPendingEntries() {
        Entry entry;
        while ((entry = pendingEntries.pollFirst()) != null) {
            if (!entry.particle.isAlive()) continue;
            entry.prevAlpha = entry.alpha;
            entry.alpha = computeAlpha(entry.particle, entry.fadeTicks);
            entries.addLast(entry);
        }
    }

    private void trim(int incoming) {
        int overflow = entries.size() + pendingEntries.size() + incoming - limit;
        while (overflow > 0) {
            Entry oldest = entries.pollFirst();
            if (oldest == null) oldest = pendingEntries.pollFirst();
            if (oldest == null) break;
            oldest.particle.kill();
            overflow--;
        }
    }

    private static int resolveFadeTicks(ClientParticle particle, int fadeTicks) {
        int lifetime = Math.max(1, particle.getLifetime());
        return fadeTicks <= 0 ? Math.max(1, lifetime / 2) : Math.max(1, Math.min(fadeTicks, lifetime));
    }

    private static float computeAlpha(ClientParticle particle, int fadeTicks) {
        int lifetime = Math.max(1, particle.getLifetime());
        float age = Math.min(lifetime, particle.getAge());

        if (particle.getInterpolationMode() == ParticleInterpolationMode.LINEAR) {
            return Math.clamp(1.0f - age / lifetime, 0.0f, 1.0f);
        }

        int fade = Math.max(1, Math.min(fadeTicks, lifetime));
        float fadeStart = lifetime - fade;
        float fadeOut = age > fadeStart ? (lifetime - age) / fade : 1.0f;
        float fadeIn = age / Math.max(1.0f, Math.min(3.0f, fade));
        float t = Math.clamp(Math.min(fadeIn, fadeOut), 0.0f, 1.0f);
        return t * t * (3.0f - 2.0f * t);
    }

    public static final class Entry {
        private final ClientParticle particle;
        private final int color;
        private final float size;
        private final int fadeTicks;
        private final float textureSeed = ThreadLocalRandom.current().nextFloat();
        private boolean visible = true;
        private float prevAlpha;
        private float alpha;

        private Entry(ClientParticle particle, int color, float size, int fadeTicks) {
            this.particle = particle;
            this.color = color;
            this.size = size;
            this.fadeTicks = fadeTicks;
        }

        public ClientParticle getParticle() { return particle; }
        public int getColor() { return color; }
        public float getSize() { return size; }
        public float getTextureSeed() { return textureSeed; }
        public boolean isVisible() { return visible; }

        public float alpha(float partialTicks) {
            return prevAlpha + (alpha - prevAlpha) * partialTicks;
        }
    }
}
