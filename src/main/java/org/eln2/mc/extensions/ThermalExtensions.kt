package org.eln2.mc.extensions

import mcp.mobius.waila.api.IPluginConfig
import org.ageseries.libage.sim.thermal.Simulator
import org.ageseries.libage.sim.thermal.ThermalMass
import org.eln2.mc.integration.waila.TooltipBuilder

fun ThermalMass.appendBody(builder: TooltipBuilder, config: IPluginConfig?) {
    builder.energy(this.energy)
    builder.mass(this.mass)
    builder.temperature(this.temperature.kelvin)
}

fun Simulator.subStep(dt: Double, steps: Int, consumer: ((Int, Double) -> Unit)? = null){
    val stepSize = dt / steps

    repeat(steps) {
        this.step(stepSize)
        consumer?.invoke(it, stepSize)
    }
}
