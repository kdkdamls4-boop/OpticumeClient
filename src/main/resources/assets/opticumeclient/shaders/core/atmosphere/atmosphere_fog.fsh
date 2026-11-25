#version 330
#extension GL_ARB_separate_shader_objects : require

layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;

uniform sampler2D DepthSampler;
uniform sampler2D SceneSampler;

layout(std140) uniform AtmosphereUniforms {
    mat4 InvViewProjection;
    vec4 FogColorDensity;
    vec4 FogParams;
    vec4 FogExtra;
    vec4 SunDirection;
    vec4 CameraOrigin;
    vec4 WindParams;
    vec4 FlashColor;
    vec4 BoltEnds;
    vec4 BoltColor;
    vec4 BoltParams;
    vec4 RainColor;
    vec4 RainParams;
    vec4 RippleParams;
    vec4 HazeColor;
    vec4 HazeParams;
    vec4 SunScreenParams;
    vec4 SoftPostParams;
    vec4 PostTintParams;
    vec4 DdaParticleParams;
};

float hash11(float p) {
    p = fract(p * 0.1031);
    p *= p + 33.33;
    p *= p + p;
    return fract(p);
}

float hash21(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

vec3 hash33(vec3 p3) {
    p3 = fract(p3 * vec3(0.1031, 0.1030, 0.0973));
    p3 += dot(p3, p3.yxz + 33.33);
    return fract((p3.xxy + p3.yxx) * p3.zyx);
}

float valueNoise(vec3 p) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float n000 = hash33(i).x;
    float n100 = hash33(i + vec3(1.0, 0.0, 0.0)).x;
    float n010 = hash33(i + vec3(0.0, 1.0, 0.0)).x;
    float n110 = hash33(i + vec3(1.0, 1.0, 0.0)).x;
    float n001 = hash33(i + vec3(0.0, 0.0, 1.0)).x;
    float n101 = hash33(i + vec3(1.0, 0.0, 1.0)).x;
    float n011 = hash33(i + vec3(0.0, 1.0, 1.0)).x;
    float n111 = hash33(i + vec3(1.0)).x;
    float nx00 = mix(n000, n100, f.x);
    float nx10 = mix(n010, n110, f.x);
    float nx01 = mix(n001, n101, f.x);
    float nx11 = mix(n011, n111, f.x);
    return mix(mix(nx00, nx10, f.y), mix(nx01, nx11, f.y), f.z);
}

vec3 atmosphereReconstruct(vec2 coords, float depth) {
    float clipZ = FogParams.z > 0.5 ? depth : depth * 2.0 - 1.0;
    vec4 ndc = vec4(coords * 2.0 - 1.0, clipZ, 1.0);
    vec4 position = InvViewProjection * ndc;
    return position.xyz / max(abs(position.w), 1.0e-5);
}

const float SKY_DISTANCE = 600.0;
const float GROUND_OFFSET = 8.0;
const float BOLT_JAG = 0.045;
const float TAU = 6.28318530;
const float INV_TAU = 0.15915494;

float boltKink(float t, float seed, float nodes) {
    float x = t * nodes;
    float node = floor(x);
    float f = x - node;
    float a = hash11(node * 1.37 + seed) - 0.5;
    float b = hash11((node + 1.0) * 1.37 + seed) - 0.5;
    return mix(a, b, f) * 2.0;
}

float boltWander(float t, float seed) {
    return boltKink(t, seed, 7.0)
            + boltKink(t, seed + 19.3, 19.0) * 0.34
            + boltKink(t, seed + 41.7, 47.0) * 0.11;
}

