@file:Suppress("NonAsciiCharacters", "LocalVariableName", "RemoveRedundantBackticks")

package org.eln2.mc.mathematics

import org.eln2.mc.data.SegmentRange
import org.eln2.mc.data.SegmentTree
import org.eln2.mc.data.SegmentTreeBuilder
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.cosh
import kotlin.math.pow

/**
 * The grid interpolator is used to query arbitrary coordinates inside a [ArrayKDGridD], with interpolation
 * of the neighbor cells.
 * */
class GridInterpolator(val grid: KDGridD) {
    fun interface Interpolator {
        fun interpolate(a: Double, b: Double, t: Double): Double
    }

    /**
     * Pre-computed offset table to compute neighbor coordinates.
     * */
    private val neighborOffsets =
        ArrayList<ArrayKDVectorI>()
            .also { findCorners(it, grid.dimensions - 1, ArrayKDVectorI.ofSize(grid.dimensions)) }
            .map { KDVectorIImmutable(it.values.toList()) }

    /**
     * Clamps the [coordinates] and returns the cell value.
     * */
    private fun getClamped(coordinates: KDVectorI): Double {
        val clamped = ArrayKDVectorI(coordinates.toArray()).also {
            for (i in 0 until it.size) {
                it.values[i] = it.values[i].coerceIn(0, grid.getSize(i) - 1)
            }
        }

        return grid[clamped]
    }

    /**
     * Queries the grid at the specified coordinates, applying the interpolation function on
     * the neighbor cells.
     * */
    fun evaluate(coordinates: KDVectorD, function: Interpolator): Double {
        if (coordinates.size != grid.dimensions) {
            error("Cannot access ${grid.dimensions}-D grid with ${coordinates.size} coordinates")
        }

        val lower = coordinates.floored()

        val neighborValues = ArrayList<Double>(pow2I(grid.dimensions))

        neighborOffsets.forEach { offset ->
            neighborValues.add(getClamped(lower + offset))
        }

        return kdInterpolate(grid.dimensions, 0, neighborValues, coordinates.fraction(), function)
    }

    companion object {
        /**
         * Finds neighbor coordinate offsets.
         * */
        private fun findCorners(results: ArrayList<ArrayKDVectorI>, index: Int, current: ArrayKDVectorI) {
            if (index == 0) {
                results.add(current.bind().also { it[0] = 0 })
                results.add(current.bind().also { it[0] = 1 })
            } else {
                findCorners(results, index - 1, current.bind().also { it[index] = 0 })
                findCorners(results, index - 1, current.bind().also { it[index] = 1 })
            }
        }

        /**
         * Interpolates the neighbor values using the specified function.
         * */
        private fun kdInterpolate(
            dimension: Int,
            index: Int,
            samples: List<Double>,
            progress: KDVectorD,
            function: Interpolator,
        ): Double {
            return if (dimension == 1) {
                function.interpolate(samples[index], samples[index + 1], progress[0])
            } else function.interpolate(
                lowerBoundary(dimension, index, samples, progress, function),
                higherBoundary(dimension, index, samples, progress, function),
                progress[dimension - 1]
            )
        }

        private fun lowerBoundary(
            dims: Int,
            i: Int,
            values: List<Double>,
            t: KDVectorD,
            function: Interpolator,
        ): Double {
            return kdInterpolate(dims - 1, i, values, t, function)
        }

        private fun higherBoundary(
            dims: Int,
            i: Int,
            values: List<Double>,
            t: KDVectorD,
            function: Interpolator,
        ): Double {
            return kdInterpolate(dims - 1, i + pow2I(dims - 1), values, t, function)
        }
    }
}

interface SplineSegment {
    val t0: Double
    val t1: Double
    val y0: Double
    val y1: Double

    fun evaluate(t: Double): Double
    fun evaluateDual(t: Dual): Dual
}

abstract class SplineSegmentMap(val segments: List<SplineSegment>) {
    init {
        require(segments.isNotEmpty()) { "Cannot create empty spline segment list" }

        if(segments.size > 1) {
            for(i in 1 until segments.size) {
                val previous = segments[i - 1]
                val current = segments[i]

                if(previous.t1 != current.t0) {
                    error("Segment list continuity error")
                }
            }
        }
    }

    fun left(index: Int): SplineSegment {
        if (index <= 0) {
            return segments.first()
        }

        return segments[index - 1]
    }

