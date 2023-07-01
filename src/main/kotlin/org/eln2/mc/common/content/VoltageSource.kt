package org.eln2.mc.common.content

import mcp.mobius.waila.api.IPluginConfig
import net.minecraft.resources.ResourceLocation
import org.ageseries.libage.sim.electrical.mna.Circuit
import org.ageseries.libage.sim.electrical.mna.component.VoltageSource
import org.eln2.mc.client.render.PartialModels
import org.eln2.mc.client.render.PartialModels.bbOffset
import org.eln2.mc.client.render.foundation.BasicPartRenderer
import org.eln2.mc.common.cells.foundation.*
import org.eln2.mc.common.parts.foundation.CellPart
import org.eln2.mc.common.parts.foundation.PartPlacementInfo
import org.eln2.mc.data.DataEntity
import org.eln2.mc.data.DataNode
import org.eln2.mc.data.VoltageField
import org.eln2.mc.data.withDirectionActualRule
import org.eln2.mc.integration.WailaEntity
import org.eln2.mc.integration.WailaTooltipBuilder
import org.eln2.mc.mathematics.DirectionMask
import org.eln2.mc.mathematics.bbVec
import org.eln2.mc.voltageSource

/**
 * The voltage source object has a bundle of resistors, whose External Pins are exported to other objects, and
 * a voltage source, connected to the Internal Pins of the bundle.
 * */
class VoltageSourceObject(cell: Cell) : ElectricalObject(cell), WailaEntity, DataEntity {
    private lateinit var source: VoltageSource
    val hasSource get() = this::source.isInitialized

    private val resistors = ResistorBundle(0.01, this)

    /**
     * Gets or sets the potential of the voltage source.
     * */
    var potential: Double = 1200.0
        set(value) {
            field = value

            if (hasSource) {
                source.potential = potential
            }
        }

    /**
     * Gets or sets the resistance of the bundle.
     * Only applied when the circuit is re-built.
     * */
    var resistance: Double
        get() = resistors.resistance
        set(value) {
            resistors.resistance = value
        }

    override fun offerComponent(neighbour: ElectricalObject): ElectricalComponentInfo {
        return resistors.getOfferedResistor(neighbour)
    }

    override fun clearComponents() {
        source = VoltageSource()
        source.potential = potential

        resistors.clear()
    }

    override fun addComponents(circuit: Circuit) {
        circuit.add(source)
        resistors.register(connections, circuit)
    }

    override fun build() {
        source.ground(INTERNAL_PIN)

        resistors.connect(connections, this)
        resistors.process { it.connect(INTERNAL_PIN, source, EXTERNAL_PIN) }
    }

    override fun appendBody(builder: WailaTooltipBuilder, config: IPluginConfig?) {
        builder.voltageSource(source)
    }

    override val dataNode = DataNode().also {
        it.data.withField {
            VoltageField { potential }
        }
    }
}

class VoltageSourceCell(ci: CellCreateInfo) : Cell(ci) {
    @SimObject
    val voltageSourceObj = VoltageSourceObject(this)

    init {
        ruleSet.withDirectionActualRule(DirectionMask.FRONT)
    }
}

class VoltageSourcePart(id: ResourceLocation, placementContext: PartPlacementInfo) :
    CellPart<BasicPartRenderer>(id, placementContext, Content.VOLTAGE_SOURCE_CELL.get()) {

    override val sizeActual = bbVec(6.0, 2.5, 6.0)

    override fun createRenderer() = BasicPartRenderer(this, PartialModels.VOLTAGE_SOURCE).also {
        it.downOffset = bbOffset(2.5)
    }
}
