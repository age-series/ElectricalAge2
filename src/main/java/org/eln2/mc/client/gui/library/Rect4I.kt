package org.eln2.mc.client.gui.library

class Rect4I(val pos : Point2I, val size : Point2I) {
    constructor(x : Int, y : Int, width : Int, height : Int) : this(Point2I(x, y), Point2I(width, height))
    constructor(width : Int, height: Int) : this(0, 0, width, height)
    constructor(size : Point2I) : this(size.x, size.y)

    fun scale(scale : Float) : Rect4F { return Rect4F(pos.scale(scale), size.scale(scale)) }

    val top = pos.y
    val bottom = pos.y + size.y
    val left = pos.x
    val right = pos.x + size.x

    val x = pos.x
    val y = pos.y
    val width = size.x
    val height = size.y

    fun deltaPos(delta : Point2I) : Rect4I{
        return Rect4I(x + delta.x, y + delta.y, width, height)
    }

    fun deltaSize(delta: Point2I) : Rect4I {
        return Rect4I(x, y, width + delta.x, height + delta.y)
    }

    fun hasPoint(pt : Point2I) : Boolean{
        return  pt.x > left &&
                pt.y > top &&
                pt.x < right &&
                pt.y < bottom
    }

    override fun toString(): String {
        return "X: $x Y: $y W: $width H: $height"
    }

    fun toF() : Rect4F{
        return Rect4F(x.toFloat(), y.toFloat(), width.toFloat(), height.toFloat())
    }

    companion object{
        val zero = Rect4I(0, 0, 0, 0)
    }
}
