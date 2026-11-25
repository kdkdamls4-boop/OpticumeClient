package dev.opticumeclient.client.services.auth;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Microsoft OAuth 2.0 Device Code Flow -> Xbox Live -> XSTS -> Minecraft Services authentication client.
 */
public final class MicrosoftOAuthService {
    public record MsaAccount(String username, UUID uuid, String accessToken, long expiresAtMs) {
        public MsaAccount(String username, UUID uuid, String accessToken) {
            this(username, uuid, accessToken, System.currentTimeMillis() + 86_400_000L);
        }
    }

    private static final String CLIENT_ID = "c36a9fb6-4f2a-41ff-90bd-ae7cc92031eb";
    private static final String DEVICE_CODE_URL = "https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode";
    private static final String TOKEN_URL = "https://login.microsoftonline.com/consumers/oauth2/v2.0/token";

    private static final MicrosoftOAuthService INSTANCE = new MicrosoftOAuthService();
    public enum VerifyResult {
        VALID, REJECTED, UNAVAILABLE
    }

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
    private final AtomicLong flowGeneration = new AtomicLong(0L);
    private volatile Thread activeThread;
    private volatile String activeUserCode = "";
    private volatile String activeVerifyUrl = "";

    private MicrosoftOAuthService() {
    }

    public static MicrosoftOAuthService getInstance() {
        return INSTANCE;
    }

    public boolean isRunning() {
        Thread t = activeThread;
        return t != null && t.isAlive();
    }

    public String getActiveUserCode() {
        return isRunning() ? activeUserCode : "";
    }

    public String getActiveVerifyUrl() {
        return isRunning() ? activeVerifyUrl : "";
    }

    public synchronized void cancelActiveFlow() {
        flowGeneration.incrementAndGet();
        activeUserCode = "";
        activeVerifyUrl = "";
        Thread prev = activeThread;
        activeThread = null;
        if (prev != null) {
            prev.interrupt();
        }
    }

    public void verifyTokenAsync(String mcAccessToken, Consumer<VerifyResult> onResult) {
        if (onResult == null) return;
        if (mcAccessToken == null || mcAccessToken.isBlank()) {
            onResult.accept(VerifyResult.REJECTED);
            return;
        }
        Thread t = new Thread(() -> {
            VerifyResult result;
            try {
                HttpRequest req = HttpRequest.newBuilder(URI.create("https://api.minecraftservices.com/minecraft/profile"))
                        .timeout(Duration.ofSeconds(6))
                        .header("Authorization", "Bearer " + mcAccessToken)
                        .GET()
                        .build();
                HttpResponse<Void> resp = http.send(req, HttpResponse.BodyHandlers.discarding());
                int code = resp.statusCode();
                if (code == 200) {
                    result = VerifyResult.VALID;
                } else if (code == 401 || code == 403) {
                    result = VerifyResult.REJECTED;
                } else {
                    result = VerifyResult.UNAVAILABLE;
                }
            } catch (Throwable ignored) {
                result = VerifyResult.UNAVAILABLE;
            }
            onResult.accept(result);
        }, "opticume-ms-verify");
        t.setDaemon(true);
        t.start();
    }

    public synchronized void startDeviceCodeLogin(Consumer<String> onStatus,
                                                  Consumer<MsaAccount> onSuccess,
                                                  Consumer<String> onError) {
        final long gen = flowGeneration.incrementAndGet();
        activeUserCode = "";
        activeVerifyUrl = "";
        Thread prev = activeThread;
        if (prev != null) {
            prev.interrupt();
        }
        Consumer<String> safeStatus = msg -> {
            if (flowGeneration.get() == gen && onStatus != null) onStatus.accept(msg);
        };
        Consumer<MsaAccount> safeSuccess = acc -> {
            if (flowGeneration.get() == gen && onSuccess != null) onSuccess.accept(acc);
        };
        Consumer<String> safeError = err -> {
            if (flowGeneration.get() == gen && onError != null) onError.accept(err);
        };
        Thread t = new Thread(() -> {
            try {
                runDeviceFlow(gen, safeStatus, safeSuccess, safeError);
            } finally {
                synchronized (MicrosoftOAuthService.this) {
                    if (activeThread == Thread.currentThread()) {
                        activeThread = null;
                        activeUserCode = "";
                        activeVerifyUrl = "";
                    }
                }
            }
        }, "opticume-ms-oauth");
        activeThread = t;
        t.setDaemon(true);
        t.start();
    }

