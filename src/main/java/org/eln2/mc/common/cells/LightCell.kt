package org.eln2.mc.common.cells

import mcp.mobius.waila.api.IPluginConfig
import org.ageseries.libage.sim.electrical.mna.component.Resistor
import org.eln2.mc.common.cells.foundation.CellBase
import org.eln2.mc.common.cells.foundation.CellPos
import org.eln2.mc.common.cells.foundation.ComponentInfo
import org.eln2.mc.extensions.ComponentExtensions.connect
import org.eln2.mc.integration.waila.IWailaProvider
import org.eln2.mc.integration.waila.TooltipBuilder
import org.eln2.mc.utility.UnitType
import org.eln2.mc.utility.ValueText

class LightCell(pos: CellPos) : CellBase(pos) {

    /*  R -> local resistors. Their first pin is grounded.
    *   C -> remote components. The second pin of the local resistors is used for them.
    *
    *       C
    *       R
    *   C R G R C
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
            resistor.resistance = 75.0
            circuit.add(resistor)
            resistor
        }, 1) // pin 1 will be connected to the remote components
    }

    override fun buildConnections() {
        connections.forEach { adjacentCell ->
            val resistor = getOfferedComponent(adjacentCell).component
            resistor.ground(0) // ground one pin of our resistor
            // then connect the other pin to them
            resistor.connect(1, adjacentCell.getOfferedComponent(this))
        }
    }

    private val neighbourToResistorLookup = HashMap<CellBase, Resistor>()
}
