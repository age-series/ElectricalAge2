package org.eln2.mc.mathematics

import com.mojang.math.Vector4f
import net.minecraft.core.Vec3i
import net.minecraft.world.phys.Vec3
import kotlin.math.*

object Geometry {
    /**
     * Computes the surface area of a cylinder with specified [length] and [radius].
     * */
    fun cylinderSurfaceArea(length: Double, radius: Double): Double {
        return 2 * PI * radius * length + 2 * PI * radius * radius
    }

    /**
     * Computes the surface area of a circle.
     * */
    fun circleSurfaceArea(radius: Double): Double {
        return PI * radius * radius
    }

    fun angleNorm(angle: Double, range: Double): Double {
        return angle % range
    }

    /**
     * Normalizes an [angle] to range from 0-360 degrees.
     * */
    fun angleNormDeg(angle: Double): Double = angleNorm(angle, 360.0)

    /**
     * Normalizes an [angle] to range from 0-2π
     * */
    fun angleNorm(angle: Double): Double = angleNorm(angle, PI * 2.0)
}

//#region Interpolations
fun lerp(from: Double, to: Double, factor: Double): Double {
    return (1.0 - factor) * from + factor * to
}

fun lerp(from: Float, to: Float, factor: Float): Float {
    return (1f - factor) * from + factor * to
}

/**
 * Computes the [base] with the specified power [exponent] efficiently.
 * */
fun powI(base: Int, exponent: Int): Int {
    var b = base
    var exp = exponent
    var result = 1

    while (true) {
        if (exp and 1 != 0) {
            result *= b
        }
        exp = exp shr 1
        if (exp == 0) {
            break
        }
        b *= b
    }

    return result
}

/**
 * Computes 2 to the specified power [exponent]. Calls [powI] with a base of 2.
 * */
fun pow2I(exponent: Int): Int {
    return powI(2, exponent)
}

/**
 * Computes the fractional part of [x].
 * */
fun frac(x: Double): Double = x - floor(x)

/**
 * Computes the fractional part of [x].
 * */
fun frac(x: Float): Float = x - floor(x)

/**
 * Maps [v] from a source range to a destination range.
 * @param srcMin The minimum value in [v]'s range.
 * @param srcMax The maximum value in [v]'s range.
 * @param dstMin The resulting range minimum.
 * @param dstMax The resulting range maximum.
 * @return [v] mapped from the source range to the destination range.
 * */
fun map(v: Double, srcMin: Double, srcMax: Double, dstMin: Double, dstMax: Double): Double {
    return dstMin + (v - srcMin) * (dstMax - dstMin) / (srcMax - srcMin)
}

/**
 * Maps [v] from a source range to a destination range.
 * @param srcMin The minimum value in [v]'s range.
 * @param srcMax The maximum value in [v]'s range.
 * @param dstMin The resulting range minimum.
 * @param dstMax The resulting range maximum.
 * @return [v] mapped from the source range to the destination range.
 * */
fun map(v: Float, srcMin: Float, srcMax: Float, dstMin: Float, dstMax: Float): Float {
    return dstMin + (v - srcMin) * (dstMax - dstMin) / (srcMax - srcMin)
}

/**
 * Maps [v] from a source range to a destination range.
 * @param srcMin The minimum value in [v]'s range.
 * @param srcMax The maximum value in [v]'s range.
 * @param dstMin The resulting range minimum.
 * @param dstMax The resulting range maximum.
 * @return [v] mapped from the source range to the destination range.
 * */
fun map(v: Int, srcMin: Int, srcMax: Int, dstMin: Int, dstMax: Int): Int {
    return dstMin + (v - srcMin) * (dstMax - dstMin) / (srcMax - srcMin)
}

/**
 * Converts from BlockBench units to world units.
 * */
fun bbSize(size: Int): Double {
    return bbSize(size.toDouble())
}

/**
 * Converts from BlockBench units to world units.
 * */
fun bbSize(size: Double): Double {
    return size / 16.0
}

/**
 * Converts from BlockBench coordinates to world coordinates. Internally, this uses [bbSize] to convert every value in the vector.
 * */
fun bbVec(vec: Vec3): Vec3 {
    return Vec3(bbSize(vec.x), bbSize(vec.y), bbSize(vec.z))
}

/**
 * Converts from BlockBench coordinates to world coordinates. Internally, this uses [bbSize] to convert every value.
 * */
fun bbVec(x: Double, y: Double, z: Double): Vec3 {
    return Vec3(x, y, z).fromBB()
}

/**
 * Converts a [Vec3] from BlockBench coordinates to world coordinates. Internally, this uses [bbVec].
 * */
fun Vec3.fromBB(): Vec3 {
    return bbVec(this)
}

/**
 * @return a [Vec3] with all values initialized to [value].
 * */
fun vec3(value: Double): Vec3 {
    return Vec3(value, value, value)
}

/**
 * @return a [Vec3i] with all values set to [value].
 * */
fun vec3i(value: Int): Vec3i {
    return Vec3i(value, value, value)
}

/**
 * @return a [Vec3i] with X set to [x].
 * */
fun vec3iX(x: Int): Vec3i {
    return Vec3i(x, 0, 0)
}

