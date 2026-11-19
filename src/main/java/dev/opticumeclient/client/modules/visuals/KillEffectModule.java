package dev.opticumeclient.client.modules.visuals;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.opticumeclient.client.modules.Category;
import dev.opticumeclient.client.modules.Module;
import dev.opticumeclient.client.modules.ModuleManager;
import dev.opticumeclient.client.modules.combat.KillAuraModule;
import dev.opticumeclient.client.modules.combat.OpticumeAuraModule;
import dev.opticumeclient.client.render.ColorUtils;
import dev.opticumeclient.client.render.Render3DHelper;
import dev.opticumeclient.client.render.TextureAssets;
import dev.opticumeclient.client.render.ThemeManager;
import dev.opticumeclient.client.setting.BooleanSetting;
import dev.opticumeclient.client.setting.ColorSetting;
import dev.opticumeclient.client.setting.ModeSetting;
import dev.opticumeclient.client.setting.NumberSetting;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class KillEffectModule extends Module {
    private static KillEffectModule INSTANCE;

    private final ModeSetting effect = new ModeSetting("Effect", "Тип визуального эффекта", "All", "Lightning", "Explosion", "Blood", "Theme Bloom", "All");
    private final BooleanSetting sound = new BooleanSetting("Sound", "Звуковой эффект", true);
    private final NumberSetting volume = new NumberSetting("Volume", "Громкость", 1.0, 0.1, 1.0, 0.1);
    private final ModeSetting filter = new ModeSetting("Filter", "Фильтр целей", "Players Only", "Players Only", "All Living");
    private final NumberSetting particles = new NumberSetting("Particles", "Количество частиц", 35.0, 10.0, 80.0, 5.0);
    private final ModeSetting colorMode = new ModeSetting("ColorMode", "Цвет партиклов Theme Bloom", "Theme", "Theme", "Red", "Rainbow", "Custom");
    private final ColorSetting customColor = (ColorSetting) new ColorSetting("CustomColor", "Пользовательский цвет", 0xFFFF2244)
            .visibleWhen(() -> colorMode.is("Custom"));

    private Entity lastAttackedEntity = null;
    private long lastAttackTime = 0L;
    private int lastTriggeredEntityId = Integer.MIN_VALUE;
    private long lastTriggeredTimeMs = 0L;
    private int nextClientLightningId = -1_000_000;

    private static class BloomParticle {
        Vec3 pos;
        Vec3 vel;
        float rotation;
        int age = 0;
        final int maxAge;

        BloomParticle(Vec3 pos, Vec3 vel, float rotation, int maxAge) {
            this.pos = pos;
            this.vel = vel;
            this.rotation = rotation;
            this.maxAge = maxAge;
        }
    }

    private final List<BloomParticle> activeBloomParticles = new ArrayList<>();

    public KillEffectModule() {
        super("KillEffect", "Эффектная анимация молнии, взрыва, крови и светлячков при устранении цели", Category.VISUALS);
        addSetting(effect);
        addSetting(sound);
        addSetting(volume);
        addSetting(filter);
        addSetting(particles);
        addSetting(colorMode);
        addSetting(customColor);
        INSTANCE = this;
        setEnabled(false);
    }

    public static KillEffectModule getInstance() {
        return INSTANCE;
    }

    @Override
    public String getDisplaySuffix() {
        return effect.getMode();
    }

    public void recordAttack(Entity target) {
        this.lastAttackedEntity = target;
        this.lastAttackTime = System.currentTimeMillis();
    }

    public void onEntityDeath(Entity target) {
        if (!isEnabled() || target == null || mc.level == null || !mc.isSameThread()) return;
        if (target == mc.player) return;

        // Check target filter
        if (filter.is("Players Only") && !(target instanceof Player)) {
            return;
        }
        if (!(target instanceof LivingEntity)) {
            return;
        }

        long now = System.currentTimeMillis();
        if (target.getId() == lastTriggeredEntityId && (now - lastTriggeredTimeMs) < 500L) {
            return;
        }

        boolean validKill = (lastAttackedEntity == target && (now - lastAttackTime) <= 4000L);

        // Also check if Aura target matches
        OpticumeAuraModule aura = ModuleManager.getInstance().getModule(OpticumeAuraModule.class);
        if (aura != null && aura.isEnabled() && aura.getTarget() == target) {
            validKill = true;
        }
        KillAuraModule killAura = ModuleManager.getInstance().getModule(KillAuraModule.class);
        if (killAura != null && killAura.isEnabled() && killAura.getCurrentTarget() == target) {
            validKill = true;
        }

        if (validKill) {
            lastTriggeredEntityId = target.getId();
            lastTriggeredTimeMs = now;
            if (lastAttackedEntity == target) {
                lastAttackedEntity = null;
            }
            triggerKillEffect(target.position());
        }
    }

    public void triggerKillEffect(Vec3 pos) {
        ClientLevel level = mc.level;
        if (level == null || pos == null || !mc.isSameThread()) return;

        String mode = effect.getMode();
        float vol = volume.getFloatValue();
        boolean playSound = sound.isEnabled();
        int partCount = particles.getIntValue();

        // 1. Lightning Bolt (Visual Only, isolated negative client ID so it NEVER deletes server entities/player!)
        if (mode.equals("Lightning") || mode.equals("All")) {
            try {
                LightningBolt bolt = new LightningBolt(EntityTypes.LIGHTNING_BOLT, level);
                bolt.setId(nextClientLightningId--);
                if (nextClientLightningId < -1_900_000) nextClientLightningId = -1_000_000;
                bolt.setVisualOnly(true);
                bolt.setPos(pos.x, pos.y, pos.z);
                level.addEntity(bolt);
            } catch (Throwable ignored) {
            }

            if (playSound) {
                level.playLocalSound(pos.x, pos.y, pos.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, vol, 1.0f, false);
            }
        }

        // 2. Explosion
        if (mode.equals("Explosion") || mode.equals("All")) {
            level.addParticle(ParticleTypes.EXPLOSION_EMITTER, pos.x, pos.y + 1.0, pos.z, 0, 0, 0);
            level.addParticle(ParticleTypes.EXPLOSION, pos.x, pos.y + 1.0, pos.z, 0, 0, 0);

            if (playSound && !mode.equals("All")) {
                level.playLocalSound(pos.x, pos.y, pos.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, vol, 1.0f, false);
            }
        }

        // 3. Blood (Damage Indicator & Redstone Shards)
        if (mode.equals("Blood") || mode.equals("All")) {
            int bloodCount = Math.min(28, partCount);
            for (int i = 0; i < bloodCount; i++) {
                double vx = (Math.random() - 0.5) * 0.45;
                double vy = Math.random() * 0.35 + 0.12;
                double vz = (Math.random() - 0.5) * 0.45;
                level.addParticle(ParticleTypes.DAMAGE_INDICATOR, pos.x, pos.y + 1.0, pos.z, vx, vy, vz);
                level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.REDSTONE_BLOCK.defaultBlockState()), pos.x, pos.y + 1.0, pos.z, vx, vy, vz);
            }
        }

        // 4. Theme Bloom (Ascending Glowing Particles)
        if (mode.equals("Theme Bloom") || mode.equals("All")) {
            for (int i = 0; i < partCount && activeBloomParticles.size() < 200; i++) {
                double vx = (Math.random() - 0.5) * 0.22;
                double vy = Math.random() * 0.20 + 0.05;
                double vz = (Math.random() - 0.5) * 0.22;
                float rot = (float) (Math.random() * 360.0);
                activeBloomParticles.add(new BloomParticle(pos.add(0.0, 1.0, 0.0), new Vec3(vx, vy, vz), rot, 30));
            }

            if (playSound) {
                level.playLocalSound(pos.x, pos.y, pos.z, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, vol * 0.85f, 1.45f, false);
            }
        }
    }

    @Override
    public void onTick() {
        if (activeBloomParticles.isEmpty()) return;

        Iterator<BloomParticle> it = activeBloomParticles.iterator();
        while (it.hasNext()) {
            BloomParticle p = it.next();
            p.age++;
            p.pos = p.pos.add(p.vel);
            p.vel = new Vec3(p.vel.x * 0.95, p.vel.y * 0.95, p.vel.z * 0.95);
            p.rotation = (p.rotation + 3.0f) % 360.0f;
            if (p.age >= p.maxAge) {
                it.remove();
            }
        }
    }

    @Override
    public void onRender3D(PoseStack matrices, float tickDelta) {
        if (!isEnabled() || activeBloomParticles.isEmpty()) return;

        int baseColor;
        if (colorMode.is("Theme")) {
            baseColor = ThemeManager.getInstance().getAccentColor(0);
        } else if (colorMode.is("Red")) {
            baseColor = 0xFFFF2E54;
        } else if (colorMode.is("Rainbow")) {
            baseColor = ColorUtils.getRainbow(4.0f, 0.85f, 1.0f, 0L);
        } else {
            baseColor = customColor.getColor();
        }

        for (BloomParticle p : activeBloomParticles) {
            float fade = 1.0f - ((float) p.age / p.maxAge);
            int partCol = ColorUtils.withAlphaFactor(baseColor, Math.max(0.05f, fade));
            Render3DHelper.queueBillboardSprite(p.pos, 0.28f * fade, TextureAssets.GLOW_RADIAL, partCol, p.rotation, true);
        }
    }
}
