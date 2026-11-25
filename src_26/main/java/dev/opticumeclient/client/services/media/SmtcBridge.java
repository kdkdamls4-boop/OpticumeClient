package dev.opticumeclient.client.services.media;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.util.Mth;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Lightweight long-lived Windows SMTC worker (one persistent background PowerShell WinRT channel,
 * zero per-frame powershell.exe process spawning), with timeline interpolation and media control commands.
 */
public final class SmtcBridge {
    private static final SmtcBridge INSTANCE = new SmtcBridge();
    private static final long STALE_HOLD_MS = 5_000L;

    private static final String PS_SCRIPT = """
            $ErrorActionPreference = 'SilentlyContinue'
            [Console]::OutputEncoding = [System.Text.Encoding]::UTF8
            [Console]::InputEncoding = [System.Text.Encoding]::UTF8
            Add-Type -AssemblyName System.Runtime.WindowsRuntime
            $null = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager, Windows.Media.Control, ContentType=WindowsRuntime]
            $null = [Windows.Storage.Streams.DataReader, Windows.Storage.Streams, ContentType=WindowsRuntime]
            $asTaskGeneric = ([System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object {
                $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1'
            })[0]
            function AwaitWinRT($op, [Type]$t) {
                if ($null -eq $op) { return $null }
                $m = $asTaskGeneric.MakeGenericMethod($t)
                $task = $m.Invoke($null, @($op))
                $task.Wait() | Out-Null
                return $task.Result
            }
            $mgr = AwaitWinRT ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]::RequestAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager])
            $lastTrackKey = ''
            # Poll stdin asynchronously inside the primary PowerShell runspace (raw CLR threads lack a Runspace)
            $stdinReader = [System.IO.StreamReader]::new([Console]::OpenStandardInput(), [System.Text.Encoding]::UTF8)
            $readTask = $stdinReader.ReadLineAsync()
            $lastPollAt = [DateTime]::MinValue

            while ($true) {
                if ($null -eq $mgr) {
                    $mgr = AwaitWinRT ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]::RequestAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager])
                }
                $session = if ($null -ne $mgr) { $mgr.GetCurrentSession() } else { $null }
                if ($null -eq $session -and $null -ne $mgr) {
                    $all = $mgr.GetSessions()
                    if ($null -ne $all -and $all.Count -gt 0) { $session = $all[0] }
                }
                $quit = $false
                while ($null -ne $readTask -and $readTask.IsCompleted) {
                    $line = $null
                    try { $line = $readTask.Result } catch { $quit = $true; break }
                    if ($null -eq $line) { $quit = $true; break }
                    $cmdItem = $line.Trim()
                    if ($cmdItem -eq 'QUIT') { $quit = $true; break }
                    if ($null -ne $session) {
                        if ($cmdItem -eq 'PLAYPAUSE') { $null = $session.TryTogglePlayPauseAsync() }
                        elseif ($cmdItem -eq 'NEXT') { $null = $session.TrySkipNextAsync() }
                        elseif ($cmdItem -eq 'PREV') { $null = $session.TrySkipPreviousAsync() }
                    }
                    $readTask = $stdinReader.ReadLineAsync()
                }
                if ($quit) { break }
                $nowUtc = [DateTime]::UtcNow
                if (($nowUtc - $lastPollAt).TotalMilliseconds -lt 360.0) {
                    Start-Sleep -Milliseconds 90
                    continue
                }
                $lastPollAt = $nowUtc
                if ($null -eq $session) {
                    Write-Output '{"ok":false}'
                    [Console]::Out.Flush()
                    Start-Sleep -Milliseconds 120
                    continue
                }
                $props = AwaitWinRT ($session.TryGetMediaPropertiesAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties])
                $play = $session.GetPlaybackInfo()
                $tl = $session.GetTimelineProperties()
                $title = if ($null -ne $props -and $props.Title) { $props.Title } else { '' }
                $artist = if ($null -ne $props -and $props.Artist) { $props.Artist } else { '' }
                $album = if ($null -ne $props -and $props.AlbumTitle) { $props.AlbumTitle } else { '' }
                $playing = ($null -ne $play -and $play.PlaybackStatus -eq [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionPlaybackStatus]::Playing)
                $dur = if ($null -ne $tl) { [Math]::Max(0.0, $tl.EndTime.TotalSeconds) } else { 0.0 }
                $pos = if ($null -ne $tl) { [Math]::Max(0.0, $tl.Position.TotalSeconds) } else { 0.0 }
                $posTs = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
                if ($null -ne $tl -and $tl.LastUpdatedTime -gt [DateTimeOffset]::MinValue) {
                    $candidateTs = [long]$tl.LastUpdatedTime.ToUnixTimeMilliseconds()
                    if ($candidateTs -gt 0) { $posTs = $candidateTs }
                }
                $key = "$artist|$title"
                $thumbB64 = ''
                if ($key -ne '|' -and $key -ne $lastTrackKey -and $null -ne $props -and $null -ne $props.Thumbnail) {
                    $lastTrackKey = $key
                    try {
                        $stream = AwaitWinRT ($props.Thumbnail.OpenReadAsync()) ([Windows.Storage.Streams.IRandomAccessStreamWithContentType])
                        if ($null -ne $stream -and $stream.Size -gt 0 -and $stream.Size -lt 2097152) {
                            $len = [uint32]$stream.Size
                            $reader = [Windows.Storage.Streams.DataReader]::new($stream)
                            $loaded = AwaitWinRT ($reader.LoadAsync($len)) ([uint32])
                            $bytes = [byte[]]::new($loaded)
                            $reader.ReadBytes($bytes)
                            $thumbB64 = [Convert]::ToBase64String($bytes)
                            $reader.Dispose()
                            $stream.Dispose()
                        }
                    } catch {}
                } elseif ($key -eq '|') {
                    $lastTrackKey = ''
                }
                $obj = @{
                    ok = ($title -ne '')
                    title = $title
                    artist = $artist
                    album = $album
                    playing = $playing
                    pos = [Math]::Round($pos, 3)
                    dur = [Math]::Round($dur, 3)
                    posTs = $posTs
                    thumb = $thumbB64
                } | ConvertTo-Json -Compress
                Write-Output $obj
                [Console]::Out.Flush()
            }
            """;

