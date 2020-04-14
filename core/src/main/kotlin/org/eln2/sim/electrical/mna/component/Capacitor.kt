package org.eln2.sim.electrical.mna.component

open class Capacitor : Port() {
	override var name: String = "c"

	var c: Double = 0.0
	var ts: Double = 0.05  // A safe default
	val eqR: Double
		get() = ts / c
	internal var i: Double = 0.0
		set(value) {
			if (isInCircuit)
				circuit!!.stampCurrentSource(pos.index, neg.index, value - field)
			field = value
		}
	var lastI: Double = 0.0

	override fun detail(): String {
		return "[capacitor c:$c]"
	}

	override fun preStep(dt: Double) {
		ts = dt
		i = (-potential) / eqR
	}

	override fun postStep(dt: Double) {
		lastI = potential / eqR + i
	}

	override fun stamp() {
		pos.stampResistor(neg, eqR)
	}
}
