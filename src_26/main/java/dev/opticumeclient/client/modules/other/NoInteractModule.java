package dev.opticumeclient.client.modules.other;

import dev.opticumeclient.client.modules.Category;
import dev.opticumeclient.client.modules.Module;
import dev.opticumeclient.client.setting.BooleanSetting;
import dev.opticumeclient.client.setting.MultiBoxSetting;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.CraftingTableBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.EnchantingTableBlock;
import net.minecraft.world.level.block.EnderChestBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Blocks accidental right-click interactions with containers, doors, trapdoors,
 * workstations, or item frames during PvP or building.
 */
public class NoInteractModule extends Module {
    private static NoInteractModule INSTANCE;

    private static final String[] TARGET_OPTIONS = {
            "Containers", "Workstations", "Doors/Gates", "ItemFrames"
    };

    private final MultiBoxSetting targets = new MultiBoxSetting(
            "Targets",
            "Какие объекты блокировать от случайного ПКМ",
            TARGET_OPTIONS,
            "Containers", "Workstations", "Doors/Gates", "ItemFrames"
    );
    private final BooleanSetting allowWhenSneaking = new BooleanSetting(
            "AllowSneak",
            "Разрешать взаимодействие при зажатом Shift",
            true
    );

    public NoInteractModule() {
        super("NoInteract", "Блокирует случайное открытие сундуков, печек, калиток и рамок в бою", Category.OTHER);
        INSTANCE = this;
        addSetting(targets);
        addSetting(allowWhenSneaking);
    }

    public static NoInteractModule getInstance() {
        return INSTANCE;
    }

    public boolean shouldBlockBlockInteract(BlockPos pos) {
        if (!isEnabled() || mc.player == null || mc.level == null || pos == null) return false;
        if (allowWhenSneaking.isEnabled() && mc.player.isShiftKeyDown()) return false;

        BlockState state = mc.level.getBlockState(pos);
        Block block = state.getBlock();

        if (targets.isEnabled("Containers") && (
                block instanceof ChestBlock
                        || block instanceof EnderChestBlock
                        || block instanceof BarrelBlock
                        || block instanceof ShulkerBoxBlock
                        || block instanceof HopperBlock
                        || block instanceof AbstractFurnaceBlock)) {
            return true;
        }
        if (targets.isEnabled("Workstations") && (
                block instanceof CraftingTableBlock
                        || block instanceof AnvilBlock
                        || block instanceof EnchantingTableBlock)) {
            return true;
        }
        if (targets.isEnabled("Doors/Gates") && (
                block instanceof DoorBlock
                        || block instanceof TrapDoorBlock
                        || block instanceof FenceGateBlock)) {
            return true;
        }
        return false;
    }

    public boolean shouldBlockEntityInteract(Entity entity) {
        if (!isEnabled() || mc.player == null || entity == null) return false;
        if (allowWhenSneaking.isEnabled() && mc.player.isShiftKeyDown()) return false;
        if (targets.isEnabled("ItemFrames") && (entity instanceof ItemFrame || entity instanceof ArmorStand)) {
            return true;
        }
        return false;
    }
}
