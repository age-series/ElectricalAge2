package org.eln2.mc.mathematics

import org.ageseries.libage.data.MutableSetMapMultiMap
import java.io.File
import java.io.FileWriter
import kotlin.math.PI

class ObjWriter {
    val file = FileWriter(File("test.obj"), false)

    private fun getValue(d: Double) = d.toBigDecimal().toPlainString()
    private fun getValue(v: Vector3d) = "${getValue(v.x)} ${getValue(v.y)} ${getValue(v.z)}"

    fun vert(v: Vector3d) = file.appendLine("v ${getValue(v)}")
    fun vert(x: Double, y: Double, z: Double) = vert(Vector3d(x, y, z))
    fun vert(v: List<Vector3d>) = file.appendLine("v ${v.joinToString(" ")}")
    fun indices(indices: List<Int>) = file.appendLine("f ${indices.map{ it + 1}.joinToString(" ")}")

    fun close() {
        file.close()
    }
}

data class IndexedMesh(val vertices: List<Vector3d>, val indices: List<Int>) {
    fun saveTest(size: Int = 3) {
        ObjWriter().apply {
            vertices.forEach { vert(it) }
            indices.chunked(size).forEach {
                indices(it)
            }
        }.close()
    }
}

data class IndexedTri(val a: Int, val b: Int, val c: Int)
data class IndexedQuad(val a: Int, val b: Int, val c: Int, val d: Int)

class MeshBuilder {
    val vertices = ArrayList<Vector3d>()
    val edges = MutableSetMapMultiMap<Int, Int>()

    fun addVertex(v: Vector3d): Int {
        val idx = vertices.size
        vertices.add(v)
        return idx
    }

    fun addEdge(a: Int, b: Int) {
        edges[a] = b
        edges[b] = a
    }

    fun triangulate() {
        val generated = HashSet<IndexedQuad>()

        vertices.indices.forEach { vertIdxMesh ->
            cycleScan(vertIdxMesh, 4).forEach {
                val splitTarget = it[1]

                it.add(vertIdxMesh)
                it.sort()

                val quad = IndexedQuad(it[0], it[1], it[2], it[3])

                if(generated.add(quad)) {
                    addEdge(vertIdxMesh, splitTarget)
                }
            }
        }
    }

    fun build(): IndexedMesh {
        val indices = ArrayList<Int>()

        val generated = HashSet<IndexedTri>()

        vertices.indices.forEach { vertIdxMesh ->
            cycleScan(vertIdxMesh, 3).forEach {
                it.add(vertIdxMesh)
                it.sort()

                val tri = IndexedTri(it[0], it[1], it[2])

                if(generated.add(tri)) {
                    indices.addAll(it)
                }
            }
        }

        return IndexedMesh(
            vertices.toList(),
            indices
        )
    }

    fun cycleScan(vert: Int, targetLength: Int): ArrayList<ArrayList<Int>> {
        val visits = HashMap<Int, Int>()
        val cycles = ArrayList<ArrayList<Int>>()

        fun levelScan(rxRemaining: Int, actualVert: Int, actualVertSource: Int, cycleVert: Int, user: (ArrayList<Int>) -> Unit) {
            visits[actualVert] = actualVertSource

            if (rxRemaining > 0) {
                edges[actualVert].forEach { actualVertConn ->
                    if(!visits.contains(actualVertConn)) {
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

            if(edges[actualVert].contains(cycleVert)) {
                val pathway = ArrayList<Int>()

                var current = actualVert

                while (true) {
                    pathway.add(current)
                    current = visits[current]!!

                    if(current == cycleVert) {
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

fun extrudeSketch(sketch: Sketch, spline: Spline3d, samples: List<Double>): MeshBuilder {
    val builder = MeshBuilder()

    fun extrude(isFirst: Boolean, t: Double) {
        val mapSketchModel = HashMap<Int, Int>()

        val txSketchTarget = spline.evaluatePoseFrenet(t)

        sketch.vertices.forEachIndexed { vertIdxSketch, (xSketch, ySketch) ->
            val vertIdxModel = builder.addVertex(
                txSketchTarget * Vector3d(0.0, xSketch, ySketch)
            )

            mapSketchModel[vertIdxSketch] = vertIdxModel

            if(!isFirst) {
                builder.addEdge(vertIdxModel, vertIdxModel - sketch.vertices.size)
            }
        }

        sketch.vertices.indices.forEach { vertIdxSketch ->
            builder.addEdge(
                mapSketchModel[vertIdxSketch]!!,
                if(vertIdxSketch == sketch.vertices.size - 1) mapSketchModel[0]!!
                else mapSketchModel[vertIdxSketch+1]!!
            )
        }
    }

    samples.forEachIndexed { i, t -> extrude(i == 0, t) }

    return builder
}
