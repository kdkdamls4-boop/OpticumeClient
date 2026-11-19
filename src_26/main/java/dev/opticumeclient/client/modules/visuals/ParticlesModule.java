package dev.opticumeclient.client.modules.visuals;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.math.Axis;
import dev.opticumeclient.client.modules.Category;
import dev.opticumeclient.client.modules.Module;
import dev.opticumeclient.client.modules.visuals.particles.ClientParticle;
import dev.opticumeclient.client.modules.visuals.particles.ClientParticleManager;
import dev.opticumeclient.client.modules.visuals.particles.ClientParticleRenderer;
import dev.opticumeclient.client.modules.visuals.particles.ParticleCollisionMode;
import dev.opticumeclient.client.modules.visuals.particles.ParticleInterpolationMode;
import dev.opticumeclient.client.modules.visuals.targetesp.BufferPool;
import dev.opticumeclient.client.modules.visuals.targetesp.CubeParticleRenderer;
import dev.opticumeclient.client.modules.visuals.targetesp.TargetEspFrame;
import dev.opticumeclient.client.render.WorldPrimitiveRenderer;
import dev.opticumeclient.client.render.ColorUtils;
import dev.opticumeclient.client.render.TextureAssets;
import dev.opticumeclient.client.render.ThemeManager;
import dev.opticumeclient.client.setting.BooleanSetting;
import dev.opticumeclient.client.setting.ModeSetting;
import dev.opticumeclient.client.setting.MultiBoxSetting;
import dev.opticumeclient.client.setting.NumberSetting;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Unified ParticlesModule combining Desire 3D physics particles (Hit/Idle/Pearl/Arrow, Bounce & Ricochet, 11 elements, Glow)
 * with Opticume's floating 3D cubes & shooting-star comets.
 */
public class ParticlesModule extends Module {
    private static final String ON_HIT = "Ударе";
    private static final String ON_IDLE = "Бездействие";
    private static final String ON_PEARL = "Эндер жемчуг";
    private static final String ON_ARROW = "Стрела";

    private record ElementEntry(String label, Identifier texture) {}

    private static final ElementEntry[] ELEMENTS = {
            new ElementEntry("Орбиз", TextureAssets.DESIRE_PARTICLE_HEX_ORBIZ),
            new ElementEntry("Звезды", TextureAssets.DESIRE_PARTICLE_HEX_STAR),
            new ElementEntry("Сердечки", TextureAssets.DESIRE_PARTICLE_HEX_HEART),
            new ElementEntry("Доллары", TextureAssets.DESIRE_PARTICLE_HEX_DOLLAR),
            new ElementEntry("Снежинки", TextureAssets.DESIRE_PARTICLE_HEX_SNOWFLAKE),
            new ElementEntry("Короны", TextureAssets.DESIRE_PARTICLE_HEX_CROWN),
            new ElementEntry("Молнии", TextureAssets.DESIRE_PARTICLE_HEX_LIGHTNING),
            new ElementEntry("Линии", TextureAssets.DESIRE_PARTICLE_HEX_LINE),
            new ElementEntry("Кругляши", TextureAssets.DESIRE_PARTICLE_HEX_POINT),
            new ElementEntry("Ромбы", TextureAssets.DESIRE_PARTICLE_HEX_RHOMBUS),
            new ElementEntry("Искры", TextureAssets.DESIRE_PARTICLE_HEX_SPARK)
    };

    private static ParticlesModule instance;

