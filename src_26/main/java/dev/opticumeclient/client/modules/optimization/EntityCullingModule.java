package dev.opticumeclient.client.modules.optimization;

import dev.opticumeclient.client.modules.Category;
import dev.opticumeclient.client.modules.Module;
import dev.opticumeclient.client.setting.BooleanSetting;
import dev.opticumeclient.client.setting.NumberSetting;
import net.minecraft.client.Camera;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;

public class EntityCullingModule extends Module {
    private static EntityCullingModule INSTANCE;
    private final NumberSetting maxDistance = new NumberSetting("MaxDist", "Максимальная дистанция сущностей", 64.0, 16.0, 128.0, 8.0);
    private final BooleanSetting keepPlayers = new BooleanSetting("Keep Players", "Не скрывать игроков в пределах видимости", true);
    private final BooleanSetting itemCulling = new BooleanSetting("Item Culling", "Отсечение далёких лежащих предметов", true);
    private final NumberSetting itemDistance = new NumberSetting("Item Distance", "Дистанция видимости предметов", 28.0, 8.0, 64.0, 4.0);
    private final BooleanSetting blockEntityCulling = new BooleanSetting("BlockEntity Culling", "Отсечение далёких блок-сущностей (сундуки, таблички)", true);
    private final NumberSetting blockEntityDistance = new NumberSetting("BlockEntity Dist", "Дистанция блок-сущностей", 36.0, 8.0, 96.0, 4.0);
    private final BooleanSetting nameTagCulling = new BooleanSetting("NameTag Culling", "Скрытие далёких ванильных никнеймов мобов", true);
    private final NumberSetting nameTagDistance = new NumberSetting("NameTag Dist", "Дистанция никнеймов мобов", 24.0, 8.0, 64.0, 4.0);
    private final BooleanSetting renderStatePool = new BooleanSetting("Render State Pool", "Переиспользование Entity/BlockEntity RenderState без аллокаций", true);
    private final BooleanSetting singleItemCopy = new BooleanSetting("Single Item Copy", "Отрисовывать 1 модель в стопке предметов", false);
    private final BooleanSetting restingItems = new BooleanSetting("Resting Items", "Редкий тик далёких неподвижных предметов", true);

    public EntityCullingModule() {
        super("EntityCulling", "Отсечение рендера далеких сущностей, блок-сущностей и пул состояний", Category.OPTIMIZATION);
        itemDistance.visibleWhen(itemCulling::isEnabled);
        blockEntityDistance.visibleWhen(blockEntityCulling::isEnabled);
        nameTagDistance.visibleWhen(nameTagCulling::isEnabled);
        addSetting(maxDistance);
        addSetting(keepPlayers);
        addSetting(itemCulling);
        addSetting(itemDistance);
        addSetting(blockEntityCulling);
        addSetting(blockEntityDistance);
        addSetting(nameTagCulling);
        addSetting(nameTagDistance);
        addSetting(renderStatePool);
        addSetting(singleItemCopy);
        addSetting(restingItems);
        INSTANCE = this;
        setEnabled(true);
    }

    public static EntityCullingModule getInstance() {
        return INSTANCE;
    }

    public boolean shouldCull(Entity entity) {
        if (!isEnabled() || mc.player == null) return false;
        if (entity == mc.player) return false;
        if (entity instanceof Player && keepPlayers.isEnabled()) return false;

        Camera cam = (mc.gameRenderer != null) ? mc.gameRenderer.mainCamera() : null;
        if (cam != null && cam.isInitialized() && entity == cam.entity()) {
            return false;
        }

        double px = (cam != null && cam.isInitialized()) ? cam.position().x : mc.player.getX();
        double py = (cam != null && cam.isInitialized()) ? cam.position().y : mc.player.getY();
        double pz = (cam != null && cam.isInitialized()) ? cam.position().z : mc.player.getZ();

        double dx = entity.getX() - px;
        double dy = entity.getY() - py;
        double dz = entity.getZ() - pz;
        double distSq = dx * dx + dy * dy + dz * dz;

        if (entity instanceof ItemEntity) {
            if (!itemCulling.isEnabled()) return false;
            double itemMax = itemDistance.getValue();
            return distSq > itemMax * itemMax;
        }

        double maxDist = maxDistance.getValue();
        if (distSq > maxDist * maxDist) {
            return true;
        }

        // Behind-the-back culling for entities further than 8 blocks (8^2 = 64)
        if (distSq > 64.0) {
            float xRot = (cam != null && cam.isInitialized()) ? cam.xRot() : mc.player.getXRot();
            float yRot = (cam != null && cam.isInitialized()) ? cam.yRot() : mc.player.getYRot();
            float f = xRot * 0.017453292F;
            float g = -yRot * 0.017453292F;
            float h = Mth.cos(g);
            float i = Mth.sin(g);
            float j = Mth.cos(f);
            float k = Mth.sin(f);
            double lookX = i * j;
            double lookY = -k;
            double lookZ = h * j;

            double dot = dx * lookX + dy * lookY + dz * lookZ;
            if (dot < 0.0 && (dot * dot) > (0.0625 * distSq)) {
                return true;
            }
        }

        return false;
    }

    public boolean isBlockEntityCullingEnabled() {
        return isEnabled() && blockEntityCulling.isEnabled();
    }

    public double getBlockEntityDistance() {
        return blockEntityDistance.getValue();
    }

    public boolean isNameTagCullingEnabled() {
        return isEnabled() && nameTagCulling.isEnabled();
    }

    public double getNameTagDistance() {
        return nameTagDistance.getValue();
    }

    public boolean isRenderStatePoolEnabled() {
        return isEnabled() && renderStatePool.isEnabled();
    }

    public boolean isSingleItemCopyEnabled() {
        return isEnabled() && singleItemCopy.isEnabled();
    }

    public boolean isRestingItemsEnabled() {
        return isEnabled() && restingItems.isEnabled();
    }
}
