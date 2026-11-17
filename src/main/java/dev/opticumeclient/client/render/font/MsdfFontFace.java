package dev.opticumeclient.client.render.font;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2fc;

import java.util.HashMap;
import java.util.Map;

public final class MsdfFontFace {
    private static final int[] MC_COLORS = {
            0x000000, // 0 black
            0x0000AA, // 1 dark_blue
            0x00AA00, // 2 dark_green
            0x00AAAA, // 3 dark_aqua
            0xAA0000, // 4 dark_red
            0xAA00AA, // 5 dark_purple
            0xFFAA00, // 6 gold
            0xAAAAAA, // 7 gray
            0x555555, // 8 dark_gray
            0x5555FF, // 9 blue
            0x55FF55, // a green
            0x55FFFF, // b aqua
            0xFF5555, // c red
            0xFF55FF, // d light_purple
            0xFFFF55, // e yellow
            0xFFFFFF  // f white
    };

    public static final class Glyph {
        public final int unicode;
        public final float advance;
        public final boolean visible;
        public final float planeLeft;
        public final float planeBottom;
        public final float planeRight;
        public final float planeTop;
        public final float minU;
        public final float minV;
        public final float maxU;
        public final float maxV;

        public Glyph(MsdfFontData.GlyphEntry entry, float atlasW, float atlasH, boolean yOriginBottom) {
            this.unicode = entry.unicode();
            this.advance = entry.advance();
            if (entry.planeBounds() != null && entry.atlasBounds() != null) {
                this.visible = true;
                this.planeLeft = entry.planeBounds().left();
                this.planeBottom = entry.planeBounds().bottom();
                this.planeRight = entry.planeBounds().right();
                this.planeTop = entry.planeBounds().top();
                this.minU = entry.atlasBounds().left() / atlasW;
                this.maxU = entry.atlasBounds().right() / atlasW;
                if (yOriginBottom) {
                    this.minV = 1.0f - entry.atlasBounds().top() / atlasH;
                    this.maxV = 1.0f - entry.atlasBounds().bottom() / atlasH;
                } else {
                    this.minV = entry.atlasBounds().top() / atlasH;
                    this.maxV = entry.atlasBounds().bottom() / atlasH;
                }
            } else {
                this.visible = false;
                this.planeLeft = this.planeBottom = this.planeRight = this.planeTop = 0.0f;
                this.minU = this.minV = this.maxU = this.maxV = 0.0f;
            }
        }
    }

    private final String id;
    private final Identifier textureId;
    private final RenderPipeline pipeline;
    private final float lineHeight;
    private final float ascender;
    private final Glyph[] asciiGlyphs = new Glyph[256];
    private final Map<Integer, Glyph> extraGlyphs = new HashMap<>();
    private final Map<Long, Float> kernings = new HashMap<>();

    public MsdfFontFace(String id, Identifier textureId, RenderPipeline pipeline, MsdfFontData data) {
        this.id = id;
        this.textureId = textureId;
        this.pipeline = pipeline;
        this.lineHeight = data.metrics.lineHeight();
        this.ascender = data.metrics.ascender();

        float aw = Math.max(1.0f, data.atlas.width());
        float ah = Math.max(1.0f, data.atlas.height());
        boolean yBottom = data.atlas.yOriginBottom();

        for (MsdfFontData.GlyphEntry entry : data.glyphs) {
            Glyph glyph = new Glyph(entry, aw, ah, yBottom);
            int cp = glyph.unicode;
            if (cp >= 0 && cp < 256) {
                asciiGlyphs[cp] = glyph;
            } else {
                extraGlyphs.put(cp, glyph);
            }
        }

        for (MsdfFontData.KerningEntry k : data.kernings) {
            long key = (((long) k.unicode1()) << 32) | (k.unicode2() & 0xFFFFFFFFL);
            kernings.put(key, k.advance());
        }
    }

    public String getId() {
        return id;
    }

    public RenderPipeline getPipeline() {
        return pipeline;
    }

    public float getLineHeight() {
        return lineHeight;
    }

    public float getAscender() {
        return ascender;
    }

