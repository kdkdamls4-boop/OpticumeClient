package dev.opticumeclient.client.modules;

import dev.opticumeclient.client.hud.*;
import dev.opticumeclient.client.modules.hud.InterfaceModule;
import dev.opticumeclient.client.modules.combat.*;
import dev.opticumeclient.client.modules.movement.*;
import dev.opticumeclient.client.modules.optimization.*;
import dev.opticumeclient.client.modules.other.*;
import dev.opticumeclient.client.modules.visuals.*;
import dev.opticumeclient.client.render.Render3DHelper;
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
        register(new OpticumeAuraModule());
        register(new TriggerBotModule());
        register(new AimAssistModule());
        register(new AutoTotemModule());
        register(new AutoSwapModule());
        register(new ShieldBreakerModule());
        register(new AntiBotModule());
        register(new NoFriendDamageModule());
        register(new MiddleClickFriendModule());
        register(new MiddleClickPearlModule());
        register(new AutoCrystalModule());
        register(new AutoAnchorModule());
        register(new MaceHelperModule());
        register(new BackTrackModule());
        register(new HitBoxesModule());
        register(new AutoPotionModule());
        register(new TargetPearlModule());
        register(new BowAimBotModule());

        // Movement
        register(new SprintModule());
        register(new NoSlowModule());
        register(new VelocityModule());
        register(new NoPushModule());
        register(new NoJumpDelayModule());
        register(new GuiMoveModule());
        register(new AutoToolModule());
        register(new ItemScrollerModule());
        register(new NoWebModule());
        register(new ElytraHelperModule());
        register(new JesusModule());
        register(new SpiderModule());
        register(new NoFallModule());
        register(new AirStuckModule());
        register(new SpeedModule());
        register(new SuperFireworkModule());

        // Visuals & Animations
        register(new PlayerEspModule());
        register(new TargetEspModule());
        register(new ViewModelModule());
        register(new NameTagsModule());
        register(new PopChamsModule());
        register(new KillEffectModule());
        register(new ItemEspModule());
        register(new TracersModule());
        register(new GlowEspModule());
        register(new JumpCirclesModule());
        register(new ParticlesModule());
        register(new ChinaHatModule());
        register(new HitBubblesModule());
        register(new ArrowsModule());
        register(new FullbrightModule());
        register(new TrailsModule());
        register(new CrosshairModule());
        register(new SwingAnimationsModule());
        register(new CustomFontModule());
        register(new ToggleSoundsModule());
        register(new NoRenderModule());
        register(new ZoomModule());
        register(new PredictionsModule());
        register(new AspectRatioModule());
        register(new InterpolateF5Module());
        register(new BlockOverlayModule());
        register(new DamageTintModule());
        register(new SeeInvisiblesModule());
        register(new HandsModule());
        register(new AmbienceModule());
        register(new FreeCamModule());
        register(new FreeLookModule());
        register(new ObjectInfoModule());
        register(new BlockESPModule());
        register(new TntTimerModule());
        register(new PotsTimerModule());
        register(new FireworkESPModule());
        register(new ShulkerPreviewModule());
        register(new ItemPhysicsModule());
        register(new MotionBlurModule());
        register(new BetterTabModule());

        // Optimization
        register(new LowFireModule());
        register(new MemoryCleanerModule());
        register(new EntityCullingModule());
        register(new FrameSyncModule());
        register(new VideoOptimizerModule());

        // Other
        register(new AssistModule());
        register(new AutoBuyModule());
        register(new BotsModule());
        register(new AutoAcceptModule());
        register(new LockSlotModule());
        register(new NoInteractModule());
        register(new ServerRPSpoofModule());
        register(new AuctionHelperModule());
        register(new NameProtectModule());
        register(new PanicModule());
        register(new CaptureBypassModule());
        register(new DiscordRpcModule());

        // HUD
        InterfaceModule iface = new InterfaceModule();
        TargetHudModule targetHud = new TargetHudModule();
        KeystrokesHud keystrokes = new KeystrokesHud();
        ArmorHud armor = new ArmorHud();
        PotionHud potion = new PotionHud();
        CooldownsHud cooldowns = new CooldownsHud();
        BindsHud binds = new BindsHud();
        InventoryHud inventoryHud = new InventoryHud();
        AutoBuyReportHud autoBuyReport = new AutoBuyReportHud();
        WatermarkHud watermarkHud = new WatermarkHud();
        DynamicIslandHud dynamicIslandHud = new DynamicIslandHud();
        ArrayListHud arrayListHud = new ArrayListHud();
        StaffListHud staffListHud = new StaffListHud();
        RadarHud radarHud = new RadarHud();
        BpsGraphHud bpsGraphHud = new BpsGraphHud();
        TotemCounterHud totemCounterHud = new TotemCounterHud();
        CoordinatesHud coordinatesHud = new CoordinatesHud();
        NotificationsHud notificationsHud = new NotificationsHud();
        ServerKeybindsHud serverKeybindsHud = new ServerKeybindsHud();
        SchedulesHud schedulesHud = new SchedulesHud();
        CustomHotbarHud customHotbarHud = new CustomHotbarHud();
        MediaPlayerHud mediaPlayerHud = new MediaPlayerHud();
        ProfilerHud profilerHud = new ProfilerHud();

        register(iface);
        register(targetHud);
        register(keystrokes);
        register(armor);
        register(potion);
        register(cooldowns);
        register(binds);
        register(inventoryHud);
        register(autoBuyReport);
        register(watermarkHud);
        register(dynamicIslandHud);
        register(arrayListHud);
        register(staffListHud);
        register(radarHud);
        register(bpsGraphHud);
        register(totemCounterHud);
        register(coordinatesHud);
        register(notificationsHud);
        register(serverKeybindsHud);
        register(schedulesHud);
        register(customHotbarHud);
        register(mediaPlayerHud);
        register(profilerHud);

        HudManager.getInstance().register(targetHud);
        HudManager.getInstance().register(keystrokes);
        HudManager.getInstance().register(armor);
        HudManager.getInstance().register(potion);
        HudManager.getInstance().register(cooldowns);
        HudManager.getInstance().register(binds);
        HudManager.getInstance().register(inventoryHud);
        HudManager.getInstance().register(autoBuyReport);
        HudManager.getInstance().register(watermarkHud);
        HudManager.getInstance().register(dynamicIslandHud);
        HudManager.getInstance().register(arrayListHud);
        HudManager.getInstance().register(staffListHud);
        HudManager.getInstance().register(radarHud);
        HudManager.getInstance().register(bpsGraphHud);
        HudManager.getInstance().register(totemCounterHud);
        HudManager.getInstance().register(coordinatesHud);
        HudManager.getInstance().register(notificationsHud);
        HudManager.getInstance().register(serverKeybindsHud);
        HudManager.getInstance().register(schedulesHud);
        HudManager.getInstance().register(customHotbarHud);
        HudManager.getInstance().register(mediaPlayerHud);
        HudManager.getInstance().register(profilerHud);
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

    public void onPreTick() {
        boolean prof = dev.opticumeclient.client.command.ModuleProfiler.getInstance().isRecording();
        for (Module m : modules) {
            if (m.isEnabled()) {
                long t0 = prof ? System.nanoTime() : 0L;
                m.onPreTick();
                if (prof) {
                    dev.opticumeclient.client.command.ModuleProfiler.getInstance().recordTick(m, System.nanoTime() - t0);
                }
            }
        }
    }

    public void onTick() {
        boolean prof = dev.opticumeclient.client.command.ModuleProfiler.getInstance().isRecording();
        for (Module m : modules) {
            if (m.isEnabled()) {
                long t0 = prof ? System.nanoTime() : 0L;
                m.onTick();
                if (prof) {
                    dev.opticumeclient.client.command.ModuleProfiler.getInstance().recordTick(m, System.nanoTime() - t0);
                }
            }
        }
        HudAlertBus.getInstance().onTick();
    }

    public void onRender3D(PoseStack matrices, float tickDelta) {
        boolean prof = dev.opticumeclient.client.command.ModuleProfiler.getInstance().isRecording();
        for (Module m : modules) {
            if (m.isEnabled()) {
                long t0 = prof ? System.nanoTime() : 0L;
                m.onRender3D(matrices, tickDelta);
                if (prof) {
                    dev.opticumeclient.client.command.ModuleProfiler.getInstance().recordRender3D(m, System.nanoTime() - t0);
                }
            }
        }
    }

    public void onRender2D(GuiGraphicsExtractor context, float tickDelta) {
        Render3DHelper.flush2D(context);
        boolean prof = dev.opticumeclient.client.command.ModuleProfiler.getInstance().isRecording();
        for (Module m : modules) {
            if (m.isEnabled() && m.getCategory() != Category.HUD) {
                long t0 = prof ? System.nanoTime() : 0L;
                m.onRender2D(context, tickDelta);
                if (prof) {
                    dev.opticumeclient.client.command.ModuleProfiler.getInstance().recordRender2D(m, System.nanoTime() - t0);
                }
            }
        }
        HudManager.getInstance().render(context, tickDelta);
    }

    public void onKeyPressed(int key) {
        if (key <= 0) return;
        for (Module m : modules) {
            if (m.getKeyBind() == key) {
                if (m instanceof ZoomModule zoom && zoom.isHoldMode()) {
                    continue;
                }
                m.toggle();
            }
        }
    }
}
