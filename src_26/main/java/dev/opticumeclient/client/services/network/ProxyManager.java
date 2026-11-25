package dev.opticumeclient.client.services.network;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.jna.platform.win32.Crypt32Util;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.ssl.SslHandshakeCompletionEvent;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.ScheduledFuture;
import net.minecraft.client.Minecraft;

import javax.net.ssl.SSLParameters;
import java.io.File;
import java.io.Reader;
import java.net.InetSocketAddress;
import java.nio.channels.ClosedChannelException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Pure-Netty SOCKS4 / SOCKS5 / HTTP CONNECT proxy manager integrated directly into Minecraft's
 * Connection pipeline (supporting both main player connections and background BotSessionManager bots).
 */
public final class ProxyManager {
    public enum ProxyType {
        SOCKS5, SOCKS4, HTTP
    }

    public record ProxyEntry(String name, ProxyType type, String host, int port, String username, String password, boolean tls) {
        public ProxyEntry(String name, ProxyType type, String host, int port, String username, String password) {
            this(name, type, host, port, username, password, false);
        }

        public String label() {
            String prefix = (name != null && !name.isBlank() ? name + " · " : "");
            String tlsTag = tls ? "TLS " : "";
            return prefix + tlsTag + type.name() + " " + host + ":" + port;
        }
    }

    public record PendingTunnel(ProxyEntry proxy, InetSocketAddress target, InetSocketAddress proxyAddress) {
    }

    public interface IProxyConnection {
        void opticume$setPendingTunnel(PendingTunnel tunnel);

        PendingTunnel opticume$consumePendingTunnel();
    }

    private static final ProxyManager INSTANCE = new ProxyManager();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final long HANDSHAKE_TIMEOUT_MS = 10_000L;
    private static final ThreadLocal<InetSocketAddress> SAME_THREAD_PROXY_ADDR = new ThreadLocal<>();

    private final List<ProxyEntry> proxies = new ArrayList<>();
    private final Map<ProxyEntry, String> preservedEncryptedBlobs = new IdentityHashMap<>();
    private final Set<ProxyEntry> missingSessionPasswords = Collections.newSetFromMap(new IdentityHashMap<>());
    private ProxyEntry activeProxy = null;
    private boolean enabled = false;
    private File storeFile;
    private boolean loaded = false;
    private boolean loadFailed = false;
    private boolean plaintextMigrationFailed = false;
    private String loadError = "";

    private ProxyManager() {
    }

    public static ProxyManager getInstance() {
        return INSTANCE;
    }

    public synchronized void ensureLoaded() {
        if (loaded) {
            if (plaintextMigrationFailed && !loadFailed && save()) {
                plaintextMigrationFailed = false;
            }
            return;
        }
        loaded = true;
        Minecraft mc = Minecraft.getInstance();
        File gameDir = (mc != null && mc.gameDirectory != null) ? mc.gameDirectory : new File(".");
        File dir = new File(gameDir, "opticumeclient");
        if (!dir.exists()) dir.mkdirs();
        storeFile = new File(dir, "proxies.json");
        load();
    }

    public synchronized boolean hasLoadFailure() {
        ensureLoaded();
        return loadFailed;
    }

    public synchronized boolean hasPlaintextMigrationFailure() {
        ensureLoaded();
        return plaintextMigrationFailed;
    }

    public synchronized boolean retryPlaintextMigration() {
        ensureLoaded();
        if (!plaintextMigrationFailed) return true;
        if (loadFailed) return false;
        if (save()) {
            plaintextMigrationFailed = false;
            return true;
        }
        return false;
    }

    public synchronized String getLoadError() {
        ensureLoaded();
        return loadError;
    }

    public synchronized boolean isEnabled() {
        ensureLoaded();
        return enabled && activeProxy != null;
    }

    public synchronized boolean setEnabled(boolean enabled) {
        ensureLoaded();
        if (loadFailed) {
            return false;
        }
        if (enabled && activeProxy == null) {
            return false;
        }
        boolean prev = this.enabled;
        this.enabled = enabled;
        if (!save()) {
            this.enabled = prev;
            return false;
        }
        return true;
    }

    public synchronized boolean isActiveMissingSessionPassword() {
        ensureLoaded();
        return activeProxy != null && missingSessionPasswords.contains(activeProxy);
    }

