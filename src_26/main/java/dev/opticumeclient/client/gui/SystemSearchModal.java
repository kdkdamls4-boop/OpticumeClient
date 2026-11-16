package dev.opticumeclient.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.opticumeclient.client.modules.Category;
import dev.opticumeclient.client.modules.Module;
import dev.opticumeclient.client.modules.ModuleManager;
import dev.opticumeclient.client.render.Render2DHelper;
import dev.opticumeclient.client.render.TextureAssets;
import dev.opticumeclient.client.setting.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Spotlight search overlay ported from System's {@code SearchBar}.
 * Matches categories, modules, and individual settings; keyboard (Up/Down/Enter/Esc) and mouse ready.
 */
public final class SystemSearchModal {
    public record SearchResult(String title, String subtitle, Category category, Module module, boolean settingMatch) {
    }

    private static final float MODAL_W = 350.0f;
    private static final float MODAL_H = 248.0f;
    private static final float INPUT_H = 32.0f;
    private static final float ROW_H = 26.0f;

    private boolean open = false;
    private float openAnim = 0.0f;
    private String query = "";
    private int selectedIndex = 0;
    private float scroll = 0.0f;
    private float maxScroll = 0.0f;
    private final List<SearchResult> results = new ArrayList<>();

    public SystemSearchModal() {
        rebuildResults();
    }

    public boolean isOpen() {
        return open;
    }

    public void open() {
        this.open = true;
        this.query = "";
        this.selectedIndex = 0;
        this.scroll = 0.0f;
        rebuildResults();
    }

    public void close() {
        this.open = false;
    }

    public float getOpenAnim() {
        return openAnim;
    }

    public void update(float dt) {
        float target = open ? 1.0f : 0.0f;
        openAnim += (target - openAnim) * (1.0f - (float) Math.exp(-16.0f * dt));
        if (Math.abs(target - openAnim) < 0.002f) openAnim = target;
    }

    public void render(GuiGraphicsExtractor context, float winX, float winY, float winW, float winH,
                       int screenW, int screenH, int mouseX, int mouseY) {
        if (openAnim <= 0.01f) return;

        float a = Mth.clamp(openAnim, 0.0f, 1.0f);
        // Backdrop dim over entire ClickGUI window
        Render2DHelper.drawRoundedRect(context, winX, winY, winW, winH, 10.0f,
                SystemTheme.fade(0xFF000000, a * 0.55f));

        float mx = (screenW - MODAL_W) * 0.5f;
        float my = (screenH - MODAL_H) * 0.5f + (1.0f - a) * 10.0f;

        Render2DHelper.drawGlow(context, mx, my + 2.0f, MODAL_W, MODAL_H, 10.0f,
                SystemTheme.fade(0xFF000000, a * 0.65f));
        Render2DHelper.drawRoundedRect(context, mx, my, MODAL_W, MODAL_H, 10.0f,
                SystemTheme.fade(SystemTheme.bg(), a));
        Render2DHelper.drawRoundedOutline(context, mx, my, MODAL_W, MODAL_H, 10.0f, 1.0f,
                SystemTheme.fade(SystemTheme.border(), a));

        // Top search bar
        Render2DHelper.drawTexture(context, TextureAssets.ICON_SEARCH, mx + 12.0f, my + 10.0f, 12.0f, 12.0f,
                SystemTheme.fade(SystemTheme.mutedFg(), a));

        Font font = Minecraft.getInstance().font;
        String display = query.isEmpty() ? "Search modules, categories or settings..." : query;
        String caret = open && (System.currentTimeMillis() / 500L) % 2L == 0 ? "_" : "";
        int textCol = query.isEmpty() ? SystemTheme.mutedFg() : SystemTheme.foreground();
        Render2DHelper.drawScaledText(context,
                SystemModuleCard.trimToWidth(font, query.isEmpty() ? display : display + caret, 0.90f, MODAL_W - 64.0f),
                mx + 31.0f, my + 12.0f, SystemTheme.fade(textCol, a), 0.90f, false);

        Render2DHelper.drawRect(context, mx, my + INPUT_H, MODAL_W, 1.0f, SystemTheme.fade(SystemTheme.border(), a));

        // Results list
        float listX = mx + 6.0f;
        float listY = my + INPUT_H + 5.0f;
        float listW = MODAL_W - 12.0f;
        float listH = MODAL_H - INPUT_H - 22.0f;
        float totalH = results.size() * ROW_H;
        maxScroll = Math.max(0.0f, totalH - listH);
        scroll = Mth.clamp(scroll, 0.0f, maxScroll);

        context.enableScissor(Math.round(listX), Math.round(listY), Math.round(listX + listW), Math.round(listY + listH));
        if (results.isEmpty()) {
            Render2DHelper.drawCenteredText(context, "No matches for \"" + query + "\"",
                    mx + MODAL_W * 0.5f, listY + listH * 0.42f, SystemTheme.fade(SystemTheme.mutedFg(), a), false);
        } else {
            for (int i = 0; i < results.size(); i++) {
                float ry = listY + i * ROW_H - scroll;
                if (ry + ROW_H < listY || ry > listY + listH) continue;
                SearchResult r = results.get(i);
                boolean hov = open && mouseX >= listX && mouseX <= listX + listW && mouseY >= ry && mouseY <= ry + ROW_H;
                boolean sel = (i == selectedIndex) || hov;
                if (sel) {
                    Render2DHelper.drawRoundedRect(context, listX, ry + 1.0f, listW, ROW_H - 2.0f, 6.0f,
                            SystemTheme.fade(SystemTheme.elevated(), a));
                }

                // Title & subtitle
                Render2DHelper.drawScaledText(context,
                        SystemModuleCard.trimToWidth(font, r.title(), 0.86f, listW - 84.0f),
                        listX + 8.0f, ry + 5.0f, SystemTheme.fade(SystemTheme.foreground(), a), 0.86f, false);
                Render2DHelper.drawScaledText(context,
                        SystemModuleCard.trimToWidth(font, r.subtitle(), 0.70f, listW - 84.0f),
                        listX + 8.0f, ry + 15.0f, SystemTheme.fade(SystemTheme.mutedFg(), a), 0.70f, false);

                // Category badge on right
                String tag = r.category() != null ? r.category().getDisplayName() : "Category";
                float tagScale = 0.68f;
                float tw = font.width(tag) * tagScale + 8.0f;
                float tx = listX + listW - tw - 6.0f;
                Render2DHelper.drawRoundedRect(context, tx, ry + 6.5f, tw, 13.0f, 4.0f,
                        SystemTheme.fade(SystemTheme.surface(), a));
                Render2DHelper.drawScaledText(context, tag, tx + 4.0f, ry + 9.5f,
                        SystemTheme.fade(r.module() != null && r.module().isEnabled() ? SystemTheme.SUCCESS : SystemTheme.mutedFg(), a),
                        tagScale, false);
            }
        }
        context.disableScissor();

        // Bottom hint bar
        Render2DHelper.drawScaledText(context, "↑↓ Navigate   •   Enter Jump   •   Esc Close",
                mx + 12.0f, my + MODAL_H - 12.0f, SystemTheme.fade(SystemTheme.mutedFg(), a * 0.85f), 0.70f, false);
    }

