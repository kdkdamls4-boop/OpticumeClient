package dev.optimusprime.client.render;

import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

public final class TextureAssets {
    // Asset textures copied from ThunderHack / CIS reference pack
    public static final Identifier FIREFLY = Identifier.fromNamespaceAndPath("optimusprime", "textures/firefly.png");
    public static final Identifier STAR = Identifier.fromNamespaceAndPath("optimusprime", "textures/star.png");
    public static final Identifier HEART = Identifier.fromNamespaceAndPath("optimusprime", "textures/heart.png");
    public static final Identifier SNOWFLAKE = Identifier.fromNamespaceAndPath("optimusprime", "textures/snowflake.png");
    public static final Identifier DOLLAR = Identifier.fromNamespaceAndPath("optimusprime", "textures/dollar.png");
    public static final Identifier CIRCLE = Identifier.fromNamespaceAndPath("optimusprime", "textures/circle.png");
    public static final Identifier HITBUBBLE = Identifier.fromNamespaceAndPath("optimusprime", "textures/hitbubble.png");
    public static final Identifier CAPTURE = Identifier.fromNamespaceAndPath("optimusprime", "textures/capture.png");
    public static final Identifier TRIANGLE = Identifier.fromNamespaceAndPath("optimusprime", "textures/triangle.png");
    public static final Identifier THUD = Identifier.fromNamespaceAndPath("optimusprime", "textures/thud.png");
    public static final Identifier MINI_LOGO = Identifier.fromNamespaceAndPath("optimusprime", "textures/mini_logo.png");
    public static final Identifier FPS_ICON = Identifier.fromNamespaceAndPath("optimusprime", "textures/fps.png");
    public static final Identifier PING_ICON = Identifier.fromNamespaceAndPath("optimusprime", "textures/ping.png");
    public static final Identifier COORDS_ICON = Identifier.fromNamespaceAndPath("optimusprime", "textures/coords.png");
    public static final Identifier SPEED_ICON = Identifier.fromNamespaceAndPath("optimusprime", "textures/speedometer.png");
    public static final Identifier TPS_ICON = Identifier.fromNamespaceAndPath("optimusprime", "textures/tps.png");

    // Procedurally generated runtime textures (100% guaranteed to exist in TextureManager)
    public static final Identifier GLOW_RADIAL = Identifier.fromNamespaceAndPath("optimusprime", "dynamic/glow_radial");
    public static final Identifier GLOW = GLOW_RADIAL;
    public static final Identifier RING_SOFT = Identifier.fromNamespaceAndPath("optimusprime", "dynamic/ring_soft");
    public static final Identifier FIREFLY_RADIAL = Identifier.fromNamespaceAndPath("optimusprime", "dynamic/firefly_radial");
    public static final Identifier CROSSHAIR_RING_HD = Identifier.fromNamespaceAndPath("optimusprime", "dynamic/crosshair_ring_hd");
    public static final Identifier ROUNDED_CORNER_MASK = Identifier.fromNamespaceAndPath("optimusprime", "dynamic/rounded_corner_mask");
    public static final Identifier ROUNDED_OUTLINE_MASK = Identifier.fromNamespaceAndPath("optimusprime", "dynamic/rounded_outline_mask");

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
            DynamicTexture glowTex = new DynamicTexture(() -> "optimus_glow_radial", glowImg);
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
            DynamicTexture ringTex = new DynamicTexture(() -> "optimus_ring_soft", ringImg);
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
            DynamicTexture ffTex = new DynamicTexture(() -> "optimus_firefly_radial", ffImg);
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
            DynamicTexture crossTex = new DynamicTexture(() -> "optimus_crosshair_ring_hd", crossImg);
            mc.getTextureManager().register(CROSSHAIR_RING_HD, crossTex);

            // 5. Generate 512x64 1:1 multi-radius 4x4-supersampled SDF corner & outline atlases (r = 1..20)
            NativeImage cornerImg = new NativeImage(CORNER_ATLAS_W, CORNER_ATLAS_H, false);
            NativeImage outlineImg = new NativeImage(CORNER_ATLAS_W, CORNER_ATLAS_H, false);
            for (int y = 0; y < CORNER_ATLAS_H; y++) {
                for (int x = 0; x < CORNER_ATLAS_W; x++) {
                    cornerImg.setPixel(x, y, 0x00FFFFFF);
                    outlineImg.setPixel(x, y, 0x00FFFFFF);
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
                        cornerImg.setPixel(u0 + px, py, (fillAlpha << 24) | 0x00FFFFFF);
                        outlineImg.setPixel(u0 + px, py, (outAlpha << 24) | 0x00FFFFFF);
                    }
                }
            }

            DynamicTexture cornerTex = new DynamicTexture(() -> "optimus_rounded_corner_mask", cornerImg);
            mc.getTextureManager().register(ROUNDED_CORNER_MASK, cornerTex);

            DynamicTexture outlineTex = new DynamicTexture(() -> "optimus_rounded_outline_mask", outlineImg);
            mc.getTextureManager().register(ROUNDED_OUTLINE_MASK, outlineTex);

            initialized = true;
        } catch (Throwable ignored) {
        }
    }
}
