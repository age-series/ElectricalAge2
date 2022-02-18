package org.eln2.mc.common.cell.types

import net.minecraft.core.BlockPos
import org.ageseries.libage.sim.electrical.mna.component.Capacitor
import org.eln2.mc.common.cell.CellBase
import org.eln2.mc.common.cell.ComponentInfo
import org.eln2.mc.extensions.ComponentExtensions.connectToPinOf
import org.eln2.mc.utility.UnitType
import org.eln2.mc.utility.ValueText

class CapacitorCell(pos : BlockPos) : CellBase(pos) {
    lateinit var capacitor : Capacitor
    var added = false

    override fun clearForRebuild() {
        capacitor = Capacitor()
        added = false
    }

    override fun componentForNeighbour(neighbour: CellBase): ComponentInfo {
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

    override fun createDataPrint(): String {
        val current = connections.sumOf { (componentForNeighbour(it).component as Capacitor).current }
        return ValueText.valueText(current, UnitType.AMPERE)
    }
}
