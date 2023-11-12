package org.eln2.mc.common.content

import mcp.mobius.waila.api.IPluginConfig
import org.ageseries.libage.sim.electrical.mna.Circuit
import org.ageseries.libage.sim.electrical.mna.component.VoltageSource
import org.eln2.mc.add
import org.eln2.mc.client.render.PartialModels
import org.eln2.mc.client.render.foundation.BasicPartRenderer
import org.eln2.mc.common.cells.foundation.*
import org.eln2.mc.common.parts.foundation.CellPart
import org.eln2.mc.common.parts.foundation.PartCreateInfo
import org.eln2.mc.data.withDirectionRulePlanar
import org.eln2.mc.integration.WailaNode
import org.eln2.mc.integration.WailaTooltipBuilder
import org.eln2.mc.mathematics.Base6Direction3dMask

/**
 * The voltage source object has a bundle of resistors, whose External Pins are exported to other objects, and
 * a voltage source, connected to the Internal Pins of the bundle.
 * */
class VoltageSourceObject(cell: Cell) : ElectricalObject<Cell>(cell) {
    private val source = ComponentHolder {
        VoltageSource().also {
            it.potential = potential
        }
    }

    private val resistors = ResistorBundle(0.01, this)

    /**
     * Gets or sets the potential of the voltage source.
     * */
    var potential: Double = 1200.0
        set(value) {
            field = value
            if (source.isPresent) {
                source.instance.potential = value
            }
        }

    val current get() = if(source.isPresent) source.instance.current else 0.0

    /**
     * Gets or sets the resistance of the bundle.
     * Only applied when the circuit is re-built.
     * */
    var resistance: Double
        get() = resistors.resistance
        set(value) { resistors.resistance = value }

    override fun offerComponent(neighbour: ElectricalObject<*>): ElectricalComponentInfo {
        return resistors.getOfferedResistor(neighbour)
    }

    override fun clearComponents() {
        source.clear()
        resistors.clear()
    }

    override fun addComponents(circuit: Circuit) {
        circuit.add(source)
        resistors.register(connections, circuit)
    }

    override fun build() {
        source.ground(INTERNAL_PIN)
        resistors.connect(connections, this)
        resistors.forEach { it.connect(INTERNAL_PIN, source.instance, EXTERNAL_PIN) }
    }
}

class VoltageSourceCell(ci: CellCreateInfo) : Cell(ci) {
    @SimObject @Inspect
    val voltageSource = VoltageSourceObject(this)

    init {
        ruleSet.withDirectionRulePlanar(Base6Direction3dMask.FRONT)
    }
}

class VoltageSourcePart(ci: PartCreateInfo) : CellPart<VoltageSourceCell, BasicPartRenderer>(ci, Content.VOLTAGE_SOURCE_CELL.get()), WailaNode {
    override fun createRenderer() = BasicPartRenderer(this, PartialModels.VOLTAGE_SOURCE)

    override fun appendWaila(builder: WailaTooltipBuilder, config: IPluginConfig?) {
        runIfCell {
            builder.voltage(cell.voltageSource.potential)
            builder.current(cell.voltageSource.current)
        }
    }
}
