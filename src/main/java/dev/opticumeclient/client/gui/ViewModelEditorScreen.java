package dev.opticumeclient.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.opticumeclient.client.config.ConfigManager;
import dev.opticumeclient.client.modules.visuals.SwingAnimationsModule;
import dev.opticumeclient.client.modules.visuals.ViewModelModule;
import dev.opticumeclient.client.render.ColorUtils;
import dev.opticumeclient.client.render.Render2DHelper;
import dev.opticumeclient.client.render.ThemeManager;
import dev.opticumeclient.client.setting.NumberSetting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.Locale;

/**
 * Interactive ViewModel & Cubic Bezier Swing Curve Editor overlaying live 3D game world,
 * allowing real-time hand transform adjustment (MainHand / OffHand) and dragging P1/P2 Bezier handles.
 */
public class ViewModelEditorScreen extends Screen {
    private final Screen parent;
    private boolean editingOffhand = false;
    private int draggingHandle = 0; // 1 = P1, 2 = P2
    private NumberSetting draggingSlider = null;
    private float sliderDragX, sliderDragW;

    public ViewModelEditorScreen(Screen parent) {
        super(Component.literal("3D ViewModel & Bezier Editor"));
        this.parent = parent;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        // Keep world transparent on right half so user sees their live held item transform in real time
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        if (draggingSlider != null) {
            updateSliderValue(mouseX);
        }
        if (draggingHandle != 0) {
            updateBezierHandle(mouseX, mouseY);
        }

        int accent = ThemeManager.getInstance().getAccentColor(0);
        int accent2 = ThemeManager.getInstance().getCurrentTheme().getSecondaryColor();

        float panelW = Math.min(278.0f, width * 0.48f);
        float panelH = Math.min(352.0f, height - 24.0f);
        float px = 14.0f;
        float py = (height - panelH) * 0.5f;
        float pad = 10.0f;
        float innerW = panelW - pad * 2.0f;

        Render2DHelper.drawBlurredRoundedRect(context, px, py, panelW, panelH, 9.0f, 0xCC090B12, 1.0f);
        Render2DHelper.drawRoundedRect(context, px, py, panelW, panelH, 9.0f, 0xD80A0C14);
        Render2DHelper.drawRoundedOutline(context, px, py, panelW, panelH, 9.0f, 1.0f, ColorUtils.withAlpha(accent, 140));

        // Header
        Render2DHelper.drawScaledText(context, "ViewModel & Bezier Editor", px + pad, py + 10.0f, 0xFFF2F5FA, 0.92f, true);
        float closeW = 46.0f;
        float closeX = px + panelW - pad - closeW;
        Render2DHelper.drawRoundedRect(context, closeX, py + 7.0f, closeW, 15.0f, 4.0f, 0x33FFFFFF);
        Render2DHelper.drawCenteredText(context, "Close", closeX + closeW * 0.5f, py + 11.0f, 0xFFFFFFFF, false);

        // Hand switcher + Reset + Test Swing
        float tabY = py + 28.0f;
        float btnW = (innerW - 8.0f) / 3.0f;
        drawMiniBtn(context, px + pad, tabY, btnW, 16.0f, editingOffhand ? "OffHand" : "MainHand", true, accent);
        drawMiniBtn(context, px + pad + btnW + 4.0f, tabY, btnW, 16.0f, "Test Swing", false, accent2);
        drawMiniBtn(context, px + pad + (btnW + 4.0f) * 2.0f, tabY, btnW, 16.0f, "Reset Hand", false, 0xFFF87171);

        // Hand transform sliders
        ViewModelModule vm = ViewModelModule.getInstance();
        float curY = tabY + 22.0f;
        if (vm != null) {
            NumberSetting[] sliders = getActiveHandSliders(vm);
            for (NumberSetting ns : sliders) {
                drawSliderRow(context, ns, px + pad, curY, innerW, accent, accent2);
                curY += 18.0f;
            }
        }

        // Interactive Cubic Bezier Graph (for SwingAnimations -> Custom)
        SwingAnimationsModule swing = SwingAnimationsModule.getInstance();
        if (swing != null) {
            float graphH = Math.max(76.0f, py + panelH - curY - 34.0f);
            float graphY = curY + 6.0f;
            float gx = px + pad;
            float gw = innerW;

            Render2DHelper.drawScaledText(context,
                    String.format(Locale.US, "Swing Bezier (%.2f, %.2f) / (%.2f, %.2f)",
                            swing.getCurve1X().getFloatValue(), swing.getCurve1Y().getFloatValue(),
                            swing.getCurve2X().getFloatValue(), swing.getCurve2Y().getFloatValue()),
                    gx, curY - 2.0f, 0xFFCBD5E1, 0.68f, false);

            Render2DHelper.drawRoundedRect(context, gx, graphY, gw, graphH, 6.0f, 0x99111420);
            Render2DHelper.drawRoundedOutline(context, gx, graphY, gw, graphH, 6.0f, 0.8f, 0x44FFFFFF);

            float padG = 8.0f;
            float plotX = gx + padG;
            float plotY = graphY + padG;
            float plotW = gw - padG * 2.0f;
            float plotH = graphH - padG * 2.0f;

            // Grid diagonal reference
            Render2DHelper.drawLine(context, plotX, plotY + plotH, plotX + plotW, plotY, 0.8f, 0x33FFFFFF);

            float p1x = plotX + swing.getCurve1X().getFloatValue() * plotW;
            float p1y = plotY + (1.0f - swing.getCurve1Y().getFloatValue()) * plotH;
            float p2x = plotX + swing.getCurve2X().getFloatValue() * plotW;
            float p2y = plotY + (1.0f - swing.getCurve2Y().getFloatValue()) * plotH;

            // Control lines
            Render2DHelper.drawLine(context, plotX, plotY + plotH, p1x, p1y, 1.0f, ColorUtils.withAlpha(accent, 140));
            Render2DHelper.drawLine(context, plotX + plotW, plotY, p2x, p2y, 1.0f, ColorUtils.withAlpha(accent2, 140));

            // Sample cubic curve (24 segments)
            float prevX = plotX;
            float prevY = plotY + plotH;
            int segs = 24;
            for (int i = 1; i <= segs; i++) {
                float t = i / (float) segs;
                float u = 1.0f - t;
                float bx = 3.0f * u * u * t * swing.getCurve1X().getFloatValue()
                        + 3.0f * u * t * t * swing.getCurve2X().getFloatValue() + t * t * t;
                float by = 3.0f * u * u * t * swing.getCurve1Y().getFloatValue()
                        + 3.0f * u * t * t * swing.getCurve2Y().getFloatValue() + t * t * t;
                float sx = plotX + bx * plotW;
                float sy = plotY + (1.0f - by) * plotH;
                int segCol = ColorUtils.interpolateColor(accent, accent2, t);
                Render2DHelper.drawLine(context, prevX, prevY, sx, sy, 2.0f, segCol);
                prevX = sx;
                prevY = sy;
            }

            // Draggable P1 and P2 handles
            Render2DHelper.drawCircle(context, p1x, p1y, 4.2f, draggingHandle == 1 ? 0xFFFFFFFF : accent);
            Render2DHelper.drawCircle(context, p2x, p2y, 4.2f, draggingHandle == 2 ? 0xFFFFFFFF : accent2);
        }
    }

