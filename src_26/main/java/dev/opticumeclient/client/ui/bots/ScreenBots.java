package dev.opticumeclient.client.ui.bots;

import com.mojang.blaze3d.platform.InputConstants;
import dev.opticumeclient.client.bots.BotSession;
import dev.opticumeclient.client.bots.BotSessionManager;
import dev.opticumeclient.client.bots.SessionState;
import dev.opticumeclient.client.gui.ClickGuiScreen;
import dev.opticumeclient.client.gui.SystemTheme;
import dev.opticumeclient.client.render.BlurredRectRenderState;
import dev.opticumeclient.client.render.ColorUtils;
import dev.opticumeclient.client.render.KawaseBlurRenderer;
import dev.opticumeclient.client.render.Render2DHelper;
import dev.opticumeclient.client.render.ThemeManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.IntPredicate;

/**
 * Desire-ported multi-session bot dashboard rendered with OpticumeClient 2D Liquid Glass UI.
 */
public class ScreenBots extends Screen {
    private static final ScreenBots INSTANCE = new ScreenBots();
    private static final Minecraft mc = Minecraft.getInstance();

    private static final String[] NICK_PREFIXES = {
            "Shadow", "Night", "Cyber", "Swift", "Ghost", "Frost", "Storm", "Pixel", "Toxic", "Lucky"
    };

    private static final float PAD = 10f;
    private static final float BTN_H = 16f;
    private static final float ROW_H = 32f;
    private static final float SLOT_GAP = 3f;

    private static final int GREEN = 0xFF34C45C;
    private static final int ORANGE = 0xFFE68A10;
    private static final int RED = 0xFFEB5050;
    private static final int YELLOW = 0xFFFFC846;

    private final Field nickField = new Field("Ник", 16, c -> Character.isLetterOrDigit(c) && c < 128 || c == '_', false);
    private final Field ipField = new Field("IP сервера", 64, c -> c > ' ' && c != 127, false);
    private final Field passField = new Field("Пароль (авто /login)", 32, c -> c > ' ' && c != 127, true);
    private final Field chatField = new Field("Напишите сообщение", 256, c -> c >= ' ' && c != 127, false);
    private final Field inlineField = new Field("", 64, c -> c > ' ' && c != 127, false);

    private BotSession inlineTarget;
    private InlineMode inlineMode;
    private BotSession selected;
    private boolean adding;
    private ViewMode view = ViewMode.INVENTORY;
    private boolean chatToAll;

    private Field focused;
    private String status;
    private boolean statusError;
    private long statusAtMs;

    private float scroll;
    private float targetScroll;
    private float maxScroll;
    private float listX, listY, listW, listH;

    private final List<Hit> hits = new ArrayList<>();
    private final List<ItemDraw> items = new ArrayList<>();
    private final Map<BotSession, BotMiniMap> miniMaps = new IdentityHashMap<>();
    private float clipTop = Float.NEGATIVE_INFINITY;
    private float clipBottom = Float.POSITIVE_INFINITY;

    private ScreenBots() {
        super(Component.literal("Bots"));
    }

    public static ScreenBots getInstance() {
        return INSTANCE;
    }

    @Override
    protected void init() {
        super.init();
        if (ipField.text.isEmpty()) {
            ServerData server = mc.getCurrentServer();
            if (server != null && server.ip != null) {
                ipField.set(server.ip);
            }
        }
        closeInline();
        focused = null;
        adding = false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
    }

    @Override
    public void onClose() {
        focused = null;
        closeInline();
        super.onClose();
    }

    @Override
    public void removed() {
        focused = null;
        if (minecraft != null) {
            minecraft.textInputManager().stopTextInput(this);
        }
        super.removed();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        if (minecraft != null) {
            if (focused != null) minecraft.textInputManager().startTextInput(this);
            else minecraft.textInputManager().stopTextInput(this);
        }
        hits.clear();
        items.clear();

        scroll += (targetScroll - scroll) * (1.0f - (float) Math.exp(-16.0f * Math.min(0.05f, delta)));
        renderWindow(context, mouseX, mouseY);

        for (ItemDraw item : items) {
            context.item(item.stack(), item.x(), item.y());
            context.itemDecorations(font, item.stack(), item.x(), item.y());
        }
    }

