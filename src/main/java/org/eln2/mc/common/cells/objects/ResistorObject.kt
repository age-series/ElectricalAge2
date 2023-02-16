package org.eln2.mc.common.cells.objects

import org.ageseries.libage.sim.electrical.mna.Circuit
import org.ageseries.libage.sim.electrical.mna.component.Resistor
import org.eln2.mc.common.cells.foundation.objects.ComponentInfo
import org.eln2.mc.common.cells.foundation.objects.ElectricalObject

class ResistorObject : ElectricalObject() {
    private lateinit var resistor: Resistor

    override fun offerComponent(neighbour: ElectricalObject): ComponentInfo {
        return ComponentInfo(resistor, connections.indexOf(neighbour))
    }

    override fun recreateComponents() {
        resistor = Resistor()
        resistor.resistance = 1.0
    }

    override fun registerComponents(circuit: Circuit) {
        circuit.add(resistor)
    }

    override fun build() {
        connections.forEach { remote ->
            val localInfo = offerComponent(remote)
            val remoteInfo = remote.offerComponent(this)

            localInfo.component.connect(localInfo.index, remoteInfo.component, remoteInfo.index)
        }
    }
}
