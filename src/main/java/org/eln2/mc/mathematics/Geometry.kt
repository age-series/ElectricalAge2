package org.eln2.mc.mathematics

import kotlin.math.floor

/**
 * Represents a vector of [size] elements.
 * */
interface IKDVector<T> {
    val size: Int

    /**
     * Gets the value at the specified [index].
     * An error will be produced if [index] is out-of-bounds.
     * */
    operator fun get(index: Int): T
}

/**
 * [IKDVector] with mutation capability.
 * */
interface IKDVectorMutable<T>: IKDVector<T> {
    /**
     * Sets the element at the specified [index] to [value].
     * An error will be produced if [index] is out-of-bounds.
     * */
    operator fun set(index: Int, value: T)
}

/**
 * Specialized [IKDVector] with integer values.
 * */
interface IKDVectorI: IKDVector<Int> {
    override val size: Int
    operator fun iterator(): Iterator<Int>
    override operator fun get(index: Int): Int
    fun toArray(): IntArray

    operator fun plus(other: IKDVectorI): IKDVectorI
}

/**
 * [IKDVectorI] with mutation capability.
 * */
interface IKDVectorIMutable : IKDVectorI, IKDVectorMutable<Int> {
    override operator fun set(index: Int, value: Int)
}

/**
 * Mutable [IKDVectorI] with an [IntArray] as backing store.
 * */
class KDVectorI(val values: IntArray) : IKDVectorIMutable {
    constructor(size: Int) : this(IntArray(size))

    override val size get() = values.size

    override fun iterator(): Iterator<Int> {
        return values.iterator()
    }

    override operator fun get(index: Int): Int {
        return values[index]
    }

    override fun toArray(): IntArray {
        return values.copyOf()
    }

    override operator fun set(index: Int, value: Int) {
        values[index] = value
    }

    override operator fun plus(other: IKDVectorI): KDVectorI {
        val result = KDVectorI(size)

        for (i in 0 until size) {
            result[i] = this[i] + other[i]
        }

        return result
    }

    operator fun minus(other: KDVectorI): KDVectorI {
        val result = KDVectorI(size)

        for (i in 0 until size) {
            result[i] = this[i] - other[i]
        }

        return result
    }

    operator fun plusAssign(other: KDVectorI) {
        for (i in 0 until size) {
            this[i] += other[i]
        }
    }

    operator fun minusAssign(other: KDVectorI) {
        for (i in 0 until size) {
            this[i] -= other[i]
        }
    }

    fun copy(): KDVectorI {
        return KDVectorI(values.copyOf())
    }

    fun clamp(min: Int, max: Int) {
        for (i in 0 until size) {
            this[i] -= this[i].coerceIn(min, max)
        }
    }

    fun clamped(min: Int, max: Int): KDVectorI {
        return this.copy().also { it.clamp(min, max) }
    }

    companion object {
        fun ofSize(size: Int): KDVectorI {
            return KDVectorI(IntArray(size))
        }
    }
}

/**
 * @return A [KDVectorI] with the specified [values].
 * */
fun kdVectorIOf(vararg values: Int): KDVectorI {
    return KDVectorI(values.asList().toIntArray())
}

/**
 * @return A [KDVectorI] with the specified [values].
 * */
fun kdVectorIOf(values: List<Int>): KDVectorI {
    return KDVectorI(values.toIntArray())
}

/**
 * @return A [KDVectorIImmutable] with the specified [values].
 * */
fun kdVectorImOf(values: List<Int>): KDVectorIImmutable {
    return KDVectorIImmutable(values)
}

/**
 * @return A [KDVectorIImmutable] with the specified [values].
 * */
fun kdVectorImOf(vararg values: Int): KDVectorIImmutable {
    return kdVectorImOf(values.asList())
}

/**
 * [IKDVectorI] that is guaranteed to be immutable.
 * @param source The values in this vector. This collection is cloned (and a reference is not retained).
 * */
class KDVectorIImmutable(source: List<Int>) : IKDVectorI {
    constructor(vararg source: Int) : this(source.asList())

    val values = source.toList()

    override val size get() = values.size

    override fun iterator(): Iterator<Int> {
        return values.iterator()
    }

    val isEmpty get() = size == 0