    public record TrackSnapshot(
            boolean active,
            String title,
            String artist,
            String album,
            boolean playing,
            double positionSec,
            double durationSec,
            long updatedMs,
            long positionUpdatedMs
    ) {
        public TrackSnapshot(boolean active, String title, String artist, String album,
                             boolean playing, double positionSec, double durationSec, long updatedMs) {
            this(active, title, artist, album, playing, positionSec, durationSec, updatedMs, updatedMs);
        }

        public double interpolatedPositionSec() {
            if (!active) return 0.0;
            double p = positionSec;
            if (playing) {
                long baseMs = positionUpdatedMs > 0L ? positionUpdatedMs : updatedMs;
                p += Math.max(0L, System.currentTimeMillis() - baseMs) / 1000.0;
            }
            if (durationSec > 0.1) {
                return Mth.clamp((float) p, 0.0f, (float) durationSec);
            }
            return Math.max(0.0, p);
        }

        public String trackKey() {
            return (artist == null ? "" : artist) + "|" + (title == null ? "" : title);
        }
    }

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicInteger generation = new AtomicInteger(0);
    private Thread workerThread;
    private Thread watchdogThread;
    private volatile Process workerProcess;
    private BufferedWriter processStdin;
    private volatile long lastConsumerUsageMs = 0L;
    private volatile long lastWorkerOutputMs = 0L;

    private volatile TrackSnapshot current = new TrackSnapshot(false, "", "", "", false, 0.0, 0.0, 0L);

    private SmtcBridge() {
    }

    public static SmtcBridge getInstance() {
        return INSTANCE;
    }

    public void notifyConsumerActive() {
        lastConsumerUsageMs = System.currentTimeMillis();
        ensureStarted();
    }

