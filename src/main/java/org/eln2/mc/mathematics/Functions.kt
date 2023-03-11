package org.eln2.mc.mathematics

import net.minecraft.core.Vec3i
import net.minecraft.world.phys.Vec3
import kotlin.math.PI
import kotlin.math.floor

object Functions {
    //#region Interpolations

    fun lerp(from: Double, to: Double, factor: Double): Double {
        return (1.0 - factor) * from + factor * to
    }

    fun lerp(from: Float, to: Float, factor: Float): Float {
        return (1f - factor) * from + factor * to
    }

    /**
     * Calls [lerp] with a clamped factor.
     * */
    fun lerpC(from: Double, to: Double, factorRaw: Double): Double {
        val factor = factorRaw.coerceIn(0.0, 1.0)
        return (1.0 - factor) * from + factor * to
    }

    /**
     * Calls [lerp] with a clamped factor.
     * */
    fun lerpC(from: Float, to: Float, factorRaw: Float): Float {
        val factor = factorRaw.coerceIn(0f, 1f)
        return (1f - factor) * from + factor * to
    }

    /**
     * This is Perlin's Smoother Step function, with a clamped factor. It uses a Hermite Polynomial to smoothly interpolate two points.
     * @param edge0 Left edge. Usually, 0.
     * @param edge1 Right edge. Usually, 1.
     * @param t Interpolation argument. The function works on 0-1 ranges, and the parameter is clamped.
     * */
    fun smootherStepC(edge0: Double, edge1: Double, t: Double): Double {
        val x = ((t - edge0) / (edge1 - edge0)).coerceIn(0.0, 1.0)
        return x * x * x * (x * (x * 6 - 15) + 10)
    }

    fun smootherStepCInterpolate(a: Double, b: Double, t: Double): Double {
        return map(smootherStepC(0.0, 1.0, t), 0.0, 1.0, a, b)
    }

    //#endregion

    //#region Powers

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

    //#endregion

    /**
     * Computes the fractional part of [x].
     * */
    fun frac(x: Double): Double = x - floor(x)

    /**
     * Computes the fractional part of [x].
     * */
    fun frac(x: Float): Float = x - floor(x)

    //#region Ranges

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

    //#endregion

    //#region Vectors

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
     * @return a [Vec3] with X set to [x] and the other values set to 0.
     * */
    fun vec3X(x: Double): Vec3 {
        return Vec3(x, 0.0, 0.0)
    }

    /**
     * @return the X unit vector.
     * */
    fun vec3X(): Vec3 {
        return vec3X(1.0)
    }

    /**
     * @return a [Vec3] with Y set to [y] and the other values set to 0.
     * */
    fun vec3Y(y: Double): Vec3 {
        return Vec3(0.0, y, 0.0)
    }

    /**
     * @return the Y unit vector.
     * */
    fun vec3Y(): Vec3 {
        return vec3Y(1.0)
    }

    /**
     * @return a [Vec3] with Z set to [z] and the other values set to 0.
     * */
    fun vec3Z(z: Double): Vec3 {
        return Vec3(0.0, 0.0, z)
    }

    /**
     * @return the Z unit vector.
     * */
    fun vec3Z(): Vec3 {
        return vec3Z(1.0)
    }

    /**
     * @return a [Vec3] with all values set to 1.
     * */
    fun vec3One(): Vec3 {
        return vec3(1.0)
    }

    /**
     * @return a [Vec3] with all values set to 0.
     * */
    fun vec3Zero(): Vec3 {
        return vec3(0.0)
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
     * @return the X unit vector.
     * */
    fun vec3iX(): Vec3i {
        return vec3iX(1)
    }

    /**
     * @return a [Vec3i] with Y set to [y].
     * */
    fun vec3iY(y: Int): Vec3i {
        return Vec3i(0, y, 0)
    }

    /**
     * @return the Y unit vector.
     * */
    fun vec3iY(): Vec3i {
        return vec3iY(1)
    }

    /**
     * @return a [Vec3i] with Z set to [z].
     * */
    fun vec3iZ(z: Int): Vec3i {
        return Vec3i(0, 0, z)
    }

    /**
     * @return the Z unit vector.
     * */
    fun vec3iZ(): Vec3i {
        return vec3iZ(1)
    }

    /**
     * @return a [Vec3i] with all values set to 1.
     * */
    fun vec3iOne(): Vec3i {
        return vec3i(1)
    }

    /**
     * @return a [Vec3i] with all values set to 0.
     * */
    fun vec3iZero(): Vec3i {
        return vec3i(0)
    }

    //#endregion
}

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
}
