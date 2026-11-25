package dev.opticumeclient.client.script;

import dev.opticumeclient.client.command.CommandManager;
import dev.opticumeclient.client.hud.HudAlertBus;
import dev.opticumeclient.client.modules.Module;
import dev.opticumeclient.client.modules.ModuleManager;
import dev.opticumeclient.client.modules.other.PanicModule;
import dev.opticumeclient.client.util.ChatUtils;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Lightweight zero-dependency runtime automation script engine loading {@code *.oc} rules
 * from {@code <gameDir>/opticumeclient/scripts/} with hot-reload support.
 */
public final class ScriptManager {
    public record RuleAction(String verb, String arg) {
    }

    public record ScriptRule(String condition, int intervalTicks, List<RuleAction> actions) {
    }

    public static final class LoadedScript {
        public final String name;
        public boolean enabled;
        public final List<ScriptRule> rules;
        public long lastTickRan = 0L;

        LoadedScript(String name, boolean enabled, List<ScriptRule> rules) {
            this.name = name;
            this.enabled = enabled;
            this.rules = rules;
        }
    }

    private static final ScriptManager INSTANCE = new ScriptManager();
    private static final int MAX_SCRIPT_DEPTH = 8;
    private final Map<String, LoadedScript> scripts = new LinkedHashMap<>();
    private final ThreadLocal<Integer> execDepth = ThreadLocal.withInitial(() -> 0);
    private File scriptsDir;
    private boolean initialized = false;
    private long tickCounter = 0L;

    private ScriptManager() {
    }

    public static ScriptManager getInstance() {
        return INSTANCE;
    }

    public synchronized void ensureLoaded() {
        if (initialized) return;
        initialized = true;
        Minecraft mc = Minecraft.getInstance();
        File gameDir = (mc != null && mc.gameDirectory != null) ? mc.gameDirectory : new File(".");
        scriptsDir = new File(gameDir, "opticumeclient/scripts");
        if (!scriptsDir.exists()) {
            scriptsDir.mkdirs();
            writeExampleScript();
        }
        reload();
    }

    private void writeExampleScript() {
        File example = new File(scriptsDir, "auto_sprint_example.oc.disabled");
        if (example.exists()) return;
        String sample = """
                # OpticumeClient Runtime Script Example (rename to .oc and run .script reload)
                # Conditions: always | hp < N | hurt > 0 | on <ModuleName> | off <ModuleName>
                # Interval: every <ticks> (default 20)
                # Actions: toggle <Module> | enable <Module> | disable <Module> | cmd <command> | chat <msg> | notify <text>
                every 40 if hp < 10 -> notify Low HP Warning!
                """;
        try {
            Files.writeString(example.toPath(), sample, StandardCharsets.UTF_8);
        } catch (Throwable ignored) {
        }
    }

    public synchronized int reload() {
        ensureLoadedDirOnly();
        File[] files = scriptsDir.listFiles((d, name) -> name.toLowerCase(Locale.ROOT).endsWith(".oc"));
        if (files == null) {
            System.err.println("[OpticumeClient] Failed to list scripts directory: " + scriptsDir);
            return -1;
        }
        Map<String, Boolean> prevStates = new LinkedHashMap<>();
        for (LoadedScript s : scripts.values()) {
            prevStates.put(s.name.toLowerCase(Locale.ROOT), s.enabled);
        }
        Map<String, LoadedScript> next = new LinkedHashMap<>();
        for (File f : files) {
            try {
                String content = Files.readString(f.toPath(), StandardCharsets.UTF_8);
                String name = f.getName().substring(0, f.getName().length() - 3);
                List<ScriptRule> rules = parseRules(content);
                String key = name.toLowerCase(Locale.ROOT);
                boolean en = prevStates.getOrDefault(key, true);
                next.put(key, new LoadedScript(name, en, rules));
            } catch (Throwable ignored) {
            }
        }
        scripts.clear();
        scripts.putAll(next);
        return scripts.size();
    }

    private void ensureLoadedDirOnly() {
        if (scriptsDir != null) return;
        Minecraft mc = Minecraft.getInstance();
        File gameDir = (mc != null && mc.gameDirectory != null) ? mc.gameDirectory : new File(".");
        scriptsDir = new File(gameDir, "opticumeclient/scripts");
        if (!scriptsDir.exists()) scriptsDir.mkdirs();
    }

    public synchronized List<LoadedScript> getScripts() {
        ensureLoaded();
        return Collections.unmodifiableList(new ArrayList<>(scripts.values()));
    }

    public synchronized boolean setScriptEnabled(String name, boolean enabled) {
        ensureLoaded();
        LoadedScript s = scripts.get(name.toLowerCase(Locale.ROOT));
        if (s == null) return false;
        s.enabled = enabled;
        return true;
    }