    public TrackSnapshot getSnapshot() {
        TrackSnapshot snap = current;
        long now = System.currentTimeMillis();
        if (snap.active() && now - snap.updatedMs() > STALE_HOLD_MS) {
            TrackSnapshot expired = new TrackSnapshot(false, "", "", "", false, 0.0, 0.0, now);
            current = expired;
            return expired;
        }
        return snap;
    }

    public boolean hasActiveTrack() {
        TrackSnapshot s = current;
        return s.active() && !s.title().isBlank();
    }

    public void togglePlayPause() {
        sendCommand("PLAYPAUSE");
    }

    public void nextTrack() {
        sendCommand("NEXT");
    }

    public void prevTrack() {
        sendCommand("PREV");
    }

    private synchronized void ensureStarted() {
        if (running.get()) return;
        String os = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
        if (!os.contains("win")) return;

        int gen = generation.incrementAndGet();
        running.set(true);
        lastWorkerOutputMs = System.currentTimeMillis();
        workerThread = new Thread(() -> runWorkerLoop(gen), "opticume-smtc-bridge-" + gen);
        workerThread.setDaemon(true);
        workerThread.start();
        watchdogThread = new Thread(() -> runWatchdogLoop(gen), "opticume-smtc-watchdog-" + gen);
        watchdogThread.setDaemon(true);
        watchdogThread.start();
    }

    private void runWatchdogLoop(int gen) {
        while (running.get() && generation.get() == gen) {
            try {
                Thread.sleep(1_000L);
            } catch (InterruptedException ignored) {
                break;
            }
            if (!running.get() || generation.get() != gen) break;
            long now = System.currentTimeMillis();
            if (now - lastConsumerUsageMs > 15_000L) {
                Process p;
                synchronized (this) {
                    if (generation.get() != gen) break;
                    sendCommand("QUIT");
                    running.set(false);
                    generation.incrementAndGet();
                    p = workerProcess;
                }
                destroyProcess(p);
                break;
            }
            Process p = workerProcess;
            if (p != null && p.isAlive() && generation.get() == gen && now - lastWorkerOutputMs > 7_000L) {
                lastWorkerOutputMs = now;
                current = new TrackSnapshot(false, "", "", "", false, 0.0, 0.0, now);
                destroyProcess(p);
            }
        }
    }

    private synchronized void sendCommand(String cmd) {
        if (!running.get() || processStdin == null) return;
        try {
            processStdin.write(cmd);
            processStdin.newLine();
            processStdin.flush();
        } catch (Throwable ignored) {
        }
    }

    private void runWorkerLoop(int gen) {
        String encoded = Base64.getEncoder().encodeToString(PS_SCRIPT.getBytes(StandardCharsets.UTF_16LE));
        long backoffMs = 1_000L;

        while (running.get() && generation.get() == gen) {
            // Auto-pause worker if no HUD widget queried it for >15 seconds
            if (System.currentTimeMillis() - lastConsumerUsageMs > 15_000L) {
                synchronized (this) {
                    if (generation.get() == gen) {
                        running.set(false);
                        generation.incrementAndGet();
                    }
                }
                break;
            }
            Process proc = null;
            try {
                ProcessBuilder pb = new ProcessBuilder(
                        "powershell.exe",
                        "-NoProfile",
                        "-NonInteractive",
                        "-WindowStyle", "Hidden",
                        "-ExecutionPolicy", "Bypass",
                        "-EncodedCommand", encoded
                );
                pb.redirectErrorStream(true);
                proc = pb.start();
                boolean stillOwner;
                synchronized (this) {
                    stillOwner = running.get() && generation.get() == gen;
                    if (stillOwner) {
                        workerProcess = proc;
                        processStdin = new BufferedWriter(new OutputStreamWriter(proc.getOutputStream(), StandardCharsets.UTF_8));
                        lastWorkerOutputMs = System.currentTimeMillis();
                    }
                }
                if (!stillOwner) {
                    destroyProcess(proc);
                    break;
                }

                try (BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while (running.get() && generation.get() == gen && (line = reader.readLine()) != null) {
                        if (System.currentTimeMillis() - lastConsumerUsageMs > 15_000L) {
                            synchronized (this) {
                                if (generation.get() == gen) {
                                    sendCommand("QUIT");
                                    running.set(false);
                                    generation.incrementAndGet();
                                }
                            }
                            break;
                        }
                        line = line.trim();
                        if (line.startsWith("{") && line.endsWith("}")) {
                            if (generation.get() != gen) break;
                            lastWorkerOutputMs = System.currentTimeMillis();
                            parseLine(line, gen);
                            backoffMs = 1_000L;
                        }
                    }
                }
            } catch (Throwable ignored) {
            } finally {
                destroyProcess(proc);
                synchronized (this) {
                    if (workerProcess == proc) {
                        workerProcess = null;
                        processStdin = null;
                    }
                }
            }

            if (!running.get() || generation.get() != gen) break;
            try {
                Thread.sleep(backoffMs);
                backoffMs = Math.min(10_000L, backoffMs * 2L);
            } catch (InterruptedException ignored) {
                break;
            }
        }
        if (generation.get() == gen) {
            current = new TrackSnapshot(false, "", "", "", false, 0.0, 0.0, System.currentTimeMillis());
        }
    }

