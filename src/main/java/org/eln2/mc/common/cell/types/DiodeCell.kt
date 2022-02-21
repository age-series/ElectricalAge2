package org.eln2.mc.common.cell.types

import net.minecraft.core.BlockPos
import org.ageseries.libage.sim.electrical.mna.component.IdealDiode
import org.eln2.mc.common.cell.CellBase
import org.eln2.mc.common.cell.ComponentInfo
import org.eln2.mc.extensions.ComponentExtensions.connectToPinOf
import org.eln2.mc.utility.UnitType
import org.eln2.mc.utility.ValueText
import org.eln2.mc.utility.ValueText.valueText

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

    override fun getHudMap(): Map<String, String> {
        var voltage: String = valueText(0.0, UnitType.VOLT)
        var current: String = valueText(0.0, UnitType.AMPERE)
        var mode: String? = null
        val map = mutableMapOf<String, String>()

        try {
            // TODO: This feature (mode) should be exposed in libage as an enum. It also needs to be translated on the client.
            mode = if (diode.resistance == diode.minimumResistance) "Forward Bias Mode (conducting)" else "Reverse Bias Mode (blocking)"
            current = valueText(diode.current, UnitType.AMPERE)
            voltage = diode.pins.joinToString(", ") { valueText(it.node?.potential ?: 0.0, UnitType.VOLT) }

        } catch (_: Exception) {
            // No results from simulator
        }

        map["voltage"] = voltage
        map["current"] = current
        if (mode != null)map["mode"] = mode

        return map
    }
}
