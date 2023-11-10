@file:Suppress("LocalVariableName", "NOTHING_TO_INLINE")

package org.eln2.mc.mathematics

import net.minecraft.core.BlockPos
import org.eln2.mc.formatted
import kotlin.math.*


private const val GEOMETRY_COMPARE_EPS = 1e-8
private const val GEOMETRY_NORMALIZED_EPS = 1e-6

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
    constructor(pos: Vector2I, width: Int, height: Int) : this(pos.x, pos.y, width, height)
    constructor(pos: Vector2I, size: Vector2I) : this(pos.x, pos.y, size.x, size.y)
    constructor(x: Int, y: Int, size: Vector2I) : this(x, y, size.x, size.y)

    val left get() = x
    val right get() = x + width
    val top get() = y
    val bottom get() = y + height
}

data class Rectangle4F(val x: Float, val y: Float, val width: Float, val height: Float) {
    constructor(pos: Vector2F, width: Float, height: Float) : this(pos.x, pos.y, width, height)
    constructor(pos: Vector2F, size: Vector2F) : this(pos.x, pos.y, size.x, size.y)
    constructor(x: Float, y: Float, size: Vector2F) : this(x, y, size.x, size.y)

    val left get() = x
    val right get() = x + width
    val top get() = y
    val bottom get() = y + height
}

data class Matrix3x3(val c0: Vector3d, val c1: Vector3d, val c2: Vector3d) {
    constructor(
        m00: Double,
        m01: Double,
        m02: Double,
        m10: Double,
        m11: Double,
        m12: Double,
        m20: Double,
        m21: Double,
        m22: Double,
    ) : this(
        Vector3d(m00, m10, m20),
        Vector3d(m01, m11, m21),
        Vector3d(m02, m12, m22)
    )

    constructor(mat: DoubleArray) : this(mat[0], mat[1], mat[2], mat[3], mat[4], mat[5], mat[6], mat[7], mat[8])

    val r0 get() = Vector3d(c0.x, c1.x, c2.x)
    val r1 get() = Vector3d(c0.y, c1.y, c2.y)
    val r2 get() = Vector3d(c0.z, c1.z, c2.z)
    val determinant get() = c0.x * (c1.y * c2.z - c2.y * c1.z) - c1.x * (c0.y * c2.z - c2.y * c0.z) + c2.x * (c0.y * c1.z - c1.y * c0.z)
    val transpose get() = Matrix3x3(r0, r1, r2)
    val trace get() = c0.x + c1.y + c2.z
    val normFrobeniusSqr get() = c0.normSqr + c1.normSqr + c2.normSqr
    val normFrobenius get() = sqrt(normFrobeniusSqr)
    val isOrthogonal get() = (this * this.transpose).approxEq(identity) && this.determinant.absoluteValue.approxEq(1.0)
    val isSpecialOrthogonal get() = (this * this.transpose).approxEq(identity) && this.determinant.approxEq(1.0)

    override fun toString(): String {
        fun rowStr(row: Vector3d) = "${row.x} ${row.y} ${row.z}"
        return "${rowStr(r0)}\n${rowStr(r1)}\n${rowStr(r2)}"
    }

    operator fun not() = Matrix3x3(
        (c1.y * c2.z - c2.y * c1.z), -(c1.x * c2.z - c2.x * c1.z), (c1.x * c2.y - c2.x * c1.y),
        -(c0.y * c2.z - c2.y * c0.z), (c0.x * c2.z - c2.x * c0.z), -(c0.x * c2.y - c2.x * c0.y),
        (c0.y * c1.z - c1.y * c0.z), -(c0.x * c1.z - c1.x * c0.z), (c0.x * c1.y - c1.x * c0.y)
    ) * (1.0 / determinant)

    operator fun times(scalar: Double) = Matrix3x3(c0 * scalar, c1 * scalar, c2 * scalar)
    operator fun times(v: Vector3d) = c0 * v.x + c1 * v.y + c2 * v.z
    operator fun times(m: Matrix3x3) = Matrix3x3(this * m.c0, this * m.c1, this * m.c2)
    operator fun plus(m: Matrix3x3) = Matrix3x3(c0 + m.c0, c1 + m.c1, c2 + m.c2)
    operator fun minus(m: Matrix3x3) = Matrix3x3(c0 - m.c0, c1 - m.c1, c2 - m.c2)

    fun getColumn(c: Int) = when (c) {
        0 -> c0
        1 -> c1
        2 -> c2
        else -> error("Column $c out of bounds")
    }

    fun getRow(r: Int) = when (r) {
        0 -> r0
        1 -> r1
        2 -> r2
        else -> error("Row $r out of bounds")
    }

    operator fun get(c: Int, r: Int) = when (r) {
        0 -> getColumn(c).x
        1 -> getColumn(c).y
        2 -> getColumn(c).z
        else -> error("Row $r out of bounds")
    }

    fun approxEq(other: Matrix3x3, eps: Double = GEOMETRY_COMPARE_EPS) =
        this.c0.approxEq(other.c0, eps) && this.c1.approxEq(other.c1, eps) && this.c2.approxEq(other.c2, eps)

    fun toArray() : DoubleArray {
        val array = DoubleArray(9)

        array[0] = c0.x
        array[1] = c1.x
        array[2] = c2.x
        array[3] = c0.y
        array[4] = c1.y
        array[5] = c2.y
        array[6] = c0.z
        array[7] = c1.z
        array[8] = c2.z

        return array
    }

    companion object {
        fun rows(r0: Vector3d, r1: Vector3d, r2: Vector3d) = Matrix3x3(
            Vector3d(r0.x, r1.x, r2.x),
            Vector3d(r0.y, r1.y, r2.y),
            Vector3d(r0.z, r1.z, r2.z)
        )

        val identity = Matrix3x3(Vector3d.unitX, Vector3d.unitY, Vector3d.unitZ)

        inline fun get(array: DoubleArray, c: Int, r: Int) = r * 3 + c
    }
}

data class Matrix3x3Dual(val c0: Vector3dDual, val c1: Vector3dDual, val c2: Vector3dDual) {
    constructor(
        m00: Dual,
        m01: Dual,
        m02: Dual,
        m10: Dual,
        m11: Dual,
        m12: Dual,
        m20: Dual,
        m21: Dual,
        m22: Dual,
    ) : this(
        Vector3dDual(m00, m10, m20),
        Vector3dDual(m01, m11, m21),
        Vector3dDual(m02, m12, m22)
    )

    constructor(mat: DualArray) : this(mat[0], mat[1], mat[2], mat[3], mat[4], mat[5], mat[6], mat[7], mat[8])
    constructor(mat: List<Dual>) : this(mat[0], mat[1], mat[2], mat[3], mat[4], mat[5], mat[6], mat[7], mat[8])

    init {
        require(c0.size == c1.size && c1.size == c2.size)
    }

    val r0 get() = Vector3dDual(c0.x, c1.x, c2.x)
    val r1 get() = Vector3dDual(c0.y, c1.y, c2.y)
    val r2 get() = Vector3dDual(c0.z, c1.z, c2.z)

    val size get() = c0.size
    val isReal get() = size == 1
    val value = Matrix3x3(c0.value, c1.value, c2.value)
    fun head(n: Int) = Matrix3x3Dual(c0.head(n), c1.head(n), c2.head(n))
    fun tail(n: Int) = Matrix3x3Dual(c0.tail(n), c1.tail(n), c2.tail(n))

    val transpose get() = Matrix3x3Dual(r0, r1, r2)
    val trace get() = c0.x + c1.y + c2.z
    val normFrobeniusSqr get() = c0.normSqr + c1.normSqr + c2.normSqr
    val normFrobenius get() = sqrt(normFrobeniusSqr)
    val determinant get() = c0.x * (c1.y * c2.z - c2.y * c1.z) - c1.x * (c0.y * c2.z - c2.y * c0.z) + c2.x * (c0.y * c1.z - c1.y * c0.z)

    operator fun not() = Matrix3x3Dual(
        (c1.y * c2.z - c2.y * c1.z), -(c1.x * c2.z - c2.x * c1.z), (c1.x * c2.y - c2.x * c1.y),
        -(c0.y * c2.z - c2.y * c0.z), (c0.x * c2.z - c2.x * c0.z), -(c0.x * c2.y - c2.x * c0.y),
        (c0.y * c1.z - c1.y * c0.z), -(c0.x * c1.z - c1.x * c0.z), (c0.x * c1.y - c1.x * c0.y)
    ) * (1.0 / determinant)

    operator fun times(scalar: Dual) = Matrix3x3Dual(c0 * scalar, c1 * scalar, c2 * scalar)
    operator fun times(constant: Double) = Matrix3x3Dual(c0 * constant, c1 * constant, c2 * constant)
    operator fun div(scalar: Dual) = Matrix3x3Dual(c0 / scalar, c1 / scalar, c2 / scalar)
    operator fun div(constant: Double) = Matrix3x3Dual(c0 / constant, c1 / constant, c2 / constant)
    operator fun times(v: Vector3dDual) = c0 * v.x + c1 * v.y + c2 * v.z
    operator fun times(v: Vector3d) = c0 * v.x + c1 * v.y + c2 * v.z
    operator fun times(m: Matrix3x3Dual) = Matrix3x3Dual(this * m.c0, this * m.c1, this * m.c2)
    operator fun times(m: Matrix3x3) = Matrix3x3Dual(this * m.c0, this * m.c1, this * m.c2)
    operator fun get(n: Int) = Matrix3x3(c0[n], c1[n], c2[n])

    companion object {
        fun const(v: Matrix3x3, n: Int = 1) = Matrix3x3Dual(
            Vector3dDual.const(v.c0, n),
            Vector3dDual.const(v.c1, n),
            Vector3dDual.const(v.c2, n)
        )
    }
}

data class Matrix4x4(val c0: Vector4d, val c1: Vector4d, val c2: Vector4d, val c3: Vector4d) {
    constructor(
        m00: Double,
        m01: Double,
        m02: Double,
        m03: Double,
        m10: Double,
        m11: Double,
        m12: Double,
        m13: Double,
        m20: Double,
        m21: Double,
        m22: Double,
        m23: Double,
        m30: Double,
        m31: Double,
        m32: Double,
        m33: Double,
    ) : this(
        Vector4d(m00, m10, m20, m30),
        Vector4d(m01, m11, m21, m31),
        Vector4d(m02, m12, m22, m32),
        Vector4d(m03, m13, m23, m33)
    )

