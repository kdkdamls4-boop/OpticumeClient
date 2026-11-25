package dev.opticumeclient.client.ui.menu;

import com.mojang.blaze3d.platform.InputConstants;
import dev.opticumeclient.client.command.CommandFix;
import dev.opticumeclient.client.gui.ClickGuiScreen;
import dev.opticumeclient.client.render.ColorUtils;
import dev.opticumeclient.client.render.KawaseBlurRenderer;
import dev.opticumeclient.client.render.Render2DHelper;
import dev.opticumeclient.client.render.TextureAssets;
import dev.opticumeclient.client.render.ThemeManager;
import dev.opticumeclient.client.services.network.ProxyManager;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.List;

/**
 * Desire-styled Proxy Manager screen allowing users to add, switch, and toggle
 * SOCKS4 / SOCKS5 / HTTP proxies for Minecraft and multi-session Bots.
 */
public class ProxyScreen extends Screen {
    private final Screen parent;

    private ProxyManager.ProxyType selectedType = ProxyManager.ProxyType.SOCKS5;
    private boolean useTls = false;
    private String hostPortInput = "";
    private String userInput = "";
    private String passInput = "";
    private int focusedField = 0; // 0 = host:port, 1 = user, 2 = pass
    private String statusMsg = "";
    private long statusAt = 0L;
    private float openProgress = 0.0f;
    private float openAgeSec = 0.0f;
    private long lastNanos = System.nanoTime();
    private float smoothNormX = 0.0f;
    private float smoothNormY = 0.0f;
    private float scroll = 0.0f;
    private float targetScroll = 0.0f;
    private float maxScroll = 0.0f;

