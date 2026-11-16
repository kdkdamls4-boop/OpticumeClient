package dev.opticumeclient.client.modules.visuals;

import dev.opticumeclient.client.friend.FriendManager;
import dev.opticumeclient.client.gui.ClickGuiScreen;
import dev.opticumeclient.client.hud.HudManager;
import dev.opticumeclient.client.modules.Category;
import dev.opticumeclient.client.modules.Module;
import dev.opticumeclient.client.modules.ModuleManager;
import dev.opticumeclient.client.modules.combat.KillAuraModule;
import dev.opticumeclient.client.modules.combat.LegitAuraModule;
import dev.opticumeclient.client.modules.combat.OpticumeAuraModule;
import dev.opticumeclient.client.render.Animation;
import dev.opticumeclient.client.render.ColorUtils;
import dev.opticumeclient.client.render.Easing;
import dev.opticumeclient.client.render.Render2DHelper;
import dev.opticumeclient.client.render.TextureAssets;
import dev.opticumeclient.client.render.Theme;
import dev.opticumeclient.client.render.ThemeManager;
import dev.opticumeclient.client.setting.BooleanSetting;
import dev.opticumeclient.client.setting.ColorSetting;
import dev.opticumeclient.client.setting.ModeSetting;
import dev.opticumeclient.client.setting.NumberSetting;
import net.minecraft.client.gui.GuiGraphicsExtractor; 
import net.minecraft.client.gui.screens.ChatScreen; 
import net.minecraft.client.player.AbstractClientPlayer; 
import net.minecraft.world.entity.Entity; 
import net.minecraft.world.entity.EquipmentSlot; 
import net.minecraft.world.entity.LivingEntity; 
import net.minecraft.world.entity.player.Player; 
import net.minecraft.world.item.ItemStack; 
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ReadOnlyScoreInfo;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.resources.Identifier; 
import net.minecraft.world.phys.EntityHitResult; 
import net.minecraft.world.phys.HitResult; 
import net.minecraft.util.Mth; 
import net.minecraft.world.phys.Vec3; 
import org.joml.Matrix3x2fStack;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Flagship TargetHUD (Nursultan Nextgen / Catlavan / ThunderHack Recode).
 * Features spring EASE_OUT_BACK animation, 2D player skin avatar with hurt compression,
 * radial glowing hit particles, dual-layer interpolated health & damage trail bar,
 * WIN/LOSE combat evaluation badge, and 0.75x scaled equipment display.
 */
public class TargetHudModule extends Module {
    private static TargetHudModule instance;

    private final ModeSetting style = new ModeSetting("Style", "Визуальный стиль TargetHUD", "Nursultan", "Nursultan", "Catlavan", "ThunderHack");
    private final BooleanSetting particles = new BooleanSetting("HitParticles", "Разлетающиеся светящиеся частицы при ударе", true);
    private final BooleanSetting showEquipment = new BooleanSetting("Equipment", "Отображать броню и предметы в руках цели", true);
    private final BooleanSetting showDistance = new BooleanSetting("Distance", "Отображать дистанцию до цели", true);
    private final NumberSetting posX = new NumberSetting("PosX", "Позиция X на экране", 520.0, 0.0, 1920.0, 2.0);
    private final NumberSetting posY = new NumberSetting("PosY", "Позиция Y на экране", 320.0, 0.0, 1080.0, 2.0);
    private final ColorSetting barColor = new ColorSetting("BarColor", "Дополнительный цвет полосы здоровья", ColorUtils.OPTICUME_CYAN);

    private final Animation openAnim = new Animation(0.0f, 280L, Easing.EASE_OUT_BACK);
    private final List<HeadParticle> headParticles = new ArrayList<>();
    private final HudManager.DraggableWidget dragWidget;

    private LivingEntity currentTarget = null;
    private LivingEntity lastTarget = null;
    private LivingEntity lastAttackedEntity = null;
    private long lastAttackTime = 0L;

    private float healthLerp = 20.0f;
    private float trailLerp = 20.0f;
    private int prevHurtTime = 0;

    private static class HeadParticle {
        float x;
        float y;
        float vx;
        float vy;
        float size;
        float alpha;
        int color;
        boolean useStar;

        HeadParticle(float x, float y, float vx, float vy, float size, int color, boolean useStar) {
            this.x = x;
            this.y = y;
            this.vx = vx;
            this.vy = vy;
            this.size = size;
            this.alpha = 1.0f;
            this.color = color;
            this.useStar = useStar;
        }

