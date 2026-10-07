#version 150

/**
 * Deferred spotlight pass with soft shadows and in-scattering.
 *
 * Positions are reconstructed from the scene depth relative to the camera, and normals from neighboring depth samples.
 * Surface color comes from the unlit albedo pass, or is estimated from the scene color where that pass has no coverage.
 * Shadows use a blocker search and percentage-closer filtering, comparing each sample against the receiver plane.
 * The result is premultiplied light over the scene, so the light never darkens a surface that is already brighter.
 */

uniform sampler2D SceneDepthSampler;
uniform sampler2D SceneColorSampler;
uniform sampler2D AlbedoSampler;
uniform sampler2D ShadowDepthSampler;
uniform sampler2DShadow ShadowCompareSampler;

uniform mat4 InverseViewProjectionMatrix;
uniform mat4 ViewProjectionMatrix;
uniform mat4 LightViewMatrix;
uniform vec3 LightPosition;
uniform vec3 LightDirection;
uniform vec3 LightColor;
uniform float LightIntensity;
uniform float LightRange;
uniform float LightHalfAngle;
uniform float LightSourceRadius;
uniform float LightScattering;
uniform float ShadowTanHalfFieldOfView;
uniform float ShadowNear;
uniform float ShadowFar;
uniform float ShadowMapSize;
uniform vec2 ScreenSize;
uniform vec4 FogParameters;

out vec4 fragColor;

const float PI = 3.14159265;
const float GOLDEN_ANGLE = 2.39996323;
const float FAR_DISTANCE = 1.0e9;

const int BLOCKER_SEARCH_SAMPLE_COUNT = 16;
const int FILTER_SAMPLE_COUNT = 16;
const int SCATTERING_STEP_COUNT = 16;

const float MAXIMUM_SEARCH_TEXELS = 32.0;
const float MINIMUM_FILTER_TEXELS = 1.0;
const float MAXIMUM_FILTER_TEXELS = 20.0;
const float MAXIMUM_RECEIVER_SLOPE = 12.0;

const float BOUNCE_FACTOR = 0.05;
const float BOUNCE_ANGLE_EXTENT = 1.45;
const float SURFACE_NEAR_DISTANCE = 0.5;
const float SCATTERING_NEAR_DISTANCE = 1.5;
const float SCATTERING_ANISOTROPY = 0.4;

float interleavedGradientNoise(vec2 pixel) {
    return fract(52.9829189 * fract(dot(pixel, vec2(0.06711056, 0.00583715))));
}

float maximumComponent(vec3 value) {
    return max(max(value.r, value.g), value.b);
}

vec3 cameraRelativePosition(vec2 uv, float depth) {
    vec4 position = InverseViewProjectionMatrix * vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    return position.xyz / position.w;
}

vec3 reconstructNormal(vec2 uv, float centerDepth, vec3 centerPosition) {
    vec2 horizontalStep = vec2(1.0 / ScreenSize.x, 0.0);
    vec2 verticalStep = vec2(0.0, 1.0 / ScreenSize.y);

    float leftDepth = texture(SceneDepthSampler, uv - horizontalStep).r;
    float rightDepth = texture(SceneDepthSampler, uv + horizontalStep).r;
    float farLeftDepth = texture(SceneDepthSampler, uv - 2.0 * horizontalStep).r;
    float farRightDepth = texture(SceneDepthSampler, uv + 2.0 * horizontalStep).r;
    float downDepth = texture(SceneDepthSampler, uv - verticalStep).r;
    float upDepth = texture(SceneDepthSampler, uv + verticalStep).r;
    float farDownDepth = texture(SceneDepthSampler, uv - 2.0 * verticalStep).r;
    float farUpDepth = texture(SceneDepthSampler, uv + 2.0 * verticalStep).r;

    float leftError = abs(2.0 * leftDepth - farLeftDepth - centerDepth);
    float rightError = abs(2.0 * rightDepth - farRightDepth - centerDepth);
    float downError = abs(2.0 * downDepth - farDownDepth - centerDepth);
    float upError = abs(2.0 * upDepth - farUpDepth - centerDepth);

    vec3 horizontalTangent = leftError < rightError
        ? centerPosition - cameraRelativePosition(uv - horizontalStep, leftDepth)
        : cameraRelativePosition(uv + horizontalStep, rightDepth) - centerPosition;

    vec3 verticalTangent = downError < upError
        ? centerPosition - cameraRelativePosition(uv - verticalStep, downDepth)
        : cameraRelativePosition(uv + verticalStep, upDepth) - centerPosition;

    vec3 normal = cross(horizontalTangent, verticalTangent);
    float normalLengthSquared = dot(normal, normal);

    if (normalLengthSquared < 1.0e-20) {
        return -normalize(centerPosition);
    }

    normal *= inversesqrt(normalLengthSquared);

    if (dot(normal, centerPosition) > 0.0) {
        normal = -normal;
    }

    return normal;
}

