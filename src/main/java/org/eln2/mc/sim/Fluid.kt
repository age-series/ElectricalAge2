package org.eln2.mc.sim

import org.eln2.mc.mathematics.pow2i
import org.eln2.mc.mathematics.powi
import org.eln2.mc.mathematics.pow
import kotlin.math.*
import kotlin.system.measureNanoTime


fun main() {
    val grid = ClusterGrid3dSparseFluid(3)

    val simulator = DRAGONS(
        grid,
        DRAGONSFluidOptions()
    )

    grid.addDensityIncrTile(0, 0, 0, 1000f)

    grid.setObstacleTile(1, 0, 0, true)
    grid.setObstacleTile(-1, 0, 0, true)
    grid.setObstacleTile(0, 1, 0, true)
    grid.setObstacleTile(0, -1, 0, true)
    grid.setObstacleTile(0, 0, 1, true)
    grid.setObstacleTile(0, 0, -1, true)


    var i = 0

    while (true) {
        val nano = measureNanoTime {
            simulator.step()
        }.toDouble()

        println("Cell nanos: ${round(nano / simulator.activeFluidCells.toDouble())}, active ${simulator.activeFluidCells}, i: ${i++}")
        println("Active grids: ${grid.activeGrids.size}")
        println("Active clusters: ${simulator.activeClusters}")
    }
}

private fun reduceLocal(world: Int, size: Int, log: Int): Int {
    var v = world - size * (world shr log)

    if (v < 0) {
        v = -v
    }

    return v
}

class Grid3df(val storage: FloatArray, val log: Int) {
    constructor(log: Int) : this(FloatArray(powi(pow2i(log), 3)), log)

    val edgeSize = pow2i(log)

    init { require(log >= 1) }

    fun rxGrid(world: Int) = reduceLocal(world, edgeSize, log)

    fun ixGrid(xWorld: Int, yWorld: Int, zWorld: Int): Int {
        val xGrid = rxGrid(xWorld)
        val yGrid = rxGrid(yWorld)
        val zGrid = rxGrid(zWorld)

        return xGrid + edgeSize * (yGrid + edgeSize * zGrid)
    }

    operator fun not() = storage
    operator fun get(i: Int) = storage[i]
    operator fun set(i: Int, v: Float) { storage[i] = v }
    operator fun get(xWorld: Int, yWorld: Int, zWorld: Int) = get(ixGrid(xWorld, yWorld, zWorld))
    operator fun set(xWorld: Int, yWorld: Int, zWorld: Int, v: Float) = set(ixGrid(xWorld, yWorld, zWorld), v)
}

class Grid3db(val storage: BooleanArray, val log: Int) {
    constructor(log: Int) : this(BooleanArray(powi(pow2i(log), 3)), log)

    val edgeSize = pow2i(log)

    init { require(log >= 1) }

    fun rxGrid(world: Int) = reduceLocal(world, edgeSize, log)

    fun ixGrid(xWorld: Int, yWorld: Int, zWorld: Int): Int {
        val xGrid = rxGrid(xWorld)
        val yGrid = rxGrid(yWorld)
        val zGrid = rxGrid(zWorld)

        return xGrid + edgeSize * (yGrid + edgeSize * zGrid)
    }

    operator fun not() = storage
    operator fun get(i: Int) = storage[i]
    operator fun set(i: Int, v: Boolean) { storage[i] = v }
    operator fun get(xWorld: Int, yWorld: Int, zWorld: Int) = get(ixGrid(xWorld, yWorld, zWorld))
    operator fun set(xWorld: Int, yWorld: Int, zWorld: Int, v: Boolean) = set(ixGrid(xWorld, yWorld, zWorld), v)
}

data class SubGridKey(val x: Int, val y: Int, val z: Int) {
    fun toTile(size: Int) = SubGridKey(x * size, y * size, z * size)

    companion object {
        fun fromTile(tileX: Int, tileY: Int, tileZ: Int, size: Int): SubGridKey {
            return SubGridKey(
                rxAxis(tileX, size),
                rxAxis(tileY, size),
                rxAxis(tileZ, size)
            )
        }

        private fun rxAxis(tileCoordinate: Int, size: Int) = floor(tileCoordinate / size.toDouble()).toInt()
    }
}

