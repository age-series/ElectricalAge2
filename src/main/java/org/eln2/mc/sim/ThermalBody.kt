package org.eln2.mc.sim

import mcp.mobius.waila.api.IPluginConfig
import org.ageseries.libage.sim.thermal.Simulator
import org.ageseries.libage.sim.thermal.Temperature
import org.ageseries.libage.sim.thermal.ThermalMass
import org.eln2.mc.extensions.ThermalExtensions.appendBody
import org.eln2.mc.integration.waila.IWailaProvider
import org.eln2.mc.integration.waila.TooltipBuilder
import org.eln2.mc.utility.IntId

class ThermalBody<Locator>(
    override var locator: Locator,
    override var mass: ThermalMass,
    override var surfaceArea: Double
) : Simulator.Body<Locator>,
    IWailaProvider {
    var temperature: Temperature
        get() = mass.temperature
        set(value) { mass.temperature = value }
    var temperatureK: Double
        get() = temperature.kelvin
        set(value) { mass.temperature = Temperature(value) }

    var thermalEnergy: Double
        get() = mass.energy
        set(value) { mass.energy = value }

    fun ensureNotNegative() {
        if(mass.energy < 0) {
            mass.energy = 0.0
        }
    }

    override fun appendBody(builder: TooltipBuilder, config: IPluginConfig?) {
        mass.appendBody(builder, config)
    }
}

fun thermalBody(mass: ThermalMass, surfaceArea: Double): ThermalBody<IntId> {
    return ThermalBody(IntId.create(), mass, surfaceArea)
}
