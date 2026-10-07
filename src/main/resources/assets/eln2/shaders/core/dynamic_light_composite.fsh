#version 150

uniform sampler2D LightSampler;

out vec4 fragColor;

void main() {
    fragColor = texelFetch(LightSampler, ivec2(gl_FragCoord.xy), 0);
}
