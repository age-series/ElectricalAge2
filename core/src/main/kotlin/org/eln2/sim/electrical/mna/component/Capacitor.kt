package org.eln2.sim.electrical.mna.component

import org.eln2.debug.dprintln

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
	var idealU: Double = 0.0

	override fun detail(): String {
		return "[capacitor c:$c]"
	}

	override fun preStep(dt: Double) {
		ts = dt
		i = -idealU / eqR
		dprintln("C.preS: i=$i eqR=$eqR idealU=$idealU")
	}

	override fun postStep(dt: Double) {
		idealU = potential
		dprintln("C.postS: potential=$potential (-> idealU)")
	}

	override fun stamp() {
		pos.stampResistor(neg, eqR)
	}
}