    override operator fun get(index: Int): Int {
        return values[index]
    }

    /**
     * Creates a new IntArray from this vector's values.
     * */
    override fun toArray(): IntArray {
        return values.toIntArray()
    }

    override fun plus(other: IKDVectorI): IKDVectorI {
        val result = KDVectorI(size)

        for (i in 0 until size) {
            result[i] = this[i] + other[i]
        }

        return result
    }
}

/**
 * Specialized [IKDVector] with real values.
 * */
interface IKDVectorD: IKDVector<Double> {
    override val size: Int

    operator fun iterator(): Iterator<Double>

    override operator fun get(index: Int): Double

    /**
     * Returns an [IKDVectorI] with the values in this vector transformed using the [floor] function.
     * */
    fun floored(): IKDVectorI

    /**
     * Returns an [IKDVectorD] with the values in this vector transformed using the [frac] function.
     * */
    fun fraction(): IKDVectorD
}

/**
 * Mutable [IKDVectorD] with a [DoubleArray] as backing store.
 * */
class KDVectorD(val values: DoubleArray) : IKDVectorD, IKDVectorMutable<Double> {
    constructor(size: Int) : this(DoubleArray(size))

    override val size get() = values.size

    override fun iterator(): Iterator<Double> {
        return values.iterator()
    }

    override operator fun get(index: Int): Double {
        return values[index]
    }

    override fun floored(): KDVectorI {
        val result = KDVectorI.ofSize(size)

        for (i in 0 until size) {
            result[i] = floor(this[i]).toInt()
        }

        return result
    }

    override fun fraction(): IKDVectorD {
        val result = ofSize(size)

        for (i in 0 until size) {
            result[i] = frac(this[i])
        }

        return result
    }

    override operator fun set(index: Int, value: Double) {
        values[index] = value
    }

    operator fun plus(other: KDVectorD): KDVectorD {
        val result = KDVectorD(size)

        for (i in 0 until size) {
            result[i] = this[i] + other[i]
        }

        return result
    }

    operator fun minus(other: KDVectorD): KDVectorD {
        val result = KDVectorD(size)

        for (i in 0 until size) {
            result[i] = this[i] - other[i]
        }

        return result
    }

    operator fun plusAssign(other: KDVectorD) {
        for (i in 0 until size) {
            this[i] += other[i]
        }
    }

    operator fun minusAssign(other: KDVectorD) {
        for (i in 0 until size) {
            this[i] -= other[i]
        }
    }

    /**
     * @return A copy of this vector.
     * */
    fun copy(): KDVectorD {
        return KDVectorD(values.copyOf())
    }

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
    fun clamped(min: Double, max: Double): KDVectorD {
        return this.copy().also { it.clamp(min, max) }
    }

    companion object {
        /**
         * Create a new vector with [size] values, initialized to 0.
         * */
        fun ofSize(size: Int): KDVectorD {
            return KDVectorD(DoubleArray(size))
        }
    }
}

/**
 * @return A [KDVectorD] with the specified [values].
 * */
fun kdVectorDOf(values: List<Double>): KDVectorD {
    return KDVectorD(values.toDoubleArray())
}

/**
 * @return A [KDVectorD] with the specified [values].
 * */
fun kdVectorDOf(vararg values: Double): KDVectorD {
    return KDVectorD(values.asList().toDoubleArray())
}

/**
 * Represents a grid of an arbitrary number of [dimensions].
 * */
interface IKDGrid<T> {
    val dimensions: Int

    /**
     * @return The size of the grid along the specified [dimension].
     * */
    fun getSize(dimension: Int): Int

    /**
     * @param coordinates The coordinates inside this grid. If they are out-of-bounds, an error will be produced.
     * @return The value in the cell at the specified [coordinates].
     * */
    operator fun get(coordinates: IKDVectorI): T
}

/**
 * [IKDGrid] with mutation capability.
 * */
interface IKDGridMutable<T> {
    /**
     * Sets the [value] at the specified coordinates. If they are out-of-bounds, an error will be produced.
     * */
    operator fun set(coordinates: IKDVectorI, value: T)
}

/**
 * Mutable [KDGrid] with an [Array] as backing store.
 * */
