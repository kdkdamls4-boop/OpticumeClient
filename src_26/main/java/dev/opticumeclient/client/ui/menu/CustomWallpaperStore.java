package dev.opticumeclient.client.ui.menu;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import dev.opticumeclient.client.util.AtomicFileWriter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Manages user-uploaded custom wallpapers stored inside {@code <gameDir>/opticumeclient/wallpapers/}.
 * Validates dimensions before full decode, scales oversized backgrounds proportionally within 3840x2160,
 * generates crisp 512x512 center-square previews, and uploads DynamicTextures safely on render thread.
 */
public final class CustomWallpaperStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final long MAX_INPUT_BYTES = 32L * 1024L * 1024L;
    private static final long MAX_DECODED_PIXELS = 64_000_000L;
    private static final int MAX_RAW_DIM = 16_384;
    private static final int MAX_WIDTH = 3840;
    private static final int MAX_HEIGHT = 2160;
    private static final int PREVIEW_SIZE = 512;

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "opticume-custom-wallpapers");
        t.setDaemon(true);
        return t;
    });

    private static final List<MenuWallpapers.Wallpaper> CUSTOM_ENTRIES = new CopyOnWriteArrayList<>();
    private static final List<JsonElement> PRESERVED_INVALID_JSON = new CopyOnWriteArrayList<>();
    private static final Object IO_LOCK = new Object();
    private static volatile boolean loaded = false;

    private CustomWallpaperStore() {
    }

    public static File getWallpapersDir() {
        Minecraft mc = Minecraft.getInstance();
        File gameDir = (mc != null && mc.gameDirectory != null) ? mc.gameDirectory : new File(".");
        File root = new File(gameDir, "opticumeclient");
        File dir = new File(root, "wallpapers");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    public static List<MenuWallpapers.Wallpaper> getAll() {
        ensureLoaded();
        return List.copyOf(CUSTOM_ENTRIES);
    }

    public static boolean isSupportedExtension(Path p) {
        if (p == null) return false;
        String name = p.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg")
                || name.endsWith(".bmp") || name.endsWith(".gif");
    }

    public static void ensureLoaded() {
        Minecraft mc = Minecraft.getInstance();
        if (loaded || mc == null || !mc.isSameThread() || mc.getTextureManager() == null) return;
        synchronized (IO_LOCK) {
            if (loaded) return;
            File dir = getWallpapersDir();
            File indexFile = new File(dir, "custom_wallpapers.json");
            if (!indexFile.exists()) {
                loaded = true;
                return;
            }
            try (Reader reader = Files.newBufferedReader(indexFile.toPath(), StandardCharsets.UTF_8)) {
                JsonElement el = JsonParser.parseReader(reader);
                if (el == null || !el.isJsonArray()) {
                    return;
                }
                for (JsonElement item : el.getAsJsonArray()) {
                    try {
                        if (!item.isJsonObject()) {
                            PRESERVED_INVALID_JSON.add(item);
                            continue;
                        }
                        JsonObject obj = item.getAsJsonObject();
                        JsonElement idEl = obj.get("id");
                        JsonElement nameEl = obj.get("name");
                        JsonElement aspectEl = obj.get("aspect");
                        String id = (idEl != null && idEl.isJsonPrimitive()) ? idEl.getAsString() : "";
                        String name = (nameEl != null && nameEl.isJsonPrimitive()) ? nameEl.getAsString() : "Custom";
                        float aspect = (aspectEl != null && aspectEl.isJsonPrimitive()) ? aspectEl.getAsFloat() : 1.7778f;
                        if (!isSafeId(id)) {
                            PRESERVED_INVALID_JSON.add(item);
                            continue;
                        }

                        File fullFile = new File(dir, id + ".png");
                        File prevFile = new File(dir, id + "_preview.png");
                        if (!fullFile.exists() || !prevFile.exists()) {
                            continue;
                        }

                        Identifier fullTex = registerTextureFromFile(fullFile, id + "_full");
                        Identifier prevTex = registerTextureFromFile(prevFile, id + "_prev");
                        if (fullTex != null && prevTex != null) {
                            CUSTOM_ENTRIES.add(new MenuWallpapers.Wallpaper(
                                    id, name, fullTex, prevTex, Math.max(0.25f, Math.min(4.0f, aspect)), false,
                                    MenuWallpapers.Category.CUSTOM, true
                            ));
                        } else {
                            if (fullTex != null) mc.getTextureManager().release(fullTex);
                            if (prevTex != null) mc.getTextureManager().release(prevTex);
                            PRESERVED_INVALID_JSON.add(item);
                        }
                    } catch (Throwable entryErr) {
                        PRESERVED_INVALID_JSON.add(item);
                    }
                }
                loaded = true;
            } catch (Throwable t) {
                System.err.println("[OpticumeClient] Failed to load custom_wallpapers.json: " + t.getMessage());
            }
        }
    }

    public static void importFilesAsync(List<Path> paths, Consumer<String> onCompleteStatus) {
        if (paths == null || paths.isEmpty()) return;
        List<Path> copy = new ArrayList<>(paths);
        EXECUTOR.execute(() -> {
            int imported = 0;
            String lastError = null;

            for (Path src : copy) {
                if (src == null || !Files.isRegularFile(src)) continue;
                if (!isSupportedExtension(src)) {
                    lastError = "Unsupported format (use PNG/JPG/BMP/GIF)";
                    continue;
                }
                try {
                    if (Files.size(src) > MAX_INPUT_BYTES) {
                        lastError = "Image too large (max 32 MB): " + src.getFileName();
                        continue;
                    }
                    ImportedEntry entry = processImageToDisk(src);
                    if (entry != null) {
                        Minecraft mc = Minecraft.getInstance();
                        if (mc != null) {
                            CompletableFuture<Boolean> regDone = new CompletableFuture<>();
                            AtomicBoolean settled = new AtomicBoolean(false);
                            mc.execute(() -> {
                                try {
                                    if (settled.get()) {
                                        regDone.complete(false);
                                        return;
                                    }
                                    ensureLoaded();
                                    Identifier fullTex = registerTextureFromFile(entry.fullFile, entry.id + "_full");
                                    Identifier prevTex = registerTextureFromFile(entry.prevFile, entry.id + "_prev");
                                    if (fullTex != null && prevTex != null) {
                                        if (!settled.compareAndSet(false, true)) {
                                            mc.getTextureManager().release(fullTex);
                                            mc.getTextureManager().release(prevTex);
                                            regDone.complete(false);
                                            return;
                                        }
                                        MenuWallpapers.Wallpaper added = new MenuWallpapers.Wallpaper(
                                                entry.id, entry.name, fullTex, prevTex, entry.aspect, false,
                                                MenuWallpapers.Category.CUSTOM, true
                                        );
                                        CUSTOM_ENTRIES.add(added);
                                        if (saveIndex()) {
                                            MenuWallpapers.selectById(entry.id);
                                            regDone.complete(true);
                                        } else {
                                            CUSTOM_ENTRIES.remove(added);
                                            mc.getTextureManager().release(fullTex);
                                            mc.getTextureManager().release(prevTex);
                                            regDone.complete(false);
                                        }
                                    } else {
                                        if (fullTex != null) mc.getTextureManager().release(fullTex);
                                        if (prevTex != null) mc.getTextureManager().release(prevTex);
                                        regDone.complete(false);
                                    }
                                } catch (Throwable inner) {
                                    regDone.complete(false);
                                }
                            });
                            boolean ok = false;
                            try {
                                ok = Boolean.TRUE.equals(regDone.get(6, TimeUnit.SECONDS));
                            } catch (TimeoutException te) {
                                if (!settled.compareAndSet(false, true) && regDone.isDone()) {
                                    ok = Boolean.TRUE.equals(regDone.getNow(false));
                                } else {
                                    lastError = "Timed out registering wallpaper: " + src.getFileName();
                                }
                            }
                            if (ok) {
                                imported++;
                            } else if (lastError == null) {
                                lastError = "Failed to register or save wallpaper: " + src.getFileName();
                            }
                        }
                    } else {
                        lastError = "Could not decode image: " + src.getFileName();
                    }
                } catch (Throwable t) {
                    lastError = "Import failed: " + t.getMessage();
                }
            }

            if (onCompleteStatus != null) {
                String msg = imported > 0
                        ? (imported == 1 ? "Added custom wallpaper!" : ("Added " + imported + " wallpapers!"))
                        : (lastError != null ? lastError : "No valid images selected");
                Minecraft mc = Minecraft.getInstance();
                if (mc != null) {
                    mc.execute(() -> onCompleteStatus.accept(msg));
                }
            }
        });
    }

    public static boolean deleteCustom(String id) {
        ensureLoaded();
        if (!isSafeId(id)) return false;
        MenuWallpapers.Wallpaper target = null;
        for (MenuWallpapers.Wallpaper w : CUSTOM_ENTRIES) {
            if (w.id().equals(id)) {
                target = w;
                break;
            }
        }
        if (target == null) return false;
        int removedIdx = CUSTOM_ENTRIES.indexOf(target);
        CUSTOM_ENTRIES.remove(target);
        if (!saveIndex()) {
            if (removedIdx >= 0 && removedIdx <= CUSTOM_ENTRIES.size()) {
                CUSTOM_ENTRIES.add(removedIdx, target);
            } else {
                CUSTOM_ENTRIES.add(target);
            }
            return false;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.getTextureManager() != null) {
            final Identifier fTex = target.image();
            final Identifier pTex = target.preview();
            mc.execute(() -> {
                mc.getTextureManager().release(fTex);
                mc.getTextureManager().release(pTex);
            });
        }

        File dir = getWallpapersDir();
        try {
            Files.deleteIfExists(new File(dir, id + ".png").toPath());
            Files.deleteIfExists(new File(dir, id + "_preview.png").toPath());
        } catch (Throwable ignored) {
        }
        return true;
    }

    public static void openWallpapersFolder() {
        File dir = getWallpapersDir();
        try {
            String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
            if (os.contains("win")) {
                new ProcessBuilder("explorer.exe", dir.getAbsolutePath()).start();
            } else if (os.contains("mac")) {
                new ProcessBuilder("open", dir.getAbsolutePath()).start();
            } else {
                new ProcessBuilder("xdg-open", dir.getAbsolutePath()).start();
            }
        } catch (Throwable ignored) {
        }
    }

    private record ImportedEntry(String id, String name, float aspect, File fullFile, File prevFile) {
    }

    private static byte[] readBoundedBytes(Path path, long maxBytes) throws IOException {
        try (InputStream in = Files.newInputStream(path)) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            long total = 0L;
            int n;
            while ((n = in.read(buf)) != -1) {
                total += n;
                if (total > maxBytes) {
                    return null;
                }
                baos.write(buf, 0, n);
            }
            return baos.toByteArray();
        }
    }

    private static boolean validateImageBounds(byte[] rawBytes) {
        if (rawBytes == null || rawBytes.length == 0) return false;
        try (ImageInputStream iis = ImageIO.createImageInputStream(new ByteArrayInputStream(rawBytes))) {
            if (iis == null) return false;
            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            if (!readers.hasNext()) return false;
            ImageReader reader = readers.next();
            try {
                reader.setInput(iis, true, true);
                int rawW = reader.getWidth(0);
                int rawH = reader.getHeight(0);
                if (rawW <= 0 || rawH <= 0 || rawW > MAX_RAW_DIM || rawH > MAX_RAW_DIM) {
                    return false;
                }
                return (long) rawW * (long) rawH <= MAX_DECODED_PIXELS;
            } finally {
                reader.dispose();
            }
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static ImportedEntry processImageToDisk(Path src) throws Exception {
        byte[] rawBytes = readBoundedBytes(src, MAX_INPUT_BYTES);
        if (!validateImageBounds(rawBytes)) {
            return null;
        }

        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(rawBytes));
        if (decoded == null) return null;

        int srcW = decoded.getWidth();
        int srcH = decoded.getHeight();
        if (srcW <= 0 || srcH <= 0) return null;

        float scale = Math.min(1.0f, Math.min((float) MAX_WIDTH / srcW, (float) MAX_HEIGHT / srcH));
        int outW = Math.max(1, Math.round(srcW * scale));
        int outH = Math.max(1, Math.round(srcH * scale));

        BufferedImage fullRgb = new BufferedImage(outW, outH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g1 = fullRgb.createGraphics();
        try {
            g1.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g1.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g1.drawImage(decoded, 0, 0, outW, outH, null);
        } finally {
            g1.dispose();
        }

        int sq = Math.min(srcW, srcH);
        int cropX = Math.max(0, (srcW - sq) / 2);
        int cropY = Math.max(0, (srcH - sq) / 2);
        BufferedImage prevRgb = new BufferedImage(PREVIEW_SIZE, PREVIEW_SIZE, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = prevRgb.createGraphics();
        try {
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g2.drawImage(decoded, 0, 0, PREVIEW_SIZE, PREVIEW_SIZE, cropX, cropY, cropX + sq, cropY + sq, null);
        } finally {
            g2.dispose();
        }

        String id = "custom_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        String rawName = src.getFileName().toString();
        int dot = rawName.lastIndexOf('.');
        String baseName = (dot > 0 ? rawName.substring(0, dot) : rawName).replaceAll("[\\r\\n\\t]+", " ").trim();
        if (baseName.isEmpty()) baseName = "Custom Wallpaper";
        if (baseName.length() > 30) baseName = baseName.substring(0, 30).trim();

        File dir = getWallpapersDir();
        File fullFile = new File(dir, id + ".png");
        File prevFile = new File(dir, id + "_preview.png");
        writePngAtomic(fullRgb, fullFile.toPath());
        writePngAtomic(prevRgb, prevFile.toPath());

        float aspect = (float) outW / (float) outH;
        return new ImportedEntry(id, baseName, aspect, fullFile, prevFile);
    }

    private static void writePngAtomic(BufferedImage image, Path target) throws Exception {
        Path parent = target.getParent();
        if (parent != null) Files.createDirectories(parent);
        Path tmp = Files.createTempFile(parent != null ? parent : Path.of("."), target.getFileName().toString(), ".tmp");
        try {
            ImageIO.write(image, "PNG", tmp.toFile());
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    private static Identifier registerTextureFromFile(File file, String suffix) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getTextureManager() == null) return null;
        try {
            byte[] pngBytes = readBoundedBytes(file.toPath(), MAX_INPUT_BYTES);
            if (!validateImageBounds(pngBytes)) {
                return null;
            }
            NativeImage nativeImg = NativeImage.read(new ByteArrayInputStream(pngBytes));
            if (nativeImg == null) return null;
            Identifier texId = Identifier.fromNamespaceAndPath("opticumeclient", "menu/custom/" + suffix);
            DynamicTexture dyn = new DynamicTexture(() -> "opticume_" + suffix, nativeImg);
            mc.getTextureManager().register(texId, dyn);
            return texId;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean saveIndex() {
        synchronized (IO_LOCK) {
            if (!loaded) {
                return false;
            }
            File indexFile = new File(getWallpapersDir(), "custom_wallpapers.json");
            JsonArray arr = new JsonArray();
            for (MenuWallpapers.Wallpaper w : CUSTOM_ENTRIES) {
                JsonObject obj = new JsonObject();
                obj.addProperty("id", w.id());
                obj.addProperty("name", w.name());
                obj.addProperty("aspect", w.aspect());
                arr.add(obj);
            }
            for (JsonElement preserved : PRESERVED_INVALID_JSON) {
                arr.add(preserved);
            }
            try {
                AtomicFileWriter.writeUtf8(indexFile, writer -> GSON.toJson(arr, writer));
                return true;
            } catch (Throwable t) {
                System.err.println("[OpticumeClient] Failed to save custom_wallpapers.json: " + t.getMessage());
                return false;
            }
        }
    }

    private static boolean isSafeId(String id) {
        return id != null && id.matches("custom_[a-fA-F0-9]{6,32}");
    }
}
