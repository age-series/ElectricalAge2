package org.eln2.mc.common.cells

import mcp.mobius.waila.api.IPluginConfig
import org.ageseries.libage.sim.electrical.mna.component.Inductor
import org.eln2.mc.common.cells.foundation.CellBase
import org.eln2.mc.common.cells.foundation.CellPos
import org.eln2.mc.common.cells.foundation.ComponentInfo
import org.eln2.mc.common.cells.foundation.ISingleElementGuiCell
import org.eln2.mc.extensions.ComponentExtensions.connect
import org.eln2.mc.integration.waila.IWailaProvider
import org.eln2.mc.integration.waila.TooltipBuilder
import org.eln2.mc.utility.UnitType
import org.eln2.mc.utility.ValueText.valueText

class InductorCell(pos: CellPos) : CellBase(pos), ISingleElementGuiCell<Double>, IWailaProvider {
    lateinit var inductor: Inductor
    var added = false

    override fun clear() {
        inductor = Inductor()
        inductor.inductance = 0.1
        added = false
    }

    override fun getOfferedComponent(neighbour: CellBase): ComponentInfo {
        val circuit = graph.circuit
        if (!added) {
            circuit.add(inductor)
            added = true
        }
        return ComponentInfo(inductor, connections.indexOf(neighbour))
    }

    override fun buildConnections() {
        connections.forEach { remoteCell ->
            val localInfo = getOfferedComponent(remoteCell)
            localInfo.component.connect(localInfo.index, remoteCell.getOfferedComponent(this))
        }
    }

    override fun getGuiValue(): Double {
        return inductor.inductance
    }

    override fun setGuiValue(value: Double) {
        inductor.inductance = value
    }

    override fun appendBody(builder: TooltipBuilder, config: IPluginConfig?) {
        try {
            builder.current(inductor.current)
            builder.energy(inductor.energy)
            builder.pinVoltages(inductor.pins)
        } catch (_: Exception) {
            // No results from simulator
        }
    }
}