    private NumberSetting[] getActiveHandSliders(ViewModelModule vm) {
        if (editingOffhand) {
            return new NumberSetting[]{
                    vm.getOffXSetting(), vm.getOffYSetting(), vm.getOffZSetting(),
                    vm.getOffScaleSetting(), vm.getOffRotXSetting(), vm.getOffRotYSetting(), vm.getOffRotZSetting()
            };
        }
        return new NumberSetting[]{
                vm.getMainXSetting(), vm.getMainYSetting(), vm.getMainZSetting(),
                vm.getMainScaleSetting(), vm.getMainRotXSetting(), vm.getMainRotYSetting(), vm.getMainRotZSetting()
        };
    }

    private void drawMiniBtn(GuiGraphicsExtractor ctx, float x, float y, float w, float h, String txt, boolean active, int col) {
        Render2DHelper.drawRoundedRect(ctx, x, y, w, h, 4.0f, active ? ColorUtils.withAlpha(col, 85) : 0x26FFFFFF);
        Render2DHelper.drawRoundedOutline(ctx, x, y, w, h, 4.0f, 0.8f, ColorUtils.withAlpha(col, active ? 200 : 80));
        Render2DHelper.drawCenteredText(ctx, txt, x + w * 0.5f, y + 4.5f, 0xFFF1F5F9, false);
    }

