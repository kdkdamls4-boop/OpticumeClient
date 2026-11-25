package dev.opticumeclient.client.modules.visuals;

import com.mojang.blaze3d.pipeline.RenderTarget;
import dev.opticumeclient.client.friend.FriendManager;
import dev.opticumeclient.client.modules.Category;
import dev.opticumeclient.client.modules.Module;
import dev.opticumeclient.client.modules.visuals.targetesp.EspBloomPass;
import dev.opticumeclient.client.render.ThemeManager;
import dev.opticumeclient.client.setting.BooleanSetting;
import dev.opticumeclient.client.setting.ColorSetting;
import dev.opticumeclient.client.setting.MultiBoxSetting;
import dev.opticumeclient.client.setting.NumberSetting;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;

/**
 * Ported from Desire GlowEspModule (replaces legacy Chams):
 * Bloom outline + silhouette glow through walls using RenderPearl EspBloomPass.
 */
public class GlowEspModule extends Module {
    private static GlowEspModule instance;

    private final MultiBoxSetting targets = new MultiBoxSetting(
            "Цели",
            "Какие сущности подсвечивать",
            new String[]{"Игроки", "Мобы", "Себя", "Друзья"},
            "Игроки", "Друзья"
    );
    private final BooleanSetting themeColor = new BooleanSetting("Цвет от темы", "Использовать акцент текущей темы", true);
    private final ColorSetting color = new ColorSetting("Цвет", "Цвет свечения и обводки", 0xFFFF4678, false)
            .visibleWhen(() -> !themeColor.isEnabled());
    private final BooleanSetting friendColor = new BooleanSetting("Свой цвет друзьям", "Выделять друзей отдельным цветом", true);
    private final ColorSetting friendsColor = new ColorSetting("Цвет друзей", "Цвет свечения для друзей", 0xFF55FF82, false)
            .visibleWhen(friendColor::isEnabled);
    private final NumberSetting radius = new NumberSetting("Размытие", "Радиус свечения bloom", 6.0, 1.0, 12.0, 0.5);
    private final NumberSetting intensity = new NumberSetting("Яркость", "Интенсивность свечения", 1.4, 0.3, 3.0, 0.05);
    private final NumberSetting inner = new NumberSetting("Подсветка модели", "Заливка внутри силуэта", 0.35, 0.0, 1.0, 0.05);
    private final BooleanSetting outline = new BooleanSetting("Обводка", "Рисовать чёткий внешний контур", true);
    private final NumberSetting outlineWidth = new NumberSetting("Толщина обводки", "Ширина контура в пикселях", 1.5, 0.5, 4.0, 0.5)
            .visibleWhen(outline::isEnabled);

    public GlowEspModule() {
        super("GlowEsp", "Неоновое bloom-свечение и контур сущностей сквозь стены (Desire)", Category.VISUALS);
        instance = this;
        addSetting(targets);
        addSetting(themeColor);
        addSetting(color);
        addSetting(friendColor);
        addSetting(friendsColor);
        addSetting(radius);
        addSetting(intensity);
        addSetting(inner);
        addSetting(outline);
        addSetting(outlineWidth);
    }

    public static GlowEspModule getInstance() {
        return instance;
    }

    public boolean replacesOutline() {
        return isEnabled() && mc.player != null;
    }

    public boolean shouldGlow(Entity entity) {
        if (!isEnabled() || mc.player == null || !(entity instanceof LivingEntity living) || !living.isAlive()) {
            return false;
        }
        if (entity == mc.player) {
            return targets.isEnabled("Себя");
        }
        if (entity instanceof Player player) {
            boolean friend = FriendManager.getInstance().isFriend(player);
            return friend ? targets.isEnabled("Друзья") : targets.isEnabled("Игроки");
        }
        return entity instanceof Mob && targets.isEnabled("Мобы");
    }

    public int color(Entity entity) {
        if (friendColor.isEnabled() && entity instanceof Player player && entity != mc.player
                && FriendManager.getInstance().isFriend(player)) {
            return friendsColor.getColor() & 0x00FFFFFF;
        }
        int base = themeColor.isEnabled() ? ThemeManager.getInstance().getAccentColor(0) : color.getColor();
        return base & 0x00FFFFFF;
    }

    public void composite(RenderTarget silhouettes, RenderTarget main) {
        int glow = themeColor.isEnabled() ? ThemeManager.getInstance().getAccentColor(0) : color.getColor();
        int outlineColor = outline.isEnabled() ? (0xFF000000 | (glow & 0x00FFFFFF)) : 0;
        EspBloomPass.render(
                silhouettes,
                main,
                glow,
                radius.getFloatValue(),
                intensity.getFloatValue(),
                1.0f,
                1.0f,
                inner.getFloatValue(),
                outlineColor,
                outline.isEnabled() ? outlineWidth.getFloatValue() : 0.0f
        );
    }
}
