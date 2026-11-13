package dev.optimusprime.client.modules.visuals;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.optimusprime.client.modules.Category;
import dev.optimusprime.client.modules.Module;
import dev.optimusprime.client.render.ColorUtils;
import dev.optimusprime.client.render.Render3DHelper;
import dev.optimusprime.client.render.TextureAssets;
import dev.optimusprime.client.render.ThemeManager;
import dev.optimusprime.client.setting.ColorSetting;
import dev.optimusprime.client.setting.ModeSetting;
import dev.optimusprime.client.setting.NumberSetting;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class PopChamsModule extends Module {
    private static PopChamsModule INSTANCE;

    private final ModeSetting mode = new ModeSetting("Mode", "Стиль визуализации фантома", "Both", "Hologram Box", "Particles", "Both");
    private final ModeSetting colorMode = new ModeSetting("ColorMode", "Источник цвета", "Theme", "Theme", "Custom");
    private final ColorSetting customColor = new ColorSetting("CustomColor", "Пользовательский цвет", 0xCCFF2A85);
    private final NumberSetting duration = new NumberSetting("Duration", "Время жизни фантома в тиках", 35.0, 15.0, 60.0, 5.0);
    private final NumberSetting riseSpeed = new NumberSetting("RiseSpeed", "Скорость всплытия вверх", 0.035, 0.01, 0.10, 0.005);
    private final NumberSetting particles = new NumberSetting("Particles", "Число разлетающихся светлячков", 20.0, 5.0, 50.0, 5.0);
    private final NumberSetting size = new NumberSetting("Size", "Размер светящихся частиц", 0.20, 0.05, 0.50, 0.02);

    private static class PopParticle {
        Vec3 pos;
        Vec3 vel;
        float rotation;

        PopParticle(Vec3 pos, Vec3 vel, float rotation) {
            this.pos = pos;
            this.vel = vel;
            this.rotation = rotation;
        }
    }

    private static class TotemPopGhost {
        final AABB baseBox;
        final Vec3 origin;
        final int maxAge;
        final List<PopParticle> particles;
        int age = 0;
        float yOffset = 0.0f;

        TotemPopGhost(AABB baseBox, Vec3 origin, int maxAge, List<PopParticle> particles) {
            this.baseBox = baseBox;
            this.origin = origin;
            this.maxAge = maxAge;
            this.particles = particles;
        }
    }

    private final List<TotemPopGhost> activeGhosts = new ArrayList<>();

    public PopChamsModule() {
        super("PopChams", "Светящийся фантом и взрыв неоновых частиц при срабатывании Тотема Бессмертия", Category.VISUALS);
        addSetting(mode);
        addSetting(colorMode);
        addSetting(customColor);
        addSetting(duration);
        addSetting(riseSpeed);
        addSetting(particles);
        addSetting(size);
        INSTANCE = this;
        setEnabled(false);
    }

    public static PopChamsModule getInstance() {
        return INSTANCE;
    }

    @Override
    public String getDisplaySuffix() {
        return mode.getMode();
    }

    public void onTotemPop(Entity entity) {
        if (!isEnabled() || entity == null) return;

        AABB box = entity.getBoundingBox();
        Vec3 center = entity.position().add(0.0, entity.getBbHeight() * 0.5, 0.0);
        int count = particles.getIntValue();
        List<PopParticle> partList = new ArrayList<>(count);

        for (int i = 0; i < count; i++) {
            double vx = (Math.random() - 0.5) * 0.18;
            double vy = Math.random() * 0.16 + 0.04;
            double vz = (Math.random() - 0.5) * 0.18;
            float rot = (float) (Math.random() * 360.0);
            partList.add(new PopParticle(center, new Vec3(vx, vy, vz), rot));
        }

        activeGhosts.add(new TotemPopGhost(box, center, duration.getIntValue(), partList));
    }

    @Override
    public void onTick() {
        if (activeGhosts.isEmpty()) return;

        Iterator<TotemPopGhost> it = activeGhosts.iterator();
        while (it.hasNext()) {
            TotemPopGhost ghost = it.next();
            ghost.age++;
            ghost.yOffset += riseSpeed.getFloatValue();

            for (PopParticle p : ghost.particles) {
                p.pos = p.pos.add(p.vel);
                p.vel = new Vec3(p.vel.x * 0.94, p.vel.y * 0.94 - 0.003, p.vel.z * 0.94);
                p.rotation = (p.rotation + 4.0f) % 360.0f;
            }

            if (ghost.age >= ghost.maxAge) {
                it.remove();
            }
        }
    }

    @Override
    public void onRender3D(PoseStack matrices, float tickDelta) {
        if (!isEnabled() || activeGhosts.isEmpty()) return;

        int baseColor = colorMode.is("Theme")
                ? ThemeManager.getInstance().getAccentColor(0)
                : customColor.getColor();

        boolean doBox = mode.is("Hologram Box") || mode.is("Both");
        boolean doParticles = mode.is("Particles") || mode.is("Both");
        float partSize = size.getFloatValue();

        for (TotemPopGhost ghost : activeGhosts) {
            float fade = 1.0f - Math.min(1.0f, (ghost.age + tickDelta) / ghost.maxAge);
            int ghostColor = ColorUtils.withAlphaFactor(baseColor, Math.max(0.05f, fade));

            if (doBox) {
                float yOff = ghost.yOffset + riseSpeed.getFloatValue() * tickDelta;
                AABB shiftedBox = ghost.baseBox.move(0.0, yOff, 0.0);
                Render3DHelper.drawBoxColor(shiftedBox, ghostColor, 1.8f, true);
            }

            if (doParticles) {
                for (PopParticle p : ghost.particles) {
                    Render3DHelper.queueBillboardSprite(p.pos, partSize * fade, TextureAssets.GLOW_RADIAL, ghostColor, p.rotation, true);
                }
            }
        }
    }
}
