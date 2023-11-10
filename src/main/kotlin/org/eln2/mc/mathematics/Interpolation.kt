@file:Suppress("NonAsciiCharacters", "LocalVariableName", "RemoveRedundantBackticks", "NOTHING_TO_INLINE")

package org.eln2.mc.mathematics

import it.unimi.dsi.fastutil.doubles.DoubleArrayList
import org.eln2.mc.data.ClosedInterval
import org.eln2.mc.data.SegmentTree
import org.eln2.mc.data.SegmentTreeBuilder
import kotlin.math.*

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

        val neighborValues = ArrayList<Double>(1 shl grid.dimensions)

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
            return kdInterpolate(dims - 1, i + (1 shl (dims - 1)), values, t, function)
        }
    }
}

inline fun bilinear(m00: Double, m10: Double, m01: Double, m11: Double, x: Double, y: Double) : Double {
    return lerp(
        lerp(m00, m10, x),
        lerp(m01, m11, x),
        y
    )
}


inline fun trilinear(m000: Double, m100: Double, m010: Double, m110: Double, m001: Double, m101: Double, m011: Double, m111: Double, x: Double, y: Double, z: Double) : Double {
    return lerp(
        bilinear(m000, m001, m010, m011, y, z),
        bilinear(m100, m101, m110, m111, y, z),
        x
    )
}


inline fun bilinearF(m00: Float, m10: Float, m01: Float, m11: Float, x: Float, y: Float) : Float {
    return lerp(
        lerp(m00, m10, x),
        lerp(m01, m11, x),
        y
    )
}

inline fun trilinearF(
    m000: Float,
    m100: Float,
    m010: Float,
    m110: Float,
    m001: Float,
    m101: Float,
    m011: Float,
    m111: Float,
    x: Float,
    y: Float,
    z: Float
): Float {
    val a = m000 + y * (m001 - m000)
    val a1 = m100 + y * (m101 - m100)
    val a2 = a + z * ((m010 + y * (m011 - m010)) - a)
    return a2 + x * (a1 + z * (m110 + y * (m111 - m110) - a1) - a2)
}

interface SplineSegmentParametric {
    val t0: Double
    val t1: Double

    fun segmentAbscissa(splineAbscissa: Double) = map(splineAbscissa, t0, t1, 0.0, 1.0)
}

abstract class SplineSegmentMap<S : SplineSegmentParametric>(val segments: List<S>) {
    init {
        require(segments.isNotEmpty()) { "Cannot create empty spline segment list" }

        if (segments.size > 1) {
            for (i in 1 until segments.size) {
                val previous = segments[i - 1]
                val current = segments[i]

                if (previous.t1 != current.t0) {
                    error("Segment list continuity error")
                }
            }
        }
    }

    fun left(index: Int): S {
        if (index <= 0) {
            return segments.first()
        }

        return segments[index - 1]
    }

    fun right(index: Int): S {
        if (index >= count - 1) {
            return segments.last()
        }

        return segments[index + 1]
    }

    fun findIndex(t: Double): Int {
        if (t <= t0) {
            return 0
        }

        if (t >= t1) {
            return segments.size - 1
        }

        return findIndexCore(t)
    }

    protected abstract fun findIndexCore(key: Double): Int

    operator fun get(index: Int) = segments[index]
    operator fun get(key: Double) = segments[findIndex(key)]

    val t0 get() = segments.first().t0
    val t1 get() = segments.last().t1
    val count get() = segments.size
}

interface SplineSegment1d : SplineSegmentParametric {
    val y0: Dual
    val y1: Dual
    fun evaluate(tActual: Double): Double
    fun evaluateDual(tActual: Dual): Dual
}

interface SplineSegment3d : SplineSegmentParametric {
    val y0: Vector3dDual
    val y1: Vector3dDual
    fun evaluate(tSegment: Double): Vector3d
    fun evaluateDual(tSegment: Dual): Vector3dDual
}

/**
 * This is a spline segment list that uses a [SegmentTree] to find indices.
 * Time complexity is as specified by [SegmentTree.query]
 * */
class TreeSplineSegmentMap<S : SplineSegmentParametric>(segments: List<S>) : SplineSegmentMap<S>(segments) {
    private val segmentTree = SegmentTreeBuilder<Int>().also {
        segments.forEachIndexed { index, s ->
            it.insert(index, ClosedInterval(s.t0, s.t1))
        }
    }.build()

