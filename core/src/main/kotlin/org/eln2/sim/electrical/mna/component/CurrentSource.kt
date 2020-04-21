package org.eln2.sim.electrical.mna.component

class CurrentSource : Port() {
	override var name: String = "is"

	var current: Double = 0.0
		set(value) {
			if (isInCircuit)
				circuit!!.stampCurrentSource(pos.index, neg.index, value - field)
			field = value
		}

	override fun detail(): String {
		return "[current source i:$current]"
	}

	override fun stamp() {
		if (!isInCircuit) return
		circuit!!.stampCurrentSource(pos.index, neg.index, current)
	}
}

