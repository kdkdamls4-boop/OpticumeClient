package dev.opticumeclient.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.opticumeclient.client.hud.HudAlertBus;
import dev.opticumeclient.client.hud.HudManager;
import dev.opticumeclient.client.modules.Category;
import dev.opticumeclient.client.modules.Module;
import dev.opticumeclient.client.modules.ModuleManager;
import dev.opticumeclient.client.modules.combat.OpticumeAuraModule;
import dev.opticumeclient.client.render.ThemeManager;
import dev.opticumeclient.client.setting.BindSetting;
import dev.opticumeclient.client.setting.BooleanSetting;
import dev.opticumeclient.client.setting.ColorSetting;
import dev.opticumeclient.client.setting.ModeSetting;
import dev.opticumeclient.client.setting.MultiBoxSetting;
import dev.opticumeclient.client.setting.NumberSetting;
import dev.opticumeclient.client.setting.Setting;
import dev.opticumeclient.client.setting.TextSetting;
import dev.opticumeclient.client.modules.visuals.ToggleSoundsModule;
import dev.opticumeclient.client.util.ChatUtils;
import net.minecraft.client.Minecraft;

import java.io.File;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Full Configuration & Auto-Load Manager for OpticumeClient (Minecraft 26.3).
 * Saves and loads all modules, 5 setting types, HUD widget coordinates, and themes
 * inside <gameDir>/opticumeclient/configs/<name>.json with startup auto-load via <gameDir>/opticumeclient/autoload.txt.
 */
public class ConfigManager {
    private static final ConfigManager INSTANCE = new ConfigManager();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final List<String> BUILTIN_ORDER = Arrays.asList("Default", "ReallyWorld", "Dexland", "FunTime", "HolyWorld", "MaxFPS");
    private static final String CONFIG_FORMAT_VERSION = "1.5.5-26.3";
    private static final List<String> SUPPORTED_LEGACY_VERSIONS = Arrays.asList(
            "1.0.0-26.3", "1.1.0-26.3", "1.2.0-26.3", "1.2.1-26.3",
            "1.3.0-26.3", "1.4.0-26.3", "1.5.0-26.3", "1.5.1-26.3", "1.5.2-26.3", "1.5.3-26.3", "1.5.4-26.3",
            "1.0.0", "1.1.0"
    );

    private File rootDir;
    private File configsDir;
    private File autoLoadFile;
    private String activeConfigName = "Default";
    private String autoLoadConfigName = "Default";
    private boolean initialized = false;
    private boolean activeConfigWritable = false;

    private ConfigManager() {}

    public static ConfigManager getInstance() {
        return INSTANCE;
    }

    private void ensureDirectories() {
        if (rootDir != null && configsDir != null) return;
        Minecraft mc = Minecraft.getInstance();
        File gameDir = (mc != null && mc.gameDirectory != null) ? mc.gameDirectory : new File(".");
        rootDir = new File(gameDir, "opticumeclient");
        boolean needsLegacyMigration = !rootDir.exists();
        configsDir = new File(rootDir, "configs");
        autoLoadFile = new File(rootDir, "autoload.txt");
        if (!configsDir.exists()) {
            configsDir.mkdirs();
        }
        if (needsLegacyMigration) {
            migrateLegacyDirectory(new File(gameDir, "opticume"));
        }
    }

    private void migrateLegacyDirectory(File legacyRoot) {
        if (legacyRoot == null || !legacyRoot.isDirectory()) return;
        try {
            File legacyAutoLoad = new File(legacyRoot, "autoload.txt");
            if (legacyAutoLoad.exists() && !autoLoadFile.exists()) {
                Files.copy(legacyAutoLoad.toPath(), autoLoadFile.toPath());
            }
            File legacyConfigs = new File(legacyRoot, "configs");
            File[] files = legacyConfigs.listFiles((dir, fname) -> fname.toLowerCase().endsWith(".json"));
            if (files != null) {
                for (File f : files) {
                    String base = f.getName().substring(0, f.getName().length() - 5);
                    // Regenerate legacy broken combat presets cleanly; migrate Default, MaxFPS, and all custom configs
                    if ("Dexland".equalsIgnoreCase(base) || "FunTime".equalsIgnoreCase(base) || "HolyWorld".equalsIgnoreCase(base)) {
                        continue;
                    }
                    File target = new File(configsDir, f.getName());
                    if (!target.exists()) {
                        Files.copy(f.toPath(), target.toPath());
                    }
                }
            }
        } catch (Throwable ignored) {
        }
    }

