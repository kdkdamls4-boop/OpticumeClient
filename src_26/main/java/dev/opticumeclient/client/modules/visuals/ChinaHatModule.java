package dev.opticumeclient.client.modules.visuals;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.RenderSystem.AutoStorageIndexBuffer;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.MeshData.DrawState;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import dev.opticumeclient.client.friend.FriendManager;
import dev.opticumeclient.client.modules.Category;
import dev.opticumeclient.client.modules.Module;
import dev.opticumeclient.client.modules.visuals.targetesp.ArgbUtil;
import dev.opticumeclient.client.modules.visuals.targetesp.BufferPool;
import dev.opticumeclient.client.modules.visuals.targetesp.EspResources;
import dev.opticumeclient.client.modules.visuals.targetesp.PipelineBuilder;
import dev.opticumeclient.client.modules.visuals.targetesp.TargetEspFrame;
import dev.opticumeclient.client.render.ThemeManager;
import dev.opticumeclient.client.setting.BooleanSetting;
import dev.opticumeclient.client.setting.ColorSetting;
import dev.opticumeclient.client.setting.ModeSetting;
import dev.opticumeclient.client.setting.MultiBoxSetting;
import dev.opticumeclient.client.setting.NumberSetting;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;

/**
 * Ported from Desire ChinaHatModule:
 * Supports "Обычный", "Градиент", "Контур", "Призраки 1", "Призраки 2", "Призраки 3"
 * rendered natively through RenderPearl SPIR-V pipelines.
 */
public class ChinaHatModule extends Module {
    private static final Identifier BLOOM_TEX = dev.opticumeclient.client.render.TextureAssets.DESIRE_PARTICLE_HEX_ORBIZ;
    private static final float TAU_F = (float) (Math.PI * 2.0);
    private static final double TAU_D = Math.PI * 2.0;

    private static ChinaHatModule instance;

    private final ModeSetting mode = new ModeSetting(
            "Режим",
            "Стиль шляпы / призраков над головой",
            "Обычный",
            "Обычный", "Градиент", "Контур", "Призраки 1", "Призраки 2", "Призраки 3"
    );
    private final MultiBoxSetting displayTargets = new MultiBoxSetting(
            "Отображать",
            "Над кем отображать шляпу",
            new String[]{"Себя", "Друзей", "Игроков"},
            "Себя"
    );
    private final NumberSetting radius = new NumberSetting("Радиус", "Радиус конуса или орбиты призраков", 0.65, 0.3, 1.4, 0.05);
    private final NumberSetting height = new NumberSetting("Высота", "Высота конуса или амплитуда волны", 0.35, 0.1, 0.9, 0.05)
            .visibleWhen(() -> !mode.is("Призраки 3"));
    private final NumberSetting offset = new NumberSetting("Отступ", "Смещение по высоте над головой", 0.08, -0.2, 0.4, 0.01);
    private final NumberSetting speed = new NumberSetting("Скорость", "Скорость вращения", 45.0, 0.0, 180.0, 1.0);
    private final NumberSetting opacity = new NumberSetting("Прозрачность", "Общая прозрачность эффекта", 0.75, 0.15, 1.0, 0.05);
    private final NumberSetting ghost3Size = new NumberSetting("Размер", "Размер призраков 3", 0.25, 0.05, 1.0, 0.01)
            .visibleWhen(() -> mode.is("Призраки 3"));
    private final NumberSetting trailLength = new NumberSetting("Длина трейла", "Длина шлейфа в Призраки 3", 25.0, 5.0, 100.0, 1.0)
            .visibleWhen(() -> mode.is("Призраки 3"));
    private final NumberSetting colorOffset = new NumberSetting("Смещение цвета", "Сдвиг радуги Призраки 3", 0.0, 0.0, 360.0, 1.0)
            .visibleWhen(() -> mode.is("Призраки 3"));
    private final BooleanSetting alwaysShow = new BooleanSetting("Всегда показывать", "Отображать Призраки 3 даже от 1-го лица", false)
            .visibleWhen(() -> mode.is("Призраки 3"));
    private final BooleanSetting autoThemeColor = new BooleanSetting("Авто цвет под тему", "Использовать цвета текущей темы", true)
            .visibleWhen(() -> !mode.is("Градиент"));
    private final ColorSetting customColor = new ColorSetting("Цвет", "Пользовательский цвет", 0xFFFF6B8A, false)
            .visibleWhen(() -> !autoThemeColor.isEnabled() && !mode.is("Градиент"));

