package dev.opticumeclient.client.modules.visuals;

import dev.opticumeclient.client.modules.Category;
import dev.opticumeclient.client.modules.Module;
import dev.opticumeclient.client.modules.visuals.nametags.NameTagsRender;
import dev.opticumeclient.client.setting.BooleanSetting;
import dev.opticumeclient.client.setting.MultiBoxSetting;
import dev.opticumeclient.client.setting.NumberSetting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Ported from Desire NameTagsModule:
 * Modern floating name tags with Liquid Glass / dark surface, real HP + heart icon,
 * ping, armor/hand items with durability & over-enchant highlights, held item labels, and potion effects.
 */
public class NameTagsModule extends Module {
    private static NameTagsModule instance;

    public final MultiBoxSetting rendering = new MultiBoxSetting(
            "Отображать",
            "Кого отображать на плашках NameTags",
            new String[]{"Игроков", "Монстров", "Друзей", "Животных", "Себя", "Жителей", "Голых", "Предметы"},
            "Игроков", "Друзей", "Голых", "Предметы"
    );
    public final BooleanSetting liquidGlass = new BooleanSetting("Жидкое стекло", "Эффект размытого стекла под плашками", true);
    public final BooleanSetting viewHead = new BooleanSetting("Отображать голову", "Иконка/голова перед ником", true);
    public final BooleanSetting viewEffects = new BooleanSetting("Отображать эффекты", "Список активных зелий под ником", true);
    public final BooleanSetting viewArmor = new BooleanSetting("Отображать броню", "Броня и предметы в руках над ником", true);
    public final BooleanSetting viewEnchants = new BooleanSetting("Отображать зачарования", "Сокращённые зачарования над броней", true)
            .visibleWhen(viewArmor::isEnabled);
    public final BooleanSetting viewItem = new BooleanSetting("Отображать предметы", "Названия предметов в руках под ником", false);
    public final BooleanSetting viewDisplayName = new BooleanSetting("Отображать название предмета", "Использовать кастомные названия предметов", false)
            .visibleWhen(viewItem::isEnabled);
    public final NumberSetting tagScale = new NumberSetting("Масштаб", "Общий размер плашек", 1.0, 0.7, 1.5, 0.05);

    private final NameTagsRender renderer = new NameTagsRender(this);

    public NameTagsModule() {
        super("NameTags", "Современные плашки ников с HP, бронёй, зачарами и эффектами (Desire)", Category.VISUALS);
        instance = this;
        addSetting(rendering);
        addSetting(liquidGlass);
        addSetting(viewHead);
        addSetting(viewEffects);
        addSetting(viewArmor);
        addSetting(viewEnchants);
        addSetting(viewItem);
        addSetting(viewDisplayName);
        addSetting(tagScale);
    }

    public static NameTagsModule getInstance() {
        return instance;
    }

    public boolean shouldHideVanillaTag(LivingEntity entity) {
        if (!isEnabled() || entity == null) return false;
        if (entity instanceof Player) return true;
        return renderer.isEntityTarget(mc, entity);
    }

    @Override
    public void onDisable() {
        super.onDisable();
        renderer.clear();
    }

    @Override
    public void onRender2D(GuiGraphicsExtractor context, float tickDelta) {
        if (!isEnabled()) return;
        renderer.onRender(context, tickDelta);
    }
}
