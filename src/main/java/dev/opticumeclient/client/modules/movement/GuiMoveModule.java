package dev.opticumeclient.client.modules.movement;

import com.mojang.blaze3d.platform.InputConstants;
import dev.opticumeclient.client.gui.ClickGuiScreen;
import dev.opticumeclient.client.modules.Category;
import dev.opticumeclient.client.modules.Module;
import dev.opticumeclient.client.setting.BooleanSetting;
import dev.opticumeclient.client.setting.ModeSetting;
import dev.opticumeclient.client.setting.NumberSetting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractCommandBlockEditScreen;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import net.minecraft.client.gui.screens.inventory.BookEditScreen;
import net.minecraft.client.gui.screens.inventory.StructureBlockEditScreen;
import net.minecraft.util.Mth;

/**
 * Ported from Desire (InventoryMoveModule) & Skycore (GuiMoveModule):
 * Allows full movement (WASD, Jump, Sprint, optional Sneak, and Arrow-key camera look)
 * while inside Inventory, Containers, or ClickGUI (automatically paused when typing).
 */
public class GuiMoveModule extends Module {
    private static GuiMoveModule instance;

    private final ModeSetting modeSetting;
    private final BooleanSetting sprintSetting;
    private final BooleanSetting sneakSetting;
    private final BooleanSetting arrowLookSetting;
    private final NumberSetting lookSpeedSetting;

    private boolean wasControllingKeys = false;

    public GuiMoveModule() {
        super("GuiMove", "Движение, прыжки и поворот камеры при открытом инвентаре, сундуках и ClickGUI", Category.MOVEMENT);
        instance = this;

        this.modeSetting = addSetting(new ModeSetting(
                "Bypass Mode",
                "Режим обхода античитов при движении в инвентаре",
                "Grim / FunTime",
                "Vanilla", "Grim / FunTime", "HolyWorld"
        ));
        this.sprintSetting = addSetting(new BooleanSetting(
                "Allow Sprint",
                "Разрешить бег (Sprint) с открытым экраном",
                true
        ));
        this.sneakSetting = addSetting(new BooleanSetting(
                "Allow Sneak",
                "Передавать нажатие Shift как приседание (отключите, чтобы Shift-клик не приседал)",
                false
        ));
        this.arrowLookSetting = addSetting(new BooleanSetting(
                "Arrow Look",
                "Поворот камеры стрелочками клавиатуры при открытом инвентаре",
                true
        ));
        this.lookSpeedSetting = addSetting(new NumberSetting(
                "Look Speed",
                "Скорость поворота камеры стрелочками",
                4.5f, 1.0f, 12.0f, 0.5f
        ).visibleWhen(arrowLookSetting::isEnabled));
    }

    public static GuiMoveModule getInstance() {
        return instance;
    }

    @Override
    public void onDisable() {
        releaseControlledKeys();
    }

    @Override
    public void onTick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            wasControllingKeys = false;
            return;
        }

        Screen screen = mc.gui != null ? mc.gui.screen() : null;
        if (!shouldMoveInScreen(screen)) {
            if (wasControllingKeys && screen != null) {
                releaseControlledKeys();
            }
            wasControllingKeys = false;
            return;
        }

        wasControllingKeys = true;

        updateKey(mc.options.keyUp);
        updateKey(mc.options.keyDown);
        updateKey(mc.options.keyLeft);
        updateKey(mc.options.keyRight);
        updateKey(mc.options.keyJump);

        boolean inContainer = screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>;
        boolean blockSprint = modeSetting.isMode("HolyWorld")
                || (modeSetting.isMode("Grim / FunTime") && inContainer);

        if (sprintSetting.isEnabled() && !blockSprint) {
            updateKey(mc.options.keySprint);
        } else {
            mc.options.keySprint.setDown(false);
            if (blockSprint) {
                mc.player.setSprinting(false);
            }
        }

        if (sneakSetting.isEnabled()) {
            updateKey(mc.options.keyShift);
        }

        if (arrowLookSetting.isEnabled()) {
            float speed = lookSpeedSetting.getFloatValue();
            float dyaw = 0.0f;
            float dpitch = 0.0f;
            if (InputConstants.isKeyDown(InputConstants.KEY_LEFT)) dyaw -= speed;
            if (InputConstants.isKeyDown(InputConstants.KEY_RIGHT)) dyaw += speed;
            if (InputConstants.isKeyDown(InputConstants.KEY_UP)) dpitch -= speed;
            if (InputConstants.isKeyDown(InputConstants.KEY_DOWN)) dpitch += speed;

            if (dyaw != 0.0f || dpitch != 0.0f) {
                mc.player.setYRot(mc.player.getYRot() + dyaw);
                mc.player.setXRot(Mth.clamp(mc.player.getXRot() + dpitch, -90.0f, 90.0f));
            }
        }
    }

    public static boolean shouldMoveInScreen(Screen screen) {
        if (screen == null) return false;
        if (screen instanceof ChatScreen
                || screen instanceof AbstractSignEditScreen
                || screen instanceof AnvilScreen
                || screen instanceof BookEditScreen
                || screen instanceof AbstractCommandBlockEditScreen
                || screen instanceof StructureBlockEditScreen) {
            return false;
        }
        if (screen.getFocused() instanceof EditBox editBox && editBox.isVisible()) {
            return false;
        }
        if (screen instanceof ClickGuiScreen clickGui && clickGui.isTypingOrBinding()) {
            return false;
        }
        return true;
    }

    private static void updateKey(KeyMapping mapping) {
        if (mapping == null || mapping.isUnbound()) return;
        try {
            InputConstants.Key key = InputConstants.getKey(mapping.saveString());
            if (key != null && key != InputConstants.UNKNOWN && key.getValue() > 0 && key.getType() != InputConstants.Type.MOUSE) {
                mapping.setDown(InputConstants.isKeyDown(key.getValue()));
            }
        } catch (Throwable ignored) {
        }
    }

    private void releaseControlledKeys() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.options == null) return;
        if (mc.gui != null && mc.gui.screen() != null) {
            mc.options.keyUp.setDown(false);
            mc.options.keyDown.setDown(false);
            mc.options.keyLeft.setDown(false);
            mc.options.keyRight.setDown(false);
            mc.options.keyJump.setDown(false);
            mc.options.keySprint.setDown(false);
            if (sneakSetting.isEnabled()) {
                mc.options.keyShift.setDown(false);
            }
        }
    }
}