    override fun findIndexCore(key: Double) = segmentTree.query(key)
}
/**
 * This is a spline segment list that does a linear search to find indices.
 * Time complexity is as specified by [SegmentTree.query]
 * */
class ListSplineSegmentMap<S : SplineSegmentParametric>(segments: List<S>) : SplineSegmentMap<S>(segments) {
    override fun findIndexCore(key: Double) = segments.indexOfFirst { it.t0 <= key && key <= it.t1 }
}

fun <S : SplineSegmentParametric> splineSegmentMapOf(vararg segments: S): SplineSegmentMap<S> = TreeSplineSegmentMap(segments.asList())

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

data class ArcReparamCatenary2d(
    val p0: Vector2d,
    val p1: Vector2d,
    val length: Double,
    val t0: Double = 0.0,
    val t1: Double = 1.0,
) {
    private val a: Double

    private val pr: Double
    private val qr: Double

    init {
        require(p0.y <= p1.y)
        require(p0 != p1)
        a = parametricScan(length, p1.y - p0.y, p1.x - p0.x)
        pr = (p0.x + p1.x - a * ln((length + (p1.y - p0.y)) / (length - (p1.y - p0.y)))) / 2.0
        qr = (p0.y + p1.y - length * coth((p1.x - p0.x) / (2.0 * a))) / 2.0
    }

    private fun abscissa(t: Double, x0: Double, x1: Double) = t.mappedTo(
        t0, t1,
        x0, x1
    )

    private fun abscissa(t: Dual, x0: Dual, x1: Dual) = t.mappedTo(
        Dual.const(t0, t.size), Dual.const(t1, t.size),
        x0, x1
    )

    fun evaluate(t: Double): Vector2d {
        val x = abscissa(t, p0.x, p1.x)
        return Vector2d(x, a * cosh((x - pr) / a) + qr)
    }

    fun evaluateDual(t: Dual): Vector2dDual {
        val p0Dual = Vector2dDual.const(p0, t.size)
        val p1Dual = Vector2dDual.const(p1, t.size)
        val lengthDual = Dual.const(this.length, t.size)
        val widthDual = p1Dual.x - p0Dual.x
        val heightDual = p1Dual.y - p0Dual.y
        val `2` = Dual.const(2.0, t.size)
        val aDual = Dual.const(this.a, t.size)

        val p = (p0Dual.x + p1Dual.x - aDual * ln((lengthDual + heightDual) / (lengthDual - heightDual))) / `2`
        val q = (p0Dual.y + p1Dual.y - lengthDual * coth(widthDual / (`2` * aDual))) / `2`

        val x = abscissa(
            t,
            p0Dual.x,
            p1Dual.x
        )

        return Vector2dDual(x, aDual * cosh((x - p) / aDual) + q)
    }

    companion object {
        fun parametricScan(s: Double, v: Double, h: Double, maxI: Int = 1024, eps: Double = 1e-12): Double {
            val dagn = sqrt(s * s - v * v)

            var param = 0.5

            fun error(): Dual {
                val m = 2.0 * Dual.variable(param, 2)
                return m * sinh(h / m) - dagn
            }

            // Solve using Grissess-Raphson method:
            for (i in 1..maxI) {
                val error = error()

                param -= (error.value / error[1])

                if (error.value < eps) {
                    break
                }
            }

            return param
        }
    }
}

