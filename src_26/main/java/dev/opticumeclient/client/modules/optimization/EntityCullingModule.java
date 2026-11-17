package dev.opticumeclient.client.modules.optimization;

import dev.opticumeclient.client.modules.Category;
import dev.opticumeclient.client.modules.Module;
import dev.opticumeclient.client.setting.NumberSetting;
import net.minecraft.client.Camera;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity; 

public class EntityCullingModule extends Module {
    private static EntityCullingModule INSTANCE;
    private final NumberSetting maxDistance = new NumberSetting("MaxDist", "Максимальная дистанция сущностей", 64.0, 16.0, 128.0, 8.0);

    public EntityCullingModule() {
        super("EntityCulling", "Отсечение рендера далеких сущностей и сущностей за спиной", Category.OPTIMIZATION);
        addSetting(maxDistance);
        INSTANCE = this;
        setEnabled(true);
    }

    public static EntityCullingModule getInstance() {
        return INSTANCE;
    }

    public boolean shouldCull(Entity entity) {
        if (!isEnabled() || mc.player == null) return false;
        if (entity == mc.player) return false;

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

        double maxDist = maxDistance.getValue();
        if (distSq > maxDist * maxDist) {
            return true;
        }

        // Behind-the-back culling for entities further than 8 blocks (8^2 = 64)
        if (distSq > 64.0) {
            // Analytical look vector from camera/player rotation angles (zero Vec3 allocations)
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
            // dot / sqrt(distSq) < -0.25  <=>  dot < 0 && dot^2 > 0.0625 * distSq
            if (dot < 0.0 && (dot * dot) > (0.0625 * distSq)) {
                return true;
            }
        }

        return false;
    }
}