    public ProxyScreen(Screen parent) {
        super(Component.literal("ProxyManager"));
        this.parent = parent;
        ProxyManager.getInstance().ensureLoaded();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        TextureAssets.ensureInitialized();
        long now = System.nanoTime();
        float dt = Math.min(0.05f, Math.max(0.0001f, (now - lastNanos) / 1.0e9f));
        lastNanos = now;
        openProgress = Math.min(1.0f, openProgress + dt * 5.0f);
        openAgeSec += dt;
        float reveal = currentReveal();

        if (minecraft != null) {
            if (focusedField >= 0) minecraft.textInputManager().startTextInput(this);
            else minecraft.textInputManager().stopTextInput(this);
        }

        float targetNormX = (mouseX / Math.max(1.0f, (float) width) - 0.5f) * 2.0f;
        float targetNormY = (mouseY / Math.max(1.0f, (float) height) - 0.5f) * 2.0f;
        float parFactor = 1.0f - (float) Math.exp(-dt * 12.0f);
        smoothNormX += (targetNormX - smoothNormX) * parFactor;
        smoothNormY += (targetNormY - smoothNormY) * parFactor;

        MenuBackdrop.render(context, () -> super.extractPanorama(context, delta),
                MenuWallpapers.selected(), width, height, smoothNormX, smoothNormY, 1.0f, 1.05f);
        context.nextStratum();
        context.blurBeforeThisStratum();
        KawaseBlurRenderer.requestMenuBlur(1, 0.75f);

        float panelW = Math.min(420.0f, width - 32.0f);
        float panelH = Math.min(320.0f, height - 32.0f);
        float panelX = (width - panelW) * 0.5f;
        float panelY = computePanelY(panelH, reveal);
        float pad = 14.0f;
        float innerW = panelW - pad * 2.0f;
        int accent = ThemeManager.getInstance().getAccentColor(0);

        Render2DHelper.drawGlow(context, panelX, panelY + 3.0f, panelW, panelH, 12.0f, ColorUtils.withAlpha(0xFF000000, Math.round(52 * reveal)));
        Render2DHelper.drawBlurredRoundedRect(context, panelX, panelY, panelW, panelH, 14.0f, 0x100A0E18, reveal);
        Render2DHelper.drawRoundedRectFloat(context, panelX, panelY, panelW, panelH, 14.0f, ColorUtils.withAlpha(0xFFFFFFFF, Math.round(16 * reveal)));
        Render2DHelper.drawRect(context, panelX + 16.0f, panelY + 1.0f, panelW - 32.0f, 0.8f, ColorUtils.withAlpha(0xFFFFFFFF, Math.round(34 * reveal)));
        Render2DHelper.drawRoundedOutlineFloat(context, panelX, panelY, panelW, panelH, 14.0f, 0.55f, ColorUtils.withAlpha(0xFFFFFFFF, Math.round(42 * reveal)));

        OpticumeButtonStyle.drawScriptTitle(context, "Proxy Manager", panelX + pad, panelY + 9.0f, 19.5f, reveal, openAgeSec, true);

        ProxyManager pm = ProxyManager.getInstance();
        boolean loadErr = pm.hasLoadFailure();
        boolean pmOn = pm.isEnabled();
        float toggleW = 86.0f;
        float backW = 54.0f;
        float btnH = 20.0f;
        float backX = panelX + panelW - pad - backW;
        float toggleX = backX - toggleW - 6.0f;

        boolean togHov = isInside(mouseX, mouseY, toggleX, panelY + 11.0f, toggleW, btnH);
        OpticumeButtonStyle.renderGlassButton(context, "proxy_tog", toggleX, panelY + 11.0f,
                toggleW, btnH, true, pmOn, togHov, loadErr ? 0xFFF59E0B : (pmOn ? 0xFF4ADE80 : 0xFFEF4444), reveal);
        String togLabel = loadErr ? "Reset File" : (pmOn ? "Proxy: ON" : "Proxy: OFF");
        int togTextCol = loadErr ? 0xFFFDE68A : (pmOn ? 0xFF86EFAC : 0xFFFCA5A5);
        OpticumeButtonStyle.drawUiCenteredText(context, togLabel, toggleX + toggleW * 0.5f, panelY + 17.2f, 7.2f,
                ColorUtils.withAlpha(togTextCol, Math.round(245 * reveal)), true);

        boolean backHov = isInside(mouseX, mouseY, backX, panelY + 11.0f, backW, btnH);
        OpticumeButtonStyle.renderGlassButton(context, "proxy_back", backX, panelY + 11.0f,
                backW, btnH, true, false, backHov, 0, reveal);
        OpticumeButtonStyle.drawUiCenteredText(context, "Back", backX + backW * 0.5f, panelY + 17.2f, 7.2f,
                ColorUtils.withAlpha(0xFFFFFFFF, Math.round(240 * reveal)), true);

        // Input bar: [Type] [host:port] [user] [pass] [Add]
        float row1Y = panelY + 42.0f;
        float typeW = 56.0f;
        float addW = 46.0f;
        float hostW = innerW - typeW - addW - 12.0f;

        OpticumeButtonStyle.renderGlassButton(context, "proxy_type", panelX + pad, row1Y,
                typeW, 22.0f, true, false, isInside(mouseX, mouseY, panelX + pad, row1Y, typeW, 22), accent, reveal);
        OpticumeButtonStyle.drawUiCenteredText(context, selectedType.name(), panelX + pad + typeW * 0.5f, row1Y + 7.5f, 7.2f,
                ColorUtils.withAlpha(accent, Math.round(248 * reveal)), true);

        drawInputBox(context, panelX + pad + typeW + 6.0f, row1Y, hostW, 22.0f, hostPortInput, "host:port (127.0.0.1:1080)", focusedField == 0, reveal, accent);

        float addX = panelX + pad + typeW + 6.0f + hostW + 6.0f;
        OpticumeButtonStyle.renderGlassButton(context, "proxy_add", addX, row1Y,
                addW, 22.0f, !hostPortInput.isBlank(), false, isInside(mouseX, mouseY, addX, row1Y, addW, 22), accent, reveal);
        OpticumeButtonStyle.drawUiCenteredText(context, "Add", addX + addW * 0.5f, row1Y + 7.5f, 7.2f,
                ColorUtils.withAlpha(0xFFFFFFFF, Math.round((hostPortInput.isBlank() ? 155 : 245) * reveal)), true);

        float row2Y = row1Y + 28.0f;
        float tlsW = 44.0f;
        float halfW = (innerW - tlsW - 12.0f) * 0.5f;
        drawInputBox(context, panelX + pad, row2Y, halfW, 20.0f, userInput, "Username (optional)", focusedField == 1, reveal, accent);
        drawInputBox(context, panelX + pad + halfW + 6.0f, row2Y, halfW, 20.0f,
                "*".repeat(passInput.length()), "Password (optional)", focusedField == 2, reveal, accent);
        float tlsX = panelX + pad + (halfW + 6.0f) * 2.0f;
        boolean tlsActive = useTls || !userInput.isBlank() || !passInput.isBlank();
        OpticumeButtonStyle.renderGlassButton(context, "proxy_tls", tlsX, row2Y,
                tlsW, 20.0f, true, tlsActive, isInside(mouseX, mouseY, tlsX, row2Y, tlsW, 20.0f),
                tlsActive ? 0xFF4ADE80 : accent, reveal);
        OpticumeButtonStyle.drawUiCenteredText(context, tlsActive ? "TLS" : "Plain", tlsX + tlsW * 0.5f, row2Y + 6.5f, 6.8f,
                ColorUtils.withAlpha(tlsActive ? 0xFF86EFAC : 0xFFE2E8F0, Math.round(240 * reveal)), true);

        float listY = row2Y + 28.0f;
        float listH = panelY + panelH - pad - 14.0f - listY;
        List<ProxyManager.ProxyEntry> list = pm.getProxies();
        ProxyManager.ProxyEntry active = pm.getActiveProxy();
        float itemH = 26.0f;
        float itemStep = itemH + 4.0f;
        float contentH = list.isEmpty() ? 0.0f : (list.size() * itemStep - 4.0f);
        maxScroll = Math.max(0.0f, contentH - listH);
        targetScroll = Mth.clamp(targetScroll, 0.0f, maxScroll);
        scroll += (targetScroll - scroll) * (1.0f - (float) Math.exp(-16.0f * dt));
        scroll = Mth.clamp(scroll, 0.0f, maxScroll);

        context.enableScissor(Math.round(panelX + pad), Math.round(listY), Math.round(panelX + panelW - pad), Math.round(listY + listH));
        if (list.isEmpty()) {
            OpticumeButtonStyle.drawUiCenteredText(context, "No saved proxies. Enter host:port and click Add.",
                    panelX + panelW * 0.5f, listY + listH * 0.4f, 7.2f,
                    ColorUtils.withAlpha(0xFFE2E8F0, Math.round(185 * reveal)), true);
        } else {
            for (int i = 0; i < list.size(); i++) {
                float iy = listY + i * itemStep - scroll;
                if (iy + itemH < listY || iy > listY + listH) continue;
                ProxyManager.ProxyEntry e = list.get(i);
                boolean isActive = e == active;
                boolean hov = isInside(mouseX, mouseY, panelX + pad, iy, innerW, itemH);
                int bg = isActive ? ColorUtils.withAlpha(accent, Math.round(44 * reveal))
                        : ColorUtils.withAlpha(0xFFFFFFFF, Math.round((hov ? 28 : 14) * reveal));
                Render2DHelper.drawRoundedRectFloat(context, panelX + pad, iy, innerW, itemH, 8.0f, bg);
                Render2DHelper.drawRoundedOutlineFloat(context, panelX + pad, iy, innerW, itemH, 8.0f, 0.55f,
                        ColorUtils.withAlpha(isActive ? accent : 0xFFFFFFFF, Math.round((isActive ? 155 : 26) * reveal)));
                OpticumeButtonStyle.drawUiText(context, e.label(), panelX + pad + 8.0f, iy + 9.2f, 7.2f,
                        ColorUtils.withAlpha(0xFFFFFFFF, Math.round(248 * reveal)), true);
                OpticumeButtonStyle.drawUiCenteredText(context, "×", panelX + pad + innerW - 12.0f, iy + 8.8f, 8.2f,
                        ColorUtils.withAlpha(0xFFF87171, Math.round(220 * reveal)), true);
            }
        }
        context.disableScissor();

        if (!statusMsg.isEmpty() && System.currentTimeMillis() - statusAt < 3000L) {
            OpticumeButtonStyle.drawUiCenteredText(context, statusMsg, panelX + panelW * 0.5f, panelY + panelH - 12.5f, 6.8f,
                    ColorUtils.withAlpha(0xFF86EFAC, Math.round(240 * reveal)), true);
        } else if (pm.hasLoadFailure()) {
            String errMsg = Render2DHelper.trimToWidth("Proxy config error (blocking connect): " + pm.getLoadError(), 0.72f, innerW);
            OpticumeButtonStyle.drawUiCenteredText(context, errMsg,
                    panelX + panelW * 0.5f, panelY + panelH - 12.5f, 6.2f,
                    ColorUtils.withAlpha(0xFFF87171, Math.round(235 * reveal)), true);
        } else if (pm.hasPlaintextMigrationFailure()) {
            OpticumeButtonStyle.drawUiCenteredText(context, "Plaintext password migration failed (check write permissions to proxies.json)",
                    panelX + panelW * 0.5f, panelY + panelH - 12.5f, 6.2f,
                    ColorUtils.withAlpha(0xFFFACC15, Math.round(235 * reveal)), true);
        } else if (pm.isActiveMissingSessionPassword() || pm.hasSessionOnlyPasswords() || (!ProxyManager.supportsProtectedSecretStorage()
                && active != null && !active.username().isBlank() && active.password().isEmpty())) {
            OpticumeButtonStyle.drawUiCenteredText(context, "Session-only password missing: re-enter & re-add endpoint to connect",
                    panelX + panelW * 0.5f, panelY + panelH - 12.5f, 6.2f,
                    ColorUtils.withAlpha(0xFFFACC15, Math.round(220 * reveal)), true);
        }
    }

