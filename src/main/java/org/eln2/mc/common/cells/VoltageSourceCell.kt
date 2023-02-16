package org.eln2.mc.common.cells

import mcp.mobius.waila.api.IPluginConfig
import org.ageseries.libage.sim.electrical.mna.component.Resistor
import org.ageseries.libage.sim.electrical.mna.component.VoltageSource
import org.eln2.mc.common.cells.foundation.CellBase
import org.eln2.mc.common.cells.foundation.CellPos
import org.eln2.mc.common.cells.foundation.ComponentInfo
import org.eln2.mc.common.cells.foundation.ISingleElementGuiCell
import org.eln2.mc.extensions.ComponentExtensions.connect
import org.eln2.mc.integration.waila.IWailaProvider
import org.eln2.mc.integration.waila.TooltipBuilder
import org.eln2.mc.utility.UnitType
import org.eln2.mc.utility.ValueText.valueText

class VoltageSourceCell(pos: CellPos) : CellBase(pos), ISingleElementGuiCell<Double>, IWailaProvider {

    /*  R -> local resistors. Their first pin is connected to the voltage source.
    *   C -> remote components. The second pin of the local resistors is used for them.
    *
    *       C
    *       R
    *   C R V R C
    *       R
    *       C
    */

    private lateinit var source: VoltageSource

    override fun clear() {
        source = VoltageSource()
        source.potential = 5.0
        neighbourToResistorLookup.clear()
    }

    override fun getOfferedComponent(neighbour: CellBase): ComponentInfo {
        val circuit = graph.circuit

        return ComponentInfo(neighbourToResistorLookup.computeIfAbsent(neighbour) {
            val resistor = Resistor()
            resistor.resistance = 0.001
            circuit.add(resistor)
            resistor
        }, 1) // pin 1 will be connected to the remote cell
    }

    override fun buildConnections() {
        val circuit = graph.circuit
        circuit.add(source)
        source.ground(0)

        connections.forEach { remoteCell ->
            val localResistor = getOfferedComponent(remoteCell).component // get local resistor
            localResistor.connect(
                1,
                remoteCell.getOfferedComponent(this)
            ) // connect local resistor to remote component
            localResistor.connect(0, source, 1) // connect local resistor to our voltage source
        }
    }

    private val neighbourToResistorLookup = HashMap<CellBase, Resistor>()

    override fun getGuiValue(): Double = source.potential

    override fun setGuiValue(value: Double) {
        source.potential = value
    }

    override fun appendBody(builder: TooltipBuilder, config: IPluginConfig?) {
        try {
            builder.voltage(source.potential)
            builder.current(source.current)
            builder.power(source.potential * source.current)
        } catch (_: Exception) {
            // No results from simulator
        }
    }
}
