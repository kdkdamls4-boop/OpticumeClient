package dev.opticumeclient.client.hud;

import dev.opticumeclient.client.friend.FriendManager;
import dev.opticumeclient.client.modules.Category;
import dev.opticumeclient.client.modules.Module;
import dev.opticumeclient.client.modules.ModuleManager;
import dev.opticumeclient.client.render.Animation;
import dev.opticumeclient.client.render.ColorUtils;
import dev.opticumeclient.client.render.Easing;
import dev.opticumeclient.client.render.Render2DHelper;
import dev.opticumeclient.client.render.TextureAssets;
import dev.opticumeclient.client.render.Theme;
import dev.opticumeclient.client.render.ThemeManager;
import dev.opticumeclient.client.setting.BooleanSetting;
import net.minecraft.client.gui.GuiGraphicsExtractor; 
import net.minecraft.client.gui.screens.ChatScreen; 
import net.minecraft.client.multiplayer.PlayerInfo; 
import com.mojang.blaze3d.platform.InputConstants; 
import net.minecraft.world.entity.Entity; 
import net.minecraft.world.entity.player.Player; 
import net.minecraft.world.item.Item; 
import net.minecraft.world.item.ItemStack; 
import net.minecraft.world.item.Items; 
import net.minecraft.core.BlockPos; 
import net.minecraft.world.level.GameType; 
import org.joml.Matrix3x2fStack;
import com.mojang.blaze3d.platform.InputConstants; 

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Multi-widget HUD suite for OpticumeClient (Nursultan Nextgen / Catlavan style).
 * Provides 11 independently draggable HUD widgets with Smart Snap Lines:
 * Watermark, Dynamic Island, ArrayList, KeyBinds, Cooldowns, StaffList,
 * 2D Radar, BPS Speed Graph, Totem/GApple Counter, Coordinates, and Notifications.
 */
public class InfoHud extends Module {
    private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("HH:mm:ss");
    private static final String[] STAFF_KEYWORDS = {
            "admin", "moder", "helper", "curator", "owner", "dev", "staff", "support",
            "builder", "yt", "media", "модер", "хелпер", "админ", "куратор", "ст.", "гл."
    };

    private final BooleanSetting watermark = new BooleanSetting("Watermark", "Стеклянная капсула Watermark (FPS, Ping, BPS, XYZ)", true);
    private final BooleanSetting dynamicIsland = new BooleanSetting("DynamicIsland", "Динамический островок с эквалайзером и уведомлениями", true);
    private final BooleanSetting arrayList = new BooleanSetting("ArrayList", "Список активных модулей с волновым градиентом", true);
    private final BooleanSetting keyBinds = new BooleanSetting("KeyBinds", "Плавающее окно активных биндов клавиш", true);
    private final BooleanSetting cooldowns = new BooleanSetting("Cooldowns", "Окно активных кулдаунов предметов с иконками", true);
    private final BooleanSetting staffList = new BooleanSetting("StaffList", "Детект модераторов и спектаторов на сервере", false);
    private final BooleanSetting radar = new BooleanSetting("Radar", "2D радар игроков с вращающимся конусом FOV Sweep", false);
    private final BooleanSetting bpsGraph = new BooleanSetting("BpsGraph", "График скорости игрока в блоках/сек", true);
    private final BooleanSetting totemCounter = new BooleanSetting("TotemCounter", "Счетчик тотемов, геплов и перлов в инвентаре", true);
    private final BooleanSetting coordinates = new BooleanSetting("Coordinates", "Отдельная капсула координат XYZ и направления", true);
    private final BooleanSetting inventoryHud = new BooleanSetting("InventoryHUD", "Превью 27 слотов основного инвентаря", false);
    private final BooleanSetting notifications = new BooleanSetting("Notifications", "Всплывающие уведомления о переключении модулей", true);

    // Draggable widgets registered in HudManager
    private final HudManager.DraggableWidget watermarkWidget;
    private final HudManager.DraggableWidget islandWidget;
    private final HudManager.DraggableWidget arrayListWidget;
    private final HudManager.DraggableWidget keyBindsWidget;
    private final HudManager.DraggableWidget cooldownsWidget;
    private final HudManager.DraggableWidget staffListWidget;
    private final HudManager.DraggableWidget radarWidget;
    private final HudManager.DraggableWidget bpsGraphWidget;
    private final HudManager.DraggableWidget totemWidget;
    private final HudManager.DraggableWidget coordsWidget;
    private final HudManager.DraggableWidget inventoryWidget;
    private final HudManager.DraggableWidget notificationsWidget;

    // Dynamic Island & Notifications state
    private final Animation islandWidthAnim = new Animation(166.0f, 260L, Easing.EASE_OUT_BACK);
    private final Animation islandHeightAnim = new Animation(22.0f, 260L, Easing.EASE_OUT_BACK);
    private final Map<Module, Boolean> moduleStates = new HashMap<>();
    private final List<ToggleAlert> activeAlerts = new ArrayList<>();
    private boolean statesInitialized = false;

    // Speed & BPS Graph sampling
    private static final int BPS_SAMPLES = 44;
    private final float[] bpsHistory = new float[BPS_SAMPLES];
    private int bpsWriteIndex = 0;
    private long lastBpsSampleMs = 0L;
    private double currentBps = 0.0;
    private double peakBps = 0.0;

    // Cooldown tracking
    private Map<String, CooldownItemEntry> trackedCooldownItems;
    private final Map<Item, Long> cooldownStartMs = new HashMap<>();
    private final Map<Item, Float> lastCooldownPct = new HashMap<>();

    private record CooldownItemEntry(String label, Item item, float defaultSeconds) {}

    private static class ToggleAlert {
        final String moduleName;
        final boolean enabled;
        final long createdAt;
        final Animation slideAnim = new Animation(0.0f, 240L, Easing.EASE_OUT_BACK);
        private boolean evicting = false;

        ToggleAlert(String moduleName, boolean enabled) {
            this.moduleName = moduleName;
            this.enabled = enabled;
            this.createdAt = System.currentTimeMillis();
            this.slideAnim.animateTo(1.0f, 240L, Easing.EASE_OUT_BACK);
        }

        void evict() {
            if (!evicting) {
                evicting = true;
                slideAnim.animateTo(0.0f, 180L, Easing.EASE_IN_BACK);
            }
        }

        boolean isEvicting() {
            return evicting;
        }

        boolean isDead() {
            return evicting && (slideAnim.isFinished() || slideAnim.getValue() <= 0.01f) && slideAnim.getTargetValue() == 0.0f;
        }

        float getAgeSeconds() {
            return (System.currentTimeMillis() - createdAt) / 1000.0f;
        }
    }

    private ItemStack totemStack;
    private ItemStack gappleStack;
    private ItemStack pearlStack;

    private static class ArrayListRowHolder {
        float pillX;
        float pillY;
        float pillW;
        float rowH;
        int waveColor;
        int alpha255;
        float anim;
        String name;
        String suffix;
        float nameW;
    }
    private final List<ArrayListRowHolder> arrayListRowHolders = new ArrayList<>();
    private int arrayListRowCount = 0;

    private ArrayListRowHolder getArrayListRowHolder(int idx) {
        while (arrayListRowHolders.size() <= idx) {
            arrayListRowHolders.add(new ArrayListRowHolder());
        }
        return arrayListRowHolders.get(idx);
    }

    private record StaffEntry(String name, String statusTag, int statusColor) {}
    private record CooldownRenderRow(String label, ItemStack stack, float progress, String timeText) {}
    private record RadarPoint(double dx, double dz, boolean isFriend) {}

    private static final List<StaffEntry> PREVIEW_STAFF_LIST = List.of(
            new StaffEntry("ExampleAdmin", "ONLINE", 0xFF22C55E)
    );
    private List<CooldownRenderRow> previewCooldownRows;
    private List<CooldownRenderRow> chatPreviewCooldownRows;