    private final RenderPipeline hatPipeline;
    private final RenderPipeline ghost3Pipeline;

    private final Map<Integer, LinkedList<Vec3>[]> ghost2Trails = new HashMap<>();
    private final Vector4f ghostLocalVec = new Vector4f();
    private final Matrix4f workMatrixA = new Matrix4f();
    private final Matrix4f workMatrixB = new Matrix4f();
    private final Vector4f basisX = new Vector4f();
    private final Vector4f basisY = new Vector4f();
    private long ghost3StartMillis = -1L;

    private float orthoX0, orthoY0, orthoZ0;
    private float orthoX1, orthoY1, orthoZ1;

    public ChinaHatModule() {
        super("ChinaHat", "Красивая шляпа и призраки вокруг головы игрока (Desire)", Category.VISUALS);
        instance = this;
        addSetting(mode);
        addSetting(displayTargets);
        addSetting(radius);
        addSetting(height);
        addSetting(offset);
        addSetting(speed);
        addSetting(opacity);
        addSetting(ghost3Size);
        addSetting(trailLength);
        addSetting(colorOffset);
        addSetting(alwaysShow);
        addSetting(autoThemeColor);
        addSetting(customColor);

        this.hatPipeline = PipelineBuilder.create(PipelineBuilder.PROJECTION_SNIPPET)
                .location("opticumeclient/china_hat")
                .vertexShader(EspResources.shader("target_esp_crystal"))
                .fragmentShader(EspResources.shader("target_esp_crystal"))
                .depthTest(CompareOp.GREATER_THAN_OR_EQUAL)
                .writeDepth(false)
                .cull(false)
                .blend(BlendFunction.TRANSLUCENT)
                .vertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, PrimitiveTopology.QUADS)
                .build();

