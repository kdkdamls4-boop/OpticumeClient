#version 330
#extension GL_ARB_separate_shader_objects : require

layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;

uniform sampler2D CurrentInput;

layout(std140) uniform FogKawaseUniforms {
    vec2 TexelSize;
    float Offset;
    float AlphaMultiplier;
};

void main() {
    vec2 delta = TexelSize * Offset;
    vec4 color = vec4(0.0);
    color += texture(CurrentInput, clamp(texCoord + vec2(-delta.x * 2.0, 0.0), vec2(0.001), vec2(0.999)));
    color += texture(CurrentInput, clamp(texCoord + vec2(delta.x * 2.0, 0.0), vec2(0.001), vec2(0.999)));
    color += texture(CurrentInput, clamp(texCoord + vec2(0.0, -delta.y * 2.0), vec2(0.001), vec2(0.999)));
    color += texture(CurrentInput, clamp(texCoord + vec2(0.0, delta.y * 2.0), vec2(0.001), vec2(0.999)));
    color += texture(CurrentInput, clamp(texCoord + vec2(-delta.x, -delta.y), vec2(0.001), vec2(0.999))) * 2.0;
    color += texture(CurrentInput, clamp(texCoord + vec2(delta.x, -delta.y), vec2(0.001), vec2(0.999))) * 2.0;
    color += texture(CurrentInput, clamp(texCoord + vec2(-delta.x, delta.y), vec2(0.001), vec2(0.999))) * 2.0;
    color += texture(CurrentInput, clamp(texCoord + vec2(delta.x, delta.y), vec2(0.001), vec2(0.999))) * 2.0;
    color *= 1.0 / 12.0;
    fragColor = vec4(color.rgb, color.a * AlphaMultiplier);
}
