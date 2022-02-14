package org.eln2.mc.common.cell.types

import net.minecraft.core.BlockPos
import org.eln2.libelectric.sim.electrical.mna.Circuit
import org.eln2.libelectric.sim.electrical.mna.component.Component
import org.eln2.libelectric.sim.electrical.mna.component.Resistor
import org.eln2.mc.Eln2
import org.eln2.mc.common.cell.CellBase
import org.eln2.mc.common.cell.ComponentInfo
import org.eln2.mc.extensions.ComponentExtensions.connectTo

class ResistorCell(pos : BlockPos) : CellBase(pos) {
    lateinit var resistor : Resistor

    override fun clearForRebuild() {
        resistor = Resistor()
    }

    override fun componentForNeighbour(neighbour: CellBase): ComponentInfo {
        val circuit = graph.circuit

        if(!circuit.components.contains(resistor)){
            circuit.add(resistor)
        }

        return ComponentInfo(resistor, connections.indexOf(neighbour))
    }

    override fun buildConnections() {
        val circuit = graph.circuit

        connections.forEach{remoteCell ->
            val localInfo = componentForNeighbour(remoteCell)
            val remoteInfo = remoteCell.componentForNeighbour(this)
            localInfo.component.connectTo(localInfo.index, remoteInfo)
        }
    }

    override fun createDataPrint(): String {
        return "I: ${resistor.current} R: ${resistor.resistance}"
    }
}
