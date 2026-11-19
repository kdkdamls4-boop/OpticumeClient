package dev.opticumeclient.client.modules.visuals.nametags;

import dev.opticumeclient.client.render.ColorUtils;
import dev.opticumeclient.client.render.Render2DHelper;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3x2fStack;

/**
 * Ported from Desire NameTagItemRenderer:
 * Renders item icon, stack count above item, and durability bar below in GUI coordinates.
 */
public final class NameTagItemRenderer {
    private static final float MIN_ITEM_ALPHA = 0.45f;

    private NameTagItemRenderer() {
    }

    public static void draw(GuiGraphicsExtractor context, ItemStack stack, float x, float y, float size, float alpha) {
        if (context == null || stack == null || stack.isEmpty() || size <= 0.0f || alpha <= 0.01f) return;

        if (alpha >= MIN_ITEM_ALPHA) {
            Matrix3x2fStack pose = context.pose();
            pose.pushMatrix();
            pose.translate(x, y);
            pose.scale(size / 16.0f, size / 16.0f);
            try {
                context.fakeItem(stack, 0, 0);
            } catch (Throwable ignored) {
            }
            pose.popMatrix();
        }

        int a = Mth.clamp(Math.round(255.0f * alpha), 0, 255);
        if (stack.getCount() > 1) {
            String count = String.valueOf(stack.getCount());
            float fontScale = 0.62f;
            float w = Render2DHelper.getTextWidth(count, fontScale);
            Render2DHelper.drawScaledText(
                    context,
                    count,
                    x + size * 0.5f - w * 0.5f,
                    y - 6.0f,
                    ColorUtils.rgba(255, 255, 255, a),
                    fontScale,
                    true
            );
        }

        if (stack.isDamageableItem() && stack.isDamaged() && stack.getMaxDamage() > 0) {
            float fraction = Mth.clamp((float) (stack.getMaxDamage() - stack.getDamageValue()) / stack.getMaxDamage(), 0.0f, 1.0f);
            float barH = 1.6f;
            float barY = y + size - 1.2f;
            float filled = size * fraction;
            int barColor = ColorUtils.rgba(
                    Mth.clamp(Math.round(255.0f * (1.0f - fraction)), 0, 255),
                    Mth.clamp(Math.round(255.0f * fraction), 0, 255),
                    0,
                    a
            );
            Render2DHelper.drawRoundedRectFloat(context, x, barY, size, barH, 0.5f, ColorUtils.rgba(0, 0, 0, a));
            if (filled > 0.2f) {
                Render2DHelper.drawRoundedRectFloat(context, x, barY, filled, barH, 0.5f, barColor);
            }
        }
    }
}
