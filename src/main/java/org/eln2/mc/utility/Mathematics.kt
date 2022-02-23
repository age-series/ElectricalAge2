package org.eln2.mc.utility

import net.minecraft.core.Direction

object Mathematics {
    fun angleBetweenPoints2(x0: Double, y0: Double, x1: Double, y1: Double): Double {
        return kotlin.math.atan2(y1 - y0, x1 - x0) * 180.0 / Math.PI
    }

    fun angleToDirection(angle : Double) : Direction{
        return Direction.fromYRot(angle)
    }
}
