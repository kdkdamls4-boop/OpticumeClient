#version 330
#extension GL_ARB_separate_shader_objects : require

layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;

uniform sampler2D DepthSampler;
uniform sampler2D SceneSampler;

layout(std140) uniform MotionBlurUniforms {
    mat4 InvViewProjection;
    mat4 PrevViewProjection;
    vec4 BlurParams; // x = strength, y = maxBlur, z = reverseZ, w = samples
};

void main() {
    float depth = texture(DepthSampler, texCoord).r;
    float reverseZ = BlurParams.z;
    bool isSky = reverseZ > 0.5 ? (depth <= 0.000001) : (depth >= 0.999999);
    float ndcZ = isSky ? (reverseZ > 0.5 ? 0.0005 : 0.9995) : (reverseZ > 0.5 ? depth : depth * 2.0 - 1.0);

    vec4 clipPos = vec4(texCoord * 2.0 - 1.0, ndcZ, 1.0);
    vec4 worldH = InvViewProjection * clipPos;
    vec3 worldPos = worldH.xyz / max(abs(worldH.w), 1.0e-5) * sign(worldH.w);

    vec4 prevClip = PrevViewProjection * vec4(worldPos, 1.0);
    if (prevClip.w <= 0.05) {
        fragColor = texture(SceneSampler, texCoord);
        return;
    }

    vec2 prevUv = (prevClip.xy / prevClip.w) * 0.5 + 0.5;
    vec2 vel = (texCoord - prevUv) * BlurParams.x * 0.65;
    float len = length(vel);
    float maxBlur = max(0.002, BlurParams.y);
    if (len > maxBlur) {
        vel *= (maxBlur / len);
    }
    if (len < 0.0003) {
        fragColor = texture(SceneSampler, texCoord);
        return;
    }

    int count = clamp(int(BlurParams.w + 0.5), 4, 12);
    vec3 accum = vec3(0.0);
    for (int i = 0; i < 12; i++) {
        if (i >= count) break;
        float t = (float(i) / float(max(1, count - 1))) - 0.5;
        vec2 uv = clamp(texCoord + vel * t, vec2(0.001), vec2(0.999));
        accum += texture(SceneSampler, uv).rgb;
    }

    fragColor = vec4(accum / float(count), 1.0);
}
