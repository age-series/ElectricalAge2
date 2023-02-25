package org.eln2.mc

import net.minecraft.core.Vec3i
import net.minecraft.world.phys.Vec3
import org.eln2.mc.utility.ResourceReader
import kotlin.math.floor


// Yes, how fancy. Stop making fun of me!
object Mathematics {
    //#region Interpolations

    fun lerp(from: Double, to: Double, factor: Double): Double{
        return (1.0 - factor) * from + factor * to
    }

    fun lerp(from: Float, to: Float, factor: Float): Float{
        return (1f - factor) * from + factor * to
    }

    fun lerpC(from: Double, to: Double, factorRaw: Double): Double{
        val factor = factorRaw.coerceIn(0.0, 1.0)
        return (1.0 - factor) * from + factor * to
    }

    fun lerpC(from: Float, to: Float, factorRaw: Float): Float{
        val factor = factorRaw.coerceIn(0f, 1f)
        return (1f - factor) * from + factor * to
    }

    fun smoothstepC(from: Double, to: Double, factorRaw: Double): Double{
        val factor = ((factorRaw - from) / (to - from)).coerceIn(0.0, 1.0)
        return factor * factor * (3 - 2 * factor);
    }

    //#endregion

    fun map(v: Double, srcMin: Double, srcMax: Double, dstMin: Double, dstMax: Double): Double {
        return dstMin + (v - srcMin) * (dstMax - dstMin) / (srcMax - srcMin)
    }

    fun map(v: Float, srcMin: Float, srcMax: Float, dstMin: Float, dstMax: Float): Float {
        return dstMin + (v - srcMin) * (dstMax - dstMin) / (srcMax - srcMin)
    }

    fun bbSize(size: Int): Double {
        return bbSize(size.toDouble())
    }

    fun bbSize(size: Double): Double{
        return size / 16.0
    }

    fun bbVec(vec: Vec3): Vec3{
        return Vec3(bbSize(vec.x), bbSize(vec.y), bbSize(vec.z))
    }

    fun bbVec(x: Double, y: Double, z: Double): Vec3{
        return Vec3(x, y, z).fromBB()
    }

    fun Vec3.fromBB(): Vec3 {
        return bbVec(this)
    }

    fun vec3(value: Double): Vec3 {
        return Vec3(value, value, value)
    }

    fun vec3X(x: Double): Vec3{
        return Vec3(x, 0.0, 0.0)
    }

    fun vec3X(): Vec3{
        return vec3X(1.0)
    }

    fun vec3Y(y: Double): Vec3{
        return Vec3(0.0, y, 0.0)
    }

    fun vec3Y(): Vec3{
        return vec3Y(1.0)
    }

    fun vec3Z(z: Double): Vec3{
        return Vec3(0.0, 0.0, z)
    }

    fun vec3Z(): Vec3{
        return vec3Z(1.0)
    }

    fun vec3One(): Vec3{
        return vec3(1.0)
    }

    fun vec3Zero(): Vec3{
        return vec3(0.0)
    }

    fun vec3i(value: Int): Vec3i {
        return Vec3i(value, value, value)
    }

    fun vec3iX(x: Int): Vec3i{
        return Vec3i(x, 0, 0)
    }

    fun vec3iX(): Vec3i{
        return vec3iX(1)
    }

    fun vec3iY(y: Int): Vec3i{
        return Vec3i(0, y, 0)
    }

    fun vec3iY(): Vec3i{
        return vec3iY(1)
    }

    fun vec3iZ(z: Int): Vec3i{
        return Vec3i(0, 0, z)
    }

    fun vec3iZ(): Vec3i{
        return vec3iZ(1)
    }

    fun vec3iOne(): Vec3i{
        return vec3i(1)
    }

    fun vec3iZero(): Vec3i{
        return vec3i(0)
    }
}

class HermiteSpline {
    val points = ArrayList<Double>()

    fun evaluate(progress: Double): Double {
        if(points.size == 0){
            // Wouldn't make sense to evaluate it with one, but let's throw errors only for 0

            error("Cannot evaluate spline with 0 points")
        }

        val fuzzyIndex = (points.size - 1) * progress
        val t = fuzzyIndex - floor(fuzzyIndex)
        val pointIndex = fuzzyIndex.toInt()

        return hermite(
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

    companion object{
        fun hermite(a: Double, b: Double, c: Double, d: Double, t: Double): Double {
            val t2 = t * t
            val t3 = t2 * t

            val h1 = -a / 2.0 + 3.0 * b / 2.0 - 3.0 * c / 2.0 + d / 2.0
            val h2 = a - 5.0 * b / 2.0 + 2.0 * c - d / 2.0
            val h3 = -a / 2.0 + c / 2.0

            return h1 * t3 + h2 * t2 + h3 * t + b
        }

        fun loadSpline(path: String): HermiteSpline {
            return HermiteSpline().also { spline ->
                ResourceReader
                    .getResourceString(Eln2.resource(path))
                    .lines()
                    .filter { it.isNotBlank() && it.isNotEmpty() }
                    .map { it.toDouble() }
                    .forEach(spline.points::add)

                Eln2.LOGGER.info("Loaded ${spline.points.size} points from $path")
            }
        }
    }
}
