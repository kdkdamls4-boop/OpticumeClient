package dev.opticumeclient.client.modules.visuals;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.opticumeclient.client.friend.FriendManager;
import dev.opticumeclient.client.modules.Category;
import dev.opticumeclient.client.modules.Module;
import dev.opticumeclient.client.render.ColorUtils;
import dev.opticumeclient.client.render.Render3DHelper;
import dev.opticumeclient.client.render.ThemeManager;
import dev.opticumeclient.client.setting.BooleanSetting;
import dev.opticumeclient.client.setting.ColorSetting;
import dev.opticumeclient.client.setting.ModeSetting;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

public class ChamsModule extends Module {
    private static ChamsModule INSTANCE;

    private final ModeSetting mode = new ModeSetting("Mode", "Стиль подсветки сквозь стены", "Outline Glow", "Outline Glow", "Colored Tint", "Wireframe 3D", "Combined");
    private final BooleanSetting players = new BooleanSetting("Players", "Применять к игрокам", true);
    private final BooleanSetting friends = new BooleanSetting("Friends", "Применять к друзьям", true);
    private final BooleanSetting mobs = new BooleanSetting("Mobs", "Применять к мобам", false);
    private final BooleanSetting customColor = new BooleanSetting("CustomColor", "Пользовательский цвет", false);
    private final ColorSetting color = new ColorSetting("Color", "Цвет подсветки", 0xFF00F5FF);

    public ChamsModule() {
        super("Chams", "Подсветка силуэтов сущностей сквозь стены (Vulkan Glow Outline & Tint)", Category.VISUALS);
        addSetting(mode);
        addSetting(players);
        addSetting(friends);
        addSetting(mobs);
        addSetting(customColor);
        addSetting(color);
        INSTANCE = this;
        setEnabled(true);
    }

    public static ChamsModule getInstance() {
        return INSTANCE;
    }

    @Override
    public String getDisplaySuffix() {
        return mode.getMode();
    }

    public boolean shouldApply(LivingEntity entity) {
        if (!isEnabled() || entity == null || entity == mc.player || !entity.isAlive() || entity.isRemoved()) {
            return false;
        }
        if (entity instanceof Player player) {
            if (!players.isEnabled()) return false;
            boolean isFriend = FriendManager.getInstance().isFriend(player);
            return isFriend ? friends.isEnabled() : true;
        }
        if (mobs.isEnabled() && entity instanceof Enemy) {
            return true;
        }
        return false;
    }

    public boolean isOutlineMode() {
        return mode.is("Outline Glow") || mode.is("Combined");
    }

    public boolean isTintActive() {
        return mode.is("Colored Tint") || mode.is("Combined");
    }

    public int getColorForEntity(LivingEntity entity) {
        if (entity instanceof Player player && FriendManager.getInstance().isFriend(player)) {
            return 0xFF22C55E;
        }
        if (customColor.isEnabled()) {
            return color.getColor();
        }
        return ThemeManager.getInstance().getAccentColor(0);
    }

    public int getTintColor(LivingEntityRenderState state) {
        if (customColor.isEnabled()) {
            return ColorUtils.withAlpha(color.getColor(), 180);
        }
        return ColorUtils.withAlpha(ThemeManager.getInstance().getAccentColor(0), 180);
    }

    @Override
    public void onRender3D(PoseStack matrices, float tickDelta) {
        if (!isEnabled() || mc.level == null || mc.player == null) return;
        if (!mode.is("Wireframe 3D") && !mode.is("Combined")) return;

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity living) || !shouldApply(living)) continue;
            int c = getColorForEntity(living);
            AABB box = Render3DHelper.getLerpedBox(living, tickDelta);
            Render3DHelper.drawBoxColor(box, c, 1.4f, true);
        }
    }
}
