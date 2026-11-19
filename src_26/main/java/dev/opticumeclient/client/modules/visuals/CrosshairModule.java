package dev.opticumeclient.client.modules.visuals;

import dev.opticumeclient.client.modules.Category;
import dev.opticumeclient.client.modules.Module;
import dev.opticumeclient.client.render.ColorUtils;
import dev.opticumeclient.client.setting.BooleanSetting;
import dev.opticumeclient.client.setting.ColorSetting;
import dev.opticumeclient.client.setting.ModeSetting;
import dev.opticumeclient.client.setting.NumberSetting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Matrix3x2fStack;

/**
 * Desire-styled customizable pixel-sharp crosshair with dynamic attack spread and target hover tint.
 */
public class CrosshairModule extends Module {
    private static CrosshairModule instance;

    private final ModeSetting style = new ModeSetting("Стиль", "Форма прицела", "Desire", "Desire", "T-Style", "Dot");
    private final NumberSetting length = new NumberSetting("Длина", "Длина лучей прицела", 4.0, 1.0, 10.0, 0.1)
            .visibleWhen(() -> !style.is("Dot"));
    private final NumberSetting gap = new NumberSetting("Пробел", "Зазор от центра экрана", 1.0, 0.0, 8.0, 0.1)
            .visibleWhen(() -> !style.is("Dot"));
    private final NumberSetting thickness = new NumberSetting("Толщина", "Толщина линий или точки", 1.0, 1.0, 5.0, 0.1);
    private final BooleanSetting centerDot = new BooleanSetting("Точка", "Рисовать точку в центре прицела", false)
            .visibleWhen(() -> !style.is("Dot"));
    private final BooleanSetting dynamic = new BooleanSetting("Динамический", "Расширение при кд удара", true)
            .visibleWhen(() -> !style.is("Dot"));
    private final NumberSetting spread = new NumberSetting("Сила расширения", "Дистанция расширения при перезарядке", 10.0, 3.0, 20.0, 1.0)
            .visibleWhen(() -> !style.is("Dot") && dynamic.isEnabled());
    private final BooleanSetting hoverColor = new BooleanSetting("Цвет при наведении", "Менять цвет при наведении на цель", false);
    private final ColorSetting color = new ColorSetting("Цвет", "Основной цвет прицела", 0xFFFFFFFF, false);
    private final ColorSetting hoveredColor = new ColorSetting("Цвет при наведении (цвет)", "Цвет при наведении на сущность", 0xFFFF0000, false)
            .visibleWhen(hoverColor::isEnabled);

    private float hoverAnim;
    private long lastFrameNanos = System.nanoTime();

    public CrosshairModule() {
        super("Crosshair", "Кастомный прицел с динамическим расширением", Category.VISUALS);
        instance = this;
        addSetting(style);
        addSetting(length);
        addSetting(gap);
        addSetting(thickness);
        addSetting(centerDot);
        addSetting(dynamic);
        addSetting(spread);
        addSetting(hoverColor);
        addSetting(color);
        addSetting(hoveredColor);
    }

    public static CrosshairModule getInstance() {
        return instance;
    }

    @Override
    public void onRender2D(GuiGraphicsExtractor context, float tickDelta) {
        if (!isEnabled() || mc.player == null || mc.level == null || mc.getWindow() == null) return;
        if (mc.gui.hud.isHidden() || !mc.options.getCameraType().isFirstPerson()) return;
        if (mc.gui.screen() != null && !(mc.gui.screen() instanceof ChatScreen)) return;

        long now = System.nanoTime();
        float dt = Math.min((now - lastFrameNanos) / 1.0E9f, 0.1f);
        lastFrameNanos = now;

        boolean hovering = hoverColor.isEnabled() && mc.crosshairPickEntity instanceof LivingEntity;
        hoverAnim += ((hovering ? 1.0f : 0.0f) - hoverAnim) * (1.0f - (float) Math.exp(-10.0f * dt));

        double guiScale = Math.max(1.0, mc.getWindow().getGuiScale());
        int scale = Math.max(2, (int) Math.round(guiScale));
        int gapPx = Math.round(gap.getFloatValue() * scale);
        int lengthPx = Math.max(1, Math.round(length.getFloatValue() * scale));
        int thickPx = Math.max(1, Math.round(thickness.getFloatValue() * scale));

        if (dynamic.isEnabled()) {
            float strength = mc.player.getAttackStrengthScale(tickDelta);
            gapPx += Math.round((1.0f - strength) * spread.getFloatValue());
        }

        int cx = mc.getWindow().getWidth() / 2;
        int cy = mc.getWindow().getHeight() / 2;
        int half = thickPx / 2;

        int fillColor = ColorUtils.interpolateColor(color.getColor(), hoveredColor.getColor(), hoverAnim);
        int alpha = (fillColor >>> 24) & 0xFF;
        int outlineColor = alpha << 24;

        Matrix3x2fStack pose = context.pose();
        float toGui = (float) (1.0 / guiScale);

        pose.pushMatrix();
        pose.scale(toGui, toGui);

        if (style.is("Dot")) {
            int dotSize = Math.max(2, thickPx + 1);
            int halfDot = dotSize / 2;
            fillOutlined(context, cx - halfDot, cy - halfDot, dotSize, dotSize, fillColor, outlineColor);
        } else {
            boolean tStyle = style.is("T-Style");
            int[][] bars = tStyle
                    ? new int[][]{
                            {cx - gapPx - lengthPx, cy - half, lengthPx, thickPx},
                            {cx + gapPx + (thickPx % 2), cy - half, lengthPx, thickPx},
                            {cx - half, cy + gapPx + (thickPx % 2), thickPx, lengthPx}
                    }
                    : new int[][]{
                            {cx - gapPx - lengthPx, cy - half, lengthPx, thickPx},
                            {cx + gapPx + (thickPx % 2), cy - half, lengthPx, thickPx},
                            {cx - half, cy - gapPx - lengthPx, thickPx, lengthPx},
                            {cx - half, cy + gapPx + (thickPx % 2), thickPx, lengthPx}
                    };

            for (int[] bar : bars) {
                context.fill(bar[0] - 1, bar[1] - 1, bar[0] + bar[2] + 1, bar[1] + bar[3] + 1, outlineColor);
            }
            for (int[] bar : bars) {
                context.fill(bar[0], bar[1], bar[0] + bar[2], bar[1] + bar[3], fillColor);
            }

            if (centerDot.isEnabled()) {
                fillOutlined(context, cx - half, cy - half, thickPx, thickPx, fillColor, outlineColor);
            }
        }

        pose.popMatrix();
    }

    private static void fillOutlined(GuiGraphicsExtractor context, int x, int y, int w, int h, int fill, int outline) {
        context.fill(x - 1, y - 1, x + w + 1, y + h + 1, outline);
        context.fill(x, y, x + w, y + h, fill);
    }
}
