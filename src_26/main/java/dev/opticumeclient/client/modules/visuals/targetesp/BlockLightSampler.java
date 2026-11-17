package dev.opticumeclient.client.modules.visuals.targetesp;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.Arrays;
import java.util.IdentityHashMap;

public final class BlockLightSampler {
    public static final int GRID_WORDS = 1024;
    private final int[] occluderWords = new int[GRID_WORDS];
    private final IdentityHashMap<BlockState, Integer> stateMaskCache = new IdentityHashMap<>();
    private final MutableBlockPos mutablePos = new MutableBlockPos();
    private final Vector3f tempPos = new Vector3f();
    private long lastGameTime = Long.MIN_VALUE;
    private int originX;
    private int originY;
    private int originZ;
    private int minX;
    private int minY;
    private int minZ;
    private int maxX;
    private int maxY;
    private int maxZ;

    public BlockLightSampler() {
    }

    public float relativeOriginX(Vec3 cameraPos) {
        return (float) (this.originX - cameraPos.x);
    }

    public float relativeOriginY(Vec3 cameraPos) {
        return (float) (this.originY - cameraPos.y);
    }

    public float relativeOriginZ(Vec3 cameraPos) {
        return (float) (this.originZ - cameraPos.z);
    }

    public int[] getOccluderWords() {
        return this.occluderWords;
    }

    public void reset() {
        this.lastGameTime = Long.MIN_VALUE;
        this.stateMaskCache.clear();
        Arrays.fill(this.occluderWords, 0);
    }

    private static int computeSubvoxelMask(BlockState state) {
        int[] mask = new int[]{0};
        state.getOcclusionShape().forAllBoxes((x0, y0, z0, x1, y1, z1) -> {
            for (int sub = 0; sub < 8; sub++) {
                double sx = 0.25 + (sub & 1) * 0.5;
                double sy = 0.25 + (sub >> 1 & 1) * 0.5;
                double sz = 0.25 + (sub >> 2 & 1) * 0.5;
                if (sx >= x0 && sx < x1 && sy >= y0 && sy < y1 && sz >= z0 && sz < z1) {
                    mask[0] |= 1 << sub;
                }
            }
        });
        return mask[0];
    }

    public void sample(Matrix4f invView, Vector3f[] lightsView, float[] radii, int count, Vec3 cameraPos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        double minBoundX = Double.POSITIVE_INFINITY;
        double minBoundY = minBoundX;
        double minBoundZ = minBoundX;
        double maxBoundX = Double.NEGATIVE_INFINITY;
        double maxBoundY = maxBoundX;
        double maxBoundZ = maxBoundX;

        for (int i = 0; i < count; i++) {
            invView.transformPosition(lightsView[i], this.tempPos);
            double wx = cameraPos.x + this.tempPos.x;
            double wy = cameraPos.y + this.tempPos.y;
            double wz = cameraPos.z + this.tempPos.z;
            float r = radii[i];
            minBoundX = Math.min(minBoundX, wx - r);
            maxBoundX = Math.max(maxBoundX, wx + r);
            minBoundY = Math.min(minBoundY, wy - r);
            maxBoundY = Math.max(maxBoundY, wy + r);
            minBoundZ = Math.min(minBoundZ, wz - r);
            maxBoundZ = Math.max(maxBoundZ, wz + r);
        }

        int ox = (int) Math.floor((minBoundX + maxBoundX) * 0.5) - 8;
        int oy = (int) Math.floor((minBoundY + maxBoundY) * 0.5) - 8;
        int oz = (int) Math.floor((minBoundZ + maxBoundZ) * 0.5) - 8;
        int x0 = Math.max(ox, (int) Math.floor(minBoundX));
        int x1 = Math.min(ox + 15, (int) Math.floor(maxBoundX));
        int y0 = Math.max(oy, (int) Math.floor(minBoundY));
        int y1 = Math.min(oy + 15, (int) Math.floor(maxBoundY));
        int z0 = Math.max(oz, (int) Math.floor(minBoundZ));
        int z1 = Math.min(oz + 15, (int) Math.floor(maxBoundZ));
        long gameTime = mc.level.getGameTime();
        if (this.lastGameTime != gameTime
                || this.originX != ox
                || this.originY != oy
                || this.originZ != oz
                || this.minX != x0
                || this.maxX != x1
                || this.minY != y0
                || this.maxY != y1
                || this.minZ != z0
                || this.maxZ != z1) {
            this.originX = ox;
            this.originY = oy;
            this.originZ = oz;
            this.minX = x0;
            this.maxX = x1;
            this.minY = y0;
            this.maxY = y1;
            this.minZ = z0;
            this.maxZ = z1;
            this.lastGameTime = gameTime;
            Arrays.fill(this.occluderWords, 0);

            for (int z = z0; z <= z1; z++) {
                for (int y = y0; y <= y1; y++) {
                    for (int x = x0; x <= x1; x++) {
                        BlockState state = mc.level.getBlockState(this.mutablePos.set(x, y, z));
                        if (state.canOcclude()) {
                            int mask = this.stateMaskCache.computeIfAbsent(state, BlockLightSampler::computeSubvoxelMask);
                            for (int sub = 0; sub < 8; sub++) {
                                if ((mask & 1 << sub) != 0) {
                                    int gx = (x - ox) * 2 + (sub & 1);
                                    int gy = (y - oy) * 2 + (sub >> 1 & 1);
                                    int gz = (z - oz) * 2 + (sub >> 2 & 1);
                                    int bitIndex = gx + gy * 32 + gz * 1024;
                                    this.occluderWords[bitIndex >> 5] |= 1 << (bitIndex & 31);
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
