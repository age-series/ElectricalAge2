package org.eln2.mc.common.cell.types

import net.minecraft.core.BlockPos
import org.ageseries.libage.sim.electrical.mna.component.Capacitor
import org.eln2.mc.common.cell.CellBase
import org.eln2.mc.common.cell.ComponentInfo
import org.eln2.mc.extensions.ComponentExtensions.connectToPinOf
import org.eln2.mc.utility.UnitType
import org.eln2.mc.utility.ValueText
import org.eln2.mc.utility.ValueText.valueText

class CapacitorCell(pos : BlockPos) : CellBase(pos) {
    lateinit var capacitor : Capacitor
    var added = false

    override fun clearForRebuild() {
        capacitor = Capacitor()
        capacitor.capacitance = 1.0E-6
        added = false
    }

    override fun componentForNeighbour(neighbour: CellBase): ComponentInfo {
        val circuit = graph.circuit
        if(!added) {
            circuit.add(capacitor)
            added = true
        }
        return ComponentInfo(capacitor, connections.indexOf(neighbour))
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
        val capacitance: String = valueText(capacitor.capacitance, UnitType.FARAD)
        var joules: String = valueText(0.0, UnitType.JOULE)
        val map = mutableMapOf<String, String>()

        try {
            current = valueText(capacitor.current, UnitType.AMPERE)
            joules = valueText(capacitor.energy, UnitType.JOULE)
            voltage = capacitor.pins.joinToString(", ") { valueText(it.node?.potential ?: 0.0, UnitType.VOLT) }
        } catch (_: Exception) {
            // No results from simulator
        }

        map["voltage"] = voltage
        map["current"] = current
        map["capacitance"] = capacitance
        map["energy"] = joules

        return map
    }
}
