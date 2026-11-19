package dev.opticumeclient.client.hud;

import dev.opticumeclient.client.modules.Category;
import dev.opticumeclient.client.modules.Module;
import dev.opticumeclient.client.render.Render2DHelper;
import dev.opticumeclient.client.render.TextureAssets;
import dev.opticumeclient.client.render.Theme;
import dev.opticumeclient.client.render.ThemeManager;
import dev.opticumeclient.client.setting.NumberSetting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * Draggable StaffList HUD widget detecting moderators and spectators on the server.
 */
public class StaffListHud extends Module {
    private static final String[] STAFF_KEYWORDS = {
            "admin", "moder", "helper", "curator", "owner", "dev", "staff", "support",
            "builder", "yt", "media", "модер", "хелпер", "админ", "куратор", "ст.", "гл."
    };

    private record StaffEntry(String name, String statusTag, int statusColor) {}

    private final NumberSetting posX = new NumberSetting("PosX", "X позиция", 380.0, 0.0, 1920.0, 1.0);
    private final NumberSetting posY = new NumberSetting("PosY", "Y позиция", 36.0, 0.0, 1080.0, 1.0);
    private final HudManager.DraggableWidget dragWidget;
    private final List<StaffEntry> cachedStaffList = new ArrayList<>();

    public StaffListHud() {
        super("StaffList", "Детект модераторов и спектаторов на сервере", Category.HUD);
        addSetting(posX);
        addSetting(posY);
        setEnabled(false);

        this.dragWidget = HudManager.getInstance().registerWidget(new HudManager.DraggableWidget(
                "StaffList",
                posX.getFloatValue(),
                posY.getFloatValue(),
                138.0f,
                54.0f,
                this::isEnabled,
                (newX, newY) -> {
                    posX.setValue((double) Math.round(newX));
                    posY.setValue((double) Math.round(newY));
                }
        ));
    }

    @Override
    public void onTick() {
        cachedStaffList.clear();
        if (mc.player == null || mc.getConnection() == null) return;

        Collection<PlayerInfo> tabList = mc.getConnection().getOnlinePlayers();
        for (PlayerInfo entry : tabList) {
            if (entry.getProfile() == null) continue;
            String name = entry.getProfile().name();
            if (name.equalsIgnoreCase(mc.player.getName().getString())) continue;

            String display = entry.getTabListDisplayName() != null ? entry.getTabListDisplayName().getString() : name;
            String lower = (name + " " + display).toLowerCase(Locale.ROOT);

            boolean isSpec = entry.getGameMode() == GameType.SPECTATOR;
            boolean keywordMatch = false;
            for (String kw : STAFF_KEYWORDS) {
                if (lower.contains(kw)) {
                    keywordMatch = true;
                    break;
                }
            }

            if (isSpec || keywordMatch) {
                boolean isNear = false;
                if (mc.level != null) {
                    for (Player p : mc.level.players()) {
                        if (p != mc.player && p.getName().getString().equalsIgnoreCase(name)) {
                            isNear = true;
                            break;
                        }
                    }
                }
                String tag = isSpec ? "[SPEC]" : (isNear ? "[NEAR]" : "[ONLINE]");
                int col = isSpec ? 0xFFFF5252 : (isNear ? 0xFFFBBF24 : 0xFF4ADE80);
                cachedStaffList.add(new StaffEntry(name, tag, col));
            }
        }
    }

    @Override
    public void onRender2D(GuiGraphicsExtractor context, float tickDelta) {
        if (!isEnabled() || mc.player == null) return;
        TextureAssets.ensureInitialized();

        Theme theme = ThemeManager.getInstance().getCurrentTheme();
        float w = 138.0f;
        float headerH = 19.0f;
        float rowH = 14.5f;
        int rows = Math.max(1, cachedStaffList.size());
        float h = headerH + rows * rowH + 5.0f;

        dragWidget.syncExternalPosition(posX.getFloatValue(), posY.getFloatValue());
        dragWidget.setBounds(w, h);
        float x = dragWidget.getX();
        float y = dragWidget.getY();

        HudPanelRenderer.beginWidget("StaffList");
        try {
            HudPanelRenderer.drawHeaderPanel(context, "StaffList", x, y, w, h, headerH, 6.0f,
                    TextureAssets.DESIRE_HUD_USER, "Staff (" + cachedStaffList.size() + ")", 1.0f);

            float curY = y + headerH + 2.5f;
            if (cachedStaffList.isEmpty()) {
                Render2DHelper.drawCircle(context, x + 10.0f, curY + 6.0f, 2.2f, 0xFF4ADE80);
                Render2DHelper.drawScaledText(context, "No staff detected", x + 16.0f, curY + 2.5f, theme.getMutedTextColor(), 0.72f, false);
                return;
            }

            for (StaffEntry staff : cachedStaffList) {
                String sName = staff.name();
                if (sName.length() > 12) sName = sName.substring(0, 11) + "..";
                Render2DHelper.drawCircle(context, x + 10.0f, curY + 6.0f, 2.5f, staff.statusColor());
                Render2DHelper.drawScaledText(context, sName, x + 16.0f, curY + 2.5f, 0xFFFFFFFF, 0.74f, false);

                float tagW = Render2DHelper.getTextWidth(staff.statusTag(), 0.66f);
                Render2DHelper.drawScaledText(context, staff.statusTag(), x + w - tagW - 6.0f, curY + 2.5f, staff.statusColor(), 0.66f, true);
                curY += rowH;
            }
        } finally {
            HudPanelRenderer.endWidget();
        }
    }
}