    private void renderWindow(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        BotSessionManager manager = BotSessionManager.getInstance();
        int accent = ThemeManager.getInstance().getAccentColor(0);

        float winW = Math.min(width - 30f, 620f);
        float winH = Math.min(height - 30f, 400f);
        float winX = (width - winW) / 2f;
        float winY = (height - winH) / 2f;

        Render2DHelper.drawRect(context, 0f, 0f, width, height, 0x32000000);
        KawaseBlurRenderer.requestWorldBlur(3, 2.0f);
        int glassTint = ColorUtils.withAlpha(SystemTheme.bg(), SystemTheme.isDark() ? 115 : 165);
        if (!BlurredRectRenderState.trySubmit(context, winX, winY, winW, winH, 12f, glassTint, 1.0f)) {
            Render2DHelper.drawRoundedRectFloat(context, winX, winY, winW, winH, 12f, SystemTheme.fade(SystemTheme.bg(), 0.85f));
        }
        Render2DHelper.drawRoundedOutlineFloat(context, winX, winY, winW, winH, 12f, 0.6f, SystemTheme.fade(SystemTheme.border(), 0.85f));

        Render2DHelper.drawScaledText(context, "Боты", winX + PAD + 2f, winY + 10f, SystemTheme.foreground(), 1.0f, false);
        String counter = manager.getActiveBotsCount() + " / " + manager.getMaxBots();
        float counterW = Render2DHelper.getTextWidth(counter, 0.80f);
        Render2DHelper.drawScaledText(context, counter, winX + winW - PAD - 2f - counterW, winY + 11f,
                SystemTheme.mutedFg(), 0.80f, false);

        if (status != null && System.currentTimeMillis() - statusAtMs < 4000L) {
            float sx = winX + PAD + 14f + Render2DHelper.getTextWidth("Боты", 1.0f);
            float maxW = Math.max(40f, winX + winW - PAD - 20f - counterW - sx);
            Render2DHelper.drawScaledText(context, Render2DHelper.trimToWidth(status, 0.76f, maxW),
                    sx, winY + 11.5f, statusError ? RED : GREEN, 0.76f, false);
        }

        List<BotSession> sessions = new ArrayList<>();
        sessions.add(manager.getMainSession());
        sessions.addAll(manager.getAllBots());
        miniMaps.keySet().removeIf(session -> {
            if (sessions.contains(session)) return false;
            miniMaps.get(session).release();
            return true;
        });
        if (selected == null || !sessions.contains(selected)) {
            selected = manager.getActiveSession();
        }
        if (inlineTarget != null && inlineTarget != selected) {
            closeInline();
        }

        float top = winY + 28f;
        float chatH = 20f;
        float bodyH = winH - (top - winY) - PAD - chatH - 8f;
        float leftW = Math.min(168f, winW * 0.29f);

        renderList(context, sessions, winX + PAD, top, leftW, bodyH, mouseX, mouseY, accent);

        float rx = winX + PAD + leftW + 8f;
        float rw = winX + winW - PAD - rx;
        Render2DHelper.drawRoundedRectFloat(context, rx, top, rw, bodyH, 6f, SystemTheme.fade(SystemTheme.surface(), 0.78f));
        Render2DHelper.drawRoundedOutlineFloat(context, rx, top, rw, bodyH, 6f, 0.8f, SystemTheme.fade(SystemTheme.border(), 0.55f));
        if (adding) {
            renderAddForm(context, rx, top, rw, mouseX, mouseY);
        } else {
            renderDetails(context, selected, rx, top, rw, bodyH, mouseX, mouseY);
        }

        renderChatBar(context, winX + PAD, top + bodyH + 8f, winW - PAD * 2f, chatH, mouseX, mouseY);
    }

    private void renderList(GuiGraphicsExtractor context, List<BotSession> sessions, float x, float y, float w, float h,
                            int mouseX, int mouseY, int accent) {
        BotSessionManager manager = BotSessionManager.getInstance();
        Render2DHelper.drawRoundedRectFloat(context, x, y, w, h, 6f, SystemTheme.fade(SystemTheme.surface(), 0.78f));
        Render2DHelper.drawRoundedOutlineFloat(context, x, y, w, h, 6f, 0.8f, SystemTheme.fade(SystemTheme.border(), 0.55f));

        listX = x;
        listY = y + 5f;
        listW = w;
        listH = h - 5f - BTN_H - 12f;

        clipTop = listY;
        clipBottom = listY + listH;
        context.enableScissor(Math.round(listX), Math.round(listY), Math.round(listX + listW), Math.round(listY + listH));
        for (int i = 0; i < sessions.size(); i++) {
            BotSession session = sessions.get(i);
            float ry = listY + scroll + i * (ROW_H + 3f);
            if (ry + ROW_H < listY || ry > listY + listH) continue;

            boolean sel = session == selected && !adding;
            boolean hov = isHovered(mouseX, mouseY, x + 5f, ry, w - 10f, ROW_H)
                    && mouseY >= clipTop && mouseY <= clipBottom;
            if (sel || hov) {
                int fill = sel ? ColorUtils.withAlpha(accent, 42) : ColorUtils.withAlpha(0xFFFFFFFF, 16);
                Render2DHelper.drawRoundedRectFloat(context, x + 5f, ry, w - 10f, ROW_H, 5f, fill);
                if (sel) {
                    Render2DHelper.drawRoundedOutlineFloat(context, x + 5f, ry, w - 10f, ROW_H, 5f, 0.8f,
                            ColorUtils.withAlpha(accent, 140));
                }
            }
            Render2DHelper.drawCircle(context, x + 13f, ry + 11f, 2.2f, stateColor(session));
            Render2DHelper.drawScaledText(context, Render2DHelper.trimToWidth(displayName(session), 0.78f, w - 30f),
                    x + 20f, ry + 6.5f, SystemTheme.foreground(), 0.78f, false);
            Render2DHelper.drawScaledText(context, Render2DHelper.trimToWidth(listSubtitle(session), 0.65f, w - 30f),
                    x + 20f, ry + 18f, SystemTheme.mutedFg(), 0.65f, false);

            hit(x + 5f, ry, w - 10f, ROW_H, () -> {
                selected = session;
                adding = false;
                closeInline();
            });
        }
        context.disableScissor();
        clipTop = Float.NEGATIVE_INFINITY;
        clipBottom = Float.POSITIVE_INFINITY;

        float contentH = sessions.size() * (ROW_H + 3f) - 3f;
        maxScroll = Math.min(listH - contentH, 0f);
        targetScroll = Mth.clamp(targetScroll, maxScroll, 0f);
        scroll = Mth.clamp(scroll, maxScroll, 0f);

        float by = y + h - BTN_H - 6f;
        float bw = (w - 14f) / 2f;
        boolean full = manager.getAllBots().size() >= manager.getMaxBots();
        button(context, x + 5f, by, bw, BTN_H, full ? "Лимит " + manager.getMaxBots() : "+ добавить",
                adding ? GREEN : 0, !full, mouseX, mouseY, this::toggleAddForm);
        BotSession target = selected;
        button(context, x + 9f + bw, by, bw, BTN_H, "Удалить", 0,
                !adding && target != null && target != manager.getMainSession(), mouseX, mouseY, () -> removeBot(target));
    }