float rangeWindow(float distanceToLight) {
    float ratio = distanceToLight / LightRange;
    float ratioSquared = ratio * ratio;
    float window = clamp(1.0 - ratioSquared * ratioSquared, 0.0, 1.0);
    return window * window;
}

float illuminance(float distanceToLight, float nearDistance) {
    float referenceDistance = LightRange * 0.5;
    float falloff = referenceDistance * referenceDistance / (distanceToLight * distanceToLight + nearDistance * nearDistance);
    return LightIntensity * falloff * rangeWindow(distanceToLight);
}

float beamProfile(float normalizedAngle) {
    float hotspot = exp(-11.0 * normalizedAngle * normalizedAngle);
    float coronaOffset = (normalizedAngle - 0.42) / 0.1;
    float corona = 0.08 * exp(-coronaOffset * coronaOffset);
    float spill = 0.4 * (1.0 - smoothstep(0.75, 1.0, normalizedAngle));
    return (hotspot + corona + spill) / 1.4;
}

vec3 beamTint(float normalizedAngle) {
    return mix(vec3(1.0), vec3(1.0, 0.94, 0.86), smoothstep(0.35, 1.0, normalizedAngle));
}

float normalizedBeamAngle(vec3 directionFromLight) {
    return acos(clamp(dot(directionFromLight, LightDirection), -1.0, 1.0)) / LightHalfAngle;
}

float fogVisibility(vec3 position) {
    float fogDistance = FogParameters.w > 0.5 ? max(length(position.xz), abs(position.y)) : length(position);

    if (fogDistance <= FogParameters.x) {
        return 1.0;
    }

    float fogValue = fogDistance < FogParameters.y ? smoothstep(FogParameters.x, FogParameters.y, fogDistance) : 1.0;
    return 1.0 - fogValue * FogParameters.z;
}

vec2 vogelDiskOffset(int sampleIndex, int sampleCount, float rotation) {
    float radius = sqrt((float(sampleIndex) + 0.5) / float(sampleCount));
    float angle = float(sampleIndex) * GOLDEN_ANGLE + rotation;
    return radius * vec2(cos(angle), sin(angle));
}

float shadowLinearDepth(float windowDepth) {
    float ndcDepth = windowDepth * 2.0 - 1.0;
    return 2.0 * ShadowNear * ShadowFar / (ShadowFar + ShadowNear - ndcDepth * (ShadowFar - ShadowNear));
}

float shadowWindowDepth(float linearDepth) {
    float ndcDepth = (ShadowFar + ShadowNear - 2.0 * ShadowNear * ShadowFar / max(linearDepth, ShadowNear)) / (ShadowFar - ShadowNear);
    return clamp(ndcDepth * 0.5 + 0.5, 0.0, 1.0);
}

vec2 shadowUvFromLightView(vec3 lightViewPosition) {
    return lightViewPosition.xy / (-lightViewPosition.z * ShadowTanHalfFieldOfView) * 0.5 + 0.5;
}

float receiverPlaneDepth(vec2 shadowUv, vec3 planeNormal, float planeDistance, float centerDepth, float maximumDeviation) {
    vec3 ray = vec3((shadowUv * 2.0 - 1.0) * ShadowTanHalfFieldOfView, -1.0);
    float denominator = min(dot(planeNormal, ray), -1.0e-4);
    return clamp(planeDistance / denominator, centerDepth - maximumDeviation, centerDepth + maximumDeviation);
}

