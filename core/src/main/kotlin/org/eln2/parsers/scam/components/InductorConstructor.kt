package org.eln2.parsers.scam.components;

import org.eln2.parsers.scam.PoleConstructor
import org.eln2.sim.electrical.mna.component.Component
import org.eln2.sim.electrical.mna.component.Inductor

class InductorConstructor: PoleConstructor() {
	override fun component(data: Array<String>): Component  = Inductor()
	override fun configure(component: Component, data: Array<String>) {
		val henry = if (data.size >= 4) {
			data[3].toDoubleOrNull()?: 1.0
		} else {
			1.0
		}
		(component as Inductor).l = henry
	}
}
