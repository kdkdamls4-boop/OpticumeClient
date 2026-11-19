package dev.opticumeclient.client.bots;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.opticumeclient.client.util.ChatUtils;
import dev.opticumeclient.mixin.MinecraftAccessor;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.core.SectionPos;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

public class BotSessionManager {
    private static BotSessionManager instance;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Pattern NICK_PATTERN = Pattern.compile("[A-Za-z0-9_]{3,16}");
    private static final long RECONNECT_DELAY_MS = 5000L;
    private static final long AUTH_DELAY_MS = 1200L;

    private final Map<String, BotSession> sessions = new LinkedHashMap<>();
    private BotSession activeSession = null;
    private final BotSession mainSession;

    private final Map<PacketListener, BotSession> listenerToSession =
            Collections.synchronizedMap(new IdentityHashMap<>());

    private boolean ticking = false;
    private final Map<String, Long> reconnectAtMs = new HashMap<>();

    private boolean showBotChat = false;
    private int maxBots = 5;

    private BotSessionManager() {
        this.mainSession = new BotSession("main");
        this.mainSession.setState(SessionState.CONNECTED);
        load();
    }

    public static synchronized BotSessionManager getInstance() {
        if (instance == null) {
            instance = new BotSessionManager();
        }
        return instance;
    }

    public static synchronized void shutdownIfInitialized() {
        if (instance == null) return;
        instance.stopAll();
    }

    public void stopAll() {
        if (this.activeSession != null && this.activeSession != this.mainSession) {
            try {
                switchToMain();
            } catch (Throwable ignored) {
                this.activeSession = null;
                SessionContext.setCurrent(this.mainSession);
            }
        }
        reconnectAtMs.clear();
        for (BotSession session : new ArrayList<>(sessions.values())) {
            try {
                disconnectBot(session);
            } catch (Throwable ignored) {
            }
        }
        save();
    }

    public boolean isShowBotChat() {
        return showBotChat;
    }

    public void setShowBotChat(boolean showBotChat) {
        this.showBotChat = showBotChat;
    }

    public int getMaxBots() {
        return maxBots;
    }

    public void setMaxBots(int maxBots) {
        this.maxBots = maxBots;
    }

    public void registerBotListener(PacketListener listener, BotSession session) {
        listenerToSession.put(listener, session);
    }

    public BotSession sessionOf(PacketListener listener) {
        if (listener instanceof IBotListener botListener) {
            return botListener.opticume$getBotSession();
        }
        return listenerToSession.get(listener);
    }

    public boolean isBackground(BotSession session) {
        return session != null && !isForeground(session);
    }

    private void unregisterListenersOf(BotSession session) {
        synchronized (listenerToSession) {
            listenerToSession.values().removeIf(s -> s == session);
        }
    }

    public static boolean isValidNick(String name) {
        return name != null && NICK_PATTERN.matcher(name).matches();
    }

    public boolean createBot(String name, String serverIp, String password, boolean autoReconnect) {
        if (!isValidNick(name) || serverIp == null || serverIp.isBlank()) {
            return false;
        }
        if (getBot(name) != null || sessions.size() >= maxBots) {
            return false;
        }

        BotSession session = new BotSession(name);
        session.setServerAddress(serverIp.trim());
        session.setPassword(password == null ? "" : password);
        session.setAutoReconnect(autoReconnect);
        sessions.put(name, session);
        save();
        session.connect(session.getServerAddress());
        return true;
    }

    public boolean removeBot(String name) {
        BotSession session = getBot(name);
        if (session == null) return false;
        if (activeSession == session) switchToMain();

        reconnectAtMs.remove(session.getName());
        session.disconnect();
        unregisterListenersOf(session);
        sessions.remove(session.getName());
        save();
        return true;
    }

    public void connectBot(BotSession session) {
        if (session == null || session == mainSession || session.getServerAddress() == null) return;
        reconnectAtMs.remove(session.getName());
        if (session.getState() == SessionState.ERROR) {
            session.setState(SessionState.DISCONNECTED);
        }
        session.connect(session.getServerAddress());
    }

    public void disconnectBot(BotSession session) {
        if (session == null || session == mainSession) return;
        if (activeSession == session) switchToMain();
        reconnectAtMs.remove(session.getName());
        session.disconnect();
        unregisterListenersOf(session);
    }