    private void ensureStaticItemStacks() {
        if (totemStack != null && previewCooldownRows != null && chatPreviewCooldownRows != null) return;
        try {
            totemStack = Items.TOTEM_OF_UNDYING.getDefaultInstance();
            gappleStack = Items.GOLDEN_APPLE.getDefaultInstance();
            pearlStack = Items.ENDER_PEARL.getDefaultInstance();
            previewCooldownRows = List.of(
                    new CooldownRenderRow("Ender Pearl", Items.ENDER_PEARL.getDefaultInstance(), 0.0f, "READY"),
                    new CooldownRenderRow("Golden Apple", Items.ENCHANTED_GOLDEN_APPLE.getDefaultInstance(), 0.0f, "READY"),
                    new CooldownRenderRow("Chorus Fruit", Items.CHORUS_FRUIT.getDefaultInstance(), 0.0f, "READY"),
                    new CooldownRenderRow("Wind Charge", Items.WIND_CHARGE.getDefaultInstance(), 0.0f, "READY")
            );
            chatPreviewCooldownRows = List.of(
                    new CooldownRenderRow("Ender Pearl", Items.ENDER_PEARL.getDefaultInstance(), 0.65f, "9.8s"),
                    new CooldownRenderRow("Golden Apple", Items.ENCHANTED_GOLDEN_APPLE.getDefaultInstance(), 0.40f, "4.0s"),
                    new CooldownRenderRow("Chorus Fruit", Items.CHORUS_FRUIT.getDefaultInstance(), 0.0f, "READY"),
                    new CooldownRenderRow("Wind Charge", Items.WIND_CHARGE.getDefaultInstance(), 0.0f, "READY")
            );
        } catch (Throwable ignored) {
        }
    }

    private String cachedTimeString = "12:00:00";
    private long lastTimeUpdateMs = 0L;

    private final List<Module> cachedVisibleModules = new ArrayList<>();
    private final List<Module> cachedBoundModules = new ArrayList<>();
    private final List<CooldownRenderRow> cachedCooldownRows = new ArrayList<>();
    private final List<StaffEntry> cachedStaffList = new ArrayList<>();
    private final List<RadarPoint> cachedRadarPoints = new ArrayList<>();
    private int cachedTotems = 0;
    private int cachedGapples = 0;
    private int cachedPearls = 0;
    // Watermark telemetry cache (updated in onTick at 20 TPS instead of per-frame in onRender2D)
    private String cachedFpsStr = "0 FPS";
    private String cachedPingStr = "0 ms";
    private String cachedBpsStr = "0.0 BPS";
    private String cachedBpsGraphStr = "0.0 BPS";
    private String cachedXyzStr = "0 0 0";
    private String cachedUser = "Player";
    private float cachedTitleW = 58.0f;
    private float cachedUserW = 30.0f;
    private float cachedFpsW = 32.0f;
    private float cachedPingW = 28.0f;
    private float cachedBpsW = 42.0f;
    private float cachedBpsGraphW = 42.0f;
    private float cachedXyzW = 48.0f;
    private float cachedWatermarkTotalW = 270.0f;

    private static String formatBps(double bps) {
        long tenths = Math.round(bps * 10);
        return (tenths / 10) + "." + (tenths % 10) + " BPS";
    }

    public InfoHud() {
        super("InfoHUD", "Мульти-виджет интерфейса (Watermark, Dynamic Island, ArrayList, KeyBinds, Cooldowns, Radar и др.)", Category.HUD);

        addSetting(watermark);
        addSetting(dynamicIsland);
        addSetting(arrayList);
        addSetting(keyBinds);
        addSetting(cooldowns);
        addSetting(staffList);
        addSetting(radar);
        addSetting(bpsGraph);
        addSetting(totemCounter);
        addSetting(coordinates);
        addSetting(inventoryHud);
        addSetting(notifications);

        setEnabled(true);

        HudManager hm = HudManager.getInstance();
        this.watermarkWidget = hm.registerWidget(new HudManager.DraggableWidget(
                "Watermark", 8.0f, 8.0f, 270.0f, 22.0f, () -> isEnabled() && watermark.isEnabled()));
        this.islandWidget = hm.registerWidget(new HudManager.DraggableWidget(
                "DynamicIsland", 360.0f, 8.0f, 166.0f, 22.0f, () -> isEnabled() && dynamicIsland.isEnabled()));
        this.arrayListWidget = hm.registerWidget(new HudManager.DraggableWidget(
                "ArrayList", 740.0f, 8.0f, 118.0f, 150.0f, () -> isEnabled() && arrayList.isEnabled()));
        this.radarWidget = hm.registerWidget(new HudManager.DraggableWidget(
                "2D Radar", 8.0f, 36.0f, 84.0f, 84.0f, () -> isEnabled() && radar.isEnabled()));
        this.keyBindsWidget = hm.registerWidget(new HudManager.DraggableWidget(
                "KeyBinds", 98.0f, 36.0f, 132.0f, 58.0f, () -> isEnabled() && keyBinds.isEnabled()));
        this.cooldownsWidget = hm.registerWidget(new HudManager.DraggableWidget(
                "Cooldowns", 236.0f, 36.0f, 138.0f, 64.0f, () -> isEnabled() && cooldowns.isEnabled()));
        this.staffListWidget = hm.registerWidget(new HudManager.DraggableWidget(
                "StaffList", 380.0f, 36.0f, 136.0f, 54.0f, () -> isEnabled() && staffList.isEnabled()));
        this.bpsGraphWidget = hm.registerWidget(new HudManager.DraggableWidget(
                "BPS Graph", 8.0f, 228.0f, 134.0f, 48.0f, () -> isEnabled() && bpsGraph.isEnabled()));
        this.totemWidget = hm.registerWidget(new HudManager.DraggableWidget(
                "TotemCounter", 380.0f, 435.0f, 126.0f, 24.0f, () -> isEnabled() && totemCounter.isEnabled()));
        this.coordsWidget = hm.registerWidget(new HudManager.DraggableWidget(
                "Coordinates", 8.0f, 470.0f, 168.0f, 20.0f, () -> isEnabled() && coordinates.isEnabled()));
        this.inventoryWidget = hm.registerWidget(new HudManager.DraggableWidget(
                "InventoryHUD", 524.0f, 36.0f, 166.0f, 62.0f, () -> isEnabled() && inventoryHud.isEnabled()));
        this.notificationsWidget = hm.registerWidget(new HudManager.DraggableWidget(
                "Notifications", 700.0f, 380.0f, 156.0f, 52.0f, () -> isEnabled() && notifications.isEnabled()));
    }