    val r0 get() = Vector4d(c0.x, c1.x, c2.x, c3.x)
    val r1 get() = Vector4d(c0.y, c1.y, c2.y, c3.y)
    val r2 get() = Vector4d(c0.z, c1.z, c2.z, c3.z)
    val r3 get() = Vector4d(c0.w, c1.w, c2.w, c3.w)

    val transpose get() = Matrix4x4(r0, r1, r2, r3)
    val trace get() = c0.x + c1.y + c2.z + c3.w
    val normFrobeniusSqr get() = c0.normSqr + c1.normSqr + c2.normSqr + c3.normSqr
    val normFrobenius get() = sqrt(normFrobeniusSqr)

    // Inlined for performance. We're already down a deep rabbit hole of performance issues
    // because of our design (and ⅅ), so at least let me have this one.
    val determinant
        get() =
            Matrix3x3(c1.y, c2.y, c3.y, c1.z, c2.z, c3.z, c1.w, c2.w, c3.w).determinant * c0.x -
                Matrix3x3(c1.x, c2.x, c3.x, c1.z, c2.z, c3.z, c1.w, c2.w, c3.w).determinant * c0.y +
                Matrix3x3(c1.x, c2.x, c3.x, c1.y, c2.y, c3.y, c1.w, c2.w, c3.w).determinant * c0.z -
                Matrix3x3(c1.x, c2.x, c3.x, c1.y, c2.y, c3.y, c1.z, c2.z, c3.z).determinant * c0.w

    fun eliminate(eliminateC: Int, eliminateR: Int): Matrix3x3 {
        val values = DoubleArray(3 * 3)

        var irActual = 0
        var icActual = 0

        for (ir in 0 until 4) {
            if (ir != eliminateR) {
                for (ic in 0 until 4) {
                    if (ic != eliminateC) {
                        values[(irActual * 3) + icActual++] = this[ic, ir]
                    }
                }

                irActual++
                icActual = 0
            }
        }

        return Matrix3x3(values)
    }

    fun minor(c: Int, r: Int) = eliminate(c, r).determinant
    fun cofactor(c: Int, r: Int) = minor(c, r) * powi(-1, c + r)
    val cofactorMatrix
        get() = Matrix4x4(
            cofactor(0, 0), cofactor(1, 0), cofactor(2, 0), cofactor(3, 0),
            cofactor(0, 1), cofactor(1, 1), cofactor(2, 1), cofactor(3, 1),
            cofactor(0, 2), cofactor(1, 2), cofactor(2, 2), cofactor(3, 2),
            cofactor(0, 3), cofactor(1, 3), cofactor(2, 3), cofactor(3, 3)
        )
    val adjugateMatrix get() = cofactorMatrix.transpose

    override fun toString(): String {
        fun rowStr(row: Vector4d) = "${row.x} ${row.y} ${row.z} ${row.w}"
        return "${rowStr(r0)}\n${rowStr(r1)}\n${rowStr(r2)}\n${rowStr(r3)}"
    }

    operator fun not() = adjugateMatrix * (1.0 / determinant)
    operator fun times(scalar: Double) = Matrix4x4(c0 * scalar, c1 * scalar, c2 * scalar, c3 * scalar)
    operator fun times(v: Vector4d) = c0 * v.x + c1 * v.y + c2 * v.z + c3 * v.w
    operator fun times(v: Vector3d) = (this * Vector4d(v.x, v.y, v.z, 1.0)).let { Vector3d(it.x, it.y, it.z) }
    operator fun times(m: Matrix4x4) = Matrix4x4(this * m.c0, this * m.c1, this * m.c2, this * m.c3)

    fun getColumn(c: Int) = when (c) {
        0 -> c0
        1 -> c1
        2 -> c2
        3 -> c3
        else -> error("Column $c out of bounds")
    }

    fun getRow(r: Int) = when (r) {
        0 -> r0
        1 -> r1
        2 -> r2
        3 -> r3
        else -> error("Row $r out of bounds")
    }

    operator fun get(c: Int, r: Int) = when (r) {
        0 -> getColumn(c).x
        1 -> getColumn(c).y
        2 -> getColumn(c).z
        3 -> getColumn(c).w
        else -> error("Row $r out of bounds")
    }

    companion object {
        val identity = Matrix4x4(Vector4d.unitX, Vector4d.unitY, Vector4d.unitZ, Vector4d.unitW)
    }
}

data class Matrix4x4Dual(val c0: Vector4dDual, val c1: Vector4dDual, val c2: Vector4dDual, val c3: Vector4dDual) {
    constructor(
        m00: Dual,
        m01: Dual,
        m02: Dual,
        m03: Dual,
        m10: Dual,
        m11: Dual,
        m12: Dual,
        m13: Dual,
        m20: Dual,
        m21: Dual,
        m22: Dual,
        m23: Dual,
        m30: Dual,
        m31: Dual,
        m32: Dual,
        m33: Dual,
    ) : this(
        Vector4dDual(m00, m10, m20, m30),
        Vector4dDual(m01, m11, m21, m31),
        Vector4dDual(m02, m12, m22, m32),
        Vector4dDual(m03, m13, m23, m33)
    )

    constructor(mat: List<Dual>) : this(
        mat[0],
        mat[1],
        mat[2],
        mat[3],
        mat[4],
        mat[5],
        mat[6],
        mat[7],
        mat[8],
        mat[9],
        mat[10],
        mat[11],
        mat[12],
        mat[13],
        mat[14],
        mat[15]
    )

    init {
        require(c0.size == c1.size && c1.size == c2.size && c2.size == c3.size)
    }

    val size get() = c0.size
    val isReal get() = size == 1
    val value = Matrix4x4(c0.value, c1.value, c2.value, c3.value)
    fun head(n: Int) = Matrix4x4Dual(c0.head(n), c1.head(n), c2.head(n), c3.head(n))
    fun tail(n: Int) = Matrix4x4Dual(c0.tail(n), c1.tail(n), c2.tail(n), c3.tail(n))

    val r0 get() = Vector4dDual(c0.x, c1.x, c2.x, c3.x)
    val r1 get() = Vector4dDual(c0.y, c1.y, c2.y, c3.y)
    val r2 get() = Vector4dDual(c0.z, c1.z, c2.z, c3.z)
    val r3 get() = Vector4dDual(c0.w, c1.w, c2.w, c3.w)

    val transpose get() = Matrix4x4Dual(r0, r1, r2, r3)
    val trace get() = c0.x + c1.y + c2.z + c3.w
    val normFrobeniusSqr get() = c0.normSqr + c1.normSqr + c2.normSqr + c3.normSqr
    val normFrobenius get() = sqrt(normFrobeniusSqr)

    // Inlined for performance. We're already down a deep rabbit hole of performance issues
    // because of our design (and ⅅ, like this matrix), so at least let me have this one.
    val determinant
        get() =
            Matrix3x3Dual(c1.y, c2.y, c3.y, c1.z, c2.z, c3.z, c1.w, c2.w, c3.w).determinant * c0.x -
                Matrix3x3Dual(c1.x, c2.x, c3.x, c1.z, c2.z, c3.z, c1.w, c2.w, c3.w).determinant * c0.y +
                Matrix3x3Dual(c1.x, c2.x, c3.x, c1.y, c2.y, c3.y, c1.w, c2.w, c3.w).determinant * c0.z -
                Matrix3x3Dual(c1.x, c2.x, c3.x, c1.y, c2.y, c3.y, c1.z, c2.z, c3.z).determinant * c0.w

    fun eliminate(eliminateC: Int, eliminateR: Int): Matrix3x3Dual {
        val values = DualArray.ofZeros(3 * 3, size)

        var irActual = 0
        var icActual = 0

        for (ir in 0 until 4) {
            if (ir != eliminateR) {
                for (ic in 0 until 4) {
                    if (ic != eliminateC) {
                        values[(irActual * 3) + icActual++] = this[ic, ir]
                    }
                }

                irActual++
                icActual = 0
            }
        }

        return Matrix3x3Dual(values)
    }

    fun minor(c: Int, r: Int) = eliminate(c, r).determinant
    fun cofactor(c: Int, r: Int) = minor(c, r) * powi(-1, c + r).toDouble()
    val cofactorMatrix
        get() = Matrix4x4Dual(
            cofactor(0, 0), cofactor(1, 0), cofactor(2, 0), cofactor(3, 0),
            cofactor(0, 1), cofactor(1, 1), cofactor(2, 1), cofactor(3, 1),
            cofactor(0, 2), cofactor(1, 2), cofactor(2, 2), cofactor(3, 2),
            cofactor(0, 3), cofactor(1, 3), cofactor(2, 3), cofactor(3, 3)
        )
    val adjugateMatrix get() = cofactorMatrix.transpose

    operator fun not() = adjugateMatrix * (1.0 / determinant)
    operator fun times(scalar: Dual) = Matrix4x4Dual(c0 * scalar, c1 * scalar, c2 * scalar, c3 * scalar)
    operator fun times(constant: Double) = Matrix4x4Dual(c0 * constant, c1 * constant, c2 * constant, c3 * constant)
    operator fun times(v: Vector4dDual) = c0 * v.x + c1 * v.y + c2 * v.z + c3 * v.w
    operator fun times(v: Vector4d) = c0 * v.x + c1 * v.y + c2 * v.z + c3 * v.w
    operator fun times(m: Matrix4x4Dual) = Matrix4x4Dual(this * m.c0, this * m.c1, this * m.c2, this * m.c3)
    operator fun times(m: Matrix4x4) = Matrix4x4Dual(this * m.c0, this * m.c1, this * m.c2, this * m.c3)
    operator fun get(n: Int) = Matrix4x4(c0[n], c1[n], c2[n], c3[n])

    fun getColumn(c: Int) = when (c) {
        0 -> c0
        1 -> c1
        2 -> c2
        3 -> c3
        else -> error("Column $c out of bounds")
    }

    fun getRow(r: Int) = when (r) {
        0 -> r0
        1 -> r1
        2 -> r2
        3 -> r3
        else -> error("Row $r out of bounds")
    }