    private void parseLine(String json, int gen) {
        try {
            JsonElement parsed = JsonParser.parseString(json);
            if (!parsed.isJsonObject()) return;
            JsonObject obj = parsed.getAsJsonObject();
            boolean ok = obj.has("ok") && obj.get("ok").getAsBoolean();
            long now = System.currentTimeMillis();

            String title = obj.has("title") ? obj.get("title").getAsString() : "";
            String artist = obj.has("artist") ? obj.get("artist").getAsString() : "";
            String album = obj.has("album") ? obj.get("album").getAsString() : "";
            boolean playing = obj.has("playing") && obj.get("playing").getAsBoolean();
            double pos = obj.has("pos") ? obj.get("pos").getAsDouble() : 0.0;
            double dur = obj.has("dur") ? obj.get("dur").getAsDouble() : 0.0;
            long posTs = (obj.has("posTs") && obj.get("posTs").isJsonPrimitive()) ? obj.get("posTs").getAsLong() : now;
            if (posTs <= 0L) posTs = now;
            String thumbB64 = obj.has("thumb") ? obj.get("thumb").getAsString() : "";

            TrackSnapshot next;
            boolean trackChanged;
            synchronized (this) {
                if (!running.get() || generation.get() != gen) return;
                if (!ok) {
                    if (now - current.updatedMs() > STALE_HOLD_MS) {
                        current = new TrackSnapshot(false, "", "", "", false, 0.0, 0.0, now, now);
                    }
                    return;
                }
                String prevKey = current.trackKey();
                next = new TrackSnapshot(true, title, artist, album, playing, pos, dur, now, posTs);
                current = next;
                trackChanged = !next.trackKey().equals(prevKey);
            }

            if (trackChanged) {
                synchronized (this) {
                    if (!running.get() || generation.get() != gen) return;
                    CoverArtManager.getInstance().onTrackChanged(artist, title, thumbB64);
                    LyricsService.getInstance().requestLyrics(artist, title);
                }
            } else if (!thumbB64.isEmpty()) {
                synchronized (this) {
                    if (!running.get() || generation.get() != gen) return;
                    CoverArtManager.getInstance().supplySmtcThumbnail(next.trackKey(), thumbB64);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    public synchronized void shutdown() {
        generation.incrementAndGet();
        running.set(false);
        if (processStdin != null) {
            try {
                processStdin.write("QUIT\n");
                processStdin.flush();
            } catch (Throwable ignored) {
            }
        }
        destroyProcess(workerProcess);
        workerProcess = null;
        processStdin = null;
    }

    private static void destroyProcess(Process proc) {
        if (proc == null) return;
        try {
            if (proc.isAlive()) {
                proc.destroy();
                if (!proc.waitFor(250, TimeUnit.MILLISECONDS)) {
                    proc.destroyForcibly();
                }
            }
        } catch (Throwable ignored) {
        }
    }
}