data class CellCursor(val edgeSize: Int) {
    var x = 0
    var y = 0
    var z = 0
    var tileX = 0
    var tileY = 0
    var tileZ = 0

    fun loadGrid(grid: Grid3dFluid) {
        tileX = grid.tileX
        tileY = grid.tileY
        tileZ = grid.tileZ
    }

    fun loadIndex(i: Int) {
        x = i % edgeSize + tileX
        y = (i / edgeSize) % edgeSize + tileY
        z = i / edgeSize / edgeSize + tileZ
    }
}

class Grid3dFluid(
    val key: SubGridKey,
    val wallGrid: Grid3db,
    val densities: Grid3df,
    val newDensities: Grid3df,
    val velocitiesX: Grid3df,
    val velocitiesY: Grid3df,
    val velocitiesZ: Grid3df,
    val newVelocitiesX: Grid3df,
    val newVelocitiesY: Grid3df,
    val newVelocitiesZ: Grid3df,
) {
    constructor(key: SubGridKey, log: Int) : this(
        key,
        Grid3db(log),
        Grid3df(log),
        Grid3df(log),
        Grid3df(log),
        Grid3df(log),
        Grid3df(log),
        Grid3df(log),
        Grid3df(log),
        Grid3df(log)
    )

    val edgeSize: Int
    val cellCount: Int

    val tileX: Int
    val tileY: Int
    val tileZ: Int
    val tileRight: Int
    val tileTop: Int
    val tileFront: Int

    var rxAmount = 0f
    var absoluteFlow = 0f
    var total = 0f

    fun ixGrid(xWorld: Int, yWorld: Int, zWorld: Int) = wallGrid.ixGrid(xWorld, yWorld, zWorld)

    init {
        edgeSize = wallGrid.edgeSize
        cellCount = wallGrid.edgeSize.pow(3)

        require(
            listOf(
                wallGrid.edgeSize,
                densities.edgeSize,
                newDensities.edgeSize,
                velocitiesX.edgeSize,
                velocitiesY.edgeSize,
                newVelocitiesX.edgeSize,
                newVelocitiesY.edgeSize
            ).all { it == edgeSize }
        )

        val t = key.toTile(edgeSize)

        tileX = t.x
        tileY = t.y
        tileZ = t.z
        tileRight = tileX + edgeSize
        tileTop = tileY + edgeSize
        tileFront = tileZ + edgeSize
    }

    var left: Grid3dFluid? = null
    var right: Grid3dFluid? = null
    var front: Grid3dFluid? = null
    var back: Grid3dFluid? = null
    var up: Grid3dFluid? = null
    var down: Grid3dFluid? = null

    fun neighborScan(target: MutableList<Grid3dFluid>) = target.apply {
        val left = left
        val right = right
        val front = front
        val back = back
        val up = up
        val down = down

        if(left != null) add(left)
        if(right != null) add(right)
        if(front != null) add(front)
        if(back != null) add(back)
        if(up != null) add(up)
        if(down != null) add(down)
    }
}

class ClusterGrid3dSparseFluid(val log: Int) {
    val edgeSize = pow2i(log)
    val gridCellCount = edgeSize.pow(3)

    fun createCursor() = CellCursor(edgeSize)

    val windowRange = 0 until gridCellCount

    private val activateSet = HashSet<Grid3dFluid>()
    private val inactivateSet = HashSet<Grid3dFluid>()
    private val deleteSet = HashSet<Grid3dFluid>()

    val activeGrids = HashSet<Grid3dFluid>()
    val clusters = ArrayList<ArrayList<Grid3dFluid>>()
    val subGrids = HashMap<SubGridKey, Grid3dFluid>()

