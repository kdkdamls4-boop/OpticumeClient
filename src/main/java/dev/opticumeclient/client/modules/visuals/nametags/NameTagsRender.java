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
    private static final EquipmentSlot[] HELD_SLOTS = {
            EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND
    };
    private static final String[] ROMAN_THOUSANDS = {"", "M", "MM", "MMM"};
    private static final String[] ROMAN_HUNDREDS = {"", "C", "CC", "CCC", "CD", "D", "DC", "DCC", "DCCC", "CM"};
    private static final String[] ROMAN_TENS = {"", "X", "XX", "XXX", "XL", "L", "LX", "LXX", "LXXX", "XC"};
    private static final String[] ROMAN_ONES = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX"};
    private static final int MAX_LIQUID_GLASS_TAGS = 6;
    private static final long CACHE_REFRESH_MS = 200L;

    private static final long FADE_OUT_MS = 1000L;
    private static final float LIVING_RAISE = 0.28f;
    private static final float LIVING_PILL_LIFT = 13.5f;
    private static final float ITEM_RAISE_FACTOR = 1.2f;

    private final NameTagsModule module;
    private final Map<Integer, TagState> states = new HashMap<>();
    private final List<TagState> drawList = new ArrayList<>(32);
    private final List<ItemStack> armorScratch = new ArrayList<>(6);
    private final List<Object2IntMap.Entry<Holder<Enchantment>>> enchantEntriesScratch = new ArrayList<>(8);
    private final List<String> enchantLabelsScratch = new ArrayList<>(8);
    private final List<EffectLine> effectLinesScratch = new ArrayList<>(8);
    private final StringBuilder textScratch = new StringBuilder(64);
    private final Comparator<TagState> distanceDescComparator = (a, b) -> Double.compare(b.distanceSq, a.distanceSq);
    private final Comparator<EffectLine> effectWidthComparator = (a, b) -> Float.compare(a.width, b.width);
    private ClientLevel stateLevel;

    public NameTagsRender(NameTagsModule module) {
        this.module = module;
    }

    public void clear() {
        states.clear();
        drawList.clear();
        stateLevel = null;
    }

    public void trimStaleStates() {
        if (states.isEmpty()) return;
        states.values().removeIf(st -> !st.targeted || st.entity == null || st.entity.isRemoved());
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
        Vec3 eye = mc.player.getEyePosition(tickDelta);
        double maxDist = module.maxDistance.getValue();
        double maxDistSq = maxDist * maxDist;
        double friendMaxDistSq = (maxDist * 1.5) * (maxDist * 1.5);

        for (TagState state : states.values()) {
            state.targeted = false;
        }
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!isEntityTarget(mc, entity)) continue;
            double dx = entity.getX() - eye.x;
            double dy = entity.getY() - eye.y;
            double dz = entity.getZ() - eye.z;
            double dSq = dx * dx + dy * dy + dz * dz;
            double limitSq = (entity instanceof Player p && FriendManager.getInstance().isFriend(p)) ? friendMaxDistSq : maxDistSq;
            if (dSq > limitSq) continue;

            TagState state = states.get(entity.getId());
            if (state == null || state.entity != entity) {
                state = new TagState(entity);
                states.put(entity.getId(), state);
            }
            state.targeted = true;
        }

        drawList.clear();
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

        // Farther first so closer name tags overlap properly; reserve Liquid Glass for closest MAX_LIQUID_GLASS_TAGS
        if (drawList.size() > 1) {
            drawList.sort(distanceDescComparator);
        }
        int glassStartIdx = Math.max(0, drawList.size() - MAX_LIQUID_GLASS_TAGS);
        for (int i = 0; i < drawList.size(); i++) {
            TagState state = drawList.get(i);
            boolean allowGlass = (i >= glassStartIdx) && state.alpha > 0.7f;
            if (state.entity instanceof ItemEntity item) {
                renderItemTag(context, state, item, allowGlass);
            } else if (state.entity instanceof LivingEntity living) {
                renderEntityTag(mc, context, state, living, now, allowGlass);
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

    private void renderItemTag(GuiGraphicsExtractor context, TagState state, ItemEntity item, boolean allowGlass) {
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

        drawTagSurface(context, x, y, width, height, 3.5f, false, alpha, allowGlass);
        drawComponent(context, label, x + 4.0f, y + (height - 7.5f * module.tagScale.getFloatValue()) * 0.5f, textScale, alpha);
    }

    private void renderEntityTag(Minecraft mc, GuiGraphicsExtractor context, TagState state, LivingEntity entity, long now, boolean allowGlass) {
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

        boolean masked = dev.opticumeclient.client.modules.other.NameProtectModule.shouldMaskHud();
        boolean scaleChanged = Math.abs(state.cachedTextScale - textScale) > 0.001f;
        if (state.cachedName == null || scaleChanged || state.cachedMasked != masked || now - state.lastMetaRefreshMs >= CACHE_REFRESH_MS) {
            Component freshName = masked
                    ? dev.opticumeclient.client.modules.other.NameProtectModule.protectComponent(entity.getDisplayName())
                    : entity.getDisplayName();
            state.cachedName = freshName;
            state.cachedNameWidth = componentWidth(freshName, textScale);
            state.cachedMasked = masked;
            state.cachedTextScale = textScale;

            int pingVal = pingIntOf(mc, entity);
            if (pingVal != state.cachedPingVal || scaleChanged) {
                state.cachedPingVal = pingVal;
                if (pingVal >= 0) {
                    state.cachedPingLabel = pingVal + "ms";
                    state.cachedPingWidth = Render2DHelper.getTextWidth(state.cachedPingLabel, smallScale);
                } else {
                    state.cachedPingLabel = "";
                    state.cachedPingWidth = 0.0f;
                }
            }
            state.lastMetaRefreshMs = now;
        }

        float realHp = isPlayer ? TargetHudModule.getRealHealth(entity) + entity.getAbsorptionAmount()
                : entity.getHealth() + entity.getAbsorptionAmount();
        int hpInt = Math.max(0, Math.round(realHp));
        if (hpInt != state.cachedHpInt || scaleChanged || state.cachedHpText == null) {
            state.cachedHpInt = hpInt;
            state.cachedHpText = Integer.toString(hpInt);
            state.cachedHpWidth = Render2DHelper.getTextWidth(state.cachedHpText, textScale);
        }

        Component name = state.cachedName;
        String hpText = state.cachedHpText;
        String pingLabel = state.cachedPingLabel;
        float nameWidth = state.cachedNameWidth;
        float hpWidth = state.cachedHpWidth;
        float pingWidth = state.cachedPingWidth;

        boolean showHead = module.viewHead.isEnabled();
        float padX = 4.0f * sc;
        float width = padX + (showHead ? headSize + 4.0f * sc : 0.0f)
                + nameWidth + 5.0f * sc + heartSize + 2.0f * sc + hpWidth + padX;
        if (!pingLabel.isEmpty()) {
            width += 5.0f * sc + pingIconSize + 2.0f * sc + pingWidth;
        }
        float height = 14.5f * sc;
        float x = centerX - width * 0.5f;
        float y = centerY - height * 0.5f;

        int accent = isFriend ? FRIEND_GREEN : ThemeManager.getInstance().getAccentColor(0);
        int a255 = Mth.clamp(Math.round(255.0f * alpha), 0, 255);
        int textColor = ColorUtils.rgba(245, 247, 255, a255);
        float textY = y + (height - 8.0f * sc) * 0.5f + 0.5f;

        drawTagSurface(context, x, y, width, height, 4.5f * sc, isFriend, alpha, allowGlass);

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

        if (!pingLabel.isEmpty()) {
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
                    pingLabel,
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

    private void drawTagSurface(GuiGraphicsExtractor context, float x, float y, float w, float h, float r, boolean isFriend, float alpha, boolean allowGlass) {
        int aBg = Mth.clamp(Math.round(215.0f * alpha), 0, 255);
        int accent = isFriend ? FRIEND_GREEN : ThemeManager.getInstance().getAccentColor(0);
        if (allowGlass && module.liquidGlass.isEnabled() && alpha > 0.15f) {
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

    private static int pingIntOf(Minecraft mc, LivingEntity entity) {
        if (!(entity instanceof Player player) || mc.getConnection() == null) return -1;
        PlayerInfo info = mc.getConnection().getPlayerInfo(player.getUUID());
        return info == null ? -1 : Math.max(0, info.getLatency());
    }

    private float drawHeldItems(GuiGraphicsExtractor context, LivingEntity entity, float centerX, float startY, float sc, float alpha) {
        float fontScale = 0.65f * sc;
        float y = startY;
        int aBg = Mth.clamp(Math.round(190.0f * alpha), 0, 255);
        int aText = Mth.clamp(Math.round(255.0f * alpha), 0, 255);

        for (EquipmentSlot slot : HELD_SLOTS) {
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
        armorScratch.clear();
        for (EquipmentSlot slot : DISPLAY_ORDER) {
            ItemStack stack = entity.getItemBySlot(slot);
            if (!stack.isEmpty()) armorScratch.add(stack);
        }
        if (armorScratch.isEmpty()) return;

        float itemSize = 13.0f * sc;
        float step = 15.0f * sc;
        float y = pillTop - itemSize - 3.5f * sc;
        float offsetX = centerX - ((armorScratch.size() - 1) * step + itemSize) * 0.5f;
        float enchScale = 0.58f * sc;
        int a = Mth.clamp(Math.round(255.0f * alpha), 0, 255);

        for (int i = 0; i < armorScratch.size(); i++) {
            ItemStack stack = armorScratch.get(i);
            NameTagItemRenderer.draw(context, stack, offsetX, y, itemSize, alpha);

            if (module.viewEnchants.isEnabled() && !stack.getEnchantments().isEmpty()) {
                float enchantY = y - 6.5f * sc;
                collectEnchants(stack);
                for (int e = 0; e < enchantLabelsScratch.size(); e++) {
                    String enchantment = enchantLabelsScratch.get(e);
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

    private void collectEnchants(ItemStack stack) {
        enchantEntriesScratch.clear();
        enchantLabelsScratch.clear();
        enchantEntriesScratch.addAll(stack.getEnchantments().entrySet());
        if (enchantEntriesScratch.size() > 1) {
            enchantEntriesScratch.sort((a, b) -> Integer.compare(b.getIntValue(), a.getIntValue()));
        }
        for (int i = 0; i < enchantEntriesScratch.size(); i++) {
            Object2IntMap.Entry<Holder<Enchantment>> entry = enchantEntriesScratch.get(i);
            String full = entry.getKey().value().description().getString();
            textScratch.setLength(0);
            int len = Math.min(2, full.length());
            textScratch.append(full, 0, len).append(entry.getIntValue());
            enchantLabelsScratch.add(textScratch.toString());
        }
    }

    private void renderEffects(GuiGraphicsExtractor context, LivingEntity entity, float centerX, float startY, float sc, float alpha) {
        var effects = entity.getActiveEffects();
        if (effects.isEmpty()) return;

        float fontScale = 0.62f * sc;
        effectLinesScratch.clear();
        for (MobEffectInstance inst : effects) {
            int durationTicks = inst.getDuration();
            if (durationTicks <= 20) continue;
            String name = inst.getEffect().value().getDisplayName().getString();
            int amp = inst.getAmplifier() + 1;
            textScratch.setLength(0);
            textScratch.append(name);
            if (amp > 1) {
                textScratch.append(' ');
                appendRomanNumeral(textScratch, amp);
            }
            textScratch.append(' ');
            appendDuration(textScratch, durationTicks);
            String text = textScratch.toString();
            effectLinesScratch.add(new EffectLine(text, Render2DHelper.getTextWidth(text, fontScale), effectColor(inst)));
        }
        if (effectLinesScratch.size() > 1) {
            effectLinesScratch.sort(effectWidthComparator);
        }

        float step = 7.5f * sc;
        int a = Mth.clamp(Math.round(255.0f * alpha), 0, 255);
        for (int i = 0; i < effectLinesScratch.size(); i++) {
            EffectLine line = effectLinesScratch.get(i);
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

    private static void appendDuration(StringBuilder sb, int ticks) {
        int seconds = ticks / 20;
        int minutes = seconds / 60;
        seconds = seconds % 60;
        sb.append(minutes).append(':');
        if (seconds < 10) sb.append('0');
        sb.append(seconds);
    }

    private static void appendRomanNumeral(StringBuilder sb, int number) {
        if (number < 1 || number > 3999) {
            sb.append(number);
            return;
        }
        sb.append(ROMAN_THOUSANDS[number / 1000])
                .append(ROMAN_HUNDREDS[(number % 1000) / 100])
                .append(ROMAN_TENS[(number % 100) / 10])
                .append(ROMAN_ONES[number % 10]);
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
        Component cachedName;
        float cachedNameWidth;
        float cachedTextScale = -1.0f;
        boolean cachedMasked;
        int cachedHpInt = Integer.MIN_VALUE;
        String cachedHpText;
        float cachedHpWidth;
        int cachedPingVal = Integer.MIN_VALUE;
        String cachedPingLabel = "";
        float cachedPingWidth;
        long lastMetaRefreshMs;
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