/**
 * @return a [Vec3i] with Y set to [y].
 * */
fun vec3iY(y: Int): Vec3i {
    return Vec3i(0, y, 0)
}

/**
 * @return a [Vec3i] with Z set to [z].
 * */
fun vec3iZ(z: Int): Vec3i {
    return Vec3i(0, 0, z)
}

fun vec4f(value: Float): Vector4f {
    return Vector4f(value, value, value, value)
}

fun vec4fOne(): Vector4f {
    return vec4f(1f)
}

fun vec4FZero(): Vector4f {
    return vec4f(0f)
}

/**
 * Maps [value] ranging from 0-1 to an integer ranging from [Short.MIN_VALUE] to [Short.MAX_VALUE].
 * */
fun mapNormalizedDoubleShort(value: Double): Int {
    return map(
        value,
        0.0,
        1.0,
        Short.MIN_VALUE.toDouble(),
        Short.MAX_VALUE.toDouble()
    )
        .toInt()
}

/**
 * Maps an integer ranging from [Short.MIN_VALUE] to [Short.MAX_VALUE] to a value ranging from 0-1.
 * */
fun unmapNormalizedDoubleShort(value: Int): Double {
    return map(
        value.toDouble(),
        Short.MIN_VALUE.toDouble(),
        Short.MAX_VALUE.toDouble(),
        0.0,
        1.0
    )
}

/**
 * Computes the arithmetic mean of [a] and [b].
 * */
fun avg(a: Double, b: Double): Double = (a + b) / 2.0

/**
 * Computes the arithmetic mean of [a], [b] and [c].
 * */
fun avg(a: Double, b: Double, c: Double): Double = (a + b + c) / 3.0


fun Double.mappedTo(srcMin: Double, srcMax: Double, dstMin: Double, dstMax: Double): Double = map(this, srcMin, srcMax, dstMin, dstMax)

fun approxEqual(a: Double, b: Double, epsilon: Double = 10e-6): Boolean = abs(a - b) < epsilon
fun Double.approxEq(other: Double, epsilon: Double = 10e-6): Boolean = approxEqual(this, other, epsilon)
fun Double.sqrt() = sqrt(this)
infix fun Double.approxEq(other: Double): Boolean = approxEqual(this, other, 10e-6)

/**
 * Returns the max of [a] and [b].
 * If both values are [Double.NaN], an error is produced.
 * If [a] is [Double.NaN], [b] is returned.
 * If [b] is [Double.NaN], [a] is returned.
 * */
fun maxNaN(a: Double, b: Double): Double {
    if (a.isNaN() && b.isNaN()) {
        error("Both a and b were NaN")
    }

    if (a.isNaN()) {
        return b
    }

    if (b.isNaN()) {
        return a
    }

    return max(a, b)
}

/**
 * Returns the min of [a] and [b].
 * If both values are [Double.NaN], an error is produced.
 * If [a] is [Double.NaN], [b] is returned.
 * If [b] is [Double.NaN], [a] is returned.
 * */
fun minNaN(a: Double, b: Double): Double {
    if (a.isNaN() && b.isNaN()) {
        error("Both a and b were NaN")
    }

    if (a.isNaN()) {
        return b
    }

    if (b.isNaN()) {
        return a
    }

    return min(a, b)
}

/**
 * Throws an error if [this] is [Double.NaN]
 * */
fun Double.requireNotNaN(): Double {
    require(!this.isNaN()) { "Value was NaN" }

    return this
}

/**
 * Returns the square of this number.
 * */
fun Double.sqr(): Double = this * this

/**
 * Returns [this] with the specified [sign]. NaN is not permitted in either [this] or [sign].
 * */
fun Double.signed(sign: Double): Double {
    // Kotlin sign function docs:
    /**
     * Returns the first floating-point argument with the sign of the second floating-point argument.
     * Note that unlike the StrictMath.copySign method,
     * this method does not require NaN sign arguments to be treated as positive values;
     * implementations are permitted to treat some NaN arguments as positive and other NaN arguments as negative to allow greater performance.
     * */

    // I added NaN handling here (with requireNotNaN, which will throw if the sign or this is NaN)

    this.requireNotNaN()
    sign.requireNotNaN()

    if (this == 0.0 || sign == 0.0) {
        return 0.0
    }

    return if (sign(this) != sign(sign))
        -this
    else this
}

/**
 * Rounds the number to the specified number of [decimals].
 * */
fun Double.rounded(decimals: Int = 3): Double {
    var multiplier = 1.0
    repeat(decimals) { multiplier *= 10 }
    return round(this * multiplier) / multiplier
}

fun Double.minWith(other: Double) = min(this, other)
fun Double.maxWith(other: Double) = max(this, other)

/**
 * Epsilon Sign-Non-Zero function from the [SymForce](https://arxiv.org/abs/2204.07889) paper.
 * */
fun snzEps(a: Double): Double {
    if (a >= 0.0) {
        return 2.2e-15
    }

    return -2.2e-15
}

fun Double.nonZero() = this + snzEps(this)

fun snz(a: Double): Double {
    if (a >= 0.0) {
        return 1.0
    }

    return -1.0
}