vec2 boltChannel(vec2 p, vec2 a, vec2 b, float seed, float jag, float radius, float taper) {
    vec2 axis = b - a;
    float len = length(axis);
    if (len < 1.0e-5) {
        return vec2(0.0);
    }
    vec2 dir = axis / len;
    vec2 normal = vec2(-dir.y, dir.x);
    vec2 rel = p - a;
    float t = dot(rel, dir) / len;
    float clamped = clamp(t, 0.0, 1.0);

    float offset = boltWander(clamped, seed) * jag * len * smoothstep(0.0, 0.12, clamped);
    float lateral = abs(dot(rel, normal) - offset);
    float overshoot = (t < 0.0 ? -t : max(t - 1.0, 0.0)) * len;
    float dist = sqrt(lateral * lateral + overshoot * overshoot);

    float width = radius * mix(1.0, 0.3, taper * clamped);
    float core = smoothstep(width, width * 0.25, dist);
    float glow = exp(-dist / max(BoltColor.a, 1.0e-4));
    return vec2(core, glow) * (1.0 - smoothstep(0.82, 1.0, clamped) * taper);
}

vec3 lightning(vec2 p) {
    vec2 top = vec2(BoltEnds.x * FogParams.w, BoltEnds.y);
    vec2 ground = vec2(BoltEnds.z * FogParams.w, BoltEnds.w);
    vec2 axis = ground - top;
    float len = length(axis);
    if (len < 1.0e-5) {
        return vec3(0.0);
    }
    vec2 dir = axis / len;
    float radius = BoltParams.y;
    float seed = BoltParams.z;

    float along = clamp(dot(p - top, dir), 0.0, len);
    float reach = BOLT_JAG * len * 2.5 + len * 0.4 + radius * 4.0 + BoltColor.a * 5.0;
    if (length(p - (top + dir * along)) > reach) {
        return vec3(0.0);
    }

    vec2 normal = vec2(-dir.y, dir.x);
    vec2 accumulated = boltChannel(p, top, ground, seed, BOLT_JAG, radius, 0.35);

    int branches = int(BoltParams.w);
    for (int i = 0; i < 4; i++) {
        if (i >= branches) {
            break;
        }
        float index = float(i);
        float split = 0.22 + 0.62 * hash11(seed + index * 7.31 + 3.1);
        float side = hash11(seed + index * 13.77) < 0.5 ? -1.0 : 1.0;
        float reachOut = 0.12 + 0.22 * hash11(seed + index * 3.19 + 8.4);
        vec2 start = top
                + dir * (split * len)
                + normal * (boltWander(split, seed) * BOLT_JAG * len);
        vec2 heading = normalize(dir + normal * side * (0.4 + 0.5 * hash11(seed + index * 5.11)));
        accumulated += boltChannel(
                p,
                start,
                start + heading * (reachOut * len),
                seed + index * 31.7 + 11.0,
                BOLT_JAG * 1.4,
                radius * 0.55,
                1.0
        ) * 0.7;
    }

    return (BoltColor.rgb * accumulated.y * 1.6 + vec3(accumulated.x)) * BoltParams.x;
}

float fogNoise(vec3 p) {
    return valueNoise(p) * 0.55 + valueNoise(p * 2.13) * 0.3 + valueNoise(p * 4.31) * 0.15;
}

float fogNoiseQ(vec3 p, float quality) {
    if (quality < 0.5) {
        return valueNoise(p);
    }
    if (quality < 1.5) {
        return valueNoise(p) * 0.65 + valueNoise(p * 2.13) * 0.35;
    }
    return fogNoise(p);
}

float henyeyGreenstein(float cosTheta, float g) {
    float g2 = g * g;
    return (1.0 - g2) / pow(max(1.0 + g2 - 2.0 * g * cosTheta, 1.0e-4), 1.5);
}

float hazeSegment(float y0, float dy, float len, float invH) {
    float side = (y0 + dy * len * 0.5) >= 0.0 ? 1.0 : -1.0;
    float base = exp(clamp(-side * y0 * invH, -30.0, 2.0));
    float k = side * dy * invH;
    if (abs(k) < 1.0e-5) {
        return base * len;
    }
    return base * (1.0 - exp(clamp(-k * len, -30.0, 30.0))) / k;
}

