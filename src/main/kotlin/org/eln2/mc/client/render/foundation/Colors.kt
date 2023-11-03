package org.eln2.mc.client.render.foundation

import com.jozufozu.flywheel.util.Color
import org.eln2.mc.mathematics.Vector3d
import org.eln2.mc.mathematics.Vector4d
import org.eln2.mc.mathematics.lerp

fun colorF(r: Float, g: Float, b: Float, a: Float): Color {
    return Color(r, g, b, a)
}

fun colorLerp(from: Color, to: Color, blend: Float): Color =
    Color(
        lerp(from.redAsFloat, to.redAsFloat, blend),
        lerp(from.greenAsFloat, to.greenAsFloat, blend),
        lerp(from.blueAsFloat, to.blueAsFloat, blend),
        lerp(from.alphaAsFloat, to.alphaAsFloat, blend)
    )

data class RGBFloat(val r: Float, val g: Float, val b: Float) {
    operator fun times(scalar: Float) = RGBFloat(r * scalar, g * scalar, b * scalar)
    operator fun div(scalar: Float) = RGBFloat(r / scalar, g / scalar, b / scalar)
    operator fun times(other: RGBFloat) = RGBFloat(r * other.r, g * other.g, b * other.b)
    operator fun div(other: RGBFloat) = RGBFloat(r / other.r, g / other.g, b / other.b)
    operator fun plus(other: RGBFloat) = RGBFloat(r + other.r, g + other.g, b + other.b)
    operator fun minus(other: RGBFloat) = RGBFloat(r - other.r, g - other.g, b - other.b)

    fun toVector3d() = Vector3d(r.toDouble(), g.toDouble(), b.toDouble())

    companion object {
        val zero = RGBFloat(0f, 0f, 0f)
        val one = RGBFloat(1f, 1f, 1f)
    }
}

data class RGBAFloat(val r: Float, val g: Float, val b: Float, val a: Float) {
    operator fun times(scalar: Float) = RGBAFloat(r * scalar, g * scalar, b * scalar, a * scalar)
    operator fun div(scalar: Float) = RGBAFloat(r / scalar, g / scalar, b / scalar, a / scalar)
    operator fun times(other: RGBAFloat) = RGBAFloat(r * other.r, g * other.g, b * other.b, a * other.a)
    operator fun div(other: RGBAFloat) = RGBAFloat(r / other.r, g / other.g, b / other.b, a / other.a)
    operator fun plus(other: RGBAFloat) = RGBAFloat(r + other.r, g + other.g, b + other.b, a + other.a)
    operator fun minus(other: RGBAFloat) = RGBAFloat(r - other.r, g - other.g, b - other.b, a - other.a)

    fun toVector4d() = Vector4d(r.toDouble(), g.toDouble(), b.toDouble(), a.toDouble())

    companion object {
        val zero = RGBAFloat(0f, 0f, 0f, 0f)
        val one = RGBAFloat(1f, 1f, 1f, 1f)
    }
}

class MutableRGBA {
    var r: Double = 0.0
    var g: Double = 0.0
    var b: Double = 0.0
    var a: Double = 0.0

    fun clear(value: Double = 0.0) : MutableRGBA {
        r = value
        g = value
        b = value
        a = value
        return this
    }

    fun clearOne() = clear(1.0)
}
