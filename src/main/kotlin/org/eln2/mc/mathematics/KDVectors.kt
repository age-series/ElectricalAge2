package org.eln2.mc.mathematics

import kotlin.math.floor

interface KDVector<T> {
    val size: Int
    /**
     * Gets the value at the specified [index].
     * An error will be produced if [index] is out-of-bounds.
     * */
    operator fun get(index: Int): T
}

interface MutableKDVector<T> : KDVector<T> {
    /**
     * Sets the element at the specified [index] to [value].
     * An error will be produced if [index] is out-of-bounds.
     * */
    operator fun set(index: Int, value: T)
}

interface KDVectorI : KDVector<Int> {
    override val size: Int
    operator fun iterator(): IntIterator
    override operator fun get(index: Int): Int
    fun toArray(): IntArray

    operator fun plus(b: KDVectorI): KDVectorI
    operator fun minus(b: KDVectorI): KDVectorI
}

interface KDVectorD : KDVector<Double> {
    override val size: Int

    operator fun iterator(): DoubleIterator
    override operator fun get(index: Int): Double

    /**
     * Returns an [KDVectorI] with the values in this vector transformed using the [floor] function.
     * */
    fun floored(): KDVectorI

    /**
     * Returns an [KDVectorD] with the values in this vector transformed using the [frac] function.
     * */
    fun fraction(): KDVectorD

    operator fun plus(b: KDVectorD): KDVectorD
    operator fun minus(b: KDVectorD): KDVectorD
}

interface MutableKDVectorI : KDVectorI, MutableKDVector<Int>
interface MutableKDVectorD : KDVectorD, MutableKDVector<Double>
class ArrayKDVectorI(val values: IntArray) : MutableKDVectorI {
    constructor(size: Int) : this(IntArray(size))

    override val size get() = values.size

    override fun iterator() = values.iterator()

    override operator fun get(index: Int) = values[index]

    override fun toArray() = values.copyOf()

    override operator fun set(index: Int, value: Int) {
        values[index] = value
    }

    override operator fun plus(b: KDVectorI): ArrayKDVectorI {
        val result = ArrayKDVectorI(size)

        for (i in 0 until size) {
            result[i] = this[i] + b[i]
        }

        return result
    }

    override operator fun minus(b: KDVectorI): ArrayKDVectorI {
        val result = ArrayKDVectorI(size)

        for (i in 0 until size) {
            result[i] = this[i] - b[i]
        }

        return result
    }

    fun bind() = ArrayKDVectorI(values.copyOf())

    fun clamp(min: Int, max: Int) {
        for (i in 0 until size) {
            this[i] -= this[i].coerceIn(min, max)
        }
    }

    fun clamped(min: Int, max: Int): ArrayKDVectorI {
        return this.bind().also { it.clamp(min, max) }
    }

    companion object {
        fun ofSize(size: Int): ArrayKDVectorI {
            return ArrayKDVectorI(IntArray(size))
        }
    }
}

fun arrayKDVectorIOf(vararg values: Int) = ArrayKDVectorI(values.asList().toIntArray())
fun arrayKDVectorIOf(values: List<Int>) = ArrayKDVectorI(values.toIntArray())
fun kdVectorIOf(values: List<Int>): KDVectorI = KDVectorIImmutable(values)
fun kdVectorIOf(vararg values: Int): KDVectorI = kdVectorIOf(values.asList())
class KDVectorIImmutable private constructor(private val values: IntArray) : KDVectorI {
    constructor(values: List<Int>) : this(values.toIntArray())

    override val size get() = values.size

    override fun iterator() = values.iterator()

    val isEmpty get() = size == 0

    override operator fun get(index: Int) = values[index]

    /**
     * Creates a new IntArray from this vector's values.
     * */
    override fun toArray(): IntArray {
        return values.clone()
    }

    override fun plus(b: KDVectorI): KDVectorI {
        val result = IntArray(size)

        for (i in 0 until size) {
            result[i] = this[i] + b[i]
        }

        return KDVectorIImmutable(result)
    }

    override fun minus(b: KDVectorI): KDVectorI {
        val result = IntArray(size)

        for (i in 0 until size) {
            result[i] = this[i] - b[i]
        }

        return KDVectorIImmutable(result)
    }
}

class ArrayKDVectorD(val values: DoubleArray) : MutableKDVectorD {
    constructor(size: Int) : this(DoubleArray(size))

    override val size get() = values.size

