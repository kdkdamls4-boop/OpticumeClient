package dev.opticumeclient.client.modules.visuals.particles;

import com.mojang.blaze3d.vertex.BufferBuilder;
import dev.opticumeclient.client.modules.visuals.targetesp.TargetEspFrame;
import dev.opticumeclient.client.render.WorldPrimitiveRenderer;
import dev.opticumeclient.client.render.TextureAssets;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Ported from Desire ClientParticleRenderer -> adapted for sequential RenderPearl BufferPool batching.
 */
public final class ClientParticleRenderer {
    private final Vector3f right = new Vector3f();
    private final Vector3f up = new Vector3f();
    private final Quaternionf cameraRotation = new Quaternionf();

    public void render(TargetEspFrame frame, List<Identifier> textures, boolean glow, float sizeMultiplier) {
        ClientParticleManager manager = ClientParticleManager.getInstance();
        if (manager.isEmpty() || textures.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.gameRenderer == null) return;
        Camera camera = mc.gameRenderer.mainCamera();
        if (camera == null) return;

        manager.updateVisibility();

        Vec3 cam = frame.cameraPos();
        float partialTicks = frame.partialTicks();
        Matrix4f pose = frame.poseStack().last().pose();

        cameraRotation.set(camera.rotation());
        Vector3f baseRight = cameraRotation.transform(new Vector3f(1.0f, 0.0f, 0.0f));
        Vector3f baseUp = cameraRotation.transform(new Vector3f(0.0f, 1.0f, 0.0f));

        int slotCount = textures.size();
        @SuppressWarnings("unchecked")
        List<RenderItem>[] buckets = new List[slotCount];
        List<RenderItem> allVisible = glow ? new ArrayList<>() : null;

        for (ClientParticleManager.Entry entry : manager.entries()) {
            if (!entry.isVisible()) continue;
            ClientParticle particle = entry.getParticle();
            if (!particle.isAlive()) continue;

            float alpha = entry.alpha(partialTicks);
            if (alpha <= 0.001f) continue;
            float scale = particle.renderScale(partialTicks);
            if (scale <= 0.001f) continue;

            float angle = particle.getSpin() != 0.0f ? particle.renderAngle(partialTicks) : 0.0f;
            float half = entry.getSize() * scale * sizeMultiplier;
            float upX = baseUp.x;
            float upY = baseUp.y;
            float upZ = baseUp.z;
            if (angle != 0.0f) {
                float cos = (float) Math.cos(angle);
                float sin = (float) Math.sin(angle);
                upX = baseUp.x * cos - baseRight.x * sin;
                upY = baseUp.y * cos - baseRight.y * sin;
                upZ = baseUp.z * cos - baseRight.z * sin;
            }

            float cx = (float) (particle.renderX(partialTicks) - cam.x) + upX * half * 0.5f;
            float cy = (float) (particle.renderY(partialTicks) - cam.y) + upY * half * 0.5f;
            float cz = (float) (particle.renderZ(partialTicks) - cam.z) + upZ * half * 0.5f;

            int color = entry.getColor();
            float fade = alpha * 0.80f * alphaOf(color);
            RenderItem item = new RenderItem(cx, cy, cz, half, angle, color, fade);

            int slot = Math.min(slotCount - 1, (int) (entry.getTextureSeed() * slotCount));
            if (buckets[slot] == null) buckets[slot] = new ArrayList<>();
            buckets[slot].add(item);
            if (allVisible != null) allVisible.add(item);
        }

        // Sequential draws per texture to obey single BufferPool ByteBufferBuilder rule
        for (int slot = 0; slot < slotCount; slot++) {
            List<RenderItem> bucket = buckets[slot];
            if (bucket == null || bucket.isEmpty()) continue;

            BufferBuilder buf = WorldPrimitiveRenderer.beginTexturedQuads();
            for (RenderItem item : bucket) {
                orientBillboard(baseRight, baseUp, item.angle);
                int a = Math.clamp(Math.round(255.0f * item.fade), 0, 255);
                if (a > 1) {
                    quad(buf, pose, item.cx, item.cy, item.cz, item.half, withAlpha(item.color, a));
                }
            }
            WorldPrimitiveRenderer.drawTexturedQuads(buf, textures.get(slot), false, true);
        }

        if (glow && allVisible != null && !allVisible.isEmpty()) {
            BufferBuilder glowBuf = WorldPrimitiveRenderer.beginTexturedQuads();
            right.set(baseRight);
            up.set(baseUp);
            boolean emitted = false;
            for (RenderItem item : allVisible) {
                int a2 = Math.clamp(Math.round(72.0f * item.fade), 0, 255);
                int a4 = Math.clamp(Math.round(34.0f * item.fade), 0, 255);
                if (a2 > 1) {
                    quad(glowBuf, pose, item.cx, item.cy, item.cz, item.half * 2.1f, withAlpha(item.color, a2));
                    emitted = true;
                }
                if (a4 > 1) {
                    quad(glowBuf, pose, item.cx, item.cy, item.cz, item.half * 3.8f, withAlpha(item.color, a4));
                    emitted = true;
                }
            }
            if (emitted) {
                WorldPrimitiveRenderer.drawTexturedQuads(glowBuf, TextureAssets.DESIRE_PARTICLE_GLOW, true, true);
            } else {
                WorldPrimitiveRenderer.discardQuietly(glowBuf);
            }
        }
    }

    private void orientBillboard(Vector3f baseRight, Vector3f baseUp, float angle) {
        if (angle != 0.0f) {
            float cos = (float) Math.cos(angle);
            float sin = (float) Math.sin(angle);
            right.set(baseRight).mul(cos).add(baseUp.x * sin, baseUp.y * sin, baseUp.z * sin);
            up.set(baseUp).mul(cos).sub(baseRight.x * sin, baseRight.y * sin, baseRight.z * sin);
        } else {
            right.set(baseRight);
            up.set(baseUp);
        }
    }

    private void quad(BufferBuilder buffer, Matrix4f pose, float cx, float cy, float cz, float half, int color) {
        float rx = right.x * half, ry = right.y * half, rz = right.z * half;
        float ux = up.x * half, uy = up.y * half, uz = up.z * half;

        buffer.addVertex(pose, cx - rx - ux, cy - ry - uy, cz - rz - uz).setUv(0.0f, 1.0f).setColor(color);
        buffer.addVertex(pose, cx + rx - ux, cy + ry - uy, cz + rz - uz).setUv(1.0f, 1.0f).setColor(color);
        buffer.addVertex(pose, cx + rx + ux, cy + ry + uy, cz + rz + uz).setUv(1.0f, 0.0f).setColor(color);
        buffer.addVertex(pose, cx - rx + ux, cy - ry + uy, cz - rz + uz).setUv(0.0f, 0.0f).setColor(color);
    }

    private static float alphaOf(int color) {
        return ((color >>> 24) & 0xFF) / 255.0f;
    }

    private static int withAlpha(int color, int alpha) {
        return (Math.clamp(alpha, 0, 255) << 24) | (color & 0x00FFFFFF);
    }

    private record RenderItem(float cx, float cy, float cz, float half, float angle, int color, float fade) {}
}
