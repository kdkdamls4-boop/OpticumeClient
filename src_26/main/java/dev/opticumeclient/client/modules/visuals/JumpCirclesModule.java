package dev.opticumeclient.client.modules.visuals;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.UniformType;
import dev.opticumeclient.client.modules.Category;
import dev.opticumeclient.client.modules.Module;
import dev.opticumeclient.client.modules.visuals.ambience.WorldRenderBridge;
import dev.opticumeclient.client.modules.visuals.targetesp.PipelineBuilder;
import dev.opticumeclient.client.modules.visuals.targetesp.TargetEspFrame;
import dev.opticumeclient.client.render.ColorUtils;
import dev.opticumeclient.client.render.TextureAssets;
import dev.opticumeclient.client.render.Theme;
import dev.opticumeclient.client.render.ThemeManager;
import dev.opticumeclient.client.render.WorldPrimitiveRenderer;
import dev.opticumeclient.client.setting.ModeSetting;
import dev.opticumeclient.client.setting.NumberSetting;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

/**
 * Desire-ported Jump Effect (depth-reconstructed shader shockwave with chromatic aberration & glow)
 * plus 3D world-space Desire Circle & Portal rings.
 */
public class JumpCirclesModule extends Module {
    private static final int MAX_RIPPLES = 8;
    private static final int UBO_SIZE = 464; // 2*mat4 (128) + 21*vec4 (336)

    private static final RenderPipeline JUMP_EFFECT_PIPELINE = PipelineBuilder.create()
            .location("opticumeclient/jump_effect")
            .vertexShader(WorldRenderBridge.FULLSCREEN_QUAD_VSH)
            .fragmentShader(Identifier.fromNamespaceAndPath("opticumeclient", "core/post/jump_effect"))
            .depthTest(CompareOp.ALWAYS_PASS)
            .writeDepth(false)
            .cull(false)
            .noBlend()
            .vertexFormat(PipelineBuilder.EMPTY_VERTEX_FORMAT, PrimitiveTopology.TRIANGLES)
            .uniform("JumpData", UniformType.UNIFORM_BUFFER)
            .sampler("SceneSampler")
            .sampler("DepthSampler")
            .build();

    private static JumpCirclesModule instance;

    private final ModeSetting style = new ModeSetting(
            "Style",
            "Стиль эффекта прыжка (Desire Effect = полноэкранный шейдер)",
            "Desire Effect",
            "Desire Effect", "Desire Circle", "Rune Ring", "Neon Portal", "Double Wave"
    );
    private final NumberSetting maxRadius = new NumberSetting("Radius", "Максимальный радиус раскрытия волны/круга", 2.2, 0.8, 5.0, 0.1);
    private final NumberSetting amplitude = new NumberSetting("Amplitude", "Сила искажения пространства (Desire Effect)", 1.0, 0.1, 3.0, 0.05)
            .visibleWhen(() -> style.is("Desire Effect"));
    private final NumberSetting aberration = new NumberSetting("Aberration", "Хроматическая аберрация волны (Desire Effect)", 20.0, 0.0, 40.0, 1.0)
            .visibleWhen(() -> style.is("Desire Effect"));
    private final NumberSetting duration = new NumberSetting("Duration", "Время жизни круга/волны (сек)", 1.35, 0.6, 4.5, 0.05);
    private final NumberSetting spinSpeed = new NumberSetting("SpinSpeed", "Скорость вращения 3D-кругов", 1.5, 0.2, 4.0, 0.1)
            .visibleWhen(() -> !style.is("Desire Effect"));

    private final List<JumpCircle> circles = new ArrayList<>();
    private boolean groundKnown = false;
    private boolean wasOnGround = true;

    private TextureTarget sceneCopy;
    private GpuBuffer uniforms;

    public JumpCirclesModule() {
        super("JumpCircles", "Волна искажения прыжка (Desire Effect) и 3D-круги под ногами", Category.VISUALS);
        instance = this;
        addSetting(style);
        addSetting(maxRadius);
        addSetting(amplitude);
        addSetting(aberration);
        addSetting(duration);
        addSetting(spinSpeed);
        setEnabled(false);
    }

    public static JumpCirclesModule getInstance() {
        return instance;
    }

    @Override
    public String getDisplaySuffix() {
        return style.getMode();
    }

    public boolean isPostFxActive() {
        return isEnabled() && style.is("Desire Effect") && !circles.isEmpty();
    }

    @Override
    public void onDisable() {
        circles.clear();
        groundKnown = false;
        super.onDisable();
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.level == null) {
            circles.clear();
            groundKnown = false;
            wasOnGround = true;
            return;
        }

