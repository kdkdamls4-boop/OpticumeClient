package dev.opticumeclient.client.modules.visuals;

import com.mojang.blaze3d.vertex.BufferBuilder;
import dev.opticumeclient.client.friend.FriendManager;
import dev.opticumeclient.client.hud.HudPanelRenderer;
import dev.opticumeclient.client.modules.Category;
import dev.opticumeclient.client.modules.Module;
import dev.opticumeclient.client.modules.visuals.targetesp.TargetEspFrame;
import dev.opticumeclient.client.modules.visuals.targetesp.TargetEspRenderer;
import dev.opticumeclient.client.render.WorldPrimitiveRenderer;
import dev.opticumeclient.client.render.ColorUtils;
import dev.opticumeclient.client.render.Render2DHelper;
import dev.opticumeclient.client.render.Render3DHelper;
import dev.opticumeclient.client.render.TextureAssets;
import dev.opticumeclient.client.render.ThemeManager;
import dev.opticumeclient.client.setting.BooleanSetting;
import dev.opticumeclient.client.setting.ColorSetting;
import dev.opticumeclient.client.setting.ModeSetting;
import dev.opticumeclient.client.setting.NumberSetting;
import net.minecraft.client.Camera;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.entity.projectile.throwableitemprojectile.AbstractThrownPotion;
import net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEgg;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownExperienceBottle;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.EggItem;
import net.minecraft.world.item.EnderpearlItem;
import net.minecraft.world.item.ExperienceBottleItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SnowballItem;
import net.minecraft.world.item.ThrowablePotionItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3x2fStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * PredictionsModule powered by TargetEspRenderer's native SPIR-V shaders:
 * - Trajectory Beam: TargetESP 3D volumetric tube shader (target_esp_lightning) + EspBloomPass Gaussian bloom
 * - Landing Ring: TargetESP procedural SDF ring shader (target_esp_ring) + orbiting bloom.png comet tail
 * - Pearl Highlight: TargetESP bloom sphere + tilted rotating hoop around the pearl itself
 * - Soul Sand water bubbles (Blocks.BUBBLE_COLUMN): highlights ONLY the floating pearl without drawing a falling line
 */
public class PredictionsModule extends Module {
    private static final float GLOW_BASE_SIZE = 0.32f;
    private static final float GLOW_BASE_ALPHA = 0.20f;
    private static final int MAX_GLOW_SPRITES = 6000;

    private static PredictionsModule instance;

    private final ModeSetting styleSetting;
    private final BooleanSetting glowLineSetting;
    private final NumberSetting glowSizeSetting;
    private final NumberSetting glowStrengthSetting;
    private final BooleanSetting pearlsSetting;
    private final BooleanSetting arrowsSetting;
    private final BooleanSetting tridentsSetting;
    private final BooleanSetting throwablesSetting;
    private final BooleanSetting handPreviewSetting;
    private final BooleanSetting throughWallsSetting;
    private final BooleanSetting trajectoryBeamSetting;
    private final NumberSetting beamWidthSetting;
    private final BooleanSetting pearlHighlightSetting;
    private final BooleanSetting landingRingSetting;
    private final BooleanSetting infoBadgeSetting;
    private final ModeSetting colorModeSetting;
    private final ColorSetting customColorSetting;
    private final BooleanSetting highlightFriendsSetting;
    private final ColorSetting friendColorSetting;

    private record LandingPoint(
            Vec3 pos,
            int ticks,
            ItemStack iconStack,
            String label,
            String ownerName,
            boolean friendly,
            int color
    ) {}

    private final List<LandingPoint> landingPoints = new ArrayList<>();
    private final List<List<Vec3>> worldPaths = new ArrayList<>();
    private final List<Integer> pathColors = new ArrayList<>();
    private final List<Vec3> landingCenters = new ArrayList<>();
    private final List<Integer> landingColors = new ArrayList<>();
    private final List<Vec3> pearlCenters = new ArrayList<>();
    private final List<Integer> pearlColors = new ArrayList<>();

