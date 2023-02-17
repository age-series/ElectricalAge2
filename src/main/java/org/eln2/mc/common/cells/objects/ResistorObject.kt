package org.eln2.mc.common.cells.objects

import mcp.mobius.waila.api.IPluginConfig
import org.ageseries.libage.sim.electrical.mna.Circuit
import org.ageseries.libage.sim.electrical.mna.component.Resistor
import org.eln2.mc.common.cells.foundation.objects.ComponentInfo
import org.eln2.mc.common.cells.foundation.objects.ElectricalObject
import org.eln2.mc.extensions.TooltipBuilderExtensions.resistor
import org.eln2.mc.integration.waila.IWailaProvider
import org.eln2.mc.integration.waila.TooltipBuilder

class ResistorObject : ElectricalObject(), IWailaProvider {
    private lateinit var resistor: Resistor

    override fun offerComponent(neighbour: ElectricalObject): ComponentInfo {
        return ComponentInfo(resistor, indexOf(neighbour))
    }

    override fun recreateComponents() {
        resistor = Resistor()
        resistor.resistance = 1.0
    }

    override fun registerComponents(circuit: Circuit) {
        circuit.add(resistor)
    }

    override fun build() {
        connections.forEach { connectionInfo ->
            val remote = connectionInfo.obj
            val localInfo = offerComponent(remote)
            val remoteInfo = remote.offerComponent(this)

            localInfo.component.connect(localInfo.index, remoteInfo.component, remoteInfo.index)
        }
    }

    override fun appendBody(builder: TooltipBuilder, config: IPluginConfig?) {
        builder.resistor(resistor)
    }
}