    private void drawInputBox(GuiGraphicsExtractor context, float x, float y, float w, float h,
                              String val, String placeholder, boolean focused, float reveal, int accent) {
        Render2DHelper.drawRoundedRectFloat(context, x, y, w, h, 8.0f,
                ColorUtils.withAlpha(0xFFFFFFFF, Math.round((focused ? 28 : 16) * reveal)));
        Render2DHelper.drawRoundedOutlineFloat(context, x, y, w, h, 8.0f, 0.55f,
                ColorUtils.withAlpha(focused ? accent : 0xFFFFFFFF, Math.round((focused ? 165 : 28) * reveal)));
        String txt = val.isEmpty() && !focused ? placeholder : val + (focused && (System.currentTimeMillis() / 500L) % 2L == 0 ? "|" : "");
        int col = val.isEmpty() && !focused ? ColorUtils.withAlpha(0xFFE2E8F0, Math.round(175 * reveal)) : ColorUtils.withAlpha(0xFFFFFFFF, Math.round(248 * reveal));
        OpticumeButtonStyle.drawUiText(context, Render2DHelper.trimToWidth(txt, 0.85f, w - 12.0f), x + 7.0f, y + (h - 7.0f) * 0.5f, 7.0f, col, true);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        int btn = ClickGuiScreen.normalizeMouseButton(click.button());
        if (btn != 0) return super.mouseClicked(click, doubled);
        double mx = click.x();
        double my = click.y();

        float panelW = Math.min(420.0f, width - 32.0f);
        float panelH = Math.min(320.0f, height - 32.0f);
        float panelX = (width - panelW) * 0.5f;
        float panelY = computePanelY(panelH, currentReveal());
        float pad = 14.0f;
        float innerW = panelW - pad * 2.0f;

        float toggleW = 86.0f;
        float backW = 54.0f;
        float backX = panelX + panelW - pad - backW;
        float toggleX = backX - toggleW - 6.0f;

        if (isInside(mx, my, backX, panelY + 11.0f, backW, 20.0f)) {
            onClose();
            return true;
        }
        if (isInside(mx, my, toggleX, panelY + 11.0f, toggleW, 20.0f)) {
            ProxyManager pm = ProxyManager.getInstance();
            if (pm.hasLoadFailure()) {
                statusMsg = pm.resetAfterLoadFailure() ? "Reset broken proxies.json" : "Failed to save proxies.json";
            } else {
                boolean desired = !pm.isEnabled();
                if (!pm.setEnabled(desired)) {
                    statusMsg = (desired && pm.getActiveProxy() == null) ? "Add or select a proxy first" : "Failed to save proxies.json";
                } else {
                    statusMsg = pm.isEnabled() ? "Proxy enabled" : "Proxy disabled";
                }
            }
            statusAt = System.currentTimeMillis();
            return true;
        }

        float row1Y = panelY + 42.0f;
        float typeW = 56.0f;
        float addW = 46.0f;
        float hostW = innerW - typeW - addW - 12.0f;
        if (isInside(mx, my, panelX + pad, row1Y, typeW, 22.0f)) {
            ProxyManager.ProxyType[] vals = ProxyManager.ProxyType.values();
            selectedType = vals[(selectedType.ordinal() + 1) % vals.length];
            return true;
        }
        if (isInside(mx, my, panelX + pad + typeW + 6.0f, row1Y, hostW, 22.0f)) {
            focusedField = 0;
            return true;
        }
        float addX = panelX + pad + typeW + 6.0f + hostW + 6.0f;
        if (isInside(mx, my, addX, row1Y, addW, 22.0f)) {
            tryAddProxy();
            return true;
        }

        float row2Y = row1Y + 28.0f;
        float tlsW = 44.0f;
        float halfW = (innerW - tlsW - 12.0f) * 0.5f;
        if (isInside(mx, my, panelX + pad, row2Y, halfW, 20.0f)) {
            focusedField = 1;
            return true;
        }
        if (isInside(mx, my, panelX + pad + halfW + 6.0f, row2Y, halfW, 20.0f)) {
            focusedField = 2;
            return true;
        }
        float tlsX = panelX + pad + (halfW + 6.0f) * 2.0f;
        if (isInside(mx, my, tlsX, row2Y, tlsW, 20.0f)) {
            boolean hasCreds = !userInput.isBlank() || !passInput.isBlank();
            if (hasCreds) {
                useTls = true;
                statusMsg = "TLS is required when credentials are set";
                statusAt = System.currentTimeMillis();
            } else {
                useTls = !useTls;
            }
            return true;
        }

        float listY = row2Y + 28.0f;
        float listH = panelY + panelH - pad - 14.0f - listY;
        float itemH = 26.0f;
        float itemStep = itemH + 4.0f;
        if (isInside(mx, my, panelX + pad, listY, innerW, listH)) {
            List<ProxyManager.ProxyEntry> list = ProxyManager.getInstance().getProxies();
            for (int i = 0; i < list.size(); i++) {
                float iy = listY + i * itemStep - scroll;
                if (iy + itemH < listY || iy > listY + listH) continue;
                float clampedTop = Math.max(listY, iy);
                float clampedBot = Math.min(listY + listH, iy + itemH);
                if (my < clampedTop || my > clampedBot) continue;
                if (isInside(mx, my, panelX + pad + innerW - 22.0f, iy, 22.0f, itemH)) {
                    if (!ProxyManager.getInstance().removeProxy(i)) {
                        statusMsg = "Failed to save proxies.json";
                        statusAt = System.currentTimeMillis();
                    }
                    return true;
                }
                if (isInside(mx, my, panelX + pad, iy, innerW - 22.0f, itemH)) {
                    ProxyManager pm = ProxyManager.getInstance();
                    ProxyManager.ProxyEntry chosen = list.get(i);
                    if (pm.setActiveProxy(chosen) && pm.setEnabled(true)) {
                        statusMsg = "Selected: " + chosen.host();
                    } else {
                        statusMsg = "Failed to save proxies.json";
                    }
                    statusAt = System.currentTimeMillis();
                    return true;
                }
            }
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (maxScroll > 0.0f) {
            targetScroll = Mth.clamp(targetScroll - (float) verticalAmount * 18.0f, 0.0f, maxScroll);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    private void tryAddProxy() {
        if (ProxyManager.getInstance().hasLoadFailure()) {
            statusMsg = "Reset broken proxies.json first";
            statusAt = System.currentTimeMillis();
            return;
        }
        String[] parts = hostPortInput.trim().split(":");
        if (parts.length != 2 || parts[0].isBlank()) {
            statusMsg = "Format must be host:port";
            statusAt = System.currentTimeMillis();
            return;
        }
        try {
            int port = Integer.parseInt(parts[1].trim());
            if (port <= 0 || port > 65535) throw new IllegalArgumentException();
            boolean hasCreds = !userInput.isBlank() || !passInput.isBlank();
            boolean effectiveTls = useTls || hasCreds;
            boolean hadPass = !passInput.isBlank();
            ProxyManager.ProxyEntry entry = new ProxyManager.ProxyEntry("", selectedType, parts[0].trim(), port, userInput, passInput, effectiveTls);
            ProxyManager pm = ProxyManager.getInstance();
            if (!pm.addProxy(entry) || !pm.setActiveProxy(entry) || !pm.setEnabled(true)) {
                statusMsg = "Failed to save proxies.json";
                statusAt = System.currentTimeMillis();
                return;
            }
            hostPortInput = "";
            userInput = "";
            passInput = "";
            useTls = false;
            statusMsg = "Added " + entry.host() + ":" + entry.port()
                    + (effectiveTls ? " (TLS)" : "")
                    + (hadPass && !ProxyManager.supportsProtectedSecretStorage() ? " · session-only pass" : "");
            statusAt = System.currentTimeMillis();
        } catch (Throwable ignored) {
            statusMsg = "Invalid port number";
            statusAt = System.currentTimeMillis();
        }
    }

    @Override
    public boolean keyPressed(KeyEvent input) {
        if (input.key() == InputConstants.KEY_ESCAPE) {
            onClose();
            return true;
        }
        if (input.key() == InputConstants.KEY_TAB) {
            focusedField = (focusedField + 1) % 3;
            return true;
        }
        if (input.key() == InputConstants.KEY_BACKSPACE) {
            if (focusedField == 0 && !hostPortInput.isEmpty()) hostPortInput = hostPortInput.substring(0, hostPortInput.length() - 1);
            else if (focusedField == 1 && !userInput.isEmpty()) userInput = userInput.substring(0, userInput.length() - 1);
            else if (focusedField == 2 && !passInput.isEmpty()) passInput = passInput.substring(0, passInput.length() - 1);
            return true;
        }
        if (input.isPaste() && minecraft != null) {
            String clip = minecraft.keyboardHandler.getClipboard();
            if (clip != null) {
                String cleaned = clip.replaceAll("\\p{Cntrl}", "");
                if (focusedField == 0) {
                    String trimmed = cleaned.trim();
                    if (hostPortInput.length() + trimmed.length() > 128) {
                        statusMsg = "Host:port too long (max 128 chars)";
                        statusAt = System.currentTimeMillis();
                    } else {
                        hostPortInput += trimmed;
                    }
                } else if (focusedField == 1) {
                    if (userInput.length() + cleaned.length() > 255) {
                        statusMsg = "Username exceeds 255-char SOCKS5 limit";
                        statusAt = System.currentTimeMillis();
                    } else {
                        userInput += cleaned;
                    }
                } else if (focusedField == 2) {
                    if (passInput.length() + cleaned.length() > 255) {
                        statusMsg = "Password exceeds 255-char SOCKS5 limit";
                        statusAt = System.currentTimeMillis();
                    } else {
                        passInput += cleaned;
                    }
                }
            }
            return true;
        }
        if (input.key() == InputConstants.KEY_RETURN) {
            tryAddProxy();
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean charTyped(CharacterEvent input) {
        String s = input.codepointAsString();
        if (focusedField == 0 && s != null && !s.isEmpty() && s.charAt(0) > 127) {
            s = CommandFix.translateRuToEn(s);
        }
        if (s != null && !s.isEmpty()) {
            char cp = s.charAt(0);
            if (cp >= 32 && cp < 127) {
                if (focusedField == 0 && hostPortInput.length() < 128) hostPortInput += cp;
                else if (focusedField == 1 && userInput.length() < 255) userInput += cp;
                else if (focusedField == 2 && passInput.length() < 255) passInput += cp;
                return true;
            }
        }
        return super.charTyped(input);
    }

    @Override
    public void removed() {
        focusedField = -1;
        if (minecraft != null) {
            minecraft.textInputManager().stopTextInput(this);
        }
        super.removed();
    }

    @Override
    public void onClose() {
        if (minecraft != null && minecraft.gui != null) {
            minecraft.gui.setScreen(parent);
        }
    }

    private float currentReveal() {
        return openProgress * openProgress * (3.0f - 2.0f * openProgress);
    }

    private float computePanelY(float panelH, float reveal) {
        return (height - panelH) * 0.5f + (1.0f - reveal) * 10.0f;
    }

    private static boolean isInside(double mx, double my, float x, float y, float w, float h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }
}