    operator fun get(c: Int, r: Int) = when (r) {
        0 -> getColumn(c).x
        1 -> getColumn(c).y
        2 -> getColumn(c).z
        3 -> getColumn(c).w
        else -> error("Row $r out of bounds")
    }

    companion object {
        fun const(v: Matrix4x4, n: Int = 1) = Matrix4x4Dual(
            Vector4dDual.const(v.c0, n),
            Vector4dDual.const(v.c1, n),
            Vector4dDual.const(v.c2, n),
            Vector4dDual.const(v.c3, n)
        )
    }
}

data class Vector2d(val x: Double, val y: Double) {
    constructor(value: Double) : this(value, value)

    infix fun o(b: Vector2d) = x * b.x + y * b.y
    val normSqr get() = this o this
    val norm get() = sqrt(normSqr)
    infix fun distTo(b: Vector2d) = (this - b).norm
    infix fun distToSqr(b: Vector2d) = (this - b).normSqr
    fun nz() = Vector2d(x.nz(), y.nz())
    fun normalized() = this / norm
    fun normalizedNz() = this.nz() / norm.nz()
    val perpLeft get() = Vector2d(-y, x)
    val perpRight get() = Vector2d(y, -x)

    fun approxEq(other: Vector2d, eps: Double = GEOMETRY_COMPARE_EPS) =
        x.approxEq(other.x, eps) && y.approxEq(other.y, eps)

    override fun toString() = "x=$x, y=$y"

    operator fun rangeTo(b: Vector2d) = this distTo b
    operator fun unaryPlus() = this
    operator fun unaryMinus() = Vector2d(-x, -y)
    operator fun plus(other: Vector2d) = Vector2d(x + other.x, y + other.y)
    operator fun minus(other: Vector2d) = Vector2d(x - other.x, y - other.y)
    operator fun times(other: Vector2d) = Vector2d(x * other.x, y * other.y)
    operator fun div(other: Vector2d) = Vector2d(x / other.x, y / other.y)
    operator fun times(scalar: Double) = Vector2d(x * scalar, y * scalar)
    operator fun div(scalar: Double) = Vector2d(x / scalar, y / scalar)

    operator fun compareTo(other: Vector2d) = this.normSqr.compareTo(other.normSqr)

    companion object {
        val zero = Vector2d(0.0, 0.0)
        val one = Vector2d(1.0, 1.0)
        val unitX = Vector2d(1.0, 0.0)
        val unitY = Vector2d(0.0, 1.0)

        fun lerp(a: Vector2d, b: Vector2d, t: Double) = Vector2d(
            lerp(a.x, b.x, t),
            lerp(a.y, b.y, t)
        )
    }
}

fun min(a: Vector2d, b: Vector2d) = Vector2d(min(a.x, b.x), min(a.y, b.y))
fun max(a: Vector2d, b: Vector2d) = Vector2d(max(a.x, b.x), max(a.y, b.y))

data class Vector2dDual(val x: Dual, val y: Dual) {
    constructor(value: Dual) : this(value, value)
    constructor(values: List<Vector2d>) : this(
        Dual(values.map { it.x }),
        Dual(values.map { it.y })
    )

    init {
        require(x.size == y.size) { "Dual X and Y must be of the same size" }
        require(x.size > 0) { "X and Y must not be empty" }
    }

    val size get() = x.size
    val isReal get() = size == 1
    infix fun o(b: Vector2dDual) = x * b.x + y * b.y
    val normSqr get() = this o this
    val norm get() = sqrt(normSqr)
    fun normalized() = this / norm
    val value get() = Vector2d(x.value, y.value)
    fun head(n: Int = 1) = Vector2dDual(x.head(n), y.head(n))
    fun tail(n: Int = 1) = Vector2dDual(x.tail(n), y.tail(n))

    override fun toString() = "x=$x, y=$y"

    operator fun unaryPlus() = this
    operator fun unaryMinus() = Vector2dDual(-x, -y)
    operator fun plus(other: Vector2dDual) = Vector2dDual(x + other.x, y + other.y)
    operator fun minus(other: Vector2dDual) = Vector2dDual(x - other.x, y - other.y)
    operator fun times(other: Vector2dDual) = Vector2dDual(x * other.x, y * other.y)
    operator fun div(other: Vector2dDual) = Vector2dDual(x / other.x, y / other.y)
    operator fun times(scalar: Dual) = Vector2dDual(x * scalar, y * scalar)
    operator fun div(scalar: Dual) = Vector2dDual(x / scalar, y / scalar)
    operator fun times(constant: Double) = Vector2dDual(x * constant, y * constant)
    operator fun div(constant: Double) = Vector2dDual(x / constant, y / constant)
    operator fun get(n: Int) = Vector2d(x[n], y[n])

    companion object {
        fun const(x: Double, y: Double, n: Int = 1) = Vector2dDual(Dual.const(x, n), Dual.const(y, n))
        fun const(value: Vector2d, n: Int = 1) = const(value.x, value.y, n)
        fun of(vararg values: Vector2d) = Vector2dDual(values.asList())
    }
}

data class Rotation2d(val re: Double, val im: Double) {
    fun log() = atan2(im, re)
    fun scaled(k: Double) = exp(log() * k)
    val inverse get() = Rotation2d(re, -im)
    val direction get() = Vector2d(re, im)

    fun approxEq(other: Rotation2d, eps: Double = GEOMETRY_COMPARE_EPS) = re.approxEq(other.re, eps) && im.approxEq(other.im, eps)

    override fun toString() = "${Math.toDegrees(log()).formatted()} deg"

    operator fun not() = this.inverse
    operator fun times(b: Rotation2d) = Rotation2d(this.re * b.re - this.im * b.im, this.re * b.im + this.im * b.re)
    operator fun times(r2: Vector2d) = Vector2d(this.re * r2.x - this.im * r2.y, this.im * r2.x + this.re * r2.y)
    operator fun div(b: Rotation2d) = b.inverse * this
    operator fun plus(incr: Double) = this * exp(incr)
    operator fun minus(b: Rotation2d) = (this / b).log()

    companion object {
        val identity = exp(0.0)

        fun exp(angleIncr: Double) = Rotation2d(cos(angleIncr), sin(angleIncr))

        fun dir(direction: Vector2d): Rotation2d {
            val dir = direction.normalized()

            return Rotation2d(dir.x, dir.y)
        }

        fun interpolate(r0: Rotation2d, r1: Rotation2d, t: Double) = exp(t * (r1 / r0).log()) * r0
    }
}

data class Rotation2dDual(val re: Dual, val im: Dual) {
    val value get() = Rotation2d(re.value, im.value)
    val angularVelocity get() = re * im.tail() - im * re.tail()
    val inverse get() = Rotation2dDual(re, -im)
    val direction get() = Vector2dDual(re, im)

    operator fun not() = this.inverse
    operator fun times(b: Rotation2dDual) =
        Rotation2dDual(this.re * b.re - this.im * b.im, this.re * b.im + this.im * b.re)

    operator fun times(b: Rotation2d) = Rotation2dDual(this.re * b.re - this.im * b.im, this.re * b.im + this.im * b.re)
    operator fun times(r2: Vector2dDual) =
        Vector2dDual(this.re * r2.x - this.im * r2.y, this.im * r2.x + this.re * r2.y)

    operator fun times(r2: Vector2d) = Vector2dDual(this.re * r2.x - this.im * r2.y, this.im * r2.x + this.re * r2.y)

    companion object {
        fun exp(angleIncr: Dual) = Rotation2dDual(cos(angleIncr), sin(angleIncr))
        fun const(value: Rotation2d, n: Int = 1) = Rotation2dDual(Dual.const(value.re, n), Dual.const(value.im, n))
        fun const(angleIncr: Double, n: Int = 1) = exp(Dual.const(angleIncr, n))
    }
}

data class Twist2dIncr(val trIncr: Vector2d, val rotIncr: Double) {
    constructor(xIncr: Double, yIncr: Double, rotIncr: Double) : this(Vector2d(xIncr, yIncr), rotIncr)
}

data class Twist2dIncrDual(val trIncr: Vector2dDual, val rotIncr: Dual) {
    constructor(xIncr: Dual, yIncr: Dual, rotIncr: Dual) : this(Vector2dDual(xIncr, yIncr), rotIncr)

    val value get() = Twist2dIncr(trIncr.value, rotIncr.value)
    val velocity get() = Twist2dDual(trIncr.tail(), rotIncr.tail())

    companion object {
        fun const(trIncr: Vector2d, rotIncr: Double, n: Int = 1) =
            Twist2dIncrDual(Vector2dDual.const(trIncr, n), Dual.const(rotIncr, n))
    }
}

data class Twist2d(val trVelocity: Vector2d, val rotVelocity: Double) {
    constructor(dx: Double, dy: Double, dTheta: Double) : this(Vector2d(dx, dy), dTheta)

    operator fun plus(other: Twist2d) = Twist2d(trVelocity + other.trVelocity, rotVelocity + other.rotVelocity)
    operator fun minus(other: Twist2d) = Twist2d(trVelocity - other.trVelocity, rotVelocity - other.rotVelocity)
    operator fun times(scalar: Double) = Twist2d(trVelocity * scalar, rotVelocity * scalar)
    operator fun div(scalar: Double) = Twist2d(trVelocity / scalar, rotVelocity / scalar)
}

data class Twist2dDual(val trVelocity: Vector2dDual, val rotVelocity: Dual) {
    constructor(dx: Dual, dy: Dual, dTheta: Dual) : this(Vector2dDual(dx, dy), dTheta)

    val value get() = Twist2d(trVelocity.value, rotVelocity.value)
    fun head(n: Int = 1) = Twist2dDual(trVelocity.head(n), rotVelocity.head(n))
    fun tail(n: Int = 1) = Twist2dDual(trVelocity.tail(n), rotVelocity.tail(n))

    operator fun plus(other: Twist2dDual) = Twist2dDual(trVelocity + other.trVelocity, rotVelocity + other.rotVelocity)
    operator fun minus(other: Twist2dDual) = Twist2dDual(trVelocity - other.trVelocity, rotVelocity - other.rotVelocity)

    companion object {
        fun const(value: Twist2d, n: Int = 1) =
            Twist2dDual(Vector2dDual.const(value.trVelocity, n), Dual.const(value.rotVelocity, n))
    }
}