    private final MultiBoxSetting spawnOn = new MultiBoxSetting(
            "Спавнить при",
            "Триггеры генерации частиц",
            new String[]{ON_HIT, ON_IDLE, ON_PEARL, ON_ARROW},
            ON_HIT, ON_IDLE
    );
    private final ModeSetting colorMode = new ModeSetting("Цвет", "Палитра оттенков частиц", "Радужные", "От темы", "Радужные");
    private final ModeSetting hitPhysics = new ModeSetting("Физика удара", "Физика разлёта частиц при атаке", "Рикошет", "Отскок", "Рикошет")
            .visibleWhen(() -> spawnOn.isEnabled(ON_HIT));
    private final NumberSetting hitCount = new NumberSetting("Спавнить при ударе", "Количество частиц при одном ударе", 15.0, 5.0, 50.0, 1.0)
            .visibleWhen(() -> spawnOn.isEnabled(ON_HIT));
    private final NumberSetting worldCount = new NumberSetting("Спавнить в мире", "Плотность атмосферных частиц в мире", 20.0, 3.0, 50.0, 1.0)
            .visibleWhen(() -> spawnOn.isEnabled(ON_IDLE));
    private final MultiBoxSetting elements = new MultiBoxSetting(
            "Элементы",
            "Типы текстур частиц",
            elementNames(),
            "Орбиз"
    );
    private final BooleanSetting glow = new BooleanSetting("Свечение", "Неоновое свечение вокруг частиц", true);
    private final BooleanSetting worldComets = new BooleanSetting("Кометы и кубы", "Летающие 3D-кубы и падающие кометы (Opticume WorldParticles)", false);
    private final NumberSetting scaleSetting = new NumberSetting("Размер", "Масштаб частиц и мировых эффектов", 1.0, 0.5, 2.0, 0.05);

    private final ClientParticleRenderer renderer = new ClientParticleRenderer();
    private final Random random = new Random();
    private final Set<Block> passableBlocks = Collections.newSetFromMap(new IdentityHashMap<>());

    private final List<FloatingCube> cubes = new ArrayList<>();
    private final List<ShootingComet> comets = new ArrayList<>();

    private ClientLevel lastLevel;
    private int idleTicks;

    public ParticlesModule() {
        super("Particles", "3D-частицы при ударах, снарядах и эффекты в мире", Category.VISUALS);
        instance = this;
        addSetting(spawnOn);
        addSetting(colorMode);
        addSetting(hitPhysics);
        addSetting(hitCount);
        addSetting(worldCount);
        addSetting(elements);
        addSetting(glow);
        addSetting(worldComets);
        addSetting(scaleSetting);
        fillPassableBlocks();
    }

    public static ParticlesModule getInstance() {
        return instance;
    }

    private static String[] elementNames() {
        String[] out = new String[ELEMENTS.length];
        for (int i = 0; i < ELEMENTS.length; i++) {
            out[i] = ELEMENTS[i].label;
        }
        return out;
    }

    @Override
    public void onDisable() {
        super.onDisable();
        reset();
    }

    private void reset() {
        idleTicks = 0;
        lastLevel = null;
        cubes.clear();
        comets.clear();
        ClientParticleManager.getInstance().clear();
    }

    public void onAttack(Entity target) {
        if (!isEnabled() || mc.player == null || mc.level == null || !spawnOn.isEnabled(ON_HIT)) return;
        if (!(target instanceof LivingEntity living)) return;

        if (hitPhysics.is("Рикошет")) {
            spawnRicochetHit(living);
        } else {
            spawnBounceHit(living);
        }
    }

    @Override
    public void onTick() {
        if (!isEnabled() || mc.level == null) {
            reset();
            return;
        }
        if (mc.level != lastLevel) {
            ClientParticleManager.getInstance().clear();
            cubes.clear();
            comets.clear();
            lastLevel = mc.level;
        }

        idleTicks++;
        if (spawnOn.isEnabled(ON_IDLE)) spawnAmbient();
        spawnProjectileTrails(spawnOn.isEnabled(ON_PEARL), spawnOn.isEnabled(ON_ARROW));
        ClientParticleManager.getInstance().tick();

        if (worldComets.isEnabled()) {
            tickWorldCometsAndCubes();
        } else {
            cubes.clear();
            comets.clear();
        }
    }