    fun right(index: Int): SplineSegment {
        if (index >= count - 1) {
            return segments.last()
        }

        return segments[index + 1]
    }

    fun findIndex(t: Double): Int {
        if(t <= t0) {
            return 0
        }

        if(t >= t1) {
            return segments.size - 1
        }

        return findIndexCore(t)
    }

    protected abstract fun findIndexCore(key: Double): Int

    operator fun get(index: Int) = segments[index]
    operator fun get(key: Double) = segments[findIndex(key)]

    val t0 get() = segments.first().t0
    val t1 get() = segments.last().t1
    val y0 get() = segments.first().y0
    val y1 get() = segments.last().y1
    val count get() = segments.size
}

/**
 * This is a spline segment list that uses a [SegmentTree] to search indices.
 * Time complexity is as specified by [SegmentTree.query]
 * */
class TreeSplineSegmentMap(segments: List<SplineSegment>): SplineSegmentMap(segments) {
    private val segmentTree = SegmentTreeBuilder<Int>().also {
        segments.forEachIndexed { index, s ->
            it.insert(index, SegmentRange(s.t0, s.t1))
        }
    }.build()

    override fun findIndexCore(key: Double) = segmentTree.query(key)
}

fun hermiteCubic(p0: Double, v0: Double, v1: Double, p1: Double, t: Double): Double {
    val t2 = t * t
    val t3 = t2 * t
    val h0 = 1.0 - 3.0 * t2 + 2.0 * t3
    val h1 = t - 2.0 * t2 + t3
    val h2 = -t2 + t3
    val h3 = 3.0 * t2 - 2.0 * t3

    return h0 * p0 + h1 * v0 + h2 * v1 + h3 * p1
}

fun hermiteCubicDual(p0: Double, v0: Double, v1: Double, p1: Double, t: Dual): Dual {
    val t2 = t * t
    val t3 = t2 * t
    val h0 = 1.0 - 3.0 * t2 + 2.0 * t3
    val h1 = t - 2.0 * t2 + t3
    val h2 = -t2 + t3
    val h3 = 3.0 * t2 - 2.0 * t3

    return h0 * p0 + h1 * v0 + h2 * v1 + h3 * p1
}

fun hermiteQuintic(p0: Double, v0: Double, a0: Double, a1: Double, v1: Double, p1: Double, t: Double): Double {
    val t3 = t * t * t
    val t4 = t3 * t
    val t5 = t4 * t
    val h0 = 1.0 - 10.0 * t3 + 15.0 * t4 - 6.0 * t5
    val h1 = t - 6.0 * t3 + 8.0 * t4 - 3.0 * t5
    val h2 = 1.0 / 2.0 * (t * t) - 3.0 / 2.0 * t3 + 3.0 / 2.0 * t4 - 1.0 / 2.0 * t5
    val h3 = 1.0 / 2.0 * t3 - t4 + 1.0 / 2.0 * t5
    val h4 = -4.0 * t3 + 7.0 * t4 - 3.0 * t5
    val h5 = 10.0 * t3 - 15.0 * t4 + 6.0 * t5

    return h0 * p0 + h1 * v0 + h2 * a0 + h3 * a1 + h4 * v1 + h5 * p1
}

fun hermiteQuinticDual(p0: Double, v0: Double, a0: Double, a1: Double, v1: Double, p1: Double, t: Dual): Dual {
    val t3 = t * t * t
    val t4 = t3 * t
    val t5 = t4 * t
    val h0 = 1.0 - 10.0 * t3 + 15.0 * t4 - 6.0 * t5
    val h1 = t - 6.0 * t3 + 8.0 * t4 - 3.0 * t5
    val h2 = 1.0 / 2.0 * (t * t) - 3.0 / 2.0 * t3 + 3.0 / 2.0 * t4 - 1.0 / 2.0 * t5
    val h3 = 1.0 / 2.0 * t3 - t4 + 1.0 / 2.0 * t5
    val h4 = -4.0 * t3 + 7.0 * t4 - 3.0 * t5
    val h5 = 10.0 * t3 - 15.0 * t4 + 6.0 * t5

    return h0 * p0 + h1 * v0 + h2 * a0 + h3 * a1 + h4 * v1 + h5 * p1
}

