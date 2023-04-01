package org.eln2.mc.client.render.animations.colors

import com.jozufozu.flywheel.util.Color

object Utilities {
    fun colorF(r: Float, g: Float, b: Float, a: Float): Color{
        return Color(r, g, b, a)
    }

    fun colorD(r: Double, g: Double, b: Double, a: Double): Color{
        return colorF(r.toFloat(), g.toFloat(), b.toFloat(), a.toFloat())
    }
}
