package dev.opticumeclient.client.render;

import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

public final class TextureAssets {
    // Asset textures copied from ThunderHack / CIS reference pack
    public static final Identifier FIREFLY = Identifier.fromNamespaceAndPath("opticumeclient", "textures/firefly.png");
    public static final Identifier STAR = Identifier.fromNamespaceAndPath("opticumeclient", "textures/star.png");
    public static final Identifier HEART = Identifier.fromNamespaceAndPath("opticumeclient", "textures/heart.png");
    public static final Identifier SNOWFLAKE = Identifier.fromNamespaceAndPath("opticumeclient", "textures/snowflake.png");
    public static final Identifier DOLLAR = Identifier.fromNamespaceAndPath("opticumeclient", "textures/dollar.png");
    public static final Identifier CIRCLE = Identifier.fromNamespaceAndPath("opticumeclient", "textures/circle.png");
    public static final Identifier HITBUBBLE = Identifier.fromNamespaceAndPath("opticumeclient", "textures/hitbubble.png");
    public static final Identifier CAPTURE = Identifier.fromNamespaceAndPath("opticumeclient", "textures/capture.png");
    public static final Identifier TRIANGLE = Identifier.fromNamespaceAndPath("opticumeclient", "textures/triangle.png");
    public static final Identifier THUD = Identifier.fromNamespaceAndPath("opticumeclient", "textures/thud.png");
    public static final Identifier MINI_LOGO = Identifier.fromNamespaceAndPath("opticumeclient", "textures/mini_logo.png");
    public static final Identifier FPS_ICON = Identifier.fromNamespaceAndPath("opticumeclient", "textures/fps.png");
    public static final Identifier PING_ICON = Identifier.fromNamespaceAndPath("opticumeclient", "textures/ping.png");
    public static final Identifier COORDS_ICON = Identifier.fromNamespaceAndPath("opticumeclient", "textures/coords.png");
    public static final Identifier SPEED_ICON = Identifier.fromNamespaceAndPath("opticumeclient", "textures/speedometer.png");
    public static final Identifier TPS_ICON = Identifier.fromNamespaceAndPath("opticumeclient", "textures/tps.png");

    // Menu & ClickGUI icons (assets/opticumeclient/textures/menu/icons/*.png)
    public static final Identifier MENU_SINGLEPLAYER = Identifier.fromNamespaceAndPath("opticumeclient", "textures/menu/icons/singleplayer.png");
    public static final Identifier MENU_MULTIPLAYER = Identifier.fromNamespaceAndPath("opticumeclient", "textures/menu/icons/multiplayer.png");
    public static final Identifier MENU_ALTMANAGER = Identifier.fromNamespaceAndPath("opticumeclient", "textures/menu/icons/altmanager.png");
    public static final Identifier MENU_SETTINGS = Identifier.fromNamespaceAndPath("opticumeclient", "textures/menu/icons/settings.png");
    public static final Identifier MENU_QUIT = Identifier.fromNamespaceAndPath("opticumeclient", "textures/menu/icons/quit.png");
    public static final Identifier MENU_PANORAMA = Identifier.fromNamespaceAndPath("opticumeclient", "textures/menu/icons/panorama.png");

