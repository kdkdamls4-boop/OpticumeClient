#version 330
#extension GL_ARB_separate_shader_objects : require

uniform sampler2D HandSampler;
uniform sampler2D HandMaskSampler;

layout(location = 0) in vec2 TexCoord;
layout(location = 0) out vec4 fragColor;

vec3 handsItemColor(vec4 sampleColor) {
    vec3 color = sampleColor.rgb;
    if (sampleColor.a > 0.001) color /= max(sampleColor.a, 0.03125);
    color = clamp(color, 0.0, 1.0);
    float peak = max(color.r, max(color.g, color.b));
    vec3 hue = color / max(peak, 0.001);
    return mix(vec3(0.45), hue * (0.78 + 0.22 * sqrt(peak)), smoothstep(0.005, 0.08, peak));
}

vec4 itemSample(vec2 uv) {
    float coverage = texture(HandMaskSampler, uv).r;
    if (coverage <= 0.0) return vec4(0.0);
    return vec4(handsItemColor(texture(HandSampler, uv)) * coverage, coverage);
}

void main() {
    vec2 offset = 0.5 / vec2(textureSize(HandSampler, 0));
    fragColor = (itemSample(TexCoord - offset)
            + itemSample(TexCoord + offset)
            + itemSample(TexCoord + vec2(offset.x, -offset.y))
            + itemSample(TexCoord + vec2(-offset.x, offset.y))) * 0.25;
}
