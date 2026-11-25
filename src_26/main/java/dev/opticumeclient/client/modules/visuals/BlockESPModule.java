package dev.opticumeclient.client.modules.visuals;

import com.mojang.blaze3d.vertex.BufferBuilder;
import dev.opticumeclient.client.command.CommandDataStore;
import dev.opticumeclient.client.modules.Category;
import dev.opticumeclient.client.modules.Module;
import dev.opticumeclient.client.modules.visuals.targetesp.TargetEspFrame;
import dev.opticumeclient.client.render.ColorUtils;
import dev.opticumeclient.client.render.ThemeManager;
import dev.opticumeclient.client.render.WorldPrimitiveRenderer;
import dev.opticumeclient.client.setting.BooleanSetting;
import dev.opticumeclient.client.setting.ModeSetting;
import dev.opticumeclient.client.setting.NumberSetting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Chunk-budgeted BlockESP highlighting chests, spawners, shulkers, ancient debris,
 * diamond ore and custom block IDs added via {@code .blockesp add <id>}.
 */
public class BlockESPModule extends Module {
    private static BlockESPModule instance;

    private final ModeSetting mode = new ModeSetting("Mode", "Стиль обводки блоков", "Both", "Both", "Outline", "Fill");
    private final NumberSetting range = new NumberSetting("Range", "Радиус поиска блоков", 40.0, 16.0, 80.0, 4.0);
    private final NumberSetting maxBoxes = new NumberSetting("Max Blocks", "Лимит отображаемых блоков", 256.0, 32.0, 600.0, 32.0);
    private final BooleanSetting chests = new BooleanSetting("Chests", "Сундуки и бочки", true);
    private final BooleanSetting enderChests = new BooleanSetting("Ender Chests", "Эндер-сундуки", true);
    private final BooleanSetting shulkers = new BooleanSetting("Shulkers", "Шалкеры", true);
    private final BooleanSetting spawners = new BooleanSetting("Spawners", "Спавнеры", true);
    private final BooleanSetting debris = new BooleanSetting("Ancient Debris", "Древние обломки", true);
    private final BooleanSetting diamondOre = new BooleanSetting("Diamond Ore", "Алмазная руда", false);
    private final BooleanSetting customIds = new BooleanSetting("Custom (.blockesp)", "Блоки из команды .blockesp", true);

    private final Map<BlockPos, Integer> foundBlocks = new LinkedHashMap<>();
    private int scanCursorY = 0;

    public BlockESPModule() {
        super("BlockESP", "Подсветка сундуков, спавнеров, шалкеров, руд и блоков из .blockesp", Category.VISUALS);
        instance = this;
        addSetting(mode);
        addSetting(range);
        addSetting(maxBoxes);
        addSetting(chests);
        addSetting(enderChests);
        addSetting(shulkers);
        addSetting(spawners);
        addSetting(debris);
        addSetting(diamondOre);
        addSetting(customIds);
    }

    public static BlockESPModule getInstance() {
        return instance;
    }

    @Override
    public void onDisable() {
        super.onDisable();
        foundBlocks.clear();
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.level == null) {
            foundBlocks.clear();
            return;
        }

        int r = range.getIntValue();
        double maxDistSq = (r + 8.0) * (r + 8.0);
        BlockPos playerPos = mc.player.blockPosition();

