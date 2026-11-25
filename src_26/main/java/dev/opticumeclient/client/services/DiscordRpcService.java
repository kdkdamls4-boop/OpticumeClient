package dev.opticumeclient.client.services;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.jna.Native;
import com.sun.jna.Platform;
import com.sun.jna.Pointer;
import com.sun.jna.WString;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.win32.StdCallLibrary;
import dev.opticumeclient.client.modules.Module;
import dev.opticumeclient.client.modules.ModuleManager;
import net.minecraft.client.Minecraft;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Windows Discord Rich Presence Named Pipe IPC client (\\.\pipe\discord-ipc-0..9)
 * using non-blocking Win32 {@code PeekNamedPipe} so writes never stall behind a pending synchronous read.
 */
public final class DiscordRpcService {
    private static final DiscordRpcService INSTANCE = new DiscordRpcService();
    private static final String CLIENT_ID = "1352248102759141418";

    private static final int GENERIC_READ = 0x80000000;
    private static final int GENERIC_WRITE = 0x40000000;
    private static final int OPEN_EXISTING = 3;
    private static final int SECURITY_SQOS_PRESENT = 0x00100000;
    private static final int SECURITY_ANONYMOUS = 0x00000000;
    private static final int PROCESS_QUERY_LIMITED_INFORMATION = 0x00001000;
    private static final long INVALID_HANDLE_VALUE_ADDR = -1L;

    private interface Kernel32Ext extends StdCallLibrary {
        Kernel32Ext INSTANCE = Native.load("kernel32", Kernel32Ext.class);

        Pointer CreateFileW(
                WString lpFileName,
                int dwDesiredAccess,
                int dwShareMode,
                Pointer lpSecurityAttributes,
                int dwCreationDisposition,
                int dwFlagsAndAttributes,
                Pointer hTemplateFile
        );

        boolean PeekNamedPipe(
                Pointer hNamedPipe,
                byte[] lpBuffer,
                int nBufferSize,
                IntByReference lpBytesRead,
                IntByReference lpTotalBytesAvail,
                IntByReference lpBytesLeftThisMessage
        );

        boolean ReadFile(
                Pointer hFile,
                byte[] lpBuffer,
                int nNumberOfBytesToRead,
                IntByReference lpNumberOfBytesRead,
                Pointer lpOverlapped
        );

        boolean WriteFile(
                Pointer hFile,
                byte[] lpBuffer,
                int nNumberOfBytesToWrite,
                IntByReference lpNumberOfBytesWritten,
                Pointer lpOverlapped
        );

        boolean GetNamedPipeServerProcessId(Pointer NamedPipe, IntByReference ServerProcessId);

        Pointer OpenProcess(int dwDesiredAccess, boolean bInheritHandle, int dwProcessId);

        boolean QueryFullProcessImageNameW(Pointer hProcess, int dwFlags, char[] lpExeName, IntByReference lpdwSize);

        boolean CloseHandle(Pointer hObject);
    }

    private final AtomicBoolean active = new AtomicBoolean(false);
    private AtomicBoolean currentWorkerStop;
    private Thread workerThread;
    private volatile PipeSession activeSession;
    private volatile boolean hideServerIp = false;
    private volatile boolean showModuleCount = true;
    private final long sessionStartSec = System.currentTimeMillis() / 1000L;

    private DiscordRpcService() {
    }

    public static DiscordRpcService getInstance() {
        return INSTANCE;
    }

    public void configure(boolean hideIp, boolean showModules) {
        this.hideServerIp = hideIp;
        this.showModuleCount = showModules;
    }

    public synchronized void start() {
        if (active.get()) return;
        String os = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
        if (!os.contains("win")) return;
        active.set(true);
        AtomicBoolean stopToken = new AtomicBoolean(false);
        currentWorkerStop = stopToken;
        workerThread = new Thread(() -> runLoop(stopToken), "opticume-discord-rpc");
        workerThread.setDaemon(true);
        workerThread.start();
    }