    public synchronized boolean runOnce(String name) {
        if (PanicModule.isPanicActive()) return false;
        ensureLoaded();
        LoadedScript s = scripts.get(name.toLowerCase(Locale.ROOT));
        if (s == null) return false;
        int depth = execDepth.get();
        if (depth >= MAX_SCRIPT_DEPTH) {
            ChatUtils.sendClientMessage("§c[Script] Recursion limit reached running '" + s.name + "'");
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        execDepth.set(depth + 1);
        try {
            for (ScriptRule r : s.rules) {
                if (PanicModule.isPanicActive()) break;
                try {
                    if (evaluateCondition(mc, r.condition())) {
                        executeActions(mc, r.actions());
                    }
                } catch (Throwable t) {
                    System.err.println("[OpticumeClient] Script error in '" + s.name + "': " + t.getMessage());
                }
                if (PanicModule.isPanicActive()) break;
            }
        } finally {
            execDepth.set(depth);
        }
        return true;
    }

    public void onTick() {
        if (PanicModule.isPanicActive()) return;
        ensureLoaded();
        tickCounter++;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || mc.level == null || scripts.isEmpty()) return;

        List<LoadedScript> snapshot;
        synchronized (this) {
            snapshot = new ArrayList<>(scripts.values());
        }
        int depth = execDepth.get();
        if (depth >= MAX_SCRIPT_DEPTH) return;
        execDepth.set(depth + 1);
        try {
            for (LoadedScript s : snapshot) {
                if (PanicModule.isPanicActive()) return;
                if (!s.enabled) continue;
                for (ScriptRule rule : s.rules) {
                    if (PanicModule.isPanicActive()) return;
                    try {
                        int iv = Math.max(1, rule.intervalTicks());
                        if (tickCounter % iv == 0 && evaluateCondition(mc, rule.condition())) {
                            executeActions(mc, rule.actions());
                        }
                    } catch (Throwable t) {
                        System.err.println("[OpticumeClient] Script tick error in '" + s.name + "': " + t.getMessage());
                    }
                    if (PanicModule.isPanicActive()) return;
                }
            }
        } finally {
            execDepth.set(depth);
        }
    }

    private static List<ScriptRule> parseRules(String content) {
        List<ScriptRule> out = new ArrayList<>();
        for (String rawLine : content.split("\n")) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("//")) continue;
            int arrow = line.indexOf("->");
            if (arrow < 0) continue;

            String lhs = line.substring(0, arrow).trim();
            String rhs = line.substring(arrow + 2).trim();

            int interval = 20;
            String cond = "always";
            if (lhs.toLowerCase(Locale.ROOT).startsWith("every ")) {
                String rest = lhs.substring(6).trim();
                int sp = rest.indexOf(' ');
                String numTok = sp > 0 ? rest.substring(0, sp) : rest;
                try {
                    interval = Math.max(1, Integer.parseInt(numTok));
                    lhs = sp > 0 ? rest.substring(sp + 1).trim() : "";
                } catch (NumberFormatException ignored) {
                }
            }
            if (lhs.toLowerCase(Locale.ROOT).startsWith("if ")) {
                cond = lhs.substring(3).trim();
            } else if (!lhs.isEmpty() && !"always".equalsIgnoreCase(lhs)) {
                cond = lhs;
            }

            List<RuleAction> actions = new ArrayList<>();
            for (String act : rhs.split(";")) {
                String t = act.trim();
                if (t.isEmpty()) continue;
                int sp = t.indexOf(' ');
                if (sp > 0) {
                    actions.add(new RuleAction(t.substring(0, sp).trim().toLowerCase(Locale.ROOT), t.substring(sp + 1).trim()));
                } else {
                    actions.add(new RuleAction(t.toLowerCase(Locale.ROOT), ""));
                }
            }
            if (!actions.isEmpty()) {
                out.add(new ScriptRule(cond, interval, actions));
            }
        }
        return out;
    }

    private static boolean evaluateCondition(Minecraft mc, String cond) {
        if (cond == null || cond.isBlank() || "always".equalsIgnoreCase(cond)) return true;
        String low = cond.toLowerCase(Locale.ROOT).trim();
        try {
            if (low.startsWith("hp <")) {
                float val = Float.parseFloat(low.substring(4).trim());
                return mc.player != null && (mc.player.getHealth() + mc.player.getAbsorptionAmount()) < val;
            }
            if (low.startsWith("hp >")) {
                float val = Float.parseFloat(low.substring(4).trim());
                return mc.player != null && (mc.player.getHealth() + mc.player.getAbsorptionAmount()) > val;
            }
            if (low.startsWith("hurt >")) {
                int val = Integer.parseInt(low.substring(6).trim());
                return mc.player != null && mc.player.hurtTime > val;
            }
            if (low.startsWith("on ")) {
                Module m = findModule(cond.substring(3).trim());
                return m != null && m.isEnabled();
            }
            if (low.startsWith("off ")) {
                Module m = findModule(cond.substring(4).trim());
                return m != null && !m.isEnabled();
            }
        } catch (Throwable ignored) {
            return false;
        }
        return false;
    }

    private static void executeActions(Minecraft mc, List<RuleAction> actions) {
        for (RuleAction a : actions) {
            if (PanicModule.isPanicActive()) return;
            switch (a.verb()) {
                case "toggle" -> {
                    Module m = findModule(a.arg());
                    if (m != null) m.toggle();
                }
                case "enable" -> {
                    Module m = findModule(a.arg());
                    if (m != null && !m.isEnabled()) m.setEnabled(true);
                }
                case "disable" -> {
                    Module m = findModule(a.arg());
                    if (m != null && m.isEnabled()) m.setEnabled(false);
                }
                case "notify" -> ChatUtils.sendClientMessage("§d[Script] §f" + a.arg());
                case "cmd" -> {
                    String pfx = CommandManager.getInstance().getPrefix();
                    String line = a.arg().startsWith(pfx) ? a.arg() : (pfx + a.arg());
                    CommandManager.getInstance().executeCommands(line, new CallbackInfo("script", true));
                }
                case "chat" -> {
                    if (mc != null && mc.player != null && mc.player.connection != null) {
                        if (a.arg().startsWith("/")) {
                            mc.player.connection.sendCommand(a.arg().substring(1));
                        } else {
                            mc.player.connection.sendChat(a.arg());
                        }
                    }
                }
                default -> {
                    if ("toast".equals(a.verb())) {
                        HudAlertBus.getInstance();
                    }
                }
            }
        }
    }

    private static Module findModule(String name) {
        for (Module m : ModuleManager.getInstance().getModules()) {
            if (m.getName().equalsIgnoreCase(name)) return m;
        }
        return null;
    }
}
