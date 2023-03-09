package org.eln2.mc.sim

import mcp.mobius.waila.api.IPluginConfig
import org.ageseries.libage.sim.Material
import org.ageseries.libage.sim.thermal.STANDARD_TEMPERATURE
import org.ageseries.libage.sim.thermal.Simulator
import org.ageseries.libage.sim.thermal.Temperature
import org.ageseries.libage.sim.thermal.ThermalMass
import org.eln2.mc.extensions.ThermalExtensions.appendBody
import org.eln2.mc.integration.waila.IWailaProvider
import org.eln2.mc.integration.waila.TooltipBuilder
import org.eln2.mc.utility.IntId

class ThermalBody(
    var mass: ThermalMass,
    var surfaceArea: Double
) : IWailaProvider {
    var temperature: Temperature
        get() = mass.temperature
        set(value) { mass.temperature = value }
    var temperatureK: Double
        get() = temperature.kelvin
        set(value) { mass.temperature = Temperature(value) }

    var thermalEnergy: Double
        get() = mass.energy
        set(value) { mass.energy = value }

    override fun appendBody(builder: TooltipBuilder, config: IPluginConfig?) {
        mass.appendBody(builder, config)
    }

    companion object {
        fun createDefault(): ThermalBody {
            return ThermalBody(ThermalMass(Material.COPPER), 0.5)
        }
    }
}