fun catenary(a: Double, x: Double) = a * cosh(x / a)
fun catenaryDual(a: Double, x: Dual) = a * cosh(x / a)

data class CubicHermiteSplineSegment(
    override val t0: Double,
    override val t1: Double,
    override val y0: Double,
    override val y1: Double,
    val v0: Double,
    val v1: Double,
) : SplineSegment {
    override fun evaluate(t: Double) = hermiteCubic(y0, v0, v1, y1, t)
    override fun evaluateDual(t: Dual) = hermiteCubicDual(y0, v0, v1, y1, t)
}

data class QuinticHermiteSplineSegment(
    override val t0: Double,
    override val t1: Double,
    override val y0: Double,
    override val y1: Double,
    val v0: Double,
    val a0: Double,
    val a1: Double,
    val v1: Double,
) : SplineSegment {
    override fun evaluate(t: Double) = hermiteQuintic(y0, v0, a0, a1, v1, y1, t)
    override fun evaluateDual(t: Dual) = hermiteQuinticDual(y0, v0, a0, a1, v1, y1, t)
}

data class Spline(val segments: SplineSegmentMap) {
    fun evaluate(t: Double) = segments[t].let {
        it.evaluate(
            t.mappedTo(it.t0, it.t1, 0.0, 1.0)
        )
    }

    fun evaluateDual(t: Double, n: Int = 1) = segments[t].let {
        it.evaluateDual(
            Dual.variable(
                t.mappedTo(it.t0, it.t1, 0.0, 1.0), n
            )
        )
    }
}

class SplineBuilder {
    private data class Point(val t: Double, val y: Double) {
        operator fun minus(b: Point) = y - b.y
    }

    private val points = ArrayList<Point>()

    fun with(t: Double, y: Double): SplineBuilder {
        points.add(Point(t, y))

        return this
    }

    fun buildCubicKB(t: Double = 0.0, b: Double = 0.0, c: Double = 0.0): Spline {
        require(points.size >= 2) { "Cannot build spline with ${points.size} points"}

        val segments = ArrayList<SplineSegment>()

        for (i in 1 until points.size) {
            val `pᵢ` = points[i - 1]
            val `pᵢ₊₁` = points[i]
            val `pᵢ₋₁` = if(i == 1) `pᵢ` else points[i - 2]
            val `pᵢ₊₂` = if(i == points.size - 1) `pᵢ₊₁` else points[i + 1]

           segments.add(
                CubicHermiteSplineSegment(
                    `pᵢ`.t,
                    `pᵢ₊₁`.t,
                    `pᵢ`.y,
                    `pᵢ₊₁`.y,
                    dy0KochanekBartels(t, b, c, `pᵢ₋₁`.y, `pᵢ`.y, `pᵢ₊₁`.y),
                    dy1KochanekBartels(t, b, c, `pᵢ`.y, `pᵢ₊₁`.y, `pᵢ₊₂`.y)
                )
            )
        }

        return Spline(TreeSplineSegmentMap(segments))
    }

    fun buildQuinticLoose(): Spline {
        require(points.size >= 2) { "Cannot build spline with ${points.size} points"}

        val segments = ArrayList<SplineSegment>()

        for (i in 1 until points.size) {
            val `pᵢ` = points[i - 1]
            val `pᵢ₊₁` = points[i]
            val `pᵢ₋₁` = if(i == 1) `pᵢ` else points[i - 2]
            val `pᵢ₊₂` = if(i == points.size - 1) `pᵢ₊₁` else points[i + 1]

            segments.add(
                QuinticHermiteSplineSegment(
                    `pᵢ`.t,
                    `pᵢ₊₁`.t,
                    `pᵢ`.y,
                    `pᵢ₊₁`.y,
                    `pᵢ₋₁`.y,
                    0.0,
                    0.0,
                    `pᵢ₊₂`.y
                )
            )
        }

        return Spline(TreeSplineSegmentMap(segments))
    }

