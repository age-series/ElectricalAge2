package org.eln2.mc.common.cell.types

import net.minecraft.core.BlockPos
import org.eln2.libelectric.sim.electrical.mna.component.Resistor
import org.eln2.mc.common.cell.AbstractCell
import org.eln2.mc.common.cell.ComponentInfo
import org.eln2.mc.extensions.ComponentExtensions.connectToPinOf
import org.eln2.mc.extensions.DataBuilderExtensions.of
import org.eln2.mc.utility.DataBuilder

class ResistorCell(pos : BlockPos) : AbstractCell(pos) {
    lateinit var resistor : Resistor
    var added = false

    override fun clearForRebuild() {
        resistor = Resistor()
        resistor.resistance = 100.0
        added = false
    }

    override fun componentForNeighbour(neighbour: AbstractCell): ComponentInfo {
        val circuit = graph.circuit

        if(!added) {
            circuit.add(resistor)
            added = true
        }

        return ComponentInfo(resistor, connections.indexOf(neighbour))
    }

    override fun buildConnections() {
        connections.forEach{remoteCell ->
            val localInfo = componentForNeighbour(remoteCell)
            localInfo.component.connectToPinOf(localInfo.index, remoteCell.componentForNeighbour(this))
        }
    }

    override fun createDataBuilder(): DataBuilder {
        return DataBuilder().of(resistor)
    }
}