        boolean update() {
            x += vx;
            y += vy;
            vx *= 0.93f;
            vy *= 0.93f;
            alpha -= 0.032f;
            size *= 0.988f;
            return alpha > 0.02f && size > 1.2f;
        }
    }

    public TargetHudModule() {
        super("TargetHUD", "Премиум панель цели (Nursultan / Catlavan / ThunderHack)", Category.VISUALS);
        instance = this;

        addSetting(style);
        addSetting(particles);
        addSetting(showEquipment);
        addSetting(showDistance);
        addSetting(posX);
        addSetting(posY);
        addSetting(barColor);

        setEnabled(true);

        this.dragWidget = HudManager.getInstance().registerWidget(new HudManager.DraggableWidget(
                "TargetHUD",
                posX.getFloatValue(),
                posY.getFloatValue(),
                176.0f,
                58.0f,
                this::isEnabled,
                (newX, newY) -> {
                    posX.setValue((double) Math.round(newX));
                    posY.setValue((double) Math.round(newY));
                }
        ));
    }

    public static TargetHudModule getInstance() {
        if (instance == null) {
            instance = ModuleManager.getInstance().getModule(TargetHudModule.class);
        }
        return instance;
    }

    @Override
    public String getDisplaySuffix() {
        return style.getMode();
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.level == null) {
            currentTarget = null;
            lastTarget = null;
            headParticles.clear();
            return;
        }

        // Track player's last attacked entity within 12 blocks for 4 seconds
        LivingEntity attacking = mc.player.getLastHurtMob();
        if (attacking != null && attacking != mc.player && attacking.isAlive() && mc.player.distanceTo(attacking) <= 12.0f) {
            lastAttackedEntity = attacking;
            lastAttackTime = System.currentTimeMillis();
        } else if (mc.player.isSwinging() && mc.crosshairPickEntity instanceof LivingEntity targeted && targeted != mc.player && targeted.isAlive()) {
            lastAttackedEntity = targeted;
            lastAttackTime = System.currentTimeMillis();
        }

