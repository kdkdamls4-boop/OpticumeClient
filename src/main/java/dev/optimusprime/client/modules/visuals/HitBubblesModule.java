package dev.optimusprime.client.modules.visuals;

import dev.optimusprime.client.modules.Category;
import dev.optimusprime.client.modules.Module;
import dev.optimusprime.client.render.ColorUtils;
import dev.optimusprime.client.render.Render3DHelper;
import dev.optimusprime.client.render.TextureAssets;
import dev.optimusprime.client.render.ThemeManager;
import dev.optimusprime.client.setting.NumberSetting;
import com.mojang.blaze3d.vertex.PoseStack; 
import net.minecraft.world.entity.Entity; 
import net.minecraft.world.entity.LivingEntity; 
import net.minecraft.world.phys.Vec3; 

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class HitBubblesModule extends Module {
    private final NumberSetting maxScale = new NumberSetting("Scale", "Максимальный размер 3D портала удара", 1.4, 0.7, 2.4, 0.1);
    private final NumberSetting durationMs = new NumberSetting("Duration", "Длительность анимации (мс)", 900.0, 400.0, 1800.0, 50.0);

    private final List<HitBubble> bubbles = new ArrayList<>();
    private final Map<Integer, Integer> prevHurtMap = new HashMap<>();

    public HitBubblesModule() {
        super("HitBubbles", "3D волновые порталы HitBubble на теле врага при попадании", Category.VISUALS);
        addSetting(maxScale);
        addSetting(durationMs);
        setEnabled(true);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.level == null) {
            bubbles.clear();
            prevHurtMap.clear();
            return;
        }

        long now = System.currentTimeMillis();
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity living) || entity == mc.player || !living.isAlive()) continue;
            if (mc.player.distanceTo(living) > 10.0f) continue;

            int lastHurt = prevHurtMap.getOrDefault(living.getId(), 0);
            int currHurt = living.hurtTime;
            prevHurtMap.put(living.getId(), currHurt);

            if (currHurt == 9 && lastHurt < 9) {
                Vec3 chestPos = living.position().add(0.0, living.getBbHeight() * 0.55, 0.0);
                bubbles.add(new HitBubble(chestPos, now, (bubbles.size() * 45) % 360));
                if (bubbles.size() > 20) {
                    bubbles.remove(0);
                }
            }
        }

        long maxLife = durationMs.getIntValue();
        bubbles.removeIf(b -> now - b.spawnTime > maxLife);
    }

    @Override
    public void onRender3D(PoseStack matrices, float tickDelta) {
        if (mc.player == null || mc.level == null || bubbles.isEmpty()) return;

        long now = System.currentTimeMillis();
        float maxLife = durationMs.getFloatValue();
        float targetScale = maxScale.getFloatValue();

        Iterator<HitBubble> it = bubbles.iterator();
        while (it.hasNext()) {
            HitBubble b = it.next();
            float t = (now - b.spawnTime) / maxLife;
            if (t < 0.0f || t >= 1.0f) {
                it.remove();
                continue;
            }

            // Smooth Ease-Out Cubic expansion from 0.2f to targetScale (1.4f)
            float inv = 1.0f - t;
            float easeOut = 1.0f - (inv * inv * inv);
            float scale = 0.2f + (targetScale - 0.2f) * easeOut;

            // Smooth alpha fade-out
            float alphaFactor = (1.0f - t) * (1.0f - t * 0.6f);
            int baseColor = ThemeManager.getInstance().getAccentColor(b.colorOffset);
            int color = ColorUtils.withAlphaFactor(baseColor, alphaFactor);

            float rot = b.colorOffset + t * 240.0f;
            Render3DHelper.queueBillboardSprite(b.pos, scale, TextureAssets.HITBUBBLE, color, rot, true);
        }
    }

    private record HitBubble(Vec3 pos, long spawnTime, int colorOffset) {}
}