    private Map<String, CooldownItemEntry> getTrackedCooldowns() {
        if (trackedCooldownItems == null) {
            trackedCooldownItems = new LinkedHashMap<>();
            try {
                trackedCooldownItems.put("Ender Pearl", new CooldownItemEntry("Ender Pearl", Items.ENDER_PEARL, 15.0f));
                trackedCooldownItems.put("GApple", new CooldownItemEntry("Enchanted GApple", Items.ENCHANTED_GOLDEN_APPLE, 30.0f));
                trackedCooldownItems.put("Golden Apple", new CooldownItemEntry("Golden Apple", Items.GOLDEN_APPLE, 4.0f));
                trackedCooldownItems.put("Chorus Fruit", new CooldownItemEntry("Chorus Fruit", Items.CHORUS_FRUIT, 1.0f));
                trackedCooldownItems.put("Totem", new CooldownItemEntry("Totem", Items.TOTEM_OF_UNDYING, 15.0f));
                trackedCooldownItems.put("Shield", new CooldownItemEntry("Shield", Items.SHIELD, 5.0f));
                trackedCooldownItems.put("Firework", new CooldownItemEntry("Firework", Items.FIREWORK_ROCKET, 3.0f));
                trackedCooldownItems.put("Wind Charge", new CooldownItemEntry("Wind Charge", Items.WIND_CHARGE, 0.5f));
            } catch (Throwable ignored) {
            }
        }
        return trackedCooldownItems;
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;

        updateSpeedMetrics();

        long now = System.currentTimeMillis();
        if (now - lastTimeUpdateMs >= 500L) {
            cachedTimeString = TIME_FORMAT.format(new Date(now));
            lastTimeUpdateMs = now;
        }

        // Cache watermark telemetry at 20 TPS (zero allocations in onRender2D)
        int fps = mc.getFps();
        int ping = getPlayerPing();
        cachedFpsStr = fps + " FPS";
        cachedPingStr = ping + " ms";
        cachedBpsStr = formatBps(currentBps);
        cachedBpsGraphStr = cachedBpsStr;
        BlockPos pos = mc.player.blockPosition();
        cachedXyzStr = pos.getX() + " " + pos.getY() + " " + pos.getZ();
        cachedUser = (mc.getUser() != null && mc.getUser().getName() != null) ? mc.getUser().getName() : "Player";

        float wmScale = 0.78f;
        cachedTitleW = Render2DHelper.getTextWidth("OpticumeClient", 0.82f);
        cachedUserW = Render2DHelper.getTextWidth(cachedUser, wmScale);
        cachedFpsW = Render2DHelper.getTextWidth(cachedFpsStr, wmScale);
        cachedPingW = Render2DHelper.getTextWidth(cachedPingStr, wmScale);
        cachedBpsW = Render2DHelper.getTextWidth(cachedBpsStr, wmScale);
        cachedBpsGraphW = Render2DHelper.getTextWidth(cachedBpsGraphStr, 0.74f);
        cachedXyzW = Render2DHelper.getTextWidth(cachedXyzStr, wmScale);
        cachedWatermarkTotalW = 24.0f + cachedTitleW + 14.0f + cachedUserW + 22.0f + cachedFpsW + 22.0f + cachedPingW + 22.0f + cachedBpsW + 22.0f + cachedXyzW + 12.0f;

        // Dynamic maxVisible notifications calculation based on screen height
        float notifCardH = 26.0f;
        float notifScreenH = mc.getWindow() != null ? (float) mc.getWindow().getGuiScaledHeight() : 540.0f;
        float notifStartY = Math.max(4.0f, Math.min(notifScreenH - 30.0f - 4.0f, notificationsWidget.getY()));
        int maxVisible = Math.max(1, Math.min(4, (int) ((notifScreenH - notifStartY - 12.0f) / (notifCardH + 4.0f))));

        // 1. Module toggles for Dynamic Island & Toast Notifications
        List<Module> allModules = ModuleManager.getInstance().getModules();
        if (!statesInitialized) {
            for (Module m : allModules) {
                moduleStates.put(m, m.isEnabled());
                if (m.isEnabled() && m.getToggleAnimation().getValue() < 0.05f) {
                    m.getToggleAnimation().setImmediate(1.0f);
                }
            }
            statesInitialized = true;
        } else {
            for (Module m : allModules) {
                Boolean prev = moduleStates.get(m);
                boolean nowState = m.isEnabled();
                if (prev != null && prev != nowState) {
                    moduleStates.put(m, nowState);
                    activeAlerts.add(new ToggleAlert(m.getName(), nowState));
                } else if (prev == null) {
                    moduleStates.put(m, nowState);
                }
            }
        }

        // Evict expired alerts (> 1.8s) or when active (non-evicting) alerts exceed maxVisible
        int activeAlertCount = 0;
        for (ToggleAlert alert : activeAlerts) {
            if (!alert.isEvicting()) {
                activeAlertCount++;
            }
        }
        for (ToggleAlert alert : activeAlerts) {
            if (!alert.isEvicting()) {
                if (alert.getAgeSeconds() > 1.8f || activeAlertCount > maxVisible) {
                    alert.evict();
                    activeAlertCount--;
                }
            }
        }

        // Remove only dead alerts
        activeAlerts.removeIf(ToggleAlert::isDead);

        // 2. Cache visible modules for ArrayList (sorted by width)
        cachedVisibleModules.clear();
        for (Module m : allModules) {
            if (m == this) continue;
            if (m.isEnabled() && m.getToggleAnimation().getValue() < 0.05f && m.getToggleAnimation().getTargetValue() == 0.0f) {
                m.getToggleAnimation().setImmediate(1.0f);
            }
            if (m.isEnabled() || m.getToggleAnimation().getValue() > 0.02f) {
                cachedVisibleModules.add(m);
            }
        }
        float scale = 0.82f;
        cachedVisibleModules.sort(Comparator.comparingDouble((Module m) -> {
            String suffix = m.getDisplaySuffix();
            String full = (suffix != null && !suffix.isEmpty()) ? (m.getName() + " " + suffix) : m.getName();
            return -Render2DHelper.getTextWidth(full, scale);
        }));

        // 3. Cache bound modules for KeyBinds
        cachedBoundModules.clear();
        for (Module m : allModules) {
            if (m.getKeyBind() > 0) {
                cachedBoundModules.add(m);
            }
        }

        // 4. Cache cooldowns
        cachedCooldownRows.clear();
        Map<String, CooldownItemEntry> tracked = getTrackedCooldowns();
        for (CooldownItemEntry entry : tracked.values()) {
            ItemStack defaultStack = entry.item().getDefaultInstance();
            float pct = 0.0f;
            if (mc.player.getCooldowns().isOnCooldown(defaultStack)) {
                pct = mc.player.getCooldowns().getCooldownPercent(defaultStack, 0.0f);
            }
            Float prevPct = lastCooldownPct.getOrDefault(entry.item(), 0.0f);
            if (pct > 0.01f && prevPct <= 0.01f) {
                cooldownStartMs.put(entry.item(), now);
            }
            lastCooldownPct.put(entry.item(), pct);

            if (pct > 0.01f) {
                float remSeconds = Math.max(0.1f, pct * entry.defaultSeconds());
                int remTenths = Math.round(remSeconds * 10);
                String timeText = (remTenths / 10) + "." + (remTenths % 10) + "s";
                cachedCooldownRows.add(new CooldownRenderRow(entry.label(), defaultStack, pct, timeText));
            }
        }

        // 5. Cache staff list
        cachedStaffList.clear();
        if (mc.getConnection() != null) {
            Collection<PlayerInfo> tabList = mc.getConnection().getOnlinePlayers();
            for (PlayerInfo entry : tabList) {
                if (entry.getProfile() == null) continue;
                String name = entry.getProfile().name();
                if (mc.player != null && name.equalsIgnoreCase(mc.player.getName().getString())) continue;

                String display = entry.getTabListDisplayName() != null ? entry.getTabListDisplayName().getString() : name;
                String lower = (name + " " + display).toLowerCase(Locale.ROOT);

                boolean isSpec = entry.getGameMode() == GameType.SPECTATOR;
                boolean keywordMatch = false;
                for (String kw : STAFF_KEYWORDS) {
                    if (lower.contains(kw)) {
                        keywordMatch = true;
                        break;
                    }
                }

                if (isSpec || keywordMatch) {
                    boolean isNear = false;
                    if (mc.level != null) {
                        for (Player p : mc.level.players()) {
                            if (p != mc.player && p.getName().getString().equalsIgnoreCase(name)) {
                                isNear = true;
                                break;
                            }
                        }
                    }
                    String tag = isSpec ? "[SPEC]" : (isNear ? "[NEAR]" : "[ONLINE]");
                    int col = isSpec ? 0xFFFF5252 : (isNear ? 0xFFFBBF24 : 0xFF4ADE80);
                    cachedStaffList.add(new StaffEntry(name, tag, col));
                }
            }
        }

        // 6. Cache inventory totem/gapple/pearl counts
        cachedTotems = countInventoryItem(Items.TOTEM_OF_UNDYING);
        cachedGapples = countInventoryItem(Items.GOLDEN_APPLE) + countInventoryItem(Items.ENCHANTED_GOLDEN_APPLE);
        cachedPearls = countInventoryItem(Items.ENDER_PEARL);

        // 7. Cache radar player relative points
        cachedRadarPoints.clear();
        if (mc.level != null && mc.player != null) {
            double px = mc.player.getX();
            double pz = mc.player.getZ();
            float maxRange = 55.0f;
            for (Entity entity : mc.level.entitiesForRendering()) {
                if (entity == mc.player || !(entity instanceof Player p) || !p.isAlive()) continue;
                double dx = p.getX() - px;
                double dz = p.getZ() - pz;
                if (Math.hypot(dx, dz) <= maxRange) {
                    boolean isFriend = FriendManager.getInstance().isFriend(p);
                    cachedRadarPoints.add(new RadarPoint(dx, dz, isFriend));
                }
            }
        }
    }