    public PredictionsModule() {
        super("Predictions", "3D-траектории и кольца приземления (Opticume 3D / Desire Glow) + стеклянный бейдж", Category.VISUALS);
        instance = this;

        this.styleSetting = addSetting(new ModeSetting("Style", "Стиль траектории: Opticume 3D (Режим 1) или Desire Glow (Режим 2)", "Desire Glow", "Opticume 3D", "Desire Glow"));
        this.glowLineSetting = addSetting(new BooleanSetting("Glow", "Неоновое свечение линии (Desire Glow)", true)
                .visibleWhen(() -> styleSetting.is("Desire Glow")));
        this.glowSizeSetting = addSetting(new NumberSetting("Glow Size", "Толщина неоновой линии Desire", 1.0, 0.4, 2.5, 0.1)
                .visibleWhen(() -> styleSetting.is("Desire Glow") && glowLineSetting.isEnabled()));
        this.glowStrengthSetting = addSetting(new NumberSetting("Glow Strength", "Яркость свечения Desire", 1.0, 0.2, 2.0, 0.1)
                .visibleWhen(() -> styleSetting.is("Desire Glow") && glowLineSetting.isEnabled()));
        this.pearlsSetting = addSetting(new BooleanSetting("Ender Pearls", "Траектория и подсветка эндер-перлов", true));
        this.arrowsSetting = addSetting(new BooleanSetting("Arrows", "Траектория выпущенных стрел", true));
        this.tridentsSetting = addSetting(new BooleanSetting("Tridents", "Траектория брошенных трезубцев", true));
        this.throwablesSetting = addSetting(new BooleanSetting("Potions & Balls", "Траектория взрывных зелий, снежков, яиц и опыта", true));
        this.handPreviewSetting = addSetting(new BooleanSetting("Hand Preview", "Показывать кольцо падения при удержании перла/лука в руке", false));
        this.throughWallsSetting = addSetting(new BooleanSetting("Through Walls", "Видеть траекторию и кольца приземления сквозь блоки", true));
        this.trajectoryBeamSetting = addSetting(new BooleanSetting("Trajectory Beam", "Рисовать линию траектории полёта", true));
        this.beamWidthSetting = addSetting(new NumberSetting("Beam Width", "Толщина объёмного 3D-луча (Режим 1)", 0.85f, 0.35f, 2.0f, 0.05f)
                .visibleWhen(() -> styleSetting.is("Opticume 3D") && trajectoryBeamSetting.isEnabled()));
        this.pearlHighlightSetting = addSetting(new BooleanSetting("Highlight Pearl", "Кольцо и свечение вокруг летящего перла (Режим 1)", true)
                .visibleWhen(() -> styleSetting.is("Opticume 3D")));
        this.landingRingSetting = addSetting(new BooleanSetting("Landing Ring", "Индикатор в точке приземления", true));
        this.infoBadgeSetting = addSetting(new BooleanSetting("2D Info Badge", "Стеклянный бейдж с иконкой и таймером", true));
        this.colorModeSetting = addSetting(new ModeSetting("Color Mode", "Режим цвета траектории и колец", "Theme", "Theme", "Rainbow", "Custom"));
        this.customColorSetting = addSetting(new ColorSetting("Custom Color", "Пользовательский цвет", ColorUtils.rgba(120, 165, 255, 240))
                .visibleWhen(() -> colorModeSetting.isMode("Custom")));
        this.highlightFriendsSetting = addSetting(new BooleanSetting("Friend Color Override", "Выделять броски друзей отдельным цветом", false));
        this.friendColorSetting = addSetting(new ColorSetting("Friend Color", "Цвет бросков друзей", ColorUtils.rgba(90, 245, 155, 240))
                .visibleWhen(highlightFriendsSetting::isEnabled));
    }

    public static PredictionsModule getInstance() {
        return instance;
    }

