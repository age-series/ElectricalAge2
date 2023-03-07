package org.eln2.mc.mathematics

import org.eln2.mc.mathematics.Mathematics.frac
import kotlin.math.floor

interface IKDVectorI {
    val size: Int
    operator fun iterator(): Iterator<Int>
    operator fun get(index: Int): Int
    fun toArray(): IntArray

    operator fun plus(other: IKDVectorI): IKDVectorI
}

interface IKDVectorIMutable : IKDVectorI {
    operator fun set(index: Int, value: Int)
}

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

fun kdVectorIOf(vararg values: Int): KDVectorI {
    return KDVectorI(values.asList().toIntArray())
}

fun kdVectorIOf(values: List<Int>): KDVectorI {
    return KDVectorI(values.toIntArray())
}

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

interface IKDVectorD {
    val size: Int
    operator fun iterator(): Iterator<Double>
    operator fun get(index: Int): Double

    fun floored(): IKDVectorI
    fun fraction(): IKDVectorD
}

class KDVectorD(val values: DoubleArray) : IKDVectorD {
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

    operator fun set(index: Int, value: Double) {
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

    fun copy(): KDVectorD {
        return KDVectorD(values.copyOf())
    }

    fun clamp(min: Double, max: Double) {
        for (i in 0 until size) {
            this[i] -= this[i].coerceIn(min, max)
        }
    }

    fun clamped(min: Double, max: Double): KDVectorD {
        return this.copy().also { it.clamp(min, max) }
    }

    companion object {
        fun ofSize(size: Int): KDVectorD {
            return KDVectorD(DoubleArray(size))
        }
    }
}

fun kdVectorDOf(values: List<Double>): KDVectorD {
    return KDVectorD(values.toDoubleArray())
}

fun kdVectorDOf(vararg values: Double): KDVectorD {
    return KDVectorD(values.asList().toDoubleArray())
}

interface IKdGrid<T> {
    val dimensions: Int
    fun getSize(dimension: Int): Int
    operator fun get(coordinates: IKDVectorI): T
}

interface IKdGridMutable<T> {
    operator fun set(coordinates: IKDVectorI, value: T)
}

class KDGrid<T>(val sizes: KDVectorIImmutable, val grid: Array<T>) : IKdGrid<T>, IKdGridMutable<T> {
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


class KDGridD(val sizes: KDVectorIImmutable, val grid: DoubleArray) : IKdGrid<Double>, IKdGridMutable<Double> {
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

inline fun <reified T> kdGridOf(sizes: KDVectorIImmutable, default: T): KDGrid<T> {
    return KDGrid(sizes, Array(KDGrid.computeGridSize(sizes)) { default })
}

inline fun <reified T> kdGridOf(default: T, vararg sizes: Int): KDGrid<T> {
    return kdGridOf(KDVectorIImmutable(sizes.asList()), default)
}

fun kdGridDOf(sizes: KDVectorIImmutable, default: Double): KDGridD {
    return KDGridD(sizes, DoubleArray(KDGrid.computeGridSize(sizes)) { default })
}

fun kdGridDOf(default: Double, vararg sizes: Int): KDGridD {
    return kdGridDOf(KDVectorIImmutable(sizes.asList()), default)
}

fun kdGridDOf(sizes: KDVectorIImmutable): KDGridD {
    return kdGridDOf(sizes, 0.0)
}

fun kdGridDOf(vararg sizes: Int): KDGridD {
    return kdGridDOf(KDVectorIImmutable(sizes.asList()))
}
