package dev.optimusprime.client.modules.visuals;

import dev.optimusprime.client.friend.FriendManager;
import dev.optimusprime.client.modules.Category;
import dev.optimusprime.client.modules.Module;
import dev.optimusprime.client.render.ColorUtils;
import dev.optimusprime.client.render.Render2DHelper;
import dev.optimusprime.client.render.Render3DHelper;
import dev.optimusprime.client.render.ThemeManager;
import dev.optimusprime.client.setting.BooleanSetting;
import dev.optimusprime.client.setting.ModeSetting;
import dev.optimusprime.client.setting.NumberSetting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3x2fStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class NameTagsModule extends Module {
    private static NameTagsModule INSTANCE;

    private final ModeSetting mode = new ModeSetting("Mode", "Стиль плашек", "Liquid Glass", "Liquid Glass", "Minimal", "Nursultan");
    private final NumberSetting scale = new NumberSetting("Scale", "Масштаб плашки", 1.0, 0.5, 2.0, 0.05);
    private final BooleanSetting distanceScale = new BooleanSetting("DistanceScale", "Компенсация размера по глубине", true);
    private final BooleanSetting health = new BooleanSetting("Health", "Отображение HP числом", true);
    private final BooleanSetting healthBar = new BooleanSetting("HealthBar", "Полоска здоровья", true);
    private final BooleanSetting armor = new BooleanSetting("Armor", "Отображение слотов брони", true);
    private final BooleanSetting heldItems = new BooleanSetting("HeldItems", "Предметы в руках", true);
    private final BooleanSetting ping = new BooleanSetting("Ping", "Пинг в мс", true);
    private final BooleanSetting distance = new BooleanSetting("Distance", "Дистанция в метрах", true);
    private final BooleanSetting self = new BooleanSetting("Self", "Отображать над собой от 3-го лица", false);

    public NameTagsModule() {
        super("NameTags", "Плавающие Liquid Glass плашки с броней, HP, пингом и предметами игроков", Category.VISUALS);
        addSetting(mode);
        addSetting(scale);
        addSetting(distanceScale);
        addSetting(health);
        addSetting(healthBar);
        addSetting(armor);
        addSetting(heldItems);
        addSetting(ping);
        addSetting(distance);
        addSetting(self);
        INSTANCE = this;
        setEnabled(false);
    }

    public static NameTagsModule getInstance() {
        return INSTANCE;
    }

    @Override
    public String getDisplaySuffix() {
        return mode.getMode();
    }

    @Override
    public void onRender2D(GuiGraphicsExtractor context, float tickDelta) {
        if (!isEnabled() || mc.level == null || mc.player == null) return;

        List<Player> players = new ArrayList<>();
        for (Player p : mc.level.players()) {
            if (p == null || !p.isAlive()) continue;
            if (p == mc.player && (!self.isEnabled() || mc.options.getCameraType().isFirstPerson())) {
                continue;
            }
            players.add(p);
        }

        // Sort descending by distance from camera so nearer tags render on top of farther ones
        Vec3 camPos = (mc.gameRenderer != null && mc.gameRenderer.mainCamera() != null)
                ? mc.gameRenderer.mainCamera().position()
                : mc.player.position();

        players.sort(Comparator.comparingDouble((Player p) -> p.distanceToSqr(camPos)).reversed());

        int glowAlpha = (players.size() > 4) ? 0 : 36;
        for (Player p : players) {
            renderPlayerTag(context, p, tickDelta, glowAlpha);
        }
    }

    private void renderPlayerTag(GuiGraphicsExtractor context, Player player, float tickDelta, int glowAlpha) {
        Vec3 lerped = Render3DHelper.getLerpedPos(player, tickDelta);
        Vec3 tagWorldPos = lerped.add(0.0, player.getBbHeight() + 0.45, 0.0);

        float[] proj = Render3DHelper.worldToScreen(tagWorldPos);
        if (proj == null) return;

        float dist = Math.max(0.5f, proj[2]);
        float s = scale.getFloatValue();
        if (distanceScale.isEnabled()) {
            float distFactor = Math.clamp(7.5f / dist, 0.65f, 1.35f);
            s *= distFactor;
        }

        float sx = proj[0];
        float sy = proj[1];

        // Gather display info
        boolean isFriend = FriendManager.getInstance().isFriend(player);
        int nameColor = isFriend ? 0xFF22C55E : 0xFFFFFFFF;
        String name = player.getScoreboardName();

        float realHp = TargetHudModule.getRealHealth(player);
        int hp = (int) Math.ceil(realHp);
        float maxHp = Math.max(1.0f, Math.max(player.getMaxHealth(), realHp));
        float hpPct = Math.clamp(realHp / maxHp, 0.0f, 1.0f);
        int hpColor = ColorUtils.interpolateColor(0xFFFF2E54, 0xFF22C55E, hpPct);

        StringBuilder info = new StringBuilder();
        if (isFriend) info.append("[F] ");
        info.append(name);

        if (ping.isEnabled() && mc.getConnection() != null) {
            PlayerInfo pi = mc.getConnection().getPlayerInfo(player.getUUID());
            if (pi != null) {
                info.append(" ").append(Math.max(0, pi.getLatency())).append("ms");
            }
        }

        if (health.isEnabled()) {
            info.append(" ").append(hp).append("HP");
        }

        if (distance.isEnabled()) {
            int d = (int) mc.player.distanceTo(player);
            info.append(" [").append(d).append("m]");
        }

        String fullTag = info.toString();
        float textW = Render2DHelper.getTextWidth(fullTag, 0.85f);
        float plateW = textW + 16.0f;

        // Check equipment items to display
        List<ItemStack> items = new ArrayList<>();
        if (heldItems.isEnabled()) {
            ItemStack main = player.getMainHandItem();
            if (main != null && !main.isEmpty()) items.add(main);
        }
        if (armor.isEnabled()) {
            ItemStack helm = player.getItemBySlot(EquipmentSlot.HEAD);
            ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
            ItemStack legs = player.getItemBySlot(EquipmentSlot.LEGS);
            ItemStack boots = player.getItemBySlot(EquipmentSlot.FEET);
            if (helm != null && !helm.isEmpty()) items.add(helm);
            if (chest != null && !chest.isEmpty()) items.add(chest);
            if (legs != null && !legs.isEmpty()) items.add(legs);
            if (boots != null && !boots.isEmpty()) items.add(boots);
        }
        if (heldItems.isEnabled()) {
            ItemStack off = player.getOffhandItem();
            if (off != null && !off.isEmpty()) items.add(off);
        }

        float itemsW = items.size() * 18.0f;
        plateW = Math.max(plateW, itemsW + 12.0f);

        boolean hasItems = !items.isEmpty();
        boolean hasBar = healthBar.isEnabled();
        float plateH = 16.0f + (hasItems ? 18.0f : 0.0f) + (hasBar ? 4.0f : 0.0f);

        Matrix3x2fStack matrices = context.pose();
        matrices.pushMatrix();
        matrices.translate(sx, sy);
        if (s != 1.0f) {
            matrices.scale(s, s);
        }

        float px = -plateW * 0.5f;
        float py = -plateH;

        // 1. Draw Liquid Glass plate
        if (mode.is("Liquid Glass") || mode.is("Nursultan")) {
            Render2DHelper.drawLiquidGlass(context, px, py, plateW, plateH, 4.0f, 225, glowAlpha);
            int accentBorder = isFriend ? 0xFF22C55E : ThemeManager.getInstance().getAccentColor(0);
            Render2DHelper.drawRoundedOutline(context, px, py, plateW, plateH, 4.0f, 1.0f, ColorUtils.withAlpha(accentBorder, 110));
        } else {
            Render2DHelper.drawRoundedRect(context, px, py, plateW, plateH, 3.0f, 0xB0000000);
        }

        // 2. Draw Tag Text
        float textX = px + (plateW - textW) * 0.5f;
        float textY = py + 3.0f;
        Render2DHelper.drawScaledText(context, fullTag, textX, textY, nameColor, 0.85f, true);

        // 3. Draw Items (Armor + Hands)
        float curY = textY + 12.0f;
        if (hasItems) {
            float itemStartX = px + (plateW - itemsW) * 0.5f;
            for (int i = 0; i < items.size(); i++) {
                ItemStack stack = items.get(i);
                int ix = (int) Math.round(itemStartX + i * 18.0f);
                int iy = (int) Math.round(curY);
                context.item(stack, ix, iy);
                context.itemDecorations(mc.font, stack, ix, iy);
            }
            curY += 18.0f;
        }

        // 4. Draw Health Bar
        if (hasBar) {
            float barX = px + 4.0f;
            float barY = py + plateH - 3.5f;
            float barW = plateW - 8.0f;
            Render2DHelper.drawRoundedRect(context, barX, barY, barW, 2.2f, 1.0f, 0x35FFFFFF);
            Render2DHelper.drawRoundedRect(context, barX, barY, Math.max(2.0f, barW * hpPct), 2.2f, 1.0f, hpColor);
        }

        matrices.popMatrix();
    }
}