    private void drawSliderRow(GuiGraphicsExtractor ctx, NumberSetting ns, float x, float y, float w, int c1, int c2) {
        String val = String.format(Locale.US, "%.2f", ns.getValue());
        Render2DHelper.drawScaledText(ctx, ns.getName(), x, y, 0xFFE2E8F0, 0.70f, false);
        float vw = Render2DHelper.getTextWidth(val, 0.70f);
        Render2DHelper.drawScaledText(ctx, val, x + w - vw, y, c1, 0.70f, false);

        float barY = y + 9.0f;
        float barH = 4.0f;
        float pct = Mth.clamp((float) ((ns.getValue() - ns.getMin()) / Math.max(1e-5, ns.getMax() - ns.getMin())), 0.0f, 1.0f);
        Render2DHelper.drawRoundedRect(ctx, x, barY, w, barH, 2.0f, 0x38FFFFFF);
        Render2DHelper.drawGradientRoundedRect(ctx, x, barY, Math.max(4.0f, w * pct), barH, 2.0f, c1, c2);
        Render2DHelper.drawCircle(ctx, x + w * pct, barY + barH * 0.5f, 3.2f, 0xFFFFFFFF);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        int btn = ClickGuiScreen.normalizeMouseButton(click.button());
        if (btn != 0) return super.mouseClicked(click, doubled);
        double mx = click.x();
        double my = click.y();

        float panelW = Math.min(278.0f, width * 0.48f);
        float panelH = Math.min(352.0f, height - 24.0f);
        float px = 14.0f;
        float py = (height - panelH) * 0.5f;
        float pad = 10.0f;
        float innerW = panelW - pad * 2.0f;

        if (mx >= px + panelW - pad - 46.0f && mx <= px + panelW - pad && my >= py + 7.0f && my <= py + 22.0f) {
            onClose();
            return true;
        }

        float tabY = py + 28.0f;
        float btnW = (innerW - 8.0f) / 3.0f;
        if (my >= tabY && my <= tabY + 16.0f) {
            if (mx >= px + pad && mx <= px + pad + btnW) {
                editingOffhand = !editingOffhand;
                return true;
            }
            if (mx >= px + pad + btnW + 4.0f && mx <= px + pad + btnW * 2.0f + 4.0f) {
                if (minecraft != null && minecraft.player != null) {
                    minecraft.player.swing(
                            net.minecraft.world.InteractionHand.MAIN_HAND,
                            net.minecraft.world.item.component.SwingAnimation.DEFAULT,
                            false
                    );
                }
                return true;
            }
            if (mx >= px + pad + (btnW + 4.0f) * 2.0f && mx <= px + pad + innerW) {
                ViewModelModule vm = ViewModelModule.getInstance();
                if (vm != null) {
                    for (NumberSetting ns : getActiveHandSliders(vm)) {
                        ns.setValue(ns.getName().contains("Scale") ? 1.0 : 0.0);
                    }
                    ConfigManager.getInstance().saveActiveConfigQuietly();
                }
                return true;
            }
        }

        ViewModelModule vm = ViewModelModule.getInstance();
        float curY = tabY + 22.0f;
        if (vm != null) {
            if (!vm.isEnabled()) vm.setEnabled(true);
            for (NumberSetting ns : getActiveHandSliders(vm)) {
                if (mx >= px + pad && mx <= px + pad + innerW && my >= curY && my <= curY + 16.0f) {
                    draggingSlider = ns;
                    sliderDragX = px + pad;
                    sliderDragW = innerW;
                    updateSliderValue(mx);
                    return true;
                }
                curY += 18.0f;
            }
        }

        SwingAnimationsModule swing = SwingAnimationsModule.getInstance();
        if (swing != null) {
            float graphH = Math.max(76.0f, py + panelH - curY - 34.0f);
            float graphY = curY + 6.0f;
            float gx = px + pad;
            float gw = innerW;
            if (mx >= gx && mx <= gx + gw && my >= graphY && my <= graphY + graphH) {
                if (!swing.isEnabled()) swing.setEnabled(true);
                swing.mode.setMode("Custom");
                float plotX = gx + 8.0f;
                float plotY = graphY + 8.0f;
                float plotW = gw - 16.0f;
                float plotH = graphH - 16.0f;
                float p1x = plotX + swing.getCurve1X().getFloatValue() * plotW;
                float p1y = plotY + (1.0f - swing.getCurve1Y().getFloatValue()) * plotH;
                float p2x = plotX + swing.getCurve2X().getFloatValue() * plotW;
                float p2y = plotY + (1.0f - swing.getCurve2Y().getFloatValue()) * plotH;
                double d1 = Math.hypot(mx - p1x, my - p1y);
                double d2 = Math.hypot(mx - p2x, my - p2y);
                draggingHandle = d1 <= d2 ? 1 : 2;
                updateBezierHandle(mx, my);
                return true;
            }
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent click) {
        if (draggingSlider != null || draggingHandle != 0) {
            draggingSlider = null;
            draggingHandle = 0;
            ConfigManager.getInstance().saveActiveConfigQuietly();
        }
        return super.mouseReleased(click);
    }

    private void updateSliderValue(double mx) {
        if (draggingSlider == null) return;
        double pct = Mth.clamp((mx - sliderDragX) / Math.max(1.0, sliderDragW), 0.0, 1.0);
        double val = draggingSlider.getMin() + pct * (draggingSlider.getMax() - draggingSlider.getMin());
        draggingSlider.setValueClamped(val);
    }

    private void updateBezierHandle(double mx, double my) {
        SwingAnimationsModule swing = SwingAnimationsModule.getInstance();
        if (swing == null) return;
        float panelW = Math.min(278.0f, width * 0.48f);
        float panelH = Math.min(352.0f, height - 24.0f);
        float py = (height - panelH) * 0.5f;
        float pad = 10.0f;
        float innerW = panelW - pad * 2.0f;
        float curY = py + 28.0f + 22.0f + 7 * 18.0f;
        float graphH = Math.max(76.0f, py + panelH - curY - 34.0f);
        float graphY = curY + 6.0f;
        float plotX = 14.0f + pad + 8.0f;
        float plotY = graphY + 8.0f;
        float plotW = innerW - 16.0f;
        float plotH = graphH - 16.0f;

        float nx = Mth.clamp((float) ((mx - plotX) / Math.max(1.0f, plotW)), 0.0f, 1.0f);
        float ny = Mth.clamp(1.0f - (float) ((my - plotY) / Math.max(1.0f, plotH)), 0.0f, 1.0f);
        if (draggingHandle == 1) {
            swing.getCurve1X().setValue((double) Math.round(nx * 100.0) / 100.0);
            swing.getCurve1Y().setValue((double) Math.round(ny * 100.0) / 100.0);
        } else if (draggingHandle == 2) {
            swing.getCurve2X().setValue((double) Math.round(nx * 100.0) / 100.0);
            swing.getCurve2Y().setValue((double) Math.round(ny * 100.0) / 100.0);
        }
    }

    @Override
    public boolean keyPressed(KeyEvent input) {
        if (input.key() == InputConstants.KEY_ESCAPE) {
            onClose();
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public void onClose() {
        ConfigManager.getInstance().saveActiveConfigQuietly();
        if (minecraft != null && minecraft.gui != null) {
            minecraft.gui.setScreen(parent);
        }
    }
}
