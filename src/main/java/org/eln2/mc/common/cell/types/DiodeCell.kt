package org.eln2.mc.common.cell.types

import net.minecraft.core.BlockPos
import org.ageseries.libage.sim.electrical.mna.component.IdealDiode
import org.eln2.mc.common.cell.CellBase
import org.eln2.mc.common.cell.ComponentInfo
import org.eln2.mc.extensions.ComponentExtensions.connectToPinOf
import org.eln2.mc.utility.UnitType
import org.eln2.mc.utility.ValueText

class DiodeCell(pos : BlockPos): CellBase(pos) {
    lateinit var diode : IdealDiode
    var added = false

    override fun clearForRebuild() {
        diode = IdealDiode()
        added = false
    }

    override fun componentForNeighbour(neighbour: CellBase): ComponentInfo {
        val circuit = graph.circuit
        if(!added) {
            circuit.add(diode)
            added = true
        }
        return ComponentInfo(diode, connections.indexOf(neighbour))
    }

    override fun buildConnections() {
        connections.forEach{remoteCell ->
            val localInfo = componentForNeighbour(remoteCell)
            localInfo.component.connectToPinOf(localInfo.index, remoteCell.componentForNeighbour(this))
        }
    }

    override fun createDataPrint(): String {
        return ValueText.valueText(diode.current, UnitType.AMPERE)
    }
}
