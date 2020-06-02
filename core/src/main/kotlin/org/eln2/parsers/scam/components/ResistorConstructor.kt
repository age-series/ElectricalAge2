package org.eln2.parsers.scam.components

import org.eln2.parsers.scam.PoleConstructor
import org.eln2.sim.electrical.mna.component.Component
import org.eln2.sim.electrical.mna.component.Resistor

class ResistorConstructor : PoleConstructor() {
    override fun component(data: Array<String>): Component = Resistor()
    override fun configure(component: Component, data: Array<String>) {
        val resistance = if (data.size >= 4) {
            data[3].toDoubleOrNull() ?: 1.0
        } else {
            1.0
        }
        (component as Resistor).resistance = resistance
    }
}
