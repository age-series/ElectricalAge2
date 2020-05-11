package org.eln2.parsers.scam.components

import org.eln2.parsers.scam.PoleConstructor
import org.eln2.sim.electrical.mna.component.Component
import org.eln2.sim.electrical.mna.component.CurrentSource

class CurrentSourceConstructor : PoleConstructor() {
	override fun component(data: Array<String>): Component = CurrentSource()
	override fun configure(component: Component, data: Array<String>) {
		val current = if (data.size >= 4) {
			data[3].toDoubleOrNull()?: 1.0
		} else {
			1.0
		}
		(component as CurrentSource).current = current
	}
}
