package dev.optimusprime.client.modules.visuals;

import dev.optimusprime.client.modules.Category;
import dev.optimusprime.client.modules.Module;
import dev.optimusprime.client.render.ColorUtils;
import dev.optimusprime.client.render.Render2DHelper;
import dev.optimusprime.client.render.Render3DHelper;
import dev.optimusprime.client.render.TextureAssets;
import dev.optimusprime.client.render.ThemeManager;
import dev.optimusprime.client.setting.BooleanSetting;
import dev.optimusprime.client.setting.ModeSetting;
import dev.optimusprime.client.setting.NumberSetting;
import net.minecraft.client.gui.GuiGraphicsExtractor; 
import net.minecraft.world.entity.LivingEntity; 
import net.minecraft.world.phys.EntityHitResult; 

public class CrosshairModule extends Module {
    private static CrosshairModule INSTANCE;

    private final ModeSetting mode = new ModeSetting(
            "Style",
            "Стиль кастомного прицела",
            "Nursultan Circle",
            "Nursultan Circle",
            "Catlavan Cross",
            "Expensive Modern",
            "Dot"
    );
    private final NumberSetting size = new NumberSetting("Size", "Радиус кольца / длина лучей", 6.5, 3.0, 16.0, 0.5);
    private final NumberSetting gap = new NumberSetting("Gap", "Базовый зазор от центра", 3.0, 0.5, 10.0, 0.5);
    private final BooleanSetting dynamicGap = new BooleanSetting("DynamicGap", "Динамическое расширение при ударе и движении", true);
    private final BooleanSetting targetHighlight = new BooleanSetting("TargetRed", "Покраснение при наведении на сущность", true);

    private float animatedSpread = 0.0f;

    public CrosshairModule() {
        super("Crosshair", "Неоновый прицел Nursultan Circle с кулдауном атаки и Catlavan Cross", Category.VISUALS);
        addSetting(mode);
        addSetting(size);
        addSetting(gap);
        addSetting(dynamicGap);
        addSetting(targetHighlight);
        INSTANCE = this;
        setEnabled(true);
    }

    public static CrosshairModule getInstance() {
        return INSTANCE;
    }

    @Override
    public String getDisplaySuffix() {
        return mode.getMode();
    }

    @Override
    public void onRender2D(GuiGraphicsExtractor context, float tickDelta) {
        if (!isEnabled() || mc.player == null || mc.level == null) return;
        if (mc.options.getCameraType() != null && !mc.options.getCameraType().isFirstPerson()) {
            return;
        }

        float cx = mc.getWindow().getGuiScaledWidth() * 0.5f;
        float cy = mc.getWindow().getGuiScaledHeight() * 0.5f;

        float cooldown = mc.player.getAttackStrengthScale(tickDelta);
        boolean aimingAtEntity = mc.hitResult instanceof EntityHitResult ehr
                && ehr.getEntity() instanceof LivingEntity living && living.isAlive();

        float targetSpread = 0.0f;
        if (dynamicGap.isEnabled()) {
            if (mc.player.getDeltaMovement().horizontalDistanceSqr() > 0.0015) {
                targetSpread += 2.2f;
            }
            if (cooldown < 0.99f) {
                targetSpread += (1.0f - cooldown) * 4.5f;
            }
            if (mc.player.hurtTime > 0) {
                targetSpread += 1.8f;
            }
        }
        animatedSpread += (targetSpread - animatedSpread) * 0.25f;

        int accent1 = (targetHighlight.isEnabled() && aimingAtEntity)
                ? 0xFFFF2E54
                : ThemeManager.getInstance().getAccentColor(0);
        int accent2 = (targetHighlight.isEnabled() && aimingAtEntity)
                ? 0xFFFF6B81
                : ThemeManager.getInstance().getAccentColor(120);

        switch (mode.getMode()) {
            case "Nursultan Circle" -> renderNursultanCircle(context, cx, cy, cooldown, accent1, accent2);
            case "Catlavan Cross" -> renderCatlavanCross(context, cx, cy, accent1, accent2);
            case "Expensive Modern" -> renderExpensiveModern(context, cx, cy, cooldown, accent1, accent2);
            case "Dot" -> renderDot(context, cx, cy, accent1);
        }
    }

