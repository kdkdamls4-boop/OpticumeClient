package dev.opticumeclient.client.modules.optimization;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Shared runtime coordinator queried by Stage 6 optimization mixins in MC 26.3.
 * Connects {@link EntityCullingModule} and {@link FrameSyncModule} cleanly without duplicating logic.
 */
public final class OptimizationCoordinator {
    private static volatile boolean worldEntityExtract = false;
    private static int particleThinCounter = 0;

    private OptimizationCoordinator() {
    }

    public static void setWorldEntityExtract(boolean active) {
        worldEntityExtract = active;
    }

    public static boolean isPoolingEntityStates() {
        if (!worldEntityExtract) return false;
        EntityCullingModule culling = EntityCullingModule.getInstance();
        return culling != null && culling.isRenderStatePoolEnabled();
    }

    public static boolean isPoolingBlockEntityStates() {
        EntityCullingModule culling = EntityCullingModule.getInstance();
        return culling != null && culling.isRenderStatePoolEnabled();
    }

    public static boolean shouldCullBlockEntity(BlockEntity blockEntity) {
        if (blockEntity == null) return false;
        EntityCullingModule culling = EntityCullingModule.getInstance();
        if (culling == null || !culling.isBlockEntityCullingEnabled()) return false;
        double[] cam = getCameraPos();
        if (cam == null) return false;
        double limit = culling.getBlockEntityDistance();
        return blockEntity.getBlockPos().distToCenterSqr(cam[0], cam[1], cam[2]) > limit * limit;
    }

    public static boolean shouldCullNameTag(Entity entity) {
        if (entity == null || entity instanceof Player) return false;
        EntityCullingModule culling = EntityCullingModule.getInstance();
        if (culling == null || !culling.isNameTagCullingEnabled()) return false;
        double[] cam = getCameraPos();
        if (cam == null) return false;
        double limit = culling.getNameTagDistance();
        return entity.distanceToSqr(cam[0], cam[1], cam[2]) > limit * limit;
    }

    public static boolean isSingleItemCopy() {
        EntityCullingModule culling = EntityCullingModule.getInstance();
        return culling != null && culling.isSingleItemCopyEnabled();
    }

    public static boolean shouldSkipRestingItemTick(ItemEntity item) {
        if (item == null || !item.level().isClientSide()) return false;
        EntityCullingModule culling = EntityCullingModule.getInstance();
        if (culling == null || !culling.isRestingItemsEnabled()) return false;
        if (!item.onGround() || item.getDeltaMovement().lengthSqr() > 1.0E-6) return false;
        double[] cam = getCameraPos();
        return cam != null && item.distanceToSqr(cam[0], cam[1], cam[2]) > (24.0 * 24.0);
    }

    public static boolean shouldCullParticle(double x, double y, double z) {
        FrameSyncModule frame = FrameSyncModule.getInstance();
        if (frame == null || !frame.isParticleCullingEnabled()) return false;
        double[] cam = getCameraPos();
        if (cam == null) return false;
        double limit = frame.getParticleDistance();
        return distSq(x, y, z, cam) > limit * limit;
    }

    public static boolean shouldThinParticle(double x, double y, double z) {
        FrameSyncModule frame = FrameSyncModule.getInstance();
        if (frame == null || !frame.isParticleThinningEnabled()) return false;
        double[] cam = getCameraPos();
        if (cam == null) return false;
        double d2 = distSq(x, y, z, cam);
        int n = ++particleThinCounter;
        if (d2 > 32.0 * 32.0) return (n & 3) != 0;
        if (d2 > 16.0 * 16.0) return (n & 1) != 0;
        return false;
    }

    public static boolean shouldSimplifyParticlePhysics(double x, double y, double z) {
        FrameSyncModule frame = FrameSyncModule.getInstance();
        if (frame == null || !frame.isSimpleParticlePhysicsEnabled()) return false;
        double[] cam = getCameraPos();
        if (cam == null) return false;
        return distSq(x, y, z, cam) > 16.0 * 16.0;
    }

    public static boolean isParticleLightCache() {
        FrameSyncModule frame = FrameSyncModule.getInstance();
        return frame != null && frame.isParticleLightCacheEnabled();
    }

    public static int limitBackgroundFramerate(int currentLimit) {
        FrameSyncModule frame = FrameSyncModule.getInstance();
        if (frame == null || !frame.isEnabled() || !frame.isSkipUnfocusedEnabled()) {
            return currentLimit;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.isWindowActive()) {
            return currentLimit;
        }
        return Math.min(currentLimit, frame.getBackgroundFpsLimit());
    }

    private static double[] getCameraPos() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null) return null;
        if (mc.gameRenderer != null) {
            Camera cam = mc.gameRenderer.mainCamera();
            if (cam != null && cam.isInitialized()) {
                return new double[]{cam.position().x, cam.position().y, cam.position().z};
            }
        }
        Entity view = mc.getCameraEntity();
        if (view == null) view = mc.player;
        if (view == null) return null;
        return new double[]{view.getX(), view.getEyeY(), view.getZ()};
    }

    private static double distSq(double x, double y, double z, double[] cam) {
        double dx = x - cam[0];
        double dy = y - cam[1];
        double dz = z - cam[2];
        return dx * dx + dy * dy + dz * dz;
    }
}
