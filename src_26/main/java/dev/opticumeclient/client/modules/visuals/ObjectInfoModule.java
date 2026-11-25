package dev.opticumeclient.client.modules.visuals;

import com.mojang.blaze3d.vertex.BufferBuilder;
import dev.opticumeclient.client.modules.Category;
import dev.opticumeclient.client.modules.Module;
import dev.opticumeclient.client.modules.visuals.targetesp.TargetEspFrame;
import dev.opticumeclient.client.render.ColorUtils;
import dev.opticumeclient.client.render.Render2DHelper;
import dev.opticumeclient.client.render.Render3DHelper;
import dev.opticumeclient.client.render.ThemeManager;
import dev.opticumeclient.client.render.WorldPrimitiveRenderer;
import dev.opticumeclient.client.setting.BooleanSetting;
import dev.opticumeclient.client.setting.NumberSetting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/**
 * Displays 3D radius rings and 2D countdown timers for anarchy custom items / zones
 * (Trap, Plast, Disorientation, Aura Sphere, Stan, Snowball frost, etc.).
 */
public class ObjectInfoModule extends Module {
    private static ObjectInfoModule instance;

    private record TrackedZone(String title, Vec3 pos, float radius, long startMs, long durationMs, int color) {
        float remainingSec(long now) {
            return Math.max(0.0f, (startMs + durationMs - now) / 1000.0f);
        }

        float progress(long now) {
            return Mth.clamp((now - startMs) / (float) Math.max(1L, durationMs), 0.0f, 1.0f);
        }

        boolean expired(long now) {
            return now >= startMs + durationMs;
        }
    }

    private final BooleanSetting showCircles = new BooleanSetting("3D Circles", "Отображать 3D-радиус действия объектов в мире", true);
    private final BooleanSetting showTimers = new BooleanSetting("2D Timers", "Отображать 2D-таймер и прогресс над объектом", true);
    private final NumberSetting scale = new NumberSetting("Scale", "Масштаб 2D-таймеров", 1.0, 0.6, 1.6, 0.05);

    private final List<TrackedZone> zones = new ArrayList<>();
    private int lastUseCooldownId = -1;

    public ObjectInfoModule() {
        super("ObjectInfo", "Таймеры и радиусы трапок, пластов, дезориентаций и сфер (FT/HW)", Category.VISUALS);
        instance = this;
        addSetting(showCircles);
        addSetting(showTimers);
        addSetting(scale);
    }

    public static ObjectInfoModule getInstance() {
        return instance;
    }

    public void addZone(String title, Vec3 pos, float radius, float durationSec, int color) {
        if (pos == null) return;
        long now = System.currentTimeMillis();
        zones.removeIf(z -> z.pos().distanceToSqr(pos) < 4.0 && z.title().equals(title));
        zones.add(new TrackedZone(title, pos, radius, now, Math.round(durationSec * 1000.0f), color));
        if (zones.size() > 24) {
            zones.remove(0);
        }
    }

    public void onChatMessage(String cleanText) {
        if (!isEnabled() || cleanText == null || mc.player == null) return;
        String low = cleanText.toLowerCase(Locale.ROOT);
        if (low.contains("трапк") && (low.contains("использ") || low.contains("активир") || low.contains("поставил"))) {
            addZone("Трапка", mc.player.position(), 5.0f, 15.0f, 0xFFFF5252);
        } else if (low.contains("пласт") && (low.contains("использ") || low.contains("активир"))) {
            addZone("Пласт", mc.player.position(), 5.0f, 15.0f, 0xFFF59E0B);
        } else if (low.contains("дезориент") && (low.contains("использ") || low.contains("активир") || low.contains("радиус"))) {
            addZone("Дезориентация", mc.player.position(), 10.0f, 10.0f, 0xFFA855F7);
        } else if (low.contains("явная пыль") || low.contains("пыль")) {
            addZone("Явная пыль", mc.player.position(), 10.0f, 8.0f, 0xFF38BDF8);
        } else if (low.contains("огненн") && low.contains("смерч")) {
            addZone("Смерч", mc.player.position(), 10.0f, 10.0f, 0xFFFF6D28);
        }
    }

    public void onPlayerRightClick(ItemStack stack, Player player) {
        if (!isEnabled() || stack == null || stack.isEmpty() || player == null) return;
        String name = stack.getHoverName().getString().toLowerCase(Locale.ROOT);
        Vec3 pos = player.position();
        if (name.contains("трапк") || stack.is(Items.NETHERITE_SCRAP)) {
            if (name.contains("трапк")) addZone("Трапка", pos, 5.0f, 15.0f, 0xFFFF5252);
        } else if (name.contains("пласт") || (stack.is(Items.DRIED_KELP) && name.contains("пласт"))) {
            addZone("Пласт", pos, 5.0f, 15.0f, 0xFFF59E0B);
        } else if (name.contains("дезориент")) {
            addZone("Дезориентация", pos, 10.0f, 10.0f, 0xFFA855F7);
        } else if (name.contains("смерч")) {
            addZone("Смерч", pos, 10.0f, 10.0f, 0xFFFF6D28);
        } else if (name.contains("явная пыль")) {
            addZone("Явная пыль", pos, 10.0f, 8.0f, 0xFF38BDF8);
        } else if (name.contains("стан") || name.contains("сфера")) {
            addZone("Сфера/Стан", pos, 6.0f, 12.0f, 0xFF4ADE80);
        }
    }