    public void updateBot(BotSession session, String serverIp, String password, boolean autoReconnect) {
        if (session == null || session == mainSession) return;
        if (serverIp != null && !serverIp.isBlank()) session.setServerAddress(serverIp.trim());
        if (password != null) session.setPassword(password);
        session.setAutoReconnect(autoReconnect);
        if (!autoReconnect) reconnectAtMs.remove(session.getName());
        save();
    }

    public void handleBotDisconnect(BotSession session) {
        if (session == mainSession) {
            switchToMain();
            return;
        }
        if (session.getState() == SessionState.DISCONNECTED) return;
        if (activeSession == session) switchToMain();

        unregisterListenersOf(session);
        if (session.getDisconnectReason() == null && session.getConnection() != null
                && session.getConnection().getDisconnectionDetails() != null) {
            session.setDisconnectReason(session.getConnection().getDisconnectionDetails().reason().getString());
        }
        if (session.getDisconnectReason() == null) {
            session.setDisconnectReason("Соединение закрыто");
        }
        session.setState(SessionState.DISCONNECTED);
        session.cleanup();

        boolean willReconnect = scheduleReconnect(session);
        ChatUtils.sendClientMessage("§cБот §f" + session.getName()
                + " §cотключён: §7" + session.getDisconnectReason()
                + (willReconnect ? "§7, переподключаюсь…" : ""));
    }

    public void onConnectFailed(BotSession session) {
        if (!sessions.containsValue(session)) return;
        unregisterListenersOf(session);
        session.cleanup();
        boolean willReconnect = scheduleReconnect(session);
        ChatUtils.sendClientMessage("§cБот §f" + session.getName() + " §cне зашёл: §7" + session.getDisconnectReason()
                + (willReconnect ? "§7, повтор через 5с" : ""));
    }

    private boolean scheduleReconnect(BotSession session) {
        if (!session.isAutoReconnect() || session.getServerAddress() == null || !sessions.containsValue(session)) {
            return false;
        }
        reconnectAtMs.put(session.getName(), System.currentTimeMillis() + RECONNECT_DELAY_MS);
        return true;
    }

