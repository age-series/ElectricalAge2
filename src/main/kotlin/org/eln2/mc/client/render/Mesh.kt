package org.eln2.mc.client.render

import com.jozufozu.flywheel.api.vertex.VertexList
import com.jozufozu.flywheel.util.Color
import org.ageseries.libage.data.MutableSetMapMultiMap
import org.eln2.mc.bind
import org.eln2.mc.mathematics.*
import java.io.File
import java.io.FileWriter
import kotlin.math.PI

class ObjWriter {
    val file = FileWriter(File("test.obj"), false)

    private fun getValue(d: Double) = d.toBigDecimal().toPlainString()
    private fun getValue(v: Vector3d) = "${getValue(v.x)} ${getValue(v.y)} ${getValue(v.z)}"

    fun vert(v: Vector3d) = file.appendLine("v ${getValue(v)}")
    fun vert(x: Double, y: Double, z: Double) = vert(Vector3d(x, y, z))
    fun vert(v: List<Vector3d>) = file.appendLine("v ${v.joinToString(" ") { getValue(it) }}")
    fun indices(indices: List<Int>) = file.appendLine("f ${indices.map { it + 1 }.joinToString(" ")}")

    fun close() {
        file.close()
    }
}

fun polygralScan(ref: Vector3d, vectors: List<Vector3d>): Vector3d {
    fun get(i: Int) = if (i == vectors.size) vectors[0] else vectors[i]

    var sum = Vector3d.zero

    vectors.indices.forEach {
        sum += ((get(it) - ref) x (get(it + 1) - ref)) * 0.5
    }

    return sum
}

data class IndexedTri(val a: Int, val b: Int, val c: Int) {
    val indices get() = listOf(a, b, c)
}

data class IndexedQuad(val a: Int, val b: Int, val c: Int, val d: Int) {
    val indices get() = listOf(a, b, c, d)
    fun rewind() = IndexedQuad(a, d, c, b)
}

interface Quads
interface Triangles

data class MeshBuilder<Vertex, Primitive>(val vertices: ArrayList<Vertex>, val edges: MutableSetMapMultiMap<Int, Int>) {
    constructor() : this(ArrayList(), MutableSetMapMultiMap())

    fun <P> reparam() = MeshBuilder<Vertex, P>(vertices.bind(), edges.bind())

    fun addVertex(v: Vertex): Int {
        val idx = vertices.size
        vertices.add(v)
        return idx
    }

    fun addEdge(a: Int, b: Int) {
        edges[a] = b
        edges[b] = a
    }

    fun cycleScan(vert: Int, targetLength: Int): ArrayList<ArrayList<Int>> {
        val visits = HashMap<Int, Int>()
        val cycles = ArrayList<ArrayList<Int>>()

        fun levelScan(
            rxRemaining: Int,
            actualVert: Int,
            actualVertSource: Int,
            cycleVert: Int,
            user: (ArrayList<Int>) -> Unit,
        ) {
            visits[actualVert] = actualVertSource

            if (rxRemaining > 0) {
                edges[actualVert].forEach { actualVertConn ->
                    if (!visits.contains(actualVertConn)) {
                        levelScan(
                            rxRemaining = rxRemaining - 1,
                            actualVert = actualVertConn,
                            actualVertSource = actualVert,
                            cycleVert,
                            user
                        )
                    }
                }

                return
            }

            if (edges[actualVert].contains(cycleVert)) {
                val pathway = ArrayList<Int>()

                var current = actualVert

                while (true) {
                    pathway.add(current)
                    current = visits[current]!!

                    if (current == cycleVert) {
                        user(pathway)
                        break
                    }
                }
            }

            visits.remove(actualVert)
        }

        levelScan(
            rxRemaining = targetLength - 1,
            actualVert = vert,
            actualVertSource = vert,
            cycleVert = vert,
            cycles::add
        )

        return cycles
    }
}

fun <Vertex> MeshBuilder<Vertex, Quads>.triangulate(): MeshBuilder<Vertex, Triangles> {
    val result = this.reparam<Triangles>()

    val triangulated = HashSet<IndexedQuad>()

    vertices.indices.forEach { vertIdxMesh ->
        cycleScan(vertIdxMesh, 4).forEach {
            val splitTargetMesh = it[1]

            it.add(vertIdxMesh)
            it.sort()

            val quad = IndexedQuad(it[0], it[1], it[2], it[3])

            if (triangulated.add(quad)) {
                result.addEdge(vertIdxMesh, splitTargetMesh)
            }
        }
    }

    return result
}