        currentTarget = findTarget();
        if (currentTarget != null && currentTarget.isAlive()) {
            if (lastTarget != currentTarget) {
                float maxHp = Math.max(1.0f, currentTarget.getMaxHealth());
                float curHp = Math.max(0.0f, Math.min(maxHp, getRealHealth(currentTarget)));
                healthLerp = curHp;
                trailLerp = curHp;
                prevHurtTime = 0;
            }
            lastTarget = currentTarget;
            openAnim.animateTo(1.0f, 280L, Easing.EASE_OUT_BACK);
        } else {
            openAnim.animateTo(0.0f, 220L, Easing.EASE_IN_BACK);
            if (openAnim.getValue() <= 0.02f) {
                lastTarget = null;
            }
        }
    }

    /**
     * Reads real server HP from the BELOW_NAME Scoreboard objective on servers like FunTime, HolyWorld,
     * and Dexland where entity.getHealth() is spoofed to 20.0f by anti-ESP plugins.
     */
    public static float getRealHealth(LivingEntity target) {
        if (target == null) return 0.0f;
        float raw = target.getHealth();
        if (target instanceof Player player && target != net.minecraft.client.Minecraft.getInstance().player) {
            try {
                var level = net.minecraft.client.Minecraft.getInstance().level;
                if (level != null) {
                    Scoreboard scoreboard = level.getScoreboard();
                    Objective obj = scoreboard.getDisplayObjective(DisplaySlot.BELOW_NAME);
                    if (obj != null) {
                        ReadOnlyScoreInfo info = scoreboard.getPlayerScoreInfo(player, obj);
                        if (info != null && info.value() > 0 && info.value() <= 200) {
                            return (float) info.value();
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        return raw;
    }

    @Override
    public void onRender2D(GuiGraphicsExtractor context, float tickDelta) {
        if (!isEnabled() || mc.player == null) return;

        // Re-check preview mode in case onRender2D runs while in ChatScreen / ClickGuiScreen
        if (mc.gui.screen() instanceof ChatScreen || mc.gui.screen() instanceof ClickGuiScreen) {
            if (currentTarget == null || !currentTarget.isAlive()) {
                currentTarget = mc.player;
                lastTarget = mc.player;
            }
            openAnim.animateTo(1.0f, 260L, Easing.EASE_OUT_BACK);
        }

        float anim = openAnim.getValue();
        LivingEntity target = (currentTarget != null && currentTarget.isAlive()) ? currentTarget : lastTarget;
        if (target == null || anim <= 0.015f) {
            return;
        }

        String mode = style.getMode();
        float cardW = mode.equalsIgnoreCase("Catlavan") ? 182.0f : (mode.equalsIgnoreCase("ThunderHack") ? 176.0f : 174.0f);
        float cardH = showEquipment.isEnabled() ? 58.0f : 46.0f;

        dragWidget.syncExternalPosition(posX.getFloatValue(), posY.getFloatValue());
        dragWidget.setBounds(cardW, cardH);

        float x = dragWidget.getX();
        float y = dragWidget.getY();

        // Update smooth health & trailing damage bars (using Scoreboard HP when available)
        float rawRealHp = getRealHealth(target);
        float maxHp = Math.max(1.0f, Math.max(target.getMaxHealth(), rawRealHp));
        float curHp = Math.max(0.0f, Math.min(maxHp, rawRealHp));
        float absorption = Math.max(0.0f, target.getAbsorptionAmount());

        healthLerp = Animation.lerp(healthLerp, curHp, 0.16f);
        if (trailLerp < healthLerp) {
            trailLerp = healthLerp;
        } else {
            trailLerp = Animation.lerp(trailLerp, curHp, 0.045f);
        }

        // Check for new hit (hurtTime >= 8) to spawn flying particles from avatar center
        float headCenterX = x + 24.0f;
        float headCenterY = y + cardH * 0.5f;
        int hurtTime = target.hurtTime;
        if (particles.isEnabled()) {
            if (hurtTime >= 8 && prevHurtTime < 8) {
                spawnHitParticles(headCenterX, headCenterY, 12);
            }
        }
        prevHurtTime = hurtTime;

        float clampedAlpha = Math.max(0.0f, Math.min(1.0f, anim));
        float scale = Math.max(0.08f, anim);

        Matrix3x2fStack matrices = context.pose();
        matrices.pushMatrix();
        float cx = x + cardW * 0.5f;
        float cy = y + cardH * 0.5f;
        matrices.translate(cx, cy);
        matrices.scale(scale, scale);
        matrices.translate(-cx, -cy);

        if (mode.equalsIgnoreCase("Catlavan")) {
            renderCatlavanStyle(context, target, x, y, cardW, cardH, curHp, maxHp, absorption, clampedAlpha, tickDelta);
        } else if (mode.equalsIgnoreCase("ThunderHack")) {
            renderThunderHackStyle(context, target, x, y, cardW, cardH, curHp, maxHp, absorption, clampedAlpha, tickDelta);
        } else {
            renderNursultanStyle(context, target, x, y, cardW, cardH, curHp, maxHp, absorption, clampedAlpha, tickDelta);
        }

        // Render & update hit particles on top of avatar
        if (particles.isEnabled() && !headParticles.isEmpty()) {
            renderAndStepParticles(context, clampedAlpha);
        }

        matrices.popMatrix();
    }

    private void renderNursultanStyle(GuiGraphicsExtractor context, LivingEntity target, float x, float y, float w, float h,
                                      float curHp, float maxHp, float absorption, float alpha, float tickDelta) {
        Theme theme = ThemeManager.getInstance().getCurrentTheme();
        int c1 = theme.getPrimaryColor();
        int c2 = theme.getSecondaryColor();

        // 1. 4-Layer Liquid Glass Card
        Render2DHelper.drawLiquidGlass(context, x, y, w, h, 7.0f, (int) (222 * alpha), (int) (46 * alpha));

        // 2. Avatar with hurt compression & red flash
        float headSize = 36.0f;
        float headX = x + 6.0f;
        float headY = y + (h - headSize) * 0.5f;
        renderTargetAvatar(context, target, headX, headY, headSize, tickDelta, alpha, c1, c2);

        // 3. Name + Friend tag + WIN/LOSE Badge
        float contentX = headX + headSize + 7.0f;
        float contentW = w - (contentX - x) - 7.0f;

        boolean isFriend = FriendManager.getInstance().isFriend(target);
        String rawName = target.getName().getString();
        if (rawName.length() > 13) rawName = rawName.substring(0, 12) + "..";
        String displayName = isFriend ? "§a[F] §f" + rawName : rawName;
        Render2DHelper.drawScaledText(context, displayName, contentX, y + 6.0f,
                ColorUtils.withAlpha(0xFFFFFFFF, (int) (255 * alpha)), 0.85f, true);

        renderWinLoseBadge(context, target, curHp, absorption, x + w - 6.0f, y + 5.5f, alpha);

        // 4. Equipment Row (0.75f scale) & Distance
        if (showEquipment.isEnabled()) {
            renderEquipmentRow(context, target, contentX, y + 17.5f, 0.75f);
        }

        if (showDistance.isEnabled() && mc.player != null) {
            String distStr = String.format(Locale.US, "%.1fm", mc.player.distanceTo(target));
            float distW = Render2DHelper.getTextWidth(distStr, 0.68f);
            Render2DHelper.drawScaledText(context, distStr, x + w - distW - 7.0f, y + (showEquipment.isEnabled() ? 20.5f : 18.0f),
                    ColorUtils.withAlpha(theme.getMutedTextColor(), (int) (230 * alpha)), 0.68f, false);
        }

        // 5. Dual-Layer Health & Trail Bar + Absorption + HP Counter
        float barY = y + h - 14.5f;
        float barH = 8.5f;
        renderDualHealthBar(context, contentX, barY, contentW, barH, 3.8f, curHp, maxHp, absorption, c1, c2, alpha, true);
    }

    private void renderCatlavanStyle(GuiGraphicsExtractor context, LivingEntity target, float x, float y, float w, float h,
                                     float curHp, float maxHp, float absorption, float alpha, float tickDelta) {
        Theme theme = ThemeManager.getInstance().getCurrentTheme();
        int c1 = theme.getPrimaryColor();
        int c2 = barColor.isRainbow() ? ColorUtils.rainbow(0, 0.85f, 1.0f) : theme.getSecondaryColor();

        // Deep cyber-graphite card with neon left indicator bar
        Render2DHelper.drawLiquidGlass(context, x, y, w, h, 6.0f, (int) (235 * alpha), (int) (55 * alpha));
        Render2DHelper.drawActiveCardIndicator(context, x + 1.5f, y + 3.0f, h - 6.0f, alpha, c1, c2);

        // Top cyber gradient accent strip
        Render2DHelper.drawGradientRoundedRect(context, x + 8.0f, y + 1.0f, w - 16.0f, 1.5f, 0.8f,
                ColorUtils.withAlpha(c1, (int) (210 * alpha)),
                ColorUtils.withAlpha(c2, (int) (210 * alpha)));

        float headSize = 38.0f;
        float headX = x + 8.0f;
        float headY = y + (h - headSize) * 0.5f;
        renderTargetAvatar(context, target, headX, headY, headSize, tickDelta, alpha, c1, c2);

        float contentX = headX + headSize + 7.0f;
        float contentW = w - (contentX - x) - 7.0f;

        String rawName = target.getName().getString();
        if (rawName.length() > 13) rawName = rawName.substring(0, 12) + "..";
        Render2DHelper.drawScaledText(context, rawName, contentX, y + 6.0f,
                ColorUtils.withAlpha(0xFFFFFFFF, (int) (255 * alpha)), 0.86f, true);

        renderWinLoseBadge(context, target, curHp, absorption, x + w - 6.0f, y + 5.5f, alpha);

        // Equipment slots with subtle dark backplates
        if (showEquipment.isEnabled()) {
            renderEquipmentSlotsCatlavan(context, target, contentX, y + 17.5f, alpha);
        }

        // Health + Absorption readout above bar on right
        String hpLabel = String.format(Locale.US, "%.1f HP", curHp) + (absorption > 0.1f ? String.format(Locale.US, " §6+%.0f", absorption) : "");
        float hpW = Render2DHelper.getTextWidth(hpLabel, 0.68f);
        Render2DHelper.drawScaledText(context, hpLabel, x + w - hpW - 7.0f, y + (showEquipment.isEnabled() ? 20.5f : 18.0f),
                ColorUtils.withAlpha(c2, (int) (245 * alpha)), 0.68f, true);

        float barY = y + h - 13.0f;
        float barH = 7.0f;
        renderDualHealthBar(context, contentX, barY, contentW, barH, 3.0f, curHp, maxHp, absorption, c1, c2, alpha, false);
    }

    private void renderThunderHackStyle(GuiGraphicsExtractor context, LivingEntity target, float x, float y, float w, float h,
                                        float curHp, float maxHp, float absorption, float alpha, float tickDelta) {
        Theme theme = ThemeManager.getInstance().getCurrentTheme();
        int c1 = theme.getPrimaryColor();
        int c2 = theme.getSecondaryColor();

        // Dual-tone obsidian glass card inspired by ThunderHack Recode
        Render2DHelper.drawGlow(context, x, y, w, h, 8.0f, ColorUtils.withAlpha(c1, (int) (60 * alpha)));
        Render2DHelper.drawRoundedRect(context, x, y, w, h, 7.0f, ColorUtils.withAlpha(0xFF0A0B10, (int) (235 * alpha)));
        Render2DHelper.drawRoundedRect(context, x + 48.0f, y + 1.0f, w - 49.0f, h - 2.0f, 6.0f,
                ColorUtils.withAlpha(0xFF11131D, (int) (220 * alpha)));

        // Subtle THUD / glow watermark texture on right side
        Render2DHelper.drawTexture(context, TextureAssets.THUD, x + w - 46.0f, y + 4.0f, 40.0f, h - 8.0f,
                ColorUtils.withAlpha(c1, (int) (28 * alpha)));

        Render2DHelper.drawRoundedOutline(context, x, y, w, h, 7.0f, 1.0f,
                ColorUtils.withAlpha(ColorUtils.interpolateColor(c1, c2, 0.5f), (int) (130 * alpha)));

        float headSize = 40.0f;
        float headX = x + 5.0f;
        float headY = y + (h - headSize) * 0.5f;
        renderTargetAvatar(context, target, headX, headY, headSize, tickDelta, alpha, c1, c2);

        float contentX = x + 51.0f;
        float contentW = w - 58.0f;

        String rawName = target.getName().getString();
        if (rawName.length() > 13) rawName = rawName.substring(0, 12) + "..";
        Render2DHelper.drawScaledText(context, rawName, contentX, y + 5.5f,
                ColorUtils.withAlpha(0xFFFFFFFF, (int) (255 * alpha)), 0.85f, true);

        renderWinLoseBadge(context, target, curHp, absorption, x + w - 6.0f, y + 5.0f, alpha);

        // Glowing central health bar (ThunderHack layout: bar in middle, armor at bottom)
        float barY = showEquipment.isEnabled() ? (y + 19.0f) : (y + 24.0f);
        float barH = 10.0f;
        renderDualHealthBar(context, contentX, barY, contentW, barH, 3.0f, curHp, maxHp, absorption, c1, c2, alpha, true);

        if (showEquipment.isEnabled()) {
            renderEquipmentRow(context, target, contentX, y + 33.5f, 0.75f);
            if (showDistance.isEnabled() && mc.player != null) {
                String distStr = String.format(Locale.US, "%.1fm", mc.player.distanceTo(target));
                float distW = Render2DHelper.getTextWidth(distStr, 0.68f);
                Render2DHelper.drawScaledText(context, distStr, x + w - distW - 7.0f, y + 37.0f,
                        ColorUtils.withAlpha(theme.getMutedTextColor(), (int) (220 * alpha)), 0.68f, false);
            }
        }
    }

    private void renderTargetAvatar(GuiGraphicsExtractor context, LivingEntity target, float headX, float headY, float headSize,
                                    float tickDelta, float alpha, int c1, int c2) {
        float hurtProgress = 0.0f;
        if (target.hurtTime > 0) {
            hurtProgress = Math.max(0.0f, Math.min(1.0f, (target.hurtTime - tickDelta) / 10.0f));
        }

        // Compress avatar scale on hit (1.0 -> 0.84)
        float hurtScale = 1.0f - hurtProgress * 0.16f;
        float centerX = headX + headSize * 0.5f;
        float centerY = headY + headSize * 0.5f;

        // Outer glowing frame (turns crimson red when target is hit)
        int frameColor = hurtProgress > 0.05f
                ? ColorUtils.interpolateColor(c1, 0xFFFF2E54, Math.min(1.0f, hurtProgress * 1.3f))
                : ColorUtils.interpolateColor(c1, c2, 0.5f);

        Render2DHelper.drawGlow(context, headX, headY, headSize, headSize, 5.0f,
                ColorUtils.withAlpha(frameColor, (int) ((55 + 95 * hurtProgress) * alpha)));
        Render2DHelper.drawRoundedRect(context, headX - 1.0f, headY - 1.0f, headSize + 2.0f, headSize + 2.0f, 5.5f,
                ColorUtils.withAlpha(0xFF0B0D14, (int) (220 * alpha)));

        Matrix3x2fStack matrices = context.pose();
        matrices.pushMatrix();
        matrices.translate(centerX, centerY);
        matrices.scale(hurtScale, hurtScale);
        matrices.translate(-centerX, -centerY);

        if (target instanceof AbstractClientPlayer clientPlayer) {
            Render2DHelper.drawPlayerHead(context, clientPlayer, Math.round(headX), Math.round(headY), Math.round(headSize), hurtProgress);
            if (hurtProgress > 0.02f) {
                Render2DHelper.drawRoundedRect(context, headX, headY, headSize, headSize, 4.0f,
                        ColorUtils.withAlpha(0xFFFF1744, (int) (hurtProgress * 115 * alpha)));
            }
        } else {
            // Stylized fallback avatar for mobs/non-player entities
            int bg = ColorUtils.interpolateColor(0xFF1A1E2E, 0xFF881B2B, hurtProgress);
            Render2DHelper.drawRoundedRect(context, headX, headY, headSize, headSize, 4.5f, ColorUtils.withAlpha(bg, (int) (240 * alpha)));
            String initial = target.getName().getString().isEmpty() ? "?" : target.getName().getString().substring(0, 1).toUpperCase();
            Render2DHelper.drawCenteredText(context, initial, centerX, centerY - 4.0f,
                    ColorUtils.withAlpha(0xFFFFFFFF, (int) (255 * alpha)), true);
        }

        // Rounded mask bevel border around head
        Render2DHelper.drawRoundedOutline(context, headX - 0.5f, headY - 0.5f, headSize + 1.0f, headSize + 1.0f, 5.0f, 1.2f,
                ColorUtils.withAlpha(frameColor, (int) (190 * alpha)));

        matrices.popMatrix();
    }

    private void renderDualHealthBar(GuiGraphicsExtractor context, float barX, float barY, float barW, float barH, float radius,
                                     float curHp, float maxHp, float absorption, int c1, int c2, float alpha, boolean drawTextInside) {
        // 1. Dark glass track
        Render2DHelper.drawRoundedRect(context, barX, barY, barW, barH, radius,
                ColorUtils.withAlpha(0xFF090B11, (int) (210 * alpha)));

        float hpPct = Math.max(0.0f, Math.min(1.0f, healthLerp / maxHp));
        float trailPct = Math.max(hpPct, Math.min(1.0f, trailLerp / maxHp));

        float fillW = barW * hpPct;
        float trailW = barW * trailPct;

        // 2. Lagging golden-white damage trail bar (trailLerp)
        if (trailW > fillW + 0.5f) {
            Render2DHelper.drawRoundedRect(context, barX, barY, Math.max(radius * 2.0f, trailW), barH, radius,
                    ColorUtils.withAlpha(0xFFFFE599, (int) (190 * alpha)));
        }

        // 3. Main smooth gradient health bar (healthLerp)
        if (fillW > 0.5f) {
            float clampedFillW = Math.max(radius * 2.0f, fillW);
            int leftColor = ColorUtils.withAlpha(c1, (int) (255 * alpha));
            int rightColor = ColorUtils.withAlpha(barColor.isRainbow() ? ColorUtils.rainbow(0, 0.85f, 1.0f) : c2, (int) (255 * alpha));
            Render2DHelper.drawGlow(context, barX, barY, clampedFillW, barH, 4.0f,
                    ColorUtils.withAlpha(c1, (int) (65 * alpha)));
            Render2DHelper.drawGradientRoundedRect(context, barX, barY, clampedFillW, barH, radius, leftColor, rightColor);
        }

        // 4. Golden Absorption Hearts overlay strip
        if (absorption > 0.05f) {
            float absPct = Math.max(0.08f, Math.min(1.0f, absorption / 20.0f));
            float absW = Math.max(radius * 2.0f, barW * absPct);
            Render2DHelper.drawGradientRoundedRect(context, barX, barY, absW, Math.max(2.5f, barH * 0.42f), radius * 0.6f,
                    ColorUtils.withAlpha(0xFFFFD700, (int) (220 * alpha)),
                    ColorUtils.withAlpha(0xFFFFAB00, (int) (220 * alpha)));
        }

        // 5. Subtle outline
        Render2DHelper.drawRoundedOutline(context, barX, barY, barW, barH, radius, 1.0f,
                ColorUtils.withAlpha(0xFFFFFFFF, (int) (32 * alpha)));

        // 6. Exact HP + Absorption text inside/over bar
        if (drawTextInside) {
            String hpStr = String.format(Locale.US, "%.1f HP", curHp)
                    + (absorption > 0.1f ? String.format(Locale.US, " +%.1f", absorption) : "");
            float scale = 0.66f;
            float tw = Render2DHelper.getTextWidth(hpStr, scale);
            float tx = barX + (barW - tw) * 0.5f;
            float ty = barY + (barH - 5.5f) * 0.5f;
            Render2DHelper.drawScaledText(context, hpStr, tx, ty,
                    ColorUtils.withAlpha(0xFFFFFFFF, (int) (250 * alpha)), scale, true);
        }
    }

    private void renderWinLoseBadge(GuiGraphicsExtractor context, LivingEntity target, float targetHp, float targetAbs,
                                    float rightEdgeX, float topY, float alpha) {
        float myTotal = mc.player != null ? (mc.player.getHealth() + mc.player.getAbsorptionAmount()) : 20.0f;
        float enemyTotal = targetHp + targetAbs;
        boolean win = myTotal >= enemyTotal;

        String label = win ? "WIN" : "LOSE";
        float scale = 0.64f;
        float tw = Render2DHelper.getTextWidth(label, scale);
        float pillW = tw + 7.0f;
        float pillH = 10.0f;
        float pillX = rightEdgeX - pillW;

        int bg = win ? ColorUtils.withAlpha(0xFF16A34A, (int) (65 * alpha)) : ColorUtils.withAlpha(0xFFDC2626, (int) (65 * alpha));
        int border = win ? ColorUtils.withAlpha(0xFF4ADE80, (int) (185 * alpha)) : ColorUtils.withAlpha(0xFFF87171, (int) (185 * alpha));
        int textCol = win ? ColorUtils.withAlpha(0xFF4ADE80, (int) (255 * alpha)) : ColorUtils.withAlpha(0xFFF87171, (int) (255 * alpha));

        Render2DHelper.drawRoundedRect(context, pillX, topY, pillW, pillH, 3.0f, bg);
        Render2DHelper.drawRoundedOutline(context, pillX, topY, pillW, pillH, 3.0f, 1.0f, border);
        Render2DHelper.drawScaledText(context, label, pillX + 3.5f, topY + 2.2f, textCol, scale, false);
    }

    private void renderEquipmentRow(GuiGraphicsExtractor context, LivingEntity target, float startX, float startY, float scale) {
        ItemStack[] stacks = new ItemStack[] {
                target.getItemBySlot(EquipmentSlot.HEAD),
                target.getItemBySlot(EquipmentSlot.CHEST),
                target.getItemBySlot(EquipmentSlot.LEGS),
                target.getItemBySlot(EquipmentSlot.FEET),
                target.getMainHandItem(),
                target.getOffhandItem()
        };

        float itemX = startX;
        Matrix3x2fStack matrices = context.pose();
        for (ItemStack stack : stacks) {
            if (stack != null && !stack.isEmpty()) {
                matrices.pushMatrix();
                matrices.translate(itemX, startY);
                matrices.scale(scale, scale);
                context.item(stack, 0, 0);
                context.itemDecorations(mc.font, stack, 0, 0);
                matrices.popMatrix();
                itemX += 13.5f;
            }
        }
    }

    private void renderEquipmentSlotsCatlavan(GuiGraphicsExtractor context, LivingEntity target, float startX, float startY, float alpha) {
        ItemStack[] stacks = new ItemStack[] {
                target.getItemBySlot(EquipmentSlot.HEAD),
                target.getItemBySlot(EquipmentSlot.CHEST),
                target.getItemBySlot(EquipmentSlot.LEGS),
                target.getItemBySlot(EquipmentSlot.FEET),
                target.getMainHandItem(),
                target.getOffhandItem()
        };

        float itemX = startX;
        Matrix3x2fStack matrices = context.pose();
        for (ItemStack stack : stacks) {
            Render2DHelper.drawRoundedRect(context, itemX - 1.0f, startY - 1.0f, 13.0f, 13.0f, 2.5f,
                    ColorUtils.withAlpha(0xFF0A0C12, (int) (145 * alpha)));
            if (stack != null && !stack.isEmpty()) {
                matrices.pushMatrix();
                matrices.translate(itemX - 0.5f, startY - 0.5f);
                matrices.scale(0.75f, 0.75f);
                context.item(stack, 0, 0);
                context.itemDecorations(mc.font, stack, 0, 0);
                matrices.popMatrix();
            }
            itemX += 14.0f;
        }
    }

    private void spawnHitParticles(float centerX, float centerY, int count) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        for (int i = 0; i < count; i++) {
            double angle = (Math.PI * 2.0 * i / count) + rng.nextDouble(-0.35, 0.35);
            float speed = (float) rng.nextDouble(1.5, 3.8);
            float vx = (float) Math.cos(angle) * speed;
            float vy = (float) Math.sin(angle) * speed;
            float size = (float) rng.nextDouble(6.5, 11.5);
            int color = ThemeManager.getInstance().getAccentColor(i * 30);
            boolean useStar = (i % 2 == 0);
            headParticles.add(new HeadParticle(centerX, centerY, vx, vy, size, color, useStar));
        }
    }

    private void renderAndStepParticles(GuiGraphicsExtractor context, float masterAlpha) {
        TextureAssets.ensureInitialized();
        Iterator<HeadParticle> it = headParticles.iterator();
        while (it.hasNext()) {
            HeadParticle p = it.next();
            if (!p.update()) {
                it.remove();
                continue;
            }
            float a = p.alpha * masterAlpha;
            if (a <= 0.02f) continue;

            int glowCol = ColorUtils.withAlpha(p.color, (int) (150 * a));
            int coreCol = ColorUtils.withAlpha(p.color, (int) (255 * a));
            Identifier tex = p.useStar ? TextureAssets.STAR : TextureAssets.FIREFLY;

            float glowSize = p.size * 2.1f;
            Render2DHelper.drawTexture(context, TextureAssets.GLOW_RADIAL,
                    p.x - glowSize * 0.5f, p.y - glowSize * 0.5f, glowSize, glowSize, glowCol);
            Render2DHelper.drawTexture(context, tex,
                    p.x - p.size * 0.5f, p.y - p.size * 0.5f, p.size, p.size, coreCol);
        }
    }

    private LivingEntity findTarget() {
        if (mc.player == null || mc.level == null) return null;

        // 1. OpticumeAuraModule
        OpticumeAuraModule opticumeAura = ModuleManager.getInstance().getModule(OpticumeAuraModule.class);
        if (opticumeAura != null && opticumeAura.isEnabled() && opticumeAura.getTarget() != null && opticumeAura.getTarget().isAlive()) {
            return opticumeAura.getTarget();
        }

        // 2. KillAuraModule
        KillAuraModule killAura = ModuleManager.getInstance().getModule(KillAuraModule.class);
        if (killAura != null && killAura.isEnabled() && killAura.getCurrentTarget() != null && killAura.getCurrentTarget().isAlive()) {
            return killAura.getCurrentTarget();
        }

        // 3. LegitAuraModule
        LegitAuraModule legitAura = ModuleManager.getInstance().getModule(LegitAuraModule.class);
        if (legitAura != null && legitAura.isEnabled()) {
            LivingEntity legitTarget = findLegitAuraCandidate();
            if (legitTarget != null) {
                return legitTarget;
            }
        }

        // 4. Direct targetedEntity / crosshair entity
        if (mc.crosshairPickEntity instanceof LivingEntity living && living.isAlive() && living != mc.player) {
            return living;
        }
        HitResult hit = mc.hitResult;
        if (hit instanceof EntityHitResult entityHit && entityHit.getEntity() instanceof LivingEntity living
                && living.isAlive() && living != mc.player) {
            return living;
        }

        // 5. Last attacked target within 12 blocks for 4 seconds after hit
        if (lastAttackedEntity != null && lastAttackedEntity.isAlive() && lastAttackedEntity != mc.player) {
            long elapsed = System.currentTimeMillis() - lastAttackTime;
            if (elapsed <= 4000L && mc.player.distanceTo(lastAttackedEntity) <= 12.0f) {
                return lastAttackedEntity;
            }
        }

        // 6. Preview on self when ChatScreen or ClickGuiScreen is open
        if (mc.gui.screen() instanceof ChatScreen || mc.gui.screen() instanceof ClickGuiScreen) {
            return mc.player;
        }

        return null;
    }

    private LivingEntity findLegitAuraCandidate() {
        LivingEntity best = null;
        double bestDist = 4.0;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof LivingEntity living) || e == mc.player || !e.isAlive()) continue;
            if (!(e instanceof Player)) continue;
            if (FriendManager.getInstance().isFriend(e)) continue;

            double dist = mc.player.distanceTo(e);
            if (dist <= bestDist) {
                Vec3 diff = e.position().subtract(mc.player.position());
                double angle = Math.toDegrees(Math.atan2(diff.z, diff.x)) - 90.0;
                double deltaYaw = Mth.wrapDegrees(angle - mc.player.getYRot());
                if (Math.abs(deltaYaw) <= 45.0) {
                    best = living;
                    bestDist = dist;
                }
            }
        }
        return best;
    }

    public LivingEntity getCurrentTarget() {
        return currentTarget != null ? currentTarget : lastTarget;
    }

    public ModeSetting getStyle() {
        return style;
    }

    public BooleanSetting getParticles() {
        return particles;
    }

    public BooleanSetting getShowEquipment() {
        return showEquipment;
    }
}
