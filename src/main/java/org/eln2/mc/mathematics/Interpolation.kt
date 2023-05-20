package org.eln2.mc.mathematics

import org.eln2.mc.Eln2
import org.eln2.mc.data.SegmentRange
import org.eln2.mc.data.SegmentTree
import org.eln2.mc.data.SegmentTreeBuilder
import org.eln2.mc.utility.ResourceReader
import kotlin.math.floor

/**
 * The grid interpolator is used to query arbitrary coordinates inside a [KDGridD], with interpolation
 * of the neighbor cells.
 * */
class GridInterpolator(val grid: KDGridD) {
    fun interface IInterpolationFunction {
        fun interpolate(a: Double, b: Double, t: Double): Double
    }

    /**
     * Pre-computed offset table to compute neighbor coordinates.
     * */
    private val neighborOffsets =
        ArrayList<KDVectorI>()
            .also { findCorners(it, grid.dimensions - 1, KDVectorI.ofSize(grid.dimensions)) }
            .map { KDVectorIImmutable(it.values.toList()) }

    /**
     * Clamps the [coordinates] and returns the cell value.
     * */
    private fun getClamped(coordinates: IKDVectorI): Double {
        val clamped = KDVectorI(coordinates.toArray()).also {
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
    fun evaluate(coordinates: IKDVectorD, function: IInterpolationFunction): Double {
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
        private fun findCorners(results: ArrayList<KDVectorI>, index: Int, current: KDVectorI) {
            if (index == 0) {
                results.add(current.copy().also { it[0] = 0 })
                results.add(current.copy().also { it[0] = 1 })
            } else {
                findCorners(results, index - 1, current.copy().also { it[index] = 0 })
                findCorners(results, index - 1, current.copy().also { it[index] = 1 })
            }
        }

        /**
         * Interpolates the neighbor values using the specified function.
         * */
        private fun kdInterpolate(
            dimension: Int,
            index: Int,
            samples: List<Double>,
            progress: IKDVectorD,
            function: IInterpolationFunction
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
            t: IKDVectorD,
            function: IInterpolationFunction
        ): Double {
            return kdInterpolate(dims - 1, i, values, t, function)
        }

        private fun higherBoundary(
            dims: Int,
            i: Int,
            values: List<Double>,
            t: IKDVectorD,
            function: IInterpolationFunction
        ): Double {
            return kdInterpolate(dims - 1, i + pow2I(dims - 1), values, t, function)
        }
    }
}

/**
 * The grid vector interpolator is used to interpolate a grid of vectors.
 * Internally, this uses a [GridInterpolator] for every dimension.
 * */
class GridVectorInterpolator(val interpolators: List<GridInterpolator>) {
    fun evaluate(coordinates: IKDVectorD, function: GridInterpolator.IInterpolationFunction): KDVectorD {
        val result = KDVectorD.ofSize(interpolators.size)

        for (i in interpolators.indices) {
            result[i] = interpolators[i].evaluate(coordinates, function)
        }

        return result
    }
}

/**
 * Represents a cubic hermite spline with parameter ranges assigned based on the number of samples, from 0-1.
 * */
class HermiteSplineCubic {
    val points = ArrayList<Double>()

    fun evaluate(progress: Double): Double {
        if (points.size == 0) {
            // Wouldn't make sense to evaluate it with one, but let's throw errors only for 0

            error("Cannot evaluate spline with 0 points")
        }

        val fuzzyIndex = (points.size - 1) * progress
        val t = fuzzyIndex - floor(fuzzyIndex)
        val pointIndex = fuzzyIndex.toInt()

        return hermiteCubic(
            getPoint(pointIndex - 1),
            getPoint(pointIndex + 0),
            getPoint(pointIndex + 1),
            getPoint(pointIndex + 2),
            t
        )
    }

    private fun getPoint(index: Int): Double {
        if (index < 0) {
            return points.first()
        }

        if (index >= points.size) {
            return points.last()
        }

        return points[index]
    }

    companion object {

        fun loadSpline(path: String): HermiteSplineCubic {
            return HermiteSplineCubic().also { spline ->
                ResourceReader.getResourceString(Eln2.resource(path))
                    .lines()
                    .filter { it.isNotBlank() && it.isNotEmpty() }
                    .map { it.toDouble() }
                    .forEach(spline.points::add)

                Eln2.LOGGER.info("Loaded ${spline.points.size} points from $path")
            }
        }
    }
}

interface ISplineSegment {
    /**
     * Gets the parameter range's lower boundary.
     * */
    val keyStart: Double

    /**
     * Gets the parameter range's upper boundary.
     * */
    val keyEnd: Double

    /**
     * Gets the values stored in this segment.
     * */
    val points: List<Double>

    /**
     * Gets the leftmost value in this segment.
     * */
    val valueStart: Double

    /**
     * Gets the rightmost value in this segment.
     * */
    val valueEnd: Double
}

/**
 * Represents a list of spline segments with search capabilities.
 * Implementors should document time complexities of the search functions.
 * */
interface ISplineSegmentList<Segment : ISplineSegment> {
    /**
     * Finds the spline segment that matches the range of the key.
     * @return The index of the spline segment. If the segment is out of range,
     * the corresponding boundary index will be given.
     * */
    fun find(key: Double): Int

    /**
     * Gets the left neighbor of the segment at the specified index.
     * */
    fun left(index: Int): Segment

    /**
     * Gets the right neighbor of the segment at the specified index.
     * */
    fun right(index: Int): Segment

    /**
     * Gets the spline segment at the specified index.
     * */
    operator fun get(index: Int): Segment

    /**
     * Gets the leftmost (smallest) key in this segment list.
     * */
    val startKey: Double

    /**
     * Gets the rightmost (largest) key in this segment list.
     * */
    val endKey: Double

    /**
     * Gets the leftmost value in this segment list.
     * */
    val startValue: Double

    /**
     * Gets the rightmost value in this segment list.
     * */
    val endValue: Double

    /**
     * Gets the number of spline segments.
     * */
    val count: Int
}

/**
 * Base [SplineSegmentList] behavior. Search is not implemented here.
 * */
abstract class SplineSegmentList<Segment : ISplineSegment>(protected val segments: List<Segment>): ISplineSegmentList<Segment> {
    override fun left(index: Int): Segment {
        if (index <= 0) {
            return segments.first()
        }

        return segments[index - 1]
    }

    override fun right(index: Int): Segment {
        if (index >= count - 1) {
            return segments.last()
        }

        return segments[index + 1]
    }

    override fun get(index: Int): Segment {
        return segments[index]
    }

    override val startKey: Double
        get() = segments.first().keyStart

    override val endKey: Double
        get() = segments.last().keyEnd

    override val startValue: Double
        get() = segments.first().valueStart

    override val endValue: Double
        get() = segments.last().valueEnd

    override val count: Int
        get() = segments.size
}

/**
 * This is a [SplineSegmentList] with *O(n)* search time.
 * */
class LinearSplineSegmentList<Segment : ISplineSegment>(segments: List<Segment>) : SplineSegmentList<Segment>(segments) {
    init {
        if(segments.isEmpty()){
            error("Tried to initialize segment list with 0 segments")
        }

        if(segments.size > 1) {
            for(i in 1 until segments.size) {
                val previous = segments[i - 1]
                val current = segments[i]

                if(previous.keyEnd != current.keyStart) {
                    error("Segment list continuity error")
                }
            }
        }
    }

    override fun find(key: Double): Int {
        val index = segments.indexOfFirst { it.keyStart <= key && it.keyEnd >= key }

        if (index == -1) {
            return if(key < segments.first().keyStart) {
                0
            } else if(key > segments.last().keyEnd) {
                segments.size - 1
            }
            else error("Unexpected key $key")
        }

        return index
    }


}

/**
 * This is a spline segment list that uses a [SegmentTree] to search indices.
 * Time complexity is as specified by [SegmentTree.query]
 * */
class TreeSplineSegmentList<Segment : ISplineSegment>(segments: List<Segment>): SplineSegmentList<Segment>(segments) {
    private val segmentTree = SegmentTreeBuilder<Int>().also {
        segments.forEachIndexed { index, s ->
            it.insert(index, SegmentRange(s.keyStart, s.keyEnd))
        }
    }.build()

    //FIXME: we should be returning the boundary index, not ERROR!
    override fun find(key: Double): Int {
        return segmentTree.queryOrNull(key) ?: error("Spline index $key out of range")
    }
}

/**
 * @param keyStart Parameter range lower boundary.
 * @param keyEnd Parameter range upper boundary.
 * @param points Spline knots.
 * */
data class HermiteSplineCubicSegment(override val keyStart: Double, override val keyEnd: Double, override val points: List<Double>) : ISplineSegment {
    init {
        if (points.size < 2) {
            error("Cannot create spline segment with ${points.size} points")
        }

        if (keyEnd <= keyStart) {
            error("Cannot create spline segment with keys $keyStart - $keyEnd")
        }
    }

    /**
     * Gets the leftmost value in this segment.
     * */
    override val valueStart get() = points.first()

    /**
     * Gets the rightmost value in this segment.
     * */
    override val valueEnd get() = points.last()
}

/**
 * Represents a cubic hermite spline with arbitrary parameter ranges.
 * */
class HermiteSplineCubicMapped(val segments: ISplineSegmentList<HermiteSplineCubicSegment>) {
    fun evaluate(key: Double): Double {
        if(key < segments.startKey) {
            return segments.startValue
        }

        if(key > segments.endKey) {
            return segments.endValue
        }

        val index = segments.find(key)

        val left = segments.left(index)
        val right = segments.right(index)
        val middle = segments[index]

        val progress = map(key, middle.keyStart, middle.keyEnd, 0.0, 1.0)

        return hermiteCubic(
            left.valueStart,
            middle.valueStart,
            middle.valueEnd,
            right.valueEnd,
            progress
        )
    }
}

/**
 * Utility class for building a spline from data points.
 * */
class MappedSplineBuilder {
    private val segments = ArrayList<HermiteSplineCubicSegment>()

    private var started = false
    private var lastKey = 0.0
    private var lastValue = 0.0

    fun point(key: Double, value: Double): MappedSplineBuilder {
        if (!started) {
            started = true
            lastKey = key
            lastValue = value
        } else {
            segments.add(HermiteSplineCubicSegment(lastKey, key, listOf(lastValue, value)))
            lastKey = key
            lastValue = value
        }

        return this
    }

    fun buildHermite(listFactory: ((List<HermiteSplineCubicSegment>) -> ISplineSegmentList<HermiteSplineCubicSegment>)): HermiteSplineCubicMapped {
        if (segments.isEmpty()) {
            error("Tried to build spline with 0 segments")
        }

        return HermiteSplineCubicMapped(listFactory(segments.toList()))
    }

    fun buildHermite(): HermiteSplineCubicMapped {
        return buildHermite(::TreeSplineSegmentList)
    }

    fun buildHermite2(): HermiteSplineCubicMapped {
        return buildHermite(::LinearSplineSegmentList)
    }
}

fun hermiteMappedCubic(): MappedSplineBuilder {
    return MappedSplineBuilder()
}

/**
 * Uses a grid interpolator and maps values to grid indices using splines.
 * @param interpolator The grid interpolator to use.
 * @param mappings Value-coordinate splines for every grid dimension.
 * */
class MappedGridInterpolator(
    val interpolator: GridInterpolator,
    val mappings: List<HermiteSplineCubicMapped>) {
    init {
        if(mappings.size != interpolator.grid.dimensions) {
            error("Mismatched mapping set")
        }
    }

    fun evaluate(coordinates: IKDVectorD): Double {
        val grid = interpolator.grid

        val gridCoordinates = KDVectorD.ofSize(grid.dimensions)

        for (dim in 0 until grid.dimensions) {
            gridCoordinates[dim] = mappings[dim].evaluate(coordinates[dim])
        }

        return interpolator.evaluate(gridCoordinates, ::lerp)
    }
}

fun MappedGridInterpolator.evaluate(vararg coordinates: Double): Double {
    return this.evaluate(kdVectorDOf(coordinates.asList()))
}

fun hermiteCubic(a: Double, b: Double, c: Double, d: Double, t: Double): Double {
    val t2 = t * t
    val t3 = t2 * t

    val h1 = -a / 2.0 + 3.0 * b / 2.0 - 3.0 * c / 2.0 + d / 2.0
    val h2 = a - 5.0 * b / 2.0 + 2.0 * c - d / 2.0
    val h3 = -a / 2.0 + c / 2.0

    return h1 * t3 + h2 * t2 + h3 * t + b
}
