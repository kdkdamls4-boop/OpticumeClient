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
import java.util.Optional;

public final class OpticumeFonts {
    public static final float BASE_EM_PX = 9.0f;

    private static volatile MsdfFontFace modernFace;
    private static volatile MsdfFontFace smoothFace;
    private static volatile boolean modernAttempted;
    private static volatile boolean smoothAttempted;

    private OpticumeFonts() {
    }

    public static boolean isCustomStyle(String style) {
        return "Modern".equalsIgnoreCase(style) || "Smooth".equalsIgnoreCase(style);
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
        try {
            if (RenderSystem.getCompiledPipelineNullable(face.getPipeline()) == null) {
                return false;
            }
        } catch (Throwable ignored) {
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

    private static MsdfFontFace loadFace(String key, String jsonPath, String pngPath) {
        try {
            MsdfFontData data = readFontData(jsonPath);
            if (data == null || data.glyphs.isEmpty()) {
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
                    .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
                    .withPrimitiveTopology(PrimitiveTopology.QUADS)
                    .build();

            Identifier texId = Identifier.fromNamespaceAndPath("opticumeclient", pngPath);
            return new MsdfFontFace(key, texId, pipeline, data);
        } catch (Throwable ignored) {
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