data class Pose2d(val translation: Vector2d, val rotation: Rotation2d) {
    constructor(x: Double, y: Double, angle: Double) : this(Vector2d(x, y), Rotation2d.exp(angle))
    constructor(x: Double, y: Double) : this(x, y, 0.0)

    val inverse get() = Pose2d(rotation.inverse * -translation, rotation.inverse)

    fun log(): Twist2dIncr {
        val angle = rotation.log()
        val u = (0.5 * angle).nz()
        val ht = u / tan(u)

        return Twist2dIncr(
            Vector2d(
                ht * translation.x + u * translation.y,
                -u * translation.x + ht * translation.y
            ),
            angle
        )
    }

    fun approxEqs(other: Pose2d, eps: Double = GEOMETRY_COMPARE_EPS) =
        translation.approxEq(other.translation, eps) && rotation.approxEq(other.rotation, eps)

    override fun toString() = "$translation $rotation"

    operator fun not() = this.inverse
    operator fun times(b: Pose2d) = Pose2d(this.translation + this.rotation * b.translation, this.rotation * b.rotation)
    operator fun times(v: Vector2d) = this.translation + this.rotation * v
    operator fun div(b: Pose2d) = b.inverse * this
    operator fun plus(incr: Twist2dIncr) = this * exp(incr)
    operator fun minus(b: Pose2d) = (this / b).log()

    companion object {
        fun exp(incr: Twist2dIncr): Pose2d {
            val u = incr.rotIncr.nz() // Replaces series expansion (maybe)
            val c = 1.0 - cos(u)
            val s = sin(u)

            return Pose2d(
                Vector2d(
                    (s * incr.trIncr.x - c * incr.trIncr.y) / u,
                    (c * incr.trIncr.x + s * incr.trIncr.y) / u
                ),
                Rotation2d.exp(incr.rotIncr)
            )
        }
    }
}

data class Pose2dDual(val translation: Vector2dDual, val rotation: Rotation2dDual) {
    val inverse get() = Pose2dDual(rotation.inverse * -translation, rotation.inverse)
    val value get() = Pose2d(translation.value, rotation.value)
    val velocity get() = Twist2dDual(translation.tail(), rotation.angularVelocity)

    override fun toString() = "$translation $rotation"

    operator fun not() = this.inverse
    operator fun times(b: Pose2dDual) = Pose2dDual(this.translation + this.rotation * b.translation, this.rotation * b.rotation)

    operator fun times(b: Pose2d) = Pose2dDual(this.translation + this.rotation * b.translation, this.rotation * b.rotation)

    operator fun times(b: Twist2dDual) = Twist2dDual(rotation * b.trVelocity, b.rotVelocity)
    operator fun div(b: Pose2dDual) = b.inverse * this
    operator fun plus(incr: Twist2dIncr) = this * Pose2d.exp(incr)

    companion object {
        fun const(v: Pose2d, n: Int = 1) = Pose2dDual(Vector2dDual.const(v.translation, n), Rotation2dDual.const(v.rotation, n))
    }
}

data class Vector3di(val x: Int, val y: Int, val z: Int) {
    constructor(value: Int) : this(value, value, value)

    operator fun unaryPlus() = this
    operator fun unaryMinus() = Vector3di(-x, -y, -z)
    operator fun times(b: Int) = Vector3di(x * b, y * b, z * b)
    operator fun div(b: Int) = Vector3di(x / b, y / b, z / b)
    operator fun minus(b: Vector3di) = Vector3di(x - b.x, y - b.y, z - b.z)
    operator fun plus(b: Vector3di) = Vector3di(x + b.x, y + b.y, z + b.z)

    companion object {
        val zero = Vector3di(0, 0, 0)
        val one = Vector3di(1, 1, 1)
        val unitX = Vector3di(1, 0, 0)
        val unitY = Vector3di(0, 1, 0)
        val unitZ = Vector3di(0, 0, 1)

        fun manhattan(a: Vector3di, b: Vector3di) : Int {
            val dx = abs(a.x - b.x)
            val dy = abs(a.y - b.y)
            val dz = abs(a.z - b.z)

            return dx + dy + dz
        }
    }
}

data class Vector3d(val x: Double, val y: Double, val z: Double) {
    constructor(x: Int, y: Int, z: Int) : this(x.toDouble(), y.toDouble(), z.toDouble())
    constructor(value: Double) : this(value, value, value)

    infix fun dot(b: Vector3d) = x * b.x + y * b.y + z * b.z
    val normSqr get() = this dot this
    val norm get() = sqrt(normSqr)
    val isUnit get() = normSqr.approxEq(1.0, GEOMETRY_NORMALIZED_EPS)
    infix fun distanceTo(b: Vector3d) = (this - b).norm
    infix fun distanceToSqr(b: Vector3d) = (this - b).normSqr
    infix fun cosAngle(b: Vector3d) = (this dot b) / (this.norm * b.norm)
    infix fun angle(b: Vector3d) = acos((this cosAngle b).coerceIn(-1.0, 1.0))

    fun nz() = Vector3d(x.nz(), y.nz(), z.nz())
    fun normalized() = this / norm
    fun normalizedNz() = this.nz() / norm.nz()

    infix fun cross(b: Vector3d) = Vector3d(
        this.y * b.z - this.z * b.y,
        this.z * b.x - this.x * b.z,
        this.x * b.y - this.y * b.x
    )

    fun approxEq(other: Vector3d, eps: Double = GEOMETRY_COMPARE_EPS) = x.approxEq(other.x, eps) && y.approxEq(other.y, eps) && z.approxEq(other.z, eps)

    override fun toString() = "x=$x, y=$y, z=$z"

    operator fun not() = if(this.isUnit) {
        this
    } else if(this != zero) {
        this.normalized()
    } else {
        this
    }

    operator fun unaryPlus() = this
    operator fun unaryMinus() = Vector3d(-x, -y, -z)
    operator fun plus(b: Vector3d) = Vector3d(x + b.x, y + b.y, z + b.z)
    operator fun minus(b: Vector3d) = Vector3d(x - b.x, y - b.y, z - b.z)
    operator fun times(b: Vector3d) = Vector3d(x * b.x, y * b.y, z * b.z)
    operator fun div(b: Vector3d) = Vector3d(x / b.x, y / b.y, z / b.z)
    operator fun times(scalar: Double) = Vector3d(x * scalar, y * scalar, z * scalar)
    operator fun div(scalar: Double) = Vector3d(x / scalar, y / scalar, z / scalar)
    operator fun compareTo(other: Vector3d) = this.normSqr.compareTo(other.normSqr)

    fun projectOnPlane(n: Vector3d) = this - n * ((this dot n) / n.normSqr)
    fun projectOnVector(v: Vector3d) = if (this == zero || v == zero) zero else v * (this dot v) / v.normSqr

    fun frac() = Vector3d(frac(this.x), frac(this.y), frac(this.z))
    fun floor() = Vector3d(floor(this.x), floor(this.y), floor(this.z))
    fun ceil() = Vector3d(ceil(this.x), ceil(this.y), ceil(this.z))
    fun round() = Vector3d(round(this.x), round(this.y), round(this.z))
    fun floorInt() = Vector3di(floor(this.x).toInt(), floor(this.y).toInt(), floor(this.z).toInt())
    fun ceilInt() = Vector3di(ceil(this.x).toInt(), ceil(this.y).toInt(), ceil(this.z).toInt())
    fun roundInt() = Vector3di(round(this.x).toInt(), round(this.y).toInt(), round(this.z).toInt())
    fun floorBlockPos() = BlockPos(floor(this.x).toInt(), floor(this.y).toInt(), floor(this.z).toInt())
    fun ceilBlockPos() = BlockPos(ceil(this.x).toInt(), ceil(this.y).toInt(), ceil(this.z).toInt())
    fun roundBlockPos() = BlockPos(round(this.x).toInt(), round(this.y).toInt(), round(this.z).toInt())
    fun intCast() = Vector3di(x.toInt(), y.toInt(), z.toInt())

    fun perpendicular() : Vector3d {
        val (x, y, z) = if(!this.isUnit && this != zero) {
            this.normalized()
        }
        else {
            this
        }

        val result = if (abs(y + z) > GEOMETRY_NORMALIZED_EPS || abs(x) > GEOMETRY_NORMALIZED_EPS) {
            Vector3d(-y - z, x, x)
        }
        else {
            Vector3d(z, z, -x - y)
        }

        return result.normalized()
    }

    companion object {
        val zero = Vector3d(0.0, 0.0, 0.0)
        val one = Vector3d(1.0, 1.0, 1.0)
        val unitX = Vector3d(1.0, 0.0, 0.0)
        val unitY = Vector3d(0.0, 1.0, 0.0)
        val unitZ = Vector3d(0.0, 0.0, 1.0)

        fun lerp(from: Vector3d, to: Vector3d, t: Double) = Vector3d(
            lerp(from.x, to.x, t),
            lerp(from.y, to.y, t),
            lerp(from.z, to.z, t)
        )
    }
}

operator fun Vector3d.rangeTo(b: Vector3d) = this distanceTo b
infix fun Vector3d.o(other: Vector3d) = this dot other
infix fun Vector3d.x(other: Vector3d) = this cross other

fun min(a: Vector3d, b: Vector3d) = Vector3d(min(a.x, b.x), min(a.y, b.y), min(a.z, b.z))
fun max(a: Vector3d, b: Vector3d) = Vector3d(max(a.x, b.x), max(a.y, b.y), max(a.z, b.z))
infix fun Vector3d.directionTo(b: Vector3d) = (b - this).normalized()

data class Vector3dDual(val x: Dual, val y: Dual, val z: Dual) {
    constructor(value: Dual) : this(value, value, value)

    constructor(values: List<Vector3d>) : this(
        Dual(values.map { it.x }),
        Dual(values.map { it.y }),
        Dual(values.map { it.z })
    )

    init {
        require(x.size == y.size && y.size == z.size) { "Dual X, Y and Z must be of the same size" }
    }

    val size get() = x.size
    val isReal get() = size == 1
    infix fun dot(b: Vector3d) = x * b.x + y * b.y + z * b.z
    infix fun dot(b: Vector3dDual) = x * b.x + y * b.y + z * b.z
    val normSqr get() = this dot this
    val norm get() = sqrt(normSqr)
    fun normalized() = this / norm