data class ArcReparamCatenary3d(
    val p0: Vector3d,
    val p1: Vector3d,
    val length: Double,
    val vertical: Vector3d,
    val t0: Double = 0.0,
    val t1: Double = 1.0,
) {
    val catenary = ArcReparamCatenary2d(
        Vector2d.zero,
        Vector2d(
            (p1 - p0).projectOnPlane(vertical).norm,
            (p1 - p0).projectOnVector(vertical).norm
        ),
        length,
        t0,
        t1
    )

    fun matchesParameters(eps: Double = 1e-3) : Boolean {
        if(!evaluate(t0).approxEq(p0, eps)) {
            return false
        }

        if(!evaluate(t1).approxEq(p1, eps)) {
            return false
        }

        return true
    }

    fun evaluate(t: Double): Vector3d {
        val ordinate = catenary.evaluate(t)

        val trHorizontal = p0 + (p1 - p0).projectOnPlane(vertical).normalized() * ordinate.x
        val trVertical = vertical * ordinate.y

        return trHorizontal + trVertical
    }

    fun evaluateDual(t: Dual): Vector3dDual {
        val p0 = Vector3dDual.const(p0, t.size)
        val p1 = Vector3dDual.const(p1, t.size)
        val vertical = Vector3dDual.const(vertical, t.size)

        val ordinate = catenary.evaluateDual(t)

        val trHorizontal = p0 + (p1 - p0).projectOnPlane(vertical).normalized() * ordinate.x
        val trVertical = vertical * ordinate.y

        return trHorizontal + trVertical
    }
}

data class ArcReparamCatenarySegment3d(
    override val t0: Double,
    override val t1: Double,
    val p0: Vector3d,
    val p1: Vector3d,
    val length: Double,
    val vertical: Vector3d
) : SplineSegment3d {
    val catenary = ArcReparamCatenary3d(p0, p1, length, vertical, 0.0, 1.0)

    override val y0 = Vector3dDual.const(p0, 1)
    override val y1 = Vector3dDual.const(p1, 1)
    override fun evaluate(tSegment: Double) = catenary.evaluateDual(Dual.variable(tSegment, 1)).value
    override fun evaluateDual(tSegment: Dual) = catenary.evaluateDual(tSegment)
}

data class LinearSplineSegment3d(
    override val t0: Double,
    override val t1: Double,
    val p0: Vector3d,
    val p1: Vector3d
) : SplineSegment3d {
    override val y0 = Vector3dDual.of(p0)
    override val y1 = Vector3dDual.of(p1)
    override fun evaluate(tSegment: Double) = Vector3d.lerp(p0, p1, tSegment)
    // Do not evaluate with y0, y1, it will get reduced
    override fun evaluateDual(tSegment: Dual) = Vector3dDual.lerp(p0, p1, tSegment)
}

data class CubicHermiteSplineSegment1d(
    override val t0: Double,
    override val t1: Double,
    val p0: Double,
    val p1: Double,
    val v0: Double,
    val v1: Double,
) : SplineSegment1d {
    override val y0 = Dual.of(p0, v0)
    override val y1 = Dual.of(p1, v1)
    override fun evaluate(tActual: Double) = hermiteCubic(p0, v0, v1, p1, tActual)
    override fun evaluateDual(tActual: Dual) = hermiteCubicDual(p0, v0, v1, p1, tActual)
}

data class LinearSplineSegment1d(
    override val t0: Double,
    override val t1: Double,
    val p0: Double,
    val p1: Double,
) : SplineSegment1d {
    override val y0 = Dual.of(p0)
    override val y1 = Dual.of(p1)
    override fun evaluate(tActual: Double) = lerp(p0, p1, tActual)
    override fun evaluateDual(tActual: Dual) = lerp(p0, p1, tActual)
}

data class QuinticHermiteSplineSegment1d(
    override val t0: Double,
    override val t1: Double,
    val p0: Double,
    val p1: Double,
    val v0: Double,
    val a0: Double,
    val a1: Double,
    val v1: Double,
) : SplineSegment1d {
    override val y0 = Dual.of(p0, v0, a0)
    override val y1 = Dual.of(p1, v1, a1)
    override fun evaluate(tActual: Double) = hermiteQuintic(p0, v0, a0, a1, v1, p1, tActual)
    override fun evaluateDual(tActual: Dual) = hermiteQuinticDual(p0, v0, a0, a1, v1, p1, tActual)
}

