package org.eln2.parsers.falstad.components.passive

import org.eln2.parsers.falstad.CCData
import org.eln2.parsers.falstad.PoleConstructor
import org.eln2.sim.electrical.mna.component.Capacitor
import org.eln2.sim.electrical.mna.component.Component

/**
 * Capacitor Constructor
 *
 * Basic Falstad Capacitor
 */
class CapacitorConstructor: PoleConstructor() {
	override fun component(ccd: CCData) = Capacitor()
	override fun configure(ccd: CCData, cmp: Component) {
		val c = (cmp as Capacitor)
		c.timeStep = ccd.falstad.nominalTimestep
		c.capacitance = ccd.data[0].toDouble()
	}
}
