
struct Fragment {
    vec2 texCoords;
    vec4 color;
    float diffuse;
    vec2 light;
};

vec4 fragment(Fragment r) {
    vec4 overrides = r.color;
    vec4 tex = FLWBlockTexture(r.texCoords);

    return vec4(tex.rgb * max(vec3(overrides.a), FLWLight(r.light).rgb) * r.diffuse, tex.a) * vec4(overrides.r, overrides.g, overrides.b, 1);
}
