package org.eln2.mc.common.content

import mcp.mobius.waila.api.IPluginConfig
import net.minecraft.resources.ResourceLocation
import org.ageseries.libage.sim.electrical.mna.Circuit
import org.ageseries.libage.sim.electrical.mna.component.VoltageSource
import org.eln2.mc.mathematics.bbVec
import org.eln2.mc.client.render.PartialModels
import org.eln2.mc.client.render.PartialModels.bbOffset
import org.eln2.mc.client.render.foundation.BasicPartRenderer
import org.eln2.mc.common.cells.foundation.CellBase
import org.eln2.mc.common.cells.foundation.CellPos
import org.eln2.mc.common.cells.foundation.Conventions
import org.eln2.mc.common.cells.foundation.objects.*
import org.eln2.mc.common.parts.foundation.CellPart
import org.eln2.mc.common.parts.foundation.IPartRenderer
import org.eln2.mc.common.parts.foundation.PartPlacementContext
import org.eln2.mc.extensions.TooltipBuilderExtensions.voltageSource
import org.eln2.mc.integration.waila.IWailaProvider
import org.eln2.mc.integration.waila.TooltipBuilder

/**
 * The voltage source object has a bundle of resistors, whose External Pins are exported to other objects, and
 * a voltage source, connected to the Internal Pins of the bundle.
 * */
class VoltageSourceObject : ElectricalObject(), IWailaProvider {
    private lateinit var source: VoltageSource
    val hasSource get() = this::source.isInitialized

    private val resistors = ResistorBundle(0.01)

    /**
     * Gets or sets the potential of the voltage source.
     * */
    var potential: Double = 1200.0
        set(value) {
            field = value

            if(hasSource){
                source.potential = potential
            }
        }

    /**
     * Gets or sets the resistance of the bundle.
     * Only applied when the circuit is re-built.
     * */
    var resistance: Double
        get() = resistors.resistance
        set(value) { resistors.resistance = value }

    override fun offerComponent(neighbour: ElectricalObject): ElectricalComponentInfo {
        return resistors.getOfferedResistor(directionOf(neighbour))
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
        source.ground(Conventions.INTERNAL_PIN)

        resistors.connect(connections, this)
        resistors.process { it.connect(Conventions.INTERNAL_PIN, source, Conventions.EXTERNAL_PIN) }
    }

    override fun appendBody(builder: TooltipBuilder, config: IPluginConfig?) {
        builder.voltageSource(source)
    }
}

class VoltageSourceCell(pos: CellPos, id: ResourceLocation) : CellBase(pos, id) {
    override fun createObjectSet(): SimulationObjectSet {
        return SimulationObjectSet(VoltageSourceObject())
    }
}

class VoltageSourcePart(id: ResourceLocation, placementContext: PartPlacementContext) :
    CellPart(id, placementContext, Content.VOLTAGE_SOURCE_CELL.get()) {

    override val baseSize = bbVec(6.0, 2.5, 6.0)

    override fun createRenderer(): IPartRenderer {
        return BasicPartRenderer(this, PartialModels.VOLTAGE_SOURCE).also {
            it.downOffset = bbOffset(2.5)
        }
    }
}
