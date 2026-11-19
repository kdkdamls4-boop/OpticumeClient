#version 330
#extension GL_ARB_separate_shader_objects : require

layout(std140) uniform AmbienceSaturationUniforms {
    vec4 Params;
};

uniform sampler2D InSampler;

layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;

void main() {
    vec4 color = texture(InSampler, clamp(texCoord, vec2(0.0005), vec2(0.9995)));
    float luma = dot(color.rgb, vec3(0.2126, 0.7152, 0.0722));
    vec3 saturated = mix(vec3(luma), color.rgb, Params.x);
    fragColor = vec4(clamp(saturated, 0.0, 1.0), color.a);
}
