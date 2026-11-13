package dev.optimusprime.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.optimusprime.client.config.ConfigManager;
import dev.optimusprime.client.gui.component.BindComponent;
import dev.optimusprime.client.modules.Category;
import dev.optimusprime.client.modules.Module;
import dev.optimusprime.client.modules.ModuleManager;
import dev.optimusprime.client.render.Animation;
import dev.optimusprime.client.render.ColorUtils;
import dev.optimusprime.client.render.Easing;
import dev.optimusprime.client.render.Render2DHelper;
import dev.optimusprime.client.render.TextureAssets;
import dev.optimusprime.client.render.Theme;
import dev.optimusprime.client.render.ThemeManager;
import dev.optimusprime.client.setting.BooleanSetting;
import dev.optimusprime.client.setting.ColorSetting;
import dev.optimusprime.client.setting.ModeSetting;
import dev.optimusprime.client.setting.MultiBoxSetting;
import dev.optimusprime.client.setting.NumberSetting;
import dev.optimusprime.client.setting.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import org.joml.Matrix3x2fStack;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * OpticumeClient Nextgen Polish ClickGUI (Minecraft 26.3 / Java 25 / Vulkan 1.4):
 *
 * Mode 1: Sidebar Window Mode (660x430 centered liquid glass window):
 *   - Category sidebar with floating dark glass tab (accent fill + 1px outline + 3px glowing neon pill)
 *   - Header Bar with clean modern buttons without brackets ("Layout: Window", "Bound Only")
 *   - Search box with ⌕ icon and clean placeholder
 *   - 2-Column Masonry Grid with 42px module cards:
 *       * Subtle theme tint and glowing 3px left indicator bar when enabled
 *       * Module title + colored preset/suffix badge
 *       * Smooth description text clamped accurately to available width
 *       * 24x12 switch pill with 8x8 white knob
 *       * Keybind badge only shown when bound, binding, or hovered
 *       * Interactive Mode selector row with ‹ CurrentMode › cycling LMB/RMB + Preset glow
 *       * Full-width 4px gradient slider with 8x8 white thumb and dragging support
 *       * Sleek 18x9 mini toggle switch for booleans
 *       * Accurate maxScroll calculation from tallest column and sleek 3px scrollbar
 *
 * Mode 2: Dropdown Panels Mode (6 draggable floating columns).
 */
public class ClickGuiScreen extends Screen {
    public static final float WINDOW_WIDTH = 660.0f;
    public static final float WINDOW_HEIGHT = 430.0f;
    public static final float SIDEBAR_WIDTH = 155.0f;
    public static final float CONTENT_WIDTH = WINDOW_WIDTH - SIDEBAR_WIDTH;
    public static final float HEADER_HEIGHT = 44.0f;
    public static final float CARD_WIDTH = 233.0f;
    public static final float CARD_HEADER_HEIGHT = 48.0f;

    public static final float PANEL_WIDTH = 152.0f;
    public static final float PANEL_HEADER_HEIGHT = 22.0f;
    public static final float PANEL_MAX_BODY_HEIGHT = 310.0f;

    // Persistent static state across GUI openings
    private static boolean dropdownPanelsMode = false;
    private static Category persistentCategory = Category.COMBAT;
    private static final Map<Category, DropdownPanelState> panelStates = new EnumMap<>(Category.class);

    private static class DropdownPanelState {
        float x;
        float y;
        boolean open = true;
        float targetScroll = 0.0f;
        float currentScroll = 0.0f;

        DropdownPanelState(float x, float y) {
            this.x = x;
            this.y = y;
        }
    }

    static {
        float startX = 18.0f;
        float gap = 8.0f;
        for (Category cat : Category.values()) {
            panelStates.put(cat, new DropdownPanelState(startX, 34.0f));
            startX += PANEL_WIDTH + gap;
        }
    }

    private Category selectedCategory = persistentCategory;
    private String searchQuery = "";
    private boolean searchFocused = false;
    private boolean bindOnlyFilter = false;
    private boolean themePopupOpen = false;
    private boolean configsViewOpen = false;
    private String statusToast = "";
    private long statusToastTime = 0L;

    private float targetScroll = 0.0f;
    private float currentScroll = 0.0f;
    private float maxScroll = 0.0f;

    private final Animation openScaleAnim = new Animation(0.82f, 260L, Easing.EASE_OUT_BACK);
    private final Animation openAlphaAnim = new Animation(0.0f, 190L, Easing.EASE_OUT_CUBIC);
    private final Animation activeTabYAnim;
    private final Map<Category, Animation> categoryHoverAnims = new EnumMap<>(Category.class);
    private final Map<Module, Animation> cardHoverAnims = new HashMap<>();

    // Interactive state for sliders and keybinding
    private Module bindingModule = null;
    private NumberSetting draggingSlider = null;
    private float draggingSliderTrackX = 0.0f;
    private float draggingSliderTrackW = 0.0f;

    // Floating tooltip state tracking
    private String hoveredTooltipTitle = null;
    private String hoveredTooltipDesc = null;

    // Layout cache for hit detection
    private final Map<Module, CardBox> cardBoxes = new HashMap<>();
    private final List<DropdownPanel> dropdownPanels = new ArrayList<>();