    fun scanClusters() {
        clusters.clear()

        val traversed = HashSet<SubGridKey>()

        activeGrids.forEach { activeGrid ->
            if(traversed.contains(activeGrid.key)) {
                return@forEach
            }

            val cluster = ArrayList<Grid3dFluid>()
            walkGraph(activeGrid, cluster, traversed)
            clusters.add(cluster)
        }
    }

    private fun walkGraph(subGrid: Grid3dFluid, output: ArrayList<Grid3dFluid>, traversed: HashSet<SubGridKey>) {
        val queue = ArrayDeque<Grid3dFluid>().apply { add(subGrid) }

        while (queue.isNotEmpty()) {
            val front = queue.removeFirst()

            if(!traversed.add(front.key)) {
                continue
            }

            output.add(front)

            front.neighborScan(queue)
        }
    }

    fun deferActivation(grid: Grid3dFluid) = activateSet.add(grid)
    fun deferInactivation(grid: Grid3dFluid) = inactivateSet.add(grid)
    fun deferDeletion(grid: Grid3dFluid) = deleteSet.add(grid)

    fun applyActivation() {
        activeGrids.addAll(activateSet)
        activateSet.clear()
    }

    fun applyInactivation() {
        activeGrids.removeAll(inactivateSet)
        inactivateSet.clear()
    }

    fun applyDeletion() {
        activeGrids.removeAll(deleteSet)
        deleteSet.forEach { subGrids.remove(it.key) }
        deleteSet.clear()
    }

    fun getOrCreateTile(tileX: Int, tileY: Int, tileZ: Int) = getOrCreateGrid(SubGridKey.fromTile(tileX, tileY, tileZ, edgeSize))
    fun getOrCreateGrid(key: SubGridKey) = subGrids.getOrPut(key) { Grid3dFluid(key, log) }

    fun readDensity(tileX: Int, tileY: Int, tileZ: Int) = getOrCreateTile(tileX, tileY, tileZ).densities[tileX, tileY, tileZ]

    fun addDensityIncrTile(tileX: Int, tileY: Int, tileZ: Int, densityIncr: Float, activate: Boolean = true) {
        val grid = getOrCreateTile(tileX, tileY, tileZ)

        grid.densities[tileX, tileY, tileZ] += densityIncr
        grid.newDensities[tileX, tileY, tileZ] += densityIncr

        if(activate) {
            activeGrids.add(grid)
        }
    }

    fun setObstacleTile(tileX: Int, tileY: Int, tileZ: Int, value: Boolean, activate: Boolean = true) {
        val grid = getOrCreateTile(tileX, tileY, tileZ)

        grid.wallGrid[tileX, tileY, tileZ] = value

        if(activate) {
            activeGrids.add(grid)
        }
    }
}

data class DRAGONSFluidOptions(
    val pressureThreshold: Float = 0f,
    val pressureCoefficient: Float = 60f,
    val dampening: Float = 0.01f,
    val diffusionRadius: Float = 0.7f,
    val diffusionThreshold: Float = 0f,
    val diffusionRate: Float = 0.01f,
    val densityThreshold: Float = 10e-10f,
    val activateThreshold: Float = 10e-10f,
    val velocityRange: Float = 30f,
    val gridDeleteThreshold: Float = 0.01f,
    val clusterSleepThreshold: Float = 0.00f
)

/**
 * Diffusion-Reaction-Advection Grid for Optimized Non-physical Simulations
 * */
class DRAGONS(val sparseGrid: ClusterGrid3dSparseFluid, val options: DRAGONSFluidOptions) {
    var totalDensity = 0f
        private set

    var activeClusters = 0
        private set

    var activeFluidCells = 0
        private set

    private fun rxTargetGrid(x: Int, y: Int, z: Int, current: Grid3dFluid): Grid3dFluid {
        // can be made branch-less using an indexing system
        return if (x == current.tileX - 1) current.left!!
        else if (x == current.tileRight) current.right!!
        else if (y == current.tileY - 1) current.down!!
        else if (y == current.tileTop) current.up!!
        else if(z == current.tileZ - 1) current.back!!
        else if(z == current.tileFront) current.front!!
        else current
    }