    // Desire pre-baked PNG icons converted from SVGs (assets/opticumeclient/textures/desire/*)
    public static final Identifier DESIRE_MENU_SINGLEPLAYER = Identifier.fromNamespaceAndPath("opticumeclient", "textures/desire/menu/title/singleplayer.png");
    public static final Identifier DESIRE_MENU_MULTIPLAYER = Identifier.fromNamespaceAndPath("opticumeclient", "textures/desire/menu/title/multiplayer.png");
    public static final Identifier DESIRE_MENU_ALTMANAGER = Identifier.fromNamespaceAndPath("opticumeclient", "textures/desire/menu/title/accounts.png");
    public static final Identifier DESIRE_MENU_SETTINGS = Identifier.fromNamespaceAndPath("opticumeclient", "textures/desire/menu/title/options.png");
    public static final Identifier DESIRE_MENU_QUIT = Identifier.fromNamespaceAndPath("opticumeclient", "textures/desire/menu/title/quit.png");
    public static final Identifier DESIRE_GUI_CHECK = Identifier.fromNamespaceAndPath("opticumeclient", "textures/desire/menu/gui/check.png");
    public static final Identifier DESIRE_GUI_CHEVRON = Identifier.fromNamespaceAndPath("opticumeclient", "textures/desire/menu/gui/chevron.png");
    public static final Identifier DESIRE_GUI_CHEVRON_DOWN = Identifier.fromNamespaceAndPath("opticumeclient", "textures/desire/menu/gui/chevron_down.png");
    public static final Identifier DESIRE_GUI_CHEVRON_UP = Identifier.fromNamespaceAndPath("opticumeclient", "textures/desire/menu/gui/chevron_up.png");
    public static final Identifier DESIRE_GUI_INFO = Identifier.fromNamespaceAndPath("opticumeclient", "textures/desire/menu/gui/info_fill.png");
    public static final Identifier DESIRE_GUI_KEYBOARD = Identifier.fromNamespaceAndPath("opticumeclient", "textures/desire/menu/gui/keyboard_small.png");
    public static final Identifier DESIRE_GUI_KEYBOARD_BIG = Identifier.fromNamespaceAndPath("opticumeclient", "textures/desire/menu/gui/keyboard.png");
    public static final Identifier DESIRE_GUI_PENCIL = Identifier.fromNamespaceAndPath("opticumeclient", "textures/desire/menu/gui/pencil.png");
    public static final Identifier DESIRE_GUI_SEARCH = Identifier.fromNamespaceAndPath("opticumeclient", "textures/desire/menu/gui/search.png");
    public static final Identifier DESIRE_GUI_TRASH = Identifier.fromNamespaceAndPath("opticumeclient", "textures/desire/menu/gui/trash.png");
    public static final Identifier DESIRE_GUI_PALETTE = Identifier.fromNamespaceAndPath("opticumeclient", "textures/desire/menu/clickgui/palette.png");
    public static final Identifier DESIRE_GUI_AUTOBUY = Identifier.fromNamespaceAndPath("opticumeclient", "textures/desire/menu/clickgui/cart.png");
    public static final Identifier DESIRE_GUI_EVENTS = Identifier.fromNamespaceAndPath("opticumeclient", "textures/desire/menu/events/glyphs/sparkle.png");
    public static final Identifier DESIRE_EVENT_CLOCK = Identifier.fromNamespaceAndPath("opticumeclient", "textures/desire/menu/events/glyphs/clock.png");
    public static final Identifier DESIRE_EVENT_PIN = Identifier.fromNamespaceAndPath("opticumeclient", "textures/desire/menu/events/glyphs/pin.png");
    public static final Identifier DESIRE_EVENT_SERVER = Identifier.fromNamespaceAndPath("opticumeclient", "textures/desire/menu/events/glyphs/server.png");
    public static final Identifier DESIRE_EVENT_SPARKLE = Identifier.fromNamespaceAndPath("opticumeclient", "textures/desire/menu/events/glyphs/sparkle.png");
    public static final Identifier DESIRE_HUD_PIN = Identifier.fromNamespaceAndPath("opticumeclient", "textures/desire/hud/wm_pin.png");
    public static final Identifier DESIRE_HUD_CROSS = Identifier.fromNamespaceAndPath("opticumeclient", "textures/desire/hud/wm_cross.png");
    public static final Identifier DESIRE_HUD_USER = Identifier.fromNamespaceAndPath("opticumeclient", "textures/desire/hud/wm_user.png");
    public static final Identifier DESIRE_HUD_GAUGE = Identifier.fromNamespaceAndPath("opticumeclient", "textures/desire/hud/wm_gauge.png");
    public static final Identifier DESIRE_HUD_CLOCK = Identifier.fromNamespaceAndPath("opticumeclient", "textures/desire/hud/wm_clock.png");
    public static final Identifier DESIRE_HUD_HOME = Identifier.fromNamespaceAndPath("opticumeclient", "textures/desire/hud/wm_home.png");
    public static final Identifier DESIRE_HUD_GLOBE = Identifier.fromNamespaceAndPath("opticumeclient", "textures/desire/hud/wm_globe.png");
    public static final Identifier DESIRE_HUD_LAYERS = Identifier.fromNamespaceAndPath("opticumeclient", "textures/desire/hud/wm_layers.png");
    public static final Identifier DESIRE_HUD_STAR = Identifier.fromNamespaceAndPath("opticumeclient", "textures/desire/hud/wm_star.png");
    public static final Identifier DESIRE_HUD_BOLT = Identifier.fromNamespaceAndPath("opticumeclient", "textures/desire/hud/logo_bolt.png");
    public static final Identifier DESIRE_ICON_KEYBINDS = Identifier.fromNamespaceAndPath("opticumeclient", "textures/desire/menu/icons/keybinds.png");
    public static final Identifier DESIRE_ICON_POTIONS = Identifier.fromNamespaceAndPath("opticumeclient", "textures/desire/menu/icons/potions.png");
    public static final Identifier DESIRE_ICON_COOLDOWNS = Identifier.fromNamespaceAndPath("opticumeclient", "textures/desire/menu/icons/cooldowns.png");
    public static final Identifier DESIRE_GLOW = Identifier.fromNamespaceAndPath("opticumeclient", "textures/desire/images/esp/glow.png");
    public static final Identifier DESIRE_GPS_ARROW = Identifier.fromNamespaceAndPath("opticumeclient", "textures/desire/images/esp/arrow_gps.png");
    public static final Identifier DESIRE_ARROWS_TRIANGLE = Identifier.fromNamespaceAndPath("opticumeclient", "textures/desire/images/arrows/triangle.png");
    public static final Identifier DESIRE_NAMETAGS_HEART = Identifier.fromNamespaceAndPath("opticumeclient", "textures/desire/images/nametags/heart.png");
    public static final Identifier DESIRE_CIRCLE_GLOW = Identifier.fromNamespaceAndPath("opticumeclient", "textures/desire/images/circle/glow_fat.png");
    public static final Identifier DESIRE_CIRCLE_LEAN = Identifier.fromNamespaceAndPath("opticumeclient", "textures/desire/images/circle/lean.png");
    public static final Identifier DESIRE_JUMP_RING = Identifier.fromNamespaceAndPath("opticumeclient", "textures/desire/images/other_visuals/jump.png");
    public static final Identifier DESIRE_PARTICLE_GLOW = Identifier.fromNamespaceAndPath("opticumeclient", "textures/desire/images/particles/glow.png");
    public static final Identifier DESIRE_PARTICLE_HEX_ORBIZ = Identifier.fromNamespaceAndPath("opticumeclient", "textures/desire/images/particles/hex/orbiznew.png");
    public static final Identifier DESIRE_PARTICLE_HEX_STAR = Identifier.fromNamespaceAndPath("opticumeclient", "textures/desire/images/particles/hex/star.png");
    public static final Identifier DESIRE_PARTICLE_HEX_HEART = Identifier.fromNamespaceAndPath("opticumeclient", "textures/desire/images/particles/hex/heart.png");
    public static final Identifier DESIRE_PARTICLE_HEX_DOLLAR = Identifier.fromNamespaceAndPath("opticumeclient", "textures/desire/images/particles/hex/dollar.png");
    public static final Identifier DESIRE_PARTICLE_HEX_SNOWFLAKE = Identifier.fromNamespaceAndPath("opticumeclient", "textures/desire/images/particles/hex/snowflake.png");
    public static final Identifier DESIRE_PARTICLE_HEX_CROWN = Identifier.fromNamespaceAndPath("opticumeclient", "textures/desire/images/particles/hex/crown.png");
    public static final Identifier DESIRE_PARTICLE_HEX_LIGHTNING = Identifier.fromNamespaceAndPath("opticumeclient", "textures/desire/images/particles/hex/lightning.png");
    public static final Identifier DESIRE_PARTICLE_HEX_LINE = Identifier.fromNamespaceAndPath("opticumeclient", "textures/desire/images/particles/hex/line.png");
    public static final Identifier DESIRE_PARTICLE_HEX_POINT = Identifier.fromNamespaceAndPath("opticumeclient", "textures/desire/images/particles/hex/point.png");
    public static final Identifier DESIRE_PARTICLE_HEX_RHOMBUS = Identifier.fromNamespaceAndPath("opticumeclient", "textures/desire/images/particles/hex/rhombus.png");
    public static final Identifier DESIRE_PARTICLE_HEX_SPARK = Identifier.fromNamespaceAndPath("opticumeclient", "textures/desire/images/particles/hex/spark.png");