    @Override
    public void onRender2D(GuiGraphicsExtractor context, float tickDelta) {
        if (!isEnabled() || mc.player == null) return;

        TextureAssets.ensureInitialized();

        float screenW = mc.getWindow() != null ? mc.getWindow().getGuiScaledWidth() : 960.0f;

        // Keep ArrayList & DynamicIsland smartly positioned on first load if outside screen
        if (!islandWidget.isDragging() && islandWidget.getX() == 360.0f && Math.abs(screenW - 960.0f) > 10.0f) {
            islandWidget.setPosition((screenW - islandWidget.getWidth()) * 0.5f, islandWidget.getY());
        }
        if (!arrayListWidget.isDragging() && arrayListWidget.getX() > screenW - 30.0f) {
            arrayListWidget.setPosition(Math.max(8.0f, screenW - arrayListWidget.getWidth() - 8.0f), arrayListWidget.getY());
        }

        if (watermark.isEnabled()) {
            renderWatermark(context);
        }
        if (dynamicIsland.isEnabled()) {
            renderDynamicIsland(context);
        }
        if (arrayList.isEnabled()) {
            renderArrayList(context);
        }
        if (keyBinds.isEnabled()) {
            renderKeyBinds(context);
        }
        if (cooldowns.isEnabled()) {
            renderCooldowns(context, tickDelta);
        }
        if (staffList.isEnabled()) {
            renderStaffList(context);
        }
        if (radar.isEnabled()) {
            renderRadar(context, tickDelta);
        }
        if (bpsGraph.isEnabled()) {
            renderBpsGraph(context);
        }
        if (totemCounter.isEnabled()) {
            renderTotemCounter(context);
        }
        if (coordinates.isEnabled()) {
            renderCoordinates(context);
        }
        if (inventoryHud.isEnabled()) {
            renderInventoryHud(context);
        }
        if (notifications.isEnabled()) {
            renderNotifications(context);
        }
    }

    private void updateSpeedMetrics() {
        if (mc.player == null) return;
        double dx = mc.player.getX() - mc.player.xOld;
        double dz = mc.player.getZ() - mc.player.zOld;
        this.currentBps = Math.hypot(dx, dz) * 20.0;
        if (this.currentBps > this.peakBps) {
            this.peakBps = this.currentBps;
        } else {
            this.peakBps = Math.max(this.currentBps, this.peakBps * 0.992);
        }

        long now = System.currentTimeMillis();
        if (now - lastBpsSampleMs >= 45L) {
            bpsHistory[bpsWriteIndex] = (float) this.currentBps;
            bpsWriteIndex = (bpsWriteIndex + 1) % BPS_SAMPLES;
            lastBpsSampleMs = now;
        }
    }

    private int getPlayerPing() {
        if (mc.getConnection() != null && mc.player != null) {
            PlayerInfo entry = mc.getConnection().getPlayerInfo(mc.player.getUUID());
            if (entry != null) {
                return Math.max(0, entry.getLatency());
            }
        }
        return 0;
    }

    // =========================================================================
    // 1. WATERMARK (Nursultan / Catlavan Liquid Glass Capsule with Icons)
    // =========================================================================
    private void renderWatermark(GuiGraphicsExtractor context) {
        Theme theme = ThemeManager.getInstance().getCurrentTheme();
        int c1 = theme.getPrimaryColor();
        int c2 = theme.getSecondaryColor();

        String title = "OpticumeClient";
        float scale = 0.78f;
        float h = 22.0f;

        watermarkWidget.setBounds(cachedWatermarkTotalW, h);
        float x = watermarkWidget.getX();
        float y = watermarkWidget.getY();

        Render2DHelper.drawLiquidGlass(context, x, y, cachedWatermarkTotalW, h, 6.0f, 225, 44);

        float curX = x + 6.0f;
        // Mini Logo icon with accent tint
        Render2DHelper.drawTexture(context, TextureAssets.MINI_LOGO, curX, y + 4.5f, 13.0f, 13.0f, c1);
        curX += 16.0f;

        // Gradient Client Name
        Render2DHelper.drawScaledText(context, title, curX, y + 6.5f, ColorUtils.interpolateColor(c1, c2, 0.35f), 0.82f, true);
        curX += cachedTitleW + 6.0f;
        drawCapsuleDivider(context, curX, y + 5.0f, h - 10.0f, c1);
        curX += 6.0f;

        // Username
        Render2DHelper.drawScaledText(context, cachedUser, curX, y + 7.0f, theme.getTextColor(), scale, false);
        curX += cachedUserW + 6.0f;
        drawCapsuleDivider(context, curX, y + 5.0f, h - 10.0f, c1);
        curX += 6.0f;

        // FPS with icon
        Render2DHelper.drawTexture(context, TextureAssets.FPS_ICON, curX, y + 5.5f, 10.0f, 10.0f, c2);
        curX += 13.0f;
        Render2DHelper.drawScaledText(context, cachedFpsStr, curX, y + 7.0f, theme.getTextColor(), scale, false);
        curX += cachedFpsW + 6.0f;
        drawCapsuleDivider(context, curX, y + 5.0f, h - 10.0f, c1);
        curX += 6.0f;

        // Ping with icon
        Render2DHelper.drawTexture(context, TextureAssets.PING_ICON, curX, y + 5.5f, 10.0f, 10.0f, c1);
        curX += 13.0f;
        Render2DHelper.drawScaledText(context, cachedPingStr, curX, y + 7.0f, theme.getTextColor(), scale, false);
        curX += cachedPingW + 6.0f;
        drawCapsuleDivider(context, curX, y + 5.0f, h - 10.0f, c1);
        curX += 6.0f;

        // Speed BPS with icon
        Render2DHelper.drawTexture(context, TextureAssets.SPEED_ICON, curX, y + 5.5f, 10.0f, 10.0f, c2);
        curX += 13.0f;
        Render2DHelper.drawScaledText(context, cachedBpsStr, curX, y + 7.0f, theme.getTextColor(), scale, false);
        curX += cachedBpsW + 6.0f;
        drawCapsuleDivider(context, curX, y + 5.0f, h - 10.0f, c1);
        curX += 6.0f;

        // Coords with icon
        Render2DHelper.drawTexture(context, TextureAssets.COORDS_ICON, curX, y + 5.5f, 10.0f, 10.0f, c1);
        curX += 13.0f;
        Render2DHelper.drawScaledText(context, cachedXyzStr, curX, y + 7.0f, theme.getMutedTextColor(), scale, false);
    }

    private void drawCapsuleDivider(GuiGraphicsExtractor context, float x, float y, float h, int color) {
        Render2DHelper.drawRect(context, x, y, 1.0f, h, ColorUtils.withAlpha(color, 90));
    }

