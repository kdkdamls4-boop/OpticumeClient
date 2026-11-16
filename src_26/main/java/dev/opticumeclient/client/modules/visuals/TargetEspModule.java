package dev.opticumeclient.client.modules.visuals;

import dev.opticumeclient.client.friend.FriendManager;
import dev.opticumeclient.client.modules.Category;
import dev.opticumeclient.client.modules.Module;
import dev.opticumeclient.client.modules.ModuleManager;
import dev.opticumeclient.client.modules.combat.KillAuraModule;
import dev.opticumeclient.client.modules.combat.LegitAuraModule;
import dev.opticumeclient.client.modules.combat.OpticumeAuraModule;
import dev.opticumeclient.client.render.ColorUtils;
import dev.opticumeclient.client.render.Render3DHelper;
import dev.opticumeclient.client.render.TextureAssets;
import dev.opticumeclient.client.render.ThemeManager;
import dev.opticumeclient.client.setting.BooleanSetting;
import dev.opticumeclient.client.setting.ModeSetting;
import dev.opticumeclient.client.setting.NumberSetting;
import com.mojang.blaze3d.vertex.PoseStack; 
import net.minecraft.world.entity.Entity; 
import net.minecraft.world.entity.LivingEntity; 
import net.minecraft.world.phys.EntityHitResult; 
import net.minecraft.world.phys.HitResult; 
import net.minecraft.world.phys.Vec3; 

public class TargetEspModule extends Module {
    private final ModeSetting mode = new ModeSetting("Mode", "Стиль 3D маркера цели", "Ghosts", "Ghosts", "Jello Ring", "Nurik Quad", "Spiral");
    private final NumberSetting speed = new NumberSetting("Speed", "Скорость вращения и анимации", 8.0, 2.0, 20.0, 0.5);
    private final NumberSetting radius = new NumberSetting("Radius", "Радиус спирали / кольца", 0.72, 0.35, 1.5, 0.05);
    private final BooleanSetting hitRed = new BooleanSetting("HitRed", "Красная вспышка при ударе по цели", true);

    private LivingEntity cachedTarget = null;
    private long lastTargetTime = 0L;

    // Nurik Quad oscillating rotation state
    private float spinAngle = 0.0f;
    private float espSpeed = 1.0f;
    private boolean flipSpeed = false;
    private long lastFrameMs = System.currentTimeMillis();

    public TargetEspModule() {
        super("TargetESP", "3D призраки Nursultan, Jello-волна и ромб захвата на цели", Category.VISUALS);
        addSetting(mode);
        addSetting(speed);
        addSetting(radius);
        addSetting(hitRed);
        setEnabled(true);
    }

    @Override
    public String getDisplaySuffix() {
        return mode.getMode();
    }

    @Override
    public void onRender3D(PoseStack matrices, float tickDelta) {
        if (mc.level == null || mc.player == null) return;

        LivingEntity target = getActiveTarget();
        if (target == null || !target.isAlive() || target.isRemoved()) return;

        long now = System.currentTimeMillis();
        float dt = Math.min(0.05f, Math.max(0.001f, (now - lastFrameMs) / 1000.0f));
        lastFrameMs = now;

        Vec3 lerpedPos = Render3DHelper.getLerpedPos(target, tickDelta);
        float hurtFactor = (hitRed.isEnabled() && target.hurtTime > 0) ? (target.hurtTime / 10.0f) : 0.0f;

        switch (mode.getMode()) {
            case "Ghosts" -> renderGhosts(target, lerpedPos, tickDelta, hurtFactor);
            case "Jello Ring" -> renderJelloRing(target, lerpedPos, hurtFactor);
            case "Nurik Quad" -> renderNurikQuad(target, lerpedPos, dt, hurtFactor);
            case "Spiral" -> renderSpiral(target, lerpedPos, tickDelta, hurtFactor);
        }
    }

    private void renderGhosts(LivingEntity target, Vec3 lerpedPos, float tickDelta, float hurtFactor) {
        float iAge = (target.tickCount - 1.0f) + tickDelta;
        double factor = speed.getValue();
        double rad = radius.getValue();
        float amplitude = 2.4f;
        float shaking = 2.3f;
        int espLength = 20;
        double baseHeight = target.getBbHeight() * 0.55;

        for (int j = 0; j < 3; j++) {
            for (int i = 0; i <= espLength; i++) {
                double radians = Math.toRadians((((float) i / 1.5f + iAge) * factor + (j * 120)) % (factor * 360));
                double sinQuad = Math.sin(Math.toRadians(iAge * 2.5f + i * (j + 1)) * amplitude) / shaking;

                float offset = (float) i / espLength;
                Vec3 pos = new Vec3(
                        lerpedPos.x + Math.cos(radians) * rad,
                        lerpedPos.y + baseHeight + sinQuad,
                        lerpedPos.z + Math.sin(radians) * rad
                );

                int baseColor = ThemeManager.getInstance().getAccentColor((int) (180 * offset) + j * 90);
                if (hurtFactor > 0.01f) {
                    baseColor = ColorUtils.interpolateColor(baseColor, 0xFFFF2E54, hurtFactor * 0.85f);
                }
                int pointColor = ColorUtils.withAlphaFactor(baseColor, Math.max(0.08f, offset * offset));
                float scale = Math.max(0.26f * offset, 0.14f) * (i == espLength ? 1.25f : 1.0f);

                Render3DHelper.queueBillboardSprite(pos, scale, TextureAssets.FIREFLY, pointColor, 0.0f, i == espLength);
            }
        }
    }