        boolean onGround = mc.player.onGround();
        long now = System.currentTimeMillis();
        if (groundKnown && wasOnGround && !onGround && mc.player.getDeltaMovement().y > 0.01) {
            boolean recent = !circles.isEmpty() && (now - circles.get(circles.size() - 1).spawnTime) < 110L;
            if (!recent) {
                Vec3 spawnPos = new Vec3(mc.player.getX(), mc.player.getY() + 0.02, mc.player.getZ());
                circles.add(new JumpCircle(spawnPos, now));
                if (circles.size() > MAX_RIPPLES) {
                    circles.remove(0);
                }
            }
        }
        wasOnGround = onGround;
        groundKnown = true;

        long maxLifeMs = Math.max(400L, (long) (duration.getValue() * 1000.0));
        circles.removeIf(c -> now - c.spawnTime > maxLifeMs);
    }

    /**
     * Desire JumpEffectModule port: screen-space depth-reconstructed shockwave distortion pass.
     */
    public void renderPostFx(CameraRenderState camera) {
        if (!isEnabled() || !style.is("Desire Effect") || circles.isEmpty() || camera == null) {
            return;
        }
        if (mc.gameRenderer == null || mc.level == null || camera.projectionMatrix == null || camera.viewRotationMatrix == null) {
            return;
        }
        RenderTarget main = mc.gameRenderer.mainRenderTarget();
        if (main == null || main.width <= 1 || main.height <= 1
                || main.getColorTextureView() == null || main.getDepthTextureView() == null) {
            return;
        }

        sceneCopy = WorldRenderBridge.ensureColorTarget(sceneCopy, "opticume-jump-scene", main.width, main.height);
        if (!WorldRenderBridge.copyMainColor(main, sceneCopy)) {
            return;
        }

        uniforms = WorldRenderBridge.ensureUniformBuffer(uniforms, "OpticumeJumpEffectUbo", UBO_SIZE);

        long now = System.currentTimeMillis();
        float maxLifeMs = Math.max(400.0f, (float) (duration.getValue() * 1000.0));
        float maxRad = maxRadius.getFloatValue();
        boolean reverseZ = WorldRenderBridge.isReverseZ();

        Matrix4f mvp = new Matrix4f(camera.projectionMatrix).mul(camera.viewRotationMatrix);
        Matrix4f invMvp = new Matrix4f(mvp).invert();

        Theme theme = ThemeManager.getInstance().getCurrentTheme();
        int c1 = theme.getPrimaryColor();
        int c2 = theme.getSecondaryColor();
        float tShader = WorldRenderBridge.shaderTimeSeconds();

        WorldRenderBridge.writeUniforms(uniforms, UBO_SIZE, b -> {
            b.putMat4f(invMvp);
            b.putMat4f(mvp);
            b.putVec4(0.38f, amplitude.getFloatValue(), reverseZ ? 1.0f : 0.0f, aberration.getFloatValue());
            b.putVec4(WorldRenderBridge.red(c1), WorldRenderBridge.green(c1), WorldRenderBridge.blue(c1), 1.0f);
            b.putVec4(WorldRenderBridge.red(c2), WorldRenderBridge.green(c2), WorldRenderBridge.blue(c2), 1.0f);
            b.putVec4((float) Math.sin(tShader), (float) Math.cos(tShader), 0.0f, 0.0f);

            int count = circles.size();
            for (int i = 0; i < MAX_RIPPLES; i++) {
                if (i < count) {
                    JumpCircle c = circles.get(i);
                    float p = Math.max(0.0f, Math.min(1.0f, (now - c.spawnTime) / maxLifeMs));
                    float inv = 1.0f - p;
                    float rad = maxRad * (1.0f - inv * inv * inv);
                    b.putVec4(
                            (float) (c.pos.x - camera.pos.x),
                            (float) (c.pos.y - camera.pos.y),
                            (float) (c.pos.z - camera.pos.z),
                            rad
                    );
                } else {
                    b.putVec4(0.0f, 0.0f, 0.0f, 0.0f);
                }
            }
            for (int i = 0; i < MAX_RIPPLES; i++) {
                if (i < count) {
                    JumpCircle c = circles.get(i);
                    float p = Math.max(0.0f, Math.min(1.0f, (now - c.spawnTime) / maxLifeMs));
                    b.putVec4(1.0f - p, 0.0f, 0.0f, 0.0f);
                } else {
                    b.putVec4(0.0f, 0.0f, 0.0f, 0.0f);
                }
            }
            b.putVec4(reverseZ ? 1.0f : 0.0f, 0.0f, 0.0f, 0.0f);
        });

        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "OpticumeJumpEffect",
                main.getColorTextureView(),
                Optional.empty()
        )) {
            pass.setPipeline(PipelineBuilder.compile(JUMP_EFFECT_PIPELINE));
            pass.setUniform("JumpData", uniforms);
            pass.setUniform("SceneSampler", sceneCopy.getColorTextureView(), WorldRenderBridge.linearSampler());
            pass.setUniform("DepthSampler", main.getDepthTextureView(), WorldRenderBridge.nearestSampler());
            pass.draw(3, 1, 0, 0);
        } catch (Throwable ignored) {
        }
    }

    /**
     * World-space 3D rendering for Desire Circle / Rune Ring / Neon Portal / Double Wave modes.
     */
    public void renderWorld(TargetEspFrame frame) {
        if (!isEnabled() || style.is("Desire Effect") || mc.player == null || mc.level == null || circles.isEmpty()) {
            return;
        }

        long now = System.currentTimeMillis();
        float maxLifeMs = Math.max(400.0f, (float) (duration.getValue() * 1000.0));
        float targetMaxRad = maxRadius.getFloatValue();
        float spin = spinSpeed.getFloatValue();
        String currentStyle = style.getMode();
        Vec3 cam = frame.cameraPos();
        Matrix4f pose = frame.poseStack().last().pose();

        if ("Desire Circle".equals(currentStyle)) {
            renderDesireTextureCircles(pose, cam, now, maxLifeMs, targetMaxRad, spin);
            return;
        }

        BufferBuilder lineBuf = WorldPrimitiveRenderer.beginLines();
        boolean anyLine = false;

        for (JumpCircle c : circles) {
            float t = (now - c.spawnTime) / maxLifeMs;
            if (t < 0.0f || t >= 1.0f) continue;

            float inv = 1.0f - t;
            float easeOut = 1.0f - inv * inv * inv * inv;
            float rad = Math.max(0.08f, targetMaxRad * easeOut);

            float alphaFactor = t < 0.12f ? (t / 0.12f) : (float) Math.pow(1.0f - (t - 0.12f) / 0.88f, 2.0);
            if (alphaFactor <= 0.02f) continue;

            double rot = t * Math.PI * 2.0 * spin;
            double relX = c.pos.x - cam.x;
            double relY = c.pos.y - cam.y + 0.02;
            double relZ = c.pos.z - cam.z;

            int segments = 48;
            double innerRatio = "Rune Ring".equals(currentStyle) ? 0.68 : 0.78;
            double innerRad = rad * innerRatio;

            for (int i = 0; i < segments; i++) {
                double a1 = rot + (i * Math.PI * 2.0) / segments;
                double a2 = rot + ((i + 1) * Math.PI * 2.0) / segments;

                int baseColor = ThemeManager.getInstance().getAccentColor(i * 7);
                int outerColor = ColorUtils.withAlphaFactor(baseColor, alphaFactor * 0.95f);
                int innerColor = ColorUtils.withAlphaFactor(baseColor, alphaFactor * 0.45f);

                float ox1 = (float) (relX + Math.cos(a1) * rad);
                float oz1 = (float) (relZ + Math.sin(a1) * rad);
                float ox2 = (float) (relX + Math.cos(a2) * rad);
                float oz2 = (float) (relZ + Math.sin(a2) * rad);
                float ix1 = (float) (relX + Math.cos(a1) * innerRad);
                float iz1 = (float) (relZ + Math.sin(a1) * innerRad);
                float ix2 = (float) (relX + Math.cos(a2) * innerRad);
                float iz2 = (float) (relZ + Math.sin(a2) * innerRad);
                float fy = (float) relY;

                lineBuf.addVertex(pose, ox1, fy, oz1).setColor(outerColor);
                lineBuf.addVertex(pose, ox2, fy, oz2).setColor(outerColor);
                lineBuf.addVertex(pose, ix1, fy, iz1).setColor(innerColor);
                lineBuf.addVertex(pose, ix2, fy, iz2).setColor(innerColor);
                anyLine = true;

                if ("Rune Ring".equals(currentStyle) && i % 3 == 0) {
                    lineBuf.addVertex(pose, ix1, fy, iz1).setColor(innerColor);
                    lineBuf.addVertex(pose, ox1, fy, oz1).setColor(innerColor);
                } else if ("Double Wave".equals(currentStyle)) {
                    double wRad = rad * 0.52;
                    float wx1 = (float) (relX + Math.cos(-a1) * wRad);
                    float wz1 = (float) (relZ + Math.sin(-a1) * wRad);
                    float wx2 = (float) (relX + Math.cos(-a2) * wRad);
                    float wz2 = (float) (relZ + Math.sin(-a2) * wRad);
                    int waveCol = ColorUtils.withAlphaFactor(baseColor, alphaFactor * 0.72f);
                    lineBuf.addVertex(pose, wx1, fy, wz1).setColor(waveCol);
                    lineBuf.addVertex(pose, wx2, fy, wz2).setColor(waveCol);
                }
            }
        }

        if (anyLine) {
            WorldPrimitiveRenderer.drawLines(lineBuf, true);
        } else {
            WorldPrimitiveRenderer.discardQuietly(lineBuf);
        }

        // Draw glow rune sprites sequentially so BufferPool never overlaps with lineBuf
        Quaternionf camOrient = mc.gameRenderer != null && mc.gameRenderer.mainCamera() != null
                ? new Quaternionf(mc.gameRenderer.mainCamera().rotation())
                : new Quaternionf();
        BufferBuilder spriteBuf = WorldPrimitiveRenderer.beginTexturedQuads();
        boolean anySprite = false;

        for (JumpCircle c : circles) {
            float t = (now - c.spawnTime) / maxLifeMs;
            if (t < 0.0f || t >= 1.0f) continue;

            float inv = 1.0f - t;
            float easeOut = 1.0f - inv * inv * inv * inv;
            float rad = Math.max(0.08f, targetMaxRad * easeOut);
            float alphaFactor = t < 0.12f ? (t / 0.12f) : (float) Math.pow(1.0f - (t - 0.12f) / 0.88f, 2.0);
            if (alphaFactor <= 0.02f) continue;

            double rot = t * Math.PI * 2.0 * spin;
            double relX = c.pos.x - cam.x;
            double relY = c.pos.y - cam.y + 0.02;
            double relZ = c.pos.z - cam.z;

            int runePoints = 8;
            for (int r = 0; r < runePoints; r++) {
                double angle = rot * 1.35 + (r * Math.PI * 2.0) / runePoints;
                int runeCol = ColorUtils.withAlphaFactor(ThemeManager.getInstance().getAccentColor(r * 45), alphaFactor * 0.9f);
                double rx = relX + Math.cos(angle) * rad;
                double rz = relZ + Math.sin(angle) * rad;
                WorldPrimitiveRenderer.glowSprite(spriteBuf, pose, rx, relY + 0.02, rz, camOrient, 0.36f, runeCol);
                anySprite = true;
            }
        }

        if (anySprite) {
            WorldPrimitiveRenderer.drawTexturedQuads(spriteBuf, TextureAssets.DESIRE_PARTICLE_GLOW, true, true);
        } else {
            WorldPrimitiveRenderer.discardQuietly(spriteBuf);
        }
    }

    private void renderDesireTextureCircles(Matrix4f pose, Vec3 cam, long now, float maxLifeMs, float maxRad, float spin) {
        BufferBuilder buf = WorldPrimitiveRenderer.beginTexturedQuads();
        boolean any = false;

        for (JumpCircle c : circles) {
            float t = (now - c.spawnTime) / maxLifeMs;
            if (t < 0.0f || t >= 1.0f) continue;

            float inv = 1.0f - t;
            float rad = Math.max(0.15f, maxRad * (1.0f - inv * inv * inv));
            float alpha = t < 0.14f ? (t / 0.14f) : (float) Math.pow(1.0f - (t - 0.14f) / 0.86f, 1.8);
            if (alpha <= 0.02f) continue;

            double angle = t * Math.PI * 2.0 * spin;
            float cos = (float) Math.cos(angle) * rad;
            float sin = (float) Math.sin(angle) * rad;
            float cx = (float) (c.pos.x - cam.x);
            float cy = (float) (c.pos.y - cam.y + 0.018);
            float cz = (float) (c.pos.z - cam.z);

            int c1 = ColorUtils.withAlphaFactor(ThemeManager.getInstance().getAccentColor(0), alpha * 0.95f);
            int c2 = ColorUtils.withAlphaFactor(ThemeManager.getInstance().getAccentColor(90), alpha * 0.95f);

            // Rotating horizontal quad anchored on ground Y
            buf.addVertex(pose, cx - cos + sin, cy, cz - sin - cos).setUv(0.0f, 0.0f).setColor(c1);
            buf.addVertex(pose, cx + cos + sin, cy, cz + sin - cos).setUv(1.0f, 0.0f).setColor(c2);
            buf.addVertex(pose, cx + cos - sin, cy, cz + sin + cos).setUv(1.0f, 1.0f).setColor(c1);
            buf.addVertex(pose, cx - cos - sin, cy, cz - sin + cos).setUv(0.0f, 1.0f).setColor(c2);
            any = true;
        }

        if (any) {
            WorldPrimitiveRenderer.drawTexturedQuads(buf, TextureAssets.DESIRE_JUMP_RING, true, true);
        } else {
            WorldPrimitiveRenderer.discardQuietly(buf);
        }
    }

    public void releaseGpuResources() {
        if (sceneCopy != null) {
            sceneCopy.destroyBuffers();
            sceneCopy = null;
        }
        if (uniforms != null && !uniforms.isClosed()) {
            uniforms.close();
            uniforms = null;
        }
    }

    private record JumpCircle(Vec3 pos, long spawnTime) {}
}
