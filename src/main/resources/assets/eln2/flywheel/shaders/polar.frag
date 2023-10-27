
struct Fragment {
    vec2 texCoords;
    vec4 color;
    float diffuse;
    vec2 light;
};

vec4 fragment(Fragment r) {
    vec4 overrides = r.color;

    vec4 tex = FLWBlockTexture(r.texCoords);
    vec3 gameLight = FLWLight(r.light).rgb;
    vec3 overrideLight = vec3(r.color.a);

    return vec4(tex.rgb * max(gameLight, overrideLight) * r.diffuse, tex.a) * vec4(overrides.r, overrides.g, overrides.b, 1);
}