    // =========================================================================
    // 2. DYNAMIC ISLAND (Symmetrically expanding pill with Equalizer & Alerts)
    // =========================================================================
    private void renderDynamicIsland(GuiGraphicsExtractor context) {
        Theme theme = ThemeManager.getInstance().getCurrentTheme();
        int c1 = theme.getPrimaryColor();
        int c2 = theme.getSecondaryColor();

        ToggleAlert latestAlert = null;
        if (!activeAlerts.isEmpty()) {
            ToggleAlert candidate = activeAlerts.get(activeAlerts.size() - 1);
            if (candidate.getAgeSeconds() < 2.1f) {
                latestAlert = candidate;
            }
        }

        boolean expanded = latestAlert != null;
        float targetW = expanded ? 216.0f : 168.0f;
        float targetH = expanded ? 30.0f : 22.0f;

        islandWidthAnim.animateTo(targetW, 260L, Easing.EASE_OUT_BACK);
        islandHeightAnim.animateTo(targetH, 260L, Easing.EASE_OUT_BACK);

        float curW = Math.max(120.0f, islandWidthAnim.getValue());
        float curH = Math.max(18.0f, islandHeightAnim.getValue());

        // Symmetrical expansion around center X without mutating islandWidget position on render frames
        float anchorCenterX = islandWidget.getX() + islandWidget.getWidth() * 0.5f;
        float drawX = anchorCenterX - curW * 0.5f;
        float drawY = islandWidget.getY();

        islandWidget.setBounds(curW, curH);

        float radius = curH * 0.5f;
        Render2DHelper.drawLiquidGlass(context, drawX, drawY, curW, curH, radius, 238, expanded ? 68 : 42);

        // Animated 4-bar Equalizer on the left
        float eqX = drawX + 10.0f;
        float eqCenterY = drawY + curH * 0.5f;
        double time = System.currentTimeMillis() / 140.0;
        for (int i = 0; i < 4; i++) {
            float wave = (float) ((Math.sin(time + i * 1.15) + 1.0) * 0.5);
            float barH = 4.0f + wave * (expanded ? 10.0f : 7.0f);
            int barCol = ColorUtils.interpolateColor(c1, c2, i / 3.0f);
            Render2DHelper.drawRoundedRect(context, eqX + i * 3.2f, eqCenterY - barH * 0.5f, 2.0f, barH, 1.0f, barCol);
        }

        if (expanded) {
            boolean state = latestAlert.enabled;
            int badgeCol = state ? 0xFF22C55E : 0xFFEF4444;
            float dotX = drawX + 32.0f;
            Render2DHelper.drawCircle(context, dotX, eqCenterY, 5.5f, ColorUtils.withAlpha(badgeCol, 65));
            Render2DHelper.drawCircle(context, dotX, eqCenterY, 3.0f, badgeCol);

            String title = state ? "Module Enabled" : "Module Disabled";
            String desc = latestAlert.moduleName + (state ? " [ON]" : " [OFF]");
            Render2DHelper.drawScaledText(context, title, drawX + 43.0f, drawY + 5.5f, badgeCol, 0.72f, true);
            Render2DHelper.drawScaledText(context, desc, drawX + 43.0f, drawY + 15.0f, 0xFFFFFFFF, 0.78f, true);

            // Progress bar along bottom of Dynamic Island
            float lifePct = Math.max(0.0f, Math.min(1.0f, 1.0f - (latestAlert.getAgeSeconds() / 2.1f)));
            float progW = (curW - 36.0f) * lifePct;
            Render2DHelper.drawGradientRoundedRect(context, drawX + 18.0f, drawY + curH - 3.2f, progW, 2.0f, 1.0f, badgeCol, c2);
        } else {
            String timeStr = cachedTimeString;
            String telemetry = mc.getFps() + "fps • " + getPlayerPing() + "ms";

            Render2DHelper.drawScaledText(context, timeStr, drawX + 27.0f, drawY + 7.0f, 0xFFFFFFFF, 0.76f, true);
            float divX = drawX + 27.0f + Render2DHelper.getTextWidth(timeStr, 0.76f) + 6.0f;
            drawCapsuleDivider(context, divX, drawY + 5.5f, curH - 11.0f, c1);
            Render2DHelper.drawScaledText(context, telemetry, divX + 6.0f, drawY + 7.0f, theme.getMutedTextColor(), 0.74f, false);

            // Pulsing live dot on right
            Render2DHelper.drawCircle(context, drawX + curW - 10.0f, eqCenterY, 2.5f, 0xFF22C55E);
        }
    }

    // =========================================================================
    // 3. ARRAYLIST (Sorted by Width, 2-Pass Batched Rendering: Geometry -> Text)
    // =========================================================================
    private void renderArrayList(GuiGraphicsExtractor context) {
        float scale = 0.82f;
        float x = arrayListWidget.getX();
        float y = arrayListWidget.getY();
        float widgetW = arrayListWidget.getWidth();

        if (cachedVisibleModules.isEmpty()) {
            arrayListWidget.setBounds(90.0f, 20.0f);
            return;
        }

        float curY = y;
        float maxW = 86.0f;
        float rowH = 14.5f;
        arrayListRowCount = 0;

        for (int i = 0; i < cachedVisibleModules.size(); i++) {
            Module m = cachedVisibleModules.get(i);
            float anim = Math.max(0.0f, Math.min(1.0f, m.getToggleAnimation().getValue()));
            if (anim <= 0.02f) continue;

            String name = m.getName();
            String suffix = m.getDisplaySuffix();
            boolean hasSuffix = suffix != null && !suffix.isEmpty();

            float nameW = Render2DHelper.getTextWidth(name, scale);
            float suffixW = hasSuffix ? Render2DHelper.getTextWidth(" " + suffix, scale) : 0.0f;
            float pillW = nameW + suffixW + 13.0f;
            if (pillW > maxW) maxW = pillW;

            float alignedRightX = x + widgetW;
            float slideOffset = (1.0f - anim) * (pillW + 10.0f);
            float pillX = alignedRightX - pillW + slideOffset;

            int waveColor = ThemeManager.getInstance().getAccentColor(i * 18);
            int alpha255 = (int) (255 * anim);

            ArrayListRowHolder row = getArrayListRowHolder(arrayListRowCount++);
            row.pillX = pillX;
            row.pillY = curY;
            row.pillW = pillW;
            row.rowH = rowH;
            row.waveColor = waveColor;
            row.alpha255 = alpha255;
            row.anim = anim;
            row.name = name;
            row.suffix = hasSuffix ? suffix : null;
            row.nameW = nameW;

            curY += rowH * anim;
        }

        if (arrayListRowCount == 0) {
            arrayListWidget.setBounds(90.0f, 20.0f);
            return;
        }

        float totalH = curY - y;
        arrayListWidget.setBounds(maxW, Math.max(20.0f, totalH));

        // Single ambient glow behind the right edge of the entire ArrayList block
        float rightEdge = x + widgetW;
        Render2DHelper.drawGlow(context, rightEdge - 5.0f, y, 5.0f, totalH, 6.0f,
                ColorUtils.withAlpha(ThemeManager.getInstance().getCurrentTheme().getPrimaryColor(), 45));

        // Pass 1: Geometry / Fills only (solid quad pipeline)
        for (int i = 0; i < arrayListRowCount; i++) {
            ArrayListRowHolder row = arrayListRowHolders.get(i);

            // Frosted dark glass row background
            Render2DHelper.drawRoundedRect(context, row.pillX, row.pillY, row.pillW, row.rowH - 1.0f, 3.0f,
                    ColorUtils.withAlpha(0xFF0D0F18, (int) (205 * row.anim)));

            // Right-side accent bar
            float barX = row.pillX + row.pillW - 2.2f;
            Render2DHelper.drawRoundedRect(context, barX, row.pillY, 2.2f, row.rowH - 1.0f, 1.0f,
                    ColorUtils.withAlpha(row.waveColor, row.alpha255));
        }

        // Pass 2: Text only (font pipeline)
        for (int i = 0; i < arrayListRowCount; i++) {
            ArrayListRowHolder row = arrayListRowHolders.get(i);

            // Module name (wave gradient)
            Render2DHelper.drawScaledText(context, row.name, row.pillX + 5.0f, row.pillY + 3.2f,
                    ColorUtils.withAlpha(row.waveColor, row.alpha255), scale, true);

            // Gray suffix
            if (row.suffix != null) {
                Render2DHelper.drawScaledText(context, " " + row.suffix, row.pillX + 5.0f + row.nameW, row.pillY + 3.2f,
                        ColorUtils.withAlpha(0xFF9CA3AF, row.alpha255), scale, true);
            }
        }
    }