    public void renderWorld(TargetEspFrame frame) {
        if (!isEnabled() || mc.level == null || mc.player == null) return;
        renderer.render(frame, selectedTextures(), glow.isEnabled(), scaleSetting.getFloatValue());

        if (worldComets.isEnabled()) {
            renderWorldCometsAndCubes(frame);
        }
    }

    private void spawnAmbient() {
        if (idleTicks % 15 != 0 || mc.gameRenderer == null || mc.gameRenderer.mainCamera() == null) return;
        idleTicks = 0;

        Vec3 camera = mc.gameRenderer.mainCamera().position();
        float spread = 3.0f;
        int count = worldCount.getIntValue() * 8;

        for (int i = 0; i < count; i++) {
            double distance = random.nextFloat(1.0f, 30.0f);
            double yaw = Math.toRadians(random.nextFloat(0.0f, 360.0f));
            Vec3 pos = new Vec3(
                    camera.x - Math.sin(yaw) * distance,
                    camera.y + random.nextFloat(-15.0f, 15.0f),
                    camera.z + Math.cos(yaw) * distance);
            Vec3 motion = new Vec3(
                    random.nextFloat(-spread, spread),
                    random.nextFloat(-1.0f, 1.0f),
                    random.nextFloat(-spread, spread));
            spawnAmbientParticle(
                    pos, motion, 8.0E-6f, 2500,
                    pickColor(ClientParticleManager.getInstance().size() * 50),
                    random.nextFloat(0.1f, 0.225f)
            );
        }
    }

    private void spawnProjectileTrails(boolean pearls, boolean arrows) {
        if ((!pearls && !arrows) || mc.level == null) return;
        for (Entity entity : mc.level.entitiesForRendering()) {
            if ((pearls && entity instanceof ThrownEnderpearl) || (arrows && entity instanceof Arrow)) {
                spawnTrail(entity);
            }
        }
    }

    private void spawnTrail(Entity entity) {
        double dx = entity.getX() - entity.xo;
        double dy = entity.getY() - entity.yo;
        double dz = entity.getZ() - entity.zo;
        if (Math.sqrt(dx * dx + dy * dy + dz * dz) * 20.0 < 0.35) return;

        Vec3 pos = entity.position();
        Vec3 velocity = entity.getDeltaMovement();
        for (int i = 0; i < 4; i++) {
            float spread = random.nextFloat();
            Vec3 motion = new Vec3(
                    random.nextFloat(-spread, spread) + velocity.x,
                    random.nextFloat(-spread, spread) + velocity.y,
                    random.nextFloat(-spread, spread) + velocity.z);
            spawnAmbientParticle(
                    pos, motion, 8.0E-6f, 1000,
                    pickColor(ClientParticleManager.getInstance().size() * 50),
                    random.nextFloat(0.1f, 0.225f)
            );
        }
    }

    private void spawnRicochetHit(LivingEntity target) {
        int lifetime = msToTicks(3000);
        int count = hitCount.getIntValue();

        for (int i = 0; i < count; i++) {
            ClientParticle.Builder builder = new ClientParticle.Builder(
                    target.getX() + (random.nextDouble() - 0.5) * 0.5,
                    target.getY() + random.nextDouble() * target.getBbHeight(),
                    target.getZ() + (random.nextDouble() - 0.5) * 0.5)
                    .motion(
                            (random.nextDouble() * 0.5 - 0.25) * 0.9,
                            random.nextDouble() * 0.25 * 0.01,
                            (random.nextDouble() * 0.5 - 0.25) * 0.9)
                    .collisionMode(ParticleCollisionMode.RICOCHET)
                    .interpolation(ParticleInterpolationMode.LINEAR)
                    .gravity(1.0f)
                    .collide(true)
                    .size(0.6f, 0.6f)
                    .lifetime(lifetime)
                    .maxDistance(15.0);
            ClientParticleManager.getInstance().spawn(builder, pickColor(i * 30), 0.15f, lifetime);
        }
    }

