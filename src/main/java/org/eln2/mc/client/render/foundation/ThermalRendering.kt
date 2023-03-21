package org.eln2.mc.client.render.foundation

import com.jozufozu.flywheel.util.Color
import org.ageseries.libage.sim.thermal.STANDARD_TEMPERATURE
import org.ageseries.libage.sim.thermal.Temperature
import org.ageseries.libage.sim.thermal.ThermalUnits
import org.eln2.mc.client.render.animations.colors.ColorInterpolators
import org.eln2.mc.client.render.animations.colors.IColorInterpolator
import org.eln2.mc.client.render.animations.colors.Utilities
import org.eln2.mc.mathematics.Functions.map

class RadiantBodyColorBuilder {
    var coldTint = Utilities.colorF(1f, 1f, 1f, 1f)
    var hotTint = Utilities.colorF(5f, 0.1f, 0.2f, 1f)
    var coldTemperature = STANDARD_TEMPERATURE
    var hotTemperature = Temperature.from(1000.0, ThermalUnits.CELSIUS)
    var interpolator = ColorInterpolators.rgbLinear()

    fun build(): RadiantBodyColor {
        return RadiantBodyColor(
            coldTint,
            hotTint,
            coldTemperature,
            hotTemperature,
            interpolator
        )
    }
}

fun defaultRadiantBodyColor(): RadiantBodyColor {
    return RadiantBodyColorBuilder().build()
}

class RadiantBodyColor(
    val coldTint: Color,
    val hotTint: Color,
    val coldTemperature:Temperature,
    val hotTemperature : Temperature,
    val interpolator : IColorInterpolator,
) {
    fun evaluate(t: Temperature): Color {
        val progress = map(
            t.kelvin.coerceIn(coldTemperature.kelvin, hotTemperature.kelvin),
            coldTemperature.kelvin,
            hotTemperature.kelvin,
            0.0,
            1.0)

        return interpolator.interpolate(coldTint, hotTint, progress.toFloat())
    }

    fun evaluate(t: Double): Color {
        return evaluate(Temperature(t))
    }
}