        // Prune out-of-range or broken blocks
        Iterator<Map.Entry<BlockPos, Integer>> it = foundBlocks.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<BlockPos, Integer> e = it.next();
            BlockPos bp = e.getKey();
            if (bp.distSqr(playerPos) > maxDistSq) {
                it.remove();
                continue;
            }
            int color = classifyBlock(mc.level.getBlockState(bp));
            if (color == 0) {
                it.remove();
            } else {
                e.setValue(color);
            }
        }

        // Budgeted incremental Y-slice scan (zero frame hitches)
        int minY = Math.max(mc.level.getMinY(), playerPos.getY() - 28);
        int maxY = Math.min(mc.level.getMaxY() - 1, playerPos.getY() + 28);
        if (maxY < minY) return;

        int slicesPerTick = 3;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int cap = maxBoxes.getIntValue();

        for (int s = 0; s < slicesPerTick; s++) {
            int y = minY + Math.floorMod(scanCursorY++, Math.max(1, maxY - minY + 1));
            for (int dx = -r; dx <= r; dx += 1) {
                for (int dz = -r; dz <= r; dz += 1) {
                    if (dx * dx + dz * dz > r * r) continue;
                    cursor.set(playerPos.getX() + dx, y, playerPos.getZ() + dz);
                    BlockState state = mc.level.getBlockState(cursor);
                    if (state.isAir()) continue;
                    int color = classifyBlock(state);
                    if (color != 0 && foundBlocks.size() < cap) {
                        foundBlocks.putIfAbsent(cursor.immutable(), color);
                    }
                }
            }
        }
    }

    private int classifyBlock(BlockState state) {
        Block block = state.getBlock();
        if (block == Blocks.AIR || block == Blocks.CAVE_AIR || block == Blocks.VOID_AIR) return 0;

        if (chests.isEnabled() && (block == Blocks.CHEST || block == Blocks.TRAPPED_CHEST || block == Blocks.BARREL)) {
            return 0xFFF59E0B;
        }
        if (enderChests.isEnabled() && block == Blocks.ENDER_CHEST) {
            return 0xFFA855F7;
        }
        if (shulkers.isEnabled() && block instanceof ShulkerBoxBlock) {
            return 0xFFEC4899;
        }
        if (spawners.isEnabled() && block == Blocks.SPAWNER) {
            return 0xFFEF4444;
        }
        if (debris.isEnabled() && block == Blocks.ANCIENT_DEBRIS) {
            return 0xFFFF6D28;
        }
        if (diamondOre.isEnabled() && (block == Blocks.DIAMOND_ORE || block == Blocks.DEEPSLATE_DIAMOND_ORE)) {
            return 0xFF22D3EE;
        }
        if (customIds.isEnabled()) {
            Set<String> custom = CommandDataStore.getInstance().getBlockEspIds();
            if (!custom.isEmpty()) {
                Identifier key = BuiltInRegistries.BLOCK.getKey(block);
                if (key != null && custom.contains(key.toString())) {
                    return ThemeManager.getInstance().getAccentColor(0);
                }
            }
        }
        return 0;
    }

    public void renderWorld(TargetEspFrame frame) {
        if (!isEnabled() || foundBlocks.isEmpty() || mc.level == null) return;

        boolean drawFill = mode.is("Fill") || mode.is("Both");
        boolean drawOutline = mode.is("Outline") || mode.is("Both");
        Vec3 cam = frame.cameraPos();
        Matrix4f pose = frame.poseStack().last().pose();

        if (drawFill) {
            BufferBuilder quadBuf = WorldPrimitiveRenderer.beginQuads();
            boolean anyFill = false;
            for (Map.Entry<BlockPos, Integer> entry : foundBlocks.entrySet()) {
                BlockPos bp = entry.getKey();
                int base = entry.getValue();
                float x1 = (float) (bp.getX() - cam.x);
                float y1 = (float) (bp.getY() - cam.y);
                float z1 = (float) (bp.getZ() - cam.z);
                emitBoxQuads(quadBuf, pose, x1, y1, z1, x1 + 1.0f, y1 + 1.0f, z1 + 1.0f, ColorUtils.withAlphaFactor(base, 0.22f));
                anyFill = true;
            }
            if (anyFill) {
                WorldPrimitiveRenderer.drawQuads(quadBuf, true, false);
            } else {
                WorldPrimitiveRenderer.discardQuietly(quadBuf);
            }
        }

        if (drawOutline) {
            BufferBuilder lineBuf = WorldPrimitiveRenderer.beginLines();
            boolean anyLine = false;
            for (Map.Entry<BlockPos, Integer> entry : foundBlocks.entrySet()) {
                BlockPos bp = entry.getKey();
                int base = entry.getValue();
                float x1 = (float) (bp.getX() - cam.x);
                float y1 = (float) (bp.getY() - cam.y);
                float z1 = (float) (bp.getZ() - cam.z);
                emitBoxLines(lineBuf, pose, x1, y1, z1, x1 + 1.0f, y1 + 1.0f, z1 + 1.0f, ColorUtils.withAlphaFactor(base, 0.90f));
                anyLine = true;
            }
            if (anyLine) {
                WorldPrimitiveRenderer.drawLines(lineBuf, false);
            } else {
                WorldPrimitiveRenderer.discardQuietly(lineBuf);
            }
        }
    }

    public static void emitBoxQuads(BufferBuilder buf, Matrix4f pose, float x1, float y1, float z1, float x2, float y2, float z2, int col) {
        // Bottom & Top
        buf.addVertex(pose, x1, y1, z1).setColor(col); buf.addVertex(pose, x2, y1, z1).setColor(col);
        buf.addVertex(pose, x2, y1, z2).setColor(col); buf.addVertex(pose, x1, y1, z2).setColor(col);
        buf.addVertex(pose, x1, y2, z1).setColor(col); buf.addVertex(pose, x1, y2, z2).setColor(col);
        buf.addVertex(pose, x2, y2, z2).setColor(col); buf.addVertex(pose, x2, y2, z1).setColor(col);
        // Sides
        buf.addVertex(pose, x1, y1, z1).setColor(col); buf.addVertex(pose, x1, y2, z1).setColor(col);
        buf.addVertex(pose, x2, y2, z1).setColor(col); buf.addVertex(pose, x2, y1, z1).setColor(col);
        buf.addVertex(pose, x1, y1, z2).setColor(col); buf.addVertex(pose, x2, y1, z2).setColor(col);
        buf.addVertex(pose, x2, y2, z2).setColor(col); buf.addVertex(pose, x1, y2, z2).setColor(col);
        buf.addVertex(pose, x1, y1, z1).setColor(col); buf.addVertex(pose, x1, y1, z2).setColor(col);
        buf.addVertex(pose, x1, y2, z2).setColor(col); buf.addVertex(pose, x1, y2, z1).setColor(col);
        buf.addVertex(pose, x2, y1, z1).setColor(col); buf.addVertex(pose, x2, y2, z1).setColor(col);
        buf.addVertex(pose, x2, y2, z2).setColor(col); buf.addVertex(pose, x2, y1, z2).setColor(col);
    }

    public static void emitBoxLines(BufferBuilder buf, Matrix4f pose, float x1, float y1, float z1, float x2, float y2, float z2, int col) {
        buf.addVertex(pose, x1, y1, z1).setColor(col); buf.addVertex(pose, x2, y1, z1).setColor(col);
        buf.addVertex(pose, x2, y1, z1).setColor(col); buf.addVertex(pose, x2, y1, z2).setColor(col);
        buf.addVertex(pose, x2, y1, z2).setColor(col); buf.addVertex(pose, x1, y1, z2).setColor(col);
        buf.addVertex(pose, x1, y1, z2).setColor(col); buf.addVertex(pose, x1, y1, z1).setColor(col);

        buf.addVertex(pose, x1, y2, z1).setColor(col); buf.addVertex(pose, x2, y2, z1).setColor(col);
        buf.addVertex(pose, x2, y2, z1).setColor(col); buf.addVertex(pose, x2, y2, z2).setColor(col);
        buf.addVertex(pose, x2, y2, z2).setColor(col); buf.addVertex(pose, x1, y2, z2).setColor(col);
        buf.addVertex(pose, x1, y2, z2).setColor(col); buf.addVertex(pose, x1, y2, z1).setColor(col);

        buf.addVertex(pose, x1, y1, z1).setColor(col); buf.addVertex(pose, x1, y2, z1).setColor(col);
        buf.addVertex(pose, x2, y1, z1).setColor(col); buf.addVertex(pose, x2, y2, z1).setColor(col);
        buf.addVertex(pose, x2, y1, z2).setColor(col); buf.addVertex(pose, x2, y2, z2).setColor(col);
        buf.addVertex(pose, x1, y1, z2).setColor(col); buf.addVertex(pose, x1, y2, z2).setColor(col);
    }
}
