package org.eln2.mc.common.cell.types

import net.minecraft.core.BlockPos
import org.eln2.libelectric.sim.electrical.mna.component.Resistor
import org.eln2.mc.common.cell.CellBase
import org.eln2.mc.common.cell.ComponentInfo
import org.eln2.mc.extensions.ComponentExtensions.connectTo

class WireCell(pos : BlockPos) : CellBase(pos) {
    override fun clearForRebuild() {
        neighbourToResistorLookup.clear()
    }

    override fun componentForNeighbour(neighbour: CellBase): ComponentInfo {
        val circuit = graph.circuit

        return ComponentInfo(neighbourToResistorLookup.computeIfAbsent(neighbour) {
            val r = Resistor()
            r.resistance = 0.001

            if(!circuit.components.contains(r)){
                circuit.add(r)
                // attention! o(1) lookup required todo!
            }

            if(neighbourToResistorLookup.isNotEmpty()){
                neighbourToResistorLookup.values.first().connect(0, r, 0)
            }

            r
        }, 1)
    }

    override fun buildConnections() {
        connections.forEach{ adjacentCell ->
            // get our resistor for the neighbour
            val resistor = componentForNeighbour(adjacentCell).component

            // get the component to connect to
            val remoteComponentInfo = adjacentCell.componentForNeighbour(this)

            // connect the other pin of our resistor to their pin
            resistor.connectTo(1, remoteComponentInfo)
        }
    }

    private val neighbourToResistorLookup = HashMap<CellBase, Resistor>()

    override fun createDataPrint(): String {
        val current = connections.sumOf { (componentForNeighbour(it).component as Resistor).current }
        return "I: $current"
    }
}