    public void init() {
        if (initialized) return;
        initialized = true;
        try {
            ensureDirectories();

            // Capture clean initial state so preset generation doesn't pollute defaults
            JsonObject baseline = serializeCurrentState();

            // Create Default preset if missing
            File defaultFile = getConfigFile("Default");
            if (!defaultFile.exists()) {
                writeJsonFile(defaultFile, baseline);
            }

            // Generate server & FPS presets if missing
            ensureBuiltInPreset("ReallyWorld", baseline, () -> {
                ThemeManager.getInstance().setThemeByName("Rockstar Crimson");
                configureServerPreset("ReallyWorld", true);
            });
            ensureBuiltInPreset("Dexland", baseline, () -> {
                ThemeManager.getInstance().setThemeByName("Nursultan Alpha");
                configureServerPreset("Dexland", true);
            });
            ensureBuiltInPreset("FunTime", baseline, () -> {
                ThemeManager.getInstance().setThemeByName("Catlavan Acid");
                configureServerPreset("FunTime", true);
            });
            ensureBuiltInPreset("HolyWorld", baseline, () -> {
                ThemeManager.getInstance().setThemeByName("Expensive Purple");
                configureServerPreset("HolyWorld", true);
            });
            ensureBuiltInPreset("MaxFPS", baseline, () -> {
                ThemeManager.getInstance().setThemeByName("Vape Ghost");
                configureMaxFpsPreset();
            });

            // Upgrade outdated built-in preset files on disk cleanly (without overwriting user custom configs)
            for (String preset : BUILTIN_ORDER) {
                if ("Default".equalsIgnoreCase(preset)) continue;
                upgradeOutdatedBuiltInPreset(preset, baseline);
            }

            // Restore baseline before applying startup auto-load config
            deserializeState(baseline);

            // Read autoload.txt (or create with "Default")
            if (autoLoadFile.exists()) {
                String savedAuto = Files.readString(autoLoadFile.toPath(), StandardCharsets.UTF_8).trim();
                if (!savedAuto.isEmpty()) {
                    autoLoadConfigName = sanitizeName(savedAuto);
                }
            } else {
                setAutoLoadConfig("Default");
            }

            boolean loaded = false;
            if (getConfigFile(autoLoadConfigName).exists()) {
                loaded = loadConfig(autoLoadConfigName);
            }
            if (!loaded && !"Default".equalsIgnoreCase(autoLoadConfigName) && getConfigFile("Default").exists()) {
                loaded = loadConfig("Default");
            }
            if (!loaded) {
                activeConfigWritable = false;
            }
            System.out.println("[OpticumeClient] ConfigManager initialized. Auto-loaded config: " + activeConfigName);
        } catch (Throwable t) {
            System.err.println("[OpticumeClient] ConfigManager init warning: " + t.getMessage());
        }
    }

    private void ensureBuiltInPreset(String presetName, JsonObject baseline, Runnable mutator) {
        File f = getConfigFile(presetName);
        if (f.exists()) return;
        deserializeState(baseline);
        mutator.run();
        writeJsonFile(f, serializeCurrentState());
    }