    private void spawnBounceHit(LivingEntity target) {
        float spread = 0.5f;
        Vec3 velocity = target.getDeltaMovement();
        int count = hitCount.getIntValue();

        for (int i = 0; i < count; i++) {
            float size = random.nextFloat(0.1f, 0.15f);
            int lifetime = random.nextInt(40, 60);
            double motionX = random.nextFloat(-spread, spread) + velocity.x * 0.35;
            double motionY = random.nextFloat(-0.2f, 0.15f) + Math.max(0.0, velocity.y) * 0.25;
            double motionZ = random.nextFloat(-spread, spread) + velocity.z * 0.35;

            ClientParticle.Builder builder = new ClientParticle.Builder(
                    target.getX() + random.nextDouble(-0.25, 0.25),
                    target.getY() + random.nextDouble(0.1, Math.max(0.2, target.getBbHeight())),
                    target.getZ() + random.nextDouble(-0.25, 0.25))
                    .motion(motionX, motionY, motionZ)
                    .gravity(0.2f)
                    .groundBoost(true)
                    .drag(0.9f)
                    .bounce(1.5f)
                    .collide(true)
                    .size(size, size)
                    .lifetime(lifetime)
                    .delay(random.nextInt(1, 3));
            ClientParticleManager.getInstance().spawn(builder, pickColor(random.nextInt()), size, Math.max(12, lifetime / 2));
        }
    }

    private void spawnAmbientParticle(Vec3 pos, Vec3 motion, float gravity, int lifeMs, int color, float size) {
        if (mc.level == null) return;
        Block block = mc.level.getBlockState(BlockPos.containing(pos.x, pos.y, pos.z)).getBlock();
        if (!passableBlocks.contains(block)) return;

        float clampedSize = Math.max(0.02f, size);
        ClientParticle.Builder builder = new ClientParticle.Builder(pos.x, pos.y, pos.z)
                .motion(motion.x * 0.07, motion.y * 0.07, motion.z * 0.07)
                .gravity(Math.max(0.0f, gravity * 175.0f))
                .lifetime(msToTicks(lifeMs))
                .drag(0.98f)
                .collide(false)
                .size(clampedSize, clampedSize);
        ClientParticleManager.getInstance().spawn(builder, color, size, msToTicks(300));
    }

    private void tickWorldCometsAndCubes() {
        if (mc.player == null) return;
        long now = System.currentTimeMillis();
        int targetCount = Math.max(6, worldCount.getIntValue());
        float radius = 16.0f;

        Iterator<FloatingCube> itCubes = cubes.iterator();
        while (itCubes.hasNext()) {
            FloatingCube c = itCubes.next();
            if (now - c.spawnMs > c.lifeMs || mc.player.position().distanceToSqr(c.x, c.y, c.z) > (radius + 8.0) * (radius + 8.0)) {
                itCubes.remove();
                continue;
            }
            c.x += c.vx;
            c.y += c.vy;
            c.z += c.vz;
            c.rotX += c.rotSpeedX;
            c.rotY += c.rotSpeedY;
        }
        if (cubes.size() < targetCount) {
            spawnCube(mc.player.position(), radius, now);
        }

        int maxComets = Math.max(4, targetCount / 3);
        Iterator<ShootingComet> itComets = comets.iterator();
        while (itComets.hasNext()) {
            ShootingComet cm = itComets.next();
            if (now - cm.spawnMs > cm.lifeMs) {
                itComets.remove();
                continue;
            }
            cm.trail.add(0, new Vec3(cm.x, cm.y, cm.z));
            if (cm.trail.size() > 10) cm.trail.remove(cm.trail.size() - 1);
            cm.x += cm.vx;
            cm.y += cm.vy;
            cm.z += cm.vz;
        }
        if (comets.size() < maxComets && ThreadLocalRandom.current().nextFloat() < 0.25f) {
            spawnComet(mc.player.position(), radius, now);
        }
    }

