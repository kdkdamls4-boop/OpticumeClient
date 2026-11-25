#version 330
#extension GL_ARB_separate_shader_objects : require

layout(std140) uniform AmbienceSkyUniforms {
    mat4 uInvViewProj;
    vec4 uRadius;
    vec4 uShape;
    vec4 uTopLeftColor;
    vec4 uBottomLeftColor;
    vec4 uBottomRightColor;
    vec4 uTopRightColor;
    vec4 uText;
    vec4 uOutlineColor;
};

#define uResolution (uRadius.xy)
#define uTime       (uShape.x)
#define uScale      (uShape.w)
#define uIntensity  (uBottomRightColor.y)
#define uSpeed      (uBottomRightColor.z)
#define uNight      (uBottomRightColor.w)
#define uRain       (uTopRightColor.x)
#define uThunder    (uTopRightColor.y)
#define uThemeMix   (uTopRightColor.z)
#define uColor      (uTopLeftColor.rgb)
#define uColor2     (uBottomLeftColor.rgb)
#define uAlpha_Sky  (uTopLeftColor.a)

layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

float hash31(vec3 p) {
    p = fract(p * 0.1031);
    p += dot(p, p.yzx + 33.33);
    return fract((p.x + p.y) * p.z);
}

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1.0, 0.0)), u.x),
               mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), u.x), u.y);
}

float fbm(vec2 p) {
    float v = 0.0;
    float a = 0.5;
    for (int i = 0; i < 5; i++) {
        v += noise(p) * a;
        p = p * 2.05 + vec2(3.1, 1.7);
        a *= 0.5;
    }
    return v;
}

vec3 skyRay() {
    vec4 world = uInvViewProj * vec4(texCoord * 2.0 - 1.0, 0.0, 1.0);
    return normalize(world.xyz / max(abs(world.w), 1.0e-5));
}

void main() {
    vec3 ray = skyRay();
    float t = uTime * uSpeed;
    float height = ray.y;
    float absH = abs(height);

    vec3 themeTint = mix(uColor, uColor2, 0.5);
    float up = smoothstep(-0.2, 1.0, height);
    vec3 baseHorizon = mix(vec3(0.055, 0.040, 0.115), themeTint * 0.14, 0.45 * uThemeMix);
    vec3 baseZenith = mix(vec3(0.020, 0.025, 0.075), themeTint * 0.10, 0.40 * uThemeMix);
    vec3 sky = mix(baseHorizon, baseZenith, up);

    // Seamless dome UV extending through zenith & nadir without black pole hole
    vec2 hp = ray.xz / (absH + 0.45);
    float scale = max(uScale, 1.0);

    vec3 aurora = vec3(0.0);
    float total = 0.0;

    for (int i = 0; i < 5; i++) {
        float fi = float(i);
        vec2 c = hp * (1.25 + fi * 0.25) * (0.65 + scale * 0.05);
        vec2 drift = vec2(t * (0.03 + fi * 0.012), t * 0.02);

        float band = fbm(c * 1.35 + drift + fi * 3.1);
        band = smoothstep(0.36, 0.88, band);

        float rays = fbm(c * 4.6 + vec2(0.0, absH * 1.1) - drift * 1.5 + fi * 1.7);
        rays = pow(clamp(rays, 0.0, 1.0), 1.35);

        float lowFade = smoothstep(-0.35, 0.14, absH);
        float highSoft = 1.0 - smoothstep(0.68, 1.0, absH) * 0.35;
        float vzone = lowFade * highSoft;

        float mask = band * rays * vzone;
        float g = clamp(absH, 0.0, 1.0);
        vec3 col = mix(vec3(0.10, 1.0, 0.45), vec3(0.15, 0.85, 0.75), smoothstep(0.0, 0.5, g));
        col = mix(col, vec3(0.70, 0.20, 1.0), smoothstep(0.45, 1.0, g));
        col = mix(col, themeTint, 0.55 * uThemeMix);

        aurora += col * mask;
        total += mask;
    }

    float hemiScale = height >= 0.0 ? 1.0 : mix(0.75, 0.48, smoothstep(0.0, 0.9, -height));
    float glow = clamp(total * (1.4 + uIntensity * 22.0), 0.0, 2.8);
    sky += aurora * glow * hemiScale;

    vec3 groundGlow = mix(vec3(0.05, 0.28, 0.14), themeTint * 0.35, uThemeMix);
    sky += groundGlow * (0.28 + 0.72 * smoothstep(0.0, 0.5, total)) * 0.42;

    // 3D seamless starfield
    vec3 sp = ray * 230.0;
    float sh = hash31(floor(sp));
    float stars = smoothstep(0.993, 0.9996, sh) * (1.0 - smoothstep(0.0, 0.42, length(fract(sp) - 0.5)));
    sky += vec3(0.84, 0.91, 1.0) * stars * 0.75;

    sky = mix(sky, sky * vec3(0.5, 0.55, 0.65), clamp(uRain + uThunder, 0.0, 1.0) * 0.4);
    fragColor = vec4(clamp(sky, 0.0, 1.4), uAlpha_Sky);
}
