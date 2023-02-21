package org.eln2.mc.common.cells.foundation

import mcp.mobius.waila.api.IPluginConfig
import net.minecraft.nbt.CompoundTag
import org.eln2.mc.integration.waila.IWailaProvider
import org.eln2.mc.integration.waila.TooltipBuilder

interface IHeatBodyView {
    val thermalEnergy: Double
    val temperature: Double
    val specificHeat: Double
    val mass: Double
    fun convertToEnergy(temperature: Double): Double
}

/**
 * Temporary utility for heat storage calculations.
 * */
class HeatBody(override var specificHeat: Double, override var mass: Double) : IHeatBodyView, IWailaProvider {
    override var thermalEnergy = 0.0

    override var temperature
        get() = thermalEnergy / (mass * specificHeat)
        set(value) { thermalEnergy = convertToEnergy(value) }

    fun ensureNotNegative() {
        if(thermalEnergy < 0.0) {
            thermalEnergy = 0.0
        }
    }

    companion object {
        fun iron(mass: Double): HeatBody {
            return HeatBody(460.6, mass)
        }

        fun fromNbt(tag: CompoundTag): HeatBody {
            return HeatBody(0.0, 0.0).also {
                it.deserializeNbt(tag)
            }
        }

        private const val MASS = "mass"
        private const val SPECIFIC_HEAT = "specificHeat"
        private const val THERMAL_ENERGY = "thermalEnergy"
    }

    override fun convertToEnergy(temperature: Double): Double {
        return temperature * mass * specificHeat
    }

    override fun appendBody(builder: TooltipBuilder, config: IPluginConfig?) {
        builder.temperature(if(temperature < 0.1) 0.0 else temperature)
    }

    fun serializeNbt(): CompoundTag{
        val tag = CompoundTag()

        tag.putDouble(MASS, mass)
        tag.putDouble(SPECIFIC_HEAT, specificHeat)
        tag.putDouble(THERMAL_ENERGY, thermalEnergy)

        return tag
    }

    fun deserializeNbt(tag: CompoundTag) {
        mass = tag.getDouble(MASS)
        specificHeat = tag.getDouble(SPECIFIC_HEAT)
        thermalEnergy = tag.getDouble(THERMAL_ENERGY)
    }
}
