package org.eln2.mc.common.cells.objects

import org.ageseries.libage.sim.electrical.mna.Circuit
import org.eln2.mc.common.cells.foundation.objects.ComponentInfo
import org.eln2.mc.common.cells.foundation.objects.ElectricalObject
import org.eln2.mc.common.cells.foundation.objects.ResistorBundle

class GroundObject : ElectricalObject() {
    private val resistors = ResistorBundle(0.1)

    override fun offerComponent(neighbour: ElectricalObject): ComponentInfo {
        return resistors.getOfferedResistor(directionOf(neighbour))
    }

    override fun recreateComponents() {
        resistors.clear()
    }

    override fun registerComponents(circuit: Circuit) {
        resistors.register(connections, circuit)
    }

    override fun build() {
        resistors.connect(connections, this)
        resistors.process { it.ground(Conventions.INTERNAL_PIN) }
    }
}
