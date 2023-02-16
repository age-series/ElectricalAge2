package org.eln2.mc.common.cells

import mcp.mobius.waila.api.IPluginConfig
import org.ageseries.libage.sim.electrical.mna.component.IdealDiode
import org.eln2.mc.common.cells.foundation.CellBase
import org.eln2.mc.common.cells.foundation.CellPos
import org.eln2.mc.common.cells.foundation.ComponentInfo
import org.eln2.mc.extensions.ComponentExtensions.connect
import org.eln2.mc.integration.waila.IWailaProvider
import org.eln2.mc.integration.waila.TooltipBuilder
import org.eln2.mc.utility.UnitType
import org.eln2.mc.utility.ValueText.valueText

class DiodeCell(pos: CellPos) : CellBase(pos), IWailaProvider {
    lateinit var diode: IdealDiode
    var added = false

    override fun clear() {
        diode = IdealDiode()
        added = false
    }

    override fun getOfferedComponent(neighbour: CellBase): ComponentInfo {
        val circuit = graph.circuit
        if (!added) {
            circuit.add(diode)
            added = true
        }
        return ComponentInfo(diode, connections.indexOf(neighbour))
    }

    override fun buildConnections() {
        connections.forEach { remoteCell ->
            val localInfo = getOfferedComponent(remoteCell)
            localInfo.component.connect(localInfo.index, remoteCell.getOfferedComponent(this))
        }
    }

    override fun appendBody(builder: TooltipBuilder, config: IPluginConfig?) {
        try {
            builder.mode(if (diode.resistance == diode.minimumResistance) "Forward Bias Mode (conducting)" else "Reverse Bias Mode (blocking)")
            builder.current(diode.current)
            builder.pinVoltages(diode.pins)
        } catch (_: Exception) {
            // No results from simulator
        }
    }
}
