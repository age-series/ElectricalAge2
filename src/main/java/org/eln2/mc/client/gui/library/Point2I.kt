package org.eln2.mc.client.gui.library

class Point2I(val x : Int, val y : Int) {
    constructor(value : Int) : this(value, value)
    fun normalizeX(width : Int) : Float { return x.toFloat() / width.toFloat() }
    fun normalizeY(height : Int) : Float{ return y.toFloat() / height.toFloat() }

    fun scale(scale : Float) : Point2F { return Point2F(x * scale, y * scale) }

    operator fun plus(other : Point2I) : Point2I { return Point2I(x + other.x, y + other.y) }
    operator fun minus(other : Point2I) : Point2I { return Point2I(x - other.x, y - other.y) }
    operator fun div(other: Point2I) : Point2I { return Point2I(x / other.x, y / other.y) }
    operator fun div(scalar : Int) : Point2I { return Point2I(x / scalar, y / scalar) }
    operator fun div(scalar : Float) : Point2F { return  Point2F(x / scalar, y / scalar) }

    fun toF() : Point2F { return Point2F(x.toFloat(), y.toFloat()) }

    val inverse get() = Point2I(-x, -y)

    companion object{
        val zero = Point2I(0,0)
    }

    override fun toString(): String {
        return "X: $x Y: $y"
    }
}
