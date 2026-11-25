package dev.opticumeclient.client.hud;

import dev.opticumeclient.client.config.ConfigManager;
import dev.opticumeclient.client.modules.Category;
import dev.opticumeclient.client.modules.Module;
import dev.opticumeclient.client.render.Animation;
import dev.opticumeclient.client.render.ColorUtils;
import dev.opticumeclient.client.render.Easing;
import dev.opticumeclient.client.render.Render2DHelper;
import dev.opticumeclient.client.render.Theme;
import dev.opticumeclient.client.render.ThemeManager;
import dev.opticumeclient.client.gui.ClickGuiScreen;
import net.minecraft.client.Minecraft; 
import net.minecraft.client.gui.GuiGraphicsExtractor; 
import net.minecraft.client.gui.screens.ChatScreen; 
import com.mojang.blaze3d.platform.InputConstants; 

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;

/**
 * Central HUD Manager for OpticumeClient (Minecraft 26.3).
 * Manages HUD modules and provides interactive Drag-and-Drop in ChatScreen
 * with magnetic alignment guides (Smart Snap Lines).
 */
public class HudManager {
    private static final HudManager INSTANCE = new HudManager();
    private static final float SNAP_DISTANCE = 5.5f;

    private final List<Module> hudModules = new ArrayList<>();
    private final List<DraggableWidget> widgets = new ArrayList<>();
    private final List<SnapLine> activeSnapLines = new ArrayList<>();

    private DraggableWidget draggedWidget = null;
    private float dragStartX = 0.0f;
    private float dragStartY = 0.0f;
    private boolean wasMouseDown = false;
    private boolean editMode = false;

    public record SnapLine(float x1, float y1, float x2, float y2, boolean vertical) {}

    public static class DraggableWidget {
        private final String name;
        private float x;
        private float y;
        private float width;
        private float height;
        private final float defaultX;
        private final float defaultY;
        private final BooleanSupplier enabledSupplier;
        private final BiConsumer<Float, Float> positionChangedCallback;
        private boolean dragging = false;
        private float dragOffsetX = 0.0f;
        private float dragOffsetY = 0.0f;
        private float scale = 1.0f;
        private final Animation hoverAnim = new Animation(0.0f, 160L, Easing.EASE_OUT_QUAD);

        public DraggableWidget(String name, float defaultX, float defaultY, float width, float height,
                               BooleanSupplier enabledSupplier, BiConsumer<Float, Float> positionChangedCallback) {
            this.name = name;
            this.x = defaultX;
            this.y = defaultY;
            this.defaultX = defaultX;
            this.defaultY = defaultY;
            this.width = width;
            this.height = height;
            this.enabledSupplier = enabledSupplier != null ? enabledSupplier : () -> true;
            this.positionChangedCallback = positionChangedCallback;
        }

        public DraggableWidget(String name, float defaultX, float defaultY, float width, float height,
                               BooleanSupplier enabledSupplier) {
            this(name, defaultX, defaultY, width, height, enabledSupplier, null);
        }

        public boolean isEnabled() {
            return enabledSupplier.getAsBoolean();
        }

        public boolean isHovered(float mouseX, float mouseY) {
            return mouseX >= x && mouseX <= x + getScaledWidth() && mouseY >= y && mouseY <= y + getScaledHeight();
        }

        public void setPosition(float newX, float newY) {
            this.x = newX;
            this.y = newY;
            if (positionChangedCallback != null) {
                positionChangedCallback.accept(this.x, this.y);
            }
        }

        public void syncExternalPosition(float extX, float extY) {
            if (!dragging) {
                this.x = extX;
                this.y = extY;
            }
        }

        public void setBounds(float width, float height) {
            this.width = Math.max(24.0f, width);
            this.height = Math.max(14.0f, height);
        }

        public void resetPosition() {
            setPosition(defaultX, defaultY);
            this.scale = 1.0f;
            this.dragging = false;
        }

