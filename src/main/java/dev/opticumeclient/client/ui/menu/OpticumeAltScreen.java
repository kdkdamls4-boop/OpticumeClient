package dev.opticumeclient.client.ui.menu;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.InputConstants;
import dev.opticumeclient.client.command.CommandFix;
import dev.opticumeclient.client.gui.ClickGuiScreen;
import dev.opticumeclient.client.render.ColorUtils;
import dev.opticumeclient.client.render.KawaseBlurRenderer;
import dev.opticumeclient.client.render.Render2DHelper;
import dev.opticumeclient.client.render.TextureAssets;
import dev.opticumeclient.client.render.ThemeManager;
import dev.opticumeclient.client.services.auth.BanTrackerStore;
import dev.opticumeclient.client.services.auth.MicrosoftOAuthService;
import dev.opticumeclient.client.services.network.ProxyManager;
import dev.opticumeclient.mixin.MinecraftAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Working offline account switcher styled cleanly in the Desire glass aesthetic.
 * Persists saved nicknames in {@link MenuConfigStore} and switches {@code Minecraft.user}.
 */
public class OpticumeAltScreen extends Screen {
    private static final String[] NAME_PREFIXES = {"Opti", "Nova", "Aura", "Shadow", "Cryo", "Sky", "Velo", "Blade", "Frost", "Kuro"};
    private static final String[] NAME_SUFFIXES = {"PvP", "Ray", "Craft", "Fox", "Z", "MC", "Vibe", "Storm", "Byte", "Core"};
    private record CachedMsaToken(String token, long expiresAtMs) {
    }

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final Random RANDOM = new Random();
    private static final AtomicLong AUTH_EPOCH = new AtomicLong(0L);
    private static final AtomicLong OP_SEQ = new AtomicLong(0L);
    private static final Map<String, CachedMsaToken> IN_MEMORY_MSA_TOKENS = new ConcurrentHashMap<>();
    private static final Map<String, Long> DELETED_MSA_SEQ = new ConcurrentHashMap<>();
    private static volatile String activeFlowUuidKey = "";

    private static final class AltEntry {
        final String name;
        boolean favorite;
        final String date;
        boolean msa;
        String uuidStr;
        String token;
        float hover;

        AltEntry(String name, boolean favorite, String date) {
            this(name, favorite, date, false, "", "");
        }

        AltEntry(String name, boolean favorite, String date, boolean msa, String uuidStr, String token) {
            this.name = name;
            this.favorite = favorite;
            this.date = date == null || date.isBlank() ? LocalDate.now().format(DATE_FMT) : date;
            this.msa = msa;
            this.uuidStr = uuidStr == null ? "" : uuidStr;
            this.token = token == null ? "" : token;
        }
    }

    private final Screen parent;
    private final List<AltEntry> entries = new ArrayList<>();
    private String inputName = "";
    private boolean inputFocused = false;
    private String statusMessage = "";
    private long statusTime = 0L;
    private float scroll = 0.0f;
    private float targetScroll = 0.0f;
    private float maxScroll = 0.0f;
    private float openProgress = 0.0f;
    private float openAgeSec = 0.0f;
    private long lastNanos = System.nanoTime();
    private float smoothNormX = 0.0f;
    private float smoothNormY = 0.0f;

    public OpticumeAltScreen(Screen parent) {
        super(Component.literal("AltManager"));
        this.parent = parent;
        loadEntries();
    }