    private void renderWorldCometsAndCubes(TargetEspFrame frame) {
        if (cubes.isEmpty() && comets.isEmpty()) return;
        long now = System.currentTimeMillis();
        Vec3 camPos = frame.cameraPos();
        float scale = scaleSetting.getFloatValue() * 0.13f;

        if (!cubes.isEmpty()) {
            PoseStack ps = new PoseStack();
            ps.mulPose(frame.poseStack().last().pose());
            BufferBuilder solidBuf = BufferPool.getInstance().begin(PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_COLOR);
            boolean any = false;
            for (FloatingCube c : cubes) {
                float lifeProg = (float) (now - c.spawnMs) / (float) c.lifeMs;
                float fade = lifeProg < 0.2f ? lifeProg / 0.2f : (lifeProg > 0.8f ? (1.0f - lifeProg) / 0.2f : 1.0f);
                fade = Mth.clamp(fade, 0.0f, 1.0f);
                if (fade <= 0.02f) continue;

                int baseCol = ThemeManager.getInstance().getAccentColor(c.colorOffset);
                int fillCol = ColorUtils.withAlpha(baseCol, Math.round(145 * fade));

                ps.pushPose();
                ps.translate((float) (c.x - camPos.x), (float) (c.y - camPos.y), (float) (c.z - camPos.z));
                ps.rotateDegrees(Axis.XP, c.rotX);
                ps.rotateDegrees(Axis.YP, c.rotY);
                CubeParticleRenderer.emitSolidCube(solidBuf, ps.last().pose(), scale, fillCol);
                ps.popPose();
                any = true;
            }
            if (any) {
                CubeParticleRenderer.draw(solidBuf, false, null, true);
            } else {
                WorldPrimitiveRenderer.discardQuietly(solidBuf);
            }
        }

        if (!comets.isEmpty() && mc.gameRenderer != null && mc.gameRenderer.mainCamera() != null) {
            Camera cam = mc.gameRenderer.mainCamera();
            Quaternionf orient = new Quaternionf(cam.rotation());
            Matrix4f pose = frame.poseStack().last().pose();
            BufferBuilder cometBuf = WorldPrimitiveRenderer.beginTexturedQuads();
            boolean any = false;
            for (ShootingComet cm : comets) {
                float lifeProg = (float) (now - cm.spawnMs) / (float) cm.lifeMs;
                float fade = lifeProg < 0.15f ? lifeProg / 0.15f : (lifeProg > 0.75f ? (1.0f - lifeProg) / 0.25f : 1.0f);
                fade = Mth.clamp(fade, 0.0f, 1.0f);
                if (fade <= 0.02f) continue;

                int col = pickColor(cm.colorOffset);
                Vec3 head = new Vec3(cm.x - camPos.x, cm.y - camPos.y, cm.z - camPos.z);
                WorldPrimitiveRenderer.glowSprite(cometBuf, pose, head.x, head.y, head.z, orient, 0.42f * scaleSetting.getFloatValue(), ColorUtils.withAlpha(col, Math.round(230 * fade)));
                any = true;
                for (int i = 0; i < cm.trail.size(); i++) {
                    Vec3 tp = cm.trail.get(i).subtract(camPos);
                    float tailScale = Math.max(0.08f, (0.34f - i * 0.028f) * scaleSetting.getFloatValue());
                    int tCol = ColorUtils.withAlpha(col, Math.round(160 * fade * (1.0f - i / 11.0f)));
                    WorldPrimitiveRenderer.glowSprite(cometBuf, pose, tp.x, tp.y, tp.z, orient, tailScale, tCol);
                }
            }
            if (any) {
                WorldPrimitiveRenderer.drawTexturedQuads(cometBuf, TextureAssets.DESIRE_PARTICLE_GLOW, true, true);
            } else {
                WorldPrimitiveRenderer.discardQuietly(cometBuf);
            }
        }
    }

