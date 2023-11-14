
struct Instance {
    vec2 light;
    vec4 color1;
    vec4 color2;
    mat4 transform;
    mat3 normalMat;
};

void vertex(inout Vertex v, Instance i) {
    v.pos = (i.transform * vec4(v.pos, 1.)).xyz;
    v.normal = i.normalMat * v.normal;

    vec4 colors[2];
    colors[0] = i.color1;
    colors[1] = i.color2;

    int vertexIdQuad = gl_VertexID % 4;
    int pole = vertexIdQuad / 2;

    v.color = colors[pole];
    v.light = i.light;
}
