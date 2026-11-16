package dev.opticumeclient.client.modules.visuals;

import dev.opticumeclient.client.modules.Category;
import dev.opticumeclient.client.modules.Module;
import dev.opticumeclient.client.render.ColorUtils;
import dev.opticumeclient.client.render.Render3DHelper;
import dev.opticumeclient.client.render.TextureAssets;
import dev.opticumeclient.client.render.ThemeManager;
import dev.opticumeclient.client.setting.BooleanSetting;
import dev.opticumeclient.client.setting.ColorSetting;
import dev.opticumeclient.client.setting.ModeSetting;
import dev.opticumeclient.client.setting.NumberSetting;
import com.mojang.blaze3d.vertex.PoseStack; 
import net.minecraft.world.entity.Entity; 
import net.minecraft.world.entity.LivingEntity; 
import net.minecraft.resources.Identifier; 
import net.minecraft.core.BlockPos; 
import net.minecraft.util.Mth; 
import net.minecraft.world.phys.Vec3; 

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public class ParticlesModule extends Module {
    private static final int MAX_PARTICLES = 450;

    private static final Identifier[] MIXED_POOL = new Identifier[]{
            TextureAssets.FIREFLY,
            TextureAssets.STAR,
            TextureAssets.HEART,
            TextureAssets.SNOWFLAKE,
            TextureAssets.DOLLAR
    };

    private final ModeSetting texture = new ModeSetting("Texture", "Текстура 3D частиц", "Mixed", "Mixed", "Firefly", "Stars", "Hearts", "Snowflakes", "Dollars");
    private final ModeSetting colorMode = new ModeSetting("Color Mode", "Цветовая схема частиц", "Theme", "Theme", "Rainbow", "Sync", "Custom");
    private final ColorSetting customColor = new ColorSetting("Custom Color", "Пользовательский цвет частиц", 0xFF60A5FA);
    private final BooleanSetting hitParticles = new BooleanSetting("HitParticles", "Разлет частиц при ударе по цели", true);
    private final BooleanSetting worldFireflies = new BooleanSetting("WorldFireflies", "Атмосферные светлячки вокруг игрока", true);
    private final BooleanSetting glowHalo = new BooleanSetting("Glow Halo", "Мягкое неоновое свечение и яркое ядро", true);
    private final NumberSetting count = new NumberSetting("Hit Count", "Количество частиц при ударе", 30.0, 5.0, 150.0, 1.0);
    private final NumberSetting ambientCount = new NumberSetting("Ambient Count", "Количество атмосферных светлячков", 45.0, 10.0, 200.0, 1.0);
    private final NumberSetting size = new NumberSetting("Size", "Размер 3D частиц", 0.45, 0.15, 2.0, 0.05);
    private final NumberSetting speed = new NumberSetting("Speed", "Скорость разлета частиц", 1.0, 0.2, 3.0, 0.1);
    private final NumberSetting lifetime = new NumberSetting("LifeTime", "Время жизни частиц (сек)", 2.5, 1.0, 10.0, 0.5);
    private final BooleanSetting physicsBounce = new BooleanSetting("PhysicsBounce", "Отскок частиц от блоков с упругостью", true);

    private final List<Particle> particles = new ArrayList<>();
    private final Map<Integer, Integer> lastHurtTimes = new HashMap<>();

    public ParticlesModule() {
        super("Particles", "Физические 3D частицы при ударах и атмосферные светлячки в мире", Category.VISUALS);
        addSetting(texture);
        addSetting(colorMode);
        addSetting(customColor);
        addSetting(hitParticles);
        addSetting(worldFireflies);
        addSetting(glowHalo);
        addSetting(count);
        addSetting(ambientCount);
        addSetting(size);
        addSetting(speed);
        addSetting(lifetime);
        addSetting(physicsBounce);
        setEnabled(false);
    }

    @Override
    public String getDisplaySuffix() {
        return texture.getMode();
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.level == null) {
            particles.clear();
            lastHurtTimes.clear();
            return;
        }

        long now = System.currentTimeMillis();
        ThreadLocalRandom rng = ThreadLocalRandom.current();

        // 1. Spawn HitParticles when a nearby entity receives a hit (hurtTime == 9)
        if (hitParticles.isEnabled()) {
            for (Entity entity : mc.level.entitiesForRendering()) {
                if (!(entity instanceof LivingEntity living) || entity == mc.player || !living.isAlive()) continue;
                if (mc.player.distanceTo(living) > 12.0f) continue;

                int prevHurt = lastHurtTimes.getOrDefault(living.getId(), 0);
                int currHurt = living.hurtTime;
                lastHurtTimes.put(living.getId(), currHurt);

                if (currHurt == 9 && prevHurt < 9) {
                    spawnHitBurst(living, now, rng);
                }
            }
        }

        // 2. Spawn ambient WorldFireflies within 14 blocks around the player
        if (worldFireflies.isEnabled()) {
            int currentAmbient = 0;
            for (Particle p : particles) {
                if (p.ambient) currentAmbient++;
            }
            int targetAmbient = ambientCount.getIntValue();
            float spd = speed.getFloatValue();
            long baseLifeMs = (long) (lifetime.getValue() * 1500.0);
            long minLife = Math.max(800L, (long) (baseLifeMs * 0.8));
            long maxLife = Math.max(minLife + 200L, (long) (baseLifeMs * 1.6));

            for (int i = currentAmbient; i < targetAmbient && particles.size() < MAX_PARTICLES; i++) {
                double ox = rng.nextDouble(-14.0, 14.0);
                double oy = rng.nextDouble(-2.5, 6.5);
                double oz = rng.nextDouble(-14.0, 14.0);
                Vec3 pos = mc.player.position().add(ox, oy, oz);
                if (!mc.level.getBlockState(BlockPos.containing(pos.x, pos.y, pos.z)).isAir()) continue;

                Vec3 vel = new Vec3(
                        rng.nextDouble(-0.025, 0.025) * spd,
                        rng.nextDouble(-0.015, 0.022) * spd,
                        rng.nextDouble(-0.025, 0.025) * spd
                );
                particles.add(new Particle(
                        pos,
                        vel,
                        now,
                        rng.nextLong(minLife, maxLife),
                        pickTexture(rng),
                        rng.nextFloat(0.0f, 360.0f),
                        rng.nextFloat(-4.5f, 4.5f),
                        rng.nextInt(0, 360),
                        true
                ));
            }
        }

        // 3. Update physics and bounce off blocks
        boolean bounce = physicsBounce.isEnabled();
        Iterator<Particle> it = particles.iterator();
        while (it.hasNext()) {
            Particle p = it.next();
            if (now - p.spawnTime > p.maxLife) {
                it.remove();
                continue;
            }

            p.prevPos = p.pos;
            p.rotation = (p.rotation + p.rotSpeed) % 360.0f;

            if (p.ambient) {
                double wave = (now - p.spawnTime) * 0.003 + p.colorOffset;
                float spd = speed.getFloatValue();
                p.velocity = p.velocity.add(
                        Math.sin(wave) * 0.0018 * spd,
                        Math.cos(wave * 0.8) * 0.0012 * spd,
                        Math.cos(wave) * 0.0018 * spd
                ).scale(0.96);
            } else {
                // Gravity + air drag for hit particles
                p.velocity = p.velocity.subtract(0.0, 0.016, 0.0).scale(0.96);
            }

            double nextX = p.pos.x + p.velocity.x;
            double nextY = p.pos.y + p.velocity.y;
            double nextZ = p.pos.z + p.velocity.z;

            if (bounce) {
                if (!mc.level.getBlockState(BlockPos.containing(nextX, p.pos.y, p.pos.z)).isAir()) {
                    p.velocity = new Vec3(p.velocity.x * -0.65, p.velocity.y, p.velocity.z);
                    nextX = p.pos.x + p.velocity.x;
                }
                if (!mc.level.getBlockState(BlockPos.containing(p.pos.x, nextY, p.pos.z)).isAir()) {
                    p.velocity = new Vec3(p.velocity.x * 0.88, p.velocity.y * -0.65, p.velocity.z * 0.88);
                    nextY = p.pos.y + p.velocity.y;
                }
                if (!mc.level.getBlockState(BlockPos.containing(p.pos.x, p.pos.y, nextZ)).isAir()) {
                    p.velocity = new Vec3(p.velocity.x, p.velocity.y, p.velocity.z * -0.65);
                    nextZ = p.pos.z + p.velocity.z;
                }
            }

            p.pos = new Vec3(nextX, nextY, nextZ);
        }
    }

    private void spawnHitBurst(LivingEntity target, long now, ThreadLocalRandom rng) {
        int burstCount = count.getIntValue();
        float spd = speed.getFloatValue();
        long lifeBase = (long) (lifetime.getValue() * 1000.0);
        long minLife = Math.max(500L, (long) (lifeBase * 0.7));
        long maxLife = Math.max(minLife + 100L, (long) (lifeBase * 1.3));
        Vec3 center = target.position().add(0.0, target.getBbHeight() * 0.6, 0.0);

        for (int i = 0; i < burstCount && particles.size() < MAX_PARTICLES; i++) {
            double angle = rng.nextDouble(0.0, Math.PI * 2.0);
            double speedXZ = rng.nextDouble(0.07, 0.24) * spd;
            double speedY = rng.nextDouble(0.05, 0.22) * spd;
            Vec3 vel = new Vec3(Math.cos(angle) * speedXZ, speedY, Math.sin(angle) * speedXZ);

            particles.add(new Particle(
                    center.add(rng.nextDouble(-0.2, 0.2), rng.nextDouble(-0.2, 0.2), rng.nextDouble(-0.2, 0.2)),
                    vel,
                    now,
                    rng.nextLong(minLife, maxLife),
                    pickTexture(rng),
                    rng.nextFloat(0.0f, 360.0f),
                    rng.nextFloat(-9.0f, 9.0f),
                    i * 22,
                    false
            ));
        }
    }

    private Identifier pickTexture(ThreadLocalRandom rng) {
        return switch (texture.getMode()) {
            case "Firefly" -> TextureAssets.FIREFLY;
            case "Stars" -> TextureAssets.STAR;
            case "Hearts" -> TextureAssets.HEART;
            case "Snowflakes" -> TextureAssets.SNOWFLAKE;
            case "Dollars" -> TextureAssets.DOLLAR;
            default -> MIXED_POOL[rng.nextInt(MIXED_POOL.length)];
        };
    }

    @Override
    public void onRender3D(PoseStack matrices, float tickDelta) {
        if (mc.player == null || mc.level == null || particles.isEmpty()) return;

        long now = System.currentTimeMillis();
        float baseScale = size.getFloatValue();

        for (Particle p : particles) {
            float lifeProgress = (float) (now - p.spawnTime) / (float) p.maxLife;
            if (lifeProgress < 0.0f || lifeProgress >= 1.0f) continue;

            float alphaFactor;
            if (lifeProgress < 0.15f) {
                alphaFactor = lifeProgress / 0.15f;
            } else {
                alphaFactor = 1.0f - ((lifeProgress - 0.15f) / 0.85f);
            }
            if (alphaFactor <= 0.02f) continue;

            Vec3 lerpedParticlePos = new Vec3(
                    Mth.lerp(tickDelta, p.prevPos.x, p.pos.x),
                    Mth.lerp(tickDelta, p.prevPos.y, p.pos.y),
                    Mth.lerp(tickDelta, p.prevPos.z, p.pos.z)
            );

            int baseColor = switch (colorMode.getMode()) {
                case "Rainbow" -> ColorUtils.rainbow(p.colorOffset * 10, 0.85f, 1.0f);
                case "Sync" -> ThemeManager.getInstance().getCurrentTheme().getPrimaryColor();
                case "Custom" -> customColor.getColor();
                default -> ThemeManager.getInstance().getAccentColor(p.colorOffset); // "Theme"
            };
            int color = ColorUtils.withAlphaFactor(baseColor, alphaFactor);
            float scale = baseScale * (p.ambient ? 0.85f : 1.0f) * (0.5f + 0.5f * alphaFactor);
            float rot = p.rotation + p.rotSpeed * tickDelta;

            boolean halo = glowHalo.isEnabled();
            Render3DHelper.queueBillboardSprite(lerpedParticlePos, scale, p.tex, color, rot, halo);
            if (halo) {
                int coreColor = ColorUtils.withAlphaFactor(0xFFFFFFFF, alphaFactor * 0.85f);
                Render3DHelper.queueBillboardSprite(lerpedParticlePos, scale * 0.45f, p.tex, coreColor, rot, false);
            }
        }
    }

    private static class Particle {
        Vec3 pos;
        Vec3 prevPos;
        Vec3 velocity;
        final long spawnTime;
        final long maxLife;
        final Identifier tex;
        float rotation;
        final float rotSpeed;
        final int colorOffset;
        final boolean ambient;

        Particle(Vec3 pos, Vec3 velocity, long spawnTime, long maxLife, Identifier tex,
                 float rotation, float rotSpeed, int colorOffset, boolean ambient) {
            this.pos = pos;
            this.prevPos = pos;
            this.velocity = velocity;
            this.spawnTime = spawnTime;
            this.maxLife = maxLife;
            this.tex = tex;
            this.rotation = rotation;
            this.rotSpeed = rotSpeed;
            this.colorOffset = colorOffset;
            this.ambient = ambient;
        }
    }
}