    public synchronized boolean resetAfterLoadFailure() {
        ensureLoaded();
        boolean prevFailed = this.loadFailed;
        String prevError = this.loadError;
        boolean prevEnabled = this.enabled;
        List<ProxyEntry> prevProxies = new ArrayList<>(this.proxies);
        Map<ProxyEntry, String> prevBlobs = new IdentityHashMap<>(this.preservedEncryptedBlobs);
        Set<ProxyEntry> prevMissing = Collections.newSetFromMap(new IdentityHashMap<>());
        prevMissing.addAll(this.missingSessionPasswords);
        ProxyEntry prevActive = this.activeProxy;

        this.loadFailed = false;
        this.loadError = "";
        this.enabled = false;
        this.proxies.clear();
        this.preservedEncryptedBlobs.clear();
        this.missingSessionPasswords.clear();
        this.activeProxy = null;
        if (!save()) {
            this.loadFailed = prevFailed;
            this.loadError = prevError;
            this.enabled = prevEnabled;
            this.proxies.addAll(prevProxies);
            this.preservedEncryptedBlobs.putAll(prevBlobs);
            this.missingSessionPasswords.addAll(prevMissing);
            this.activeProxy = prevActive;
            return false;
        }
        return true;
    }

    public synchronized ProxyEntry getActiveProxy() {
        ensureLoaded();
        return activeProxy;
    }

    public synchronized boolean setActiveProxy(ProxyEntry entry) {
        ensureLoaded();
        if (loadFailed) return false;
        ProxyEntry prevActive = this.activeProxy;
        boolean prevEnabled = this.enabled;
        if (entry == null) {
            this.activeProxy = null;
            this.enabled = false;
        } else {
            int idx = identityIndexOf(proxies, entry);
            if (idx < 0) {
                return false;
            }
            this.activeProxy = proxies.get(idx);
        }
        if (!save()) {
            this.activeProxy = prevActive;
            this.enabled = prevEnabled;
            return false;
        }
        return true;
    }

    public synchronized List<ProxyEntry> getProxies() {
        ensureLoaded();
        return Collections.unmodifiableList(new ArrayList<>(proxies));
    }

    public synchronized boolean addProxy(ProxyEntry entry) {
        ensureLoaded();
        if (loadFailed) return false;
        if (entry == null || entry.host().isBlank() || entry.port() <= 0 || entry.port() > 65535) return false;
        List<ProxyEntry> prevProxies = new ArrayList<>(this.proxies);
        ProxyEntry prevActive = this.activeProxy;
        this.proxies.add(entry);
        if (this.activeProxy == null) {
            this.activeProxy = entry;
        }
        if (!save()) {
            this.proxies.clear();
            this.proxies.addAll(prevProxies);
            this.activeProxy = prevActive;
            return false;
        }
        return true;
    }

    public synchronized boolean removeProxy(int index) {
        ensureLoaded();
        if (loadFailed) return false;
        if (index < 0 || index >= proxies.size()) return false;
        List<ProxyEntry> prevProxies = new ArrayList<>(this.proxies);
        ProxyEntry prevActive = this.activeProxy;
        boolean prevEnabled = this.enabled;
        Map<ProxyEntry, String> prevBlobs = new IdentityHashMap<>(this.preservedEncryptedBlobs);
        Set<ProxyEntry> prevMissing = Collections.newSetFromMap(new IdentityHashMap<>());
        prevMissing.addAll(this.missingSessionPasswords);
        ProxyEntry removed = proxies.remove(index);
        preservedEncryptedBlobs.remove(removed);
        missingSessionPasswords.remove(removed);
        if (removed == activeProxy) {
            activeProxy = proxies.isEmpty() ? null : proxies.get(0);
            if (activeProxy == null) enabled = false;
        }
        if (!save()) {
            this.proxies.clear();
            this.proxies.addAll(prevProxies);
            this.activeProxy = prevActive;
            this.enabled = prevEnabled;
            this.preservedEncryptedBlobs.clear();
            this.preservedEncryptedBlobs.putAll(prevBlobs);
            this.missingSessionPasswords.clear();
            this.missingSessionPasswords.addAll(prevMissing);
            return false;
        }
        return true;
    }

