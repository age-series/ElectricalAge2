package org.eln2.mc.common.cell.types

import net.minecraft.core.BlockPos
import org.eln2.libelectric.sim.electrical.mna.component.Inductor
import org.eln2.mc.common.cell.AbstractCell
import org.eln2.mc.common.cell.ComponentInfo
import org.eln2.mc.extensions.ComponentExtensions.connectToPinOf
import org.eln2.mc.extensions.DataBuilderExtensions.of
import org.eln2.mc.utility.DataBuilder

class InductorCell(pos : BlockPos) : AbstractCell(pos) {
    lateinit var inductor : Inductor
    var added = false

    override fun clearForRebuild() {
        inductor = Inductor()
        added = false
    }

    override fun componentForNeighbour(neighbour: AbstractCell): ComponentInfo {
        val circuit = graph.circuit

        if(!added) {
            circuit.add(inductor)
            added = true
        }

        inductor.inductance = 0.1

        return ComponentInfo(inductor, connections.indexOf(neighbour))
    }

    override fun buildConnections() {
        connections.forEach{remoteCell ->
            val localInfo = componentForNeighbour(remoteCell)
            localInfo.component.connectToPinOf(localInfo.index, remoteCell.componentForNeighbour(this))
        }
    }

    override fun createDataBuilder(): DataBuilder {
        return DataBuilder().of(inductor)
    }
}
