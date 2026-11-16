package dev.opticumeclient.client.modules.visuals;

import dev.opticumeclient.client.friend.FriendManager;
import dev.opticumeclient.client.modules.Category;
import dev.opticumeclient.client.modules.Module;
import dev.opticumeclient.client.render.ColorUtils;
import dev.opticumeclient.client.render.Render2DHelper;
import dev.opticumeclient.client.render.TextureAssets;
import dev.opticumeclient.client.render.ThemeManager;
import dev.opticumeclient.client.setting.BooleanSetting;
import dev.opticumeclient.client.setting.NumberSetting;
import net.minecraft.client.renderer.RenderPipelines; 
import net.minecraft.client.gui.GuiGraphicsExtractor; 
import net.minecraft.world.entity.player.Player; 
import net.minecraft.util.Mth; 
import net.minecraft.world.phys.Vec3; 
import org.joml.Matrix3x2fStack;

public class ArrowsModule extends Module {
    private final NumberSetting radius = new NumberSetting("Radius", "Радиус круга стрелочек от прицела", 52.0, 25.0, 140.0, 2.0);
    private final NumberSetting size = new NumberSetting("Size", "Размер стрелочек радара", 10.0, 6.0, 20.0, 1.0);
    private final BooleanSetting showDistance = new BooleanSetting("Distance", "Показывать дистанцию в метрах", true);
    private final BooleanSetting showName = new BooleanSetting("Name", "Показывать имя игрока возле стрелки", false);

    public ArrowsModule() {
        super("Arrows", "Круговые OOF-стрелочки радара вокруг прицела на игроков", Category.VISUALS);
        addSetting(radius);
        addSetting(size);
        addSetting(showDistance);
        addSetting(showName);
        setEnabled(false);
    }

    @Override
    public void onRender2D(GuiGraphicsExtractor context, float tickDelta) {
        if (mc.player == null || mc.level == null) return;

        TextureAssets.ensureInitialized();
        float cx = mc.getWindow().getGuiScaledWidth() * 0.5f;
        float cy = mc.getWindow().getGuiScaledHeight() * 0.5f;
        float rad = radius.getFloatValue();
        int arrowSize = size.getIntValue();
        float half = arrowSize * 0.5f;

        Vec3 selfPos = Render2DOrPlayerPos(tickDelta);
        float cameraYaw = mc.gameRenderer.mainCamera().yRot();
        Matrix3x2fStack matrices = context.pose();

        int index = 0;
        for (Player player : mc.level.players()) {
            if (player == mc.player || !player.isAlive() || player.isSpectator()) continue;

            Vec3 targetPos = new Vec3(
                    Mth.lerp(tickDelta, player.xOld, player.getX()),
                    Mth.lerp(tickDelta, player.yOld, player.getY()),
                    Mth.lerp(tickDelta, player.zOld, player.getZ())
            );

            double dx = targetPos.x - selfPos.x;
            double dz = targetPos.z - selfPos.z;
            double angleDeg = Math.toDegrees(Math.atan2(dz, dx)) - cameraYaw - 90.0;
            float angleRad = (float) Math.toRadians(angleDeg);

            float ax = cx + (float) Math.cos(angleRad) * rad;
            float ay = cy + (float) Math.sin(angleRad) * rad;

            boolean isFriend = FriendManager.getInstance().isFriend(player);
            int color = isFriend ? 0xFF22C55E : ThemeManager.getInstance().getAccentColor(index * 30);

            // Soft radial neon glow halo behind arrow
            int glowSize = Math.round(arrowSize * 1.9f);
            float glowHalf = glowSize * 0.5f;
            Render2DHelper.drawTexture(context, TextureAssets.GLOW_RADIAL, ax - glowHalf, ay - glowHalf, glowSize, glowSize, ColorUtils.withAlpha(color, 110));

            // Rotated triangle pointer pointing towards target
            matrices.pushMatrix();
            matrices.translate(ax, ay);
            matrices.rotate(angleRad + (float) (Math.PI * 0.5));
            try {
                context.blit(
                        RenderPipelines.GUI_TEXTURED,
                        TextureAssets.TRIANGLE,
                        Math.round(-half),
                        Math.round(-half),
                        0.0f,
                        0.0f,
                        arrowSize,
                        arrowSize,
                        arrowSize,
                        arrowSize,
                        ColorUtils.withAlpha(color, 240)
                );
            } catch (Throwable ignored) {
            }
            matrices.popMatrix();

            // Distance & optional name label
            int distMeters = (int) Math.round(mc.player.distanceTo(player));
            String label = null;
            if (showName.isEnabled() && showDistance.isEnabled()) {
                label = player.getName().getString() + " " + distMeters + "m";
            } else if (showDistance.isEnabled()) {
                label = distMeters + "m";
            } else if (showName.isEnabled()) {
                label = player.getName().getString();
            }

            if (label != null) {
                float textW = Render2DHelper.getTextWidth(label, 0.75f);
                Render2DHelper.drawScaledText(context, label, ax - textW * 0.5f, ay + half + 2.0f, 0xFFEFF6FF, 0.75f, true);
            }

            index++;
        }
    }

    private Vec3 Render2DOrPlayerPos(float tickDelta) {
        return new Vec3(
                Mth.lerp(tickDelta, mc.player.xOld, mc.player.getX()),
                Mth.lerp(tickDelta, mc.player.yOld, mc.player.getY()),
                Mth.lerp(tickDelta, mc.player.zOld, mc.player.getZ())
        );
    }
}
