package dev.opticumeclient.client.modules.visuals;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.opticumeclient.client.modules.Category;
import dev.opticumeclient.client.modules.Module;
import dev.opticumeclient.client.modules.ModuleManager;
import dev.opticumeclient.client.modules.combat.LegitAuraModule;
import dev.opticumeclient.client.modules.combat.OpticumeAuraModule;
import dev.opticumeclient.client.modules.visuals.killeffect.DeathPiece;
import dev.opticumeclient.client.modules.visuals.killeffect.DeathPoseFactory;
import dev.opticumeclient.client.modules.visuals.targetesp.TargetEspFrame;
import dev.opticumeclient.client.render.ColorUtils;
import dev.opticumeclient.client.render.TextureAssets;
import dev.opticumeclient.client.render.ThemeManager;
import dev.opticumeclient.client.render.WorldPrimitiveRenderer;
import dev.opticumeclient.client.setting.BooleanSetting;
import dev.opticumeclient.client.setting.ColorSetting;
import dev.opticumeclient.client.setting.ModeSetting;
import dev.opticumeclient.client.setting.NumberSetting;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class KillEffectModule extends Module {
    private static KillEffectModule INSTANCE;
    private static volatile boolean spawningClientExplosion = false;

    public static boolean isSpawningClientExplosion() {
        return spawningClientExplosion;
    }

    private final ModeSetting effect = new ModeSetting("Effect", "Тип визуального эффекта", "All", "Lightning", "Explosion", "Blood", "Theme Bloom", "All", "None");
    private final ModeSetting deathAnim = new ModeSetting("Death Anim", "3D-анимация разрушения тела при смерти", "Shatter", "Off", "Shatter", "Dissolve", "Soul", "Ash");
    private final NumberSetting animDuration = new NumberSetting("Anim Duration", "Длительность 3D-анимации смерти (сек)", 1.4, 0.5, 3.0, 0.1)
            .visibleWhen(() -> !deathAnim.is("Off"));
    private final BooleanSetting hideCorpse = new BooleanSetting("Hide Corpse", "Скрывать ванильный падающий труп во время 3D-анимации", true)
            .visibleWhen(() -> !deathAnim.is("Off"));
    private final NumberSetting deathForce = new NumberSetting("Death Force", "Импульс разлёта фрагментов 3D-анимации", 1.0, 0.4, 2.0, 0.1)
            .visibleWhen(() -> !deathAnim.is("Off"));
    private final BooleanSetting sound = new BooleanSetting("Sound", "Звуковой эффект", true);
    private final NumberSetting volume = new NumberSetting("Volume", "Громкость", 1.0, 0.1, 1.0, 0.1);
    private final ModeSetting filter = new ModeSetting("Filter", "Фильтр целей", "Players Only", "Players Only", "All Living");
    private final NumberSetting particles = new NumberSetting("Particles", "Количество частиц", 35.0, 10.0, 80.0, 5.0);
    private final ModeSetting colorMode = new ModeSetting("ColorMode", "Цвет партиклов и 3D-эффекта", "Theme", "Theme", "Red", "Rainbow", "Custom");
    private final ColorSetting customColor = (ColorSetting) new ColorSetting("CustomColor", "Пользовательский цвет", 0xFFFF2244)
            .visibleWhen(() -> colorMode.is("Custom"));

    private Entity lastAttackedEntity = null;
    private long lastAttackTime = 0L;
    private int lastTriggeredEntityId = Integer.MIN_VALUE;
    private long lastTriggeredTimeMs = 0L;
    private int nextClientLightningId = -1_000_000;

    private static class BloomParticle {
        Vec3 pos;
        Vec3 prevPos;
        Vec3 vel;
        float rotation;
        int age = 0;
        final int maxAge;

        BloomParticle(Vec3 pos, Vec3 vel, float rotation, int maxAge) {
            this.pos = pos;
            this.prevPos = pos;
            this.vel = vel;
            this.rotation = rotation;
            this.maxAge = maxAge;
        }
    }

    private final List<BloomParticle> activeBloomParticles = new ArrayList<>();
    private final List<DeathPiece> activeDeathPieces = new ArrayList<>();
    private final Map<Integer, Long> hiddenCorpseUntil = new HashMap<>();

    public KillEffectModule() {
        super("KillEffect", "Эффекты молнии, взрыва, светлячков и 3D-анимации смерти (Shatter / Dissolve / Soul / Ash)", Category.VISUALS);
        addSetting(effect);
        addSetting(deathAnim);
        addSetting(animDuration);
        addSetting(hideCorpse);
        addSetting(deathForce);
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
        LegitAuraModule legitAura = ModuleManager.getInstance().getModule(LegitAuraModule.class);
        if (legitAura != null && legitAura.isEnabled() && legitAura.getTarget() == target) {
            validKill = true;
        }

        if (validKill && target instanceof LivingEntity living) {
            lastTriggeredEntityId = target.getId();
            lastTriggeredTimeMs = now;
            if (lastAttackedEntity == target) {
                lastAttackedEntity = null;
            }
            if (!deathAnim.is("Off")) {
                List<DeathPiece> pieces = DeathPoseFactory.build(
                        living,
                        deathAnim.getMode(),
                        animDuration.getFloatValue(),
                        deathForce.getFloatValue(),
                        resolveEffectColor()
                );
                if (!pieces.isEmpty()) {
                    while (activeDeathPieces.size() > 360) {
                        activeDeathPieces.remove(0);
                    }
                    activeDeathPieces.addAll(pieces);
                }
                if (hideCorpse.isEnabled()) {
                    hiddenCorpseUntil.put(target.getId(), now + Math.round(animDuration.getFloatValue() * 1000.0) + 1500L);
                }
            }
            triggerKillEffect(target.position());
        }
    }

    public boolean shouldHideCorpse(Entity entity) {
        if (!isEnabled() || entity == null || deathAnim.is("Off") || !hideCorpse.isEnabled()) {
            return false;
        }
        Long until = hiddenCorpseUntil.get(entity.getId());
        if (until == null) return false;
        if (System.currentTimeMillis() > until) {
            hiddenCorpseUntil.remove(entity.getId());
            return false;
        }
        return true;
    }

    @Override
    public void onDisable() {
        super.onDisable();
        activeBloomParticles.clear();
        activeDeathPieces.clear();
        hiddenCorpseUntil.clear();
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
            spawningClientExplosion = true;
            try {
                level.addParticle(ParticleTypes.EXPLOSION_EMITTER, pos.x, pos.y + 1.0, pos.z, 0, 0, 0);
                level.addParticle(ParticleTypes.EXPLOSION, pos.x, pos.y + 1.0, pos.z, 0, 0, 0);
            } finally {
                spawningClientExplosion = false;
            }

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
        long now = System.currentTimeMillis();
        hiddenCorpseUntil.entrySet().removeIf(e -> now > e.getValue());

        if (!activeBloomParticles.isEmpty()) {
            Iterator<BloomParticle> it = activeBloomParticles.iterator();
            while (it.hasNext()) {
                BloomParticle p = it.next();
                p.age++;
                p.prevPos = p.pos;
                p.pos = p.pos.add(p.vel);
                p.vel = new Vec3(p.vel.x * 0.95, p.vel.y * 0.95, p.vel.z * 0.95);
                p.rotation = (p.rotation + 3.0f) % 360.0f;
                if (p.age >= p.maxAge) {
                    it.remove();
                }
            }
        }

        if (!activeDeathPieces.isEmpty()) {
            Iterator<DeathPiece> pieceIt = activeDeathPieces.iterator();
            while (pieceIt.hasNext()) {
                DeathPiece piece = pieceIt.next();
                boolean expired = piece.tick();
                if ((piece.mode == DeathPiece.MODE_SOUL || piece.mode == DeathPiece.MODE_DISSOLVE)
                        && piece.age % 4 == 0 && activeBloomParticles.size() < 240 && Math.random() < 0.35) {
                    activeBloomParticles.add(new BloomParticle(
                            piece.pos,
                            new Vec3((Math.random() - 0.5) * 0.02, 0.025 + Math.random() * 0.03, (Math.random() - 0.5) * 0.02),
                            (float) (Math.random() * 360.0),
                            18
                    ));
                }
                if (expired) {
                    pieceIt.remove();
                }
            }
        }
    }

    @Override
    public void onRender3D(PoseStack matrices, float tickDelta) {
        // Rendered in native Vulkan 3D pass via renderWorld(TargetEspFrame)
    }

    public void renderWorld(TargetEspFrame frame) {
        if (!isEnabled() || frame == null || mc.level == null) return;

        Vec3 cam = frame.cameraPos();
        Matrix4f pose = frame.poseStack().last().pose();
        float tickDelta = frame.partialTicks();

        if (!activeDeathPieces.isEmpty()) {
            BufferBuilder quadBuf = WorldPrimitiveRenderer.beginQuads();
            boolean anyQuads = false;
            for (DeathPiece piece : activeDeathPieces) {
                anyQuads |= piece.emitQuads(quadBuf, pose, cam, tickDelta);
            }
            if (anyQuads) {
                WorldPrimitiveRenderer.drawQuads(quadBuf, true, true);
            } else {
                WorldPrimitiveRenderer.discardQuietly(quadBuf);
            }

            BufferBuilder lineBuf = WorldPrimitiveRenderer.beginLines();
            boolean anyLines = false;
            for (DeathPiece piece : activeDeathPieces) {
                anyLines |= piece.emitLines(lineBuf, pose, cam, tickDelta);
            }
            if (anyLines) {
                WorldPrimitiveRenderer.drawLines(lineBuf, true);
            } else {
                WorldPrimitiveRenderer.discardQuietly(lineBuf);
            }
        }

        if (!activeBloomParticles.isEmpty() && mc.gameRenderer != null && mc.gameRenderer.mainCamera() != null) {
            int baseColor = resolveEffectColor();
            Quaternionf orient = new Quaternionf(mc.gameRenderer.mainCamera().rotation());
            BufferBuilder glowBuf = WorldPrimitiveRenderer.beginTexturedQuads();
            boolean any = false;
            for (BloomParticle p : activeBloomParticles) {
                float fade = 1.0f - ((float) p.age / p.maxAge);
                if (fade <= 0.02f) continue;
                double x = Mth.lerp(tickDelta, p.prevPos.x, p.pos.x) - cam.x;
                double y = Mth.lerp(tickDelta, p.prevPos.y, p.pos.y) - cam.y;
                double z = Mth.lerp(tickDelta, p.prevPos.z, p.pos.z) - cam.z;
                float sz = 0.30f * fade;
                int partCol = ColorUtils.withAlphaFactor(baseColor, Math.max(0.05f, fade));
                WorldPrimitiveRenderer.glowSprite(glowBuf, pose, x, y, z, orient, sz, partCol);
                any = true;
            }
            if (any) {
                WorldPrimitiveRenderer.drawTexturedQuads(glowBuf, TextureAssets.GLOW_RADIAL, true, true);
            } else {
                WorldPrimitiveRenderer.discardQuietly(glowBuf);
            }
        }
    }

    private int resolveEffectColor() {
        if (colorMode.is("Theme")) {
            return ThemeManager.getInstance().getAccentColor(0);
        } else if (colorMode.is("Red")) {
            return 0xFFFF2E54;
        } else if (colorMode.is("Rainbow")) {
            return ColorUtils.getRainbow(4.0f, 0.85f, 1.0f, 0L);
        }
        return customColor.getColor();
    }
}