    @Override
    public void onTick() {
        long now = System.currentTimeMillis();
        zones.removeIf(z -> z.expired(now));

        if (mc.player != null && mc.player.isUsingItem()) {
            InteractionHand hand = mc.player.getUsedItemHand();
            ItemStack used = mc.player.getItemInHand(hand);
            if (!used.isEmpty() && mc.player.tickCount != lastUseCooldownId) {
                lastUseCooldownId = mc.player.tickCount;
                if (mc.player.getUseItemRemainingTicks() == 1) {
                    onPlayerRightClick(used, mc.player);
                }
            }
        }
    }

    @Override
    public void onDisable() {
        super.onDisable();
        zones.clear();
    }

    public void renderWorld(TargetEspFrame frame) {
        if (!isEnabled() || !showCircles.isEnabled() || zones.isEmpty() || mc.level == null) return;

        long now = System.currentTimeMillis();
        Vec3 cam = frame.cameraPos();
        Matrix4f pose = frame.poseStack().last().pose();
        BufferBuilder lineBuf = WorldPrimitiveRenderer.beginLines();
        boolean any = false;

        int segments = 48;
        for (TrackedZone z : zones) {
            if (z.expired(now)) continue;
            float fade = 1.0f - z.progress(now) * 0.35f;
            int col = ColorUtils.withAlphaFactor(z.color(), fade);
            double cx = z.pos().x - cam.x;
            double cy = z.pos().y + 0.08 - cam.y;
            double cz = z.pos().z - cam.z;
            float r = z.radius();

            for (int i = 0; i < segments; i++) {
                double a1 = (Math.PI * 2.0 * i) / segments;
                double a2 = (Math.PI * 2.0 * (i + 1)) / segments;
                lineBuf.addVertex(pose, (float) (cx + Math.cos(a1) * r), (float) cy, (float) (cz + Math.sin(a1) * r)).setColor(col);
                lineBuf.addVertex(pose, (float) (cx + Math.cos(a2) * r), (float) cy, (float) (cz + Math.sin(a2) * r)).setColor(col);
            }
            // Center vertical marker
            lineBuf.addVertex(pose, (float) cx, (float) cy, (float) cz).setColor(col);
            lineBuf.addVertex(pose, (float) cx, (float) (cy + 2.2), (float) cz).setColor(ColorUtils.withAlphaFactor(col, 0.25f));
            any = true;
        }

        if (any) {
            WorldPrimitiveRenderer.drawLines(lineBuf, false);
        } else {
            WorldPrimitiveRenderer.discardQuietly(lineBuf);
        }
    }

    @Override
    public void onRender2D(GuiGraphicsExtractor context, float tickDelta) {
        if (!isEnabled() || !showTimers.isEnabled() || zones.isEmpty() || mc.player == null) return;

        long now = System.currentTimeMillis();
        float sc = scale.getFloatValue();
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();

        Iterator<TrackedZone> it = zones.iterator();
        while (it.hasNext()) {
            TrackedZone z = it.next();
            if (z.expired(now)) continue;
            float[] screen = Render3DHelper.worldToScreen(z.pos().add(0.0, 2.35, 0.0));
            if (screen == null || screen[0] < 0 || screen[0] > sw || screen[1] < 0 || screen[1] > sh) continue;

            float rem = z.remainingSec(now);
            String text = z.title() + " §f" + String.format(Locale.US, "%.1fs", rem);
            float tw = Render2DHelper.getTextWidth(text, 0.75f * sc);
            float pw = tw + 12.0f * sc;
            float ph = 15.0f * sc;
            float px = screen[0] - pw * 0.5f;
            float py = screen[1] - ph * 0.5f;

            Render2DHelper.drawRoundedRectFloat(context, px, py, pw, ph, 3.5f * sc, 0xCC0C0E17);
            Render2DHelper.drawRoundedOutlineFloat(context, px, py, pw, ph, 3.5f * sc, 0.8f, ColorUtils.withAlpha(z.color(), 180));
            float barW = (pw - 4.0f * sc) * (1.0f - z.progress(now));
            Render2DHelper.drawRoundedRectFloat(context, px + 2.0f * sc, py + ph - 2.4f * sc, Math.max(1.0f, barW), 1.4f * sc, 0.7f, z.color());
            Render2DHelper.drawScaledText(context, text, px + 6.0f * sc, py + 3.0f * sc, ThemeManager.getInstance().getAccentColor(0), 0.75f * sc, true);
        }
    }
}