    private fun loadSimulationGraphs() {
        sparseGrid.activeGrids.forEach { grid ->
            val x = grid.key.x
            val y = grid.key.y
            val z = grid.key.z
            grid.left = sparseGrid.getOrCreateGrid(SubGridKey(x - 1, y, z))
            grid.right = sparseGrid.getOrCreateGrid(SubGridKey(x + 1, y, z))
            grid.up = sparseGrid.getOrCreateGrid(SubGridKey(x, y + 1, z))
            grid.down = sparseGrid.getOrCreateGrid(SubGridKey(x, y - 1, z))
            grid.back = sparseGrid.getOrCreateGrid(SubGridKey(x, y, z - 1))
            grid.front = sparseGrid.getOrCreateGrid(SubGridKey(x, y, z + 1))
        }
    }

    private fun velocityPass(dt: Float) {
        val cursor = sparseGrid.createCursor()

        sparseGrid.activeGrids.forEach { grid ->
            cursor.loadGrid(grid)

            val densityEps = options.densityThreshold
            val velocityRange = options.velocityRange
            val pressureThreshold = options.pressureThreshold
            val pressureCoefficient = options.pressureCoefficient

            for (i in sparseGrid.windowRange) {
                cursor.loadIndex(i)

                val x = cursor.x
                val y = cursor.y
                val z = cursor.z

                val actualIndex = grid.ixGrid(x, y, z)

                if(grid.wallGrid[actualIndex] || grid.densities[actualIndex] < densityEps) {
                    continue
                }

                val lGrid = rxTargetGrid(x - 1, y, z, grid)
                val rGrid = rxTargetGrid(x + 1, y, z, grid)
                val uGrid = rxTargetGrid(x, y + 1, z, grid)
                val dGrid = rxTargetGrid(x, y - 1, z, grid)
                val bGrid = rxTargetGrid(x, y, z - 1, grid)
                val fGrid = rxTargetGrid(x, y, z + 1, grid)

                var fx = 0f
                var fy = 0f
                var fz = 0f

                val actualDensity = grid.densities[actualIndex] // density is analogous to mass

                val densityL = lGrid.densities[x - 1, y, z]
                val densityR = rGrid.densities[x + 1, y, z]
                val densityU = uGrid.densities[x, y + 1, z]
                val densityD = dGrid.densities[x, y - 1, z]
                val densityB = bGrid.densities[x, y, z - 1]
                val densityF = fGrid.densities[x, y, z + 1]

                val dxDensL = actualDensity - densityL
                val dxDensR = actualDensity - densityR
                val dxDensU = actualDensity - densityU
                val dxDensD = actualDensity - densityD
                val dxDensB = actualDensity - densityB
                val dxDensF = actualDensity - densityF

                // Apply forces:

                if(actualDensity > pressureThreshold) {
                    // Apply pressure force:
                    if(!lGrid.wallGrid[x - 1, y, z]) fx -= dxDensL * pressureCoefficient
                    if(!rGrid.wallGrid[x + 1, y, z]) fx += dxDensR * pressureCoefficient
                    if(!uGrid.wallGrid[x, y + 1, z]) fy += dxDensU * pressureCoefficient
                    if(!dGrid.wallGrid[x, y - 1, z]) fy -= dxDensD * pressureCoefficient
                    if(!bGrid.wallGrid[x, y, z - 1]) fz -= dxDensB * pressureCoefficient
                    if(!fGrid.wallGrid[x, y, z + 1]) fz += dxDensF * pressureCoefficient
                }

                // Integrate in time:
                grid.velocitiesX[actualIndex] = ((fx / actualDensity) * dt).coerceIn(-velocityRange, velocityRange)
                grid.velocitiesY[actualIndex] = ((fy / actualDensity) * dt).coerceIn(-velocityRange, velocityRange)
                grid.velocitiesZ[actualIndex] = ((fz / actualDensity) * dt).coerceIn(-velocityRange, velocityRange)
            }
        }
    }

