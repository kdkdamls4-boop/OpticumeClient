package dev.opticumeclient.client.hud;

import dev.opticumeclient.client.modules.Category;
import dev.opticumeclient.client.modules.Module;
import dev.opticumeclient.client.modules.other.AssistModule;
import dev.opticumeclient.client.render.ColorUtils;
import dev.opticumeclient.client.render.Render2DHelper;
import dev.opticumeclient.client.render.TextureAssets;
import dev.opticumeclient.client.render.Theme;
import dev.opticumeclient.client.render.ThemeManager;
import dev.opticumeclient.client.setting.BooleanSetting;
import dev.opticumeclient.client.setting.NumberSetting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;

import java.util.List;
import java.util.Locale;

/**
 * Displays active server-item keybinds from AssistModule with live item counts,
 * cooldown progress indicators, and customizable HUD position/scale.
 */
public class ServerKeybindsHud extends Module {
    private static ServerKeybindsHud instance;

    private final BooleanSetting showEmpty = new BooleanSetting("Show Missing", "Показывать бинды с 0 предметов в инвентаре", true);
    private final NumberSetting posX = new NumberSetting("PosX", "X позиция", 12.0, 0.0, 1920.0, 1.0);
    private final NumberSetting posY = new NumberSetting("PosY", "Y позиция", 160.0, 0.0, 1080.0, 1.0);
    private final HudManager.DraggableWidget dragWidget;

    public ServerKeybindsHud() {
        super("ServerKeybinds", "HUD биндов донат-предметов (Трапка, Пласт, Дезор, Стан)", Category.HUD);
        instance = this;
        addSetting(showEmpty);
        addSetting(posX);
        addSetting(posY);
        setEnabled(false);

        this.dragWidget = HudManager.getInstance().registerWidget(new HudManager.DraggableWidget(
                "ServerKeybinds",
                posX.getFloatValue(),
                posY.getFloatValue(),
                138.0f,
                52.0f,
                this::isEnabled,
                (nx, ny) -> {
                    posX.setValue((double) Math.round(nx));
                    posY.setValue((double) Math.round(ny));
                }
        ));
    }

    public static ServerKeybindsHud getInstance() {
        return instance;
    }

    @Override
    public void onRender2D(GuiGraphicsExtractor context, float tickDelta) {
        if (!isEnabled() || mc.player == null) return;
        TextureAssets.ensureInitialized();

        AssistModule assist = AssistModule.getInstance();
        List<AssistModule.BoundSummary> all = assist != null ? assist.getBoundSummaries() : List.of();
        List<AssistModule.BoundSummary> visible = all.stream()
                .filter(b -> showEmpty.isEnabled() || b.count() > 0)
                .toList();

        boolean preview = mc.gui != null && mc.gui.screen() instanceof ChatScreen;
        if (visible.isEmpty() && !preview) {
            return;
        }

        Theme theme = ThemeManager.getInstance().getCurrentTheme();
        int accent = theme.getPrimaryColor();
        float w = 144.0f;
        float headerH = 19.0f;
        float rowH = 15.0f;
        int rows = Math.max(1, visible.size());
        float h = headerH + rows * rowH + 5.0f;

        dragWidget.syncExternalPosition(posX.getFloatValue(), posY.getFloatValue());
        dragWidget.setBounds(w, h);
        float x = dragWidget.getX();
        float y = dragWidget.getY();

        HudPanelRenderer.beginWidget("ServerKeybinds");
        try {
            HudPanelRenderer.drawHeaderPanel(context, "ServerKeybinds", x, y, w, h, headerH, 6.0f,
                    TextureAssets.DESIRE_GUI_KEYBOARD_BIG, "Server Binds", 1.0f);

            float curY = y + headerH + 3.0f;
            if (visible.isEmpty()) {
                Render2DHelper.drawScaledText(context, "Bind items in Assist", x + 8.0f, curY + 2.5f,
                        theme.getMutedTextColor(), 0.72f, false);
                return;
            }

            for (AssistModule.BoundSummary b : visible) {
                if (!b.icon().isEmpty()) {
                    var pose = context.pose();
                    pose.pushMatrix();
                    pose.translate(x + 7.0f, curY + 1.0f);
                    pose.scale(0.68f, 0.68f);
                    context.item(b.icon(), 0, 0);
                    pose.popMatrix();
                } else {
                    Render2DHelper.drawCircle(context, x + 12.0f, curY + 6.5f, 2.5f, ColorUtils.withAlpha(accent, 190));
                }

                String name = Render2DHelper.trimToWidth(b.name(), 0.72f, w - 74.0f);
                int textCol = b.count() > 0 ? 0xFFF1F5F9 : 0xFF7B8498;
                Render2DHelper.drawScaledText(context, name, x + 21.0f, curY + 3.0f, textCol, 0.72f, false);

                String rightTag = "[" + b.keyLabel().toUpperCase(Locale.ROOT) + "] x" + b.count();
                int tagCol = b.cooldownSec() > 0.01f ? 0xFFF87171 : (b.count() > 0 ? accent : 0xFF64748B);
                float tw = Render2DHelper.getTextWidth(rightTag, 0.66f);
                Render2DHelper.drawScaledText(context, rightTag, x + w - tw - 6.0f, curY + 3.2f, tagCol, 0.66f, true);
                curY += rowH;
            }
        } finally {
            HudPanelRenderer.endWidget();
        }
    }
}
