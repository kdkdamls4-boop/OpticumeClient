package dev.opticumeclient.client.modules.movement;

import dev.opticumeclient.client.modules.Category;
import dev.opticumeclient.client.modules.Module;
import dev.opticumeclient.client.setting.BooleanSetting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Ported from Desire & Skycore (AutoToolModule):
 * Automatically selects the fastest tool in the hotbar when mining a block,
 * with durability protection and optional slot restore after mining stops.
 */
public class AutoToolModule extends Module {
    private static AutoToolModule instance;

    private final BooleanSetting switchBackSetting;
    private final BooleanSetting saveDurabilitySetting;

    private int previousSlot = -1;
    private boolean switched = false;

    public AutoToolModule() {
        super("AutoTool", "Автоматический выбор лучшего инструмента в хотбаре при копании блоков", Category.MOVEMENT);
        instance = this;

        this.switchBackSetting = addSetting(new BooleanSetting(
                "Switch Back",
                "Возвращать предыдущий слот после окончания ломания блока",
                true
        ));
        this.saveDurabilitySetting = addSetting(new BooleanSetting(
                "Save Durability",
                "Не брать инструмент, у которого осталось менее 4 единиц прочности",
                true
        ));
    }

    public static AutoToolModule getInstance() {
        return instance;
    }

    @Override
    public void onDisable() {
        restoreSlotIfNeeded();
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.level == null || mc.gameMode == null) {
            previousSlot = -1;
            switched = false;
            return;
        }

        boolean attackingBlock = mc.options.keyAttack.isDown()
                && mc.gui.screen() == null
                && mc.hitResult instanceof BlockHitResult bhr
                && bhr.getType() == HitResult.Type.BLOCK;

        if (!attackingBlock) {
            restoreSlotIfNeeded();
            return;
        }

        BlockPos pos = ((BlockHitResult) mc.hitResult).getBlockPos();
        BlockState state = mc.level.getBlockState(pos);
        if (state.isAir() || state.getDestroySpeed(mc.level, pos) < 0.0f) {
            return;
        }

        int currentSlot = mc.player.getInventory().getSelectedSlot();
        int bestSlot = currentSlot;
        float bestSpeed = getToolSpeed(mc.player.getInventory().getItem(currentSlot), state);

        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.isEmpty()) continue;
            if (saveDurabilitySetting.isEnabled() && stack.isDamageableItem() && (stack.getMaxDamage() - stack.getDamageValue()) <= 4) {
                continue;
            }
            float speed = getToolSpeed(stack, state);
            if (speed > bestSpeed) {
                bestSpeed = speed;
                bestSlot = i;
            }
        }

        if (bestSlot != currentSlot && bestSpeed > 1.0f) {
            if (!switched) {
                previousSlot = currentSlot;
                switched = true;
            }
            mc.player.getInventory().setSelectedSlot(bestSlot);
            if (mc.player.connection != null) {
                mc.player.connection.send(new ServerboundSetCarriedItemPacket(bestSlot));
            }
        }
    }

    private float getToolSpeed(ItemStack stack, BlockState state) {
        if (stack == null || stack.isEmpty()) return 1.0f;
        return stack.getDestroySpeed(state);
    }

    private void restoreSlotIfNeeded() {
        if (switched && switchBackSetting.isEnabled() && previousSlot >= 0 && previousSlot < 9 && mc.player != null) {
            mc.player.getInventory().setSelectedSlot(previousSlot);
            if (mc.player.connection != null) {
                mc.player.connection.send(new ServerboundSetCarriedItemPacket(previousSlot));
            }
        }
        switched = false;
        previousSlot = -1;
    }
}