    public void renderWorld(TargetEspFrame frame) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            clearFrameLists();
            return;
        }

        clearFrameLists();
        float tickDelta = frame.partialTicks();

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof Projectile projectile)) continue;

            boolean isPearl = entity instanceof ThrownEnderpearl;
            boolean isTrident = entity instanceof ThrownTrident;
            boolean isArrow = entity instanceof AbstractArrow && !isTrident;
            boolean isThrowable = entity instanceof AbstractThrownPotion
                    || entity instanceof Snowball
                    || entity instanceof ThrownEgg
                    || entity instanceof ThrownExperienceBottle;

            if (isPearl && !pearlsSetting.isEnabled()) continue;
            if (isArrow && !arrowsSetting.isEnabled()) continue;
            if (isTrident && !tridentsSetting.isEnabled()) continue;
            if (isThrowable && !throwablesSetting.isEnabled()) continue;
            if (!isPearl && !isArrow && !isTrident && !isThrowable) continue;

            Vec3 motion = entity.getDeltaMovement();
            if ((isArrow || isTrident) && (motion.lengthSqr() < 0.002 || entity.onGround())) {
                continue;
            }

            Entity owner = projectile.getOwner();
            boolean isFriend = false;
            String ownerName = null;
            if (owner instanceof Player p) {
                String pName = p.getName().getString();
                if (p == mc.player || pName.equalsIgnoreCase(mc.player.getName().getString())) {
                    ownerName = null;
                } else if (FriendManager.getInstance().isFriend(pName)) {
                    isFriend = true;
                    ownerName = pName;
                } else {
                    ownerName = pName;
                }
            }

            ItemStack iconStack = resolveIconStack(entity);
            String label = iconStack.getHoverName().getString();
            int baseColor = resolveColor(isFriend, entity.getId() * 35);
            Vec3 startPos = Render3DHelper.getLerpedPos(entity, tickDelta);
            Vec3 centerPos = startPos.add(0.0, entity.getBbHeight() * 0.5, 0.0);

            if (isPearl && pearlHighlightSetting.isEnabled()) {
                pearlCenters.add(centerPos);
                pearlColors.add(baseColor);
            }

            // Soul Sand water bubble column check: highlight ONLY the floating pearl itself, do not draw a falling trajectory line
            BlockPos curBp = entity.blockPosition();
            boolean inBubbleColumn = mc.level.getBlockState(curBp).is(Blocks.BUBBLE_COLUMN)
                    || mc.level.getBlockState(curBp.below()).is(Blocks.BUBBLE_COLUMN);
            if (inBubbleColumn) {
                if (!pearlHighlightSetting.isEnabled()) {
                    pearlCenters.add(centerPos);
                    pearlColors.add(baseColor);
                }
                if (infoBadgeSetting.isEnabled()) {
                    landingPoints.add(new LandingPoint(
                            startPos.add(0.0, entity.getBbHeight() + 0.18, 0.0),
                            0,
                            iconStack,
                            label,
                            ownerName,
                            isFriend,
                            baseColor
                    ));
                }
                continue;
            }

            double airDrag = 0.99;
            double waterDrag = isTrident ? 0.99 : (isArrow ? 0.6 : 0.8);
            double gravity = entity.isNoGravity() ? 0.0 : (isArrow || isTrident || entity instanceof AbstractThrownPotion || entity instanceof ThrownExperienceBottle ? 0.05 : 0.03);

            simulateProjectile(
                    mc,
                    entity,
                    startPos,
                    motion,
                    airDrag,
                    waterDrag,
                    gravity,
                    baseColor,
                    isFriend,
                    iconStack,
                    label,
                    ownerName,
                    trajectoryBeamSetting.isEnabled(),
                    true
            );
        }

        if (handPreviewSetting.isEnabled()) {
            simulateHeldPreview(mc, tickDelta);
        }

        if (styleSetting.is("Opticume 3D")) {
            TargetEspRenderer.renderPredictionVisuals(
                    frame,
                    worldPaths,
                    pathColors,
                    landingCenters,
                    landingColors,
                    pearlCenters,
                    pearlColors,
                    beamWidthSetting.getFloatValue(),
                    throughWallsSetting.isEnabled()
            );
        } else {
            renderDesireGlow(frame);
        }
    }

    private void renderDesireGlow(TargetEspFrame frame) {
        if (mc.gameRenderer == null || mc.gameRenderer.mainCamera() == null) return;
        boolean depthTest = !throughWallsSetting.isEnabled();
        Matrix4f pose = frame.poseStack().last().pose();
        Vec3 camPos = frame.cameraPos();

        if (!worldPaths.isEmpty()) {
            if (glowLineSetting.isEnabled()) {
                Camera camera = mc.gameRenderer.mainCamera();
                Quaternionf orientation = new Quaternionf(camera.rotation());
                float size = GLOW_BASE_SIZE * glowSizeSetting.getFloatValue();
                int alpha = Math.min(255, Math.round(255.0f * GLOW_BASE_ALPHA * glowStrengthSetting.getFloatValue()));
                double spacing = Math.max(0.05, size * 0.3);

                BufferBuilder glowBuf = WorldPrimitiveRenderer.beginTexturedQuads();
                int budget = MAX_GLOW_SPRITES;
                boolean emitted = false;
                for (int i = 0; i < worldPaths.size() && budget > 0; i++) {
                    List<Vec3> path = worldPaths.get(i);
                    if (path.size() < 2) continue;
                    int argb = (alpha << 24) | (pathColors.get(i) & 0x00FFFFFF);
                    for (int s = 0; s < path.size() - 1 && budget > 0; s++) {
                        Vec3 from = path.get(s).subtract(camPos);
                        Vec3 to = path.get(s + 1).subtract(camPos);
                        budget -= WorldPrimitiveRenderer.glowSegment(glowBuf, pose, from, to, orientation, size, argb, spacing);
                        emitted = true;
                    }
                }
                if (landingRingSetting.isEnabled() && !landingCenters.isEmpty()) {
                    for (int i = 0; i < landingCenters.size(); i++) {
                        Vec3 rel = landingCenters.get(i).subtract(camPos).add(0.0, 0.04, 0.0);
                        int spotCol = ColorUtils.withAlpha(landingColors.get(i), Math.min(255, alpha + 90));
                        WorldPrimitiveRenderer.glowSprite(glowBuf, pose, rel.x, rel.y, rel.z, orientation, size * 2.1f, spotCol);
                        emitted = true;
                    }
                }
                if (emitted) {
                    WorldPrimitiveRenderer.drawTexturedQuads(glowBuf, TextureAssets.DESIRE_PARTICLE_GLOW, true, depthTest);
                } else {
                    WorldPrimitiveRenderer.discardQuietly(glowBuf);
                }
            } else {
                BufferBuilder lineBuf = WorldPrimitiveRenderer.beginLines();
                boolean emitted = false;
                for (int i = 0; i < worldPaths.size(); i++) {
                    List<Vec3> path = worldPaths.get(i);
                    if (path.size() < 2) continue;
                    int argb = ColorUtils.withAlpha(pathColors.get(i), 235);
                    for (int s = 0; s < path.size() - 1; s++) {
                        Vec3 a = path.get(s).subtract(camPos);
                        Vec3 b = path.get(s + 1).subtract(camPos);
                        lineBuf.addVertex(pose, (float) a.x, (float) a.y, (float) a.z).setColor(argb);
                        lineBuf.addVertex(pose, (float) b.x, (float) b.y, (float) b.z).setColor(argb);
                        emitted = true;
                    }
                }
                if (emitted) {
                    WorldPrimitiveRenderer.drawLines(lineBuf, depthTest);
                } else {
                    WorldPrimitiveRenderer.discardQuietly(lineBuf);
                }
            }
        }

        if (landingRingSetting.isEnabled() && !landingCenters.isEmpty()) {
            BufferBuilder ringBuf = WorldPrimitiveRenderer.beginLines();
            boolean emitted = false;
            int segments = 28;
            float ringRadius = 0.42f;
            for (int i = 0; i < landingCenters.size(); i++) {
                Vec3 center = landingCenters.get(i).subtract(camPos).add(0.0, 0.03, 0.0);
                int col = ColorUtils.withAlpha(landingColors.get(i), 220);
                for (int k = 0; k < segments; k++) {
                    double a0 = k * (Math.PI * 2.0 / segments);
                    double a1 = (k + 1) * (Math.PI * 2.0 / segments);
                    ringBuf.addVertex(pose, (float) (center.x + Math.cos(a0) * ringRadius), (float) center.y, (float) (center.z + Math.sin(a0) * ringRadius)).setColor(col);
                    ringBuf.addVertex(pose, (float) (center.x + Math.cos(a1) * ringRadius), (float) center.y, (float) (center.z + Math.sin(a1) * ringRadius)).setColor(col);
                    emitted = true;
                }
            }
            if (emitted) {
                WorldPrimitiveRenderer.drawLines(ringBuf, depthTest);
            } else {
                WorldPrimitiveRenderer.discardQuietly(ringBuf);
            }
        }
    }

    private void clearFrameLists() {
        landingPoints.clear();
        worldPaths.clear();
        pathColors.clear();
        landingCenters.clear();
        landingColors.clear();
        pearlCenters.clear();
        pearlColors.clear();
    }

    private void simulateHeldPreview(Minecraft mc, float tickDelta) {
        Player player = mc.player;
        if (player == null) return;

        ItemStack stack = player.getMainHandItem();
        if (!isPredictableHeldItem(stack)) {
            stack = player.getOffhandItem();
            if (!isPredictableHeldItem(stack)) return;
        }

        Item item = stack.getItem();
        float velocity;
        float pitchOffset = 0.0f;
        double gravity = 0.03;
        double waterDrag = 0.8;
        ItemStack displayIcon = stack.copyWithCount(1);

        if (item instanceof BowItem) {
            if (!player.isUsingItem() || !(player.getUseItem().getItem() instanceof BowItem)) return;
            int useTicks = stack.getUseDuration(player) - player.getUseItemRemainingTicks();
            velocity = BowItem.getPowerForTime(useTicks) * 3.0f;
            if (velocity < 0.3f) return;
            gravity = 0.05;
            waterDrag = 0.6;
            displayIcon = new ItemStack(Items.ARROW);
        } else if (item instanceof CrossbowItem) {
            if (!CrossbowItem.isCharged(stack)) return;
            velocity = 3.15f;
            gravity = 0.05;
            waterDrag = 0.6;
            displayIcon = new ItemStack(Items.ARROW);
        } else if (item instanceof TridentItem) {
            if (!player.isUsingItem()) return;
            int useTicks = stack.getUseDuration(player) - player.getUseItemRemainingTicks();
            if (useTicks < 10) return;
            velocity = 2.5f;
            gravity = 0.05;
            waterDrag = 0.99;
        } else if (item instanceof ThrowablePotionItem || item instanceof ExperienceBottleItem) {
            velocity = item instanceof ExperienceBottleItem ? 0.7f : 0.5f;
            pitchOffset = -20.0f;
            gravity = 0.05;
        } else {
            velocity = 1.5f;
            gravity = 0.03;
        }

        Vec3 eyePos = player.getEyePosition(tickDelta).add(0.0, -0.1, 0.0);
        float yaw = player.getViewYRot(tickDelta);
        float pitch = player.getViewXRot(tickDelta) + pitchOffset;
        float pitchBase = player.getViewXRot(tickDelta);

        float yawRad = yaw * ((float) Math.PI / 180.0f);
        float pitchRad = pitch * ((float) Math.PI / 180.0f);
        float pitchBaseRad = pitchBase * ((float) Math.PI / 180.0f);

        double mx = -Mth.sin(yawRad) * Mth.cos(pitchBaseRad);
        double my = -Mth.sin(pitchRad);
        double mz = Mth.cos(yawRad) * Mth.cos(pitchBaseRad);
        Vec3 motion = new Vec3(mx, my, mz).normalize().scale(velocity);

        Vec3 playerVel = player.getDeltaMovement();
        motion = motion.add(playerVel.x, player.onGround() ? 0.0 : playerVel.y, playerVel.z);

        int baseColor = resolveColor(false, 0);
        boolean draw3DLine = trajectoryBeamSetting.isEnabled() && !mc.options.getCameraType().isFirstPerson();
        simulateProjectile(
                mc,
                player,
                eyePos,
                motion,
                0.99,
                waterDrag,
                gravity,
                baseColor,
                false,
                displayIcon,
                displayIcon.getHoverName().getString(),
                null,
                draw3DLine,
                true
        );
    }

    private void simulateProjectile(
            Minecraft mc,
            Entity contextEntity,
            Vec3 startPos,
            Vec3 startMotion,
            double airDrag,
            double waterDrag,
            double gravity,
            int baseColor,
            boolean isFriend,
            ItemStack iconStack,
            String label,
            String ownerName,
            boolean recordPath,
            boolean recordBadge
    ) {
        Vec3 pos = startPos;
        Vec3 motion = startMotion;
        int ticks = 0;
        Vec3 hitPos = null;
        boolean hitBubbleCol = false;
        List<Vec3> path = recordPath ? new ArrayList<>(64) : null;
        if (path != null) {
            path.add(pos);
        }

        for (int i = 0; i < 200; i++) {
            Vec3 nextPos = pos.add(motion);
            BlockHitResult hit = mc.level.clip(new ClipContext(
                    pos,
                    nextPos,
                    ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE,
                    contextEntity
            ));

            if (hit.getType() == HitResult.Type.BLOCK) {
                nextPos = hit.getLocation();
                if (path != null) {
                    path.add(nextPos);
                }
                hitPos = nextPos;
                break;
            }

            BlockPos bp = BlockPos.containing(nextPos);
            BlockState bs = mc.level.getBlockState(bp);
            if (bs.is(Blocks.BUBBLE_COLUMN)) {
                if (path != null) {
                    path.add(nextPos);
                }
                hitPos = nextPos;
                hitBubbleCol = true;
                break;
            }

            if (path != null) {
                path.add(nextPos);
            }
            pos = nextPos;

            if (pos.y < mc.level.getMinY() - 32) {
                break;
            }

            boolean inWater = bs.is(Blocks.WATER);
            double drag = inWater ? waterDrag : airDrag;
            motion = motion.scale(drag).subtract(0.0, gravity, 0.0);
            ticks++;
        }

        if (path != null && path.size() >= 2) {
            worldPaths.add(path);
            pathColors.add(baseColor);
        }

        if (hitPos != null) {
            if (landingRingSetting.isEnabled() && !hitBubbleCol) {
                landingCenters.add(hitPos);
                landingColors.add(baseColor);
            }
            if (recordBadge && infoBadgeSetting.isEnabled()) {
                landingPoints.add(new LandingPoint(hitPos, ticks, iconStack, label, ownerName, isFriend, baseColor));
            }
        }
    }

    @Override
    public void onRender2D(GuiGraphicsExtractor context, float tickDelta) {
        if (!infoBadgeSetting.isEnabled() || landingPoints.isEmpty()) return;

        Render3DHelper.setupRender3D();

        for (LandingPoint pt : landingPoints) {
            float[] screen = Render3DHelper.worldToScreen(pt.pos);
            if (screen == null) continue;

            float sx = screen[0];
            float sy = screen[1];

            float seconds = pt.ticks * 0.05f;
            String nameText = pt.label;
            String timeText = pt.ticks > 0 ? String.format(Locale.US, "%.1fc", seconds) : "";
            String ownerSuffix = (pt.ownerName != null && !pt.ownerName.isEmpty()) ? " • " + pt.ownerName : "";

            float scale = 0.74f;
            float iconSize = 11.0f;
            float padX = 5.5f;
            float gap = 4.0f;

            float nameW = Render2DHelper.getTextWidth(nameText, scale);
            float timeW = timeText.isEmpty() ? 0.0f : Render2DHelper.getTextWidth(timeText, scale);
            float ownerW = ownerSuffix.isEmpty() ? 0.0f : Render2DHelper.getTextWidth(ownerSuffix, 0.68f);

            float pillW = padX + iconSize + gap + nameW + (timeW > 0.0f ? gap + timeW + 6.0f : 0.0f) + ownerW + padX;
            float pillH = 16.0f;
            float bx = sx - pillW * 0.5f;
            float by = sy - pillH - 6.0f;

            int timePillBg = ColorUtils.withAlpha(pt.color, 42);

            HudPanelRenderer.drawSurface(context, "Predictions", bx, by, pillW, pillH, 5.0f, 220, 36);

            float curX = bx + padX;
            drawScaledItem(context, pt.iconStack, curX, by + (pillH - iconSize) * 0.5f, iconSize);
            curX += iconSize + gap;

            Render2DHelper.drawScaledText(context, nameText, curX, by + 4.8f, ColorUtils.rgba(244, 246, 255, 255), scale, false);
            curX += nameW;

            if (timeW > 0.0f) {
                curX += gap;
                Render2DHelper.drawRoundedRectFloat(context, curX, by + 2.5f, timeW + 6.0f, pillH - 5.0f, 3.5f, timePillBg);
                int brightAccent = ColorUtils.interpolateColor(pt.color, 0xFFFFFFFF, 0.28f) | 0xFF000000;
                Render2DHelper.drawScaledText(context, timeText, curX + 3.0f, by + 4.8f, brightAccent, scale, false);
                curX += timeW + 6.0f;
            }

            if (!ownerSuffix.isEmpty()) {
                int ownerCol = pt.friendly ? ColorUtils.rgba(110, 250, 165, 240) : ColorUtils.rgba(175, 185, 208, 235);
                Render2DHelper.drawScaledText(context, ownerSuffix, curX, by + 5.2f, ownerCol, 0.68f, false);
            }
        }
    }

    private static void drawScaledItem(GuiGraphicsExtractor context, ItemStack stack, float x, float y, float size) {
        if (stack == null || stack.isEmpty()) return;
        Matrix3x2fStack matrices = context.pose();
        float scale = size / 16.0f;
        matrices.pushMatrix();
        matrices.translate(x, y);
        matrices.scale(scale, scale);
        try {
            context.fakeItem(stack, 0, 0);
        } catch (Throwable ignored) {
        }
        matrices.popMatrix();
    }

    private static ItemStack resolveIconStack(Entity entity) {
        if (entity instanceof ThrowableItemProjectile tip && !tip.getItem().isEmpty()) {
            return tip.getItem();
        }
        if (entity instanceof ThrownEnderpearl) {
            return new ItemStack(Items.ENDER_PEARL);
        }
        if (entity instanceof ThrownTrident) {
            return new ItemStack(Items.TRIDENT);
        }
        if (entity instanceof AbstractArrow) {
            return new ItemStack(Items.ARROW);
        }
        return new ItemStack(Items.ENDER_PEARL);
    }

    private boolean isPredictableHeldItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        Item item = stack.getItem();
        return item instanceof EnderpearlItem
                || item instanceof BowItem
                || item instanceof CrossbowItem
                || item instanceof TridentItem
                || item instanceof ThrowablePotionItem
                || item instanceof SnowballItem
                || item instanceof EggItem
                || item instanceof ExperienceBottleItem;
    }

    private int resolveColor(boolean isFriend, int indexOffset) {
        if (isFriend && highlightFriendsSetting.isEnabled()) {
            return friendColorSetting.getColor(indexOffset);
        }
        if (colorModeSetting.isMode("Custom")) {
            return customColorSetting.getColor(indexOffset);
        }
        if (colorModeSetting.isMode("Rainbow")) {
            return ColorUtils.withAlpha(ColorUtils.getRainbow(4.0f, 0.85f, 1.0f, indexOffset * 15L), 240);
        }
        return ColorUtils.withAlpha(ThemeManager.getInstance().getAccentColor(indexOffset), 240);
    }
}
