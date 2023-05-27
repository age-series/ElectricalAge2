package org.eln2.mc.common.content

import mcp.mobius.waila.api.IPluginConfig
import net.minecraft.resources.ResourceLocation
import org.ageseries.libage.sim.electrical.mna.Circuit
import org.ageseries.libage.sim.electrical.mna.component.Resistor
import org.eln2.mc.mathematics.bbVec
import org.eln2.mc.client.render.PartialModels
import org.eln2.mc.client.render.PartialModels.bbOffset
import org.eln2.mc.client.render.foundation.BasicPartRenderer
import org.eln2.mc.common.cells.foundation.*
import org.eln2.mc.common.parts.foundation.CellPart
import org.eln2.mc.common.parts.foundation.PartRenderer
import org.eln2.mc.common.parts.foundation.PartPlacementInfo
import org.eln2.mc.common.space.DirectionMask
import org.eln2.mc.common.space.RelativeDirection
import org.eln2.mc.common.space.withDirectionActualRule
import org.eln2.mc.extensions.resistor
import org.eln2.mc.integration.WailaEntity
import org.eln2.mc.integration.WailaTooltipBuilder

/**
 * The resistor object has a single resistor. At most, two connections can be made by this object.
 * */
class ResistorObject(cell: Cell, val dir1: RelativeDirection = RelativeDirection.Front, val dir2: RelativeDirection = RelativeDirection.Back) : ElectricalObject(cell),
    WailaEntity {
    private lateinit var resistor: Resistor

    init {
        ruleSet.withDirectionActualRule(DirectionMask.ofRelatives(dir1, dir2))
    }

    val hasResistor get() = this::resistor.isInitialized

    /**
     * Gets or sets the resistance.
     * */
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

    override fun appendBody(builder: WailaTooltipBuilder, config: IPluginConfig?) {
        builder.resistor(resistor)
    }
}

class ResistorCell(ci: CellCI) : Cell(ci) {
    @SimObject
    val resistorObj = ResistorObject(this)

    @SimObject
    val thermalWireObj = ThermalWireObject(this)

    init {
        behaviors.withStandardBehavior(this, { resistorObj.power }, { thermalWireObj.body })
        ruleSet.withDirectionActualRule(DirectionMask.FRONT + DirectionMask.BACK)
    }
}

class ResistorPart(id: ResourceLocation, placementContext: PartPlacementInfo) :
    CellPart(id, placementContext, Content.RESISTOR_CELL.get()) {

    override val sizeActual = bbVec(3.5, 2.25, 5.0)

    override fun createRenderer(): PartRenderer {
        return BasicPartRenderer(this, PartialModels.RESISTOR).also {
            it.downOffset = bbOffset(2.5)
        }
    }
}