    public static final Identifier ICON_ALL = Identifier.fromNamespaceAndPath("opticumeclient", "textures/menu/icons/cat_all.png");
    public static final Identifier ICON_FAVORITES = Identifier.fromNamespaceAndPath("opticumeclient", "textures/menu/icons/cat_favorites.png");
    public static final Identifier ICON_STAR_FILLED = Identifier.fromNamespaceAndPath("opticumeclient", "textures/menu/icons/star_filled.png");
    public static final Identifier ICON_COMBAT = Identifier.fromNamespaceAndPath("opticumeclient", "textures/menu/icons/cat_combat.png");
    public static final Identifier ICON_VISUALS = Identifier.fromNamespaceAndPath("opticumeclient", "textures/menu/icons/cat_visuals.png");
    public static final Identifier ICON_OPTIMIZATION = Identifier.fromNamespaceAndPath("opticumeclient", "textures/menu/icons/cat_optimization.png");
    public static final Identifier ICON_HUD = Identifier.fromNamespaceAndPath("opticumeclient", "textures/menu/icons/cat_hud.png");
    public static final Identifier ICON_MOVEMENT = Identifier.fromNamespaceAndPath("opticumeclient", "textures/menu/icons/cat_movement.png");
    public static final Identifier ICON_FRIENDS = Identifier.fromNamespaceAndPath("opticumeclient", "textures/menu/icons/cat_friends.png");
    public static final Identifier ICON_CONFIGS = Identifier.fromNamespaceAndPath("opticumeclient", "textures/menu/icons/page_configs.png");
    public static final Identifier ICON_HUD_EDITOR = Identifier.fromNamespaceAndPath("opticumeclient", "textures/menu/icons/hud_editor.png");
    public static final Identifier ICON_SEARCH = Identifier.fromNamespaceAndPath("opticumeclient", "textures/menu/icons/search.png");
    public static final Identifier ICON_MOON = Identifier.fromNamespaceAndPath("opticumeclient", "textures/menu/icons/moon.png");
    public static final Identifier ICON_SUN = Identifier.fromNamespaceAndPath("opticumeclient", "textures/menu/icons/sun.png");
    public static final Identifier ICON_SIDEBAR = Identifier.fromNamespaceAndPath("opticumeclient", "textures/menu/icons/sidebar_toggle.png");
    public static final Identifier ICON_COLLAPSE = Identifier.fromNamespaceAndPath("opticumeclient", "textures/menu/icons/collapse.png");