    companion object {
        private fun dy0KochanekBartels(t: Double, b: Double, c: Double, `pᵢ₋₁`: Double, `pᵢ`: Double, `pᵢ₊₁`: Double) = ((1.0 - t) * (1.0 + b) * (1.0 + c)) / 2.0 * (`pᵢ` - `pᵢ₋₁`) + ((1.0 - t) * (1.0 - b) * (1.0 - c)) / 2.0 * (`pᵢ₊₁` - `pᵢ`)
        private fun dy1KochanekBartels(t: Double, b: Double, c: Double, `pᵢ`: Double, `pᵢ₊₁`: Double, `pᵢ₊₂`: Double) = ((1.0 - t) * (1.0 + b) * (1.0 - c)) / 2.0 * (`pᵢ₊₁` - `pᵢ`) + ((1.0 - t) * (1.0 - b) * (1.0 + c)) / 2.0 * (`pᵢ₊₂` - `pᵢ₊₁`)
    }
}

class SplineBuilder3d {
    val x = SplineBuilder()
    val y = SplineBuilder()
    val z = SplineBuilder()

    fun with(t: Double, v: Vector3d): SplineBuilder3d {
        x.with(t, v.x)
        y.with(t, v.y)
        z.with(t, v.z)

        return this
    }

    fun buildCubicKB(t: Double = 0.0, b: Double = 0.0, c: Double = 0.0) = Spline3d(
        x.buildCubicKB(t, b, c),
        y.buildCubicKB(t, b, c),
        z.buildCubicKB(t, b, c)
    )

    fun buildQuinticLoose(t: Double = 0.0, b: Double = 0.0, c: Double = 0.0) = Spline3d(
        x.buildCubicKB(t, b, c),
        y.buildCubicKB(t, b, c),
        z.buildCubicKB(t, b, c)
    )
}

class KDCubicInterpolatorBuilder(val size: Int) {
    init {
        require(size > 0)
    }

    val builders = List(size) { SplineBuilder() }

    fun with(t: Double, y: KDVectorD): KDCubicInterpolatorBuilder {
        require(y.size == size)

        builders.forEachIndexed { i, builder ->
            builder.with(t, y[i])
        }

        return this
    }
}

fun Spline.valueScan(samples: Int): DoubleArray {
    val results = DoubleArray(samples)

    for (i in 0 until samples) {
        results[i] = this.evaluate(
            i.toDouble().mappedTo(
                0.0,
                samples - 1.0,
                this.segments.t0,
                this.segments.t1
            )
        )
    }

    return results
}

fun Spline.pairScan(samples: Int): List<Pair<Double, Double>> {
    val results = ArrayList<Pair<Double, Double>>(samples)

    for (i in 0 until samples) {
        val t = i.toDouble().mappedTo(
            0.0,
            samples - 1.0,
            this.segments.t0,
            this.segments.t1
        )

        results.add(Pair(t, this.evaluate(t)))
    }

    return results
}

fun Spline.pairScanDual(samples: Int, n: Int = 1): List<Pair<Double, Dual>> {
    val results = ArrayList<Pair<Double, Dual>>(samples)

    for (i in 0 until samples) {
        val t = i.toDouble().mappedTo(
            0.0,
            samples - 1.0,
            this.segments.t0,
            this.segments.t1
        )

        results.add(Pair(t, this.evaluateDual(t, n)))
    }

    return results
}

fun Spline.arclengthScan(a: Double, b: Double, eps: Double = 1e-15) = integralScan(a, b, eps) { this.evaluateDual(it, 2)[1] }

data class Pose2dParametric(val value: Pose2d, val param: Double)

data class Spline2d(val x: Spline, val y: Spline) {
    fun evaluate(t: Double) = Vector2d(x.evaluate(t), y.evaluate(t))
    fun evaluateDual(t: Double, n: Int = 1) = Vector2dDual(x.evaluateDual(t, n), y.evaluateDual(t, n))
    fun evaluateTan(t: Double) = evaluateDual(t, 2).tail().value.normalized()
    fun evaluatePose(t: Double) = Pose2d(evaluate(t), Rotation2d.dir(evaluateTan(t)))
    fun arclengthScan(a: Double, b: Double, eps: Double = 1e-15) = integralScan(a, b, eps) { this.evaluateDual(it, 2)[1].norm }
}

data class Pose3dParametric(val value: Pose3d, val param: Double)

