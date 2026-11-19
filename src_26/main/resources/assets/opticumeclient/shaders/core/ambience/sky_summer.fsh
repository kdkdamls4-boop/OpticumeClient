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
    return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453);
}

float hash13(vec3 p3) {
    p3 = fract(p3 * 0.1031);
    p3 += dot(p3, p3.zyx + 31.32);
    return fract((p3.x + p3.y) * p3.z);
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
        p = p * 2.0 + vec2(1.7, 3.1);
        a *= 0.5;
    }
    return v;
}

vec3 skyRay() {
    vec4 world = uInvViewProj * vec4(texCoord * 2.0 - 1.0, 1.0, 1.0);
    return normalize(world.xyz / max(abs(world.w), 1.0e-5));
}

float roundStars(vec3 dir, float t) {
    vec3 p = dir * 70.0;
    vec3 ip = floor(p);
    vec3 fp = fract(p) - 0.5;
    float rnd = hash13(ip);
    float has = step(0.965, rnd);
    float d = length(fp);
    float starDot = smoothstep(0.22, 0.0, d);
    float tw = 0.55 + 0.45 * sin(t * 2.5 + rnd * 6.2831);
    return has * starDot * tw;
}

void main() {
    vec3 ray = skyRay();
    float t = uTime * uSpeed;
    float height = ray.y;
    float h = smoothstep(-0.45, 0.95, height);
    float night = clamp(uNight, 0.0, 1.0);

    vec3 themeTint = mix(uColor, uColor2, 0.5);

    vec3 dayZenith  = mix(vec3(0.28, 0.62, 1.0), uColor, 0.22 * uThemeMix);
    vec3 dayHorizon = mix(vec3(1.0, 0.72, 0.36), uColor2, 0.18 * uThemeMix);
    vec3 nightZenith  = mix(vec3(0.015, 0.03, 0.09), themeTint * 0.2, uThemeMix);
    vec3 nightHorizon = mix(vec3(0.05, 0.08, 0.18), themeTint * 0.3, uThemeMix);
    vec3 zenith  = mix(dayZenith, nightZenith, night);
    vec3 horizon = mix(dayHorizon, nightHorizon, night);
    vec3 sky = mix(horizon, zenith, h);

    vec3 sunDir = normalize(vec3(-0.42, 0.34, 0.84));
    float sunDot = max(dot(ray, sunDir), 0.0);
    vec3 dayLight = vec3(1.0, 0.88, 0.55) * pow(sunDot, 14.0) * 0.58
                  + vec3(1.0, 0.96, 0.82) * pow(sunDot, 900.0) * 2.4;
    vec3 nightLight = vec3(0.7, 0.78, 0.95) * pow(sunDot, 40.0) * 0.4
                    + vec3(0.95, 0.97, 1.0) * pow(sunDot, 2200.0) * 2.2;
    sky += mix(dayLight, nightLight, night);

    float stars = roundStars(ray, t) * smoothstep(0.02, 0.4, height);
    sky += vec3(0.9, 0.93, 1.0) * stars * night * (1.0 - clamp(uRain + uThunder, 0.0, 1.0));

    float scale = max(uScale, 1.0);
    vec2 cc = vec2(ray.x, ray.z) * scale * 1.6 + vec2(t * 0.02, t * 0.008);
    float cloudBase = fbm(cc * 0.42 + vec2(0.0, 4.0));
    float cloudDetail = fbm(cc * 0.9 + vec2(8.5, 1.7));
    float cloudBand = smoothstep(-0.05, 0.55, height) * smoothstep(0.85, 0.12, height);
    float clouds = smoothstep(0.48, 0.72, cloudBase + cloudDetail * 0.28) * cloudBand;
    clouds *= 1.0 - clamp(uRain + uThunder, 0.0, 1.0) * 0.7;
    vec3 dayCloud = mix(vec3(1.0, 0.92, 0.82), vec3(0.88, 0.94, 1.0), h);
    vec3 nightCloud = mix(vec3(0.10, 0.13, 0.22), vec3(0.16, 0.20, 0.32), h);
    vec3 cloudColor = mix(dayCloud, nightCloud, night);
    sky = mix(sky, cloudColor, clouds * mix(0.78, 0.62, night));

    float haze = smoothstep(-0.6, 0.08, height) * (1.0 - smoothstep(0.08, 0.45, height));
    vec3 dayHaze = mix(vec3(1.0, 0.55, 0.22), themeTint, uThemeMix);
    vec3 nightHaze = mix(vec3(0.14, 0.20, 0.40), themeTint * 0.5, uThemeMix);
    sky += mix(dayHaze, nightHaze, night) * haze * (0.22 + uIntensity * 14.0);

    vec3 overcast = mix(vec3(0.34, 0.38, 0.48), vec3(0.06, 0.08, 0.14), night);
    sky = mix(sky, overcast, clamp(uRain + uThunder, 0.0, 1.0) * 0.35);

    fragColor = vec4(clamp(sky, 0.0, 1.0), uAlpha_Sky);
}
