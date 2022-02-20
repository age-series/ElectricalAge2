package org.eln2.mc.common.cell.types

import net.minecraft.core.BlockPos
import org.ageseries.libage.sim.electrical.mna.component.Resistor
import org.eln2.mc.common.cell.CellBase
import org.eln2.mc.common.cell.ComponentInfo
import org.eln2.mc.extensions.ComponentExtensions.connectToPinOf
import org.eln2.mc.utility.UnitType
import org.eln2.mc.utility.ValueText

class LightCell(pos : BlockPos): CellBase(pos) {

    /*  R -> local resistors. Their first pin is grounded.
    *   C -> remote components. The second pin of the local resistors is used for them.
    *
    *       C
    *       R
    *   C R G R C
    *       R
    *       C
    */

    override fun clearForRebuild() {
        neighbourToResistorLookup.clear()
    }

    override fun componentForNeighbour(neighbour: CellBase): ComponentInfo {
        val circuit = graph.circuit

        return ComponentInfo(neighbourToResistorLookup.computeIfAbsent(neighbour) {
            val resistor = Resistor()
            resistor.resistance = 75.0
            circuit.add(resistor)
            resistor
        }, 1) // pin 1 will be connected to the remote components
    }

    override fun buildConnections() {
        connections.forEach{ adjacentCell ->
            val resistor = componentForNeighbour(adjacentCell).component
            resistor.ground(0) // ground one pin of our resistor
            // then connect the other pin to them
            resistor.connectToPinOf(1, adjacentCell.componentForNeighbour(this))
        }
    }

    private val neighbourToResistorLookup = HashMap<CellBase, Resistor>()

    override fun getHudMap(): Map<String, String> {
        val voltage: String = ValueText.valueText(0.0, UnitType.VOLT)
        var current: String = ValueText.valueText(0.0, UnitType.AMPERE)
        val map = mutableMapOf<String, String>()

        try {
            val currents = connections.map { (componentForNeighbour(it).component as Resistor).current }
            val currentString = currents.joinToString(", ") { ValueText.valueText(it, UnitType.AMPERE) }
            if (currentString.isNotEmpty())
                current = currentString
        } catch (_: Exception) {
            // don't care, sim is in a bad/unready state
        }

        map["voltage"] = voltage
        map["current"] = current

        return map
    }
}
