package dev.opticumeclient.client.render.font;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

public final class MsdfFontData {
    public record Atlas(float distanceRange, float size, float width, float height, boolean yOriginBottom) {}
    public record Metrics(float lineHeight, float ascender, float descender) {}
    public record Bounds(float left, float bottom, float right, float top) {}
    public record GlyphEntry(int unicode, float advance, Bounds planeBounds, Bounds atlasBounds) {}
    public record KerningEntry(int unicode1, int unicode2, float advance) {}

    public final Atlas atlas;
    public final Metrics metrics;
    public final List<GlyphEntry> glyphs;
    public final List<KerningEntry> kernings;

    private MsdfFontData(Atlas atlas, Metrics metrics, List<GlyphEntry> glyphs, List<KerningEntry> kernings) {
        this.atlas = atlas;
        this.metrics = metrics;
        this.glyphs = glyphs;
        this.kernings = kernings;
    }

    public static MsdfFontData parse(Reader reader) {
        JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();

        JsonObject atlasObj = root.getAsJsonObject("atlas");
        float range = getFloat(atlasObj, "distanceRange", 12.0f);
        float size = getFloat(atlasObj, "size", 64.0f);
        float width = getFloat(atlasObj, "width", 512.0f);
        float height = getFloat(atlasObj, "height", 512.0f);
        String yOrigin = atlasObj.has("yOrigin") ? atlasObj.get("yOrigin").getAsString() : "bottom";
        Atlas atlas = new Atlas(range, size, width, height, !"top".equalsIgnoreCase(yOrigin));

        JsonObject metricsObj = root.getAsJsonObject("metrics");
        float lineHeight = getFloat(metricsObj, "lineHeight", 1.2f);
        float ascender = getFloat(metricsObj, "ascender", 0.9f);
        float descender = getFloat(metricsObj, "descender", -0.25f);
        Metrics metrics = new Metrics(lineHeight, ascender, descender);

        List<GlyphEntry> glyphs = new ArrayList<>();
        if (root.has("glyphs") && root.get("glyphs").isJsonArray()) {
            JsonArray arr = root.getAsJsonArray("glyphs");
            for (JsonElement el : arr) {
                if (!el.isJsonObject()) continue;
                JsonObject g = el.getAsJsonObject();
                int unicode = g.get("unicode").getAsInt();
                float advance = getFloat(g, "advance", 0.0f);
                Bounds plane = parseBounds(g.getAsJsonObject("planeBounds"));
                Bounds atlasB = parseBounds(g.getAsJsonObject("atlasBounds"));
                glyphs.add(new GlyphEntry(unicode, advance, plane, atlasB));
            }
        }

        List<KerningEntry> kernings = new ArrayList<>();
        if (root.has("kerning") && root.get("kerning").isJsonArray()) {
            JsonArray arr = root.getAsJsonArray("kerning");
            for (JsonElement el : arr) {
                if (!el.isJsonObject()) continue;
                JsonObject k = el.getAsJsonObject();
                kernings.add(new KerningEntry(
                        k.get("unicode1").getAsInt(),
                        k.get("unicode2").getAsInt(),
                        getFloat(k, "advance", 0.0f)
                ));
            }
        }

        return new MsdfFontData(atlas, metrics, glyphs, kernings);
    }

    private static Bounds parseBounds(JsonObject obj) {
        if (obj == null) return null;
        return new Bounds(
                getFloat(obj, "left", 0.0f),
                getFloat(obj, "bottom", 0.0f),
                getFloat(obj, "right", 0.0f),
                getFloat(obj, "top", 0.0f)
        );
    }

    private static float getFloat(JsonObject obj, String key, float def) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) return def;
        return obj.get(key).getAsFloat();
    }
}
