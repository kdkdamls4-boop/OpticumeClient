#version 330
#extension GL_ARB_separate_shader_objects : require

layout(std140) uniform UiBlurParams {
    vec4 TexelAndDir;
};

uniform sampler2D InSampler;

layout(location = 0) in vec2 TexCoord;
layout(location = 0) out vec4 fragColor;

void main() {
    vec2 stepUv = TexelAndDir.xy * TexelAndDir.zw;
    vec4 sum = texture(InSampler, clamp(TexCoord, vec2(0.001), vec2(0.999))) * 0.227027;
    sum += texture(InSampler, clamp(TexCoord + stepUv * 1.3846153846, vec2(0.001), vec2(0.999))) * 0.3162162162;
    sum += texture(InSampler, clamp(TexCoord - stepUv * 1.3846153846, vec2(0.001), vec2(0.999))) * 0.3162162162;
    sum += texture(InSampler, clamp(TexCoord + stepUv * 3.2307692308, vec2(0.001), vec2(0.999))) * 0.0702702703;
    sum += texture(InSampler, clamp(TexCoord - stepUv * 3.2307692308, vec2(0.001), vec2(0.999))) * 0.0702702703;
    fragColor = vec4(sum.rgb, 1.0);
}
