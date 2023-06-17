package org.eln2.mc.data

import org.eln2.mc.mathematics.Vector3di
import org.eln2.mc.mathematics.exp2i
import org.eln2.mc.mathematics.pow
import org.eln2.mc.sim.Grid3dFluid
import kotlin.math.floor

data class SubGridKey(val x: Int, val y: Int, val z: Int) {
    fun toTile(size: Int) = Vector3di(x * size, y * size, z * size)

    companion object {
        fun fromTile(tileX: Int, tileY: Int, tileZ: Int, size: Int): SubGridKey {
            return SubGridKey(
                mapAxis(tileX, size),
                mapAxis(tileY, size),
                mapAxis(tileZ, size)
            )
        }

        private fun mapAxis(tileCoordinate: Int, size: Int) = floor(tileCoordinate / size.toDouble()).toInt()
    }
}

abstract class SubGrid3d(val key: SubGridKey, val log: Int) {
    val edgeSize: Int = exp2i(log)
    val cellCount: Int = edgeSize.pow(3)

    val min = key.toTile(edgeSize)
    val max = min + Vector3di(edgeSize)

    val windowRange get() = 0 until cellCount
    val edgeRange get() = 0 until edgeSize
    val xRange get() = min.x until max.x
    val yRange get() = min.y until max.y
    val zRange get() = min.z until max.z

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SubGrid3d

        if (key != other.key) return false
        if (log != other.log) return false

        return true
    }

    override fun hashCode(): Int {
        var result = key.hashCode()
        result = 31 * result + log
        return result
    }
}

data class SparseGrid3d<G : SubGrid3d>(val log: Int, val factory: (SubGridKey) -> G) {
    val edgeSize: Int = exp2i(log)
    val subGrids = HashMap<SubGridKey, G>()

    fun createCursor() = GridCursor3d(edgeSize)

    fun getGrid(tileX: Int, tileY: Int, tileZ: Int) = getGrid(createTileKey(tileX, tileY, tileZ))
    fun getGrid(tile: Vector3di) = getGrid(tile.x, tile.y, tile.z)

    fun getGrid(k: SubGridKey): G  = subGrids.getOrPut(k) { factory(k)}

    fun createTileKey(tileX: Int, tileY: Int, tileZ: Int) = SubGridKey.fromTile(tileX, tileY, tileZ, edgeSize)
    fun createTileKey(tile: Vector3di) = createTileKey(tile.x, tile.y, tile.z)
}

class TileGrid3d<T>(key: SubGridKey, log: Int, val storage: Array<T>) : SubGrid3d(key, log) {
    init {
        require(storage.size >= cellCount)
    }

    fun reduce(world: Int) = reduceLocal(world, edgeSize, log)

    fun reduce(tileX: Int, tileY: Int, tileZ: Int): Int {
        val xGrid = reduce(tileX)
        val yGrid = reduce(tileY)
        val zGrid = reduce(tileZ)
        return xGrid + edgeSize * (yGrid + edgeSize * zGrid)
    }

    operator fun not() = storage
    operator fun get(i: Int) = storage[i]
    operator fun set(i: Int, v: T) { storage[i] = v }
    operator fun get(tileX: Int, tileY: Int, tileZ: Int) = get(reduce(tileX, tileY, tileZ))
    operator fun get(tile: Vector3di) = get(tile.x, tile.y, tile.z)
    operator fun set(tileX: Int, tileY: Int, tileZ: Int, v: T) = set(reduce(tileX, tileY, tileZ), v)
    operator fun set(tile: Vector3di, v: T) = set(tile.x, tile.y, tile.z, v)

    companion object {
        inline fun<reified T> create(key: SubGridKey, log: Int, initial: T) = TileGrid3d(
            key,
            log,
            Array(exp2i(log).pow(3)) { initial }
        )
    }
}

operator fun<T, G : TileGrid3d<T>> SparseGrid3d<G>.get(tileX: Int, tileY: Int, tileZ: Int): T =
    this.getGrid(tileX, tileY, tileZ)[tileX, tileY, tileZ]

operator fun<T, G : TileGrid3d<T>> SparseGrid3d<G>.get(tile: Vector3di): T =
    this.getGrid(tile)[tile]

operator fun<T, G : TileGrid3d<T>> SparseGrid3d<G>.set(tileX: Int, tileY: Int, tileZ: Int, v: T) {
    this.getGrid(tileX, tileY, tileZ)[tileX, tileY, tileZ] = v
}

operator fun<T, G : TileGrid3d<T>> SparseGrid3d<G>.set(tile: Vector3di, v: T) {
    this.getGrid(tile)[tile] = v
}

data class GridCursor3d(val edgeSize: Int) {
    var x = 0
    var y = 0
    var z = 0
    var tileX = 0
    var tileY = 0
    var tileZ = 0

    fun loadg(grid: Grid3dFluid) {
        tileX = grid.tileX
        tileY = grid.tileY
        tileZ = grid.tileZ
    }

    fun loadi(i: Int) {
        x = i % edgeSize + tileX
        y = (i / edgeSize) % edgeSize + tileY
        z = i / edgeSize / edgeSize + tileZ
    }

    val vector get() = Vector3di(x, y, z)
}

fun reduceLocal(world: Int, size: Int, log: Int): Int {
    var v = world - size * (world shr log)

    if (v < 0) {
        v = -v
    }

    return v
}