    public synchronized void stop() {
        active.set(false);
        if (currentWorkerStop != null) {
            currentWorkerStop.set(true);
            currentWorkerStop = null;
        }
        PipeSession session = activeSession;
        activeSession = null;
        if (session != null) {
            session.close();
        }
        if (workerThread != null) {
            workerThread.interrupt();
            workerThread = null;
        }
    }

    private void runLoop(AtomicBoolean stopToken) {
        int nonce = 1;
        while (!stopToken.get()) {
            PipeSession opened = null;
            try (PipeSession session = openSession()) {
                opened = session;
                if (session == null) {
                    Thread.sleep(10_000L);
                    continue;
                }
                synchronized (this) {
                    if (stopToken.get()) {
                        break;
                    }
                    activeSession = session;
                }
                JsonObject hello = new JsonObject();
                hello.addProperty("v", 1);
                hello.addProperty("client_id", CLIENT_ID);
                if (!session.writeFrame(0, hello.toString()) || !awaitReadyHandshake(session, 4_000L)) {
                    Thread.sleep(8_000L);
                    continue;
                }

                while (!stopToken.get() && !session.isClosed()) {
                    JsonObject payload = buildActivityPayload(nonce++);
                    if (!session.writeFrame(1, payload.toString()) || drainIncomingIfClose(session)) {
                        break;
                    }
                    Thread.sleep(5_000L);
                }
            } catch (InterruptedException ignored) {
                break;
            } catch (Throwable ignored) {
                try {
                    Thread.sleep(8_000L);
                } catch (InterruptedException ie) {
                    break;
                }
            } finally {
                if (opened != null) {
                    synchronized (this) {
                        if (activeSession == opened) {
                            activeSession = null;
                        }
                    }
                }
            }
        }
    }

    private PipeSession openSession() {
        if (!Platform.isWindows()) {
            return null;
        }
        Pointer handle = openPipe();
        if (handle == null) return null;
        return new PipeSession(handle);
    }

