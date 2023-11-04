package org.eln2.mc.common.content

import net.minecraft.resources.ResourceLocation
import org.ageseries.libage.sim.electrical.mna.Circuit
import org.ageseries.libage.sim.electrical.mna.component.VoltageSource
import org.eln2.mc.add
import org.eln2.mc.client.render.PartialModels
import org.eln2.mc.client.render.PartialModels.bbOffset
import org.eln2.mc.client.render.foundation.BasicPartRenderer
import org.eln2.mc.common.cells.foundation.*
import org.eln2.mc.common.parts.foundation.CellPart
import org.eln2.mc.common.parts.foundation.PartCreateInfo
import org.eln2.mc.common.parts.foundation.PartPlacementInfo
import org.eln2.mc.data.DataContainer
import org.eln2.mc.data.VoltageField
import org.eln2.mc.data.data
import org.eln2.mc.data.withDirectionRulePlanar
import org.eln2.mc.integration.WailaEntity
import org.eln2.mc.mathematics.Base6Direction3dMask
import org.eln2.mc.mathematics.bbVec

/**
 * The voltage source object has a bundle of resistors, whose External Pins are exported to other objects, and
 * a voltage source, connected to the Internal Pins of the bundle.
 * */
class VoltageSourceObject(cell: Cell) : ElectricalObject<Cell>(cell), WailaEntity, DataContainer {
    override val dataNode = data {
        it.withField(VoltageField {
            source.value?.potential ?: 0.0
        })
    }

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

class VoltageSourcePart(ci: PartCreateInfo) : CellPart<VoltageSourceCell, BasicPartRenderer>(ci, Content.VOLTAGE_SOURCE_CELL.get()) {

    override fun createRenderer() = BasicPartRenderer(this, PartialModels.VOLTAGE_SOURCE)
}
