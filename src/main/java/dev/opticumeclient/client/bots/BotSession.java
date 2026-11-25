package dev.opticumeclient.client.bots;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.SectionPos;
import net.minecraft.network.Connection;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.PacketProcessor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundClientTickEndPacket;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public class BotSession {
    private static final int CHAT_HISTORY = 60;

    private final String name;
    private volatile String serverAddress;
    private String password = "";
    private boolean autoReconnect;
    private volatile SessionState state;

    private volatile Connection connection;
    private ClientPacketListener networkHandler;
    private LocalPlayer player;
    private ClientLevel world;
    private MultiPlayerGameMode interactionManager;
    private Screen screen;
    private PacketProcessor packetProcessor;

    private final Deque<String> chatLog = new ArrayDeque<>();

    private long connectedTime;
    private long nextRespawnAtMs;
    private volatile String disconnectReason;
    private String actionBar;
    private long actionBarAtMs;

    private String pendingAuthCommand;
    private long pendingAuthAtMs;
    private boolean registerSent;
    private boolean loginSent;

    public BotSession(String name) {
        this.name = name;
        this.state = SessionState.DISCONNECTED;
    }

    public String getName() {
        return name;
    }

    public String getServerAddress() {
        return serverAddress;
    }

    public void setServerAddress(String serverAddress) {
        this.serverAddress = serverAddress;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public boolean isAutoReconnect() {
        return autoReconnect;
    }

    public void setAutoReconnect(boolean autoReconnect) {
        this.autoReconnect = autoReconnect;
    }

    public SessionState getState() {
        return state;
    }

    public void setState(SessionState state) {
        this.state = state;
        if (state == SessionState.CONNECTED) {
            this.connectedTime = System.currentTimeMillis();
        }
    }

    public Connection getConnection() {
        return connection;
    }

    public void setConnection(Connection connection) {
        this.connection = connection;
    }

    public ClientPacketListener getNetworkHandler() {
        return networkHandler;
    }

    public void setNetworkHandler(ClientPacketListener networkHandler) {
        this.networkHandler = networkHandler;
    }

    public LocalPlayer getPlayer() {
        return player;
    }

    public void setPlayer(LocalPlayer player) {
        this.player = player;
    }

    public ClientLevel getWorld() {
        return world;
    }

    public void setWorld(ClientLevel world) {
        this.world = world;
    }

    public MultiPlayerGameMode getInteractionManager() {
        return interactionManager;
    }

    public void setInteractionManager(MultiPlayerGameMode interactionManager) {
        this.interactionManager = interactionManager;
    }

    public Screen getScreen() {
        return screen;
    }

    public void setScreen(Screen screen) {
        this.screen = screen;
    }

    public PacketProcessor getPacketProcessor() {
        return packetProcessor;
    }

    public void setPacketProcessor(PacketProcessor packetProcessor) {
        this.packetProcessor = packetProcessor;
    }

    public String getDisconnectReason() {
        return disconnectReason;
    }

    public void setDisconnectReason(String disconnectReason) {
        this.disconnectReason = disconnectReason;
    }

    public String getActionBar() {
        return actionBar;
    }

    public long getActionBarAtMs() {
        return actionBarAtMs;
    }

    public String getPendingAuthCommand() {
        return pendingAuthCommand;
    }

    public void setPendingAuthCommand(String pendingAuthCommand) {
        this.pendingAuthCommand = pendingAuthCommand;
    }

    public long getPendingAuthAtMs() {
        return pendingAuthAtMs;
    }

    public void setPendingAuthAtMs(long pendingAuthAtMs) {
        this.pendingAuthAtMs = pendingAuthAtMs;
    }

    public boolean isRegisterSent() {
        return registerSent;
    }

    public void setRegisterSent(boolean registerSent) {
        this.registerSent = registerSent;
    }

    public boolean isLoginSent() {
        return loginSent;
    }

    public void setLoginSent(boolean loginSent) {
        this.loginSent = loginSent;
    }

    public void connect(String serverIp) {
        if (this.state == SessionState.CONNECTED || this.state == SessionState.CONNECTING) {
            return;
        }
        this.serverAddress = serverIp;
        this.state = SessionState.CONNECTING;
        this.disconnectReason = null;
        this.actionBar = null;
        this.pendingAuthCommand = null;
        this.registerSent = false;
        this.loginSent = false;
        this.packetProcessor = new PacketProcessor(Minecraft.getInstance().getRunningThread());
        BotConnectionUtil.connectBot(this, serverIp);
    }

    public void disconnect() {
        if (this.state == SessionState.DISCONNECTED) {
            return;
        }
        Connection current = this.connection;
        if (current != null && current.isConnected()) {
            current.disconnect(Component.literal("Disconnected"));
        }
        cleanup();
        this.state = SessionState.DISCONNECTED;
    }

    void bindMain(Connection connection, ClientPacketListener handler) {
        this.connection = connection;
        this.networkHandler = handler;
        this.packetProcessor = new PacketProcessor(Minecraft.getInstance().getRunningThread());
        this.setState(SessionState.CONNECTED);
    }

    public void tick() {
        Connection current = this.connection;
        if (current == null) {
            return;
        }

        if (!current.isConnected()) {
            if (this.disconnectReason == null) {
                DisconnectionDetails details = current.getDisconnectionDetails();
                if (details != null && details.reason() != null) {
                    this.disconnectReason = details.reason().getString();
                }
            }
            BotSessionManager.getInstance().handleBotDisconnect(this);
            return;
        }

        if (!BotContext.enter(this)) {
            return;
        }
        try {
            if (this.packetProcessor != null) {
                this.packetProcessor.processQueuedPackets();
            }
            Minecraft mc = Minecraft.getInstance();
            if (mc.gameMode != null && mc.player != null && mc.level != null) {
                tickWorld(mc);
            } else if (current.isConnected()) {
                current.tick();
            }
        } catch (Throwable t) {
            this.disconnectReason = "Ошибка: " + t.getMessage();
            current.disconnect(Component.literal("Bot crashed"));
        } finally {
            BotContext.exit(this);
        }
    }

    private void tickWorld(Minecraft mc) {
        mc.level.tickRateManager().tick();

        if (BotContext.isBackgroundContextActive() && mc.player.input instanceof KeyboardInput) {
            mc.player.input = new ClientInput();
        }

        Runnable sectionReady = mc.player.connection.getPlayerCompiledSectionCallback();
        if (sectionReady != null && (mc.level.isOutsideBuildHeight(mc.player.getBlockY())
                || mc.player.isSpectator()
                || !mc.player.isAlive()
                || mc.level.hasChunk(
                        SectionPos.blockToSectionCoord(mc.player.getBlockX()),
                        SectionPos.blockToSectionCoord(mc.player.getBlockZ())))) {
            sectionReady.run();
        }

        mc.gameMode.tick();
        if (mc.level == null || mc.player == null || !this.connection.isConnected()) {
            return;
        }

        if (mc.player.isDeadOrDying() && this != BotSessionManager.getInstance().getMainSession()) {
            long now = System.currentTimeMillis();
            if (now >= this.nextRespawnAtMs) {
                this.nextRespawnAtMs = now + 1500L;
                mc.player.respawn();
            }
        }

        mc.level.tickEntities();
        mc.level.tickBlockEntities();
        if (!mc.player.isRemoved()) {
            mc.player.sendChanges();
        }
        mc.level.tick(() -> true);
        this.connection.send(ServerboundClientTickEndPacket.INSTANCE);
    }

    public void addChatLine(String line) {
        if (line == null || line.isBlank()) {
            return;
        }
        synchronized (this.chatLog) {
            this.chatLog.addLast(line);
            while (this.chatLog.size() > CHAT_HISTORY) {
                this.chatLog.removeFirst();
            }
        }
    }

    public List<String> getRecentChat(int count) {
        synchronized (this.chatLog) {
            List<String> all = new ArrayList<>(this.chatLog);
            return all.subList(Math.max(0, all.size() - count), all.size());
        }
    }

    public void setActionBar(String text) {
        this.actionBar = text;
        this.actionBarAtMs = System.currentTimeMillis();
    }

    public boolean isActive() {
        Connection current = this.connection;
        return this.state == SessionState.CONNECTED && current != null && current.isConnected();
    }

    public boolean isPlayable() {
        return isActive() && this.world != null && this.player != null && this.interactionManager != null;
    }

    public void cleanup() {
        if (this.packetProcessor != null) {
            this.packetProcessor.close();
            this.packetProcessor = null;
        }
        this.player = null;
        this.world = null;
        this.interactionManager = null;
        this.networkHandler = null;
        this.connection = null;
        this.screen = null;
    }

    public long getUptime() {
        if (state != SessionState.CONNECTED) {
            return 0L;
        }
        return System.currentTimeMillis() - connectedTime;
    }
}