        public void renderEditFrame(GuiGraphicsExtractor context, float mouseX, float mouseY) {
            boolean hovered = isHovered(mouseX, mouseY);
            hoverAnim.animateTo((hovered || dragging) ? 1.0f : 0.0f, 150L, Easing.EASE_OUT_QUAD);
            float anim = hoverAnim.getValue();

            Theme theme = ThemeManager.getInstance().getCurrentTheme();
            int accent = theme.getPrimaryColor();
            int accent2 = theme.getSecondaryColor();

            float sw = getScaledWidth();
            float sh = getScaledHeight();

            // Subtle outline for all active widgets in ChatScreen, brighter when hovered/dragging
            int baseOutlineAlpha = (int) (45 + 190 * anim);
            int outlineColor = dragging
                    ? accent2
                    : ColorUtils.withAlpha(accent, baseOutlineAlpha);

            Render2DHelper.drawRoundedOutline(
                    context,
                    x - 2.0f,
                    y - 2.0f,
                    sw + 4.0f,
                    sh + 4.0f,
                    5.0f,
                    dragging ? 1.5f : 1.0f,
                    outlineColor
            );

            // Corner anchor handles when hovered or dragging
            if (anim > 0.05f || dragging) {
                int handleColor = ColorUtils.withAlpha(accent2, (int) (235 * Math.max(0.4f, anim)));
                float hs = 3.0f;
                Render2DHelper.drawRect(context, x - 2.5f, y - 2.5f, hs, hs, handleColor);
                Render2DHelper.drawRect(context, x + sw - 0.5f, y - 2.5f, hs, hs, handleColor);
                Render2DHelper.drawRect(context, x - 2.5f, y + sh - 0.5f, hs, hs, handleColor);
                Render2DHelper.drawRect(context, x + sw - 0.5f, y + sh - 0.5f, hs, hs, handleColor);

                // Floating label pill above or below the widget
                String scaleTag = Math.abs(scale - 1.0f) > 0.02f ? String.format(" %.2fx", scale) : "";
                String badge = name + scaleTag + (dragging ? String.format(" [%d, %d]", Math.round(x), Math.round(y)) : "");
                float badgeScale = 0.70f;
                float textW = Render2DHelper.getTextWidth(badge, badgeScale);
                float pillW = textW + 8.0f;
                float pillH = 11.0f;
                float pillX = x - 2.0f;
                float pillY = (y > 16.0f) ? (y - pillH - 3.5f) : (y + sh + 3.5f);

                int badgeBg = ColorUtils.withAlpha(theme.getBackgroundColor(), (int) (225 * Math.max(0.6f, anim)));
                Render2DHelper.drawRoundedRect(context, pillX, pillY, pillW, pillH, 3.0f, badgeBg);
                Render2DHelper.drawRoundedOutline(context, pillX, pillY, pillW, pillH, 3.0f, 1.0f, outlineColor);
                Render2DHelper.drawScaledText(context, badge, pillX + 4.0f, pillY + 2.5f,
                        ColorUtils.withAlpha(0xFFFFFFFF, (int) (255 * Math.max(0.65f, anim))), badgeScale, false);
            }
        }

        public String getName() { return name; }
        public float getX() { return x; }
        public float getY() { return y; }
        public float getWidth() { return width; }
        public float getHeight() { return height; }
        public float getScale() { return scale; }
        public void setScale(float scale) { this.scale = net.minecraft.util.Mth.clamp(scale, 0.55f, 1.65f); }
        public float getScaledWidth() { return width * scale; }
        public float getScaledHeight() { return height * scale; }
        public boolean isDragging() { return dragging; }
        public void setDragging(boolean dragging) { this.dragging = dragging; }
        public float getDragOffsetX() { return dragOffsetX; }
        public void setDragOffsetX(float dragOffsetX) { this.dragOffsetX = dragOffsetX; }
        public float getDragOffsetY() { return dragOffsetY; }
        public void setDragOffsetY(float dragOffsetY) { this.dragOffsetY = dragOffsetY; }
    }

    private HudManager() {}

    public static HudManager getInstance() {
        return INSTANCE;
    }

    public void register(Module module) {
        if (module != null && module.getCategory() == Category.HUD && !hudModules.contains(module)) {
            hudModules.add(module);
        }
    }