    // =========================================================================
    // 4. KEYBINDS (Floating Glass Card with Gradient Header Line & Key Capsules)
    // =========================================================================
    private void renderKeyBinds(GuiGraphicsExtractor context) {
        Theme theme = ThemeManager.getInstance().getCurrentTheme();
        int c1 = theme.getPrimaryColor();
        int c2 = theme.getSecondaryColor();

        float w = 132.0f;
        float headerH = 19.0f;
        float rowH = 14.5f;

        int rowCount = Math.max(1, cachedBoundModules.size());
        float h = headerH + rowCount * rowH + 5.0f;

        keyBindsWidget.setBounds(w, h);
        float x = keyBindsWidget.getX();
        float y = keyBindsWidget.getY();

        Render2DHelper.drawLiquidGlass(context, x, y, w, h, 6.0f, 220, 38);

        // Header + Active count badge
        Render2DHelper.drawScaledText(context, "KeyBinds", x + 7.0f, y + 5.5f, 0xFFFFFFFF, 0.80f, true);
        String countBadge = String.valueOf(cachedBoundModules.size());
        float cbW = Render2DHelper.getTextWidth(countBadge, 0.68f) + 6.0f;
        Render2DHelper.drawRoundedRect(context, x + w - cbW - 6.0f, y + 4.5f, cbW, 10.0f, 3.0f, ColorUtils.withAlpha(c1, 60));
        Render2DHelper.drawScaledText(context, countBadge, x + w - cbW - 3.0f, y + 6.2f, c2, 0.68f, false);

        // Gradient line under header
        Render2DHelper.drawGradientRoundedRect(context, x + 6.0f, y + headerH - 1.5f, w - 12.0f, 1.5f, 0.8f, c1, c2);

        float curY = y + headerH + 2.5f;
        if (cachedBoundModules.isEmpty()) {
            // Show ClickGUI default bind so window looks informative
            Render2DHelper.drawScaledText(context, "ClickGUI", x + 7.0f, curY + 2.5f, theme.getMutedTextColor(), 0.74f, false);
            drawKeyCapsule(context, "[RSHIFT]", x + w - 6.0f, curY + 1.0f, c1, true);
            return;
        }

        for (Module m : cachedBoundModules) {
            String mName = m.getName();
            if (mName.length() > 14) mName = mName.substring(0, 13) + "..";
            int nameCol = m.isEnabled() ? 0xFFFFFFFF : theme.getMutedTextColor();
            Render2DHelper.drawScaledText(context, mName, x + 7.0f, curY + 2.5f, nameCol, 0.75f, m.isEnabled());

            String keyName = "[" + formatKeyName(m.getKeyBind()) + "]";
            drawKeyCapsule(context, keyName, x + w - 6.0f, curY + 1.0f, m.isEnabled() ? c1 : 0xFF6B7280, m.isEnabled());
            curY += rowH;
        }
    }

    private void drawKeyCapsule(GuiGraphicsExtractor context, String keyLabel, float rightX, float topY, int accentColor, boolean active) {
        float scale = 0.68f;
        float tw = Render2DHelper.getTextWidth(keyLabel, scale);
        float kw = tw + 6.0f;
        float kh = 11.0f;
        float kx = rightX - kw;

        Render2DHelper.drawRoundedRect(context, kx, topY, kw, kh, 2.8f,
                ColorUtils.withAlpha(accentColor, active ? 65 : 30));
        Render2DHelper.drawRoundedOutline(context, kx, topY, kw, kh, 2.8f, 1.0f,
                ColorUtils.withAlpha(accentColor, active ? 180 : 90));
        Render2DHelper.drawScaledText(context, keyLabel, kx + 3.0f, topY + 2.5f,
                active ? 0xFFFFFFFF : 0xFF9CA3AF, scale, false);
    }

    private String formatKeyName(int keyCode) {
        if (keyCode <= 0) return "NONE";
        if (keyCode == InputConstants.KEY_RSHIFT) return "RSHIFT";
        if (keyCode == InputConstants.KEY_LSHIFT) return "LSHIFT";
        if (keyCode == InputConstants.KEY_LCONTROL) return "LCTRL";
        if (keyCode == InputConstants.KEY_LALT) return "LALT";
        try {
            String glfwName; try { glfwName = com.mojang.blaze3d.platform.InputConstants.Type.KEYBOARD.getOrCreate(keyCode).getDisplayName().getString(); } catch (Throwable t) { glfwName = null; }
            if (glfwName != null && !glfwName.isBlank()) {
                return glfwName.toUpperCase(Locale.ROOT);
            }
            return InputConstants.Type.KEYBOARD.getOrCreate(keyCode).getDisplayName().getString().toUpperCase(Locale.ROOT);
        } catch (Throwable ignored) {
            return "K" + keyCode;
        }
    }

    // =========================================================================
    // 5. COOLDOWNS (Real 16x16 Item Icons + Timer Progress Bars)
    // =========================================================================
    private void renderCooldowns(GuiGraphicsExtractor context, float tickDelta) {
        Theme theme = ThemeManager.getInstance().getCurrentTheme();
        int c1 = theme.getPrimaryColor();
        int c2 = theme.getSecondaryColor();

        ensureStaticItemStacks();
        boolean inChat = mc.gui.screen() instanceof ChatScreen;
        List<CooldownRenderRow> fallbackRows = inChat ? chatPreviewCooldownRows : previewCooldownRows;
        if (fallbackRows == null) fallbackRows = List.of();
        List<CooldownRenderRow> rows = cachedCooldownRows.isEmpty()
                ? fallbackRows
                : cachedCooldownRows;

        float w = 152.0f;
        float headerH = 19.0f;
        float rowH = 20.0f;
        float h = headerH + rows.size() * rowH + 5.0f;

        cooldownsWidget.setBounds(w, h);
        float x = cooldownsWidget.getX();
        float y = cooldownsWidget.getY();

        Render2DHelper.drawLiquidGlass(context, x, y, w, h, 6.0f, 220, 38);
        Render2DHelper.drawScaledText(context, "Cooldowns", x + 7.0f, y + 5.5f, 0xFFFFFFFF, 0.80f, true);
        Render2DHelper.drawGradientRoundedRect(context, x + 6.0f, y + headerH - 1.5f, w - 12.0f, 1.5f, 0.8f, c1, c2);

        float curY = y + headerH + 2.5f;
        for (CooldownRenderRow row : rows) {
            // Render real 16x16 item icon at exact 1.0x scale (pixel-perfect)
            if (row.stack() != null && !row.stack().isEmpty()) {
                context.item(row.stack(), (int) Math.round(x + 6.0f), (int) Math.round(curY + 1.0f));
            }

            Render2DHelper.drawScaledText(context, row.label(), x + 25.0f, curY + 2.0f, theme.getTextColor(), 0.72f, false);

            float tw = Render2DHelper.getTextWidth(row.timeText(), 0.66f);
            int timeCol = row.progress() > 0.01f ? c2 : 0xFF4ADE80;
            Render2DHelper.drawScaledText(context, row.timeText(), x + w - tw - 6.0f, curY + 2.0f, timeCol, 0.66f, false);

            // Progress bar underneath item label
            float barX = x + 25.0f;
            float barY = curY + 13.0f;
            float barW = w - 31.0f;
            Render2DHelper.drawRoundedRect(context, barX, barY, barW, 3.0f, 1.5f, 0x30FFFFFF);
            if (row.progress() > 0.01f) {
                float fillW = Math.max(3.0f, barW * row.progress());
                Render2DHelper.drawGradientRoundedRect(context, barX, barY, fillW, 3.0f, 1.5f, c1, c2);
            }

            curY += rowH;
        }
    }

    // =========================================================================
    // 6. STAFFLIST (Detects Moderators/Admins/Spectators with [SPEC]/[NEAR]/[ONLINE])
    // =========================================================================
    private void renderStaffList(GuiGraphicsExtractor context) {
        Theme theme = ThemeManager.getInstance().getCurrentTheme();
        int c1 = theme.getPrimaryColor();
        int c2 = theme.getSecondaryColor();

        float w = 138.0f;
        float headerH = 19.0f;
        float rowH = 14.5f;
        int rows = Math.max(1, cachedStaffList.size());
        float h = headerH + rows * rowH + 5.0f;

        staffListWidget.setBounds(w, h);
        float x = staffListWidget.getX();
        float y = staffListWidget.getY();

        Render2DHelper.drawLiquidGlass(context, x, y, w, h, 6.0f, 220, 38);
        Render2DHelper.drawScaledText(context, "StaffList (" + cachedStaffList.size() + ")", x + 7.0f, y + 5.5f, 0xFFFFFFFF, 0.80f, true);
        Render2DHelper.drawGradientRoundedRect(context, x + 6.0f, y + headerH - 1.5f, w - 12.0f, 1.5f, 0.8f, c1, c2);

        float curY = y + headerH + 2.5f;
        if (cachedStaffList.isEmpty()) {
            Render2DHelper.drawCircle(context, x + 10.0f, curY + 6.0f, 2.2f, 0xFF4ADE80);
            Render2DHelper.drawScaledText(context, "No staff detected", x + 16.0f, curY + 2.5f, theme.getMutedTextColor(), 0.72f, false);
            return;
        }

        for (StaffEntry staff : cachedStaffList) {
            String sName = staff.name();
            if (sName.length() > 12) sName = sName.substring(0, 11) + "..";
            Render2DHelper.drawCircle(context, x + 10.0f, curY + 6.0f, 2.5f, staff.statusColor());
            Render2DHelper.drawScaledText(context, sName, x + 16.0f, curY + 2.5f, 0xFFFFFFFF, 0.74f, false);

            float tagW = Render2DHelper.getTextWidth(staff.statusTag(), 0.66f);
            Render2DHelper.drawScaledText(context, staff.statusTag(), x + w - tagW - 6.0f, curY + 2.5f, staff.statusColor(), 0.66f, true);
            curY += rowH;
        }
    }

