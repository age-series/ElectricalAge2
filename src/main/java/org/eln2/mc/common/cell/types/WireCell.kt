package org.eln2.mc.common.cell.types

import net.minecraft.core.BlockPos
import org.eln2.libelectric.sim.electrical.mna.component.Resistor
import org.eln2.mc.common.cell.AbstractCell
import org.eln2.mc.common.cell.ComponentInfo
import org.eln2.mc.extensions.ComponentExtensions.connectToPinOf
import org.eln2.mc.extensions.DataBuilderExtensions.enumerateOn
import org.eln2.mc.extensions.DataBuilderExtensions.siEntry
import org.eln2.mc.extensions.DataBuilderExtensions.withElectricalValueColor
import org.eln2.mc.extensions.DataBuilderExtensions.withLabelColor
import org.eln2.mc.extensions.DataBuilderExtensions.withValueColor
import org.eln2.mc.utility.DataBuilder
import org.eln2.mc.utility.McColors
import org.eln2.mc.utility.SuffixConverter

class WireCell(pos : BlockPos) : AbstractCell(pos) {
    /*  R -> local resistors. Their first pins are interconnected.
    *   C -> remote components. The second pin of the local resistors is used for them.
    *
    *       C
    *       R
    *   C R ┼ R C
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

            if(neighbourToResistorLookup.isNotEmpty()){
                neighbourToResistorLookup.values.first().connect(0, resistor, 0)
            }

            resistor
        }, 1)
    }

    override fun buildConnections() {
        connections.forEach{ adjacentCell ->
            val resistor = componentForNeighbour(adjacentCell).component
            resistor.connectToPinOf(1, adjacentCell.componentForNeighbour(this))
        }
    }

    private val neighbourToResistorLookup = HashMap<AbstractCell, Resistor>()

    override fun createDataBuilder(): DataBuilder {
        return DataBuilder().enumerateOn(connections) { connection, builder, i ->
            builder.siEntry("Connection $i", "A", neighbourToResistorLookup[connection]!!.current)
                .withLabelColor(McColors.red)
                .withElectricalValueColor()
        }
    }
}