    private void renderDetails(GuiGraphicsExtractor context, BotSession session, float x, float y,
                               float w, float h, int mouseX, int mouseY) {
        BotSessionManager manager = BotSessionManager.getInstance();
        boolean onScreen = manager.isForeground(session);
        LocalPlayer player = onScreen ? mc.player : session.getPlayer();
        ClientLevel level = onScreen ? mc.level : session.getWorld();
        if (level == null) player = null;

        float ix = x + 10f;
        float iw = w - 20f;
        float cy = y + 9f;

        Render2DHelper.drawScaledText(context, Render2DHelper.trimToWidth(displayName(session), 0.96f, iw - 90f),
                ix, cy, SystemTheme.foreground(), 0.96f, false);
        String state = onScreen ? "На экране" : stateText(session);
        float stateW = Render2DHelper.getTextWidth(state, 0.72f);
        Render2DHelper.drawScaledText(context, state, ix + iw - stateW, cy + 1.5f, SystemTheme.mutedFg(), 0.72f, false);
        cy += 16f;

        float hp = player != null ? player.getHealth() : 0f;
        float maxHp = player != null ? Math.max(1f, player.getMaxHealth()) : 20f;
        String hpText = player != null ? Math.round(hp) + " / " + Math.round(maxHp) : "— / —";
        Render2DHelper.drawScaledText(context, hpText, ix, cy, SystemTheme.mutedFg(), 0.72f, false);
        float barX = ix + Render2DHelper.getTextWidth(hpText, 0.72f) + 8f;
        float barW = Math.max(20f, ix + iw - barX);
        Render2DHelper.drawRoundedRectFloat(context, barX, cy + 2.5f, barW, 3f, 1.5f, SystemTheme.fade(SystemTheme.border(), 0.6f));
        if (player != null) {
            float ratio = Mth.clamp(hp / maxHp, 0f, 1f);
            Render2DHelper.drawRoundedRectFloat(context, barX, cy + 2.5f, Math.max(3f, barW * ratio), 3f, 1.5f, hp <= 6f ? RED : GREEN);
        }
        cy += 13f;

        float tileH = 24f;
        float tileGap = 5f;
        float tileW = (iw - tileGap * 2f) / 3f;
        String position = "—";
        String ping = "—";
        if (player != null) {
            String dimension = level.dimension() == Level.NETHER ? "Ад" : level.dimension() == Level.END ? "Энд" : "Мир";
            position = dimension + " " + player.getBlockX() + " " + player.getBlockY() + " " + player.getBlockZ();
            ping = ping(player) + " мс";
        }
        tile(context, ix, cy, tileW, tileH, "Сервер", serverOf(session));
        tile(context, ix + tileW + tileGap, cy, tileW, tileH, "Позиция", position);
        tile(context, ix + (tileW + tileGap) * 2f, cy, tileW, tileH, "Пинг", ping);
        cy += tileH + 7f;

        float rowGap = 5f;
        float controlsY = y + h - 9f - (BTN_H * 3f + rowGap * 2f);
        String label = switch (view) {
            case INVENTORY -> "Инвентарь";
            case SCREEN -> "Экран бота";
            case CHAT -> "Чат бота";
        };
        Render2DHelper.drawScaledText(context, label, ix, cy, SystemTheme.mutedFg(), 0.70f, false);
        float viewY = cy + 11f;
        float viewH = Math.max(30f, controlsY - 8f - viewY);
        switch (view) {
            case INVENTORY -> renderInventory(context, player, ix, viewY, iw, viewH, cy, mouseX, mouseY);
            case SCREEN -> renderMiniScreen(context, session, player, level, ix, viewY, iw, viewH, mouseX, mouseY);
            case CHAT -> renderChatLog(context, session, ix, viewY, iw, viewH);
        }

        renderControls(context, session, ix, controlsY, iw, rowGap, mouseX, mouseY);
    }

    private void tile(GuiGraphicsExtractor context, float x, float y, float w, float h, String title, String value) {
        Render2DHelper.drawRoundedRectFloat(context, x, y, w, h, 4f, SystemTheme.fade(SystemTheme.elevated(), 0.72f));
        float tw = Render2DHelper.getTextWidth(title, 0.62f);
        Render2DHelper.drawScaledText(context, title, x + (w - tw) * 0.5f, y + 3.5f, SystemTheme.mutedFg(), 0.62f, false);
        String vTrim = Render2DHelper.trimToWidth(value, 0.70f, w - 8f);
        float vw = Render2DHelper.getTextWidth(vTrim, 0.70f);
        Render2DHelper.drawScaledText(context, vTrim, x + (w - vw) * 0.5f, y + 12.5f, SystemTheme.foreground(), 0.70f, false);
    }

