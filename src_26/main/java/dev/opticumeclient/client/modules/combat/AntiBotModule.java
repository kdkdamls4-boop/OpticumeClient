package dev.opticumeclient.client.modules.combat;

import dev.opticumeclient.client.modules.Category;
import dev.opticumeclient.client.modules.Module;
import dev.opticumeclient.client.setting.BooleanSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.core.UUIDUtil;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/**
 * Ported from Desire & Skycore (AntiBotModule):
 * Detects and filters out anticheat bots (Matrix / Grim / Vulcan / ReallyWorld / FunTime)
 * from Combat and Target modules.
 */
public class AntiBotModule extends Module {
    private static AntiBotModule instance;

    private final BooleanSetting tabCheckSetting;
    private final BooleanSetting uuidCheckSetting;
    private final BooleanSetting invalidNameSetting;

    public AntiBotModule() {
        super("AntiBot", "Игнорирование фейковых ботов античита в боевых модулях и ESP", Category.COMBAT);
        instance = this;

        this.tabCheckSetting = addSetting(new BooleanSetting(
                "Tab List Check",
                "Считать ботом игрока, отсутствующего в серверном Tab-листе",
                true
        ));
        this.uuidCheckSetting = addSetting(new BooleanSetting(
                "Offline UUID Check",
                "Проверять несоответствие UUID на пиратских СНГ-серверах",
                false
        ));
        this.invalidNameSetting = addSetting(new BooleanSetting(
                "Name Pattern Check",
                "Фильтровать ботов с невалидными символами или техническими префиксами в нике",
                true
        ));
    }

    public static AntiBotModule getInstance() {
        return instance;
    }

    public static boolean isBot(Entity entity) {
        if (instance == null || !instance.isEnabled()) return false;
        if (!(entity instanceof Player player)) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || player == mc.player) return false;

        String name = player.getName().getString();
        if (instance.invalidNameSetting.isEnabled()) {
            if (name == null || name.length() < 3 || name.length() > 16 || name.contains(" ") || name.startsWith("CIT-")) {
                return true;
            }
        }

        if (instance.tabCheckSetting.isEnabled() && mc.getConnection() != null) {
            PlayerInfo info = mc.getConnection().getPlayerInfo(player.getUUID());
            if (info == null) {
                return true;
            }
        }

        if (instance.uuidCheckSetting.isEnabled()) {
            if (!player.getUUID().equals(UUIDUtil.createOfflinePlayerUUID(name)) && player.getUUID().version() != 4) {
                return true;
            }
        }

        return false;
    }
}
