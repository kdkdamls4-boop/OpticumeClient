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

#define u_Time  (uShape.x)
#define u_Scale (uShape.w)
#define u_Color (uTopLeftColor)
#define u_Color2 (uBottomLeftColor.rgb)

#define TAU 6.28318530718
#define MAX_ITER 5
#define INTENSITY 0.005

layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;

vec3 skyRay() {
    vec4 world = uInvViewProj * vec4(texCoord * 2.0 - 1.0, 1.0, 1.0);
    return normalize(world.xyz / max(abs(world.w), 1.0e-5));
}

void main() {
    vec3 ray = skyRay();

    vec3 basisX = normalize(vec3(0.74, 0.18, 0.65));
    vec3 basisY = normalize(vec3(-0.22, 0.96, 0.18));
    vec2 p = vec2(dot(ray, basisX), dot(ray, basisY)) * max(u_Scale, 0.001) * TAU - 250.0;

    vec2 iterPos = p;
    float value = 1.0;
    float timeOffset = u_Time * 0.5 + 23.0;

    for (int n = 0; n < MAX_ITER; n++) {
        float iterTime = timeOffset * (1.0 - (3.5 / float(n + 1)));
        iterPos = p + vec2(
            cos(iterTime - iterPos.x) + sin(iterTime + iterPos.y),
            sin(iterTime - iterPos.y) + cos(iterTime + iterPos.x)
        );

        float sinX = sin(iterPos.x + iterTime);
        float cosY = cos(iterPos.y + iterTime);
        float compX = p.x * INTENSITY / max(abs(sinX), 0.001);
        float compY = p.y * INTENSITY / max(abs(cosY), 0.001);
        value += 1.0 / max(sqrt(compX * compX + compY * compY), 0.001);
    }

    value = 1.17 - pow(value / float(MAX_ITER), 1.4);
    float pwr = value * value;
    pwr = pwr * pwr;
    pwr = pwr * pwr;

    vec3 baseColor = vec3(abs(pwr));
    vec3 enhanced = clamp(baseColor + vec3(0.0, 0.35, 0.5), 0.0, 1.0);
    float luminance = dot(enhanced, vec3(0.299, 0.587, 0.114));
    vec3 themeMix = mix(u_Color.rgb, u_Color2, clamp(luminance, 0.0, 1.0));
    vec3 color = vec3(luminance) * themeMix * 1.4;

    fragColor = vec4(clamp(color, 0.0, 1.0), u_Color.a);
}