float computeShadow(vec3 position, vec3 normal, float normalDotLight, float noise) {
    mat3 lightRotation = mat3(LightViewMatrix);
    vec3 lightViewPosition = lightRotation * (position - LightPosition);
    float receiverDepth = -lightViewPosition.z;

    if (receiverDepth <= ShadowNear) {
        return 1.0;
    }

    float texelSize = 1.0 / ShadowMapSize;
    float uvToWorld = 2.0 * receiverDepth * ShadowTanHalfFieldOfView;
    float sinIncidence = sqrt(max(1.0 - normalDotLight * normalDotLight, 0.0));
    vec3 planeNormal = lightRotation * normal;
    vec3 offsetPosition = lightViewPosition + planeNormal * uvToWorld * texelSize * (0.5 + 1.5 * sinIncidence);

    float centerDepth = -offsetPosition.z;
    vec2 centerUv = shadowUvFromLightView(offsetPosition);

    if (any(lessThan(centerUv, vec2(0.0))) || any(greaterThan(centerUv, vec2(1.0)))) {
        return 1.0;
    }

    float planeDistance = min(dot(planeNormal, offsetPosition), -1.0e-4);
    float depthBias = 0.002 + 0.001 * centerDepth;
    float rotation = noise * 2.0 * PI;

    float searchRadius = LightSourceRadius * (centerDepth - ShadowNear) / (centerDepth * ShadowNear * 2.0 * ShadowTanHalfFieldOfView);
    searchRadius = clamp(searchRadius, texelSize, MAXIMUM_SEARCH_TEXELS * texelSize);
    float searchDeviation = searchRadius * uvToWorld * MAXIMUM_RECEIVER_SLOPE;

    float blockerDepthSum = 0.0;
    float blockerCount = 0.0;

    for (int sampleIndex = 0; sampleIndex < BLOCKER_SEARCH_SAMPLE_COUNT; sampleIndex++) {
        vec2 sampleUv = centerUv + vogelDiskOffset(sampleIndex, BLOCKER_SEARCH_SAMPLE_COUNT, rotation) * searchRadius;
        float occluderDepth = shadowLinearDepth(texture(ShadowDepthSampler, sampleUv).r);
        float sampleReceiverDepth = receiverPlaneDepth(sampleUv, planeNormal, planeDistance, centerDepth, searchDeviation);

        if (occluderDepth < sampleReceiverDepth - depthBias) {
            blockerDepthSum += occluderDepth;
            blockerCount += 1.0;
        }
    }

    if (blockerCount < 0.5) {
        return texture(ShadowCompareSampler, vec3(centerUv, shadowWindowDepth(centerDepth - depthBias)));
    }

    float blockerDepth = blockerDepthSum / blockerCount;
    float penumbraRadius = LightSourceRadius * (centerDepth - blockerDepth) / max(blockerDepth, ShadowNear);
    float filterRadius = clamp(penumbraRadius / uvToWorld, MINIMUM_FILTER_TEXELS * texelSize, MAXIMUM_FILTER_TEXELS * texelSize);
    float filterDeviation = filterRadius * uvToWorld * MAXIMUM_RECEIVER_SLOPE;

    float visibility = 0.0;

    for (int sampleIndex = 0; sampleIndex < FILTER_SAMPLE_COUNT; sampleIndex++) {
        vec2 sampleUv = centerUv + vogelDiskOffset(sampleIndex, FILTER_SAMPLE_COUNT, rotation) * filterRadius;
        float sampleReceiverDepth = receiverPlaneDepth(sampleUv, planeNormal, planeDistance, centerDepth, filterDeviation);
        visibility += texture(ShadowCompareSampler, vec3(sampleUv, shadowWindowDepth(sampleReceiverDepth - depthBias)));
    }

    return visibility / float(FILTER_SAMPLE_COUNT);
}

float pointShadow(vec3 position) {
    vec3 lightViewPosition = mat3(LightViewMatrix) * (position - LightPosition);
    float pointDepth = -lightViewPosition.z;

    if (pointDepth <= ShadowNear) {
        return 1.0;
    }

    vec2 shadowUv = shadowUvFromLightView(lightViewPosition);
    return texture(ShadowCompareSampler, vec3(shadowUv, shadowWindowDepth(pointDepth * 0.99)));
}

vec2 sphereInterval(vec3 rayDirection) {
    float projection = dot(rayDirection, LightPosition);
    float discriminant = projection * projection - dot(LightPosition, LightPosition) + LightRange * LightRange;

    if (discriminant <= 0.0) {
        return vec2(1.0, 0.0);
    }

    float root = sqrt(discriminant);
    return vec2(projection - root, projection + root);
}