float hazeOpticalDepth(float y0, float dy, float len, float invH) {
    if (abs(dy) < 1.0e-4) {
        return exp(clamp(-abs(y0) * invH, -30.0, 2.0)) * len;
    }
    float crossing = -y0 / dy;
    if (crossing > 0.0 && crossing < len) {
        return hazeSegment(y0, dy, crossing, invH)
                + hazeSegment(0.0, dy, len - crossing, invH);
    }
    return hazeSegment(y0, dy, len, invH);
}

float rainLayer(vec3 rayDir,
                float hitHorizontal,
                float shell,
                float cellHeight,
                float cellWidth,
                float seed) {
    float horizontal = length(rayDir.xz);
    float zenithFade = smoothstep(0.30, 0.62, horizontal);
    float slope = clamp(rayDir.y / max(horizontal, 0.25), -3.2, 3.2);
    float azimuth = atan(rayDir.z, rayDir.x);
    float height = CameraOrigin.y + slope * shell;

    float columns = max(floor(TAU * shell / cellWidth + 0.5), 8.0);
    float u = (azimuth * INV_TAU + 0.5) * columns;
    float v = (height + RainParams.y) / cellHeight;

    float pixel = clamp(fwidth(u) * cellWidth, 1.0e-5, cellWidth * 0.5);
    float aliasFade = clamp(1.6 - fwidth(v), 0.0, 1.0);

    if (zenithFade <= 0.0 || aliasFade <= 0.0 || hitHorizontal < shell) {
        return 0.0;
    }

    vec2 id = floor(vec2(u, v));
    vec2 f = vec2(u, v) - id;
    vec2 key = vec2(mod(id.x, columns), id.y) + seed;

    if (hash21(key) > RainParams.x) {
        return 0.0;
    }

    bool snow = RainParams.z > 0.5;
    float offsetX = 0.15 + 0.7 * hash21(key + 7.7);
    float offsetY = 0.02 + 0.25 * hash21(key + 3.3);
    vec2 local = vec2((f.x - offsetX) * cellWidth, (f.y - offsetY) * cellHeight);

    float visibility = zenithFade * aliasFade;

    if (snow) {
        float radius = max(0.075, pixel * 0.6);
        return smoothstep(radius + pixel, max(radius - pixel, 0.0), length(local))
                * min(1.0, 0.075 / radius)
                * visibility;
    }

    float streakLength = (0.35 + 0.35 * hash21(key + 13.1)) * cellHeight;
    float along = local.y / streakLength;
    float tilt = (-WindParams.x * sin(azimuth) + WindParams.y * cos(azimuth))
            * WindParams.z * RainParams.w;
    float lateral = abs(local.x - tilt * local.y);

    float halfWidth = max(0.015, pixel * 0.6);
    float body = smoothstep(halfWidth + pixel, max(halfWidth - pixel, 0.0), lateral)
            * min(1.0, 0.015 / halfWidth);
    float head = smoothstep(-0.08, 0.06, along);
    float tail = 1.0 - smoothstep(0.15, 1.0, along);
    float brightness = 0.55 + 0.45 * hash21(key + 21.7);
    return body * head * tail * brightness * visibility;
}

float rain(vec3 rayDir, float hitHorizontal) {
    float total = rainLayer(rayDir, hitHorizontal, 3.0, 1.6, 0.8, 0.0) * 0.85
            + rainLayer(rayDir, hitHorizontal, 9.0, 3.2, 1.4, 17.3) * 0.7
            + rainLayer(rayDir, hitHorizontal, 26.0, 6.4, 2.6, 41.1) * 0.55;
    return min(total, 1.0);
}

float rippleGrid(vec2 ground, float size, float rate, float seed, float pixel) {
    vec2 p = ground / size;
    vec2 id = floor(p);
    vec2 key = id + seed;
    if (hash21(key + 3.9) > RippleParams.z) {
        return 0.0;
    }

    vec2 center = vec2(0.2 + 0.6 * hash21(key), 0.2 + 0.6 * hash21(key + 5.1));
    float life = fract(RippleParams.y * rate + hash21(key + 11.7));
    float radius = life * size * 0.45;
    float dist = length((p - id - center) * size);

    float thickness = max(0.035, pixel);
    float rim = smoothstep(thickness, 0.0, abs(dist - radius));
    return rim * (1.0 - life) * (1.0 - life) * smoothstep(0.0, 0.12, life);
}