    private void renderNursultanCircle(GuiGraphicsExtractor context, float cx, float cy, float cooldown, int accent1, int accent2) {
        TextureAssets.ensureInitialized();
        float rad = size.getFloatValue() + animatedSpread * 0.45f;
        float diameter = rad * 2.0f;

        // Bilinear anti-aliased HD procedural background ring
        Render2DHelper.drawTexture(context, TextureAssets.CROSSHAIR_RING_HD, cx - rad, cy - rad, diameter, diameter, 0x9010121A);

        // Batched cooldown arc rendering
        if (cooldown >= 0.99f) {
            // Full attack cooldown ready: 1 fast batched procedural HD ring + ambient glow (1 blit instead of 48 lines)
            Render2DHelper.drawGlow(context, cx - rad, cy - rad, diameter, diameter, 3.5f, ColorUtils.withAlpha(accent1, 100));
            Render2DHelper.drawTexture(context, TextureAssets.CROSSHAIR_RING_HD, cx - rad, cy - rad, diameter, diameter, accent1);
        } else {
            // Cooldown recharging: 1 ambient background glow for the whole arc
            Render2DHelper.drawGlow(context, cx - rad, cy - rad, diameter, diameter, 3.0f, ColorUtils.withAlpha(accent1, 65));
            // Batched 24-segment arc without per-segment glow calls
            int segments = 24;
            int activeSegments = Math.max(1, Math.round(segments * Math.min(1.0f, Math.max(0.05f, cooldown))));
            for (int i = 0; i < activeSegments; i++) {
                float t = (float) i / segments;
                int arcColor = ColorUtils.interpolateColor(accent1, accent2, t);
                double a1 = Math.toRadians(i * (360.0 / segments) - 90.0);
                double a2 = Math.toRadians((i + 1) * (360.0 / segments) - 90.0);
                float x1 = cx + (float) Math.cos(a1) * rad;
                float y1 = cy + (float) Math.sin(a1) * rad;
                float x2 = cx + (float) Math.cos(a2) * rad;
                float y2 = cy + (float) Math.sin(a2) * rad;
                Render3DHelper.drawSmoothLine(context, x1, y1, x2, y2, 1.8f, arcColor, false);
            }
        }

        // Central glowing dot
        Render2DHelper.drawGlow(context, cx - 2.0f, cy - 2.0f, 4.0f, 4.0f, 5.0f, ColorUtils.withAlpha(accent1, 140));
        Render2DHelper.drawRoundedRect(context, cx - 1.5f, cy - 1.5f, 3.0f, 3.0f, 1.5f, accent1);
    }

    private void renderExpensiveModern(GuiGraphicsExtractor context, float cx, float cy, float cooldown, int accent1, int accent2) {
        TextureAssets.ensureInitialized();
        float baseR = size.getFloatValue() + animatedSpread * 0.4f;
        float innerRingR = baseR * 0.65f;
        float ringD = innerRingR * 2.0f;

        // 1. HD Inner Ring with cooldown pulse
        int ringColor = (cooldown < 0.99f)
                ? ColorUtils.interpolateColor(accent1, accent2, 1.0f - cooldown)
                : ColorUtils.withAlpha(accent1, 200);
        Render2DHelper.drawTexture(context, TextureAssets.CROSSHAIR_RING_HD, cx - innerRingR, cy - innerRingR, ringD, ringD, ringColor);

        // 2. 4 Precision Outer Ticks
        float tickOffset = innerRingR + 2.5f + animatedSpread * 0.5f;
        float tickLen = Math.max(3.0f, baseR * 0.45f);
        float tickThick = 1.4f;

        // Top tick
        drawSmoothTick(context, cx, cy - tickOffset - tickLen, cx, cy - tickOffset, tickThick, accent1);
        // Bottom tick
        drawSmoothTick(context, cx, cy + tickOffset, cx, cy + tickOffset + tickLen, tickThick, accent2);
        // Left tick
        drawSmoothTick(context, cx - tickOffset - tickLen, cy, cx - tickOffset, cy, tickThick, accent1);
        // Right tick
        drawSmoothTick(context, cx + tickOffset, cy, cx + tickOffset + tickLen, cy, tickThick, accent2);

        // 3. Precision Center Micro-Dot
        Render2DHelper.drawGlow(context, cx - 1.5f, cy - 1.5f, 3.0f, 3.0f, 4.0f, ColorUtils.withAlpha(accent1, 150));
        Render2DHelper.drawRoundedRect(context, cx - 1.0f, cy - 1.0f, 2.0f, 2.0f, 1.0f, 0xFFFFFFFF);
    }

    private void renderCatlavanCross(GuiGraphicsExtractor context, float cx, float cy, int accent1, int accent2) {
        float g = gap.getFloatValue() + animatedSpread;
        float len = size.getFloatValue();

        // 4 antialiased neon rays with dark contrast outline and glow
        // Top ray
        drawSmoothTick(context, cx, cy - g - len, cx, cy - g, 1.8f, accent1);
        // Bottom ray
        drawSmoothTick(context, cx, cy + g, cx, cy + g + len, 1.8f, accent2);
        // Left ray
        drawSmoothTick(context, cx - g - len, cy, cx - g, cy, 1.8f, accent1);
        // Right ray
        drawSmoothTick(context, cx + g, cy, cx + g + len, cy, 1.8f, accent2);

        // Center micro-dot
        Render2DHelper.drawRoundedRect(context, cx - 1.0f, cy - 1.0f, 2.0f, 2.0f, 1.0f, 0xFFFFFFFF);
    }

    private void drawSmoothTick(GuiGraphicsExtractor context, float x1, float y1, float x2, float y2, float thickness, int color) {
        Render3DHelper.drawSmoothLine(context, x1, y1, x2, y2, thickness + 1.2f, 0xAA000000, false);
        Render3DHelper.drawSmoothLine(context, x1, y1, x2, y2, thickness, color, true);
    }

    private void renderDot(GuiGraphicsExtractor context, float cx, float cy, int color) {
        Render2DHelper.drawGlow(context, cx - 2.5f, cy - 2.5f, 5.0f, 5.0f, 6.0f, ColorUtils.withAlpha(color, 160));
        Render2DHelper.drawRoundedRect(context, cx - 2.5f, cy - 2.5f, 5.0f, 5.0f, 2.5f, 0xCC000000);
        Render2DHelper.drawRoundedRect(context, cx - 1.5f, cy - 1.5f, 3.0f, 3.0f, 1.5f, color);
    }
}