    private void upgradeOutdatedBuiltInPreset(String presetName, JsonObject baseline) {
        File f = getConfigFile(presetName);
        if (!f.exists()) return;
        try (Reader reader = Files.newBufferedReader(f.toPath(), StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            String ver = (root != null && root.has("version") && root.get("version").isJsonPrimitive())
                    ? root.get("version").getAsString().trim() : "";
            if (!CONFIG_FORMAT_VERSION.equals(ver) && SUPPORTED_LEGACY_VERSIONS.contains(ver)) {
                deserializeState(baseline);
                if (deserializeState(root)) {
                    writeJsonFile(f, serializeCurrentState());
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private void configureServerPreset(String auraPresetName, boolean enableCombat) {
        ModuleManager mm = ModuleManager.getInstance();
        for (Module m : mm.getModules()) {
            if (m.getCategory() == Category.OPTIMIZATION) {
                m.setEnabled(true);
            }
        }
        Module sprint = findModule(mm, "Sprint");
        if (sprint != null) {
            sprint.setEnabled(true);
            setBoolSetting(sprint, "StopOnEat", true);
        }
        Module noSlow = findModule(mm, "NoSlow");
        if (noSlow != null) {
            noSlow.setEnabled(true);
            setModeSetting(noSlow, "Mode", "Grim Safe");
        }
        Module aura = findModule(mm, "OpticumeAura");
        if (aura instanceof OpticumeAuraModule opticumeAura) {
            opticumeAura.setEnabled(false); // Keep combat keybound/ready rather than swinging immediately on spawn
            setModeSetting(opticumeAura, "Preset", auraPresetName);
            opticumeAura.applyPreset(auraPresetName);
        }
    }

    private void configureMaxFpsPreset() {
        ModuleManager mm = ModuleManager.getInstance();
        for (Module m : mm.getModules()) {
            if (m.getCategory() == Category.OPTIMIZATION) {
                m.setEnabled(true);
            } else if (m.getCategory() == Category.VISUALS) {
                // Keep only ultra-lightweight visuals enabled for maximum 350+ FPS
                boolean light = m.getName().equalsIgnoreCase("Fullbright")
                        || m.getName().equalsIgnoreCase("Crosshair")
                        || m.getName().equalsIgnoreCase("TargetHUD")
                        || m.getName().equalsIgnoreCase("ViewModel")
                        || m.getName().equalsIgnoreCase("SwingAnimations");
                m.setEnabled(light);
            }
        }
    }

    private Module findModule(ModuleManager mm, String name) {
        for (Module m : mm.getModules()) {
            if (m.getName().equalsIgnoreCase(name)) return m;
        }
        return null;
    }

    private void setModeSetting(Module m, String settingName, String modeVal) {
        for (Setting<?> s : m.getSettings()) {
            if (s instanceof ModeSetting ms && s.getName().equalsIgnoreCase(settingName)) {
                ms.setMode(modeVal);
                return;
            }
        }
    }

    private void setBoolSetting(Module m, String settingName, boolean val) {
        for (Setting<?> s : m.getSettings()) {
            if (s instanceof BooleanSetting bs && s.getName().equalsIgnoreCase(settingName)) {
                bs.setValue(val);
                return;
            }
        }
    }

    private void setNumberSetting(Module m, String settingName, double val) {
        for (Setting<?> s : m.getSettings()) {
            if (s instanceof NumberSetting ns && s.getName().equalsIgnoreCase(settingName)) {
                ns.setValueClamped(val);
                return;
            }
        }
    }

    public String sanitizeName(String raw) {
        if (raw == null) return "Custom";
        String cleaned = raw.trim().replaceAll("[^a-zA-Z0-9_\\- ]", "");
        return cleaned.isEmpty() ? "Custom" : cleaned;
    }

    private File getConfigFile(String name) {
        ensureDirectories();
        return new File(configsDir, sanitizeName(name) + ".json");
    }

    public List<String> listConfigs() {
        ensureDirectories();
        List<String> result = new ArrayList<>();
        File[] files = configsDir.listFiles((dir, fname) -> fname.toLowerCase().endsWith(".json"));
        List<String> custom = new ArrayList<>();
        if (files != null) {
            for (File f : files) {
                String n = f.getName();
                String base = n.substring(0, n.length() - 5);
                boolean isBuiltin = false;
                for (String b : BUILTIN_ORDER) {
                    if (b.equalsIgnoreCase(base)) {
                        isBuiltin = true;
                        break;
                    }
                }
                if (!isBuiltin) {
                    custom.add(base);
                }
            }
        }
        for (String b : BUILTIN_ORDER) {
            if (getConfigFile(b).exists()) {
                result.add(b);
            }
        }
        Collections.sort(custom, String.CASE_INSENSITIVE_ORDER);
        result.addAll(custom);
        if (result.isEmpty()) {
            result.add("Default");
        }
        return result;
    }

    public String sanitizeConfigName(String name) {
        return sanitizeName(name);
    }

    public boolean saveConfig(String name) {
        String clean = sanitizeName(name);
        if (isBuiltInPreset(clean) && !"Default".equalsIgnoreCase(clean)) {
            String base = clean + " Custom";
            String candidate = base;
            int idx = 2;
            while (getConfigFile(candidate).exists()) {
                candidate = base + " " + idx++;
            }
            clean = candidate;
        }
        File file = getConfigFile(clean);
        boolean ok = writeJsonFile(file, serializeCurrentState());
        if (ok) {
            activeConfigName = clean;
            activeConfigWritable = !isBuiltInPreset(clean) || "Default".equalsIgnoreCase(clean);
        }
        return ok;
    }

    public boolean loadConfig(String name) {
        String clean = sanitizeName(name);
        File file = getConfigFile(clean);
        if (!file.exists()) return false;
        try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            boolean wasLegacy = root.has("version") && root.get("version").isJsonPrimitive()
                    && !CONFIG_FORMAT_VERSION.equals(root.get("version").getAsString().trim());
            if (!deserializeState(root)) {
                System.err.println("[OpticumeClient] Rejected config '" + clean + "': missing or unsupported format version");
                return false;
            }
            activeConfigName = clean;
            activeConfigWritable = !isBuiltInPreset(clean) || "Default".equalsIgnoreCase(clean);
            if (wasLegacy && (activeConfigWritable || isBuiltInPreset(clean))) {
                writeJsonFile(file, serializeCurrentState());
            }
            return true;
        } catch (Throwable t) {
            System.err.println("[OpticumeClient] Failed to load config '" + clean + "': " + t.getMessage());
            return false;
        }
    }

    public boolean deleteConfig(String name) {
        String clean = sanitizeName(name);
        if (isBuiltInPreset(clean)) {
            return false; // Protect built-in presets (Default, Dexland, FunTime, HolyWorld, MaxFPS)
        }
        File file = getConfigFile(clean);
        if (file.exists() && file.delete()) {
            if (activeConfigName.equalsIgnoreCase(clean)) {
                activeConfigName = "Default";
            }
            if (autoLoadConfigName.equalsIgnoreCase(clean)) {
                setAutoLoadConfig("Default");
            }
            return true;
        }
        return false;
    }

    public void setAutoLoadConfig(String name) {
        ensureDirectories();
        String clean = sanitizeName(name);
        this.autoLoadConfigName = clean;
        try {
            Files.writeString(autoLoadFile.toPath(), clean, StandardCharsets.UTF_8);
        } catch (Throwable ignored) {
        }
    }

    public String getAutoLoadConfigName() {
        return autoLoadConfigName;
    }

    public String getActiveConfigName() {
        return activeConfigName;
    }

    public void saveActiveConfigQuietly() {
        if (!initialized || !activeConfigWritable || activeConfigName == null || activeConfigName.isBlank()) {
            return;
        }
        String clean = sanitizeName(activeConfigName);
        if (isBuiltInPreset(clean) && !"Default".equalsIgnoreCase(clean)) {
            return;
        }
        try {
            String target = "Default".equalsIgnoreCase(clean) ? "Default" : clean;
            writeJsonFile(getConfigFile(target), serializeCurrentState());
        } catch (Throwable ignored) {
        }
    }

    public void saveHudChangeQuietly() {
        if (!initialized) return;
        if (activeConfigWritable) {
            saveActiveConfigQuietly();
            return;
        }
        String clean = sanitizeName(activeConfigName);
        if (isBuiltInPreset(clean) && !"Default".equalsIgnoreCase(clean)) {
            String writableName = clean + " Custom";
            if (saveConfig(writableName)) {
                if (isBuiltInPreset(autoLoadConfigName)) {
                    setAutoLoadConfig(writableName);
                }
                ChatUtils.sendClientMessage("§a[Config] §fПресет §e" + clean + " §fтолько для чтения — изменения HUD сохранены в §b" + writableName);
            }
        } else {
            ChatUtils.sendError("[Config] Текущий конфиг не был загружен (неподдерживаемая версия) — сохраните профиль под новым именем в Configs.");
        }
    }

    public boolean isBuiltInPreset(String name) {
        for (String b : BUILTIN_ORDER) {
            if (b.equalsIgnoreCase(name)) return true;
        }
        return false;
    }

    private boolean writeJsonFile(File file, JsonObject obj) {
        try {
            dev.opticumeclient.client.util.AtomicFileWriter.writeUtf8(file, writer -> GSON.toJson(obj, writer));
            return true;
        } catch (Throwable t) {
            System.err.println("[OpticumeClient] Failed to write config file: " + t.getMessage());
            return false;
        }
    }

    private JsonObject serializeCurrentState() {
        JsonObject root = new JsonObject();
        root.addProperty("client", "OpticumeClient");
        root.addProperty("version", CONFIG_FORMAT_VERSION);
        root.addProperty("theme", ThemeManager.getInstance().getCurrentTheme().getName());

        JsonObject modulesObj = new JsonObject();
        for (Module m : ModuleManager.getInstance().getModules()) {
            JsonObject mObj = new JsonObject();
            mObj.addProperty("enabled", m.isEnabled());
            mObj.addProperty("keyBind", m.getKeyBind());

            JsonObject settingsObj = new JsonObject();
            for (Setting<?> s : m.getSettings()) {
                if (s instanceof BooleanSetting bs) {
                    settingsObj.addProperty(s.getName(), bs.isEnabled());
                } else if (s instanceof NumberSetting ns) {
                    settingsObj.addProperty(s.getName(), ns.getValue());
                } else if (s instanceof ModeSetting ms) {
                    settingsObj.addProperty(s.getName(), ms.getMode());
                } else if (s instanceof MultiBoxSetting mbs) {
                    JsonArray arr = new JsonArray();
                    for (String opt : mbs.getOptions()) {
                        if (mbs.isEnabled(opt)) {
                            arr.add(opt);
                        }
                    }
                    settingsObj.add(s.getName(), arr);
                } else if (s instanceof ColorSetting cs) {
                    JsonObject cObj = new JsonObject();
                    cObj.addProperty("color", cs.getStaticValue());
                    cObj.addProperty("rainbow", cs.isRainbow());
                    cObj.addProperty("themeSync", cs.isThemeSync());
                    settingsObj.add(s.getName(), cObj);
                } else if (s instanceof TextSetting ts) {
                    settingsObj.addProperty(s.getName(), ts.getText());
                } else if (s instanceof BindSetting bindSetting) {
                    settingsObj.addProperty(s.getName(), bindSetting.getBindCode());
                }
            }
            mObj.add("settings", settingsObj);
            modulesObj.add(m.getName(), mObj);
        }
        root.add("modules", modulesObj);

        JsonObject hudObj = new JsonObject();
        for (HudManager.DraggableWidget w : HudManager.getInstance().getWidgets()) {
            JsonObject wObj = new JsonObject();
            wObj.addProperty("x", w.getX());
            wObj.addProperty("y", w.getY());
            wObj.addProperty("scale", w.getScale());
            hudObj.add(w.getName(), wObj);
        }
        root.add("hud", hudObj);
        return root;
    }

    private boolean deserializeState(JsonObject root) {
        if (root == null) return false;
        if (!root.has("version") || !root.get("version").isJsonPrimitive()) {
            return false;
        }
        String cfgVersion = root.get("version").getAsString().trim();
        boolean isCurrent = CONFIG_FORMAT_VERSION.equals(cfgVersion);
        boolean isSupportedLegacy = !isCurrent && SUPPORTED_LEGACY_VERSIONS.contains(cfgVersion);
        if (!isCurrent && !isSupportedLegacy) {
            return false;
        }

        ToggleSoundsModule.setSuppressSounds(true);
        HudAlertBus.setSuppressAlerts(true);
        try {
            boolean isPre120 = isSupportedLegacy && (
                    "1.0.0".equals(cfgVersion) || "1.1.0".equals(cfgVersion)
                            || "1.0.0-26.3".equals(cfgVersion) || "1.1.0-26.3".equals(cfgVersion)
            );
            boolean isPre121 = isPre120 || "1.2.0-26.3".equals(cfgVersion);
            boolean isPre153 = isSupportedLegacy && !"1.5.3-26.3".equals(cfgVersion) && !"1.5.4-26.3".equals(cfgVersion);
            boolean isPre154 = isSupportedLegacy && !"1.5.4-26.3".equals(cfgVersion);
            boolean isPre155 = isSupportedLegacy;
            deserializeStateInner(root, isPre120, isPre121, isPre153, isPre154, isPre155);
            return true;
        } finally {
            HudAlertBus.setSuppressAlerts(false);
            ToggleSoundsModule.setSuppressSounds(false);
        }
    }

    private void deserializeStateInner(JsonObject root, boolean isPre120, boolean isPre121,
                                       boolean isPre153, boolean isPre154, boolean isPre155) {
        if (root.has("theme") && root.get("theme").isJsonPrimitive()) {
            ThemeManager.getInstance().setThemeByName(root.get("theme").getAsString());
        }

        if (root.has("modules") && root.get("modules").isJsonObject()) {
            JsonObject modulesObj = root.getAsJsonObject("modules");
            for (Module m : ModuleManager.getInstance().getModules()) {
                JsonObject mObj = null;
                if (modulesObj.has(m.getName()) && modulesObj.get(m.getName()).isJsonObject()) {
                    mObj = modulesObj.getAsJsonObject(m.getName());
                } else if ("OpticumeAura".equals(m.getName()) && modulesObj.has("OptimusAura") && modulesObj.get("OptimusAura").isJsonObject()) {
                    mObj = modulesObj.getAsJsonObject("OptimusAura");
                }
                if (mObj == null) continue;

                if (mObj.has("keyBind") && mObj.get("keyBind").isJsonPrimitive()) {
                    m.setKeyBind(mObj.get("keyBind").getAsInt());
                }
                if (mObj.has("settings") && mObj.get("settings").isJsonObject()) {
                    JsonObject sObj = mObj.getAsJsonObject("settings");
                    for (Setting<?> s : m.getSettings()) {
                        if (!sObj.has(s.getName())) continue;
                        JsonElement el = sObj.get(s.getName());
                        try {
                            if (s instanceof BooleanSetting bs && el.isJsonPrimitive()) {
                                bs.setValue(el.getAsBoolean());
                            } else if (s instanceof NumberSetting ns && el.isJsonPrimitive()) {
                                ns.setValueClamped(el.getAsDouble());
                            } else if (s instanceof ModeSetting ms && el.isJsonPrimitive()) {
                                ms.setMode(el.getAsString());
                            } else if (s instanceof MultiBoxSetting mbs && el.isJsonArray()) {
                                JsonArray arr = el.getAsJsonArray();
                                List<String> active = new ArrayList<>();
                                for (JsonElement item : arr) {
                                    if (item.isJsonPrimitive()) active.add(item.getAsString().toLowerCase());
                                }
                                for (String opt : mbs.getOptions()) {
                                    mbs.set(opt, active.contains(opt.toLowerCase()));
                                }
                            } else if (s instanceof ColorSetting cs && el.isJsonObject()) {
                                JsonObject cObj = el.getAsJsonObject();
                                if (cObj.has("color")) cs.setValue(cObj.get("color").getAsInt());
                                if (cObj.has("rainbow")) cs.setRainbow(cObj.get("rainbow").getAsBoolean());
                                if (cObj.has("themeSync")) cs.setThemeSync(cObj.get("themeSync").getAsBoolean());
                            } else if (s instanceof TextSetting ts && el.isJsonPrimitive()) {
                                ts.setText(el.getAsString());
                            } else if (s instanceof BindSetting bindSetting && el.isJsonPrimitive()) {
                                bindSetting.setValue(el.getAsInt());
                            }
                        } catch (Throwable ignored) {
                        }
                    }
                }
                if (mObj.has("enabled") && mObj.get("enabled").isJsonPrimitive()) {
                    m.setEnabled(mObj.get("enabled").getAsBoolean());
                }
            }

            if (isPre120) {
                migratePre120Modules(modulesObj);
            }
            if (isPre121) {
                migratePre121Modules(modulesObj);
            }
        }

        if (root.has("hud") && root.get("hud").isJsonObject()) {
            JsonObject hudObj = root.getAsJsonObject("hud");
            for (Map.Entry<String, JsonElement> entry : hudObj.entrySet()) {
                HudManager.DraggableWidget w = HudManager.getInstance().getWidget(entry.getKey());
                if (w != null && entry.getValue().isJsonObject()) {
                    JsonObject wObj = entry.getValue().getAsJsonObject();
                    if (wObj.has("x") && wObj.has("y")) {
                        w.setPosition(wObj.get("x").getAsFloat(), wObj.get("y").getAsFloat());
                    }
                    if (wObj.has("scale")) {
                        w.setScale(wObj.get("scale").getAsFloat());
                    }
                }
            }
        }

        if (isPre153) {
            migratePre153Configs(root);
        }
        if (isPre154) {
            migratePre154Configs(root);
        }
        if (isPre155) {
            migratePre155Configs(root);
        }
    }

    private void migratePre120Modules(JsonObject modulesObj) {
        ModuleManager mm = ModuleManager.getInstance();

        // 1. Migrate InfoHUD sub-toggles into standalone Category.HUD modules
        if (modulesObj.has("InfoHUD") && modulesObj.get("InfoHUD").isJsonObject()) {
            JsonObject infoObj = modulesObj.getAsJsonObject("InfoHUD");
            boolean masterOn = !infoObj.has("enabled") || !infoObj.get("enabled").isJsonPrimitive()
                    || infoObj.get("enabled").getAsBoolean();
            JsonObject infoSettings = (infoObj.has("settings") && infoObj.get("settings").isJsonObject())
                    ? infoObj.getAsJsonObject("settings") : null;

            String[][] mappings = {
                    {"Watermark", "Watermark"},
                    {"DynamicIsland", "DynamicIsland"},
                    {"ArrayList", "ArrayList"},
                    {"StaffList", "StaffList"},
                    {"Radar", "Radar"},
                    {"BpsGraph", "BPSGraph"},
                    {"TotemCounter", "TotemCounter"},
                    {"Coordinates", "Coordinates"},
                    {"Notifications", "Notifications"}
            };
            for (String[] pair : mappings) {
                String legacySetting = pair[0];
                String targetModule = pair[1];
                if (modulesObj.has(targetModule)) continue;
                Module m = findModule(mm, targetModule);
                if (m != null && infoSettings != null && infoSettings.has(legacySetting)
                        && infoSettings.get(legacySetting).isJsonPrimitive()) {
                    m.setEnabled(masterOn && infoSettings.get(legacySetting).getAsBoolean());
                }
            }
        }

        // 2. Fix Arrows & NameTags hiding unarmored players by default on pre-1.2.0 configs
        for (String visualName : new String[]{"Arrows", "NameTags"}) {
            if (!modulesObj.has(visualName)) continue;
            Module m = findModule(mm, visualName);
            if (m != null) {
                for (Setting<?> s : m.getSettings()) {
                    if (s instanceof MultiBoxSetting mbs && "Отображать".equals(s.getName())) {
                        mbs.set("Голых", true);
                    }
                }
            }
        }

        // 3. Upgrade JumpCircles saved default style ("Double Wave") to Desire Effect
        Module jumpCircles = findModule(mm, "JumpCircles");
        if (jumpCircles != null) {
            for (Setting<?> s : jumpCircles.getSettings()) {
                if (s instanceof ModeSetting ms && "Style".equals(s.getName()) && ms.is("Double Wave")) {
                    ms.setMode("Desire Effect");
                }
            }
        }
    }

    private void migratePre121Modules(JsonObject modulesObj) {
        ModuleManager mm = ModuleManager.getInstance();

        // 1. Migrate legacy NoHurtCam into NoRender -> HurtCam
        if (modulesObj.has("NoHurtCam") && modulesObj.get("NoHurtCam").isJsonObject()) {
            JsonObject nhc = modulesObj.getAsJsonObject("NoHurtCam");
            if (nhc.has("enabled") && nhc.get("enabled").isJsonPrimitive() && nhc.get("enabled").getAsBoolean()) {
                Module noRender = findModule(mm, "NoRender");
                if (noRender != null) {
                    setBoolSetting(noRender, "HurtCam", true);
                }
            }
        }

        // 2. Migrate legacy standalone Atmosphere into unified Ambience -> Atmosphere
        if (modulesObj.has("Atmosphere") && modulesObj.get("Atmosphere").isJsonObject()) {
            JsonObject atmoObj = modulesObj.getAsJsonObject("Atmosphere");
            Module ambience = findModule(mm, "Ambience");
            if (ambience != null) {
                boolean atmoOn = atmoObj.has("enabled") && atmoObj.get("enabled").isJsonPrimitive()
                        && atmoObj.get("enabled").getAsBoolean();
                if (atmoOn) {
                    ambience.setEnabled(true);
                    setBoolSetting(ambience, "Atmosphere", true);
                }
                if (atmoObj.has("settings") && atmoObj.get("settings").isJsonObject()) {
                    JsonObject sObj = atmoObj.getAsJsonObject("settings");
                    String[][] renamed = {
                            {"Preset", "Atmo Preset"},
                            {"Color Mode", "Atmo Color Mode"},
                            {"Fog Color", "Atmo Fog Color"},
                            {"Ripple Strength", "Atmo Ripple Str"}
                    };
                    for (String[] pair : renamed) {
                        if (!sObj.has(pair[0])) continue;
                        JsonElement el = sObj.get(pair[0]);
                        for (Setting<?> s : ambience.getSettings()) {
                            if (!pair[1].equals(s.getName())) continue;
                            try {
                                if (s instanceof ModeSetting ms && el.isJsonPrimitive()) ms.setMode(el.getAsString());
                                else if (s instanceof NumberSetting ns && el.isJsonPrimitive()) ns.setValueClamped(el.getAsDouble());
                                else if (s instanceof ColorSetting cs && el.isJsonObject()) {
                                    JsonObject cObj = el.getAsJsonObject();
                                    if (cObj.has("color")) cs.setValue(cObj.get("color").getAsInt());
                                    if (cObj.has("rainbow")) cs.setRainbow(cObj.get("rainbow").getAsBoolean());
                                    if (cObj.has("themeSync")) cs.setThemeSync(cObj.get("themeSync").getAsBoolean());
                                }
                            } catch (Throwable ignored) {
                            }
                        }
                    }
                    String[] sameName = {
                            "Volumetric Fog", "Fog Density", "Fog Height", "Turbulence", "Sun Scatter",
                            "Ground Haze", "Haze Density", "Precipitation", "Precip Type", "Precip Density",
                            "Surface Ripples", "Lightning", "Wind"
                    };
                    for (String key : sameName) {
                        if (!sObj.has(key)) continue;
                        JsonElement el = sObj.get(key);
                        for (Setting<?> s : ambience.getSettings()) {
                            if (!key.equals(s.getName())) continue;
                            try {
                                if (s instanceof BooleanSetting bs && el.isJsonPrimitive()) bs.setValue(el.getAsBoolean());
                                else if (s instanceof NumberSetting ns && el.isJsonPrimitive()) ns.setValueClamped(el.getAsDouble());
                                else if (s instanceof ModeSetting ms && el.isJsonPrimitive()) ms.setMode(el.getAsString());
                            } catch (Throwable ignored) {
                            }
                        }
                    }
                }
            }
        }
    }

    private void migratePre153Configs(JsonObject root) {
        ModuleManager mm = ModuleManager.getInstance();

        // 1. Resolve CooldownsHUD <-> Coordinates overlap in older saved configs
        HudManager.DraggableWidget cd = HudManager.getInstance().getWidget("CooldownsHUD");
        HudManager.DraggableWidget crd = HudManager.getInstance().getWidget("Coordinates");
        if (cd != null && crd != null) {
            float cdH = Math.max(60.0f, cd.getScaledHeight());
            float crdH = Math.max(20.0f, crd.getScaledHeight());
            boolean horizOverlap = Math.abs(cd.getX() - crd.getX()) < 150.0f;
            boolean vertOverlap = cd.getY() + cdH > crd.getY() - 2.0f && cd.getY() < crd.getY() + crdH + 2.0f;
            if (horizOverlap && vertOverlap) {
                float safeY = Math.max(8.0f, crd.getY() - cdH - 6.0f);
                cd.setPosition(cd.getX(), safeY);
            }
        }

        // 2. Reset stale BetterTab PosX=411 saved prior to center-animation fix
        Module betterTab = findModule(mm, "BetterTab");
        if (betterTab != null) {
            for (Setting<?> s : betterTab.getSettings()) {
                if (s instanceof NumberSetting ns && "PosX".equalsIgnoreCase(s.getName())) {
                    if (Math.abs(ns.getValue() - 411.0) <= 1.5) {
                        ns.setValueClamped(-1.0);
                        HudManager.DraggableWidget tabW = HudManager.getInstance().getWidget("TabList");
                        if (tabW != null) {
                            tabW.setPosition(-1.0f, tabW.getY());
                        }
                    }
                    break;
                }
            }
        }

        // 3. Auto-center Watermark under DynamicIsland when positioned near center
        Module watermark = findModule(mm, "Watermark");
        if (watermark != null) {
            double wx = -1.0;
            for (Setting<?> s : watermark.getSettings()) {
                if (s instanceof NumberSetting ns && "PosX".equalsIgnoreCase(s.getName())) {
                    wx = ns.getValue();
                    break;
                }
            }
            if (wx >= 330.0 && wx <= 450.0) {
                setBoolSetting(watermark, "Auto Center", true);
            }
        }

        // 4. Enable EntityCulling (keeping players visible) and tune default BlurPasses 3 -> 2 for Iris Xe
        Module culling = findModule(mm, "EntityCulling");
        if (culling != null && !culling.isEnabled()) {
            culling.setEnabled(true);
            setBoolSetting(culling, "Keep Players", true);
        }

        Module iface = findModule(mm, "Interface");
        if (iface != null) {
            for (Setting<?> s : iface.getSettings()) {
                if (s instanceof NumberSetting ns && "BlurPasses".equalsIgnoreCase(s.getName())
                        && Math.abs(ns.getValue() - 3.0) < 0.01) {
                    ns.setValueClamped(2.0);
                    break;
                }
            }
        }
    }

    private void migratePre154Configs(JsonObject root) {
        ModuleManager mm = ModuleManager.getInstance();

        // 1. Resolve ArmorHUD <-> PotionHUD overlap in legacy configs (e.g. HolyWorld 1.1.0-26.3)
        HudManager.DraggableWidget armor = HudManager.getInstance().getWidget("ArmorHUD");
        HudManager.DraggableWidget potion = HudManager.getInstance().getWidget("PotionHUD");
        if (armor != null && potion != null) {
            float armorH = Math.max(144.0f, armor.getScaledHeight());
            float potionH = Math.max(36.0f, potion.getScaledHeight());
            boolean horizOverlap = Math.abs(armor.getX() - potion.getX()) < 60.0f;
            boolean vertOverlap = armor.getY() + armorH > potion.getY() - 4.0f
                    && armor.getY() < potion.getY() + potionH + 4.0f;
            if (horizOverlap && vertOverlap) {
                float safeY = Math.max(8.0f, potion.getY() - armorH - 8.0f);
                armor.setPosition(armor.getX(), safeY);
                Module armorMod = findModule(mm, "ArmorHUD");
                if (armorMod != null) {
                    setNumberSetting(armorMod, "PosY", safeY);
                }
            }
        }

        // 2. Move InventoryHUD out of top-center Watermark/DynamicIsland collision zone
        HudManager.DraggableWidget inv = HudManager.getInstance().getWidget("InventoryHUD");
        if (inv != null && inv.getY() < 85.0f && inv.getX() >= 280.0f && inv.getX() <= 620.0f) {
            inv.setPosition(760.0f, 415.0f);
            Module invMod = findModule(mm, "InventoryHUD");
            if (invMod != null) {
                setNumberSetting(invMod, "PosX", 760.0);
                setNumberSetting(invMod, "PosY", 415.0);
            }
        }

        // 3. Default ArrayList.Hide HUD to true on pre-1.5.4 configs to prevent list overflow
        Module arrayList = findModule(mm, "ArrayList");
        if (arrayList != null) {
            setBoolSetting(arrayList, "Hide HUD", true);
        }

        // 4. Default Ambience.Shader Quality to Balanced for Intel Iris Xe performance
        Module ambience = findModule(mm, "Ambience");
        if (ambience != null) {
            setModeSetting(ambience, "Shader Quality", "Balanced");
        }
    }

    private void migratePre155Configs(JsonObject root) {
        ModuleManager mm = ModuleManager.getInstance();
        JsonObject modulesObj = (root != null && root.has("modules") && root.get("modules").isJsonObject())
                ? root.getAsJsonObject("modules") : null;

        // 1. Migrate OpticumeAura legacy AimMode names and unsafe HitboxMargin values
        Module aura = findModule(mm, "OpticumeAura");
        if (aura != null) {
            JsonObject auraSettings = null;
            if (modulesObj != null) {
                if (modulesObj.has("OpticumeAura") && modulesObj.get("OpticumeAura").isJsonObject()) {
                    JsonObject mObj = modulesObj.getAsJsonObject("OpticumeAura");
                    if (mObj.has("settings") && mObj.get("settings").isJsonObject()) {
                        auraSettings = mObj.getAsJsonObject("settings");
                    }
                } else if (modulesObj.has("OptimusAura") && modulesObj.get("OptimusAura").isJsonObject()) {
                    JsonObject mObj = modulesObj.getAsJsonObject("OptimusAura");
                    if (mObj.has("settings") && mObj.get("settings").isJsonObject()) {
                        auraSettings = mObj.getAsJsonObject("settings");
                    }
                }
            }
            if (auraSettings != null && auraSettings.has("AimMode") && auraSettings.get("AimMode").isJsonPrimitive()) {
                String oldAim = auraSettings.get("AimMode").getAsString().trim();
                if ("Smooth".equalsIgnoreCase(oldAim)) {
                    setModeSetting(aura, "AimMode", "Organic");
                } else if ("SnapOnHit".equalsIgnoreCase(oldAim)) {
                    setModeSetting(aura, "AimMode", "Snap");
                }
            }
            String savedPreset = "";
            if (auraSettings != null && auraSettings.has("Preset") && auraSettings.get("Preset").isJsonPrimitive()) {
                savedPreset = auraSettings.get("Preset").getAsString().trim();
            } else {
                for (Setting<?> s : aura.getSettings()) {
                    if (s instanceof ModeSetting ms && "Preset".equalsIgnoreCase(s.getName())) {
                        savedPreset = ms.getMode();
                        break;
                    }
                }
            }
            if (!"Custom".equalsIgnoreCase(savedPreset)) {
                for (Setting<?> s : aura.getSettings()) {
                    if (s instanceof NumberSetting ns && "HitboxMargin".equalsIgnoreCase(s.getName())) {
                        if (ns.getValue() > 0.10) {
                            boolean strict = "HolyWorld".equalsIgnoreCase(savedPreset)
                                    || "FunTime".equalsIgnoreCase(savedPreset)
                                    || "SpookyTime".equalsIgnoreCase(savedPreset)
                                    || "Grim 1.21".equalsIgnoreCase(savedPreset);
                            ns.setValueClamped(strict ? 0.0 : 0.04);
                        }
                        break;
                    }
                }
            }
        }

        // 2. Migrate LegitAura legacy AimMode "Standard" -> "Organic"
        Module legitAura = findModule(mm, "LegitAura");
        if (legitAura != null && modulesObj != null && modulesObj.has("LegitAura") && modulesObj.get("LegitAura").isJsonObject()) {
            JsonObject mObj = modulesObj.getAsJsonObject("LegitAura");
            if (mObj.has("settings") && mObj.get("settings").isJsonObject()) {
                JsonObject sObj = mObj.getAsJsonObject("settings");
                if (sObj.has("AimMode") && sObj.get("AimMode").isJsonPrimitive()
                        && "Standard".equalsIgnoreCase(sObj.get("AimMode").getAsString().trim())) {
                    setModeSetting(legitAura, "AimMode", "Organic");
                }
            }
        }

        // 3. Reset BetterTab off-center PosX saved before Auto Center / Center-Anchor fix
        Module betterTab = findModule(mm, "BetterTab");
        if (betterTab != null) {
            setBoolSetting(betterTab, "Auto Center", true);
            setNumberSetting(betterTab, "PosX", -1.0);
            HudManager.DraggableWidget tabW = HudManager.getInstance().getWidget("TabList");
            if (tabW != null) {
                tabW.syncExternalPosition(-1.0f, tabW.getY());
            }
        }
    }
}