    // =========================================================================
    // 7. 2D RADAR (Liquid Glass Radar with Rotating FOV Sweep Cone & Player/Friend Blips)
    // =========================================================================
    private void renderRadar(GuiGraphicsExtractor context, float tickDelta) {
        Theme theme = ThemeManager.getInstance().getCurrentTheme();
        int c1 = theme.getPrimaryColor();
        int c2 = theme.getSecondaryColor();

        float size = 86.0f;
        radarWidget.setBounds(size, size);
        float x = radarWidget.getX();
        float y = radarWidget.getY();
        float cx = x + size * 0.5f;
        float cy = y + size * 0.5f;
        float radius = size * 0.5f - 6.0f;

        Render2DHelper.drawLiquidGlass(context, x, y, size, size, 8.0f, 222, 42);

        // Concentric radar rings
        Render2DHelper.drawRoundedOutline(context, cx - radius, cy - radius, radius * 2.0f, radius * 2.0f, radius, 1.0f, 0x26FFFFFF);
        Render2DHelper.drawRoundedOutline(context, cx - radius * 0.5f, cy - radius * 0.5f, radius, radius, radius * 0.5f, 1.0f, 0x1AFFFFFF);

        // Crosshair axes
        Render2DHelper.drawRect(context, cx - radius, cy, radius * 2.0f, 1.0f, 0x20FFFFFF);
        Render2DHelper.drawRect(context, cx, cy - radius, 1.0f, radius * 2.0f, 0x20FFFFFF);

        // Forward FOV View Cone + Animated Radar Sweep Line
        for (int step = 1; step <= 6; step++) {
            float t = step / 6.0f;
            float r = radius * 0.85f * t;
            float spread = r * 0.58f;
            int coneAlpha = (int) ((1.0f - t) * 42.0f);
            Render2DHelper.drawRect(context, cx - spread, cy - r, spread * 2.0f, 2.0f, ColorUtils.withAlpha(c1, coneAlpha));
        }

        // Rotating sweep ray
        double sweepAngle = (System.currentTimeMillis() % 2800L) / 2800.0 * Math.PI * 2.0;
        for (int rStep = 3; rStep < (int) radius; rStep += 3) {
            float sx = cx + (float) Math.cos(sweepAngle) * rStep;
            float sy = cy + (float) Math.sin(sweepAngle) * rStep;
            Render2DHelper.drawRect(context, sx, sy, 1.5f, 1.5f, ColorUtils.withAlpha(c2, 135));
        }

        // Player blips (Enemies = Red/Magenta, Friends = Emerald Green)
        if (!cachedRadarPoints.isEmpty() && mc.player != null) {
            float yaw = mc.player.getYRot(tickDelta);
            double rad = Math.toRadians(yaw + 180.0);
            double cos = Math.cos(rad);
            double sin = Math.sin(rad);
            float maxRange = 55.0f;

            for (RadarPoint pt : cachedRadarPoints) {
                double rx = pt.dx() * cos - pt.dz() * sin;
                double ry = pt.dx() * sin + pt.dz() * cos;

                float px = cx + (float) (rx / maxRange) * (radius - 3.0f);
                float py = cy + (float) (ry / maxRange) * (radius - 3.0f);

                int dotCol = pt.isFriend() ? 0xFF00E676 : 0xFFFF2E54;
                Render2DHelper.drawCircle(context, px, py, 2.8f, ColorUtils.withAlpha(dotCol, 90));
                Render2DHelper.drawCircle(context, px, py, 1.8f, dotCol);
            }
        }

        // Center local player indicator
        Render2DHelper.drawCircle(context, cx, cy, 3.0f, c1);
        Render2DHelper.drawCircle(context, cx, cy, 1.5f, 0xFFFFFFFF);
        Render2DHelper.drawScaledText(context, "N", cx - 2.0f, y + 3.0f, c2, 0.65f, true);
    }

    // =========================================================================
    // 8. BPS SPEED GRAPH (Smooth Speed Curve with Optimized Segments)
    // =========================================================================
    private void renderBpsGraph(GuiGraphicsExtractor context) {
        Theme theme = ThemeManager.getInstance().getCurrentTheme();
        int c1 = theme.getPrimaryColor();
        int c2 = theme.getSecondaryColor();

        float w = 136.0f;
        float h = 48.0f;
        bpsGraphWidget.setBounds(w, h);
        float x = bpsGraphWidget.getX();
        float y = bpsGraphWidget.getY();

        Render2DHelper.drawLiquidGlass(context, x, y, w, h, 6.0f, 220, 38);

        Render2DHelper.drawTexture(context, TextureAssets.SPEED_ICON, x + 6.0f, y + 4.5f, 10.0f, 10.0f, c1);
        Render2DHelper.drawScaledText(context, "Speed", x + 19.0f, y + 5.5f, 0xFFFFFFFF, 0.76f, true);

        // Uses precomputed text width and formatted string from onTick() to eliminate per-frame allocations
        Render2DHelper.drawScaledText(context, cachedBpsGraphStr, x + w - cachedBpsGraphW - 6.0f, y + 5.5f, c2, 0.74f, true);

        float graphX = x + 6.0f;
        float graphY = y + 17.0f;
        float graphW = w - 12.0f;
        float graphH = h - 22.0f;
        float maxScale = Math.max(12.0f, (float) peakBps + 2.0f);

        Render2DHelper.drawRoundedRect(context, graphX, graphY, graphW, graphH, 3.0f, 0x4407090E);

        float stepW = graphW / (BPS_SAMPLES - 1);
        int step = 3;
        for (int i = 0; i < BPS_SAMPLES - 1; i += step) {
            int nextI = Math.min(BPS_SAMPLES - 1, i + step);
            int idx1 = (bpsWriteIndex + i) % BPS_SAMPLES;
            int idx2 = (bpsWriteIndex + nextI) % BPS_SAMPLES;

            float v1 = Math.min(maxScale, bpsHistory[idx1]);
            float v2 = Math.min(maxScale, bpsHistory[idx2]);

            float norm1 = v1 / maxScale;
            float norm2 = v2 / maxScale;

            float x1 = graphX + i * stepW;
            float spanW = (nextI - i) * stepW;
            float y1 = graphY + graphH - norm1 * (graphH - 3.0f) - 1.0f;
            float y2 = graphY + graphH - norm2 * (graphH - 3.0f) - 1.0f;

            int col = ColorUtils.interpolateColor(c1, c2, (float) i / (BPS_SAMPLES - 1));
            int fillCol = ColorUtils.withAlpha(col, 45);

            // Fast solid column fill under curve (eliminates per-frame fillGradient overhead)
            float topY = Math.min(y1, y2);
            Render2DHelper.drawRect(context, x1, topY, spanW + 0.6f, (graphY + graphH) - topY, fillCol);

            // Top curve crest line (fast solid fill, no glow calls)
            float segH = Math.max(1.4f, Math.abs(y2 - y1) + 1.0f);
            Render2DHelper.drawRect(context, x1, topY, spanW + 0.6f, segH, col);
        }
    }

    // =========================================================================
    // 9. TOTEM, GAPPLE & PEARL COUNTER (Real Item Icons + Count Badges)
    // =========================================================================
    private void renderTotemCounter(GuiGraphicsExtractor context) {
        Theme theme = ThemeManager.getInstance().getCurrentTheme();
        int c1 = theme.getPrimaryColor();

        int totems = cachedTotems;
        int gapples = cachedGapples;
        int pearls = cachedPearls;

        float w = 138.0f;
        float h = 26.0f;
        totemWidget.setBounds(w, h);
        float x = totemWidget.getX();
        float y = totemWidget.getY();

        Render2DHelper.drawLiquidGlass(context, x, y, w, h, 6.0f, 222, 40);

        ensureStaticItemStacks();
        float itemY = y + (h - 16.0f) * 0.5f;
        drawCounterSlot(context, totemStack, totems, x + 5.0f, itemY, totems > 0 ? 0xFFFBBF24 : 0xFF9CA3AF);
        drawCapsuleDivider(context, x + 47.0f, y + 5.0f, h - 10.0f, c1);
        drawCounterSlot(context, gappleStack, gapples, x + 51.0f, itemY, gapples > 0 ? 0xFF4ADE80 : 0xFF9CA3AF);
        drawCapsuleDivider(context, x + 92.0f, y + 5.0f, h - 10.0f, c1);
        drawCounterSlot(context, pearlStack, pearls, x + 96.0f, itemY, pearls > 0 ? 0xFF38BDF8 : 0xFF9CA3AF);
    }

