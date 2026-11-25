package dev.opticumeclient.client.modules.movement;

import dev.opticumeclient.client.modules.Category;
import dev.opticumeclient.client.modules.Module;
import dev.opticumeclient.client.setting.BooleanSetting;
import dev.opticumeclient.client.setting.ModeSetting;
import dev.opticumeclient.client.setting.NumberSetting;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;

/**
 * Water & lava surface traversal module:
 * - Solid: stabilizes vertical motion right on the fluid surface (plus fluid buoyancy lift).
 * - Dolphin: rapid surface swim hops with horizontal speed boost.
 * - Bounce: rhythmic low-profile surface bounces.
 */
public class JesusModule extends Module {
    private static JesusModule INSTANCE;

    private final ModeSetting mode = new ModeSetting("Mode", "Режим ходьбы по воде/лаве", "Solid", "Solid", "Dolphin", "Bounce");
    private final NumberSetting speed = new NumberSetting("Speed", "Горизонтальная скорость на воде", 1.05, 0.5, 2.5, 0.05);
    private final BooleanSetting lava = new BooleanSetting("Lava", "Работать также на поверхности лавы", true);
    private final BooleanSetting sneakSink = new BooleanSetting("SneakSink", "Погружаться при удержании Shift", true);

    public JesusModule() {
        super("Jesus", "Передвижение по поверхности воды и лавы (Solid / Dolphin / Bounce)", Category.MOVEMENT);
        INSTANCE = this;
        addSetting(mode);
        addSetting(speed);
        addSetting(lava);
        addSetting(sneakSink);
    }

    public static JesusModule getInstance() {
        return INSTANCE;
    }

    @Override
    public String getDisplaySuffix() {
        return mode.getMode();
    }

    public boolean shouldSolidifyFluid(BlockPos pos, FluidState fluidState) {
        if (!isEnabled() || !mode.is("Solid") || mc.player == null || fluidState == null || fluidState.isEmpty()) {
            return false;
        }
        if (sneakSink.isEnabled() && mc.player.isShiftKeyDown()) return false;
        if (mc.player.fallDistance > 3.0f) return false;
        if (!lava.isEnabled() && fluidState.is(net.minecraft.tags.FluidTags.LAVA)) return false;
        return mc.player.getY() >= pos.getY() + 0.85 && !mc.player.isUnderWater();
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.level == null) return;
        if (sneakSink.isEnabled() && mc.player.isShiftKeyDown()) return;

        boolean inWater = mc.player.isInWater();
        boolean inLava = lava.isEnabled() && mc.player.isInLava();
        if (!inWater && !inLava) {
            // Check standing right above fluid in Solid mode
            if (mode.is("Solid")) {
                BlockPos below = BlockPos.containing(mc.player.getX(), mc.player.getY() - 0.12, mc.player.getZ());
                BlockState st = mc.level.getBlockState(below);
                FluidState fs = st.getFluidState();
                if (!fs.isEmpty() && (lava.isEnabled() || !fs.is(net.minecraft.tags.FluidTags.LAVA))) {
                    Vec3 vel = mc.player.getDeltaMovement();
                    if (vel.y < 0.0) {
                        mc.player.setDeltaMovement(vel.x * speed.getValue(), 0.0, vel.z * speed.getValue());
                        mc.player.setOnGround(true);
                    }
                }
            }
            return;
        }

        Vec3 vel = mc.player.getDeltaMovement();
        switch (mode.getMode()) {
            case "Solid" -> {
                if (mc.player.isUnderWater()) {
                    mc.player.setDeltaMovement(vel.x, 0.12, vel.z);
                } else {
                    mc.player.setDeltaMovement(vel.x * Math.min(1.18, speed.getValue()), 0.08, vel.z * Math.min(1.18, speed.getValue()));
                }
            }
            case "Dolphin" -> {
                double vy = mc.player.horizontalCollision ? 0.28 : 0.14;
                double boost = Math.min(1.35, speed.getValue());
                mc.player.setDeltaMovement(vel.x * boost, vy, vel.z * boost);
            }
            case "Bounce" -> {
                double vy = mc.player.tickCount % 3 == 0 ? 0.24 : 0.06;
                mc.player.setDeltaMovement(vel.x * Math.min(1.25, speed.getValue()), vy, vel.z * Math.min(1.25, speed.getValue()));
            }
        }
    }
}