    public PendingTunnel prepareTunnel(InetSocketAddress target) {
        SAME_THREAD_PROXY_ADDR.remove();
        ProxyEntry p;
        synchronized (this) {
            ensureLoaded();
            if (loadFailed) {
                throw new IllegalStateException("Proxy config failed to load (" + loadError + "); refusing direct connection");
            }
            if (enabled && activeProxy == null) {
                throw new IllegalStateException("Proxy is enabled with no valid endpoint; refusing direct connection");
            }
            if (!enabled || activeProxy == null || target == null) {
                return null;
            }
            if (preservedEncryptedBlobs.containsKey(activeProxy)) {
                throw new IllegalStateException("Active proxy password could not be decrypted for this Windows user; refusing connection");
            }
            if (missingSessionPasswords.contains(activeProxy)) {
                throw new IllegalStateException("Active proxy requires re-entering its session-only password before connecting");
            }
            p = activeProxy;
        }
        InetSocketAddress proxyAddr = new InetSocketAddress(p.host(), p.port());
        SAME_THREAD_PROXY_ADDR.set(proxyAddr);
        return new PendingTunnel(p, target, proxyAddr);
    }

    public InetSocketAddress consumeRewriteTarget(InetSocketAddress original) {
        InetSocketAddress rewritten = SAME_THREAD_PROXY_ADDR.get();
        SAME_THREAD_PROXY_ADDR.remove();
        return rewritten != null ? rewritten : original;
    }

    public void installPipeline(ChannelPipeline pipeline, PendingTunnel tunnel) {
        if (pipeline == null || tunnel == null) return;
        ProxyEntry proxy = tunnel.proxy();
        pipeline.addFirst("opticume_proxy", createHandler(proxy, tunnel.target()));
        if (proxy.tls()) {
            try {
                SslContext sslCtx = SslContextBuilder.forClient().build();
                SslHandler sslHandler = sslCtx.newHandler(pipeline.channel().alloc(), proxy.host(), proxy.port());
                SSLParameters sslParams = sslHandler.engine().getSSLParameters();
                sslParams.setEndpointIdentificationAlgorithm("HTTPS");
                sslHandler.engine().setSSLParameters(sslParams);
                // Added with addFirst after opticume_proxy so SslHandler sits between the socket and NettyProxyHandler
                pipeline.addFirst("opticume_proxy_tls", sslHandler);
            } catch (Throwable t) {
                throw new IllegalStateException("Failed to initialize TLS for proxy connection", t);
            }
        }
    }

