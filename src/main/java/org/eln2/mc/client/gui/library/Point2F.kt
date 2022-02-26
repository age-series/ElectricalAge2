package org.eln2.mc.client.gui.library

import org.apache.commons.math3.util.Precision.round

class Point2F(val x : Float, val y : Float) {
    fun normalizeX(width : Float) : Float { return x / width }
    fun normalizeY(height : Float) : Float{ return y / height }
    fun normalizeX(width : Int) : Float { return normalizeX(width.toFloat()) }
    fun normalizeY(height : Int) : Float { return normalizeY(height.toFloat()) }

    fun scale(scale : Float) : Point2F { return Point2F(x * scale, y * scale) }

    operator fun plus(other : Point2F) : Point2F { return Point2F(x + other.x, y + other.y) }
    operator fun minus(other : Point2F) : Point2F { return Point2F(x - other.x, y - other.y) }

    fun toI() : Point2I { return Point2I(x.toInt(), y.toInt()) }

    val inverse get() = Point2F(-x, -y)

    companion object{
        val zero = Point2F(0f, 0f)
    }

    override fun toString(): String {
        return "X: ${round(x, 2)} Y: ${round(y, 2)}"
    }
}