    override fun iterator() = values.iterator()

    override operator fun get(index: Int) = values[index]

    override fun floored(): ArrayKDVectorI {
        val result = ArrayKDVectorI.ofSize(size)

        for (i in 0 until size) {
            result[i] = floor(this[i]).toInt()
        }

        return result
    }

    override fun fraction(): KDVectorD {
        val result = ofSize(size)

        for (i in 0 until size) {
            result[i] = frac(this[i])
        }

        return result
    }

    override operator fun set(index: Int, value: Double) {
        values[index] = value
    }

    override operator fun plus(b: KDVectorD): ArrayKDVectorD {
        val result = ArrayKDVectorD(size)

        for (i in 0 until size) {
            result[i] = this[i] + b[i]
        }

        return result
    }

    override operator fun minus(b: KDVectorD): ArrayKDVectorD {
        val result = ArrayKDVectorD(size)

        for (i in 0 until size) {
            result[i] = this[i] - b[i]
        }

        return result
    }

    operator fun plusAssign(other: ArrayKDVectorD) {
        for (i in 0 until size) {
            this[i] += other[i]
        }
    }

    operator fun minusAssign(other: ArrayKDVectorD) {
        for (i in 0 until size) {
            this[i] -= other[i]
        }
    }

    /**
     * @return A copy of this vector.
     * */
    fun bind() = ArrayKDVectorD(values.copyOf())

    /**
     * Clamps the values in this vector to be in the range [min]-[max].
     * */
    fun clamp(min: Double, max: Double) {
        for (i in 0 until size) {
            this[i] -= this[i].coerceIn(min, max)
        }
    }

    /**
     * @return A copy of this vector, with the values clamped to be in the range [min]-[max].
     * */
    fun clamped(min: Double, max: Double): ArrayKDVectorD {
        return this.bind().also { it.clamp(min, max) }
    }

    companion object {
        /**
         * Create a new vector with [size] values, initialized to 0.
         * */
        fun ofSize(size: Int): ArrayKDVectorD {
            return ArrayKDVectorD(DoubleArray(size))
        }
    }
}

fun kdVectorDOf(values: List<Double>): ArrayKDVectorD {
    return ArrayKDVectorD(values.toDoubleArray())
}

fun kdVectorDOf(vararg values: Double): ArrayKDVectorD {
    return ArrayKDVectorD(values.asList().toDoubleArray())
}

interface KDGrid<T> {
    val dimensions: Int

    /**
     * @return The size of the grid along the specified [dimension].
     * */
    fun getSize(dimension: Int): Int

    /**
     * @param coordinates The coordinates inside this grid. If they are out-of-bounds, an error will be produced.
     * @return The value in the cell at the specified [coordinates].
     * */
    operator fun get(coordinates: KDVectorI): T
}

interface MutableKDGrid<T> : KDGrid<T> {
    /**
     * Sets the [value] at the specified coordinates. If they are out-of-bounds, an error will be produced.
     * */
    operator fun set(coordinates: KDVectorI, value: T)
}

interface KDGridD : KDGrid<Double>
interface MutableKDGridD : KDGridD, MutableKDGrid<Double>
class ArrayKDGrid<T>(val sizes: KDVectorIImmutable, val grid: Array<T>) : MutableKDGrid<T> {
    private val strides: IntArray

    override val dimensions get() = sizes.size

    init {
        validateGrid(sizes, grid.size)
        strides = computeStrides(sizes)
    }

    override fun getSize(dimension: Int): Int {
        return sizes[dimension]
    }

    private fun computeIndex(coordinates: KDVectorI): Int {
        return computeIndex(coordinates)
    }

    override operator fun get(coordinates: KDVectorI): T {
        return grid[computeIndex(coordinates)]
    }

    override operator fun set(coordinates: KDVectorI, value: T) {
        grid[computeIndex(coordinates)] = value
    }