data class QuinticHermiteSplineSegment3d(
    override val t0: Double,
    override val t1: Double,
    val p0: Vector3d,
    val v0: Vector3d,
    val a0: Vector3d,
    val p1: Vector3d,
    val v1: Vector3d,
    val a1: Vector3d,
) : SplineSegment3d {
    override val y0 = Vector3dDual.of(p0, v0, a0)
    override val y1 = Vector3dDual.of(p1, v1, a1)
    override fun evaluate(tSegment: Double) = Vector3d(
        hermiteQuintic(p0.x, v0.x, a0.x, a1.x, v1.x, p1.x, tSegment),
        hermiteQuintic(p0.y, v0.y, a0.y, a1.y, v1.y, p1.y, tSegment),
        hermiteQuintic(p0.z, v0.z, a0.z, a1.z, v1.z, p1.z, tSegment)
    )

    override fun evaluateDual(tSegment: Dual) = Vector3dDual(
        hermiteQuinticDual(p0.x, v0.x, a0.x, a1.x, v1.x, p1.x, tSegment),
        hermiteQuinticDual(p0.y, v0.y, a0.y, a1.y, v1.y, p1.y, tSegment),
        hermiteQuinticDual(p0.z, v0.z, a0.z, a1.z, v1.z, p1.z, tSegment)
    )
}

fun interface InterpolationFunction<P, V> {
    fun evaluate(t: P): V
}

interface InterpolationFunctionDual<P, V, VD> : InterpolationFunction<P, V> {
    fun evaluateDual(t: P, n: Int = 1): VD
}

data class Spline1d(val map: SplineSegmentMap<SplineSegment1d>) : InterpolationFunctionDual<Double, Double, Dual> {
    override fun evaluate(t: Double) = map[t].let {
        it.evaluate(it.segmentAbscissa(t))
    }

    override fun evaluateDual(t: Double, n: Int) = map[t].let {
        it.evaluateDual(Dual.variable(it.segmentAbscissa(t), n))
    }

    fun arclengthScan(a: Double, b: Double, eps: Double) = integralScan(a, b, eps) { this.evaluateDual(it, 2)[1] }
}

fun <P, V, VD> InterpolationFunctionDual<P, V, VD>.reparamV(f: (P) -> P) = this.let { original ->
    object : InterpolationFunctionDual<P, V, VD> {
        override fun evaluate(t: P) = original.evaluate(f(t))
        override fun evaluateDual(t: P, n: Int) = original.evaluateDual(f(t), n)
    }
}

fun <P, V, VD> InterpolationFunctionDual<P, V, VD>.reordV(f: (V) -> V, fd: (VD) -> VD) = this.let { original ->
    object : InterpolationFunctionDual<P, V, VD> {
        override fun evaluate(t: P) = f(original.evaluate(t))
        override fun evaluateDual(t: P, n: Int) = fd(original.evaluateDual(t, n))
    }
}

fun <P0, P1, V, VD> InterpolationFunctionDual<P0, V, VD>.reparamU(f: (P1) -> P0) = this.let { original ->
    object : InterpolationFunctionDual<P1, V, VD> {
        override fun evaluate(t: P1) = original.evaluate(f(t))
        override fun evaluateDual(t: P1, n: Int) = original.evaluateDual(f(t), n)
    }
}

fun <P, V0, VD0, V, VD> InterpolationFunctionDual<P, V0, VD0>.reordU(f: (V0) -> V, fd: (VD0) -> VD) =
    this.let { original ->
        object : InterpolationFunctionDual<P, V, VD> {
            override fun evaluate(t: P) = f(original.evaluate(t))
            override fun evaluateDual(t: P, n: Int) = fd(original.evaluateDual(t))
        }
    }

data class Pose3dParametric(val value: Pose3d, val t: Double)

data class Spline3d(val segments: SplineSegmentMap<SplineSegment3d>) : InterpolationFunctionDual<Double, Vector3d, Vector3dDual> {
    constructor(segment: SplineSegment3d) : this(ListSplineSegmentMap(listOf(segment)))

    override fun evaluate(t: Double) = segments[t].let { it.evaluate(it.segmentAbscissa(t)) }
    override fun evaluateDual(t: Double, n: Int) = segments[t].let { it.evaluateDual(Dual.variable(it.segmentAbscissa(t), n)) }
    fun tangent(t: Double) = evaluateDual(t, 2)[1].normalized()
    fun arclengthScan(a: Double, b: Double, eps: Double) = integralScan(a, b, eps) { this.evaluateDual(it, 2)[1].norm }
    fun paramSpeed(t: Double) = evaluateDual(t, 2)[1].norm
    fun curvature(t: Double) = evaluateDual(t, 3).let { (it[1] x it[2]).norm / it[1].norm.pow(3) }
    fun torsion(t: Double) = evaluateDual(t, 4).let { ((it[1] x it[2]) o it[3]) / (it[1] x it[2]).norm.pow(2) }