    public boolean mouseClicked(double mx, double my, int screenW, int screenH, ClickGuiScreen host) {
        if (!open) return false;
        float modX = (screenW - MODAL_W) * 0.5f;
        float modY = (screenH - MODAL_H) * 0.5f;
        if (mx < modX || mx > modX + MODAL_W || my < modY || my > modY + MODAL_H) {
            close();
            return true;
        }
        float listX = modX + 6.0f;
        float listY = modY + INPUT_H + 5.0f;
        float listW = MODAL_W - 12.0f;
        float listH = MODAL_H - INPUT_H - 22.0f;
        if (mx >= listX && mx <= listX + listW && my >= listY && my <= listY + listH) {
            int idx = (int) ((my - listY + scroll) / ROW_H);
            if (idx >= 0 && idx < results.size()) {
                selectedIndex = idx;
                activateSelected(host);
            }
        }
        return true;
    }

    public boolean mouseScrolled(double verticalAmount) {
        if (!open) return false;
        scroll = Mth.clamp(scroll - (float) (verticalAmount * 22.0f), 0.0f, maxScroll);
        return true;
    }

    public boolean keyPressed(int key, ClickGuiScreen host) {
        if (!open) return false;
        if (key == InputConstants.KEY_ESCAPE) {
            close();
            return true;
        }
        if (key == InputConstants.KEY_BACKSPACE) {
            if (!query.isEmpty()) {
                query = query.substring(0, query.length() - 1);
                rebuildResults();
            }
            return true;
        }
        if (key == InputConstants.KEY_UP && !results.isEmpty()) {
            selectedIndex = (selectedIndex - 1 + results.size()) % results.size();
            ensureSelectionVisible();
            return true;
        }
        if (key == InputConstants.KEY_DOWN && !results.isEmpty()) {
            selectedIndex = (selectedIndex + 1) % results.size();
            ensureSelectionVisible();
            return true;
        }
        if (key == InputConstants.KEY_RETURN) {
            activateSelected(host);
            return true;
        }
        return true;
    }

    public boolean charTyped(String ch) {
        if (!open) return false;
        if (query.length() < 32) {
            query += ch;
            rebuildResults();
        }
        return true;
    }

    private void ensureSelectionVisible() {
        float listH = MODAL_H - INPUT_H - 22.0f;
        float rowTop = selectedIndex * ROW_H;
        float rowBot = rowTop + ROW_H;
        if (rowTop < scroll) scroll = rowTop;
        else if (rowBot > scroll + listH) scroll = rowBot - listH;
        scroll = Mth.clamp(scroll, 0.0f, maxScroll);
    }

    private void activateSelected(ClickGuiScreen host) {
        if (results.isEmpty() || selectedIndex < 0 || selectedIndex >= results.size()) return;
        SearchResult res = results.get(selectedIndex);
        close();
        host.jumpToSearchResult(res);
    }

    private void rebuildResults() {
        results.clear();
        String q = query.trim().toLowerCase(Locale.ROOT);
        for (Category c : Category.values()) {
            if (q.isEmpty() || c.getDisplayName().toLowerCase(Locale.ROOT).contains(q)) {
                results.add(new SearchResult(c.getDisplayName(), "Open category", c, null, false));
            }
        }
        for (Module m : ModuleManager.getInstance().getModules()) {
            String nameLow = m.getName().toLowerCase(Locale.ROOT);
            String descLow = m.getDescription() != null ? m.getDescription().toLowerCase(Locale.ROOT) : "";
            if (q.isEmpty() || nameLow.contains(q) || descLow.contains(q)) {
                String sub = m.getDescription() != null && !m.getDescription().isBlank() ? m.getDescription() : m.getCategory().getDisplayName();
                results.add(new SearchResult(m.getName(), sub, m.getCategory(), m, false));
            } else {
                for (Setting<?> s : m.getSettings()) {
                    if (s.getName().toLowerCase(Locale.ROOT).contains(q)) {
                        results.add(new SearchResult(s.getName(), "Setting in " + m.getName(), m.getCategory(), m, true));
                    }
                }
            }
        }
        selectedIndex = 0;
        scroll = 0.0f;
    }
}
