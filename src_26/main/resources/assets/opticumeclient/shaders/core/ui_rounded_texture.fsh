#version 330
#extension GL_ARB_separate_shader_objects : require

uniform sampler2D Sampler0;

layout(location = 0) in vec2 texUv;
layout(location = 1) in vec4 vertColor;
layout(location = 2) in vec3 rectParams;
layout(location = 3) in vec2 fragCoord;

layout(location = 0) out vec4 fragColor;

vec4 sampleRow(vec2 baseUv, vec2 dx, vec2 rowOff) {
    vec2 r = baseUv + rowOff;
    vec4 s0 = texture(Sampler0, clamp(r - dx * 0.675, vec2(0.0002), vec2(0.9998)));
    vec4 s1 = texture(Sampler0, clamp(r - dx * 0.225, vec2(0.0002), vec2(0.9998)));
    vec4 s2 = texture(Sampler0, clamp(r + dx * 0.225, vec2(0.0002), vec2(0.9998)));
    vec4 s3 = texture(Sampler0, clamp(r + dx * 0.675, vec2(0.0002), vec2(0.9998)));
    return (s0 + s3) * 0.15 + (s1 + s2) * 0.35;
}

void main() {
    vec2 size = max(rectParams.xy, vec2(0.5));
    vec2 halfSize = size * 0.5;
    float radius = clamp(rectParams.z, 0.0, min(halfSize.x, halfSize.y));

    // 4x4 screen-footprint Gaussian supersampling eliminates subpixel shimmer during parallax/scroll
    vec2 dx = dFdx(texUv);
    vec2 dy = dFdy(texUv);
    vec4 r0 = sampleRow(texUv, dx, -dy * 0.675);
    vec4 r1 = sampleRow(texUv, dx, -dy * 0.225);
    vec4 r2 = sampleRow(texUv, dx,  dy * 0.225);
    vec4 r3 = sampleRow(texUv, dx,  dy * 0.675);
    vec4 texSample = ((r0 + r3) * 0.15 + (r1 + r2) * 0.35) * vertColor;

    float mask = 1.0;
    if (radius > 0.001) {
        vec2 localPos = (fragCoord - 0.5) * size;
        vec2 q = abs(localPos) - halfSize + vec2(radius);
        float dist = length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - radius;
        float aa = max(fwidth(dist), 0.75);
        mask = clamp(0.5 - dist / aa, 0.0, 1.0);
    }

    float outA = texSample.a * mask;
    if (outA <= 0.003) {
        discard;
    }
    fragColor = vec4(texSample.rgb, outA);
}
