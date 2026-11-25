package dev.opticumeclient.client.hud;

import dev.opticumeclient.client.modules.Category;
import dev.opticumeclient.client.modules.Module;
import dev.opticumeclient.client.render.ColorUtils;
import dev.opticumeclient.client.render.Render2DHelper;
import dev.opticumeclient.client.render.Theme;
import dev.opticumeclient.client.render.ThemeManager;
import dev.opticumeclient.client.setting.BooleanSetting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;

/**
 * Liquid Glass / Relic custom hotbar replacing the vanilla item hotbar via InGameHudMixin,
 * featuring an exponentially interpolated selection indicator and integrated offhand pill.
 */
public class CustomHotbarHud extends Module {
    private static CustomHotbarHud instance;

    private final BooleanSetting smoothSelector = new BooleanSetting("Smooth Selector", "Плавное скольжение рамки выбранного слота", true);
    private final BooleanSetting showSlotNumbers = new BooleanSetting("Slot Numbers", "Цифры 1–9 в углах слотов", false);

    private float animSlot = 0.0f;
    private long lastNanos = System.nanoTime();

    public CustomHotbarHud() {
        super("CustomHotbar", "Стеклянный кастомный хотбар с плавным переключением слотов", Category.HUD);
        instance = this;
        addSetting(smoothSelector);
        addSetting(showSlotNumbers);
        setEnabled(false);
    }

    public static CustomHotbarHud getInstance() {
        return instance;
    }

    public static boolean replacesVanillaHotbar() {
        return instance != null && instance.isEnabled();
    }

    @Override
    public void onRender2D(GuiGraphicsExtractor context, float tickDelta) {
        if (!isEnabled() || mc.player == null || mc.getWindow() == null) return;

        long now = System.nanoTime();
        float dt = Math.min(0.05f, Math.max(0.0001f, (now - lastNanos) / 1.0e9f));
        lastNanos = now;

        int selected = Mth.clamp(mc.player.getInventory().getSelectedSlot(), 0, 8);
        if (smoothSelector.isEnabled()) {
            float rate = 1.0f - (float) Math.exp(-22.0f * dt);
            animSlot += (selected - animSlot) * rate;
        } else {
            animSlot = selected;
        }

        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();

        float slotW = 20.0f;
        float barW = 186.0f;
        float barH = 22.0f;
        float barX = (sw - barW) * 0.5f;
        float barY = sh - barH - 2.0f;

        Theme theme = ThemeManager.getInstance().getCurrentTheme();
        int c1 = theme.getPrimaryColor();
        int c2 = theme.getSecondaryColor();

        HudPanelRenderer.beginWidget("CustomHotbar");
        try {
            HudPanelRenderer.drawSurface(context, "CustomHotbar", barX, barY, barW, barH, 6.0f, 232, 42);

            float selX = barX + 3.0f + animSlot * slotW;
            float selY = barY + 2.0f;
            Render2DHelper.drawRoundedRectFloat(context, selX, selY, 18.0f, 18.0f, 4.5f,
                    ColorUtils.withAlpha(c1, 68));
            Render2DHelper.drawGradientRoundedRectFloat(context, selX + 3.0f, barY + barH - 2.4f, 12.0f, 1.5f, 0.75f,
                    c1, c1, c2, c2);
            Render2DHelper.drawRoundedOutlineFloat(context, selX, selY, 18.0f, 18.0f, 4.5f, 0.9f,
                    ColorUtils.withAlpha(c2, 210));

            for (int i = 0; i < 9; i++) {
                int ix = Math.round(barX + 4.0f + i * slotW);
                int iy = Math.round(barY + 3.0f);
                ItemStack stack = mc.player.getInventory().getItem(i);
                if (!stack.isEmpty()) {
                    context.item(stack, ix, iy);
                    context.itemDecorations(mc.font, stack, ix, iy);
                }
                if (showSlotNumbers.isEnabled()) {
                    Render2DHelper.drawScaledText(context, String.valueOf(i + 1), ix + 1.0f, iy + 0.5f,
                            0x88CBD5E1, 0.56f, false);
                }
            }

            // Offhand slot pill on the opposite side of main arm
            ItemStack offhand = mc.player.getOffhandItem();
            if (!offhand.isEmpty()) {
                boolean leftSide = mc.player.getMainArm() == HumanoidArm.RIGHT;
                float offW = 24.0f;
                float offX = leftSide ? (barX - offW - 5.0f) : (barX + barW + 5.0f);
                HudPanelRenderer.drawSurface(context, "CustomHotbar", offX, barY, offW, barH, 6.0f, 226, 36);
                int ix = Math.round(offX + 4.0f);
                int iy = Math.round(barY + 3.0f);
                context.item(offhand, ix, iy);
                context.itemDecorations(mc.font, offhand, ix, iy);
            }
        } finally {
            HudPanelRenderer.endWidget();
        }
    }
}
