package org.eln2.mc.common.cells

import mcp.mobius.waila.api.IPluginConfig
import org.ageseries.libage.sim.electrical.mna.component.Capacitor
import org.eln2.mc.common.cells.foundation.CellBase
import org.eln2.mc.common.cells.foundation.CellPos
import org.eln2.mc.common.cells.foundation.ComponentInfo
import org.eln2.mc.common.cells.foundation.ISingleElementGuiCell
import org.eln2.mc.extensions.ComponentExtensions.connect
import org.eln2.mc.integration.waila.IWailaProvider
import org.eln2.mc.integration.waila.TooltipBuilder
import org.eln2.mc.utility.UnitType
import org.eln2.mc.utility.ValueText.valueText

class CapacitorCell(pos: CellPos) : CellBase(pos), ISingleElementGuiCell<Double>, IWailaProvider {
    lateinit var capacitor: Capacitor
    var added = false

    override fun clear() {
        capacitor = Capacitor()
        capacitor.capacitance = 1.0E-6
        added = false
    }

    override fun getOfferedComponent(neighbour: CellBase): ComponentInfo {
        val circuit = graph.circuit
        if (!added) {
            circuit.add(capacitor)
            added = true
        }
        return ComponentInfo(capacitor, connections.indexOf(neighbour))
    }

    override fun buildConnections() {
        connections.forEach { remoteCell ->
            val localInfo = getOfferedComponent(remoteCell)
            localInfo.component.connect(localInfo.index, remoteCell.getOfferedComponent(this))
        }
    }

    override fun getGuiValue(): Double {
        return capacitor.capacitance
    }

    override fun setGuiValue(value: Double) {
        capacitor.capacitance = value
    }

    override fun appendBody(builder: TooltipBuilder, config: IPluginConfig?) {
        builder.capacitance(capacitor.capacitance)

        try {
            builder.current(capacitor.current)
            builder.energy(capacitor.energy)
            builder.pinVoltages(capacitor.pins)
        } catch (_: Exception) {
            // No results from simulator
        }
    }
}
