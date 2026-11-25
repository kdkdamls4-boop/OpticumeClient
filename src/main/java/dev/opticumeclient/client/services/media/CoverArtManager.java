package dev.opticumeclient.client.services.media;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;

/**
 * Manages album cover textures via DynamicTexture with safe resource cleanup,
 * SMTC thumbnail decoding, iTunes Search 512x512 artwork fallback, and smooth coverFade.
 */
public final class CoverArtManager {
    private static final CoverArtManager INSTANCE = new CoverArtManager();
    public static final int MAX_METADATA_BYTES = 512 * 1024;
    private static final int MAX_IMAGE_BYTES = 4 * 1024 * 1024;
    private static final int MAX_IMAGE_DIM = 2048;
    private static final long MAX_IMAGE_PIXELS = 2048L * 2048L;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(4))
            .build();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "opticume-cover-art");
        t.setDaemon(true);
        return t;
    });

    private volatile String activeKey = "";
    private volatile String texturedKey = "";
    private volatile Identifier activeTextureId = null;
    private volatile float coverFade = 0.0f;
    private long lastFadeNanos = System.nanoTime();
    private int textureSeq = 0;

    private CoverArtManager() {
    }

    public static CoverArtManager getInstance() {
        return INSTANCE;
    }

    public void onTrackChanged(String artist, String title, String smtcThumbBase64) {
        String key = (artist == null ? "" : artist.trim()) + "|" + (title == null ? "" : title.trim());
        activeKey = key;
        coverFade = 0.0f;
        if (key.equals("|") || title == null || title.isBlank()) {
            releaseOnMainThread();
            return;
        }
        releaseStaleOnMainThread(key);
        executor.execute(() -> {
            byte[] pngBytes = null;
            if (smtcThumbBase64 != null && !smtcThumbBase64.isBlank()) {
                pngBytes = decodeBase64ToPng(smtcThumbBase64);
            }
            if (pngBytes == null) {
                pngBytes = fetchItunesArtworkPng(artist, title);
            }
            if (pngBytes == null || !key.equals(activeKey)) return;
            uploadTexture(key, pngBytes);
        });
    }

    public void supplySmtcThumbnail(String key, String smtcThumbBase64) {
        if (smtcThumbBase64 == null || smtcThumbBase64.isBlank() || !key.equals(activeKey)) return;
        if (activeTextureId != null && key.equals(texturedKey)) return;
        executor.execute(() -> {
            byte[] png = decodeBase64ToPng(smtcThumbBase64);
            if (png != null && key.equals(activeKey)) {
                uploadTexture(key, png);
            }
        });
    }

    public Identifier getTextureId() {
        return activeTextureId;
    }

    public float updateFade() {
        long now = System.nanoTime();
        float dt = Math.min(0.05f, Math.max(0.0001f, (now - lastFadeNanos) / 1.0e9f));
        lastFadeNanos = now;
        float target = activeTextureId != null ? 1.0f : 0.0f;
        coverFade += (target - coverFade) * (1.0f - (float) Math.exp(-10.0f * dt));
        return Mth.clamp(coverFade, 0.0f, 1.0f);
    }

    public void releaseOnMainThread() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        Runnable r = () -> {
            if (activeTextureId != null && mc.getTextureManager() != null) {
                mc.getTextureManager().release(activeTextureId);
            }
            activeTextureId = null;
            texturedKey = "";
        };
        if (mc.isSameThread()) r.run();
        else mc.execute(r);
    }

    private void releaseStaleOnMainThread(String expectedKey) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        Runnable r = () -> {
            if (activeTextureId != null && !expectedKey.equals(texturedKey)) {
                if (mc.getTextureManager() != null) {
                    mc.getTextureManager().release(activeTextureId);
                }
                activeTextureId = null;
                texturedKey = "";
            }
        };
        if (mc.isSameThread()) r.run();
        else mc.execute(r);
    }

    private void uploadTexture(String key, byte[] pngBytes) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        mc.execute(() -> {
            if (!key.equals(activeKey)) return;
            try (ByteArrayInputStream bais = new ByteArrayInputStream(pngBytes)) {
                NativeImage image = NativeImage.read(bais);
                if (image == null) return;
                if (activeTextureId != null) {
                    mc.getTextureManager().release(activeTextureId);
                }
                Identifier nextId = Identifier.fromNamespaceAndPath("opticumeclient", "cover_art_" + (++textureSeq));
                DynamicTexture dyn = new DynamicTexture(() -> "opticume_cover_" + textureSeq, image);
                mc.getTextureManager().register(nextId, dyn);
                activeTextureId = nextId;
                texturedKey = key;
                coverFade = 0.0f;
            } catch (Throwable ignored) {
            }
        });
    }

    private byte[] fetchItunesArtworkPng(String artist, String title) {
        try {
            String term = ((artist == null ? "" : artist) + " " + title).trim();
            String url = "https://itunes.apple.com/search?term=" + URLEncoder.encode(term, StandardCharsets.UTF_8)
                    + "&entity=song&limit=1";
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(4))
                    .GET()
                    .build();
            HttpResponse<String> resp = http.send(req, boundedStringHandler(MAX_METADATA_BYTES));
            if (resp.statusCode() != 200 || resp.body() == null) return null;
            JsonElement parsed = JsonParser.parseString(resp.body());
            if (!parsed.isJsonObject()) return null;
            JsonArray arr = parsed.getAsJsonObject().getAsJsonArray("results");
            if (arr == null || arr.isEmpty()) return null;
            JsonObject first = arr.get(0).getAsJsonObject();
            if (!first.has("artworkUrl100")) return null;
            String artUrl = first.get("artworkUrl100").getAsString().replace("100x100bb", "512x512bb").replace("100x100", "512x512");
            HttpRequest imgReq = HttpRequest.newBuilder(URI.create(artUrl)).timeout(Duration.ofSeconds(4)).GET().build();
            CompletableFuture<HttpResponse<byte[]>> fut = http.sendAsync(imgReq, boundedByteArrayHandler(MAX_IMAGE_BYTES));
            HttpResponse<byte[]> imgResp;
            try {
                imgResp = fut.get(6, TimeUnit.SECONDS);
            } finally {
                fut.cancel(true);
            }
            if (imgResp.statusCode() != 200 || imgResp.body() == null) return null;
            return decodeToPng(imgResp.body());
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static HttpResponse.BodyHandler<String> boundedStringHandler(int maxBytes) {
        return responseInfo -> new HttpResponse.BodySubscriber<>() {
            private final CompletableFuture<String> bodyCf = new CompletableFuture<>();
            private final ByteArrayOutputStream baos = new ByteArrayOutputStream();
            private Flow.Subscription subscription;

            @Override
            public CompletionStage<String> getBody() {
                return bodyCf;
            }

            @Override
            public void onSubscribe(Flow.Subscription s) {
                this.subscription = s;
                s.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(List<ByteBuffer> items) {
                for (ByteBuffer buf : items) {
                    int rem = buf.remaining();
                    if (baos.size() + rem > maxBytes) {
                        subscription.cancel();
                        bodyCf.completeExceptionally(new IOException("Metadata response exceeded " + maxBytes + " bytes"));
                        return;
                    }
                    byte[] tmp = new byte[rem];
                    buf.get(tmp);
                    baos.write(tmp, 0, tmp.length);
                }
            }

            @Override
            public void onError(Throwable throwable) {
                bodyCf.completeExceptionally(throwable);
            }

            @Override
            public void onComplete() {
                bodyCf.complete(baos.toString(StandardCharsets.UTF_8));
            }
        };
    }

    private static HttpResponse.BodyHandler<byte[]> boundedByteArrayHandler(int maxBytes) {
        return responseInfo -> {
            if (responseInfo.statusCode() != 200) {
                return HttpResponse.BodySubscribers.replacing(null);
            }
            return new HttpResponse.BodySubscriber<>() {
                private final CompletableFuture<byte[]> bodyCf = new CompletableFuture<>();
                private final ByteArrayOutputStream baos = new ByteArrayOutputStream();
                private Flow.Subscription subscription;

                @Override
                public CompletionStage<byte[]> getBody() {
                    return bodyCf;
                }

                @Override
                public void onSubscribe(Flow.Subscription s) {
                    this.subscription = s;
                    s.request(Long.MAX_VALUE);
                }

                @Override
                public void onNext(List<ByteBuffer> items) {
                    for (ByteBuffer buf : items) {
                        int rem = buf.remaining();
                        if (baos.size() + rem > maxBytes) {
                            subscription.cancel();
                            bodyCf.completeExceptionally(new IOException("Artwork exceeded " + maxBytes + " bytes"));
                            return;
                        }
                        byte[] tmp = new byte[rem];
                        buf.get(tmp);
                        baos.write(tmp, 0, tmp.length);
                    }
                }

                @Override
                public void onError(Throwable throwable) {
                    bodyCf.completeExceptionally(throwable);
                }

                @Override
                public void onComplete() {
                    bodyCf.complete(baos.toByteArray());
                }
            };
        };
    }

    private static byte[] decodeBase64ToPng(String base64) {
        if (base64 == null || base64.isBlank()) return null;
        long maxB64Chars = ((long) MAX_IMAGE_BYTES * 4L) / 3L + 16L;
        if (base64.length() > maxB64Chars) return null;
        try {
            return decodeToPng(Base64.getDecoder().decode(base64));
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static byte[] decodeToPng(byte[] raw) {
        if (raw == null || raw.length == 0 || raw.length > MAX_IMAGE_BYTES) return null;
        try (ImageInputStream iis = ImageIO.createImageInputStream(new ByteArrayInputStream(raw))) {
            if (iis == null) return null;
            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            if (!readers.hasNext()) return null;
            ImageReader reader = readers.next();
            try {
                reader.setInput(iis, true, true);
                int w = reader.getWidth(0);
                int h = reader.getHeight(0);
                if (w <= 0 || h <= 0 || w > MAX_IMAGE_DIM || h > MAX_IMAGE_DIM || (long) w * h > MAX_IMAGE_PIXELS) {
                    return null;
                }
                BufferedImage img = reader.read(0);
                if (img == null) return null;
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(img, "PNG", baos);
                return baos.toByteArray();
            } finally {
                reader.dispose();
            }
        } catch (Throwable ignored) {
            return null;
        }
    }
}
