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
		c.ts = ccd.falstad.nominalTimestep
		c.c = ccd.data[0].toDouble()
		c.idealU = ccd.data[1].toDouble()
	}
}