    private void tickReconnects(long now) {
        if (reconnectAtMs.isEmpty()) return;
        Iterator<Map.Entry<String, Long>> it = reconnectAtMs.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Long> entry = it.next();
            if (now < entry.getValue()) continue;
            it.remove();
            BotSession session = sessions.get(entry.getKey());
            if (session == null || !session.isAutoReconnect() || session.getServerAddress() == null) continue;
            if (session.isActive() || session.getState() == SessionState.CONNECTING) continue;
            session.setState(SessionState.DISCONNECTED);
            session.connect(session.getServerAddress());
        }
    }

    public long getReconnectInMs(BotSession session) {
        Long at = reconnectAtMs.get(session.getName());
        return at == null ? -1L : Math.max(0L, at - System.currentTimeMillis());
    }

    public boolean switchToMain() {
        return switchTo(mainSession);
    }

    public boolean switchTo(BotSession target) {
        if (target == null) return false;
        if (target == getActiveSession()) return true;
        if (target != mainSession && !target.isPlayable()) return false;
        if (BotContext.current() != null) {
            Minecraft.getInstance().execute(() -> switchTo(target));
            return true;
        }

        Minecraft mc = Minecraft.getInstance();
        BotSession previous = getActiveSession();
        if (previous == mainSession) bindMainConnection();

        mc.packetProcessor().processQueuedPackets();
        if (previous.getPacketProcessor() != null) {
            previous.getPacketProcessor().processQueuedPackets();
        }

        previous.setWorld(mc.level);
        previous.setPlayer(mc.player);
        previous.setInteractionManager(mc.gameMode);
        previous.setScreen(null);
        if (mc.player != null) {
            previous.setNetworkHandler(mc.player.connection);
            mc.player.input = new ClientInput();
        }

        this.activeSession = target == mainSession ? null : target;
        SessionContext.setCurrent(target);
        loadSessionContext(target);
        return true;
    }

    private void loadSessionContext(BotSession session) {
        Minecraft mc = Minecraft.getInstance();
        mc.level = session.getWorld();
        mc.player = session.getPlayer();
        mc.gameMode = session.getInteractionManager();
        mc.hitResult = null;
        mc.crosshairPickEntity = null;

        ((MinecraftAccessor) (Object) mc).opticume$updateLevelInEngines(mc.level, false);
        if (mc.level != null) {
            resyncChunkTracking(mc.level, mc.player);
        }

        if (mc.player != null) {
            mc.player.input = new KeyboardInput(mc.options);
            mc.setCameraEntity(mc.player);
        }

        mc.gui.setScreen(session.getScreen());
        session.setScreen(null);
    }

    private static void resyncChunkTracking(ClientLevel level, Entity center) {
        ClientChunkCache cache = level.getChunkSource();
        LongOpenHashSet addedChunks = cache.addedLoadedChunks();
        LongOpenHashSet addedEmpty = cache.addedEmptySections();
        int centerX = center != null ? center.chunkPosition().x() : 0;
        int centerZ = center != null ? center.chunkPosition().z() : 0;
        int radius = 48;
        for (int x = centerX - radius; x <= centerX + radius; x++) {
            for (int z = centerZ - radius; z <= centerZ + radius; z++) {
                LevelChunk chunk = cache.getChunk(x, z, ChunkStatus.FULL, false);
                if (chunk == null) continue;
                addedChunks.add(chunk.getPos().pack());
                LevelChunkSection[] sections = chunk.getSections();
                for (int i = 0; i < sections.length; i++) {
                    if (sections[i].hasOnlyAir()) {
                        addedEmpty.add(SectionPos.asLong(x, chunk.getSectionYFromSectionIndex(i), z));
                    }
                }
            }
        }
    }

    private void bindMainConnection() {
        if (activeSession != null) return;
        Minecraft mc = Minecraft.getInstance();
        ClientPacketListener handler = mc.getConnection();
        if (handler == null) return;
        Connection connection = handler.getConnection();
        IBotConnection botConnection = (IBotConnection) (Object) connection;
        if (botConnection.opticume$getBotSession() == null) {
            botConnection.opticume$setBotSession(mainSession);
            mainSession.bindMain(connection, handler);
        } else if (botConnection.opticume$getBotSession() == mainSession) {
            mainSession.setNetworkHandler(handler);
        }
    }

    public void drainForegroundPackets() {
        if (BotContext.current() != null) return;
        BotSession foreground = getActiveSession();
        if (foreground.getPacketProcessor() != null) {
            foreground.getPacketProcessor().processQueuedPackets();
        }
    }

    public void sendChat(BotSession session, String text) {
        if (session == null || text == null || text.isBlank()) return;
        runInContext(session, () -> {
            Minecraft mc = Minecraft.getInstance();
            ClientPacketListener handler = mc.player != null ? mc.player.connection : session.getNetworkHandler();
            if (handler == null) return;
            if (text.startsWith("/")) {
                handler.sendCommand(text.substring(1));
            } else {
                handler.sendChat(text);
            }
        });
    }

    public void runInContext(BotSession session, Runnable action) {
        if (session == getActiveSession() || BotContext.current() == session) {
            action.run();
            return;
        }
        if (!session.isActive() || !BotContext.enter(session)) return;
        try {
            action.run();
        } finally {
            BotContext.exit(session);
        }
    }

    public void onChatMessage(BotSession session, Component message) {
        String text = message.getString();
        session.addChatLine(text);
        detectAuthPrompt(session, text);

        if (showBotChat && isBackground(session) && session != mainSession) {
            Component forwarded = botChatPrefix(session).copy().append(message);
            Minecraft mc = Minecraft.getInstance();
            mc.execute(() -> mc.gui.hud.getChat().addClientSystemMessage(forwarded));
        }
    }

    private void detectAuthPrompt(BotSession session, String text) {
        String password = session.getPassword();
        if (session == mainSession || password == null || password.isBlank()) return;
        String lower = text.toLowerCase(Locale.ROOT);
        if (!session.isRegisterSent() && (lower.contains("/reg") || lower.contains("регистр") || lower.contains("register"))) {
            session.setRegisterSent(true);
            session.setPendingAuthCommand("/register " + password + " " + password);
            session.setPendingAuthAtMs(System.currentTimeMillis() + AUTH_DELAY_MS);
        } else if (!session.isLoginSent() && (lower.contains("/login") || lower.contains("/l ")
                || lower.contains("авториз") || lower.contains("войдите") || lower.contains("залогин"))) {
            session.setLoginSent(true);
            session.setPendingAuthCommand("/login " + password);
            session.setPendingAuthAtMs(System.currentTimeMillis() + AUTH_DELAY_MS);
        }
    }

    private void tickAutoAuth(BotSession session, long now) {
        String command = session.getPendingAuthCommand();
        if (command == null || now < session.getPendingAuthAtMs() || !session.isActive()) return;
        session.setPendingAuthCommand(null);
        sendChat(session, command);
    }

    public void tick() {
        if (ticking) return;
        ticking = true;
        try {
            long now = System.currentTimeMillis();
            bindMainConnection();
            tickReconnects(now);

            if (activeSession != null && !activeSession.isActive()) {
                BotSession dead = activeSession;
                switchToMain();
                handleBotDisconnect(dead);
            }

            BotSession foreground = getActiveSession();
            for (BotSession session : new ArrayList<>(sessions.values())) {
                if (session != foreground) {
                    session.tick();
                }
                tickAutoAuth(session, now);
            }

            if (activeSession != null) {
                mainSession.tick();
            }
        } finally {
            ticking = false;
        }
    }

    public BotSession getBot(String name) {
        if (name == null) return null;
        BotSession exact = sessions.get(name);
        if (exact != null) return exact;
        for (BotSession session : sessions.values()) {
            if (session.getName().equalsIgnoreCase(name)) return session;
        }
        return null;
    }

    public Collection<BotSession> getAllBots() {
        return Collections.unmodifiableCollection(sessions.values());
    }

    public BotSession getActiveSession() {
        return activeSession != null ? activeSession : mainSession;
    }

    public boolean isForeground(BotSession session) {
        return getActiveSession() == session;
    }

    public BotSession getMainSession() {
        return mainSession;
    }

    public static Component botChatPrefix(BotSession session) {
        return Component.literal(session.getName() + " ").withStyle(ChatFormatting.RED, ChatFormatting.BOLD)
                .append(Component.literal("» ").withStyle(ChatFormatting.DARK_GRAY));
    }

    public int getActiveBotsCount() {
        return (int) sessions.values().stream().filter(BotSession::isActive).count();
    }

    private static Path storagePath() {
        Minecraft mc = Minecraft.getInstance();
        File gameDir = mc != null && mc.gameDirectory != null ? mc.gameDirectory : new File(".");
        return new File(new File(gameDir, "opticumeclient"), "bots.json").toPath();
    }

    public void save() {
        JsonArray array = new JsonArray();
        for (BotSession session : sessions.values()) {
            JsonObject obj = new JsonObject();
            obj.addProperty("name", session.getName());
            obj.addProperty("ip", session.getServerAddress() == null ? "" : session.getServerAddress());
            obj.addProperty("password", session.getPassword() == null ? "" : session.getPassword());
            obj.addProperty("autoReconnect", session.isAutoReconnect());
            array.add(obj);
        }
        try {
            Files.createDirectories(storagePath().getParent());
            Files.writeString(storagePath(), GSON.toJson(array), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
        }
    }

    private void load() {
        try {
            Path path = storagePath();
            if (!Files.exists(path)) return;
            JsonArray array = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonArray();
            for (JsonElement el : array) {
                if (!el.isJsonObject()) continue;
                JsonObject entry = el.getAsJsonObject();
                String name = entry.has("name") && !entry.get("name").isJsonNull() ? entry.get("name").getAsString() : "";
                if (!isValidNick(name) || getBot(name) != null) continue;
                BotSession session = new BotSession(name);
                session.setServerAddress(entry.has("ip") && !entry.get("ip").isJsonNull() ? entry.get("ip").getAsString() : "");
                session.setPassword(entry.has("password") && !entry.get("password").isJsonNull() ? entry.get("password").getAsString() : "");
                session.setAutoReconnect(entry.has("autoReconnect") && !entry.get("autoReconnect").isJsonNull()
                        && entry.get("autoReconnect").getAsBoolean());
                sessions.put(name, session);
            }
        } catch (Exception ignored) {
        }
    }
}