fun <Vertex> MeshBuilder<Vertex, Quads>.quadScan(): ArrayList<IndexedQuad> {
    val visited = HashSet<IndexedQuad>()
    val result = ArrayList(visited)

    this.vertices.indices.forEach { vertIdx ->
        this.cycleScan(vertIdx, 4).forEach { loop ->
            val sorted = arrayListOf(loop[0], loop[1], loop[2], vertIdx).apply { sort() }

            if (visited.add(IndexedQuad(sorted[0], sorted[1], sorted[2], sorted[3]))) {
                result.add(IndexedQuad(vertIdx, loop[0], loop[1], loop[2]))
            }
        }
    }

    return result
}

fun <Vertex> MeshBuilder<Vertex, Triangles>.triScan(): ArrayList<IndexedTri> {
    val visited = HashSet<IndexedTri>()
    val result = ArrayList(visited)

    this.vertices.indices.forEach { vertIdx ->
        this.cycleScan(vertIdx, 3).forEach { loop ->
            val sorted = arrayListOf(loop[0], loop[1], vertIdx).apply { sort() }

            if (visited.add(IndexedTri(sorted[0], sorted[1], sorted[2]))) {
                result.add(IndexedTri(vertIdx, loop[0], loop[1]))
            }
        }
    }

    return result
}

data class Sketch(val vertices: List<Vector2d>)

fun sketchCircle(vertices: Int, r: Double = 1.0): Sketch {
    require(vertices >= 3)

    val results = ArrayList<Vector2d>()

    repeat(vertices) { vertIdx ->
        val angle = vertIdx
            .toDouble()
            .mappedTo(
                srcMin = 0.0,
                srcMax = vertices.toDouble(),
                dstMin = -PI,
                dstMax = PI
            )

        results.add(Rotation2d.exp(angle).direction * r)
    }

    return Sketch(results)
}

fun extrudeSketch(sketch: Sketch, spline: Spline3d, samples: List<Double>): MeshBuilder<Vector3dParametric, Quads> {
    val builder = MeshBuilder<Vector3dParametric, Quads>()

    // Compute transport (RMF):
    val initialConfigSpline = spline.evaluatePoseFrenet(samples.first()).rotation
    val rxTransportIncr = (spline.evaluatePoseFrenet(samples.last()).rotation / initialConfigSpline)

    fun extrude(prevMapSketchModel: HashMap<Int, Int>?, t: Double): HashMap<Int, Int> {
        val mapSketchModel = HashMap<Int, Int>()

        val txSketchModel = Pose3d(
            spline.evaluate(t),
            initialConfigSpline * rxTransportIncr.scaled(
                t.mappedTo(
                    samples.first(),
                    samples.last(),
                    snzE(0.0),
                    1.0
                )
            )
        )

        sketch.vertices.forEachIndexed { vertIdxSketch, (xSketch, ySketch) ->
            mapSketchModel[vertIdxSketch] = builder.addVertex(
                Vector3dParametric(
                    value = txSketchModel * Vector3d(0.0, xSketch, ySketch),
                    param = t
                )
            )

            // Build edges between consecutive rings:
            if (prevMapSketchModel != null) {
                builder.addEdge(
                    mapSketchModel[vertIdxSketch]!!,
                    prevMapSketchModel[vertIdxSketch]!!
                )
            }
        }

        // Build ring edges:
        sketch.vertices.indices.forEach { vertIdxSketch ->
            builder.addEdge(
                mapSketchModel[vertIdxSketch]!!,
                if (vertIdxSketch == sketch.vertices.size - 1) mapSketchModel[0]!! // Loop around to first to complete ring
                else mapSketchModel[vertIdxSketch + 1]!!
            )
        }

        return mapSketchModel
    }

    var previous: HashMap<Int, Int>? = null
    samples.forEach {
        previous = extrude(previous, it)
    }

    return builder
}

data class VertPositionColorNormalUv(
    val position: Vector3d,
    val color: Color,
    val normal: Vector3d,
    val uv: Vector2d,
)

class ListVertexList(val vertices: List<VertPositionColorNormalUv>) : VertexList {
    override fun getX(index: Int) = vertices[index].position.x.toFloat()
    override fun getY(index: Int) = vertices[index].position.y.toFloat()
    override fun getZ(index: Int) = vertices[index].position.z.toFloat()

    override fun getR(index: Int) = Byte.MAX_VALUE
    override fun getG(index: Int) = Byte.MAX_VALUE
    override fun getB(index: Int) = Byte.MAX_VALUE
    override fun getA(index: Int) = Byte.MAX_VALUE

    override fun getU(index: Int) = vertices[index].uv.x.toFloat()
    override fun getV(index: Int) = vertices[index].uv.y.toFloat()

    override fun getLight(index: Int) = Int.MAX_VALUE

    override fun getNX(index: Int) = vertices[index].normal.x.toFloat()
    override fun getNY(index: Int) = vertices[index].normal.y.toFloat()
    override fun getNZ(index: Int) = vertices[index].normal.z.toFloat()

    override fun getVertexCount() = vertices.size
}
