package org.eln2.mc.common.cells

import mcp.mobius.waila.api.IPluginConfig
import org.ageseries.libage.sim.electrical.mna.component.Resistor
import org.eln2.mc.common.cells.foundation.CellBase
import org.eln2.mc.common.cells.foundation.CellPos
import org.eln2.mc.common.cells.foundation.ComponentInfo
import org.eln2.mc.common.cells.foundation.ISingleElementGuiCell
import org.eln2.mc.extensions.ComponentExtensions.connect
import org.eln2.mc.integration.waila.IWailaProvider
import org.eln2.mc.integration.waila.TooltipBuilder
import org.eln2.mc.utility.UnitType
import org.eln2.mc.utility.ValueText.valueText

class ResistorCell(pos: CellPos) : CellBase(pos), ISingleElementGuiCell<Double>, IWailaProvider {
    lateinit var resistor: Resistor
    private var added = false

    override fun clear() {
        resistor = Resistor()
        resistor.resistance = 100.0
        added = false
    }

    override fun getOfferedComponent(neighbour: CellBase): ComponentInfo {
        val circuit = graph.circuit
        if (!added) {
            circuit.add(resistor)
            added = true
        }
        return ComponentInfo(resistor, connections.indexOf(neighbour))
    }

    override fun buildConnections() {
        connections.forEach { remoteCell ->
            val localInfo = getOfferedComponent(remoteCell)
            localInfo.component.connect(localInfo.index, remoteCell.getOfferedComponent(this))
        }
    }

    override fun getGuiValue(): Double {
        return resistor.resistance
    }

    override fun setGuiValue(value: Double) {
        resistor.resistance = value
    }

    override fun appendBody(builder: TooltipBuilder, config: IPluginConfig?) {
        try {
            builder.resistance(resistor.resistance)
            builder.current(resistor.current)
            builder.power(resistor.power)
            builder.pinVoltages(resistor.pins)
        } catch (_: Exception) {
            // No results from simulator
        }
    }
}
