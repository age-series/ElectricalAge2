package org.eln2.mc.common.cell.types

import net.minecraft.core.BlockPos
import org.ageseries.libage.sim.electrical.mna.component.Resistor
import org.eln2.mc.common.cell.CellBase
import org.eln2.mc.common.cell.ComponentInfo
import org.eln2.mc.extensions.ComponentExtensions.connectToPinOf
import org.eln2.mc.utility.UnitType
import org.eln2.mc.utility.ValueText
import org.eln2.mc.utility.ValueText.valueText

class ResistorCell(pos : BlockPos) : CellBase(pos) {
    lateinit var resistor : Resistor
    var added = false

    override fun clearForRebuild() {
        resistor = Resistor()
        resistor.resistance = 100.0
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

    override fun getHudMap(): Map<String, String> {
        var voltage: String = valueText(0.0, UnitType.VOLT)
        var current: String = valueText(0.0, UnitType.AMPERE)
        val resistance: String = valueText(resistor.resistance, UnitType.OHM)
        var power: String = valueText(0.0, UnitType.WATT)
        val map = mutableMapOf<String, String>()

        try {
            current = valueText(resistor.current, UnitType.AMPERE)
            voltage = resistor.pins.joinToString(", ") { valueText(it.node?.potential ?: 0.0, UnitType.VOLT) }
            power = valueText(resistor.power, UnitType.WATT)
        } catch (_: Exception) {
            // No results from simulator
        }

        map["voltage"] = voltage
        map["current"] = current
        map["resistance"] = resistance
        map["power"] = power

        return map
    }
}