    infix fun cross(b: Vector3d) = Vector3dDual(
        this.y * b.z - this.z * b.y,
        this.z * b.x - this.x * b.z,
        this.x * b.y - this.y * b.x
    )

    infix fun cross(b: Vector3dDual) = Vector3dDual(
        this.y * b.z - this.z * b.y,
        this.z * b.x - this.x * b.z,
        this.x * b.y - this.y * b.x
    )

    val value get() = Vector3d(x.value, y.value, z.value)
    fun head(n: Int = 1) = Vector3dDual(x.head(n), y.head(n), z.head(n))
    fun tail(n: Int = 1) = Vector3dDual(x.tail(n), y.tail(n), z.tail(n))

    fun projectOnPlane(n: Vector3dDual) = this - n * ((this dot n) / n.normSqr)
    fun projectOnPlane(n: Vector3d) = projectOnPlane(const(n, size))
    fun projectOnVector(v: Vector3dDual) = v * (this dot v) / v.normSqr
    fun projectOnVector(v: Vector3d) = projectOnVector(const(v, size))

    operator fun unaryPlus() = this
    operator fun unaryMinus() = Vector3dDual(-x, -y, -z)
    operator fun plus(other: Vector3dDual) = Vector3dDual(x + other.x, y + other.y, z + other.z)
    operator fun plus(other: Vector3d) = Vector3dDual(x + other.x, y + other.y, z + other.z)
    operator fun minus(other: Vector3dDual) = Vector3dDual(x - other.x, y - other.y, z - other.z)
    operator fun minus(other: Vector3d) = Vector3dDual(x - other.x, y - other.y, z - other.z)
    operator fun times(other: Vector3dDual) = Vector3dDual(x * other.x, y * other.y, z * other.z)
    operator fun times(other: Vector3d) = Vector3dDual(x * other.x, y * other.y, z * other.z)
    operator fun div(other: Vector3dDual) = Vector3dDual(x / other.x, y / other.y, z / other.z)
    operator fun div(other: Vector3d) = Vector3dDual(x / other.x, y / other.y, z / other.z)
    operator fun times(scalar: Dual) = Vector3dDual(x * scalar, y * scalar, z * scalar)
    operator fun div(scalar: Dual) = Vector3dDual(x / scalar, y / scalar, z / scalar)
    operator fun times(constant: Double) = Vector3dDual(x * constant, y * constant, z * constant)
    operator fun div(constant: Double) = Vector3dDual(x / constant, y / constant, z / constant)
    operator fun get(n: Int) = Vector3d(x[n], y[n], z[n])

    companion object {
        fun const(x: Double, y: Double, z: Double, n: Int = 1) = Vector3dDual(Dual.const(x, n), Dual.const(y, n), Dual.const(z, n))
        fun const(value: Vector3d, n: Int = 1) = const(value.x, value.y, value.z, n)
        fun of(vararg values: Vector3d) = Vector3dDual(values.asList())

        fun lerp(from: Vector3dDual, to: Vector3dDual, t: Dual) = Vector3dDual(
            lerp(from.x, to.x, t),
            lerp(from.y, to.y, t),
            lerp(from.z, to.z, t)
        )

        fun lerp(from: Vector3d, to: Vector3d, t: Dual) = lerp(
            const(from, t.size),
            const(to, t.size),
            t
        )
    }
}

fun Vector3dDual.o(other: Vector3d) = this dot other
fun Vector3dDual.o(other: Vector3dDual) = this dot other
fun Vector3dDual.x(other: Vector3d) = this cross other
fun Vector3dDual.x(other: Vector3dDual) = this cross other

data class Vector4d(val x: Double, val y: Double, val z: Double, val w: Double) {
    constructor(value: Double) : this(value, value, value, value)

    infix fun dot(b: Vector4d) = x * b.x + y * b.y + z * b.z + w * b.w
    val normSqr get() = this dot this
    val norm get() = sqrt(normSqr)
    infix fun distTo(b: Vector4d) = (this - b).norm
    infix fun distToSqr(b: Vector4d) = (this - b).normSqr
    infix fun cosAngle(b: Vector4d) = (this o b) / (this.norm * b.norm)
    infix fun angle(b: Vector4d) = acos((this cosAngle b).coerceIn(-1.0, 1.0))

    fun nz() = Vector4d(x.nz(), y.nz(), z.nz(), w.nz())
    fun normalized() = this / norm
    fun normalizedNz() = this.nz() / norm.nz()

    fun approxEq(other: Vector4d, eps: Double = GEOMETRY_COMPARE_EPS) = x.approxEq(other.x, eps) && y.approxEq(other.y, eps) && z.approxEq(other.z, eps) && w.approxEq(other.w, eps)

    override fun toString() = "x=$x, y=$y, z=$z, w=$w"

    operator fun rangeTo(b: Vector4d) = this distTo b
    operator fun unaryPlus() = this
    operator fun unaryMinus() = Vector4d(-x, -y, -z, -w)
    operator fun plus(b: Vector4d) = Vector4d(x + b.x, y + b.y, z + b.z, w + b.w)
    operator fun minus(b: Vector4d) = Vector4d(x - b.x, y - b.y, z - b.z, w - b.w)
    operator fun times(b: Vector4d) = Vector4d(x * b.x, y * b.y, z * b.z, w * b.w)
    operator fun div(b: Vector4d) = Vector4d(x / b.x, y / b.y, z / b.z, w / b.w)
    operator fun rem(b: Vector4d) = this angle b
    operator fun times(scalar: Double) = Vector4d(x * scalar, y * scalar, z * scalar, w * scalar)
    operator fun div(scalar: Double) = Vector4d(x / scalar, y / scalar, z / scalar, w / scalar)

    operator fun compareTo(other: Vector4d) = this.normSqr.compareTo(other.normSqr)

    companion object {
        val zero = Vector4d(0.0, 0.0, 0.0, 0.0)
        val one = Vector4d(1.0, 1.0, 1.0, 1.0)
        val unitX = Vector4d(1.0, 0.0, 0.0, 0.0)
        val unitY = Vector4d(0.0, 1.0, 0.0, 0.0)
        val unitZ = Vector4d(0.0, 0.0, 1.0, 0.0)
        val unitW = Vector4d(0.0, 0.0, 0.0, 1.0)

        fun lerp(a: Vector4d, b: Vector4d, t: Double) = Vector4d(
            lerp(a.x, b.x, t),
            lerp(a.y, b.y, t),
            lerp(a.z, b.z, t),
            lerp(a.w, b.w, t)
        )
    }
}

infix fun Vector4d.o(other: Vector4d) = this dot other

data class Vector4dDual(val x: Dual, val y: Dual, val z: Dual, val w: Dual) {
    constructor(value: Dual) : this(value, value, value, value)
    constructor(values: List<Vector4d>) : this(
        Dual(values.map { it.x }),
        Dual(values.map { it.y }),
        Dual(values.map { it.z }),
        Dual(values.map { it.w })
    )

    init {
        require(x.size == y.size && y.size == z.size && z.size == w.size) { "Dual X, Y, Z and W must be of the same size" }
    }

    val size get() = x.size
    val isReal get() = size == 1
    infix fun o(b: Vector4dDual) = x * b.x + y * b.y + z * b.z + w * b.w
    val normSqr get() = this o this
    val norm get() = sqrt(normSqr)
    val value get() = Vector4d(x.value, y.value, z.value, w.value)
    fun head(n: Int = 1) = Vector4dDual(x.head(n), y.head(n), z.head(n), w.head(n))
    fun tail(n: Int = 1) = Vector4dDual(x.tail(n), y.tail(n), z.tail(n), w.tail(n))

    fun normalized() = this / norm

    operator fun unaryPlus() = this
    operator fun unaryMinus() = Vector4dDual(-x, -y, -z, -w)
    operator fun plus(b: Vector4dDual) = Vector4dDual(x + b.x, y + b.y, z + b.z, w + b.w)
    operator fun plus(b: Vector4d) = Vector4dDual(x + b.x, y + b.y, z + b.z, w + b.w)
    operator fun minus(b: Vector4dDual) = Vector4dDual(x - b.x, y - b.y, z - b.z, w - b.w)
    operator fun minus(b: Vector4d) = Vector4dDual(x - b.x, y - b.y, z - b.z, w - b.w)
    operator fun times(b: Vector4dDual) = Vector4dDual(x * b.x, y * b.y, z * b.z, w * b.w)
    operator fun times(b: Vector4d) = Vector4dDual(x * b.x, y * b.y, z * b.z, w * b.w)
    operator fun div(b: Vector4dDual) = Vector4dDual(x / b.x, y / b.y, z / b.z, w / b.w)
    operator fun div(b: Vector4d) = Vector4dDual(x / b.x, y / b.y, z / b.z, w / b.w)
    operator fun times(scalar: Dual) = Vector4dDual(x * scalar, y * scalar, z * scalar, w * scalar)
    operator fun times(constant: Double) = Vector4dDual(x * constant, y * constant, z * constant, w * constant)
    operator fun div(scalar: Dual) = Vector4dDual(x / scalar, y / scalar, z / scalar, w / scalar)
    operator fun div(constant: Double) = Vector4dDual(x / constant, y / constant, z / constant, w / constant)
    operator fun get(n: Int) = Vector4d(x[n], y[n], z[n], w[n])

    companion object {
        fun const(x: Double, y: Double, z: Double, w: Double, n: Int = 1) =
            Vector4dDual(Dual.const(x, n), Dual.const(y, n), Dual.const(z, n), Dual.const(w, n))

        fun const(value: Vector4d, n: Int = 1) = const(value.x, value.y, value.z, value.w, n)
        fun of(vararg values: Vector4d) = Vector4dDual(values.asList())
    }
}