data class Spline3d(val x: Spline, val y: Spline, val z: Spline) {
    fun evaluate(t: Double) = Vector3d(x.evaluate(t), y.evaluate(t), z.evaluate(t))
    fun evaluateDual(t: Double, n: Int = 1) = Vector3dDual(x.evaluateDual(t, n), y.evaluateDual(t, n), z.evaluateDual(t, n))
    fun tangent(t: Double) = evaluateDual(t, 2)[1].normalized()
    fun evaluatePose(t: Double) = Pose3d(evaluate(t), Rotation3d.rma(frenet(t)))
    fun arclengthScan(a: Double, b: Double, eps: Double = 1e-15) = integralScan(a, b, eps) { this.evaluateDual(it, 2)[1].norm }
    fun paramSpeed(t: Double) = evaluateDual(t, 2)[1].norm
    fun curvature(t: Double) = evaluateDual(t, 3).let { (it[1] x it[2]).norm / it[1].norm.pow(3) }
    fun torsion(t: Double) = evaluateDual(t, 4).let { ((it[1] x it[2]) o it[3]) / (it[1] x it[2]).norm.pow(2) }

    fun frenet(t: Double) = evaluateDual(t, 3).let {
        val T = it[1].normalized()
        val B = (it[1] x it[2]).normalized()
        val N = (B x T)
        Matrix3x3(T, N, B)
    }
}

fun interface Adaptscan3dCondition {
    fun splits(s: Spline3d, t0: Double, t1: Double): Boolean
}

fun chordNormCondition3d(distMax: Double) : Adaptscan3dCondition {
    require(distMax > 0.0)

    val distMaxSqr = distMax * distMax

    return Adaptscan3dCondition { s, t0, t1 ->
        s.evaluate(t0) distToSqr s.evaluate(t1) > distMaxSqr
    }
}

fun diffCondition3d(distMax: Double, rotIncrMax: Double) : Adaptscan3dCondition {
    require(distMax > 0.0)
    require(rotIncrMax > 0.0)

    val distMaxSqr = distMax * distMax
    val c = cos(rotIncrMax)

    return Adaptscan3dCondition { s, t0, t1 ->
        val r0 = s.evaluateDual(t0, 2)
        val r1 = s.evaluateDual(t1, 2)

        (r0.value - r1.value).normSqr > distMaxSqr || abs(r0[1] cosAngle r1[1]) < c
    }
}

private data class AdaptscanFrame(val t0: Double, val t1: Double)

fun Spline3d.adaptscan(
    rxT: MutableList<Double>, t0: Double, t1: Double,
    tIncrMax: Double,
    iMax: Int = Int.MAX_VALUE,
    condition: Adaptscan3dCondition
): Boolean {
    require(t0 < t1)
    require(tIncrMax > 0.0)

    rxT.add(t0)

    val stack = ArrayDeque<AdaptscanFrame>().apply {
        addLast(AdaptscanFrame(t0, t1))
    }

    var iter = 0
    var t = t0

    while (stack.isNotEmpty()) {
        val fr = stack.removeLast()

        if (abs(fr.t1 - t) > tIncrMax || condition.splits(this, fr.t0, fr.t1)) {
            stack.addLast(AdaptscanFrame((fr.t0 + fr.t1) / 2.0, fr.t1))
            stack.addLast(AdaptscanFrame(fr.t0, (fr.t0 + fr.t1) / 2.0))
        } else {
            rxT.add(fr.t1)
            t = fr.t1
        }

        if (iter++ >= iMax) {
            return false
        }
    }

    return true
}

/**
 * Uses a grid interpolator and maps values to grid indices using splines.
 * @param interpolator The grid interpolator to use.
 * @param mappings Value-coordinate splines for every grid dimension.
 * */
class MappedGridInterpolator(val interpolator: GridInterpolator, val mappings: List<Spline>) {
    init {
        if(mappings.size != interpolator.grid.dimensions) {
            error("Mismatched mapping set")
        }
    }

    fun evaluate(coordinates: KDVectorD): Double {
        val grid = interpolator.grid

        val gridCoordinates = ArrayKDVectorD.ofSize(grid.dimensions)

        for (dim in 0 until grid.dimensions) {
            gridCoordinates[dim] = mappings[dim].evaluate(coordinates[dim])
        }

        return interpolator.evaluate(gridCoordinates, ::lerp)
    }
}

fun MappedGridInterpolator.evaluate(vararg coordinates: Double): Double {
    return this.evaluate(kdVectorDOf(coordinates.asList()))
}

/**
 * @return A [GridInterpolator] created from [this] grid.
 * */
fun ArrayKDGridD.interpolator(): GridInterpolator {
    return GridInterpolator(this)
}
