package org.eln2.mc.common.content

import mcp.mobius.waila.api.IPluginConfig
import org.ageseries.libage.sim.electrical.mna.Circuit
import org.eln2.mc.client.render.PartialModels
import org.eln2.mc.client.render.foundation.BasicPartRenderer
import org.eln2.mc.common.cells.foundation.*
import org.eln2.mc.common.parts.foundation.CellPart
import org.eln2.mc.common.parts.foundation.PartCreateInfo
import org.eln2.mc.data.withDirectionRulePlanar
import org.eln2.mc.integration.WailaNode
import org.eln2.mc.integration.WailaTooltipBuilder
import org.eln2.mc.mathematics.Base6Direction3dMask

class GroundObject(cell: Cell) : ElectricalObject<Cell>(cell) {
    private val resistors = ResistorBundle(0.01, this)

    val totalCurrent get() = resistors.totalCurrent
    val totalPower get() = resistors.totalPower

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

class GroundPart(ci: PartCreateInfo) : CellPart<GroundCell, BasicPartRenderer>(ci, Content.GROUND_CELL.get()), WrenchRotatablePart, WailaNode {
    override fun createRenderer() = BasicPartRenderer(this, PartialModels.GROUND)

    override fun appendWaila(builder: WailaTooltipBuilder, config: IPluginConfig?) {
        runIfCell {
            builder.current(cell.ground.totalCurrent)
            builder.power(cell.ground.totalPower)
        }
    }
}
