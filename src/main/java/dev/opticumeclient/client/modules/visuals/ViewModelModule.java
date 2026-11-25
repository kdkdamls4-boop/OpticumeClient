package dev.opticumeclient.client.modules.visuals;

import dev.opticumeclient.client.modules.Category;
import dev.opticumeclient.client.modules.Module;
import dev.opticumeclient.client.setting.BooleanSetting;
import dev.opticumeclient.client.setting.NumberSetting;
import dev.opticumeclient.client.setting.RunSetting;

public class ViewModelModule extends Module {
    private static ViewModelModule INSTANCE;

    private final NumberSetting mainX = new NumberSetting("MainX", "Смещение правой руки по X", 0.0, -1.0, 1.0, 0.02);
    private final NumberSetting mainY = new NumberSetting("MainY", "Смещение правой руки по Y", 0.0, -1.0, 1.0, 0.02);
    private final NumberSetting mainZ = new NumberSetting("MainZ", "Смещение правой руки по Z", 0.0, -1.0, 1.0, 0.02);
    private final NumberSetting mainScale = new NumberSetting("MainScale", "Масштаб правой руки", 1.0, 0.2, 2.0, 0.05);
    private final NumberSetting mainRotX = new NumberSetting("MainRotX", "Вращение правой руки по X", 0.0, -180.0, 180.0, 5.0);
    private final NumberSetting mainRotY = new NumberSetting("MainRotY", "Вращение правой руки по Y", 0.0, -180.0, 180.0, 5.0);
    private final NumberSetting mainRotZ = new NumberSetting("MainRotZ", "Вращение правой руки по Z", 0.0, -180.0, 180.0, 5.0);

    private final NumberSetting offX = new NumberSetting("OffX", "Смещение левой руки по X", 0.0, -1.0, 1.0, 0.02);
    private final NumberSetting offY = new NumberSetting("OffY", "Смещение левой руки по Y", 0.0, -1.0, 1.0, 0.02);
    private final NumberSetting offZ = new NumberSetting("OffZ", "Смещение левой руки по Z", 0.0, -1.0, 1.0, 0.02);
    private final NumberSetting offScale = new NumberSetting("OffScale", "Масштаб левой руки", 1.0, 0.2, 2.0, 0.05);
    private final NumberSetting offRotX = new NumberSetting("OffRotX", "Вращение левой руки по X", 0.0, -180.0, 180.0, 5.0);
    private final NumberSetting offRotY = new NumberSetting("OffRotY", "Вращение левой руки по Y", 0.0, -180.0, 180.0, 5.0);
    private final NumberSetting offRotZ = new NumberSetting("OffRotZ", "Вращение левой руки по Z", 0.0, -180.0, 180.0, 5.0);

    private final BooleanSetting noEquipReset = new BooleanSetting("No Equip Reset", "Убирает опускание меча при переключении и ударе", true);

    private final BooleanSetting customEat = new BooleanSetting("CustomEat", "Кастомная анимация еды и зелий", true);
    private final NumberSetting eatScale = new NumberSetting("EatScale", "Масштаб предмета при поедании", 1.0, 0.5, 1.8, 0.05);
    private final NumberSetting eatY = new NumberSetting("EatY", "Смещение по Y при поедании", 0.0, -0.4, 0.4, 0.02);

    public ViewModelModule() {
        super("ViewModel", "Позиция, масштаб и повороты рук от первого лица (анимации взмаха — в SwingAnimations)", Category.VISUALS);
        eatScale.visibleWhen(customEat::isEnabled);
        eatY.visibleWhen(customEat::isEnabled);
        addSetting(mainX);
        addSetting(mainY);
        addSetting(mainZ);
        addSetting(mainScale);
        addSetting(mainRotX);
        addSetting(mainRotY);
        addSetting(mainRotZ);
        addSetting(offX);
        addSetting(offY);
        addSetting(offZ);
        addSetting(offScale);
        addSetting(offRotX);
        addSetting(offRotY);
        addSetting(offRotZ);
        addSetting(noEquipReset);
        addSetting(customEat);
        addSetting(eatScale);
        addSetting(eatY);
        addSetting(new RunSetting("Open 3D/Bezier Editor", "Открыть интерактивный визуальный редактор ViewModel и кривой Безье", () -> {
            if (mc != null && mc.gui != null) {
                mc.gui.setScreen(new dev.opticumeclient.client.gui.ViewModelEditorScreen(mc.gui.screen()));
            }
        }));
        INSTANCE = this;
    }

    public static ViewModelModule getInstance() {
        return INSTANCE;
    }

    public float getMainX() { return mainX.getFloatValue(); }
    public float getMainY() { return mainY.getFloatValue(); }
    public float getMainZ() { return mainZ.getFloatValue(); }
    public float getMainScale() { return mainScale.getFloatValue(); }
    public float getMainRotX() { return mainRotX.getFloatValue(); }
    public float getMainRotY() { return mainRotY.getFloatValue(); }
    public float getMainRotZ() { return mainRotZ.getFloatValue(); }

    public float getOffX() { return offX.getFloatValue(); }
    public float getOffY() { return offY.getFloatValue(); }
    public float getOffZ() { return offZ.getFloatValue(); }
    public float getOffScale() { return offScale.getFloatValue(); }
    public float getOffRotX() { return offRotX.getFloatValue(); }
    public float getOffRotY() { return offRotY.getFloatValue(); }
    public float getOffRotZ() { return offRotZ.getFloatValue(); }

    public boolean isNoEquipReset() { return noEquipReset.isEnabled(); }

    public boolean isCustomEat() { return customEat.isEnabled(); }
    public float getEatScale() { return eatScale.getFloatValue(); }
    public float getEatY() { return eatY.getFloatValue(); }

    public NumberSetting getMainXSetting() { return mainX; }
    public NumberSetting getMainYSetting() { return mainY; }
    public NumberSetting getMainZSetting() { return mainZ; }
    public NumberSetting getMainScaleSetting() { return mainScale; }
    public NumberSetting getMainRotXSetting() { return mainRotX; }
    public NumberSetting getMainRotYSetting() { return mainRotY; }
    public NumberSetting getMainRotZSetting() { return mainRotZ; }

    public NumberSetting getOffXSetting() { return offX; }
    public NumberSetting getOffYSetting() { return offY; }
    public NumberSetting getOffZSetting() { return offZ; }
    public NumberSetting getOffScaleSetting() { return offScale; }
    public NumberSetting getOffRotXSetting() { return offRotX; }
    public NumberSetting getOffRotYSetting() { return offRotY; }
    public NumberSetting getOffRotZSetting() { return offRotZ; }
}