    public static ChannelHandler createHandler(ProxyEntry proxy, InetSocketAddress target) {
        return new NettyProxyHandler(proxy, target);
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    public static boolean supportsProtectedSecretStorage() {
        return isWindows();
    }

    public synchronized boolean hasSessionOnlyPasswords() {
        ensureLoaded();
        if (supportsProtectedSecretStorage()) return false;
        for (ProxyEntry p : proxies) {
            if (p.password() != null && !p.password().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static String protectSecret(String plain) {
        if (plain == null || plain.isEmpty() || !isWindows()) return "";
        try {
            byte[] enc = Crypt32Util.cryptProtectData(plain.getBytes(StandardCharsets.UTF_8));
            return "dpapi:" + Base64.getEncoder().encodeToString(enc);
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static String unprotectSecret(String stored) {
        if (stored == null || stored.isBlank() || !stored.startsWith("dpapi:") || !isWindows()) return "";
        try {
            byte[] enc = Base64.getDecoder().decode(stored.substring("dpapi:".length()));
            byte[] dec = Crypt32Util.cryptUnprotectData(enc);
            return new String(dec, StandardCharsets.UTF_8);
        } catch (Throwable ignored) {
            return "";
        }
    }

    private void load() {
        if (storeFile == null) {
            this.loadFailed = true;
            this.loadError = "Proxy config path is null";
            return;
        }
        try {
            Files.readAttributes(storeFile.toPath(), BasicFileAttributes.class);
        } catch (NoSuchFileException missing) {
            this.loadFailed = false;
            this.loadError = "";
            return;
        } catch (Throwable inspectErr) {
            this.loadFailed = true;
            this.loadError = "Cannot inspect proxies.json (" + inspectErr.getClass().getSimpleName() + ")";
            System.err.println("[OpticumeClient] " + this.loadError + ": " + inspectErr.getMessage());
            return;
        }
        boolean parsedEnabled;
        int activeIdx;
        List<ProxyEntry> parsedList = new ArrayList<>();
        Map<ProxyEntry, String> parsedPreserved = new IdentityHashMap<>();
        Set<ProxyEntry> parsedMissingPass = Collections.newSetFromMap(new IdentityHashMap<>());
        ProxyEntry resolvedActive = null;
        boolean selectedRowInvalid = false;
        boolean migratedPlaintext = false;
        int rawCount = 0;
        try (Reader r = Files.newBufferedReader(storeFile.toPath(), StandardCharsets.UTF_8)) {
            JsonElement parsed = JsonParser.parseReader(r);
            if (!parsed.isJsonObject()) {
                this.loadFailed = true;
                this.loadError = "Root JSON is not an object";
                System.err.println("[OpticumeClient] Failed to load proxies.json: " + this.loadError);
                return;
            }
            JsonObject root = parsed.getAsJsonObject();
            parsedEnabled = false;
            if (root.has("enabled")) {
                JsonElement enEl = root.get("enabled");
                if (!enEl.isJsonPrimitive() || !enEl.getAsJsonPrimitive().isBoolean()) {
                    loadFailed = true;
                    loadError = "Field 'enabled' must be a boolean";
                    return;
                }
                parsedEnabled = enEl.getAsBoolean();
            }
            activeIdx = -1;
            if (root.has("activeIndex")) {
                JsonElement idxEl = root.get("activeIndex");
                if (!idxEl.isJsonPrimitive() || !idxEl.getAsJsonPrimitive().isNumber()) {
                    loadFailed = true;
                    loadError = "Field 'activeIndex' must be an integer";
                    return;
                }
                double d = idxEl.getAsDouble();
                if (!Double.isFinite(d) || d != Math.rint(d) || d < Integer.MIN_VALUE || d > Integer.MAX_VALUE) {
                    loadFailed = true;
                    loadError = "Field 'activeIndex' must be an integer";
                    return;
                }
                activeIdx = idxEl.getAsInt();
            }
            if (root.has("proxies") && root.get("proxies").isJsonArray()) {
                JsonArray arr = root.getAsJsonArray("proxies");
                rawCount = arr.size();
                for (int idx = 0; idx < arr.size(); idx++) {
                    JsonElement el = arr.get(idx);
                    if (!el.isJsonObject()) {
                        if (idx == activeIdx) selectedRowInvalid = true;
                        continue;
                    }
                    JsonObject obj = el.getAsJsonObject();
                    String name = obj.has("name") ? obj.get("name").getAsString() : "";
                    ProxyType type = ProxyType.valueOf(obj.has("type") ? obj.get("type").getAsString() : "SOCKS5");
                    String host = obj.has("host") ? obj.get("host").getAsString() : "";
                    int port = obj.has("port") ? obj.get("port").getAsInt() : 1080;
                    String user = obj.has("username") ? obj.get("username").getAsString() : "";
                    boolean tls = obj.has("tls") && obj.get("tls").getAsBoolean();
                    boolean requiresPass = obj.has("requiresPassword") && obj.get("requiresPassword").getAsBoolean();
                    String rawEnc = obj.has("encryptedPassword") ? obj.get("encryptedPassword").getAsString() : "";
                    String pass = rawEnc.isBlank() ? "" : unprotectSecret(rawEnc);
                    String preservedBlob = (!rawEnc.isBlank() && pass.isEmpty()) ? rawEnc : null;
                    if (obj.has("password")) {
                        String legacy = obj.get("password").getAsString();
                        if (pass.isEmpty() && !legacy.isEmpty()) {
                            pass = legacy;
                            preservedBlob = null;
                        }
                        migratedPlaintext = true;
                    }
                    if (!host.isBlank() && port > 0 && port <= 65535) {
                        ProxyEntry entry = new ProxyEntry(name, type, host, port, user, pass, tls);
                        parsedList.add(entry);
                        if (preservedBlob != null) {
                            parsedPreserved.put(entry, preservedBlob);
                        } else if (requiresPass && pass.isEmpty()) {
                            parsedMissingPass.add(entry);
                        }
                        if (idx == activeIdx) {
                            resolvedActive = entry;
                        }
                    } else if (idx == activeIdx) {
                        selectedRowInvalid = true;
                    }
                }
            }
        } catch (Throwable t) {
            this.loadFailed = true;
            this.loadError = (t.getMessage() != null && !t.getMessage().isBlank()) ? t.getMessage() : t.getClass().getSimpleName();
            System.err.println("[OpticumeClient] Failed to load proxies.json: " + this.loadError);
            return;
        }
        if (activeIdx != -1 && (activeIdx < 0 || activeIdx >= rawCount || selectedRowInvalid || resolvedActive == null)) {
            this.loadFailed = true;
            this.loadError = "Selected active proxy #" + activeIdx + " is invalid or out of range";
            System.err.println("[OpticumeClient] Failed to load proxies.json: " + this.loadError);
            return;
        }
        if (parsedEnabled && parsedList.isEmpty()) {
            this.loadFailed = true;
            this.loadError = "Proxy is enabled but proxies.json has no valid endpoints";
            System.err.println("[OpticumeClient] Failed to load proxies.json: " + this.loadError);
            return;
        }
        this.loadFailed = false;
        this.loadError = "";
        this.enabled = parsedEnabled;
        proxies.clear();
        proxies.addAll(parsedList);
        preservedEncryptedBlobs.clear();
        preservedEncryptedBlobs.putAll(parsedPreserved);
        missingSessionPasswords.clear();
        missingSessionPasswords.addAll(parsedMissingPass);
        if (resolvedActive != null) {
            activeProxy = resolvedActive;
        } else {
            activeProxy = proxies.isEmpty() ? null : proxies.get(0);
        }
        if (migratedPlaintext) {
            plaintextMigrationFailed = !save();
            if (plaintextMigrationFailed) {
                System.err.println("[OpticumeClient] Failed to persist migration of legacy plaintext passwords in proxies.json; plaintext credentials may remain on disk until write succeeds.");
            }
        } else {
            plaintextMigrationFailed = false;
        }
    }

    private static int identityIndexOf(List<ProxyEntry> list, ProxyEntry target) {
        if (target == null) return -1;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i) == target) {
                return i;
            }
        }
        return -1;
    }

    private synchronized boolean save() {
        if (storeFile == null || loadFailed) return false;
        try {
            JsonObject root = new JsonObject();
            root.addProperty("enabled", enabled);
            root.addProperty("activeIndex", identityIndexOf(proxies, activeProxy));
            JsonArray arr = new JsonArray();
            for (ProxyEntry p : proxies) {
                JsonObject o = new JsonObject();
                o.addProperty("name", p.name());
                o.addProperty("type", p.type().name());
                o.addProperty("host", p.host());
                o.addProperty("port", p.port());
                o.addProperty("username", p.username());
                o.addProperty("tls", p.tls());
                String pass = p.password() == null ? "" : p.password();
                if (!pass.isEmpty()) {
                    String protectedPass = protectSecret(pass);
                    if (!protectedPass.isEmpty()) {
                        o.addProperty("encryptedPassword", protectedPass);
                    } else if (isWindows()) {
                        System.err.println("[OpticumeClient] Failed to protect proxy password via Windows DPAPI; aborting proxies.json save.");
                        return false;
                    } else {
                        o.addProperty("requiresPassword", true);
                    }
                } else if (preservedEncryptedBlobs.containsKey(p)) {
                    o.addProperty("encryptedPassword", preservedEncryptedBlobs.get(p));
                } else if (missingSessionPasswords.contains(p)) {
                    o.addProperty("requiresPassword", true);
                }
                arr.add(o);
            }
            root.add("proxies", arr);
            dev.opticumeclient.client.util.AtomicFileWriter.writeUtf8(storeFile, w -> GSON.toJson(root, w));
            plaintextMigrationFailed = false;
            return true;
        } catch (Throwable t) {
            System.err.println("[OpticumeClient] Failed to save proxies.json: " + t.getMessage());
            return false;
        }
    }

    /**
     * Minimal Netty 4 duplex tunnel handler supporting SOCKS4a, SOCKS5 (with optional user/pass), and HTTP CONNECT.
     */
    private static final class NettyProxyHandler extends ChannelDuplexHandler {
        private enum State {INIT, WAIT_AUTH, WAIT_CONNECT, CONNECTED, FAILED}

        private record PendingWrite(Object msg, ChannelPromise promise) {
        }

        private final ProxyEntry proxy;
        private final InetSocketAddress target;
        private final Deque<PendingWrite> pendingWrites = new ArrayDeque<>();
        private final ByteBuf rxAccum = Unpooled.buffer();
        private static final int MAX_HTTP_HEADER_BYTES = 16 * 1024;
        private ScheduledFuture<?> handshakeTimeout;
        private State state = State.INIT;
        private boolean handshakeStarted = false;
        private int httpScanOffset = 0;
        private boolean rxReleased = false;

        NettyProxyHandler(ProxyEntry proxy, InetSocketAddress target) {
            this.proxy = proxy;
            this.target = target;
        }

        private boolean hasAuthenticatedTls(ChannelHandlerContext ctx) {
            if (!proxy.tls()) return false;
            SslHandler ssl = ctx.pipeline().get(SslHandler.class);
            return ssl != null && ssl.handshakeFuture().isDone() && ssl.handshakeFuture().isSuccess();
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            handshakeTimeout = ctx.executor().schedule(() -> {
                if (state != State.CONNECTED && state != State.FAILED) {
                    failTunnel(ctx, new TimeoutException("Proxy handshake timed out after " + HANDSHAKE_TIMEOUT_MS + " ms"));
                }
            }, HANDSHAKE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (!proxy.tls()) {
                startProxyHandshake(ctx);
            }
        }

        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
            if (evt instanceof SslHandshakeCompletionEvent tlsEvt) {
                if (tlsEvt.isSuccess()) {
                    startProxyHandshake(ctx);
                } else {
                    failTunnel(ctx, tlsEvt.cause() != null ? tlsEvt.cause() : new IllegalStateException("Proxy TLS handshake failed"));
                }
            }
            super.userEventTriggered(ctx, evt);
        }

        private void startProxyHandshake(ChannelHandlerContext ctx) {
            if (handshakeStarted || state == State.CONNECTED || state == State.FAILED) {
                return;
            }
            handshakeStarted = true;
            boolean hasCreds = !proxy.username().isBlank() || !proxy.password().isBlank();
            if (hasCreds && !hasAuthenticatedTls(ctx)) {
                failTunnel(ctx, new IllegalStateException("Refusing to send proxy credentials without authenticated TLS"));
                return;
            }
            if (proxy.type() == ProxyType.SOCKS5) {
                ByteBuf greeting = ctx.alloc().buffer(4);
                greeting.writeByte(0x05);
                if (hasCreds) {
                    greeting.writeByte(2).writeByte(0x00).writeByte(0x02);
                } else {
                    greeting.writeByte(1).writeByte(0x00);
                }
                state = State.INIT;
                ctx.writeAndFlush(greeting);
            } else if (proxy.type() == ProxyType.SOCKS4) {
                byte[] hostBytes = target.getHostString().getBytes(StandardCharsets.US_ASCII);
                byte[] userBytes = hasCreds ? proxy.username().getBytes(StandardCharsets.US_ASCII) : new byte[0];
                ByteBuf req = ctx.alloc().buffer(10 + userBytes.length + hostBytes.length);
                req.writeByte(0x04).writeByte(0x01).writeShort(target.getPort());
                req.writeByte(0).writeByte(0).writeByte(0).writeByte(1); // SOCKS4a unresolved IP marker 0.0.0.1
                req.writeBytes(userBytes).writeByte(0);
                req.writeBytes(hostBytes).writeByte(0);
                state = State.WAIT_CONNECT;
                ctx.writeAndFlush(req);
            } else {
                // HTTP CONNECT
                String hostPort = target.getHostString() + ":" + target.getPort();
                StringBuilder sb = new StringBuilder();
                sb.append("CONNECT ").append(hostPort).append(" HTTP/1.1\r\n");
                sb.append("Host: ").append(hostPort).append("\r\n");
                if (hasCreds && hasAuthenticatedTls(ctx)) {
                    String token = Base64.getEncoder().encodeToString(
                            (proxy.username() + ":" + proxy.password()).getBytes(StandardCharsets.UTF_8));
                    sb.append("Proxy-Authorization: Basic ").append(token).append("\r\n");
                }
                sb.append("\r\n");
                state = State.WAIT_CONNECT;
                ctx.writeAndFlush(Unpooled.copiedBuffer(sb.toString(), StandardCharsets.US_ASCII));
            }
        }

        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
            if (state == State.CONNECTED) {
                super.write(ctx, msg, promise);
            } else if (state == State.FAILED) {
                ReferenceCountUtil.safeRelease(msg);
                promise.tryFailure(new IllegalStateException("Proxy tunnel failed"));
            } else {
                pendingWrites.addLast(new PendingWrite(msg, promise));
            }
        }

        @Override
        public void flush(ChannelHandlerContext ctx) throws Exception {
            if (state == State.CONNECTED) {
                super.flush(ctx);
            }
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            if (state == State.CONNECTED) {
                super.channelRead(ctx, msg);
                return;
            }
            if (!(msg instanceof ByteBuf buf)) {
                super.channelRead(ctx, msg);
                return;
            }
            rxAccum.writeBytes(buf);
            buf.release();

            try {
                if (proxy.type() == ProxyType.SOCKS5) {
                    handleSocks5Read(ctx);
                } else if (proxy.type() == ProxyType.SOCKS4) {
                    handleSocks4Read(ctx);
                } else {
                    handleHttpRead(ctx);
                }
            } catch (Throwable t) {
                failTunnel(ctx, t);
            }
        }

        private void handleSocks5Read(ChannelHandlerContext ctx) throws Exception {
            if (state == State.INIT && rxAccum.readableBytes() >= 2) {
                byte ver = rxAccum.readByte();
                byte method = rxAccum.readByte();
                if (ver != 0x05 || (method & 0xFF) == 0xFF) {
                    failTunnel(ctx, new IllegalStateException("SOCKS5 method rejected"));
                    return;
                }
                if (method == 0x02) {
                    if (!hasAuthenticatedTls(ctx)) {
                        failTunnel(ctx, new IllegalStateException("Refusing SOCKS5 credentials without authenticated TLS"));
                        return;
                    }
                    byte[] u = proxy.username().getBytes(StandardCharsets.UTF_8);
                    byte[] p = proxy.password().getBytes(StandardCharsets.UTF_8);
                    ByteBuf auth = ctx.alloc().buffer(3 + u.length + p.length);
                    auth.writeByte(0x01).writeByte(u.length).writeBytes(u).writeByte(p.length).writeBytes(p);
                    state = State.WAIT_AUTH;
                    ctx.writeAndFlush(auth);
                } else {
                    sendSocks5Connect(ctx);
                }
            } else if (state == State.WAIT_AUTH && rxAccum.readableBytes() >= 2) {
                rxAccum.readByte();
                byte status = rxAccum.readByte();
                if (status != 0x00) {
                    failTunnel(ctx, new IllegalStateException("SOCKS5 auth failed"));
                    return;
                }
                sendSocks5Connect(ctx);
            } else if (state == State.WAIT_CONNECT && rxAccum.readableBytes() >= 5) {
                rxAccum.markReaderIndex();
                rxAccum.readByte(); // ver
                byte rep = rxAccum.readByte();
                rxAccum.readByte(); // rsv
                byte atyp = rxAccum.readByte();
                int addrLen = switch (atyp) {
                    case 0x01 -> 4;
                    case 0x04 -> 16;
                    case 0x03 -> (rxAccum.readableBytes() >= 1 ? (rxAccum.readUnsignedByte()) : -1);
                    default -> -2;
                };
                if (addrLen == -1 || rxAccum.readableBytes() < addrLen + 2) {
                    rxAccum.resetReaderIndex();
                    return;
                }
                if (addrLen < 0 || rep != 0x00) {
                    failTunnel(ctx, new IllegalStateException("SOCKS5 connect error: " + rep));
                    return;
                }
                rxAccum.skipBytes(addrLen + 2);
                finishConnected(ctx);
            }
        }

        private void sendSocks5Connect(ChannelHandlerContext ctx) {
            byte[] host = target.getHostString().getBytes(StandardCharsets.US_ASCII);
            ByteBuf req = ctx.alloc().buffer(7 + host.length);
            req.writeByte(0x05).writeByte(0x01).writeByte(0x00).writeByte(0x03);
            req.writeByte(host.length).writeBytes(host);
            req.writeShort(target.getPort());
            state = State.WAIT_CONNECT;
            ctx.writeAndFlush(req);
        }

        private void handleSocks4Read(ChannelHandlerContext ctx) throws Exception {
            if (rxAccum.readableBytes() < 8) return;
            rxAccum.readByte();
            byte cd = rxAccum.readByte();
            rxAccum.skipBytes(6);
            if (cd != 90) {
                failTunnel(ctx, new IllegalStateException("SOCKS4 rejected: " + cd));
                return;
            }
            finishConnected(ctx);
        }

        private void handleHttpRead(ChannelHandlerContext ctx) throws Exception {
            int readable = rxAccum.readableBytes();
            if (readable > MAX_HTTP_HEADER_BYTES) {
                failTunnel(ctx, new IllegalStateException("HTTP CONNECT response header exceeded " + MAX_HTTP_HEADER_BYTES + " bytes"));
                return;
            }
            int rIdx = rxAccum.readerIndex();
            int scanStart = Math.max(rIdx, rIdx + httpScanOffset - 3);
            int limit = rIdx + readable - 4;
            int endAbs = -1;
            for (int pos = scanStart; pos <= limit; pos++) {
                if (rxAccum.getByte(pos) == '\r'
                        && rxAccum.getByte(pos + 1) == '\n'
                        && rxAccum.getByte(pos + 2) == '\r'
                        && rxAccum.getByte(pos + 3) == '\n') {
                    endAbs = pos;
                    break;
                }
            }
            if (endAbs < 0) {
                httpScanOffset = Math.max(0, readable - 3);
                return;
            }
            int headerLen = endAbs - rIdx;
            byte[] headerBytes = new byte[headerLen];
            rxAccum.readBytes(headerBytes);
            rxAccum.skipBytes(4); // consume \r\n\r\n
            String header = new String(headerBytes, StandardCharsets.US_ASCII);
            if (!header.startsWith("HTTP/1.1 200") && !header.startsWith("HTTP/1.0 200")) {
                failTunnel(ctx, new IllegalStateException("HTTP CONNECT failed: " + header.lines().findFirst().orElse("")));
                return;
            }
            finishConnected(ctx);
        }

        private void finishConnected(ChannelHandlerContext ctx) throws Exception {
            state = State.CONNECTED;
            cancelTimeout();
            super.channelActive(ctx);
            while (!pendingWrites.isEmpty()) {
                PendingWrite pw = pendingWrites.removeFirst();
                ctx.write(pw.msg(), pw.promise());
            }
            ctx.flush();
            if (rxAccum.isReadable()) {
                ByteBuf leftover = rxAccum.readRetainedSlice(rxAccum.readableBytes());
                super.channelRead(ctx, leftover);
            }
            releaseRxOnce();
            ctx.pipeline().remove(this);
        }

        private void releaseRxOnce() {
            if (!rxReleased) {
                rxReleased = true;
                if (rxAccum.refCnt() > 0) {
                    rxAccum.release();
                }
            }
        }

        private void cancelTimeout() {
            if (handshakeTimeout != null) {
                handshakeTimeout.cancel(false);
                handshakeTimeout = null;
            }
        }

        private void cleanupPendingWritesAndRx(Throwable cause) {
            cancelTimeout();
            while (!pendingWrites.isEmpty()) {
                PendingWrite pw = pendingWrites.removeFirst();
                ReferenceCountUtil.safeRelease(pw.msg());
                pw.promise().tryFailure(cause);
            }
            releaseRxOnce();
        }

        private void failTunnel(ChannelHandlerContext ctx, Throwable cause) {
            if (state == State.FAILED || state == State.CONNECTED) return;
            state = State.FAILED;
            cleanupPendingWritesAndRx(cause);
            ctx.fireExceptionCaught(cause);
            ctx.close();
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            if (state != State.CONNECTED && state != State.FAILED) {
                state = State.FAILED;
                cleanupPendingWritesAndRx(new ClosedChannelException());
            } else {
                releaseRxOnce();
            }
            super.channelInactive(ctx);
        }
    }
}
