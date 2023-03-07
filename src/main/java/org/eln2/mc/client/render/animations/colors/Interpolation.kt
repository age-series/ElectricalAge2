package org.eln2.mc.client.render.animations.colors

import com.jozufozu.flywheel.util.Color
import org.eln2.mc.mathematics.Mathematics.lerp
import org.eln2.mc.client.render.animations.colors.Extensions.rgbToHsb

fun interface IColorInterpolator {
    fun interpolate(from: Color, to: Color, blend: Float): Color
}

object ColorInterpolators {
    fun rgbLinear() : IColorInterpolator {
        return IColorInterpolator { from, to, blend ->
            return@IColorInterpolator Color(
                lerp(from.redAsFloat, to.redAsFloat, blend),
                lerp(from.greenAsFloat, to.greenAsFloat, blend),
                lerp(from.blueAsFloat, to.blueAsFloat, blend),
                lerp(from.alphaAsFloat, to.alphaAsFloat, blend)
            )
        }
    }

    fun hsvLinear() : IColorInterpolator {
        return IColorInterpolator { from, to, blend ->
            val fromHsb = from.rgbToHsb()
            val toHsb = to.rgbToHsb()

            val h = lerp(fromHsb[0], toHsb[0], blend)
            val s = lerp(fromHsb[1], toHsb[1], blend)
            val v = lerp(fromHsb[2], toHsb[2], blend)

            val rgb = java.awt.Color(java.awt.Color.HSBtoRGB(h, s, v))

            return@IColorInterpolator Color(
                rgb.red,
                rgb.green,
                rgb.blue,
                (lerp(from.alphaAsFloat, to.alphaAsFloat, blend) * 255).toInt()
            )
        }
    }
}

private object Extensions {
    fun Color.toAwtColor(): java.awt.Color{
        return java.awt.Color(
            this.redAsFloat,
            this.greenAsFloat,
            this.blueAsFloat,
            this.alphaAsFloat
        )
    }

    fun java.awt.Color.toFlwColor(): Color{
        return Color(this.red, this.green, this.blue, this.alpha)
    }

    fun Color.rgbToHsb(): FloatArray{
        return java.awt.Color.RGBtoHSB(this.red, this.green, this.blue, null)
    }
}
