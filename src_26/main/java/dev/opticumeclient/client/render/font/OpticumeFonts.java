package dev.opticumeclient.client.render.font;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.joml.Matrix3x2f;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class OpticumeFonts {
    public static final float BASE_EM_PX = 9.0f;

    public static final String KEY_ETERNAL_HEAVEN = "eternal_heaven";
    public static final String KEY_DREAMLINE_SF = "dreamline_sf";
    public static final String KEY_MENU_INTER = "menu_inter";
    public static final String KEY_HUD_MEDIUM = "hud_medium";
    public static final String KEY_HUD_SEMIBOLD = "hud_semibold";
    public static final String KEY_HUD_BOLD = "hud_bold";

    private static volatile MsdfFontFace modernFace;
    private static volatile MsdfFontFace smoothFace;
    private static volatile boolean modernAttempted;
    private static volatile boolean smoothAttempted;
    private static final Map<String, MsdfFontFace> EXTRA_FACES = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> EXTRA_ATTEMPTED = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> PIPELINE_WARNED = new ConcurrentHashMap<>();

    private OpticumeFonts() {
    }

    public static boolean isCustomStyle(String style) {
        return "Modern".equalsIgnoreCase(style) || "Smooth".equalsIgnoreCase(style);
    }

    /**
     * Eagerly loads core MSDF font metadata and warms up RenderPearl font pipelines on the render thread
     * so opening menus or joining crowded spawns does not cause first-frame shader compilation spikes.
     */
    public static void prewarm() {
        String[] directKeys = {
                KEY_HUD_MEDIUM, KEY_HUD_SEMIBOLD, KEY_MENU_INTER, KEY_ETERNAL_HEAVEN
        };
        for (String key : directKeys) {
            try {
                MsdfFontFace face = getDirectFace(key);
                if (face != null) {
                    face.getTextureView();
                    isPipelineReady(face);
                }
            } catch (Throwable ignored) {
            }
        }
        for (String style : new String[]{"Modern", "Smooth"}) {
            try {
                MsdfFontFace face = getFace(style);
                if (face != null) {
                    face.getTextureView();
                    isPipelineReady(face);
                }
            } catch (Throwable ignored) {
            }
        }
    }

    public static MsdfFontFace getFace(String style) {
        if ("Modern".equalsIgnoreCase(style)) {
            if (!modernAttempted) {
                synchronized (OpticumeFonts.class) {
                    if (!modernAttempted) {
                        modernFace = loadFace("modern", "fonts/modern/sf_regular.json", "fonts/modern/sf_regular.png");
                        modernAttempted = true;
                    }
                }
            }
            return modernFace;
        }
        if ("Smooth".equalsIgnoreCase(style)) {
            if (!smoothAttempted) {
                synchronized (OpticumeFonts.class) {
                    if (!smoothAttempted) {
                        smoothFace = loadFace("smooth", "fonts/smooth/productsans_regular.json", "fonts/smooth/productsans_regular.png");
                        smoothAttempted = true;
                    }
                }
            }
            return smoothFace;
        }
        return null;
    }

    public static boolean tryRender(
            GuiGraphicsExtractor context,
            String style,
            String text,
            float x,
            float y,
            float scale,
            int color,
            boolean shadow
    ) {
        if (context == null || text == null || text.isEmpty() || !isCustomStyle(style)) {
            return false;
        }
        MsdfFontFace face = getFace(style);
        if (face == null || !face.supportsText(text)) {
            return false;
        }
        GpuTextureView texView = face.getTextureView();
        if (texView == null || texView.isClosed()) {
            return false;
        }
        if (!isPipelineReady(face)) {
            return false;
        }

        if (((color >>> 24) & 0xFF) == 0) {
            return true;
        }

        if (!(((Object) context) instanceof OpticumeGuiContext guiCtx)) {
            return false;
        }

        float pxSize = BASE_EM_PX * Math.max(0.1f, scale);
        CustomFontRenderState state = new CustomFontRenderState(
                face,
                texView,
                text,
                x,
                y,
                pxSize,
                color,
                shadow,
                new Matrix3x2f(context.pose()),
                guiCtx.opticume$peekScissor()
        );
        guiCtx.opticume$submitElement(state);
        return true;
    }

    public static float getWidth(String style, String text, float scale) {
        if (text == null || text.isEmpty() || !isCustomStyle(style)) {
            return -1.0f;
        }
        MsdfFontFace face = getFace(style);
        if (face == null || !face.supportsText(text)) {
            return -1.0f;
        }
        return face.getWidth(text, BASE_EM_PX * Math.max(0.1f, scale));
    }

    public static MsdfFontFace getDirectFace(String key) {
        if (key == null) return null;
        MsdfFontFace cached = EXTRA_FACES.get(key);
        if (cached != null) return cached;
        if (Boolean.TRUE.equals(EXTRA_ATTEMPTED.get(key))) return null;
        synchronized (OpticumeFonts.class) {
            cached = EXTRA_FACES.get(key);
            if (cached != null) return cached;
            if (Boolean.TRUE.equals(EXTRA_ATTEMPTED.get(key))) return null;
            EXTRA_ATTEMPTED.put(key, Boolean.TRUE);
            String base = switch (key) {
                case KEY_ETERNAL_HEAVEN -> "fonts/desire/eternal/eternal_heaven";
                case KEY_DREAMLINE_SF -> "fonts/desire/dreamline/dreamlinesf";
                case KEY_MENU_INTER -> "fonts/desire/mainmenu/mainmenu_inter";
                case KEY_HUD_MEDIUM -> "fonts/desire/blade_sf/sf_pro_medium";
                case KEY_HUD_SEMIBOLD -> "fonts/desire/blade_sf/sf_pro_semibold";
                case KEY_HUD_BOLD -> "fonts/desire/blade_sf/sf_pro_bold";
                default -> null;
            };
            if (base == null) return null;
            MsdfFontFace loaded = loadFace(key, base + ".json", base + ".png");
            if (loaded != null) {
                EXTRA_FACES.put(key, loaded);
            }
            return loaded;
        }
    }

    public static float getDirectWidth(String key, String text, float pxSize) {
        if (text == null || text.isEmpty()) return 0.0f;
        MsdfFontFace face = getDirectFace(key);
        if (face == null) return 0.0f;
        return face.getWidth(text, pxSize);
    }

    public static boolean drawDirect(
            GuiGraphicsExtractor context,
            String key,
            String text,
            float x,
            float y,
            float pxSize,
            int color
    ) {
        return drawDirectGradient(context, key, text, x, y, pxSize, color, color, 0.0f, 3000L, false);
    }

    public static boolean drawDirectGradient(
            GuiGraphicsExtractor context,
            String key,
            String text,
            float x,
            float y,
            float pxSize,
            int color1,
            int color2,
            float gradientOffset,
            long gradientPeriod,
            boolean shadow
    ) {
        if (context == null || text == null || text.isEmpty() || (((color1 >>> 24) & 0xFF) == 0)) {
            return false;
        }
        MsdfFontFace face = getDirectFace(key);
        if (face == null || !face.supportsText(text)) return false;
        GpuTextureView texView = face.getTextureView();
        if (texView == null || texView.isClosed()) return false;
        if (!isPipelineReady(face)) return false;
        if (!(((Object) context) instanceof OpticumeGuiContext guiCtx)) return false;

        CustomFontRenderState state = new CustomFontRenderState(
                face,
                texView,
                text,
                x,
                y,
                pxSize,
                color1,
                color2,
                gradientOffset,
                gradientPeriod,
                true,
                shadow,
                new Matrix3x2f(context.pose()),
                guiCtx.opticume$peekScissor()
        );
        guiCtx.opticume$submitElement(state);
        return true;
    }

    private static boolean isPipelineReady(MsdfFontFace face) {
        try {
            if (RenderSystem.getCompiledPipelineNullable(face.getPipeline()) != null) {
                return true;
            }
            if (PIPELINE_WARNED.putIfAbsent(face.getId(), Boolean.TRUE) == null) {
                System.err.println("[OpticumeFonts] Compiled pipeline was null for font '" + face.getId() + "'");
            }
            return false;
        } catch (Throwable t) {
            if (PIPELINE_WARNED.putIfAbsent(face.getId(), Boolean.TRUE) == null) {
                System.err.println("[OpticumeFonts] Pipeline compile error for font '" + face.getId() + "': " + t);
            }
            return false;
        }
    }

    private static MsdfFontFace loadFace(String key, String jsonPath, String pngPath) {
        try {
            MsdfFontData data = readFontData(jsonPath);
            if (data == null || data.glyphs.isEmpty()) {
                System.err.println("[OpticumeFonts] Failed to load font metadata '" + key + "' from " + jsonPath);
                return null;
            }
            Identifier shaderId = Identifier.fromNamespaceAndPath("opticumeclient", "core/msdf_text");
            RenderPipeline pipeline = RenderPipeline.builder()
                    .withLocation(Identifier.fromNamespaceAndPath("opticumeclient", "pipeline/msdf_text_" + key))
                    .withBindGroupLayout(BindGroupLayouts.GLOBALS)
                    .withBindGroupLayout(BindGroupLayouts.PROJECTION)
                    .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
                    .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
                    .withVertexShader(shaderId)
                    .withFragmentShader(shaderId)
                    .withShaderDefine("MSDF_RANGE", data.atlas.distanceRange())
                    .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                    .withCull(false)
                    .withDepthStencilState(Optional.empty())
                    .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
                    .withPrimitiveTopology(PrimitiveTopology.QUADS)
                    .build();

            Identifier texId = Identifier.fromNamespaceAndPath("opticumeclient", pngPath);
            return new MsdfFontFace(key, texId, pipeline, data);
        } catch (Throwable t) {
            System.err.println("[OpticumeFonts] Failed to initialize font '" + key + "' (" + jsonPath + "): " + t);
            return null;
        }
    }

    private static MsdfFontData readFontData(String jsonPath) {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.getResourceManager() != null) {
            try {
                ResourceManager rm = mc.getResourceManager();
                Identifier id = Identifier.fromNamespaceAndPath("opticumeclient", jsonPath);
                Optional<Resource> res = rm.getResource(id);
                if (res.isPresent()) {
                    try (Reader reader = res.get().openAsReader()) {
                        return MsdfFontData.parse(reader);
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        try (InputStream is = OpticumeFonts.class.getClassLoader().getResourceAsStream("assets/opticumeclient/" + jsonPath)) {
            if (is != null) {
                try (Reader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
                    return MsdfFontData.parse(reader);
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }
}
