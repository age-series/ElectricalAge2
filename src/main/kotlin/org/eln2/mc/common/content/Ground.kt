package org.eln2.mc.common.content

import net.minecraft.resources.ResourceLocation
import org.ageseries.libage.sim.electrical.mna.Circuit
import org.eln2.mc.client.render.PartialModels
import org.eln2.mc.client.render.PartialModels.bbOffset
import org.eln2.mc.client.render.foundation.BasicPartRenderer
import org.eln2.mc.common.cells.foundation.*
import org.eln2.mc.common.parts.foundation.CellPart
import org.eln2.mc.common.parts.foundation.PartPlacementInfo
import org.eln2.mc.data.withDirectionRulePlanar
import org.eln2.mc.mathematics.Base6Direction3dMask
import org.eln2.mc.mathematics.bbVec

class GroundObject(cell: Cell) : ElectricalObject<Cell>(cell) {
    private val resistors = ResistorBundle(0.01, this)

    var resistance: Double
        get() = resistors.resistance
        set(value) {
            resistors.resistance = value
        }

    override fun offerComponent(neighbour: ElectricalObject<*>): ElectricalComponentInfo {
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
        resistors.forEach { it.ground(INTERNAL_PIN) }
    }
}

class GroundCell(ci: CellCreateInfo) : Cell(ci) {
    @SimObject
    val ground = GroundObject(this)

    init {
        ruleSet.withDirectionRulePlanar(Base6Direction3dMask.FRONT)
    }
}

class GroundPart(id: ResourceLocation, placementContext: PartPlacementInfo) : CellPart<GroundCell, BasicPartRenderer>(id, placementContext, Content.GROUND_CELL.get()), RotatablePart {
    override val partSize = bbVec(4.0, 4.0, 4.0)

    override fun createRenderer() = BasicPartRenderer(this, PartialModels.GROUND).also {
        it.downOffset = bbOffset(4)
    }
}
