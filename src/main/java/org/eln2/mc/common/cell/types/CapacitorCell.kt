package org.eln2.mc.common.cell.types

import net.minecraft.core.BlockPos
import org.eln2.libelectric.sim.electrical.mna.component.Capacitor
import org.eln2.mc.common.cell.AbstractCell
import org.eln2.mc.common.cell.ComponentInfo
import org.eln2.mc.extensions.ComponentExtensions.connectToPinOf
import org.eln2.mc.extensions.DataLabelBuilderExtensions.of
import org.eln2.mc.utility.DataLabelBuilder
import org.eln2.mc.utility.McColor
import org.eln2.mc.utility.McColorValues
import org.eln2.mc.utility.McColors

class CapacitorCell(pos : BlockPos) : AbstractCell(pos) {
    lateinit var capacitor : Capacitor
    var added = false

    override fun clearForRebuild() {
        capacitor = Capacitor()
        added = false
    }

    override fun componentForNeighbour(neighbour: AbstractCell): ComponentInfo {
        val circuit = graph.circuit

        if(!added) {
            circuit.add(capacitor)
            added = true
        }

        capacitor.capacitance = 1.0E-6

        return ComponentInfo(capacitor, connections.indexOf(neighbour))
    }

    override fun buildConnections() {
        connections.forEach{remoteCell ->
            val localInfo = componentForNeighbour(remoteCell)
            localInfo.component.connectToPinOf(localInfo.index, remoteCell.componentForNeighbour(this))
        }
    }

    override fun createDataPrint(): DataLabelBuilder {
        return DataLabelBuilder().of(capacitor)
    }
}