        this.ghost3Pipeline = PipelineBuilder.create(PipelineBuilder.PROJECTION_SNIPPET)
                .location("opticumeclient/china_hat_ghost3")
                .vertexShader(EspResources.shader("dead_chams"))
                .fragmentShader(EspResources.shader("dead_chams"))
                .depthTest(CompareOp.GREATER_THAN_OR_EQUAL)
                .writeDepth(false)
                .cull(false)
                .blend(BlendFunction.ADDITIVE)
                .vertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, PrimitiveTopology.QUADS)
                .sampler("Sampler0")
                .build();
    }

    public static ChinaHatModule getInstance() {
        return instance;
    }

    @Override
    public void onDisable() {
        super.onDisable();
        ghost3StartMillis = -1L;
        ghost2Trails.clear();
    }

    public void renderWorld(TargetEspFrame frame) {
        if (!isEnabled() || mc.player == null || mc.level == null) return;

        float tickDelta = frame.deltaTracker().getGameTimeDeltaPartialTick(true);
        Vec3 camPos = frame.cameraPos();

        if (mode.is("Призраки 3")) {
            ghost2Trails.clear();
            renderGhost3(frame, tickDelta, camPos);
            return;
        }

        ghost3StartMillis = -1L;
        float spinRad = (float) Math.toRadians(((float) mc.level.getGameTime() + tickDelta) * speed.getFloatValue() * 0.05f);
        boolean isGhost2 = mode.is("Призраки 2");
        if (!isGhost2) {
            ghost2Trails.clear();
        }

        BufferBuilder buf = BufferPool.getInstance().begin(PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        int vertCount = 0;
        Set<Integer> seenPlayers = isGhost2 ? new HashSet<>() : null;
        PoseStack pose = frame.poseStack();

        for (Player player : mc.level.players()) {
            if (!shouldRenderFor(player)) continue;
            if (isGhost2) {
                seenPlayers.add(player.getId());
                vertCount += emitGhost2(buf, pose, camPos, player, tickDelta);
            } else if (mode.is("Призраки 1")) {
                vertCount += emitGhost1(buf, pose, camPos, player, tickDelta, spinRad);
            } else {
                vertCount += emitHatCone(buf, pose, camPos, player, tickDelta, spinRad);
            }
        }

        if (seenPlayers != null) {
            ghost2Trails.keySet().removeIf(id -> !seenPlayers.contains(id));
        }

        submitPipeline(buf, vertCount, hatPipeline, "chinaHat", false);
    }

    private void renderGhost3(TargetEspFrame frame, float tickDelta, Vec3 camPos) {
        if (ghost3StartMillis < 0L) {
            ghost3StartMillis = System.currentTimeMillis();
        }
        Camera camera = mc.gameRenderer.mainCamera();
        float rVal = radius.getFloatValue();
        float szVal = ghost3Size.getFloatValue();
        float spdVal = speed.getFloatValue();
        float alphaVal = opacity.getFloatValue();
        int trail = trailLength.getIntValue();
        float colOff = colorOffset.getFloatValue();
        long now = System.currentTimeMillis();

        PoseStack pose = frame.poseStack();
        BufferBuilder buf = BufferPool.getInstance().begin(PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        int vertCount = 0;

        for (Player player : mc.level.players()) {
            if (!shouldRenderFor(player)) continue;
            HeadPose hp = sampleHeadPose(player, tickDelta);
            applyMatrixToHead(workMatrixA, camPos, player, tickDelta, hp, 0.0f, resolveHeadTop(player, hp));
            float r = rVal * hp.scale;
            float sz = szVal * hp.scale;

            for (int arm = 0; arm < 2; arm++) {
                double phaseOffset = arm * Math.PI;
                for (int i = 0; i < trail; i++) {
                    float aFactor = (1.0f - (float) i / trail) * alphaVal;
                    float segSize = sz * (1.0f - (float) i / trail);
                    double tSec = (now - ghost3StartMillis - i * 20.0) / 1000.0;
                    double theta = tSec * (spdVal / 10.0f) + phaseOffset;

                    ghostLocalVec.set((float) (Math.sin(theta) * r), 0.0f, (float) (Math.cos(theta) * r), 1.0f);
                    workMatrixA.transform(ghostLocalVec);

                    int argb = autoThemeColor.isEnabled()
                            ? ArgbUtil.themeWave((int) (colOff + i * 30.0f))
                            : customColor.getColor();
                    int mainCol = ArgbUtil.withAlphaFactor(argb, aFactor);
                    int outerCol = ArgbUtil.withAlphaFactor(ArgbUtil.lerp(argb, 0xFFFFFFFF, 0.3f), aFactor);

                    pose.pushPose();
                    pose.translate(ghostLocalVec.x, ghostLocalVec.y, ghostLocalVec.z);
                    pose.mulPose(new Matrix4f().rotation(camera.rotation()));
                    Matrix4f m = pose.last().pose();
                    // Match Particles crisp luminous orb proportions (compact bright core + controlled soft halo)
                    float coreSize = segSize * 0.56f;
                    float haloSize = segSize * 0.88f;
                    vertCount += emitBloomQuad(buf, m, coreSize, mainCol);
                    vertCount += emitBloomQuad(buf, m, haloSize, outerCol);
                    pose.popPose();
                }
            }
        }

        submitPipeline(buf, vertCount, ghost3Pipeline, "chinaHatGhost3", true);
    }

    private int emitHatCone(BufferBuilder buf, PoseStack pose, Vec3 camPos, Player player, float tickDelta, float spinRad) {
        float scale = pushPlayerHeadPose(pose, camPos, player, tickDelta, spinRad);
        Matrix4f m = pose.last().pose();
        float r = radius.getFloatValue() * scale;
        float h = height.getFloatValue() * scale;
        float a = opacity.getFloatValue();
        int verts = mode.is("Контур") ? emitOutlineBand(buf, m, r, h, a) : emitSolidCone(buf, m, r, h, a);
        pose.popPose();
        return verts;
    }

    private int emitSolidCone(BufferBuilder buf, Matrix4f m, float r, float h, float alpha) {
        int verts = 0;
        boolean grad = mode.is("Градиент");
        Vector4f apexCol = ArgbUtil.toVector4f(ArgbUtil.withAlphaFactor(pickColor(0), alpha * (grad ? 0.95f : 0.9f)));

        for (int i = 0; i < 64; i++) {
            float a0 = TAU_F * i / 64.0f;
            float a1 = TAU_F * (i + 1) / 64.0f;
            float x0 = Mth.cos(a0) * r;
            float z0 = Mth.sin(a0) * r;
            float x1 = Mth.cos(a1) * r;
            float z1 = Mth.sin(a1) * r;
            Vector4f c0 = ArgbUtil.toVector4f(ArgbUtil.withAlphaFactor(pickColor(i), alpha));
            Vector4f c1 = ArgbUtil.toVector4f(ArgbUtil.withAlphaFactor(pickColor(i + 1), alpha));
            emitVert(buf, m, 0.0f, h, 0.0f, apexCol);
            emitVert(buf, m, x0, 0.0f, z0, c0);
            emitVert(buf, m, x1, 0.0f, z1, c1);
            emitVert(buf, m, 0.0f, h, 0.0f, apexCol);
            verts += 4;
        }
        return verts;
    }

    private int emitOutlineBand(BufferBuilder buf, Matrix4f m, float r, float h, float alpha) {
        int verts = 0;
        float innerR = Math.max(0.01f, r - 0.03f);
        float ribThickness = 0.012f;

        for (int i = 0; i < 64; i++) {
            float a0 = TAU_F * i / 64.0f;
            float a1 = TAU_F * (i + 1) / 64.0f;
            Vector4f c0 = ArgbUtil.toVector4f(ArgbUtil.withAlphaFactor(pickColor(i), alpha));
            Vector4f c1 = ArgbUtil.toVector4f(ArgbUtil.withAlphaFactor(pickColor(i + 1), alpha));
            float ox0 = Mth.cos(a0) * r, oz0 = Mth.sin(a0) * r;
            float ox1 = Mth.cos(a1) * r, oz1 = Mth.sin(a1) * r;
            float ix0 = Mth.cos(a0) * innerR, iz0 = Mth.sin(a0) * innerR;
            float ix1 = Mth.cos(a1) * innerR, iz1 = Mth.sin(a1) * innerR;
            emitVert(buf, m, ox0, 0.0f, oz0, c0);
            emitVert(buf, m, ox1, 0.0f, oz1, c1);
            emitVert(buf, m, ix1, 0.0f, iz1, c1);
            emitVert(buf, m, ix0, 0.0f, iz0, c0);
            verts += 4;

            if (i % 4 == 0) {
                float nx = -Mth.sin(a0);
                float nz = Mth.cos(a0);
                Vector4f apexCol = ArgbUtil.toVector4f(ArgbUtil.withAlphaFactor(pickColor(i), alpha));
                emitVert(buf, m, nx * ribThickness, h, nz * ribThickness, apexCol);
                emitVert(buf, m, ox0 + nx * ribThickness, 0.0f, oz0 + nz * ribThickness, c0);
                emitVert(buf, m, ox0 - nx * ribThickness, 0.0f, oz0 - nz * ribThickness, c0);
                emitVert(buf, m, -nx * ribThickness, h, -nz * ribThickness, apexCol);
                verts += 4;
            }
        }
        return verts;
    }

    private int emitGhost1(BufferBuilder buf, PoseStack pose, Vec3 camPos, Player player, float tickDelta, float spinRad) {
        float scale = pushPlayerHeadPose(pose, camPos, player, tickDelta, spinRad);
        computeCameraOrigin(camPos, player, tickDelta, spinRad);
        Matrix4f m = pose.last().pose();
        float r = radius.getFloatValue() * scale;
        float h = height.getFloatValue() * scale;
        float a = opacity.getFloatValue();
        float sz = Mth.clamp(r * 0.5f, 0.26f, 0.42f);
        int verts = 0;

        for (int i = 0; i < 6; i++) {
            float angle = TAU_F * i / 6.0f;
            float bobY = Mth.sin(spinRad * 4.0f + i * 1.9f) * h * 0.35f;
            verts += emitGhostSilhouette(buf, m, Mth.cos(angle) * r, bobY, Mth.sin(angle) * r, sz,
                    ArgbUtil.withAlphaFactor(pickColor(i * 8), a));
        }

        pose.popPose();
        return verts;
    }

    @SuppressWarnings("unchecked")
    private int emitGhost2(BufferBuilder buf, PoseStack pose, Vec3 camPos, Player player, float tickDelta) {
        float scale = pushPlayerHeadPose(pose, camPos, player, tickDelta, 0.0f);
        Matrix4f m = pose.last().pose();
        workMatrixB.set(m).invert();
        basisX.set(1.0f, 0.0f, 0.0f, 0.0f);
        workMatrixB.transform(basisX);
        basisY.set(0.0f, 1.0f, 0.0f, 0.0f);
        workMatrixB.transform(basisY);

        if (!normalizeVec4(basisX) || !normalizeVec4(basisY)) {
            pose.popPose();
            return 0;
        }

        float r = radius.getFloatValue() * scale;
        float h = height.getFloatValue() * scale;
        float a = opacity.getFloatValue();
        int c1 = pickColor(0);
        int c2 = autoThemeColor.isEnabled() ? ThemeManager.getInstance().getCurrentTheme().getSecondaryColor() : pickColor(48);
        double t = System.currentTimeMillis() * 0.004 * (speed.getFloatValue() / 45.0f);

        LinkedList<Vec3>[] trails = ghost2Trails.computeIfAbsent(player.getId(), id -> {
            LinkedList<Vec3>[] arr = new LinkedList[5];
            for (int k = 0; k < arr.length; k++) arr[k] = new LinkedList<>();
            return arr;
        });

        int verts = 0;
        for (int idx = 0; idx < trails.length; idx++) {
            double ang = idx * (TAU_D / trails.length);
            double px = Math.sin(t + ang) * r;
            double pz = Math.cos(t + ang) * r;
            double sign = (idx % 2 == 0) ? 1.0 : -1.0;
            double py = Math.sin((t + ang) * 0.8) * sign * h;

            LinkedList<Vec3> list = trails[idx];
            Vec3 nextPt = new Vec3(px, py, pz);
            if (!list.isEmpty()) {
                Vec3 prevPt = list.getFirst();
                double dist = prevPt.distanceTo(nextPt);
                int steps = Mth.clamp((int) Math.ceil(dist / 0.04), 1, 6);
                for (int s = 1; s <= steps; s++) {
                    list.addFirst(prevPt.lerp(nextPt, (double) s / steps));
                }
            } else {
                list.addFirst(nextPt);
            }
            while (list.size() > 24) list.removeLast();

            int n = 0;
            int total = list.size();
            for (Vec3 pt : list) {
                float k = 1.0f - (float) n / total;
                int col = ArgbUtil.lerp(c1, c2, k);
                float baseRadius = (n == 0 ? 0.26f : 0.16f) * k * Math.max(a, 0.35f);
                float alphaFade = (float) (Math.pow(k, 1.8) * a);
                verts += emitBillboardCircle(buf, m, (float) pt.x, (float) pt.y, (float) pt.z, baseRadius, col, alphaFade);
                if (n == 0) {
                    verts += emitBillboardCircle(buf, m, (float) pt.x, (float) pt.y, (float) pt.z, 0.12f, 0xFFFFFFFF, a);
                }
                n++;
            }
        }

        pose.popPose();
        return verts;
    }

    private int emitBillboardCircle(BufferBuilder buf, Matrix4f m, float x, float y, float z, float r, int color, float alphaFactor) {
        Vector4f centerCol = ArgbUtil.toVector4f(ArgbUtil.withAlphaFactor(color, alphaFactor));
        Vector4f edgeCol = ArgbUtil.toVector4f(ArgbUtil.withAlphaFactor(color, 0.0f));
        int verts = 0;
        for (int i = 0; i < 8; i++) {
            float a0 = TAU_F * i / 8.0f;
            float a1 = TAU_F * (i + 1) / 8.0f;
            float c0 = Mth.cos(a0), s0 = Mth.sin(a0);
            float c1 = Mth.cos(a1), s1 = Mth.sin(a1);
            emitVert(buf, m, x, y, z, centerCol);
            emitVert(buf, m, x + basisX.x * c0 * r + basisY.x * s0 * r, y + basisX.y * c0 * r + basisY.y * s0 * r, z + basisX.z * c0 * r + basisY.z * s0 * r, edgeCol);
            emitVert(buf, m, x + basisX.x * c1 * r + basisY.x * s1 * r, y + basisX.y * c1 * r + basisY.y * s1 * r, z + basisX.z * c1 * r + basisY.z * s1 * r, edgeCol);
            emitVert(buf, m, x, y, z, centerCol);
            verts += 4;
        }
        return verts;
    }

    private int emitGhostSilhouette(BufferBuilder buf, Matrix4f m, float x, float y, float z, float size, int color) {
        Vector4f body = ArgbUtil.toVector4f(color);
        Vector4f eye = ArgbUtil.toVector4f(ArgbUtil.withAlphaFactor(0xFF151924, Math.max(0.45f, body.w)));
        float w = size;
        float h = size * 1.25f;
        float[] outline = new float[]{
                0.0f, h * 0.5f, w * 0.22f, h * 0.42f, w * 0.38f, h * 0.22f, w * 0.44f, h * 0.02f,
                w * 0.42f, -h * 0.18f, w * 0.36f, -h * 0.4f, w * 0.2f, -h * 0.28f, w * 0.06f, -h * 0.48f,
                0.0f, -h * 0.34f, -w * 0.06f, -h * 0.48f, -w * 0.2f, -h * 0.28f, -w * 0.36f, -h * 0.4f,
                -w * 0.42f, -h * 0.18f, -w * 0.44f, h * 0.02f, -w * 0.38f, h * 0.22f, -w * 0.22f, h * 0.42f
        };
        if (!buildOrthoBasis(x, y, z, 1.0f, 1.0f)) {
            orthoX0 = 1.0f; orthoY0 = 0.0f; orthoZ0 = 0.0f;
            orthoX1 = 0.0f; orthoY1 = 1.0f; orthoZ1 = 0.0f;
        }
        int segs = outline.length / 2;
        int verts = 0;
        float cy = h * 0.05f;
        for (int i = 0; i < segs; i++) {
            int j = (i + 1) % segs;
            verts += emitOrthoVert(buf, m, x, y, z, 0.0f, cy, body);
            verts += emitOrthoVert(buf, m, x, y, z, outline[i * 2], outline[i * 2 + 1], body);
            verts += emitOrthoVert(buf, m, x, y, z, outline[j * 2], outline[j * 2 + 1], body);
            verts += emitOrthoVert(buf, m, x, y, z, 0.0f, cy, body);
        }
        verts += emitOrthoQuad(buf, m, x, y, z, -w * 0.13f, h * 0.14f, w * 0.07f, h * 0.11f, eye);
        verts += emitOrthoQuad(buf, m, x, y, z, w * 0.13f, h * 0.14f, w * 0.07f, h * 0.11f, eye);
        return verts;
    }

    private int emitOrthoQuad(BufferBuilder buf, Matrix4f m, float x, float y, float z, float u, float v, float hw, float hh, Vector4f col) {
        emitOrthoVert(buf, m, x, y, z, u - hw, v - hh, col);
        emitOrthoVert(buf, m, x, y, z, u - hw, v + hh, col);
        emitOrthoVert(buf, m, x, y, z, u + hw, v + hh, col);
        emitOrthoVert(buf, m, x, y, z, u + hw, v - hh, col);
        return 4;
    }

    private int emitOrthoVert(BufferBuilder buf, Matrix4f m, float x, float y, float z, float u, float v, Vector4f col) {
        emitVert(buf, m, x + orthoX0 * u + orthoX1 * v, y + orthoY0 * u + orthoY1 * v, z + orthoZ0 * u + orthoZ1 * v, col);
        return 1;
    }

    private boolean buildOrthoBasis(float x, float y, float z, float w, float h) {
        float dx = ghostLocalVec.x - x;
        float dy = ghostLocalVec.y - y;
        float dz = ghostLocalVec.z - z;
        float len = Mth.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1.0E-5f) return false;
        dx /= len; dy /= len; dz /= len;

        float rx = dz, ry = 0.0f, rz = -dx;
        float rLen = Mth.sqrt(rx * rx + rz * rz);
        if (rLen < 1.0E-5f) {
            rx = w;
        } else {
            float k = w / rLen;
            rx *= k; rz *= k;
        }

        float ux = dy * rz - dz * ry;
        float uy = dz * rx - dx * rz;
        float uz = dx * ry - dy * rx;
        float uLen = Mth.sqrt(ux * ux + uy * uy + uz * uz);
        if (uLen < 1.0E-5f) return false;
        float uk = h / uLen;
        orthoX0 = rx; orthoY0 = ry; orthoZ0 = rz;
        orthoX1 = ux * uk; orthoY1 = uy * uk; orthoZ1 = uz * uk;
        return true;
    }

    private float pushPlayerHeadPose(PoseStack pose, Vec3 camPos, Player player, float tickDelta, float spinRad) {
        HeadPose hp = sampleHeadPose(player, tickDelta);
        pose.pushPose();
        Vec3 pos = lerpedPos(player, tickDelta);
        pose.translate(pos.x - camPos.x, pos.y - camPos.y, pos.z - camPos.z);
        pose.rotateDegrees(Axis.YP, -hp.bodyYaw);
        if (hp.xRot != 0.0f) pose.rotateDegrees(Axis.XP, hp.xRot);
        if (hp.swimShift > 0.0f) {
            pose.translate(0.0f, -1.0f * hp.swimShift * hp.scale, 0.3f * hp.swimShift * hp.scale);
        }
        pose.translate(0.0f, resolveHeadTop(player, hp), 0.0f);
        if (spinRad != 0.0f) {
            pose.rotate(Axis.YP.rotation(spinRad));
        }
        return hp.scale;
    }

    private void computeCameraOrigin(Vec3 camPos, Player player, float tickDelta, float spinRad) {
        HeadPose hp = sampleHeadPose(player, tickDelta);
        applyMatrixToHead(workMatrixA, camPos, player, tickDelta, hp, spinRad, resolveHeadTop(player, hp));
        workMatrixA.invert();
        ghostLocalVec.set(0.0f, 0.0f, 0.0f, 1.0f);
        workMatrixA.transform(ghostLocalVec);
    }

    private void applyMatrixToHead(Matrix4f mat, Vec3 camPos, Player player, float tickDelta, HeadPose hp, float spinRad, float headY) {
        Vec3 pos = lerpedPos(player, tickDelta);
        mat.translation((float) (pos.x - camPos.x), (float) (pos.y - camPos.y), (float) (pos.z - camPos.z));
        mat.rotate(Axis.YP.rotationDegrees(-hp.bodyYaw));
        if (hp.xRot != 0.0f) mat.rotate(Axis.XP.rotationDegrees(hp.xRot));
        if (hp.swimShift > 0.0f) mat.translate(0.0f, -1.0f * hp.swimShift * hp.scale, 0.3f * hp.swimShift * hp.scale);
        mat.translate(0.0f, headY, 0.0f);
        if (spinRad != 0.0f) mat.rotate(Axis.YP.rotation(spinRad));
    }

    private HeadPose sampleHeadPose(Player player, float tickDelta) {
        float bodyYaw = Mth.rotLerp(tickDelta, player.yBodyRotO, player.yBodyRot);
        float xRot = Mth.lerp(tickDelta, player.xRotO, player.getXRot());
        float swim = player.getSwimAmount(tickDelta);
        float scale = player.isBaby() ? 0.5f : 1.0f;
        float pitchRot = 0.0f;
        float swimShift = 0.0f;
        if (player.isFallFlying()) {
            float t = player.getFallFlyingTicks() + tickDelta;
            float k = Mth.clamp(t * t / 100.0f, 0.0f, 1.0f);
            if (!player.isAutoSpinAttack()) pitchRot = k * (xRot + 90.0f);
        } else if (swim > 0.0f) {
            float target = player.isInWater() ? xRot + 90.0f : 90.0f;
            pitchRot = Mth.lerp(swim, 0.0f, target);
            if (player.isVisuallySwimming()) swimShift = swim;
        }
        return new HeadPose(bodyYaw, pitchRot, swimShift, scale);
    }

    private float resolveHeadTop(Player player, HeadPose hp) {
        boolean tilted = hp.xRot != 0.0f || hp.swimShift > 0.0f || player.isFallFlying() || player.isVisuallySwimming() || player.isVisuallyCrawling();
        float h = (tilted ? player.getDimensions(Pose.STANDING).height() : player.getBbHeight()) * hp.scale;
        return h + offset.getFloatValue();
    }

    private static Vec3 lerpedPos(Player p, float tickDelta) {
        return new Vec3(Mth.lerp(tickDelta, p.xo, p.getX()), Mth.lerp(tickDelta, p.yo, p.getY()), Mth.lerp(tickDelta, p.zo, p.getZ()));
    }

    private boolean shouldRenderFor(Player player) {
        if (player != mc.player) {
            return FriendManager.getInstance().isFriend(player) ? displayTargets.isEnabled("Друзей") : displayTargets.isEnabled("Игроков");
        }
        if (!displayTargets.isEnabled("Себя")) return false;
        return !mc.options.getCameraType().isFirstPerson() || (mode.is("Призраки 3") && alwaysShow.isEnabled());
    }

    private int pickColor(int index) {
        if (!mode.is("Градиент") && (!isGhostMode() || !autoThemeColor.isEnabled())) {
            return autoThemeColor.isEnabled() ? ThemeManager.getInstance().getAccentColor(index) : customColor.getColor();
        }
        return ArgbUtil.themeWave(index * 3);
    }

    private boolean isGhostMode() {
        return mode.is("Призраки 1") || mode.is("Призраки 2") || mode.is("Призраки 3");
    }

    private static boolean normalizeVec4(Vector4f v) {
        float len = Mth.sqrt(v.x * v.x + v.y * v.y + v.z * v.z);
        if (len < 1.0E-5f || !Float.isFinite(len)) return false;
        v.x /= len; v.y /= len; v.z /= len;
        return true;
    }

    private static void emitVert(BufferBuilder buf, Matrix4f m, float x, float y, float z, Vector4f col) {
        buf.addVertex(m, x, y, z).setUv(0.0f, 0.0f).setColor(col.x, col.y, col.z, col.w);
    }

    private static int emitBloomQuad(BufferBuilder buf, Matrix4f m, float halfSize, int argb) {
        Vector4f c = ArgbUtil.toVector4f(argb);
        buf.addVertex(m, -halfSize, -halfSize, 0.0f).setUv(0.0f, 0.0f).setColor(c.x, c.y, c.z, c.w);
        buf.addVertex(m, -halfSize, halfSize, 0.0f).setUv(0.0f, 1.0f).setColor(c.x, c.y, c.z, c.w);
        buf.addVertex(m, halfSize, halfSize, 0.0f).setUv(1.0f, 1.0f).setColor(c.x, c.y, c.z, c.w);
        buf.addVertex(m, halfSize, -halfSize, 0.0f).setUv(1.0f, 0.0f).setColor(c.x, c.y, c.z, c.w);
        return 4;
    }

    private void submitPipeline(BufferBuilder buf, int vertCount, RenderPipeline pipeline, String label, boolean textured) {
        MeshData mesh;
        try {
            mesh = buf.build();
        } catch (Throwable ignored) {
            return;
        }
        if (mesh == null) return;
        if (vertCount <= 0) {
            mesh.close();
            return;
        }

        RenderTarget mainTarget = mc.gameRenderer != null ? mc.gameRenderer.mainRenderTarget() : null;
        if (mainTarget == null || mainTarget.getColorTextureView() == null) {
            mesh.close();
            return;
        }

        AbstractTexture tex = textured ? mc.getTextureManager().getTexture(BLOOM_TEX) : null;
        try (mesh; GpuBuffer vb = RenderSystem.getDevice().createBuffer(() -> label + "Vertices", GpuBuffer.USAGE_VERTEX, mesh.vertexBuffer())) {
            DrawState state = mesh.drawState();
            AutoStorageIndexBuffer seq = RenderSystem.getSequentialBuffer(state.primitiveTopology());
            GpuBuffer ib = seq.getBuffer(state.indexCount());
            CommandEncoder enc = RenderSystem.getDevice().createCommandEncoder();
            try (RenderPass pass = enc.createRenderPass(
                    () -> label,
                    mainTarget.getColorTextureView(),
                    Optional.empty(),
                    mainTarget.getDepthTextureView(),
                    OptionalDouble.empty()
            )) {
                RenderSystem.bindDefaultUniforms(pass);
                pass.setPipeline(PipelineBuilder.compile(pipeline));
                if (tex != null) {
                    pass.setUniform("Sampler0", tex.getTextureView(), tex.getSampler());
                }
                pass.setVertexBuffer(0, vb.slice());
                pass.setIndexBuffer(ib, seq.type());
                pass.drawIndexed(state.indexCount(), 1, 0, 0, 0);
            }
        } catch (Throwable ignored) {
        }
    }

    private record HeadPose(float bodyYaw, float xRot, float swimShift, float scale) {}
}
