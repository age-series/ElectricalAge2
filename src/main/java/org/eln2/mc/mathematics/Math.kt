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
    ).toInt()
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
 * @return 0 if [this] is NaN. Otherwise, [this].
 * */
fun Double.nanZero(): Double {
    if(this.isNaN()) {
        return 0.0
    }

    return this
}

/**
 * @return 0 if [this] is infinity. Otherwise, [this].
 * */
fun Double.infinityZero(): Double {
    if(this.isInfinite()) {
        return 0.0
    }

    return this
}

/**
 * @return 0 if [this] is NaN or infinity. Otherwise, [this].
 * */
fun Double.definedOrZero(): Double = this.nanZero().infinityZero()

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

fun snzi(a: Double): Int {
    if (a >= 0.0) {
        return 1
    }

    return -1
}

private const val ADAPTLOB_ALPHA = 0.816496580927726
private const val ADAPTLOB_BETA = 0.447213595499958

private fun adaptlobStp(f: ((Double) -> Double), a: Double, b: Double, fa: Double, fb: Double, `is`: Double): Double {
    val h = (b - a) / 2.0
    val m = (a + b) / 2.0
    val mll = m - ADAPTLOB_ALPHA * h
    val ml = m - ADAPTLOB_BETA * h
    val mr = m + ADAPTLOB_BETA * h
    val mrr = m + ADAPTLOB_ALPHA * h
    val fmll = f(mll)
    val fml = f(ml)
    val fm = f(m)
    val fmr = f(mr)
    val fmrr = f(mrr)

    val i2 = h / 6.0 * (fa + fb + 5.0 * (fml + fmr))
    val i1 = h / 1470.0 * (77.0 * (fa + fb) + 432.0 * (fmll + fmrr) + 625.0 * (fml + fmr) + 672.0 * fm)

    return if (`is` + (i1 - i2) == `is` || mll <= a || b <= mrr)
        i1
    else
        adaptlobStp(f, a, mll, fa, fmll, `is`) +
        adaptlobStp(f, mll, ml, fmll, fml, `is`) +
        adaptlobStp(f, ml, m, fml, fm, `is`) +
        adaptlobStp(f, m, mr, fm, fmr, `is`) +
        adaptlobStp(f, mr, mrr, fmr, fmrr, `is`) +
        adaptlobStp(f, mrr, b, fmrr, fb, `is`)
}

fun integralScan(f: ((Double) -> Double), a: Double, b: Double, tolerance: Double = 1e-15): Double {
    var tol = tolerance

    val eps = 1e-15

    val m = (a + b) / 2.0
    val h = (b - a) / 2.0

    val x1 = 0.942882415695480
    val x2 = 0.641853342345781
    val x3 = 0.236383199662150

    val y1 = f(a)
    val y2 = f(m - x1 * h)
    val y3 = f(m - ADAPTLOB_ALPHA * h)
    val y4 = f(m - x2 * h)
    val y5 = f(m - ADAPTLOB_BETA * h)
    val y6 = f(m - x3 * h)
    val y7 = f(m)
    val y8 = f(m + x3 * h)
    val y9 = f(m + ADAPTLOB_BETA * h)
    val y10 = f(m + x2 * h)
    val y11 = f(m + ADAPTLOB_ALPHA * h)
    val y12 = f(m + x1 * h)
    val y13 = f(b)

    val i2 = h / 6.0 * (y1 + y13 + 5.0 * (y5 + y9))
    val i1 = h / 1470.0 * (77.0 * (y1 + y13) + 432.0 * (y3 + y11) + 625.0 * (y5 + y9) + 672.0 * y7)

    var `is` = h * (
        0.0158271919734802 * (y1 + y13) +
            0.0942738402188500 * (y2 + y12) +
            0.155071987336585 * (y3 + y11) +
            0.188821573960182 * (y4 + y10) +
            0.199773405226859 * (y5 + y9) +
            0.224926465333340 * (y6 + y8) +
            0.242611071901408 * y7
        )

    val s = snz(`is`)
    val erri1 = abs(i1 - `is`)
    val erri2 = abs(i2 - `is`)
    var r = 1.0

    if (erri2 != 0.0) {
        r = erri1 / erri2
    }

    if (r > 0.0 && r < 1.0) {
        tol /= r
    }
    `is` = s * abs(`is`) * tol / eps

    if (`is` == 0.0) {
        `is` = b - a
    }

    return adaptlobStp(f, a, b, y1, y13, `is`)
}

