package org.eln2.mc.common.cell.types

import net.minecraft.core.BlockPos
import org.ageseries.libage.sim.electrical.mna.component.Resistor
import org.eln2.mc.common.cell.CellBase
import org.eln2.mc.common.cell.ComponentInfo
import org.eln2.mc.extensions.ComponentExtensions.connectToPinOf
import org.eln2.mc.utility.UnitType
import org.eln2.mc.utility.ValueText.valueText

class ResistorCell(pos : BlockPos) : CellBase(pos) {
    lateinit var resistor : Resistor
    var added = false

    override fun clearForRebuild() {
        resistor = Resistor()
        added = false
    }

    override fun componentForNeighbour(neighbour: CellBase): ComponentInfo {
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

    override fun createDataPrint(): String {
        return "${valueText(resistor.current, UnitType.AMPERE)} ${valueText(resistor.resistance, UnitType.OHM)}"
    }
}
