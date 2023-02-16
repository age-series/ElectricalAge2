package org.eln2.mc.common.cells

import mcp.mobius.waila.api.IPluginConfig
import org.ageseries.libage.sim.electrical.mna.component.Resistor
import org.eln2.mc.Eln2
import org.eln2.mc.common.cells.foundation.CellBase
import org.eln2.mc.common.cells.foundation.CellPos
import org.eln2.mc.common.cells.foundation.ComponentInfo
import org.eln2.mc.extensions.ComponentExtensions.connect
import org.eln2.mc.integration.waila.IWailaProvider
import org.eln2.mc.integration.waila.TooltipBuilder
import org.eln2.mc.utility.UnitType
import org.eln2.mc.utility.ValueText.valueText
import kotlin.math.abs

class WireCell(pos: CellPos) : CellBase(pos), IWailaProvider {
    /*  R -> local resistors. Their first pins are interconnected.
    *   C -> remote components. The second pin of the local resistors is used for them.
    *
    *       C
    *       R
    *   C R ┼ R C
    *       R
    *       C
    */

    override fun clear() {
        neighbourToResistorLookup.clear()
    }

    override fun getOfferedComponent(neighbour: CellBase): ComponentInfo {
        val circuit = graph.circuit

        return ComponentInfo(neighbourToResistorLookup.computeIfAbsent(neighbour) {
            val resistor = Resistor()
            resistor.resistance = 0.001
            circuit.add(resistor)
            if (neighbourToResistorLookup.isNotEmpty()) {
                neighbourToResistorLookup.values.first().connect(0, resistor, 0)
            }
            resistor
        }, 1)
    }

    override fun buildConnections() {
        connections.forEach { adjacentCell ->
            val resistor = getOfferedComponent(adjacentCell).component
            resistor.connect(1, adjacentCell.getOfferedComponent(this))
        }
    }

    private val neighbourToResistorLookup = HashMap<CellBase, Resistor>()

    override fun appendBody(builder: TooltipBuilder, config: IPluginConfig?) {
        try {
            if (connections.size == 2) {
                // Straight through wire. Just give absolute value I guess since directionality is ~ meaningless for wires.

                builder.current(abs((getOfferedComponent(connections[0]).component as Resistor).current))
            }
            else {
                // Branch currents. Print them all.

                connections
                    .map { (getOfferedComponent(it).component as Resistor).current }
                    .forEach { builder.current(it) }
            }

            builder.voltage((getOfferedComponent(connections[0]).component as Resistor).pins[0].node?.potential ?: 0.0)
        } catch (_: Exception) {
            // No results from simulator
        }
    }
}