fun avg(a: Vector2d, b: Vector2d) = (a + b) / 2.0
fun avg(a: Vector2d, b: Vector2d, c: Vector2d) = (a + b + c) / 3.0
fun avg(a: Vector2d, b: Vector2d, c: Vector2d, d: Vector2d) = (a + b + c + d) / 4.0
fun avg(vectors: List<Vector2d>) = vectors.reduce { a, b -> a + b } / vectors.size.toDouble()
fun avg(a: Vector3d, b: Vector3d) = (a + b) / 2.0
fun avg(a: Vector3d, b: Vector3d, c: Vector3d) = (a + b + c) / 3.0
fun avg(a: Vector3d, b: Vector3d, c: Vector3d, d: Vector3d) = (a + b + c + d) / 4.0
fun avg(vectors: List<Vector3d>) = vectors.reduce { a, b -> a + b } / vectors.size.toDouble()
fun avg(a: Vector4d, b: Vector4d) = (a + b) / 2.0
fun avg(a: Vector4d, b: Vector4d, c: Vector4d) = (a + b + c) / 3.0
fun avg(a: Vector4d, b: Vector4d, c: Vector4d, d: Vector4d) = (a + b + c + d) / 4.0
fun avg(vectors: List<Vector4d>) = vectors.reduce { a, b -> a + b } / vectors.size.toDouble()

data class Rotation3d(val x: Double, val y: Double, val z: Double, val w: Double) {
    val normSqr get() = x * x + y * y + z * z + w * w
    val norm get() = sqrt(normSqr)
    fun normalized() = this / norm
    val xyz get() = Vector3d(x, y, z)
    val inverse get() = Rotation3d(-x, -y, -z, w) / normSqr

    fun log(): Vector3d {
        val n = xyz.norm
        return xyz * if (n < 1e-9) 2.0 / w - 2.0 / 3.0 * n * n / (w * w * w)
        else 2.0 * atan2(n * snz(w), w * snz(w)) / n
    }

    operator fun times(scalar: Double) = Rotation3d(x * scalar, y * scalar, z * scalar, w * scalar)
    operator fun div(scalar: Double) = Rotation3d(x / scalar, y / scalar, z / scalar, w / scalar)
    operator fun unaryPlus() = this
    operator fun unaryMinus() = Rotation3d(-x, -y, -z, -w)
    operator fun not() = this.inverse

    operator fun times(b: Rotation3d) =
        Rotation3d(
            x * b.w + b.x * w + (y * b.z - z * b.y), // Could also use FMADD
            y * b.w + b.y * w + (z * b.x - x * b.z),
            z * b.w + b.z * w + (x * b.y - y * b.x),
            w * b.w - (x * b.x + y * b.y + z * b.z)
        )

    operator fun times(value: Vector3d): Vector3d {
        val `2wx` = 2.0 * (w * x)
        val `2wy` = 2.0 * (w * y)
        val `2wz` = 2.0 * (w * z)
        val `2xx` = 2.0 * (x * x)
        val `2xy` = 2.0 * (x * y)
        val `2xz` = 2.0 * (x * z)
        val `2yy` = 2.0 * (y * y)
        val `2yz` = 2.0 * (y * z)
        val `2zz` = 2.0 * (z * z)

        return Vector3d(
            (value.x * (1.0 - `2yy` - `2zz`) + value.y * (`2xy` - `2wz`) + value.z * (`2xz` + `2wy`)),
            (value.x * (`2xy` + `2wz`) + value.y * (1.0 - `2xx` - `2zz`) + value.z * (`2yz` - `2wx`)),
            (value.x * (`2xz` - `2wy`) + value.y * (`2yz` + `2wx`) + value.z * (1.0 - `2xx` - `2yy`))
        )
    }

    operator fun div(b: Rotation3d) = b.inverse * this
    operator fun plus(w: Vector3d) = this * exp(w)
    operator fun minus(b: Rotation3d) = (this / b).log()

    operator fun invoke() : Matrix3x3 {
        val `2xx` = 2.0 * (x * x)
        val `2yy` = 2.0 * (y * y)
        val `2zz` = 2.0 * (z * z)
        val `2xy` = 2.0 * (x * y)
        val `2xz` = 2.0 * (x * z)
        val `2xw` = 2.0 * (x * w)
        val `2yz` = 2.0 * (y * z)
        val `2yw` = 2.0 * (y * w)
        val `2zw` = 2.0 * (z * w)

        return Matrix3x3(
            1.0 - `2yy` - `2zz`, `2xy` - `2zw`, `2xz` + `2yw`,
            `2xy` + `2zw`, 1.0 - `2xx` - `2zz`, `2yz` - `2xw`,
            `2xz` - `2yw`, `2yz` + `2xw`, 1.0 - `2xx` - `2yy`
        )
    }

    operator fun invoke(k: Double) = exp(log() * k)

    fun approxEq(other: Rotation3d, eps: Double = GEOMETRY_COMPARE_EPS) = x.approxEq(other.x, eps) && y.approxEq(other.y, eps) && z.approxEq(other.z, eps) && w.approxEq(other.w, eps)

    companion object {
        val identity = Rotation3d(0.0, 0.0, 0.0, 1.0)

        fun exp(w: Vector3d): Rotation3d {
            val t = w.norm

            if (t == 0.0) {
                return identity
            }

            val axis = w / t
            val s = sin(t / 2.0)

            return Rotation3d(axis.x * s, axis.y * s, axis.z * s, cos(t / 2.0))
        }

        fun fromAxisAngle(axis: Vector3d, angle: Double) = exp(axis.normalizedNz() * angle)

        fun fromRotationMatrix(matrix: Matrix3x3): Rotation3d {
            val c0 = matrix.c0
            val c1 = matrix.c1
            val c2 = matrix.c2

            val m00 = c0.x
            val m01 = c0.y
            val m02 = c0.z
            val m10 = c1.x
            val m11 = c1.y
            val m12 = c1.z
            val m20 = c2.x
            val m21 = c2.y
            val m22 = c2.z

            val t: Double
            val q: Rotation3d

            if (m22 < 0) {
                if (m00 > m11) {
                    t = 1.0 + m00 - m11 - m22
                    q = Rotation3d(
                        t,
                        m01 + m10,
                        m20 + m02,
                        m12 - m21
                    )
                } else {
                    t = 1.0 - m00 + m11 - m22
                    q = Rotation3d(
                        m01 + m10,
                        t,
                        m12 + m21,
                        m20 - m02
                    )
                }
            } else {
                if (m00 < -m11) {
                    t = 1.0 - m00 - m11 + m22
                    q = Rotation3d(
                        m20 + m02,
                        m12 + m21,
                        t,
                        m01 - m10
                    )
                } else {
                    t = 1.0 + m00 + m11 + m22
                    q = Rotation3d(
                        m12 - m21,
                        m20 - m02,
                        m01 - m10,
                        t
                    )
                }
            }

            return q * 0.5 / sqrt(t)
        }

        fun alg(w: Vector3d) = Matrix3x3(
            0.0, -w.z, w.y,
            w.z, 0.0, -w.x,
            -w.y, w.x, 0.0
        )

        fun interpolate(r0: Rotation3d, r1: Rotation3d, t: Double) = exp((r1 / r0).log() * t) * r0
    }
}

data class Rotation3dDual(val x: Dual, val y: Dual, val z: Dual, val w: Dual) {
    init {
        require(x.size == y.size && y.size == z.size && z.size == w.size)
    }

    val size get() = x.size

    val xyz get() = Vector3dDual(x, y, z)
    val normSqr get() = x * x + y * y + z * z + w * w
    val norm get() = sqrt(normSqr)
    fun normalized() = this / norm
    val inverse get() = Rotation3dDual(-x, -y, -z, w) / normSqr
    val value get() = Rotation3d(x.value, y.value, z.value, w.value)

    val angularVelocity: Vector3dDual
        get() {
            val n = xyz.norm
            val im = n * snz(w.value)
            val re = w * snz(w.value)
            return xyz * (2.0 * (re * im.tail() - im * re.tail()) / n)
        }

    fun head(n: Int = 1) = Rotation3dDual(x.head(n), y.head(n), z.head(n), w.head(n))
    fun tail(n: Int = 1) = Rotation3dDual(x.tail(n), y.tail(n), z.tail(n), w.tail(n))

    operator fun div(scalar: Dual) = Rotation3dDual(x / scalar, y / scalar, z / scalar, w / scalar)

    operator fun unaryPlus() = this
    operator fun unaryMinus() = Rotation3dDual(-x, -y, -z, -w)
    operator fun not() = this.inverse
    operator fun times(b: Rotation3dDual) =
        Rotation3dDual(
            x * b.w + b.x * w + (y * b.z - z * b.y),
            y * b.w + b.y * w + (z * b.x - x * b.z),
            z * b.w + b.z * w + (x * b.y - y * b.x),
            w * b.w - (x * b.x + y * b.y + z * b.z)
        )

    operator fun times(value: Vector3dDual): Vector3dDual {
        val a = 2.0 * (w * x)
        val b = 2.0 * (w * y)
        val c = 2.0 * (w * z)
        val d = 2.0 * (x * x)
        val e = 2.0 * (x * y)
        val f = 2.0 * (x * z)
        val g = 2.0 * (y * y)
        val h = 2.0 * (y * z)
        val i = 2.0 * (z * z)

        return Vector3dDual(
            (value.x * (1.0 - g - i) + value.y * (e - c) + value.z * (f + b)),
            (value.x * (e + c) + value.y * (1.0 - d - i) + value.z * (h - a)),
            (value.x * (f - b) + value.y * (h + a) + value.z * (1.0 - d - g))
        )
    }

    operator fun div(b: Rotation3dDual) = b.inverse * this

    operator fun invoke() : Matrix3x3Dual {
        val `2xx` = 2.0 * (x * x)
        val `2yy` = 2.0 * (y * y)
        val `2zz` = 2.0 * (z * z)
        val `2xy` = 2.0 * (x * y)
        val `2xz` = 2.0 * (x * z)
        val `2xw` = 2.0 * (x * w)
        val `2yz` = 2.0 * (y * z)
        val `2yw` = 2.0 * (y * w)
        val `2zw` = 2.0 * (z * w)

        return Matrix3x3Dual(
            1.0 - `2yy` - `2zz`, `2xy` - `2zw`, `2xz` + `2yw`,
            `2xy` + `2zw`, 1.0 - `2xx` - `2zz`, `2yz` - `2xw`,
            `2xz` - `2yw`, `2yz` + `2xw`, 1.0 - `2xx` - `2yy`
        )
    }

