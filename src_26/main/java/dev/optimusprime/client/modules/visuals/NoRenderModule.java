package dev.optimusprime.client.modules.visuals;

import dev.optimusprime.client.modules.Category;
import dev.optimusprime.client.modules.Module;
import dev.optimusprime.client.setting.BooleanSetting;

public class NoRenderModule extends Module {
    private static NoRenderModule INSTANCE;

    private final BooleanSetting fire = new BooleanSetting("Fire", "Отключает огонь от первого лица", true);
    private final BooleanSetting weather = new BooleanSetting("Weather", "Отключает рендер дождя и снега", true);
    private final BooleanSetting totem = new BooleanSetting("Totem", "Отключает анимацию тотема на весь экран", true);
    private final BooleanSetting explosions = new BooleanSetting("Explosions", "Отключает тяжелые частицы взрывов", true);
    private final BooleanSetting bossBar = new BooleanSetting("BossBar", "Скрывает полосу боссбара", false);
    private final BooleanSetting badEffects = new BooleanSetting("BadEffects", "Отключает слепоту, тошноту и портал", true);
    private final BooleanSetting armor = new BooleanSetting("Armor", "Отключает рендер слоев брони", false);
    private final BooleanSetting hurtCam = new BooleanSetting("HurtCam", "Отключает тряску камеры при уроне", true);
    private final BooleanSetting scoreboard = new BooleanSetting("Scoreboard", "Скрывает боковой скорборд", false);

    public NoRenderModule() {
        super("NoRender", "Выборочное отключение тяжелых и мешающих визуальных эффектов", Category.VISUALS);
        addSetting(fire);
        addSetting(weather);
        addSetting(totem);
        addSetting(explosions);
        addSetting(bossBar);
        addSetting(badEffects);
        addSetting(armor);
        addSetting(hurtCam);
        addSetting(scoreboard);
        INSTANCE = this;
        setEnabled(true);
    }

    public static NoRenderModule getInstance() {
        return INSTANCE;
    }

    public boolean isFire() {
        return isEnabled() && fire.isEnabled();
    }

    public boolean isWeather() {
        return isEnabled() && weather.isEnabled();
    }

    public boolean isTotem() {
        return isEnabled() && totem.isEnabled();
    }

    public boolean isExplosions() {
        return isEnabled() && explosions.isEnabled();
    }

    public boolean isBossBar() {
        return isEnabled() && bossBar.isEnabled();
    }

    public boolean isBadEffects() {
        return isEnabled() && badEffects.isEnabled();
    }

    public boolean isArmor() {
        return isEnabled() && armor.isEnabled();
    }

    public boolean isHurtCam() {
        return isEnabled() && hurtCam.isEnabled();
    }

    public boolean isScoreboard() {
        return isEnabled() && scoreboard.isEnabled();
    }

    public BooleanSetting getFire() {
        return fire;
    }

    public BooleanSetting getWeather() {
        return weather;
    }

    public BooleanSetting getTotem() {
        return totem;
    }

    public BooleanSetting getExplosions() {
        return explosions;
    }

    public BooleanSetting getBossBar() {
        return bossBar;
    }

    public BooleanSetting getBadEffects() {
        return badEffects;
    }

    public BooleanSetting getArmor() {
        return armor;
    }

    public BooleanSetting getHurtCam() {
        return hurtCam;
    }

    public BooleanSetting getScoreboard() {
        return scoreboard;
    }
}
