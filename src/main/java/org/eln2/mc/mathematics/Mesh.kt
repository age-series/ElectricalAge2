package org.eln2.mc.mathematics

import org.ageseries.libage.data.MutableSetMapMultiMap
import java.io.File
import java.io.FileWriter
import kotlin.math.PI

class Obj {
    val file = FileWriter(File("test.obj"), false)

    private fun getValue(d: Double) = d.toBigDecimal().toPlainString()

    private fun getValue(v: Vector3d) = "${getValue(v.x)} ${getValue(v.y)} ${getValue(v.z)}"

    fun vert(v: Vector3d) = file.appendLine("v ${getValue(v)}")
    fun vert(x: Double, y: Double, z: Double) = vert(Vector3d(x, y, z))
    fun vert(v: List<Vector3d>) = file.appendLine("v ${v.joinToString(" ")}")
    fun indices(a: Int, b: Int, c: Int) = file.appendLine("f ${a + 1} ${b + 1} ${c + 1}")
    fun indices(a: Int, b: Int, c: Int, d: Int) = file.appendLine("f ${a + 1} ${b + 1} ${c + 1} ${d + 1}")

    fun close() {
        file.close()
    }
}

class IndexedMesh {
    val vertices = ArrayList<Vector3d>()
    val indices = ArrayList<Int>()

    // precondition: triangulated
    fun save() {
        Obj().apply {
            vertices.forEach { vert(it) }
            indices.chunked(3).forEach {
                indices(it[0], it[1], it[2])
            }
        }.close()
    }
}

data class VtxTri(val a: Int, val b: Int, val c: Int)
data class VtxQuad(val a: Int, val b: Int, val c: Int, val d: Int)

class MeshBuilder {
    val vertices = ArrayList<Vector3d>()
    val connections = MutableSetMapMultiMap<Int, Int>()

    fun addVertex(v: Vector3d): Int {
        val idx = vertices.size
        vertices.add(v)
        return idx
    }

    fun connect(a: Int, b: Int) {
        connections[a] = b
        connections[b] = a
    }

    fun triangulate() {
        val generated = HashSet<VtxQuad>()

        vertices.indices.forEach { vertIdxMesh ->
            cycleScan(vertIdxMesh, 4).forEach {
                val splitTarget = it[1]

                it.add(vertIdxMesh)
                it.sort()

                val quad = VtxQuad(it[0], it[1], it[2], it[3])

                if(generated.add(quad)) {
                    connect(vertIdxMesh, splitTarget)
                }
            }
        }
    }

    fun indexedMeshScan(): IndexedMesh {
        val mesh = IndexedMesh()

        mesh.vertices.addAll(vertices)

        val generated = HashSet<VtxTri>()

        mesh.vertices.indices.forEach { vertIdxMesh ->
            cycleScan(vertIdxMesh, 3).forEach {
                it.add(vertIdxMesh)
                it.sort()

                val tri = VtxTri(it[0], it[1], it[2])

                if(generated.add(tri)) {
                    mesh.indices.addAll(it)
                }
            }
        }

        return mesh
    }

    fun cycleScan(vert: Int, targetLength: Int): ArrayList<ArrayList<Int>> {
        val visits = HashMap<Int, Int>()
        val cycles = ArrayList<ArrayList<Int>>()

        fun levelScan(rxRemaining: Int, actualVert: Int, actualVertSource: Int, cycleVert: Int, user: (ArrayList<Int>) -> Unit) {
            visits[actualVert] = actualVertSource

            if (rxRemaining > 0) {
                connections[actualVert].forEach { actualVertConn ->
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

            if(connections[actualVert].contains(cycleVert)) {
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

fun printCircle(vertices: Int, r: Double = 1.0): ArrayList<Vector2d> {
    require(vertices >= 2)

    val results = ArrayList<Vector2d>()

    repeat(vertices) {
        val angle = it.toDouble().mappedTo(0.0, vertices.toDouble(), -PI, PI)
        results.add(Rotation2d.exp(angle).direction * r)
    }

    return results
}

fun main() {
    val path = PathBuilder3d().apply {
        add(
            0.0,
            Vector3dDual.of(
                Vector3d(0.0, 0.0, 0.0),
                Vector3d(0.0, 0.0, 5.0),
                Vector3d(1.0, 0.0, 0.0)
            )
        )
        add(
            1.0,
            Vector3dDual.of(
                Vector3d(3.0, 0.0, 10.0),
                Vector3d(5.0, 0.0, 0.0),
                Vector3d(0.0, 0.0, -1.0)
            )
        )
    }.buildQuintic()

    val meshB = MeshBuilder()

    val params = ArrayList<Double>()

    path.adaptscan(
        rxT = params,
        0.0,
        1.0,
        0.1,
        condition = diffCondition3d(
            distMax = 1.0,
            rotIncrMax = PI / 32.0
        )
    )

    val circlePrint = printCircle(
        vertices = 8,
        r = 0.25
    )

    val txPathMdl = Matrix3x3(
        Vector3d.unitX,
        Vector3d.unitZ,
        -Vector3d.unitY
    )

    fun extrude(previousLayer: HashMap<Int, Int>?, t: Double): HashMap<Int, Int> {
        val mapPrintMdl = HashMap<Int, Int>()

        val txPrintPath = path.evaluatePoseFrenet(t)

        circlePrint.forEachIndexed { vertIdxPrint, (xPrint, yPrint) ->
            val vertIdxMdl = meshB.addVertex(
                txPathMdl * (txPrintPath * Vector3d(0.0, xPrint, yPrint))
            )

            mapPrintMdl[vertIdxPrint] = vertIdxMdl

            if(previousLayer != null) {
                meshB.connect(
                    vertIdxMdl,
                    previousLayer[vertIdxPrint]!!
                )
            }
        }

        circlePrint.indices.forEach { vertIdxPrint ->
            meshB.connect(
                mapPrintMdl[vertIdxPrint]!!,
                if(vertIdxPrint == circlePrint.size - 1) mapPrintMdl[0]!!
                else mapPrintMdl[vertIdxPrint+1]!!
            )
        }

        return mapPrintMdl
    }

    var level: HashMap<Int, Int>? = null
    params.forEach {
        level = extrude(level, it)
    }

    meshB.triangulate()

    val mesh = meshB.indexedMeshScan()

    mesh.save()
}
