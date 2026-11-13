package dev.optimusprime.client.modules.visuals;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.optimusprime.client.modules.Category;
import dev.optimusprime.client.modules.Module;
import dev.optimusprime.client.render.ColorUtils;
import dev.optimusprime.client.render.Render2DHelper;
import dev.optimusprime.client.render.Render3DHelper;
import dev.optimusprime.client.render.TextureAssets;
import dev.optimusprime.client.render.ThemeManager;
import dev.optimusprime.client.setting.BooleanSetting;
import dev.optimusprime.client.setting.ModeSetting;
import dev.optimusprime.client.setting.NumberSetting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class ItemEspModule extends Module {
    private static ItemEspModule INSTANCE;

    private final ModeSetting boxMode = new ModeSetting("BoxMode", "Стиль подсветки", "2D Box", "2D Box", "3D Box", "Radial Glow", "Tags Only", "None");
    private final BooleanSetting nameTags = new BooleanSetting("NameTags", "Плавающая плашка с названием", true);
    private final BooleanSetting showCount = new BooleanSetting("ShowCount", "Количество в стаке", true);
    private final BooleanSetting showDistance = new BooleanSetting("ShowDistance", "Расстояние до предмета", false);
    private final BooleanSetting showIcon = new BooleanSetting("ShowIcon", "Отрисовка иконки предмета", true);
    private final NumberSetting maxDistance = new NumberSetting("MaxDistance", "Радиус поиска", 64.0, 16.0, 128.0, 4.0);
    private final BooleanSetting valuableGlow = new BooleanSetting("ValuableGlow", "Золотое/пурпурное свечение ценных предметов", true);

    public ItemEspModule() {
        super("ItemESP", "2D/3D подсветка, радиальное свечение и плашки выпавших предметов", Category.VISUALS);
        addSetting(boxMode);
        addSetting(nameTags);
        addSetting(showCount);
        addSetting(showDistance);
        addSetting(showIcon);
        addSetting(maxDistance);
        addSetting(valuableGlow);
        INSTANCE = this;
        setEnabled(false);
    }

    public static ItemEspModule getInstance() {
        return INSTANCE;
    }

    @Override
    public String getDisplaySuffix() {
        return boxMode.getMode();
    }

    private int getItemColor(ItemStack stack) {
        if (valuableGlow.isEnabled() && stack != null && !stack.isEmpty()) {
            if (stack.is(Items.TOTEM_OF_UNDYING) || stack.is(Items.ENCHANTED_GOLDEN_APPLE)) {
                return 0xFFFBBF24; // Gold
            }
            if (stack.is(Items.END_CRYSTAL) || stack.is(Items.NETHERITE_INGOT) || stack.is(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE)) {
                return 0xFFA855F7; // Purple
            }
            if (stack.is(Items.GOLDEN_APPLE) || stack.is(Items.DIAMOND_BLOCK) || stack.is(Items.NETHERITE_BLOCK)) {
                return 0xFF38BDF8; // Diamond Cyan
            }
        }
        return ThemeManager.getInstance().getAccentColor(0);
    }

    @Override
    public void onRender3D(PoseStack matrices, float tickDelta) {
        if (!isEnabled() || mc.level == null || mc.player == null) return;

        double maxDistSq = maxDistance.getValue() * maxDistance.getValue();
        int count = 0;

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof ItemEntity item)) continue;
            if (mc.player.distanceToSqr(item) > maxDistSq) continue;
            if (++count > 64) break;

            ItemStack stack = item.getItem();
            int color = getItemColor(stack);

            if (boxMode.is("3D Box")) {
                AABB lerpedBox = Render3DHelper.getLerpedBox(item, tickDelta);
                Render3DHelper.drawBoxColor(lerpedBox, color, 1.4f, true);
            } else if (boxMode.is("Radial Glow")) {
                Vec3 pos = Render3DHelper.getLerpedPos(item, tickDelta);
                Render3DHelper.queueBillboardSprite(pos, 0.45f, TextureAssets.GLOW_RADIAL, ColorUtils.withAlpha(color, 210), 0.0f, true);
            }
        }
    }

    private record RenderBox(float x, float y, float w, float h, int color) {}
    private record RenderTag(
            ItemStack stack, String name,
            float tx, float ty, float tagW, float tagH,
            boolean withIcon, int color
    ) {}

    private final List<ItemEntity> candidateItems = new ArrayList<>();
    private final List<RenderBox> boxList = new ArrayList<>();
    private final List<RenderTag> tagList = new ArrayList<>();

    @Override
    public void onRender2D(GuiGraphicsExtractor context, float tickDelta) {
        if (!isEnabled() || mc.level == null || mc.player == null) return;

        double maxDist = Math.min(maxDistance.getValue(), 24.0);
        double maxDistSq = maxDist * maxDist;

        candidateItems.clear();
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof ItemEntity item)) continue;
            if (mc.player.distanceToSqr(item) <= maxDistSq) {
                candidateItems.add(item);
            }
        }
        if (candidateItems.isEmpty()) return;

        // Limit to closest 16 items within 24 blocks
        candidateItems.sort(Comparator.comparingDouble(mc.player::distanceToSqr));
        int limit = Math.min(16, candidateItems.size());

        boxList.clear();
        tagList.clear();

        boolean is2DBox = boxMode.is("2D Box");
        boolean isNameTags = nameTags.isEnabled();

        for (int i = 0; i < limit; i++) {
            ItemEntity item = candidateItems.get(i);
            ItemStack stack = item.getItem();
            int color = getItemColor(stack);

            if (is2DBox) {
                AABB lerpedBox = Render3DHelper.getLerpedBox(item, tickDelta);
                float[] rect = Render3DHelper.projectBox2D(lerpedBox);
                if (rect != null) {
                    boxList.add(new RenderBox(rect[0], rect[1], rect[2] - rect[0], rect[3] - rect[1], color));
                }
            }

            if (isNameTags) {
                Vec3 tagPos = Render3DHelper.getLerpedPos(item, tickDelta).add(0.0, 0.52, 0.0);
                float[] proj = Render3DHelper.worldToScreen(tagPos);
                if (proj != null) {
                    String name = stack.getHoverName().getString();
                    if (showCount.isEnabled() && stack.getCount() > 1) {
                        name = name + " x" + stack.getCount();
                    }
                    if (showDistance.isEnabled()) {
                        int d = (int) mc.player.distanceTo(item);
                        name = name + " [" + d + "m]";
                    }

                    boolean withIcon = showIcon.isEnabled();
                    float textW = Render2DHelper.getTextWidth(name, 0.75f);
                    float tagW = textW + (withIcon ? 24.0f : 12.0f);
                    float tagH = withIcon ? 18.0f : 14.0f;
                    float tx = proj[0] - tagW * 0.5f;
                    float ty = proj[1] - tagH;

                    tagList.add(new RenderTag(stack, name, tx, ty, tagW, tagH, withIcon, color));
                }
            }
        }

        // Pass 1: All geometry & backgrounds (2D boxes and NameTag glass plates)
        for (RenderBox box : boxList) {
            Render2DHelper.drawGlow(context, box.x, box.y, box.w, box.h, 3.0f, ColorUtils.withAlpha(box.color, 80));
            Render2DHelper.drawRoundedOutline(context, box.x, box.y, box.w, box.h, 1.5f, 1.2f, box.color);
        }
        for (RenderTag tag : tagList) {
            Render2DHelper.drawLiquidGlass(context, tag.tx, tag.ty, tag.tagW, tag.tagH, 3.5f, 220, 32);
            Render2DHelper.drawRoundedOutline(context, tag.tx, tag.ty, tag.tagW, tag.tagH, 3.5f, 1.0f, ColorUtils.withAlpha(tag.color, 120));
        }

        // Pass 2: All item icons & text labels
        for (RenderTag tag : tagList) {
            if (tag.withIcon) {
                int ix = (int) Math.round(tag.tx + 2.0f);
                int iy = (int) Math.round(tag.ty + 1.0f);
                context.item(tag.stack, ix, iy);
                Render2DHelper.drawScaledText(context, tag.name, tag.tx + 20.0f, tag.ty + 3.5f, 0xFFFFFFFF, 0.75f, true);
            } else {
                Render2DHelper.drawScaledText(context, tag.name, tag.tx + 6.0f, tag.ty + 2.5f, 0xFFFFFFFF, 0.75f, true);
            }
        }
    }
}
