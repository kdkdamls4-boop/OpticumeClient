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
    vec4 color = texture(CurrentInput, clamp(texCoord, vec2(0.001), vec2(0.999))) * 4.0;
    color += texture(CurrentInput, clamp(texCoord + vec2(-delta.x, -delta.y), vec2(0.001), vec2(0.999)));
    color += texture(CurrentInput, clamp(texCoord + vec2(delta.x, -delta.y), vec2(0.001), vec2(0.999)));
    color += texture(CurrentInput, clamp(texCoord + vec2(-delta.x, delta.y), vec2(0.001), vec2(0.999)));
    color += texture(CurrentInput, clamp(texCoord + vec2(delta.x, delta.y), vec2(0.001), vec2(0.999)));
    color *= 0.125;
    fragColor = vec4(color.rgb, color.a * AlphaMultiplier);
}