    public GpuTextureView getTextureView() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getTextureManager() == null) return null;
        AbstractTexture tex = mc.getTextureManager().getTexture(textureId);
        return tex != null ? tex.getTextureView() : null;
    }

    public Glyph getGlyphRaw(int cp) {
        if (cp >= 0 && cp < 256) {
            return asciiGlyphs[cp];
        }
        return extraGlyphs.get(cp);
    }

    public Glyph getGlyph(int cp) {
        Glyph g = getGlyphRaw(cp);
        if (g != null) return g;

        int fb = switch (cp) {
            case 0x2022 -> 0x00B7; // • -> ·
            case 0x2014 -> 0x2013; // — -> –
            case 0x00D7 -> 'x';    // × -> x
            case 0x25B2, 0x2191 -> '^'; // ▲ / ↑ -> ^
            case 0x25BC, 0x2193 -> 'v'; // ▼ / ↓ -> v
            case 0x2606, 0x2605 -> '*'; // ☆ / ★ -> *
            case 0x00B0 -> 'o';    // ° -> o
            case 0x2018, 0x2019 -> '\'';
            case 0x201C, 0x201D -> '"';
            case 0x00A0 -> ' ';
            default -> -1;
        };
        if (fb >= 0) {
            g = getGlyphRaw(fb);
            if (g != null) return g;
        }
        if (cp == 0x2022 || cp == 0x2013 || cp == 0x2014) {
            g = getGlyphRaw('-');
            if (g != null) return g;
            return getGlyphRaw('*');
        }
        if (cp == 0x00B7) {
            return getGlyphRaw('.');
        }
        return null;
    }

    public boolean supportsText(String text) {
        if (text == null || text.isEmpty()) return true;
        int len = text.length();
        for (int i = 0; i < len; i++) {
            char c = text.charAt(i);
            if (c == '\u00A7' && i + 1 < len) {
                i++;
                continue;
            }
            if (c == '\n' || c == '\r' || c == '\t') continue;
            if (getGlyph(c) == null) {
                return false;
            }
        }
        return true;
    }

    public float getWidth(String text, float pxSize) {
        if (text == null || text.isEmpty()) return 0.0f;
        float width = 0.0f;
        int prev = -1;
        int len = text.length();
        for (int i = 0; i < len; i++) {
            char c = text.charAt(i);
            if (c == '\u00A7' && i + 1 < len) {
                i++;
                continue;
            }
            if (c == '\t') {
                Glyph sp = getGlyph(' ');
                width += (sp != null ? sp.advance : 0.28f) * 4.0f * pxSize;
                prev = ' ';
                continue;
            }
            Glyph g = getGlyph(c);
            if (g == null) {
                prev = -1;
                continue;
            }
            if (prev >= 0 && !kernings.isEmpty()) {
                Float kern = kernings.get((((long) prev) << 32) | (g.unicode & 0xFFFFFFFFL));
                if (kern != null) {
                    width += kern * pxSize;
                }
            }
            width += g.advance * pxSize;
            prev = g.unicode;
        }
        return width;
    }

    public void emitText(VertexConsumer vc, Matrix3x2fc pose, String text, float x, float y, float pxSize, int baseColor, boolean shadow) {
        if (text == null || text.isEmpty()) return;
        float baselineY = y + this.ascender * pxSize * 0.90f;
        if (shadow) {
            float shadowOffset = Math.max(0.55f, pxSize * 0.075f);
            emitPass(vc, pose, text, x + shadowOffset, baselineY + shadowOffset, pxSize, baseColor, true);
        }
        emitPass(vc, pose, text, x, baselineY, pxSize, baseColor, false);
    }

    private void emitPass(VertexConsumer vc, Matrix3x2fc pose, String text, float startX, float baselineY, float pxSize, int baseColor, boolean shadowPass) {
        int baseAlpha = (baseColor >>> 24) & 0xFF;
        if (baseAlpha == 0) return;

        float cursorX = startX;
        int curColor = shadowPass ? toShadowColor(baseColor) : baseColor;
        int prev = -1;
        int len = text.length();

        for (int i = 0; i < len; i++) {
            char c = text.charAt(i);
            if (c == '\u00A7' && i + 1 < len) {
                char code = Character.toLowerCase(text.charAt(i + 1));
                int colorIdx = hexIndex(code);
                if (colorIdx >= 0) {
                    int rgb = MC_COLORS[colorIdx];
                    int resolved = (baseAlpha << 24) | rgb;
                    curColor = shadowPass ? toShadowColor(resolved) : resolved;
                } else if (code == 'r') {
                    curColor = shadowPass ? toShadowColor(baseColor) : baseColor;
                }
                i++;
                continue;
            }
            if (c == '\t') {
                Glyph sp = getGlyph(' ');
                cursorX += (sp != null ? sp.advance : 0.28f) * 4.0f * pxSize;
                prev = ' ';
                continue;
            }
            Glyph g = getGlyph(c);
            if (g == null) {
                prev = -1;
                continue;
            }
            if (prev >= 0 && !kernings.isEmpty()) {
                Float kern = kernings.get((((long) prev) << 32) | (g.unicode & 0xFFFFFFFFL));
                if (kern != null) {
                    cursorX += kern * pxSize;
                }
            }
            if (g.visible) {
                float x0 = cursorX + g.planeLeft * pxSize;
                float x1 = cursorX + g.planeRight * pxSize;
                float y0 = baselineY - g.planeTop * pxSize;
                float y1 = baselineY - g.planeBottom * pxSize;
                vc.addVertexWith2DPose(pose, x0, y0).setUv(g.minU, g.minV).setColor(curColor);
                vc.addVertexWith2DPose(pose, x0, y1).setUv(g.minU, g.maxV).setColor(curColor);
                vc.addVertexWith2DPose(pose, x1, y1).setUv(g.maxU, g.maxV).setColor(curColor);
                vc.addVertexWith2DPose(pose, x1, y0).setUv(g.maxU, g.minV).setColor(curColor);
            }
            cursorX += g.advance * pxSize;
            prev = g.unicode;
        }
    }

    private static int toShadowColor(int argb) {
        int a = (argb >>> 24) & 0xFF;
        int r = ((argb >>> 16) & 0xFF) >> 2;
        int g = ((argb >>> 8) & 0xFF) >> 2;
        int b = (argb & 0xFF) >> 2;
        int sa = Math.max(0, Math.min(255, Math.round(a * 0.78f)));
        return (sa << 24) | (r << 16) | (g << 8) | b;
    }

    private static int hexIndex(char c) {
        if (c >= '0' && c <= '9') return c - '0';
        if (c >= 'a' && c <= 'f') return 10 + (c - 'a');
        return -1;
    }
}
