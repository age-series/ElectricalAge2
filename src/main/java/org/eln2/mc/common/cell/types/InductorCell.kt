package org.eln2.mc.common.cell.types

import net.minecraft.core.BlockPos
import org.ageseries.libage.sim.electrical.mna.component.Inductor
import org.eln2.mc.common.cell.CellBase
import org.eln2.mc.common.cell.ComponentInfo
import org.eln2.mc.extensions.ComponentExtensions.connectToPinOf
import org.eln2.mc.utility.UnitType
import org.eln2.mc.utility.ValueText

class InductorCell(pos : BlockPos) : CellBase(pos) {
    lateinit var inductor : Inductor
    var added = false

    override fun clearForRebuild() {
        inductor = Inductor()
        inductor.inductance = 0.1
        added = false
    }

    override fun componentForNeighbour(neighbour: CellBase): ComponentInfo {
        val circuit = graph.circuit
        if(!added) {
            circuit.add(inductor)
            added = true
        }
        return ComponentInfo(inductor, connections.indexOf(neighbour))
    }

    override fun buildConnections() {
        connections.forEach{remoteCell ->
            val localInfo = componentForNeighbour(remoteCell)
            localInfo.component.connectToPinOf(localInfo.index, remoteCell.componentForNeighbour(this))
        }
    }

    override fun createDataPrint(): String {
        return ValueText.valueText(inductor.current, UnitType.AMPERE)
    }
}