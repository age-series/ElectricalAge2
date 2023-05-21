package org.eln2.mc.common.content

import mcp.mobius.waila.api.IPluginConfig
import net.minecraft.resources.ResourceLocation
import org.ageseries.libage.sim.electrical.mna.Circuit
import org.ageseries.libage.sim.electrical.mna.component.Resistor
import org.eln2.mc.mathematics.bbVec
import org.eln2.mc.client.render.PartialModels
import org.eln2.mc.client.render.PartialModels.bbOffset
import org.eln2.mc.client.render.foundation.BasicPartRenderer
import org.eln2.mc.common.cells.foundation.CellBase
import org.eln2.mc.common.cells.foundation.CellPos
import org.eln2.mc.common.cells.foundation.behaviors.withStandardBehavior
import org.eln2.mc.common.cells.foundation.objects.ElectricalComponentInfo
import org.eln2.mc.common.cells.foundation.objects.ElectricalObject
import org.eln2.mc.common.cells.foundation.objects.SimulationObjectSet
import org.eln2.mc.common.parts.foundation.CellPart
import org.eln2.mc.common.parts.foundation.IPartRenderer
import org.eln2.mc.common.parts.foundation.PartPlacementContext
import org.eln2.mc.extensions.resistor
import org.eln2.mc.integration.waila.IWailaProvider
import org.eln2.mc.integration.waila.TooltipBuilder
/*

*/
/**
 * The resistor object has a single resistor. At most, two connections can be made by this object.
 * *//*

class ResistorObject(cell: CellBase) : ElectricalObject(cell), IWailaProvider {
    private lateinit var resistor: Resistor

    val hasResistor get() = this::resistor.isInitialized

    */
/**
     * Gets or sets the resistance.
     * *//*

    var resistance: Double = 1.0
        set(value) {
            field = value

            // P.S. do not use an epsilon comparison here. I just want to make sure
            // we can set the same resistance, presumably in an update loop.
            if(hasResistor && resistor.resistance != value){
                resistor.resistance = value
            }
        }

    val current get() = resistor.current
    val power get() = resistor.power

    override val maxConnections = 2

    override fun offerComponent(neighbour: ElectricalObject): ElectricalComponentInfo {
        return ElectricalComponentInfo(resistor, indexOf(neighbour))
    }

    override fun clearComponents() {
        resistor = Resistor()
        resistor.resistance = resistance
    }

    override fun addComponents(circuit: Circuit) {
        circuit.add(resistor)
    }

    override fun build() {
        connections.forEach { remote ->
            val localInfo = offerComponent(remote)
            val remoteInfo = remote.offerComponent(this)

            localInfo.component.connect(localInfo.index, remoteInfo.component, remoteInfo.index)
        }
    }

    override fun appendBody(builder: TooltipBuilder, config: IPluginConfig?) {
        builder.resistor(resistor)
    }
}

class ResistorCell(pos: CellPos, id: ResourceLocation) : CellBase(pos, id) {
    init {
        behaviors.withStandardBehavior(this, { resistor.power }, { thermal.body })
    }

    override fun createObjectSet(): SimulationObjectSet {
        return SimulationObjectSet(ResistorObject(this), ThermalWireObject(this))
    }

    private val resistor get() = electricalObject as ResistorObject
    private val thermal get() = thermalObject as ThermalWireObject
}

class ResistorPart(id: ResourceLocation, placementContext: PartPlacementContext) :
    CellPart(id, placementContext, Content.RESISTOR_CELL.get()) {

    override val baseSize = bbVec(3.5, 2.25, 5.0)

    override fun createRenderer(): IPartRenderer {
        return BasicPartRenderer(this, PartialModels.RESISTOR).also {
            it.downOffset = bbOffset(2.5)
        }
    }
}
*/
