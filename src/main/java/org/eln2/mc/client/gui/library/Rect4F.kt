package org.eln2.mc.client.gui.library

import org.apache.commons.math3.util.Precision

class Rect4F(val pos : Point2F, val size : Point2F) {
    constructor(x : Float, y : Float, width : Float, height : Float) : this(Point2F(x, y), Point2F(width, height))

    fun scale(scale : Float) : Rect4F { return Rect4F(pos.scale(scale), size.scale(scale)) }

    val top = pos.y
    val bottom = pos.y + size.y
    val left = pos.x
    val right = pos.x + size.x

    val x = pos.x
    val y = pos.y
    val width = size.x
    val height = size.y

    fun deltaPos(delta : Point2F) : Rect4F{
        return Rect4F(x + delta.x, y + delta.y, width, height)
    }

    fun deltaSize(delta: Point2F) : Rect4F {
        return Rect4F(x, y, width + delta.x, height + delta.y)
    }

    fun hasPoint(pt : Point2F) : Boolean{
        return  pt.x > left &&
                pt.y > top &&
                pt.x < right &&
                pt.y < bottom
    }

    override fun toString(): String {
        return "X: ${Precision.round(x, 2)} Y: ${Precision.round(y, 2)} W: ${Precision.round(width, 2)} H: ${Precision.round(height, 2)}"
    }
}
