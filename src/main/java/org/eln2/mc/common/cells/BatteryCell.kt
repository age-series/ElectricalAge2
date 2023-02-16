package org.eln2.mc.common.cells

import mcp.mobius.waila.api.IPluginConfig
import org.ageseries.libage.sim.electrical.mna.component.Resistor
import org.ageseries.libage.sim.electrical.mna.component.VoltageSource
import org.eln2.mc.common.cells.foundation.CellBase
import org.eln2.mc.common.cells.foundation.CellPos
import org.eln2.mc.common.cells.foundation.ComponentInfo
import org.eln2.mc.extensions.ComponentExtensions.connect
import org.eln2.mc.integration.waila.IWailaProvider
import org.eln2.mc.integration.waila.TooltipBuilder
import org.eln2.mc.utility.UnitType
import org.eln2.mc.utility.ValueText

class BatteryCell(pos: CellPos) : CellBase(pos), IWailaProvider {

    /*
    *   V -> local voltage source.
    *   C -> remote components.
    *
    *   C V C
    */

    private lateinit var source: VoltageSource

    override fun clear() {
        source = VoltageSource()
        source.potential = 12.5
        neighbourToResistorLookup.clear()
    }

    // TODO: Battery needs a + and - terminal. Currently, it's just implicitly grounded because I can't get that to work.

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
