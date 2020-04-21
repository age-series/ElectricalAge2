package org.eln2.sim.electrical.mna.component

open class Inductor : Port() {
	override var name: String = "l"

	var l: Double = 0.0
	var ts: Double = 0.05  // A safe default
	val eqR: Double
		get() = l / ts
	internal var i: Double = 0.0
		set(value) {
			if (isInCircuit)
				circuit!!.stampCurrentSource(pos.index, neg.index, value - field)
			field = value
		}
	var phi: Double = 0.0

	override fun detail(): String {
		return "[inductor h:$l]"
	}

	override fun preStep(dt: Double) {
		ts = dt
		i = phi / l
	}

	override fun postStep(dt: Double) {
		phi += potential * ts
	}

	override fun stamp() {
		node(0).stampResistor(node(1), eqR)
	}
}