    private void runDeviceFlow(long gen,
                               Consumer<String> onStatus,
                               Consumer<MsaAccount> onSuccess,
                               Consumer<String> onError) {
        try {
            if (flowGeneration.get() != gen) return;
            String form = "client_id=" + enc(CLIENT_ID) + "&scope=" + enc("XboxLive.signin offline_access");
            JsonObject dc = postForm(DEVICE_CODE_URL, form);
            if (flowGeneration.get() != gen) return;
            if (dc == null || !dc.has("device_code") || !dc.has("user_code")) {
                onError.accept("Failed to start Microsoft device auth");
                return;
            }
            String deviceCode = dc.get("device_code").getAsString();
            String userCode = dc.get("user_code").getAsString();
            String verifyUrl = dc.has("verification_uri") ? dc.get("verification_uri").getAsString() : "https://www.microsoft.com/link";
            int intervalSec = dc.has("interval") ? Math.max(3, dc.get("interval").getAsInt()) : 5;
            int expiresSec = dc.has("expires_in") ? dc.get("expires_in").getAsInt() : 600;
            if (flowGeneration.get() == gen) {
                activeUserCode = userCode;
                activeVerifyUrl = verifyUrl;
            }

            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.keyboardHandler != null) {
                mc.execute(() -> {
                    if (flowGeneration.get() == gen) {
                        mc.keyboardHandler.setClipboard(userCode);
                    }
                });
            }
            openBrowser(verifyUrl);
            onStatus.accept("Code copied: " + userCode + " -> open " + verifyUrl);

            long deadline = System.currentTimeMillis() + expiresSec * 1000L;
            String msAccessToken = null;
            int transientFailures = 0;
            while (System.currentTimeMillis() < deadline && flowGeneration.get() == gen && !Thread.currentThread().isInterrupted()) {
                long sleepMs = Math.min(intervalSec * 1000L, Math.max(0L, deadline - System.currentTimeMillis()));
                if (sleepMs > 0L) {
                    Thread.sleep(sleepMs);
                }
                if (flowGeneration.get() != gen) return;
                String pollForm = "grant_type=urn:ietf:params:oauth:grant-type:device_code"
                        + "&client_id=" + enc(CLIENT_ID)
                        + "&device_code=" + enc(deviceCode);
                JsonObject tok;
                try {
                    tok = postForm(TOKEN_URL, pollForm);
                    transientFailures = 0;
                } catch (IOException transientErr) {
                    if (flowGeneration.get() != gen || Thread.currentThread().isInterrupted()) return;
                    transientFailures++;
                    long backoffMs = Math.min(15_000L, (long) intervalSec * 1000L * (1L << Math.min(3, transientFailures)));
                    long remainingMs = deadline - System.currentTimeMillis();
                    if (remainingMs <= 0L) break;
                    Thread.sleep(Math.min(backoffMs, remainingMs));
                    continue;
                }
                if (tok == null) continue;
                if (tok.has("access_token")) {
                    msAccessToken = tok.get("access_token").getAsString();
                    break;
                }
                if (tok.has("error")) {
                    String err = tok.get("error").getAsString();
                    if ("authorization_pending".equals(err)) continue;
                    if ("slow_down".equals(err)) {
                        intervalSec += 2;
                        continue;
                    }
                    onError.accept("Microsoft auth error: " + err);
                    return;
                }
            }
            if (flowGeneration.get() != gen) return;
            if (msAccessToken == null) {
                onError.accept("Microsoft OAuth timed out");
                return;
            }

            onStatus.accept("Exchanging Xbox Live token...");
            JsonObject xblReq = new JsonObject();
            JsonObject xblProps = new JsonObject();
            xblProps.addProperty("AuthMethod", "RPS");
            xblProps.addProperty("SiteName", "user.auth.xboxlive.com");
            xblProps.addProperty("RpsTicket", "d=" + msAccessToken);
            xblReq.add("Properties", xblProps);
            xblReq.addProperty("RelyingParty", "http://auth.xboxlive.com");
            xblReq.addProperty("TokenType", "JWT");

            JsonObject xblResp = postJson("https://user.auth.xboxlive.com/user/authenticate", xblReq);
            if (xblResp == null || !xblResp.has("Token")) {
                onError.accept("Xbox Live authentication failed");
                return;
            }
            String xblToken = xblResp.get("Token").getAsString();
            String uhs = xblResp.getAsJsonObject("DisplayClaims").getAsJsonArray("xui").get(0).getAsJsonObject().get("uhs").getAsString();

            onStatus.accept("Exchanging XSTS token...");
            JsonObject xstsReq = new JsonObject();
            JsonObject xstsProps = new JsonObject();
            xstsProps.addProperty("SandboxId", "RETAIL");
            JsonArray tokensArr = new JsonArray();
            tokensArr.add(xblToken);
            xstsProps.add("UserTokens", tokensArr);
            xstsReq.add("Properties", xstsProps);
            xstsReq.addProperty("RelyingParty", "rp://api.minecraftservices.com/");
            xstsReq.addProperty("TokenType", "JWT");

            JsonObject xstsResp = postJson("https://xsts.auth.xboxlive.com/xsts/authorize", xstsReq);
            if (xstsResp == null || !xstsResp.has("Token")) {
                onError.accept("XSTS authorization failed");
                return;
            }
            String xstsToken = xstsResp.get("Token").getAsString();

            onStatus.accept("Signing in to Minecraft Services...");
            JsonObject mcReq = new JsonObject();
            mcReq.addProperty("identityToken", "XBL3.0 x=" + uhs + ";" + xstsToken);
            JsonObject mcResp = postJson("https://api.minecraftservices.com/authentication/login_with_xbox", mcReq);
            if (mcResp == null || !mcResp.has("access_token")) {
                onError.accept("Minecraft token login failed");
                return;
            }
            String mcAccessToken = mcResp.get("access_token").getAsString();
            long expiresInSec = mcResp.has("expires_in") ? mcResp.get("expires_in").getAsLong() : 86_400L;
            long expiresAtMs = System.currentTimeMillis() + Math.max(60L, expiresInSec - 60L) * 1000L;

            HttpRequest profReq = HttpRequest.newBuilder(URI.create("https://api.minecraftservices.com/minecraft/profile"))
                    .timeout(Duration.ofSeconds(8))
                    .header("Authorization", "Bearer " + mcAccessToken)
                    .GET()
                    .build();
            HttpResponse<String> profResp = http.send(profReq, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (profResp.statusCode() != 200) {
                onError.accept("Account does not own Minecraft Java Edition");
                return;
            }
            JsonObject prof = JsonParser.parseString(profResp.body()).getAsJsonObject();
            String rawUuid = prof.get("id").getAsString();
            String name = prof.get("name").getAsString();
            UUID uuid = parseUndashedUuid(rawUuid);

            onSuccess.accept(new MsaAccount(name, uuid, mcAccessToken, expiresAtMs));
        } catch (Throwable t) {
            onError.accept("OAuth error: " + t.getMessage());
        }
    }

    private JsonObject postForm(String url, String body) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.body() == null || resp.body().isBlank()) return null;
        return JsonParser.parseString(resp.body()).getAsJsonObject();
    }

    private JsonObject postJson(String url, JsonObject payload) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString(), StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.body() == null || resp.body().isBlank()) return null;
        return JsonParser.parseString(resp.body()).getAsJsonObject();
    }

    private static UUID parseUndashedUuid(String hex) {
        if (hex.contains("-")) return UUID.fromString(hex);
        String dashed = hex.replaceFirst(
                "(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}+)",
                "$1-$2-$3-$4-$5"
        );
        return UUID.fromString(dashed);
    }

    private static void openBrowser(String url) {
        try {
            String os = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
            if (os.contains("win")) {
                new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", url).start();
            } else {
                new ProcessBuilder("xdg-open", url).start();
            }
        } catch (Throwable ignored) {
        }
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
