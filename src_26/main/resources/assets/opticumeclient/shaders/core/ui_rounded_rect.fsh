#version 330
#extension GL_ARB_separate_shader_objects : require

layout(location = 0) in vec2 fragCoord;
layout(location = 1) in vec4 vertColor;
layout(location = 2) in vec3 rectParams;

layout(location = 0) out vec4 fragColor;

void main() {
    vec2 size = max(rectParams.xy, vec2(0.5));
    vec2 halfSize = size * 0.5;
    float raw = rectParams.z;
    // If raw < 0: outline mode where abs(raw) = thickness * 2048.0 + radius
    bool isOutline = raw < 0.0;
    float p = abs(raw);
    float thickness = isOutline ? max(0.35, floor(p / 2048.0) / 16.0) : 0.0;
    float radius = clamp(mod(p, 2048.0), 0.0, min(halfSize.x, halfSize.y));

    vec2 localPos = (fragCoord - 0.5) * size;
    vec2 q = abs(localPos) - halfSize + vec2(radius);
    float dist = length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - radius;
    float aa = max(fwidth(dist), 0.75);

    float mask;
    if (isOutline) {
        float outer = clamp(0.5 - dist / aa, 0.0, 1.0);
        float inner = clamp(0.5 - (dist + thickness) / aa, 0.0, 1.0);
        mask = clamp(outer - inner, 0.0, 1.0);
    } else {
        mask = clamp(0.5 - dist / aa, 0.0, 1.0);
    }

    float outA = vertColor.a * mask;
    if (outA <= 0.003) {
        discard;
    }
    fragColor = vec4(vertColor.rgb, outA);
}
