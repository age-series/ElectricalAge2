package org.eln2.mc

import mcp.mobius.waila.api.IPluginConfig
import org.ageseries.libage.data.biMapOf
import org.ageseries.libage.sim.Material
import org.ageseries.libage.sim.electrical.mna.Circuit
import org.ageseries.libage.sim.electrical.mna.component.Component
import org.ageseries.libage.sim.thermal.Temperature
import org.ageseries.libage.sim.thermal.ThermalMass
import org.eln2.mc.data.HashDataTable
import org.eln2.mc.integration.WailaNode
import org.eln2.mc.integration.WailaTooltipBuilder

const val LARGE_RESISTANCE = 1e9

object MaterialMapping {
    private val map = biMapOf(
        "iron" to Material.IRON,
        "copper" to Material.COPPER
    )

    fun getMaterial(name: String): Material {
        return map.forward[name] ?: error("Name $name does not correspond to any material.")
    }

    fun getName(material: Material): String {
        return map.backward[material] ?: error("Material $material does not have a mapping!")
    }
}

data class ThermalBodyDef(val material: Material, val mass: Double, val area: Double, val energy: Double? = null) {
    fun create() = ThermalBody(
        ThermalMass(
            material,
            energy,
            mass,
        ),
        area
    )
}

val nullMaterial = Material(0.0, 0.0, 0.0, 0.0)
fun nullThermalMass() = ThermalMass(nullMaterial, 0.0, 0.0)
fun nullThermalBody() = ThermalBody(nullThermalMass(), 0.0)

fun Material.hash(): Int {
    val a = this.electricalResistivity
    val b = this.thermalConductivity
    val c = this.specificHeat
    val d = this.density

    var result = a.hashCode()
    result = 31 * result + b.hashCode()
    result = 31 * result + c.hashCode()
    result = 31 * result + d.hashCode()
    return result
}

class ThermalBody(var thermal: ThermalMass, var area: Double) : WailaNode {
    var temperature: Temperature
        get() = thermal.temperature
        set(value) {
            thermal.temperature = value
        }

    var temperatureKelvin: Double
        get() = temperature.kelvin
        set(value) {
            thermal.temperature = Temperature(value)
        }

    var energy: Double
        get() = thermal.energy
        set(value) {
            thermal.energy = value
        }

    override fun appendWaila(builder: WailaTooltipBuilder, config: IPluginConfig?) {
        thermal.appendBody(builder, config)
    }

    companion object {
        fun createDefault(): ThermalBody {
            return ThermalBody(ThermalMass(Material.COPPER), 0.5)
        }

        fun createDefault(env: HashDataTable): ThermalBody {
            return createDefault().also { b ->
                env.getOrNull<EnvironmentalTemperatureField>()?.readTemperature()?.also {
                    b.temperature = it
                }
            }
        }
    }
}

fun Circuit.addAll(components: Iterable<Component>) {
    components.forEach {
        this.add(it)
    }
}