vec2 coneInterval(vec3 rayDirection) {
    vec3 originFromApex = -LightPosition;
    float cosHalfAngle = cos(LightHalfAngle);
    float cosSquared = cosHalfAngle * cosHalfAngle;
    float directionAlongAxis = dot(rayDirection, LightDirection);
    float originAlongAxis = dot(originFromApex, LightDirection);

    float quadratic = directionAlongAxis * directionAlongAxis - cosSquared;
    float linear = 2.0 * (directionAlongAxis * originAlongAxis - cosSquared * dot(rayDirection, originFromApex));
    float constant = originAlongAxis * originAlongAxis - cosSquared * dot(originFromApex, originFromApex);

    float firstRoot = -1.0;
    float secondRoot = -1.0;

    if (abs(quadratic) < 1.0e-6) {
        if (abs(linear) > 1.0e-6) {
            firstRoot = -constant / linear;
        }
    } else {
        float discriminant = linear * linear - 4.0 * quadratic * constant;

        if (discriminant >= 0.0) {
            float root = sqrt(discriminant);
            float rootA = (-linear - root) / (2.0 * quadratic);
            float rootB = (-linear + root) / (2.0 * quadratic);
            firstRoot = min(rootA, rootB);
            secondRoot = max(rootA, rootB);
        }
    }

    bool firstValid = firstRoot > 0.0 && originAlongAxis + firstRoot * directionAlongAxis > 0.0;
    bool secondValid = secondRoot > 0.0 && originAlongAxis + secondRoot * directionAlongAxis > 0.0;
    bool originInside = originAlongAxis > 0.0 && constant > 0.0;

    if (originInside) {
        if (firstValid) {
            return vec2(0.0, firstRoot);
        }

        if (secondValid) {
            return vec2(0.0, secondRoot);
        }

        return vec2(0.0, FAR_DISTANCE);
    }

    if (firstValid && secondValid) {
        return vec2(firstRoot, secondRoot);
    }

    if (firstValid) {
        return vec2(firstRoot, FAR_DISTANCE);
    }

    if (secondValid) {
        return vec2(secondRoot, FAR_DISTANCE);
    }

    return vec2(1.0, 0.0);
}

float henyeyGreenstein(float cosScatteringAngle) {
    float anisotropySquared = SCATTERING_ANISOTROPY * SCATTERING_ANISOTROPY;
    float denominator = 1.0 + anisotropySquared - 2.0 * SCATTERING_ANISOTROPY * cosScatteringAngle;
    return (1.0 - anisotropySquared) / (denominator * sqrt(denominator));
}

float computeInScattering(vec3 rayDirection, float maximumDistance, float noise) {
    vec2 sphere = sphereInterval(rayDirection);
    vec2 cone = coneInterval(rayDirection);
    float start = max(max(sphere.x, cone.x), 0.0);
    float end = min(min(sphere.y, cone.y), maximumDistance);

    if (end <= start) {
        return 0.0;
    }

    float stepLength = (end - start) / float(SCATTERING_STEP_COUNT);
    float total = 0.0;

    for (int stepIndex = 0; stepIndex < SCATTERING_STEP_COUNT; stepIndex++) {
        vec3 samplePosition = rayDirection * (start + (float(stepIndex) + noise) * stepLength);
        vec3 fromLight = samplePosition - LightPosition;
        float distanceToLight = length(fromLight);
        vec3 directionFromLight = fromLight / max(distanceToLight, 1.0e-4);
        float normalizedAngle = normalizedBeamAngle(directionFromLight);

        if (normalizedAngle >= 1.0) {
            continue;
        }

        float radiance = illuminance(distanceToLight, SCATTERING_NEAR_DISTANCE) * beamProfile(normalizedAngle);
        float phase = henyeyGreenstein(dot(directionFromLight, -rayDirection));
        total += radiance * phase * pointShadow(samplePosition) * fogVisibility(samplePosition);
    }

    return total * stepLength * LightScattering;
}

