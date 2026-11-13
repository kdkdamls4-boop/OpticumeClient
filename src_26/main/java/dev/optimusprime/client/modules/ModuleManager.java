package dev.optimusprime.client.modules;

import dev.optimusprime.client.hud.*;
import dev.optimusprime.client.modules.animations.OldAnimationsModule;
import dev.optimusprime.client.modules.combat.*;
import dev.optimusprime.client.modules.movement.NoSlowModule;
import dev.optimusprime.client.modules.movement.SprintModule;
import dev.optimusprime.client.modules.optimization.*;
import dev.optimusprime.client.modules.visuals.*;
import dev.optimusprime.client.render.Render3DHelper;
import net.minecraft.client.gui.GuiGraphicsExtractor; 
import com.mojang.blaze3d.vertex.PoseStack; 

import java.util.ArrayList;
import java.util.List;

public class ModuleManager {
    private static ModuleManager instance;
    private final List<Module> modules = new ArrayList<>();

    private ModuleManager() {
        initModules();
    }

    public static synchronized ModuleManager getInstance() {
        if (instance == null) {
            instance = new ModuleManager();
        }
        return instance;
    }

    private void initModules() {
        // Combat
        register(new LegitAuraModule());
        register(new KillAuraModule());
        register(new OptimusAuraModule());
        register(new TriggerBotModule());
        register(new AimAssistModule());
        register(new AutoTotemModule());
        register(new ShieldBreakerModule());
        register(new MiddleClickFriendModule());

        // Movement
        register(new SprintModule());
        register(new NoSlowModule());

        // Visuals & Animations
        register(new PlayerEspModule());
        register(new TargetEspModule());
        register(new ViewModelModule());
        register(new NameTagsModule());
        register(new PopChamsModule());
        register(new AmbienceModule());
        register(new KillEffectModule());
        register(new ItemEspModule());
        register(new TracersModule());
        register(new ChamsModule());
        register(new JumpCirclesModule());
        register(new ParticlesModule());
        register(new ChinaHatModule());
        register(new HitBubblesModule());
        register(new ArrowsModule());
        register(new FullbrightModule());
        register(new HitColorModule());
        register(new TrailsModule());
        register(new CrosshairModule());
        register(new OldAnimationsModule());
        register(new TargetHudModule());
        register(new CustomFontModule());
        register(new NoRenderModule());

        // Optimization
        register(new LowFireModule());
        register(new NoHurtCamModule());
        register(new MemoryCleanerModule());
        register(new EntityCullingModule());
        register(new ChunkAnimatorModule());
        register(new FastMathModule());
        register(new FrameSyncModule());

        // HUD
        KeystrokesHud keystrokes = new KeystrokesHud();
        ArmorHud armor = new ArmorHud();
        PotionHud potion = new PotionHud();
        InfoHud info = new InfoHud();

        register(keystrokes);
        register(armor);
        register(potion);
        register(info);

        HudManager.getInstance().register(keystrokes);
        HudManager.getInstance().register(armor);
        HudManager.getInstance().register(potion);
        HudManager.getInstance().register(info);
    }

    public void register(Module module) {
        modules.add(module);
    }

    public List<Module> getModules() {
        return modules;
    }

    public List<Module> getModulesByCategory(Category category) {
        List<Module> list = new ArrayList<>();
        for (Module m : modules) {
            if (m.getCategory() == category) {
                list.add(m);
            }
        }
        return list;
    }

    @SuppressWarnings("unchecked")
    public <T extends Module> T getModule(Class<T> clazz) {
        for (Module m : modules) {
            if (m.getClass() == clazz) {
                return (T) m;
            }
        }
        return null;
    }

    public void onTick() {
        for (Module m : modules) {
            if (m.isEnabled()) {
                m.onTick();
            }
        }
    }

    public void onRender3D(PoseStack matrices, float tickDelta) {
        for (Module m : modules) {
            if (m.isEnabled()) {
                m.onRender3D(matrices, tickDelta);
            }
        }
    }

    public void onRender2D(GuiGraphicsExtractor context, float tickDelta) {
        Render3DHelper.flush2D(context);
        for (Module m : modules) {
            if (m.isEnabled() && m.getCategory() != Category.HUD) {
                m.onRender2D(context, tickDelta);
            }
        }
        HudManager.getInstance().render(context, tickDelta);
    }

    public void onKeyPressed(int key) {
        if (key <= 0) return;
        for (Module m : modules) {
            if (m.getKeyBind() == key) {
                m.toggle();
            }
        }
    }
}
