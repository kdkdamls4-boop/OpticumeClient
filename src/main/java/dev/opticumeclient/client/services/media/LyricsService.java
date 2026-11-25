package dev.opticumeclient.client.services.media;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.util.Mth;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Asynchronous synced LRC lyrics fetcher backed by lrclib.net, computing line-level progress
 * for dual-layer scissor karaoke highlighting in DynamicIslandHud and MediaPlayerHud.
 */
public final class LyricsService {
    public record LyricLine(long timeMs, String text) {
    }

    public record ActiveLyric(String current, String next, float progress) {
    }

    private static final LyricsService INSTANCE = new LyricsService();
    private static final Pattern LRC_LINE = Pattern.compile("\\[(\\d{1,2}):(\\d{2})(?:\\.(\\d{1,3}))?\\](.*)");

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(4))
            .build();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "opticume-lyrics");
        t.setDaemon(true);
        return t;
    });

    private static final long RETRY_COOLDOWN_MS = 15_000L;
    private static final long DEFAULT_RATE_LIMIT_MS = 60_000L;
    private static final long MAX_RATE_LIMIT_MS = 600_000L;

    private static final class RateLimitedException extends IOException {
        RateLimitedException(String msg) {
            super(msg);
        }
    }

    private final Map<String, List<LyricLine>> cache = new ConcurrentHashMap<>();
    private final Map<String, Boolean> inFlight = new ConcurrentHashMap<>();
    private final Map<String, Long> retryAfterMs = new ConcurrentHashMap<>();
    private volatile long rateLimitedUntilMs = 0L;

    private LyricsService() {
    }

    public static LyricsService getInstance() {
        return INSTANCE;
    }

    public void requestLyrics(String artist, String title) {
        if (title == null || title.isBlank()) return;
        String key = (artist == null ? "" : artist.trim()) + "|" + title.trim();
        long now = System.currentTimeMillis();
        Long retryAt = retryAfterMs.get(key);
        long blockedUntil = Math.max(retryAt != null ? retryAt : 0L, rateLimitedUntilMs);
        if (cache.containsKey(key) || now < blockedUntil || inFlight.putIfAbsent(key, Boolean.TRUE) != null) {
            return;
        }
        executor.execute(() -> {
            try {
                List<LyricLine> lines = fetchFromLrclib(artist, title);
                cache.put(key, lines);
                retryAfterMs.remove(key);
            } catch (Throwable ignored) {
                long cooldownUntil = Math.max(System.currentTimeMillis() + RETRY_COOLDOWN_MS, rateLimitedUntilMs);
                retryAfterMs.put(key, cooldownUntil);
            } finally {
                inFlight.remove(key);
            }
        });
    }

    public ActiveLyric getActiveLine(String artist, String title, long positionMs) {
        if (title == null || title.isBlank()) return null;
        String key = (artist == null ? "" : artist.trim()) + "|" + title.trim();
        List<LyricLine> lines = cache.get(key);
        if (lines == null || lines.isEmpty()) return null;

        int activeIdx = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).timeMs() <= positionMs + 120L) {
                activeIdx = i;
            } else {
                break;
            }
        }
        if (activeIdx < 0) {
            return new ActiveLyric(lines.get(0).text(), lines.size() > 1 ? lines.get(1).text() : "", 0.0f);
        }

        LyricLine cur = lines.get(activeIdx);
        long nextMs = (activeIdx + 1 < lines.size()) ? lines.get(activeIdx + 1).timeMs() : (cur.timeMs() + 4500L);
        String nextText = (activeIdx + 1 < lines.size()) ? lines.get(activeIdx + 1).text() : "";
        float span = Math.max(400.0f, nextMs - cur.timeMs());
        float progress = Mth.clamp((positionMs - cur.timeMs()) / span, 0.0f, 1.0f);
        return new ActiveLyric(cur.text(), nextText, progress);
    }

    private List<LyricLine> fetchFromLrclib(String artist, String title) throws Exception {
        if (System.currentTimeMillis() < rateLimitedUntilMs) {
            throw new RateLimitedException("LRCLIB rate-limited");
        }
        String cleanTitle = cleanQuery(title);
        String cleanArtist = cleanQuery(artist == null ? "" : artist);

        String url = "https://lrclib.net/api/get?track_name=" + enc(cleanTitle) + "&artist_name=" + enc(cleanArtist);
        String synced = null;
        IOException getFailure = null;
        try {
            synced = querySingleUrl(url, false);
        } catch (RateLimitedException rle) {
            throw rle;
        } catch (IOException e) {
            getFailure = e;
        }
        if (synced == null || synced.isBlank()) {
            if (System.currentTimeMillis() < rateLimitedUntilMs) {
                throw new RateLimitedException("LRCLIB rate-limited before search fallback");
            }
            String searchUrl = "https://lrclib.net/api/search?q=" + enc((cleanArtist + " " + cleanTitle).trim());
            String searched = querySingleUrl(searchUrl, true);
            if (searched != null && !searched.isBlank()) {
                synced = searched;
            } else if (getFailure != null) {
                throw getFailure;
            }
        }
        return parseLrc(synced);
    }

    private String querySingleUrl(String url, boolean arrayMode) throws IOException {
        if (System.currentTimeMillis() < rateLimitedUntilMs) {
            throw new RateLimitedException("LRCLIB rate-limited");
        }
        HttpResponse<String> resp;
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(5))
                    .header("User-Agent", "OpticumeClient/1.4 (MC 26.3)")
                    .GET()
                    .build();
            resp = http.send(req, CoverArtManager.boundedStringHandler(CoverArtManager.MAX_METADATA_BYTES));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Lyrics request interrupted", e);
        } catch (IllegalArgumentException e) {
            return null;
        }
        int status = resp.statusCode();
        if (status == 429 || (status == 503 && resp.headers().firstValue("Retry-After").isPresent())) {
            long waitMs = parseRetryAfterMs(resp.headers().firstValue("Retry-After").orElse(null));
            long nextDeadline = System.currentTimeMillis() + waitMs;
            if (nextDeadline > rateLimitedUntilMs) {
                rateLimitedUntilMs = nextDeadline;
            }
            throw new RateLimitedException("LRCLIB rate-limited (HTTP " + status + ", wait " + waitMs + "ms)");
        }
        if (status == 404) {
            return null;
        }
        if (status != 200) {
            throw new IOException("Unexpected LRCLIB HTTP status: " + status);
        }
        if (resp.body() == null || resp.body().isBlank()) return null;
        try {
            JsonElement parsed = JsonParser.parseString(resp.body());
            if (arrayMode) {
                if (!parsed.isJsonArray()) return null;
                JsonArray arr = parsed.getAsJsonArray();
                for (JsonElement el : arr) {
                    if (el.isJsonObject()) {
                        String s = extractSynced(el.getAsJsonObject());
                        if (s != null && !s.isBlank()) return s;
                    }
                }
                return null;
            }
            return parsed.isJsonObject() ? extractSynced(parsed.getAsJsonObject()) : null;
        } catch (RuntimeException e) {
            throw new IOException("Malformed LRCLIB JSON response", e);
        }
    }

    private static long parseRetryAfterMs(String header) {
        if (header == null || header.isBlank()) {
            return DEFAULT_RATE_LIMIT_MS;
        }
        String trimmed = header.trim();
        try {
            long secs = Long.parseLong(trimmed);
            long ms = Math.max(1_000L, Math.min(MAX_RATE_LIMIT_MS, secs * 1_000L));
            return ms;
        } catch (NumberFormatException ignored) {
        }
        try {
            long untilMs = ZonedDateTime.parse(trimmed, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli();
            long delta = untilMs - System.currentTimeMillis();
            return Math.max(1_000L, Math.min(MAX_RATE_LIMIT_MS, delta));
        } catch (Throwable ignored) {
            return DEFAULT_RATE_LIMIT_MS;
        }
    }

    private static String extractSynced(JsonObject obj) {
        if (obj.has("syncedLyrics") && !obj.get("syncedLyrics").isJsonNull()) {
            return obj.get("syncedLyrics").getAsString();
        }
        return null;
    }

    private static List<LyricLine> parseLrc(String lrc) {
        if (lrc == null || lrc.isBlank()) return Collections.emptyList();
        List<LyricLine> out = new ArrayList<>();
        for (String line : lrc.split("\n")) {
            Matcher m = LRC_LINE.matcher(line.trim());
            if (!m.find()) continue;
            long min = Long.parseLong(m.group(1));
            long sec = Long.parseLong(m.group(2));
            String fracStr = m.group(3);
            long ms = 0L;
            if (fracStr != null) {
                if (fracStr.length() == 1) ms = Long.parseLong(fracStr) * 100L;
                else if (fracStr.length() == 2) ms = Long.parseLong(fracStr) * 10L;
                else ms = Long.parseLong(fracStr.substring(0, 3));
            }
            String text = m.group(4).trim();
            if (!text.isEmpty()) {
                out.add(new LyricLine(min * 60_000L + sec * 1_000L + ms, text));
            }
        }
        out.sort(Comparator.comparingLong(LyricLine::timeMs));
        return Collections.unmodifiableList(out);
    }

    private static String cleanQuery(String raw) {
        return raw.replaceAll("\\s*\\([^)]*\\)", "")
                .replaceAll("\\s*\\[[^]]*\\]", "")
                .replaceAll("\\s*-\\s*Topic$", "")
                .trim();
    }

    private static String enc(String s) {
        return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8);
    }
}
