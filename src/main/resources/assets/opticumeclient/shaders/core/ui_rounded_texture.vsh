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
layout(location = 4) in vec3 Normal;
layout(location = 5) in float LineWidth;

layout(location = 0) out vec2 texUv;
layout(location = 1) out vec4 vertColor;
layout(location = 2) out vec3 rectParams;
layout(location = 3) out vec2 fragCoord;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    texUv = UV0;
    vertColor = Color * ColorModulator;
    rectParams = vec3(UV3, LineWidth);
    fragCoord = Normal.xy;
}
