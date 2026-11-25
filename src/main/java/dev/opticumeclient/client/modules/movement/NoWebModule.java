package dev.opticumeclient.client.modules.movement;

import dev.opticumeclient.client.modules.Category;
import dev.opticumeclient.client.modules.Module;
import dev.opticumeclient.client.setting.BooleanSetting;
import dev.opticumeclient.client.setting.ModeSetting;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Ported from Desire & Skycore (NoWebModule):
 * Cancels or reduces slowdown inside Cobwebs and Sweet Berry Bushes.
 */
public class NoWebModule extends Module {
    private static NoWebModule instance;

    private final ModeSetting modeSetting;
    private final BooleanSetting sweetBerriesSetting;

    public NoWebModule() {
        super("NoWeb", "Отключает замедление в паутине и кустах сладких ягод", Category.MOVEMENT);
        instance = this;

        this.modeSetting = addSetting(new ModeSetting(
                "Mode",
                "Режим обхода паутины",
                "Vanilla",
                "Vanilla", "Grim / FastFall"
        ));
        this.sweetBerriesSetting = addSetting(new BooleanSetting(
                "Sweet Berries",
                "Отключать замедление также в кустах сладких ягод",
                true
        ));
    }

    public static NoWebModule getInstance() {
        return instance;
    }

    @Override
    public String getDisplaySuffix() {
        return modeSetting.getMode();
    }

    @Override
    public void onTick() {
        if (!isGrimMode() || mc.player == null || mc.level == null) return;
        if (!isPlayerInCobweb()) return;

        Vec3 vel = mc.player.getDeltaMovement();
        boolean jumping = mc.options != null && mc.options.keyJump.isDown();
        boolean sneaking = mc.player.isShiftKeyDown();

        double vy = vel.y;
        if (sneaking) {
            vy = Math.min(vy, -0.35);
        } else if (!jumping && !mc.player.onGround() && vy <= 0.0) {
            // Fast-fall through cobweb while keeping motion inside Grim's vertical threshold
            vy = Math.min(vy - 0.14, -0.22);
        }

        double vx = vel.x;
        double vz = vel.z;
        double horizSq = vx * vx + vz * vz;
        if (horizSq > 1.0E-5 && horizSq < 0.048) {
            double boost = 1.65;
            double maxHoriz = 0.21;
            double nx = vx * boost;
            double nz = vz * boost;
            double len = Math.hypot(nx, nz);
            if (len > maxHoriz) {
                nx = nx / len * maxHoriz;
                nz = nz / len * maxHoriz;
            }
            vx = nx;
            vz = nz;
        }
        mc.player.setDeltaMovement(vx, vy, vz);
    }

    private boolean isPlayerInCobweb() {
        AABB box = mc.player.getBoundingBox().deflate(0.01);
        int minX = Mth.floor(box.minX);
        int maxX = Mth.floor(box.maxX);
        int minY = Mth.floor(box.minY);
        int maxY = Mth.floor(box.maxY);
        int minZ = Mth.floor(box.minZ);
        int maxZ = Mth.floor(box.maxZ);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    pos.set(x, y, z);
                    if (mc.level.getBlockState(pos).is(Blocks.COBWEB)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public boolean shouldCancelStuck(BlockState state) {
        if (!isEnabled() || state == null) return false;
        if (state.is(Blocks.COBWEB)) {
            return modeSetting.isMode("Vanilla");
        }
        if (sweetBerriesSetting.isEnabled() && state.is(Blocks.SWEET_BERRY_BUSH)) {
            return true;
        }
        return false;
    }

    public boolean isGrimMode() {
        return isEnabled() && modeSetting.isMode("Grim / FastFall");
    }
}