    public ClickGuiScreen() {
        super(net.minecraft.network.chat.Component.nullToEmpty("OpticumeClient ClickGUI"));

        openScaleAnim.animateTo(1.0f, 260L, Easing.EASE_OUT_BACK);
        openAlphaAnim.animateTo(1.0f, 190L, Easing.EASE_OUT_CUBIC);

        int catIdx = selectedCategory.ordinal();
        this.activeTabYAnim = new Animation(catIdx * 31.0f, 200L, Easing.EASE_OUT_CUBIC);

        for (Category cat : Category.values()) {
            categoryHoverAnims.put(cat, new Animation(0.0f, 150L, Easing.EASE_OUT_QUAD));
        }

        for (Category cat : Category.values()) {
            dropdownPanels.add(new DropdownPanel(cat, panelStates.get(cat)));
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /**
     * SDL3 mouse buttons in Minecraft 26.x:
     * 1=LMB, 2=MMB, 3=RMB -> Minecraft legacy indices: 0=LMB, 1=RMB, 2=MMB.
     */
    public static int normalizeMouseButton(int rawButton) {
        if (rawButton == 1 || rawButton == InputConstants.MOUSE_BUTTON_LEFT) return 0; // LMB
        if (rawButton == 3 || rawButton == InputConstants.MOUSE_BUTTON_RIGHT) return 1; // RMB
        if (rawButton == 2 || rawButton == InputConstants.MOUSE_BUTTON_MIDDLE) return 2; // MMB
        return rawButton;
    }

    private static int toLegacyButton(int button) {
        return normalizeMouseButton(button);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        TextureAssets.ensureInitialized();

        // Reset tooltip tracking for current frame
        hoveredTooltipTitle = null;
        hoveredTooltipDesc = null;

        float alpha = openAlphaAnim.getValue();
        float scale = openScaleAnim.getValue();
        Theme theme = ThemeManager.getInstance().getCurrentTheme();

        // 1. Ambient Dark Translucent Backdrop with subtle top theme vignette
        int bgDim = ColorUtils.withAlpha(0xFF06070B, (int) (145 * alpha));
        Render2DHelper.drawRect(context, 0, 0, this.width, this.height, bgDim);
        Render2DHelper.drawGradientRect(context, 0, 0, this.width, 90,
                ColorUtils.withAlpha(theme.getPrimaryColor(), (int) (26 * alpha)), 0x00000000);

        // 2. Render active GUI mode
        if (!dropdownPanelsMode) {
            renderSidebarWindowMode(context, mouseX, mouseY, delta, scale, alpha);
        } else {
            renderDropdownPanelsMode(context, mouseX, mouseY, delta, scale, alpha);
        }

        // 3. Bottom Hint Bar
        String hint = "LMB: Toggle | RMB: Settings | MMB: Bind | Drag HUD elements directly when Chat or ClickGUI is open";
        float hintScale = 0.74f;
        float hintW = Render2DHelper.getTextWidth(hint, hintScale);
        float pillW = hintW + 18.0f;
        float pillH = 16.0f;
        float pillX = (this.width - pillW) * 0.5f;
        float pillY = this.height - 20.0f;

        Render2DHelper.drawRoundedRect(context, pillX, pillY, pillW, pillH, 4.0f,
                ColorUtils.withAlpha(0xFF0B0D14, (int) (190 * alpha)));
        Render2DHelper.drawRoundedOutline(context, pillX, pillY, pillW, pillH, 4.0f, 1.0f,
                ColorUtils.withAlpha(theme.getPrimaryColor(), (int) (80 * alpha)));
        Render2DHelper.drawScaledText(context, hint, pillX + 9.0f, pillY + 4.5f,
                ColorUtils.withAlpha(theme.getMutedTextColor(), (int) (235 * alpha)), hintScale, false);

        // 4. Floating Liquid Glass Tooltip (topmost layer, outside scissor clipping)
        if (draggingSlider == null) {
            renderFloatingTooltip(context, mouseX, mouseY);
        }

        super.extractRenderState(context, mouseX, mouseY, delta);
    }

    // =================================================================================
    // MODE 1: SIDEBAR WINDOW (660x430 Nextgen Polish)
    // =================================================================================
    private void renderSidebarWindowMode(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta, float scale, float alpha) {
        Theme theme = ThemeManager.getInstance().getCurrentTheme();
        float winX = (this.width - WINDOW_WIDTH) * 0.5f;
        float winY = (this.height - WINDOW_HEIGHT) * 0.5f;

        Matrix3x2fStack matrices = context.pose();
        float centerX = winX + WINDOW_WIDTH * 0.5f;
        float centerY = winY + WINDOW_HEIGHT * 0.5f;

        matrices.pushMatrix();
        matrices.translate(centerX, centerY);
        matrices.scale(scale, scale);
        matrices.translate(-centerX, -centerY);

        // Main Liquid Glass Window Frame
        Render2DHelper.drawLiquidGlass(context, winX, winY, WINDOW_WIDTH, WINDOW_HEIGHT, 10.0f,
                (int) (235 * alpha), (int) (50 * alpha));
        Render2DHelper.drawSpecularBorder(context, winX, winY, WINDOW_WIDTH, WINDOW_HEIGHT, 10.0f,
                mouseX, mouseY, theme.getPrimaryColor());

        // Left Sidebar (155px)
        renderSidebar(context, winX, winY, mouseX, mouseY);

        // Right Content Header & Masonry Grid (or Configs Manager View)
        renderContentHeader(context, winX + SIDEBAR_WIDTH, winY, mouseX, mouseY);
        if (configsViewOpen) {
            renderConfigsManager(context, winX + SIDEBAR_WIDTH, winY + HEADER_HEIGHT, mouseX, mouseY);
        } else {
            renderMasonryGrid(context, winX + SIDEBAR_WIDTH, winY + HEADER_HEIGHT, mouseX, mouseY, delta);
        }

        // Floating 12-Theme Selector Popup
        if (themePopupOpen) {
            renderThemeSelectorPopup(context, winX + 10.0f, winY + WINDOW_HEIGHT - 262.0f, mouseX, mouseY);
        }

        matrices.popMatrix();
    }

    private void renderSidebar(GuiGraphicsExtractor context, float winX, float winY, float mouseX, float mouseY) {
        Theme theme = ThemeManager.getInstance().getCurrentTheme();
        int accent1 = theme.getPrimaryColor();
        int accent2 = theme.getSecondaryColor();

        // Darker sidebar glass backdrop + vertical divider
        Render2DHelper.drawRoundedRect(context, winX, winY, SIDEBAR_WIDTH, WINDOW_HEIGHT, 10.0f, 0x7007080D);
        Render2DHelper.drawRect(context, winX + SIDEBAR_WIDTH - 1.0f, winY + 6.0f, 1.0f, WINDOW_HEIGHT - 12.0f,
                ColorUtils.withAlpha(accent1, 65));

        // Top Client Brand Header
        Render2DHelper.drawTexture(context, TextureAssets.MINI_LOGO, winX + 12.0f, winY + 13.0f, 18.0f, 18.0f, accent1);
        Render2DHelper.drawScaledText(context, "OpticumeClient", winX + 34.0f, winY + 13.0f, accent1, 0.95f, true);
        Render2DHelper.drawScaledText(context, "v1.0.0 • " + net.minecraft.SharedConstants.getCurrentVersion().name(),
                winX + 34.0f, winY + 24.5f, theme.getMutedTextColor(), 0.65f, false);

        // Category List with Floating Dark Glass Animated Pill
        float listStartY = winY + 48.0f;
        float itemH = 26.0f;
        float itemGap = 5.0f;
        float stepY = itemH + itemGap;
        float itemX = winX + 8.0f;
        float itemW = SIDEBAR_WIDTH - 16.0f;

        activeTabYAnim.animateTo(selectedCategory.ordinal() * stepY);
        float pillY = listStartY + activeTabYAnim.getValue();

        // Active category tab: dark glass fill + 1px accent outline + 3px left neon bar
        Render2DHelper.drawRoundedRect(context, itemX, pillY, itemW, itemH, 5.0f, ColorUtils.withAlpha(accent1, 45));
        Render2DHelper.drawRoundedOutline(context, itemX, pillY, itemW, itemH, 5.0f, 1.0f, ColorUtils.withAlpha(accent1, 130));
        Render2DHelper.drawGlow(context, itemX + 1.5f, pillY + 4.0f, 3.0f, itemH - 8.0f, 4.0f, ColorUtils.withAlpha(accent1, 160));
        Render2DHelper.drawRoundedRect(context, itemX + 1.5f, pillY + 4.0f, 3.0f, itemH - 8.0f, 1.5f, accent1);

        Category[] categories = Category.values();
        for (int i = 0; i < categories.length; i++) {
            Category cat = categories[i];
            float cy = listStartY + i * stepY;
            boolean isSelected = (cat == selectedCategory);
            boolean isHovered = mouseX >= itemX && mouseX <= itemX + itemW && mouseY >= cy && mouseY <= cy + itemH;

            Animation hoverAnim = categoryHoverAnims.get(cat);
            if (hoverAnim != null) {
                hoverAnim.animateTo((isHovered && !isSelected) ? 1.0f : 0.0f);
                float hv = hoverAnim.getValue();
                if (hv > 0.01f) {
                    Render2DHelper.drawRoundedRect(context, itemX, cy, itemW, itemH, 5.0f,
                            ColorUtils.withAlpha(0xFFFFFFFF, (int) (18 * hv)));
                }
            }

            String icon = getCategoryIcon(cat);
            int textCol = isSelected ? 0xFFFFFFFF : (isHovered ? theme.getTextColor() : theme.getMutedTextColor());
            Render2DHelper.drawScaledText(context, icon, itemX + 10.0f, cy + 8.0f, textCol, 0.84f, false);
            Render2DHelper.drawScaledText(context, cat.getDisplayName(), itemX + 26.0f, cy + 8.0f, textCol, 0.82f, isSelected);

            // Sleek pill badge on right: enabled/total count (e.g. "2/8" or "6")
            List<Module> catMods = ModuleManager.getInstance().getModulesByCategory(cat);
            int total = catMods.size();
            int enabled = 0;
            for (Module m : catMods) {
                if (m.isEnabled()) enabled++;
            }
            String badgeText = (enabled > 0) ? (enabled + "/" + total) : String.valueOf(total);
            float bScale = 0.64f;
            float bTextW = Render2DHelper.getTextWidth(badgeText, bScale);
            float bPillW = bTextW + 8.0f;
            float bPillH = 13.0f;
            float bPillX = itemX + itemW - bPillW - 6.0f;
            float bPillY = cy + (itemH - bPillH) * 0.5f;

            int bBg = isSelected ? ColorUtils.withAlpha(accent1, 55) : 0x18FFFFFF;
            int bBorder = isSelected ? ColorUtils.withAlpha(accent1, 120) : 0x28FFFFFF;
            Render2DHelper.drawRoundedRect(context, bPillX, bPillY, bPillW, bPillH, 3.5f, bBg);
            Render2DHelper.drawRoundedOutline(context, bPillX, bPillY, bPillW, bPillH, 3.5f, 1.0f, bBorder);

            int bCol = isSelected ? 0xFFFFFFFF : (enabled > 0 ? accent1 : theme.getMutedTextColor());
            Render2DHelper.drawScaledText(context, badgeText, bPillX + 4.0f, bPillY + 3.0f, bCol, bScale, false);
        }

        // Configs Manager Button above Theme Selector Button
        float cfgBtnX = winX + 8.0f;
        float cfgBtnY = winY + WINDOW_HEIGHT - 101.0f;
        float cfgBtnW = SIDEBAR_WIDTH - 16.0f;
        float cfgBtnH = 22.0f;
        boolean cfgHovered = mouseX >= cfgBtnX && mouseX <= cfgBtnX + cfgBtnW
                && mouseY >= cfgBtnY && mouseY <= cfgBtnY + cfgBtnH;

        int cfgBg = configsViewOpen ? ColorUtils.withAlpha(accent1, 60) : (cfgHovered ? 0x35FFFFFF : 0x20FFFFFF);
        int cfgOutline = configsViewOpen ? accent1 : ColorUtils.withAlpha(accent1, cfgHovered ? 180 : 90);
        Render2DHelper.drawRoundedRect(context, cfgBtnX, cfgBtnY, cfgBtnW, cfgBtnH, 4.5f, cfgBg);
        Render2DHelper.drawRoundedOutline(context, cfgBtnX, cfgBtnY, cfgBtnW, cfgBtnH, 4.5f, 1.0f, cfgOutline);
        Render2DHelper.drawScaledText(context, "⚙", cfgBtnX + 8.0f, cfgBtnY + 6.0f, configsViewOpen ? 0xFFFFFFFF : accent2, 0.80f, false);

        String cName = "Config: " + ConfigManager.getInstance().getActiveConfigName();
        if (cName.length() > 19) cName = cName.substring(0, 18) + "..";
        Render2DHelper.drawScaledText(context, cName, cfgBtnX + 22.0f, cfgBtnY + 7.0f,
                configsViewOpen ? 0xFFFFFFFF : theme.getTextColor(), 0.68f, false);

        // 12-Theme Selector Button above Profile Card
        float themeBtnX = winX + 8.0f;
        float themeBtnY = winY + WINDOW_HEIGHT - 74.0f;
        float themeBtnW = SIDEBAR_WIDTH - 16.0f;
        float themeBtnH = 22.0f;
        boolean themeHovered = mouseX >= themeBtnX && mouseX <= themeBtnX + themeBtnW
                && mouseY >= themeBtnY && mouseY <= themeBtnY + themeBtnH;

        Render2DHelper.drawRoundedRect(context, themeBtnX, themeBtnY, themeBtnW, themeBtnH, 4.5f,
                themeHovered ? 0x35FFFFFF : 0x20FFFFFF);
        Render2DHelper.drawRoundedOutline(context, themeBtnX, themeBtnY, themeBtnW, themeBtnH, 4.5f, 1.0f,
                ColorUtils.withAlpha(accent1, themeHovered || themePopupOpen ? 180 : 90));

        Render2DHelper.drawCircle(context, themeBtnX + 10.0f, themeBtnY + 11.0f, 4.0f, accent1);
        Render2DHelper.drawCircle(context, themeBtnX + 16.5f, themeBtnY + 11.0f, 4.0f, accent2);

        String tName = "Theme: " + theme.getName();
        if (tName.length() > 19) tName = tName.substring(0, 18) + "..";
        Render2DHelper.drawScaledText(context, tName, themeBtnX + 25.0f, themeBtnY + 7.0f,
                theme.getTextColor(), 0.68f, false);

        // Player Profile Card at Bottom with real 2D Skin Head
        float profX = winX + 8.0f;
        float profY = winY + WINDOW_HEIGHT - 46.0f;
        float profW = SIDEBAR_WIDTH - 16.0f;
        float profH = 38.0f;

        Render2DHelper.drawRoundedRect(context, profX, profY, profW, profH, 5.5f, 0x22FFFFFF);
        Render2DHelper.drawRoundedOutline(context, profX, profY, profW, profH, 5.5f, 1.0f,
                ColorUtils.withAlpha(accent1, 85));

        Minecraft mc = Minecraft.getInstance();
        int headX = Math.round(profX + 6.0f);
        int headY = Math.round(profY + 6.0f);
        int headSize = 26;

        Render2DHelper.drawGlow(context, headX, headY, headSize, headSize, 3.5f, ColorUtils.withAlpha(accent1, 95));
        if (mc != null && mc.player != null) {
            Render2DHelper.drawPlayerHead(context, mc.player, headX, headY, headSize, 0.0f);
        } else {
            Render2DHelper.drawGradientRoundedRect(context, headX, headY, headSize, headSize, 4.0f, accent1, accent2);
            Render2DHelper.drawCenteredText(context, "OC", headX + headSize * 0.5f, headY + 9.0f, 0xFFFFFFFF, true);
        }
        Render2DHelper.drawRoundedOutline(context, headX - 1.0f, headY - 1.0f, headSize + 2.0f, headSize + 2.0f, 3.0f, 1.0f, accent1);

        String username = (mc != null && mc.getUser() != null) ? mc.getUser().getName() : "OpticumeUser";
        if (username.length() > 12) username = username.substring(0, 11) + "..";
        Render2DHelper.drawScaledText(context, username, profX + 38.0f, profY + 7.0f, theme.getTextColor(), 0.78f, false);
        Render2DHelper.drawScaledText(context, "UID: #0001 | Prime", profX + 38.0f, profY + 18.0f, accent1, 0.62f, false);
        int fps = mc != null ? mc.getFps() : 0;
        Render2DHelper.drawScaledText(context, "FPS: " + fps + " • Nextgen", profX + 38.0f, profY + 27.0f, 0xFF22C55E, 0.60f, false);
    }

    private String getCategoryIcon(Category category) {
        return switch (category) {
            case COMBAT -> "⚔";
            case VISUALS -> "👁";
            case MOVEMENT -> "⚡";
            case HUD -> "🖥";
            case OPTIMIZATION -> "🚀";
            case FRIENDS -> "👥";
        };
    }

    private void renderContentHeader(GuiGraphicsExtractor context, float contentX, float contentY, float mouseX, float mouseY) {
        Theme theme = ThemeManager.getInstance().getCurrentTheme();
        int accent1 = theme.getPrimaryColor();
        float headerY = contentY + 11.0f;
        float headerH = 22.0f;

        // Selected Category Title + Module Count (or Configs Header)
        String title = configsViewOpen ? "Configs & Presets" : selectedCategory.getDisplayName();
        Render2DHelper.drawScaledText(context, title, contentX + 14.0f, headerY + 4.0f, 0xFFFFFFFF, 1.06f, true);
        float titleW = Render2DHelper.getTextWidth(title, 1.06f);

        int count = configsViewOpen ? ConfigManager.getInstance().listConfigs().size() : getFilteredSidebarModules().size();
        Render2DHelper.drawScaledText(context, "(" + count + ")", contentX + 20.0f + titleW, headerY + 6.5f,
                theme.getMutedTextColor(), 0.74f, false);

        // 1. Layout Switcher: "Layout: Window" / "Layout: Panels" (clean, NO square brackets)
        float layoutBtnW = 98.0f;
        float layoutBtnH = headerH;
        float searchW = 132.0f;
        float bindBtnW = 78.0f;
        float layoutBtnX = contentX + CONTENT_WIDTH - searchW - bindBtnW - layoutBtnW - 24.0f;
        boolean layoutHovered = mouseX >= layoutBtnX && mouseX <= layoutBtnX + layoutBtnW
                && mouseY >= headerY && mouseY <= headerY + layoutBtnH;

        Render2DHelper.drawRoundedRect(context, layoutBtnX, headerY, layoutBtnW, layoutBtnH, 4.5f,
                layoutHovered ? 0x2EFFFFFF : 0x16FFFFFF);
        Render2DHelper.drawRoundedOutline(context, layoutBtnX, headerY, layoutBtnW, layoutBtnH, 4.5f, 1.0f,
                layoutHovered ? ColorUtils.withAlpha(accent1, 170) : 0x33FFFFFF);
        Render2DHelper.drawCircle(context, layoutBtnX + 9.0f, headerY + 11.0f, 2.5f, accent1);

        String layoutText = dropdownPanelsMode ? "Layout: Panels" : "Layout: Window";
        Render2DHelper.drawScaledText(context, layoutText, layoutBtnX + 16.0f, headerY + 6.5f,
                layoutHovered ? 0xFFFFFFFF : theme.getTextColor(), 0.67f, false);

        // 2. Bound Only Filter Button (or "+ Save Config" when in Configs View)
        float bindBtnX = contentX + CONTENT_WIDTH - searchW - bindBtnW - 16.0f;
        boolean bindHovered = mouseX >= bindBtnX && mouseX <= bindBtnX + bindBtnW
                && mouseY >= headerY && mouseY <= headerY + layoutBtnH;

        if (configsViewOpen) {
            int saveBg = bindHovered ? ColorUtils.withAlpha(accent1, 120) : ColorUtils.withAlpha(accent1, 65);
            Render2DHelper.drawRoundedRect(context, bindBtnX, headerY, bindBtnW, layoutBtnH, 4.5f, saveBg);
            Render2DHelper.drawRoundedOutline(context, bindBtnX, headerY, bindBtnW, layoutBtnH, 4.5f, 1.0f, accent1);
            Render2DHelper.drawCenteredText(context, "+ Save New", bindBtnX + bindBtnW * 0.5f, headerY + 6.5f, 0xFFFFFFFF, false);
        } else {
            int bindBg = bindOnlyFilter ? ColorUtils.withAlpha(accent1, 70) : (bindHovered ? 0x2AFFFFFF : 0x16FFFFFF);
            int bindOutline = bindOnlyFilter ? accent1 : (bindHovered ? 0x55FFFFFF : 0x33FFFFFF);
            Render2DHelper.drawRoundedRect(context, bindBtnX, headerY, bindBtnW, layoutBtnH, 4.5f, bindBg);
            Render2DHelper.drawRoundedOutline(context, bindBtnX, headerY, bindBtnW, layoutBtnH, 4.5f, 1.0f, bindOutline);
            Render2DHelper.drawCircle(context, bindBtnX + 9.0f, headerY + 11.0f, 2.5f, bindOnlyFilter ? accent1 : 0xFF606D80);

            int bindTextCol = bindOnlyFilter ? 0xFFFFFFFF : (bindHovered ? theme.getTextColor() : theme.getMutedTextColor());
            Render2DHelper.drawScaledText(context, "Bound Only", bindBtnX + 16.0f, headerY + 6.5f, bindTextCol, 0.68f, false);
        }

        // 3. Live Search / Config Name Box: Sleek dark inset pill (0xCC0A0D16)
        float searchX = contentX + CONTENT_WIDTH - searchW - 8.0f;
        Render2DHelper.drawRoundedRect(context, searchX, headerY, searchW, layoutBtnH, 5.0f, 0xCC0A0D16);
        Render2DHelper.drawRoundedOutline(context, searchX, headerY, searchW, layoutBtnH, 5.0f, 1.0f,
                searchFocused ? accent1 : 0x33FFFFFF);

        Render2DHelper.drawScaledText(context, configsViewOpen ? "✎" : "⌕", searchX + 8.0f, headerY + 5.5f,
                searchFocused ? accent1 : 0xFF7D8B9F, 0.80f, false);

        if (searchQuery.isEmpty() && !searchFocused) {
            String placeholder = configsViewOpen ? "New config name..." : "Search modules...";
            Render2DHelper.drawScaledText(context, placeholder, searchX + 20.0f, headerY + 6.5f,
                    0xFF5D6B80, 0.68f, false);
        } else {
            String cursor = (searchFocused && (System.currentTimeMillis() % 800L > 400L)) ? "|" : "";
            Render2DHelper.drawScaledText(context, searchQuery + cursor, searchX + 20.0f, headerY + 6.5f,
                    0xFFFFFFFF, 0.70f, false);
        }

        if (!searchQuery.isEmpty()) {
            Render2DHelper.drawScaledText(context, "×", searchX + searchW - 11.0f, headerY + 5.0f,
                    0xFF8A97AA, 0.82f, false);
        }

        // Bottom divider of header
        Render2DHelper.drawRect(context, contentX, contentY + HEADER_HEIGHT - 1.0f, CONTENT_WIDTH, 1.0f,
                ColorUtils.withAlpha(accent1, 55));
    }

    private void renderConfigsManager(GuiGraphicsExtractor context, float contentX, float contentY, float mouseX, float mouseY) {
        Theme theme = ThemeManager.getInstance().getCurrentTheme();
        int accent1 = theme.getPrimaryColor();
        int accent2 = theme.getSecondaryColor();
        ConfigManager cm = ConfigManager.getInstance();

        // 1. Top Status & Auto-Load Info Banner
        float bannerX = contentX + 10.0f;
        float bannerY = contentY + 8.0f;
        float bannerW = CONTENT_WIDTH - 20.0f;
        float bannerH = 34.0f;
        Render2DHelper.drawRoundedRect(context, bannerX, bannerY, bannerW, bannerH, 6.0f, 0xE6101522);
        Render2DHelper.drawRoundedOutline(context, bannerX, bannerY, bannerW, bannerH, 6.0f, 1.0f, ColorUtils.withAlpha(accent1, 100));

        String activeStr = "Active Config: " + cm.getActiveConfigName();
        String autoStr = "Startup Auto-Load: ★ " + cm.getAutoLoadConfigName();
        Render2DHelper.drawScaledText(context, activeStr, bannerX + 10.0f, bannerY + 7.0f, 0xFFFFFFFF, 0.80f, true);
        Render2DHelper.drawScaledText(context, autoStr, bannerX + 10.0f, bannerY + 19.5f, 0xFFFFD166, 0.72f, false);

        if (!statusToast.isEmpty() && System.currentTimeMillis() - statusToastTime < 3500L) {
            float tw = Render2DHelper.getTextWidth(statusToast, 0.72f);
            Render2DHelper.drawScaledText(context, statusToast, bannerX + bannerW - tw - 10.0f, bannerY + 13.0f, 0xFF4ADE80, 0.72f, true);
        } else {
            String tip = "Type name top-right & click '+ Save New' to create config";
            float tw = Render2DHelper.getTextWidth(tip, 0.65f);
            Render2DHelper.drawScaledText(context, tip, bannerX + bannerW - tw - 10.0f, bannerY + 13.5f, theme.getMutedTextColor(), 0.65f, false);
        }

        // 2. 2-Column Config Cards Grid
        List<String> configs = cm.listConfigs();
        float gridStartY = bannerY + bannerH + 8.0f;
        float col0X = contentX + 10.0f;
        float col1X = col0X + CARD_WIDTH + 10.0f;
        float cardH = 48.0f;
        float gap = 8.0f;

        for (int i = 0; i < configs.size(); i++) {
            String cfgName = configs.get(i);
            int col = i % 2;
            int row = i / 2;
            float cx = (col == 0) ? col0X : col1X;
            float cy = gridStartY + row * (cardH + gap);
            if (cy + cardH > contentY + WINDOW_HEIGHT - HEADER_HEIGHT - 4.0f) break;

            boolean isActive = cfgName.equalsIgnoreCase(cm.getActiveConfigName());
            boolean isAuto = cfgName.equalsIgnoreCase(cm.getAutoLoadConfigName());
            boolean isPreset = cm.isBuiltInPreset(cfgName);

            int bg = isActive ? ColorUtils.interpolateColor(0xE6101522, ColorUtils.withAlpha(accent1, 48), 0.75f) : 0xE6101522;
            Render2DHelper.drawRoundedRect(context, cx, cy, CARD_WIDTH, cardH, 6.0f, bg);
            Render2DHelper.drawRoundedOutline(context, cx, cy, CARD_WIDTH, cardH, 6.0f, 1.0f,
                    isActive ? accent1 : 0x2AFFFFFF);

            if (isActive) {
                Render2DHelper.drawRoundedRect(context, cx + 1.5f, cy + 6.0f, 3.0f, cardH - 12.0f, 1.5f, accent1);
            }

            // Config Title + Badges
            Render2DHelper.drawScaledText(context, cfgName, cx + 9.0f, cy + 7.5f, 0xFFFFFFFF, 0.86f, true);
            float nameW = Render2DHelper.getTextWidth(cfgName, 0.86f);

            String badge = isPreset ? "PRESET" : "CUSTOM";
            float bScale = 0.56f;
            float bW = Render2DHelper.getTextWidth(badge, bScale) + 6.0f;
            float bX = cx + 14.0f + nameW;
            Render2DHelper.drawRoundedRect(context, bX, cy + 8.0f, bW, 10.0f, 2.5f, ColorUtils.withAlpha(accent1, 45));
            Render2DHelper.drawScaledText(context, badge, bX + 3.0f, cy + 9.8f, accent2, bScale, false);

            if (isAuto) {
                String autoBadge = "★ AUTO";
                float aw = Render2DHelper.getTextWidth(autoBadge, 0.58f) + 6.0f;
                Render2DHelper.drawRoundedRect(context, cx + CARD_WIDTH - aw - 7.0f, cy + 7.5f, aw, 11.0f, 3.0f, 0x38FFD166);
                Render2DHelper.drawRoundedOutline(context, cx + CARD_WIDTH - aw - 7.0f, cy + 7.5f, aw, 11.0f, 3.0f, 1.0f, 0xAAFFD166);
                Render2DHelper.drawScaledText(context, autoBadge, cx + CARD_WIDTH - aw - 4.0f, cy + 9.5f, 0xFFFFD166, 0.58f, false);
            }

            // Action Buttons Row at bottom of card (Load | Save | ★ Auto | ×)
            float btnY = cy + 25.0f;
            float btnH = 16.5f;

            // Load Button
            float loadX = cx + 8.0f;
            float loadW = 54.0f;
            boolean loadHov = mouseX >= loadX && mouseX <= loadX + loadW && mouseY >= btnY && mouseY <= btnY + btnH;
            Render2DHelper.drawRoundedRect(context, loadX, btnY, loadW, btnH, 3.5f,
                    loadHov ? ColorUtils.withAlpha(accent1, 140) : ColorUtils.withAlpha(accent1, 70));
            Render2DHelper.drawRoundedOutline(context, loadX, btnY, loadW, btnH, 3.5f, 1.0f, accent1);
            Render2DHelper.drawCenteredText(context, "Load", loadX + loadW * 0.5f, btnY + 4.5f, 0xFFFFFFFF, false);

            // Save Button
            float saveX = loadX + loadW + 5.0f;
            float saveW = 54.0f;
            boolean saveHov = mouseX >= saveX && mouseX <= saveX + saveW && mouseY >= btnY && mouseY <= btnY + btnH;
            Render2DHelper.drawRoundedRect(context, saveX, btnY, saveW, btnH, 3.5f, saveHov ? 0x38FFFFFF : 0x1EFFFFFF);
            Render2DHelper.drawRoundedOutline(context, saveX, btnY, saveW, btnH, 3.5f, 1.0f, 0x48FFFFFF);
            Render2DHelper.drawCenteredText(context, "Save", saveX + saveW * 0.5f, btnY + 4.5f, 0xFFE2E8F0, false);

            // Auto-Load Toggle Button
            float autoX = saveX + saveW + 5.0f;
            float autoW = 68.0f;
            boolean autoHov = mouseX >= autoX && mouseX <= autoX + autoW && mouseY >= btnY && mouseY <= btnY + btnH;
            int autoBg = isAuto ? 0x45FFD166 : (autoHov ? 0x30FFFFFF : 0x18FFFFFF);
            int autoBorder = isAuto ? 0xFFFFD166 : 0x38FFFFFF;
            Render2DHelper.drawRoundedRect(context, autoX, btnY, autoW, btnH, 3.5f, autoBg);
            Render2DHelper.drawRoundedOutline(context, autoX, btnY, autoW, btnH, 3.5f, 1.0f, autoBorder);
            Render2DHelper.drawCenteredText(context, isAuto ? "★ Auto ON" : "☆ Set Auto", autoX + autoW * 0.5f, btnY + 4.5f,
                    isAuto ? 0xFFFFD166 : 0xFFCBD5E1, false);

            // Delete Button (for non-Default configs)
            if (!"Default".equalsIgnoreCase(cfgName)) {
                float delX = autoX + autoW + 5.0f;
                float delW = 24.0f;
                boolean delHov = mouseX >= delX && mouseX <= delX + delW && mouseY >= btnY && mouseY <= btnY + btnH;
                Render2DHelper.drawRoundedRect(context, delX, btnY, delW, btnH, 3.5f, delHov ? 0x66EF4444 : 0x26EF4444);
                Render2DHelper.drawRoundedOutline(context, delX, btnY, delW, btnH, 3.5f, 1.0f, 0x99EF4444);
                Render2DHelper.drawCenteredText(context, "×", delX + delW * 0.5f, btnY + 4.0f, 0xFFFCA5A5, false);
            }
        }
    }

    private List<Module> getFilteredSidebarModules() {
        List<Module> list;
        if (!searchQuery.isBlank()) {
            list = new ArrayList<>(ModuleManager.getInstance().getModules());
            String q = searchQuery.toLowerCase().trim();
            list.removeIf(m -> !m.getName().toLowerCase().contains(q)
                    && (m.getDescription() == null || !m.getDescription().toLowerCase().contains(q)));
        } else {
            list = new ArrayList<>(ModuleManager.getInstance().getModulesByCategory(selectedCategory));
        }

        if (bindOnlyFilter) {
            list.removeIf(m -> m.getKeyBind() <= 0 || m.getKeyBind() == InputConstants.UNKNOWN.getValue());
        }
        return list;
    }

    private void renderMasonryGrid(GuiGraphicsExtractor context, float contentX, float contentY, float mouseX, float mouseY, float delta) {
        currentScroll = Animation.lerp(currentScroll, targetScroll, 0.28f);

        List<Module> modules = getFilteredSidebarModules();
        float listStartY = contentY;
        float listViewH = WINDOW_HEIGHT - HEADER_HEIGHT - 6.0f;
        float col0X = contentX + 10.0f;
        float col1X = col0X + CARD_WIDTH + 10.0f;
        float gap = 8.0f;

        float[] colY = new float[] { 8.0f, 8.0f };
        cardBoxes.clear();

        context.enableScissor(
                Math.round(contentX),
                Math.round(contentY),
                Math.round(contentX + CONTENT_WIDTH),
                Math.round(contentY + listViewH)
        );

        for (Module m : modules) {
            // Keep animations running smoothly
            m.getToggleAnimation().animateTo(m.isEnabled() ? 1.0f : 0.0f);
            m.getExpandAnimation().animateTo(m.isExpanded() ? 1.0f : 0.0f);

            float cardH = calculateCardHeight(m);
            int col = (colY[0] <= colY[1]) ? 0 : 1;
            float cardX = (col == 0) ? col0X : col1X;
            float cardY = listStartY + colY[col] - currentScroll;

            // Render card if visible in or near viewport
            if (cardY + cardH >= listStartY - 30.0f && cardY <= listStartY + listViewH + 30.0f) {
                renderModuleCard(context, m, cardX, cardY, CARD_WIDTH, cardH, mouseX, mouseY, delta);
            }

            colY[col] += cardH + gap;
        }

        context.disableScissor();

        // Accurate maxScroll calculation from the tallest column
        float tallestCol = Math.max(colY[0], colY[1]);
        this.maxScroll = Math.max(0.0f, tallestCol - listViewH + 20.0f);
        if (targetScroll > maxScroll) targetScroll = maxScroll;
        if (targetScroll < 0.0f) targetScroll = 0.0f;

        // Draw sleek 3px vertical scrollbar track and thumb on right edge
        if (maxScroll > 1.0f) {
            Theme theme = ThemeManager.getInstance().getCurrentTheme();
            int accent1 = theme.getPrimaryColor();
            float sbX = contentX + CONTENT_WIDTH - 6.0f;
            float sbTrackY = listStartY + 4.0f;
            float sbTrackH = listViewH - 8.0f;

            Render2DHelper.drawRoundedRect(context, sbX, sbTrackY, 3.0f, sbTrackH, 1.5f, 0x1AFFFFFF);
            float sbThumbH = Math.max(24.0f, (listViewH / tallestCol) * sbTrackH);
            float sbThumbY = sbTrackY + (currentScroll / maxScroll) * (sbTrackH - sbThumbH);

            Render2DHelper.drawGlow(context, sbX, sbThumbY, 3.0f, sbThumbH, 2.5f, ColorUtils.withAlpha(accent1, 90));
            Render2DHelper.drawRoundedRect(context, sbX, sbThumbY, 3.0f, sbThumbH, 1.5f, ColorUtils.withAlpha(accent1, 210));
        }
    }

    private float calculateSettingsHeight(Module module) {
        return calculateSettingsHeight(module, CARD_WIDTH);
    }

    private float calculateSettingsHeight(Module module, float cardW) {
        List<Setting<?>> settings = module.getSettings();
        if (settings.isEmpty()) {
            return 0.0f;
        }

        float setW = cardW - 14.0f;
        float settingsH = 4.0f; // top padding below divider
        for (Setting<?> setting : settings) {
            if (!setting.isVisible()) continue;
            if (setting instanceof ModeSetting) {
                settingsH += 22.0f + 3.0f;
            } else if (setting instanceof NumberSetting) {
                settingsH += 24.0f + 4.0f;
            } else if (setting instanceof BooleanSetting) {
                settingsH += 20.0f + 3.0f;
            } else if (setting instanceof MultiBoxSetting mb) {
                float curRowW = 0.0f;
                int rows = 1;
                for (String opt : mb.getOptions()) {
                    float optW = Render2DHelper.getTextWidth(opt, 0.62f) + 8.0f;
                    if (curRowW > 0.0f && curRowW + optW > setW) {
                        rows++;
                        curRowW = optW + 4.0f;
                    } else {
                        curRowW += optW + 4.0f;
                    }
                }
                settingsH += 16.0f + rows * 16.0f + 3.0f;
            } else if (setting instanceof ColorSetting) {
                settingsH += 20.0f + 3.0f;
            }
        }
        // Always include Keybind row at bottom of expanded settings
        settingsH += 20.0f + 6.0f;

        return settingsH;
    }

    private float calculateCardHeight(Module module) {
        float baseH = CARD_HEADER_HEIGHT;
        float expandVal = module.getExpandAnimation().getValue();
        if (!module.isExpanded() && expandVal <= 0.002f) {
            return baseH;
        }
        return baseH + calculateSettingsHeight(module, CARD_WIDTH) * expandVal;
    }

    private void renderModuleCard(GuiGraphicsExtractor context, Module module, float cardX, float cardY,
                                  float cardW, float cardH, float mouseX, float mouseY, float delta) {
        Theme theme = ThemeManager.getInstance().getCurrentTheme();
        int accent1 = theme.getPrimaryColor();
        int accent2 = theme.getSecondaryColor();

        float toggleProgress = module.getToggleAnimation().getValue();
        float expandProgress = module.getExpandAnimation().getValue();

        Animation hoverAnim = cardHoverAnims.computeIfAbsent(module, m -> new Animation(0.0f, 140L, Easing.EASE_OUT_QUAD));
        boolean cardHovered = (mouseX >= cardX && mouseX <= cardX + cardW && mouseY >= cardY && mouseY <= cardY + CARD_HEADER_HEIGHT);
        hoverAnim.animateTo(cardHovered ? 1.0f : 0.0f);
        float hoverVal = hoverAnim.getValue();

        // 1. Subtle theme tint on card background when enabled
        int normalBg = 0xE6101522;
        int activeTint = ColorUtils.withAlpha(accent1, 38);
        int cardBg = ColorUtils.interpolateColor(normalBg, activeTint, toggleProgress);
        if (hoverVal > 0.01f) {
            cardBg = ColorUtils.interpolateColor(cardBg, 0xFF182033, hoverVal * 0.4f);
        }

        Render2DHelper.drawRoundedRect(context, cardX, cardY, cardW, cardH, 6.0f, cardBg);
        Render2DHelper.drawSpecularBorder(context, cardX, cardY, cardW, cardH, 6.0f, mouseX, mouseY, accent1);

        // 2. Glowing 3px vertical accent indicator bar on left edge when enabled
        if (toggleProgress > 0.01f) {
            Render2DHelper.drawGlow(context, cardX + 1.5f, cardY + 5.0f, 3.0f, 38.0f, 3.5f * toggleProgress,
                    ColorUtils.withAlpha(accent1, (int) (150 * toggleProgress)));
            Render2DHelper.drawRoundedRect(context, cardX + 1.5f, cardY + 5.0f, 3.0f, 38.0f, 1.5f, accent1);
        }

        // 3. Module Title (crisp white, 0.88f) + Mode Badge (suffix) on top row (cardY + 7.0f)
        float topRowY = cardY + 7.0f;
        float textLeft = cardX + (toggleProgress > 0.05f ? 11.0f : 8.5f);
        int titleCol = ColorUtils.interpolateColor(0xFFDEE5F0, 0xFFFFFFFF, toggleProgress);
        Render2DHelper.drawScaledText(context, module.getName(), textLeft, topRowY, titleCol, 0.88f, false);
        float titleW = Render2DHelper.getTextWidth(module.getName(), 0.88f);

        String suffix = module.getDisplaySuffix();
        if (suffix != null && !suffix.isBlank()) {
            float sfxScale = 0.60f;
            float sfxW = Render2DHelper.getTextWidth(suffix, sfxScale);
            float pillW = sfxW + 8.0f;
            float pillH = 11.0f;
            float pillX = textLeft + titleW + 5.0f;
            float pillY = topRowY + 0.5f;

            Render2DHelper.drawRoundedRect(context, pillX, pillY, pillW, pillH, 3.0f, ColorUtils.withAlpha(accent1, 45));
            Render2DHelper.drawRoundedOutline(context, pillX, pillY, pillW, pillH, 3.0f, 1.0f, ColorUtils.withAlpha(accent1, 110));
            Render2DHelper.drawScaledText(context, suffix, pillX + 4.0f, pillY + 1.5f, accent2, sfxScale, false);
        }

        // 4. Right-side controls on card header top row (cardY + 7.0f)
        float ctrlRight = cardX + cardW - 7.0f;

        // Expand Chevron (if module has settings)
        boolean hasSettings = !module.getSettings().isEmpty();
        float chevX = 0.0f;
        float chevY = topRowY;
        float chevW = 10.0f;
        float chevH = 12.0f;
        if (hasSettings) {
            chevX = ctrlRight - chevW;
            String chev = module.isExpanded() ? "▴" : "▾";
            int chevCol = module.isExpanded() ? accent1 : 0xFF7A879B;
            Render2DHelper.drawScaledText(context, chev, chevX + 1.0f, chevY, chevCol, 0.80f, false);
            ctrlRight -= (chevW + 5.0f);
        }

        // Toggle Switch: 24x12 smooth pill with 8x8 white knob
        float swW = 24.0f;
        float swH = 12.0f;
        float swX = ctrlRight - swW;
        float swY = topRowY;

        int trackBg = ColorUtils.interpolateColor(0xFF1E2536, ColorUtils.interpolateColor(accent1, accent2, 0.35f), toggleProgress);
        Render2DHelper.drawRoundedRect(context, swX, swY, swW, swH, 6.0f, trackBg);
        if (toggleProgress > 0.05f) {
            Render2DHelper.drawGlow(context, swX, swY, swW, swH, 3.0f * toggleProgress,
                    ColorUtils.withAlpha(accent1, (int) (80 * toggleProgress)));
        }
        Render2DHelper.drawRoundedOutline(context, swX, swY, swW, swH, 6.0f, 1.0f,
                ColorUtils.interpolateColor(0x33FFFFFF, accent1, toggleProgress));

        float knobX = swX + 2.0f + 12.0f * toggleProgress;
        float knobY = swY + 2.0f;
        Render2DHelper.drawRoundedRect(context, knobX, knobY, 8.0f, 8.0f, 4.0f, 0xFFFFFFFF);
        ctrlRight -= (swW + 5.0f);

        // Keybind Badge: only shown if bound ([RSHIFT], [K]), binding ("..."), or hovered ("BIND")
        boolean isBinding = (bindingModule == module);
        boolean isBound = (module.getKeyBind() > 0 && module.getKeyBind() != InputConstants.UNKNOWN.getValue());
        boolean showKb = isBinding || isBound || cardHovered;

        float kbX = 0.0f;
        float kbY = topRowY;
        float kbW = 0.0f;
        float kbH = 12.0f;

        if (showKb) {
            String keyText = isBinding ? "..." : (isBound ? BindComponent.getKeyName(module.getKeyBind()) : "BIND");
            float kbScale = 0.63f;
            float kbTextW = Render2DHelper.getTextWidth(keyText, kbScale);
            kbW = kbTextW + 8.0f;
            kbX = ctrlRight - kbW;

            int kbBg = isBinding ? ColorUtils.withAlpha(accent1, 190) : (isBound ? ColorUtils.withAlpha(accent1, 38) : 0x16FFFFFF);
            int kbBorder = isBinding ? accent2 : (isBound ? ColorUtils.withAlpha(accent1, 95) : 0x2AFFFFFF);
            Render2DHelper.drawRoundedRect(context, kbX, kbY, kbW, kbH, 3.0f, kbBg);
            Render2DHelper.drawRoundedOutline(context, kbX, kbY, kbW, kbH, 3.0f, 1.0f, kbBorder);

            int kbTextCol = isBinding ? 0xFFFFFFFF : (isBound ? accent1 : 0xFF6E788C);
            Render2DHelper.drawScaledText(context, keyText, kbX + 4.0f, kbY + 2.0f, kbTextCol, kbScale, false);
            ctrlRight -= (kbW + 5.0f);
        }

        // 5. 2-line wrapped description on lower half of header (cardY + 24.0f) using full width (cardW - 18.0f)
        float maxDescW = cardW - 18.0f;
        String desc = module.getDescription();
        if (desc != null && !desc.isEmpty()) {
            float descScale = 0.63f;
            List<String> wrapped = wrapText(desc, maxDescW, descScale);
            if (!wrapped.isEmpty()) {
                float line1Y = cardY + 24.0f;
                Render2DHelper.drawScaledText(context, wrapped.get(0), textLeft, line1Y, 0xFF8B95A8, descScale, false);
                if (wrapped.size() > 1) {
                    String line2;
                    if (wrapped.size() > 2) {
                        StringBuilder rest = new StringBuilder();
                        for (int i = 1; i < wrapped.size(); i++) {
                            if (rest.length() > 0) rest.append(" ");
                            rest.append(wrapped.get(i));
                        }
                        line2 = getTruncatedDescription(rest.toString(), maxDescW, descScale);
                    } else {
                        line2 = wrapped.get(1);
                    }
                    Render2DHelper.drawScaledText(context, line2, textLeft, line1Y + 11.0f, 0xFF8B95A8, descScale, false);
                }
            }
        }

        // Tooltip tracking when hovered over header
        if (cardHovered && desc != null && !desc.isEmpty()) {
            hoveredTooltipTitle = module.getName();
            hoveredTooltipDesc = desc;
        }

        // Record layout for clicks
        cardBoxes.put(module, new CardBox(module, cardX, cardY, cardW, cardH,
                swX, swY, swW, swH,
                kbX, kbY, kbW, kbH, showKb,
                chevX, chevY, chevW, chevH, hasSettings));

        // 6. Render Expanded Settings
        if (expandProgress > 0.02f && hasSettings) {
            renderExpandedSettings(context, module, cardX, cardY, cardW, cardH, CARD_HEADER_HEIGHT, expandProgress, mouseX, mouseY);
        }
    }

    private List<String> wrapText(String text, float maxWidth, float scale) {
        if (text == null || text.isEmpty()) return new ArrayList<>();
        List<String> lines = new ArrayList<>();
        String[] words = text.split("\\s+");
        StringBuilder currentLine = new StringBuilder();

        for (String word : words) {
            if (word.isEmpty()) continue;
            String testLine = (currentLine.length() == 0) ? word : (currentLine + " " + word);
            if (Render2DHelper.getTextWidth(testLine, scale) <= maxWidth) {
                currentLine = new StringBuilder(testLine);
            } else {
                if (currentLine.length() > 0) {
                    lines.add(currentLine.toString());
                    currentLine = new StringBuilder(word);
                } else {
                    lines.add(word);
                    currentLine = new StringBuilder();
                }
            }
        }
        if (currentLine.length() > 0) {
            lines.add(currentLine.toString());
        }
        return lines;
    }

    private String getTruncatedDescription(String desc, float maxWidth) {
        return getTruncatedDescription(desc, maxWidth, 0.66f);
    }

    private String getTruncatedDescription(String desc, float maxWidth, float scale) {
        if (desc == null || desc.isEmpty()) return "";
        float fullW = Render2DHelper.getTextWidth(desc, scale);
        if (fullW <= maxWidth) return desc;

        String ellipsis = "...";
        float ellW = Render2DHelper.getTextWidth(ellipsis, scale);
        float targetW = maxWidth - ellW;
        if (targetW <= 10.0f) return "";

        int low = 0;
        int high = desc.length();
        int best = 0;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            String sub = desc.substring(0, mid);
            if (Render2DHelper.getTextWidth(sub, scale) <= targetW) {
                best = mid;
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }
        return desc.substring(0, best).trim() + ellipsis;
    }

    private void renderFloatingTooltip(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        if (hoveredTooltipDesc == null || hoveredTooltipDesc.isEmpty()) return;

        Theme theme = ThemeManager.getInstance().getCurrentTheme();
        int accent1 = theme.getPrimaryColor();

        float maxTipW = 210.0f;
        float titleScale = 0.74f;
        float descScale = 0.63f;

        List<String> wrappedDesc = wrapText(hoveredTooltipDesc, maxTipW, descScale);
        if (wrappedDesc.isEmpty()) return;

        float titleW = (hoveredTooltipTitle != null && !hoveredTooltipTitle.isEmpty())
                ? Render2DHelper.getTextWidth(hoveredTooltipTitle, titleScale) : 0.0f;
        float contentW = titleW;
        for (String line : wrappedDesc) {
            contentW = Math.max(contentW, Render2DHelper.getTextWidth(line, descScale));
        }

        float tipW = Math.max(90.0f, contentW + 16.0f);
        float lineH = 10.5f;
        float headerH = (hoveredTooltipTitle != null && !hoveredTooltipTitle.isEmpty()) ? 14.0f : 0.0f;
        float tipH = 7.0f + headerH + wrappedDesc.size() * lineH + 5.0f;

        float tipX = mouseX + 12.0f;
        float tipY = mouseY + 12.0f;

        if (tipX + tipW > this.width - 6.0f) {
            tipX = Math.max(6.0f, mouseX - tipW - 8.0f);
        }
        if (tipY + tipH > this.height - 6.0f) {
            tipY = Math.max(6.0f, this.height - tipH - 6.0f);
        }
        tipX = Math.clamp(tipX, 6.0f, Math.max(6.0f, this.width - tipW - 6.0f));
        tipY = Math.clamp(tipY, 6.0f, Math.max(6.0f, this.height - tipH - 6.0f));

        Render2DHelper.drawLiquidGlass(context, tipX, tipY, tipW, tipH, 4.0f, 242, 50);
        Render2DHelper.drawSpecularBorder(context, tipX, tipY, tipW, tipH, 4.0f, mouseX, mouseY, accent1);

        float curY = tipY + 6.0f;
        if (hoveredTooltipTitle != null && !hoveredTooltipTitle.isEmpty()) {
            Render2DHelper.drawScaledText(context, hoveredTooltipTitle, tipX + 8.0f, curY, accent1, titleScale, true);
            curY += 12.0f;
            Render2DHelper.drawRect(context, tipX + 6.0f, curY, tipW - 12.0f, 1.0f, 0x22FFFFFF);
            curY += 3.0f;
        }

        for (String line : wrappedDesc) {
            Render2DHelper.drawScaledText(context, line, tipX + 8.0f, curY, 0xFFE2E8F0, descScale, false);
            curY += lineH;
        }
    }

    private void renderExpandedSettings(GuiGraphicsExtractor context, Module module, float cardX, float cardY,
                                        float cardW, float cardH, float expandProgress, float mouseX, float mouseY) {
        renderExpandedSettings(context, module, cardX, cardY, cardW, cardH, CARD_HEADER_HEIGHT, expandProgress, mouseX, mouseY);
    }

    private void renderExpandedSettings(GuiGraphicsExtractor context, Module module, float cardX, float cardY,
                                        float cardW, float cardH, float headerH, float expandProgress, float mouseX, float mouseY) {
        Theme theme = ThemeManager.getInstance().getCurrentTheme();
        int accent1 = theme.getPrimaryColor();
        int accent2 = theme.getSecondaryColor();

        // 1. Subtle 1px divider line between header and settings
        Render2DHelper.drawRect(context, cardX + 6.0f, cardY + headerH - 1.0f,
                cardW - 12.0f, 1.0f, 0x22FFFFFF);

        float setX = cardX + 7.0f;
        float setW = cardW - 14.0f;
        float curY = cardY + headerH + 4.0f;

        for (Setting<?> setting : module.getSettings()) {
            if (!setting.isVisible()) continue;

            if (setting instanceof ModeSetting modeSetting) {
                // Interactive selector row (22px): label on left, ‹ CurrentMode › pill on right
                float rowH = 22.0f;
                boolean rowHov = (mouseX >= setX && mouseX <= setX + setW && mouseY >= curY && mouseY <= curY + rowH);
                if (rowHov && modeSetting.getDescription() != null && !modeSetting.getDescription().isEmpty()) {
                    hoveredTooltipTitle = modeSetting.getName();
                    hoveredTooltipDesc = modeSetting.getDescription();
                }

                float labelScale = (setW < 160.0f) ? 0.68f : 0.74f;
                float modeScale = (setW < 160.0f) ? 0.62f : 0.68f;

                String currentMode = modeSetting.getMode() != null ? modeSetting.getMode() : "";
                String pillText = "‹  " + currentMode + "  ›";
                float modeTextW = Render2DHelper.getTextWidth(pillText, modeScale);
                float pillW = Math.min(setW * 0.54f, Math.max(50.0f, modeTextW + 10.0f));
                float pillH = 17.0f;
                float pillX = setX + setW - pillW - 2.0f;
                float pillY = curY + 2.5f;

                Render2DHelper.drawScaledText(context, modeSetting.getName(), setX + 2.0f, curY + 6.0f,
                        0xFFD0D7E6, labelScale, false);

                boolean pillHov = mouseX >= pillX && mouseX <= pillX + pillW && mouseY >= pillY && mouseY <= pillY + pillH;
                int pillBg = pillHov ? 0xFF212B3E : 0xFF182032;
                int pillBorder = ColorUtils.withAlpha(accent1, pillHov ? 190 : 100);

                // Highlight/glow for server presets
                boolean isPreset = modeSetting.getName().toLowerCase(Locale.ROOT).contains("preset");
                if (isPreset || pillHov) {
                    Render2DHelper.drawGlow(context, pillX, pillY, pillW, pillH, 2.5f,
                            ColorUtils.withAlpha(accent2, isPreset ? 70 : 100));
                }

                Render2DHelper.drawRoundedRect(context, pillX, pillY, pillW, pillH, 3.5f, pillBg);
                Render2DHelper.drawRoundedOutline(context, pillX, pillY, pillW, pillH, 3.5f, 1.0f, pillBorder);
                Render2DHelper.drawScaledText(context, pillText, pillX + (pillW - modeTextW) * 0.5f, pillY + 4.0f,
                        accent2, modeScale, false);

                curY += rowH + 3.0f;

            } else if (setting instanceof NumberSetting numSetting) {
                // Number slider row (24px): label top-left, value top-right, 4px track below with 8x8 thumb
                float rowH = 24.0f;
                boolean rowHov = (mouseX >= setX && mouseX <= setX + setW && mouseY >= curY && mouseY <= curY + rowH);
                if (rowHov && numSetting.getDescription() != null && !numSetting.getDescription().isEmpty()) {
                    hoveredTooltipTitle = numSetting.getName();
                    hoveredTooltipDesc = numSetting.getDescription();
                }

                Render2DHelper.drawScaledText(context, numSetting.getName(), setX + 2.0f, curY + 2.0f,
                        0xFFD0D7E6, 0.72f, false);

                String valStr = String.format(Locale.US,
                        numSetting.getStep() >= 1.0 ? "%.0f" : (numSetting.getStep() >= 0.1 ? "%.1f" : "%.2f"),
                        numSetting.getValue());
                float valW = Render2DHelper.getTextWidth(valStr, 0.70f);
                Render2DHelper.drawScaledText(context, valStr, setX + setW - valW - 2.0f, curY + 2.0f,
                        0xFF00CEC9, 0.70f, true);

                float trackX = setX + 2.0f;
                float trackW = setW - 4.0f;
                float trackY = curY + 16.0f;
                float trackH = 4.0f;

                double min = numSetting.getMin();
                double max = numSetting.getMax();
                float ratio = (float) ((numSetting.getValue() - min) / (max - min));
                ratio = Math.max(0.0f, Math.min(1.0f, ratio));

                Render2DHelper.drawRoundedRect(context, trackX, trackY, trackW, trackH, 2.0f, 0xFF1A2234);
                if (ratio > 0.01f) {
                    Render2DHelper.drawGradientRoundedRect(context, trackX, trackY, trackW * ratio, trackH, 2.0f,
                            accent1, accent2);
                }

                float thumbX = trackX + ratio * trackW - 4.0f;
                float thumbY = trackY + (trackH - 8.0f) * 0.5f;
                Render2DHelper.drawGlow(context, thumbX, thumbY, 8.0f, 8.0f, 2.5f, ColorUtils.withAlpha(accent1, 100));
                Render2DHelper.drawRoundedRect(context, thumbX, thumbY, 8.0f, 8.0f, 4.0f, 0xFFFFFFFF);

                curY += rowH + 4.0f;

            } else if (setting instanceof BooleanSetting boolSetting) {
                // Boolean row (20px): label on left, 18x9 mini toggle switch on right
                float rowH = 20.0f;
                boolean rowHov = (mouseX >= setX && mouseX <= setX + setW && mouseY >= curY && mouseY <= curY + rowH);
                if (rowHov && boolSetting.getDescription() != null && !boolSetting.getDescription().isEmpty()) {
                    hoveredTooltipTitle = boolSetting.getName();
                    hoveredTooltipDesc = boolSetting.getDescription();
                }

                Render2DHelper.drawScaledText(context, boolSetting.getName(), setX + 2.0f, curY + 5.0f,
                        0xFFD0D7E6, 0.74f, false);

                float miniW = 18.0f;
                float miniH = 9.0f;
                float miniX = setX + setW - miniW - 2.0f;
                float miniY = curY + 5.5f;

                boolean bVal = boolSetting.getValue();
                int miniBg = bVal ? ColorUtils.interpolateColor(accent1, accent2, 0.35f) : 0xFF1E2536;
                Render2DHelper.drawRoundedRect(context, miniX, miniY, miniW, miniH, 4.5f, miniBg);
                Render2DHelper.drawRoundedOutline(context, miniX, miniY, miniW, miniH, 4.5f, 1.0f,
                        bVal ? accent1 : 0x33FFFFFF);

                float knobX = bVal ? (miniX + miniW - 6.0f - 1.5f) : (miniX + 1.5f);
                float knobY = miniY + 1.5f;
                Render2DHelper.drawRoundedRect(context, knobX, knobY, 6.0f, 6.0f, 3.0f, 0xFFFFFFFF);

                curY += rowH + 3.0f;

            } else if (setting instanceof MultiBoxSetting mb) {
                // MultiBox row: label on top, rounded chips below
                boolean mbHov = (mouseX >= setX && mouseX <= setX + setW && mouseY >= curY && mouseY <= curY + 14.0f);
                if (mbHov && mb.getDescription() != null && !mb.getDescription().isEmpty()) {
                    hoveredTooltipTitle = mb.getName();
                    hoveredTooltipDesc = mb.getDescription();
                }

                Render2DHelper.drawScaledText(context, mb.getName(), setX + 2.0f, curY + 2.0f,
                        0xFFD0D7E6, 0.72f, false);
                float chipX = setX + 2.0f;
                float chipY = curY + 14.0f;

                for (String opt : mb.getOptions()) {
                    boolean optOn = mb.isEnabled(opt);
                    float optScale = 0.62f;
                    float optW = Render2DHelper.getTextWidth(opt, optScale) + 8.0f;
                    if (chipX + optW > setX + setW) {
                        chipX = setX + 2.0f;
                        chipY += 16.0f;
                    }

                    int chipBg = optOn ? ColorUtils.withAlpha(accent1, 60) : 0x18FFFFFF;
                    int chipBorder = optOn ? accent1 : 0x2AFFFFFF;
                    Render2DHelper.drawRoundedRect(context, chipX, chipY, optW, 13.0f, 3.0f, chipBg);
                    Render2DHelper.drawRoundedOutline(context, chipX, chipY, optW, 13.0f, 3.0f, 1.0f, chipBorder);
                    Render2DHelper.drawScaledText(context, opt, chipX + 4.0f, chipY + 2.5f,
                            optOn ? 0xFFFFFFFF : 0xFF8895A7, optScale, false);

                    chipX += optW + 4.0f;
                }

                float curRowW = 0.0f;
                int rows = 1;
                for (String opt : mb.getOptions()) {
                    float optW = Render2DHelper.getTextWidth(opt, 0.62f) + 8.0f;
                    if (curRowW > 0.0f && curRowW + optW > setW) {
                        rows++;
                        curRowW = optW + 4.0f;
                    } else {
                        curRowW += optW + 4.0f;
                    }
                }
                curY += 16.0f + rows * 16.0f + 3.0f;

            } else if (setting instanceof ColorSetting colorSetting) {
                // Color row (20px): label on left, color preview pill on right
                float rowH = 20.0f;
                boolean rowHov = (mouseX >= setX && mouseX <= setX + setW && mouseY >= curY && mouseY <= curY + rowH);
                if (rowHov && colorSetting.getDescription() != null && !colorSetting.getDescription().isEmpty()) {
                    hoveredTooltipTitle = colorSetting.getName();
                    hoveredTooltipDesc = colorSetting.getDescription();
                }

                Render2DHelper.drawScaledText(context, colorSetting.getName(), setX + 2.0f, curY + 5.0f,
                        0xFFD0D7E6, 0.74f, false);

                float colW = 24.0f;
                float colH = 12.0f;
                float colX = setX + setW - colW - 2.0f;
                float colY = curY + 4.0f;

                Render2DHelper.drawRoundedRect(context, colX, colY, colW, colH, 3.0f, colorSetting.getColor());
                Render2DHelper.drawRoundedOutline(context, colX, colY, colW, colH, 3.0f, 1.0f, 0x66FFFFFF);

                curY += rowH + 3.0f;
            }
        }

        // Quick Keybind row at bottom of settings
        float bindRowH = 20.0f;
        boolean kbHov = (mouseX >= setX && mouseX <= setX + setW && mouseY >= curY && mouseY <= curY + bindRowH);
        if (kbHov) {
            hoveredTooltipTitle = "Keybind";
            hoveredTooltipDesc = "Click to set a keyboard shortcut, RMB to clear.";
        }

        Render2DHelper.drawScaledText(context, "Keybind", setX + 2.0f, curY + 5.0f, 0xFF8B95A8, 0.72f, false);

        boolean isBinding = (bindingModule == module);
        String kbStr = isBinding ? "[Press Key...]" : (module.getKeyBind() > 0 ? "[" + BindComponent.getKeyName(module.getKeyBind()) + "]" : "[NONE]");
        float kbScale = 0.64f;
        float kbW = Render2DHelper.getTextWidth(kbStr, kbScale) + 8.0f;
        float kbX = setX + setW - kbW - 2.0f;
        float kbY = curY + 3.0f;

        int kbBg = isBinding ? ColorUtils.withAlpha(accent1, 180) : 0x18FFFFFF;
        Render2DHelper.drawRoundedRect(context, kbX, kbY, kbW, 14.0f, 3.0f, kbBg);
        Render2DHelper.drawRoundedOutline(context, kbX, kbY, kbW, 14.0f, 3.0f, 1.0f,
                isBinding ? accent2 : ColorUtils.withAlpha(accent1, 90));
        Render2DHelper.drawScaledText(context, kbStr, kbX + 4.0f, kbY + 3.0f,
                isBinding ? 0xFFFFFFFF : accent1, kbScale, false);
    }

    private void renderThemeSelectorPopup(GuiGraphicsExtractor context, float popupX, float popupY, float mouseX, float mouseY) {
        List<Theme> themes = ThemeManager.getInstance().getThemes();
        Theme current = ThemeManager.getInstance().getCurrentTheme();

        float popupW = 166.0f;
        float itemH = 16.5f;
        float popupH = themes.size() * itemH + 10.0f;

        Render2DHelper.drawLiquidGlass(context, popupX, popupY, popupW, popupH, 6.0f, 248, 55);

        float iy = popupY + 5.0f;
        for (Theme t : themes) {
            boolean isCurrent = (t == current);
            boolean hovered = mouseX >= popupX && mouseX <= popupX + popupW && mouseY >= iy && mouseY <= iy + itemH;

            if (hovered || isCurrent) {
                int bg = isCurrent
                        ? ColorUtils.withAlpha(t.getPrimaryColor(), 75)
                        : ColorUtils.withAlpha(0xFFFFFFFF, 25);
                Render2DHelper.drawRoundedRect(context, popupX + 4.0f, iy + 0.5f, popupW - 8.0f, itemH - 1.0f, 3.0f, bg);
            }

            Render2DHelper.drawCircle(context, popupX + 12.0f, iy + itemH * 0.5f, 3.5f, t.getPrimaryColor());
            Render2DHelper.drawCircle(context, popupX + 18.0f, iy + itemH * 0.5f, 3.5f, t.getSecondaryColor());

            int textCol = isCurrent ? t.getPrimaryColor() : (hovered ? 0xFFFFFFFF : current.getMutedTextColor());
            Render2DHelper.drawScaledText(context, t.getName(), popupX + 26.0f, iy + 4.5f, textCol, 0.70f, false);

            if (isCurrent) {
                Render2DHelper.drawScaledText(context, "●", popupX + popupW - 13.0f, iy + 4.5f, t.getSecondaryColor(), 0.72f, false);
            }
            iy += itemH;
        }
    }

    // =================================================================================
    // MODE 2: DROPDOWN PANELS (Classic 6 x 116px Columns)
    // =================================================================================
    private void renderDropdownPanelsMode(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta, float scale, float alpha) {
        Theme theme = ThemeManager.getInstance().getCurrentTheme();
        int accent1 = theme.getPrimaryColor();
        int accent2 = theme.getSecondaryColor();

        // Top Control Bar with Layout and Theme switcher (clean, NO square brackets)
        float barW = 360.0f;
        float barH = 22.0f;
        float barX = (this.width - barW) * 0.5f;
        float barY = 6.0f;

        Render2DHelper.drawLiquidGlass(context, barX, barY, barW, barH, 6.0f, (int) (230 * alpha), (int) (40 * alpha));
        Render2DHelper.drawScaledText(context, "OpticumeClient", barX + 10.0f, barY + 6.5f, accent1, 0.82f, true);

        // Layout button
        float layoutBtnW = 100.0f;
        float layoutBtnH = 16.0f;
        float layoutBtnX = barX + barW - layoutBtnW - 6.0f;
        float layoutBtnY = barY + 3.0f;
        boolean layoutHov = mouseX >= layoutBtnX && mouseX <= layoutBtnX + layoutBtnW
                && mouseY >= layoutBtnY && mouseY <= layoutBtnY + layoutBtnH;

        Render2DHelper.drawGradientRoundedRect(context, layoutBtnX, layoutBtnY, layoutBtnW, layoutBtnH, 3.5f,
                ColorUtils.withAlpha(accent1, layoutHov ? 225 : 175),
                ColorUtils.withAlpha(accent2, layoutHov ? 225 : 175));
        String layoutTxt = "Layout: Window";
        float vw = Render2DHelper.getTextWidth(layoutTxt, 0.66f);
        Render2DHelper.drawScaledText(context, layoutTxt, layoutBtnX + (layoutBtnW - vw) * 0.5f, layoutBtnY + 4.5f,
                0xFFFFFFFF, 0.66f, false);

        // Theme cycle button in top bar
        float thBtnW = 110.0f;
        float thBtnX = layoutBtnX - thBtnW - 6.0f;
        boolean thHov = mouseX >= thBtnX && mouseX <= thBtnX + thBtnW
                && mouseY >= layoutBtnY && mouseY <= layoutBtnY + layoutBtnH;

        Render2DHelper.drawRoundedRect(context, thBtnX, layoutBtnY, thBtnW, layoutBtnH, 3.5f, thHov ? 0x38FFFFFF : 0x22FFFFFF);
        Render2DHelper.drawRoundedOutline(context, thBtnX, layoutBtnY, thBtnW, layoutBtnH, 3.5f, 1.0f, accent1);
        String thTxt = "Theme: " + theme.getName();
        if (thTxt.length() > 18) thTxt = thTxt.substring(0, 17) + "..";
        Render2DHelper.drawScaledText(context, thTxt, thBtnX + 6.0f, layoutBtnY + 4.5f, theme.getTextColor(), 0.64f, false);

        // Render 6 Floating Dropdown Panels
        for (DropdownPanel panel : dropdownPanels) {
            panel.render(context, mouseX, mouseY, delta);
        }
    }

    private class DropdownPanel {
        private final Category category;
        private final DropdownPanelState state;
        private final Animation openAnim;
        private boolean dragging = false;
        private float dragOffsetX;
        private float dragOffsetY;
        private float maxScroll = 0.0f;

        DropdownPanel(Category category, DropdownPanelState state) {
            this.category = category;
            this.state = state;
            this.openAnim = new Animation(state.open ? 1.0f : 0.0f, 200L, Easing.EASE_OUT_CUBIC);
        }

        void render(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
            if (dragging) {
                state.x = mouseX - dragOffsetX;
                state.y = mouseY - dragOffsetY;
            }

            openAnim.animateTo(state.open ? 1.0f : 0.0f);
            float openVal = openAnim.getValue();
            state.currentScroll = Animation.lerp(state.currentScroll, state.targetScroll, 0.28f);

            Theme theme = ThemeManager.getInstance().getCurrentTheme();
            int accent1 = theme.getPrimaryColor();
            int accent2 = theme.getSecondaryColor();

            List<Module> mods = ModuleManager.getInstance().getModulesByCategory(category);
            float contentTotalH = 4.0f;
            for (Module m : mods) {
                m.getToggleAnimation().animateTo(m.isEnabled() ? 1.0f : 0.0f);
                m.getExpandAnimation().animateTo(m.isExpanded() ? 1.0f : 0.0f);
                float expandVal = m.getExpandAnimation().getValue();
                float mH = (m.isExpanded() || expandVal > 0.002f)
                        ? (24.0f + calculateSettingsHeight(m, PANEL_WIDTH - 6.0f) * expandVal)
                        : 24.0f;
                contentTotalH += mH + 3.0f;
            }

            float visibleBodyH = Math.min(PANEL_MAX_BODY_HEIGHT, contentTotalH) * openVal;
            float totalPanelH = PANEL_HEADER_HEIGHT + visibleBodyH;

            // Panel Liquid Glass Container
            Render2DHelper.drawLiquidGlass(context, state.x, state.y, PANEL_WIDTH, totalPanelH, 6.0f, 232, 42);
            Render2DHelper.drawSpecularBorder(context, state.x, state.y, PANEL_WIDTH, totalPanelH, 6.0f, mouseX, mouseY, accent1);

            // Header Gradient Bar
            Render2DHelper.drawGradientRoundedRect(context, state.x, state.y, PANEL_WIDTH, PANEL_HEADER_HEIGHT, 6.0f,
                    ColorUtils.withAlpha(accent1, 210), ColorUtils.withAlpha(accent2, 185));

            String headerTitle = getCategoryIcon(category) + " " + category.getDisplayName();
            Render2DHelper.drawScaledText(context, headerTitle, state.x + 6.0f, state.y + 7.0f, 0xFFFFFFFF, 0.78f, true);

            String countStr = String.valueOf(mods.size());
            float cw = Render2DHelper.getTextWidth(countStr, 0.68f);
            Render2DHelper.drawScaledText(context, countStr, state.x + PANEL_WIDTH - cw - 6.0f, state.y + 7.5f,
                    0xDDFFFFFF, 0.68f, false);

            if (openVal > 0.02f && visibleBodyH > 1.0f) {
                float bodyY = state.y + PANEL_HEADER_HEIGHT;
                context.enableScissor(
                        Math.round(state.x),
                        Math.round(bodyY),
                        Math.round(state.x + PANEL_WIDTH),
                        Math.round(bodyY + visibleBodyH)
                );

                float curY = bodyY + 3.0f - state.currentScroll;
                for (Module m : mods) {
                    float expandVal = m.getExpandAnimation().getValue();
                    float mH = (m.isExpanded() || expandVal > 0.002f)
                            ? (24.0f + calculateSettingsHeight(m, PANEL_WIDTH - 6.0f) * expandVal)
                            : 24.0f;
                    if (curY + mH >= bodyY - 12.0f && curY <= bodyY + visibleBodyH + 12.0f) {
                        renderCompactModuleCard(context, m, state.x + 3.0f, curY, PANEL_WIDTH - 6.0f, mH, mouseX, mouseY);
                    }
                    curY += mH + 3.0f;
                }

                context.disableScissor();

                this.maxScroll = Math.max(0.0f, contentTotalH - PANEL_MAX_BODY_HEIGHT);
                if (state.targetScroll > maxScroll) state.targetScroll = maxScroll;
                if (state.targetScroll < 0.0f) state.targetScroll = 0.0f;
            }
        }

        private void renderCompactModuleCard(GuiGraphicsExtractor context, Module m, float cx, float cy, float cw, float ch,
                                             float mouseX, float mouseY) {
            Theme theme = ThemeManager.getInstance().getCurrentTheme();
            int accent1 = theme.getPrimaryColor();
            float toggleProgress = m.getToggleAnimation().getValue();
            float expandProgress = m.getExpandAnimation().getValue();

            boolean headerHov = (mouseX >= cx && mouseX <= cx + cw && mouseY >= cy && mouseY <= cy + 24.0f);
            if (headerHov && m.getDescription() != null && !m.getDescription().isEmpty()) {
                hoveredTooltipTitle = m.getName();
                hoveredTooltipDesc = m.getDescription();
            }

            int bg = ColorUtils.interpolateColor(0xE6101522, ColorUtils.withAlpha(accent1, 45), toggleProgress);
            Render2DHelper.drawRoundedRect(context, cx, cy, cw, ch, 4.0f, bg);
            Render2DHelper.drawRoundedOutline(context, cx, cy, cw, ch, 4.0f, 1.0f,
                    ColorUtils.interpolateColor(0x22FFFFFF, accent1, toggleProgress));

            // Name
            int titleCol = ColorUtils.interpolateColor(theme.getTextColor(), 0xFFFFFFFF, toggleProgress);
            Render2DHelper.drawScaledText(context, m.getName(), cx + 5.0f, cy + 7.0f, titleCol, 0.74f, false);

            // Expand indicator / chevron if has settings
            if (!m.getSettings().isEmpty()) {
                String chev = m.isExpanded() ? "▴" : "▾";
                int chevCol = m.isExpanded() ? accent1 : 0xFF7A879B;
                Render2DHelper.drawScaledText(context, chev, cx + cw - 32.0f, cy + 6.5f, chevCol, 0.76f, false);
            }

            // Mini switch
            float swW = 16.0f;
            float swH = 8.0f;
            float swX = cx + cw - swW - 4.0f;
            float swY = cy + 7.5f;
            int swBg = toggleProgress > 0.05f ? accent1 : 0xFF1E2536;
            Render2DHelper.drawRoundedRect(context, swX, swY, swW, swH, 4.0f, swBg);
            float knobX = swX + 1.0f + 8.0f * toggleProgress;
            Render2DHelper.drawRoundedRect(context, knobX, swY + 1.0f, 6.0f, 6.0f, 3.0f, 0xFFFFFFFF);

            // Render expanded settings below 24px header
            if (expandProgress > 0.02f && !m.getSettings().isEmpty()) {
                renderExpandedSettings(context, m, cx, cy, cw, ch, 24.0f, expandProgress, mouseX, mouseY);
            }
        }

        boolean mouseClicked(double mouseX, double mouseY, int button) {
            // Header click
            if (mouseX >= state.x && mouseX <= state.x + PANEL_WIDTH
                    && mouseY >= state.y && mouseY <= state.y + PANEL_HEADER_HEIGHT) {
                if (button == 0) {
                    dragging = true;
                    dragOffsetX = (float) mouseX - state.x;
                    dragOffsetY = (float) mouseY - state.y;
                    return true;
                } else if (button == 1) {
                    state.open = !state.open;
                    return true;
                }
            }

            if (state.open && openAnim.getValue() > 0.5f) {
                float bodyY = state.y + PANEL_HEADER_HEIGHT;
                if (mouseX >= state.x && mouseX <= state.x + PANEL_WIDTH
                        && mouseY >= bodyY && mouseY <= bodyY + PANEL_MAX_BODY_HEIGHT) {
                    float curY = bodyY + 3.0f - state.currentScroll;
                    for (Module m : ModuleManager.getInstance().getModulesByCategory(category)) {
                        float expandVal = m.getExpandAnimation().getValue();
                        float mH = (m.isExpanded() || expandVal > 0.002f)
                                ? (24.0f + calculateSettingsHeight(m, PANEL_WIDTH - 6.0f) * expandVal)
                                : 24.0f;
                        if (mouseX >= state.x + 3.0f && mouseX <= state.x + PANEL_WIDTH - 3.0f
                                && mouseY >= curY && mouseY <= curY + mH) {
                            if (mouseY <= curY + 24.0f) {
                                if (button == 0) {
                                    m.toggle();
                                    return true;
                                } else if (button == 1) {
                                    m.toggleExpanded();
                                    return true;
                                } else if (button == 2) {
                                    bindingModule = (bindingModule == m ? null : m);
                                    return true;
                                }
                            } else if (m.isExpanded() && expandVal > 0.02f) {
                                if (handleSettingsClick(m, state.x + 3.0f, curY, PANEL_WIDTH - 6.0f, 24.0f, mouseX, mouseY, button)) {
                                    return true;
                                }
                            }
                        }
                        curY += mH + 3.0f;
                    }
                }
            }
            return false;
        }

        void mouseReleased() {
            dragging = false;
        }

        boolean mouseScrolled(double mouseX, double mouseY, double verticalAmount) {
            if (!state.open) return false;
            float bodyY = state.y + PANEL_HEADER_HEIGHT;
            if (mouseX >= state.x && mouseX <= state.x + PANEL_WIDTH
                    && mouseY >= state.y && mouseY <= bodyY + PANEL_MAX_BODY_HEIGHT) {
                state.targetScroll -= (float) (verticalAmount * 22.0f);
                if (state.targetScroll < 0.0f) state.targetScroll = 0.0f;
                if (state.targetScroll > maxScroll) state.targetScroll = maxScroll;
                return true;
            }
            return false;
        }
    }

    private static class CardBox {
        final Module module;
        final float x, y, width, height;
        final float switchX, switchY, switchW, switchH;
        final float kbX, kbY, kbW, kbH;
        final boolean kbVisible;
        final float chevX, chevY, chevW, chevH;
        final boolean chevVisible;

        CardBox(Module module, float x, float y, float width, float height,
                float switchX, float switchY, float switchW, float switchH,
                float kbX, float kbY, float kbW, float kbH, boolean kbVisible,
                float chevX, float chevY, float chevW, float chevH, boolean chevVisible) {
            this.module = module;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.switchX = switchX;
            this.switchY = switchY;
            this.switchW = switchW;
            this.switchH = switchH;
            this.kbX = kbX;
            this.kbY = kbY;
            this.kbW = kbW;
            this.kbH = kbH;
            this.kbVisible = kbVisible;
            this.chevX = chevX;
            this.chevY = chevY;
            this.chevW = chevW;
            this.chevH = chevH;
            this.chevVisible = chevVisible;
        }
    }

    // =================================================================================
    // INPUT HANDLERS (SDL3 NORMALIZED: 0=LMB, 1=RMB, 2=MMB)
    // =================================================================================
    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = normalizeMouseButton(click.button());

        if (!dropdownPanelsMode) {
            float winX = (this.width - WINDOW_WIDTH) * 0.5f;
            float winY = (this.height - WINDOW_HEIGHT) * 0.5f;

            // 1. Theme Popup click
            if (themePopupOpen) {
                List<Theme> themes = ThemeManager.getInstance().getThemes();
                float popupX = winX + 10.0f;
                float popupY = winY + WINDOW_HEIGHT - 262.0f;
                float popupW = 166.0f;
                float itemH = 16.5f;
                float popupH = themes.size() * itemH + 10.0f;

                if (mouseX >= popupX && mouseX <= popupX + popupW && mouseY >= popupY && mouseY <= popupY + popupH) {
                    int idx = (int) ((mouseY - (popupY + 5.0f)) / itemH);
                    if (idx >= 0 && idx < themes.size()) {
                        ThemeManager.getInstance().setCurrentTheme(themes.get(idx));
                        themePopupOpen = false;
                        return true;
                    }
                } else {
                    themePopupOpen = false;
                }
            }

            // 2a. Sidebar Configs Button
            float cfgBtnX = winX + 8.0f;
            float cfgBtnY = winY + WINDOW_HEIGHT - 101.0f;
            float cfgBtnW = SIDEBAR_WIDTH - 16.0f;
            float cfgBtnH = 22.0f;
            if (mouseX >= cfgBtnX && mouseX <= cfgBtnX + cfgBtnW
                    && mouseY >= cfgBtnY && mouseY <= cfgBtnY + cfgBtnH) {
                configsViewOpen = !configsViewOpen;
                return true;
            }

            // 2. Sidebar Theme Button
            float themeBtnX = winX + 8.0f;
            float themeBtnY = winY + WINDOW_HEIGHT - 74.0f;
            float themeBtnW = SIDEBAR_WIDTH - 16.0f;
            float themeBtnH = 22.0f;
            if (mouseX >= themeBtnX && mouseX <= themeBtnX + themeBtnW
                    && mouseY >= themeBtnY && mouseY <= themeBtnY + themeBtnH) {
                if (button == 0) {
                    themePopupOpen = !themePopupOpen;
                } else if (button == 1) {
                    ThemeManager.getInstance().nextTheme();
                }
                return true;
            }

            // 3. Sidebar Category Tabs
            float listStartY = winY + 48.0f;
            float itemH = 26.0f;
            float stepY = itemH + 5.0f;
            float itemX = winX + 8.0f;
            float itemW = SIDEBAR_WIDTH - 16.0f;
            Category[] categories = Category.values();
            for (int i = 0; i < categories.length; i++) {
                float cy = listStartY + i * stepY;
                if (mouseX >= itemX && mouseX <= itemX + itemW && mouseY >= cy && mouseY <= cy + itemH) {
                    selectedCategory = categories[i];
                    persistentCategory = selectedCategory;
                    configsViewOpen = false;
                    targetScroll = 0.0f;
                    currentScroll = 0.0f;
                    return true;
                }
            }

            // 4. Content Header Controls
            float contentX = winX + SIDEBAR_WIDTH;
            float contentY = winY;
            float headerY = contentY + 11.0f;
            float headerH = 22.0f;

            // Layout switcher button
            float searchW = 132.0f;
            float bindBtnW = 78.0f;
            float layoutBtnW = 98.0f;
            float layoutBtnX = contentX + CONTENT_WIDTH - searchW - bindBtnW - layoutBtnW - 24.0f;
            if (mouseX >= layoutBtnX && mouseX <= layoutBtnX + layoutBtnW
                    && mouseY >= headerY && mouseY <= headerY + headerH) {
                dropdownPanelsMode = !dropdownPanelsMode;
                return true;
            }

            // Bound Only filter button (or "+ Save New" in Configs View)
            float bindBtnX = contentX + CONTENT_WIDTH - searchW - bindBtnW - 16.0f;
            if (mouseX >= bindBtnX && mouseX <= bindBtnX + bindBtnW
                    && mouseY >= headerY && mouseY <= headerY + headerH) {
                if (configsViewOpen) {
                    String targetName = searchQuery.isBlank() ? ConfigManager.getInstance().getActiveConfigName() : searchQuery.trim();
                    if (ConfigManager.getInstance().saveConfig(targetName)) {
                        statusToast = "✓ Saved '" + ConfigManager.getInstance().getActiveConfigName() + "'";
                        statusToastTime = System.currentTimeMillis();
                        searchQuery = "";
                        searchFocused = false;
                    }
                } else {
                    bindOnlyFilter = !bindOnlyFilter;
                    targetScroll = 0.0f;
                    currentScroll = 0.0f;
                }
                return true;
            }

            // Search box
            float searchX = contentX + CONTENT_WIDTH - searchW - 8.0f;
            if (mouseX >= searchX && mouseX <= searchX + searchW
                    && mouseY >= headerY && mouseY <= headerY + headerH) {
                if (!searchQuery.isEmpty() && mouseX >= searchX + searchW - 15.0f) {
                    searchQuery = "";
                } else if (button == 1) {
                    searchQuery = "";
                    searchFocused = true;
                } else {
                    searchFocused = true;
                }
                return true;
            } else {
                searchFocused = false;
            }

            // 4b. Configs Manager View Clicks
            if (configsViewOpen) {
                ConfigManager cm = ConfigManager.getInstance();
                List<String> configs = cm.listConfigs();
                float gridStartY = (winY + HEADER_HEIGHT) + 8.0f + 34.0f + 8.0f;
                float col0X = contentX + 10.0f;
                float col1X = col0X + CARD_WIDTH + 10.0f;
                float cardH = 48.0f;
                float gap = 8.0f;

                for (int i = 0; i < configs.size(); i++) {
                    String cfgName = configs.get(i);
                    int col = i % 2;
                    int row = i / 2;
                    float cx = (col == 0) ? col0X : col1X;
                    float cy = gridStartY + row * (cardH + gap);

                    float btnY = cy + 25.0f;
                    float btnH = 16.5f;
                    float loadX = cx + 8.0f;
                    float loadW = 54.0f;
                    float saveX = loadX + loadW + 5.0f;
                    float saveW = 54.0f;
                    float autoX = saveX + saveW + 5.0f;
                    float autoW = 68.0f;
                    float delX = autoX + autoW + 5.0f;
                    float delW = 24.0f;

                    if (mouseY >= btnY && mouseY <= btnY + btnH) {
                        if (mouseX >= loadX && mouseX <= loadX + loadW) {
                            if (cm.loadConfig(cfgName)) {
                                statusToast = "✓ Loaded '" + cfgName + "'";
                                statusToastTime = System.currentTimeMillis();
                            }
                            return true;
                        }
                        if (mouseX >= saveX && mouseX <= saveX + saveW) {
                            if (cm.saveConfig(cfgName)) {
                                statusToast = "✓ Saved '" + cfgName + "'";
                                statusToastTime = System.currentTimeMillis();
                            }
                            return true;
                        }
                        if (mouseX >= autoX && mouseX <= autoX + autoW) {
                            cm.setAutoLoadConfig(cfgName);
                            statusToast = "★ Auto-Load set to '" + cfgName + "'";
                            statusToastTime = System.currentTimeMillis();
                            return true;
                        }
                        if (!"Default".equalsIgnoreCase(cfgName) && mouseX >= delX && mouseX <= delX + delW) {
                            if (cm.deleteConfig(cfgName)) {
                                statusToast = "Deleted '" + cfgName + "'";
                                statusToastTime = System.currentTimeMillis();
                            }
                            return true;
                        }
                    }
                }
                return true;
            }

            // 5. Masonry Grid Module Cards Click
            float viewportY = winY + HEADER_HEIGHT;
            float viewportH = WINDOW_HEIGHT - HEADER_HEIGHT - 6.0f;
            if (mouseX >= contentX && mouseX <= winX + WINDOW_WIDTH
                    && mouseY >= viewportY && mouseY <= viewportY + viewportH) {

                for (Module m : getFilteredSidebarModules()) {
                    CardBox box = cardBoxes.get(m);
                    if (box == null) continue;

                    if (mouseX >= box.x && mouseX <= box.x + box.width
                            && mouseY >= box.y && mouseY <= box.y + box.height) {

                        // Header Click
                        if (mouseY <= box.y + CARD_HEADER_HEIGHT) {
                            // MMB -> Quick Keybind toggle
                            if (button == 2) {
                                bindingModule = (bindingModule == m ? null : m);
                                return true;
                            }

                            // Switch pill click
                            if (mouseX >= box.switchX && mouseX <= box.switchX + box.switchW
                                    && mouseY >= box.switchY - 2.0f && mouseY <= box.switchY + box.switchH + 2.0f) {
                                if (button == 0) {
                                    m.toggle();
                                    return true;
                                } else if (button == 1) {
                                    m.toggleExpanded();
                                    return true;
                                }
                            }

                            // Keybind badge click
                            if (box.kbVisible && mouseX >= box.kbX && mouseX <= box.kbX + box.kbW
                                    && mouseY >= box.kbY - 2.0f && mouseY <= box.kbY + box.kbH + 2.0f) {
                                if (button == 0) {
                                    bindingModule = (bindingModule == m ? null : m);
                                    return true;
                                } else if (button == 1) {
                                    m.setKeyBind(0);
                                    bindingModule = null;
                                    return true;
                                }
                            }

                            // Expand chevron click
                            if (box.chevVisible && mouseX >= box.chevX && mouseX <= box.chevX + box.chevW
                                    && mouseY >= box.chevY - 2.0f && mouseY <= box.chevY + box.chevH + 2.0f) {
                                m.toggleExpanded();
                                return true;
                            }

                            // General header click: LMB toggles module, RMB expands settings
                            if (button == 0) {
                                m.toggle();
                                return true;
                            } else if (button == 1) {
                                m.toggleExpanded();
                                return true;
                            }
                        }

                        // Expanded Settings Area Click
                        if (m.isExpanded() && mouseY > box.y + CARD_HEADER_HEIGHT) {
                            if (handleSettingsClick(m, box.x, box.y, box.width, CARD_HEADER_HEIGHT, mouseX, mouseY, button)) {
                                return true;
                            }
                        }
                    }
                }
            }
        } else {
            // Dropdown Panels Mode top bar
            float barW = 360.0f;
            float barX = (this.width - barW) * 0.5f;
            float barY = 6.0f;
            float layoutBtnW = 100.0f;
            float layoutBtnH = 16.0f;
            float layoutBtnX = barX + barW - layoutBtnW - 6.0f;
            float layoutBtnY = barY + 3.0f;

            if (mouseX >= layoutBtnX && mouseX <= layoutBtnX + layoutBtnW
                    && mouseY >= layoutBtnY && mouseY <= layoutBtnY + layoutBtnH) {
                dropdownPanelsMode = !dropdownPanelsMode;
                return true;
            }

            float thBtnW = 110.0f;
            float thBtnX = layoutBtnX - thBtnW - 6.0f;
            if (mouseX >= thBtnX && mouseX <= thBtnX + thBtnW
                    && mouseY >= layoutBtnY && mouseY <= layoutBtnY + layoutBtnH) {
                ThemeManager.getInstance().nextTheme();
                return true;
            }

            for (DropdownPanel panel : dropdownPanels) {
                if (panel.mouseClicked(mouseX, mouseY, button)) {
                    return true;
                }
            }
        }

        return super.mouseClicked(click, doubled);
    }

    private boolean handleSettingsClick(Module m, float cardX, float cardY, float cardW, float headerH,
                                        double mouseX, double mouseY, int button) {
        float setX = cardX + 7.0f;
        float setW = cardW - 14.0f;
        float curY = cardY + headerH + 4.0f;

        for (Setting<?> setting : m.getSettings()) {
            if (!setting.isVisible()) continue;

            if (setting instanceof ModeSetting modeSetting) {
                float rowH = 22.0f;
                if (mouseY >= curY && mouseY <= curY + rowH) {
                    if (button == 0) {
                        modeSetting.cycle(true);
                    } else if (button == 1) {
                        modeSetting.cycle(false);
                    }
                    return true;
                }
                curY += rowH + 3.0f;

            } else if (setting instanceof NumberSetting numSetting) {
                float rowH = 24.0f;
                if (mouseY >= curY && mouseY <= curY + rowH) {
                    float trackX = setX + 2.0f;
                    float trackW = setW - 4.0f;
                    draggingSlider = numSetting;
                    draggingSliderTrackX = trackX;
                    draggingSliderTrackW = trackW;
                    updateSlider(numSetting, mouseX, trackX, trackW);
                    return true;
                }
                curY += rowH + 4.0f;

            } else if (setting instanceof BooleanSetting boolSetting) {
                float rowH = 20.0f;
                if (mouseY >= curY && mouseY <= curY + rowH) {
                    boolSetting.toggle();
                    return true;
                }
                curY += rowH + 3.0f;

            } else if (setting instanceof MultiBoxSetting mb) {
                float chipX = setX + 2.0f;
                float chipY = curY + 14.0f;
                for (String opt : mb.getOptions()) {
                    float optScale = 0.62f;
                    float optW = Render2DHelper.getTextWidth(opt, optScale) + 8.0f;
                    if (chipX + optW > setX + setW) {
                        chipX = setX + 2.0f;
                        chipY += 16.0f;
                    }
                    if (mouseX >= chipX && mouseX <= chipX + optW && mouseY >= chipY && mouseY <= chipY + 13.0f) {
                        mb.toggle(opt);
                        return true;
                    }
                    chipX += optW + 4.0f;
                }
                float curRowW = 0.0f;
                int rows = 1;
                for (String opt : mb.getOptions()) {
                    float optW = Render2DHelper.getTextWidth(opt, 0.62f) + 8.0f;
                    if (curRowW > 0.0f && curRowW + optW > setW) {
                        rows++;
                        curRowW = optW + 4.0f;
                    } else {
                        curRowW += optW + 4.0f;
                    }
                }
                curY += 16.0f + rows * 16.0f + 3.0f;

            } else if (setting instanceof ColorSetting colorSetting) {
                float rowH = 20.0f;
                if (mouseY >= curY && mouseY <= curY + rowH) {
                    int cur = colorSetting.getColor();
                    colorSetting.setValue(cur ^ 0x00FFFFFF);
                    return true;
                }
                curY += rowH + 3.0f;
            }
        }

        // Keybind row at bottom of settings
        if (mouseY >= curY && mouseY <= curY + 20.0f) {
            if (button == 0) {
                bindingModule = (bindingModule == m ? null : m);
                return true;
            } else if (button == 1) {
                m.setKeyBind(0);
                bindingModule = null;
                return true;
            }
        }

        return false;
    }

    private void updateSlider(NumberSetting setting, double mouseX, float trackX, float trackW) {
        double ratio = (mouseX - trackX) / trackW;
        ratio = Math.max(0.0, Math.min(1.0, ratio));
        double range = setting.getMax() - setting.getMin();
        double value = setting.getMin() + ratio * range;
        setting.setValueClamped(value);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent click) {
        draggingSlider = null;
        for (DropdownPanel panel : dropdownPanels) {
            panel.mouseReleased();
        }
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent click, double offsetX, double offsetY) {
        double mouseX = click.x();
        double mouseY = click.y();

        if (draggingSlider != null && draggingSliderTrackW > 0.0f) {
            updateSlider(draggingSlider, mouseX, draggingSliderTrackX, draggingSliderTrackW);
            return true;
        }

        return super.mouseDragged(click, offsetX, offsetY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (!dropdownPanelsMode) {
            targetScroll -= (float) (verticalAmount * 28.0f);
            if (targetScroll < 0.0f) targetScroll = 0.0f;
            if (targetScroll > maxScroll) targetScroll = maxScroll;
            return true;
        } else {
            for (DropdownPanel panel : dropdownPanels) {
                if (panel.mouseScrolled(mouseX, mouseY, verticalAmount)) {
                    return true;
                }
            }
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(KeyEvent input) {
        int key = input.key();

        // 1. Module keybinding
        if (bindingModule != null) {
            if (key == InputConstants.KEY_ESCAPE || key == InputConstants.KEY_DELETE || key == InputConstants.KEY_BACKSPACE) {
                bindingModule.setKeyBind(0);
            } else {
                bindingModule.setKeyBind(key);
            }
            bindingModule = null;
            return true;
        }

        // 2. Search Box typing
        if (!dropdownPanelsMode && searchFocused) {
            if (key == InputConstants.KEY_BACKSPACE) {
                if (!searchQuery.isEmpty()) {
                    searchQuery = searchQuery.substring(0, searchQuery.length() - 1);
                    targetScroll = 0.0f;
                }
                return true;
            } else if (key == InputConstants.KEY_ESCAPE || key == InputConstants.KEY_RETURN) {
                if (key == InputConstants.KEY_RETURN && configsViewOpen && !searchQuery.isBlank()) {
                    ConfigManager.getInstance().saveConfig(searchQuery.trim());
                    statusToast = "✓ Saved '" + ConfigManager.getInstance().getActiveConfigName() + "'";
                    statusToastTime = System.currentTimeMillis();
                    searchQuery = "";
                }
                searchFocused = false;
                return true;
            }
        }

        // 3. Close on Escape or Right Shift
        if (key == InputConstants.KEY_ESCAPE || key == InputConstants.KEY_RSHIFT) {
            this.onClose();
            return true;
        }

        return super.keyPressed(input);
    }

    @Override
    public void onClose() {
        try {
            ConfigManager.getInstance().saveConfig(ConfigManager.getInstance().getActiveConfigName());
        } catch (Throwable ignored) {
        }
        super.onClose();
    }

    @Override
    public boolean charTyped(CharacterEvent input) {
        if (!dropdownPanelsMode && searchFocused && input.isAllowedChatCharacter()) {
            searchQuery += input.codepointAsString();
            targetScroll = 0.0f;
            return true;
        }
        return super.charTyped(input);
    }
}
