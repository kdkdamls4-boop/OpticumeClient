package dev.opticumeclient.client.hud;

import dev.opticumeclient.client.modules.Category;
import dev.opticumeclient.client.modules.Module;
import dev.opticumeclient.client.render.ColorUtils;
import dev.opticumeclient.client.render.Render2DHelper;
import dev.opticumeclient.client.render.Theme;
import dev.opticumeclient.client.render.ThemeManager;
import dev.opticumeclient.client.setting.ModeSetting;
import dev.opticumeclient.client.setting.NumberSetting;
import net.minecraft.client.gui.GuiGraphicsExtractor; 
import net.minecraft.world.entity.EquipmentSlot; 
import net.minecraft.world.item.ItemStack; 
import org.joml.Matrix3x2fStack;

import java.util.Locale;

/**
 * Liquid Glass Armor & Hand Equipment HUD widget with durability bars and Smart Snap dragging.
 */
public class ArmorHud extends Module {
    private final NumberSetting posX = new NumberSetting("PosX", "X позиция", 8.0, 0.0, 1920.0, 1.0);
    private final NumberSetting posY = new NumberSetting("PosY", "Y позиция", 226.0, 0.0, 1080.0, 1.0);
    private final ModeSetting layout = new ModeSetting("Layout", "Расположение слотов брони", "Vertical", "Vertical", "Horizontal");

    private final HudManager.DraggableWidget dragWidget;

    private final ItemStack[] cachedEquipment = new ItemStack[6];
    private final String[] cachedDurText = new String[6];
    private final String[] cachedItemName = new String[6];
    private final float[] cachedPct = new float[6];
    private final int[] cachedDurCol = new int[6];
    private final boolean[] cachedDamageable = new boolean[6];
    private final boolean[] cachedNotEmpty = new boolean[6];

    public ArmorHud() {
        super("ArmorHUD", "Индикатор надетой брони и прочности экипировки", Category.HUD);
        addSetting(layout);
        addSetting(posX);
        addSetting(posY);
        setEnabled(true);

        this.dragWidget = HudManager.getInstance().registerWidget(new HudManager.DraggableWidget(
                "ArmorHUD",
                posX.getFloatValue(),
                posY.getFloatValue(),
                122.0f,
                126.0f,
                this::isEnabled,
                (newX, newY) -> {
                    posX.setValue((double) Math.round(newX));
                    posY.setValue((double) Math.round(newY));
                }
        ));
    }

    @Override
    public void onTick() {
        if (!isEnabled() || mc.player == null) return;

        cachedEquipment[0] = mc.player.getItemBySlot(EquipmentSlot.HEAD);
        cachedEquipment[1] = mc.player.getItemBySlot(EquipmentSlot.CHEST);
        cachedEquipment[2] = mc.player.getItemBySlot(EquipmentSlot.LEGS);
        cachedEquipment[3] = mc.player.getItemBySlot(EquipmentSlot.FEET);
        cachedEquipment[4] = mc.player.getMainHandItem();
        cachedEquipment[5] = mc.player.getOffhandItem();

        for (int i = 0; i < 6; i++) {
            ItemStack stack = cachedEquipment[i];
            boolean notEmpty = stack != null && !stack.isEmpty();
            cachedNotEmpty[i] = notEmpty;
            if (notEmpty) {
                boolean dmg = stack.isDamageableItem();
                cachedDamageable[i] = dmg;
                if (dmg) {
                    int max = Math.max(1, stack.getMaxDamage());
                    int cur = Math.max(0, max - stack.getDamageValue());
                    float pct = Math.max(0.0f, Math.min(1.0f, (float) cur / max));
                    cachedPct[i] = pct;
                    int durCol = getDurabilityColor(pct);
                    cachedDurCol[i] = durCol;
                    cachedDurText[i] = cur + " (" + Math.round(pct * 100) + "%)";
                } else {
                    String name = stack.getHoverName().getString();
                    if (name.length() > 14) name = name.substring(0, 13) + "..";
                    cachedItemName[i] = name;
                }
            } else {
                cachedDamageable[i] = false;
                cachedDurText[i] = null;
                cachedItemName[i] = null;
            }
        }
    }