    public DraggableWidget registerWidget(DraggableWidget widget) {
        if (widget != null) {
            widgets.removeIf(w -> w.getName().equalsIgnoreCase(widget.getName()));
            widgets.add(widget);
        }
        return widget;
    }

    public DraggableWidget getWidget(String name) {
        for (DraggableWidget w : widgets) {
            if (w.getName().equalsIgnoreCase(name)) {
                return w;
            }
        }
        return null;
    }

    public List<DraggableWidget> getWidgets() {
        return Collections.unmodifiableList(widgets);
    }

    public boolean isAnyWidgetHovered(float mouseX, float mouseY) {
        for (DraggableWidget w : widgets) {
            if (w.isEnabled() && w.isHovered(mouseX, mouseY)) {
                return true;
            }
        }
        return false;
    }

    public void resetAllLayouts() {
        for (DraggableWidget w : widgets) {
            w.resetPosition();
        }
        activeSnapLines.clear();
        ConfigManager.getInstance().saveHudChangeQuietly();
    }

    public boolean isEditMode() {
        return editMode;
    }

    public void render(GuiGraphicsExtractor context, float tickDelta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getWindow() == null) return;

        boolean inChat = mc.gui.screen() instanceof ChatScreen;
        boolean inClickGui = mc.gui.screen() instanceof ClickGuiScreen;
        this.editMode = inChat || inClickGui;

        float screenW = mc.getWindow().getGuiScaledWidth();
        float screenH = mc.getWindow().getGuiScaledHeight();

        // Viewport bounds clamping: ensure widgets stay inside visible screen on scale/resolution changes
        for (DraggableWidget w : widgets) {
            if (!w.isDragging() && !"TabList".equals(w.getName())) {
                float maxX = Math.max(2.0f, screenW - w.getScaledWidth() - 2.0f);
                float maxY = Math.max(2.0f, screenH - w.getScaledHeight() - 2.0f);
                float curX = net.minecraft.util.Mth.clamp(w.getX(), 2.0f, maxX);
                float curY = net.minecraft.util.Mth.clamp(w.getY(), 2.0f, maxY);
                if (Math.abs(curX - w.getX()) > 0.1f || Math.abs(curY - w.getY()) > 0.1f) {
                    w.setPosition(curX, curY);
                }
            }
        }

        float mouseX = (float) mc.mouseHandler.getScaledXPos(mc.getWindow());
        float mouseY = (float) mc.mouseHandler.getScaledYPos(mc.getWindow());

        // ClickGUI window bounds check (avoid grabbing widgets through the ClickGUI window)
        boolean overClickGuiWindow = inClickGui && ClickGuiScreen.isMouseOverWindow(mouseX, mouseY);

        // Handle mouse polling in ChatScreen & ClickGuiScreen before rendering widgets so positions update with zero lag
        if (inChat || inClickGui) {
            boolean lmbDown = mc.mouseHandler.isLeftPressed();

            if (lmbDown && !wasMouseDown) {
                // Avoid grabbing widgets when clicking inside the bottom chat text input bar or inside ClickGUI window
                boolean canStartDrag = (inChat && mouseY < screenH - 26.0f) || (inClickGui && !overClickGuiWindow);
                if (canStartDrag) {
                    for (int i = widgets.size() - 1; i >= 0; i--) {
                        DraggableWidget w = widgets.get(i);
                        if (w.isEnabled() && w.isHovered(mouseX, mouseY)) {
                            draggedWidget = w;
                            dragStartX = w.getX();
                            dragStartY = w.getY();
                            w.setDragging(true);
                            w.setDragOffsetX(mouseX - w.getX());
                            w.setDragOffsetY(mouseY - w.getY());
                            break;
                        }
                    }
                }
            } else if (lmbDown && draggedWidget != null) {
                if (!draggedWidget.isEnabled()) {
                    draggedWidget.setDragging(false);
                    draggedWidget = null;
                    activeSnapLines.clear();
                } else {
                    float rawX = mouseX - draggedWidget.getDragOffsetX();
                    float rawY = mouseY - draggedWidget.getDragOffsetY();
                    float clampedX = Math.max(2.0f, Math.min(screenW - draggedWidget.getScaledWidth() - 2.0f, rawX));
                    float clampedY = Math.max(2.0f, Math.min(screenH - draggedWidget.getScaledHeight() - 2.0f, rawY));

                    draggedWidget.setPosition(clampedX, clampedY);
                    calculateSmartSnapping(draggedWidget, screenW, screenH);
                }
            } else if (!lmbDown && draggedWidget != null) {
                boolean moved = Math.abs(draggedWidget.getX() - dragStartX) > 0.5f
                        || Math.abs(draggedWidget.getY() - dragStartY) > 0.5f;
                draggedWidget.setDragging(false);
                draggedWidget = null;
                activeSnapLines.clear();
                if (moved) {
                    ConfigManager.getInstance().saveHudChangeQuietly();
                }
            }

            wasMouseDown = lmbDown;
        } else {
            if (draggedWidget != null) {
                boolean moved = Math.abs(draggedWidget.getX() - dragStartX) > 0.5f
                        || Math.abs(draggedWidget.getY() - dragStartY) > 0.5f;
                draggedWidget.setDragging(false);
                draggedWidget = null;
                if (moved) {
                    ConfigManager.getInstance().saveHudChangeQuietly();
                }
            }
            activeSnapLines.clear();
            wasMouseDown = false;
        }