float computeGlare(vec3 rayDirection) {
    float distanceToLight = length(LightPosition);

    if (distanceToLight < 1.0) {
        return 0.0;
    }

    vec3 directionToLight = LightPosition / distanceToLight;
    float facingAngle = normalizedBeamAngle(-directionToLight);

    if (facingAngle >= 1.0) {
        return 0.0;
    }

    vec4 lightClip = ViewProjectionMatrix * vec4(LightPosition, 1.0);

    if (lightClip.w <= 0.0) {
        return 0.0;
    }

    vec3 lightNdc = lightClip.xyz / lightClip.w;

    if (any(greaterThan(abs(lightNdc), vec3(1.0)))) {
        return 0.0;
    }

    float sceneDepthAtLight = texture(SceneDepthSampler, lightNdc.xy * 0.5 + 0.5).r;

    if (lightNdc.z * 0.5 + 0.5 > sceneDepthAtLight) {
        return 0.0;
    }

    float angleToLight = acos(clamp(dot(rayDirection, directionToLight), -1.0, 1.0));
    float apparentRadius = max(LightSourceRadius / distanceToLight, 0.0015);
    float coreOffset = angleToLight / apparentRadius;
    float haloOffset = angleToLight / (apparentRadius * 8.0 + 0.02);
    float glare = 3.0 * exp(-coreOffset * coreOffset) + 0.15 * exp(-haloOffset * haloOffset);

    return glare * beamProfile(facingAngle) * LightIntensity * fogVisibility(LightPosition);
}

vec3 estimateAlbedo(vec3 sceneColor) {
    float peak = maximumComponent(sceneColor);
    float confidence = smoothstep(0.015, 0.1, peak);
    vec3 chromaticity = sceneColor / max(peak, 1.0e-4);
    return mix(vec3(0.45), chromaticity * 0.6, confidence);
}

void main() {
    vec2 pixel = gl_FragCoord.xy;
    vec2 uv = pixel / ScreenSize;
    float noise = interleavedGradientNoise(pixel);

    float depth = texture(SceneDepthSampler, uv).r;
    bool isSky = depth >= 1.0;
    vec3 position = cameraRelativePosition(uv, depth);
    float distanceToSurface = length(position);
    vec3 rayDirection = position / max(distanceToSurface, 1.0e-4);

    vec3 surfaceLight = vec3(0.0);

    if (!isSky) {
        vec3 toLight = LightPosition - position;
        float distanceToLight = length(toLight);
        vec3 directionToLight = toLight / max(distanceToLight, 1.0e-4);
        float normalizedAngle = normalizedBeamAngle(-directionToLight);

        if (distanceToLight < LightRange && normalizedAngle < BOUNCE_ANGLE_EXTENT) {
            vec3 normal = reconstructNormal(uv, depth, position);
            float normalDotLight = dot(normal, directionToLight);
            float baseIlluminance = illuminance(distanceToLight, SURFACE_NEAR_DISTANCE);
            float bounceSpread = 1.0 - smoothstep(0.5, BOUNCE_ANGLE_EXTENT, normalizedAngle);
            float bounce = BOUNCE_FACTOR * baseIlluminance * bounceSpread * (0.6 + 0.4 * max(normalDotLight, 0.0));
            float direct = 0.0;

            if (normalizedAngle < 1.0 && normalDotLight > 0.0) {
                direct = baseIlluminance * beamProfile(normalizedAngle) * normalDotLight;

                if (direct > 0.002) {
                    direct *= computeShadow(position, normal, normalDotLight, noise);
                }
            }

            float exposure = 1.0 - exp(-(direct + bounce));
            surfaceLight = LightColor * beamTint(min(normalizedAngle, 1.0)) * exposure * fogVisibility(position);
        }
    }

    float emission = computeGlare(rayDirection);

    if (LightScattering > 0.0) {
        emission += computeInScattering(rayDirection, isSky ? FAR_DISTANCE : distanceToSurface, noise);
    }

    vec3 emittedLight = LightColor * (1.0 - exp(-emission));
    float emittedCoverage = maximumComponent(emittedLight);
    float surfaceCoverage = maximumComponent(surfaceLight);

    if (emittedCoverage + surfaceCoverage < 1.0 / 1024.0) {
        discard;
    }

    vec3 sceneColor = texture(SceneColorSampler, uv).rgb;
    vec4 albedoSample = texture(AlbedoSampler, uv);
    bool hasAlbedo = max(maximumComponent(albedoSample.rgb), albedoSample.a) > 0.5 / 255.0;
    vec3 albedo = max(hasAlbedo ? albedoSample.rgb : estimateAlbedo(sceneColor), sceneColor);

    vec3 color = emittedLight + surfaceLight * albedo * (1.0 - emittedCoverage);
    float coverage = 1.0 - (1.0 - surfaceCoverage) * (1.0 - emittedCoverage);
    float dither = (interleavedGradientNoise(pixel + vec2(37.0, 17.0)) - 0.5) / 255.0;

    fragColor = vec4(max(color + dither, vec3(0.0)), coverage);
}
