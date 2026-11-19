#version 330
#extension GL_ARB_separate_shader_objects : require

uniform sampler2D Sampler0;

layout(location = 0) in vec2 fragCoord;
layout(location = 1) in vec4 tintColor;
layout(location = 2) in vec3 rectParams;
layout(location = 3) in vec2 screenUv;

layout(location = 0) out vec4 fragColor;

void main() {
    vec2 size = max(rectParams.xy, vec2(1.0));
    vec2 halfSize = size * 0.5;
    float packed = max(rectParams.z, 0.0);
    float overallAlpha = clamp(floor(packed / 2048.0) / 255.0, 0.0, 1.0);
    float radius = clamp(mod(packed, 2048.0), 0.0, min(halfSize.x, halfSize.y));

    vec2 localPos = (fragCoord - 0.5) * size;
    vec2 q = abs(localPos) - halfSize + vec2(radius);
    float dist = length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - radius;
    float aa = max(fwidth(dist), 0.75);
    float mask = clamp(0.5 - dist / aa, 0.0, 1.0);
    float outAlpha = mask * overallAlpha;
    if (outAlpha <= 0.003) {
        discard;
    }

    vec3 blurred = texture(Sampler0, clamp(screenUv, vec2(0.001), vec2(0.999))).rgb;
    vec3 mixed = mix(blurred, tintColor.rgb, clamp(tintColor.a, 0.0, 1.0));
    float dither = (fract(sin(dot(gl_FragCoord.xy, vec2(12.9898, 78.233))) * 43758.5453) - 0.5) / 255.0;
    fragColor = vec4(mixed + vec3(dither), outAlpha);
}