        // Render all enabled HUD modules (applying individual widget scale around top-left origin)
        for (Module m : hudModules) {
            if (!m.isEnabled()) continue;
            DraggableWidget w = getWidget(m.getName());
            float sc = (w != null) ? w.getScale() : 1.0f;
            if (w != null && Math.abs(sc - 1.0f) > 0.005f) {
                var pose = context.pose();
                pose.pushMatrix();
                pose.translate(w.getX(), w.getY());
                pose.scale(sc, sc);
                pose.translate(-w.getX(), -w.getY());
                try {
                    m.onRender2D(context, tickDelta);
                } finally {
                    pose.popMatrix();
                }
            } else {
                m.onRender2D(context, tickDelta);
            }
        }

        // Render edit overlays & Smart Snap Lines when ChatScreen or ClickGuiScreen is open
        if (inChat || inClickGui) {
            renderSnapGuides(context);
            float effectiveMouseX = (inClickGui && overClickGuiWindow && draggedWidget == null) ? -10000.0f : mouseX;
            float effectiveMouseY = (inClickGui && overClickGuiWindow && draggedWidget == null) ? -10000.0f : mouseY;
            for (DraggableWidget w : widgets) {
                if (w.isEnabled()) {
                    w.renderEditFrame(context, effectiveMouseX, effectiveMouseY);
                }
            }
            if (inChat) {
                renderEditorHintBanner(context, screenW, screenH);
            }
            renderWidgetContextMenu(context, mouseX, mouseY, screenW, screenH);
        } else {
            menuWidget = null;
        }
    }

    private void calculateSmartSnapping(DraggableWidget el, float screenW, float screenH) {
        activeSnapLines.clear();

        float curX = el.getX();
        float curY = el.getY();
        float w = el.getWidth();
        float h = el.getHeight();

        float snappedX = curX;
        float snappedY = curY;
        boolean xSnapped = false;
        boolean ySnapped = false;

        // 1. Screen Margins & Center Snapping (X axis)
        float margin = 6.0f;
        if (Math.abs(curX - margin) <= SNAP_DISTANCE) {
            snappedX = margin;
            xSnapped = true;
            activeSnapLines.add(new SnapLine(margin, 0.0f, margin, screenH, true));
        } else if (Math.abs((curX + w) - (screenW - margin)) <= SNAP_DISTANCE) {
            snappedX = screenW - w - margin;
            xSnapped = true;
            activeSnapLines.add(new SnapLine(screenW - margin, 0.0f, screenW - margin, screenH, true));
        } else if (Math.abs((curX + w * 0.5f) - (screenW * 0.5f)) <= SNAP_DISTANCE) {
            snappedX = (screenW - w) * 0.5f;
            xSnapped = true;
            activeSnapLines.add(new SnapLine(screenW * 0.5f, 0.0f, screenW * 0.5f, screenH, true));
        }

        // 2. Screen Margins & Center Snapping (Y axis)
        if (Math.abs(curY - margin) <= SNAP_DISTANCE) {
            snappedY = margin;
            ySnapped = true;
            activeSnapLines.add(new SnapLine(0.0f, margin, screenW, margin, false));
        } else if (Math.abs((curY + h) - (screenH - margin)) <= SNAP_DISTANCE) {
            snappedY = screenH - h - margin;
            ySnapped = true;
            activeSnapLines.add(new SnapLine(0.0f, screenH - margin, screenW, screenH - margin, false));
        } else if (Math.abs((curY + h * 0.5f) - (screenH * 0.5f)) <= SNAP_DISTANCE) {
            snappedY = (screenH - h) * 0.5f;
            ySnapped = true;
            activeSnapLines.add(new SnapLine(0.0f, screenH * 0.5f, screenW, screenH * 0.5f, false));
        }

        // 3. Neighbor Widget Magnetic Snapping
        for (DraggableWidget other : widgets) {
            if (other == el || !other.isEnabled()) continue;

            float ox = other.getX();
            float oy = other.getY();
            float ow = other.getWidth();
            float oh = other.getHeight();

            if (!xSnapped) {
                // Left-to-Left
                if (Math.abs(curX - ox) <= SNAP_DISTANCE) {
                    snappedX = ox;
                    xSnapped = true;
                    float minY = Math.min(curY, oy) - 6.0f;
                    float maxY = Math.max(curY + h, oy + oh) + 6.0f;
                    activeSnapLines.add(new SnapLine(ox, minY, ox, maxY, true));
                }
                // Right-to-Right
                else if (Math.abs((curX + w) - (ox + ow)) <= SNAP_DISTANCE) {
                    snappedX = ox + ow - w;
                    xSnapped = true;
                    float lineX = ox + ow;
                    float minY = Math.min(curY, oy) - 6.0f;
                    float maxY = Math.max(curY + h, oy + oh) + 6.0f;
                    activeSnapLines.add(new SnapLine(lineX, minY, lineX, maxY, true));
                }
                // CenterX-to-CenterX
                else if (Math.abs((curX + w * 0.5f) - (ox + ow * 0.5f)) <= SNAP_DISTANCE) {
                    snappedX = ox + ow * 0.5f - w * 0.5f;
                    xSnapped = true;
                    float lineX = ox + ow * 0.5f;
                    float minY = Math.min(curY, oy) - 6.0f;
                    float maxY = Math.max(curY + h, oy + oh) + 6.0f;
                    activeSnapLines.add(new SnapLine(lineX, minY, lineX, maxY, true));
                }
                // Left-to-Right (with 4px gap or flush)
                else if (Math.abs(curX - (ox + ow + 4.0f)) <= SNAP_DISTANCE) {
                    snappedX = ox + ow + 4.0f;
                    xSnapped = true;
                    float lineX = ox + ow + 2.0f;
                    float minY = Math.min(curY, oy) - 6.0f;
                    float maxY = Math.max(curY + h, oy + oh) + 6.0f;
                    activeSnapLines.add(new SnapLine(lineX, minY, lineX, maxY, true));
                }
                // Right-to-Left (with 4px gap)
                else if (Math.abs((curX + w) - (ox - 4.0f)) <= SNAP_DISTANCE) {
                    snappedX = ox - w - 4.0f;
                    xSnapped = true;
                    float lineX = ox - 2.0f;
                    float minY = Math.min(curY, oy) - 6.0f;
                    float maxY = Math.max(curY + h, oy + oh) + 6.0f;
                    activeSnapLines.add(new SnapLine(lineX, minY, lineX, maxY, true));
                }
            }

            if (!ySnapped) {
                // Top-to-Top
                if (Math.abs(curY - oy) <= SNAP_DISTANCE) {
                    snappedY = oy;
                    ySnapped = true;
                    float minX = Math.min(snappedX, ox) - 6.0f;
                    float maxX = Math.max(snappedX + w, ox + ow) + 6.0f;
                    activeSnapLines.add(new SnapLine(minX, oy, maxX, oy, false));
                }
                // Bottom-to-Bottom
                else if (Math.abs((curY + h) - (oy + oh)) <= SNAP_DISTANCE) {
                    snappedY = oy + oh - h;
                    ySnapped = true;
                    float lineY = oy + oh;
                    float minX = Math.min(snappedX, ox) - 6.0f;
                    float maxX = Math.max(snappedX + w, ox + ow) + 6.0f;
                    activeSnapLines.add(new SnapLine(minX, lineY, maxX, lineY, false));
                }
                // CenterY-to-CenterY
                else if (Math.abs((curY + h * 0.5f) - (oy + oh * 0.5f)) <= SNAP_DISTANCE) {
                    snappedY = oy + oh * 0.5f - h * 0.5f;
                    ySnapped = true;
                    float lineY = oy + oh * 0.5f;
                    float minX = Math.min(snappedX, ox) - 6.0f;
                    float maxX = Math.max(snappedX + w, ox + ow) + 6.0f;
                    activeSnapLines.add(new SnapLine(minX, lineY, maxX, lineY, false));
                }
                // Top-to-Bottom (with 4px gap)
                else if (Math.abs(curY - (oy + oh + 4.0f)) <= SNAP_DISTANCE) {
                    snappedY = oy + oh + 4.0f;
                    ySnapped = true;
                    float lineY = oy + oh + 2.0f;
                    float minX = Math.min(snappedX, ox) - 6.0f;
                    float maxX = Math.max(snappedX + w, ox + ow) + 6.0f;
                    activeSnapLines.add(new SnapLine(minX, lineY, maxX, lineY, false));
                }
                // Bottom-to-Top (with 4px gap)
                else if (Math.abs((curY + h) - (oy - 4.0f)) <= SNAP_DISTANCE) {
                    snappedY = oy - h - 4.0f;
                    ySnapped = true;
                    float lineY = oy - 2.0f;
                    float minX = Math.min(snappedX, ox) - 6.0f;
                    float maxX = Math.max(snappedX + w, ox + ow) + 6.0f;
                    activeSnapLines.add(new SnapLine(minX, lineY, maxX, lineY, false));
                }
            }
        }

        if (xSnapped || ySnapped) {
            el.setPosition(snappedX, snappedY);
        }
    }

    private void renderSnapGuides(GuiGraphicsExtractor context) {
        if (activeSnapLines.isEmpty()) return;

        Theme theme = ThemeManager.getInstance().getCurrentTheme();
        int primary = theme.getPrimaryColor();
        int secondary = theme.getSecondaryColor();
        int coreColor = ColorUtils.interpolateColor(primary, secondary, 0.5f);
        int glowColor = ColorUtils.withAlpha(coreColor, 85);

        for (SnapLine line : activeSnapLines) {
            if (line.vertical()) {
                float h = Math.max(1.0f, line.y2() - line.y1());
                Render2DHelper.drawRect(context, line.x1() - 1.5f, line.y1(), 3.0f, h, glowColor);
                Render2DHelper.drawRect(context, line.x1() - 0.5f, line.y1(), 1.0f, h, coreColor);
                Render2DHelper.drawCircle(context, line.x1(), (line.y1() + line.y2()) * 0.5f, 2.5f, 0xFFFFFFFF);
            } else {
                float w = Math.max(1.0f, line.x2() - line.x1());
                Render2DHelper.drawRect(context, line.x1(), line.y1() - 1.5f, w, 3.0f, glowColor);
                Render2DHelper.drawRect(context, line.x1(), line.y1() - 0.5f, w, 1.0f, coreColor);
                Render2DHelper.drawCircle(context, (line.x1() + line.x2()) * 0.5f, line.y1(), 2.5f, 0xFFFFFFFF);
            }
        }
    }

    private void renderEditorHintBanner(GuiGraphicsExtractor context, float screenW, float screenH) {
        String hint = "HUD Layout Editor • Drag widgets with LMB (Smart Snap Active)";
        float scale = 0.72f;
        float textW = Render2DHelper.getTextWidth(hint, scale);
        float boxW = textW + 16.0f;
        float boxH = 14.0f;
        float boxX = (screenW - boxW) * 0.5f;
        float boxY = screenH - 36.0f;

        Theme theme = ThemeManager.getInstance().getCurrentTheme();
        Render2DHelper.drawRoundedRect(context, boxX, boxY, boxW, boxH, 4.0f,
                ColorUtils.withAlpha(theme.getBackgroundColor(), 200));
        Render2DHelper.drawRoundedOutline(context, boxX, boxY, boxW, boxH, 4.0f, 1.0f,
                ColorUtils.withAlpha(theme.getPrimaryColor(), 110));
        Render2DHelper.drawScaledText(context, hint, boxX + 8.0f, boxY + 3.5f, theme.getTextColor(), scale, false);
    }

    private DraggableWidget menuWidget = null;
    private float menuOpenAnim = 0.0f;
    private float ctxMenuX, ctxMenuY, ctxMenuW, ctxMenuRowH;
    private long ctxLastNanos = System.nanoTime();

    public void onMousePress(int button) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getWindow() == null || mc.gui == null) return;
        var iface = dev.opticumeclient.client.modules.hud.InterfaceModule.getInstance();
        if (iface == null || !iface.isRightClickMenuEnabled()) {
            menuWidget = null;
            return;
        }
        boolean inChat = mc.gui.screen() instanceof ChatScreen;
        boolean inGui = mc.gui.screen() instanceof ClickGuiScreen;
        if (!inChat && !inGui) {
            menuWidget = null;
            return;
        }

        float mx = (float) mc.mouseHandler.getScaledXPos(mc.getWindow());
        float my = (float) mc.mouseHandler.getScaledYPos(mc.getWindow());
        if (inGui && ClickGuiScreen.isMouseOverWindow(mx, my)) return;

        if (menuWidget != null) {
            String[] opts = getMenuOptions(menuWidget);
            if (mx >= ctxMenuX && mx <= ctxMenuX + ctxMenuW && my >= ctxMenuY && my <= ctxMenuY + ctxMenuRowH * opts.length) {
                int idx = (int) ((my - ctxMenuY) / ctxMenuRowH);
                if (idx == 0) {
                    iface.setWidgetBgOverride(menuWidget.getName(), "solid");
                } else if (idx == 1) {
                    iface.setWidgetBgOverride(menuWidget.getName(), "glass");
                } else if (idx == 2) {
                    iface.setWidgetBgOverride(menuWidget.getName(), "global");
                } else if (idx == 3) {
                    menuWidget.setScale(menuWidget.getScale() + 0.10f);
                    ConfigManager.getInstance().saveHudChangeQuietly();
                    return;
                } else if (idx == 4) {
                    menuWidget.setScale(menuWidget.getScale() - 0.10f);
                    ConfigManager.getInstance().saveHudChangeQuietly();
                    return;
                } else if (idx == 5 && "Watermark".equals(menuWidget.getName())) {
                    int sw = mc.getWindow().getGuiScaledWidth();
                    menuWidget.setPosition((sw - menuWidget.getScaledWidth()) * 0.5f, 6.0f);
                    menuWidget = null;
                } else if (idx == 6 && "Watermark".equals(menuWidget.getName())) {
                    menuWidget.setPosition(6.0f, 6.0f);
                    menuWidget = null;
                }
                return;
            }
        }

        if (button == 1) {
            DraggableWidget hit = null;
            for (int i = widgets.size() - 1; i >= 0; i--) {
                DraggableWidget w = widgets.get(i);
                if (w.isEnabled() && w.isHovered(mx, my)) {
                    hit = w;
                    break;
                }
            }
            menuWidget = (hit != null && hit != menuWidget) ? hit : null;
        } else {
            menuWidget = null;
        }
    }

    public boolean onMouseScroll(double verticalAmount) {
        if (verticalAmount == 0.0) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getWindow() == null || mc.gui == null) return false;
        boolean inChat = mc.gui.screen() instanceof ChatScreen;
        boolean inGui = mc.gui.screen() instanceof ClickGuiScreen;
        if (!inChat && !inGui) return false;

        float mx = (float) mc.mouseHandler.getScaledXPos(mc.getWindow());
        float my = (float) mc.mouseHandler.getScaledYPos(mc.getWindow());
        if (inGui && ClickGuiScreen.isMouseOverWindow(mx, my)) return false;

        for (int i = widgets.size() - 1; i >= 0; i--) {
            DraggableWidget w = widgets.get(i);
            if (w.isEnabled() && w.isHovered(mx, my)) {
                w.setScale(w.getScale() + (verticalAmount > 0 ? 0.05f : -0.05f));
                ConfigManager.getInstance().saveHudChangeQuietly();
                return true;
            }
        }
        return false;
    }

    private static String[] getMenuOptions(DraggableWidget widget) {
        if (widget != null && "Watermark".equals(widget.getName())) {
            return new String[]{"Фон", "Жидкое стекло", "Глобально", "Масштаб +", "Масштаб -", "Центр сверху", "В угол"};
        }
        return new String[]{"Фон", "Жидкое стекло", "Глобально", "Масштаб +", "Масштаб -"};
    }

    private void renderWidgetContextMenu(GuiGraphicsExtractor context, float mx, float my, float screenW, float screenH) {
        long now = System.nanoTime();
        float dt = Math.min(0.05f, Math.max(0.0001f, (now - ctxLastNanos) / 1.0e9f));
        ctxLastNanos = now;

        boolean wantOpen = menuWidget != null && menuWidget.isEnabled() && !menuWidget.isDragging();
        menuOpenAnim += ((wantOpen ? 1.0f : 0.0f) - menuOpenAnim) * (1.0f - (float) Math.exp(-18.0f * dt));
        if (!wantOpen && menuOpenAnim <= 0.01f) return;

        DraggableWidget target = menuWidget;
        if (target == null) return;
        var iface = dev.opticumeclient.client.modules.hud.InterfaceModule.getInstance();
        String curMode = iface != null ? iface.getWidgetBgOverride(target.getName()) : "global";

        String[] opts = getMenuOptions(target);
        ctxMenuW = 114.0f;
        ctxMenuRowH = 14.0f;
        float menuH = ctxMenuRowH * opts.length;
        ctxMenuX = Math.max(4.0f, Math.min(screenW - ctxMenuW - 4.0f, target.getX()));
        ctxMenuY = Math.max(4.0f, Math.min(screenH - menuH - 4.0f, target.getY() + target.getHeight() + 3.0f));

        float top = ctxMenuY - (1.0f - menuOpenAnim) * 4.0f;
        HudPanelRenderer.drawSurface(context, target.getName(), ctxMenuX, top, ctxMenuW, menuH,
                4.5f, Math.round(230 * menuOpenAnim), Math.round(35 * menuOpenAnim));

        int accent = ThemeManager.getInstance().getAccentColor(0);
        for (int i = 0; i < opts.length; i++) {
            float ry = top + i * ctxMenuRowH;
            boolean hov = mx >= ctxMenuX && mx <= ctxMenuX + ctxMenuW && my >= ry && my < ry + ctxMenuRowH;
            if (hov) {
                Render2DHelper.drawRoundedRectFloat(context, ctxMenuX + 2.0f, ry + 1.5f, ctxMenuW - 4.0f, ctxMenuRowH - 3.0f,
                        2.5f, ColorUtils.withAlpha(accent, Math.round(52 * menuOpenAnim)));
            }
            Render2DHelper.drawScaledText(context, opts[i], ctxMenuX + 6.0f, ry + 3.5f,
                    ColorUtils.rgba(236, 238, 245, Math.round((hov ? 255 : 210) * menuOpenAnim)), 0.72f, false);

            if (i < 3) {
                boolean active = (i == 0 && "solid".equals(curMode))
                        || (i == 1 && "glass".equals(curMode))
                        || (i == 2 && "global".equals(curMode));
                String tag = active ? "Вкл" : "Выкл";
                float tw = Render2DHelper.getTextWidth(tag, 0.66f);
                int col = active ? accent : ColorUtils.rgba(255, 255, 255, Math.round(130 * menuOpenAnim));
                Render2DHelper.drawScaledText(context, tag, ctxMenuX + ctxMenuW - tw - 6.0f, ry + 4.0f,
                        ColorUtils.withAlphaFactor(col, menuOpenAnim), 0.66f, false);
            }
        }
    }

    public List<Module> getHudModules() {
        return hudModules;
    }
}
