package dev.opticumeclient.client.modules.hud;

import dev.opticumeclient.client.modules.Category;
import dev.opticumeclient.client.modules.Module;
import dev.opticumeclient.client.modules.ModuleManager;
import dev.opticumeclient.client.setting.BooleanSetting;
import dev.opticumeclient.client.setting.ModeSetting;
import dev.opticumeclient.client.setting.NumberSetting;
import dev.opticumeclient.client.ui.menu.MenuConfigStore;

/**
 * Desire-style {@code Interface} module controlling HUD panel rendering:
 * - Switch between real Kawase Liquid Glass backdrop blur and solid Relic panels globally or per-widget
 * - Customize corner radius, glass opacity, blur strength/offset, and watermark/panel design
 */
public class InterfaceModule extends Module {
    private static InterfaceModule instance;

    private final ModeSetting bgMode = new ModeSetting("BgMode", "Глобальный фон виджетов HUD",
            "Жидкое стекло", "Жидкое стекло", "Обычный");
    private final ModeSetting design = new ModeSetting("Design", "Визуальный стиль панелей HUD",
            "Relic", "Relic", "Modern");
    private final ModeSetting watermarkStyle = new ModeSetting("Watermark", "Макет ватермарки",
            "Relic", "Relic", "Modern");
    private final NumberSetting cornerRadius = new NumberSetting("Radius", "Скругление углов HUD панелей",
            6.5, 1.0, 12.0, 0.5);
    private final NumberSetting glassOpacity = new NumberSetting("Opacity", "Непрозрачность заливки жидкого стекла",
            152.0, 40.0, 240.0, 4.0);
    private final NumberSetting blurPasses = new NumberSetting("BlurPasses", "Проходы размытия Kawase для HUD",
            3.0, 1.0, 4.0, 1.0);
    private final NumberSetting blurOffset = new NumberSetting("BlurRadius", "Радиус размытия жидкого стекла",
            2.0, 0.5, 4.0, 0.25);
    private final BooleanSetting rightClickMenu = new BooleanSetting("WidgetMenu", "ПКМ по виджету в чате открывает настройки фона", true);

    public InterfaceModule() {
        super("Interface", "Настройка стиля HUD: Жидкое стекло, углы скругления, блюр и оформление", Category.HUD);
        instance = this;
        addSetting(bgMode);
        addSetting(design);
        addSetting(watermarkStyle);
        addSetting(cornerRadius);
        addSetting(glassOpacity);
        addSetting(blurPasses);
        addSetting(blurOffset);
        addSetting(rightClickMenu);
        setEnabled(true);
    }

    public static InterfaceModule getInstance() {
        if (instance == null) {
            instance = ModuleManager.getInstance().getModule(InterfaceModule.class);
        }
        return instance;
    }

    @Override
    public String getDisplaySuffix() {
        return isGlobalLiquidGlass() ? "Glass" : "Solid";
    }

    public boolean isGlobalLiquidGlass() {
        return !isEnabled() || bgMode.is("Жидкое стекло");
    }

    public boolean isEffectiveLiquidGlass(String widgetName) {
        if (widgetName != null && !widgetName.isBlank()) {
            String key = "hudBg_" + widgetName;
            String saved = MenuConfigStore.getString(key, "global");
            if ("glass".equals(saved)) return true;
            if ("solid".equals(saved)) return false;
        }
        return isGlobalLiquidGlass();
    }

    public void setWidgetBgOverride(String widgetName, String mode) {
        if (widgetName == null || widgetName.isBlank()) return;
        MenuConfigStore.save(obj -> obj.addProperty("hudBg_" + widgetName, mode));
    }

    public String getWidgetBgOverride(String widgetName) {
        if (widgetName == null || widgetName.isBlank()) return "global";
        return MenuConfigStore.getString("hudBg_" + widgetName, "global");
    }

    public boolean isRelicDesign() {
        return !isEnabled() || design.is("Relic");
    }

    public boolean isRelicWatermark() {
        return !isEnabled() || watermarkStyle.is("Relic");
    }

    public float getEffectiveRadius(float baseRadius) {
        if (!isEnabled()) return baseRadius;
        float r = cornerRadius.getFloatValue();
        // Scale proportionally relative to default 6.5 so smaller pills stay harmonious
        return Math.max(1.0f, baseRadius * (r / 6.5f));
    }

    public int getGlassOpacityAlpha() {
        return Math.max(40, Math.min(245, Math.round(glassOpacity.getFloatValue())));
    }

    public int getBlurPasses() {
        return Math.max(1, Math.min(4, blurPasses.getIntValue()));
    }

    public float getBlurOffset() {
        return Math.max(0.5f, Math.min(4.0f, blurOffset.getFloatValue()));
    }

    public boolean isRightClickMenuEnabled() {
        return rightClickMenu.isEnabled();
    }
}