    companion object {
        fun fromAxisAngle(axis: Vector3dDual, angle: Dual) = exp(axis.normalized() * angle)

        fun exp(w: Vector3dDual): Rotation3dDual {
            val t = w.norm
            val axis = w.normalized()
            val s = sin(t / 2.0)

            return Rotation3dDual(axis.x * s, axis.y * s, axis.z * s, cos(t / 2.0))
        }
    }
}

data class Twist3d(val trVelocity: Vector3d, val rotVelocity: Vector3d)

data class Twist3dDual(val trVelocity: Vector3dDual, val rotVelocity: Vector3dDual) {
    val value get() = Twist3d(trVelocity.value, rotVelocity.value)
    fun head(n: Int = 1) = Twist3dDual(trVelocity.head(n), rotVelocity.head(n))
    fun tail(n: Int = 1) = Twist3dDual(trVelocity.tail(n), rotVelocity.tail(n))
}

data class Twist3dIncr(val trIncr: Vector3d, val rotIncr: Vector3d)

data class Twist3dIncrDual(val trIncr: Vector3dDual, val rotIncr: Vector3dDual) {
    val value get() = Twist3dIncr(trIncr.value, rotIncr.value)
    val velocity get() = Twist3dDual(trIncr.tail(), rotIncr.tail())
}

data class Pose3d(val translation: Vector3d, val rotation: Rotation3d) {
    val inverse get() = Pose3d(rotation.inverse * -translation, rotation.inverse)

    fun log(): Twist3dIncr {
        val w = rotation.log()
        val wx = Rotation3d.alg(w)
        val t = w.norm

        val c = if (abs(t) < 1e-7) {
            1 / 12.0 + t * t / 720.0 + t * t * (t * t) / 30240.0
        } else {
            (1.0 - sin(t) / t / (2.0 * ((1 - cos(t)) / (t * t)))) / (t * t)
        }

        return Twist3dIncr(
            (Matrix3x3.identity - (wx * 0.5) + ((wx * wx) * c)) * translation,
            w
        )
    }

    operator fun not() = this.inverse
    operator fun times(b: Pose3d) = Pose3d(this.translation + this.rotation * b.translation, this.rotation * b.rotation)
    operator fun times(v: Vector3d) = this.translation + this.rotation * v
    operator fun div(b: Pose3d) = b.inverse * this
    operator fun plus(incr: Twist3dIncr) = this * exp(incr)
    operator fun minus(b: Pose3d) = (this / b).log()

    fun approxEq(other: Pose3d, eps: Double = GEOMETRY_COMPARE_EPS) =
        translation.approxEq(other.translation) && rotation.approxEq(other.rotation)

    operator fun invoke() = rotation().let { (rc0, rc1, rc2) ->
        Matrix4x4(
            rc0.x, rc1.x, rc2.x, translation.x,
            rc0.y, rc1.y, rc2.y, translation.y,
            rc0.z, rc1.z, rc2.z, translation.z,
            0.0, 0.0, 0.0, 1.0
        )
    }

    companion object {
        fun exp(incr: Twist3dIncr): Pose3d {
            val t = incr.rotIncr.norm

            val b: Double
            val c: Double

            if (abs(t) < 1e-7) {
                b = 1.0 / 2.0 - t * t / 24.0 + t * t * (t * t) / 720.0
                c = 1.0 / 6.0 - t * t / 120.0 + t * t * (t * t) / 5040.0
            } else {
                b = (1.0 - cos(t)) / (t * t)
                c = (1.0 - sin(t) / t) / (t * t)
            }

            val wx = Rotation3d.alg(incr.rotIncr)

            return Pose3d(
                (Matrix3x3.identity + wx * b + (wx * wx) * c) * incr.trIncr,
                Rotation3d.exp(incr.rotIncr)
            )
        }
    }
}

data class Pose3dDual(val translation: Vector3dDual, val rotation: Rotation3dDual) {
    init {
        require(translation.size == rotation.size)
    }

    val size get() = translation.size

    val inverse get() = Pose3dDual(rotation.inverse * -translation, rotation.inverse)
    val value get() = Pose3d(translation.value, rotation.value)
    val velocity get() = Twist3dDual(translation.tail(), rotation.angularVelocity)

    operator fun not() = this.inverse
    operator fun times(b: Pose3dDual) =
        Pose3dDual(this.translation + this.rotation * b.translation, this.rotation * b.rotation)

    operator fun times(v: Vector3dDual) = this.translation + this.rotation * v
    operator fun div(b: Pose3dDual) = b.inverse * this
    operator fun invoke() = rotation().let { (rc0, rc1, rc2) ->
        val `0` = Dual.const(0.0, size)
        val `1` = Dual.const(1.0, size)

        Matrix4x4Dual(
            rc0.x, rc1.x, rc2.x, translation.x,
            rc0.y, rc1.y, rc2.y, translation.y,
            rc0.z, rc1.z, rc2.z, translation.z,
            `0`, `0`, `0`, `1`
        )
    }
}

data class CoordinateSystem(val transform: Matrix3x3) {
    init {
        require(transform.isOrthogonal)
    }

    operator fun rangeTo(b: CoordinateSystem) = b.transform * !this.transform

    companion object {
        val rfu = CoordinateSystem(Matrix3x3.identity)

        val minecraft = CoordinateSystem(
            Matrix3x3(
                Vector3d.unitX,
                Vector3d.unitZ,
                Vector3d.unitY
            )
        )
    }
}

enum class ContainmentMode {
    Disjoint,
    Intersected,
    ContainedFully
}

interface BoundingBox<Self : BoundingBox<Self>> {
    val capacity: Double
    val surface: Double

    infix fun u(b: Self): Self
    infix fun intersectsWith(b: Self): Boolean
    fun inflatedBy(percent: Double): Self
    fun evaluateContainment(b: Self): ContainmentMode
}

data class BoundingBox2d(val min: Vector2d, val max: Vector2d) : BoundingBox<BoundingBox2d> {
    constructor(minX: Double, minY: Double, width: Double, height: Double) : this(
        Vector2d(minX, minY),
        Vector2d(minX + width, minY + height)
    )

    val center = (min + max) / 2.0
    val width get() = max.x - min.x
    val height get() = max.y - min.y
    override val capacity get() = width * height
    override val surface get() = width * height

    infix fun intersectionWith(b: BoundingBox2d): BoundingBox2d {
        val result = BoundingBox2d(
            Vector2d(max(this.min.x, b.min.x), max(this.min.y, b.min.y)),
            Vector2d(java.lang.Double.min(this.max.x, b.max.x), java.lang.Double.min(this.max.y, b.max.y))
        )

        return if (result.max.x < result.min.x || result.max.y < result.min.y) {
            zero
        } else result
    }

    override infix fun intersectsWith(b: BoundingBox2d) =
        b.min.x < this.max.x && this.min.x < b.max.x &&
            b.min.y < this.max.y && this.min.y < b.max.y

    override infix fun u(b: BoundingBox2d) = BoundingBox2d(
        Vector2d(java.lang.Double.min(this.min.x, b.min.x), java.lang.Double.min(this.min.y, b.min.y)),
        Vector2d(max(this.max.x, b.max.x), max(this.max.y, b.max.y))
    )

    fun inflated(amountX: Double, amountY: Double) = BoundingBox2d(
        Vector2d(this.min.x - amountX, this.min.y - amountY),
        Vector2d(this.max.x + amountX, this.max.y + amountY)
    )

    fun inflated(amount: Vector2d) = inflated(amount.x, amount.y)
    fun inflated(amount: Double) = inflated(amount, amount)
    override fun inflatedBy(percent: Double) = inflated(width * percent, height * percent)

    override fun evaluateContainment(b: BoundingBox2d): ContainmentMode {
        if (!this.intersectsWith(b)) {
            return ContainmentMode.Disjoint
        }

        val test = this.min.x > b.min.x || b.max.x > this.max.x ||
            this.min.y > b.min.y || b.max.y > this.max.y

        return if (test) ContainmentMode.Intersected
        else ContainmentMode.ContainedFully
    }

    fun contains(point: Vector2d) =
        this.min.x < point.x && this.min.y < point.y &&
            this.max.x > point.x && this.max.y > point.y

    companion object {
        val zero = BoundingBox2d(Vector2d.zero, Vector2d.zero)

        fun centerSize(center: Vector2d, size: Vector2d) = BoundingBox2d(
            center - size / 2.0,
            size
        )

        fun centerSize(center: Vector2d, size: Double) = BoundingBox2d(
            center.x - size / 2.0,
            center.y - size / 2.0,
            size, size
        )
    }
}

data class BoundingBox3d(val min: Vector3d, val max: Vector3d) : BoundingBox<BoundingBox3d> {
    constructor(minX: Double, minY: Double, minZ: Double, width: Double, height: Double, depth: Double) : this(
        Vector3d(minX, minY, minZ),
        Vector3d(minX + width, minY + height, minZ + depth)
    )

    constructor(sphere: BoundingSphere) : this(
        sphere.origin - Vector3d(sphere.radius),
        sphere.origin + Vector3d(sphere.radius)
    )

    val center = (min + max) / 2.0
    val width get() = max.x - min.x
    val height get() = max.y - min.y
    val depth get() = max.z - min.z
    override val capacity get() = width * height * depth
    override val surface get() = 2.0 * (width * height + depth * height + width * depth)

    infix fun intersectionWith(b: BoundingBox3d): BoundingBox3d {
        val result = BoundingBox3d(
            Vector3d(
                max(this.min.x, b.min.x),
                max(this.min.y, b.min.y),
                max(this.min.z, b.min.z)
            ),
            Vector3d(
                min(this.max.x, b.max.x),
                min(this.max.y, b.max.y),
                min(this.max.z, b.max.z)
            )
        )

        return if (result.max.x < result.min.x || result.max.y < result.min.y || result.max.z < result.min.z) {
            zero
        } else result
    }

    override infix fun intersectsWith(b: BoundingBox3d) =
        b.min.x < this.max.x && this.min.x < b.max.x &&
            b.min.y < this.max.y && this.min.y < b.max.y &&
            b.min.z < this.max.z && this.min.z < b.max.z

    override infix fun u(b: BoundingBox3d) = BoundingBox3d(
        Vector3d(
            java.lang.Double.min(this.min.x, b.min.x),
            java.lang.Double.min(this.min.y, b.min.y),
            java.lang.Double.min(this.min.z, b.min.z)
        ),
        Vector3d(max(this.max.x, b.max.x), max(this.max.y, b.max.y), max(this.max.z, b.max.z))
    )

