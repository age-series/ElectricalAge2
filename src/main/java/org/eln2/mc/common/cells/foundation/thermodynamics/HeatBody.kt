package org.eln2.mc.common.cells.foundation.thermodynamics

import mcp.mobius.waila.api.IPluginConfig
import net.minecraft.nbt.CompoundTag
import org.eln2.mc.integration.waila.IWailaProvider
import org.eln2.mc.integration.waila.TooltipBuilder
import kotlin.math.min

data class ThermalMaterialInfo(val specificHeat: Double, val thermalConductivity: Double){
    companion object{
        val IRON = ThermalMaterialInfo(460.0, 80.2)

        private const val SPECIFIC_HEAT = "specificHeat"
        private const val THERMAL_CONDUCTIVITY = "thermalConductivity"

        fun fromNbt(tag: CompoundTag): ThermalMaterialInfo {
            return ThermalMaterialInfo(
                tag.getDouble(SPECIFIC_HEAT),
                tag.getDouble(THERMAL_CONDUCTIVITY)
            )
        }
    }

    fun serializeNbt(): CompoundTag {
        val tag = CompoundTag()

        tag.putDouble(SPECIFIC_HEAT, specificHeat)
        tag.putDouble(THERMAL_CONDUCTIVITY, thermalConductivity)

        return tag
    }
}

interface IHeatBodyView {
    val materialInfo: ThermalMaterialInfo
    val thermalEnergy: Double
    val temperature: Double
    val mass: Double

    fun convertToEnergy(temperature: Double): Double
}

/**
 * Temporary utility for heat storage calculations.
 * */
class HeatBody(override val materialInfo: ThermalMaterialInfo, override val mass: Double) : IHeatBodyView, IWailaProvider {
    override var thermalEnergy = 0.0

    val specificHeat get() = materialInfo.specificHeat
    val thermalConductivity get() = materialInfo.thermalConductivity

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
            return HeatBody(ThermalMaterialInfo.IRON, mass)
        }

        fun fromNbt(tag: CompoundTag): HeatBody {
            return HeatBody(ThermalMaterialInfo.fromNbt(tag.get(MATERIAL) as CompoundTag), tag.getDouble(MASS)).also {
                it.thermalEnergy = tag.getDouble(THERMAL_ENERGY)
            }
        }

        private const val MASS = "mass"
        private const val MATERIAL = "material"
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
        tag.put(MATERIAL, materialInfo.serializeNbt())
        tag.putDouble(THERMAL_ENERGY, thermalEnergy)

        return tag
    }
}

class HeatBodySystem {
    fun interface ISurfaceAreaAccessor{
        fun get(body: HeatBody): Double
    }

    private data class BodyConnection(val a: HeatBody, val b: HeatBody, val areaAccessor: ISurfaceAreaAccessor){
        fun contains(heatBody: HeatBody): Boolean{
            return heatBody == a || heatBody == b
        }
    }

    private val bodies = ArrayList<HeatBody>()
    private val pairs = ArrayList<BodyConnection>()

    fun insertBody(body: HeatBody) {
        if(bodies.contains(body)){
            error("Duplicate add $body")
        }

        bodies.add(body)
    }

    fun makeConnection(a: HeatBody, b: HeatBody, area: ISurfaceAreaAccessor){
        if(pairs.any { it.contains(a) && it.contains(b) }){
            error("Duplicate add $a - $b")
        }

        pairs.add(BodyConnection(a, b, area))
    }

    fun removeBody(body: HeatBody) {
        if(!bodies.remove(body)){
            error("Could not remove $body")
        }

        pairs.removeAll { it.contains(body) }
    }

    private fun conductPair(bCold: HeatBody, bHot: HeatBody, surface: Double, elapsed: Double) {
        val k = (bHot.thermalConductivity + bCold.thermalConductivity) / 2.0

        val heatFlux = k * (bHot.temperature - bCold.temperature)
        val thermalPower = heatFlux * surface
        val energyTransfer = thermalPower * elapsed

        bCold.thermalEnergy += energyTransfer
        bHot.thermalEnergy -= energyTransfer
    }

    fun conduction(elapsed: Double) {
        pairs.forEach {
            var hot = it.a
            var cold = it.b

            if(cold.temperature > hot.temperature){
               val temp = hot
               hot = cold
               cold = temp
            }

            conductPair(hot, cold, min(it.areaAccessor.get(hot), it.areaAccessor.get(cold)), elapsed)
        }
    }
}
