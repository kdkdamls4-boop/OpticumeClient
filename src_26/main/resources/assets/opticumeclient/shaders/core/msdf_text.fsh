#version 330
#extension GL_ARB_separate_shader_objects : require

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    mat4 TextureMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
};

uniform sampler2D Sampler0;

layout(location = 0) in vec2 texCoord0;
layout(location = 1) in vec4 vertexColor;

layout(location = 0) out vec4 fragColor;

float median(float r, float g, float b) {
    return max(min(r, g), min(max(r, g), b));
}

void main() {
    vec3 sampleValue = texture(Sampler0, texCoord0).rgb;
    float signedDistance = median(sampleValue.r, sampleValue.g, sampleValue.b) - 0.5;

    vec2 unitRange = vec2(float(MSDF_RANGE)) / max(vec2(textureSize(Sampler0, 0)), vec2(1.0));
    vec2 screenTexSize = vec2(1.0) / max(fwidth(texCoord0), vec2(0.0001));
    float screenPxRange = max(0.5 * dot(unitRange, screenTexSize), 1.0);
    float smoothing = screenPxRange < 2.0 ? 0.65 : 0.5;
    float opacity = smoothstep(-smoothing, smoothing, screenPxRange * signedDistance);
    if (opacity <= 0.001) {
        discard;
    }

    fragColor = vec4(vertexColor.rgb, vertexColor.a * opacity) * ColorModulator;
}