    private fun volumetricPass(dt: Float) {
        val kDampening = 1f - options.dampening

        val cursor = sparseGrid.createCursor()

        val diffusionBox = Box()
        val rxDiffusionBox = Box()

        val rxDiffusionMargin = options.diffusionRadius - 0.5f
        val diffusionDiameter = options.diffusionRadius * 2f

        val neighbors = ArrayList<Grid3dFluid>()

        activeFluidCells = 0

        sparseGrid.activeGrids.forEach { grid ->
            cursor.loadGrid(grid)

            val densityEps = options.densityThreshold

            for (i in sparseGrid.windowRange) {
                cursor.loadIndex(i)

                val x = cursor.x
                val y = cursor.y
                val z = cursor.z

                val actualIndex = grid.ixGrid(x, y, z)

                if(grid.wallGrid[actualIndex] || grid.densities[actualIndex] < densityEps) {
                    continue
                }

                activeFluidCells++

                val lGrid = rxTargetGrid(x - 1, y, z, grid)
                val rGrid = rxTargetGrid(x + 1, y, z, grid)
                val uGrid = rxTargetGrid(x, y + 1, z, grid)
                val dGrid = rxTargetGrid(x, y - 1, z, grid)
                val fGrid = rxTargetGrid(x, y, z + 1, grid)
                val bGrid = rxTargetGrid(x, y, z - 1, grid)

                val velX = grid.velocitiesX[actualIndex]
                val velY = grid.velocitiesY[actualIndex]
                val velZ = grid.velocitiesZ[actualIndex]

                grid.newVelocitiesX[actualIndex] = velX * kDampening
                grid.newVelocitiesY[actualIndex] = velY * kDampening
                grid.newVelocitiesZ[actualIndex] = velZ * kDampening

                val diffusionCenterX = (velX * dt).coerceIn(-1f, 1f)
                val diffusionCenterY = (velY * dt).coerceIn(-1f, 1f)
                val diffusionCenterZ = (velZ * dt).coerceIn(-1f, 1f)

                val xf = x.toFloat()
                val yf = y.toFloat()
                val zf = z.toFloat()

                diffusionBox.load(
                    x = xf + diffusionCenterX - rxDiffusionMargin,
                    y = yf + diffusionCenterY - rxDiffusionMargin,
                    z = zf + diffusionCenterZ - rxDiffusionMargin,
                    width = diffusionDiameter,
                    height = diffusionDiameter,
                    depth = diffusionDiameter
                )

                val sVolume = volumetricScan(diffusionBox, rxDiffusionBox, xf, yf, zf)
                val lVolume = volumetricScan(diffusionBox, rxDiffusionBox, xf - 1f, yf, zf)
                val rVolume = volumetricScan(diffusionBox, rxDiffusionBox, xf + 1f, yf, zf)
                val uVolume = volumetricScan(diffusionBox, rxDiffusionBox, xf, yf + 1f, zf)
                val dVolume = volumetricScan(diffusionBox, rxDiffusionBox, xf, yf - 1f, zf)
                val bVolume = volumetricScan(diffusionBox, rxDiffusionBox, xf, yf, zf - 1f)
                val fVolume = volumetricScan(diffusionBox, rxDiffusionBox, xf, yf, zf + 1f)

                val totalVolume = sVolume + lVolume + rVolume + uVolume + dVolume + bVolume + fVolume
                val normalizeRecip = 1f / (totalVolume / grid.densities[actualIndex])

                diffuse(actualIndex, grid, x - 1, y, z, lGrid, lVolume * normalizeRecip)
                diffuse(actualIndex, grid, x + 1, y, z, rGrid, rVolume * normalizeRecip)
                diffuse(actualIndex, grid, x, y + 1, z, uGrid, uVolume * normalizeRecip)
                diffuse(actualIndex, grid, x, y - 1, z, dGrid, dVolume * normalizeRecip)
                diffuse(actualIndex, grid, x, y, z - 1, bGrid, bVolume * normalizeRecip)
                diffuse(actualIndex, grid, x, y, z + 1, fGrid, fVolume * normalizeRecip)
            }

            fun evaluateActivation(neighborGrid: Grid3dFluid) {
                if(neighborGrid.rxAmount > options.activateThreshold) {
                    sparseGrid.deferActivation(neighborGrid)
                }
            }

            grid.neighborScan(neighbors)
            neighbors.forEach(::evaluateActivation)
            neighbors.clear()

            grid.rxAmount = 0f
        }
    }