    fun inflated(amountX: Double, amountY: Double, amountZ: Double) = BoundingBox3d(
        Vector3d(this.min.x - amountX, this.min.y - amountY, this.min.z - amountZ),
        Vector3d(this.max.x + amountX, this.max.y + amountY, this.max.z + amountZ)
    )

    fun inflated(amount: Vector3d) = inflated(amount.x, amount.y, amount.z)
    fun inflated(amount: Double) = inflated(amount, amount, amount)
    override fun inflatedBy(percent: Double) = inflated(width * percent, height * percent, depth * percent)

    override fun evaluateContainment(b: BoundingBox3d): ContainmentMode {
        if (!this.intersectsWith(b)) {
            return ContainmentMode.Disjoint
        }

        val test = this.min.x > b.min.x || b.max.x > this.max.x ||
            this.min.y > b.min.y || b.max.y > this.max.y ||
            this.min.z > b.min.z || b.max.z > this.max.z

        return if (test) ContainmentMode.Intersected
        else ContainmentMode.ContainedFully
    }

    fun contains(point: Vector3d) =
        this.min.x < point.x && this.min.y < point.y && this.min.z < point.z &&
            this.max.x > point.x && this.max.y > point.y && this.max.z > point.z

    companion object {
        val zero = BoundingBox3d(Vector3d.zero, Vector3d.zero)

        fun centerSize(center: Vector3d, size: Vector3d) = BoundingBox3d(
            center - size / 2.0,
            size
        )

        fun centerSize(center: Vector3d, size: Double) = BoundingBox3d(
            center.x - size / 2.0,
            center.y - size / 2.0,
            center.z - size / 2.0,
            size, size, size
        )
    }
}

data class IntersectionParametricBi(
    val entry: Double,
    val exit: Double,
)

data class Ray3d(val origin: Vector3d, val direction: Vector3d) {
    init {
        require(direction.isUnit) {
            "Cannot create ray with non-unit $direction (${direction.norm})"
        }
    }

    fun evaluate(t: Double) = origin + direction * t

    infix fun intersectionWith(b: BoundingBox3d): IntersectionParametricBi? {
        var tmin = Double.NEGATIVE_INFINITY
        var tmax = Double.POSITIVE_INFINITY

        if (this.direction.x != 0.0) {
            val tx1 = (b.min.x - this.origin.x) / this.direction.x
            val tx2 = (b.max.x - this.origin.x) / this.direction.x
            tmin = max(tmin, min(tx1, tx2))
            tmax = min(tmax, max(tx1, tx2))
        }

        if (this.direction.y != 0.0) {
            val ty1 = (b.min.y - this.origin.y) / this.direction.y
            val ty2 = (b.max.y - this.origin.y) / this.direction.y
            tmin = max(tmin, min(ty1, ty2))
            tmax = min(tmax, max(ty1, ty2))
        }

        if (this.direction.z != 0.0) {
            val tz1 = (b.min.z - this.origin.z) / this.direction.z
            val tz2 = (b.max.z - this.origin.z) / this.direction.z
            tmin = max(tmin, min(tz1, tz2))
            tmax = min(tmax, max(tz1, tz2))
        }

        return if (tmax >= tmin) IntersectionParametricBi(tmin, tmax)
        else null
    }

    companion object {
        fun fromSourceAndDestination(source: Vector3d, destination: Vector3d) = Ray3d(source, source directionTo destination)
    }
}

data class BoundingSphere(val origin: Vector3d, val radius: Double) {
    constructor(box: BoundingBox3d) : this(box.center, box.center..box.max)

    infix fun u(b: BoundingSphere): BoundingSphere {
        val dxAB = b.origin - this.origin

        val rdxAB = dxAB.norm

        if (radius + b.radius >= rdxAB) {
            if (radius - b.radius >= rdxAB) {
                return this
            } else if (b.radius - radius >= rdxAB) {
                return b
            }
        }

        val nxAB = dxAB / rdxAB
        val a = min(-radius, rdxAB - b.radius)
        val r = (max(radius, rdxAB + b.radius) - a) / 2.0

        return BoundingSphere(this.origin + nxAB * (r + a), r)
    }

    fun contains(point: Vector3d) = origin..point < radius
}

fun bresenham(
    startX: Int,
    startY: Int,
    startZ: Int,
    endX: Int,
    endY: Int,
    endZ: Int,
    user: (Int, Int, Int) -> Boolean,
) {
    var x = startX
    var y = startY
    var z = startZ

    if (!user(x, y, z)) {
        return
    }

    val dx = abs(endX - x)
    val dy = abs(endY - y)
    val dz = abs(endZ - z)
    val sx = nsnzi(dx)
    val sy = nsnzi(dy)
    val sz = nsnzi(dz)

    if (dx >= dy && dx >= dz) {
        var a = 2 * dy - dx
        var b = 2 * dz - dx

        while (x != endX) {
            x += sx

            if (a >= 0) {
                y += sy
                a -= 2 * dx
            }

            if (b >= 0) {
                z += sz
                b -= 2 * dx
            }

            a += 2 * dy
            b += 2 * dz

            if (!user(x, y, z)) {
                return
            }
        }
    } else if (dy >= dx && dy >= dz) {
        var a = 2 * dx - dy
        var b = 2 * dz - dy

        while (y != endY) {
            y += sy

            if (a >= 0) {
                x += sx
                a -= 2 * dy
            }

            if (b >= 0) {
                z += sz
                b -= 2 * dy
            }

            a += 2 * dx
            b += 2 * dz

            if (!user(x, y, z)) {
                return
            }
        }
    } else {
        var a = 2 * dy - dz
        var b = 2 * dx - dz

        while (z != endZ) {
            z += sz

            if (a >= 0) {
                y += sy
                a -= 2 * dz
            }

            if (b >= 0) {
                x += sx
                b -= 2 * dz
            }

            a += 2 * dy
            b += 2 * dx

            if (!user(x, y, z)) {
                return
            }
        }
    }
}

fun dda(ray: Ray3d, withSource: Boolean = true, user: (Int, Int, Int) -> Boolean) {
    var x = floor(ray.origin.x).toInt()
    var y = floor(ray.origin.y).toInt()
    var z = floor(ray.origin.z).toInt()

    if (withSource) {
        if (!user(x, y, z)) {
            return
        }
    }

    val sx = snzi(ray.direction.x)
    val sy = snzi(ray.direction.y)
    val sz = snzi(ray.direction.z)

    val nextBX = x + sx + if (sx.sign == -1) 1
    else 0
    val nextBY = y + sy + if (sy.sign == -1) 1
    else 0
    val nextBZ = z + sz + if (sz.sign == -1) 1
    else 0

    var tmx = if (ray.direction.x != 0.0) (nextBX - ray.origin.x) / ray.direction.x else Double.MAX_VALUE
    var tmy = if (ray.direction.y != 0.0) (nextBY - ray.origin.y) / ray.direction.y else Double.MAX_VALUE
    var tmz = if (ray.direction.z != 0.0) (nextBZ - ray.origin.z) / ray.direction.z else Double.MAX_VALUE
    val tdx = if (ray.direction.x != 0.0) 1.0 / ray.direction.x * sx else Double.MAX_VALUE
    val tdy = if (ray.direction.y != 0.0) 1.0 / ray.direction.y * sy else Double.MAX_VALUE
    val tdz = if (ray.direction.z != 0.0) 1.0 / ray.direction.z * sz else Double.MAX_VALUE

    while (true) {
        if (tmx < tmy) {
            if (tmx < tmz) {
                x += sx
                tmx += tdx
            } else {
                z += sz
                tmz += tdz
            }
        } else {
            if (tmy < tmz) {
                y += sy
                tmy += tdy
            } else {
                z += sz
                tmz += tdz
            }
        }

        if (!user(x, y, z)) {
            return
        }
    }
}

@JvmInline
value class BlockPosInt(val value: Int) {
    constructor(x: Int, y: Int, z: Int) : this(pack(x, y, z))

    val x get() = unpackX(value)
    val y get() = unpackY(value)
    val z get() = unpackZ(value)

    val isZero get() = value == 0

    fun toBlockPos() = unpackBlockPos(value)

    operator fun not() = value

    companion object {
        const val MAX_VALUE = +511
        const val MIN_VALUE = -511
        const val SIGN_BIT = 1 shl 9
        const val SIGN_TABLE = (-1L shl 32) or 1

        inline fun packMember(value: Int): Int {
            val bit = value shr 31
            val mod = (value + bit) xor bit
            return ((value ushr 31) shl 9) or mod
        }

        inline fun unpackMember(value: Int): Int {
            val idx = (value and SIGN_BIT) ushr 4
            val sign = (SIGN_TABLE ushr idx).toInt()
            return sign * (value and MAX_VALUE)
        }

        inline fun pack(x: Int, y: Int, z: Int): Int {
            if(x < MIN_VALUE || x > MAX_VALUE || y < MIN_VALUE || y > MAX_VALUE || z < MIN_VALUE || z > MAX_VALUE) {
                error("XYZ $x $y $z are out of bounds")
            }

            val i = packMember(x)
            val j = packMember(y) shl 10
            val k = packMember(z) shl 20

            return i or j or k
        }

        inline fun pack(blockPos: BlockPos) = pack(blockPos.x, blockPos.y, blockPos.z)

        inline fun pack(vector: Vector3di) = pack(vector.x, vector.y, vector.z)

        inline fun of(x: Int, y: Int, z: Int) = BlockPosInt(pack(x, y, z))

        inline fun of(blockPos: BlockPos) = BlockPosInt(pack(blockPos.x, blockPos.y, blockPos.z))

        inline fun unpackX(v: Int) = unpackMember(v)

        inline fun unpackY(v: Int) = unpackMember(v shr 10)

        inline fun unpackZ(v: Int) = unpackMember(v shr 20)

        inline fun unpackBlockPos(value: Int) = BlockPos(unpackX(value), unpackY(value), unpackZ(value))
    }
}

/**
 * Computes the surface area of a cylinder with specified [length] and [radius].
 * */
fun cylinderSurfaceArea(length: Double, radius: Double): Double {
    return 2 * PI * radius * length + 2 * PI * radius * radius
}
