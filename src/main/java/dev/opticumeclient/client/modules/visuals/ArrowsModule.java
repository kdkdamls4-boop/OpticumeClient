package dev.opticumeclient.client.modules.visuals;

import dev.opticumeclient.client.friend.FriendManager;
import dev.opticumeclient.client.modules.Category;
import dev.opticumeclient.client.modules.Module;
import dev.opticumeclient.client.modules.combat.AntiBotModule;
import dev.opticumeclient.client.render.ColorUtils;
import dev.opticumeclient.client.render.Render2DHelper;
import dev.opticumeclient.client.render.TextureAssets;
import dev.opticumeclient.client.setting.BooleanSetting;
import dev.opticumeclient.client.setting.ColorSetting;
import dev.opticumeclient.client.setting.MultiBoxSetting;
import dev.opticumeclient.client.setting.NumberSetting;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3x2fStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Ported from Desire ArrowsModule (triangle directional indicators around screen center).
 */
public class ArrowsModule extends Module {
    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };
    private static ArrowsModule instance;

    private final MultiBoxSetting rendering = new MultiBoxSetting(
            "Отображать",
            "Какие цели отображать стрелками",
            new String[]{"Игроков", "Друзей", "Голых", "Мобов", "Предметы"},
            "Игроков", "Друзей", "Голых"
    );
    private final ColorSetting playerColor = new ColorSetting("Цвет игроков", "Цвет стрелок для игроков", 0xFFFFFFFF)
            .visibleWhen(() -> rendering.isEnabled("Игроков") || rendering.isEnabled("Голых"));
    private final ColorSetting friendColor = new ColorSetting("Цвет друзей", "Цвет стрелок для друзей", 0xFF00FF00)
            .visibleWhen(() -> rendering.isEnabled("Друзей"));
    private final ColorSetting mobColor = new ColorSetting("Цвет мобов", "Цвет стрелок для мобов", 0xFFFFAA00)
            .visibleWhen(() -> rendering.isEnabled("Мобов"));
    private final ColorSetting itemColor = new ColorSetting("Цвет предметов", "Цвет стрелок для предметов", 0xFFFF3030)
            .visibleWhen(() -> rendering.isEnabled("Предметы"));
    private final NumberSetting radius = new NumberSetting("Радиус", "Радиус кольца стрелок от прицела", 40.0, 25.0, 120.0, 2.0);
    private final NumberSetting arrowSize = new NumberSetting("Размер", "Размер стрелки (Desire = 46)", 46.0, 20.0, 72.0, 2.0);
    private final BooleanSetting onlyIfNotVisible = new BooleanSetting("Только вне зоны видимости", "Скрывать стрелку, если цель видна напрямую", false);
    private final BooleanSetting showDistance = new BooleanSetting("Дистанция", "Показывать расстояние под стрелкой", false);

    public ArrowsModule() {
        super("Arrows", "Стрелки к игрокам и предметам вне экрана", Category.VISUALS);
        instance = this;
        addSetting(rendering);
        addSetting(playerColor);
        addSetting(friendColor);
        addSetting(mobColor);
        addSetting(itemColor);
        addSetting(radius);
        addSetting(arrowSize);
        addSetting(onlyIfNotVisible);
        addSetting(showDistance);
    }

    public static ArrowsModule getInstance() {
        return instance;
    }

    @Override
    public void onRender2D(GuiGraphicsExtractor context, float tickDelta) {
        if (!isEnabled() || mc.player == null || mc.level == null) return;
        if (mc.getDebugOverlay().showDebugScreen()) return;

        Camera camera = mc.gameRenderer.mainCamera();
        float camYaw = camera.yRot();
        if (mc.options.getCameraType() == CameraType.THIRD_PERSON_FRONT) {
            camYaw += 180.0f;
        }
        double cos = Math.cos(Math.toRadians(camYaw));
        double sin = Math.sin(Math.toRadians(camYaw));
        double baseRadius = radius.getFloatValue();
        float width = mc.getWindow().getGuiScaledWidth();
        float height = mc.getWindow().getGuiScaledHeight();
        Vec3 cameraPos = camera.position();
        float drawSize = arrowSize.getFloatValue();

        Matrix3x2fStack pose = context.pose();

        for (Entity entity : collectTargets()) {
            if (!isValidEntity(entity, tickDelta)) continue;

            double adjustedRadius = (entity instanceof ItemEntity) ? Math.max(baseRadius - 20.0, 10.0) : baseRadius;
            double xOffset = (width * 0.5) - adjustedRadius;
            double yOffset = (height * 0.5) - adjustedRadius;

            double ex = Mth.lerp(tickDelta, entity.xOld, entity.getX());
            double ez = Mth.lerp(tickDelta, entity.zOld, entity.getZ());
            double xWay = (ex - cameraPos.x) * 0.01D;
            double zWay = (ez - cameraPos.z) * 0.01D;

            double rotationY = -(zWay * cos - xWay * sin);
            double rotationX = -(xWay * cos + zWay * sin);
            if (!isValidRotation(rotationX, rotationY, adjustedRadius)) continue;

            double angle = Math.toDegrees(Math.atan2(rotationY, rotationX));
            double x = (adjustedRadius * Math.cos(Math.toRadians(angle))) + xOffset + adjustedRadius;
            double y = (adjustedRadius * Math.sin(Math.toRadians(angle))) + yOffset + adjustedRadius;

            int color = determineEntityColor(entity);

            pose.pushMatrix();
            pose.translate((float) x, (float) y);
            pose.rotate((float) Math.toRadians(angle));
            Render2DHelper.drawTextureFloat(
                    context,
                    TextureAssets.DESIRE_ARROWS_TRIANGLE,
                    -drawSize * 0.5f,
                    -drawSize * 0.5f,
                    drawSize,
                    drawSize,
                    color
            );
            pose.popMatrix();

            if (showDistance.isEnabled()) {
                double dist = mc.player.distanceTo(entity);
                String distText = String.format(Locale.US, "%.0fm", dist);
                float tw = Render2DHelper.getTextWidth(distText, 0.65f);
                Render2DHelper.drawScaledText(
                        context,
                        distText,
                        (float) x - tw * 0.5f,
                        (float) y + drawSize * 0.25f + 2.0f,
                        ColorUtils.withAlpha(color, 210),
                        0.65f,
                        true
                );
            }
        }
    }

    private List<Entity> collectTargets() {
        List<Entity> out = new ArrayList<>();
        if (mc.level == null || mc.player == null) return out;
        if (rendering.isEnabled("Мобов")) {
            for (Entity entity : mc.level.entitiesForRendering()) {
                if (entity instanceof Player || entity instanceof Mob) {
                    out.add(entity);
                }
            }
        } else {
            out.addAll(mc.level.players());
        }
        if (rendering.isEnabled("Предметы")) {
            AABB box = mc.player.getBoundingBox().inflate(64.0);
            out.addAll(mc.level.getEntitiesOfClass(ItemEntity.class, box, item -> true));
        }
        return out;
    }

    private boolean isValidEntity(Entity entity, float tickDelta) {
        if (entity == mc.player || !entity.isAlive()) return false;
        if (entity instanceof Player player) {
            if (AntiBotModule.isBot(player)) return false;
            if (!isPlayerTarget(player)) return false;
        } else if (entity instanceof Mob) {
            if (!rendering.isEnabled("Мобов")) return false;
        } else if (!(entity instanceof ItemEntity) || !rendering.isEnabled("Предметы")) {
            return false;
        }
        return !onlyIfNotVisible.isEnabled() || !isEntityVisible(entity, tickDelta);
    }

    private boolean isPlayerTarget(Player player) {
        boolean friend = FriendManager.getInstance().isFriend(player);
        boolean naked = isNaked(player);
        if (friend) return rendering.isEnabled("Друзей");
        if (naked) return rendering.isEnabled("Голых");
        return rendering.isEnabled("Игроков");
    }

    private static boolean isNaked(Player player) {
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            if (!player.getItemBySlot(slot).isEmpty()) return false;
        }
        return true;
    }

    private boolean isEntityVisible(Entity entity, float tickDelta) {
        if (mc.player == null || mc.level == null) return false;
        Vec3 start = mc.player.getEyePosition(tickDelta);
        Vec3 end = entity.position().add(0.0, entity.getBbHeight() * 0.5, 0.0);
        HitResult result = mc.level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
        return result.getType() == HitResult.Type.MISS;
    }

    private int determineEntityColor(Entity entity) {
        if (entity instanceof Player player) {
            if (FriendManager.getInstance().isFriend(player)) {
                return friendColor.getColor();
            }
            return playerColor.getColor();
        }
        if (entity instanceof Mob) {
            return mobColor.getColor();
        }
        if (entity instanceof ItemEntity) {
            return itemColor.getColor();
        }
        return playerColor.getColor();
    }

    private static boolean isValidRotation(double rotationX, double rotationY, double radiusVal) {
        double mrotY = -rotationY;
        double mrotX = -rotationX;
        return Mth.sqrt((float) (mrotX * mrotX + mrotY * mrotY)) < radiusVal;
    }
}