    private void renderJelloRing(LivingEntity target, Vec3 lerpedPos, float hurtFactor) {
        double time = (System.currentTimeMillis() / 1000.0) * (speed.getValue() * 0.35);
        double currentAnim = absSinAnimation(time);
        double rad = radius.getValue();
        double entityH = target.getBbHeight();
        int segments = 36;
        int trailLayers = 5;

        double leadY = lerpedPos.y + currentAnim * entityH;

        for (int i = 0; i < segments; i++) {
            double a1 = (i * Math.PI * 2.0) / segments;
            double a2 = ((i + 1) * Math.PI * 2.0) / segments;

            double x1 = lerpedPos.x + Math.cos(a1) * rad;
            double z1 = lerpedPos.z + Math.sin(a1) * rad;
            double x2 = lerpedPos.x + Math.cos(a2) * rad;
            double z2 = lerpedPos.z + Math.sin(a2) * rad;

            int segColor = ThemeManager.getInstance().getAccentColor(i * 10);
            if (hurtFactor > 0.01f) {
                segColor = ColorUtils.interpolateColor(segColor, 0xFFFF2E54, hurtFactor * 0.85f);
            }

            // Main bright leading ring
            Vec3 p1 = new Vec3(x1, leadY, z1);
            Vec3 p2 = new Vec3(x2, leadY, z2);
            Render3DHelper.queueLine3D(p1, p2, 2.4f, ColorUtils.withAlpha(segColor, 245), true);

            // Trailing rings behind the wave + vertical connecting gradient lines
            double prevLayerY = leadY;
            for (int layer = 1; layer <= trailLayers; layer++) {
                double trailTime = time - layer * 0.085;
                double layerY = lerpedPos.y + absSinAnimation(trailTime) * entityH;
                float layerAlphaFactor = (1.0f - (float) layer / (trailLayers + 1)) * 0.55f;
                int trailColor = ColorUtils.withAlphaFactor(segColor, layerAlphaFactor);

                Vec3 tp1 = new Vec3(x1, layerY, z1);
                Vec3 tp2 = new Vec3(x2, layerY, z2);
                Render3DHelper.queueLine3D(tp1, tp2, 1.5f, trailColor, false);

                if (i % 2 == 0) {
                    Render3DHelper.queueLine3D(new Vec3(x1, prevLayerY, z1), tp1, 1.2f, trailColor, false);
                }
                prevLayerY = layerY;
            }
        }
    }

    private void renderNurikQuad(LivingEntity target, Vec3 lerpedPos, float dt, float hurtFactor) {
        float speedMult = speed.getFloatValue() / 8.0f;
        float step = 38.0f * dt * speedMult;
        if (flipSpeed) {
            espSpeed -= step;
            if (espSpeed <= -22.0f) {
                espSpeed = -22.0f;
                flipSpeed = false;
            }
        } else {
            espSpeed += step;
            if (espSpeed >= 22.0f) {
                espSpeed = 22.0f;
                flipSpeed = true;
            }
        }
        spinAngle = (spinAngle + espSpeed * dt * 28.0f) % 360.0f;

        Vec3 centerPos = lerpedPos.add(0.0, target.getBbHeight() * 0.55, 0.0);
        int baseColor = ThemeManager.getInstance().getAccentColor(0);
        if (hurtFactor > 0.01f) {
            baseColor = ColorUtils.interpolateColor(baseColor, 0xFFFF2E54, hurtFactor * 0.88f);
        }

        float scale = (float) (1.35f * (radius.getValue() / 0.72));
        Render3DHelper.queueBillboardSprite(centerPos, scale, TextureAssets.CAPTURE, ColorUtils.withAlpha(baseColor, 240), spinAngle, true);
    }

