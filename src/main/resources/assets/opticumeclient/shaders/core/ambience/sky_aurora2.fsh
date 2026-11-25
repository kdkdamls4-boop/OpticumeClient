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
#define uColor2     (uBottomLeftColor.rgb)
#define uColorA     uTopLeftColor
#define uColorB     vec4(uColor2, uTopLeftColor.a)

layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;

mat2 dMm2(float a) {
    float c = cos(a), s = sin(a);
    return mat2(c, s, -s, c);
}

float dHash31(vec3 p) {
    p = fract(p * 0.1031);
    p += dot(p, p.yzx + 33.33);
    return fract((p.x + p.y) * p.z);
}

float dTri(float x) {
    return clamp(abs(fract(x) - 0.5), 0.03, 0.47);
}

vec2 dTri2(vec2 p) {
    return vec2(dTri(p.x) + dTri(p.y), dTri(p.y + dTri(p.x)));
}

float dAuroraNoise(vec2 p, float time) {
    float z = 1.65;
    float z2 = 2.3;
    float rz = 0.0;
    p *= dMm2(sin(p.x * 0.18 + time * 0.05) * 0.65);
    vec2 bp = p;

    for (int i = 0; i < 4; i++) {
        vec2 dg = dTri2(bp * 1.65) * 0.72;
        dg *= dMm2(time * 0.07 + float(i) * 0.4);
        p -= dg / z2;
        bp *= 1.32;
        z2 *= 0.48;
        z *= 0.44;
        p *= 1.18 + (rz - 0.8) * 0.04;
        rz += dTri(p.x + dTri(p.y)) * z;
        p *= mat2(-0.95534, 0.29552, -0.29552, -0.95534);
    }
    return clamp(1.0 / pow(max(rz * 18.0, 0.9), 1.35), 0.0, 0.72);
}

vec3 divineRayDir() {
    vec4 world = uInvViewProj * vec4(texCoord * 2.0 - 1.0, 0.0, 1.0);
    return normalize(world.xyz / max(abs(world.w), 1.0e-5));
}

vec3 dAuroraColor(vec3 rd, float time, float scale, vec3 colorA, vec3 colorB) {
    vec3 col = vec3(0.0);
    vec3 avgCol = vec3(0.0);
    float absY = abs(rd.y);
    // Seamless stereographic-like dome projection without off-center pole hole
    vec2 baseUv = rd.xz / (absY + 0.52);

    for (float i = 0.0; i < 12.0; i++) {
        float layerZoom = 0.75 + i * 0.055;
        vec2 p = baseUv * scale * 0.24 * layerZoom + vec2(time * 0.035, -time * 0.025) * (0.4 + i * 0.05);
        float rzt = dAuroraNoise(p, time + i * 0.12);

        float t = sin(i * 0.28 + time * 0.75 + baseUv.x * 0.5) * 0.5 + 0.5;
        vec3 layerCol = mix(colorA, colorB, t) * rzt;

        avgCol = mix(avgCol, layerCol, 0.5);
        col += avgCol * exp2(-i * 0.16 - 0.55);
    }

    float horizonSoft = mix(0.45, 1.0, smoothstep(0.0, 0.42, absY));
    return col * horizonSoft * (1.25 + clamp(uIntensity, 0.05, 1.5) * 1.45);
}

vec3 dStars(vec3 p) {
    vec3 c = vec3(0.0);
    for (int i = 0; i < 2; i++) {
        float density = i == 0 ? 190.0 : 340.0;
        vec3 sp = p * density;
        vec3 q = fract(sp) - 0.5;
        vec3 id = floor(sp);
        float rn = dHash31(id);
        float star = (1.0 - smoothstep(0.0, 0.42, length(q))) * step(0.992 - float(i) * 0.002, rn);
        c += star * vec3(0.88, 0.93, 1.0) * (i == 0 ? 0.85 : 0.45);
    }
    return c;
}

void main() {
    float animatedTime = uTime * uSpeed;
    vec3 rd = divineRayDir();

    float skyGradient = clamp(rd.y * 0.5 + 0.5, 0.0, 1.0);
    vec3 zenithBg = mix(vec3(0.025, 0.025, 0.065), uColorA.rgb * 0.12, 0.45);
    vec3 horizonBg = mix(vec3(0.055, 0.030, 0.105), uColorB.rgb * 0.16, 0.45);
    vec3 nadirBg = mix(vec3(0.016, 0.016, 0.045), uColorA.rgb * 0.07, 0.35);

    vec3 bg = rd.y >= 0.0
            ? mix(horizonBg, zenithBg, smoothstep(0.0, 0.85, rd.y))
            : mix(horizonBg, nadirBg, smoothstep(0.0, 0.85, -rd.y));

    vec3 aur = dAuroraColor(rd, animatedTime, max(uScale, 1.0), uColorA.rgb, uColorB.rgb);
    float lowerAtten = rd.y >= 0.0 ? 1.0 : mix(0.82, 0.52, smoothstep(0.0, 0.9, -rd.y));
    vec3 starField = dStars(rd) * mix(0.55, 0.95, smoothstep(0.0, 0.5, abs(rd.y)));

    vec3 col = bg + (aur * lowerAtten) + starField * (0.45 + 0.35 * skyGradient);
    col = clamp(col, 0.0, 1.25);

    fragColor = vec4(col, uColorA.a);
}