    fun frenet(t: Double) = evaluateDual(t, 3).let {
        val T = it[1].normalized()
        val B = (it[1] x it[2]).normalized()
        val N = (B x T)

        Matrix3x3(T, N, B)
    }

    fun frenetPose(t: Double) = evaluateDual(t, 3).let {
        val T = it[1].normalized()
        val B = (it[1] x it[2]).normalized()
        val N = (B x T)

        Pose3d(
            it.value,
            Rotation3d.fromRotationMatrix(Matrix3x3(T, N, B))
        )
    }

}

class InterpolatorBuilder {
    private data class Point(val t: Double, val y: Double) {
        operator fun minus(b: Point) = y - b.y
    }

    private val points = ArrayList<Point>()

    fun with(t: Double, y: Double): InterpolatorBuilder {
        points.add(Point(t, y))

        return this
    }

    fun with(p: Pair<Double, Double>) = with(p.first, p.second)

    fun buildCubic(t: Double = 0.0, b: Double = 0.0, c: Double = 0.0): Spline1d {
        require(points.size >= 2) { "Cannot build spline with ${points.size} points" }

        val segments = ArrayList<CubicHermiteSplineSegment1d>()

        for (i in 1 until points.size) {
            val `pᵢ` = points[i - 1]
            val `pᵢ₊₁` = points[i]
            val `pᵢ₋₁` = if (i == 1) `pᵢ` else points[i - 2]
            val `pᵢ₊₂` = if (i == points.size - 1) `pᵢ₊₁` else points[i + 1]

            segments.add(
                CubicHermiteSplineSegment1d(
                    `pᵢ`.t,
                    `pᵢ₊₁`.t,
                    `pᵢ`.y,
                    `pᵢ₊₁`.y,
                    dy0KochanekBartels(t, b, c, `pᵢ₋₁`.y, `pᵢ`.y, `pᵢ₊₁`.y),
                    dy1KochanekBartels(t, b, c, `pᵢ`.y, `pᵢ₊₁`.y, `pᵢ₊₂`.y)
                )
            )
        }

        return Spline1d(TreeSplineSegmentMap(segments))
    }

    fun buildLinear(): Spline1d {
        val segments = ArrayList<LinearSplineSegment1d>()

        for (i in 1 until points.size) {
            val l = points[i - 1]
            val r = points[i]

            segments.add(
                LinearSplineSegment1d(
                    l.t,
                    r.t,
                    l.y,
                    r.y
                )
            )
        }

        return Spline1d(TreeSplineSegmentMap(segments))
    }

    companion object {
        private fun dy0KochanekBartels(t: Double, b: Double, c: Double, `pᵢ₋₁`: Double, `pᵢ`: Double, `pᵢ₊₁`: Double) =
            ((1.0 - t) * (1.0 + b) * (1.0 + c)) / 2.0 * (`pᵢ` - `pᵢ₋₁`) + ((1.0 - t) * (1.0 - b) * (1.0 - c)) / 2.0 * (`pᵢ₊₁` - `pᵢ`)

        private fun dy1KochanekBartels(t: Double, b: Double, c: Double, `pᵢ`: Double, `pᵢ₊₁`: Double, `pᵢ₊₂`: Double) =
            ((1.0 - t) * (1.0 + b) * (1.0 - c)) / 2.0 * (`pᵢ₊₁` - `pᵢ`) + ((1.0 - t) * (1.0 - b) * (1.0 + c)) / 2.0 * (`pᵢ₊₂` - `pᵢ₊₁`)
    }
}

data class Vector3dParametric(val value: Vector3d, val t: Double)
data class Vector3dDualParametric(val value: Vector3dDual, val t: Double)

class PathBuilder3d {
    val points = ArrayList<Vector3dDualParametric>()

    fun add(point: Vector3dDualParametric) = points.add(point)
    fun add(t: Double, v: Vector3dDual) = add(Vector3dDualParametric(v, t))