    private void renderInventory(GuiGraphicsExtractor context, LocalPlayer player, float x, float y, float w, float h,
                                 float labelY, int mouseX, int mouseY) {
        float sectionGap = 5f;
        float slot = Math.min((w - SLOT_GAP * 8f) / 9f, (h - SLOT_GAP * 2f - sectionGap * 2f) / 5f);
        slot = Math.max(12f, Math.min(slot, 32f));
        float gridW = slot * 9f + SLOT_GAP * 8f;
        float gx = x + (w - gridW) / 2f;

        ItemStack hovered = ItemStack.EMPTY;
        EquipmentSlot[] armor = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        for (int col = 0; col < armor.length; col++) {
            ItemStack stack = player != null ? player.getItemBySlot(armor[col]) : ItemStack.EMPTY;
            if (slot(context, stack, gx + col * (slot + SLOT_GAP), y, slot, false, mouseX, mouseY)) hovered = stack;
        }
        ItemStack offhand = player != null ? player.getItemBySlot(EquipmentSlot.OFFHAND) : ItemStack.EMPTY;
        if (slot(context, offhand, gx + 8f * (slot + SLOT_GAP), y, slot, false, mouseX, mouseY)) hovered = offhand;

        float rowsY = y + slot + sectionGap + SLOT_GAP;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                ItemStack stack = player != null ? player.getInventory().getItem(9 + row * 9 + col) : ItemStack.EMPTY;
                if (slot(context, stack, gx + col * (slot + SLOT_GAP), rowsY + row * (slot + SLOT_GAP), slot, false, mouseX, mouseY)) {
                    hovered = stack;
                }
            }
        }

        float hotbarY = rowsY + 3f * (slot + SLOT_GAP) + sectionGap;
        int selectedSlot = player != null ? player.getInventory().getSelectedSlot() : -1;
        for (int col = 0; col < 9; col++) {
            ItemStack stack = player != null ? player.getInventory().getItem(col) : ItemStack.EMPTY;
            if (slot(context, stack, gx + col * (slot + SLOT_GAP), hotbarY, slot, col == selectedSlot, mouseX, mouseY)) {
                hovered = stack;
            }
        }

        String right = !hovered.isEmpty() ? hovered.getHoverName().getString() : player == null ? "нет данных" : null;
        if (right != null) {
            right = Render2DHelper.trimToWidth(right, 0.68f, w - 70f);
            float rw = Render2DHelper.getTextWidth(right, 0.68f);
            Render2DHelper.drawScaledText(context, right, x + w - rw, labelY,
                    hovered.isEmpty() ? SystemTheme.mutedFg() : SystemTheme.foreground(), 0.68f, false);
        }
    }

    private boolean slot(GuiGraphicsExtractor context, ItemStack stack, float x, float y, float size, boolean active, int mouseX, int mouseY) {
        boolean hov = isHovered(mouseX, mouseY, x, y, size, size) && !stack.isEmpty();
        int fill = active ? 0x22FFFFFF : (hov ? 0x16FFFFFF : SystemTheme.fade(SystemTheme.elevated(), 0.72f));
        Render2DHelper.drawRoundedRectFloat(context, x, y, size, size, 3.5f, fill);
        if (active) {
            Render2DHelper.drawRoundedOutlineFloat(context, x, y, size, size, 3.5f, 0.9f, 0x70FFFFFF);
        }
        if (!stack.isEmpty()) {
            items.add(new ItemDraw(stack, Math.round(x + (size - 16f) / 2f), Math.round(y + (size - 16f) / 2f)));
        }
        return hov;
    }

    private void renderChatLog(GuiGraphicsExtractor context, BotSession session, float x, float y, float w, float h) {
        Render2DHelper.drawRoundedRectFloat(context, x, y, w, h, 4f, SystemTheme.fade(SystemTheme.bg(), 0.75f));
        int count = Math.max(1, (int) ((h - 8f) / 10f));
        List<String> chat = session.getRecentChat(count);
        if (chat.isEmpty()) {
            Render2DHelper.drawCenteredText(context, "Чат пуст", x + w * 0.5f, y + h * 0.5f - 4f, SystemTheme.mutedFg(), false);
            return;
        }
        float lineY = y + h - 5f - chat.size() * 10f;
        for (String line : chat) {
            Render2DHelper.drawScaledText(context, Render2DHelper.trimToWidth(line, 0.72f, w - 12f),
                    x + 6f, lineY, 0xFFE1E1E6, 0.72f, false);
            lineY += 10f;
        }
    }

    private void renderMiniScreen(GuiGraphicsExtractor context, BotSession session, LocalPlayer player,
                                  ClientLevel level, float x, float y, float w, float h, int mouseX, int mouseY) {
        BotSessionManager manager = BotSessionManager.getInstance();
        Render2DHelper.drawRoundedRectFloat(context, x, y, w, h, 4f, 0xFF08080A);

        if (level == null || player == null) {
            String title;
            int color = SystemTheme.mutedFg();
            switch (session.getState()) {
                case CONNECTING -> title = "Подключение…";
                case CONNECTED -> title = session == manager.getMainSession() ? "Не в игре" : "Загрузка мира…";
                case ERROR -> {
                    title = "Ошибка";
                    color = RED;
                }
                default -> title = "Оффлайн";
            }
            if (session == manager.getMainSession()) title = "Не в игре";
            Render2DHelper.drawCenteredText(context, title, x + w * 0.5f, y + h * 0.5f - 10f, color, false);
            String reason = session.getDisconnectReason();
            if (reason != null && session.getState() != SessionState.CONNECTING) {
                Render2DHelper.drawCenteredText(context, Render2DHelper.trimToWidth(reason, 0.72f, w - 12f),
                        x + w * 0.5f, y + h * 0.5f + 4f, SystemTheme.mutedFg(), false);
            }
            long reconnectIn = manager.getReconnectInMs(session);
            if (reconnectIn >= 0) {
                Render2DHelper.drawCenteredText(context, "Реконнект через " + (reconnectIn / 1000L + 1) + "с",
                        x + w * 0.5f, y + h * 0.5f + 16f, ORANGE, false);
            }
            return;
        }

        BotMiniMap map = miniMaps.computeIfAbsent(session, s -> new BotMiniMap());
        Identifier texture = map.update(level, player);
        float mapSize = Math.min(w, h);
        context.enableScissor(Math.round(x), Math.round(y), Math.round(x + w), Math.round(y + h));
        Render2DHelper.drawTexture(context, texture, x + (w - mapSize) * 0.5f, y + (h - mapSize) * 0.5f, mapSize, mapSize, 0xFFFFFFFF);
        context.disableScissor();
        Render2DHelper.drawRect(context, x, y, w, h, 0x28000000);

        float scale = mapSize / BotMiniMap.SIZE;
        float centerX = x + w * 0.5f;
        float centerY = y + h * 0.5f;
        for (Entity entity : level.entitiesForRendering()) {
            if (entity == player) continue;
            float dx = (float) (entity.getX() - player.getX()) * scale;
            float dz = (float) (entity.getZ() - player.getZ()) * scale;
            if (Math.abs(dx) > w * 0.5f - 2f || Math.abs(dz) > h * 0.5f - 2f) continue;
            int dot;
            float r;
            if (entity instanceof Player) {
                dot = 0xFFFF4646;
                r = 1.6f;
            } else if (entity instanceof Enemy) {
                dot = 0xFFFFAA28;
                r = 1.3f;
            } else {
                continue;
            }
            Render2DHelper.drawCircle(context, centerX + dx, centerY + dz, r, dot);
        }

        float yaw = (float) Math.toRadians(player.getYRot());
        for (int i = 1; i <= 3; i++) {
            float step = i * 2.6f;
            Render2DHelper.drawCircle(context, centerX - Mth.sin(yaw) * step, centerY + Mth.cos(yaw) * step,
                    0.9f, ColorUtils.rgba(255, 255, 255, 230 - i * 50));
        }
        Render2DHelper.drawCircle(context, centerX, centerY, 2.4f, 0xFFFFFFFF);

        int modelX1 = (int) (x + w - 4f);
        int modelX0 = (int) (modelX1 - h * 0.45f);
        int modelY1 = (int) (y + h - 2f);
        int modelY0 = (int) (y + 6f);
        if (player.isAlive()) {
            InventoryScreen.extractEntityInInventoryFollowsMouse(context, modelX0, modelY0, modelX1, modelY1,
                    Math.max(10, (int) (h * 0.2f)), 0.0625f, mouseX, mouseY, player);
        } else {
            Render2DHelper.drawCenteredText(context, "МЁРТВ", x + w * 0.5f, y + h * 0.5f - 14f, RED, false);
        }

        float textMaxW = Math.max(40f, w - (modelX1 - modelX0) - 14f);
        List<String> chat = session.getRecentChat(3);
        float lineY = y + h - 4f - chat.size() * 9f;
        if (session.getActionBar() != null && System.currentTimeMillis() - session.getActionBarAtMs() < 3000L) {
            String bar = Render2DHelper.trimToWidth(session.getActionBar(), 0.70f, textMaxW);
            Render2DHelper.drawScaledText(context, bar, x + 6f, lineY - 11f, 0xFFFFE678, 0.70f, false);
        }
        for (String line : chat) {
            String text = Render2DHelper.trimToWidth(line, 0.66f, textMaxW);
            float tw = Render2DHelper.getTextWidth(text, 0.66f);
            Render2DHelper.drawRoundedRectFloat(context, x + 4f, lineY - 1f, tw + 6f, 9f, 2f, 0x78000000);
            Render2DHelper.drawScaledText(context, text, x + 7f, lineY + 0.5f, 0xFFEBEBEB, 0.66f, false);
            lineY += 9f;
        }
    }

    private void renderControls(GuiGraphicsExtractor context, BotSession session, float x, float y, float w, float gap,
                                int mouseX, int mouseY) {
        BotSessionManager manager = BotSessionManager.getInstance();
        boolean isMain = session == manager.getMainSession();
        boolean onScreen = manager.isForeground(session);
        boolean online = isMain ? mainOnline() : session.isActive();

        float third = (w - gap * 2f) / 3f;
        boolean canPlay = !onScreen && (isMain || session.isPlayable());
        button(context, x, y, third, BTN_H, onScreen ? "На экране" : "Играть", 0, canPlay, mouseX, mouseY, () -> {
            if (isMain) manager.switchToMain();
            else manager.switchTo(session);
        });
        button(context, x + third + gap, y, third, BTN_H, "Выйти в /hub", ORANGE, online, mouseX, mouseY, () -> {
            manager.sendChat(session, "/hub");
            setStatus(displayName(session) + ": /hub", false);
        });
        boolean running = session.getState() == SessionState.CONNECTED || session.getState() == SessionState.CONNECTING;
        button(context, x + (third + gap) * 2f, y, third, BTN_H, running || isMain ? "Отключить" : "Подключить", 0, !isMain,
                mouseX, mouseY, () -> {
                    if (running) {
                        manager.disconnectBot(session);
                        setStatus("Бот " + session.getName() + " отключён", false);
                    } else {
                        manager.connectBot(session);
                        setStatus("Бот " + session.getName() + " заходит на " + session.getServerAddress(), false);
                    }
                });

        float y2 = y + BTN_H + gap;
        if (inlineTarget == session) {
            float okW = 40f;
            float closeW = BTN_H;
            field(context, inlineField, x, y2, w - okW - closeW - 8f, BTN_H, mouseX, mouseY);
            button(context, x + w - okW - closeW - 4f, y2, okW, BTN_H, "OK", GREEN, true, mouseX, mouseY, this::submitInline);
            button(context, x + w - closeW, y2, closeW, BTN_H, "X", 0, true, mouseX, mouseY, this::closeInline);
        } else {
            boolean hasPass = !isMain && session.getPassword() != null && !session.getPassword().isBlank();
            button(context, x, y2, third, BTN_H, "Реконнект", !isMain && session.isAutoReconnect() ? GREEN : 0, !isMain,
                    mouseX, mouseY, () -> manager.updateBot(session, null, null, !session.isAutoReconnect()));
            button(context, x + third + gap, y2, third, BTN_H, "Авто-логин", hasPass ? GREEN : 0, !isMain,
                    mouseX, mouseY, () -> openInline(session, InlineMode.PASSWORD));
            button(context, x + (third + gap) * 2f, y2, third, BTN_H, "Сменить IP", 0, !isMain,
                    mouseX, mouseY, () -> openInline(session, InlineMode.IP));
        }

        float y3 = y2 + BTN_H + gap;
        float half = (w - gap) / 2f;
        button(context, x, y3, half, BTN_H, "Экран бота", view == ViewMode.SCREEN ? GREEN : 0, true, mouseX, mouseY,
                () -> view = view == ViewMode.SCREEN ? ViewMode.INVENTORY : ViewMode.SCREEN);
        button(context, x + half + gap, y3, half, BTN_H, "Чат бота", view == ViewMode.CHAT ? GREEN : 0, true, mouseX, mouseY,
                () -> view = view == ViewMode.CHAT ? ViewMode.INVENTORY : ViewMode.CHAT);
    }

    private void renderAddForm(GuiGraphicsExtractor context, float x, float y, float w, int mouseX, int mouseY) {
        BotSessionManager manager = BotSessionManager.getInstance();
        float ix = x + 10f;
        float iw = Math.min(w - 20f, 300f);
        float cy = y + 9f;
        float fh = 18f;

        Render2DHelper.drawScaledText(context, "Новый бот", ix, cy, SystemTheme.foreground(), 0.96f, false);
        button(context, x + w - 10f - BTN_H, cy - 2f, BTN_H, BTN_H, "X", 0, true, mouseX, mouseY, () -> adding = false);
        cy += 22f;

        Render2DHelper.drawScaledText(context, "Ник", ix, cy, SystemTheme.mutedFg(), 0.68f, false);
        cy += 10f;
        field(context, nickField, ix, cy, iw - fh - 4f, fh, mouseX, mouseY);
        button(context, ix + iw - fh, cy, fh, fh, "?", 0, true, mouseX, mouseY, this::generateNick);
        cy += fh + 9f;

        Render2DHelper.drawScaledText(context, "IP сервера", ix, cy, SystemTheme.mutedFg(), 0.68f, false);
        cy += 10f;
        field(context, ipField, ix, cy, iw, fh, mouseX, mouseY);
        cy += fh + 9f;

        Render2DHelper.drawScaledText(context, "Пароль для авто /login — необязательно", ix, cy, SystemTheme.mutedFg(), 0.68f, false);
        cy += 10f;
        field(context, passField, ix, cy, iw, fh, mouseX, mouseY);
        cy += fh + 12f;

        boolean full = manager.getAllBots().size() >= manager.getMaxBots();
        button(context, ix, cy, 110f, fh, full ? "Лимит " + manager.getMaxBots() : "+ Добавить", GREEN, !full,
                mouseX, mouseY, this::addBot);
        Render2DHelper.drawScaledText(context, "Enter — добавить  ·  Tab — следующее поле", ix + 118f, cy + 5.5f,
                SystemTheme.mutedFg(), 0.65f, false);
    }

    private void renderChatBar(GuiGraphicsExtractor context, float x, float y, float w, float h, int mouseX, int mouseY) {
        float allW = 92f;
        float sendW = 24f;
        field(context, chatField, x, y, w - allW - sendW - 8f, h, mouseX, mouseY);
        button(context, x + w - allW - sendW - 4f, y, sendW, h, ">", 0, !chatField.text.isEmpty(), mouseX, mouseY, this::sendChatBar);
        button(context, x + w - allW, y, allW, h, "от всех ботов", chatToAll ? GREEN : 0, true, mouseX, mouseY,
                () -> chatToAll = !chatToAll);
    }

    private void button(GuiGraphicsExtractor context, float x, float y, float w, float h, String label, int fill, boolean enabled,
                        int mouseX, int mouseY, Runnable action) {
        boolean hov = enabled && isHovered(mouseX, mouseY, x, y, w, h) && mouseY >= clipTop && mouseY <= clipBottom;
        int bg;
        if (!enabled) {
            bg = SystemTheme.fade(SystemTheme.elevated(), 0.35f);
        } else if (fill != 0) {
            bg = hov ? ColorUtils.interpolateColor(fill, 0xFFFFFFFF, 0.14f) : fill;
        } else {
            bg = SystemTheme.fade(hov ? SystemTheme.elevated() : SystemTheme.bg(), 0.85f);
        }
        Render2DHelper.drawRoundedRectFloat(context, x, y, w, h, 4f, bg);
        Render2DHelper.drawRoundedOutlineFloat(context, x, y, w, h, 4f, 0.7f, SystemTheme.fade(SystemTheme.border(), 0.65f));
        float scale = h >= 18f ? 0.74f : 0.68f;
        String shown = Render2DHelper.trimToWidth(label, scale, w - 6f);
        float tw = Render2DHelper.getTextWidth(shown, scale);
        Render2DHelper.drawScaledText(context, shown, x + (w - tw) * 0.5f, y + (h - 7f) * 0.5f,
                enabled ? SystemTheme.foreground() : SystemTheme.mutedFg(), scale, false);
        if (enabled) hit(x, y, w, h, action);
    }

    private void field(GuiGraphicsExtractor context, Field field, float x, float y, float w, float h, int mouseX, int mouseY) {
        boolean focus = focused == field;
        boolean hov = isHovered(mouseX, mouseY, x, y, w, h);
        int accent = ThemeManager.getInstance().getAccentColor(0);
        Render2DHelper.drawRoundedRectFloat(context, x, y, w, h, 4f,
                SystemTheme.fade(focus ? SystemTheme.elevated() : (hov ? SystemTheme.surface() : SystemTheme.bg()), 0.9f));
        Render2DHelper.drawRoundedOutlineFloat(context, x, y, w, h, 4f, 0.85f,
                focus ? accent : SystemTheme.fade(SystemTheme.border(), 0.7f));

        float scale = h >= 18f ? 0.74f : 0.68f;
        boolean empty = field.text.isEmpty();
        String shown = empty ? (focus ? "" : field.placeholder) : field.display();
        while (!shown.isEmpty() && Render2DHelper.getTextWidth(shown, scale) > w - 12f) {
            shown = shown.substring(1);
        }
        Render2DHelper.drawScaledText(context, shown, x + 6f, y + (h - 7f) * 0.5f,
                empty ? SystemTheme.mutedFg() : SystemTheme.foreground(), scale, false);
        if (focus && (System.currentTimeMillis() / 450L) % 2 == 0) {
            float textW = empty ? 0f : Render2DHelper.getTextWidth(shown, scale);
            Render2DHelper.drawRect(context, x + 6f + textW + 1f, y + 3f, 1f, h - 6f, SystemTheme.foreground());
        }
        hit(x, y, w, h, () -> focused = field);
    }

    private void hit(float x, float y, float w, float h, Runnable action) {
        float top = Math.max(y, clipTop);
        float bottom = Math.min(y + h, clipBottom);
        if (bottom <= top) return;
        hits.add(new Hit(x, top, w, bottom - top, action));
    }

    private void toggleAddForm() {
        adding = !adding;
        closeInline();
        if (adding) {
            if (ipField.text.isEmpty()) {
                ServerData server = mc.getCurrentServer();
                if (server != null && server.ip != null) ipField.set(server.ip);
            }
            focused = nickField;
        }
    }

    private void addBot() {
        BotSessionManager manager = BotSessionManager.getInstance();
        String nick = nickField.text.toString().trim();
        String ip = ipField.text.toString().trim();
        if (!BotSessionManager.isValidNick(nick)) {
            setStatus("Ник: 3–16 символов, латиница, цифры и _", true);
            focused = nickField;
            return;
        }
        if (ip.isEmpty()) {
            setStatus("Укажите IP сервера", true);
            focused = ipField;
            return;
        }
        if (manager.getBot(nick) != null) {
            setStatus("Бот " + nick + " уже есть", true);
            return;
        }
        if (manager.getAllBots().size() >= manager.getMaxBots()) {
            setStatus("Достигнут лимит ботов (" + manager.getMaxBots() + ")", true);
            return;
        }
        if (manager.createBot(nick, ip, passField.text.toString(), false)) {
            setStatus("Бот " + nick + " заходит на " + ip, false);
            nickField.set("");
            focused = null;
            adding = false;
            selected = manager.getBot(nick);
        } else {
            setStatus("Не удалось создать бота", true);
        }
    }

    private void removeBot(BotSession session) {
        if (session == null || session == BotSessionManager.getInstance().getMainSession()) return;
        BotSessionManager.getInstance().removeBot(session.getName());
        if (inlineTarget == session) closeInline();
        selected = null;
        setStatus("Бот " + session.getName() + " удалён", false);
    }

    private void generateNick() {
        String prefix = NICK_PREFIXES[ThreadLocalRandom.current().nextInt(NICK_PREFIXES.length)];
        nickField.set(prefix + "_" + ThreadLocalRandom.current().nextInt(100, 9999));
        focused = nickField;
    }

    private void sendChatBar() {
        BotSessionManager manager = BotSessionManager.getInstance();
        String text = chatField.text.toString().trim();
        focused = chatField;
        if (text.isEmpty()) return;

        if (chatToAll) {
            int sent = 0;
            for (BotSession bot : manager.getAllBots()) {
                if (!bot.isActive()) continue;
                manager.sendChat(bot, text);
                sent++;
            }
            if (sent == 0) {
                setStatus("Нет ботов онлайн", true);
                return;
            }
            setStatus("Отправлено от " + sent + " ботов", false);
        } else {
            BotSession target = selected;
            boolean online = target == manager.getMainSession() ? mainOnline() : target != null && target.isActive();
            if (!online) {
                setStatus(target == null ? "Бот не выбран" : displayName(target) + " не в сети", true);
                return;
            }
            manager.sendChat(target, text);
        }
        chatField.set("");
    }

    private boolean mainOnline() {
        return BotSessionManager.getInstance().getMainSession().isActive();
    }

    private void openInline(BotSession session, InlineMode mode) {
        inlineTarget = session;
        inlineMode = mode;
        switch (mode) {
            case IP -> {
                inlineField.placeholder = "IP сервера";
                inlineField.masked = false;
                inlineField.set(session.getServerAddress() == null ? "" : session.getServerAddress());
            }
            case PASSWORD -> {
                inlineField.placeholder = "Пароль (пусто — без авто-логина)";
                inlineField.masked = true;
                inlineField.set(session.getPassword() == null ? "" : session.getPassword());
            }
        }
        focused = inlineField;
    }

    private void closeInline() {
        if (focused == inlineField) focused = null;
        inlineTarget = null;
        inlineMode = null;
        inlineField.set("");
    }

    private void submitInline() {
        BotSession target = inlineTarget;
        if (target == null) return;
        BotSessionManager manager = BotSessionManager.getInstance();
        String value = inlineField.text.toString().trim();
        switch (inlineMode) {
            case IP -> {
                if (value.isEmpty() || value.contains(" ")) {
                    setStatus("Некорректный IP", true);
                    focused = inlineField;
                    return;
                }
                manager.updateBot(target, value, null, target.isAutoReconnect());
                setStatus("IP бота " + target.getName() + ": " + value, false);
            }
            case PASSWORD -> {
                manager.updateBot(target, null, value, target.isAutoReconnect());
                setStatus(value.isEmpty() ? "Авто-логин выключен" : "Пароль сохранён", false);
            }
        }
        closeInline();
    }

    private void setStatus(String text, boolean error) {
        status = text;
        statusError = error;
        statusAtMs = System.currentTimeMillis();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        if (ClickGuiScreen.normalizeMouseButton(click.button()) != 0) return super.mouseClicked(click, doubled);
        double mx = click.x();
        double my = click.y();
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit hit = hits.get(i);
            if (isHovered(mx, my, hit.x, hit.y, hit.w, hit.h)) {
                focused = null;
                hit.action.run();
                return true;
            }
        }
        focused = null;
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (isHovered(mouseX, mouseY, listX, listY, listW, listH)) {
            targetScroll = Mth.clamp(targetScroll + (float) (verticalAmount * 30.0), maxScroll, 0f);
        }
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent input) {
        int key = input.key();
        boolean ctrl = input.hasControlDownWithQuirk();

        if (focused != null) {
            switch (key) {
                case InputConstants.KEY_ESCAPE -> {
                    if (focused == inlineField) closeInline();
                    focused = null;
                }
                case InputConstants.KEY_RETURN, InputConstants.KEY_NUMPADENTER -> {
                    if (focused == inlineField) submitInline();
                    else if (focused == chatField) sendChatBar();
                    else addBot();
                }
                case InputConstants.KEY_TAB -> {
                    if (focused == nickField) focused = ipField;
                    else if (focused == ipField) focused = passField;
                    else if (focused == passField) focused = nickField;
                }
                case InputConstants.KEY_BACKSPACE -> {
                    if (ctrl) focused.set("");
                    else focused.backspace();
                }
                case InputConstants.KEY_V -> {
                    if (ctrl) {
                        String clip = mc.keyboardHandler != null ? mc.keyboardHandler.getClipboard() : null;
                        if (clip != null) focused.type(clip.trim());
                    }
                }
                default -> {
                }
            }
            return true;
        }

        if (key == InputConstants.KEY_ESCAPE) {
            if (adding) adding = false;
            else onClose();
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean charTyped(CharacterEvent input) {
        if (focused != null) {
            focused.type(input.codepointAsString());
            return true;
        }
        return super.charTyped(input);
    }

    private static String displayName(BotSession session) {
        return session == BotSessionManager.getInstance().getMainSession() ? mc.getUser().getName() : session.getName();
    }

    private static String listSubtitle(BotSession session) {
        BotSessionManager manager = BotSessionManager.getInstance();
        if (manager.isForeground(session)) return "Ты играешь";
        if (session == manager.getMainSession()) return "основной аккаунт";
        return stateText(session);
    }

    private static String serverOf(BotSession session) {
        BotSessionManager manager = BotSessionManager.getInstance();
        if (session != manager.getMainSession()) {
            String address = session.getServerAddress();
            return address == null || address.isBlank() ? "—" : address;
        }
        if (mc.getCurrentServer() != null) return mc.getCurrentServer().ip;
        return mc.level != null && manager.isForeground(session) ? "одиночный мир" : "—";
    }

    private static int stateColor(BotSession session) {
        BotSessionManager manager = BotSessionManager.getInstance();
        if (session == manager.getMainSession()) {
            return mc.level != null || session.isActive() ? GREEN : 0xFF696973;
        }
        return switch (session.getState()) {
            case CONNECTED -> GREEN;
            case CONNECTING -> YELLOW;
            case ERROR -> RED;
            default -> 0xFF696973;
        };
    }

    private static String stateText(BotSession session) {
        return switch (session.getState()) {
            case CONNECTED -> session.isPlayable() ? "онлайн " + formatUptime(session.getUptime()) : "загрузка";
            case CONNECTING -> "подключение";
            case ERROR -> "ошибка";
            default -> "оффлайн";
        };
    }

    private static String formatUptime(long ms) {
        long seconds = ms / 1000L;
        long hours = seconds / 3600L;
        long minutes = (seconds / 60L) % 60L;
        return hours > 0 ? String.format("%d:%02d:%02d", hours, minutes, seconds % 60L)
                : String.format("%d:%02d", minutes, seconds % 60L);
    }

    private static int ping(LocalPlayer player) {
        if (player.connection == null) return 0;
        PlayerInfo info = player.connection.getPlayerInfo(player.getUUID());
        return info != null ? info.getLatency() : 0;
    }

    private static boolean isHovered(double mx, double my, float x, float y, float w, float h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    private record Hit(float x, float y, float w, float h, Runnable action) {
    }

    private record ItemDraw(ItemStack stack, int x, int y) {
    }

    private enum InlineMode {
        IP, PASSWORD
    }

    private enum ViewMode {
        INVENTORY, SCREEN, CHAT
    }

    private static final class Field {
        private final StringBuilder text = new StringBuilder();
        private final int maxLength;
        private final IntPredicate filter;
        private String placeholder;
        private boolean masked;

        private Field(String placeholder, int maxLength, IntPredicate filter, boolean masked) {
            this.placeholder = placeholder;
            this.maxLength = maxLength;
            this.filter = filter;
            this.masked = masked;
        }

        private void type(String input) {
            if (input == null) return;
            input.codePoints().forEach(c -> {
                if (text.length() < maxLength && filter.test(c)) {
                    text.appendCodePoint(c);
                }
            });
        }

        private void backspace() {
            if (!text.isEmpty()) text.deleteCharAt(text.length() - 1);
        }

        private void set(String value) {
            text.setLength(0);
            type(value);
        }

        private String display() {
            return masked ? "*".repeat(text.length()) : text.toString();
        }
    }
}