    // Procedurally generated runtime textures (100% guaranteed to exist in TextureManager)
    public static final Identifier GLOW_RADIAL = Identifier.fromNamespaceAndPath("opticumeclient", "dynamic/glow_radial");
    public static final Identifier GLOW = GLOW_RADIAL;
    public static final Identifier RING_SOFT = Identifier.fromNamespaceAndPath("opticumeclient", "dynamic/ring_soft");
    public static final Identifier FIREFLY_RADIAL = Identifier.fromNamespaceAndPath("opticumeclient", "dynamic/firefly_radial");
    public static final Identifier CROSSHAIR_RING_HD = Identifier.fromNamespaceAndPath("opticumeclient", "dynamic/crosshair_ring_hd");
    public static final Identifier ROUNDED_CORNER_MASK = Identifier.fromNamespaceAndPath("opticumeclient", "dynamic/rounded_corner_mask");
    public static final Identifier ROUNDED_OUTLINE_MASK = Identifier.fromNamespaceAndPath("opticumeclient", "dynamic/rounded_outline_mask");
    public static final Identifier ROUNDED_INVERSE_CORNER_MASK = Identifier.fromNamespaceAndPath("opticumeclient", "dynamic/rounded_inverse_corner_mask");
    public static final Identifier COLOR_HUE_STRIP = Identifier.fromNamespaceAndPath("opticumeclient", "dynamic/color_hue_strip");
    public static final Identifier COLOR_SV_SAT_GRAD = Identifier.fromNamespaceAndPath("opticumeclient", "dynamic/color_sv_sat_grad");
    public static final Identifier COLOR_SV_VAL_GRAD = Identifier.fromNamespaceAndPath("opticumeclient", "dynamic/color_sv_val_grad");
    public static final Identifier COLOR_ALPHA_CHECKER = Identifier.fromNamespaceAndPath("opticumeclient", "dynamic/color_alpha_checker");
    public static final Identifier COLOR_ALPHA_GRAD = Identifier.fromNamespaceAndPath("opticumeclient", "dynamic/color_alpha_grad");

