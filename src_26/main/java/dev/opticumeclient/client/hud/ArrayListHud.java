package dev.opticumeclient.client.hud;

import dev.opticumeclient.client.modules.Category;
import dev.opticumeclient.client.modules.Module;
import dev.opticumeclient.client.modules.ModuleManager;
import dev.opticumeclient.client.render.ColorUtils;
import dev.opticumeclient.client.render.Render2DHelper;
import dev.opticumeclient.client.render.ThemeManager;
import dev.opticumeclient.client.setting.BooleanSetting;
import dev.opticumeclient.client.setting.NumberSetting;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Draggable ArrayList displaying active modules sorted by width with gradient accents.
 */
public class ArrayListHud extends Module {
    private static final class RowHolder {
        float pillX, pillY, pillW, rowH, anim, nameW;
        int waveColor, alpha255;
        String name, suffix;
    }

    private final NumberSetting posX = new NumberSetting("PosX", "X позиция", 830.0, 0.0, 1920.0, 1.0);
    private final NumberSetting posY = new NumberSetting("PosY", "Y позиция", 8.0, 0.0, 1080.0, 1.0);
    private final BooleanSetting hideHudModules = new BooleanSetting("Hide HUD", "Скрывать HUD-модули из списка", false);

    private final HudManager.DraggableWidget dragWidget;
    private final List<Module> visibleModules = new ArrayList<>();
    private final List<RowHolder> rowHolders = new ArrayList<>();
    private int rowCount = 0;

    public ArrayListHud() {
        super("ArrayList", "Список активных модулей с волновым градиентом", Category.HUD);
        addSetting(posX);
        addSetting(posY);
        addSetting(hideHudModules);
        setEnabled(true);

        this.dragWidget = HudManager.getInstance().registerWidget(new HudManager.DraggableWidget(
                "ArrayList",
                posX.getFloatValue(),
                posY.getFloatValue(),
                118.0f,
                150.0f,
                this::isEnabled,
                (newX, newY) -> {
                    posX.setValue((double) Math.round(newX));
                    posY.setValue((double) Math.round(newY));
                }
        ));
    }

    private RowHolder getHolder(int idx) {
        while (rowHolders.size() <= idx) {
            rowHolders.add(new RowHolder());
        }
        return rowHolders.get(idx);
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;
        visibleModules.clear();
        for (Module m : ModuleManager.getInstance().getModules()) {
            if (hideHudModules.isEnabled() && m.getCategory() == Category.HUD) continue;
            if (m.isEnabled() && m.getToggleAnimation().getValue() < 0.05f && m.getToggleAnimation().getTargetValue() == 0.0f) {
                m.getToggleAnimation().setImmediate(1.0f);
            }
            if (m.isEnabled() || m.getToggleAnimation().getValue() > 0.02f) {
                visibleModules.add(m);
            }
        }
        float scale = 0.82f;
        visibleModules.sort(Comparator.comparingDouble((Module m) -> {
            String suffix = m.getDisplaySuffix();
            String full = (suffix != null && !suffix.isEmpty()) ? (m.getName() + " " + suffix) : m.getName();
            return -Render2DHelper.getTextWidth(full, scale);
        }));
    }

    @Override
    public void onRender2D(GuiGraphicsExtractor context, float tickDelta) {
        if (!isEnabled() || mc.player == null) return;

        float screenW = mc.getWindow() != null ? mc.getWindow().getGuiScaledWidth() : 960.0f;
        if (!dragWidget.isDragging() && posX.getFloatValue() > screenW - 30.0f) {
            posX.setValue((double) Math.max(8.0f, screenW - dragWidget.getWidth() - 8.0f));
        }
        dragWidget.syncExternalPosition(posX.getFloatValue(), posY.getFloatValue());

        float scale = 0.82f;
        float x = dragWidget.getX();
        float y = dragWidget.getY();
        float widgetW = dragWidget.getWidth();

        if (visibleModules.isEmpty()) {
            dragWidget.setBounds(90.0f, 20.0f);
            return;
        }

        float curY = y;
        float maxW = 86.0f;
        float rowH = 14.5f;
        rowCount = 0;

        for (int i = 0; i < visibleModules.size(); i++) {
            Module m = visibleModules.get(i);
            float anim = Math.max(0.0f, Math.min(1.0f, m.getToggleAnimation().getValue()));
            if (anim <= 0.02f) continue;

            String name = m.getName();
            String suffix = m.getDisplaySuffix();
            boolean hasSuffix = suffix != null && !suffix.isEmpty();

            float nameW = Render2DHelper.getTextWidth(name, scale);
            float suffixW = hasSuffix ? Render2DHelper.getTextWidth(" " + suffix, scale) : 0.0f;
            float pillW = nameW + suffixW + 13.0f;
            if (pillW > maxW) maxW = pillW;

            float alignedRightX = x + widgetW;
            float slideOffset = (1.0f - anim) * (pillW + 10.0f);
            float pillX = alignedRightX - pillW + slideOffset;

            int waveColor = ThemeManager.getInstance().getAccentColor(i * 18);
            int alpha255 = (int) (255 * anim);

            RowHolder row = getHolder(rowCount++);
            row.pillX = pillX;
            row.pillY = curY;
            row.pillW = pillW;
            row.rowH = rowH;
            row.waveColor = waveColor;
            row.alpha255 = alpha255;
            row.anim = anim;
            row.name = name;
            row.suffix = hasSuffix ? suffix : null;
            row.nameW = nameW;

            curY += rowH * anim;
        }

        if (rowCount == 0) {
            dragWidget.setBounds(90.0f, 20.0f);
            return;
        }

        float totalH = curY - y;
        dragWidget.setBounds(maxW, Math.max(20.0f, totalH));

        float rightEdge = x + widgetW;
        Render2DHelper.drawGlow(context, rightEdge - 5.0f, y, 5.0f, totalH, 6.0f,
                ColorUtils.withAlpha(ThemeManager.getInstance().getCurrentTheme().getPrimaryColor(), 45));

        for (int i = 0; i < rowCount; i++) {
            RowHolder row = rowHolders.get(i);
            Render2DHelper.drawRoundedRect(context, row.pillX, row.pillY, row.pillW, row.rowH - 1.0f, 3.0f,
                    ColorUtils.withAlpha(0xFF0D0F18, (int) (205 * row.anim)));
            float barX = row.pillX + row.pillW - 2.2f;
            Render2DHelper.drawRoundedRect(context, barX, row.pillY, 2.2f, row.rowH - 1.0f, 1.0f,
                    ColorUtils.withAlpha(row.waveColor, row.alpha255));
        }

        for (int i = 0; i < rowCount; i++) {
            RowHolder row = rowHolders.get(i);
            Render2DHelper.drawScaledText(context, row.name, row.pillX + 5.0f, row.pillY + 3.2f,
                    ColorUtils.withAlpha(row.waveColor, row.alpha255), scale, true);
            if (row.suffix != null) {
                Render2DHelper.drawScaledText(context, " " + row.suffix, row.pillX + 5.0f + row.nameW, row.pillY + 3.2f,
                        ColorUtils.withAlpha(0xFF9CA3AF, row.alpha255), scale, true);
            }
        }
    }
}