    companion object {
        fun computeIndex(coordinates: KDVectorI, strides: IntArray): Int {
            if (coordinates.size != strides.size) {
                error("Cannot index ${strides.size}D grid with ${coordinates.size} coordinates")
            }

            var index = 0

            for (i in strides.indices) {
                index += strides[i] * coordinates[i]
            }

            return index
        }

        fun computeGridSize(sizes: KDVectorI): Int {
            var result = 1

            for (i in 0 until sizes.size) {
                result *= sizes[i]
            }

            return result
        }

        fun gridFits(sizes: KDVectorI, gridSize: Int): Boolean {
            if (sizes.size == 0) {
                error("Grid sizes cannot be empty")
            }

            for (size in sizes) {
                if (size <= 0) {
                    error("Size cannot be negative or 0")
                }
            }

            val cells = computeGridSize(sizes)

            return gridSize >= cells
        }

        fun validateGrid(sizes: KDVectorI, gridSize: Int) {
            if (!gridFits(sizes, gridSize)) {
                error("Insufficient space in grid")
            }
        }

        fun computeStrides(sizes: KDVectorI): IntArray {
            val strides = IntArray(sizes.size)

            for (i in 0 until sizes.size) {
                var pow = 1

                for (j in 0 until i) {
                    pow *= sizes[j]
                }

                strides[i] = pow
            }

            return strides
        }
    }
}

class ArrayKDGridD(val sizes: KDVectorIImmutable, val grid: DoubleArray) : MutableKDGridD {
    private val strides: IntArray

    override val dimensions get() = sizes.size

    init {
        ArrayKDGrid.validateGrid(sizes, grid.size)
        strides = ArrayKDGrid.computeStrides(sizes)
    }

    override fun getSize(dimension: Int): Int {
        return sizes[dimension]
    }

    private fun computeIndex(coordinates: KDVectorI): Int {
        return ArrayKDGrid.computeIndex(coordinates, strides)
    }

    override operator fun get(coordinates: KDVectorI): Double {
        return grid[computeIndex(coordinates)]
    }

    override operator fun set(coordinates: KDVectorI, value: Double) {
        grid[computeIndex(coordinates)] = value
    }
}

inline operator fun <reified T> KDGrid<T>.get(vararg coordinates: Int): T {
    return this[arrayKDVectorIOf(coordinates.asList())]
}

inline operator fun <reified T> MutableKDGrid<T>.set(vararg coordinates: Int, value: T) {
    this[arrayKDVectorIOf(coordinates.asList())] = value
}

inline fun <reified T> arrayKDGridOf(sizes: KDVectorIImmutable, default: T): ArrayKDGrid<T> {
    return ArrayKDGrid(sizes, Array(ArrayKDGrid.computeGridSize(sizes)) { default })
}

/**
 * @return A [ArrayKDGrid] with the specified [sizes], with all cells initialized to [default].
 * */
inline fun <reified T> arrayKDGridOf(default: T, vararg sizes: Int): ArrayKDGrid<T> {
    return arrayKDGridOf(KDVectorIImmutable(sizes.asList()), default)
}

/**
 * @return A [ArrayKDGridD] with the specified [sizes], with all cells initialized to [default].
 * */
fun arrayKDGridDOf(sizes: KDVectorIImmutable, default: Double): ArrayKDGridD {
    return ArrayKDGridD(sizes, DoubleArray(ArrayKDGrid.computeGridSize(sizes)) { default })
}

/**
 * @return A [ArrayKDGridD] with the specified [sizes], with all cells initialized to [default].
 * */
fun arrayKDGridDOf(default: Double, vararg sizes: Int): ArrayKDGridD {
    return arrayKDGridDOf(KDVectorIImmutable(sizes.asList()), default)
}

/**
 * @return A [ArrayKDGrid] with the specified [sizes], with all cells initialized to 0.
 * */
fun arrayKDGridDOf(sizes: KDVectorIImmutable): ArrayKDGridD {
    return arrayKDGridDOf(sizes, 0.0)
}

fun arrayKDGridDOf(vararg sizes: Int): ArrayKDGridD {
    return arrayKDGridDOf(KDVectorIImmutable(sizes.asList()))
}

fun <T> KDGrid<T>.traverse(consumer: ((KDVectorI) -> Unit)) {
    fun traverseDimension(dimension: Int, coordinates: ArrayKDVectorI) {
        val size = this.getSize(dimension)

        for (i in 0 until size) {
            coordinates[dimension] = i

            if (dimension != 0) {
                traverseDimension(dimension - 1, coordinates.bind())
            } else {
                consumer(coordinates)
            }
        }
    }

    traverseDimension(this.dimensions - 1, ArrayKDVectorI.ofSize(this.dimensions))
}

fun KDVector<Double>.toKDVectorD(): ArrayKDVectorD {
    val values = DoubleArray(this.size)

    for (i in 0 until this.size) {
        values[i] = this[i]
    }

    return ArrayKDVectorD(values)
}
