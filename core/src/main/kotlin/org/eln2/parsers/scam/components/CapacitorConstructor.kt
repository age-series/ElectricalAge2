package org.eln2.parsers.scam.components

import org.eln2.parsers.scam.PoleConstructor
import org.eln2.sim.electrical.mna.component.Capacitor
import org.eln2.sim.electrical.mna.component.Component

class CapacitorConstructor : PoleConstructor() {
    override fun component(data: Array<String>): Component = Capacitor()
    override fun configure(component: Component, data: Array<String>) {
        val farad = if (data.size >= 4) {
            data[3].toDoubleOrNull() ?: 1.0
        } else {
            1.0
        }
        (component as Capacitor).capacitance = farad
    }
}
