package org.eln2.mc.common.content

import net.minecraft.resources.ResourceLocation
import net.minecraft.world.phys.Vec3
import org.ageseries.libage.sim.Material
import org.ageseries.libage.sim.thermal.ThermalMass
import org.eln2.mc.client.render.PartialModels
import org.eln2.mc.client.render.foundation.BasicPartRenderer
import org.eln2.mc.common.cells.foundation.CellBase
import org.eln2.mc.common.cells.foundation.CellPos
import org.eln2.mc.common.cells.foundation.behaviors.withStandardExplosionBehavior
import org.eln2.mc.common.cells.foundation.objects.SimulationObjectSet
import org.eln2.mc.common.parts.foundation.CellPart
import org.eln2.mc.common.parts.foundation.IPartRenderer
import org.eln2.mc.common.parts.foundation.PartPlacementContext
import org.eln2.mc.sim.ThermalBody

data class RadiatorModel(
    val destructionTemperature: Double,
    val surfaceArea: Double,
    val material: Material,
    val mass: Double)

class ThermalRadiatorCell(pos: CellPos, id: ResourceLocation, val model: RadiatorModel): CellBase(pos, id) {
    init {
        behaviors.withStandardExplosionBehavior(this, model.destructionTemperature) {
            thermalWire.body.temperatureK
        }
    }

    override fun createObjectSet(): SimulationObjectSet {
        return SimulationObjectSet(ThermalWireObject(this).also {
            it.body = ThermalBody(ThermalMass(model.material, it.body.thermalEnergy, model.mass), model.surfaceArea)
        })
    }

    private val thermalWire get() = thermalObject as ThermalWireObject

    val temperature get() = thermalWire.body.temperatureK
}

class RadiatorPart(id: ResourceLocation, placementContext: PartPlacementContext)
    : CellPart(id, placementContext, Content.THERMAL_COPPER_WIRE_CELL.get()) {
    override val baseSize: Vec3
        get() = Vec3(1.0, 0.5, 1.0)

    override fun createRenderer(): IPartRenderer {
        return BasicPartRenderer(this, PartialModels.THERMAL_WIRE_CROSSING_FULL)
    }
}
