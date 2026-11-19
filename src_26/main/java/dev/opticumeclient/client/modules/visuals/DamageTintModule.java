package dev.opticumeclient.client.modules.visuals;

import dev.opticumeclient.client.modules.Category;
import dev.opticumeclient.client.modules.Module;
import dev.opticumeclient.client.render.ColorUtils;
import dev.opticumeclient.client.render.Render2DHelper;
import dev.opticumeclient.client.render.Render3DHelper;
import dev.opticumeclient.client.render.TextureAssets;
import dev.opticumeclient.client.setting.BooleanSetting;
import dev.opticumeclient.client.setting.NumberSetting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Ported from Desire DamageTintModule:
 * Floating 3D->2D damage & heal popups around the player with soft radial glow.
 */
public class DamageTintModule extends Module {
    private static final int HEAL_COLOR = ColorUtils.rgba(85, 255, 80, 255);
    private static final int DAMAGE_COLOR = ColorUtils.rgba(255, 220, 50, 255);
    private static final int MAX_AGE = 36;
    private static final float BASE_FONT_SCALE = 1.15f;

    private static DamageTintModule instance;

    private final BooleanSetting showDamage = new BooleanSetting("Урон", "Показывать получаемый урон", true);
    private final BooleanSetting showHeal = new BooleanSetting("Лечение", "Показывать восстановление здоровья", true);
    private final NumberSetting scale = new NumberSetting("Размер", "Масштаб всплывающих цифр", 1.0, 0.6, 2.0, 0.1);

    private final List<TintPopup> tints = new ArrayList<>();
    private final Random random = new Random();
    private float lastTotal = -1.0f;

    public DamageTintModule() {
        super("DamageTint", "Всплывающие цифры урона и лечения с подсветкой", Category.VISUALS);
        instance = this;
        addSetting(showDamage);
        addSetting(showHeal);
        addSetting(scale);
    }

    public static DamageTintModule getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        super.onEnable();
        lastTotal = -1.0f;
        tints.clear();
    }

    @Override
    public void onDisable() {
        super.onDisable();
        lastTotal = -1.0f;
        tints.clear();
    }

    @Override
    public void onTick() {
        if (!isEnabled() || mc.player == null || mc.level == null) {
            lastTotal = -1.0f;
            tints.clear();
            return;
        }

        float total = mc.player.getHealth() + mc.player.getAbsorptionAmount();
        if (lastTotal < 0.0f) {
            lastTotal = total;
            tickTints();
            return;
        }

        float delta = total - lastTotal;
        if (Math.abs(delta) >= 0.05f) {
            boolean heal = delta > 0.0f;
            if ((heal && showHeal.isEnabled()) || (!heal && showDamage.isEnabled())) {
                spawn(Math.abs(delta), heal);
            }
        }
        lastTotal = total;
        tickTints();
    }

    private void spawn(float amount, boolean heal) {
        if (mc.player == null) return;
        double x = mc.player.getX() + (random.nextDouble() - 0.5) * 1.1;
        double y = mc.player.getY() + mc.player.getBbHeight() * (0.25 + random.nextDouble() * 0.7);
        double z = mc.player.getZ() + (random.nextDouble() - 0.5) * 1.1;
        tints.add(new TintPopup(
                x, y, z,
                (random.nextDouble() - 0.5) * 0.045,
                0.018 + random.nextDouble() * 0.025,
                (random.nextDouble() - 0.5) * 0.045,
                amount,
                heal
        ));
    }

    private void tickTints() {
        Iterator<TintPopup> iterator = tints.iterator();
        while (iterator.hasNext()) {
            if (iterator.next().tick()) {
                iterator.remove();
            }
        }
    }

    @Override
    public void onRender2D(GuiGraphicsExtractor context, float tickDelta) {
        if (!isEnabled() || tints.isEmpty() || mc.player == null || mc.level == null) return;

        Render3DHelper.setupRender3D();
        float fontScale = BASE_FONT_SCALE * scale.getFloatValue();
        float lineHeight = 9.0f * fontScale;

        for (TintPopup tint : tints) {
            float[] projected = Render3DHelper.worldToScreen(new Vec3(tint.x(tickDelta), tint.y(tickDelta), tint.z(tickDelta)));
            if (projected == null) continue;

            float life = 1.0f - tint.age / (float) tint.maxAge;
            float fade = life < 0.25f ? life / 0.25f : 1.0f;
            int alpha = Mth.clamp(Math.round(255.0f * fade), 0, 255);
            if (alpha <= 8) continue;

            String text = (tint.heal ? "+" : "-") + format(tint.amount);
            int baseColor = tint.heal ? HEAL_COLOR : DAMAGE_COLOR;
            float width = Render2DHelper.getTextWidth(text, fontScale);
            float x = projected[0] - width * 0.5f;
            float y = projected[1] - lineHeight * 0.5f;

            drawGlow(context, x, y, width, lineHeight, baseColor, fade);
            Render2DHelper.drawScaledText(context, text, x, y, ColorUtils.withAlpha(baseColor, alpha), fontScale, true);
        }
    }

    private static void drawGlow(GuiGraphicsExtractor context, float x, float y, float width, float height, int color, float fade) {
        float cx = x + width * 0.5f;
        float cy = y + height * 0.5f;
        glowLayer(context, cx, cy, width * 4.2f, height * 4.0f, color, 0.55f * fade);
        glowLayer(context, cx, cy, width * 2.6f, height * 2.4f, color, 0.85f * fade);
        glowLayer(context, cx, cy, width * 1.6f, height * 1.5f, 0xFFFFFFFF, 0.35f * fade);
    }

    private static void glowLayer(GuiGraphicsExtractor context, float cx, float cy, float w, float h, int color, float alpha) {
        int a = Mth.clamp(Math.round(255.0f * alpha), 0, 255);
        if (a <= 2) return;
        Render2DHelper.drawTextureFloat(
                context,
                TextureAssets.DESIRE_GLOW,
                cx - w * 0.5f,
                cy - h * 0.5f,
                w,
                h,
                ColorUtils.withAlpha(color, a)
        );
    }

    private static String format(float amount) {
        float rounded = Math.round(amount * 10.0f) / 10.0f;
        if (Math.abs(rounded - Math.round(rounded)) < 0.05f) {
            return String.valueOf(Math.round(rounded));
        }
        return String.format(Locale.ROOT, "%.1f", rounded);
    }

    private static final class TintPopup {
        double x, y, z;
        double prevX, prevY, prevZ;
        double vx, vy, vz;
        final float amount;
        final boolean heal;
        int age;
        final int maxAge = MAX_AGE;

        TintPopup(double x, double y, double z, double vx, double vy, double vz, float amount, boolean heal) {
            this.x = this.prevX = x;
            this.y = this.prevY = y;
            this.z = this.prevZ = z;
            this.vx = vx;
            this.vy = vy;
            this.vz = vz;
            this.amount = amount;
            this.heal = heal;
        }

        boolean tick() {
            prevX = x;
            prevY = y;
            prevZ = z;
            x += vx;
            y += vy;
            z += vz;
            vx *= 0.96;
            vz *= 0.96;
            vy *= 0.985;
            age++;
            return age >= maxAge;
        }

        double x(float partial) {
            return Mth.lerp(partial, prevX, x);
        }

        double y(float partial) {
            return Mth.lerp(partial, prevY, y);
        }

        double z(float partial) {
            return Mth.lerp(partial, prevZ, z);
        }
    }
}
