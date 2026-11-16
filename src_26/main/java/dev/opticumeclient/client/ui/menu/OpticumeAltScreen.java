package dev.opticumeclient.client.ui.menu;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.InputConstants;
import dev.opticumeclient.client.gui.ClickGuiScreen;
import dev.opticumeclient.client.render.ColorUtils;
import dev.opticumeclient.client.render.Render2DHelper;
import dev.opticumeclient.client.render.TextureAssets;
import dev.opticumeclient.client.render.ThemeManager;
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
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

/**
 * Working offline account switcher styled cleanly in the Desire glass aesthetic.
 * Persists saved nicknames in {@link MenuConfigStore} and switches {@code Minecraft.user}.
 */
public class OpticumeAltScreen extends Screen {
    private static final String[] NAME_PREFIXES = {"Opti", "Nova", "Aura", "Shadow", "Cryo", "Sky", "Velo", "Blade", "Frost", "Kuro"};
    private static final String[] NAME_SUFFIXES = {"PvP", "Ray", "Craft", "Fox", "Z", "MC", "Vibe", "Storm", "Byte", "Core"};
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final Random RANDOM = new Random();

    private static final class AltEntry {
        final String name;
        boolean favorite;
        final String date;
        float hover;

        AltEntry(String name, boolean favorite, String date) {
            this.name = name;
            this.favorite = favorite;
            this.date = date == null || date.isBlank() ? LocalDate.now().format(DATE_FMT) : date;
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
    private long lastNanos = System.nanoTime();

    public OpticumeAltScreen(Screen parent) {
        super(Component.literal("AltManager"));
        this.parent = parent;
        loadEntries();
    }

    @Override
    protected void init() {
        super.init();
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
        float reveal = openProgress * openProgress * (3.0f - 2.0f * openProgress);
        scroll += (targetScroll - scroll) * (1.0f - (float) Math.exp(-16.0f * dt));

        float normX = (mouseX / Math.max(1.0f, (float) width) - 0.5f) * 2.0f;
        float normY = (mouseY / Math.max(1.0f, (float) height) - 0.5f) * 2.0f;
        MenuBackdrop.render(context, () -> super.extractPanorama(context, delta),
                MenuWallpapers.selected(), width, height, normX, normY, 1.0f, 1.05f);

        // Panel geometry
        float panelW = Math.min(390.0f, width - 32.0f);
        float panelH = Math.min(310.0f, height - 32.0f);
        float panelX = (width - panelW) * 0.5f;
        float panelY = (height - panelH) * 0.5f + (1.0f - reveal) * 12.0f;

        Render2DHelper.drawGlow(context, panelX, panelY + 2, panelW, panelH, 8.0f,
                ColorUtils.withAlpha(0xFF000000, Math.round(90 * reveal)));
        Render2DHelper.drawRoundedRect(context, panelX, panelY, panelW, panelH, 12.0f,
                ColorUtils.withAlpha(0xFF0B0E17, Math.round(188 * reveal)));
        Render2DHelper.drawRoundedOutline(context, panelX, panelY, panelW, panelH, 12.0f, 1.0f,
                ColorUtils.withAlpha(0xFFFFFFFF, Math.round(34 * reveal)));

        // Header + Back button
        float pad = 14.0f;
        Render2DHelper.drawScaledText(context, "AltManager", panelX + pad, panelY + 13.0f,
                ColorUtils.withAlpha(0xFFFFFFFF, Math.round(240 * reveal)), 1.25f, true);

        float backW = 54.0f;
        float backH = 20.0f;
        float backX = panelX + panelW - pad - backW;
        float backY = panelY + 11.0f;
        boolean backHov = isInside(mouseX, mouseY, backX, backY, backW, backH);
        OpticumeButtonStyle.renderVanillaButton(context, "alt_back", Math.round(backX), Math.round(backY),
                Math.round(backW), Math.round(backH), true, backHov, reveal);
        Render2DHelper.drawCenteredText(context, "Back", backX + backW * 0.5f, backY + 6.0f,
                ColorUtils.withAlpha(0xFFFFFFFF, Math.round(235 * reveal)), false);

        // Active account card
        String currentName = (minecraft != null && minecraft.getUser() != null) ? minecraft.getUser().getName() : "Player";
        UUID currentUuid = UUIDUtil.createOfflinePlayerUUID(currentName);
        float cardY = panelY + 38.0f;
        float cardH = 38.0f;
        float innerW = panelW - pad * 2.0f;
        int accent = ThemeManager.getInstance().getAccentColor(0);

        Render2DHelper.drawRoundedRect(context, panelX + pad, cardY, innerW, cardH, 8.0f,
                ColorUtils.withAlpha(0xFFFFFFFF, Math.round(16 * reveal)));
        Render2DHelper.drawRoundedOutline(context, panelX + pad, cardY, innerW, cardH, 8.0f, 1.0f,
                ColorUtils.withAlpha(accent, Math.round(115 * reveal)));

        Render2DHelper.drawPlayerHeadByUuid(context, currentUuid, Math.round(panelX + pad + 7), Math.round(cardY + 7), 24, reveal);
        Render2DHelper.drawText(context, currentName, panelX + pad + 39.0f, cardY + 8.0f,
                ColorUtils.withAlpha(0xFFFFFFFF, Math.round(245 * reveal)), true);
        Render2DHelper.drawScaledText(context, "Active Offline Session", panelX + pad + 39.0f, cardY + 21.0f,
                ColorUtils.withAlpha(0xFF9CA3AF, Math.round(220 * reveal)), 0.82f, false);

        // Input row: [ Nickname input ] [ Login ] [ Random ]
        float rowY = cardY + cardH + 10.0f;
        float rowH = 24.0f;
        float rndW = 58.0f;
        float loginW = 54.0f;
        float gap = 6.0f;
        float inputW = innerW - rndW - loginW - gap * 2.0f;

        boolean inputHov = isInside(mouseX, mouseY, panelX + pad, rowY, inputW, rowH);
        int inputBg = ColorUtils.withAlpha(0xFF070910, Math.round((inputFocused ? 205 : 160) * reveal));
        int inputBorder = inputFocused
                ? ColorUtils.withAlpha(accent, Math.round(180 * reveal))
                : ColorUtils.withAlpha(0xFFFFFFFF, Math.round((inputHov ? 64 : 32) * reveal));
        Render2DHelper.drawRoundedRect(context, panelX + pad, rowY, inputW, rowH, 7.0f, inputBg);
        Render2DHelper.drawRoundedOutline(context, panelX + pad, rowY, inputW, rowH, 7.0f, 1.0f, inputBorder);

        String displayInput = inputName.isEmpty() && !inputFocused ? "Enter nickname (3-16 chars)..." : inputName;
        String caret = inputFocused && (System.currentTimeMillis() / 500L) % 2L == 0 ? "_" : "";
        int inputTextColor = inputName.isEmpty() && !inputFocused
                ? ColorUtils.withAlpha(0xFF8B93A6, Math.round(200 * reveal))
                : ColorUtils.withAlpha(0xFFFFFFFF, Math.round(245 * reveal));
        Render2DHelper.drawText(context, displayInput + caret, panelX + pad + 8.0f, rowY + 8.0f, inputTextColor, false);

        float loginX = panelX + pad + inputW + gap;
        boolean loginHov = isInside(mouseX, mouseY, loginX, rowY, loginW, rowH);
        OpticumeButtonStyle.renderVanillaButton(context, "alt_login", Math.round(loginX), Math.round(rowY),
                Math.round(loginW), Math.round(rowH), !inputName.isBlank(), loginHov, reveal);
        Render2DHelper.drawCenteredText(context, "Login", loginX + loginW * 0.5f, rowY + 8.0f,
                ColorUtils.withAlpha(0xFFFFFFFF, Math.round((inputName.isBlank() ? 120 : 245) * reveal)), false);

        float rndX = loginX + loginW + gap;
        boolean rndHov = isInside(mouseX, mouseY, rndX, rowY, rndW, rowH);
        OpticumeButtonStyle.renderVanillaButton(context, "alt_random", Math.round(rndX), Math.round(rowY),
                Math.round(rndW), Math.round(rowH), true, rndHov, reveal);
        Render2DHelper.drawCenteredText(context, "Random", rndX + rndW * 0.5f, rowY + 8.0f,
                ColorUtils.withAlpha(0xFFFFFFFF, Math.round(235 * reveal)), false);

        // Saved accounts list
        float listY = rowY + rowH + 10.0f;
        float listH = (panelY + panelH - pad - 14.0f) - listY;
        float itemH = 28.0f;
        float itemGap = 5.0f;
        float totalListH = Math.max(0.0f, entries.size() * (itemH + itemGap) - itemGap);
        maxScroll = Math.max(0.0f, totalListH - listH);
        targetScroll = Mth.clamp(targetScroll, 0.0f, maxScroll);
        scroll = Mth.clamp(scroll, 0.0f, maxScroll);

        context.enableScissor(Math.round(panelX + pad), Math.round(listY), Math.round(panelX + panelW - pad), Math.round(listY + listH));
        if (entries.isEmpty()) {
            Render2DHelper.drawCenteredText(context, "No saved alts yet — type a name and click Login",
                    panelX + panelW * 0.5f, listY + listH * 0.4f, ColorUtils.withAlpha(0xFF94A3B8, Math.round(200 * reveal)), false);
        } else {
            for (int i = 0; i < entries.size(); i++) {
                AltEntry entry = entries.get(i);
                float iy = listY + i * (itemH + itemGap) - scroll;
                if (iy + itemH < listY || iy > listY + listH) continue;

                boolean activeAcc = entry.name.equalsIgnoreCase(currentName);
                boolean rowHov = isInside(mouseX, mouseY, panelX + pad, iy, innerW, itemH);
                entry.hover += ((rowHov ? 1.0f : 0.0f) - entry.hover) * (1.0f - (float) Math.exp(-14.0f * dt));

                int rowBg = activeAcc
                        ? ColorUtils.withAlpha(accent, Math.round(48 * reveal))
                        : ColorUtils.withAlpha(0xFFFFFFFF, Math.round(Mth.lerp(entry.hover, 10.0f, 24.0f) * reveal));
                int rowOutline = activeAcc
                        ? ColorUtils.withAlpha(accent, Math.round(165 * reveal))
                        : ColorUtils.withAlpha(0xFFFFFFFF, Math.round(Mth.lerp(entry.hover, 18.0f, 52.0f) * reveal));

                Render2DHelper.drawRoundedRect(context, panelX + pad, iy, innerW, itemH, 7.0f, rowBg);
                Render2DHelper.drawRoundedOutline(context, panelX + pad, iy, innerW, itemH, 7.0f, 1.0f, rowOutline);

                UUID accUuid = UUIDUtil.createOfflinePlayerUUID(entry.name);
                Render2DHelper.drawPlayerHeadByUuid(context, accUuid, Math.round(panelX + pad + 6), Math.round(iy + 5), 18, reveal);
                Render2DHelper.drawText(context, entry.name, panelX + pad + 30.0f, iy + 6.0f,
                        ColorUtils.withAlpha(0xFFFFFFFF, Math.round(240 * reveal)), false);
                Render2DHelper.drawScaledText(context, (activeAcc ? "Active • " : "") + entry.date,
                        panelX + pad + 30.0f, iy + 16.5f, ColorUtils.withAlpha(0xFF9CA3AF, Math.round(205 * reveal)), 0.72f, false);

                // Favorite star button
                float delX = panelX + pad + innerW - 20.0f;
                float starX = delX - 20.0f;
                float btnY = iy + 6.0f;
                int starTint = entry.favorite
                        ? ColorUtils.withAlpha(0xFFFACC15, Math.round(245 * reveal))
                        : ColorUtils.withAlpha(0xFFFFFFFF, Math.round((rowHov ? 140 : 65) * reveal));
                Render2DHelper.drawTexture(context, entry.favorite ? TextureAssets.ICON_STAR_FILLED : TextureAssets.ICON_FAVORITES,
                        starX + 2.0f, btnY + 2.0f, 12.0f, 12.0f, starTint);

                // Delete button
                boolean delHov = isInside(mouseX, mouseY, delX, btnY, 16.0f, 16.0f);
                Render2DHelper.drawCenteredText(context, "×", delX + 8.0f, btnY + 4.0f,
                        ColorUtils.withAlpha(delHov ? 0xFFF87171 : 0xFF9CA3AF, Math.round((delHov ? 255 : 170) * reveal)), false);
            }
        }
        context.disableScissor();

        // Status toast footer
        if (!statusMessage.isEmpty() && System.currentTimeMillis() - statusTime < 2600L) {
            Render2DHelper.drawCenteredText(context, statusMessage, panelX + panelW * 0.5f,
                    panelY + panelH - 13.0f, ColorUtils.withAlpha(0xFF86EFAC, Math.round(235 * reveal)), false);
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

        // Back button
        float backW = 54.0f;
        float backH = 20.0f;
        if (isInside(mx, my, panelX + panelW - pad - backW, panelY + 11.0f, backW, backH)) {
            playClick();
            onClose();
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
        float listY = rowY + rowH + 10.0f;
        float listH = (panelY + panelH - pad - 14.0f) - listY;
        float itemH = 28.0f;
        float itemGap = 5.0f;
        if (isInside(mx, my, panelX + pad, listY, innerW, listH)) {
            for (int i = 0; i < entries.size(); i++) {
                float iy = listY + i * (itemH + itemGap) - scroll;
                if (my >= iy && my <= iy + itemH) {
                    float delX = panelX + pad + innerW - 20.0f;
                    float starX = delX - 20.0f;
                    AltEntry entry = entries.get(i);
                    if (mx >= delX && mx <= delX + 18.0f) {
                        playClick();
                        entries.remove(i);
                        saveEntries();
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
                    playClick();
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
            if (ch.matches("[a-zA-Z0-9_]") && inputName.length() < 16) {
                inputName += ch;
            }
            return true;
        }
        return super.charTyped(input);
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
            if (e.name.equalsIgnoreCase(name)) {
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
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        UUID uuid = UUIDUtil.createOfflinePlayerUUID(name);
        User newUser = new User(name, uuid, "0", Optional.empty(), Optional.empty());
        ((MinecraftAccessor) (Object) mc).opticume$setUser(newUser);
        MenuConfigStore.save(obj -> obj.addProperty("selectedAccount", name));
        status("Signed in as " + name);
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
        entries.sort((a, b) -> Boolean.compare(b.favorite, a.favorite));
    }

    private void loadEntries() {
        entries.clear();
        JsonArray arr = MenuConfigStore.getArray("alts");
        if (arr != null) {
            for (JsonElement el : arr) {
                if (!el.isJsonObject()) continue;
                JsonObject obj = el.getAsJsonObject();
                String n = obj.has("name") ? obj.get("name").getAsString() : "";
                if (!n.matches("^[a-zA-Z0-9_]{1,16}$")) continue;
                boolean fav = obj.has("favorite") && obj.get("favorite").getAsBoolean();
                String date = obj.has("date") ? obj.get("date").getAsString() : "";
                entries.add(new AltEntry(n, fav, date));
            }
        }
        sortEntries();
    }

    private void saveEntries() {
        JsonArray arr = new JsonArray();
        for (AltEntry e : entries) {
            JsonObject obj = new JsonObject();
            obj.addProperty("name", e.name);
            obj.addProperty("favorite", e.favorite);
            obj.addProperty("date", e.date);
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
