#version 330
#extension GL_ARB_separate_shader_objects : require

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    mat4 TextureMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
};

layout(std140) uniform Projection {
    mat4 ProjMat;
};

layout(location = 0) in vec3 Position;
layout(location = 1) in vec2 UV0;
layout(location = 2) in vec4 Color;
layout(location = 3) in vec2 UV3;
layout(location = 4) in float LineWidth;

layout(location = 0) out vec2 fragCoord;
layout(location = 1) out vec4 tintColor;
layout(location = 2) out vec3 rectParams;
layout(location = 3) out vec2 screenUv;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    fragCoord = UV0;
    tintColor = Color * ColorModulator;
    rectParams = vec3(UV3, LineWidth);
    screenUv = gl_Position.xy / max(gl_Position.w, 0.0001) * 0.5 + 0.5;
}