    private void spawnCube(Vec3 center, float radius, long now) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        FloatingCube c = new FloatingCube();
        double angle = rng.nextDouble(Math.PI * 2.0);
        double dist = rng.nextDouble(2.5, radius);
        c.x = center.x + Math.cos(angle) * dist;
        c.y = center.y + rng.nextDouble(-2.0, 6.5);
        c.z = center.z + Math.sin(angle) * dist;
        c.vx = rng.nextDouble(-0.018, 0.018);
        c.vy = rng.nextDouble(0.005, 0.024);
        c.vz = rng.nextDouble(-0.018, 0.018);
        c.rotX = (float) rng.nextDouble(360.0);
        c.rotY = (float) rng.nextDouble(360.0);
        c.rotSpeedX = (float) rng.nextDouble(1.5, 4.5);
        c.rotSpeedY = (float) rng.nextDouble(1.5, 4.5);
        c.spawnMs = now;
        c.lifeMs = rng.nextLong(3500L, 7000L);
        c.colorOffset = rng.nextInt(360);
        cubes.add(c);
    }

    private void spawnComet(Vec3 center, float radius, long now) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        ShootingComet cm = new ShootingComet();
        double angle = rng.nextDouble(Math.PI * 2.0);
        double dist = rng.nextDouble(3.0, radius * 1.2);
        cm.x = center.x + Math.cos(angle) * dist;
        cm.y = center.y + rng.nextDouble(5.0, 12.0);
        cm.z = center.z + Math.sin(angle) * dist;
        double dir = angle + rng.nextDouble(-0.8, 0.8);
        double speed = rng.nextDouble(0.22, 0.42);
        cm.vx = Math.cos(dir) * speed;
        cm.vy = -rng.nextDouble(0.16, 0.32);
        cm.vz = Math.sin(dir) * speed;
        cm.spawnMs = now;
        cm.lifeMs = rng.nextLong(1200L, 2400L);
        cm.colorOffset = rng.nextInt(360);
        comets.add(cm);
    }

    private static int msToTicks(int ms) {
        return Math.max(1, Math.round(ms / 50.0f));
    }

    private int pickColor(int index) {
        if (colorMode.is("Радужные")) {
            return Color.HSBtoRGB(random.nextFloat(), 1.0f, 1.0f);
        }
        return ThemeManager.getInstance().getAccentColor(index);
    }

    private List<Identifier> selectedTextures() {
        List<Identifier> textures = new ArrayList<>();
        for (ElementEntry entry : ELEMENTS) {
            if (elements.isEnabled(entry.label)) {
                textures.add(entry.texture);
            }
        }
        if (textures.isEmpty()) {
            textures.add(ELEMENTS[0].texture);
        }
        return textures;
    }

    private void fillPassableBlocks() {
        Collections.addAll(
                passableBlocks,
                Blocks.AIR, Blocks.CAVE_AIR, Blocks.VOID_AIR, Blocks.WATER, Blocks.LAVA,
                Blocks.GRASS_BLOCK, Blocks.TALL_GRASS, Blocks.FERN, Blocks.LARGE_FERN, Blocks.DEAD_BUSH,
                Blocks.SEAGRASS, Blocks.TALL_SEAGRASS, Blocks.VINE, Blocks.GLOW_LICHEN, Blocks.SUGAR_CANE,
                Blocks.CACTUS, Blocks.SNOW, Blocks.COBWEB, Blocks.ICE, Blocks.PACKED_ICE, Blocks.BLUE_ICE,
                Blocks.FROSTED_ICE, Blocks.SLIME_BLOCK, Blocks.HONEY_BLOCK
        );
    }

    private static final class FloatingCube {
        double x, y, z, vx, vy, vz;
        float rotX, rotY, rotSpeedX, rotSpeedY;
        long spawnMs, lifeMs;
        int colorOffset;
    }

    private static final class ShootingComet {
        double x, y, z, vx, vy, vz;
        long spawnMs, lifeMs;
        int colorOffset;
        final List<Vec3> trail = new ArrayList<>();
    }
}