    public static final int CORNER_ATLAS_W = 512;
    public static final int CORNER_ATLAS_H = 64;
    public static final int MAX_ATLAS_RADIUS = 20;
    private static final int[] CORNER_U_OFFSETS = new int[MAX_ATLAS_RADIUS + 1];

    static {
        int u = 0;
        for (int r = 1; r <= MAX_ATLAS_RADIUS; r++) {
            CORNER_U_OFFSETS[r] = u;
            u += r * 2 + 2; // 2px padding between radius cells to prevent UV bleeding
        }
    }

    private static boolean initialized = false;

    private TextureAssets() {}

    public static int clampCornerRadius(int ir) {
        return Math.max(1, Math.min(MAX_ATLAS_RADIUS, ir));
    }

    public static int getCornerAtlasU(int ir) {
        return CORNER_U_OFFSETS[clampCornerRadius(ir)];
    }

    public static void ensureInitialized() {
        if (initialized) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getTextureManager() == null) return;
        try {
            // 1. Generate 128x128 radial Gaussian glow texture
            int size = 128;
            NativeImage glowImg = new NativeImage(size, size, false);
            float center = (size - 1) * 0.5f;
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    float dx = (x - center) / center;
                    float dy = (y - center) / center;
                    float distSq = dx * dx + dy * dy;
                    if (distSq >= 1.0f) {
                        glowImg.setPixel(x, y, 0x00FFFFFF);
                    } else {
                        float falloff = (float) Math.exp(-distSq * 4.2f) * (1.0f - distSq);
                        int alpha = Math.max(0, Math.min(255, (int) (falloff * 255.0f)));
                        glowImg.setPixel(x, y, (alpha << 24) | 0x00FFFFFF);
                    }
                }
            }
            DynamicTexture glowTex = new DynamicTexture(() -> "opticume_glow_radial", glowImg);
            mc.getTextureManager().register(GLOW_RADIAL, glowTex);

            // 2. Generate 128x128 smooth neon ring texture for JumpCircles fallback/overlay
            NativeImage ringImg = new NativeImage(size, size, false);
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    float dx = (x - center) / center;
                    float dy = (y - center) / center;
                    float dist = (float) Math.sqrt(dx * dx + dy * dy);
                    if (dist > 0.98f || dist < 0.25f) {
                        ringImg.setPixel(x, y, 0x00FFFFFF);
                    } else {
                        float ringDist = Math.abs(dist - 0.78f);
                        float mainRing = (float) Math.exp(-(ringDist * ringDist) / 0.008f);
                        float innerGlow = dist < 0.78f ? (float) Math.pow((dist - 0.25f) / 0.53f, 2.2f) * 0.45f : 0.0f;
                        int alpha = Math.max(0, Math.min(255, (int) (Math.min(1.0f, mainRing + innerGlow) * 255.0f)));
                        ringImg.setPixel(x, y, (alpha << 24) | 0x00FFFFFF);
                    }
                }
            }
            DynamicTexture ringTex = new DynamicTexture(() -> "opticume_ring_soft", ringImg);
            mc.getTextureManager().register(RING_SOFT, ringTex);

            // 3. Generate 128x128 soft radial firefly texture with gaussian falloff
            NativeImage ffImg = new NativeImage(size, size, false);
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    float dx = (x - center) / center;
                    float dy = (y - center) / center;
                    float distSq = dx * dx + dy * dy;
                    if (distSq >= 1.0f) {
                        ffImg.setPixel(x, y, 0x00FFFFFF);
                    } else {
                        float falloff = (float) Math.exp(-distSq * 3.5f) * (1.0f - (float) Math.sqrt(distSq));
                        int alpha = Math.max(0, Math.min(255, (int) (falloff * 255.0f)));
                        ffImg.setPixel(x, y, (alpha << 24) | 0x00FFFFFF);
                    }
                }
            }
            DynamicTexture ffTex = new DynamicTexture(() -> "opticume_firefly_radial", ffImg);
            mc.getTextureManager().register(FIREFLY_RADIAL, ffTex);

            // 4. Generate 128x128 anti-aliased crosshair ring texture
            NativeImage crossImg = new NativeImage(size, size, false);
            float midR = size * 0.38f;
            float halfThickness = size * 0.055f;
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    float dist = (float) Math.hypot(x - center, y - center);
                    float d = Math.abs(dist - midR);
                    float alphaFactor = Math.max(0.0f, Math.min(1.0f, 1.0f - (d - (halfThickness - 1.0f)) / 2.0f));
                    int alpha = (int) (alphaFactor * 255.0f);
                    crossImg.setPixel(x, y, (alpha << 24) | 0x00FFFFFF);
                }
            }
            DynamicTexture crossTex = new DynamicTexture(() -> "opticume_crosshair_ring_hd", crossImg);
            mc.getTextureManager().register(CROSSHAIR_RING_HD, crossTex);

            // 5. Generate 512x64 1:1 multi-radius 4x4-supersampled SDF corner & outline atlases (r = 1..20)
            NativeImage cornerImg = new NativeImage(CORNER_ATLAS_W, CORNER_ATLAS_H, false);
            NativeImage outlineImg = new NativeImage(CORNER_ATLAS_W, CORNER_ATLAS_H, false);
            NativeImage invCornerImg = new NativeImage(CORNER_ATLAS_W, CORNER_ATLAS_H, false);
            for (int y = 0; y < CORNER_ATLAS_H; y++) {
                for (int x = 0; x < CORNER_ATLAS_W; x++) {
                    cornerImg.setPixel(x, y, 0x00FFFFFF);
                    outlineImg.setPixel(x, y, 0x00FFFFFF);
                    invCornerImg.setPixel(x, y, 0x00FFFFFF);
                }
            }

            for (int r = 1; r <= MAX_ATLAS_RADIUS; r++) {
                int u0 = CORNER_U_OFFSETS[r];
                int diam = r * 2;
                float rOuter = (float) r;
                float rInner = Math.max(0.0f, r - 1.05f);
                for (int py = 0; py < diam; py++) {
                    for (int px = 0; px < diam; px++) {
                        // 4x4 subpixel supersampling for silky-smooth anti-aliased edges at exact 1:1 pixel scale
                        float fillCov = 0.0f;
                        float outCov = 0.0f;
                        for (int sy = 0; sy < 4; sy++) {
                            float subY = (py + (sy + 0.5f) * 0.25f) - r;
                            for (int sx = 0; sx < 4; sx++) {
                                float subX = (px + (sx + 0.5f) * 0.25f) - r;
                                float dist = (float) Math.hypot(subX, subY);
                                float cFill = Math.max(0.0f, Math.min(1.0f, rOuter - dist + 0.35f));
                                fillCov += cFill;

                                float cOutOuter = Math.max(0.0f, Math.min(1.0f, rOuter - dist + 0.35f));
                                float cOutInner = Math.max(0.0f, Math.min(1.0f, dist - rInner + 0.35f));
                                outCov += Math.min(cOutOuter, cOutInner);
                            }
                        }
                        int fillAlpha = Math.max(0, Math.min(255, Math.round((fillCov / 16.0f) * 255.0f)));
                        int outAlpha = Math.max(0, Math.min(255, Math.round((outCov / 16.0f) * 255.0f)));
                        int invAlpha = 255 - fillAlpha;
                        cornerImg.setPixel(u0 + px, py, (fillAlpha << 24) | 0x00FFFFFF);
                        outlineImg.setPixel(u0 + px, py, (outAlpha << 24) | 0x00FFFFFF);
                        invCornerImg.setPixel(u0 + px, py, (invAlpha << 24) | 0x00FFFFFF);
                    }
                }
            }

            DynamicTexture cornerTex = new DynamicTexture(() -> "opticume_rounded_corner_mask", cornerImg);
            mc.getTextureManager().register(ROUNDED_CORNER_MASK, cornerTex);

            DynamicTexture outlineTex = new DynamicTexture(() -> "opticume_rounded_outline_mask", outlineImg);
            mc.getTextureManager().register(ROUNDED_OUTLINE_MASK, outlineTex);

            DynamicTexture invCornerTex = new DynamicTexture(() -> "opticume_rounded_inverse_corner_mask", invCornerImg);
            mc.getTextureManager().register(ROUNDED_INVERSE_CORNER_MASK, invCornerTex);

            // 6. Generate 24-bit smooth Color Picker gradient textures (with 1px clamped edge padding)
            NativeImage hueImg = new NativeImage(258, 4, false);
            NativeImage satImg = new NativeImage(258, 4, false);
            NativeImage alphaGradImg = new NativeImage(258, 4, false);
            for (int x = 0; x < 258; x++) {
                float t = Math.max(0.0f, Math.min(1.0f, (x - 1) / 255.0f));
                int hueCol = ColorUtils.hsbToRgb(Math.min(0.9999f, t), 1.0f, 1.0f, 255);
                int satAlpha = Math.round((1.0f - t) * 255.0f);
                int satCol = (satAlpha << 24) | 0x00FFFFFF;
                int aGradAlpha = Math.round(t * 255.0f);
                int aGradCol = (aGradAlpha << 24) | 0x00FFFFFF;
                for (int y = 0; y < 4; y++) {
                    hueImg.setPixel(x, y, hueCol);
                    satImg.setPixel(x, y, satCol);
                    alphaGradImg.setPixel(x, y, aGradCol);
                }
            }
            mc.getTextureManager().register(COLOR_HUE_STRIP, new DynamicTexture(() -> "opticume_color_hue_strip", hueImg));
            mc.getTextureManager().register(COLOR_SV_SAT_GRAD, new DynamicTexture(() -> "opticume_color_sv_sat_grad", satImg));
            mc.getTextureManager().register(COLOR_ALPHA_GRAD, new DynamicTexture(() -> "opticume_color_alpha_grad", alphaGradImg));

            NativeImage valImg = new NativeImage(4, 258, false);
            for (int y = 0; y < 258; y++) {
                float t = Math.max(0.0f, Math.min(1.0f, (y - 1) / 255.0f));
                int valAlpha = Math.round(t * 255.0f);
                int valCol = valAlpha << 24; // black with gradient alpha from transparent top to opaque bottom
                for (int x = 0; x < 4; x++) {
                    valImg.setPixel(x, y, valCol);
                }
            }
            mc.getTextureManager().register(COLOR_SV_VAL_GRAD, new DynamicTexture(() -> "opticume_color_sv_val_grad", valImg));

            NativeImage checkerImg = new NativeImage(128, 16, false);
            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 128; x++) {
                    boolean light = ((x / 4) + (y / 4)) % 2 == 0;
                    checkerImg.setPixel(x, y, light ? 0xFF3E4250 : 0xFF22252F);
                }
            }
            mc.getTextureManager().register(COLOR_ALPHA_CHECKER, new DynamicTexture(() -> "opticume_color_alpha_checker", checkerImg));

            initialized = true;
        } catch (Throwable ignored) {
        }
    }
}