    private void renderSpiral(LivingEntity target, Vec3 lerpedPos, float tickDelta, float hurtFactor) {
        double rad = radius.getValue();
        double entityH = target.getBbHeight();
        double time = (System.currentTimeMillis() / 1000.0) * (speed.getValue() * 0.45);
        int points = 42;

        for (int strand = 0; strand < 3; strand++) {
            double phaseOffset = strand * (Math.PI * 2.0 / 3.0);
            Vec3 prev = null;

            for (int i = 0; i <= points; i++) {
                float t = (float) i / points;
                double angle = time * 3.2 + t * Math.PI * 3.0 + phaseOffset;
                double r = rad * (0.75 + 0.25 * Math.sin(t * Math.PI));
                double y = lerpedPos.y + t * entityH;

                Vec3 curr = new Vec3(
                        lerpedPos.x + Math.cos(angle) * r,
                        y,
                        lerpedPos.z + Math.sin(angle) * r
                );

                int c = ThemeManager.getInstance().getAccentColor(i * 8 + strand * 80);
                if (hurtFactor > 0.01f) {
                    c = ColorUtils.interpolateColor(c, 0xFFFF2E54, hurtFactor * 0.85f);
                }
                float alpha = (float) Math.sin(t * Math.PI);
                int lineColor = ColorUtils.withAlphaFactor(c, Math.max(0.15f, alpha));

                if (prev != null) {
                    Render3DHelper.queueLine3D(prev, curr, 2.0f, lineColor, i % 4 == 0);
                }
                if (i == points / 2 || i == points) {
                    Render3DHelper.queueBillboardSprite(curr, 0.22f, TextureAssets.FIREFLY, ColorUtils.withAlpha(c, 230), 0.0f, true);
                }
                prev = curr;
            }
        }
    }

    private static double absSinAnimation(double input) {
        return Math.abs(1.0 + Math.sin(input)) * 0.5;
    }

    private LivingEntity getActiveTarget() {
        long now = System.currentTimeMillis();

        // 1. Check OpticumeAura
        OpticumeAuraModule opticumeAura = ModuleManager.getInstance().getModule(OpticumeAuraModule.class);
        if (opticumeAura != null && opticumeAura.isEnabled() && opticumeAura.getTarget() != null && opticumeAura.getTarget().isAlive()) {
            cachedTarget = opticumeAura.getTarget();
            lastTargetTime = now;
            return cachedTarget;
        }

        // 2. Check KillAura
        KillAuraModule killAura = ModuleManager.getInstance().getModule(KillAuraModule.class);
        if (killAura != null && killAura.isEnabled() && killAura.getCurrentTarget() != null && killAura.getCurrentTarget().isAlive()) {
            cachedTarget = killAura.getCurrentTarget();
            lastTargetTime = now;
            return cachedTarget;
        }

        // 3. Check LegitAura active state + player's last attacked entity
        LegitAuraModule legitAura = ModuleManager.getInstance().getModule(LegitAuraModule.class);
        if (mc.player.getLastHurtMob() != null && mc.player.getLastHurtMob().isAlive() && mc.player.getLastHurtMob() != mc.player) {
            if (mc.player.distanceTo(mc.player.getLastHurtMob()) <= 8.0f) {
                cachedTarget = mc.player.getLastHurtMob();
                lastTargetTime = now;
                return cachedTarget;
            }
        }

        // 4. Check targetedEntity / crosshairTarget
        if (mc.crosshairPickEntity instanceof LivingEntity living && living != mc.player && living.isAlive() && !FriendManager.getInstance().isFriend(living)) {
            cachedTarget = living;
            lastTargetTime = now;
            return cachedTarget;
        }

        HitResult hit = mc.hitResult;
        if (hit instanceof EntityHitResult entityHit && entityHit.getEntity() instanceof LivingEntity living && living != mc.player && living.isAlive()) {
            cachedTarget = living;
            lastTargetTime = now;
            return cachedTarget;
        }

        // 5. Check nearby hurt living entity if LegitAura is enabled or player recently swung
        if (legitAura != null && legitAura.isEnabled()) {
            for (Entity e : mc.level.entitiesForRendering()) {
                if (e instanceof LivingEntity living && e != mc.player && living.isAlive() && !FriendManager.getInstance().isFriend(living)) {
                    if (living.hurtTime > 0 && mc.player.distanceTo(living) <= 4.5f) {
                        cachedTarget = living;
                        lastTargetTime = now;
                        return cachedTarget;
                    }
                }
            }
        }

        // 6. Keep last attacked LivingEntity for 3.5 seconds
        if (cachedTarget != null && cachedTarget.isAlive() && !cachedTarget.isRemoved()
                && (now - lastTargetTime) <= 3500L && mc.player.distanceTo(cachedTarget) <= 12.0f) {
            return cachedTarget;
        }

        cachedTarget = null;
        return null;
    }
}