    @Override
    protected void init() {
        super.init();
        loadEntries();
        if (minecraft != null && minecraft.gameRenderer != null) {
            minecraft.gameRenderer.panorama().startSpin();
        }
        lastNanos = System.nanoTime();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        // Rendered inside extractRenderState for full panorama/wallpaper parity
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        TextureAssets.ensureInitialized();
        long now = System.nanoTime();
        float dt = Math.min(0.05f, Math.max(0.0001f, (now - lastNanos) / 1.0e9f));
        lastNanos = now;

        openProgress = Math.min(1.0f, openProgress + dt * 5.0f);
        openAgeSec += dt;
        float reveal = openProgress * openProgress * (3.0f - 2.0f * openProgress);
        scroll += (targetScroll - scroll) * (1.0f - (float) Math.exp(-16.0f * dt));

        if (minecraft != null) {
            if (inputFocused) minecraft.textInputManager().startTextInput(this);
            else minecraft.textInputManager().stopTextInput(this);
        }

        float targetNormX = (mouseX / Math.max(1.0f, (float) width) - 0.5f) * 2.0f;
        float targetNormY = (mouseY / Math.max(1.0f, (float) height) - 0.5f) * 2.0f;
        float parFactor = 1.0f - (float) Math.exp(-dt * 12.0f);
        smoothNormX += (targetNormX - smoothNormX) * parFactor;
        smoothNormY += (targetNormY - smoothNormY) * parFactor;

        // Stratum 0: Fullscreen MenuBackdrop
        MenuBackdrop.render(context, () -> super.extractPanorama(context, delta),
                MenuWallpapers.selected(), width, height, smoothNormX, smoothNormY, 1.0f, 1.05f);
        // Stratum 1: Capture & light Kawase-blur Stratum 0 before drawing luminous Liquid Glass UI
        context.nextStratum();
        context.blurBeforeThisStratum();
        KawaseBlurRenderer.requestMenuBlur(1, 0.75f);

        // Panel geometry
        float panelW = Math.min(390.0f, width - 32.0f);
        float panelH = Math.min(310.0f, height - 32.0f);
        float panelX = (width - panelW) * 0.5f;
        float panelY = (height - panelH) * 0.5f + (1.0f - reveal) * 12.0f;

        Render2DHelper.drawGlow(context, panelX, panelY + 3.0f, panelW, panelH, 12.0f,
                ColorUtils.withAlpha(0xFF000000, Math.round(52 * reveal)));
        Render2DHelper.drawBlurredRoundedRect(context, panelX, panelY, panelW, panelH, 14.0f,
                0x100A0E18, reveal);
        Render2DHelper.drawRoundedRectFloat(context, panelX, panelY, panelW, panelH, 14.0f,
                ColorUtils.withAlpha(0xFFFFFFFF, Math.round(16 * reveal)));
        Render2DHelper.drawRect(context, panelX + 16.0f, panelY + 1.0f, panelW - 32.0f, 0.8f,
                ColorUtils.withAlpha(0xFFFFFFFF, Math.round(34 * reveal)));
        Render2DHelper.drawRoundedOutlineFloat(context, panelX, panelY, panelW, panelH, 14.0f, 0.55f,
                ColorUtils.withAlpha(0xFFFFFFFF, Math.round(42 * reveal)));

        // Header in Welcome!'s signature EternalHeaven script font + glass pills
        float pad = 14.0f;
        OpticumeButtonStyle.drawScriptTitle(context, "AltManager", panelX + pad, panelY + 8.5f, 21.5f, reveal, openAgeSec, true);

        float backW = 54.0f;
        float backH = 20.0f;
        float backX = panelX + panelW - pad - backW;
        float backY = panelY + 11.0f;
        boolean backHov = isInside(mouseX, mouseY, backX, backY, backW, backH);
        OpticumeButtonStyle.renderGlassButton(context, "alt_back", backX, backY, backW, backH,
                true, false, backHov, 0, reveal);
        OpticumeButtonStyle.drawUiCenteredText(context, "Back", backX + backW * 0.5f, backY + 6.5f, 7.2f,
                ColorUtils.withAlpha(0xFFFFFFFF, Math.round(240 * reveal)), true);

        float proxyW = 56.0f;
        float proxyX = backX - proxyW - 6.0f;
        boolean proxyOn = ProxyManager.getInstance().isEnabled();
        OpticumeButtonStyle.renderGlassButton(context, "alt_proxy", proxyX, backY, proxyW, backH,
                true, proxyOn, isInside(mouseX, mouseY, proxyX, backY, proxyW, backH), 0xFF4ADE80, reveal);
        OpticumeButtonStyle.drawUiCenteredText(context, proxyOn ? "Proxy*" : "Proxy", proxyX + proxyW * 0.5f, backY + 6.5f, 7.2f,
                ColorUtils.withAlpha(proxyOn ? 0xFF86EFAC : 0xFFFFFFFF, Math.round(240 * reveal)), true);

        float msW = 66.0f;
        float msX = proxyX - msW - 6.0f;
        boolean msBusy = MicrosoftOAuthService.getInstance().isRunning();
        OpticumeButtonStyle.renderGlassButton(context, "alt_ms", msX, backY, msW, backH,
                true, msBusy, isInside(mouseX, mouseY, msX, backY, msW, backH), 0xFF38BDF8, reveal);
        OpticumeButtonStyle.drawUiCenteredText(context, msBusy ? "Auth..." : "MS Login", msX + msW * 0.5f, backY + 6.5f, 7.2f,
                ColorUtils.withAlpha(msBusy ? 0xFFFACC15 : 0xFFBAE6FD, Math.round(245 * reveal)), true);

        // Active account card (MainMenu frosted glass)
        String currentName = (minecraft != null && minecraft.getUser() != null) ? minecraft.getUser().getName() : "Player";
        UUID currentUuid = UUIDUtil.createOfflinePlayerUUID(currentName);
        float cardY = panelY + 38.0f;
        float cardH = 38.0f;
        float innerW = panelW - pad * 2.0f;
        int accent = ThemeManager.getInstance().getAccentColor(0);

        Render2DHelper.drawRoundedRectFloat(context, panelX + pad, cardY, innerW, cardH, 10.0f,
                ColorUtils.withAlpha(0xFFFFFFFF, Math.round(20 * reveal)));
        Render2DHelper.drawRect(context, panelX + pad + 10.0f, cardY + 0.8f, innerW - 20.0f, 0.7f,
                ColorUtils.withAlpha(0xFFFFFFFF, Math.round(32 * reveal)));
        Render2DHelper.drawRoundedOutlineFloat(context, panelX + pad, cardY, innerW, cardH, 10.0f, 0.65f,
                ColorUtils.withAlpha(accent, Math.round(130 * reveal)));

        Render2DHelper.drawPlayerHeadByUuid(context, currentUuid, Math.round(panelX + pad + 7), Math.round(cardY + 7), 24, reveal);
        OpticumeButtonStyle.drawUiText(context, currentName, panelX + pad + 39.0f, cardY + 9.0f, 8.2f,
                ColorUtils.withAlpha(0xFFFFFFFF, Math.round(248 * reveal)), true);
        OpticumeButtonStyle.drawUiText(context, "Active Session", panelX + pad + 39.0f, cardY + 21.5f, 6.4f,
                ColorUtils.withAlpha(0xFFE2E8F0, Math.round(210 * reveal)), true);

        // Input row: [ Nickname input ] [ Login ] [ Random ]
        float rowY = cardY + cardH + 10.0f;
        float rowH = 24.0f;
        float rndW = 58.0f;
        float loginW = 54.0f;
        float gap = 6.0f;
        float inputW = innerW - rndW - loginW - gap * 2.0f;

        boolean inputHov = isInside(mouseX, mouseY, panelX + pad, rowY, inputW, rowH);
        int inputBg = ColorUtils.withAlpha(0xFFFFFFFF, Math.round((inputFocused ? 28 : (inputHov ? 22 : 16)) * reveal));
        int inputBorder = inputFocused
                ? ColorUtils.withAlpha(accent, Math.round(165 * reveal))
                : ColorUtils.withAlpha(0xFFFFFFFF, Math.round((inputHov ? 52 : 28) * reveal));
        Render2DHelper.drawRoundedRectFloat(context, panelX + pad, rowY, inputW, rowH, 8.0f, inputBg);
        Render2DHelper.drawRoundedOutlineFloat(context, panelX + pad, rowY, inputW, rowH, 8.0f, 0.55f, inputBorder);

        String displayInput = inputName.isEmpty() && !inputFocused ? "Enter nickname (3-16 chars)..." : inputName;
        String caret = inputFocused && (System.currentTimeMillis() / 500L) % 2L == 0 ? "|" : "";
        int inputTextColor = inputName.isEmpty() && !inputFocused
                ? ColorUtils.withAlpha(0xFFE2E8F0, Math.round(175 * reveal))
                : ColorUtils.withAlpha(0xFFFFFFFF, Math.round(248 * reveal));
        OpticumeButtonStyle.drawUiText(context, displayInput + caret, panelX + pad + 8.0f, rowY + 8.2f, 7.2f, inputTextColor, true);

        float loginX = panelX + pad + inputW + gap;
        boolean loginHov = isInside(mouseX, mouseY, loginX, rowY, loginW, rowH);
        OpticumeButtonStyle.renderGlassButton(context, "alt_login", loginX, rowY, loginW, rowH,
                !inputName.isBlank(), false, loginHov, accent, reveal);
        OpticumeButtonStyle.drawUiCenteredText(context, "Login", loginX + loginW * 0.5f, rowY + 8.2f, 7.2f,
                ColorUtils.withAlpha(0xFFFFFFFF, Math.round((inputName.isBlank() ? 155 : 245) * reveal)), true);

        float rndX = loginX + loginW + gap;
        boolean rndHov = isInside(mouseX, mouseY, rndX, rowY, rndW, rowH);
        OpticumeButtonStyle.renderGlassButton(context, "alt_random", rndX, rowY, rndW, rowH,
                true, false, rndHov, accent, reveal);
        OpticumeButtonStyle.drawUiCenteredText(context, "Random", rndX + rndW * 0.5f, rowY + 8.2f, 7.2f,
                ColorUtils.withAlpha(0xFFFFFFFF, Math.round(240 * reveal)), true);

        // Saved accounts list
        String devCode = MicrosoftOAuthService.getInstance().getActiveUserCode();
        String devUrl = MicrosoftOAuthService.getInstance().getActiveVerifyUrl();
        boolean hasDeviceBanner = !devCode.isEmpty() && !devUrl.isEmpty();
        float bannerH = 30.0f;
        float listY = rowY + rowH + 10.0f;
        float listH = (panelY + panelH - pad - 14.0f - (hasDeviceBanner ? bannerH + 4.0f : 0.0f)) - listY;
        float itemH = 28.0f;
        float itemGap = 5.0f;
        float totalListH = Math.max(0.0f, entries.size() * (itemH + itemGap) - itemGap);
        maxScroll = Math.max(0.0f, totalListH - listH);
        targetScroll = Mth.clamp(targetScroll, 0.0f, maxScroll);
        scroll = Mth.clamp(scroll, 0.0f, maxScroll);

        context.enableScissor(Math.round(panelX + pad), Math.round(listY), Math.round(panelX + panelW - pad), Math.round(listY + listH));
        if (entries.isEmpty()) {
            OpticumeButtonStyle.drawUiCenteredText(context, "No saved alts yet — type a name and click Login",
                    panelX + panelW * 0.5f, listY + listH * 0.4f, 7.2f,
                    ColorUtils.withAlpha(0xFFE2E8F0, Math.round(185 * reveal)), true);
        } else {
            for (int i = 0; i < entries.size(); i++) {
                AltEntry entry = entries.get(i);
                float iy = listY + i * (itemH + itemGap) - scroll;
                if (iy + itemH < listY || iy > listY + listH) continue;

                boolean activeAcc = entry.name.equalsIgnoreCase(currentName);
                boolean rowHov = isInside(mouseX, mouseY, panelX + pad, iy, innerW, itemH);
                entry.hover += ((rowHov ? 1.0f : 0.0f) - entry.hover) * (1.0f - (float) Math.exp(-14.0f * dt));

                int rowBg = activeAcc
                        ? ColorUtils.withAlpha(accent, Math.round(44 * reveal))
                        : ColorUtils.withAlpha(0xFFFFFFFF, Math.round(Mth.lerp(entry.hover, 14.0f, 30.0f) * reveal));
                int rowOutline = activeAcc
                        ? ColorUtils.withAlpha(accent, Math.round(150 * reveal))
                        : ColorUtils.withAlpha(0xFFFFFFFF, Math.round(Mth.lerp(entry.hover, 22.0f, 52.0f) * reveal));

                Render2DHelper.drawRoundedRectFloat(context, panelX + pad, iy, innerW, itemH, 9.0f, rowBg);
                Render2DHelper.drawRoundedOutlineFloat(context, panelX + pad, iy, innerW, itemH, 9.0f, 0.55f, rowOutline);

                UUID accUuid = UUIDUtil.createOfflinePlayerUUID(entry.name);
                Render2DHelper.drawPlayerHeadByUuid(context, accUuid, Math.round(panelX + pad + 6), Math.round(iy + 5), 18, reveal);
                OpticumeButtonStyle.drawUiText(context, entry.name, panelX + pad + 30.0f, iy + 5.8f, 7.6f,
                        ColorUtils.withAlpha(0xFFFFFFFF, Math.round(248 * reveal)), true);
                if (entry.msa) {
                    float nw = OpticumeButtonStyle.getUiTextWidth(entry.name, 7.6f);
                    OpticumeButtonStyle.drawUiText(context, "[MSA]", panelX + pad + 34.0f + nw, iy + 6.4f, 6.4f,
                            ColorUtils.withAlpha(0xFF7DD3FC, Math.round(240 * reveal)), true);
                }

                BanTrackerStore.BanEntry ban = BanTrackerStore.getInstance().getBan(entry.name);
                String subText = (ban != null)
                        ? ("BANNED" + (!ban.server().isBlank() ? " · " + ban.server() : ""))
                        : ((activeAcc ? "Active · " : "") + entry.date);
                OpticumeButtonStyle.drawUiText(context, subText,
                        panelX + pad + 30.0f, iy + 16.5f, 6.0f,
                        ColorUtils.withAlpha(ban != null ? 0xFFFCA5A5 : 0xFFE2E8F0, Math.round((ban != null ? 235 : 195) * reveal)), true);

                // Ban toggle, Favorite star & Delete buttons
                float delX = panelX + pad + innerW - 20.0f;
                float starX = delX - 20.0f;
                float banX = starX - 26.0f;
                float btnY = iy + 6.0f;

                boolean banHov = isInside(mouseX, mouseY, banX, btnY, 24.0f, 16.0f);
                Render2DHelper.drawRoundedRectFloat(context, banX, btnY + 1.0f, 24.0f, 14.0f, 4.5f,
                        ColorUtils.withAlpha(ban != null ? 0xFFEF4444 : 0xFFFFFFFF, Math.round((ban != null ? 78 : (banHov ? 36 : 16)) * reveal)));
                OpticumeButtonStyle.drawUiCenteredText(context, "BAN", banX + 12.0f, btnY + 4.8f, 5.8f,
                        ColorUtils.withAlpha(ban != null ? 0xFFFEE2E2 : 0xFFE2E8F0, Math.round(235 * reveal)), true);

                int starTint = entry.favorite
                        ? ColorUtils.withAlpha(0xFFFACC15, Math.round(245 * reveal))
                        : ColorUtils.withAlpha(0xFFFFFFFF, Math.round((rowHov ? 165 : 85) * reveal));
                Render2DHelper.drawTexture(context, entry.favorite ? TextureAssets.ICON_STAR_FILLED : TextureAssets.ICON_FAVORITES,
                        starX + 2.0f, btnY + 2.0f, 12.0f, 12.0f, starTint);

                // Delete button
                boolean delHov = isInside(mouseX, mouseY, delX, btnY, 16.0f, 16.0f);
                OpticumeButtonStyle.drawUiCenteredText(context, "×", delX + 8.0f, btnY + 3.8f, 8.2f,
                        ColorUtils.withAlpha(delHov ? 0xFFF87171 : 0xFFE2E8F0, Math.round((delHov ? 255 : 175) * reveal)), true);
            }
        }
        context.disableScissor();

        if (hasDeviceBanner) {
            float bannerY = listY + listH + 4.0f;
            boolean banHov = isInside(mouseX, mouseY, panelX + pad, bannerY, innerW, bannerH);
            Render2DHelper.drawRoundedRectFloat(context, panelX + pad, bannerY, innerW, bannerH, 8.0f,
                    ColorUtils.withAlpha(accent, Math.round((banHov ? 44 : 28) * reveal)));
            Render2DHelper.drawRoundedOutlineFloat(context, panelX + pad, bannerY, innerW, bannerH, 8.0f, 0.6f,
                    ColorUtils.withAlpha(accent, Math.round(150 * reveal)));
            OpticumeButtonStyle.drawUiText(context, "MS Code: " + devCode + "  (click to copy)",
                    panelX + pad + 8.0f, bannerY + 5.0f, 6.8f,
                    ColorUtils.withAlpha(0xFFFFFFFF, Math.round(248 * reveal)), true);
            OpticumeButtonStyle.drawUiText(context, devUrl,
                    panelX + pad + 8.0f, bannerY + 16.5f, 6.2f,
                    ColorUtils.withAlpha(0xFF93C5FD, Math.round(235 * reveal)), true);
        }

        // Status toast footer
        long statusTtl = MicrosoftOAuthService.getInstance().isRunning() ? 60000L : 4500L;
        if (!statusMessage.isEmpty() && System.currentTimeMillis() - statusTime < statusTtl) {
            OpticumeButtonStyle.drawUiCenteredText(context, Render2DHelper.trimToWidth(statusMessage, 1.0f, innerW),
                    panelX + panelW * 0.5f, panelY + panelH - 13.5f, 6.8f,
                    ColorUtils.withAlpha(0xFF86EFAC, Math.round(240 * reveal)), true);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        int btn = ClickGuiScreen.normalizeMouseButton(click.button());
        if (btn != 0) return super.mouseClicked(click, doubled);
        double mx = click.x();
        double my = click.y();

        float panelW = Math.min(390.0f, width - 32.0f);
        float panelH = Math.min(310.0f, height - 32.0f);
        float panelX = (width - panelW) * 0.5f;
        float panelY = (height - panelH) * 0.5f;
        float pad = 14.0f;
        float innerW = panelW - pad * 2.0f;

        // Back, Proxy & MS Login buttons
        float backW = 54.0f;
        float backH = 20.0f;
        float backX = panelX + panelW - pad - backW;
        float backY = panelY + 11.0f;
        if (isInside(mx, my, backX, backY, backW, backH)) {
            playClick();
            onClose();
            return true;
        }
        float proxyW = 56.0f;
        float proxyX = backX - proxyW - 6.0f;
        if (isInside(mx, my, proxyX, backY, proxyW, backH)) {
            playClick();
            if (minecraft != null && minecraft.gui != null) {
                minecraft.gui.setScreen(new ProxyScreen(this));
            }
            return true;
        }
        float msW = 66.0f;
        float msX = proxyX - msW - 6.0f;
        if (isInside(mx, my, msX, backY, msW, backH)) {
            playClick();
            startMsaAuthFlow(null, null);
            return true;
        }

        // Input row
        float rowY = panelY + 38.0f + 38.0f + 10.0f;
        float rowH = 24.0f;
        float rndW = 58.0f;
        float loginW = 54.0f;
        float gap = 6.0f;
        float inputW = innerW - rndW - loginW - gap * 2.0f;

        if (isInside(mx, my, panelX + pad, rowY, inputW, rowH)) {
            inputFocused = true;
            return true;
        }
        inputFocused = false;

        float loginX = panelX + pad + inputW + gap;
        if (isInside(mx, my, loginX, rowY, loginW, rowH)) {
            if (!inputName.isBlank()) {
                playClick();
                loginAndSave(inputName.trim());
                inputName = "";
            }
            return true;
        }

        float rndX = loginX + loginW + gap;
        if (isInside(mx, my, rndX, rowY, rndW, rowH)) {
            playClick();
            String rnd = generateRandomName();
            loginAndSave(rnd);
            return true;
        }

        // List rows
        String devCode = MicrosoftOAuthService.getInstance().getActiveUserCode();
        String devUrl = MicrosoftOAuthService.getInstance().getActiveVerifyUrl();
        boolean hasDeviceBanner = !devCode.isEmpty() && !devUrl.isEmpty();
        float bannerH = 30.0f;
        float listY = rowY + rowH + 10.0f;
        float listH = (panelY + panelH - pad - 14.0f - (hasDeviceBanner ? bannerH + 4.0f : 0.0f)) - listY;
        if (hasDeviceBanner) {
            float bannerY = listY + listH + 4.0f;
            if (isInside(mx, my, panelX + pad, bannerY, innerW, bannerH)) {
                playClick();
                Minecraft mc = Minecraft.getInstance();
                if (mc != null && mc.keyboardHandler != null) {
                    mc.keyboardHandler.setClipboard(devCode);
                }
                status("Copied code " + devCode + " · open " + devUrl);
                return true;
            }
        }
        float itemH = 28.0f;
        float itemGap = 5.0f;
        if (isInside(mx, my, panelX + pad, listY, innerW, listH)) {
            for (int i = 0; i < entries.size(); i++) {
                float iy = listY + i * (itemH + itemGap) - scroll;
                if (my >= iy && my <= iy + itemH) {
                    float delX = panelX + pad + innerW - 20.0f;
                    float starX = delX - 20.0f;
                    float banX = starX - 26.0f;
                    AltEntry entry = entries.get(i);
                    if (mx >= delX && mx <= delX + 18.0f) {
                        playClick();
                        entries.remove(i);
                        saveEntries();
                        if (entry.msa) {
                            String delUuidKey = entry.uuidStr == null ? "" : entry.uuidStr.toLowerCase(Locale.ROOT);
                            if (!delUuidKey.isBlank()) {
                                DELETED_MSA_SEQ.put(delUuidKey, OP_SEQ.incrementAndGet());
                                IN_MEMORY_MSA_TOKENS.remove(delUuidKey);
                                if (delUuidKey.equalsIgnoreCase(activeFlowUuidKey)) {
                                    activeFlowUuidKey = "";
                                    MicrosoftOAuthService.getInstance().cancelActiveFlow();
                                }
                            }
                        }
                        status("Removed " + entry.name);
                        return true;
                    }
                    if (mx >= starX && mx <= starX + 18.0f) {
                        playClick();
                        entry.favorite = !entry.favorite;
                        sortEntries();
                        saveEntries();
                        return true;
                    }
                    if (mx >= banX && mx <= banX + 24.0f) {
                        playClick();
                        BanTrackerStore.getInstance().toggleManualBan(entry.name);
                        return true;
                    }
                    playClick();
                    if (entry.msa) {
                        String uuidKey = entry.uuidStr == null ? "" : entry.uuidStr.toLowerCase(Locale.ROOT);
                        final long opSeq = OP_SEQ.incrementAndGet();
                        CachedMsaToken cached = uuidKey.isBlank() ? null : IN_MEMORY_MSA_TOKENS.get(uuidKey);
                        if (cached != null && !cached.token().isBlank() && System.currentTimeMillis() < cached.expiresAtMs()) {
                            final long verifyEpoch = AUTH_EPOCH.incrementAndGet();
                            activeFlowUuidKey = "";
                            MicrosoftOAuthService.getInstance().cancelActiveFlow();
                            final String tok = cached.token();
                            final String expectedUuidStr = entry.uuidStr;
                            final String expectedNameStr = entry.name;
                            status("Verifying Microsoft session for " + expectedNameStr + "...");
                            MicrosoftOAuthService.getInstance().verifyTokenAsync(tok, verifyRes -> {
                                Minecraft mc = Minecraft.getInstance();
                                if (mc == null) return;
                                mc.execute(() -> {
                                    Long delSeq = uuidKey.isBlank() ? null : DELETED_MSA_SEQ.get(uuidKey);
                                    if ((delSeq != null && delSeq > opSeq) || AUTH_EPOCH.get() != verifyEpoch) {
                                        return;
                                    }
                                    if (verifyRes == MicrosoftOAuthService.VerifyResult.UNAVAILABLE) {
                                        status("Microsoft verification unavailable for " + expectedNameStr + " — click again to retry");
                                        return;
                                    }
                                    if (verifyRes == MicrosoftOAuthService.VerifyResult.REJECTED) {
                                        IN_MEMORY_MSA_TOKENS.remove(uuidKey);
                                        entry.token = "";
                                        status("Microsoft session rejected for " + expectedNameStr + " — sign in via browser");
                                        startMsaAuthFlow(expectedUuidStr, expectedNameStr);
                                        return;
                                    }
                                    try {
                                        applyMsaSession(expectedNameStr, UUID.fromString(expectedUuidStr), tok);
                                    } catch (Throwable t) {
                                        IN_MEMORY_MSA_TOKENS.remove(uuidKey);
                                        entry.token = "";
                                        status("Invalid saved UUID for " + expectedNameStr + " — sign in via browser");
                                        startMsaAuthFlow(expectedUuidStr, expectedNameStr);
                                    }
                                });
                            });
                            return true;
                        }
                        if (!uuidKey.isBlank()) {
                            IN_MEMORY_MSA_TOKENS.remove(uuidKey);
                        }
                        entry.token = "";
                        status("Microsoft session expired for " + entry.name + " — sign in via browser");
                        startMsaAuthFlow(entry.uuidStr, entry.name);
                        return true;
                    }
                    applySession(entry.name);
                    return true;
                }
            }
        }

        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        targetScroll = Mth.clamp(targetScroll - (float) (verticalAmount * 20.0f), 0.0f, maxScroll);
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent input) {
        int key = input.key();
        if (inputFocused) {
            if (key == InputConstants.KEY_BACKSPACE) {
                if (!inputName.isEmpty()) {
                    inputName = inputName.substring(0, inputName.length() - 1);
                }
                return true;
            }
            if (key == InputConstants.KEY_RETURN) {
                if (!inputName.isBlank()) {
                    playClick();
                    loginAndSave(inputName.trim());
                    inputName = "";
                }
                inputFocused = false;
                return true;
            }
            if (key == InputConstants.KEY_ESCAPE) {
                inputFocused = false;
                return true;
            }
            // Ctrl+V paste support
            if (input.isPaste() && minecraft != null) {
                String clip = minecraft.keyboardHandler.getClipboard();
                if (clip != null) {
                    String cleaned = clip.replaceAll("[^a-zA-Z0-9_]", "");
                    inputName = (inputName + cleaned).substring(0, Math.min(16, (inputName + cleaned).length()));
                }
                return true;
            }
        }
        if (key == InputConstants.KEY_ESCAPE) {
            onClose();
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean charTyped(CharacterEvent input) {
        if (inputFocused && input.isAllowedChatCharacter()) {
            String ch = input.codepointAsString();
            if (!ch.matches("[a-zA-Z0-9_]")) {
                ch = CommandFix.translateRuToEn(ch);
            }
            if (ch != null && ch.matches("[a-zA-Z0-9_]") && inputName.length() < 16) {
                inputName += ch;
            }
            return true;
        }
        return super.charTyped(input);
    }

    @Override
    public void removed() {
        inputFocused = false;
        if (minecraft != null) {
            minecraft.textInputManager().stopTextInput(this);
        }
        super.removed();
    }

    @Override
    public void onClose() {
        if (minecraft != null && minecraft.gui != null) {
            minecraft.gui.setScreen(parent != null ? parent : new OpticumeMainMenuScreen());
        }
    }

    private void loginAndSave(String rawName) {
        String name = rawName.replaceAll("[^a-zA-Z0-9_]", "");
        if (name.length() < 1) return;
        if (name.length() > 16) name = name.substring(0, 16);

        boolean exists = false;
        for (AltEntry e : entries) {
            if (!e.msa && e.name.equalsIgnoreCase(name)) {
                exists = true;
                break;
            }
        }
        if (!exists) {
            entries.add(0, new AltEntry(name, false, LocalDate.now().format(DATE_FMT)));
            sortEntries();
            saveEntries();
        }
        applySession(name);
    }

    private void applySession(String name) {
        AUTH_EPOCH.incrementAndGet();
        activeFlowUuidKey = "";
        MicrosoftOAuthService.getInstance().cancelActiveFlow();
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        UUID uuid = UUIDUtil.createOfflinePlayerUUID(name);
        User newUser = new User(name, uuid, "0", Optional.empty(), Optional.empty());
        ((MinecraftAccessor) (Object) mc).opticume$setUser(newUser);
        MenuConfigStore.save(obj -> obj.addProperty("selectedAccount", name));
        status("Signed in as " + name);
    }

    private void startMsaAuthFlow(String expectedUuid, String expectedName) {
        final long expectedEpoch = AUTH_EPOCH.incrementAndGet();
        final long opSeq = OP_SEQ.incrementAndGet();
        final String expectedUuidKey = expectedUuid == null ? "" : expectedUuid.toLowerCase(Locale.ROOT);
        activeFlowUuidKey = expectedUuidKey;
        MicrosoftOAuthService.getInstance().startDeviceCodeLogin(
                msg -> Minecraft.getInstance().execute(() -> {
                    if (AUTH_EPOCH.get() == expectedEpoch) {
                        status(msg);
                    }
                }),
                acc -> Minecraft.getInstance().execute(() -> {
                    if (AUTH_EPOCH.get() != expectedEpoch) {
                        return;
                    }
                    if (!expectedUuidKey.isBlank()) {
                        Long expDel = DELETED_MSA_SEQ.get(expectedUuidKey);
                        if (expDel != null && expDel > opSeq) {
                            return;
                        }
                    }
                    String uuidKey = acc.uuid().toString().toLowerCase(Locale.ROOT);
                    Long resDel = DELETED_MSA_SEQ.get(uuidKey);
                    if (resDel != null && resDel > opSeq) {
                        return;
                    }
                    if (expectedUuid != null && !expectedUuid.isBlank()
                            && !uuidKey.equalsIgnoreCase(expectedUuid)) {
                        String who = (expectedName != null && !expectedName.isBlank()) ? expectedName : expectedUuid;
                        status("Signed into wrong MSA (" + acc.username() + "); expected " + who);
                        return;
                    }
                    DELETED_MSA_SEQ.remove(uuidKey);
                    if (acc.accessToken() != null && !acc.accessToken().isEmpty()) {
                        IN_MEMORY_MSA_TOKENS.put(uuidKey, new CachedMsaToken(acc.accessToken(), acc.expiresAtMs()));
                    }
                    AltEntry msaEntry = new AltEntry(acc.username(), true, LocalDate.now().format(DATE_FMT),
                            true, acc.uuid().toString(), acc.accessToken());
                    List<AltEntry> latest = loadEntriesFromStore();
                    latest.removeIf(e -> e.msa && e.uuidStr != null && e.uuidStr.equalsIgnoreCase(uuidKey));
                    latest.add(0, msaEntry);
                    sortEntriesList(latest);
                    saveEntriesToStore(latest);
                    Minecraft mc = Minecraft.getInstance();
                    if (mc != null && mc.gui != null && mc.gui.screen() instanceof OpticumeAltScreen activeAlt) {
                        activeAlt.loadEntries();
                    }
                    applyMsaSession(acc.username(), acc.uuid(), acc.accessToken());
                }),
                err -> Minecraft.getInstance().execute(() -> {
                    if (AUTH_EPOCH.get() == expectedEpoch) {
                        status(err);
                    }
                })
        );
    }

    private void applyMsaSession(String name, UUID uuid, String accessToken) {
        AUTH_EPOCH.incrementAndGet();
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        User newUser = new User(name, uuid, accessToken, Optional.empty(), Optional.empty());
        ((MinecraftAccessor) (Object) mc).opticume$setUser(newUser);
        MenuConfigStore.save(obj -> obj.addProperty("selectedAccount", name));
        status("MSA Signed in as " + name);
    }

    private String generateRandomName() {
        String prefix = NAME_PREFIXES[RANDOM.nextInt(NAME_PREFIXES.length)];
        String suffix = NAME_SUFFIXES[RANDOM.nextInt(NAME_SUFFIXES.length)];
        int num = 10 + RANDOM.nextInt(90);
        String name = prefix + suffix + num;
        return name.length() > 16 ? name.substring(0, 16) : name;
    }

    private void status(String msg) {
        this.statusMessage = msg;
        this.statusTime = System.currentTimeMillis();
    }

    private void sortEntries() {
        sortEntriesList(entries);
    }

    private static void sortEntriesList(List<AltEntry> list) {
        list.sort((a, b) -> Boolean.compare(b.favorite, a.favorite));
    }

    private void loadEntries() {
        entries.clear();
        entries.addAll(loadEntriesFromStore());
    }

    private static List<AltEntry> loadEntriesFromStore() {
        List<AltEntry> out = new ArrayList<>();
        JsonArray arr = MenuConfigStore.getArray("alts");
        if (arr != null) {
            for (JsonElement el : arr) {
                if (!el.isJsonObject()) continue;
                JsonObject obj = el.getAsJsonObject();
                String n = obj.has("name") ? obj.get("name").getAsString() : "";
                if (!n.matches("^[a-zA-Z0-9_]{1,16}$")) continue;
                boolean fav = obj.has("favorite") && obj.get("favorite").getAsBoolean();
                String date = obj.has("date") ? obj.get("date").getAsString() : "";
                boolean msa = obj.has("msa") && obj.get("msa").getAsBoolean();
                String uuidStr = obj.has("uuid") ? obj.get("uuid").getAsString() : "";
                CachedMsaToken cached = (msa && !uuidStr.isBlank())
                        ? IN_MEMORY_MSA_TOKENS.get(uuidStr.toLowerCase(Locale.ROOT))
                        : null;
                String memToken = (cached != null && System.currentTimeMillis() < cached.expiresAtMs())
                        ? cached.token()
                        : "";
                out.add(new AltEntry(n, fav, date, msa, uuidStr, memToken));
            }
        }
        sortEntriesList(out);
        return out;
    }

    private void saveEntries() {
        saveEntriesToStore(entries);
    }

    private static void saveEntriesToStore(List<AltEntry> list) {
        JsonArray arr = new JsonArray();
        for (AltEntry e : list) {
            JsonObject obj = new JsonObject();
            obj.addProperty("name", e.name);
            obj.addProperty("favorite", e.favorite);
            obj.addProperty("date", e.date);
            if (e.msa) {
                obj.addProperty("msa", true);
                obj.addProperty("uuid", e.uuidStr);
            }
            arr.add(obj);
        }
        MenuConfigStore.save(root -> root.add("alts", arr));
    }

    private void playClick() {
        if (minecraft != null && minecraft.getSoundManager() != null) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }
    }

    private static boolean isInside(double mx, double my, float x, float y, float w, float h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }
}