    @Override
    public void onRender2D(GuiGraphicsExtractor context, float tickDelta) {
        if (!isEnabled() || mc.player == null) return;

        // Ensure cached equipment is populated on first frame
        if (cachedEquipment[0] == null && cachedEquipment[4] == null) {
            onTick();
        }

        boolean horizontal = layout.is("Horizontal");
        float cardW = horizontal ? (6 * 24.0f + 8.0f) : 118.0f;
        float cardH = horizontal ? 34.0f : (18.0f + 6 * 20.0f + 6.0f);

        dragWidget.syncExternalPosition(posX.getFloatValue(), posY.getFloatValue());
        dragWidget.setBounds(cardW, cardH);

        float x = dragWidget.getX();
        float y = dragWidget.getY();

        Theme theme = ThemeManager.getInstance().getCurrentTheme();
        int c1 = theme.getPrimaryColor();
        int c2 = theme.getSecondaryColor();

        Render2DHelper.drawLiquidGlass(context, x, y, cardW, cardH, 6.0f, 220, 38);

        if (horizontal) {
            float curX = x + 5.0f;
            for (int i = 0; i < 6; i++) {
                Render2DHelper.drawRoundedRect(context, curX, y + 4.0f, 22.0f, 26.0f, 3.0f, 0x40080A10);
                if (cachedNotEmpty[i]) {
                    ItemStack stack = cachedEquipment[i];
                    context.item(stack, (int) Math.round(curX + 3.0f), (int) Math.round(y + 5.0f));
                    context.itemDecorations(mc.font, stack, (int) Math.round(curX + 3.0f), (int) Math.round(y + 5.0f));

                    if (cachedDamageable[i]) {
                        float pct = cachedPct[i];
                        int col = cachedDurCol[i];
                        String pctStr = Math.round(pct * 100) + "%";
                        float tw = Render2DHelper.getTextWidth(pctStr, 0.56f);
                        Render2DHelper.drawScaledText(context, pctStr, curX + (22.0f - tw) * 0.5f, y + 22.0f, col, 0.56f, true);
                    }
                }
                curX += 24.0f;
            }
            return;
        }

        // Vertical Layout with Header + Durability Bars
        Render2DHelper.drawScaledText(context, "Equipment", x + 7.0f, y + 5.0f, 0xFFFFFFFF, 0.78f, true);
        Render2DHelper.drawGradientRoundedRect(context, x + 6.0f, y + 15.5f, cardW - 12.0f, 1.4f, 0.7f, c1, c2);

        float curY = y + 19.0f;
        for (int i = 0; i < 6; i++) {
            Render2DHelper.drawRoundedRect(context, x + 5.0f, curY, 18.0f, 18.0f, 3.0f, 0x45090B12);

            if (cachedNotEmpty[i]) {
                ItemStack stack = cachedEquipment[i];
                context.item(stack, (int) Math.round(x + 6.0f), (int) Math.round(curY + 1.0f));
                context.itemDecorations(mc.font, stack, (int) Math.round(x + 6.0f), (int) Math.round(curY + 1.0f));

                if (cachedDamageable[i]) {
                    float pct = cachedPct[i];
                    int durCol = cachedDurCol[i];
                    String durText = cachedDurText[i];
                    if (durText != null) {
                        Render2DHelper.drawScaledText(context, durText, x + 26.0f, curY + 2.0f, durCol, 0.70f, true);
                    }

                    float barX = x + 26.0f;
                    float barY = curY + 12.0f;
                    float barW = cardW - 32.0f;
                    Render2DHelper.drawRoundedRect(context, barX, barY, barW, 2.8f, 1.4f, 0x35FFFFFF);
                    Render2DHelper.drawRoundedRect(context, barX, barY, Math.max(2.5f, barW * pct), 2.8f, 1.4f, durCol);
                } else {
                    String itemName = cachedItemName[i];
                    if (itemName != null) {
                        Render2DHelper.drawScaledText(context, itemName, x + 26.0f, curY + 4.0f, 0xFFFFFFFF, 0.70f, false);
                    }
                }
            } else {
                Render2DHelper.drawScaledText(context, "Empty slot", x + 26.0f, curY + 4.0f, 0x669CA3AF, 0.68f, false);
            }
            curY += 20.0f;
        }
    }

    private int getDurabilityColor(float pct) {
        if (pct > 0.65f) return 0xFF4ADE80;
        if (pct > 0.30f) return 0xFFFBBF24;
        return 0xFFF87171;
    }
}
