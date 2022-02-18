package org.eln2.mc.common.cell.types

import net.minecraft.core.BlockPos
import org.eln2.libelectric.sim.electrical.mna.component.Resistor
import org.eln2.mc.common.cell.AbstractCell
import org.eln2.mc.common.cell.ComponentInfo
import org.eln2.mc.extensions.ComponentExtensions.connectToPinOf
import org.eln2.mc.extensions.DataBuilderExtensions.amps
import org.eln2.mc.extensions.DataBuilderExtensions.withElectricalValueColor
import org.eln2.mc.extensions.DataBuilderExtensions.withLabelColor
import org.eln2.mc.extensions.DataBuilderExtensions.withValueColor
import org.eln2.mc.utility.DataBuilder
import org.eln2.mc.utility.McColors

class GroundCell(pos : BlockPos) : AbstractCell(pos) {

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

    override fun componentForNeighbour(neighbour: AbstractCell): ComponentInfo {
        val circuit = graph.circuit

        return ComponentInfo(neighbourToResistorLookup.computeIfAbsent(neighbour) {
            val resistor = Resistor()
            resistor.resistance = 0.001
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

    private val neighbourToResistorLookup = HashMap<AbstractCell, Resistor>()

    override fun createDataBuilder(): DataBuilder {
        val current = connections.sumOf { (componentForNeighbour(it).component as Resistor).current }
        return DataBuilder()
            .amps(current)
                .withLabelColor(McColors.red)
                .withElectricalValueColor()
    }
}