class KDGrid<T>(val sizes: KDVectorIImmutable, val grid: Array<T>) : IKDGrid<T>, IKDGridMutable<T> {
    private val strides: IntArray

    override val dimensions get() = sizes.size

    init {
        validateGrid(sizes, grid.size)
        strides = computeStrides(sizes)
    }

    override fun getSize(dimension: Int): Int {
        return sizes[dimension]
    }

    private fun computeIndex(coordinates: IKDVectorI): Int {
        return computeIndex(coordinates)
    }

    override operator fun get(coordinates: IKDVectorI): T {
        return grid[computeIndex(coordinates)]
    }

    override operator fun set(coordinates: IKDVectorI, value: T) {
        grid[computeIndex(coordinates)] = value
    }

    companion object {
        fun computeIndex(coordinates: IKDVectorI, strides: IntArray): Int {
            if (coordinates.size != strides.size) {
                error("Cannot index ${strides.size}D grid with ${coordinates.size} coordinates")
            }

            var index = 0

            for (i in strides.indices) {
                index += strides[i] * coordinates[i]
            }

            return index
        }

        fun computeGridSize(sizes: IKDVectorI): Int {
            var result = 1

            for (i in 0 until sizes.size) {
                result *= sizes[i]
            }

            return result
        }

        fun gridFits(sizes: IKDVectorI, gridSize: Int): Boolean {
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

        fun validateGrid(sizes: IKDVectorI, gridSize: Int) {
            if (!gridFits(sizes, gridSize)) {
                error("Insufficient space in grid")
            }
        }

        fun computeStrides(sizes: IKDVectorI): IntArray {
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

/**
 * Mutable [KDGrid] of [Double] with a [DoubleArray] as backing store.
 * */
class KDGridD(val sizes: KDVectorIImmutable, val grid: DoubleArray) : IKDGrid<Double>, IKDGridMutable<Double> {
    private val strides: IntArray

    override val dimensions get() = sizes.size

    init {
        KDGrid.validateGrid(sizes, grid.size)
        strides = KDGrid.computeStrides(sizes)
    }

    override fun getSize(dimension: Int): Int {
        return sizes[dimension]
    }

    private fun computeIndex(coordinates: IKDVectorI): Int {
        return KDGrid.computeIndex(coordinates, strides)
    }

    override operator fun get(coordinates: IKDVectorI): Double {
        return grid[computeIndex(coordinates)]
    }

    override operator fun set(coordinates: IKDVectorI, value: Double) {
        grid[computeIndex(coordinates)] = value
    }
}

/**
 * Calls [IKDGrid.get] with the specified [coordinates].
 * */
inline operator fun<reified T> IKDGrid<T>.get(vararg coordinates: Int): T {
    return this[kdVectorIOf(coordinates.asList())]
}

/**
 * Calls [IKDGridMutable.set] with the specified [coordinates] and [value].
 * */
inline operator fun<reified T> IKDGridMutable<T>.set(vararg coordinates: Int, value: T) {
    this[kdVectorIOf(coordinates.asList())] = value
}

/**
 * @return A [KDGrid] with the specified [sizes], with all cells initialized to [default].
 * */
inline fun <reified T> kdGridOf(sizes: KDVectorIImmutable, default: T): KDGrid<T> {
    return KDGrid(sizes, Array(KDGrid.computeGridSize(sizes)) { default })
}

/**
 * @return A [KDGrid] with the specified [sizes], with all cells initialized to [default].
 * */
inline fun <reified T> kdGridOf(default: T, vararg sizes: Int): KDGrid<T> {
    return kdGridOf(KDVectorIImmutable(sizes.asList()), default)
}

/**
 * @return A [KDGridD] with the specified [sizes], with all cells initialized to [default].
 * */
fun kdGridDOf(sizes: KDVectorIImmutable, default: Double): KDGridD {
    return KDGridD(sizes, DoubleArray(KDGrid.computeGridSize(sizes)) { default })
}

/**
 * @return A [KDGridD] with the specified [sizes], with all cells initialized to [default].
 * */
fun kdGridDOf(default: Double, vararg sizes: Int): KDGridD {
    return kdGridDOf(KDVectorIImmutable(sizes.asList()), default)
}

/**
 * @return A [KDGrid] with the specified [sizes], with all cells initialized to 0.
 * */
fun kdGridDOf(sizes: KDVectorIImmutable): KDGridD {
    return kdGridDOf(sizes, 0.0)
}

fun kdGridDOf(vararg sizes: Int): KDGridD {
    return kdGridDOf(KDVectorIImmutable(sizes.asList()))
}

/**
 * @return A [GridInterpolator] created from [this] grid.
 * */
fun KDGridD.interpolator(): GridInterpolator {
    return GridInterpolator(this)
}

/**
 * Visits all cells in a grid by calling [consumer] with the coordinates of the cell.
 * */
fun <T> IKDGrid<T>.traverse(consumer: ((IKDVectorI) -> Unit)) {
    fun traverseDimension(dimension: Int, coordinates: KDVectorI) {
        val size = this.getSize(dimension)

        for (i in 0 until size) {
            coordinates[dimension] = i

            if(dimension != 0) {
                traverseDimension(dimension - 1, coordinates.copy())
            }
            else {
                consumer(coordinates)
            }
        }
    }

    traverseDimension(this.dimensions - 1, KDVectorI.ofSize(this.dimensions))
}

/**
 * @return The value of [this] vector along the first dimension.
 * */
fun<T> IKDVector<T>.x(): T = this[0]
/**
 * @return The value of [this] vector along the second dimension.
 * */
fun<T> IKDVector<T>.y(): T = this[1]
/**
 * @return The value of [this] vector along the third dimension.
 * */
fun<T> IKDVector<T>.z(): T = this[2]
/**
 * @return The value of [this] vector along the fourth dimension.
 * */
fun<T> IKDVector<T>.w(): T = this[3]
/**
 * @return The size of [this] grid along the first dimension.
 * */
fun IKDGrid<*>.width(): Int = this.getSize(0)
/**
 * @return The size of [this] grid along the second dimension.
 * */
fun IKDGrid<*>.height(): Int = this.getSize(1)
/**
 * @return The size of [this] grid along the third dimension.
 * */
fun IKDGrid<*>.depth(): Int = this.getSize(2)

/**
 * @return A [KDVectorD] created from the values in [this] vector.
 * */
fun IKDVector<Double>.toKDVectorD(): KDVectorD {
    val values = DoubleArray(this.size)

    for (i in 0 until this.size) {
        values[i] = this[i]
    }

    return KDVectorD(values)
}

// todo maybe use apache math or joml:

data class Vector2I(val x: Int, val y: Int) {
    companion object {
        fun one(): Vector2I = Vector2I(1, 1)
        fun zero(): Vector2I = Vector2I(0, 0)
    }

    operator fun plus(other: Vector2I): Vector2I = Vector2I(x + other.x, y + other.y)

    fun toVector2F(): Vector2F = Vector2F(x.toFloat(), y.toFloat())
}

data class Vector2F(val x: Float, val y: Float) {
    companion object {
        fun one(): Vector2F = Vector2F(1f, 1f)
        fun zero(): Vector2F = Vector2F(0f, 0f)
    }

    fun toVector2I(): Vector2I {
        return Vector2I(x.toInt(), y.toInt())
    }
}

data class Rectangle4I(val x: Int, val y: Int, val width: Int, val height: Int) {
    constructor(pos: Vector2I, width: Int, height: Int): this(pos.x, pos.y, width, height)
    constructor(pos: Vector2I, size: Vector2I): this(pos.x, pos.y, size.x, size.y)
    constructor(x: Int, y: Int, size: Vector2I): this(x, y, size.x, size.y)

    val left get() = x
    val right get() = x + width
    val top get() = y
    val bottom get() = y + height
}

data class Rectangle4F(val x: Float, val y: Float, val width: Float, val height: Float) {
    constructor(pos: Vector2F, width: Float, height: Float): this(pos.x, pos.y, width, height)
    constructor(pos: Vector2F, size: Vector2F): this(pos.x, pos.y, size.x, size.y)
    constructor(x: Float, y: Float, size: Vector2F): this(x, y, size.x, size.y)

    val left get() = x
    val right get() = x + width
    val top get() = y
    val bottom get() = y + height
}