    private fun diffuse(
        actualIndex: Int,
        actualGrid: Grid3dFluid,
        neighborX: Int,
        neighborY: Int,
        neighborZ: Int,
        neighborGrid: Grid3dFluid,
        amount: Float
    ) {
        val neighborIndex = neighborGrid.ixGrid(neighborX, neighborY, neighborZ)

        if(amount < options.diffusionThreshold || neighborGrid.wallGrid[neighborIndex]) {
            return
        }

        actualGrid.newDensities[actualIndex] -= amount
        neighborGrid.newDensities[neighborIndex] += amount
        neighborGrid.rxAmount += amount
    }

    private fun volumetricScan(srcBox: Box, neighborBox: Box, x: Float, y: Float, z: Float): Float {
        neighborBox.load(x, y, z, 1f, 1f, 1f)

        return Box.intersectVolume(srcBox, neighborBox)
    }

    private fun applyChangesAndRefitClusters() {
        totalDensity = 0f

        val cursor = sparseGrid.createCursor()

        sparseGrid.activeGrids.forEach { grid ->
            var gridDelta = 0f
            var gridTotal = 0f
            var hasWalls = false

            cursor.loadGrid(grid)

            for (i in sparseGrid.windowRange) {
                cursor.loadIndex(i)
                val actualIndex = grid.ixGrid(cursor.x, cursor.y, cursor.z)

                hasWalls = hasWalls || grid.wallGrid[actualIndex]

                val newDensity = grid.newDensities[actualIndex]

                gridDelta += abs(grid.densities[actualIndex] - newDensity)
                grid.densities[actualIndex] = newDensity
                gridTotal += newDensity
            }

            grid.absoluteFlow = gridDelta
            grid.total = gridTotal

            if(!hasWalls && gridTotal < options.gridDeleteThreshold) {
                sparseGrid.deferDeletion(grid)
            }
            else if(gridDelta < options.activateThreshold) {
                sparseGrid.deferInactivation(grid)
            }
        }

        sparseGrid.applyInactivation()
        sparseGrid.applyDeletion()
        sparseGrid.scanClusters()

        sparseGrid.subGrids.values.forEach {
            totalDensity += it.total
        }

        activeClusters = sparseGrid.clusters.size

        sparseGrid.clusters.forEach { cluster ->
            var rationalDelta = 0f
            // why no sum function with floats?

            cluster.forEach { rationalDelta += it.absoluteFlow }
            rationalDelta /= cluster.size

            if(rationalDelta < options.gridDeleteThreshold) {
                cluster.forEach { sparseGrid.deferInactivation(it) }
            }
        }
    }

    fun step() {
        val dt = options.diffusionRate

        loadSimulationGraphs()

        velocityPass(dt)
        volumetricPass(dt)

        sparseGrid.applyActivation()
        applyChangesAndRefitClusters()
        sparseGrid.applyInactivation()
    }

    private class Box {
        var left = 0f
        var right = 0f
        var bottom = 0f
        var top = 0f
        var back = 0f
        var front = 0f

        fun load(x: Float, y: Float, z: Float, width: Float, height: Float, depth: Float) {
            left = x
            right = x + width
            bottom = y
            top = y + height
            back = z
            front = z + depth
        }

        companion object {
            fun intersectVolume(a: Box, b: Box): Float {
                val x1 = max(a.left, b.left)
                val x2 = min(a.right, b.right)
                val y1 = max(a.bottom, b.bottom)
                val y2 = min(a.top, b.top)
                val z1 = max(a.back, b.back)
                val z2 = min(a.front, b.front)

                return if (x2 > x1 && y2 > y1 && z2 > z1)
                    (x2 - x1) * (y2 - y1) * (z2 - z1)
                else 0f
            }
        }
    }
}
