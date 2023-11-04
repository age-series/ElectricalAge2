package org.eln2.mc.client.render

import it.unimi.dsi.fastutil.doubles.Double2ObjectOpenHashMap
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap
import it.unimi.dsi.fastutil.ints.IntArrayList
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

    fun cycleScan(vert: Int, targetLength: Int): ArrayList<IntArrayList> {
        val visits = Int2IntOpenHashMap()
        val cycles = ArrayList<IntArrayList>()

        fun levelScan(
            remaining: Int,
            actualVert: Int,
            actualVertSource: Int,
            cycleVert: Int,
            user: (IntArrayList) -> Unit,
        ) {
            visits.put(actualVert, actualVertSource)

            if (remaining > 0) {
                for (actualVertConn in edges[actualVert]) {
                    if (!visits.containsKey(actualVertConn)) {
                        levelScan(
                            remaining = remaining - 1,
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
                val pathway = IntArrayList(targetLength)

                var current = actualVert

                while (true) {
                    pathway.add(current)
                    current = visits.get(current)

                    if (current == cycleVert) {
                        user(pathway)
                        break
                    }
                }
            }

            visits.remove(actualVert)
        }

        levelScan(
            remaining = targetLength - 1,
            actualVert = vert,
            actualVertSource = vert,
            cycleVert = vert,
            cycles::add
        )

        return cycles
    }
}

fun <Vertex> MeshBuilder<Vertex, Quads>.quadScan(consumer: (IndexedQuad) -> Unit) {
    val visited = HashSet<IndexedQuad>()
    val buffer = IntArray(4)

    for (vertIdx in this.vertices.indices) {
        this.cycleScan(vertIdx, 4).forEach { loop ->
            val l0 = loop.getInt(0)
            val l1 = loop.getInt(1)
            val l2 = loop.getInt(2)

            buffer[0] = l0
            buffer[1] = l1
            buffer[2] = l2
            buffer[3] = vertIdx

            buffer.sort()

            if (visited.add(IndexedQuad(buffer[0], buffer[1], buffer[2], buffer[3]))) {
                consumer(IndexedQuad(vertIdx, l0, l1, l2))
            }
        }
    }
}

data class Sketch(val vertices: List<Vector2d>)

fun sketchCircle(vertices: Int, radius: Double = 1.0): Sketch {
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

        results.add(Rotation2d.exp(angle).direction * radius)
    }

    return Sketch(results)
}

fun extrudeSketchFrenet(sketch: Sketch, spline: Spline3d, samples: List<Double>): SketchExtrusion {
    val f0 = spline.frenetPose(samples.first())
    val f1 = spline.frenetPose(samples.last())
    return extrudeSketch(sketch, spline, samples, f0, f1)
}
// RMF assumes no "twists and turns", fix in the future?
fun extrudeSketch(sketch: Sketch, spline: Spline3d, samples: List<Double>, f0: Pose3d, f1: Pose3d): SketchExtrusion {
    val builder = MeshBuilder<Vector3dParametric, Quads>()

    val y0 = samples.first()
    val y1 = samples.last()

    val increment = f1.rotation / f0.rotation

    val rmf = ArrayList<Pose3dParametric>(samples.size)
    val rmfLookup = Double2ObjectOpenHashMap<Pose3d>(samples.size)

    fun extrude(previousMapSketchModel: Int2IntOpenHashMap?, t: Double): Int2IntOpenHashMap {
        val mapSketchModel = Int2IntOpenHashMap()

        val pose = Pose3d(
            spline.evaluate(t),
            f0.rotation * increment(
                map(t, y0, y1, 0.0, 1.0)
            )
        )

        rmf.add(Pose3dParametric(pose, t))
        rmfLookup.put(t, pose)

        sketch.vertices.forEachIndexed { vertexIdSketch, (xSketch, ySketch) ->
            mapSketchModel.put(
                vertexIdSketch,
                builder.addVertex(
                    Vector3dParametric(
                        value = pose * Vector3d(0.0, xSketch, ySketch),
                        t = t
                    )
                )
            )

            // Build edges between consecutive rings:
            if (previousMapSketchModel != null) {
                builder.addEdge(
                    mapSketchModel.get(vertexIdSketch),
                    previousMapSketchModel.get(vertexIdSketch)
                )
            }
        }

        // Build ring edges:
        sketch.vertices.indices.forEach { vertIdxSketch ->
            builder.addEdge(
                mapSketchModel.get(vertIdxSketch),
                if (vertIdxSketch == sketch.vertices.size - 1) mapSketchModel.get(0) // Loop around to first to complete ring
                else mapSketchModel.get(vertIdxSketch + 1)
            )
        }

        return mapSketchModel
    }

    var previous: Int2IntOpenHashMap? = null
    samples.forEach {
        previous = extrude(previous, it)
    }

    return SketchExtrusion(builder, f0, f1, rmf, rmfLookup)
}

data class SketchExtrusion(
    val mesh: MeshBuilder<Vector3dParametric, Quads>,
    val f0: Pose3d,
    val f1: Pose3d,
    val rmfProgression: ArrayList<Pose3dParametric>,
    val rmfLookup: Double2ObjectOpenHashMap<Pose3d>
)