    private static Pointer openPipe() {
        for (int i = 0; i <= 9; i++) {
            try {
                Pointer h = Kernel32Ext.INSTANCE.CreateFileW(
                        new WString("\\\\.\\pipe\\discord-ipc-" + i),
                        GENERIC_READ | GENERIC_WRITE,
                        0,
                        null,
                        OPEN_EXISTING,
                        SECURITY_SQOS_PRESENT | SECURITY_ANONYMOUS,
                        null
                );
                if (h != null && Pointer.nativeValue(h) != 0L && Pointer.nativeValue(h) != INVALID_HANDLE_VALUE_ADDR) {
                    if (verifyDiscordPipeServer(h)) {
                        return h;
                    }
                    try {
                        Kernel32Ext.INSTANCE.CloseHandle(h);
                    } catch (Throwable ignored) {
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static boolean verifyDiscordPipeServer(Pointer pipeHandle) {
        IntByReference pidRef = new IntByReference();
        if (!Kernel32Ext.INSTANCE.GetNamedPipeServerProcessId(pipeHandle, pidRef)) {
            return false;
        }
        int pid = pidRef.getValue();
        if (pid <= 0) return false;

        Pointer hProc = Kernel32Ext.INSTANCE.OpenProcess(PROCESS_QUERY_LIMITED_INFORMATION, false, pid);
        if (hProc == null || Pointer.nativeValue(hProc) == 0L || Pointer.nativeValue(hProc) == INVALID_HANDLE_VALUE_ADDR) {
            return false;
        }
        try {
            char[] buf = new char[1024];
            IntByReference lenRef = new IntByReference(buf.length);
            if (!Kernel32Ext.INSTANCE.QueryFullProcessImageNameW(hProc, 0, buf, lenRef) || lenRef.getValue() <= 0) {
                return false;
            }
            String exePath = new String(buf, 0, lenRef.getValue());
            String fileName = new File(exePath).getName().toLowerCase(Locale.ROOT);
            if (!"discord.exe".equals(fileName)
                    && !"discordptb.exe".equals(fileName)
                    && !"discordcanary.exe".equals(fileName)
                    && !"discorddevelopment.exe".equals(fileName)) {
                return false;
            }
            if (!hasValidDiscordAuthenticode(exePath)) {
                return false;
            }
            IntByReference confirmPid = new IntByReference();
            return Kernel32Ext.INSTANCE.GetNamedPipeServerProcessId(pipeHandle, confirmPid)
                    && confirmPid.getValue() == pid;
        } finally {
            try {
                Kernel32Ext.INSTANCE.CloseHandle(hProc);
            } catch (Throwable ignored) {
            }
        }
    }

    private static boolean hasValidDiscordAuthenticode(String exePath) {
        Process proc = null;
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "powershell.exe",
                    "-NoProfile",
                    "-NonInteractive",
                    "-WindowStyle", "Hidden",
                    "-Command",
                    "$s = Get-AuthenticodeSignature -LiteralPath $env:OPTICUME_PIPE_EXE; "
                            + "if ($null -ne $s -and $s.Status -eq 'Valid' -and $null -ne $s.SignerCertificate "
                            + "-and $s.SignerCertificate.GetNameInfo([System.Security.Cryptography.X509Certificates.X509NameType]::SimpleName, $false) -eq 'Discord Inc.') "
                            + "{ 'VALID_DISCORD' }"
            );
            pb.environment().put("OPTICUME_PIPE_EXE", exePath);
            pb.redirectErrorStream(true);
            proc = pb.start();
            String output = "";
            try (BufferedReader br = new BufferedReader(new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    if ("VALID_DISCORD".equals(line.trim())) {
                        output = "VALID_DISCORD";
                    }
                }
            }
            if (!proc.waitFor(4, TimeUnit.SECONDS)) {
                proc.destroyForcibly();
                return false;
            }
            return proc.exitValue() == 0 && "VALID_DISCORD".equals(output);
        } catch (Throwable ignored) {
            return false;
        } finally {
            if (proc != null && proc.isAlive()) {
                proc.destroyForcibly();
            }
        }
    }

    private record IpcFrame(int opcode, String payload) {
    }

    private static final class PipeSession implements AutoCloseable {
        private static final int MAX_FRAME_BODY = 131_072;
        private final Pointer handle;
        private final AtomicBoolean closed = new AtomicBoolean(false);
        private final Object ioLock = new Object();
        private final ByteArrayOutputStream rxBuf = new ByteArrayOutputStream();
        private final byte[] readChunk = new byte[4096];

        PipeSession(Pointer handle) {
            this.handle = handle;
        }

        boolean writeFrame(int opcode, String json) {
            if (closed.get()) return false;
            byte[] data = json.getBytes(StandardCharsets.UTF_8);
            ByteBuffer buf = ByteBuffer.allocate(8 + data.length).order(ByteOrder.LITTLE_ENDIAN);
            buf.putInt(opcode);
            buf.putInt(data.length);
            buf.put(data);
            byte[] packet = buf.array();

            synchronized (ioLock) {
                if (closed.get()) return false;
                IntByReference written = new IntByReference();
                if (!Kernel32Ext.INSTANCE.WriteFile(handle, packet, packet.length, written, null)
                        || written.getValue() != packet.length) {
                    close();
                    return false;
                }
                return true;
            }
        }

        IpcFrame pollNow() {
            synchronized (ioLock) {
                if (closed.get()) return null;
                IntByReference avail = new IntByReference();
                if (!Kernel32Ext.INSTANCE.PeekNamedPipe(handle, null, 0, null, avail, null)) {
                    close();
                    return null;
                }
                int remainingAvail = avail.getValue();
                while (remainingAvail > 0 && rxBuf.size() < 8 + MAX_FRAME_BODY) {
                    int want = Math.min(readChunk.length, Math.min(remainingAvail, (8 + MAX_FRAME_BODY) - rxBuf.size()));
                    IntByReference readRef = new IntByReference();
                    if (!Kernel32Ext.INSTANCE.ReadFile(handle, readChunk, want, readRef, null) || readRef.getValue() <= 0) {
                        close();
                        return null;
                    }
                    int got = readRef.getValue();
                    rxBuf.write(readChunk, 0, got);
                    remainingAvail -= got;
                }

                byte[] buf = rxBuf.toByteArray();
                if (buf.length < 8) {
                    return null;
                }
                ByteBuffer bb = ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN);
                int opcode = bb.getInt(0);
                int len = bb.getInt(4);
                if (len < 0 || len > MAX_FRAME_BODY) {
                    close();
                    return null;
                }
                int frameEnd = 8 + len;
                if (buf.length < frameEnd) {
                    return null;
                }
                String payload = len > 0 ? new String(buf, 8, len, StandardCharsets.UTF_8) : "";
                rxBuf.reset();
                if (buf.length > frameEnd) {
                    rxBuf.write(buf, frameEnd, buf.length - frameEnd);
                }
                return new IpcFrame(opcode, payload);
            }
        }

        boolean isClosed() {
            return closed.get();
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                try {
                    Kernel32Ext.INSTANCE.CloseHandle(handle);
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private static boolean awaitReadyHandshake(PipeSession session, long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (!session.isClosed()) {
            IpcFrame frame = session.pollNow();
            if (frame == null) {
                long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0L) break;
                Thread.sleep(Math.min(25L, remaining));
                continue;
            }
            if (frame.opcode() == 2) {
                return false;
            }
            if (frame.opcode() == 3) {
                if (!session.writeFrame(4, frame.payload() != null ? frame.payload() : "")) {
                    return false;
                }
                continue;
            }
            if (frame.opcode() == 1 && frame.payload() != null) {
                try {
                    JsonElement el = JsonParser.parseString(frame.payload());
                    if (el.isJsonObject()) {
                        JsonObject obj = el.getAsJsonObject();
                        String evt = obj.has("evt") && !obj.get("evt").isJsonNull() ? obj.get("evt").getAsString() : "";
                        if ("READY".equalsIgnoreCase(evt)) {
                            return true;
                        }
                        if ("ERROR".equalsIgnoreCase(evt)) {
                            return false;
                        }
                    }
                } catch (Throwable ignored) {
                }
            }
        }
        return false;
    }

    private static boolean drainIncomingIfClose(PipeSession session) {
        if (session.isClosed()) return true;
        for (int i = 0; i < 8; i++) {
            IpcFrame f = session.pollNow();
            if (f == null) return false;
            if (f.opcode() == 2) return true;
            if (f.opcode() == 3) {
                if (!session.writeFrame(4, f.payload() != null ? f.payload() : "")) {
                    return true;
                }
            }
        }
        return session.isClosed();
    }

    private JsonObject buildActivityPayload(int nonce) {
        Minecraft mc = Minecraft.getInstance();
        String details = "OpticumeClient 1.5.0 · MC 26.3";
        String state = "В главном меню";
        if (mc != null && mc.level != null) {
            if (mc.isLocalServer()) {
                state = "Одиночная игра";
            } else if (mc.getCurrentServer() != null) {
                state = hideServerIp ? "На сервере (скрыто)" : ("Сервер: " + mc.getCurrentServer().ip);
            } else {
                state = "В игре";
            }
            if (showModuleCount) {
                int enabled = 0;
                for (Module m : ModuleManager.getInstance().getModules()) {
                    if (m.isEnabled()) enabled++;
                }
                state += " · Модулей: " + enabled;
            }
        }

        JsonObject timestamps = new JsonObject();
        timestamps.addProperty("start", sessionStartSec);

        JsonObject assets = new JsonObject();
        assets.addProperty("large_image", "opticume_logo");
        assets.addProperty("large_text", "OpticumeClient 26.3 Vulkan");

        JsonObject activity = new JsonObject();
        activity.addProperty("details", details);
        activity.addProperty("state", state);
        activity.add("timestamps", timestamps);
        activity.add("assets", assets);

        JsonObject args = new JsonObject();
        args.addProperty("pid", ProcessHandle.current().pid());
        args.add("activity", activity);

        JsonObject root = new JsonObject();
        root.addProperty("cmd", "SET_ACTIVITY");
        root.add("args", args);
        root.addProperty("nonce", String.valueOf(nonce));
        return root;
    }
}
