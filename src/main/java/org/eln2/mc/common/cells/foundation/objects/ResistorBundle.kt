package org.eln2.mc.common.cells.foundation.objects

import org.ageseries.libage.sim.electrical.mna.Circuit
import org.ageseries.libage.sim.electrical.mna.component.Resistor
import org.eln2.mc.common.cells.objects.Conventions
import org.eln2.mc.common.space.RelativeRotationDirection

/**
 * Utility class that holds a collection of resistors to be used as contact points for external components.
 * */
class ResistorBundle(private val resistance: Double) {
    private val resistors = HashMap<RelativeRotationDirection, Resistor>()

    private var prepared = false

    /**
     * This must be called once the circuit is made available, in order to register the resistors.
     * */
    fun register(connections: List<ConnectionInfo>, circuit: Circuit) {
        if (prepared) {
            error("Already prepared")
        }

        connections.forEach {
            val resistor = getResistor(it.direction)
            circuit.add(resistor)
        }

        prepared = true
    }

    /**
     * This must be called after "prepare", to finalize connections.
     * */
    fun connect(connections: List<ConnectionInfo>, sender: ElectricalObject) {
        if (!prepared) {
            error("Not prepared")
        }

        connections.forEach { connectionInfo ->
            val resistor = getResistor(connectionInfo.direction)
            val offered = connectionInfo.obj.offerComponent(sender)
            resistor.connect(Conventions.EXTERNAL_PIN, offered.component, offered.index)
        }
    }

    private fun getResistor(direction: RelativeRotationDirection): Resistor {
        return resistors.computeIfAbsent(direction) {
            if (prepared) {
                error("Tried to create resistors after bundle was prepared")
            }

            val result = Resistor()
            result.resistance = resistance

            return@computeIfAbsent result
        }
    }

    fun getOfferedResistor(direction: RelativeRotationDirection): ComponentInfo {
        return ComponentInfo(getResistor(direction), Conventions.EXTERNAL_PIN)
    }

    fun process(action: ((Resistor) -> Unit)) {
        resistors.values.forEach { action(it) }
    }

    fun clear() {
        resistors.clear()
        prepared = false
    }
}