void main() {
    float depth = texture(DepthSampler, texCoord).r;
    bool sky = FogParams.z > 0.5 ? (depth <= 0.000001) : (depth >= 0.999999);

    vec3 relativeEnd = atmosphereReconstruct(texCoord, sky ? 1.0 : depth);
    float rawLength = length(relativeEnd);
    vec3 rayDir = rawLength > 1.0e-4 ? relativeEnd / rawLength : vec3(0.0, 0.0, -1.0);
    float rayLength = sky ? SKY_DISTANCE : min(rawLength, SKY_DISTANCE);

    float quality = FogExtra.w;
    float fusedSat = WindParams.w;

    float invH = FogParams.x;
    float density = FogColorDensity.a * exp(-GROUND_OFFSET * invH);

    float turbulence = FogExtra.x;
    if (turbulence > 0.0 && FogColorDensity.a > 0.0005) {
        vec3 drift = vec3(WindParams.x, 0.0, WindParams.y) * FogExtra.z * 0.35;
        if (quality < 1.5) {
            vec3 samplePoint = CameraOrigin.xyz + rayDir * min(rayLength, 90.0) - drift;
            float banding = fogNoiseQ(samplePoint * 0.014, quality);
            density *= mix(1.0, 0.2 + 1.6 * banding, turbulence);
        } else {
            vec3 nearPoint = CameraOrigin.xyz + rayDir * min(rayLength, 40.0) - drift;
            vec3 farPoint = CameraOrigin.xyz + rayDir * min(rayLength, 180.0) - drift;
            float banding = fogNoise(nearPoint * 0.018) * 0.55 + fogNoise(farPoint * 0.007) * 0.45;
            density *= mix(1.0, 0.2 + 1.6 * banding, turbulence);
        }
    }

    float dy = rayDir.y;
    float opticalDepth;
    if (abs(dy) < 1.0e-3) {
        opticalDepth = density * rayLength;
    } else {
        float exponent = clamp(-dy * rayLength * invH, -30.0, 30.0);
        opticalDepth = density * (1.0 - exp(exponent)) / (dy * invH);
    }
    opticalDepth = clamp(opticalDepth, 0.0, 30.0);

    float transmittance = exp(-opticalDepth);
    vec3 scene = texture(SceneSampler, texCoord).rgb;

    // Depth-aware Fog Softness (Reverse-Z safe: compares relative depth delta & sky mask)
    if (quality > 0.5 && SoftPostParams.x > 0.001 && FogColorDensity.a > 0.0 && !sky && (1.0 - transmittance) > 0.015) {
        vec2 res = vec2(textureSize(SceneSampler, 0));
        vec2 px = vec2(2.2) / max(res, vec2(1.0));
        vec2 offsets[4] = vec2[4](
            vec2(px.x, 0.0), vec2(-px.x, 0.0),
            vec2(0.0, px.y), vec2(0.0, -px.y)
        );
        vec3 accum = vec3(0.0);
        float wSum = 0.0;
        for (int i = 0; i < 4; i++) {
            vec2 sUv = clamp(texCoord + offsets[i], vec2(0.001), vec2(0.999));
            float sd = texture(DepthSampler, sUv).r;
            float sSky = (FogParams.z > 0.5) ? step(sd, 0.000001) : step(0.999999, sd);
            float relDiff = abs(sd - depth) / max(depth, 1.0e-4);
            float w = (1.0 - sSky) * (1.0 - smoothstep(0.02, 0.14, relDiff));
            accum += texture(SceneSampler, sUv).rgb * w;
            wSum += w;
        }
        if (wSum > 0.01) {
            float softBlend = SoftPostParams.x * (1.0 - transmittance) * 0.65;
            scene = mix(scene, accum / wSum, clamp(softBlend, 0.0, 0.7));
        }
    }

    float flash = FlashColor.a;

    vec3 scatter = FogColorDensity.rgb * FogParams.y * (1.0 - transmittance) * (1.0 + flash * 2.5);

    float sunScatter = FogExtra.y * SunDirection.w;
    if (sunScatter > 0.0) {
        scatter *= 1.0 + henyeyGreenstein(dot(rayDir, SunDirection.xyz), 0.66) * 0.1 * sunScatter;
    }

    vec3 color = scene * transmittance + scatter;
    if (flash > 0.0) {
        color += scene * FlashColor.rgb * flash * 0.65;
        color += FlashColor.rgb * flash * 0.05;
    }

    if (HazeColor.a > 0.0) {
        float hazeInvH = HazeParams.x;
        float deckOffset = HazeParams.y;
        float hazeDepth = HazeColor.a * hazeOpticalDepth(deckOffset, dy, rayLength, hazeInvH);

        float banks = HazeParams.z;
        if (banks > 0.0) {
            float deckHit = clamp(abs(dy) > 1.0e-3 ? -deckOffset / dy : 0.0, 0.0, min(rayLength, 320.0));
            vec3 deckPoint = CameraOrigin.xyz + rayDir * deckHit
                    - vec3(WindParams.x, 0.0, WindParams.y) * FogExtra.z * 0.18;
            float field = fogNoiseQ(deckPoint * vec3(0.013, 0.055, 0.013), quality);
            hazeDepth *= mix(1.0, 0.15 + 1.75 * field, banks);
        }

        float hazeTransmittance = exp(-clamp(hazeDepth, 0.0, 30.0));
        vec3 hazeScatter = HazeColor.rgb * (1.0 - hazeTransmittance) * (1.0 + flash * 2.5);
        float moonScatter = HazeParams.w;
        if (moonScatter > 0.0) {
            hazeScatter *= 1.0 + henyeyGreenstein(dot(rayDir, SunDirection.xyz), 0.72) * 0.14 * moonScatter;
        }
        color = color * hazeTransmittance + hazeScatter;
    }

    if (RippleParams.x > 0.0 && !sky) {
        vec3 hitPos = CameraOrigin.xyz + relativeEnd;
        vec3 hitDdx = dFdx(hitPos);
        vec3 hitDdy = dFdy(hitPos);
        vec3 hitCross = cross(hitDdx, hitDdy);
        float hitCrossLength = length(hitCross);
        vec3 hitNormal = hitCrossLength > 1.0e-9 ? hitCross / hitCrossLength : vec3(0.0, 1.0, 0.0);
        if (dot(hitNormal, rayDir) > 0.0) {
            hitNormal = -hitNormal;
        }
        float surfacePixel = clamp(max(length(hitDdx), length(hitDdy)), 1.0e-4, 0.35);

        float upFacing = smoothstep(0.6, 0.86, hitNormal.y);
        float reach = 1.0 - smoothstep(18.0, 42.0, rawLength);
        float strength = RippleParams.x * upFacing * reach;
        if (strength > 0.0) {
            float rings = rippleGrid(hitPos.xz, 1.3, 1.0, 0.0, surfacePixel)
                    + rippleGrid(hitPos.xz, 2.7, 0.6, 31.4, surfacePixel);
            color += RainColor.rgb * min(rings, 1.5) * strength;
        }
    }

    float outside = clamp(SunScreenParams.w, 0.0, 1.0);

    if (RainParams.x > 0.0 && outside > 0.02) {
        float hitHorizontal = sky ? 1.0e6 : rawLength * length(rayDir.xz);
        float coverage = rain(rayDir, hitHorizontal) * outside;
        if (coverage > 0.0) {
            color = mix(color, RainColor.rgb * (1.0 + flash * 2.0), coverage * RainColor.a);
        }
    }

    // 26-step 3D DDA world-cell particles (1 = Dust, 2 = Snow, 3 = Embers) occluded by sceneDistance
    float sceneDistance = sky ? 1.0e9 : rawLength;
    int ddaMode = int(DdaParticleParams.x + 0.5);
    float ddaVisibility = (ddaMode == 3) ? max(0.35, outside) : outside;
    if (ddaMode > 0 && DdaParticleParams.y > 0.005 && ddaVisibility > 0.02) {
        float cellSize = max(1.2, DdaParticleParams.w);
        vec3 flowVel = vec3(WindParams.x * WindParams.z * 0.65, 0.0, WindParams.y * WindParams.z * 0.65);
        if (ddaMode == 3) {
            flowVel += vec3(0.0, 1.45 * DdaParticleParams.z, 0.0);
        } else if (ddaMode == 2) {
            flowVel += vec3(0.18, -1.05 * DdaParticleParams.z, 0.12);
        } else {
            flowVel += vec3(0.08, 0.16 * DdaParticleParams.z, 0.06);
        }
        vec3 originGrid = (CameraOrigin.xyz - flowVel * FogExtra.z) / cellSize;
        vec3 rd = rayDir;
        vec3 cell = floor(originGrid);
        vec3 stepDir = sign(rd);
        vec3 tMax = vec3(1.0e9);
        vec3 tDelta = vec3(1.0e9);
        if (abs(rd.x) > 1.0e-5) {
            float invX = 1.0 / rd.x;
            tMax.x = ((cell.x + step(0.0, rd.x)) - originGrid.x) * invX;
            tDelta.x = abs(invX);
        }
        if (abs(rd.y) > 1.0e-5) {
            float invY = 1.0 / rd.y;
            tMax.y = ((cell.y + step(0.0, rd.y)) - originGrid.y) * invY;
            tDelta.y = abs(invY);
        }
        if (abs(rd.z) > 1.0e-5) {
            float invZ = 1.0 / rd.z;
            tMax.z = ((cell.z + step(0.0, rd.z)) - originGrid.z) * invZ;
            tDelta.z = abs(invZ);
        }
        float maxGridDist = sceneDistance / cellSize;
        float glowAccum = 0.0;
        vec3 pTint = (ddaMode == 3) ? vec3(1.0, 0.45, 0.16)
                   : (ddaMode == 2) ? vec3(0.90, 0.95, 1.0)
                   : mix(vec3(0.95, 0.88, 0.72), PostTintParams.rgb, 0.45);
        float radiusBase = (ddaMode == 2) ? 0.045 : (ddaMode == 3 ? 0.036 : 0.024);
        float tEnter = 0.0;
        int ddaSteps = (quality < 0.5) ? 10 : ((quality < 1.5) ? 14 : 26);

        for (int i = 0; i < 26; i++) {
            if (i >= ddaSteps || tEnter > maxGridDist || tEnter > 18.0) break;

            vec3 h = hash33(cell);
            if (h.x < clamp(0.28 * DdaParticleParams.y, 0.04, 0.85)) {
                vec3 pLocal = vec3(0.18) + 0.64 * fract(h * 13.7 + vec3(
                    sin(FogExtra.z * 0.9 * DdaParticleParams.z + h.y * 6.283) * 0.14,
                    cos(FogExtra.z * 0.7 * DdaParticleParams.z + h.z * 6.283) * 0.14,
                    sin(FogExtra.z * 0.8 * DdaParticleParams.z + h.x * 6.283) * 0.14
                ));
                vec3 pCenter = cell + pLocal;
                float proj = dot(pCenter - originGrid, rd);
                if (proj > 0.15 && proj < maxGridDist) {
                    float dPerp = length(pCenter - (originGrid + rd * proj));
                    float distFade = exp(-proj * 0.14) * smoothstep(0.05, 0.6, proj);
                    float rad = radiusBase * (0.65 + 0.65 * h.y);
                    glowAccum += (1.0 - smoothstep(0.0, rad, dPerp)) * distFade * (0.55 + 0.45 * h.z);
                }
            }

            if (tMax.x < tMax.y) {
                if (tMax.x < tMax.z) { tEnter = tMax.x; tMax.x += tDelta.x; cell.x += stepDir.x; }
                else { tEnter = tMax.z; tMax.z += tDelta.z; cell.z += stepDir.z; }
            } else {
                if (tMax.y < tMax.z) { tEnter = tMax.y; tMax.y += tDelta.y; cell.y += stepDir.y; }
                else { tEnter = tMax.z; tMax.z += tDelta.z; cell.z += stepDir.z; }
            }
        }
        color += pTint * min(glowAccum, 1.4) * ddaVisibility * 0.65;
    }

    // Screen-space God Rays (Reverse-Z safe)
    if (SunScreenParams.z > 0.005 && outside > 0.05) {
        int raySteps = (quality < 0.5) ? 8 : ((quality < 1.5) ? 12 : 20);
        float fRaySteps = float(raySteps);
        vec2 deltaUv = (texCoord - SunScreenParams.xy) / fRaySteps * 0.88;
        vec2 sampleUv = texCoord;
        float decay = 1.0;
        float raySum = 0.0;
        for (int i = 0; i < 20; i++) {
            if (i >= raySteps) break;
            sampleUv -= deltaUv;
            vec2 cUv = clamp(sampleUv, vec2(0.001), vec2(0.999));
            float sd = texture(DepthSampler, cUv).r;
            float isSkyS = (FogParams.z > 0.5) ? step(sd, 0.000001) : step(0.999999, sd);
            float inScreen = step(0.0, sampleUv.x) * step(sampleUv.x, 1.0) * step(0.0, sampleUv.y) * step(sampleUv.y, 1.0);
            raySum += isSkyS * decay * inScreen;
            decay *= 0.91;
        }
        float sunAligned = max(0.0, dot(rayDir, SunDirection.xyz));
        float raysIntensity = (raySum / fRaySteps) * SunScreenParams.z * outside * (0.45 + 0.55 * sunAligned);
        vec3 rayCol = mix(PostTintParams.rgb, vec3(1.0, 0.88, 0.64), 0.5);
        color += rayCol * raysIntensity * 0.42;
    }

    if (BoltParams.x > 0.0 && sceneDistance > CameraOrigin.w) {
        color += lightning(vec2(texCoord.x * FogParams.w, texCoord.y));
    }

    // Post-grade, subtle horizon warmth, vignette & winter edge frost
    if (SoftPostParams.y > 0.001) {
        float grade = SoftPostParams.y;
        vec3 graded = mix(color, color * PostTintParams.rgb * 1.12, 0.24 * grade);
        graded += PostTintParams.rgb * PostTintParams.w * 0.04 * grade;
        color = mix(color, clamp(graded * (1.0 + 0.08 * grade) - 0.02 * grade, 0.0, 4.0), 0.85);
    }

    if (SoftPostParams.z > 0.001) {
        float edge = smoothstep(0.36, 1.02, length(texCoord - vec2(0.5)));
        color *= 1.0 - edge * SoftPostParams.z * 0.32;
    }

    if (SoftPostParams.w > 0.001) {
        float edge = smoothstep(0.45, 0.98, length(texCoord - vec2(0.5)));
        float frostNoise = fogNoiseQ(vec3(texCoord * 24.0, FogExtra.z * 0.03), quality);
        float frostMask = edge * smoothstep(0.32, 0.78, frostNoise) * SoftPostParams.w;
        color = mix(color, vec3(0.84, 0.93, 1.0), clamp(frostMask * 0.28, 0.0, 0.32));
    }

    if (abs(fusedSat - 1.0) > 0.005) {
        float luma = dot(color, vec3(0.2126, 0.7152, 0.0722));
        color = max(mix(vec3(luma), color, fusedSat), vec3(0.0));
    }

    fragColor = vec4(color, 1.0);
}