class Dual private constructor(private val values: DoubleArray) {
    /**
     * Constructs a [Dual] from the value [x] and the [tail].
     * */
    constructor(x: Double, tail: Dual): this(
        DoubleArray(tail.values.size + 1).also {
            it[0] = x

            for (i in 0 until tail.values.size) {
                it[i + 1] = tail.values[i]
            }
        }
    )

    operator fun get(index: Int) = values[index]

    val size get() = values.size
    val isReal get() = values.size == 1

    /**
     * Gets the first value in this [Dual].
     * */
    val value get() = values[0]

    /**
     * Gets the values at the start of the [Dual], ignoring the last [n] values.
     * */
    fun head(n: Int = 1) = Dual(DoubleArray(size - n) { values[it] })

    /**
     * Gets the values at the end of the [Dual], ignoring the first [n] values.
     * */
    fun tail(n: Int = 1) = Dual(DoubleArray(size - n) { values[it + n] })

    operator fun unaryPlus(): Dual {
        return this
    }

    operator fun unaryMinus() = Dual(
        DoubleArray(size).also {
            for (i in it.indices){
                it[i] = -this[i]
            }
        }
    )

    operator fun plus(other: Dual): Dual =
        if (this.isReal || other.isReal) const(this[0] + other[0])
        else Dual(this.value + other.value, this.tail() + other.tail())

    operator fun minus(other: Dual): Dual =
        if (this.isReal || other.isReal) const(this[0] - other[0])
        else Dual(this.value - other.value, this.tail() - other.tail())

    operator fun times(other: Dual): Dual =
        if (this.isReal || other.isReal) const(this[0] * other[0])
        else Dual(this.value * other.value, this.tail() * other.head() + this.head() * other.tail())

    operator fun div(other: Dual): Dual =
        if (this.isReal || other.isReal) const(this[0] / other[0])
        else Dual(this.value / other.value, (this.tail() * other - this * other.tail()) / (other * other))

    inline fun function(x: ((Double) -> Double), dxFront: ((Dual) -> Dual)): Dual =
        if (this.isReal) const(x(this.value))
        else Dual(x(this.value), dxFront(this.head()) * this.tail())

    operator fun plus(const: Double) = Dual(values.clone().also { it[0] += const })
    operator fun minus(const: Double) = Dual(values.clone().also { it[0] -= const })

    private inline fun mapValues(transform: ((Double) -> Double)) = Dual(DoubleArray(values.size) { i -> transform(values[i])})
    operator fun times(constant: Double) = mapValues { v -> v * constant }
    operator fun div(constant: Double) = mapValues { v -> v / constant }

    override fun equals(other: Any?): Boolean {
        if (this === other){
            return true
        }

        if (javaClass != other?.javaClass) {
            return false
        }

        other as Dual

        if (!values.contentEquals(other.values)){
            return false
        }

        return true
    }

    override fun hashCode(): Int {
        return values.contentHashCode()
    }

    override fun toString(): String {
        if(values.isEmpty()) {
            return "empty"
        }

        return values
            .mapIndexed { i, v -> "x$i=$v" }
            .joinToString(", ")
    }

    companion object {
        fun const(x: Double, n: Int = 1) = Dual(DoubleArray(n).also { it[0] = x })

        fun variable(v: Double, n: Int = 1) = Dual(
            DoubleArray(n).also {
                it[0] = v;
                if(n > 1){
                    it[1] = 1.0
                }
            }
        )

        fun of(vararg values: Double) = Dual(values.asList().toDoubleArray())
    }
}

operator fun Double.plus(dual: Dual) = Dual.const(this, dual.size) + dual
operator fun Double.minus(dual: Dual) = Dual.const(this, dual.size) - dual
operator fun Double.times(dual: Dual) = Dual.const(this, dual.size) * dual
operator fun Double.div(dual: Dual) = Dual.const(this, dual.size) / dual

fun sin(d: Dual): Dual = d.function({ sin(it) }) { cos(it) }
fun cos(d: Dual): Dual = d.function({ cos(it) }) { -sin(it) }
fun pow(d: Dual, n: Double): Dual = d.function({ it.pow(n) }) { n * pow(it, n - 1) }
fun sqrt(d: Dual): Dual = d.function({ sqrt(it) }) { (Dual.const(1.0, d.size) / (Dual.const(2.0, d.size) * sqrt(it))) }
fun Dual.sqr() = this * this
