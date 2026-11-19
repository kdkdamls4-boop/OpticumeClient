package dev.opticumeclient.client.hud;

import dev.opticumeclient.client.gui.SystemModuleCard;
import dev.opticumeclient.client.modules.Category;
import dev.opticumeclient.client.modules.Module;
import dev.opticumeclient.client.modules.ModuleManager;
import dev.opticumeclient.client.render.ColorUtils;
import dev.opticumeclient.client.render.Render2DHelper;
import dev.opticumeclient.client.render.TextureAssets;
import dev.opticumeclient.client.render.Theme;
import dev.opticumeclient.client.render.ThemeManager;
import dev.opticumeclient.client.setting.BooleanSetting;
import dev.opticumeclient.client.setting.NumberSetting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;

import java.util.ArrayList;
import java.util.List;

/**
 * Ported from Skycore / SystemDLC (BindsHud):
 * Draggable Liquid Glass HUD widget displaying active keybound modules with their key badges.
 */
public class BindsHud extends Module {
    private final NumberSetting posX = new NumberSetting("PosX", "X позиция", 8.0, 0.0, 1920.0, 1.0);
    private final NumberSetting posY = new NumberSetting("PosY", "Y позиция", 330.0, 0.0, 1080.0, 1.0);
    private final BooleanSetting onlyActiveSetting = new BooleanSetting("Only Enabled", "Показывать только включённые в данный момент модули", true);

    private final HudManager.DraggableWidget dragWidget;

    private record BindRow(String moduleName, String keyName, boolean active) {}

    private final List<BindRow> rows = new ArrayList<>();
    private final List<BindRow> previewRows = List.of(
            new BindRow("OpticumeAura", "R", true),
            new BindRow("TargetESP", "G", true)
    );

    public BindsHud() {
        super("BindsHUD", "Отображение списка активных модулей с назначенными клавишами (Keybinds)", Category.HUD);
        addSetting(posX);
        addSetting(posY);
        addSetting(onlyActiveSetting);
        setEnabled(true);

        this.dragWidget = HudManager.getInstance().registerWidget(new HudManager.DraggableWidget(
                "BindsHUD",
                posX.getFloatValue(),
                posY.getFloatValue(),
                138.0f,
                48.0f,
                this::isEnabled,
                (newX, newY) -> {
                    posX.setValue((double) Math.round(newX));
                    posY.setValue((double) Math.round(newY));
                }
        ));
    }

    @Override
    public void onRender2D(GuiGraphicsExtractor context, float tickDelta) {
        if (!isEnabled() || mc.player == null) return;

        rows.clear();
        for (Module m : ModuleManager.getInstance().getModules()) {
            if (m.getKeyBind() <= 0) continue;
            if (onlyActiveSetting.isEnabled() && !m.isEnabled()) continue;
            rows.add(new BindRow(m.getName(), formatKey(m.getKeyBind()), m.isEnabled()));
        }

        boolean inChat = mc.gui.screen() instanceof ChatScreen;
        if (rows.isEmpty() && !inChat) {
            return;
        }

        List<BindRow> list = rows.isEmpty() ? previewRows : rows;
        float w = 136.0f;
        float headerH = 19.0f;
        float rowH = 15.5f;
        for (BindRow r : list) {
            float needed = Render2DHelper.getTextWidth(r.moduleName(), 0.74f) + Render2DHelper.getTextWidth(r.keyName(), 0.68f) + 32.0f;
            if (needed > w) w = needed;
        }
        float h = headerH + list.size() * rowH + 5.0f;

        dragWidget.syncExternalPosition(posX.getFloatValue(), posY.getFloatValue());
        dragWidget.setBounds(w, h);

        float x = dragWidget.getX();
        float y = dragWidget.getY();

        Theme theme = ThemeManager.getInstance().getCurrentTheme();
        int c1 = theme.getPrimaryColor();
        int c2 = theme.getSecondaryColor();

        HudPanelRenderer.beginWidget("BindsHUD");
        try {
            HudPanelRenderer.drawHeaderPanel(context, "BindsHUD", x, y, w, h, headerH, 6.0f,
                    TextureAssets.DESIRE_HUD_LAYERS, "Hotkeys", 1.0f);

            float curY = y + headerH + 3.0f;
            for (BindRow r : list) {
                int nameCol = r.active() ? 0xFFF4F6FF : 0xFF9CA3AF;
                Render2DHelper.drawScaledText(context, r.moduleName(), x + 7.0f, curY + 3.2f, nameCol, 0.74f, false);

                String k = "[" + r.keyName() + "]";
                float kw = Render2DHelper.getTextWidth(k, 0.68f);
                float pillW = kw + 6.0f;
                float bx = x + w - pillW - 6.0f;
                Render2DHelper.drawRoundedRectFloat(context, bx, curY + 1.5f, pillW, 11.5f, 3.0f, ColorUtils.withAlpha(c1, r.active() ? 48 : 22));
                Render2DHelper.drawScaledText(context, k, bx + 3.0f, curY + 3.5f, r.active() ? c1 : 0xFF9CA3AF, 0.68f, false);
                curY += rowH;
            }
        } finally {
            HudPanelRenderer.endWidget();
        }
    }

    private static String formatKey(int key) {
        return SystemModuleCard.getKeyName(key);
    }
}
