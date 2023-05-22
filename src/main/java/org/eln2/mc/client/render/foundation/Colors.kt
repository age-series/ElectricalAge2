package org.eln2.mc.client.render.foundation

import com.jozufozu.flywheel.util.Color
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
