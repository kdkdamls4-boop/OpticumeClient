package dev.opticumeclient.client.services;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.opticumeclient.client.render.ColorUtils;
import net.minecraft.resources.Identifier;

import java.awt.Color;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Asynchronous live event fetcher for FunTime &amp; HolyWorld servers, ported from Desire.
 */
public final class ServerEventsService {
    private static final ServerEventsService INSTANCE = new ServerEventsService();

    private static final String FT_EVENTS = "https://funtime.me/api/backend/api/v1/events?event-type=all&server-type=";
    private static final String HW_EVENTS = "https://api.holyworld.me/v1/events";
    private static final String HW_SERVERS = "https://api.holyworld.me/v1/servers";
    private static final String AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36";

    private static final long FT_INTERVAL = 5_000L;
    private static final long HW_INTERVAL = 10_000L;
    private static final long HW_SERVERS_TTL = 5 * 60_000L;
    private static final long ERROR_RETRY = 8_000L;

    public enum Source {FT, HW}

    public enum Status {LOADING, OK, ERROR}

    public record Card(String key, String title, String server, int serverColor,
                       String phase, int phaseColor, boolean live,
                       String tag, int tagColor, String extra,
                       long endsAt, int count, String coords,
                       Identifier banner, Identifier glyph, int fromColor, int toColor) {
    }

    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "opticume-server-events");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicBoolean busy = new AtomicBoolean();

    private volatile Source source = Source.FT;
    private volatile List<Card> cards = List.of();
    private volatile Status status = Status.LOADING;
    private volatile long updatedAt;
    private volatile int generation;
    private volatile long nextRequest;

    private volatile Map<String, String> hwServers = Map.of();
    private volatile long hwServersAt;

    private ServerEventsService() {
    }

    public static ServerEventsService getInstance() {
        return INSTANCE;
    }

    public Source getSource() {
        return source;
    }

    public List<Card> getCards() {
        return cards;
    }

    public Status getStatus() {
        return status;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public void setSource(Source next) {
        if (next == null || next == source) return;
        source = next;
        generation++;
        cards = List.of();
        status = Status.LOADING;
        updatedAt = 0L;
        nextRequest = 0L;
    }

    public void tick() {
        long now = System.currentTimeMillis();
        if (now < nextRequest || !busy.compareAndSet(false, true)) return;
        Source requested = source;
        int requestedGeneration = generation;
        nextRequest = now + (requested == Source.FT ? FT_INTERVAL : HW_INTERVAL);
        executor.execute(() -> {
            try {
                List<Card> loaded = requested == Source.FT ? loadFunTime() : loadHolyWorld();
                if (requestedGeneration != generation) return;
                cards = List.copyOf(loaded);
                status = Status.OK;
                updatedAt = System.currentTimeMillis();
            } catch (Exception e) {
                if (requestedGeneration != generation) return;
                status = cards.isEmpty() ? Status.ERROR : Status.OK;
                nextRequest = System.currentTimeMillis() + ERROR_RETRY;
            } finally {
                busy.set(false);
            }
        });
    }

    private record FtStyle(String name, String banner, String glyph, int accent, int from, int to) {
    }

    private static final Map<String, FtStyle> FT_STYLES = new HashMap<>();
    private static final Map<String, String> FT_PHASES = Map.of(
            "RUNNING", "Идёт", "LOOTING", "Лут готов", "OPENED", "Открыт", "WAITING", "Ожидание",
            "PREPARING", "Готовится", "STARTING", "Запускается", "ACTIVATING", "Активация",
            "STOPPING", "Завершается", "FINISHED", "Завершён", "VOTING", "Голосование");
    private static final Map<String, Integer> FT_PHASE_COLORS = Map.of(
            "WAITING", 0xFF94A3B8, "STOPPING", 0xFFA1A1AA, "FINISHED", 0xFF71717A, "CLOSED", 0xFF71717A);
    private static final Set<String> FT_LIVE = Set.of("RUNNING", "LOOTING", "OPENED", "ACTIVATING");
    private static final Map<String, Integer> FT_LOOT = Map.of(
            "Легендарный", 0xFFFB923C, "Элитный", 0xFFC084FC, "Богатый", 0xFF60A5FA,
            "Солидный", 0xFF4ADE80, "Обычный", 0xFFA1A1AA);

    static {
        ft("airdrop", "Аирдроп", "airdrop", "package", 0xFFFBBF24, 0xFFF59E0B, 0xFFEA580C);
        ft("geyser", "Гейзер", "geyser", "sparkle", 0xFF22D3EE, 0xFF22D3EE, 0xFF0D9488);
        ft("vulkan", "Вулкан", "volcano", "flame", 0xFFF87171, 0xFFDC2626, 0xFFC2410C);
        ft("volcano", "Вулкан", "volcano", "flame", 0xFFF87171, 0xFFDC2626, 0xFFC2410C);
        ft("altar", "Алтарь", "altar", "sparkle", 0xFF34D399, 0xFF10B981, 0xFF0F766E);
        ft("altarundead", "Алтарь нежити", "altarundead", "skull", 0xFFA3E635, 0xFF84CC16, 0xFF15803D);
        ft("beacon", "Маяк-убийца", "beacon", "beacon", 0xFF38BDF8, 0xFF38BDF8, 0xFF2563EB);
        ft("myst_beacon", "Загадочный маяк", "myst_beacon", "beacon", 0xFFA78BFA, 0xFF8B5CF6, 0xFF7E22CE);
        ft("mystic", "Мистический сундук", "mystic", "chest", 0xFFE879F9, 0xFFD946EF, 0xFFBE185D);
        ft("hellm", "Адская резня", "hellm", "swords", 0xFFF43F5E, 0xFFBE123C, 0xFF450A0A);
        ft("deathchest", "Сундук Смерти", "deathchest", "chest", 0xFFD97706, 0xFFB45309, 0xFF713F12);
        ft("meteor", "Метеорит", "meteor", "comet", 0xFFFB923C, 0xFFF97316, 0xFF6B21A8);
        ft("meteor_rain", "Метеоритный дождь", "meteor", "comet", 0xFFFB923C, 0xFFF97316, 0xFF6B21A8);
        ft("santa", "Санта", null, "package", 0xFFFB7185, 0xFFEF4444, 0xFFE11D48);
        ft("portal", "Портал", null, "sparkle", 0xFF818CF8, 0xFF6366F1, 0xFF1E40AF);
        ft("vote", "Голосование", null, "sparkle", 0xFF60A5FA, 0xFF2563EB, 0xFF312E81);
        ft("voting", "Голосование", null, "sparkle", 0xFF60A5FA, 0xFF2563EB, 0xFF312E81);
    }

    private static void ft(String id, String name, String banner, String glyph, int accent, int from, int to) {
        FT_STYLES.put(id, new FtStyle(name, banner, glyph, accent, from, to));
    }

    private List<Card> loadFunTime() throws IOException {
        JsonObject root = JsonParser.parseString(get(FT_EVENTS)).getAsJsonObject();
        long now = System.currentTimeMillis();
        List<Card> out = new ArrayList<>();
        if (root.has("response") && root.get("response").isJsonArray()) {
            for (JsonElement serverElement : root.getAsJsonArray("response")) {
                JsonObject serverObject = serverElement.getAsJsonObject();
                String serverKey = str(serverObject, "server");
                if (serverKey == null || !serverObject.has("events") || !serverObject.get("events").isJsonArray()) continue;
                Map<String, List<JsonObject>> groups = new LinkedHashMap<>();
                for (JsonElement eventElement : serverObject.getAsJsonArray("events")) {
                    JsonObject event = eventElement.getAsJsonObject();
                    String id = str(event, "id");
                    if (id == null) continue;
                    groups.computeIfAbsent(id.toLowerCase(Locale.ROOT), k -> new ArrayList<>()).add(event);
                }
                for (Map.Entry<String, List<JsonObject>> group : groups.entrySet()) {
                    out.add(funTimeCard(serverKey, group.getKey(), group.getValue(), now));
                }
            }
        }
        out.sort(Comparator.comparingLong((Card c) -> c.endsAt() > 0 ? c.endsAt() : Long.MAX_VALUE)
                .thenComparing(Card::server, ServerEventsService::compareNatural));
        return out;
    }

    private static Card funTimeCard(String serverKey, String id, List<JsonObject> events, long now) {
        JsonObject main = null;
        long mainEnds = Long.MAX_VALUE;
        for (JsonObject event : events) {
            long ends = endsAt(event, now);
            if (ends > 0 && ends < mainEnds) {
                main = event;
                mainEnds = ends;
            }
        }
        if (main == null) {
            for (JsonObject event : events) {
                if (str(event, "phase") != null) {
                    main = event;
                    break;
                }
            }
        }
        if (main == null) main = events.get(0);

        FtStyle style = FT_STYLES.get(id);
        String title = style != null ? style.name() : prettify(id);
        int accent = style != null ? style.accent() : 0xFFF87171;

        String phaseKey = str(main, "phase");
        phaseKey = phaseKey == null ? null : phaseKey.toUpperCase(Locale.ROOT);
        String phase = phaseKey == null ? "Скоро" : FT_PHASES.getOrDefault(phaseKey, prettify(phaseKey));
        int phaseColor = phaseKey == null ? accent : FT_PHASE_COLORS.getOrDefault(phaseKey, accent);

        String loot = str(main, "loot");
        if (loot == null) {
            for (JsonObject event : events) {
                if ((loot = str(event, "loot")) != null) break;
            }
        }
        int lootColor = loot == null ? 0 : FT_LOOT.getOrDefault(loot, 0xFFD4D4D8);

        String coords = null;
        JsonElement location = main.get("location-event");
        if (location != null && location.isJsonObject()) {
            JsonObject l = location.getAsJsonObject();
            if (l.has("x") && l.has("y") && l.has("z")) {
                coords = l.get("x").getAsInt() + " " + l.get("y").getAsInt() + " " + l.get("z").getAsInt();
            }
        }

        Identifier banner = style != null && style.banner() != null
                ? Identifier.fromNamespaceAndPath("opticumeclient", "textures/desire/menu/events/ft/" + style.banner() + ".png")
                : null;
        int from = style != null ? style.from() : 0xFFDC2626;
        int to = style != null ? style.to() : 0xFF7F1D1D;
        return new Card("ft|" + serverKey + "|" + id, title, ftServerName(serverKey), 0,
                phase, phaseColor, phaseKey != null && FT_LIVE.contains(phaseKey),
                loot, lootColor, null,
                mainEnds == Long.MAX_VALUE ? 0L : mainEnds, events.size(), coords,
                banner, glyph(style != null ? style.glyph() : "sparkle"), from, to);
    }

    private static long endsAt(JsonObject event, long now) {
        JsonElement ms = event.get("time-ms-left");
        if (ms != null && ms.isJsonPrimitive() && ms.getAsLong() > 0) return now + ms.getAsLong();
        JsonElement seconds = event.get("time-seconds-left");
        if (seconds != null && seconds.isJsonPrimitive() && seconds.getAsLong() > 0) return now + seconds.getAsLong() * 1000L;
        return 0L;
    }

    private static String ftServerName(String key) {
        Matcher digits = Pattern.compile("(\\d+)").matcher(key);
        return digits.find() ? "Анархия " + digits.group(1) : key;
    }

    private record HwStyle(String glyph, int from, int to, String... keys) {
    }

    private static final HwStyle[] HW_STYLES = {
            new HwStyle("package", 0xFFF59E0B, 0xFFEA580C, "parcel", "package", "deliver", "post", "airdrop", "drop", "посыл", "груз", "аирдроп"),
            new HwStyle("chest", 0xFFFACC15, 0xFFB45309, "chest", "treasure", "loot", "сундук", "клад"),
            new HwStyle("skull", 0xFFF43F5E, 0xFF7F1D1D, "undead", "zombie", "skelet", "boss", "altar", "нежит", "босс", "алтар"),
            new HwStyle("flame", 0xFFF87171, 0xFFC2410C, "volcan", "lava", "fire", "hell", "nether", "вулкан", "лава", "адск"),
            new HwStyle("comet", 0xFFFB923C, 0xFF6B21A8, "meteor", "comet", "star", "метеор", "комет"),
            new HwStyle("beacon", 0xFF38BDF8, 0xFF4F46E5, "beacon", "tower", "capture", "point", "king", "маяк", "захват", "башн"),
            new HwStyle("pickaxe", 0xFF22D3EE, 0xFF0F766E, "mine", "ore", "cave", "шахт", "руд"),
            new HwStyle("swords", 0xFFE879F9, 0xFF9D174D, "pvp", "battle", "war", "arena", "duel", "битв", "арен", "дуэл"),
    };

    private static final Map<String, String[]> HW_RARITY = Map.of(
            "common", new String[]{"Обычный", "A1A1AA"}, "normal", new String[]{"Обычный", "A1A1AA"},
            "uncommon", new String[]{"Необычный", "4ADE80"}, "rare", new String[]{"Редкий", "60A5FA"},
            "epic", new String[]{"Эпический", "C084FC"}, "legendary", new String[]{"Легендарный", "FB923C"},
            "mythic", new String[]{"Мифический", "F43F5E"});

    private static final Map<String, String> HW_BIOMES = Map.ofEntries(
            Map.entry("plains", "Равнины"), Map.entry("desert", "Пустыня"), Map.entry("forest", "Лес"),
            Map.entry("taiga", "Тайга"), Map.entry("snowy", "Снега"), Map.entry("snow", "Снега"),
            Map.entry("jungle", "Джунгли"), Map.entry("swamp", "Болото"), Map.entry("savanna", "Саванна"),
            Map.entry("mountains", "Горы"), Map.entry("hills", "Холмы"), Map.entry("ocean", "Океан"),
            Map.entry("beach", "Пляж"), Map.entry("badlands", "Пустоши"), Map.entry("mesa", "Пустоши"),
            Map.entry("nether", "Незер"), Map.entry("end", "Энд"), Map.entry("mushroom", "Грибы"),
            Map.entry("ice", "Лёд"), Map.entry("river", "Река"), Map.entry("cave", "Пещеры"));

    private static final Pattern COLOR_CODE = Pattern.compile("[&§]([0-9a-fk-orA-FK-OR])");
    private static final int[] MC_COLORS = {
            0xFF000000, 0xFF0000AA, 0xFF00AA00, 0xFF00AAAA, 0xFFAA0000, 0xFFAA00AA, 0xFFFFAA00, 0xFFAAAAAA,
            0xFF555555, 0xFF5555FF, 0xFF55FF55, 0xFF55FFFF, 0xFFFF5555, 0xFFFF55FF, 0xFFFFFF55, 0xFFFFFFFF};

    private List<Card> loadHolyWorld() throws IOException {
        long now = System.currentTimeMillis();
        if (hwServers.isEmpty() || now - hwServersAt > HW_SERVERS_TTL) {
            try {
                Map<String, String> servers = new HashMap<>();
                JsonObject root = JsonParser.parseString(get(HW_SERVERS)).getAsJsonObject();
                for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
                    if (entry.getValue().isJsonPrimitive()) servers.put(entry.getKey(), entry.getValue().getAsString());
                }
                hwServers = servers;
                hwServersAt = now;
            } catch (Exception ignored) {
            }
        }

        JsonObject root = JsonParser.parseString(get(HW_EVENTS)).getAsJsonObject();
        List<Card> out = new ArrayList<>();
        for (Map.Entry<String, JsonElement> serverEntry : root.entrySet()) {
            if (!serverEntry.getValue().isJsonArray()) continue;
            String rawName = hwServers.getOrDefault(serverEntry.getKey(), serverEntry.getKey());
            Matcher code = COLOR_CODE.matcher(rawName);
            int serverColor = 0;
            if (code.find()) {
                int index = Character.digit(code.group(1).toLowerCase(Locale.ROOT).charAt(0), 16);
                if (index >= 0 && index < 16) serverColor = MC_COLORS[index];
            }
            String server = COLOR_CODE.matcher(rawName).replaceAll("").trim();

            Map<String, List<JsonObject>> groups = new LinkedHashMap<>();
            JsonArray events = serverEntry.getValue().getAsJsonArray();
            for (JsonElement element : events) {
                JsonObject event = element.getAsJsonObject();
                String id = str(event, "id");
                if (id == null) continue;
                JsonObject meta = event.has("metadata") && event.get("metadata").isJsonObject()
                        ? event.getAsJsonObject("metadata") : new JsonObject();
                groups.computeIfAbsent(id + "|" + nullToEmpty(str(meta, "rare")), k -> new ArrayList<>()).add(event);
            }
            for (List<JsonObject> group : groups.values()) {
                out.add(holyWorldCard(serverEntry.getKey(), server, serverColor, group));
            }
        }
        out.sort(Comparator.comparing(Card::server, ServerEventsService::compareNatural).thenComparing(Card::title));
        return out;
    }

    private static Card holyWorldCard(String serverKey, String server, int serverColor, List<JsonObject> group) {
        JsonObject event = group.get(0);
        String id = str(event, "id");
        JsonObject meta = event.has("metadata") && event.get("metadata").isJsonObject()
                ? event.getAsJsonObject("metadata") : new JsonObject();
        String display = str(meta, "displayName");
        String title = display != null ? COLOR_CODE.matcher(display).replaceAll("").trim() : prettify(id);

        String tag = null, extra = null;
        int tagColor = 0;
        String rare = str(meta, "rare");
        if (rare != null) {
            String[] parts = rare.toLowerCase(Locale.ROOT).split("_");
            int start = 0;
            String[] rarity = HW_RARITY.get(parts[0]);
            if (rarity != null) {
                tag = rarity[0];
                tagColor = ColorUtils.withAlpha(Integer.parseInt(rarity[1], 16), 255);
                start = 1;
            }
            if (start < parts.length) {
                String rest = String.join("_", java.util.Arrays.copyOfRange(parts, start, parts.length));
                extra = HW_BIOMES.getOrDefault(rest, prettify(rest));
            }
        }

        HwStyle style = hwStyle(id, title);
        int from, to;
        String glyph;
        if (style != null) {
            from = style.from();
            to = style.to();
            glyph = style.glyph();
        } else {
            float hue = (Math.abs(id.hashCode()) % 360) / 360f;
            from = Color.HSBtoRGB(hue, 0.62f, 0.95f) | 0xFF000000;
            to = Color.HSBtoRGB((hue + 0.08f) % 1f, 0.75f, 0.55f) | 0xFF000000;
            glyph = "sparkle";
        }
        return new Card("hw|" + serverKey + "|" + id + "|" + nullToEmpty(rare), title, server, serverColor,
                "Активен", 0xFF4ADE80, true, tag, tagColor, extra,
                0L, group.size(), null, null, glyph(glyph), from, to);
    }

    private static HwStyle hwStyle(String id, String title) {
        String haystack = (id + " " + title).toLowerCase(Locale.ROOT);
        for (HwStyle style : HW_STYLES) {
            for (String key : style.keys()) if (haystack.contains(key)) return style;
        }
        return null;
    }

    private static Identifier glyph(String name) {
        return Identifier.fromNamespaceAndPath("opticumeclient", "textures/desire/menu/events/glyphs/" + name + ".png");
    }

    private static String get(String url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(10_000);
        connection.setReadTimeout(20_000);
        connection.setRequestProperty("User-Agent", AGENT);
        connection.setRequestProperty("Accept", "application/json");
        try {
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) throw new IOException("HTTP " + code + " " + url);
            try (InputStream in = connection.getInputStream()) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        } finally {
            connection.disconnect();
        }
    }

    private static String str(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if (element == null || !element.isJsonPrimitive()) return null;
        String value = element.getAsString().trim();
        return value.isEmpty() || value.equalsIgnoreCase("null") ? null : value;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String prettify(String raw) {
        if (raw == null || raw.isEmpty()) return "";
        String text = raw.replace('_', ' ').replace('-', ' ').trim().toLowerCase(Locale.ROOT);
        return text.isEmpty() ? raw : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    private static int compareNatural(String a, String b) {
        Matcher ma = Pattern.compile("(\\d+)").matcher(a), mb = Pattern.compile("(\\d+)").matcher(b);
        String pa = a.replaceAll("\\d+", ""), pb = b.replaceAll("\\d+", "");
        int prefix = pa.compareToIgnoreCase(pb);
        if (prefix != 0) return prefix;
        if (ma.find() && mb.find()) {
            int byNumber = Long.compare(Long.parseLong(ma.group(1)), Long.parseLong(mb.group(1)));
            if (byNumber != 0) return byNumber;
        }
        return a.compareToIgnoreCase(b);
    }
}
