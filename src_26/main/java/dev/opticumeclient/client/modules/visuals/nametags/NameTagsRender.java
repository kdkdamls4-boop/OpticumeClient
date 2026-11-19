package dev.opticumeclient.client.modules.visuals.nametags;

import dev.opticumeclient.client.friend.FriendManager;
import dev.opticumeclient.client.hud.HudPanelRenderer;
import dev.opticumeclient.client.modules.visuals.NameTagsModule;
import dev.opticumeclient.client.modules.visuals.TargetHudModule;
import dev.opticumeclient.client.render.ColorUtils;
import dev.opticumeclient.client.render.Render2DHelper;
import dev.opticumeclient.client.render.Render3DHelper;
import dev.opticumeclient.client.render.TextureAssets;
import dev.opticumeclient.client.render.ThemeManager;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3x2fStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Ported from Desire NameTagsRender -> rendered smoothly in GUI scaled space using RenderPearl / HudPanelRenderer.
 */
public class NameTagsRender {
    private static final int FRIEND_GREEN = ColorUtils.rgba(64, 255, 64, 255);
    private static final int OVER_LEVEL_RED = ColorUtils.rgba(225, 78, 74, 255);
    private static final int DARK_BG = ColorUtils.rgba(14, 15, 22, 215);

    private static final EquipmentSlot[] ARMOR_ORDER = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };
    private static final EquipmentSlot[] DISPLAY_ORDER = {
            EquipmentSlot.OFFHAND, EquipmentSlot.HEAD, EquipmentSlot.CHEST,
            EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.MAINHAND
    };

    private static final long FADE_OUT_MS = 1000L;
    private static final float LIVING_RAISE = 0.28f;
    private static final float LIVING_PILL_LIFT = 13.5f;
    private static final float ITEM_RAISE_FACTOR = 1.2f;

    private final NameTagsModule module;
    private final Map<Integer, TagState> states = new HashMap<>();
    private ClientLevel stateLevel;

    public NameTagsRender(NameTagsModule module) {
        this.module = module;
    }

    public void clear() {
        states.clear();
        stateLevel = null;
    }

    public void onRender(GuiGraphicsExtractor context, float tickDelta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            clear();
            return;
        }
        if (stateLevel != mc.level) {
            states.clear();
            stateLevel = mc.level;
        }

        Render3DHelper.setupRender3D();
        long now = System.currentTimeMillis();

        for (TagState state : states.values()) {
            state.targeted = false;
        }
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!isEntityTarget(mc, entity)) continue;
            TagState state = states.get(entity.getId());
            if (state == null || state.entity != entity) {
                state = new TagState(entity);
                states.put(entity.getId(), state);
            }
            state.targeted = true;
        }

        Vec3 eye = mc.player.getEyePosition(tickDelta);
        List<TagState> drawList = new ArrayList<>();
        Iterator<TagState> iterator = states.values().iterator();
        while (iterator.hasNext()) {
            TagState state = iterator.next();
            boolean visible = state.targeted && locate(mc, state, tickDelta, true);
            state.update(visible, now);

            if (!visible && state.alpha <= 0.005f && !state.shown) {
                iterator.remove();
                continue;
            }
            if (!visible && state.alpha > 0.005f && !locate(mc, state, tickDelta, false)) {
                continue;
            }
            if (state.alpha <= 0.005f) continue;

            double dx = state.worldX - eye.x;
            double dy = state.worldY - eye.y;
            double dz = state.worldZ - eye.z;
            state.distanceSq = dx * dx + dy * dy + dz * dz;
            drawList.add(state);
        }

        // Farther first so closer name tags overlap properly
        drawList.sort(Comparator.comparingDouble((TagState s) -> s.distanceSq).reversed());
        for (TagState state : drawList) {
            if (state.entity instanceof ItemEntity item) {
                renderItemTag(context, state, item);
            } else if (state.entity instanceof LivingEntity living) {
                renderEntityTag(mc, context, state, living);
            }
        }
    }

    private boolean locate(Minecraft mc, TagState state, float tickDelta, boolean fresh) {
        Entity entity = state.entity;
        if (fresh) {
            double x = Mth.lerp(tickDelta, entity.xOld, entity.getX());
            double y = Mth.lerp(tickDelta, entity.yOld, entity.getY());
            double z = Mth.lerp(tickDelta, entity.zOld, entity.getZ());
            boolean isItem = entity instanceof ItemEntity;
            state.worldX = x;
            state.worldY = y + entity.getBbHeight() * (isItem ? ITEM_RAISE_FACTOR : 1.0f) + (isItem ? 0.0f : LIVING_RAISE);
            state.worldZ = z;
            state.hasWorld = true;
        }
        if (!state.hasWorld) return false;

        float[] projected = Render3DHelper.worldToScreen(new Vec3(state.worldX, state.worldY, state.worldZ));
        if (projected == null) return false;

        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        float margin = 64.0f;
        if (projected[0] < -margin || projected[0] > sw + margin || projected[1] < -margin || projected[1] > sh + margin) {
            return false;
        }
        state.screenX = projected[0];
        state.screenY = projected[1];
        return true;
    }

    public boolean isEntityTarget(Minecraft mc, Entity entity) {
        if (entity instanceof ItemEntity) {
            return module.rendering.isEnabled("Предметы");
        }
        if (!(entity instanceof LivingEntity living) || !living.isAlive()) {
            return false;
        }
        if (entity instanceof Player player) {
            if (entity == mc.player) {
                return module.rendering.isEnabled("Себя") && !mc.options.getCameraType().isFirstPerson();
            }
            boolean isFriend = FriendManager.getInstance().isFriend(player);
            if (isFriend) {
                return module.rendering.isEnabled("Друзей");
            }
            if (isNaked(player)) {
                return module.rendering.isEnabled("Голых");
            }
            return module.rendering.isEnabled("Игроков");
        }
        if (entity instanceof Villager) {
            return module.rendering.isEnabled("Жителей");
        }
        if (entity instanceof Animal) {
            return module.rendering.isEnabled("Животных");
        }
        return entity instanceof Mob && module.rendering.isEnabled("Монстров");
    }

    private static boolean isNaked(Player player) {
        for (EquipmentSlot slot : ARMOR_ORDER) {
            if (!player.getItemBySlot(slot).isEmpty()) return false;
        }
        return true;
    }

    private void renderItemTag(GuiGraphicsExtractor context, TagState state, ItemEntity item) {
        float alpha = state.alpha;
        ItemStack stack = item.getItem();
        float textScale = 0.72f * module.tagScale.getFloatValue();

        MutableComponent label = stack.getHoverName().copy();
        int count = stack.getCount();
        if (count > 1) {
            label.append(Component.literal(" / ").setStyle(Style.EMPTY.withColor(ChatFormatting.DARK_GRAY)));
            label.append(Component.literal(String.valueOf(count)).setStyle(Style.EMPTY.withColor(ChatFormatting.RED)));
        }

        float width = componentWidth(label, textScale) + 8.0f;
        float height = 12.0f * module.tagScale.getFloatValue();
        float x = state.screenX - width * 0.5f;
        float y = state.screenY - height * 0.5f;

        drawTagSurface(context, x, y, width, height, 3.5f, false, alpha);
        drawComponent(context, label, x + 4.0f, y + (height - 7.5f * module.tagScale.getFloatValue()) * 0.5f, textScale, alpha);
    }

    private void renderEntityTag(Minecraft mc, GuiGraphicsExtractor context, TagState state, LivingEntity entity) {
        float alpha = state.alpha;
        float sc = module.tagScale.getFloatValue();

        float centerX = state.screenX;
        float centerY = state.screenY - LIVING_PILL_LIFT * sc;

        boolean isPlayer = entity instanceof Player;
        boolean isFriend = isPlayer && FriendManager.getInstance().isFriend((Player) entity);

        float textScale = 0.72f * sc;
        float smallScale = 0.64f * sc;
        float headSize = 10.0f * sc;
        float heartSize = 7.5f * sc;
        float pingIconSize = 6.5f * sc;

        Component name = entity.getDisplayName();
        float realHp = entity instanceof Player ? TargetHudModule.getRealHealth(entity) + entity.getAbsorptionAmount()
                : entity.getHealth() + entity.getAbsorptionAmount();
        String hpText = String.valueOf(Math.max(0, Math.round(realHp)));
        String pingText = pingOf(mc, entity);

        float nameWidth = componentWidth(name, textScale);
        float hpWidth = Render2DHelper.getTextWidth(hpText, textScale);
        float pingWidth = pingText.isEmpty() ? 0.0f : Render2DHelper.getTextWidth(pingText + "ms", smallScale);

        boolean showHead = module.viewHead.isEnabled();
        float padX = 4.0f * sc;
        float width = padX + (showHead ? headSize + 4.0f * sc : 0.0f)
                + nameWidth + 5.0f * sc + heartSize + 2.0f * sc + hpWidth + padX;
        if (!pingText.isEmpty()) {
            width += 5.0f * sc + pingIconSize + 2.0f * sc + pingWidth;
        }
        float height = 14.5f * sc;
        float x = centerX - width * 0.5f;
        float y = centerY - height * 0.5f;

        int accent = isFriend ? FRIEND_GREEN : ThemeManager.getInstance().getAccentColor(0);
        int a255 = Mth.clamp(Math.round(255.0f * alpha), 0, 255);
        int textColor = ColorUtils.rgba(245, 247, 255, a255);
        float textY = y + (height - 8.0f * sc) * 0.5f + 0.5f;

        drawTagSurface(context, x, y, width, height, 4.5f * sc, isFriend, alpha);

        float cursor = x + padX;
        if (showHead) {
            if (entity instanceof AbstractClientPlayer acp) {
                drawSubpixelHead(context, acp, cursor, y + (height - headSize) * 0.5f, headSize, alpha);
            } else {
                Render2DHelper.drawTextureFloat(
                        context,
                        TextureAssets.DESIRE_HUD_USER,
                        cursor + 1.0f,
                        y + (height - (headSize - 2.0f)) * 0.5f,
                        headSize - 2.0f,
                        headSize - 2.0f,
                        ColorUtils.withAlpha(accent, a255)
                );
            }
            cursor += headSize + 4.0f * sc;
        }

        drawComponent(context, name, cursor, textY, textScale, alpha);
        cursor += nameWidth + 5.0f * sc;

        float maxHp = Math.max(1.0f, entity.getMaxHealth());
        float hpPct = Mth.clamp(realHp / maxHp, 0.0f, 1.0f);
        int heartColor = isFriend ? FRIEND_GREEN : ColorUtils.interpolateColor(0xFFFF3A4E, accent, hpPct);
        Render2DHelper.drawTextureFloat(
                context,
                TextureAssets.DESIRE_NAMETAGS_HEART,
                cursor,
                y + (height - heartSize) * 0.5f,
                heartSize,
                heartSize,
                ColorUtils.withAlpha(heartColor, a255)
        );
        cursor += heartSize + 2.0f * sc;

        Render2DHelper.drawScaledText(context, hpText, cursor, textY, textColor, textScale, false);
        cursor += hpWidth;

        if (!pingText.isEmpty()) {
            cursor += 5.0f * sc;
            Render2DHelper.drawTextureFloat(
                    context,
                    TextureAssets.PING_ICON,
                    cursor,
                    y + (height - pingIconSize) * 0.5f,
                    pingIconSize,
                    pingIconSize,
                    ColorUtils.withAlpha(accent, a255)
            );
            cursor += pingIconSize + 2.0f * sc;
            Render2DHelper.drawScaledText(
                    context,
                    pingText + "ms",
                    cursor,
                    y + (height - 7.0f * sc) * 0.5f + 0.5f,
                    ColorUtils.rgba(195, 205, 226, a255),
                    smallScale,
                    false
            );
        }

        if (module.viewArmor.isEnabled()) {
            drawArmorRow(context, entity, centerX, y, sc, alpha);
        }

        float belowY = state.screenY + 3.0f * sc;
        if (module.viewItem.isEnabled()) {
            belowY = drawHeldItems(context, entity, centerX, belowY, sc, alpha);
        }
        if (module.viewEffects.isEnabled()) {
            renderEffects(context, entity, centerX, belowY, sc, alpha);
        }
    }

    private void drawTagSurface(GuiGraphicsExtractor context, float x, float y, float w, float h, float r, boolean isFriend, float alpha) {
        int aBg = Mth.clamp(Math.round(215.0f * alpha), 0, 255);
        int accent = isFriend ? FRIEND_GREEN : ThemeManager.getInstance().getAccentColor(0);
        if (module.liquidGlass.isEnabled() && alpha > 0.15f) {
            HudPanelRenderer.drawSurface(context, "NameTags", x, y, w, h, r, aBg, isFriend ? 42 : 28);
        } else {
            Render2DHelper.drawRoundedRectFloat(context, x, y, w, h, r, ColorUtils.withAlpha(DARK_BG, aBg));
            Render2DHelper.drawRoundedOutlineFloat(context, x, y, w, h, r, 0.7f, ColorUtils.withAlpha(accent, Math.round(95 * alpha)));
        }
        if (isFriend) {
            Render2DHelper.drawRoundedRectFloat(context, x, y, w, h, r, ColorUtils.withAlpha(FRIEND_GREEN, Math.round(48 * alpha)));
        }
    }

    private static void drawSubpixelHead(GuiGraphicsExtractor context, AbstractClientPlayer player, float x, float y, float size, float alpha) {
        int a = Mth.clamp(Math.round(255.0f * alpha), 0, 255);
        Matrix3x2fStack pose = context.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(size / 16.0f, size / 16.0f);
        try {
            PlayerFaceExtractor.extractRenderState(context, player.getSkin(), 0, 0, 16, ColorUtils.rgba(255, 255, 255, a));
        } catch (Throwable ignored) {
            Render2DHelper.drawRoundedRectFloat(context, 0, 0, 16, 16, 3.0f, ThemeManager.getInstance().getAccentColor(0));
        }
        pose.popMatrix();
    }

    private static String pingOf(Minecraft mc, LivingEntity entity) {
        if (!(entity instanceof Player player) || mc.getConnection() == null) return "";
        PlayerInfo info = mc.getConnection().getPlayerInfo(player.getUUID());
        return info == null ? "" : String.valueOf(Math.max(0, info.getLatency()));
    }

    private float drawHeldItems(GuiGraphicsExtractor context, LivingEntity entity, float centerX, float startY, float sc, float alpha) {
        float fontScale = 0.65f * sc;
        float y = startY;
        int aBg = Mth.clamp(Math.round(190.0f * alpha), 0, 255);
        int aText = Mth.clamp(Math.round(255.0f * alpha), 0, 255);

        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND}) {
            ItemStack stack = entity.getItemBySlot(slot);
            if (stack.isEmpty()) continue;
            String text = module.viewDisplayName.isEnabled()
                    ? stack.getDisplayName().getString()
                    : stack.getHoverName().getString();
            float w = Render2DHelper.getTextWidth(text, fontScale);
            float x = centerX - w * 0.5f;
            Render2DHelper.drawRoundedRectFloat(context, x - 2.0f, y - 1.0f, w + 4.0f, 8.5f * sc, 2.0f, ColorUtils.withAlpha(DARK_BG, aBg));
            Render2DHelper.drawScaledText(context, text, x, y + 0.5f, ColorUtils.rgba(255, 255, 255, aText), fontScale, false);
            y += 9.5f * sc;
        }
        return y;
    }

    private void drawArmorRow(GuiGraphicsExtractor context, LivingEntity entity, float centerX, float pillTop, float sc, float alpha) {
        List<ItemStack> stacks = new ArrayList<>();
        for (EquipmentSlot slot : DISPLAY_ORDER) {
            ItemStack stack = entity.getItemBySlot(slot);
            if (!stack.isEmpty()) stacks.add(stack);
        }
        if (stacks.isEmpty()) return;

        float itemSize = 13.0f * sc;
        float step = 15.0f * sc;
        float y = pillTop - itemSize - 3.5f * sc;
        float offsetX = centerX - ((stacks.size() - 1) * step + itemSize) * 0.5f;
        float enchScale = 0.58f * sc;
        int a = Mth.clamp(Math.round(255.0f * alpha), 0, 255);

        for (ItemStack stack : stacks) {
            NameTagItemRenderer.draw(context, stack, offsetX, y, itemSize, alpha);

            if (module.viewEnchants.isEnabled() && !stack.getEnchantments().isEmpty()) {
                float enchantY = y - 6.5f * sc;
                for (String enchantment : collectEnchants(stack)) {
                    float eWidth = Render2DHelper.getTextWidth(enchantment, enchScale);
                    int col = isOverLevelled(enchantment)
                            ? ColorUtils.withAlpha(OVER_LEVEL_RED, a)
                            : ColorUtils.rgba(240, 242, 250, a);
                    Render2DHelper.drawScaledText(
                            context,
                            enchantment,
                            offsetX + itemSize * 0.5f - eWidth * 0.5f,
                            enchantY,
                            col,
                            enchScale,
                            true
                    );
                    enchantY -= 6.0f * sc;
                }
            }
            offsetX += step;
        }
    }

    private static boolean isOverLevelled(String label) {
        if (label.length() < 3) return false;
        String key = label.substring(0, 2);
        int level;
        try {
            level = Integer.parseInt(label.substring(2));
        } catch (NumberFormatException e) {
            return false;
        }
        return (key.equalsIgnoreCase("Sh") && level > 5) || (key.equalsIgnoreCase("Pr") && level > 4);
    }

    private static List<String> collectEnchants(ItemStack stack) {
        List<Object2IntMap.Entry<Holder<Enchantment>>> sorted = new ArrayList<>(stack.getEnchantments().entrySet());
        sorted.sort((a, b) -> Integer.compare(b.getIntValue(), a.getIntValue()));
        List<String> result = new ArrayList<>();
        for (Object2IntMap.Entry<Holder<Enchantment>> entry : sorted) {
            String full = entry.getKey().value().description().getString();
            String shortName = full.substring(0, Math.min(2, full.length()));
            result.add(shortName + entry.getIntValue());
        }
        return result;
    }

    private void renderEffects(GuiGraphicsExtractor context, LivingEntity entity, float centerX, float startY, float sc, float alpha) {
        List<MobEffectInstance> effects = List.copyOf(entity.getActiveEffects());
        if (effects.isEmpty()) return;

        float fontScale = 0.62f * sc;
        List<EffectLine> lines = new ArrayList<>();
        for (MobEffectInstance inst : effects) {
            int durationTicks = inst.getDuration();
            if (durationTicks <= 20) continue;
            String name = inst.getEffect().value().getDisplayName().getString();
            int amp = inst.getAmplifier() + 1;
            StringBuilder sb = new StringBuilder(name);
            if (amp > 1) sb.append(' ').append(toRomanNumeral(amp));
            sb.append(' ').append(formatDuration(durationTicks));
            String text = sb.toString();
            lines.add(new EffectLine(text, Render2DHelper.getTextWidth(text, fontScale), effectColor(inst)));
        }
        if (lines.size() > 1) {
            lines.sort(Comparator.comparingDouble(e -> e.width));
        }

        float step = 7.5f * sc;
        int a = Mth.clamp(Math.round(255.0f * alpha), 0, 255);
        for (int i = 0; i < lines.size(); i++) {
            EffectLine line = lines.get(i);
            Render2DHelper.drawScaledText(
                    context,
                    line.text,
                    centerX - line.width * 0.5f,
                    startY + i * step,
                    ColorUtils.withAlpha(line.color, a),
                    fontScale,
                    true
            );
        }
    }

    private static int effectColor(MobEffectInstance inst) {
        var keyHolder = BuiltInRegistries.MOB_EFFECT.getKey(inst.getEffect().value());
        String key = keyHolder == null ? "" : keyHolder.getPath().toLowerCase(Locale.ROOT);
        if (key.contains("strength")) return 0xFFFF5555;
        if (key.contains("fire_resistance")) return 0xFFFFAA00;
        if (key.contains("speed")) return 0xFF55FFFF;
        if (key.contains("absorption")) return 0xFFFFFF55;
        if (key.contains("regeneration")) return 0xFFFF55FF;
        if (key.contains("jump_boost")) return 0xFF55FF55;
        return 0xFFFFFFFF;
    }

    private static String formatDuration(int ticks) {
        int seconds = ticks / 20;
        int minutes = seconds / 60;
        seconds = seconds % 60;
        return String.format("%d:%02d", minutes, seconds);
    }

    private static String toRomanNumeral(int number) {
        if (number < 1 || number > 3999) return String.valueOf(number);
        String[] thousands = {"", "M", "MM", "MMM"};
        String[] hundreds = {"", "C", "CC", "CCC", "CD", "D", "DC", "DCC", "DCCC", "CM"};
        String[] tens = {"", "X", "XX", "XXX", "XL", "L", "LX", "LXX", "LXXX", "XC"};
        String[] ones = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX"};
        return thousands[number / 1000] + hundreds[(number % 1000) / 100] + tens[(number % 100) / 10] + ones[number % 10];
    }

    private static void drawComponent(GuiGraphicsExtractor context, Component component, float x, float y, float scale, float alpha) {
        float[] cursor = {x};
        int a = Mth.clamp(Math.round(255.0f * alpha), 0, 255);
        component.visit((style, segment) -> {
            if (!segment.isEmpty()) {
                TextColor color = style.getColor();
                int rgb = color == null ? 0xFFFFFF : color.getValue();
                int argb = (a << 24) | (rgb & 0x00FFFFFF);
                Render2DHelper.drawScaledText(context, segment, cursor[0], y, argb, scale, false);
                cursor[0] += Render2DHelper.getTextWidth(segment, scale);
            }
            return Optional.empty();
        }, Style.EMPTY);
    }

    private static float componentWidth(Component component, float scale) {
        float[] width = {0.0f};
        component.visit((style, segment) -> {
            width[0] += Render2DHelper.getTextWidth(segment, scale);
            return Optional.empty();
        }, Style.EMPTY);
        return width[0];
    }

    private record EffectLine(String text, float width, int color) {}

    private static final class TagState {
        final Entity entity;
        boolean targeted;
        boolean shown;
        boolean hasWorld;
        double worldX, worldY, worldZ;
        float screenX, screenY;
        double distanceSq;
        float alpha;
        private float fadeFrom;
        private long fadeStart;
        private boolean fading;

        TagState(Entity entity) {
            this.entity = entity;
        }

        void update(boolean visible, long now) {
            if (visible) {
                shown = true;
                fading = false;
                alpha = 1.0f;
                return;
            }
            if (!(entity instanceof Player)) {
                shown = false;
                alpha = 0.0f;
                return;
            }
            if (shown) {
                shown = false;
                fading = true;
                fadeFrom = alpha;
                fadeStart = now;
            }
            if (fading) {
                float t = Mth.clamp((float) (now - fadeStart) / FADE_OUT_MS, 0.0f, 1.0f);
                float eased = t * t * t * (t * (t * 6.0f - 15.0f) + 10.0f);
                alpha = fadeFrom * (1.0f - eased);
                if (t >= 1.0f) {
                    alpha = 0.0f;
                    fading = false;
                }
            }
        }
    }
}
