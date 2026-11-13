package dev.optimusprime.client.modules.visuals;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.optimusprime.client.modules.Category;
import dev.optimusprime.client.modules.Module;
import dev.optimusprime.client.setting.BooleanSetting;
import dev.optimusprime.client.setting.ModeSetting;
import dev.optimusprime.client.setting.NumberSetting;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;

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

    private final ModeSetting swingAnim = new ModeSetting(
            "SwingAnim",
            "Стиль анимации взмаха/удара",
            "Vanilla",
            "Vanilla",
            "1.7 BlockHit",
            "Slide",
            "Swipe",
            "Spin 360",
            "Nursultan Smooth",
            "Exhibition"
    );
    private final NumberSetting swingSpeed = new NumberSetting("SwingSpeed", "Скорость анимации удара", 1.0, 0.4, 2.5, 0.1);

    private final BooleanSetting customEat = new BooleanSetting("CustomEat", "Кастомная анимация еды и зелий", true);
    private final NumberSetting eatScale = new NumberSetting("EatScale", "Масштаб предмета при поедании", 1.0, 0.5, 1.8, 0.05);
    private final NumberSetting eatY = new NumberSetting("EatY", "Смещение по Y при поедании", 0.0, -0.4, 0.4, 0.02);

    public ViewModelModule() {
        super("ViewModel", "Кастомизация рук, смещения предметов от первого лица и анимаций меча", Category.VISUALS);
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
        addSetting(swingAnim);
        addSetting(swingSpeed);
        addSetting(customEat);
        addSetting(eatScale);
        addSetting(eatY);
        INSTANCE = this;
    }

    public static ViewModelModule getInstance() {
        return INSTANCE;
    }

    @Override
    public String getDisplaySuffix() {
        return swingAnim.getMode();
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

    public String getSwingAnim() { return swingAnim.getMode(); }
    public boolean isCustomEat() { return customEat.isEnabled(); }
    public float getEatScale() { return eatScale.getFloatValue(); }
    public float getEatY() { return eatY.getFloatValue(); }

    public void applyCustomSwing(PoseStack matrices, HumanoidArm arm, float swingProgress) {
        int side = (arm == HumanoidArm.RIGHT) ? 1 : -1;
        float sp = swingProgress * swingSpeed.getFloatValue();
        if (sp > 1.0f) sp = 1.0f;

        switch (swingAnim.getMode()) {
            case "1.7 BlockHit" -> {
                float f = Mth.sin(sp * sp * (float) Math.PI);
                matrices.rotateDegrees(Axis.YP, (float) side * (45.0F + f * -20.0F));
                float g = Mth.sin(Mth.sqrt(sp) * (float) Math.PI);
                matrices.rotateDegrees(Axis.ZP, (float) side * g * -20.0F);
                matrices.rotateDegrees(Axis.XP, g * -80.0F);
                matrices.rotateDegrees(Axis.YP, (float) side * -45.0F);
                matrices.translate(-0.15f * side, 0.15f, -0.1f);
                matrices.rotateDegrees(Axis.XP, -30.0f * g);
                matrices.rotateDegrees(Axis.YP, 30.0f * (float) side * g);
            }
            case "Slide" -> {
                float g = Mth.sin(Mth.sqrt(sp) * (float) Math.PI);
                matrices.translate(side * 0.2f * g, -0.2f * g, -0.25f * g);
                matrices.rotateDegrees(Axis.XP, -60.0f * g);
                matrices.rotateDegrees(Axis.YP, (float) side * 30.0f * g);
                matrices.rotateDegrees(Axis.ZP, (float) side * -30.0f * g);
            }
            case "Swipe" -> {
                float f = Mth.sin(sp * (float) Math.PI);
                matrices.translate(side * 0.1f * f, -0.1f * f, -0.1f * f);
                matrices.rotateDegrees(Axis.XP, -85.0f * f);
                matrices.rotateDegrees(Axis.YP, (float) side * 45.0f * (1.0f - f));
                matrices.rotateDegrees(Axis.ZP, (float) side * -50.0f * f);
            }
            case "Spin 360" -> {
                matrices.rotateDegrees(Axis.XP, -360.0f * sp);
                matrices.rotateDegrees(Axis.YP, (float) side * 25.0f * Mth.sin(sp * (float) Math.PI));
            }
            case "Nursultan Smooth" -> {
                matrices.rotateDegrees(Axis.YP, (float) side * 45.0f);
                float g = Mth.sin(Mth.sqrt(sp) * (float) Math.PI);
                matrices.translate(side * 0.15f * g, -0.05f * g, -0.1f * g);
                matrices.rotateDegrees(Axis.XP, -65.0f * g);
                matrices.rotateDegrees(Axis.YP, (float) side * (35.0f * g - 15.0f * g * g));
                matrices.rotateDegrees(Axis.ZP, (float) side * -35.0f * g);
                matrices.rotateDegrees(Axis.YP, (float) side * -45.0f);
            }
            case "Exhibition" -> {
                float g = Mth.sin(Mth.sqrt(sp) * (float) Math.PI);
                matrices.translate(side * -0.1f * g, 0.1f * g, -0.15f * g);
                matrices.rotateDegrees(Axis.YP, (float) side * 30.0f);
                matrices.rotateDegrees(Axis.ZP, (float) side * -20.0f);
                matrices.rotateDegrees(Axis.XP, -50.0f * g - 20.0f);
            }
        }
    }
}