    fun buildQuintic(): Spline3d {
        require(points.size >= 2)

        val segments = ArrayList<SplineSegment3d>()

        for (i in 1 until points.size) {
            val r0 = points[i - 1]
            val r1 = points[i]

            segments.add(
                QuinticHermiteSplineSegment3d(
                    t0 = r0.t,
                    t1 = r1.t,
                    p0 = r0.value[0],
                    v0 = r0.value[1],
                    a0 = r0.value[2],
                    p1 = r1.value[0],
                    v1 = r1.value[1],
                    a1 = r1.value[2]
                )
            )
        }

        return Spline3d(TreeSplineSegmentMap(segments))
    }
}

fun interface AdaptscanCondition<T> {
    fun splits(s: T, t0: Double, t1: Double): Boolean
}

fun differenceCondition3d(distMax: Double, rotIncrMax: Double): AdaptscanCondition<Spline3d> {
    require(distMax > 0.0)
    require(rotIncrMax > 0.0)

    val distMaxSqr = distMax * distMax
    val c = cos(rotIncrMax)

    return AdaptscanCondition { s, t0, t1 ->
        val r0 = s.evaluateDual(t0, 2)
        val r1 = s.evaluateDual(t1, 2)

        (r0.value - r1.value).normSqr > distMaxSqr || abs(r0[1] cosAngle r1[1]) < c
    }
}

fun differenceCondition1d(
    valDiffMax: Double,
    derivDiffMax: Double,
): AdaptscanCondition<InterpolationFunctionDual<Double, Double, Dual>> =
    AdaptscanCondition<InterpolationFunctionDual<Double, Double, Dual>> { s, t0, t1 ->
        val v0 = s.evaluateDual(t0, 2)
        val v1 = s.evaluateDual(t1, 2)

        abs(v0[0] - v1[0]) > valDiffMax || abs(v0[1] - v1[1]) > derivDiffMax
    }

private data class AdaptscanFrame(val t0: Double, val t1: Double)

fun <T> T.adaptscan(
    t0: Double,
    t1: Double,
    tIncrMax: Double,
    iMax: Int = Int.MAX_VALUE,
    condition: AdaptscanCondition<T>,
    consumer: (Double) -> Unit
): Boolean {
    require(t0 < t1)
    require(tIncrMax > 0.0)

    consumer(t0)

    val stack = ArrayDeque<AdaptscanFrame>().apply {
        addLast(AdaptscanFrame(t0, t1))
    }

    var i = 0
    var t = t0

    while (stack.isNotEmpty()) {
        val frame = stack.removeLast()

        if (abs(frame.t1 - t) > tIncrMax || condition.splits(this, frame.t0, frame.t1)) {
            stack.addLast(AdaptscanFrame((frame.t0 + frame.t1) / 2.0, frame.t1))
            stack.addLast(AdaptscanFrame(frame.t0, (frame.t0 + frame.t1) / 2.0))
        } else {
            consumer(frame.t1)
            t = frame.t1
        }

        if (i++ >= iMax) {
            return false
        }
    }

    return true
}

fun <T> T.adaptscan(t0: Double, t1: Double, tIncrMax: Double, iMax: Int = Int.MAX_VALUE, condition: AdaptscanCondition<T>, ): DoubleArrayList? {
    val results = DoubleArrayList()
    return if (this.adaptscan(t0, t1, tIncrMax, iMax, condition) { results.add(it) }) results else null
}

fun Spline3d.intersectGrid3d(t0: Double, t1: Double, tIncrMax: Double, iMax: Int) : HashSet<Vector3di>? {
    val results = HashSet<Vector3di>()

    val flag = this.adaptscan(t0, t1, tIncrMax, iMax, { _, a, b ->
        val pA = this.evaluate(a)
        val pB = this.evaluate(b)

        if(Vector3di.manhattan(pA.floorInt(), pB.floorInt()) == 1) {
            false
        }
        else {
            (pA distanceToSqr pB) > 0.5
        }
    }) {
        results.add(this.evaluate(it).floorInt())
    }

    return if(flag) results
    else null
}

/**
 * Uses a grid interpolator and maps values to grid indices using splines.
 * @param interpolator The grid interpolator to use.
 * @param mappings Value-coordinate splines for every grid dimension.
 * */
class MappedGridInterpolator(val interpolator: GridInterpolator, val mappings: List<Spline1d>) {
    init {
        if (mappings.size != interpolator.grid.dimensions) {
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
