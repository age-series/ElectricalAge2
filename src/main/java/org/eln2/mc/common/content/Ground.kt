package org.eln2.mc.common.content

import net.minecraft.resources.ResourceLocation
import org.ageseries.libage.sim.electrical.mna.Circuit
import org.eln2.mc.mathematics.bbVec
import org.eln2.mc.client.render.PartialModels
import org.eln2.mc.client.render.PartialModels.bbOffset
import org.eln2.mc.client.render.foundation.BasicPartRenderer
import org.eln2.mc.common.cells.foundation.*
import org.eln2.mc.common.parts.foundation.CellPart
import org.eln2.mc.common.parts.foundation.PartRenderer
import org.eln2.mc.common.parts.foundation.PartPlacementInfo
import org.eln2.mc.common.space.DirectionMask
import org.eln2.mc.common.space.withDirectionActualRule

/**
 * The ground object is simply a bundle of resistors, with one grounded pin.
 * The ungrounded pin is exported to other Electrical Objects.
 * */
class GroundObject(cell: Cell) : ElectricalObject(cell) {
    private val resistors = ResistorBundle(0.01, this)

    /**
     * Gets or sets the resistance of the bundle.
     * Only applied when the circuit is re-built.
     * */
    var resistance: Double
        get() = resistors.resistance
        set(value) { resistors.resistance = value }

    override fun offerComponent(neighbour: ElectricalObject): ElectricalComponentInfo {
        return resistors.getOfferedResistor(neighbour)
    }

    override fun clearComponents() {
        resistors.clear()
    }

    override fun addComponents(circuit: Circuit) {
        resistors.register(connections, circuit)
    }

    override fun build() {
        resistors.connect(connections, this)
        resistors.process { it.ground(CellConvention.INTERNAL_PIN) }
    }
}

class GroundCell(ci: CellCI) : Cell(ci) {
    @SimObject
    val groundObj = GroundObject(this)

    init {
        ruleSet.withDirectionActualRule(DirectionMask.FRONT)
    }
}

class GroundPart(id: ResourceLocation, placementContext: PartPlacementInfo) :
    CellPart(id, placementContext, Content.GROUND_CELL.get()) {

    override val sizeActual = bbVec(4.0, 4.0, 4.0)

    override fun createRenderer(): PartRenderer {
        return BasicPartRenderer(this, PartialModels.GROUND).also {
            it.downOffset = bbOffset(3 + 1)
        }
    }
}