    private void drawCounterSlot(GuiGraphicsExtractor context, ItemStack iconStack, int count, float sx, float sy, int textColor) {
        // Render vanilla 16x16 item icon at exact 1.0x scale (pixel-perfect)
        if (iconStack != null && !iconStack.isEmpty()) {
            context.item(iconStack, (int) Math.round(sx), (int) Math.round(sy));
        }
        Render2DHelper.drawScaledText(context, "x" + count, sx + 18.0f, sy + 4.5f, textColor, 0.76f, true);
    }

    private int countInventoryItem(Item item) {
        if (mc.player == null) return 0;
        int total = 0;
        for (ItemStack stack : mc.player.getInventory().getNonEquipmentItems()) {
            if (stack != null && !stack.isEmpty() && stack.is(item)) {
                total += stack.getCount();
            }
        }
        ItemStack offhand = mc.player.getOffhandItem();
        if (offhand != null && !offhand.isEmpty() && offhand.is(item)) {
            total += offhand.getCount();
        }
        return total;
    }

    // =========================================================================
    // 10. COORDINATES & DIRECTION CAPSULE
    // =========================================================================
    private void renderCoordinates(GuiGraphicsExtractor context) {
        Theme theme = ThemeManager.getInstance().getCurrentTheme();
        int c1 = theme.getPrimaryColor();
        int c2 = theme.getSecondaryColor();

        BlockPos pos = mc.player.blockPosition();
        String facing = mc.player.getDirection().getSerializedName().toUpperCase(Locale.ROOT);
        String dirShort = facing.isEmpty() ? "N" : facing.substring(0, 1);
        String text = "XYZ: " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + "  [" + dirShort + "]";

        float scale = 0.76f;
        float tw = Render2DHelper.getTextWidth(text, scale);
        float w = tw + 26.0f;
        float h = 20.0f;

        coordsWidget.setBounds(w, h);
        float x = coordsWidget.getX();
        float y = coordsWidget.getY();

        Render2DHelper.drawLiquidGlass(context, x, y, w, h, 5.5f, 218, 36);
        Render2DHelper.drawTexture(context, TextureAssets.COORDS_ICON, x + 6.0f, y + 4.5f, 10.5f, 10.5f, c2);
        Render2DHelper.drawScaledText(context, text, x + 20.0f, y + 6.2f, ColorUtils.interpolateColor(0xFFFFFFFF, c1, 0.2f), scale, true);
    }

    // =========================================================================
    // 11. INVENTORY HUD (27-Slot Main Inventory Preview)
    // =========================================================================
    private void renderInventoryHud(GuiGraphicsExtractor context) {
        float slotSize = 17.0f;
        float w = 9 * slotSize + 10.0f;
        float h = 3 * slotSize + 22.0f;

        inventoryWidget.setBounds(w, h);
        float x = inventoryWidget.getX();
        float y = inventoryWidget.getY();

        Theme theme = ThemeManager.getInstance().getCurrentTheme();
        Render2DHelper.drawLiquidGlass(context, x, y, w, h, 6.0f, 220, 38);
        Render2DHelper.drawScaledText(context, "Inventory", x + 7.0f, y + 5.0f, 0xFFFFFFFF, 0.78f, true);
        Render2DHelper.drawGradientRoundedRect(context, x + 6.0f, y + 16.0f, w - 12.0f, 1.2f, 0.6f,
                theme.getPrimaryColor(), theme.getSecondaryColor());

        List<ItemStack> mainStacks = mc.player.getInventory().getNonEquipmentItems();
        float startX = x + 5.0f;
        float startY = y + 19.0f;

        Matrix3x2fStack matrices = context.pose();
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int slotIdx = 9 + row * 9 + col;
                float sx = startX + col * slotSize;
                float sy = startY + row * slotSize;

                Render2DHelper.drawRoundedRect(context, sx, sy, slotSize - 1.5f, slotSize - 1.5f, 2.5f, 0x45090B12);
                if (slotIdx < mainStacks.size()) {
                    ItemStack stack = mainStacks.get(slotIdx);
                    if (stack != null && !stack.isEmpty()) {
                        matrices.pushMatrix();
                        matrices.translate(sx + 0.8f, sy + 0.8f);
                        matrices.scale(0.86f, 0.86f);
                        context.item(stack, 0, 0);
                        context.itemDecorations(mc.font, stack, 0, 0);
                        matrices.popMatrix();
                    }
                }
            }
        }
    }

    // =========================================================================
    // 12. NOTIFICATIONS TOAST WIDGET
    // =========================================================================
    private void renderNotifications(GuiGraphicsExtractor context) {
        boolean inChat = mc.gui.screen() instanceof ChatScreen;
        if (activeAlerts.isEmpty() && !inChat) {
            return;
        }

        float screenW = mc.getWindow() != null ? (float) mc.getWindow().getGuiScaledWidth() : 960.0f;
        float screenH = mc.getWindow() != null ? (float) mc.getWindow().getGuiScaledHeight() : 540.0f;

        float w = 156.0f;
        float cardH = 26.0f;

        float startX = Math.max(4.0f, Math.min(screenW - w - 4.0f, notificationsWidget.getX()));
        float startY = Math.max(4.0f, Math.min(screenH - 30.0f - 4.0f, notificationsWidget.getY()));

        int count = Math.max(1, activeAlerts.size());
        notificationsWidget.setBounds(w, count * (cardH + 4.0f));

        if (activeAlerts.isEmpty() && inChat) {
            Render2DHelper.drawLiquidGlass(context, startX, startY, w, cardH, 5.5f, 210, 35);
            Render2DHelper.drawCircle(context, startX + 11.0f, startY + cardH * 0.5f, 3.5f, 0xFF22C55E);
            Render2DHelper.drawScaledText(context, "Notifications Preview", startX + 20.0f, startY + 5.5f, 0xFFFFFFFF, 0.74f, true);
            Render2DHelper.drawScaledText(context, "Module toggle alerts", startX + 20.0f, startY + 14.5f, 0xFF9CA3AF, 0.66f, false);
            return;
        }

        float curY = startY;
        for (ToggleAlert alert : activeAlerts) {
            float anim = Math.max(0.0f, Math.min(1.0f, alert.slideAnim.getValue()));
            if (anim <= 0.001f) continue;

            float offsetX = (1.0f - anim) * (w + 24.0f);
            float cardX = startX + offsetX;
            float curSlotH = (cardH + 4.0f) * anim;

            int col = alert.enabled ? 0xFF22C55E : 0xFFEF4444;
            int bgAlpha = (int) (220 * anim);
            int borderAlpha = (int) (40 * anim);
            int textAlpha = (int) (255 * anim);
            int textColWhite = (textAlpha << 24) | 0x00FFFFFF;
            int textColStatus = (textAlpha << 24) | (col & 0x00FFFFFF);

            Render2DHelper.drawLiquidGlass(context, cardX, curY, w, cardH, 5.5f, bgAlpha, borderAlpha);
            Render2DHelper.drawActiveCardIndicator(context, cardX + 2.0f, curY + 2.0f, cardH - 4.0f, anim, col, col);

            String title = alert.moduleName;
            String sub = alert.enabled ? "Enabled [ON]" : "Disabled [OFF]";
            Render2DHelper.drawScaledText(context, title, cardX + 9.0f, curY + 5.0f, textColWhite, 0.76f, true);
            Render2DHelper.drawScaledText(context, sub, cardX + 9.0f, curY + 14.5f, textColStatus, 0.68f, false);

            curY += curSlotH;
        }
    }
}
